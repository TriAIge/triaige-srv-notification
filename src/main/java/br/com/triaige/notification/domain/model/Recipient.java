package br.com.triaige.notification.domain.model;

import java.util.UUID;

/** Destinatário da sessão ({@code notification_recipients}); e-mail e canal preferencial podem faltar. */
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
