# Naviamp 2.0 Platform Baseline

This document records the Android and Desktop baseline before application orchestration moves into the shared Naviamp 2.0 runtime. Update it when ownership moves or a verification command changes.

The Desktop implementation recorded below is historical and was replaced by the promoted thin Core host on 2026-07-23. Commands and artifact paths remain current.

Current target/composition status was reviewed on 2026-07-27; the dated baseline commit below remains the intentional pre-migration comparison point.

- **Snapshot date:** 2026-07-16
- **Source branch:** `feature/v2-cross-platform-app`
- **Baseline commit:** `a48bf38f`
- **Architecture decision:** [ADR 0001: Shared Runtime and Thin Platform Hosts](architecture/0001-shared-runtime-thin-hosts.md)
- **Migration tracker:** [Naviamp 2.0 Cross-Platform Plan](v2-cross-platform-plan.md)
- **Regression contracts:** [Naviamp 2.0 Migration Regression Contracts](v2-regression-contracts.md)

## Reproducible Baseline

Run commands from the repository root. `ANDROID_HOME` must point at a usable Android SDK even for some Desktop Gradle configurations.

| Purpose | Command | Expected result or artifact |
| --- | --- | --- |
| Version consistency | `make version-check` | Version validation succeeds. |
| Shared and Desktop tests | `make desktop-test` | Gradle `desktopTest` succeeds. |
| Android debug build | `make android-debug` | `apps/android/build/outputs/apk/debug/android-debug.apk` |
| Android packaged BASS check | `./gradlew :apps:android:verifyDebugBassNativePackage` | Debug APK contains JNI and BASS libraries for every packaged ABI. |
| Local macOS app | `make macos-test` | Stages and opens `build/local-test/Naviamp.app`. |
| macOS standalone archive | `make macos-standalone` | Release archive under `apps/desktop/build/compose/distributions`. |
| Windows app image | `make windows-test` | Windows app image, when run on Windows. |
| Linux app image | `make linux-test` | Linux app image, when run on Linux. |

Windows and Linux packaging must be verified on their target operating systems. The v2 work must not treat a macOS JVM compile as proof that native libraries or `jpackage` layouts work elsewhere.

### Baseline Verification Record

Verified on macOS ARM64 on 2026-07-16:

- `./gradlew desktopTest :apps:android:assembleDebug` — successful; 173 tasks considered.
- `./gradlew :core:domain:allTests :core:ui:jvmTest :providers:navidrome:jvmTest :apps:desktop:desktopTest :apps:android:testDebugUnitTest :apps:android:verifyDebugBassNativePackage` — successful; 210 tasks considered.
- Android debug artifact created and its packaged BASS/JNI libraries verified.

This proves the shared JVM/Android compilation, checked-in unit tests, Desktop tests, Android unit tests, debug APK assembly, and Android native-library package check at the baseline. It does not replace interactive launch testing or Windows/Linux packaging verification.

## Current Module Targets

| Module | Current targets | v2 implication |
| --- | --- | --- |
| `core:app` | Android, JVM, iOS device, iOS simulator | Shared runtime/controllers own lifecycle/session bootstrap, connectivity, navigation, queue and playback coordination, provider actions, settings, downloads, cache work, capabilities, and user-facing status. All three thin hosts consume this ownership. |
| `core:domain` | Android, JVM, iOS device, iOS simulator | Apple targets, Darwin shared HTTP, native time/encoding/hash actuals, and simulator tests were added in Milestone 1. |
| `core:presentation` | Android, JVM, iOS device, iOS simulator | The common composition boundary depends on both `core:app` and `core:ui`; it is the home for the complete product state/action binding rather than rebuilding that graph in each host. |
| `core:storage` | Android, JVM, iOS device, iOS simulator | Shared SQLDelight repositories own media, library, maintenance, downloads, audio-cache metadata/limits/eviction, sidecars, waveforms, lyrics offsets, sessions, and provider-action rows. Hosts select drivers and provide only native bytes/filesystem effects. |
| `core:ui` | Android, JVM, iOS device, iOS simulator | Apple targets use the same shared cover-art UI, loading/cache policy, palette/Aurora behavior, lyrics, waveform presentation, settings, navigation, and visualizer definitions. Core owns each Canvas/SkSL visualizer plus the authoritative native GLSL catalog, GLSL-to-MSL translation, renderer selection, uniforms, smoothing, and render-quality policy. macOS and iOS execute the same generated Metal shaders through narrow native command/texture adapters; iOS retains Core's distinct compiled SkSL translations only as a Metal failure fallback. Targets provide only encoded-image loading, native decoding/pixel extraction, and the unavoidable GPU drawing boundary. Authenticated iOS artwork and Aurora rendering pass on the simulator. |
| `providers:navidrome` | Android, JVM, iOS device, iOS simulator | Darwin handles normal iOS HTTPS and opt-in insecure server verification through a scoped server-trust challenge handler. The iOS host permits arbitrary loads for user-configured HTTP/private-PKI servers, while certificate bypass remains an explicit provider setting. Custom CAs and client certificates remain explicit unavailable capabilities and fail closed until secure native implementations exist. |
| `apps:android` | Android application | Thin host for Activity/service lifetime, MediaSession, notification, permissions, URI pickers, Android Auto, native playback loading, and Android storage/network effects. Product behavior and UI come from Core. |
| `apps:desktop` | Desktop JVM application | Thin host for window/Dock/taskbar state, native dialogs, updater, packaging, filesystem/credential effects, and native playback loading. Product behavior and UI come from Core. |
| `platforms:ios` | iOS device and iOS simulator | Thin Apple effects provide NSUserDefaults settings bytes, UIKit document selection plus retained scoped-URL access, UIApplication external-URL opening, calendar/time values, atomic Application Support audio-byte writes and exact-path file operations, file-URL translation, direct Kotlin/Native BASS ABI calls, `AVAudioSession` lifecycle notifications, and MediaPlayer publication/command translation. Product policy remains in Core. |
| `apps:ios` | iOS device and iOS simulator | The static Kotlin framework, SwiftUI/UIKit wrapper, and Xcode project launch on an iOS 26.5 simulator. The host owns native lifetimes and mounts shared repository/provider/playback/download/cache/lyrics/waveform/settings-sync catalogs into one process-level `NaviampCore`. Packaged simulator acceptance covers authenticated MP3/FLAC playback, queue transitions, artwork/Aurora, lyrics, waveforms, Now Playing publication, real MP3/FLAC queue prefetch into SQL-owned Application Support files, persistent download management/conversion, Prefer Downloaded selection, cold-launch playback of downloads with all networking disabled, and the complete native settings import/export, retained-folder, relaunch, Sync Now, and auto-export workflow. The Naviamp AppIcon is packaged and appears correctly. Final release signing and physical-device acceptance remain. |

