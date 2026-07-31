# V2 Core-First Platform Audit

This audit is the authoritative ownership map for the Naviamp 2.0 migration. It supersedes any earlier conclusion that a host was "thin" merely because its product decisions had been split into smaller files or because it rendered shared composables.

Naviamp Core is the product. Android, Desktop, and iOS are operating-system adapters. A feature is not truly shared when common code declares its models or callbacks but each host independently constructs its state, interprets the callback, chooses errors, or coordinates the work.

The narrower [Shared Action Parity Audit](v2-shared-action-parity-audit.md) remains useful evidence for individual callback defects. This document expands the audit to the complete product, defines the target core, and controls the build-then-delete sequence for the rest of Milestone 4.

## Audit Rules

Every feature or implementation difference has exactly one classification:

- **Core product:** Product state, UI, navigation, menus, commands, validation, orchestration, presentation policy, and user-facing outcomes. It must be implemented once in common code.
- **Core plus host effect:** Core owns the intent and outcome; a narrow host port performs an operating-system or native operation. All hosts consume the same core command.
- **Valid host integration:** The code directly integrates with an operating-system API that has no useful common implementation. The host still consumes shared state and commands whenever possible.
- **Migration debt:** Parallel or contradictory host behavior without a concrete operating-system constraint. It must move to core and then be deleted from the hosts.

The following are not valid reasons for a product difference:

- a host does not currently have a loader or executor;
- the existing Android and Desktop classes were written differently;
- a callback has a default no-op;
- data is easier to find in one host's state object;
- a feature has not yet been wired on a new platform;
- moving the same product logic into a smaller platform file.

## Current Structural Baseline

### 2026-07-31 current-host re-audit

The 2026-07-21 measurements below are retained as migration history, not current architecture. Android, Desktop, and iOS now mount `NaviampCoreApp` and do not define independent product routes, screen state, action catalogs, or feature controllers. The current capability registries match the mounted services: Android declares background playback, system media controls, downloads/offline playback, update checks, Android Auto, secure storage, file selection, and its TLS adapters; iOS declares background playback, system media controls, downloads/offline playback, update checks, secure storage, file selection, and opt-in insecure TLS. Android and iOS intentionally omit software volume; Desktop intentionally declares it. Desktop has no mounted system-media-control adapter, and iOS intentionally omits custom-CA/client-certificate controls because those native secure adapters do not exist.

The production-host file review produced these accountability groups:

- `apps/android`: `MainActivity`, `AndroidNaviampApplicationRuntime`, `AndroidNaviampPlaybackRuntime`, `AndroidNaviampPlaybackService`, `AndroidNaviampArtworkProvider`, and `AndroidCoreUriPickerEffects` are justified by Activity/service lifetime, MediaSession/notification/Android Auto, Android image/URI APIs, or native playback lifetime. `AndroidNaviampCoreCatalog` and `AndroidCapabilityPresentation` are mechanical composition and immutable OS-service facts only.
- `platforms/android` connection, diagnostics, external-URI, playback, security, settings, database-driver, dispatcher, location, and file-service adapters directly touch Android/JNI/Java filesystem/Keystore/ContentResolver/network APIs. They may translate types and manage native resources but may not own product policy.
- `apps/desktop`: `Main`, `DesktopNaviampCoreHost`, `DesktopStatsForNerdsWindow`, and `DesktopComposition` are justified by JVM process/window lifetime, a separate native diagnostics window, and service construction. No Desktop product controller survives.
- `platforms/desktop` files are limited to JVM/native BASS and GPU loading, AWT/window/Dock/taskbar/dialog/update integration, OS credential stores, JDBC-driver creation, connectivity, and JVM path/byte effects.
- `apps/ios/NaviampIosApplication` is mechanical process composition over Apple-selected directories and services. `platforms/ios` files directly wrap UIApplication/MediaPlayer/AVAudioSession, BASS cinterop, UIKit document selection, calendar/time, native tag reads, and Foundation/POSIX byte operations.

The storage debt found by this re-audit is resolved. Android and Desktop now mount `StorageCoreRepositoryCatalog` and `StorageAudioStore`, matching iOS ownership of SQL mapping, media-source identity migration, playback sessions/history, library indexing, cache limits and eviction, download replacement, missing-file repair, sidecars, waveforms, lyrics, pending actions, presets, and maintenance. Android retains SQLite-driver selection, Keystore, mutable app directories, `File` existence, atomic byte writes, exact verified deletion, and its I/O dispatcher. Desktop retains JDBC-driver selection, OS credential protection, `Path` existence, atomic byte writes, exact verified deletion, database-size lookup, and its work dispatcher. Eleven Android repository/audio implementations and four Desktop storage/audio implementations were deleted. `verifyCoreFirstArchitecture` now rejects their return and rejects generated SQL query/row mapping imports in host production code. The final file-by-file platform audit remains the blocking ownership gate.

