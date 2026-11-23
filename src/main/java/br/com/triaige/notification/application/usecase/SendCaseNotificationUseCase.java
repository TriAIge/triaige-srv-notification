package br.com.triaige.notification.application.usecase;

import br.com.triaige.notification.application.port.in.EventConsumerPort;
import br.com.triaige.notification.application.port.out.AuditPort;
import br.com.triaige.notification.application.port.out.DeliveryRepositoryPort;
import br.com.triaige.notification.application.port.out.NotificationChannelPort;
import br.com.triaige.notification.application.port.out.NotificationRetrySchedulerPort;
import br.com.triaige.notification.application.port.out.OrchestratorSessionCallbackPort;
import br.com.triaige.notification.application.port.out.RecipientRepositoryPort;
import br.com.triaige.notification.application.port.out.ReportContentPort;
import br.com.triaige.notification.domain.model.Channel;
import br.com.triaige.notification.domain.model.DeliveryErrorCode;
import br.com.triaige.notification.domain.model.NotificationAuditEventType;
import br.com.triaige.notification.domain.model.NotificationOutcome;
import br.com.triaige.notification.domain.model.Recipient;
import br.com.triaige.notification.domain.model.ResultsReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Processa a mensagem da {@code triaige-results-ready} para cada destinatário ativo. A regra de canal
 * fica aqui, fora do adapter, para valer também quando houver WhatsApp ou SMS.
 */
public class SendCaseNotificationUseCase implements EventConsumerPort {

    private static final Logger log = LoggerFactory.getLogger(SendCaseNotificationUseCase.class);

    private final RecipientRepositoryPort recipientRepository;
    private final DeliveryRepositoryPort deliveryRepository;
    private final AuditPort auditPort;
    private final OrchestratorSessionCallbackPort orchestratorCallback;
    private final NotificationSender notificationSender;
    private final NotificationOutcomeReporter outcomeReporter;

    public SendCaseNotificationUseCase(RecipientRepositoryPort recipientRepository,
                                        DeliveryRepositoryPort deliveryRepository,
                                        NotificationChannelPort emailChannel,
                                        ReportContentPort reportContentPort,
                                        AuditPort auditPort,
                                        NotificationRetrySchedulerPort retryScheduler,
                                        OrchestratorSessionCallbackPort orchestratorCallback,
                                        String dashboardBaseUrl) {
        this.recipientRepository = recipientRepository;
        this.deliveryRepository = deliveryRepository;
        this.auditPort = auditPort;
        this.orchestratorCallback = orchestratorCallback;
        this.notificationSender = new NotificationSender(reportContentPort, emailChannel, deliveryRepository,
                auditPort, retryScheduler, dashboardBaseUrl);
        this.outcomeReporter = new NotificationOutcomeReporter(deliveryRepository, orchestratorCallback);
    }

    @Override
    public void handleResultsReady(ResultsReadyEvent event) {
        List<Recipient> recipients = recipientRepository.findActiveBySessionId(event.sessionId());

        if (recipients.isEmpty()) {
            log.warn("Nenhum destinatário ativo para sessionId={}, protocolo={}", event.sessionId(), event.protocolo());
            orchestratorCallback.reportOutcome(event.sessionId(), event.correlationId(),
                    NotificationOutcome.NOTIFICATION_FAILED, 0, 0);
            auditPort.recordDispatchStep(event.sessionId(), true, null);
            return;
        }

        for (Recipient recipient : recipients) {
            processRecipient(event, recipient);
        }

        auditPort.recordDispatchStep(event.sessionId(), true, null);
        outcomeReporter.reportIfSessionComplete(event.sessionId(), event.correlationId());
    }

    private void processRecipient(ResultsReadyEvent event, Recipient recipient) {
        if (!recipient.hasEmail()) {
            // Sem e-mail: FAILED direto, sem tentativa de envio.
            deliveryRepository.markFailed(event.sessionId(), recipient.id(), Channel.EMAIL, "",
                    DeliveryErrorCode.NO_EMAIL_AVAILABLE);
            auditPort.recordEvent(NotificationAuditEventType.NOTIFICATION_FAILED,
                    event.sessionId(), event.lawFirmId(), event.correlationId(),
                    "Destinatário " + recipient.id() + " sem e-mail cadastrado");
            return;
        }

        boolean claimed = deliveryRepository.tryClaim(event.sessionId(), recipient.id(), Channel.EMAIL, recipient.email());
        if (!claimed) {
            log.info("Entrega já existente para sessionId={}, recipientId={}, canal=EMAIL - ignorando (idempotência)",
                    event.sessionId(), recipient.id());
            return;
        }

        // Canal preferencial diferente de EMAIL, mas com e-mail: envia por e-mail e registra a
        // divergência como observação.
        String divergenceNote = recipient.canalPreferencial() != null && recipient.canalPreferencial() != Channel.EMAIL
                ? "canal preferencial era " + recipient.canalPreferencial() + ", apenas EMAIL suportado nesta fase"
                : null;

        notificationSender.fetchBuildAndSend(event.sessionId(), recipient.id(), event.lawFirmId(),
                event.correlationId(), Channel.EMAIL, recipient.email(), event.protocolo(), event.resultBucket(),
                event.summaryObjectKey(), 0, divergenceNote);
    }
}
