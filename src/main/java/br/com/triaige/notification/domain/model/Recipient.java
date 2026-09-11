package br.com.triaige.notification.domain.model;

import java.util.UUID;

/**
 * Destinatario cadastrado para uma sessao de triagem ({@code notification_recipients}).
 * {@code email} e {@code canalPreferencial} podem ser nulos/ausentes conforme cadastro.
 */
public record Recipient(
        UUID id,
        UUID sessionId,
        String nome,
        String email,
        String telefone,
        Channel canalPreferencial
) {

    public boolean hasEmail() {
        return email != null && !email.isBlank();
    }
}
