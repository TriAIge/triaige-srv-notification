package br.com.triaige.notification.adapter.out.sqs;

import br.com.triaige.notification.application.port.out.NotificationRetrySchedulerPort;
import br.com.triaige.notification.config.AwsProperties;
import br.com.triaige.notification.config.SqsQueueUrlResolver;
import br.com.triaige.notification.domain.model.RetryTicket;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Publica em {@code triaige-notification-retry} / {@code triaige-notification-retry-dlq}
 * URLs resolvidas direto na AWS por nome (sem LocalStack/endpoint override).
 */
@Component
public class SqsNotificationRetrySchedulerAdapter implements NotificationRetrySchedulerPort {

    private static final Logger log = LoggerFactory.getLogger(SqsNotificationRetrySchedulerAdapter.class);

    // Delay crescente por tentativa: 30s, 2min, 10min.
    private static final int[] DELAY_SECONDS_BY_ATTEMPT = {30, 120, 600};
    private static final int MAX_SQS_DELAY_SECONDS = 900;

    private final SqsClient sqsClient;
    private final SqsQueueUrlResolver queueUrlResolver;
    private final AwsProperties awsProperties;
    private final ObjectMapper objectMapper;

    public SqsNotificationRetrySchedulerAdapter(SqsClient sqsClient, SqsQueueUrlResolver queueUrlResolver,
                                                 AwsProperties awsProperties, ObjectMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.queueUrlResolver = queueUrlResolver;
        this.awsProperties = awsProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void scheduleRetry(RetryTicket ticket) {
        int delay = delayFor(ticket.attemptNumber());
        send(awsProperties.getSqs().getNotificationRetryQueueName(), ticket, delay);
        log.info("Retry agendado sessionId={} recipientId={} attemptNumber={} delaySeconds={}",
                ticket.sessionId(), ticket.recipientId(), ticket.attemptNumber(), delay);
    }

    @Override
    public void sendToDeadLetter(RetryTicket ticket) {
        send(awsProperties.getSqs().getNotificationRetryDlqName(), ticket, 0);
        log.error("Tentativas de retry esgotadas (maxReceiveCount) - enviado a DLQ sessionId={} recipientId={} attemptNumber={}",
                ticket.sessionId(), ticket.recipientId(), ticket.attemptNumber());
    }

    private int delayFor(int attemptNumber) {
        int index = Math.min(Math.max(attemptNumber - 1, 0), DELAY_SECONDS_BY_ATTEMPT.length - 1);
        return Math.min(DELAY_SECONDS_BY_ATTEMPT[index], MAX_SQS_DELAY_SECONDS);
    }

    private void send(String queueName, RetryTicket ticket, int delaySeconds) {
        try {
            String queueUrl = queueUrlResolver.resolve(queueName);
            String body = objectMapper.writeValueAsString(ticket);
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(body)
                    .delaySeconds(delaySeconds)
                    .build());
        } catch (Exception e) {
            log.error("Falha ao publicar ticket de retry na fila '{}' sessionId={} recipientId={}",
                    queueName, ticket.sessionId(), ticket.recipientId(), e);
        }
    }
}
