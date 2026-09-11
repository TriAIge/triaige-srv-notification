package br.com.triaige.notification.adapter.in.sqs;

import br.com.triaige.notification.application.port.in.RetryConsumerPort;
import br.com.triaige.notification.config.AwsProperties;
import br.com.triaige.notification.config.SqsQueueUrlResolver;
import br.com.triaige.notification.domain.model.RetryTicket;
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
 * Adapter de entrada para {@code triaige-notification-retry}, URL resolvida direto na
 * AWS por nome (sem LocalStack/endpoint override), assim como {@link ResultsReadyConsumer}.
 */
@Component
public class RetryQueueConsumer {

    private static final Logger log = LoggerFactory.getLogger(RetryQueueConsumer.class);

    private final SqsClient sqsClient;
    private final SqsQueueUrlResolver queueUrlResolver;
    private final AwsProperties awsProperties;
    private final ObjectMapper objectMapper;
    private final RetryConsumerPort retryConsumerPort;

    public RetryQueueConsumer(SqsClient sqsClient, SqsQueueUrlResolver queueUrlResolver,
                               AwsProperties awsProperties, ObjectMapper objectMapper,
                               RetryConsumerPort retryConsumerPort) {
        this.sqsClient = sqsClient;
        this.queueUrlResolver = queueUrlResolver;
        this.awsProperties = awsProperties;
        this.objectMapper = objectMapper;
        this.retryConsumerPort = retryConsumerPort;
    }

    @Scheduled(fixedDelayString = "${aws.sqs.retry-consumer.poll-interval-ms:5000}")
    public void poll() {
        if (!awsProperties.getSqs().getRetryConsumer().isEnabled()) {
            return;
        }

        String queueUrl = queueUrlResolver.resolve(awsProperties.getSqs().getNotificationRetryQueueName());
        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(awsProperties.getSqs().getRetryConsumer().getMaxMessages())
                        .waitTimeSeconds(awsProperties.getSqs().getRetryConsumer().getWaitTimeSeconds())
                        .build())
                .messages();

        for (Message message : messages) {
            process(message, queueUrl);
        }
    }

    private void process(Message message, String queueUrl) {
        try {
            RetryTicket ticket = objectMapper.readValue(message.body(), RetryTicket.class);
            retryConsumerPort.handleRetry(ticket);
            deleteMessage(queueUrl, message);
        } catch (Exception e) {
            log.error("Falha ao processar ticket de retry messageId={} - sera reprocessado no proximo poll",
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
