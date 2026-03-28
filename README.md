# LinuxDoSpace Java SDK

This SDK implements the LinuxDoSpace token mail stream protocol:

- one `Client` holds one upstream `GET /v1/token/email/stream` connection
- full stream consumption is available through client-level listeners
- local mailbox bindings support exact and regex rules in one ordered chain
- `allowOverlap=false` stops on first match
- `allowOverlap=true` continues matching later bindings
- mailbox queues become active only when listening starts (no pre-listen backlog)

Important:

- `Suffix.LINUXDO_SPACE` is semantic, not literal
- `Suffix.LINUXDO_SPACE` now defaults to the current token owner's canonical
  mail namespace: `<owner_username>-mail.linuxdo.space`
- `Suffix.LINUXDO_SPACE.withSuffix("foo")` resolves to
  `<owner_username>-mailfoo.linuxdo.space`
- active semantic `-mail<suffix>` registrations are synchronized to
  `PUT /v1/token/email/filters`
- if the backend still projects the legacy default alias
  `<owner_username>.linuxdo.space`, the default semantic binding continues to
  match it automatically

## Requirements

- JDK 21+
- Maven 3.9+

## Build

```bash
mvn -q -DskipTests compile
```

If Maven is not available in your environment, compile directly with `javac`:

```bash
javac --release 21 -d out src/main/java/io/linuxdospace/sdk/*.java
```

## Quick start

```java
import io.linuxdospace.sdk.Client;
import io.linuxdospace.sdk.MailBox;
import io.linuxdospace.sdk.MailMessage;
import io.linuxdospace.sdk.Suffix;
import java.time.Duration;

public final class Demo {
    public static void main(String[] args) {
        try (Client client = new Client("lds_pat.example")) {
            MailBox catchAll = client.bindPattern(".*", Suffix.LINUXDO_SPACE, true);
            MailBox alice = client.bindExact("alice", Suffix.LINUXDO_SPACE, false);
            MailBox reports = client.bindExact("reports", Suffix.LINUXDO_SPACE.withSuffix("alerts"), false);
            MailMessage message = alice.next(Duration.ofSeconds(60)).orElse(null);
            if (message != null) {
                System.out.println(message.address());
                System.out.println(message.subject());
            }
            System.out.println(reports.address());
            catchAll.close();
            alice.close();
            reports.close();
        }
    }
}
```
