package br.com.triaige.notification.application.usecase;

import br.com.triaige.notification.application.port.in.RetryConsumerPort;
import br.com.triaige.notification.application.port.out.AuditPort;
import br.com.triaige.notification.application.port.out.DeliveryRepositoryPort;
import br.com.triaige.notification.application.port.out.NotificationChannelPort;
import br.com.triaige.notification.application.port.out.NotificationRetrySchedulerPort;
import br.com.triaige.notification.application.port.out.OrchestratorSessionCallbackPort;
import br.com.triaige.notification.application.port.out.ReportContentPort;
import br.com.triaige.notification.domain.model.RetryTicket;

/**
 * Reprocessa um {@link RetryTicket} da {@code triaige-notification-retry}. Pode ser aqui que o último
 * destinatário pendente termina, então também verifica o desfecho da sessão.
 */
public class RetryCaseNotificationUseCase implements RetryConsumerPort {

    private final NotificationSender notificationSender;
    private final NotificationOutcomeReporter outcomeReporter;

    public RetryCaseNotificationUseCase(NotificationChannelPort emailChannel,
                                         ReportContentPort reportContentPort,
                                         DeliveryRepositoryPort deliveryRepository,
                                         AuditPort auditPort,
                                         NotificationRetrySchedulerPort retryScheduler,
                                         OrchestratorSessionCallbackPort orchestratorCallback,
                                         String dashboardBaseUrl) {
        this.notificationSender = new NotificationSender(reportContentPort, emailChannel, deliveryRepository,
                auditPort, retryScheduler, dashboardBaseUrl);
        this.outcomeReporter = new NotificationOutcomeReporter(deliveryRepository, orchestratorCallback);
    }

    @Override
    public void handleRetry(RetryTicket ticket) {
        notificationSender.fetchBuildAndSend(ticket.sessionId(), ticket.recipientId(), ticket.lawFirmId(),
                ticket.correlationId(), ticket.canal(), ticket.destino(), ticket.protocolo(), ticket.resultBucket(),
                ticket.summaryObjectKey(), ticket.attemptNumber(), null);
        outcomeReporter.reportIfSessionComplete(ticket.sessionId(), ticket.correlationId());
    }
}