### 2026-07-31 Android file-by-file exit audit

This pass reviewed all 40 surviving Android Kotlin production files after the shared-storage migration,
plus the application and library manifests and Android packaging resources. Each surviving file has an
irreducible Android, JVM-on-Android, or native-ABI boundary. The table records the exact constraint;
mechanical composition files may select implementations but do not own product behavior.

| Production file | Concrete Android/native constraint | Audit result |
| --- | --- | --- |
| `apps/android/.../AndroidCapabilityPresentation.kt` | Immutable declaration of Activity, foreground-service, MediaSession, Android Auto, picker, Keystore, TLS, download, and updater adapters actually mounted by this APK. | Mechanical capability facts only. |
| `apps/android/.../AndroidCoreUriPickerEffects.kt` | Activity Result launchers, `Uri`, `ContentResolver.takePersistableUriPermission`, and Activity disposal/cancellation lifetime. | Thin picker effect. |
| `apps/android/.../AndroidNaviampApplicationRuntime.kt` | Android process lifetime, `Context`, `ConnectivityManager`, main dispatcher, and process-owned native BASS lifetime shared by Activity and service. | Thin process resource owner. |
| `apps/android/.../AndroidNaviampArtworkProvider.kt` | Android `ContentProvider`/`ParcelFileDescriptor` bridge required to expose authenticated artwork to MediaSession and automotive clients as local `content://` URIs. | Thin native publication adapter. |
| `apps/android/.../AndroidNaviampCoreCatalog.kt` | Selects Android `Context`, SharedPreferences, Keystore, SQLite, filesystem locations, TLS, URI pickers, clock/time-zone facts, and native playback/analyzer implementations. | Mechanical Core composition only. |
| `apps/android/.../AndroidNaviampPlaybackRuntime.kt` | Process-local handoff to an Android foreground service plus `startForegroundService`/`stopService` lifecycle effects. | Core owns the retention decision; Android executes it. |
| `apps/android/.../AndroidNaviampPlaybackService.kt` | `MediaBrowserServiceCompat`, `MediaSessionCompat`, notification channel/actions, trusted-controller checks, Android Auto paging, and foreground-service rules. | Native translation only after relative seek and shuffle/repeat selection moved to the Core bridge. |
| `apps/android/.../MainActivity.kt` | `ComponentActivity`, system bars/insets, Android intent/deep-link decoding, notification runtime permission, and Compose window mounting. | Thin Activity host. |
| `core/domain/.../AudioByteStoreService.android.kt` | Java `MessageDigest` implementation of the common SHA-256 primitive. | Irreducible actual. |
| `core/domain/.../SharedHttpPlatform.android.kt` | Android/JVM wall-clock implementation. | Irreducible actual. |
| `core/domain/.../SharedUrlEncoding.android.kt` | Java `URLEncoder` implementation. | Irreducible actual. |
| `core/domain/.../PopularTime.android.kt` | Android/JVM wall-clock implementation. | Irreducible actual. |
| `core/ui/.../NaviampSleepTimerEffects.android.kt` | Android/JVM wall-clock implementation used by shared sleep-timer presentation. | Irreducible actual. |
| `core/ui/.../NaviampTooltip.android.kt` | Touch-platform Compose behavior intentionally omits desktop hover tooltips. | Focused input-mode actual. |
| `core/ui/.../PlatformCoverArt.android.kt` | Android `BitmapFactory`, `Canvas`, app cache files, bitmap recycling, and native-image decoding. | Rendering effect only; generated-radio tile parsing and geometry now live in common UI. |
| `core/ui/.../PlatformLiveVisualizerSurface.android.kt` | Android `RuntimeShader`, GLES/`GLSurfaceView`, `BitmapShader`, Android native canvas/text masks, API-level selection, and explicit GPU/bitmap release. | Focused renderer integration; renderer selection and shader/render policy remain common. |
| `platforms/android/.../AndroidStorageDependencies.kt` | Android `Context` construction and `File` path exposure around shared repository contracts. | Mechanical delegation only; unused home mapping removed. |
| `platforms/android/.../AndroidCoreProviderSessions.kt` | Selects Android JVM TLS-default mutation needed by BASS/native URL loading. | Provider-common session policy remains shared. |
| `platforms/android/.../AndroidCoreDiagnosticsPort.kt` | `Build.VERSION`, manufacturer/model, and supported ABI facts. | Native diagnostics facts only. |
| `platforms/android/.../AndroidCoreExternalUriPort.kt` | Android `ACTION_VIEW`, `Uri`, and new-task launch requirements. | Thin effect. |
| `platforms/android/.../AndroidAudioTagReader.kt` | Java `File`/stream access to a host-selected local audio path. | Parsing and tag behavior remain common. |
| `platforms/android/.../AndroidAudioWaveformAnalyzer.kt` | Android `Uri` translation for BASS local-file decode. | Thin delegate; unused host TLS state removed. |
| `platforms/android/.../AndroidBassAudioBackend.kt` | Converts Kotlin calls/results to the Android JNI BASS ABI. | Native ABI adapter; duplicated tag parsing moved to Core. |
| `platforms/android/.../AndroidBassJni.kt` | JNI external declarations and Android log callback. | Irreducible ABI surface. |
| `platforms/android/.../AndroidBassNativeLoader.kt` | `System.loadLibrary`, Android ABI packaging, and native add-on load order/reporting. | Irreducible loader. |
| `platforms/android/.../AndroidBassPlaybackEngineRuntime.kt` | Android `Uri`, JVM `File`, I/O dispatcher, clock, and synchronization primitive supplied to the shared engine. | Narrow runtime effects. |
| `platforms/android/.../AndroidFocusedBassPlaybackEngine.kt` | `AudioManager` focus callbacks, platform duck/pause/resume semantics, `PowerManager.WakeLock`, and Android elapsed time/logging. | Focused native lifecycle wrapper around Core BASS. |
| `platforms/android/.../AndroidPlaybackAudioAssets.kt` | Java `File` existence/size and file-URI conversion for shared opaque stored paths. | Thin file-fact adapter. |
| `platforms/android/.../AndroidPlaybackTls.kt` | JVM global `SSLContext`/`HttpsURLConnection` defaults required by native BASS, including Android-selected certificate files. | Irreducible native/JVM TLS effect. |
| `platforms/android/.../AndroidCredentialProtector.kt` | Android Keystore key generation plus JVM AES/GCM cipher access. | Secure-storage adapter only. |
| `platforms/android/.../AndroidCoreSettingsSyncPort.kt` | `ContentResolver`, opaque Android tree/document URIs, and Activity-result picker effects. | Core retains sync/merge/status policy. |
| `platforms/android/.../AndroidCoreSettingsValueStore.kt` | Android SharedPreferences bytes/strings and one-time migration from the released Android preference keys. | Shared schema/policy remains Core-owned. |
| `platforms/android/.../AndroidSettingsCredentialStore.kt` | Separate Android SharedPreferences storage encrypted through Keystore; migration clears legacy plaintext keys. | Focused secure persistence effect. |
| `platforms/android/.../AndroidSettingsStore.kt` | Persists Android document-tree URI grants and auto-export flag in SharedPreferences. | Narrow configuration store. |
| `platforms/android/.../AndroidSettingsSyncFile.kt` | Android Storage Access Framework `DocumentsContract`, provider capability flags, and `ContentResolver` streams. | Native document effect; unused legacy document-store wrapper removed. |
| `platforms/android/.../AndroidAudioFileServices.kt` | Mutable Android app directories, Java atomic temp-file replacement, filesystem existence, and exact verified deletion. | Byte/file effects only; ownership and eviction remain shared. |
| `platforms/android/.../AndroidStorage.kt` | Android driver/Keystore/file/dispatcher composition over `StorageCoreRepositoryCatalog` and `StorageAudioStore`. | Mechanical storage graph; queue-protection policy moved to common storage. |
| `platforms/android/.../AndroidStorageDatabaseDriverFactory.kt` | Android SQLDelight driver, `Context.getDatabasePath`, read-only `SQLiteDatabase` version inspection, and `deleteDatabase`. | Narrow database effect. |
| `platforms/android/.../AndroidStorageLocations.kt` | Android internal/external app directories and `Environment.isExternalStorageRemovable` facts. | Native location enumeration only. |
| `providers/navidrome/.../NavidromeAndroidPlatform.kt` | Ktor CIO Android/JVM engine plus Java trust/key-manager and PKCS12/X.509 loading. | Provider protocol stays common; shared timeout policy is now consumed here. |

