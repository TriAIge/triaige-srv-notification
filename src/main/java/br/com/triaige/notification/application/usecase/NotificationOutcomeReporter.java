package br.com.triaige.notification.application.usecase;

import br.com.triaige.notification.application.port.out.DeliveryRepositoryPort;
import br.com.triaige.notification.application.port.out.OrchestratorSessionCallbackPort;
import br.com.triaige.notification.domain.model.DeliverySummary;
import br.com.triaige.notification.domain.model.NotificationOutcome;

import java.util.UUID;

/**
 * Depois de cada rodada (lote inicial ou retry), verifica se todos os destinatários chegaram a SENT ou
 * FAILED e, se sim, informa o resultado ao orchestrator uma única vez.
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
