# Naviamp 2.0 Cross-Platform Plan

This document is the durable plan and progress tracker for Naviamp 2.0. Update it in the same commit as each completed milestone so work can move safely between computers and contributors.

## Project Status

- **Target release:** `2.0.0`
- **Working branch:** `feature/v2-cross-platform-app`
- **Status:** Milestone 1 in progress; `core:domain` is Apple-compatible
- **Release policy:** Feature development for the v1 line is frozen. Only bug fixes should be released from v1 while this work is underway.
- **Versioning rule:** Do not change `VERSION` to `2.0.0` until the release-preparation milestone. Development builds and intermediate branches must remain clearly distinguishable from a finished v2 release.
- **Primary objective:** One shared Naviamp application, UI, and behavior hosted by thin Android, Desktop, and iOS applications.
- **Playback objective:** All three platforms must use BASS for the final v2 release. An AVPlayer-based iOS engine may be used as an early proof of concept and fallback while the BASS iOS integration is completed.

## Architectural Destination

```text
Shared Naviamp application
├── application lifecycle and session state
├── navigation and screen state
├── provider and library behavior
├── queue and playback coordination
├── downloads, cache, and offline policy
├── settings and user-facing errors
└── shared Compose UI

Thin platform applications
├── Android host
│   ├── Activity, Service, MediaSession, notifications, and permissions
│   └── Android BASS, HTTP, database, storage, and secret implementations
├── Desktop host
│   ├── window, menu, updater, desktop integration, and packaging
│   └── Desktop BASS, HTTP, database, storage, and secret implementations
└── iOS host
    ├── SwiftUI/UIKit shell, lifecycle, remote commands, and Now Playing
    └── iOS BASS, Darwin HTTP, native database, storage, and Keychain implementations
```

The shared application depends only on contracts such as `PlaybackEngine`, `SecretStore`, `DatabaseDriverFactory`, `ConnectivityMonitor`, and platform capability descriptions. A platform host constructs the appropriate implementations and passes them to the shared application entry point.

Thin does not mean that every platform has identical source code. Operating-system integrations remain native, but product behavior and UI must not be reimplemented separately in each launcher.

## Non-Negotiable Principles

- Keep existing Android and Desktop behavior working throughout the migration.
- Do not create a third, independent iOS application controller.
- Preserve Naviamp's API-first, bounded-paging approach for large libraries.
- Preserve OpenSubsonic capability detection, endpoint failover, TLS options, and provider tests.
- Preserve consistent Android, Desktop, and iOS behavior wherever platform capabilities allow it.
- Use Keychain or an equivalently secure platform store for iOS secrets; never store credentials in `NSUserDefaults`.
- Do not use destructive database migrations for durable user data.
- Do not silently claim feature parity. Unsupported platform functionality must be capability-gated and documented.
- Add tests at shared boundaries before moving behavior out of a working platform implementation.
- Avoid copying Navic source. Its architecture is a reference, not a code dependency.

## Proposed Module Layout

The exact names may change during the first architecture milestone, but ownership should converge on this shape:

```text
core/
├── app/       Shared application assembly, controller, lifecycle, and platform contracts
├── domain/    Models, use cases, queue rules, and playback planning
├── storage/   Shared persistence behavior and schemas
└── ui/        Shared Compose presentation

providers/
└── navidrome/ Shared Navidrome/OpenSubsonic implementation with platform HTTP adapters

apps/
├── android/   Thin Android host and Android service implementations
├── desktop/   Thin Desktop host and Desktop service implementations
└── ios/       Thin Xcode/SwiftUI host and iOS service implementations
```

Use explicit dependency construction unless a dependency-injection framework provides a demonstrated advantage. The architecture needs one composition root per platform, not necessarily a new runtime dependency.

## Milestone Checklist

### Milestone 0: Baseline and Guardrails

