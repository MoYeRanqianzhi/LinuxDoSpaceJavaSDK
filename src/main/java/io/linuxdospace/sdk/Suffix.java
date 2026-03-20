package io.linuxdospace.sdk;

/**
 * Suffix defines mailbox namespace suffix constants.
 *
 * <p>{@link #LINUXDO_SPACE} is semantic rather than literal: SDK bindings
 * resolve it to {@code <owner_username>.linuxdo.space} after the stream
 * {@code ready} event provides {@code owner_username}.</p>
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
