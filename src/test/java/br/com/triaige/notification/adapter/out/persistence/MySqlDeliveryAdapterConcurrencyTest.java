package br.com.triaige.notification.adapter.out.persistence;

import br.com.triaige.notification.domain.model.Channel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida o item de DoD "claim atomico antes do SMTP validado com duas instancias/reprocessamento
 * concorrente": MySQL real via Testcontainers (nao e emulacao de AWS - nao
 * substitui/depende de LocalStack, so o motor de banco de dados).
 */
@Testcontainers
class MySqlDeliveryAdapterConcurrencyTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("triaige")
            .withUsername("triaige")
            .withPassword("triaige");

    static JdbcTemplate jdbcTemplate;
    static MySqlDeliveryAdapter adapter;

    @BeforeAll
    static void setUp() throws SQLException {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        dataSource.setDriverClass(com.mysql.cj.jdbc.Driver.class);
        dataSource.setUrl(mysql.getJdbcUrl());
        dataSource.setUsername(mysql.getUsername());
        dataSource.setPassword(mysql.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        adapter = new MySqlDeliveryAdapter(jdbcTemplate);

        // Schema minimo: so notification_deliveries, sem FKs (o adapter nao faz join com outras
        // tabelas), com a unique key da migration V1 ja aplicada.
        jdbcTemplate.execute("""
                CREATE TABLE notification_deliveries (
                    id                    CHAR(36)     NOT NULL,
                    session_id             CHAR(36)     NOT NULL,
                    recipient_id           CHAR(36)     NOT NULL,
                    canal                  VARCHAR(20)  NOT NULL,
                    destino                VARCHAR(200) NOT NULL,
                    status                 VARCHAR(40)  NOT NULL,
                    provider               VARCHAR(50),
                    provider_message_id    VARCHAR(200),
                    error_message          VARCHAR(1000),
                    sent_at                DATETIME(6),
                    created_at             DATETIME(6)  NOT NULL,
                    updated_at             DATETIME(6)  NOT NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_notification_deliveries_session_recipient_canal (session_id, recipient_id, canal)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    @AfterAll
    static void tearDown() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS notification_deliveries");
    }

    @Test
    void duasInstanciasReivindicandoAMesmaEntregaConcorrentemente_apenasUmaVence() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        int concurrentAttempts = 8;

        CountDownLatch ready = new CountDownLatch(concurrentAttempts);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(concurrentAttempts);

        List<Future<Boolean>> futures = IntStream.range(0, concurrentAttempts)
                .mapToObj(i -> pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return adapter.tryClaim(sessionId, recipientId, Channel.EMAIL, "fulano@example.com");
                }))
                .collect(Collectors.toList());

        ready.await();
        start.countDown();

        long claimsWon = futures.stream().map(f -> {
            try {
                return f.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).filter(Boolean::booleanValue).count();

        pool.shutdown();

        assertThat(claimsWon).isEqualTo(1);

        Long rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_deliveries WHERE session_id = ? AND recipient_id = ? AND canal = 'EMAIL'",
                Long.class, sessionId.toString(), recipientId.toString());
        assertThat(rowCount).isEqualTo(1L);
    }
}
