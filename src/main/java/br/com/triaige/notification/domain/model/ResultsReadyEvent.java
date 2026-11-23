package br.com.triaige.notification.domain.model;

import java.util.UUID;

/**
 * Mensagem da {@code triaige-results-ready} já validada. O e-mail leva o relatório completo (já
 * anonimizado), decisão de produto que aceita o risco de uma caixa de e-mail comprometida do escritório.
 */
public record ResultsReadyEvent(
        UUID sessionId,
        UUID correlationId,
        String protocolo,
        UUID lawFirmId,
        String resultBucket,
        String summaryObjectKey
) {
}
