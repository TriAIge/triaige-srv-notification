package br.com.triaige.notification.application.port.out;

import br.com.triaige.notification.domain.model.RetryTicket;

/**
 * Port de saida — abstrai a fila de retry/DLQ do mecanismo real (SQS) para que a
 * camada application decida "quando" retentar sem conhecer SDK de fila nenhum.
 */
public interface NotificationRetrySchedulerPort {

    /** Publica o ticket com o delay correspondente ao seu attemptNumber (30s/2min/10min). */
    void scheduleRetry(RetryTicket ticket);

    /** Encerramento definitivo: publica na DLQ de retry apos esgotar as tentativas (maxReceiveCount=3). */
    void sendToDeadLetter(RetryTicket ticket);
}
