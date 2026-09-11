package br.com.triaige.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "aws")
public class AwsProperties {

    private String region = "us-east-1";
    private Sqs sqs = new Sqs();

    @Getter
    @Setter
    public static class Sqs {

        /**
         * Nomes das filas (nao URLs) — a URL real e sempre resolvida em runtime direto na AWS via
         * {@code GetQueueUrl}, nunca hardcoded/configurada como endpoint (ver
         * {@code SqsQueueUrlResolver}). Isso elimina a necessidade de LocalStack ou qualquer
         * endpoint override para rodar contra a AWS de verdade.
         */
        private String resultsReadyQueueName = "triaige-results-ready";
        private String notificationRetryQueueName = "triaige-notification-retry";
        private String notificationRetryDlqName = "triaige-notification-retry-dlq";

        private Consumer resultsReadyConsumer = new Consumer();
        private Consumer retryConsumer = new Consumer();
    }

    @Getter
    @Setter
    public static class Consumer {
        private boolean enabled = true;
        private long pollIntervalMs = 5000;
        private int waitTimeSeconds = 10;
        private int maxMessages = 10;
    }
}
