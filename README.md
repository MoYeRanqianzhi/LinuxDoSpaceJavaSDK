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
- the SDK resolves it to `<owner_username>.linuxdo.space` after `ready.owner_username`

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
            MailMessage message = alice.next(Duration.ofSeconds(60)).orElse(null);
            if (message != null) {
                System.out.println(message.address());
                System.out.println(message.subject());
            }
            catchAll.close();
            alice.close();
        }
    }
}
```
