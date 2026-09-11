package br.com.triaige.notification.adapter.out.email;

import br.com.triaige.notification.application.port.out.NotificationChannelPort;
import br.com.triaige.notification.domain.model.DeliveryErrorCode;
import br.com.triaige.notification.domain.model.DeliveryResult;
import br.com.triaige.notification.domain.model.NotificationMessage;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.MimeMessage;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Unica implementacao de {@link NotificationChannelPort} desta fase. Depende de
 * {@link JavaMailSender}; domain/application nunca importam esta classe nem Jakarta Mail
 * diretamente.
 */
@Component
public class EmailSmtpGmailAdapter implements NotificationChannelPort {

    private static final Logger log = LoggerFactory.getLogger(EmailSmtpGmailAdapter.class);

    private final JavaMailSender mailSender;
    private final NotificationMetrics metrics;
    private final String fromAddress;
    private final String fromDisplayName;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    public EmailSmtpGmailAdapter(JavaMailSender mailSender,
                                  NotificationMetrics metrics,
                                  @Value("${SMTP_USER}") String fromAddress,
                                  @Value("${NOTIFICATION_FROM_NAME:Triaige}") String fromDisplayName) {
        this.mailSender = mailSender;
        this.metrics = metrics;
        this.fromAddress = fromAddress;
        this.fromDisplayName = fromDisplayName;
        List<Extension> extensions = List.of(TablesExtension.create());
        this.markdownParser = Parser.builder().extensions(extensions).build();
        this.htmlRenderer = HtmlRenderer.builder().extensions(extensions).build();
    }

    @Override
    public DeliveryResult send(NotificationMessage message) {
        String maskedDestino = maskEmail(message.destino());
        long startedAt = System.currentTimeMillis();
        try {
            MDC.put("sessionId", String.valueOf(message.metadata().sessionId()));
            MDC.put("canal", "EMAIL");

            DeliveryResult result = doSend(message, maskedDestino);
            long latencyMs = System.currentTimeMillis() - startedAt;

            MDC.put("latencyMs", String.valueOf(latencyMs));
            if (result.sucesso()) {
                MDC.put("status", "SENT");
                MDC.put("event", "notification_email_send");
                log.info("E-mail enviado com sucesso destino={}", maskedDestino);
                metrics.recordSent(latencyMs);
            } else {
                MDC.put("status", "FAILED");
                MDC.put("event", "notification_email_send_failed");
                log.warn("Falha ao enviar e-mail destino={} errorMessage={}", maskedDestino, result.errorMessage());
                metrics.recordFailure(result.errorMessage(), latencyMs);
                if (DeliveryErrorCode.SMTP_AUTH_ERROR.equals(result.errorMessage())) {
                    metrics.recordSmtpAuthError();
                }
            }
            return result;
        } finally {
            MDC.remove("sessionId");
            MDC.remove("canal");
            MDC.remove("status");
            MDC.remove("latencyMs");
            MDC.remove("event");
        }
    }

    private DeliveryResult doSend(NotificationMessage message, String maskedDestino) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(fromAddress, fromDisplayName);
            helper.setTo(message.destino());
            helper.setSubject(message.assunto());
            helper.setText(renderMarkdownToHtml(message.corpo()), true);

            mailSender.send(mimeMessage);