`apps/android/src/main/AndroidManifest.xml` is justified by Android permission, Activity, foreground
media service, exported MediaBrowser, artwork provider, backup, and automotive declarations.
`platforms/android/src/main/AndroidManifest.xml` is intentionally empty library packaging metadata.
The drawable/XML resources are notification/MediaSession icons, Android Auto declaration, backup
rules, and Android theme metadata; generated launcher assets and packaged BASS libraries are build or
native-resource inputs rather than product-policy owners.

The audit extracted the following portable behavior before accepting the survivors:

- BASS/ICY stream-tag parsing moved from duplicate Android and Desktop backends to `core:domain`.
- generated-radio artwork URL parsing, defaults, dimensions, and geometry moved from Android/JVM
  renderers to `core:ui`; hosts now only draw/encode with their native image APIs.
- current/upcoming queue protection during audio-cache eviction moved from Android-only code to
  `StorageCoreRepositoryCatalog` and is now consumed by Android, Desktop, and iOS.
- relative rewind/fast-forward bounds and shuffle/repeat selection moved from the Android
  MediaSession callback to `NaviampCoreExternalPlaybackBridge`.
- Navidrome connect/request/socket timeout values moved from three actual implementations to
  provider `commonMain`.
- unused `AndroidStorageDispatcher`, `AndroidSettingsSyncMirrorStore`, the obsolete
  `AndroidSettingsSyncDocumentStore` wrapper, dead waveform TLS state, and the final stale host-debt
  allowlist entries were removed.

