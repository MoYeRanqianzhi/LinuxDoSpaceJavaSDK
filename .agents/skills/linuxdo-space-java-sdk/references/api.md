# API Reference

## Paths

- SDK root: `../../../`
- Maven metadata: `../../../pom.xml`
- Public package: `../../../src/main/java/io/linuxdospace/sdk`
- Consumer README: `../../../README.md`

## Public surface

- Types: `Client`, `ClientOptions`, `ClientSubscription`, `MailBox`, `MailMessage`, `Suffix`, `LinuxDoSpaceException`, `AuthenticationException`, `StreamException`
- Client:
  - constructors `Client(...)`
  - `listen()`
  - `bindExact(...)`
  - `bindPattern(...)`
  - `route(message)`
  - `connected()`
  - `close()`
- ClientSubscription:
  - `next(Duration)`
  - `close()`
- MailBox:
  - `next(Duration)`
  - `close()`
  - metadata accessors such as `mode()`, `suffix()`, `pattern()`, `address()`

## Semantics

- `Suffix.LINUXDO_SPACE` is semantic, not literal.
- Regex bindings share the same ordered chain as exact bindings.
- Full-stream messages use the first recipient projection address.
- Mailbox messages use matched-recipient projection addresses.
- Mailbox buffering is only active while `next(...)` is currently waiting.

