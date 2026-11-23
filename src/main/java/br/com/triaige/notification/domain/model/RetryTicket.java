package br.com.triaige.notification.domain.model;

import java.util.UUID;

/**
 * Item da fila {@code triaige-notification-retry}. Leva só os ponteiros do relatório no S3, não o
 * corpo pronto, porque o relatório é lido de novo a cada tentativa.
 */
public record RetryTicket(
        UUID sessionId,
        UUID recipientId,
        UUID lawFirmId,
        Channel canal,
        String destino,
        String protocolo,
        String resultBucket,
        String summaryObjectKey,
        UUID correlationId,
        int attemptNumber,
        String lastErrorMessage
) {
}
