package br.com.triaige.notification.application.port.in;

import br.com.triaige.notification.domain.model.RetryTicket;

/**
 * Port de entrada para a fila de retry. Acionada pelo adapter que consome
 * {@code triaige-notification-retry}.
 */
public interface RetryConsumerPort {

    void handleRetry(RetryTicket ticket);
}