## Existing Shared Foundation

The migration starts with substantial shared behavior rather than an empty multiplatform shell:

- Domain models and provider contracts
- Playback engine interfaces and optional capability interfaces
- Queue selection, mutation, lifecycle, restoration, and transition rules
- BASS playback creation, start, preparation, and polling plans
- Library paging, freshness, synchronization decisions, and API catalog services
- Playlist mutation and playback plans
- Radio, Sonic Mix, Sonic Path, Sonic Autoplay, and mix-builder services
- Downloads, audio cache, sidecar, waveform, lyrics, and keep-downloaded rules
- Settings models and synchronization mapping
- Shared Compose screens and UI models
- SQLDelight schema and repository contracts
- Navidrome/OpenSubsonic provider behavior

The v2 task is primarily to move ownership and coordination of these pieces out of platform applications, not to rewrite their underlying rules.

## Current Composition Roots

### Desktop

`apps/desktop/.../Main.kt` owns only the JVM process/window lifetime, appearance/icon, persisted geometry, and shutdown. `DesktopComposition` selects JDBC, JVM filesystem, operating-system credential, native BASS/visualizer, picker, updater, and external-URI effects and passes them to `NaviampCore`. `DesktopNaviampCoreHost` mounts `NaviampCoreApp`; no Desktop product graph survives. A separate Stats for Nerds window is an intentional native window around shared diagnostics content.

### Android

`MainActivity` owns Activity lifecycle, intents, permission/safe-area integration, and mounts the common Core app. `AndroidNaviampPlaybackService` owns the required service lifetime, audio focus/wake behavior, MediaSession, notification, and Android Auto translation while consuming Core queue/commands/state. The Android catalogs select Android SQLite, Keystore, URI/filesystem, connectivity, artwork, and BASS/JNI effects; they do not build feature controllers.

### iOS

The Swift wrapper owns UIApplication/SwiftUI lifetime and constructs `NaviampIosApplication`. The Kotlin iOS composition selects Application Support, Keychain, Darwin HTTP/native SQLite, Foundation/POSIX storage, UIKit picker, BASS cinterop, AVAudioSession, and MediaPlayer effects, then mounts the same `NaviampCoreApp`. It contains no iOS-specific album, artist, playlist, radio, download, settings, or Now Playing controller.

## Current Platform Ownership

| Concern | Android effect | Desktop effect | iOS effect | Shared owner |
| --- | --- | --- | --- | --- |
| Composition/lifecycle | Activity/service | Process/window | UIApplication/audio session | `NaviampCore` graph and lifecycle policy |
| State/navigation/features | None | None | None | Core state, controllers, commands, and shared Compose UI |
| Provider transport/TLS | OkHttp/Android TLS | CIO/JVM TLS | Darwin TLS | Provider-common protocol, mapping, failover, and session policy |
| Playback device | BASS/JNI, audio focus, wake lock | BASS/JNI, devices | BASS cinterop, AVAudioSession | `CoreBassPlaybackEngine`, queue/transitions/reporting |
| System media/automotive | MediaSession, notification, Android Auto | No mounted adapter | MediaPlayer/remote commands | Shared metadata, transport commands, automotive catalog |
| Credentials | Keystore | Keychain/DPAPI/Secret Service | Keychain | Shared media-source mapping and credential contract |
| Database/files | Android SQLite and file/URI effects | JDBC and JVM path effects | Native SQLite and Foundation/POSIX effects | Shared schema, repositories, cache/download ownership and policy |
| Cover art/waveform/visualizer | Android decode/GPU/audio samples | JVM decode/Metal/OpenGL/audio samples | Apple decode/Metal/audio samples | Shared orchestration, models, renderer selection, and UI |
| Native shell | Permissions/intents | Window/dialog/updater/packages | Picker/Now Playing/App Store | Shared intent and result policy |

