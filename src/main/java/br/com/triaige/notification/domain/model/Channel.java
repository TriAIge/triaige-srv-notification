package br.com.triaige.notification.domain.model;

/**
 * Canal de notificacao. Apenas EMAIL esta implementado nesta fase; WHATSAPP/SMS
 * existem como valores validos de {@code canal_preferencial} vindos do banco,
 * mas nao possuem adapter de envio ainda.
 */
public enum Channel {
    EMAIL,
    WHATSAPP,
    SMS;

    public static Channel fromDbValue(String value) {
        return Channel.valueOf(value.trim().toUpperCase());
    }
}
