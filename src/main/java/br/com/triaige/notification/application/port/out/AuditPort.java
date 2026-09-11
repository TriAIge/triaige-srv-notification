package br.com.triaige.notification.application.port.out;

import java.util.UUID;

/**
 * Port de saida — escreve em {@code audit_events} e {@code processing_steps}.
 * Nao faz parte da lista de 4 ports "obrigatorias", mas e necessaria para cumprir
 * isso sem vazar JPA/MySQL para a camada application.
 */
public interface AuditPort {

    void recordEvent(String eventType, UUID sessionId, UUID lawFirmId, UUID correlationId, String description);

    /**
     * Grava/atualiza o processing_step de nivel sessao {@code step_name='notification_dispatch'}
     * (document_id e attachment_group_id nulos).
     */
    void recordDispatchStep(UUID sessionId, boolean completed, String errorMessage);
}
