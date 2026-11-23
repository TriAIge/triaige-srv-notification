package br.com.triaige.notification.application.usecase;

import br.com.triaige.notification.application.port.out.AuditPort;
import br.com.triaige.notification.application.port.out.DeliveryRepositoryPort;
import br.com.triaige.notification.application.port.out.NotificationRetrySchedulerPort;
import br.com.triaige.notification.domain.model.Channel;
import br.com.triaige.notification.domain.model.DeliveryErrorCode;
import br.com.triaige.notification.domain.model.DeliveryResult;
import br.com.triaige.notification.domain.model.NotificationAuditEventType;
import br.com.triaige.notification.domain.model.RetryTicket;

import java.util.UUID;

/**
 * Decide, a partir do resultado de uma tentativa, entre marcar sucesso, agendar retry ou falhar.
 * A tentativa inicial conta 0; com 3 retries, a quarta falha retentável vai para a DLQ.
 */
final class DeliveryOutcomeHandler {

    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final DeliveryRepositoryPort deliveryRepository;
    private final AuditPort auditPort;
    private final NotificationRetrySchedulerPort retryScheduler;

    DeliveryOutcomeHandler(DeliveryRepositoryPort deliveryRepository, AuditPort auditPort,
                            NotificationRetrySchedulerPort retryScheduler) {
        this.deliveryRepository = deliveryRepository;
        this.auditPort = auditPort;
        this.retryScheduler = retryScheduler;
    }

    void apply(DeliveryResult result, UUID sessionId, UUID lawFirmId, UUID correlationId, UUID recipientId,
               Channel canal, String destino, String protocolo, String resultBucket, String summaryObjectKey,
               int attemptsAlreadyMade, String successNote) {

        if (result.sucesso()) {
            deliveryRepository.markSent(sessionId, recipientId, canal, result.providerMessageId(), successNote);
            auditPort.recordEvent(NotificationAuditEventType.NOTIFICATION_SENT, sessionId, lawFirmId, correlationId,
                    "Notificação enviada com sucesso para o destinatário " + recipientId);
            return;
        }

        boolean retryable = DeliveryErrorCode.isRetryable(result.errorMessage());
        if (retryable && attemptsAlreadyMade < MAX_RETRY_ATTEMPTS) {
            RetryTicket ticket = new RetryTicket(sessionId, recipientId, lawFirmId, canal, destino, protocolo,
                    resultBucket, summaryObjectKey, correlationId, attemptsAlreadyMade + 1, result.errorMessage());
            deliveryRepository.markRetryScheduled(sessionId, recipientId, canal, result.errorMessage());
            retryScheduler.scheduleRetry(ticket);
            return;
        }

        deliveryRepository.markFailed(sessionId, recipientId, canal, destino, result.errorMessage());
        auditPort.recordEvent(NotificationAuditEventType.NOTIFICATION_FAILED, sessionId, lawFirmId, correlationId,
                "Falha ao enviar notificação para o destinatário " + recipientId + ": " + result.errorMessage());

        if (retryable) {
            // Retentável, mas já no limite de tentativas: vai para a DLQ.
            RetryTicket exhausted = new RetryTicket(sessionId, recipientId, lawFirmId, canal, destino, protocolo,
                    resultBucket, summaryObjectKey, correlationId, attemptsAlreadyMade + 1, result.errorMessage());
            retryScheduler.sendToDeadLetter(exhausted);
        }
    }
}