            return DeliveryResult.success(extractMessageId(mimeMessage));
        } catch (java.io.UnsupportedEncodingException | MessagingException ex) {
            log.warn("Falha ao montar mensagem SMTP para destino={}: {}", maskedDestino, ex.getMessage());
            return DeliveryResult.failure(safeMessage(ex));
        } catch (MailException ex) {
            return classify(ex, maskedDestino, message);
        }
    }

    private DeliveryResult classify(MailException ex, String maskedDestino, NotificationMessage message) {
        if (ex instanceof MailAuthenticationException) {
            log.error("Falha de autenticacao SMTP (credencial invalida/expirada) - afeta todos os envios sessionId={}",
                    message.metadata().sessionId());
            return DeliveryResult.failure(DeliveryErrorCode.SMTP_AUTH_ERROR);
        }

        Throwable cause = ex.getCause();

        if (cause instanceof SendFailedException sendFailed) {
            // Destinatario invalido/rejeitado ou caixa cheia - nao retentavel.
            String smtpResponse = safeMessage(sendFailed);
            log.warn("Destinatario rejeitado pelo SMTP destino={} sessionId={}: {}",
                    maskedDestino, message.metadata().sessionId(), smtpResponse);
            return DeliveryResult.failure(smtpResponse);
        }

        if (isTimeout(cause) || (ex instanceof MailSendException && isTimeout(ex))) {
            log.warn("Timeout de conexao SMTP destino={} sessionId={} - retentavel", maskedDestino, message.metadata().sessionId());
            return DeliveryResult.failure(DeliveryErrorCode.CHANNEL_TIMEOUT);
        }

        if (looksLikeRateLimit(ex) || looksLikeRateLimit(cause)) {
            log.warn("Limite de envio do Gmail atingido destino={} sessionId={} - retentavel com backoff maior",
                    maskedDestino, message.metadata().sessionId());
            return DeliveryResult.failure(DeliveryErrorCode.CHANNEL_RATE_LIMITED);
        }

        // Comportamento conservador: erro nao mapeado -> nao retentavel, preserva
        // a mensagem crua para diagnostico em vez de assumir retry indefinido.
        log.warn("Erro SMTP nao classificado destino={} sessionId={}: {}",
                maskedDestino, message.metadata().sessionId(), safeMessage(ex));
        return DeliveryResult.failure(safeMessage(ex));
    }

    private boolean isTimeout(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof ConnectException) {
                return true;
            }
            String msg = current.getMessage();
            if (msg != null && msg.toLowerCase(Locale.ROOT).contains("timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean looksLikeRateLimit(Throwable t) {
        if (t == null || t.getMessage() == null) {
            return false;
        }
        String msg = t.getMessage().toLowerCase(Locale.ROOT);
        return msg.contains("sending limit") || msg.contains("rate limit") || msg.contains("too many")
                || msg.contains("quota exceeded") || msg.contains("421");
    }

    private String safeMessage(Throwable t) {
        String msg = t.getMessage();
        return (msg == null || msg.isBlank()) ? t.getClass().getSimpleName() : msg;
    }

    private String extractMessageId(MimeMessage mimeMessage) {
        try {
            String[] ids = mimeMessage.getHeader("Message-ID");
            if (ids != null && ids.length > 0) {
                return ids[0];
            }
        } catch (MessagingException ignored) {
            // Sem Message-ID disponivel - gera um local apenas para correlacao em log/metricas.
        }
        return "local-" + UUID.randomUUID();
    }

    /**
     * O corpo montado por {@code NotificationContentFactory} e markdown (o relatorio de triagem
     * vem assim do S3) — renderiza para HTML aqui, no adapter, porque e uma decisao de
     * apresentacao especifica do canal e-mail (um futuro adapter de WhatsApp/SMS mandaria o mesmo
     * markdown como texto puro, sem essa conversao).
     */
    private String renderMarkdownToHtml(String markdown) {
        Node document = markdownParser.parse(markdown);
        String contentHtml = htmlRenderer.render(document);
        return """
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="UTF-8">
                <style>
                  body { font-family: Arial, Helvetica, sans-serif; color: #1a1a1a; line-height: 1.5; }
                  table { border-collapse: collapse; width: 100%%; margin: 12px 0; }
                  th, td { border: 1px solid #ccc; padding: 6px 10px; text-align: left; }
                  th { background-color: #f2f2f2; }
                  hr { border: none; border-top: 1px solid #ddd; margin: 16px 0; }
                  code { background-color: #f2f2f2; padding: 1px 4px; }
                </style>
                </head>
                <body>
                %s
                </body>
                </html>
                """.formatted(contentHtml);
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
