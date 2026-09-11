package br.com.triaige.notification.domain.model;

/**
 * Valores gravados em {@code audit_events.event_type} por este servico.
 *
 * <p><b>Bloqueante externo rastreado:</b> o Orchestrator mapeia
 * {@code event_type}/{@code step_name} via enums Java fechados que lancam excecao em valor
 * desconhecido ao *ler* essas tabelas. Como o Orchestrator hoje so escreve (nunca le de volta),
 * isso nao quebra nada agora, mas notification_sent/notification_failed/notification_dispatch
 * precisam ser adicionados aos enums {@code EventType}/{@code ProcessingStepName} do
 * triaige-srv-orchestrator antes do go-live deste servico.</p>
 */
public final class NotificationAuditEventType {

    public static final String NOTIFICATION_SENT = "notification_sent";
    public static final String NOTIFICATION_FAILED = "notification_failed";

    public static final String PROCESSING_STEP_NAME = "notification_dispatch";

    private NotificationAuditEventType() {
    }
}
