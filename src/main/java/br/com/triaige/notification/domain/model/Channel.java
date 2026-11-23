package br.com.triaige.notification.domain.model;

/** Só EMAIL tem adapter; WHATSAPP e SMS existem porque são valores válidos de {@code canal_preferencial}. */
public enum Channel {
    EMAIL,
    WHATSAPP,
    SMS;

    public static Channel fromDbValue(String value) {
        return Channel.valueOf(value.trim().toUpperCase());
    }
}
