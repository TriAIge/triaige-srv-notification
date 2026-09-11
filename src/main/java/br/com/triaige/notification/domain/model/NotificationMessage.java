package br.com.triaige.notification.domain.model;

import java.util.UUID;

/**
 * Contrato de mensagem enviado a um {@code NotificationChannelPort}.
 * Qualquer adapter de canal (e-mail, WhatsApp, SMS) recebe exatamente esta forma.
 */
public record NotificationMessage(
        String destino,
        String assunto,
        String corpo,
        Metadata metadata
) {

    public record Metadata(UUID sessionId, String protocolo, UUID correlationId) {
    }
}
