package br.com.triaige.notification.application.port.out;

import br.com.triaige.notification.domain.model.Channel;
import br.com.triaige.notification.domain.model.DeliverySummary;

import java.util.UUID;

/**
 * Port de saida — persiste/atualiza status de entrega ({@code notification_deliveries}).
 *
 * <p>Idempotencia por {@code (session_id, recipient_id, canal)}: {@link #tryClaim} e a
 * unica porta de entrada para criar uma entrega logica; e atomica no adapter (INSERT ... ON DUPLICATE
 * KEY UPDATE sobre a unique key) para que, com duas instancias consumindo Q3 concorrentemente,
 * apenas uma vença o claim e prossiga para o envio SMTP.</p>
 */
public interface DeliveryRepositoryPort {

    /**
     * Tenta criar a entrega logica para {@code (sessionId, recipientId, canal)}.
     *
     * @return {@code true} se esta chamada criou o registro (esta instancia deve enviar);
     *         {@code false} se o registro ja existia (outra instancia/tentativa ja possui a entrega).
     */
    boolean tryClaim(UUID sessionId, UUID recipientId, Channel canal, String destino);

    void markSent(UUID sessionId, UUID recipientId, Channel canal, String providerMessageId, String observacao);

    /**
     * Marca a entrega como falha definitiva. Faz upsert (nao exige {@link #tryClaim} previo) para
     * cobrir o caminho de destinatario sem e-mail, que nunca chega a tentar envio.
     */
    void markFailed(UUID sessionId, UUID recipientId, Channel canal, String destino, String errorMessage);

    void markRetryScheduled(UUID sessionId, UUID recipientId, Channel canal, String lastErrorMessage);

    /** Contagem das entregas da sessao por desfecho, usada para decidir se ela ja terminou de ser notificada. */
    DeliverySummary summarize(UUID sessionId);
}
