package br.com.triaige.notification.domain.model;

/**
 * Valores gravados em {@code audit_events.event_type}. Precisam existir também no enum EventType do
 * orchestrator, que rejeita valores desconhecidos ao ler a tabela.
 */
public final class NotificationAuditEventType {

    public static final String NOTIFICATION_SENT = "notification_sent";
    public static final String NOTIFICATION_FAILED = "notification_failed";

    public static final String PROCESSING_STEP_NAME = "notification_dispatch";

    private NotificationAuditEventType() {
    }
}
