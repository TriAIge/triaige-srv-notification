package br.com.triaige.notification.domain.service;

/**
 * Monta assunto/corpo do e-mail. O corpo inclui o relatorio de triagem completo (markdown) buscado
 * do S3 pela camada application — ver Javadoc de {@code ResultsReadyEvent} para o racional dessa
 * decisao.
 *
 * <p>Continua sendo uma funcao pura (sem I/O): quem busca o relatorio e o caller
 * ({@code NotificationSender}), esta classe so formata o texto final a partir do markdown ja
 * obtido.</p>
 */
public final class NotificationContentFactory {

    private NotificationContentFactory() {
    }

    public static String buildSubject(String protocolo) {
        return "Triaige - seu caso foi processado (protocolo " + protocolo + ")";
    }

    public static String buildBody(String protocolo, String dashboardBaseUrl, String reportMarkdown) {
        String link = buildDashboardLink(protocolo, dashboardBaseUrl);
        return """
                Ola,

                Seu caso (protocolo %s) foi processado. O relatorio de triagem completo esta \
                abaixo, e tambem fica disponivel a qualquer momento no Dashboard: %s

                ---

                %s
                """.formatted(protocolo, link, reportMarkdown);
    }

    public static String buildDashboardLink(String protocolo, String dashboardBaseUrl) {
        String base = dashboardBaseUrl.endsWith("/")
                ? dashboardBaseUrl.substring(0, dashboardBaseUrl.length() - 1)
                : dashboardBaseUrl;
        return base + "/casos/" + protocolo;
    }
}
