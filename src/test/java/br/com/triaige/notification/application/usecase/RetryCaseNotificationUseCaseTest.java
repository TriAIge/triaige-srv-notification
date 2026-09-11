package br.com.triaige.notification.application.usecase;

import br.com.triaige.notification.application.InMemoryDeliveryRepository;
import br.com.triaige.notification.application.RecordingAuditPort;
import br.com.triaige.notification.application.RecordingOrchestratorCallback;
import br.com.triaige.notification.application.RecordingRetryScheduler;
import br.com.triaige.notification.application.ScriptedNotificationChannel;
import br.com.triaige.notification.application.StubReportContentPort;
import br.com.triaige.notification.domain.model.Channel;
import br.com.triaige.notification.domain.model.DeliveryErrorCode;
import br.com.triaige.notification.domain.model.DeliveryResult;
import br.com.triaige.notification.domain.model.RetryTicket;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** maxReceiveCount=3 - a 4a tentativa total que ainda falhar de forma retentavel vai para a DLQ. */
class RetryCaseNotificationUseCaseTest {

    private static final String DASHBOARD_URL = "http://localhost:3000";

    @Test
    void sucessoNoRetry_marcaSentEBuscaRelatorioDeNovo() {
        UUID sessionId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        InMemoryDeliveryRepository deliveryRepository = new InMemoryDeliveryRepository();
        deliveryRepository.tryClaim(sessionId, recipientId, Channel.EMAIL, "fulano@example.com");
        ScriptedNotificationChannel channel = new ScriptedNotificationChannel().willReturn(DeliveryResult.success("msg-1"));
        StubReportContentPort reportContentPort = StubReportContentPort.returning("# Relatorio");

        RetryCaseNotificationUseCase useCase = new RetryCaseNotificationUseCase(
                channel, reportContentPort, deliveryRepository, new RecordingAuditPort(),
                new RecordingRetryScheduler(), new RecordingOrchestratorCallback(), DASHBOARD_URL);

        useCase.handleRetry(ticket(sessionId, recipientId, 1));

        assertThat(deliveryRepository.get(sessionId, recipientId, Channel.EMAIL).status()).isEqualTo("SENT");
        assertThat(reportContentPort.calls).hasSize(1);
        assertThat(channel.sentMessages.get(0).corpo()).contains("Relatorio");
    }

    @Test
    void falhaRetentavelAbaixoDoLimite_agendaProximaTentativa() {
        UUID sessionId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        InMemoryDeliveryRepository deliveryRepository = new InMemoryDeliveryRepository();
        deliveryRepository.tryClaim(sessionId, recipientId, Channel.EMAIL, "fulano@example.com");
        ScriptedNotificationChannel channel = new ScriptedNotificationChannel()
                .willReturn(DeliveryResult.failure(DeliveryErrorCode.CHANNEL_TIMEOUT));
        RecordingRetryScheduler retryScheduler = new RecordingRetryScheduler();

        RetryCaseNotificationUseCase useCase = new RetryCaseNotificationUseCase(
                channel, StubReportContentPort.returning("conteudo"), deliveryRepository, new RecordingAuditPort(),
                retryScheduler, new RecordingOrchestratorCallback(), DASHBOARD_URL);

        useCase.handleRetry(ticket(sessionId, recipientId, 2));

        assertThat(retryScheduler.scheduled).hasSize(1);
        assertThat(retryScheduler.scheduled.get(0).attemptNumber()).isEqualTo(3);
        assertThat(retryScheduler.deadLettered).isEmpty();
    }

    @Test
    void falhaRetentavelNaQuartaTentativaTotal_esgotaEVaiParaDlq() {
        UUID sessionId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        InMemoryDeliveryRepository deliveryRepository = new InMemoryDeliveryRepository();
        deliveryRepository.tryClaim(sessionId, recipientId, Channel.EMAIL, "fulano@example.com");
        ScriptedNotificationChannel channel = new ScriptedNotificationChannel()
                .willReturn(DeliveryResult.failure(DeliveryErrorCode.CHANNEL_TIMEOUT));
        RecordingRetryScheduler retryScheduler = new RecordingRetryScheduler();

        RetryCaseNotificationUseCase useCase = new RetryCaseNotificationUseCase(
                channel, StubReportContentPort.returning("conteudo"), deliveryRepository, new RecordingAuditPort(),
                retryScheduler, new RecordingOrchestratorCallback(), DASHBOARD_URL);

        // attemptNumber=3 e o ultimo ticket de retry (3a tentativa de retry = 4a tentativa total).
        useCase.handleRetry(ticket(sessionId, recipientId, 3));

        assertThat(deliveryRepository.get(sessionId, recipientId, Channel.EMAIL).status()).isEqualTo("FAILED");
        assertThat(retryScheduler.scheduled).isEmpty();
        assertThat(retryScheduler.deadLettered).hasSize(1);
    }

    @Test
    void falhaNaoRetentavelDuranteRetry_marcaFailedSemDlq() {
        UUID sessionId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        InMemoryDeliveryRepository deliveryRepository = new InMemoryDeliveryRepository();
        deliveryRepository.tryClaim(sessionId, recipientId, Channel.EMAIL, "fulano@example.com");
        ScriptedNotificationChannel channel = new ScriptedNotificationChannel()
                .willReturn(DeliveryResult.failure("550 5.1.1 user unknown"));
        RecordingRetryScheduler retryScheduler = new RecordingRetryScheduler();

        RetryCaseNotificationUseCase useCase = new RetryCaseNotificationUseCase(
                channel, StubReportContentPort.returning("conteudo"), deliveryRepository, new RecordingAuditPort(),
                retryScheduler, new RecordingOrchestratorCallback(), DASHBOARD_URL);

        useCase.handleRetry(ticket(sessionId, recipientId, 1));

        assertThat(deliveryRepository.get(sessionId, recipientId, Channel.EMAIL).status()).isEqualTo("FAILED");
        assertThat(retryScheduler.deadLettered).isEmpty();
        assertThat(retryScheduler.scheduled).isEmpty();
    }

    @Test
    void falhaAoBuscarRelatorioDeNovoNoRetry_reagendaComoRetentavel() {
        UUID sessionId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        InMemoryDeliveryRepository deliveryRepository = new InMemoryDeliveryRepository();
        deliveryRepository.tryClaim(sessionId, recipientId, Channel.EMAIL, "fulano@example.com");
        ScriptedNotificationChannel channel = new ScriptedNotificationChannel();
        RecordingRetryScheduler retryScheduler = new RecordingRetryScheduler();

        RetryCaseNotificationUseCase useCase = new RetryCaseNotificationUseCase(
                channel, StubReportContentPort.failing(), deliveryRepository, new RecordingAuditPort(),
                retryScheduler, new RecordingOrchestratorCallback(), DASHBOARD_URL);

        useCase.handleRetry(ticket(sessionId, recipientId, 1));

        assertThat(channel.sentMessages).isEmpty();
        assertThat(retryScheduler.scheduled).hasSize(1);
        assertThat(retryScheduler.scheduled.get(0).attemptNumber()).isEqualTo(2);
    }

    private RetryTicket ticket(UUID sessionId, UUID recipientId, int attemptNumber) {
        return new RetryTicket(sessionId, recipientId, UUID.randomUUID(), Channel.EMAIL, "fulano@example.com",
                "PROTO-0001", "bucket-triaige-curated", "lawfirm/session/relatorio.md", UUID.randomUUID(),
                attemptNumber, "erro anterior");
    }
}
