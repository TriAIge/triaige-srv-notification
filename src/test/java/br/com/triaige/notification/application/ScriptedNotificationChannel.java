package br.com.triaige.notification.application;

import br.com.triaige.notification.application.port.out.NotificationChannelPort;
import br.com.triaige.notification.domain.model.DeliveryResult;
import br.com.triaige.notification.domain.model.NotificationMessage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Fake de {@link NotificationChannelPort}: devolve resultados pre-programados, um por chamada. */
public class ScriptedNotificationChannel implements NotificationChannelPort {

    private final Deque<DeliveryResult> script = new ArrayDeque<>();
    public final List<NotificationMessage> sentMessages = new ArrayList<>();

    public ScriptedNotificationChannel willReturn(DeliveryResult result) {
        script.addLast(result);
        return this;
    }

    @Override
    public DeliveryResult send(NotificationMessage message) {
        sentMessages.add(message);
        if (script.isEmpty()) {
            throw new IllegalStateException("Nenhum resultado programado para esta chamada");
        }
        return script.pollFirst();
    }
}
