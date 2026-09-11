package br.com.triaige.notification.application.usecase;

import br.com.triaige.notification.application.InMemoryDeliveryRepository;
import br.com.triaige.notification.application.RecordingAuditPort;
import br.com.triaige.notification.application.RecordingOrchestratorCallback;
import br.com.triaige.notification.application.RecordingRetryScheduler;
import br.com.triaige.notification.application.ScriptedNotificationChannel;
import br.com.triaige.notification.application.StubReportContentPort;
import br.com.triaige.notification.application.port.out.RecipientRepositoryPort;
import br.com.triaige.notification.domain.model.Channel;
import br.com.triaige.notification.domain.model.DeliveryErrorCode;
import br.com.triaige.notification.domain.model.DeliveryResult;
import br.com.triaige.notification.domain.model.NotificationAuditEventType;
import br.com.triaige.notification.domain.model.Recipient;
import br.com.triaige.notification.domain.model.ResultsReadyEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a matriz de roteamento e a idempotencia por claim, usando apenas
 * fakes das ports — sem rede, banco ou SMTP real (criterio de aceite).
 */
class SendCaseNotificationUseCaseTest {

    private static final String DASHBOARD_URL = "http://localhost:3000";
    private static final String RESULT_BUCKET = "bucket-triaige-curated";
    private static final String SUMMARY_KEY = "lawfirm/session/relatorio.md";

    @Test
    void canalPreferencialEmailComEmail_enviaComRelatorioNoCorpo() {
        UUID sessionId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Recipient recipient = new Recipient(recipientId, sessionId, "Fulano", "fulano@example.com", null, Channel.EMAIL);

        InMemoryDeliveryRepository deliveryRepository = new InMemoryDeliveryRepository();
        ScriptedNotificationChannel channel = new ScriptedNotificationChannel()
                .willReturn(DeliveryResult.success("msg-1"));
        RecordingAuditPort auditPort = new RecordingAuditPort();
        RecordingRetryScheduler retryScheduler = new RecordingRetryScheduler();
        StubReportContentPort reportContentPort = StubReportContentPort.returning("# Relatorio\n\nSintese dos fatos.");

        SendCaseNotificationUseCase useCase = new SendCaseNotificationUseCase(
                fixedRecipients(recipient), deliveryRepository, channel, reportContentPort, auditPort,
                retryScheduler, new RecordingOrchestratorCallback(), DASHBOARD_URL);

        useCase.handleResultsReady(event(sessionId));

        InMemoryDeliveryRepository.Record record = deliveryRepository.get(sessionId, recipientId, Channel.EMAIL);
        assertThat(record.status()).isEqualTo("SENT");
        assertThat(record.errorMessage()).isNull();
        assertThat(channel.sentMessages).hasSize(1);
        assertThat(channel.sentMessages.get(0).destino()).isEqualTo("fulano@example.com");
        assertThat(channel.sentMessages.get(0).corpo()).contains("Sintese dos fatos.");
        assertThat(reportContentPort.calls).containsExactly(new StubReportContentPort.Call(RESULT_BUCKET, SUMMARY_KEY));
        assertThat(auditPort.events).anyMatch(e -> e.eventType().equals(NotificationAuditEventType.NOTIFICATION_SENT));
    }

    @Test
    void canalPreferencialDiferenteDeEmailComEmail_envioPorEmailComNotaDeDivergencia() {
        UUID sessionId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Recipient recipient = new Recipient(recipientId, sessionId, "Fulano", "fulano@example.com", "11999999999", Channel.WHATSAPP);

        InMemoryDeliveryRepository deliveryRepository = new InMemoryDeliveryRepository();
        ScriptedNotificationChannel channel = new ScriptedNotificationChannel()
                .willReturn(DeliveryResult.success("msg-1"));

        SendCaseNotificationUseCase useCase = new SendCaseNotificationUseCase(
                fixedRecipients(recipient), deliveryRepository, channel, StubReportContentPort.returning("conteudo"),
                new RecordingAuditPort(), new RecordingRetryScheduler(), new RecordingOrchestratorCallback(), DASHBOARD_URL);

        useCase.handleResultsReady(event(sessionId));

        InMemoryDeliveryRepository.Record record = deliveryRepository.get(sessionId, recipientId, Channel.EMAIL);
        assertThat(record.status()).isEqualTo("SENT");
        assertThat(record.errorMessage()).contains("canal preferencial era WHATSAPP");
    }

