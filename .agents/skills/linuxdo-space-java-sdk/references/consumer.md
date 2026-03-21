# Consumer Guide

## Integrate

Current release workflow publishes jar assets to GitHub Release. The repository
does not currently publish to Maven Central or GitHub Packages.

Package / import shape:

```java
import io.linuxdospace.sdk.Client;
import io.linuxdospace.sdk.MailBox;
import io.linuxdospace.sdk.MailMessage;
import io.linuxdospace.sdk.Suffix;
```

## Full stream

```java
try (Client client = new Client("lds_pat...")) {
    var subscription = client.listen();
    MailMessage item = subscription.next(Duration.ofSeconds(30)).orElse(null);
    subscription.close();
}
```

## Mailbox binding

```java
try (Client client = new Client("lds_pat...")) {
    MailBox alice = client.bindExact("alice", Suffix.LINUXDO_SPACE, false);
    MailMessage item = alice.next(Duration.ofSeconds(30)).orElse(null);
    alice.close();
}
```

## Key semantics

- `Client.listen()` and `MailBox.next(Duration)` are different consumption models.
- Mailbox delivery is active only while a mailbox `next(...)` call is waiting.
- `route(message)` is local matching only.
