package br.com.triaige.notification.domain.model;

/**
 * Resultado agregado, a nível de sessão, de notificar todos os destinatários ativos —
 * reportado de volta ao triaige-srv-orchestrator (ver {@code OrchestratorSessionCallbackPort})
 * para que {@code triage_sessions.status} reflita se a notificação foi enviada ou falhou.
 */
public enum NotificationOutcome {
    NOTIFIED,
    NOTIFICATION_FAILED
}
