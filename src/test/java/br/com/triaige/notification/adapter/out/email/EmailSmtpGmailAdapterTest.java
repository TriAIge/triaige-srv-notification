package br.com.triaige.notification.adapter.out.email;

import br.com.triaige.notification.domain.model.DeliveryErrorCode;
import br.com.triaige.notification.domain.model.DeliveryResult;
import br.com.triaige.notification.domain.model.NotificationMessage;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sem LocalStack/Gmail real: GreenMail sobe um servidor SMTP de teste em memoria
 * ("testes de adapters podem usar... servidor SMTP de teste").
 */
class EmailSmtpGmailAdapterTest {

    private static final String TEST_USER = "notification@triaige.test";
    private static final String TEST_PASSWORD = "test-app-password";

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser(TEST_USER, TEST_PASSWORD));

    @Test
    void envioComSucesso_capturedPeloServidorSmtpDeTeste() throws Exception {
        EmailSmtpGmailAdapter adapter = new EmailSmtpGmailAdapter(
                mailSender(greenMail.getSmtp().getPort(), TEST_USER, TEST_PASSWORD),
                new NotificationMetrics(new SimpleMeterRegistry()),
                TEST_USER, "Triaige");

        NotificationMessage message = new NotificationMessage("destinatario@example.com", "Assunto de teste",
                "Corpo de teste", new NotificationMessage.Metadata(UUID.randomUUID(), "PROTO-0001", UUID.randomUUID()));

        DeliveryResult result = adapter.send(message);

        assertThat(result.sucesso()).isTrue();
        assertThat(result.providerMessageId()).isNotBlank();

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getSubject()).isEqualTo("Assunto de teste");
        assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("destinatario@example.com");
    }

    @Test
    void corpoMarkdown_renderizadoComoHtmlIncluindoTabelasGfm() throws Exception {
        EmailSmtpGmailAdapter adapter = new EmailSmtpGmailAdapter(
                mailSender(greenMail.getSmtp().getPort(), TEST_USER, TEST_PASSWORD),
                new NotificationMetrics(new SimpleMeterRegistry()),
                TEST_USER, "Triaige");

        String markdown = """
                # Relatorio de Triagem

                Sintese dos fatos com **destaque**.

                | Dimensao | Classificacao |
                | :--- | :---: |
                | Criticidade | Alta |
                """;
        NotificationMessage message = new NotificationMessage("destinatario@example.com", "Assunto",
                markdown, new NotificationMessage.Metadata(UUID.randomUUID(), "PROTO-0001", UUID.randomUUID()));

        DeliveryResult result = adapter.send(message);
        assertThat(result.sucesso()).isTrue();

        MimeMessage received = greenMail.getReceivedMessages()[0];
        assertThat(received.getContentType()).contains("text/html");

        String body = GreenMailUtil.getBody(received);
        assertThat(body).contains("<h1>Relatorio de Triagem</h1>");
        assertThat(body).contains("<strong>destaque</strong>");
        assertThat(body).contains("<table>");
        assertThat(body).contains(">Alta</td>");
    }

    @Test
    void credencialInvalida_classificaComoSmtpAuthError() {
        EmailSmtpGmailAdapter adapter = new EmailSmtpGmailAdapter(
                mailSender(greenMail.getSmtp().getPort(), TEST_USER, "senha-errada"),
                new NotificationMetrics(new SimpleMeterRegistry()),
                TEST_USER, "Triaige");

        NotificationMessage message = new NotificationMessage("destinatario@example.com", "Assunto", "Corpo",
                new NotificationMessage.Metadata(UUID.randomUUID(), "PROTO-0001", UUID.randomUUID()));

        DeliveryResult result = adapter.send(message);

        assertThat(result.sucesso()).isFalse();
        assertThat(result.errorMessage()).isEqualTo(DeliveryErrorCode.SMTP_AUTH_ERROR);
    }

    @Test
    void servidorInalcancavel_classificaComoTimeoutRetentavel() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("192.0.2.1"); // TEST-NET-1 (RFC 5737) - nao roteavel, garante timeout
        mailSender.setPort(25);
        mailSender.setUsername(TEST_USER);
        mailSender.setPassword(TEST_PASSWORD);
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.connectiontimeout", "500");
        props.put("mail.smtp.timeout", "500");
        props.put("mail.smtp.writetimeout", "500");

        EmailSmtpGmailAdapter adapter = new EmailSmtpGmailAdapter(
                mailSender, new NotificationMetrics(new SimpleMeterRegistry()), TEST_USER, "Triaige");

        NotificationMessage message = new NotificationMessage("destinatario@example.com", "Assunto", "Corpo",
                new NotificationMessage.Metadata(UUID.randomUUID(), "PROTO-0001", UUID.randomUUID()));

        DeliveryResult result = adapter.send(message);

        assertThat(result.sucesso()).isFalse();
        assertThat(result.errorMessage()).isEqualTo(DeliveryErrorCode.CHANNEL_TIMEOUT);
    }

    private JavaMailSenderImpl mailSender(int port, String username, String password) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("localhost");
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        return mailSender;
    }
}