Focused shared tests and Android/Desktop compilation pass. No unexplained Android product behavior,
portable repository, scheduler, retry policy, state machine, or feature controller remains.

The 2026-07-21 source audit measured Kotlin production and test code as follows. Counts are a diagnostic, not a quota; native integrations can legitimately be large, but product implementations cannot remain duplicated in them.

| Area | Production lines | Test lines | Finding |
| --- | ---: | ---: | --- |
| `core/domain` common | 19,679 | 18,991 | Strongest shared layer; owns models and many policies. |
| `core/app` common | 2,515 | 2,673 | Too small to be the complete application. It does not yet own the full screen-state/action graph. |
| `core/ui` common | 24,805 | 2,700 | Most product presentation exists here, but action contracts remain permissive and composition is incomplete. |
| `core/storage` common | 351 | 30 | Shared schema/repository boundaries exist, but contract coverage is thin. |
| Navidrome provider common | 2,550 | 2,668 | Provider behavior is substantially shared and well tested. |
| Android host | 20,381 | 877 | Contains extensive product composition, state adaptation, and orchestration in addition to valid Android integration. |
| Desktop host | 17,927 | 1,726 | Contains an independent product composition root, action graph, controllers, and Desktop-only presentation wrappers. |

The top-level evidence is decisive:

- Android mounts `NaviampSharedAppShell`, but its 848-line `NaviampAndroidApp`, `AndroidAppState`, `AndroidAppShellUiStateFactory`, and `AndroidMainShellActions` still construct and interpret the product independently.
- Desktop does not mount the complete shared shell. Its 1,352-line `DesktopNaviampApp` mounts `NaviampProductRouteContent`, `DesktopShellChrome`, `DesktopSettingsPanel`, Desktop dialogs, and a separately assembled action/state graph.
- `NaviampAppShellActions` still supplies default no-op implementations for most action groups. A host can therefore compile while visible product behavior is absent.
- `core:app` and `core:ui` both depend on `core:domain` but not on each other. There is no common presentation-composition layer capable of binding the shared runtime to the shared UI.
- Android and Desktop each retain product-named controllers for search, radio, playlists, media, downloads, connection, mix builders, Now Playing, and playback coordination. Many invoke shared policies, but their transaction ownership and state publication remain host-built.

## Feature and Platform Difference Matrix

“Same” means the intended Naviamp behavior must be inherited from core. “Host” means only the named operating-system effect is allowed to differ.

