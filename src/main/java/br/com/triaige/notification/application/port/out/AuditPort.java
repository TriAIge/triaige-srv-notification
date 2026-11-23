package br.com.triaige.notification.application.port.out;

import java.util.UUID;

/** Grava em {@code audit_events} e {@code processing_steps} sem expor o MySQL à camada de aplicação. */
public interface AuditPort {

    void recordEvent(String eventType, UUID sessionId, UUID lawFirmId, UUID correlationId, String description);

    /** Grava ou atualiza o processing_step da sessão {@code notification_dispatch} (sem documento nem grupo). */
    void recordDispatchStep(UUID sessionId, boolean completed, String errorMessage);
}
