package io.linuxdospace.sdk;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * MailMessage stores one normalized mail event from the NDJSON stream.
 */
public record MailMessage(
    String address,
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
}
