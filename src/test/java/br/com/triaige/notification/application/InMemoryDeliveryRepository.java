package br.com.triaige.notification.application;

import br.com.triaige.notification.application.port.out.DeliveryRepositoryPort;
import br.com.triaige.notification.domain.model.Channel;
import br.com.triaige.notification.domain.model.DeliverySummary;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fake em memoria de {@link DeliveryRepositoryPort} para os testes do core (testes
 * unitarios de domain/application rodam sem rede/banco real).
 */
public class InMemoryDeliveryRepository implements DeliveryRepositoryPort {

    public record Key(UUID sessionId, UUID recipientId, Channel canal) {
    }

    public record Record(String destino, String status, String errorMessage, String providerMessageId) {
    }

    private final Set<Key> claimed = new LinkedHashSet<>();
    private final ConcurrentHashMap<Key, Record> records = new ConcurrentHashMap<>();
    public final AtomicInteger claimAttempts = new AtomicInteger(0);

    @Override
    public synchronized boolean tryClaim(UUID sessionId, UUID recipientId, Channel canal, String destino) {
        claimAttempts.incrementAndGet();
        Key key = new Key(sessionId, recipientId, canal);
        boolean isNew = claimed.add(key);
        if (isNew) {
            records.put(key, new Record(destino, "PENDING", null, null));
        }
        return isNew;
    }

    @Override
    public void markSent(UUID sessionId, UUID recipientId, Channel canal, String providerMessageId, String observacao) {
        Key key = new Key(sessionId, recipientId, canal);
        Record existing = records.get(key);
        String destino = existing == null ? null : existing.destino();
        records.put(key, new Record(destino, "SENT", observacao, providerMessageId));
    }

    @Override
    public void markFailed(UUID sessionId, UUID recipientId, Channel canal, String destino, String errorMessage) {
        Key key = new Key(sessionId, recipientId, canal);
        records.put(key, new Record(destino, "FAILED", errorMessage, null));
    }

    @Override
    public void markRetryScheduled(UUID sessionId, UUID recipientId, Channel canal, String lastErrorMessage) {
        Key key = new Key(sessionId, recipientId, canal);
        Record existing = records.get(key);
        String destino = existing == null ? null : existing.destino();
        records.put(key, new Record(destino, "RETRY_SCHEDULED", lastErrorMessage, null));
    }

    public Record get(UUID sessionId, UUID recipientId, Channel canal) {
        return records.get(new Key(sessionId, recipientId, canal));
    }

    @Override
    public DeliverySummary summarize(UUID sessionId) {
        int sent = 0;
        int failed = 0;
        int pending = 0;
        for (var entry : records.entrySet()) {
            if (!entry.getKey().sessionId().equals(sessionId)) {
                continue;
            }
            switch (entry.getValue().status()) {
                case "SENT" -> sent++;
                case "FAILED" -> failed++;
                case "PENDING", "RETRY_SCHEDULED" -> pending++;
                default -> { }
            }
        }
        return new DeliverySummary(sent, failed, pending);
    }
}
