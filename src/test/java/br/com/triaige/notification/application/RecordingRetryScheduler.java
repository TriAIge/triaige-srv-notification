package br.com.triaige.notification.application;

import br.com.triaige.notification.application.port.out.NotificationRetrySchedulerPort;
import br.com.triaige.notification.domain.model.RetryTicket;

import java.util.ArrayList;
import java.util.List;

public class RecordingRetryScheduler implements NotificationRetrySchedulerPort {

    public final List<RetryTicket> scheduled = new ArrayList<>();
    public final List<RetryTicket> deadLettered = new ArrayList<>();

    @Override
    public void scheduleRetry(RetryTicket ticket) {
        scheduled.add(ticket);
    }

    @Override
    public void sendToDeadLetter(RetryTicket ticket) {
        deadLettered.add(ticket);
    }
}