| Product area | Android today | Desktop today | Classification and decision |
| --- | --- | --- | --- |
| App lifecycle and composition | Shared runtime is launched, but `NaviampAndroidApp` creates most state/controllers/actions. | Shared runtime is launched, but `DesktopNaviampApp` creates a separate graph. | **Migration debt.** Create one common `NaviampCore` composition and one host-neutral app entry. Hosts provide adapters and lifecycle events only. |
| Shared shell | Mounts `NaviampSharedAppShell`. | Bypasses it for product routes, shell chrome, settings wrapper, and dialogs. | **Migration debt.** Both must mount the same common entry. Responsive layout belongs in common UI; native window ownership remains Desktop. |
| Navigation and back behavior | Android route lambdas and system back adapt shared history. | Desktop route mapping and bottom-navigation helpers add separate policy. | **Core product plus host effect.** Core owns all route/history/Now Playing commands. Android system back and Desktop window navigation only dispatch them. |
| Home | Host action factory performs refresh transaction and state publication. | Host Home controller performs the corresponding work. | **Migration debt.** Core owns refresh, loading, stale request, status, selection, and media intent. Provider calls are ports. |
| Search | Android and Desktop own separate query/search controllers. | Includes a Desktop-only compact search field. | **Migration debt.** Query, debounce, cancellation, clear, results, and navigation belong to core. A host may only provide keyboard/focus integration. |
| Library | Android API paging controller. | Desktop library controller and Desktop bottom-navigation behavior. | **Core product plus host effect.** Core owns query, paging state, refresh, A-Z jump intent, selection, and errors. Hosts provide scrolling/viewport effects only where Compose cannot. |
| Album detail | Shared UI and sealed commands; separate host resolution/execution assembly remains. | Same contract but independently wired. | **Migration debt.** Keep sealed commands; move resolution, status, and transaction coordination to the common composition. Hosts retain playback/download execution ports. |
| Artist detail | Shared UI/commands and common catalog loading now exist. | Separate Desktop controller and action assembly. | **Migration debt.** Core owns catalog/popular/similar selection and status. No host-specific artist feature set is valid. |
| Playlist list/detail | Shared UI and sealed detail commands; Android controller owns list/detail transactions. | Separate playlist and smart-playlist controllers. | **Migration debt.** Core owns list/detail state, sorting, smart-editor flow, mutation results, and playback intent. Provider mutation is a port. |
| Smart-playlist authentication | Shared stale-source and password-recovery UI/policy are present. | Same, with separate controller wiring. | **Core product.** Finish moving the transaction to core; hosts must not interpret authentication outcomes. |
| Media-row menus/actions | Common action catalog and shared rows emit sealed album, artist, and playlist commands directly. | Same Core contract; host migration is intentionally deferred. | **Core complete; host deletion pending.** The broad kind/action conversion bridge and permissive generic handlers are gone. Finish removing competing direct callbacks before mounting both hosts on the Core graph. |
| Favorites and metadata mutations | Common provider-action policies exist; host controllers coordinate lookup and status. | Parallel host controller. | **Migration debt.** Core owns stable-ID resolution, optimistic/pending state, retry, and messages. Provider mutation is a port. |
| Radio DJ, seeded radio, Library Radio | Many algorithms and continuation decisions are shared; Android transaction/controller remains. | Parallel Desktop controllers remain. | **Migration debt.** Core owns request/session/progress/refill/result state. Hosts only load through provider ports and execute queue/playback commands. |
| Internet Radio | Shared screen/edit UI; host controllers load, save, select, and play. | Separate Desktop Internet Radio controller. | **Core product plus host effect.** Core owns stations, edit/save/delete validation and selection. Stream playback uses the shared playback port. |
| Mix builders, Sonic Mix, Sonic Path | Separate Android state/controllers bind shared UI. | Parallel Desktop controllers bind shared UI. | **Migration debt.** Entire builder state machine, commands, results, save intent, and status belong to core; provider algorithms are already common services. |
| Queue and playback policy | Existing Android Activity/service and playlist-engine implementations remain pending host conversion. | Existing Desktop playlist engine/controller graph remains pending host conversion. | **Core replacement complete; host deletion pending.** Core owns queue/current-target state, transport intent, repeat, shuffle, Play Next, seek, volume, sleep timer, and commands. BASS engines execute narrow effects. |
| Now Playing | Existing Android action and sidecar controllers remain pending host conversion. | Existing Desktop Now Playing state/action/controller graph remains pending host conversion. | **Core replacement complete; host deletion pending.** Core owns the complete screen mapping and every shared user intent through focused playback and media controllers. Hosts provide BASS, media-control publication, visualizer frames, file/tag analysis, connectivity, and native dialogs only. |
| Lyrics, waveform, artwork, backgrounds, visualizers | Shared UI/policy with Android analyzers/loaders/renderers. | Shared UI/policy with JVM analyzers/loaders plus native visualizer paths. | **Core plus host effect.** Source priority, cache/status, rendering choice, and presentation remain common. Byte decoding/GPU/native audio analysis are narrow adapters. |
| Downloads and keep-downloaded | Shared job/policy pieces; Activity-scope execution and Android filesystem/controller. | Separate Desktop controller/filesystem execution. | **Migration debt plus host effect.** Core owns jobs, reconciliation, retry/cancel, progress, ownership, and screen state. Host supplies transfer/filesystem/background-work executor. Android durability remains a device-test decision. |
| Offline/cache/sidecars | Shared repositories/policy with many Android stores. | Parallel Desktop cache facade and stores. | **Core plus host effect.** Schema, eviction, ownership, consistency, and diagnostics are common. Paths, byte I/O, atomic moves, and database drivers are host adapters. |
| Connection/auth/failover | Common connection controller and provider logic exist; Android session controller still owns product form/result flow. | Desktop lifecycle/form/session implementations remain. | **Migration debt plus host effect.** Core owns form state, validation, saved-source selection, heartbeat/renewal, failover policy, errors, and navigation. TLS/client/credential access are ports. |
| Settings values | Shared models/UI; Android action factory persists and applies changes. | Separate settings store/action factory and Desktop wrapper. | **Migration debt.** Core owns normalization, mutation, restart/redownload consequences, persistence intent, and state. Stores and native pickers are ports. |
| Settings import/export | Shared synchronization policy with Android URI documents. | Shared policy with native directory/file dialogs. | **Valid host integration around shared core.** Core owns sync/merge/conflict/status; hosts own picker UI, URI grants, and document bytes. |
| Storage-location selection | Android exposes internal/removable/app-specific choices. | Desktop exposes arbitrary writable directories. | **Valid host capability.** Core presents a common list and selection command; hosts enumerate/validate locations and return opaque IDs/labels. |
| About/version/update | Shared About UI; Android declares app updates available. | Uses the shared update checker but declares application updates unavailable. | **Migration debt and registry defect.** Version/update policy and UI are common. Installer execution may differ. Correct Desktop capability truth before relying on it. |
| Secure credentials | Android Keystore adapter and capability declaration. | Desktop Keychain/DPAPI/Secret Service adapter exists, but capability is declared unavailable. | **Valid host adapter; registry defect.** One common secret contract and security requirement; correct Desktop capability truth. |
| Diagnostics/Stats for Nerds | Shared diagnostics models exist; Android diagnostics are host-shaped. | Product UI and models live in a Desktop-only window. | **Migration debt plus host window effect.** Common diagnostics state/content; Desktop may host it in a separate window and mobile may use a route/dialog. |
| App chrome and responsive layout | Shared full shell and mobile bottom bar. | Desktop-specific bottom navigation, menus, route wrapper, and chrome. | **Migration debt unless tied to a native window API.** Responsive Compose chrome belongs in common. Native menu bar, dock/taskbar, window controls are ports/effects. |
| File dialogs and external links | Android Activity Result/Intent APIs. | AWT/Swing/native file dialogs and browser integration. | **Valid host integration.** Shared commands request pick/open/share operations through narrow ports and consume typed results. |
| Background playback | Foreground service, notification, MediaSession, audio focus, wake lock. | Process/window lifecycle with BASS. | **Valid host integration around common session ownership.** Core session/queue survives UI hosts; Android service mechanics remain Android. |
| System media controls | Android MediaSession/notification implemented and declared. | No equivalent capability currently declared. | **Valid platform integration, not product divergence.** Transport commands and metadata are common. Desktop dock/taskbar/media-key work is a future adapter, not a separate feature implementation. |
| Automotive UI | Android Auto browse/search/service implementation. | No Desktop analog. Future iOS CarPlay. | **Valid host integration.** A common automotive catalog/playback command surface must feed Android Auto and CarPlay; Android `MediaBrowserCompat` and Apple templates remain native. |
| Application lifecycle, permissions, intents | Activity, service, runtime permissions, URI grants, share/deep-link intents. | Window/process lifecycle and desktop activation. | **Valid host integration.** Hosts translate OS events into common lifecycle/command inputs only. |
| Playback engine and native audio | Android BASS/JNI, audio focus, routes, wake locks. | Desktop BASS/JNI, output-device selection, native library loading. | **Valid host adapter.** `PlaybackEngine` and feature contracts remain common; native execution and device enumeration remain host-specific. |
| Database/filesystem/network/TLS | Android SQLDelight driver, `ContentResolver`, files, platform TLS. | JVM driver, paths/files, OS key stores, platform TLS. | **Valid host adapters.** No product decisions or user-facing error policy may remain in them. |
| Updates, packaging, windows, dock/taskbar | APK install/update effects. | DMG/MSI/DEB/RPM, updater, windows and taskbar icon. | **Valid host integration.** Availability/result state and user flow remain common; packaging/install APIs remain native. |

