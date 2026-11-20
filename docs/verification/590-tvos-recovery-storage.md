# #590 — bounded TV recovery storage and scene host

Status: bounded authority and initial opt-in scene implemented and locally verified, 2026-11-15. This is part of
[issue 590](https://github.com/goosepod/naviamp/issues/590), on its existing draft PR 606.
It is not a claim that the full tvOS application is ready to distribute.

## Storage contract

tvOS may purge the SQLite database, artwork, downloaded audio and other files in the app's cache.
The database must therefore be a projection of durable connection configuration, rather than the
only copy of the credentials needed to reconnect. A playback queue is deliberately not part of
the recovery record. Neither library indexes nor artwork, response caches, waveform data, playback
history or sync/scan completion markers belong in preferences.

`core:storage` owns one versioned, bounded recovery document. `core:bootstrap` connects the existing
complete settings catalog to it through `NaviampRecoverySettingsValueStore`. The same document
contains main and Aurral companion configuration, active-source ordering, all protected credentials,
TLS/header configuration, Radio DJ presets, user-authored playback profiles and lyric timing offsets.
Provider identity-format versions travel with the associated user-authored IDs. Reproducible scan
and identity-probe results do not become persistent authority.

The document is encrypted with authenticated native encryption before it reaches UserDefaults.
Individual SQL credential fields use the same shared framing/protector. A small `core:host-api`
exports only the native document, reentrant lock and encryption contracts to Swift. It does not
export the provider, application, repository or Compose implementation graph.

The native secret box uses CryptoKit AES-GCM, a random 256-bit key and an app-scoped
Keychain item. It does not create a Keychain item for every credential edit. Decryption must never
create a replacement key when the original is missing. Missing, invalid or unavailable keys produce
recoverable errors; they do not trigger a silent reset. Native tests exercise real encryption,
adapter recreation, tampering, wrong namespaces, deletion, missing-key reads and the one-key
inventory. These are distinct from the common fake-cipher tests and do not establish physical
device lock/unlock behavior or a complete GUI-driven encrypted cache-purge round trip.

## Fixed bounds

| Bound | Value | Enforcement owner |
| --- | --- | --- |
| Entire decoded recovery document | 128 KiB UTF-8 | Core |
| Protected document including envelope | 192 KiB UTF-8 | Core |
| Entire app preference domain | 384 KiB, including 4 KiB reserve | Native measures, Core accepts/rejects |
| Settings keys | 192 | Core |
| Connections | 32 | Core |
| Referenced certificate files | 64 | Core |
| Each certificate/key-store file | 32 KiB | Core plus bounded native read |
| Keychain encryption keys | One per app/principal namespace | Native Keychain adapter |

The whole-domain check must include unrelated existing keys in the same standard persistent domain.
Do not evade the budget with multiple suites/domains. Measure the prospective property list before
replacing the value, allow for serialization overhead and reject measurement failures. The budget
is deliberately below Apple's documented 512 KB warning. Apple documents termination at 1 MB;
neither threshold is a target for normal operation.

Limits reject a change before its SQL transaction can commit. They do not trim settings, discard
connections, evict user preferences or drop the Aurral companion. Increasing a limit requires a
new capacity review and native size measurements, not merely a larger constant.

## Recovery and transactions

Core serializes reads, mutations, preflight, encryption and compare-and-set with a reentrant native
lock. Nested operations form one document commit. Even a caught nested failure aborts its enclosing
batch. A stale writer fails with `Conflict` and must reload, rather than overwriting newer settings.
This is an in-process serialized check-and-replace over UserDefaults, not an interprocess atomic
storage primitive. The prototype has one scene and no sharing extension or app-group writer.

Connection and user-configuration mutations run inside SQLDelight transactions. The document commits
while SQL can still roll back if preflight or the native write fails. If SQL subsequently fails to
commit, the document remains authoritative and reconstructs the projection on restart. This is not
a distributed transaction or a synchronous power-loss durability guarantee. UserDefaults makes
updates visible in memory immediately and writes to disk asynchronously. Readback is not proof of
a disk flush; do not add deprecated `synchronize()` calls or advertise stronger durability.

SQL commit/rollback failures poison the current projection and require a fresh recovery owner.
Every further read/mutation through that owner fails instead of recapturing stale SQL rows over
the committed document. The shared host closes an uncertain application and offers reload; it
cannot dismiss that condition back into a writable stale session. Cleanup errors do not suppress
the recovery state. Dedicated completion-failure tests execute SQLDelight's real transaction owner
with an injected failing driver effect, alongside the real SQLite rollback tests.

Settings import groups preferences, connection configuration, DJ presets and sync timestamps into
one persistence batch. Apply live playback settings only after that batch succeeds. Cache settings
are persisted before cache-limit callbacks can evict files. Playback preferences are persisted before
the adapter changes current state or native playback effects. Original hosts retain their existing
default immediate-persistence path unless they explicitly supply the recovery authority.

On startup, Core authenticates and validates the complete document before opening it as authority.
It restores connection rows before building providers, rewrites certificate references into current
cache paths, then restores user-authored SQL configuration. It removes obsolete SQL connections.
Existing compatible cache rows may be reused, but a purged database starts with no completed scan
markers and no playback queue. Removing a connection records its absence, so it cannot reappear
after a later cache purge.

Certificate bytes are included inside the encrypted budget, rather than retaining broken paths to
purgeable files. Core allocates stable numeric references and chooses owned recovery filenames;
the native adapter only reads/writes/removes bounded files. Recovery materializes them under the
current container. Main and companion custom-CA/client-key-store configuration use the same path.
Unreferenced recovered files are removed after a successful authoritative commit. Cleanup failure
does not invalidate an already committed document; those files remain purgeable cache material.

## Failure and reset behavior

Shared `NaviampRecoverableHostController` and `NaviampRecoverableHostApp` own startup, failure,
retry, reset confirmation and scope cancellation. Native scene code delegates to that owner.
Late callbacks from a disposed startup cannot mutate a new application. Retry closes the old
application/native resources and reopens the saved state. It never deletes unreadable data.

Capacity, unreadable/future-version documents, conflicts and native unavailability map to shared,
translated UI. All seventeen maintained catalogs contain the new recovery strings. Reset requires
an explicit second confirmation, with initial focus on Cancel. Only then may Core remove its
recovery value and encryption key. It must not clear unrelated preference domains or server data.

## Verification ledger

The initial common tests pass for UTF-8/domain bounds, native failure rollback, stale writers,
nested failure, malformed/future/missing-key documents, empty-cache main/companion recovery,
connection deletions, bounded batch rollback and playback-queue exclusion. These are fake-cipher
tests and real SQLite projection tests; they do not validate native cryptography.

The expanded connection suite includes main/companion certificate relocation, a colliding external
certificate filename, format-version/user-ID migration, bounded connection count, deletion of
dependent user configuration, settings import rollback and preservation of unsupported direct-output
preferences. Existing export policy still excludes device volume. A literal password resembling a
cipher frame is encrypted as new plaintext unless the original frame actually authenticates.

The first native purge test found a test-fixture defect: passing `:memory:` as a Native driver name
created/reused a file rather than selecting SQLiter's in-memory mode. Both Native test helpers now
use SQLDelight's explicit `inMemoryDriver` with foreign keys enabled. The replacement-database test
asserts that it starts empty before recovery, so a reused database cannot masquerade as a passing
cache-purge result. JDBC recovery fixtures also enable foreign keys.

| Local acceptance | Result |
| --- | --- |
| Original Android debug app and Windows Desktop compilation | Pass |
| JVM Core suites | 2,987 pass: storage 97, bootstrap 6, UI 780, presentation 629, domain 1,177, app 298 |
| Android host storage/bootstrap suites | 25 / 6 pass |
| Repository architecture and Android hierarchy checks | Pass; 22 Android compilations inspected |
| Translation catalog validation | All 17 catalogs pass |
| Original iOS device/simulator common compilation | Pass |
| Original iOS Native bootstrap/storage/UI suites | 6 / 31 / 213; no failures, errors or skips |
| Rebuilt original iOS app and Swift Keychain test | Visible startup inspected; test passes |
| Both actual TV application frameworks | Link successfully with the reviewed 52-publication port |
| Final TV Native bootstrap/storage/UI/direct-BASS suites on 27.2 | 6 / 30 / 212 / 3 pass; no failures, errors or skips |
| TV Swift native cryptography/preferences tests | Both pass in the isolated native test host |
| Actual TV UIKit scene launch and process relaunch | Both visibly render the shared TV connection setup |
| Owned iOS/TV simulator shutdown and deletion | Both return zero; pre-existing user simulator untouched |
| Source preparation/artifact/deployment tooling tests | All 26 pass in the normal Windows credential/process context |

The desktop-sandbox run of two local Git replay tests could not clone its own temporary fixture;
the same unchanged tests pass outside that sandbox. This did not require a credential change.

The final artwork audit moved callback state and ownership into Core. `NaviampStoredApplication`
owns a scoped binding and an idempotent `close()`. A stale application's cleanup cannot remove a
new application's binding. Android/JVM compatibility hooks now delegate to the same owner; native
image decoders and native resource caches remain adapters. No animation timing, renderer cadence,
marquee/progress policy or waveform frequency changed. The existing Windows visualizer/performance
limits in the [Windows report](590-windows-performance-2026-11-15.md) remain open.

Evidence: Windows `build/590-feasibility/recovery-{final-originals,full-shared-tests,framing-tests,sql-failure-tests,
architecture-checks,tooling-tests-v2}.log`, `recovery-{ios,tvos}-summary.json` and inspected startup
captures. The final original-iOS owned run is `build/590-recovery-common-1115-v7`; its source hashes
match every input in the working snapshot. Initial scene evidence is `build/590-scene-host-1115-v2`;
the final TV safety/UI run uses `build/590-scene-host-1115-v3`. Its LF-normalized source hashes also
match every tested input. The 251 Kotlin/Native cases and two Swift adapter tests pass together.
Compare source hashes and fresh XML timestamps rather than inheriting results from earlier attempts.

## Actual scene and build target

`apps:tvos` now contains the thin Kotlin/UIKit host, a scene-based Swift entry point, native
storage effects, privacy manifest, shared Xcode scheme and isolated native adapter test host.
`core:ui` and `core:presentation` declare their TV targets only with
`-Pnaviamp.tvos.sceneHost=true`; `core:bootstrap`, storage and host API retain the broader
`naviamp.tvos.feasibility` gate. Enabling the scene requires both flags. The headless public-BASS
workflow remains independent of private Compose UI artifacts.

In the already guarded consumer checkout, with the verified repositories and plugin selection from
the artifact report, the app targets are:

```sh
./gradlew --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8' \
  -Pnaviamp.tvos.feasibility=true -Pnaviamp.tvos.sceneHost=true \
  :apps:tvos:linkDebugFrameworkTvosArm64 :apps:tvos:linkDebugFrameworkTvosSimulatorArm64

xcodebuild -project apps/tvos/NaviampTV.xcodeproj -scheme NaviampTV \
  -configuration Debug -destination 'id=<owned-TV-simulator>' -derivedDataPath '<owned-output>' build
```

The Xcode shell phase uses the standard `embedAndSignAppleFrameworkForXcode` task with the same
bounded heap/workers. It never invokes Compose source producers. The native package links/embeds
exactly public BASS, FLAC and Opus. There is no BASSmix framework or mixer dependency.

These commands are **not yet a fresh public-checkout build recipe**: trusted distribution and
automatic selection of the reviewed port remain open. This scene was tested with manifest
`3f6d453c0de05b643ac94d14bbb296ddee9429df5308806baa9d1e2e21864193`.
The separately reconstructed port candidate retains its separate identity and is not silently
substituted. Keep both flags off in normal builds until that delivery gate and the remaining TV
acceptance gates close.

Both framework headers are only **15,641 bytes / 305 lines**, with the small host effects and UIKit
entry point. Header inspection rejects exported bootstrap/provider/repository/Core-environment
types. The first both-target app link took **365.138 seconds** with warm dependency/toolchain caches;
the unchanged simulator framework task took **6.788 seconds**. Xcode built the native scene in
**23.595 seconds**, and its native tests took **30.276 seconds**. The final safety/UI run rebuilt
the native scene in **27.195 seconds**, ran the two Swift tests in **32.902 seconds**, and confirmed
an unchanged Gradle framework invocation in **2.289 seconds**. These are local wall times with
sampling overhead, not clean-machine or older-device promises.

The final device and simulator archives each contain **3,960 objects**. The deployment audit found
zero platform/minimum-OS violations: device objects use the tvOS platform, simulator objects use
the tvOS simulator platform, and the maximum declared minimum is **18.5**. These static checks do
not replace an older-runtime launch or signed physical-device test.

The first path-based RSS probe failed to identify the Gradle daemon; its zeros are **unavailable
measurements**, not zero-memory results. A separate JVM-PID/descendant probe repeats only the two
link tasks and unchanged link. The final-source repeat in `build/590-scene-build-profile-1115-v2`
records **211.863s / 2.336 GiB** summed peak RSS for the device link, **193.815s / 3.257 GiB** for the
simulator link and **5.465s / 3.230 GiB** for the unchanged task. The latter includes the retained
Gradle daemon, not 3.230 GiB of new allocation. JVM arguments confirm `-Xmx4g`; both invocations
use two workers. One-second sampling and shared RSS pages make these approximate process totals,
not unique physical-memory or an app runtime-memory measurement.

The repeat uses warm compiler/dependency caches and explicitly reruns only the framework tasks.
The changed storage/bootstrap KLIBs also rebuild for the final safety guard. It does not invoke
producers or raise the heap. Retain the small export surface and verify unchanged links remain
within a 15-second local development budget when the port/compiler/SDK cache identity changes.

The prototype uses Apple's default-user application resources. It does not opt into per-Apple-TV-user
storage or user-independent Keychain sharing. An eventual multiuser opt-in must align preference,
cache, key and active-application lifetimes and verify account switching on a signed physical device;
an entitlement alone is insufficient. See Apple's [multiuser explanation](https://developer.apple.com/videos/play/wwdc2022/110384/).

## Native production-file audit

Every changed/new native production file has the following concrete boundary. Product state,
connection mapping, durable configuration, limits, retry/reset policy and UI remain in common code.

| File | Irreducible boundary |
| --- | --- |
| `apps/tvos/src/tvosMain/kotlin/app/naviamp/tvos/NaviampTvosApplication.kt` | UIKit/Compose UIViewController and CALayer attachment; SQLite/BASS native resource creation and release. |
| `apps/tvos/NaviampTV/AppDelegate.swift` | UIApplication/UIScene/UIWindow entry point and window lifetime. |
| `apps/tvos/NaviampTV/TvosRecoveryEffects.swift` | Foundation preference domain/NSRecursiveLock, CryptoKit AEAD and Security Keychain operations. |
| `apps/ios/src/iosMain/kotlin/app/naviamp/ios/NaviampIosApplication.kt` | Existing UIKit/CALayer and native database/audio/media resources; delegates artwork/product cleanup to Core. |
| `apps/ios/src/iosMain/kotlin/app/naviamp/ios/IosScreenAwakeEffect.kt` | Compatibility delegate for UIKit's idle timer. |
| `core/presentation/src/appleMain/kotlin/app/naviamp/presentation/AppleHostEffects.kt` | UIKit URL/idle-timer calls and Foundation local-calendar facts. |
| `core/storage/src/appleMain/kotlin/app/naviamp/storage/AppleAudioStorage.kt` | Foundation file metadata/URLs and atomic POSIX audio writes. |
| `core/storage/src/appleMain/kotlin/app/naviamp/storage/AppleAudioTagReader.kt` | Bounded POSIX file reads; parsing stays in common code. |
| `core/storage/src/appleMain/kotlin/app/naviamp/storage/AppleRecoveryFiles.kt` | Bounded POSIX reads and atomic Foundation writes/removal; Core supplies ownership paths, names and limits. |
| `core/ui/src/appleMain/kotlin/app/naviamp/ui/AppleDateTimeFormatting.kt` | Foundation date/calendar/time-zone formatting. |
| `core/ui/src/appleMain/kotlin/app/naviamp/ui/AppleNaviampLocaleEffect.kt` | Foundation volatile native language override. |
| `core/ui/src/appleMain/kotlin/app/naviamp/ui/AppleMetalVisualizerRenderer.kt` | Metal command/resources and native image/GPU ABI. |
| `core/ui/src/appleMain/kotlin/app/naviamp/ui/AppleRasterPresenter.kt` | CALayer/CoreAnimation, CoreGraphics and UIImage/NSData presentation. |
| `core/ui/src/appleMain/kotlin/app/naviamp/ui/PlatformLiveVisualizerSurface.apple.kt` | Native Skia canvas/Metal effects executing unchanged shared render policy. |
| `core/ui/src/appleMain/kotlin/app/naviamp/ui/PlatformCoverArt.apple.kt` | Skia image-decoder ABI; byte callback ownership delegates to Core. |
| `core/ui/src/iosMain/kotlin/app/naviamp/ui/IosUiCompatibility.kt` | Existing iOS API delegates for CALayer and shared byte-source binding. |
| `core/ui/src/androidMain/kotlin/app/naviamp/ui/PlatformCoverArt.android.kt` | Android Bitmap/Canvas/filesystem effects; callback state removed. |
| `core/ui/src/jvmMain/kotlin/app/naviamp/ui/PlatformCoverArt.jvm.kt` | JVM AWT/ImageIO and native Skia/resource effects; callback state removed. |
| `platforms/ios/src/iosMain/kotlin/app/naviamp/ios/platform/IosCoreExternalUriPort.kt` | Compatibility delegate for UIApplication URL opening. |
| `platforms/ios/src/iosMain/kotlin/app/naviamp/ios/platform/IosHomeDateSource.kt` | Compatibility delegate for Foundation local-calendar facts. |
| `platforms/ios/src/iosMain/kotlin/app/naviamp/ios/playback/IosAudioTagReader.kt` | Compatibility delegate for the POSIX byte reader. |
| `platforms/ios/src/iosMain/kotlin/app/naviamp/ios/storage/IosAudioStorage.kt` | Compatibility delegates for Foundation/POSIX files and file URLs. |

The six former `core:ui/iosMain` effect implementations were moved to `appleMain`, rather than
copied into a TV source set. Build scripts, Xcode metadata/manifests and platform tests are separate
from this production-code audit. The existing iOS constructor and `core:storage` module export are
retained; no provider/bootstrap/UI implementation module was newly exported to Swift.
The Metal renderer, raster presenter and live-visualizer surface are byte-identical to their
previous implementations after the Apple symbol/comment renames; their animation behavior was
not changed by the source-set move.

## Remaining host work and build controls

The subsequent [remote and app-recovery report](590-tvos-remote-recovery.md) verifies actual remote
navigation/native text entry and main/Aurral password-account recovery through the complete app.
Its guarded cache deletion, pre-launch probes, native cryptography, fresh authentication and visible
provider content pass together. It also records the two common remote fixes and current system
Home/lifecycle limits. The following list retains broader host acceptance work.

Per the owner's November 15 acceptance update, runtime acceptance uses Apple's tvOS Simulator.
Physical Apple TV testing is optional; unavailable hardware does not block #590, PR readiness or
the initial release. Hardware-only results remain unverified limitations. This supersedes earlier
physical-test gates; simulator, native build and distribution requirements remain.

The shared Apple UI/filesystem adapters and initial UIKit scene are implemented. The current host
mounts foreground direct BASS, shared repositories/providers and the television UI. Native media
commands, background/interruption integration, discovery/pairing, document selection and durable
offline downloads are not mounted or advertised as accepted capabilities. Continue with the
following work under #590; no additional issues are needed:

1. Extend the passing owned XCUIRemote/navigation/text suite to system Home/return, playback
   buttons, more layouts and simulator accessibility. The subsequent
   [system-input checkpoint](590-tvos-system-input.md) now passes idle/paused Home/return and
   foreground Play/Pause/held input with actual clock assertions. Other transport/legacy Menu
   gestures and broader layouts/accessibility remain. Keep UIKit input translation
   native and product focus/back policy shared.
2. Extend the passing main/Aurral password-account cache-purge flow to GUI certificate selection,
   custom transport configuration, general settings/imports, nonempty-queue exclusion, failed
   writes, explicit reset and native key unavailability. Existing component tests cover those
   storage cases; the broader complete GUI acceptance remains open.
3. Reuse the existing common lifecycle/media bridge with the smallest shared Apple AVAudioSession
   and MediaPlayer effects. Publish OS facts into Core. Verify interruption, background/foreground,
   scene disconnect, retry while playing and native resource disposal. Add Bonjour/local-network
   effects through existing shared connection contracts and the required localized privacy copy.
4. Verify authenticated range/TLS streaming, simulator audio/remote behavior and the accessibility
   checks supported by the simulator. Test the 18.5 compatibility candidate on an older simulator
   runtime. Physical remote/HDMI audibility and hardware performance remain unverified, optional
   follow-up checks; simulator results do not establish them.
5. Close reviewed artifact delivery and bounded producer automation, then add the full scene build,
   native storage and remote tests to hosted CI. Keep the current headless public-BASS lane separate.
   Finish signing/provisioning, icons, version automation and archive/install/distribution checks
   before considering the TV target release-ready. Android's runtime minimum remains API 26.

Export only the small host API from the new static TV framework. Keep the original iOS export
contract unchanged. Continue using bounded worker/heap settings and separate Native caches keyed
by the reviewed Compose port/compiler/SDK/GC identity. The headless BASS foundation checks must not
acquire a Compose UI dependency. Ordinary builds must consume prebuilt verified port artifacts,
never run the Compose source producers. Trusted artifact delivery remains open in the existing
[artifact report](590-compose-artifact-delivery.md).

The current private port candidate's minimum is 18.5; the accepted 27.2 scene rendering does not
establish older-runtime compatibility. Existing original-platform animation/accessibility gates
and the complete TV end-to-end gates remain explicit draft blockers.

## Primary sources

- [Apple UserDefaults](https://developer.apple.com/documentation/foundation/userdefaults) —
  supported preference types, sensitive-data guidance and asynchronous persistence.
- [Apple preference size limits](https://developer.apple.com/documentation/foundation/userdefaults/sizelimitexceededmessage)
  — tvOS warning/termination thresholds; the typed notification itself requires newer OS versions.
- [CryptoKit AES.GCM](https://developer.apple.com/documentation/cryptokit/aes/gcm) — native
  authenticated encryption boundary.
- [Keychain accessibility](https://developer.apple.com/documentation/security/ksecattraccessibleafterfirstunlockthisdeviceonly)
  — device-only accessibility after first unlock, including background use.
- [UIKit scene delegate](https://developer.apple.com/documentation/uikit/uiscenedelegate) —
  the native scene lifecycle boundary.