    @Test
    void semEmail_falhaDireto_semTentativaDeEnvio() {
        UUID sessionId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Recipient recipient = new Recipient(recipientId, sessionId, "Fulano", null, "11999999999", Channel.WHATSAPP);

        InMemoryDeliveryRepository deliveryRepository = new InMemoryDeliveryRepository();
        ScriptedNotificationChannel channel = new ScriptedNotificationChannel();
        RecordingAuditPort auditPort = new RecordingAuditPort();
        StubReportContentPort reportContentPort = StubReportContentPort.returning("conteudo");

        SendCaseNotificationUseCase useCase = new SendCaseNotificationUseCase(
                fixedRecipients(recipient), deliveryRepository, channel, reportContentPort, auditPort,
                new RecordingRetryScheduler(), new RecordingOrchestratorCallback(), DASHBOARD_URL);

        useCase.handleResultsReady(event(sessionId));

        InMemoryDeliveryRepository.Record record = deliveryRepository.get(sessionId, recipientId, Channel.EMAIL);
        assertThat(record.status()).isEqualTo("FAILED");
        assertThat(record.errorMessage()).isEqualTo(DeliveryErrorCode.NO_EMAIL_AVAILABLE);
        assertThat(channel.sentMessages).isEmpty();
        assertThat(reportContentPort.calls).isEmpty();
        assertThat(auditPort.events).anyMatch(e -> e.eventType().equals(NotificationAuditEventType.NOTIFICATION_FAILED));
    }

    @Test
    void entregaJaClaimedPorOutraInstancia_naoReenvia() {
        UUID sessionId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Recipient recipient = new Recipient(recipientId, sessionId, "Fulano", "fulano@example.com", null, Channel.EMAIL);

        InMemoryDeliveryRepository deliveryRepository = new InMemoryDeliveryRepository();
        // Simula outra instancia que ja fez o claim primeiro.
        deliveryRepository.tryClaim(sessionId, recipientId, Channel.EMAIL, "fulano@example.com");

        ScriptedNotificationChannel channel = new ScriptedNotificationChannel();
        StubReportContentPort reportContentPort = StubReportContentPort.returning("conteudo");

        SendCaseNotificationUseCase useCase = new SendCaseNotificationUseCase(
                fixedRecipients(recipient), deliveryRepository, channel, reportContentPort, new RecordingAuditPort(),
                new RecordingRetryScheduler(), new RecordingOrchestratorCallback(), DASHBOARD_URL);

        useCase.handleResultsReady(event(sessionId));

        assertThat(channel.sentMessages).isEmpty();
        assertThat(reportContentPort.calls).isEmpty();
    }