## Capability Registry Corrections

Capabilities describe genuine service facts; they must never compensate for incomplete product wiring.

Confirmed corrections or follow-ups:

- Desktop encrypted credential protection through Keychain, DPAPI, or Secret Service is now declared as available by the shared Desktop capability registry and covered by its contract test.
- Desktop constructs and runs the shared application-update checker and now declares `ApplicationUpdates` as available instead of hiding the common UI.
- `Sharing` is unavailable everywhere and has no observed product consumer. Keep it out of product presentation until a common share command and at least one real adapter exist.
- Android Auto is a valid Android capability, but its product catalog/playback intent must be reusable by future CarPlay. The OS browse/template protocols remain separate.
- Media-row feature capabilities are one common Naviamp baseline. A missing host executor is a bug, not a reason to subtract a feature.

## Target: One Complete Naviamp Core

The target composition should be conceptually equivalent to:

```text
NaviampCore.create(platformServices)
├── runtime and lifecycle
├── provider/source/session owners
├── product state store
├── feature controllers and command dispatchers
├── presentation state and required action graph
└── NaviampApp() shared Compose entry

AndroidHost
├── constructs AndroidPlatformServices
├── forwards Activity/Service/intent events
└── mounts NaviampApp(core)

DesktopHost
├── constructs DesktopPlatformServices
├── forwards window/menu/file events
└── mounts NaviampApp(core)

IosHost
├── constructs IosPlatformServices
├── forwards lifecycle/remote-command events
└── mounts NaviampApp(core)
```

