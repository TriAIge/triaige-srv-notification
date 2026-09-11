package br.com.triaige.notification.config;

import br.com.triaige.notification.application.port.out.AuditPort;
import br.com.triaige.notification.application.port.out.DeliveryRepositoryPort;
import br.com.triaige.notification.application.port.out.NotificationChannelPort;
import br.com.triaige.notification.application.port.out.NotificationRetrySchedulerPort;
import br.com.triaige.notification.application.port.out.OrchestratorSessionCallbackPort;
import br.com.triaige.notification.application.port.out.RecipientRepositoryPort;
import br.com.triaige.notification.application.port.out.ReportContentPort;
import br.com.triaige.notification.application.usecase.RetryCaseNotificationUseCase;
import br.com.triaige.notification.application.usecase.SendCaseNotificationUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fronteira de wiring: os casos de uso sao classes Java puras (sem anotacoes Spring),
 * construidas aqui com os adapters concretos injetados apenas pelas interfaces de port.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public SendCaseNotificationUseCase sendCaseNotificationUseCase(
            RecipientRepositoryPort recipientRepositoryPort,
            DeliveryRepositoryPort deliveryRepositoryPort,
            NotificationChannelPort notificationChannelPort,
            ReportContentPort reportContentPort,
            AuditPort auditPort,
            NotificationRetrySchedulerPort retrySchedulerPort,
            OrchestratorSessionCallbackPort orchestratorSessionCallbackPort,
            @Value("${notification.dashboard-base-url}") String dashboardBaseUrl) {
        return new SendCaseNotificationUseCase(recipientRepositoryPort, deliveryRepositoryPort,
                notificationChannelPort, reportContentPort, auditPort, retrySchedulerPort,
                orchestratorSessionCallbackPort, dashboardBaseUrl);
    }

    @Bean
    public RetryCaseNotificationUseCase retryCaseNotificationUseCase(
            NotificationChannelPort notificationChannelPort,
            ReportContentPort reportContentPort,
            DeliveryRepositoryPort deliveryRepositoryPort,
            AuditPort auditPort,
            NotificationRetrySchedulerPort retrySchedulerPort,
            OrchestratorSessionCallbackPort orchestratorSessionCallbackPort,
            @Value("${notification.dashboard-base-url}") String dashboardBaseUrl) {
        return new RetryCaseNotificationUseCase(notificationChannelPort, reportContentPort, deliveryRepositoryPort,
                auditPort, retrySchedulerPort, orchestratorSessionCallbackPort, dashboardBaseUrl);
    }
}
