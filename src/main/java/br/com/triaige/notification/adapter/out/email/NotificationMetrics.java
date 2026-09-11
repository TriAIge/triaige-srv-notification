package br.com.triaige.notification.adapter.out.email;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Metricas (namespace CloudWatch {@code Triaige/Notification}, configurado em
 * {@code application.yml} via {@code management.cloudwatch}).
 */
@Component
public class NotificationMetrics {

    private final MeterRegistry registry;

    public NotificationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordSent(long latencyMs) {
        Counter.builder("NotificationSentCount").register(registry).increment();
        recordLatency(latencyMs);
    }

    public void recordFailure(String errorMessage, long latencyMs) {
        Counter.builder("NotificationFailureCount")
                .tag("errorMessage", errorMessage == null ? "UNKNOWN" : errorMessage)
                .register(registry)
                .increment();
        recordLatency(latencyMs);
    }

    public void recordSmtpAuthError() {
        Counter.builder("SmtpAuthErrorCount").register(registry).increment();
    }

    private void recordLatency(long latencyMs) {
        Timer.builder("NotificationLatencyMs").register(registry)
                .record(latencyMs, TimeUnit.MILLISECONDS);
    }
}
