package br.com.triaige.notification.adapter.out.http;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Corpo do POST .../sessions/{sessionId}/notification-result no Orchestrator. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResultPayload {

    private UUID sessionId;
    private UUID correlationId;
    private String status;
    private int recipientsNotified;
    private int recipientsFailed;
}