- [x] Record the current Android and Desktop test/build commands and expected artifacts. See [Platform Baseline](v2-platform-baseline.md).
- [x] Record the current application entry points, controllers, service ownership, and dependency construction. See [Platform Baseline](v2-platform-baseline.md#current-composition-roots).
- [x] Identify Android-only and Desktop-only behavior currently embedded in shared-looking code. See [Platform Baseline](v2-platform-baseline.md#shared-looking-code-that-still-contains-platform-product-behavior).
- [x] Add or strengthen tests around queue restoration, playback transitions, provider actions, downloads, and settings before moving their owners. See [Migration Regression Contracts](v2-regression-contracts.md).
- [x] Define the platform capability model and the required v2 parity matrix. The initial fail-closed registry is in `core:domain`; the target matrix is in the [Platform Baseline](v2-platform-baseline.md#platform-capability-model).
- [x] Add an architecture decision record for the shared runtime and thin-host approach. See [ADR 0001](architecture/0001-shared-runtime-thin-hosts.md).
- [x] Confirm v1 bug-fix branches remain based on `main` or the designated v1 maintenance branch and are not accidentally coupled to v2 work. This branch is v2-only; v1 fixes start from `main` and are merged independently.

**Exit criteria:** The working Android and Desktop baseline is reproducible, important behavior has regression coverage, and platform boundaries are documented.

### Milestone 1: Make the Shared Dependency Graph Apple-Compatible

- [x] Add `iosArm64` and `iosSimulatorArm64` targets to `core:domain`. Device compilation and the full common-domain simulator test suite pass.
- [ ] Add both iOS targets to `core:storage`.
- [ ] Add both iOS targets to `core:ui`.
- [ ] Add both iOS targets to `providers:navidrome`.
- [ ] Introduce common source-set hierarchy where it reduces duplicate Apple target configuration.
- [ ] Implement iOS time, hashing, URL, encoding, connectivity, and other domain platform functions. Time, SHA-256, and form URL encoding are complete in `core:domain`; remaining module seams are pending.
- [ ] Add the Ktor Darwin engine and iOS provider client construction. The shared domain HTTP client now resolves Darwin; provider client construction remains pending.
- [ ] Explicitly capability-gate provider features whose TLS or certificate behavior is not yet available on iOS.
- [ ] Add the SQLDelight native driver and safe iOS database path construction.
- [ ] Add initial iOS `actual` implementations or honest no-op capability fallbacks for UI platform hooks.
- [x] Run iOS compilation in CI on every v2 change. Branch CI compiles the device domain target and runs its simulator suite; expand the command as each shared module becomes Apple-compatible.

**Exit criteria:** All shared modules compile and link for an Apple Silicon iOS simulator without weakening Android or Desktop tests.

### Milestone 2: Establish the Shared Application Runtime

- [ ] Add the shared application module or equivalent shared composition layer.
- [ ] Define a platform-services container and narrow interfaces for every platform dependency.
- [ ] Move common session initialization into the shared runtime.
- [ ] Move common navigation ownership into the shared runtime.
- [ ] Move common queue, Now Playing, provider-action, settings, download, and cache coordination into shared owners.
- [ ] Eliminate direct Android or Desktop API access from the shared runtime.
- [ ] Add lifecycle inputs for foreground, background, shutdown, and restoration events.
- [ ] Add shared error reporting and capability-aware UI behavior.
- [ ] Test the shared application controller independently of all three platform hosts.

**Exit criteria:** A single runtime can be constructed with fake platform services and drive the main Naviamp flows in tests.

### Milestone 3: Convert Desktop to a Thin Host

- [ ] Move remaining shared product behavior out of the Desktop entry point and Desktop-only controller.
- [ ] Keep window creation, menus, updater integration, file dialogs, desktop notifications, and packaging in the Desktop host.
- [ ] Adapt the existing Desktop BASS implementation to the shared playback contract.
- [ ] Adapt Desktop database, secret, filesystem, connectivity, and HTTP services to the shared platform contracts.
- [ ] Verify macOS, Windows, and Linux packaging assumptions remain valid.
- [ ] Run Desktop tests and launch the macOS application for functional verification.

**Exit criteria:** Desktop launches the shared runtime and contains only host/platform integration code outside shared modules.

### Milestone 4: Convert Android to a Thin Host

- [ ] Move remaining shared product behavior out of the Activity, Android app controller, and playback service.
- [ ] Keep Activity lifecycle, foreground service, MediaSession, notifications, permissions, Android Auto, and storage selection in the Android host.
- [ ] Adapt the Android BASS implementation to the shared playback contract.
- [ ] Preserve service-owned playback when the UI process or Activity is recreated.
- [ ] Adapt Android database, secret, filesystem, connectivity, and HTTP services to the shared platform contracts.
- [ ] Verify downloads, cache, offline playback, background playback, and queue restoration on a physical device.
- [ ] Run Android tests, assemble the application, install it, and launch it.

**Exit criteria:** Android launches the same shared runtime as Desktop without losing background playback or Android-specific integrations.

### Milestone 5: Add the Thin iOS Application

- [ ] Create an Xcode project and SwiftUI application under `apps/ios`.
- [ ] Export the shared Compose application through a static Kotlin framework.
- [ ] Use `embedAndSignAppleFrameworkForXcode` from the Xcode build phase.
- [ ] Add the minimal `UIViewControllerRepresentable` wrapper around the shared Compose entry point.
- [ ] Configure bundle identifiers, deployment target, orientations, icons, entitlements, and background audio mode.
- [ ] Construct the iOS platform-services container during application startup.
- [ ] Handle foreground/background lifecycle transitions and state restoration.
- [ ] Compile and launch on an iOS simulator.
- [ ] Compile and launch on a physical iPhone.

**Exit criteria:** The shared Naviamp UI can connect to a server and browse real content on simulator and device without a separate iOS product-logic implementation.

### Milestone 6: iOS Native Playback Proof of Concept

This milestone is deliberately temporary. It validates the shared playback boundary and Apple operating-system integrations before BASS is introduced.

- [ ] Implement the shared playback contract with `AVPlayer` or `AVQueuePlayer`.
- [ ] Support authenticated streaming URLs and downloaded local files.
- [ ] Support play, pause, seek, previous, next, repeat, and shuffle.
- [ ] Configure `AVAudioSession` for background music playback.
- [ ] Implement Control Center and lock-screen commands through `MPRemoteCommandCenter`.
- [ ] Publish metadata and artwork through `MPNowPlayingInfoCenter`.
- [ ] Handle interruptions, route changes, headphone removal, and audio-session reactivation.
- [ ] Verify queue advancement, restoration, provider playback reports, and offline playback.
- [ ] Document features intentionally unavailable in the temporary engine.

**Exit criteria:** End-to-end iOS playback proves the shared runtime and Apple lifecycle integrations. This is not sufficient for the final v2 release.

### Milestone 7: Integrate BASS on iOS

- [ ] Confirm the BASS iOS license and redistribution requirements for Naviamp releases.
- [ ] Vendor or reproducibly acquire the required BASS iOS binaries and headers.
- [ ] Define a maintainable Kotlin/Native or Objective-C/Swift bridge rather than duplicating playback policy.
- [ ] Implement the shared playback contract using BASS on iOS.
- [ ] Support secure authenticated streaming and local downloaded/cache files.
- [ ] Integrate the BASS engine with `AVAudioSession`, route changes, interruptions, remote commands, and Now Playing metadata.
- [ ] Implement queue preparation and playback transitions without blocking the main thread.
- [ ] Port or capability-gate ReplayGain, gapless playback, crossfade, sample-rate handling, equalization, visualizer data, waveform behavior, and other BASS-backed features.
- [ ] Verify resource disposal, background/foreground cycles, audio-route changes, memory pressure, and long-running playback.
- [ ] Compare behavior and performance with Android and Desktop using the same test scenarios.
- [ ] Remove the AVPlayer engine or retain it only as an explicitly documented fallback if it still provides operational value.

**Exit criteria:** BASS is the normal playback engine on iOS and meets the agreed v2 playback parity requirements.

### Milestone 8: Cross-Platform Product Parity

- [ ] Complete the platform capability matrix and resolve all unexplained differences.
- [ ] Verify login, endpoint failover, custom headers, secure secrets, and reconnect behavior.
- [ ] Verify library paging with very large collections.
- [ ] Verify search, albums, artists, multi-artist navigation, playlists, favorites, radio, and smart playlists.
- [ ] Verify downloads, keep-downloaded policies, cache budgets, offline mode, and storage cleanup.
- [ ] Verify queue restoration, track transitions, artwork, backgrounds, lyrics, waveforms, and visualizers.
- [ ] Verify playback reporting and pending provider actions.
- [ ] Verify accessibility, touch targets, keyboard behavior where applicable, safe areas, and compact layouts.
- [ ] Verify migration of existing Android and Desktop data and settings.
- [ ] Add iOS-specific user documentation and troubleshooting.

**Exit criteria:** Differences are intentional, tested, capability-gated, and documented rather than accidental omissions.

### Milestone 9: Build, Distribution, and Release Automation

- [ ] Add simulator compile and test jobs to continuous integration.
- [ ] Add `xcodebuild archive` verification.
- [ ] Produce an installable iOS IPA artifact using documented signing inputs.
- [ ] Add TestFlight distribution after signing and App Store Connect configuration are available.
- [ ] Preserve Android APK/AAB and Desktop package generation.
- [ ] Ensure release workflows build all supported platforms from the same tag.
- [ ] Document secrets, certificates, provisioning profiles, and renewal procedures without committing sensitive material.
- [ ] Verify artifacts on clean machines or CI runners.

**Exit criteria:** A tag can reproducibly produce the expected Android, Desktop, and iOS deliverables.

### Milestone 10: Naviamp 2.0 Release Preparation

- [ ] Resolve or explicitly defer every checklist item required for the agreed v2 scope.
- [ ] Run the complete shared, provider, Android, Desktop, and iOS test suites.
- [ ] Complete physical-device testing on Android and iOS.
- [ ] Complete macOS, Windows, and Linux Desktop smoke testing.
- [ ] Write migration notes and user-facing release notes.
- [ ] Update the in-app changelog.
- [ ] Change `VERSION` to `2.0.0` using the project versioning script.
- [ ] Update `VERSION_CODE`, changelog, build metadata, and documentation.
- [ ] Build and inspect every release artifact.
- [ ] Merge the v2 branch into `main` using the agreed release flow.
- [ ] Create and push the annotated `v2.0.0` tag.
- [ ] Verify the remote `main` ref, tag ref, peeled annotated tag, and published release artifacts.

**Exit criteria:** Naviamp 2.0.0 is released and the repository no longer depends on the long-lived migration branch.

## Platform Service Inventory

Use this table to track contract and implementation coverage. Add rows when new platform coupling is discovered.

| Service | Shared contract | Android | Desktop | iOS |
| --- | --- | --- | --- | --- |
| Application lifecycle | [ ] | [ ] | [ ] | [ ] |
| Playback engine | Existing; adapt | BASS | BASS | AVPlayer POC, then BASS |
| Media/remote controls | [ ] | MediaSession | Desktop integration | MPRemoteCommandCenter |
| Now Playing metadata | [ ] | MediaSession | Desktop integration | MPNowPlayingInfoCenter |
| HTTP engine | [ ] | OkHttp | CIO | Darwin |
| TLS and client certificates | [ ] | [ ] | [ ] | [ ] |
| Database driver | [ ] | Android SQLite | JDBC/native | Native SQLite |
| Secret storage | [ ] | Keystore-backed | OS-appropriate | Keychain |
| Files and storage locations | [ ] | [ ] | [ ] | [ ] |
| Connectivity | [ ] | [ ] | [ ] | NWPathMonitor or equivalent |
| Downloads/background work | [ ] | Foreground/background service | Desktop job | iOS-compatible strategy |
| Notifications | [ ] | [ ] | [ ] | [ ] |
| Cover art loading | [ ] | [ ] | [ ] | [ ] |
| Waveforms/visualizers | [ ] | BASS | BASS | BASS target |
| Sharing and file pickers | [ ] | [ ] | [ ] | [ ] |
| Updates/distribution | Platform-specific | App release flow | In-app updater/packages | App Store/TestFlight |

## Multi-Computer Working Agreement

Before starting work on any computer:

1. Fetch `origin`.
2. Check out `feature/v2-cross-platform-app`.
3. Pull with fast-forward only.
4. Read this document and choose the first unchecked item whose prerequisites are complete.
5. Confirm the worktree is clean and inspect recent commits before editing.

While working:

- Keep commits scoped to one checklist item or a small coherent group.
- Update this document in the same commit when an item is completed or its design changes.
- Add notes beneath an item when a decision would otherwise live only in chat history.
- Do not mark an item complete until its exit evidence exists: tests, builds, launch verification, or documented inspection as appropriate.
- Push completed commits promptly so another computer does not start from stale state.
- Rebase or fast-forward from the remote branch before beginning a new slice; avoid parallel edits to the same files when possible.
- Do not commit local signing material, credentials, downloaded proprietary binaries without redistribution approval, IDE state, or generated build output.

When handing work to another computer, record:

- the last completed checklist item;
- the next recommended item;
- commands run and their results;
- any device-only or environment-only verification still needed;
- the pushed commit hash.

## Decision Log

Record architecture decisions here or link a dedicated decision record.

| Date | Decision | Reason |
| --- | --- | --- |
| 2026-07-16 | Target this migration for Naviamp 2.0.0. | The shared-runtime and three-platform application structure is a major architectural change. |
| 2026-07-16 | Freeze v1 feature releases during the migration; permit bug fixes. | Keeps the long-running migration tractable while allowing maintenance releases. |
| 2026-07-16 | Use thin Android, Desktop, and iOS hosts around one shared application runtime. | Prevents platform behavior from diverging and makes features shared by default. |
| 2026-07-16 | Allow AVPlayer for an early iOS proof of concept, but require BASS as the final iOS playback engine. | Native playback de-risks Apple lifecycle integration; BASS provides the desired final performance and advanced features. |
| 2026-07-16 | Start from current `main`; do not merge the old `codex/ios-app` scaffold. | The old branch is incomplete and substantially diverged, but it may be inspected for isolated lessons. |
| 2026-07-16 | Retain Naviamp's provider, paging, modular boundaries, and test-first behavior. | These are stronger foundations than the reference application and are required for large libraries and advanced server support. |

## Current Handoff

- **Last completed item:** Added Apple targets, Darwin HTTP support, native platform actuals, native-safe common code, simulator tests, and branch CI for `core:domain`.
- **Next recommended item:** Add Apple targets and a Native SQLDelight driver boundary to `core:storage`.
- **Verification:** `core:domain` compiled for iOS device and simulator; its full simulator, JVM, and Android test suites passed.
- **Known blockers:** None.
