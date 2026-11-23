package br.com.triaige.notification.domain.service;

/**
 * Monta assunto e corpo do e-mail com o relatório completo em Markdown. Função pura: quem lê o
 * relatório no S3 é o {@code NotificationSender}.
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