The common composition must own:

- the complete authoritative product state, including transient loading/dialog/error state;
- all routes, detail history, overlays, dialogs, and selection state;
- every visible action as a required command or an explicitly capability-gated command;
- feature controllers for Home, Search, Library, details, playlists, radio, mixes, Downloads, Settings, and Now Playing;
- provider/source lookup, validation, retry, stale-request handling, and user-facing result policy;
- queue, playback-session, reporting, download, cache, offline, and settings orchestration;
- mapping domain state to the complete shared UI state;
- binding shared commands to narrow platform-service ports;
- the responsive Compose shell, product screens, settings, menus, and dialogs.

The common composition must not own Android, AWT/Swing, Apple, JNI, SQL driver, filesystem path, Keychain, notification, MediaSession, window, or packaging APIs.

### Required platform-service families

The final names may evolve, but hosts should implement narrow parallel roles rather than product controllers:

- `PlaybackExecutor` and optional playback feature/device ports;
- `SystemMediaControlsPublisher`;
- `AutomotivePresentationHost`;
- `CredentialProtector`;
- `DatabaseDriverFactory` and repository byte stores;
- `DocumentPicker` / `DocumentStore` / `StorageLocationProvider`;
- `ConnectivityMonitor`, `Clock`, and application lifecycle adapter;
- `HttpClientFactory` / TLS material provider;
- `ApplicationUpdateInstaller`;
- `ExternalUriOpener` / sharing service;
- `BackgroundWorkScheduler` where required by the OS;
- native waveform, metadata, artwork, and visualizer adapters where common libraries cannot perform the work.

## Core Test Contract

Core completion requires more than unit tests for extracted helpers.

- **Construction test:** `NaviampCore` builds with fake implementations of every platform port and no Android/Desktop/iOS dependency.
- **Host-neutral product smoke test:** mount the common app and navigate Home, Search, Library, album, artist, playlists, playlist detail, radio, Downloads, Settings, and Now Playing.
- **Required-action test:** every visible menu/control produces a typed core command; no required action has a default no-op.
- **Feature behavior tests:** each product controller covers success, empty, stale source/request, provider failure, cancellation, retry, and restoration where applicable.
- **State-mapping tests:** domain/application state maps to one UI state independent of host.
- **Capability tests:** only genuine OS service availability changes presentation. Removing a normal Naviamp product action is rejected.
- **Adapter contract tests:** reusable suites exercise every host implementation of common ports.
- **Lifecycle tests:** fake lifecycle transitions prove restoration, backgrounding, reattachment, and shutdown policy without an Activity or window.
- **Architecture guard:** common source must not import platform APIs, and host source must not define product routes, product action catalogs, or independent feature state machines outside an explicit allowlist.
- **Thin-host acceptance:** a minimal fake host and the initial iOS host can browse the complete product without feature-specific host controllers.

The current test ratio reinforces the need for this gate: common domain is well covered, but common application/UI/storage coverage is not yet proportional to the behavior they must absorb. Host tests should shrink with deleted product implementations while adapter tests remain.

## Build-Then-Delete Migration Order

Do not delete working host behavior before its shared replacement is executable and tested.

