package br.com.triaige.notification.domain.model;

import java.util.UUID;

/**
 * Unidade de trabalho publicada/consumida na fila {@code triaige-notification-retry}.
 *
 * <p>Carrega os ingredientes crus (protocolo, ponteiro do relatorio no S3) em vez de
 * assunto/corpo ja montados: o relatorio (seção "Corpo do e-mail" do README) e buscado de novo no
 * S3 a cada tentativa — inclusive a primeira, via {@code NotificationSender} — porque o conteudo
 * e imutavel uma vez escrito, então refazer a busca é barato e evita ticket com corpo vazio caso
 * a falha original tenha sido justamente ao buscar o relatorio (ver {@link DeliveryErrorCode#REPORT_FETCH_ERROR}).</p>
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
