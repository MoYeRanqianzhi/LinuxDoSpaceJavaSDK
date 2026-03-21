# Development Guide

## Workdir

```bash
cd sdk/java
```

## Validate

README-level build:

```bash
mvn -q -DskipTests compile
```

CI / release aligned build:

```bash
mvn -B -ntp -DskipTests package
```

Fallback compiler-only build:

```bash
javac --release 21 -d out src/main/java/io/linuxdospace/sdk/*.java
```

## Release model

- Workflow file: `../../../.github/workflows/release.yml`
- Trigger: push tag `v*`
- Current release output is a jar asset uploaded to GitHub Release

## Keep aligned

- `../../../pom.xml`
- `../../../src/main/java/io/linuxdospace/sdk`
- `../../../README.md`
- `../../../.github/workflows/ci.yml`
- `../../../.github/workflows/release.yml`

