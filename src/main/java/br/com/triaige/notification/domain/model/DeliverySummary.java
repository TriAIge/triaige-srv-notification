package br.com.triaige.notification.domain.model;

/**
 * Contagem de {@code notification_deliveries} de uma sessão por desfecho, usada por
 * {@code NotificationOutcomeReporter} para decidir se a sessão já terminou de ser notificada
 * (nenhuma entrega ainda em {@code PENDING}/{@code RETRY_SCHEDULED}) e, se sim, com qual
 * resultado agregado.
 */
public record DeliverySummary(int sent, int failed, int pending) {

    public boolean allTerminal() {
        return pending == 0;
    }
}
