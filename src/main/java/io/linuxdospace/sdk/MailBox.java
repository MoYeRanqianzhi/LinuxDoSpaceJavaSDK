package io.linuxdospace.sdk;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * MailBox is one local mailbox binding over the shared client stream.
 *
 * <p>The concrete {@code suffix} value already reflects the owner-specific
 * namespace chosen by the client, for example
 * {@code <owner>-mail.linuxdo.space} or one dynamic
 * {@code <owner>-mailfoo.linuxdo.space} variant derived from a semantic
 * suffix input.</p>
 */
public final class MailBox implements AutoCloseable {
    static final Object CLOSE_SENTINEL = new Object();

    private final String mode;
    private final String suffix;
    private final String prefix;
    private final Pattern pattern;
    private final boolean allowOverlap;
    private final Runnable unregister;
    private final BlockingQueue<Object> queue;
    private final AtomicBoolean closed;
    private final AtomicBoolean active;

    MailBox(
        String mode,
        String suffix,
        String prefix,
        Pattern pattern,
        boolean allowOverlap,
        Runnable unregister
    ) {
        this.mode = mode;
        this.suffix = suffix;
        this.prefix = prefix;
        this.pattern = pattern;
        this.allowOverlap = allowOverlap;
        this.unregister = unregister;
        this.queue = new LinkedBlockingQueue<>();
        this.closed = new AtomicBoolean(false);
        this.active = new AtomicBoolean(false);
    }

    public String mode() {
        return mode;
    }

    public String suffix() {
        return suffix;
    }

    public String prefix() {
        return prefix;
    }

    public String pattern() {
        return pattern == null ? null : pattern.pattern();
    }

    public boolean allowOverlap() {
        return allowOverlap;
    }

    public String address() {
        if (!"exact".equals(mode) || prefix == null) {
            return null;
        }
        return prefix + "@" + suffix;
    }

    public boolean closed() {
        return closed.get();
    }

    /**
     * next activates this mailbox queue and reads one message.
     */
    public Optional<MailMessage> next(Duration timeout) {
        if (closed.get()) {
            return Optional.empty();
        }
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be zero or positive");
        }
        if (!active.compareAndSet(false, true)) {
            throw new LinuxDoSpaceException("mailbox already has an active listener");
        }
        try {
            Object item = timeout.isZero()
                ? queue.poll()
                : queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (item == null) {
                return Optional.empty();
            }
            if (item == CLOSE_SENTINEL) {
                close();
                return Optional.empty();
            }
            if (item instanceof LinuxDoSpaceException sdkError) {
                throw sdkError;
            }
            return Optional.of((MailMessage) item);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            active.set(false);
        }
    }

    boolean matches(String localPart) {
        if ("exact".equals(mode)) {
            return prefix != null && prefix.equals(localPart);
        }
        return pattern != null && pattern.matcher(localPart).matches();
    }

    void enqueueMessage(MailMessage message) {
        if (closed.get()) {
            return;
        }
        if (!active.get()) {
            // Explicit protocol behavior: no backlog before listen().
            return;
        }
        queue.offer(message);
    }

    void enqueueControl(Object control) {
        queue.offer(control);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            unregister.run();
            active.set(false);
            queue.clear();
            queue.offer(CLOSE_SENTINEL);
        }
    }
}
