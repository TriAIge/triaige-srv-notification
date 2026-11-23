package br.com.triaige.notification.domain.model;

import java.util.Set;

/**
 * Códigos gravados em notification_deliveries.error_message. O port de canal não tem campo
 * "retryable": só estes códigos são retentáveis, e qualquer outro valor é falha definitiva.
 */
public final class DeliveryErrorCode {

    public static final String NO_EMAIL_AVAILABLE = "NO_EMAIL_AVAILABLE";
    public static final String SMTP_AUTH_ERROR = "SMTP_AUTH_ERROR";
    public static final String CHANNEL_TIMEOUT = "CHANNEL_TIMEOUT";
    public static final String CHANNEL_RATE_LIMITED = "CHANNEL_RATE_LIMITED";
    /** Falha ao ler o relatório no S3; costuma ser transitória. */
    public static final String REPORT_FETCH_ERROR = "REPORT_FETCH_ERROR";

    private static final Set<String> RETRYABLE = Set.of(CHANNEL_TIMEOUT, CHANNEL_RATE_LIMITED, REPORT_FETCH_ERROR);

    private DeliveryErrorCode() {
    }

    public static boolean isRetryable(String errorMessage) {
        return errorMessage != null && RETRYABLE.contains(errorMessage);
    }

    public static boolean isRateLimited(String errorMessage) {
        return CHANNEL_RATE_LIMITED.equals(errorMessage);
    }
}
