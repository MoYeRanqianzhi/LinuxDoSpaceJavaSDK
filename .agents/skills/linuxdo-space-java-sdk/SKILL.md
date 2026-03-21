---
name: linuxdo-space-java-sdk
description: Use when writing or fixing Java code that consumes or maintains the LinuxDoSpace Java SDK under sdk/java. Use for source/jar integration, Client.listen subscription usage, mailbox next(Duration) usage, ordered matching semantics, lifecycle/error handling, release guidance, and local validation.
---

# LinuxDoSpace Java SDK

Read [references/consumer.md](references/consumer.md) first for normal SDK usage.
Read [references/api.md](references/api.md) for exact public Java API names.
Read [references/examples.md](references/examples.md) for task-shaped snippets.
Read [references/development.md](references/development.md) only when editing `sdk/java`.

## Workflow

1. Prefer the public package `io.linuxdospace.sdk`.
2. The SDK root relative to this `SKILL.md` is `../../../`.
3. Preserve these invariants:
   - one `Client` owns one upstream HTTPS stream
   - `Client.listen()` creates a full-stream subscription
   - `ClientSubscription.next(Duration)` consumes the full-stream subscription
   - `bindExact(...)` / `bindPattern(...)` create mailbox bindings locally
   - `MailBox.next(Duration)` consumes one mailbox binding
   - `Suffix.LINUXDO_SPACE` is semantic and resolves after `ready.owner_username`
   - exact and regex bindings share one ordered chain per suffix
   - `allowOverlap=false` stops at first match; `true` continues
   - remote non-local `http://` base URLs are invalid
4. Keep README, source, pom metadata, and workflows aligned when behavior changes.
5. Validate with the commands in `references/development.md`.

## Do Not Regress

- Do not document a public Maven Central install path; current release output is GitHub Release jars.
- Do not describe mailbox queues as continuously buffering between `next(...)` calls.
- Do not add hidden pre-listen mailbox buffering.
