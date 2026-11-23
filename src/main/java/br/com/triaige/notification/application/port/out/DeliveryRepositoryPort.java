package br.com.triaige.notification.application.port.out;

import br.com.triaige.notification.domain.model.Channel;
import br.com.triaige.notification.domain.model.DeliverySummary;

import java.util.UUID;

/**
 * Persiste as entregas ({@code notification_deliveries}). {@link #tryClaim} é a única forma de criar
 * uma entrega e é atômico, para que só uma instância envie quando duas consomem a mesma mensagem.
 */
public interface DeliveryRepositoryPort {

    /**
     * Tenta criar a entrega de {@code (sessionId, recipientId, canal)}.
     *
     * @return {@code true} se esta chamada criou o registro e deve enviar; {@code false} se já existia.
     */
    boolean tryClaim(UUID sessionId, UUID recipientId, Channel canal, String destino);

    void markSent(UUID sessionId, UUID recipientId, Channel canal, String providerMessageId, String observacao);

    /** Marca falha definitiva, por upsert, para cobrir também o destinatário sem e-mail. */
    void markFailed(UUID sessionId, UUID recipientId, Channel canal, String destino, String errorMessage);

    void markRetryScheduled(UUID sessionId, UUID recipientId, Channel canal, String lastErrorMessage);

    /** Contagem das entregas da sessão por desfecho, para saber se a sessão terminou de ser notificada. */
    DeliverySummary summarize(UUID sessionId);
}
