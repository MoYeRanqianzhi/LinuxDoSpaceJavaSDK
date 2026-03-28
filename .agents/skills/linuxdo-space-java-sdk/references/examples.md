# Task Templates

## Create one exact mailbox

```java
MailBox alice = client.bindExact("alice", Suffix.LINUXDO_SPACE, false);
```

## Create one dynamic semantic mailbox

```java
MailBox alerts = client.bindExact("alerts", Suffix.LINUXDO_SPACE.withSuffix("foo"), false);
```

## Create one catch-all

```java
MailBox catchAll = client.bindPattern(".*", Suffix.LINUXDO_SPACE, true);
```

## Consume one full-stream message

```java
var subscription = client.listen();
MailMessage item = subscription.next(Duration.ofSeconds(30)).orElse(null);
subscription.close();
```
