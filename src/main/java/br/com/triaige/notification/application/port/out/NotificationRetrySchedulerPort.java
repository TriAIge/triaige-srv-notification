package br.com.triaige.notification.application.port.out;

import br.com.triaige.notification.domain.model.RetryTicket;

/** Agenda novas tentativas sem que a camada de aplicação conheça o SQS. */
public interface NotificationRetrySchedulerPort {

    /** Publica o ticket com o atraso da tentativa (30 s, 2 min, 10 min). */
    void scheduleRetry(RetryTicket ticket);

    /** Publica na DLQ de retry depois de esgotar as tentativas. */
    void sendToDeadLetter(RetryTicket ticket);
}
