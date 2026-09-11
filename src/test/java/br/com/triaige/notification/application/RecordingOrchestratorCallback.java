package br.com.triaige.notification.application;

import br.com.triaige.notification.application.port.out.OrchestratorSessionCallbackPort;
import br.com.triaige.notification.domain.model.NotificationOutcome;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RecordingOrchestratorCallback implements OrchestratorSessionCallbackPort {

    public record Report(UUID sessionId, UUID correlationId, NotificationOutcome outcome,
                          int recipientsNotified, int recipientsFailed) {
    }

    public final List<Report> reports = new ArrayList<>();

    @Override
    public void reportOutcome(UUID sessionId, UUID correlationId, NotificationOutcome outcome,
                               int recipientsNotified, int recipientsFailed) {
        reports.add(new Report(sessionId, correlationId, outcome, recipientsNotified, recipientsFailed));
    }
}
