package br.com.triaige.notification.application.port.out;

import br.com.triaige.notification.domain.model.DeliveryResult;
import br.com.triaige.notification.domain.model.NotificationMessage;

/**
 * Port de saida — contrato que qualquer canal (e-mail, WhatsApp, SMS) implementa.
 * O caso de uso nunca sabe se esta falando com SMTP, API de WhatsApp ou gateway de SMS.
 *
 * <p>Vocabulario de retry: quando a falha for retentavel, o adapter deve preencher
 * {@code errorMessage} com um dos codigos de {@link br.com.triaige.notification.domain.model.DeliveryErrorCode}
 * reconhecidos como retentaveis; qualquer outro valor e tratado pela camada application como falha definitiva
 * (ver Javadoc de {@code DeliveryErrorCode} para o racional).</p>
 */
public interface NotificationChannelPort {

    DeliveryResult send(NotificationMessage message);
}