1. **Create the common presentation-composition module.** It may be a new module consuming `core:app` and `core:ui`, or a dependency restructure that preserves clean direction. It exposes `NaviampCore` and the shared Compose entry.
2. **Make the contract strict.** Remove required no-op defaults, finish sealed media-command emission, and introduce narrow typed effect ports.
3. **Move authoritative state into core.** Replace `AndroidAppState` and Desktop's remembered product-state graph with one observable common state owner.
4. **Move feature transactions into core in vertical slices.** Navigation/shell first, then connection/settings, Home/Search/Library, media/details/playlists, radio/mixes, Downloads/offline, and Now Playing/playback.
5. **Mount both hosts on the common entry.** Android constructs only service adapters and forwards lifecycle/OS events. Desktop was rebuilt beside its legacy graph, passed its service/parity gates, and is now promoted at `apps:desktop`; native adapters and generated resources remain in `platforms:desktop`.
6. **Delete duplication immediately after each slice.** Remove superseded host action factories, state factories, product controllers, models, menus, dialogs, and UI wrappers. Do not leave compatibility paths indefinitely.
7. **Decompose surviving large host files by OS responsibility.** This happens after product code is removed so decomposition does not disguise relocation.
8. **Run host-neutral and platform acceptance suites.** Only then is the initial iOS wrapper allowed to add product navigation.

Current foundation: `core:presentation` owns the host-neutral `NaviampCoreStateStore`, exhaustive typed command catalog, one action graph, strict controller composition, and `NaviampCoreApp`. Core controllers own navigation/history, settings and maintenance, settings sync, connection transactions, Home, Search, Library, details, playlists, Internet Radio, standard and Sonic builders, Downloads/offline, and Now Playing/playback. Android, Desktop, and iOS all mount that graph and the same portable storage catalog/audio store. Required UI callbacks have no silent product no-ops or competing media-row paths, and the router enforces exactly one controller owner. The fake host and all three real hosts consume the same product. `verifyCoreFirstArchitecture` prevents new common platform imports, new host product surfaces, host-owned portable storage classes, and generated SQL mapping in host production code.

## Explicit Deletion Targets

These are ownership targets, not a promise that every named file disappears unchanged. A file may survive only if its remaining responsibility is a genuine host adapter and its name reflects that role.

- Android: `AndroidAppState`, `AndroidAppShellUiStateFactory`, `AndroidMainShellActions`, product portions of `NaviampAndroidApp`, `AndroidDetailActions`, and product controllers for Home/Search/Library/media/playlists/radio/mixes/Downloads/connection/Now Playing.
- Desktop: product portions of `DesktopNaviampApp`, `DesktopAppShellStateFactory`, `DesktopAppShellActionFactory`, `DesktopSharedContentActions`, `DesktopSettingsPanel`, `DesktopShellChrome`, Desktop product dialogs/menus, and parallel feature controllers.
- Both: independent playlist/queue transaction policy, duplicate state/result publication, host-specific stable-ID lookup policy, permissive handler tables, and host-specific user-facing status/error selection.

Expected survivors include Android service/Activity/Auto/MediaSession/notification/permission/audio-focus adapters, Desktop window/file-dialog/updater/packaging/native-integration adapters, and parallel BASS/storage/security/network implementations of shared contracts.

## Core Completion Exit Gate

Milestone 4 is not complete until all of the following are true:

- [x] Android and Desktop mount the same complete shared application entry.
- [x] A fake host mounts and navigates the complete app with only common state plus fake platform ports.
- [x] Normal Naviamp features, UI, menus, commands, and behavior require no host-specific product wiring.
- [ ] Every remaining platform difference in this matrix names a concrete OS/API constraint and a narrow common contract.
- [x] Required action contracts contain no silent no-op defaults or competing callback paths.
- [x] Common tests prove the complete product graph, major feature behavior, navigation, restoration, and capability presentation. The [V2 Core Test Audit](v2-core-test-audit.md) records the controller, mapping, lifecycle, restoration, capability, and exhaustive action evidence; reusable real-host adapter application remains a separate host-conversion gate.
- [x] Superseded Android and Desktop product factories/controllers/state/UI have been deleted, not merely renamed or split.
- [ ] Surviving host files are thin adapters or focused native integrations and follow the shared-role naming convention.
- [x] The initial iOS host browses the complete product without album, artist, playlist, radio, download, settings, or Now Playing product controllers.
- [ ] After feature work is otherwise complete, perform a fresh file-by-file audit of every Android, Desktop, and iOS production source. Record the concrete OS API, native ABI, or host lifecycle constraint that justifies every surviving platform file; extract duplicated scheduling, policy, state, caching, retry, validation, and user-visible behavior into Core. This audit is a blocking completion gate even when all functional and performance acceptance tests pass.
