package br.com.triaige.notification.application.port.out;

import br.com.triaige.notification.domain.model.Recipient;

import java.util.List;
import java.util.UUID;

/**
 * Port de saida — busca destinatarios ativos de uma sessao ({@code notification_recipients}).
 */
public interface RecipientRepositoryPort {

    List<Recipient> findActiveBySessionId(UUID sessionId);
}
