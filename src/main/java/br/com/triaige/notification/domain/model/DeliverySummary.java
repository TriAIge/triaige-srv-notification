package br.com.triaige.notification.domain.model;

/** Entregas da sessão por desfecho; a sessão terminou quando não há PENDING nem RETRY_SCHEDULED. */
public record DeliverySummary(int sent, int failed, int pending) {

    public boolean allTerminal() {
        return pending == 0;
    }
}
