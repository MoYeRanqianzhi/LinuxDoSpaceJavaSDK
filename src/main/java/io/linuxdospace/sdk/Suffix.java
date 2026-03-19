package io.linuxdospace.sdk;

/**
 * Suffix defines mailbox domain suffix constants.
 */
public enum Suffix {
    LINUXDO_SPACE("linuxdo.space");

    private final String value;

    Suffix(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
