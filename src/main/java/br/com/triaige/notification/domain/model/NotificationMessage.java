package br.com.triaige.notification.domain.model;

import java.util.UUID;

/** Mensagem entregue a qualquer adapter de canal (e-mail, WhatsApp, SMS). */
public record NotificationMessage(
        String destino,
        String assunto,
        String corpo,
        Metadata metadata
) {

    public record Metadata(UUID sessionId, String protocolo, UUID correlationId) {
    }
}
