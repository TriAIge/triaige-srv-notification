package br.com.triaige.notification.adapter.out.http;

import br.com.triaige.notification.application.port.out.OrchestratorSessionCallbackPort;
import br.com.triaige.notification.domain.model.NotificationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Callback síncrono ao Orchestrator: POST .../sessions/{sessionId}/notification-result com
 * X-Internal-Token, mesmo esquema usado pelo triaige-srv-mcp-ai (OrchestratorCallbackClient).
 * 1 retry com backoff fixo, best-effort: se ambas as tentativas falharem, apenas loga — não
 * existe fila de fallback aqui (gap aceito, mesmo padrão de TriggerAiAnalysisUseCase.publishResultsReady
 * no Orchestrator: o resultado da notificação em si já foi persistido/entregue, só o espelho de
 * status na sessão fica desatualizado).
 */
@Component
public class OrchestratorCallbackHttpAdapter implements OrchestratorSessionCallbackPort {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorCallbackHttpAdapter.class);
    private static final long RETRY_DELAY_MS = 3000;

    private final RestClient orchestratorRestClient;
    private final String resultPathTemplate;
    private final String internalToken;

    public OrchestratorCallbackHttpAdapter(RestClient orchestratorRestClient,
                                            @Value("${notification.orchestrator-callback.result-path-template}") String resultPathTemplate,
                                            @Value("${notification.orchestrator-callback.internal-token}") String internalToken) {
        this.orchestratorRestClient = orchestratorRestClient;
        this.resultPathTemplate = resultPathTemplate;
        this.internalToken = internalToken;
    }

    @Override
    public void reportOutcome(UUID sessionId, UUID correlationId, NotificationOutcome outcome,
                               int recipientsNotified, int recipientsFailed) {
        NotificationResultPayload payload = new NotificationResultPayload(sessionId, correlationId,
                outcome.name(), recipientsNotified, recipientsFailed);
        String path = resultPathTemplate.replace("{sessionId}", sessionId.toString());

        try {
            send(path, payload);
        } catch (Exception first) {
            log.warn("Orchestrator notification-result callback failed, retrying once: sessionId={}, error={}",
                    sessionId, first.getMessage());
            sleep(RETRY_DELAY_MS);
            try {
                send(path, payload);
            } catch (Exception second) {
                log.error("Orchestrator notification-result callback exhausted retries: sessionId={}, outcome={}",
                        sessionId, outcome, second);
                return;
            }
        }
        log.info("Orchestrator notification-result callback delivered: sessionId={}, outcome={}", sessionId, outcome);
    }

    private void send(String path, NotificationResultPayload payload) {
        orchestratorRestClient.post()
                .uri(path)
                .header("X-Internal-Token", internalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
