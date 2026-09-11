package br.com.triaige.notification.application.port.out;

import br.com.triaige.notification.domain.model.NotificationOutcome;

import java.util.UUID;

/**
 * Port de saída: reporta ao triaige-srv-orchestrator o desfecho agregado de notificar uma sessão
 * (POST .../sessions/{sessionId}/notification-result), fechando o gap de a sessão nunca refletir
 * se a notificação foi enviada ou falhou. Melhor esforço — falha aqui é logada, não propagada
 * (não deve reverter nem travar o processamento de Q3/retry já concluído).
 */
public interface OrchestratorSessionCallbackPort {

    void reportOutcome(UUID sessionId, UUID correlationId, NotificationOutcome outcome,
                        int recipientsNotified, int recipientsFailed);
}