    @Test
    void falhaRetentavel_agendaRetryEnaoMarcaFailedAinda() {
        UUID sessionId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Recipient recipient = new Recipient(recipientId, sessionId, "Fulano", "fulano@example.com", null, Channel.EMAIL);

        InMemoryDeliveryRepository deliveryRepository = new InMemoryDeliveryRepository();
        ScriptedNotificationChannel channel = new ScriptedNotificationChannel()
                .willReturn(DeliveryResult.failure(DeliveryErrorCode.CHANNEL_TIMEOUT));
        RecordingRetryScheduler retryScheduler = new RecordingRetryScheduler();

        SendCaseNotificationUseCase useCase = new SendCaseNotificationUseCase(
                fixedRecipients(recipient), deliveryRepository, channel, StubReportContentPort.returning("conteudo"),
                new RecordingAuditPort(), retryScheduler, new RecordingOrchestratorCallback(), DASHBOARD_URL);

        useCase.handleResultsReady(event(sessionId));

        InMemoryDeliveryRepository.Record record = deliveryRepository.get(sessionId, recipientId, Channel.EMAIL);
        assertThat(record.status()).isEqualTo("RETRY_SCHEDULED");
        assertThat(retryScheduler.scheduled).hasSize(1);
        assertThat(retryScheduler.scheduled.get(0).attemptNumber()).isEqualTo(1);
        assertThat(retryScheduler.scheduled.get(0).resultBucket()).isEqualTo(RESULT_BUCKET);
        assertThat(retryScheduler.scheduled.get(0).summaryObjectKey()).isEqualTo(SUMMARY_KEY);
        assertThat(retryScheduler.deadLettered).isEmpty();
    }

    @Test
    void falhaNaoRetentavel_marcaFailedSemAgendarRetry() {
        UUID sessionId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Recipient recipient = new Recipient(recipientId, sessionId, "Fulano", "fulano@example.com", null, Channel.EMAIL);

        InMemoryDeliveryRepository deliveryRepository = new InMemoryDeliveryRepository();
        ScriptedNotificationChannel channel = new ScriptedNotificationChannel()
                .willReturn(DeliveryResult.failure("550 5.1.1 user unknown"));
        RecordingRetryScheduler retryScheduler = new RecordingRetryScheduler();

        SendCaseNotificationUseCase useCase = new SendCaseNotificationUseCase(
                fixedRecipients(recipient), deliveryRepository, channel, StubReportContentPort.returning("conteudo"),
                new RecordingAuditPort(), retryScheduler, new RecordingOrchestratorCallback(), DASHBOARD_URL);

        useCase.handleResultsReady(event(sessionId));

        InMemoryDeliveryRepository.Record record = deliveryRepository.get(sessionId, recipientId, Channel.EMAIL);
        assertThat(record.status()).isEqualTo("FAILED");
        assertThat(record.errorMessage()).isEqualTo("550 5.1.1 user unknown");
        assertThat(retryScheduler.scheduled).isEmpty();
    }

    @Test
    void falhaAoBuscarRelatorioNoS3_naoTentaEnviarEAgendaRetry() {
        UUID sessionId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Recipient recipient = new Recipient(recipientId, sessionId, "Fulano", "fulano@example.com", null, Channel.EMAIL);

        InMemoryDeliveryRepository deliveryRepository = new InMemoryDeliveryRepository();
        ScriptedNotificationChannel channel = new ScriptedNotificationChannel();
        RecordingRetryScheduler retryScheduler = new RecordingRetryScheduler();

        SendCaseNotificationUseCase useCase = new SendCaseNotificationUseCase(
                fixedRecipients(recipient), deliveryRepository, channel, StubReportContentPort.failing(),
                new RecordingAuditPort(), retryScheduler, new RecordingOrchestratorCallback(), DASHBOARD_URL);

        useCase.handleResultsReady(event(sessionId));

        InMemoryDeliveryRepository.Record record = deliveryRepository.get(sessionId, recipientId, Channel.EMAIL);
        assertThat(record.status()).isEqualTo("RETRY_SCHEDULED");
        assertThat(record.errorMessage()).isEqualTo(DeliveryErrorCode.REPORT_FETCH_ERROR);
        assertThat(channel.sentMessages).isEmpty();
        assertThat(retryScheduler.scheduled).hasSize(1);
    }

    private RecipientRepositoryPort fixedRecipients(Recipient... recipients) {
        List<Recipient> list = List.of(recipients);
        return sessionId -> list;
    }

    private ResultsReadyEvent event(UUID sessionId) {
        return new ResultsReadyEvent(sessionId, UUID.randomUUID(), "PROTO-0001", UUID.randomUUID(),
                RESULT_BUCKET, SUMMARY_KEY);
    }
}
