package br.com.triaige.notification.adapter.in.sqs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Mensagem da fila {@code triaige-results-ready} ({@code ResultsReadyMessage} do orchestrator).
 * Só o Markdown ({@code summaryObjectKey}) é usado; a versão JSON do relatório é ignorada.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResultsReadyMessageDto(
        String schemaVersion,
        UUID sessionId,
        UUID correlationId,
        String protocolo,
        UUID lawFirmId,
        String resultBucket,
        String resultObjectKey,
        String summaryObjectKey
) {
}
