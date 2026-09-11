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
 * Colaborador interno (nao e port) usado por {@link NotificationSender} para interpretar o
 * {@link DeliveryResult} de uma tentativa de envio (ou de busca do relatorio) e decidir: marcar
 * sucesso, agendar retry ou falhar definitivamente.
 *
 * <p>Contagem de tentativas: {@code attemptsAlreadyMade=0} no caminho inicial (evento Q3, ainda sem
 * ticket de retry); ao processar um {@link RetryTicket} de attemptNumber N, passa-se N. Com
 * maxReceiveCount=3, a 4a tentativa total (attemptsAlreadyMade=3) que ainda falhar de
 * forma retentavel esgota as tentativas e vai para a DLQ.</p>
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
                    "Notificacao enviada com sucesso para o destinatario " + recipientId);
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
                "Falha ao enviar notificacao para o destinatario " + recipientId + ": " + result.errorMessage());

        if (retryable) {
            // Tentativas esgotadas (retentavel, mas ja chegou ao limite) -> DLQ.
            RetryTicket exhausted = new RetryTicket(sessionId, recipientId, lawFirmId, canal, destino, protocolo,
                    resultBucket, summaryObjectKey, correlationId, attemptsAlreadyMade + 1, result.errorMessage());
            retryScheduler.sendToDeadLetter(exhausted);
        }
    }
}
