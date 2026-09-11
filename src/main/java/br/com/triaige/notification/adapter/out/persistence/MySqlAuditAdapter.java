package br.com.triaige.notification.adapter.out.persistence;

import br.com.triaige.notification.application.port.out.AuditPort;
import br.com.triaige.notification.domain.model.NotificationAuditEventType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Escreve {@code audit_events} e {@code processing_steps}. Sempre insere uma linha nova
 * (sem upsert): redelivery concorrente de Q3 pode gerar registros de auditoria duplicados em um
 * cenario raro (instancia crash antes do delete da mensagem), mas isso nao duplica o envio de
 * e-mail em si — essa garantia vem de {@link MySqlDeliveryAdapter#tryClaim}. Trade-off aceito
 * para nao introduzir uma constraint de unicidade em processing_steps fora do que a spec pede
 * explicitamente.
 */
@Component
public class MySqlAuditAdapter implements AuditPort {

    private static final String INSERT_AUDIT_SQL = """
            INSERT INTO audit_events (id, session_id, law_firm_id, correlation_id, event_type, description, created_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW(6))
            """;

    private static final String INSERT_STEP_SQL = """
            INSERT INTO processing_steps
                (id, session_id, document_id, attachment_group_id, step_name, status, started_at, finished_at, error_message, created_at, updated_at)
            VALUES (?, ?, NULL, NULL, ?, ?, ?, ?, ?, NOW(6), NOW(6))
            """;

    private final JdbcTemplate jdbcTemplate;

    public MySqlAuditAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void recordEvent(String eventType, UUID sessionId, UUID lawFirmId, UUID correlationId, String description) {
        jdbcTemplate.update(INSERT_AUDIT_SQL,
                UUID.randomUUID().toString(),
                sessionId.toString(),
                lawFirmId == null ? null : lawFirmId.toString(),
                correlationId == null ? null : correlationId.toString(),
                eventType,
                description);
    }

    @Override
    public void recordDispatchStep(UUID sessionId, boolean completed, String errorMessage) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(INSERT_STEP_SQL,
                UUID.randomUUID().toString(),
                sessionId.toString(),
                NotificationAuditEventType.PROCESSING_STEP_NAME,
                completed ? "COMPLETED" : "FAILED",
                now,
                now,
                errorMessage);
    }
}
