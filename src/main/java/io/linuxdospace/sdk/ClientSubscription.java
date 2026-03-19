package io.linuxdospace.sdk;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ClientSubscription represents one full-stream local queue listener.
 */
public final class ClientSubscription implements AutoCloseable {
    static final Object CLOSE_SENTINEL = new Object();

    private final BlockingQueue<Object> queue;
    private final Runnable unregister;
    private final AtomicBoolean closed;

    ClientSubscription(Runnable unregister) {
        this.queue = new LinkedBlockingQueue<>();
        this.unregister = unregister;
        this.closed = new AtomicBoolean(false);
    }

    BlockingQueue<Object> queue() {
        return queue;
    }

    public Optional<MailMessage> next(Duration timeout) {
        if (closed.get()) {
            return Optional.empty();
        }
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be zero or positive");
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
        }
    }

    void pushCloseSignal() {
        queue.offer(CLOSE_SENTINEL);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            unregister.run();
            queue.offer(CLOSE_SENTINEL);
        }
    }
}
