package br.com.triaige.notification.adapter.in.sqs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Contrato real do evento Q3 ({@code ResultsReadyMessage.java} do Orchestrator).
 *
 * <p>{@code resultBucket}/{@code summaryObjectKey} sao usados para buscar o relatorio (markdown)
 * que compoe o corpo do e-mail (decisao de produto — ver Javadoc de {@code ResultsReadyEvent}).
 * {@code resultObjectKey} (a versao JSON do mesmo
 * relatorio) continua ignorado — o e-mail usa apenas o markdown, ja formatado para leitura.</p>
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
