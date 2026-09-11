package br.com.triaige.notification.application;

import br.com.triaige.notification.application.port.out.AuditPort;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RecordingAuditPort implements AuditPort {

    public record Event(String eventType, UUID sessionId, UUID lawFirmId, UUID correlationId, String description) {
    }

    public record Step(UUID sessionId, boolean completed, String errorMessage) {
    }

    public final List<Event> events = new ArrayList<>();
    public final List<Step> steps = new ArrayList<>();

    @Override
    public void recordEvent(String eventType, UUID sessionId, UUID lawFirmId, UUID correlationId, String description) {
        events.add(new Event(eventType, sessionId, lawFirmId, correlationId, description));
    }

    @Override
    public void recordDispatchStep(UUID sessionId, boolean completed, String errorMessage) {
        steps.add(new Step(sessionId, completed, errorMessage));
    }
}
