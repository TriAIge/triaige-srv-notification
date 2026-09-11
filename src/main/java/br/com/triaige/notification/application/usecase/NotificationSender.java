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
 * Colaborador interno compartilhado por {@link SendCaseNotificationUseCase} (tentativa inicial) e
 * {@link RetryCaseNotificationUseCase} (retries): busca o relatorio no S3, monta a mensagem e
 * envia pelo canal — sempre do zero, inclusive em retries, porque o relatorio e imutavel uma vez
 * escrito e refazer a busca e barato; evita carregar corpo/assunto pre-montados no
 * {@link br.com.triaige.notification.domain.model.RetryTicket}, que ficariam vazios justamente no
 * caso em que a falha original foi ao buscar o relatorio.
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
