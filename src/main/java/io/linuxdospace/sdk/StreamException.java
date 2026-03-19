package io.linuxdospace.sdk;

/**
 * StreamException indicates transport or stream-format failures.
 */
public final class StreamException extends LinuxDoSpaceException {
    public StreamException(String message) {
        super(message);
    }

    public StreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
