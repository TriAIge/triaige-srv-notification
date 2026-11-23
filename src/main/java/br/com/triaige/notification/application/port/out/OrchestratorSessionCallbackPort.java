package br.com.triaige.notification.application.port.out;

import br.com.triaige.notification.domain.model.NotificationOutcome;

import java.util.UUID;

/**
 * Informa ao orchestrator o desfecho da notificação da sessão (.../notification-result). Melhor
 * esforço: a falha é registrada no log e não desfaz o processamento já concluído.
 */
public interface OrchestratorSessionCallbackPort {

    void reportOutcome(UUID sessionId, UUID correlationId, NotificationOutcome outcome,
                        int recipientsNotified, int recipientsFailed);
}
