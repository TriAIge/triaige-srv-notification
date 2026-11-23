package br.com.triaige.notification.application.port.in;

import br.com.triaige.notification.domain.model.RetryTicket;

/** Acionada pelo consumidor da {@code triaige-notification-retry}. */
public interface RetryConsumerPort {

    void handleRetry(RetryTicket ticket);
}
