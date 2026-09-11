package br.com.triaige.notification.domain.model;

/**
 * Resultado devolvido por um {@code NotificationChannelPort.send(...)}.
 */
public record DeliveryResult(boolean sucesso, String providerMessageId, String errorMessage) {

    public static DeliveryResult success(String providerMessageId) {
        return new DeliveryResult(true, providerMessageId, null);
    }

    public static DeliveryResult failure(String errorMessage) {
        return new DeliveryResult(false, null, errorMessage);
    }
}
