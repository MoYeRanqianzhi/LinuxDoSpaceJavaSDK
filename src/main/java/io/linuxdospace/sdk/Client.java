package io.linuxdospace.sdk;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Client owns one upstream NDJSON stream and routes events to local listeners.
 */
public final class Client implements AutoCloseable {
    private static final String STREAM_PATH = "/v1/token/email/stream";

    private final String token;
    private final ClientOptions options;
    private final HttpClient httpClient;
    private final AtomicBoolean closed;
    private final AtomicBoolean connected;

    private final Object lock;
    private final List<ClientSubscription> fullListeners;
    private final Map<String, List<Binding>> bindingsBySuffix;
    private final CountDownLatch initialReady;

    private volatile LinuxDoSpaceException initialError;
    private volatile LinuxDoSpaceException fatalError;
    private final Thread readerThread;

    public Client(String token) {
        this(token, new ClientOptions());
    }

    public Client(String token, ClientOptions options) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be empty");
        }
        if (options == null) {
            throw new IllegalArgumentException("options must not be null");
        }
        this.token = token.strip();
        this.options = options;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(options.connectTimeout())
            .build();
        this.closed = new AtomicBoolean(false);
        this.connected = new AtomicBoolean(false);
        this.lock = new Object();
        this.fullListeners = new CopyOnWriteArrayList<>();
        this.bindingsBySuffix = new LinkedHashMap<>();
        this.initialReady = new CountDownLatch(1);
        this.readerThread = new Thread(this::runLoop, "LinuxDoSpaceJavaClient");
        this.readerThread.setDaemon(true);
        this.readerThread.start();

        awaitInitialConnection();
    }

    public boolean connected() {
        return connected.get() && !closed.get() && fatalError == null;
    }

    /**
     * listen registers one full-stream queue.
     */
    public ClientSubscription listen() {
        checkFatalError();
        final ClientSubscription[] holder = new ClientSubscription[1];
        ClientSubscription subscription = new ClientSubscription(() -> fullListeners.remove(holder[0]));
        holder[0] = subscription;
        fullListeners.add(subscription);
        return subscription;
    }

    /**
     * bindExact registers one exact local mailbox binding.
     */
    public MailBox bindExact(String prefix, Suffix suffix, boolean allowOverlap) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be empty");
        }
        if (suffix == null) {
            throw new IllegalArgumentException("suffix must not be null");
        }
        checkFatalError();
        String normalizedPrefix = prefix.strip().toLowerCase(Locale.ROOT);
        return registerBinding("exact", suffix.value(), normalizedPrefix, null, allowOverlap);
    }

    /**
     * bindPattern registers one regex local mailbox binding.
     */
    public MailBox bindPattern(String pattern, Suffix suffix, boolean allowOverlap) {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("pattern must not be empty");
        }
        if (suffix == null) {
            throw new IllegalArgumentException("suffix must not be null");
        }
        checkFatalError();
        Pattern compiled = Pattern.compile(pattern.strip());
        return registerBinding("pattern", suffix.value(), null, compiled, allowOverlap);
    }

    /**
     * route returns current local matched mailboxes for one message address.
     */
    public List<MailBox> route(MailMessage message) {
        if (message == null || message.address() == null || message.address().isBlank()) {
            return List.of();
        }
        AddressParts addressParts = splitAddress(message.address().strip().toLowerCase(Locale.ROOT));
        if (addressParts == null) {
            return List.of();
        }
        List<MailBox> matches = new ArrayList<>();
        synchronized (lock) {
            List<Binding> chain = bindingsBySuffix.getOrDefault(addressParts.suffix(), List.of());
            for (Binding binding : chain) {
                if (!binding.mailBox().matches(addressParts.localPart())) {
                    continue;
                }
                matches.add(binding.mailBox());
                if (!binding.mailBox().allowOverlap()) {
                    break;
                }
            }
        }
        return List.copyOf(matches);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        connected.set(false);
        for (ClientSubscription listener : fullListeners) {
            listener.pushCloseSignal();
        }
        synchronized (lock) {
            for (List<Binding> chain : bindingsBySuffix.values()) {
                for (Binding binding : chain) {
                    binding.mailBox().enqueueControl(MailBox.CLOSE_SENTINEL);
                }
            }
            bindingsBySuffix.clear();
        }
        try {
            readerThread.join(options.connectTimeout().plusSeconds(1).toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitInitialConnection() {
        try {
            long waitMillis = options.connectTimeout().plusSeconds(1).toMillis();
            boolean ready = initialReady.await(waitMillis, TimeUnit.MILLISECONDS);
            if (!ready) {
                close();
                throw new StreamException("timed out while opening LinuxDoSpace stream");
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            close();
            throw new StreamException("interrupted while opening LinuxDoSpace stream", interruptedException);
        }
        if (initialError != null) {
            close();
            throw initialError;
        }
    }

    private void runLoop() {
        boolean connectedOnce = false;
        while (!closed.get()) {
            try {
                consumeOnce();
                connected.set(false);
                connectedOnce = true;
            } catch (AuthenticationException authenticationException) {
                connected.set(false);
                fatalError = authenticationException;
                if (!connectedOnce) {
                    initialError = authenticationException;
                    initialReady.countDown();
                }
                broadcastControl(authenticationException);
                return;
            } catch (LinuxDoSpaceException sdkError) {
                connected.set(false);
                if (!connectedOnce) {
                    initialError = sdkError;
                    initialReady.countDown();
                    return;
                }
                sleepQuietly(options.reconnectDelay());
            } catch (Exception exception) {
                connected.set(false);
                StreamException wrapped = new StreamException("unexpected stream failure", exception);
                if (!connectedOnce) {
                    initialError = wrapped;
                    initialReady.countDown();
                    return;
                }
                sleepQuietly(options.reconnectDelay());
            }
        }
    }

    private void consumeOnce() {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(options.baseUrl() + STREAM_PATH))
            .timeout(options.streamReadTimeout())
            .header("Accept", "application/x-ndjson")
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                throw new AuthenticationException("api token was rejected by LinuxDoSpace backend");
            }
            if (status != 200) {
                throw new StreamException("unexpected stream status code: " + status);
            }

            connected.set(true);
            initialReady.countDown();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8)
            )) {
                String line;
                while (!closed.get() && (line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    handleEventLine(line);
                }
            }
        } catch (AuthenticationException authenticationException) {
            throw authenticationException;
        } catch (LinuxDoSpaceException sdkError) {
            throw sdkError;
        } catch (Exception exception) {
            throw new StreamException("failed to consume stream", exception);
        }
    }

    private void handleEventLine(String line) {
        try {
            Map<String, Object> root = parseFlatJson(line);
            String type = stringValue(root.get("type"));
            if (Set.of("ready", "heartbeat").contains(type)) {
                return;
            }
            if (!"mail".equals(type)) {
                return;
            }
            MailEnvelope envelope = parseEnvelope(root);
            dispatchEnvelope(envelope);
        } catch (LinuxDoSpaceException sdkError) {
            throw sdkError;
        } catch (Exception exception) {
            throw new StreamException("failed to decode stream event", exception);
        }
    }

    private MailEnvelope parseEnvelope(Map<String, Object> root) {
        String sender = stringValue(root.get("original_envelope_from"));
        List<String> recipients = stringList(root.get("original_recipients"));
        List<String> normalizedRecipients = new ArrayList<>();
        for (String recipient : recipients) {
            String value = recipient.strip().toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) {
                normalizedRecipients.add(value);
            }
        }
        Instant receivedAt = parseIsoTime(stringValue(root.get("received_at")));
        String base64 = stringValue(root.get("raw_message_base64"));
        if (base64.isEmpty()) {
            throw new StreamException("mail event missing raw_message_base64");
        }
        byte[] rawBytes = Base64.getDecoder().decode(base64);
        ParsedMime parsed = parseMime(rawBytes);
        return new MailEnvelope(
            sender,
            normalizedRecipients,
            receivedAt,
            parsed.subject(),
            parsed.messageId(),
            parsed.date(),
            parsed.fromHeader(),
            parsed.toHeader(),
            parsed.ccHeader(),
            parsed.replyToHeader(),
            parsed.fromAddresses(),
            parsed.toAddresses(),
            parsed.ccAddresses(),
            parsed.replyToAddresses(),
            parsed.text(),
            parsed.html(),
            parsed.headers(),
            parsed.raw(),
            rawBytes
        );
    }

    private ParsedMime parseMime(byte[] rawBytes) {
        String raw = new String(rawBytes, StandardCharsets.UTF_8);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            new ByteArrayInputStream(rawBytes),
            StandardCharsets.UTF_8
        ))) {
            Map<String, String> headers = new LinkedHashMap<>();
            String line;
            String current = null;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    break;
                }
                if ((line.startsWith(" ") || line.startsWith("\t")) && current != null) {
                    headers.put(current, headers.get(current) + " " + line.strip());
                    continue;
                }
                int index = line.indexOf(':');
                if (index <= 0) {
                    continue;
                }
                String key = line.substring(0, index).strip();
                String value = line.substring(index + 1).strip();
                headers.put(key, value);
                current = key;
            }
            String body = raw.contains("\r\n\r\n")
                ? raw.substring(raw.indexOf("\r\n\r\n") + 4)
                : "";
            String contentType = headers.getOrDefault("Content-Type", "").toLowerCase(Locale.ROOT);
            String textBody = contentType.contains("text/html") ? "" : body;
            String htmlBody = contentType.contains("text/html") ? body : "";

            return new ParsedMime(
                headers.getOrDefault("Subject", ""),
                nullable(headers.get("Message-ID")),
                parseMailDate(headers.get("Date")),
                headers.getOrDefault("From", ""),
                headers.getOrDefault("To", ""),
                headers.getOrDefault("Cc", ""),
                headers.getOrDefault("Reply-To", ""),
                extractAddresses(headers.get("From")),
                extractAddresses(headers.get("To")),
                extractAddresses(headers.get("Cc")),
                extractAddresses(headers.get("Reply-To")),
                textBody,
                htmlBody,
                Collections.unmodifiableMap(headers),
                raw
            );
        } catch (Exception exception) {
            throw new StreamException("failed to parse MIME message", exception);
        }
    }

    private void dispatchEnvelope(MailEnvelope envelope) {
        String primaryAddress = envelope.recipients().isEmpty() ? "" : envelope.recipients().get(0);
        broadcastToFull(envelope.toMessage(primaryAddress));

        Set<String> deduplicatedRecipients = Set.copyOf(envelope.recipients());
        for (String recipient : deduplicatedRecipients) {
            dispatchToBindings(recipient, envelope.toMessage(recipient));
        }
    }

    private void dispatchToBindings(String address, MailMessage message) {
        AddressParts parts = splitAddress(address);
        if (parts == null) {
            return;
        }
        synchronized (lock) {
            List<Binding> chain = bindingsBySuffix.getOrDefault(parts.suffix(), List.of());
            for (Binding binding : chain) {
                if (!binding.mailBox().matches(parts.localPart())) {
                    continue;
                }
                binding.mailBox().enqueueMessage(message);
                if (!binding.mailBox().allowOverlap()) {
                    break;
                }
            }
        }
    }

    private void broadcastToFull(MailMessage message) {
        for (ClientSubscription listener : fullListeners) {
            listener.queue().offer(message);
        }
    }

    private void broadcastControl(LinuxDoSpaceException sdkError) {
        for (ClientSubscription listener : fullListeners) {
            listener.queue().offer(sdkError);
        }
        synchronized (lock) {
            for (List<Binding> chain : bindingsBySuffix.values()) {
                for (Binding binding : chain) {
                    binding.mailBox().enqueueControl(sdkError);
                }
            }
        }
    }

    private MailBox registerBinding(
        String mode,
        String suffix,
        String prefix,
        Pattern pattern,
        boolean allowOverlap
    ) {
        final MailBox[] holder = new MailBox[1];
        Runnable unregister = () -> {
            synchronized (lock) {
                List<Binding> chain = bindingsBySuffix.get(suffix);
                if (chain == null) {
                    return;
                }
                chain.removeIf(binding -> binding.mailBox() == holder[0]);
                if (chain.isEmpty()) {
                    bindingsBySuffix.remove(suffix);
                }
            }
        };
        MailBox mailBox = new MailBox(mode, suffix, prefix, pattern, allowOverlap, unregister);
        holder[0] = mailBox;
        synchronized (lock) {
            bindingsBySuffix.computeIfAbsent(suffix, ignored -> new ArrayList<>())
                .add(new Binding(mailBox));
        }
        return mailBox;
    }

    private void sleepQuietly(Duration duration) {
        if (closed.get()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private void checkFatalError() {
        if (fatalError != null) {
            throw fatalError;
        }
    }

    private static AddressParts splitAddress(String address) {
        int at = address.indexOf('@');
        if (at <= 0 || at >= address.length() - 1) {
            return null;
        }
        return new AddressParts(
            address.substring(0, at),
            address.substring(at + 1)
        );
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String textValue) {
            return textValue;
        }
        return String.valueOf(value);
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> items) {
            List<String> result = new ArrayList<>();
            for (Object item : items) {
                result.add(stringValue(item));
            }
            return List.copyOf(result);
        }
        return List.of();
    }

    /**
     * parseFlatJson parses the current stream event shape only.
     * It intentionally supports just:
     * - string fields
     * - string array fields
     */
    private static Map<String, Object> parseFlatJson(String line) {
        Map<String, Object> result = new HashMap<>();
        int index = 0;
        while (index < line.length()) {
            int keyStart = line.indexOf('"', index);
            if (keyStart < 0) {
                break;
            }
            int keyEnd = findStringEnd(line, keyStart + 1);
            if (keyEnd < 0) {
                break;
            }
            String key = unescapeJson(line.substring(keyStart + 1, keyEnd));
            int colon = line.indexOf(':', keyEnd + 1);
            if (colon < 0) {
                break;
            }
            int valueStart = skipSpaces(line, colon + 1);
            if (valueStart >= line.length()) {
                break;
            }
            char marker = line.charAt(valueStart);
            if (marker == '"') {
                int valueEnd = findStringEnd(line, valueStart + 1);
                if (valueEnd < 0) {
                    break;
                }
                result.put(key, unescapeJson(line.substring(valueStart + 1, valueEnd)));
                index = valueEnd + 1;
                continue;
            }
            if (marker == '[') {
                int arrayEnd = findArrayEnd(line, valueStart + 1);
                if (arrayEnd < 0) {
                    break;
                }
                result.put(key, parseStringArray(line.substring(valueStart + 1, arrayEnd)));
                index = arrayEnd + 1;
                continue;
            }
            int valueEnd = findScalarEnd(line, valueStart);
            String scalar = line.substring(valueStart, valueEnd).strip();
            result.put(key, scalar);
            index = valueEnd;
        }
        return result;
    }

    private static int skipSpaces(String value, int index) {
        int current = index;
        while (current < value.length() && Character.isWhitespace(value.charAt(current))) {
            current++;
        }
        return current;
    }

    private static int findStringEnd(String value, int startIndex) {
        int current = startIndex;
        while (current < value.length()) {
            char currentChar = value.charAt(current);
            if (currentChar == '"' && value.charAt(current - 1) != '\\') {
                return current;
            }
            current++;
        }
        return -1;
    }

    private static int findArrayEnd(String value, int startIndex) {
        int current = startIndex;
        while (current < value.length()) {
            char currentChar = value.charAt(current);
            if (currentChar == ']') {
                return current;
            }
            current++;
        }
        return -1;
    }

    private static int findScalarEnd(String value, int startIndex) {
        int current = startIndex;
        while (current < value.length()) {
            char currentChar = value.charAt(current);
            if (currentChar == ',' || currentChar == '}') {
                return current;
            }
            current++;
        }
        return value.length();
    }

    private static List<String> parseStringArray(String rawArrayContent) {
        List<String> result = new ArrayList<>();
        int index = 0;
        while (index < rawArrayContent.length()) {
            int quoteStart = rawArrayContent.indexOf('"', index);
            if (quoteStart < 0) {
                break;
            }
            int quoteEnd = findStringEnd(rawArrayContent, quoteStart + 1);
            if (quoteEnd < 0) {
                break;
            }
            result.add(unescapeJson(rawArrayContent.substring(quoteStart + 1, quoteEnd)));
            index = quoteEnd + 1;
        }
        return List.copyOf(result);
    }

    private static String unescapeJson(String value) {
        return value
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\/", "/")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t");
    }

    private static String nullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static Instant parseIsoTime(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        return Instant.parse(value);
    }

    private static Instant parseMailDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<String> extractAddresses(String rawHeader) {
        if (rawHeader == null || rawHeader.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        String[] parts = rawHeader.split(",");
        for (String part : parts) {
            String value = part.strip();
            int open = value.indexOf('<');
            int close = value.indexOf('>');
            if (open >= 0 && close > open) {
                value = value.substring(open + 1, close).strip();
            }
            value = value.toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    private record Binding(MailBox mailBox) {
    }

    private record AddressParts(String localPart, String suffix) {
    }

    private record ParsedMime(
        String subject,
        String messageId,
        Instant date,
        String fromHeader,
        String toHeader,
        String ccHeader,
        String replyToHeader,
        List<String> fromAddresses,
        List<String> toAddresses,
        List<String> ccAddresses,
        List<String> replyToAddresses,
        String text,
        String html,
        Map<String, String> headers,
        String raw
    ) {
    }

    private record MailEnvelope(
        String sender,
        List<String> recipients,
        Instant receivedAt,
        String subject,
        String messageId,
        Instant date,
        String fromHeader,
        String toHeader,
        String ccHeader,
        String replyToHeader,
        List<String> fromAddresses,
        List<String> toAddresses,
        List<String> ccAddresses,
        List<String> replyToAddresses,
        String text,
        String html,
        Map<String, String> headers,
        String raw,
        byte[] rawBytes
    ) {
        private MailMessage toMessage(String address) {
            return new MailMessage(
                address,
                sender,
                recipients,
                receivedAt,
                subject,
                messageId,
                date,
                fromHeader,
                toHeader,
                ccHeader,
                replyToHeader,
                fromAddresses,
                toAddresses,
                ccAddresses,
                replyToAddresses,
                text,
                html,
                headers,
                raw,
                rawBytes
            );
        }
    }
}
