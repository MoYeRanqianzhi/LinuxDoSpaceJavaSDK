package io.linuxdospace.sdk;

/**
 * AuthenticationException indicates the backend rejected the API token.
 */
public final class AuthenticationException extends LinuxDoSpaceException {
    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
