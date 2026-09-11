package br.com.triaige.notification.adapter.in.sqs;

import br.com.triaige.notification.application.port.in.EventConsumerPort;
import br.com.triaige.notification.config.AwsProperties;
import br.com.triaige.notification.config.SqsQueueUrlResolver;
import br.com.triaige.notification.domain.model.ResultsReadyEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

/**
 * Adapter de entrada: consome Q3 ({@code triaige-results-ready}) diretamente na AWS
 * (URL resolvida por nome via {@link SqsQueueUrlResolver} — sem endpoint override/LocalStack) e
 * aciona {@link EventConsumerPort}.
 */
@Component
public class ResultsReadyConsumer {

    private static final Logger log = LoggerFactory.getLogger(ResultsReadyConsumer.class);
    private static final String SUPPORTED_SCHEMA_VERSION = "1.0";

    private final SqsClient sqsClient;
    private final SqsQueueUrlResolver queueUrlResolver;
    private final AwsProperties awsProperties;
    private final ObjectMapper objectMapper;
    private final EventConsumerPort eventConsumerPort;

    public ResultsReadyConsumer(SqsClient sqsClient, SqsQueueUrlResolver queueUrlResolver,
                                 AwsProperties awsProperties, ObjectMapper objectMapper,
                                 EventConsumerPort eventConsumerPort) {
        this.sqsClient = sqsClient;
        this.queueUrlResolver = queueUrlResolver;
        this.awsProperties = awsProperties;
        this.objectMapper = objectMapper;
        this.eventConsumerPort = eventConsumerPort;
    }

    @Scheduled(fixedDelayString = "${aws.sqs.results-ready-consumer.poll-interval-ms:5000}")
    public void poll() {
        if (!awsProperties.getSqs().getResultsReadyConsumer().isEnabled()) {
            return;
        }

        String queueUrl = queueUrlResolver.resolve(awsProperties.getSqs().getResultsReadyQueueName());
        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(awsProperties.getSqs().getResultsReadyConsumer().getMaxMessages())
                        .waitTimeSeconds(awsProperties.getSqs().getResultsReadyConsumer().getWaitTimeSeconds())
                        .build())
                .messages();

        for (Message message : messages) {
            process(message, queueUrl);
        }
    }

    private void process(Message message, String queueUrl) {
        try {
            ResultsReadyMessageDto dto = objectMapper.readValue(message.body(), ResultsReadyMessageDto.class);

            if (!SUPPORTED_SCHEMA_VERSION.equals(dto.schemaVersion())) {
                // Versao nao suportada -> DLQ existente de Q3, sem retry aplicativo.
                // Suposicao registrada: nao movemos a mensagem manualmente para a DLQ; deixamos de
                // deletar e a redrive policy ja provisionada em Q3 (fora do escopo deste servico)
                // encaminha para a DLQ apos o maxReceiveCount configurado na infra.
                log.error("schemaVersion nao suportada '{}' messageId={} - mensagem mantida para a DLQ existente de Q3",
                        dto.schemaVersion(), message.messageId());
                return;
            }

            ResultsReadyEvent event = new ResultsReadyEvent(dto.sessionId(), dto.correlationId(), dto.protocolo(),
                    dto.lawFirmId(), dto.resultBucket(), dto.summaryObjectKey());
            eventConsumerPort.handleResultsReady(event);
            deleteMessage(queueUrl, message);
        } catch (Exception e) {
            log.error("Falha ao processar mensagem Q3 messageId={} - sera reprocessada no proximo poll (ou DLQ apos maxReceiveCount de Q3)",
                    message.messageId(), e);
        }
    }

    private void deleteMessage(String queueUrl, Message message) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build());
    }
}
