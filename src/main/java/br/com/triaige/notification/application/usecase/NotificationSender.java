package br.com.triaige.notification.application.usecase;

import br.com.triaige.notification.application.port.out.AuditPort;
import br.com.triaige.notification.application.port.out.DeliveryRepositoryPort;
import br.com.triaige.notification.application.port.out.NotificationChannelPort;
import br.com.triaige.notification.application.port.out.NotificationRetrySchedulerPort;
import br.com.triaige.notification.application.port.out.ReportContentPort;
import br.com.triaige.notification.domain.model.Channel;
import br.com.triaige.notification.domain.model.DeliveryErrorCode;
import br.com.triaige.notification.domain.model.DeliveryResult;
import br.com.triaige.notification.domain.model.NotificationMessage;
import br.com.triaige.notification.domain.service.NotificationContentFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * Busca o relatório, monta a mensagem e envia, sempre do zero, inclusive nos retries: o relatório não
 * muda depois de gravado, e o ticket não fica sem corpo quando a falha foi justamente na leitura.
 */
final class NotificationSender {

    private final ReportContentPort reportContentPort;
    private final NotificationChannelPort channelPort;
    private final DeliveryOutcomeHandler outcomeHandler;
    private final String dashboardBaseUrl;

    NotificationSender(ReportContentPort reportContentPort, NotificationChannelPort channelPort,
                        DeliveryRepositoryPort deliveryRepository, AuditPort auditPort,
                        NotificationRetrySchedulerPort retryScheduler, String dashboardBaseUrl) {
        this.reportContentPort = reportContentPort;
        this.channelPort = channelPort;
        this.outcomeHandler = new DeliveryOutcomeHandler(deliveryRepository, auditPort, retryScheduler);
        this.dashboardBaseUrl = dashboardBaseUrl;
    }

    void fetchBuildAndSend(UUID sessionId, UUID recipientId, UUID lawFirmId, UUID correlationId, Channel canal,
                            String destino, String protocolo, String resultBucket, String summaryObjectKey,
                            int attemptsAlreadyMade, String successNote) {

        Optional<String> reportMarkdown = reportContentPort.fetchMarkdown(resultBucket, summaryObjectKey);
        if (reportMarkdown.isEmpty()) {
            DeliveryResult fetchFailure = DeliveryResult.failure(DeliveryErrorCode.REPORT_FETCH_ERROR);
            outcomeHandler.apply(fetchFailure, sessionId, lawFirmId, correlationId, recipientId, canal, destino,
                    protocolo, resultBucket, summaryObjectKey, attemptsAlreadyMade, successNote);
            return;
        }

        String assunto = NotificationContentFactory.buildSubject(protocolo);
        String corpo = NotificationContentFactory.buildBody(protocolo, dashboardBaseUrl, reportMarkdown.get());
        NotificationMessage message = new NotificationMessage(destino, assunto, corpo,
                new NotificationMessage.Metadata(sessionId, protocolo, correlationId));

        DeliveryResult result = channelPort.send(message);

        outcomeHandler.apply(result, sessionId, lawFirmId, correlationId, recipientId, canal, destino,
                protocolo, resultBucket, summaryObjectKey, attemptsAlreadyMade, successNote);
    }
}
