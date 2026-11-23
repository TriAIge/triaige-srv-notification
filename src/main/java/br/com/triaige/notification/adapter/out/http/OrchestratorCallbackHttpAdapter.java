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
 * Callback ao orchestrator com X-Internal-Token e um retry com espera fixa. Se as duas tentativas
 * falharem, só registra no log: o e-mail já foi tratado, só o status da sessão fica desatualizado.
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
            log.warn("Falha no callback notification-result ao orchestrator, tentando mais uma vez: sessionId={}, error={}",
                    sessionId, first.getMessage());
            sleep(RETRY_DELAY_MS);
            try {
                send(path, payload);
            } catch (Exception second) {
                log.error("Callback notification-result ao orchestrator esgotou as tentativas: sessionId={}, outcome={}",
                        sessionId, outcome, second);
                return;
            }
        }
        log.info("Callback notification-result entregue ao orchestrator: sessionId={}, outcome={}", sessionId, outcome);
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
