package br.com.triaige.notification.adapter.out.persistence;

import br.com.triaige.notification.application.port.out.RecipientRepositoryPort;
import br.com.triaige.notification.domain.model.Channel;
import br.com.triaige.notification.domain.model.Recipient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Lê notification_recipients. A tabela não tem campo de ativo: o destinatário ligado a um contato
 * inativo é ignorado, e o sem contact_id (cadastro avulso) é sempre considerado ativo.
 */
@Component
public class MySqlRecipientAdapter implements RecipientRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(MySqlRecipientAdapter.class);

    private static final String SELECT_ACTIVE_BY_SESSION = """
            SELECT r.id, r.session_id, r.nome, r.email, r.telefone, r.canal_preferencial
            FROM notification_recipients r
            LEFT JOIN law_firm_contacts c ON c.id = r.contact_id
            WHERE r.session_id = ?
              AND (r.contact_id IS NULL OR c.ativo = 1)
            """;

    private final JdbcTemplate jdbcTemplate;

    public MySqlRecipientAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Recipient> findActiveBySessionId(UUID sessionId) {
        return jdbcTemplate.query(SELECT_ACTIVE_BY_SESSION, rowMapper(), sessionId.toString());
    }

    private RowMapper<Recipient> rowMapper() {
        return (rs, rowNum) -> new Recipient(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("session_id")),
                rs.getString("nome"),
                rs.getString("email"),
                rs.getString("telefone"),
                parseChannel(rs.getString("canal_preferencial"))
        );
    }

    private Channel parseChannel(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Channel.fromDbValue(raw);
        } catch (IllegalArgumentException ex) {
            log.warn("canal_preferencial desconhecido '{}' - tratando como ausente", raw);
            return null;
        }
    }
}
