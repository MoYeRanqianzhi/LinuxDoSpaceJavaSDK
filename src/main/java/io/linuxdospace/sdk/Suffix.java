package io.linuxdospace.sdk;

/**
 * Suffix defines mailbox namespace suffix constants.
 *
 * <p>{@link #LINUXDO_SPACE} is semantic rather than literal: SDK bindings
 * resolve to the current token owner's canonical {@code -mail} namespace under
 * {@code linuxdo.space}, while still accepting the legacy owner-root alias
 * when the backend projects it.</p>
 */
public enum Suffix {
    LINUXDO_SPACE("linuxdo.space");

    private final String value;

    Suffix(String value) {
        this.value = value;
    }

    /**
     * withSuffix derives one semantic dynamic mail namespace rooted under this
     * semantic suffix.
     */
    public SemanticSuffix withSuffix(String fragment) {
        return new SemanticSuffix(this, fragment);
    }

    public String value() {
        return value;
    }
}
