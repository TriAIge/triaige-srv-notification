package br.com.triaige.notification.domain.model;

import java.util.Set;

/**
 * Codigos de erro estaveis trocados entre adapters de canal e a camada application via
 * {@link DeliveryResult#errorMessage()}, e gravados em {@code notification_deliveries.error_message}.
 *
 * <p><b>Suposicao registrada:</b> o contrato de
 * {@code NotificationChannelPort.send(...)} nao tem um campo "retryable" — para a regra de retry
 * funcionar sem a camada application conhecer detalhes de SMTP, qualquer adapter de
 * canal (e-mail hoje, WhatsApp/SMS no futuro) deve mapear falhas retentaveis para um destes codigos
 * fixos em {@code errorMessage}; qualquer outro valor e tratado como nao-retentavel por padrao
 * (comportamento conservador, evita retry infinito em erro nao mapeado).</p>
 */
public final class DeliveryErrorCode {

    public static final String NO_EMAIL_AVAILABLE = "NO_EMAIL_AVAILABLE";
    public static final String SMTP_AUTH_ERROR = "SMTP_AUTH_ERROR";
    public static final String CHANNEL_TIMEOUT = "CHANNEL_TIMEOUT";
    public static final String CHANNEL_RATE_LIMITED = "CHANNEL_RATE_LIMITED";
    /** Falha ao buscar o relatorio no S3 (ver ResultsReadyEvent). Transiente por natureza. */
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