## Platform-Layer Architecture Status

The product graph, UI, and portable persistence behavior are common. Android, Desktop, and iOS mount `StorageCoreRepositoryCatalog` and `StorageAudioStore`; the Android/Desktop duplicate SQLDelight repositories and audio-policy stores identified by the 2026-07-31 re-audit have been deleted. Driver construction, native paths, file-existence facts, atomic byte writes, exact verified deletion, credential protection, database-size lookup, and OS dispatchers remain platform effects. The architecture guard rejects generated SQL mapping and the deleted portable store roles in host production code. The remaining ownership gate is the fresh file-by-file Android/Desktop/iOS audit recorded in the [Core-first exit gate](v2-core-first-platform-audit.md#core-completion-exit-gate).

## Platform Capability Model

Every platform-facing feature must have one of these states:

- **Required:** Must work before v2.0.0 ships on that platform.
- **Optional:** Supported when the operating system or installed components provide it; UI must disclose availability.
- **Temporary:** Allowed during migration but cannot satisfy final v2 exit criteria.
- **Unsupported:** Intentionally unavailable with a documented product reason and no misleading controls.

Capability checks belong in immutable platform capability data or narrow service interfaces. Shared UI consumes those capabilities; it must not inspect an operating-system name.

### Target v2 Capability Matrix

| Capability | Android | Desktop | iOS |
| --- | --- | --- | --- |
| Shared UI/navigation/product behavior | Available | Available | Available on simulator |
| BASS playback | Available | Available | Available on simulator/device build |
| AVPlayer playback | Unsupported | Unsupported | Unsupported; temporary proof path removed |
| Streaming and downloaded playback | Available | Available | Accepted on simulator |
| Background playback | Available via service | Available while process runs | Accepted on simulator |
| OS media controls/metadata | Available | No mounted adapter | Accepted; simulator glyph rendering defect is external |
| Queue/session restoration | Available | Available | Accepted on simulator |
| Gapless, ReplayGain, crossfade, EQ | Available | Available | Accepted on simulator |
| Waveforms and visualizers | Available | Available | Accepted on simulator |
| Software volume | Intentionally hidden | Available | Intentionally hidden |
| Audio output selection | Hidden until a native inventory is supplied | Available where BASS enumerates devices | Hidden |
| Secure credential storage | Keystore | Keychain/DPAPI/Secret Service | Keychain |
| Endpoint failover and OpenSubsonic capabilities | Provider-common | Provider-common | Provider-common |
| Insecure TLS | Available | Available | Available |
| Custom CA/client certificates | Available | Available | Unavailable; native secure adapters absent |
| Downloads/cache/offline mode | Available | Available | Accepted on simulator |
| Automotive | Android Auto available | Unsupported | CarPlay not implemented |
| Application updates | Shared checks available | Shared checks and native packages | Shared checks; App Store distribution pending |

## Regression Coverage Snapshot

Checked-in `*Test.kt` files at the baseline:

| Module | Test files |
| --- | ---: |
| `core:domain` | 83 |
| `core:storage` | 0 |
| `core:ui` | 14 |
| `providers:navidrome` | 5 |
| `apps:desktop` | 9 |
| `apps:android` | 7 |

The number of files is not a quality score, but it exposes migration risk. Domain rules have substantial coverage; storage behavior and platform composition boundaries need more direct characterization.

Before moving ownership, add or confirm tests for:

- application initialization and connection restoration;
- application-state and navigation restoration;
- queue restoration across host lifecycle changes;
- provider-action retry and deduplication on both hosts;
- playback transition and prepared-next behavior through the host adapter;
- settings round trips through a shared contract;
- storage migrations and driver parity;
- Android service reconnection after Activity recreation;
- capability-gated action/UI behavior.

The durable invariant-to-test map and extraction gate are recorded in [Naviamp 2.0 Migration Regression Contracts](v2-regression-contracts.md). The first follow-up strengthened Android and Desktop session restoration coverage for internet radio, invalid saved state, duplicate occurrences, and explicit Play Next boundaries.

## Migration Order Derived from the Baseline

1. Introduce contracts and tests around existing behavior without changing ownership.
2. Move pure application state/actions from the two platform composition roots into a shared runtime.
3. Keep Android's service as the playback process owner while adapting it to shared session commands/state.
4. Make Desktop consume the same runtime through Desktop service adapters.
5. Add Apple targets only after shared boundaries no longer depend on Android or JVM application classes.
6. Add the thin iOS host and native playback proof of concept.
7. Complete BASS iOS integration and parity before v2 release preparation.
