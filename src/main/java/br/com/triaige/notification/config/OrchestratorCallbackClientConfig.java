package br.com.triaige.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Client de saída triaige-srv-notification -> Orchestrator (callback .../notification-result). */
@Configuration
public class OrchestratorCallbackClientConfig {

    @Bean
    public RestClient orchestratorRestClient(
            @Value("${notification.orchestrator-callback.base-url}") String baseUrl,
            @Value("${notification.orchestrator-callback.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${notification.orchestrator-callback.read-timeout-ms}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
