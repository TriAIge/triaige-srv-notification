package br.com.triaige.notification.application.usecase;

import br.com.triaige.notification.application.port.out.DeliveryRepositoryPort;
import br.com.triaige.notification.application.port.out.OrchestratorSessionCallbackPort;
import br.com.triaige.notification.domain.model.DeliverySummary;
import br.com.triaige.notification.domain.model.NotificationOutcome;

import java.util.UUID;

/**
 * Colaborador interno (nao e port) compartilhado por {@link SendCaseNotificationUseCase} e
 * {@link RetryCaseNotificationUseCase}: verifica, apos cada rodada de processamento de uma sessao
 * (lote inicial do Q3, ou um ticket de retry), se todos os destinatarios ja chegaram a um desfecho
 * terminal (SENT/FAILED, nenhum PENDING/RETRY_SCHEDULED restante) e, se sim, reporta o resultado
 * agregado ao Orchestrator exatamente uma vez.
 */
final class NotificationOutcomeReporter {

    private final DeliveryRepositoryPort deliveryRepository;
    private final OrchestratorSessionCallbackPort orchestratorCallback;

    NotificationOutcomeReporter(DeliveryRepositoryPort deliveryRepository,
                                 OrchestratorSessionCallbackPort orchestratorCallback) {
        this.deliveryRepository = deliveryRepository;
        this.orchestratorCallback = orchestratorCallback;
    }

    void reportIfSessionComplete(UUID sessionId, UUID correlationId) {
        DeliverySummary summary = deliveryRepository.summarize(sessionId);
        if (!summary.allTerminal()) {
            return;
        }
        NotificationOutcome outcome = summary.sent() > 0 ? NotificationOutcome.NOTIFIED : NotificationOutcome.NOTIFICATION_FAILED;
        orchestratorCallback.reportOutcome(sessionId, correlationId, outcome, summary.sent(), summary.failed());
    }
}
