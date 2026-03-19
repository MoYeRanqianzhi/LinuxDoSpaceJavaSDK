package io.linuxdospace.sdk;

import java.time.Duration;
import java.util.Locale;

/**
 * ClientOptions groups all runtime options used by Client.
 */
public final class ClientOptions {
    public static final String DEFAULT_BASE_URL = "https://api.linuxdo.space";
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration DEFAULT_STREAM_READ_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration DEFAULT_RECONNECT_DELAY = Duration.ofMillis(300);

    private final String baseUrl;
    private final Duration connectTimeout;
    private final Duration streamReadTimeout;
    private final Duration reconnectDelay;

    public ClientOptions() {
        this(DEFAULT_BASE_URL, DEFAULT_CONNECT_TIMEOUT, DEFAULT_STREAM_READ_TIMEOUT, DEFAULT_RECONNECT_DELAY);
    }

    public ClientOptions(String baseUrl, Duration connectTimeout, Duration streamReadTimeout, Duration reconnectDelay) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.connectTimeout = requirePositive(connectTimeout, "connectTimeout");
        this.streamReadTimeout = requirePositive(streamReadTimeout, "streamReadTimeout");
        this.reconnectDelay = requirePositive(reconnectDelay, "reconnectDelay");
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration streamReadTimeout() {
        return streamReadTimeout;
    }

    public Duration reconnectDelay() {
        return reconnectDelay;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be a positive duration");
        }
        return value;
    }

    private static String normalizeBaseUrl(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be empty");
        }
        String normalized = rawValue.strip().replaceAll("/+$", "");
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("https://") || lower.startsWith("http://"))) {
            throw new IllegalArgumentException("baseUrl must use http or https");
        }
        if (lower.startsWith("http://")) {
            String host = lower.substring("http://".length());
            int slash = host.indexOf('/');
            if (slash >= 0) {
                host = host.substring(0, slash);
            }
            int colon = host.indexOf(':');
            if (colon >= 0) {
                host = host.substring(0, colon);
            }
            boolean local = host.equals("localhost")
                || host.equals("127.0.0.1")
                || host.equals("::1")
                || host.endsWith(".localhost");
            if (!local) {
                throw new IllegalArgumentException("non-local baseUrl must use https");
            }
        }
        return normalized;
    }
}
