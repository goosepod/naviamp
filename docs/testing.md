# Testing Naviamp

Naviamp uses a layered test gate so shared product behavior is exercised once in Core while the
irreducible native boundaries are tested on their actual platform.

## Local verification

Run the complete non-device suite from the repository root:

```shell
make test
```

This verifies the Core-first architecture rules, SQLDelight migrations from the committed version-1
database, shared and provider behavior, JVM/Desktop behavior, Android debug and release unit tests,
Desktop BASS/JNI loading and decoding, Android native-library packaging, and a 60% aggregate JVM
line-coverage floor. It prints a combined test count from Gradle's XML reports when complete.

For a faster Desktop-oriented loop, use `make desktop-test`. To regenerate coverage independently,
use `make coverage`; its HTML report is written to `build/reports/kover/html/index.html`.

Kover measures JVM and Android unit-test execution. Kotlin/Native, XCTest, Android instrumentation,
and native C/JNI execution are intentionally represented by explicit CI gates rather than being
misreported as JVM line coverage.

## CI verification

`.github/workflows/verify.yml` runs on every branch push and pull request and is also called before
release packaging. Its required matrix contains:

- shared/provider and Android debug/release unit tests plus aggregate coverage;
- Desktop BASS/JNI integration tests on macOS, Windows, and Linux;
- an Android emulator test that exercises Android Keystore and packaged BASS decoding; and
- iOS Simulator Kotlin/Native tests, device-target compilation, a production Xcode app build,
  native BASS decoding, and a signed XCTest host that exercises Keychain storage, replacement,
  deletion, and missing-value behavior.

Release artifacts cannot start packaging unless this reusable verification workflow succeeds.

## High-risk regression coverage

- SQLDelight checks every migration from `core/storage/src/commonMain/sqldelight/app/naviamp/storage/1.db`.
- Critical stores cover library/search/history, waveform-cache eviction, lyrics and offsets,
  provider-response and sidecar caches, pending actions, and radio-DJ presets.
- Provider contracts cover Jellyfin library selection, pagination, authorization failures, malformed
  responses, and Bandcamp's supported Subsonic profile and playlist limitations.
- A responsive Compose image test renders representative phone and Desktop track lists and checks
  track-number/title alignment semantically and at the pixel-output boundary.

## Dependency audit policy

Patch and compatible minor releases may be taken on a release branch after the full matrix passes.
Toolchain or framework upgrades with migration requirements get a dedicated branch. As of the
2026-08-12 audit, Activity Compose 1.13.0, AndroidX Media 1.7.1, ProfileInstaller 1.4.1,
coroutines/serialization 1.11.0, and JNA 5.19.1 were adopted. Compose Multiplatform 1.11 and Android
Gradle Plugin 9.2 are intentionally deferred:
Compose 1.11 changes native graphics interop APIs, while AGP 9.2 requires Gradle 9.4.1 and an AGP 9
Kotlin Multiplatform migration. Kotlin 2.3.21, Ktor 3.5.0, SQLDelight 2.3.2, and Kover 0.9.8 are
already current stable versions.
