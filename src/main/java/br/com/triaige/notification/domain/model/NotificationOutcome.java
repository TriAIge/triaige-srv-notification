package br.com.triaige.notification.domain.model;

/** Desfecho da notificação da sessão, informado ao orchestrator para atualizar {@code triage_sessions.status}. */
public enum NotificationOutcome {
    NOTIFIED,
    NOTIFICATION_FAILED
}
