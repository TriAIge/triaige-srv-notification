package br.com.triaige.notification.adapter.out.persistence;

import br.com.triaige.notification.application.port.out.DeliveryRepositoryPort;
import br.com.triaige.notification.domain.model.Channel;
import br.com.triaige.notification.domain.model.DeliverySummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Claim atômico em notification_deliveries com {@code INSERT ... ON DUPLICATE KEY UPDATE id = id}
 * sobre {@code uk_notification_deliveries_session_recipient_canal}.
 *
 * <p>Sem a flag CLIENT_FOUND_ROWS, o driver devolve 1 linha afetada quando insere e 0 quando a chave
 * já existia; é esse 1 ou 0 que decide qual instância envia o e-mail.</p>
 */
@Component
public class MySqlDeliveryAdapter implements DeliveryRepositoryPort {

    private static final String CLAIM_SQL = """
            INSERT INTO notification_deliveries
                (id, session_id, recipient_id, canal, destino, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 'PENDING', NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE id = id
            """;

    private static final String MARK_SENT_SQL = """
            UPDATE notification_deliveries
            SET status = 'SENT', provider = 'SMTP_GMAIL', provider_message_id = ?, error_message = ?,
                sent_at = NOW(6), updated_at = NOW(6)
            WHERE session_id = ? AND recipient_id = ? AND canal = ?
            """;

    private static final String MARK_RETRY_SQL = """
            UPDATE notification_deliveries
            SET status = 'RETRY_SCHEDULED', error_message = ?, updated_at = NOW(6)
            WHERE session_id = ? AND recipient_id = ? AND canal = ?
            """;

    // Upsert sem exigir tryClaim, para cobrir o destinatário sem e-mail (que nunca tenta envio) e
    // para marcar como FAILED uma entrega já reivindicada.
    private static final String UPSERT_FAILED_SQL = """
            INSERT INTO notification_deliveries
                (id, session_id, recipient_id, canal, destino, status, error_message, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 'FAILED', ?, NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE status = 'FAILED', error_message = VALUES(error_message), updated_at = NOW(6)
            """;

    private static final String SUMMARIZE_SQL = """
            SELECT status, COUNT(*) AS total
            FROM notification_deliveries
            WHERE session_id = ?
            GROUP BY status
            """;

    private final JdbcTemplate jdbcTemplate;

    public MySqlDeliveryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryClaim(UUID sessionId, UUID recipientId, Channel canal, String destino) {
        int affected = jdbcTemplate.update(CLAIM_SQL, UUID.randomUUID().toString(), sessionId.toString(),
                recipientId.toString(), canal.name(), destino);
        return affected == 1;
    }

    @Override
    public void markSent(UUID sessionId, UUID recipientId, Channel canal, String providerMessageId, String observacao) {
        jdbcTemplate.update(MARK_SENT_SQL, providerMessageId, observacao, sessionId.toString(),
                recipientId.toString(), canal.name());
    }

    @Override
    public void markFailed(UUID sessionId, UUID recipientId, Channel canal, String destino, String errorMessage) {
        jdbcTemplate.update(UPSERT_FAILED_SQL, UUID.randomUUID().toString(), sessionId.toString(),
                recipientId.toString(), canal.name(), destino, errorMessage);
    }

    @Override
    public void markRetryScheduled(UUID sessionId, UUID recipientId, Channel canal, String lastErrorMessage) {
        jdbcTemplate.update(MARK_RETRY_SQL, lastErrorMessage, sessionId.toString(), recipientId.toString(), canal.name());
    }

    @Override
    public DeliverySummary summarize(UUID sessionId) {
        List<Object[]> rows = jdbcTemplate.query(SUMMARIZE_SQL,
                (rs, rowNum) -> new Object[]{rs.getString("status"), rs.getInt("total")},
                sessionId.toString());

        int sent = 0;
        int failed = 0;
        int pending = 0;
        for (Object[] row : rows) {
            String status = (String) row[0];
            int total = (Integer) row[1];
            switch (status) {
                case "SENT" -> sent += total;
                case "FAILED" -> failed += total;
                case "PENDING", "RETRY_SCHEDULED" -> pending += total;
                default -> { }
            }
        }
        return new DeliverySummary(sent, failed, pending);
    }
}
