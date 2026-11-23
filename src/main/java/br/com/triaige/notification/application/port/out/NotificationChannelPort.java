package br.com.triaige.notification.application.port.out;

import br.com.triaige.notification.domain.model.DeliveryResult;
import br.com.triaige.notification.domain.model.NotificationMessage;

/**
 * Contrato de qualquer canal (e-mail, WhatsApp, SMS). Falha retentável deve vir com um dos códigos
 * retentáveis de {@link br.com.triaige.notification.domain.model.DeliveryErrorCode}; qualquer outro é falha definitiva.
 */
public interface NotificationChannelPort {

    DeliveryResult send(NotificationMessage message);
}
