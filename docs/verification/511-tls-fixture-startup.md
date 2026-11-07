# Disposable TLS fixture startup

Tracking: #511, v2.11.0. Related reliability investigation #454 and cache follow-up #512.

## Cost removed

The fixture launcher previously prepared `core:testkit`'s JVM jar and its full runtime graph,
including presentation, shared UI, application and provider jars. Its 71-entry classpath was
unnecessary for a JDK HTTP/TLS server. The current hosted iOS diagnostic run 41715124346,
attempt 2, spent 9m52s in fixture preparation/startup before native compilation began.

The fixture protocol now has one owner in `core:tls-testkit` common code. The existing endpoint
validation, bounded manifest decoder, JVM server and tests move there. Only the JVM source set
uses JDK HTTP/TLS, filesystem, image and keytool/process APIs. `core:testkit` re-exports the
shared protocol and resolves the standalone server through an independent JVM configuration.

The existing `:core:testkit:prepareMobileMutualTlsFixture` and `runMobileMutualTlsFixture` tasks,
main class, argument-file path, bootstrap protocol and launcher script remain compatible. No
certificate, authentication, playback, range, identity, deadline or cleanup behavior changes.

## Local comparison

Sequential runs on the same Mac, JDK 21 and two Gradle workers, with the same dependency cache.
The first two invocations use `--rerun-tasks`; the third uses normal up-to-date checks. This
measures fixture preparation, not certificate generation, simulator startup or total CI time.

| Preparation | Wall time | Runtime classpath |
| --- | ---: | ---: |
| Original, forced compilation | 346.95s | 71 jars |
| Isolated fixture, forced compilation | 43.68s | 3 jars |
| Isolated fixture, warm repeat | 9.88s | 3 jars |

Forced preparation is 87% shorter locally. The isolated task graph compiles only the thin module;
its runtime contains that jar, Kotlin stdlib and annotations. Application/provider/UI compilation
is absent. Hosted improvement must be measured separately rather than inferred from these times.

Evidence: `/private/tmp/511-fixture-benchmark/results.json`, per-stage logs in that directory,
and each worktree's Gradle profile report. Baseline uses accepted main's fixture sources; the
parallel #454 diagnostic additions do not change those sources.

```sh
./gradlew --max-workers=2 --profile \
  :core:testkit:prepareMobileMutualTlsFixture --rerun-tasks
./gradlew :core:tls-testkit:jvmTest :core:testkit:jvmTest
```

## Verification

Seven existing common/JVM fixture tests pass, including concurrent real fixture ownership,
occupied legacy ports, bootstrap identity and cleanup. The thin common implementation compiles
for Android, JVM, iOS ARM64 and iOS Simulator ARM64; the architecture guard passes.
Log: `/private/tmp/511-thin-common-checks.log`.

The retained facade passes its eight JVM tests and compiles for Android and both iOS targets.
Android instrumentation, Desktop and iOS native test consumers compile successfully.
Log: `/private/tmp/511-consumer-checks.log`.

A fresh independently generated three-jar fixture and cloned simulator pass all 17 real native
methods with zero failures, errors or skips, including all five TLS rejections and positive
authentication, playback/seek and identity/range audits. Evidence:
`/private/tmp/naviamp-511-thin-native-jysc1g8m` and `/private/tmp/511-thin-native.log`.

Hosted full-matrix results remain required before merge. The separate #454 failure remains open;
faster preparation and passing retries are not proof that the intermittent native stall is fixed.
