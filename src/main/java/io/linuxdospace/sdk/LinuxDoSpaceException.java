package io.linuxdospace.sdk;

/**
 * LinuxDoSpaceException is the shared base runtime exception for the Java SDK.
 */
public class LinuxDoSpaceException extends RuntimeException {
    public LinuxDoSpaceException(String message) {
        super(message);
    }

    public LinuxDoSpaceException(String message, Throwable cause) {
        super(message, cause);
    }
}
