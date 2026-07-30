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

`apps/desktop/.../app/Main.kt` owns the JVM entry point. It currently:

- configures native application name, appearance, and icon;
- creates `DesktopAppDependencies` inside the Compose application;
- restores and persists window size;
- owns the Desktop window and minimum size;
- stops playback during normal window closure;
- calls the Desktop-only `NaviampApp` composition root.

`DesktopAppDependencies` constructs Desktop settings, BASS playback, JDBC storage, cache/sidecar services, waveform analysis, popular-track services, and playlist playback.

`DesktopNaviampApp.kt` is approximately 1,684 lines and still assembles substantial product behavior in the Desktop application. Desktop controllers separately own connection lifecycle, playlists, media actions, radio, search, downloads, navigation, Sonic features, settings maintenance, and Now Playing presentation.

### Android

`apps/android/.../app/MainActivity.kt` owns the Activity entry point. It currently:

- handles notification, Android Auto, deep-link, and settings-import intents;
- configures edge-to-edge system bars and safe-area/IME padding;
- requests notification permission;
- calls the Android-only `NaviampAndroidApp` composition root.

`AndroidAppDependencies` constructs Android settings, Android BASS runtime access, Android storage, cache/sidecar services, waveform support, popular-track services, and playlist playback.

`NaviampAndroidApp.kt` is approximately 922 lines and assembles Android product controllers and UI actions. `AndroidPlaybackForegroundService.kt` is approximately 2,367 lines and owns essential service-lifetime playback, MediaSession, notification, restoration, and Android Auto behavior.

The foreground service cannot simply be deleted or made UI-owned. The shared runtime needs a lifecycle-safe playback/session contract that allows Android playback to outlive an Activity while Desktop and iOS supply their own lifecycle adapters.

## Current Platform Ownership

| Concern | Android owner | Desktop owner | Shared migration target |
| --- | --- | --- | --- |
| Application composition | `NaviampAndroidApp` | `NaviampApp` | Shared application runtime and shared Compose entry point |
| Mutable application state | `AndroidAppState` plus service state | Desktop state assembled in `DesktopNaviampApp` | Shared state holder with lifecycle inputs |
| Navigation | Android navigation controller/actions | Desktop route state/controllers | Shared navigation state and actions |
| Provider connection | Android connection controller | Desktop connection lifecycle | Shared connection coordinator with platform secret/TLS services |
| Media browsing/actions | Android media controllers | Desktop media/controllers | Shared application controller using `MediaProvider` |
| Playlists | Android playlist controllers | Desktop playlist controllers | Shared playlist coordinator |
| Radio/Sonic features | Android-specific coordinators | Desktop-specific coordinators | Shared application orchestration around existing domain services |
| Playback policy | Split between app, playlist engine, and service | Split between app and playlist engine | Shared queue/playback coordinator |
| Playback device | Android BASS runtime/service | Desktop BASS engine | Platform `PlaybackEngine`; BASS required on all final v2 platforms |
| Background media controls | Foreground service and MediaSession | Desktop integration | Platform media-session/remote-control adapter |
| Settings | `AndroidSettingsStore` | `DesktopSettingsStore` | Shared settings contracts and models with platform persistence |
| Credentials | Android Keystore protector | Stored in Desktop settings JSON | Platform `SecretStore`; Desktop hardening and iOS Keychain required |
| Database | Android SQLDelight driver in Android storage | JDBC SQLDelight driver in Desktop cache | Platform driver factory with shared storage behavior |
| Files/downloads/cache | Android app/storage locations | Desktop filesystem locations | Platform filesystem/location contract |
| Connectivity/mobile-data policy | Android system connectivity checks | Desktop/network assumptions | Platform connectivity snapshot/flow |
| Cover art and visualizers | Android UI actuals/BASS | Desktop UI actuals/native surfaces | Shared UI contracts with platform actuals |
| Window, menus, updater | Not applicable | Desktop application | Remains Desktop-only |
| Notifications and permissions | Android platform | Desktop platform | Remain platform adapters |
| Android Auto | Android platform/service | Not applicable | Remains Android host integration over shared catalog/queue contracts |

## Shared-Looking Code That Still Contains Platform Product Behavior

These are the first extraction candidates. Move behavior behind shared contracts before changing the launchers:

1. Android and Desktop independently assemble connection, media, playlist, radio, search, download, and Sonic controllers.
2. Each platform independently maps controller functions into shared shell actions and UI state.
3. Android and Desktop settings stores combine persistence mechanics with application-level settings ownership.
4. Storage driver construction and a large amount of storage behavior live inside the application modules rather than behind a narrow platform factory.
5. Popular-track fallback behavior differs between Android and Desktop dependency containers.
6. Playback queue orchestration is split between platform playlist engines, application controllers, and Android's foreground service.
7. Pending provider actions and retry ownership are not yet uniformly platform-independent.
8. Desktop credentials require a secure-store migration rather than being passed through the general settings JSON.

Extraction should proceed by behavior slice, with characterization tests, rather than by moving whole large files at once.

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
| Shared UI/navigation/product behavior | Required | Required | Required |
| BASS playback | Required | Required | Required |
| AVPlayer playback | Unsupported | Unsupported | Unsupported; temporary proof path removed |
| Streaming and downloaded playback | Required | Required | Required |
| Background playback | Required | Required while app runs | Required |
| OS media controls/metadata | Required | Required where OS supports it | Required |
| Queue/session restoration | Required | Required | Required |
| Gapless, ReplayGain, and crossfade | Required where currently supported | Required where currently supported | Required final target; individually capability-gated until verified |
| Equalizer | Required where currently supported | Required where currently supported | Required final target |
| Waveforms and BASS visualizer data | Required | Required | Required final target |
| Audio output selection | Optional by platform/device | Optional by platform/device | Optional by Apple/BASS capability |
| Secure credential storage | Required | Required | Required |
| Endpoint failover and OpenSubsonic capabilities | Required | Required | Required |
| Custom CA/client certificates | Required | Required | Required or explicitly blocked from release pending a security decision |
| Downloads/cache/offline mode | Required | Required | Required |
| Android Auto | Required existing behavior | Unsupported | Unsupported |
| Desktop updater and native packages | Unsupported | Required | Unsupported |
| Control Center/lock-screen commands | Unsupported | Unsupported | Required |

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
