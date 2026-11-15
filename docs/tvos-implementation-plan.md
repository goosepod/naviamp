# Apple TV implementation and feasibility plan — issue #590

Owner: https://github.com/goosepod/naviamp/issues/590. All work in this plan belongs to that
issue; do not split it into new issues. Research baseline: main `57c266612`, November 13, 2026.
Working branch: `codex/590-tvos-feasibility`. This is an implementation plan, not a support announcement.

## Decision and current evidence

Build one shared Naviamp application with an additional tvOS host. Reuse the Television surface,
Core runtime, providers, queue, connection provisioning (including Aurral), navigation and settings.
No second tvOS controller, provider implementation or persistence policy is permitted.

The owner's additional [official Kotlin/Native target reference](https://kotlinlang.org/docs/native-target-support.html)
confirms `tvosArm64` and `tvosSimulatorArm64` are Tier 2: compilation is regularly checked in
Kotlin's CI, with best-effort source/binary compatibility. The ARM64 simulator supports running
tests through Gradle on a Mac. The default compiler deployment floor is tvOS 15.0; confirm the
maximum floor imposed by the final UI and native framework inventory before setting app metadata.
`tvosX64` has been deprecated since Kotlin 2.3.20 and remains outside this plan. These guarantees
cover the compiler, not Compose's platform matrix, BASS add-ons or physical Apple TV acceptance.

Feasibility is conditional. Kotlin targets are available, but a working shared renderer and the
complete native playback chain must be demonstrated before promising a shipping Apple TV app.
Do not silently replace shared UI with a separate SwiftUI product or substitute AVPlayer for BASS.
Such a change would require an explicit architecture/product decision.

### Compose

JetBrains distinguishes Kotlin tvOS support from Compose UI support. Its official Compose
platform matrix omits tvOS. The community `dev.sajidali.compose-tvos` plugin supplies tvOS variants
through dependency metadata and a patched Compose Gradle plugin. Its README documents an
official-first policy and a strict resolution mode. These are upstream claims, not Naviamp validation.

The downloaded manifest currently requires Compose 1.12.0 or later, forbids cross-version mappings,
and selects the 1.12.0 Gradle plugin. Naviamp uses Compose 1.11.0, Material3 1.9.0 and Kotlin 2.4.10.
Therefore adding the plugin to today's build is not an accepted compatibility solution.
Keep Android/iOS/Desktop on their existing dependencies until a controlled upgrade experiment passes.
Pin the plugin, its manifest contents/hash and all native dependency versions; no moving remote
manifest may decide a release dependency graph. Verify the actual resolved graph, not plugin promises.

Sources:
- https://kotlinlang.org/docs/multiplatform/supported-platforms.html
- https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- https://github.com/sajidalidev/compose-tvos
- https://raw.githubusercontent.com/sajidalidev/compose-tvos/main/manifest/compose-tvos-versions.json

Manifest observed SHA-256: `ac7672202ca43337101ebba506d92775e2e6df7447ecfe7531a461da7fa3d0b5`.
Evaluate license notices, transitive substitutions, source provenance, release cadence, resource
packaging, Metal/Skiko, text input, accessibility and remote-event translation. Do not infer readiness
from a demo or from the ability to resolve a runtime KLIB.

### BASS

Un4seen's historical forum announces tvOS BASS/BASSmix and later discusses licensed builds.
Those old statements do not establish current licensing or add-on availability.
The owner highlighted the announcement again on November 14: reply #1102 explicitly names
both BASS and BASSmix and links the same `bass-tvos.zip` URL below. This establishes that a
tvOS mixer build existed; it does not establish what today's mutable download contains.
Re-downloading that exact link on November 14 produced the same byte count, SHA-256 and
three-framework inventory below, with no BASSmix framework. Later replies #1118 and #1131
discuss requesting builds and licensed-user access at that time; they are historical evidence,
not confirmation of current availability or a reason to assume a new release is required.
The actual public `https://www.un4seen.com/stuff/bass-tvos.zip` was downloaded successfully:
801,783 bytes, SHA-256 `8f3bef0a67c20fdf1b65a7b6164b25bfb3a75e47e8bd5f1094eb0fad1ac3bbe8`.
Its entries contain BASS, BASSFLAC and BASSOPUS XCFrameworks with device arm64 and simulator
arm64/x86_64 directory names. Validate plist platform declarations and Mach-O build commands as
well; directory names and the shared arm64 architecture alone are insufficient.

The owner's download at `C:\Users\ursasmar\Downloads\bass-tvos` was also inspected: all 18
files are byte-identical to that pinned archive. Each `.xcframework` is a container of compiled
libraries, with an ARM64 slice for physical Apple TV and ARM64/x86_64 slices for the simulator.
The top-level `Info.plist` tells Apple's build tools which slice to link; the extensionless
`bass` file inside `bass.framework` is the native library. The FLAC/Opus siblings are decoder
add-ons, not BASSmix. These are integration inputs, not a separate tvOS app or new mixer build.

The archive does not contain BASSmix, MIDI, WavPack, DSD, WebM, HLS, APE or MPC. Naviamp's current
iOS build links all of those. The owner selected a **direct-BASS first version** on November 14:
use public downloads, with gapless, crossfade and the custom multichannel-to-stereo matrix
unavailable until a compatible public BASSmix build exists. BASSmix blocks that feature parity,
not all playback. Core already has a direct-stream path and mixer-dependent transition capabilities.
The November 14 common implementation adds the missing downmix capability, hides unavailable
downmix controls in both shared settings surfaces and skips mixer calls during direct-stream
cleanup. Saved/imported settings remain normalized portable preferences; only the engine copy is
capability-gated. Common and rendered regressions cover unavailable capabilities and restoration
on a capable engine. Cross-target verification is recorded in the current upgrade checkpoint.
Codec availability is a separate inventory and playback-test requirement.

The public core's **2.4.18.14** version ran in a disposable tvOS 27.2 ARM64 simulator app using
`scripts/probe-tvos-bass-runtime.py`. All 17 native checks passed: audio-session activation,
initialization, FLAC/Opus registration, direct stream creation, volume, end-sync installation,
play/advancing position, pause/stable position, seek/seek position, resume, end callback and stream/
engine cleanup. BASSmix was not linked. The synthetic WAV was silent and volume was zero:
this proves the control/lifecycle calls, not acoustic output or FLAC/Opus decoding. The owned
simulator was shut down and deleted. SDK 27's UIKit scene lifecycle is required by the probe;
an initial legacy-delegate app trapped before BASS execution. Native app launches must use the
current lifecycle rather than weakening the SDK or treating link success as runtime proof.

Do not copy an iOS binary into a tvOS slice. Confirm current versions, required symbols, SDK
minimums, device/simulator ABI and redistribution terms for each supplied component. No vendor
communication has been sent; private vendor access is outside the owner's selected public-download
scope. A weekly public-release check is configured separately, with no automatic integration.

Sources:
- https://www.un4seen.com/forum/?topic=10910.1100 (historical announcement)
- https://www.un4seen.com/forum/?topic=10910.1120 (historical licensed-build discussion)
- https://www.un4seen.com/stuff/bass-tvos.zip (inspected archive, not committed)
- `platforms/ios/vendor/bass/README.md` (Naviamp's existing eleven-component inventory)

### Local tools

The configured ARM64 Mac `naviamp-mac` has Xcode 27.2 and tvOS device/simulator SDKs 27.2.
The official tvOS 27.2 ARM64 simulator runtime (24K150) was installed on November 14; an old
tvOS 14.3 runtime remains unavailable. Simulator launch tests can now run. Physical Apple TV
availability, signing/provisioning and distribution entitlement acceptance remain unverified.

### Additional host-integration reference

The owner supplied David Cruz Anaya's
[KMP for Apple TV article](https://www.davthecoder.com/blog/kmp-for-appletv)
(published July 2024, updated July 2026). Its
[sample target configuration](https://github.com/davthecodercom/KMP-for-AppleTV/blob/main/shared/build.gradle.kts)
is useful for the Kotlin/Native framework and Xcode host boundary. The
[sample screen](https://github.com/davthecodercom/KMP-for-AppleTV/blob/main/iosApp/iosApp/ContentView.swift)
uses SwiftUI and calls shared Kotlin; it does not demonstrate shared Compose UI or BASS playback.
Use it as a host-wiring reference, not as evidence that those two Naviamp feasibility gates are
closed. Retain the shared Television UI and common navigation/focus owners. Do not copy its older
dependency pins, Intel simulator target or framework export configuration without independent need.
The article's linked integration commit did not resolve through GitHub's API during this check;
the current sample files were inspected instead.

The owner also requested a quick comparison with JellyBox. Its public
[platform list](https://github.com/avdept/JellyBoxPlayer/blob/main/README.md) and
[App Store listing](https://apps.apple.com/us/app/jellybox-player/id6469732117) cover iOS/iPadOS,
macOS, Windows, Linux and Android, plus CarPlay/Android Auto. The inspected complete repository
tree has no tvOS target. Its Flutter player uses `just_audio` 0.10.6; the Darwin implementation uses
AVQueuePlayer/AVFoundation, while Windows/Linux explicitly initialize `just_audio_media_kit`
(libmpv). See the [player provider](https://github.com/avdept/JellyBoxPlayer/blob/0550d9b727b7ad9964938a1265238320008417c9/lib/src/providers/player_provider.dart),
[desktop initialization](https://github.com/avdept/JellyBoxPlayer/blob/0550d9b727b7ad9964938a1265238320008417c9/lib/main.dart#L195)
and [Darwin implementation](https://github.com/ryanheise/just_audio/blob/minor/just_audio/darwin/just_audio/Sources/just_audio/AudioPlayer.m).
This is an alternative playback-architecture reference, not proof of JellyBox tvOS support or a
decision to replace Naviamp's BASS engine. Continue the owner-selected direct-BASS approach.

## Target graph and file placement

Introduce device `tvosArm64` and simulator `tvosSimulatorArm64` only; Intel simulator is outside the
initial plan. Use the default Apple source-set hierarchy. Portable contracts stay in commonMain.
Move existing iOS native operations to appleMain only after proving the API exists and behaves
correctly on both platforms. Keep genuinely iOS-only facilities (CarPlay, phone-specific pickers,
touch/window assumptions) outside the tvOS graph.

| Module | Required work |
| --- | --- |
| core:domain | First opt-in tvOS compilation; Darwin HTTP dependency; existing models/tests unchanged |
| core:app | Add targets; expose existing runtime through current host-effect contracts |
| core:presentation | Add targets; reuse controllers, shared Television capability and navigation policy |
| core:storage | Add targets; verify SQLDelight native driver, protected attachment schema and migrations |
| providers:navidrome, providers:jellyfin | Add targets; reuse common protocols; audit Darwin TLS actuals |
| core:ui | Add targets only after Compose graph validation; audit every expect/actual, fonts, resources and renderer |
| core:testkit, core:tls-testkit | Add native test targets where contracts/dependencies permit; fresh fixtures |
| platforms:tvos | Native operations only: lifecycle, renderer/remote bridge, audio ABI, Keychain, HTTP/TLS, discovery, database driver and paths |
| apps:tvos | Minimal shared composition and Apple executable/project, icons/metadata, signing and packaging |

Create complete common contracts before host wiring. A Swift entry point may construct a window
or view controller and translate framework callbacks; it must not decide routes, retries, queue
restoration or feature availability. Reuse Naviamp Connect for phone/Desktop setup; direct remote
setup, account switching, expired credentials and certificate-protected accounts must also work.

Add new modules to settings.gradle.kts once they have real buildable content. Begin with an explicit
`-Pnaviamp.tvos.feasibility=true` opt-in on the domain target so ordinary development does not pay
for unsupported UI/native dependency resolution. This flag is temporary build scaffolding, not a
user-facing feature setting. Remove the gate when the supported graph and CI are accepted.

## Ordered implementation and acceptance

### 1. Reproducible dependency/ABI evidence (start here)

- Implement an offline inventory command for the supplied Compose manifest and BASS ZIP. Record
  hashes, exact plist platforms/architectures/variants, binary hashes and missing required add-ons.
- Fail malformed or incomplete inventories visibly. Keep downloaded binaries under ignored build
  output until redistribution is verified. Do not automatically fetch or execute a moving plugin.
- Add opt-in domain tvOS targets and compile both against the installed SDK. Compile existing
  Android/JVM/iOS targets too. This establishes shared-target feasibility, not UI or playback support.
- On Mac inspect Mach-O platform load commands and link a minimal ABI executable for each supplied
  framework/slice. A successful link is not a decoder, audio-device or app-lifecycle acceptance result.

### 2. Controlled rendering experiment

- Freeze the current compiler/SDK baseline and dependency graph. In an isolated checkout test the
  compatible Compose/Material/lifecycle/Skiko upgrade across all existing targets first.
- Lock the community plugin/manifest and enable strict resolution. Inventory all tvOS dependencies
  including SQLDelight, Ktor, coroutines, serialization and resource variants; reject platform fallbacks.
- Render a real shared Television page, circular artist/square album artwork, editable connection
  form and moving progress/marquee. Prove select/back/play-pause and directional focus restoration.
- Install a tvOS simulator runtime for launch verification. Run on a physical Apple TV before claiming
  Siri Remote, accessibility, GPU/compositor or HDMI audio acceptance. Preserve existing shared tests.

### 3. Native playback gate

The next native implementation should extract a shared Apple ABI adapter rather than copy the
existing 700-line `IosBassPlayback.kt` into a tvOS directory:

- Keep `BassAudioBackend`, `CoreBassPlaybackEngine`, feature selection, settings, callbacks'
  portable coordination and playback orchestration in Domain. The shared mixer-free contract
  and tests now exist; do not add a second tvOS player controller.
- Put only BASS C calls, `StableRef`/C callback lifetimes, Apple locks, native file/URL translation
  and `AVAudioSession` calls in a narrow shared Apple native module/source set. These concrete
  C ABI/Foundation/AVFAudio boundaries prevent that code from compiling in common Kotlin.
- Bind the base adapter to `bass.h` only. Keep BASSmix calls/cinterop and the full iOS native
  inventory in a separate iOS mixer specialization, so the tvOS linker never needs missing
  symbols. Preserve existing iOS mixer behavior and real native regressions through the extraction.
- Use the same base adapter with tvOS's verified core/FLAC/Opus inventory. Report actual native
  facts (`supportsMixer=false`); Core derives effective transitions/downmix and UI availability.
  Public iOS headers may describe the shared C ABI; every linked binary must remain a genuine
  tvOS device/simulator slice, never an iOS binary relabeled for tvOS.
- Compile/test the Apple base on both iOS and tvOS before adding the television app host. Extend
  native acceptance beyond the small C probe to Core-driven authenticated range streams, actual
  FLAC/Opus decode, preview isolation and lifecycle cleanup. Then add only the executable/scene,
  remote, audio interruption and system Now Playing translations needed by the shared product.

- First prove direct BASS playback with the public tvOS core/FLAC/Opus binaries. Gate gapless,
  crossfade and custom stereo downmix through shared capabilities, preserving stored settings.
  Native output, pause/seek, normal queue advance, end callbacks, failure recovery, previews and
  cleanup must work without calling or linking BASSmix. Record the tested codec set separately.
- Add BASSmix and other missing formats only when compatible public builds are available and
  validated. These additions are later feature work within #590, not blockers for the explicitly
  reduced first playback milestone. Release documentation must state the actual limitations.
- Reuse common playback/native contracts; define any missing shared ABI contract first. Native
  code owns only C calls, resource lifetime, AVAudioSession and OS callbacks.
- Exercise authenticated streaming/ranges/TLS, decoder data, device startup, pause/seek/resume,
  next/prepared tracks, gapless/crossfade, cancellation and primary/Aurral preview isolation.
- Test system interruption, foreground/background, route changes, system Now Playing and remote
  commands. Simulated callbacks alone do not prove physical output or recovery.

### 4. Complete shared application and host

- Compose existing common runtime/services, storage and provider ports. Implement only missing
  native effects. Select the shared Television surface; do not construct a second navigation graph.
- Verify connection → browse/search → play → queue/Now Playing with only the remote. Include
  playlists, generated/Internet Radio, companion discovery and phone-to-TV provisioning.
- Define shared capabilities for file pickers, downloads/offline storage, Connect/Cast and output
  selection. Apple TV storage availability/purge policy must be verified with current Apple docs;
  never promise durable offline media based on iOS behavior. Unsupported actions must be honest.
- Verify Keychain accessibility and access groups on tvOS, local-network discovery/privacy,
  certificate identity handling and secure provisioning. No secrets enter logs or portable exports.
- All new copy goes through every maintained catalog; portable settings round-trip with defaults
  and normalization. Device-specific native settings remain local.

### 5. Verification, distribution and release acceptance

- Add bounded tvOS simulator/common/native jobs and exact-target device compile/archive gates.
  Preserve the existing Android/JVM/iOS matrix. Attach raw results and immutable source/tool identities.
- Reuse Apple version/build/privacy validation and adapt packaging explicitly for AppleTVOS;
  validate bundle platform, architecture, framework slices, signatures, entitlements and embedded
  dependencies. Do not rename an IPA or treat an unsigned archive as App Store acceptance.
- Configure the App Store platform, bundle identity, icons, remote requirements, signing and
  provisioning only after simulator/native feasibility. Validate distribution with Apple's tools.
- Record a physical acceptance matrix: focus/back, VoiceOver, empty/error/loading states, lifecycle,
  process restoration, Wi-Fi failure/recovery, output changes and no accidental autoplay.
- Update product support statements only when verified. #590 stays the parent acceptance record.

## Build performance: apply measured iOS lessons from day one

Read `docs/verification/592-ios-release-archive-cache.md` and
`docs/verification/512-ios-cache-population.md` before changing native build defaults.
The accepted Kotlin 2.4.10 experiment reduced one matched local optimized link from 866.882 to
624.838 seconds; this is not a tvOS forecast. Narrowing the storage framework export was slower
in the controlled iOS experiment. Do not repeat it as an assumed optimization.

1. Use exact target tasks: Debug simulator for local iteration, device compile separately,
   optimized device Release only for archive/benchmark gates. Never invoke an aggregate build or
   multi-slice XCFramework on each edit. Xcode's selected SDK/configuration must map to one link.
2. Keep one authoritative Gradle invocation for a framework. Xcode consumes that output through
   a dependency-aware phase; avoid an unconditional second Gradle link. Track task outcomes to
   distinguish up-to-date work from executed optimization.
3. Start with the accepted compiler/GC/heap policy. Bound native worker concurrency to two initially;
   measure peak RSS and run optimized device links sequentially on the same host. Do not mask long
   links with excessive parallelism, smaller test inventories or reduced runtime optimization.
4. Persist downloaded dependencies and a single consistent konan.data.dir across shell/Xcode/CI.
   Isolate caches by OS/CPU, Kotlin, Gradle, plugin/manifest/dependency lock, Xcode and SDK identities,
   tvOS target, build configuration and cinterop/native binary checksums. Do not cross iOS/tvOS
   platform caches or restore project outputs containing absolute paths from a different home.
5. Preserve trusted cache writes/read-only PR restores. Test bodies always execute. Validate current
   source hashes and inventory before replaying prebuilt tests. Framework task caching must be
   checked against the installed plugin: do not override its cache eligibility or assume KLIB
   cache hits avoid Release optimization.
6. Establish five measurements on frozen source: first dependency download, cold compile with
   downloaded dependencies, warm no-change build, shared UI edit and common behavior edit. Record
   full command wall time, task times/outcomes, compile/LLVM/LTO phases, RSS, artifact bytes and
   cache transfer overhead. Separate native link and Xcode packaging/signing costs.
7. Run at least three comparable samples for proposed performance changes. Initial review targets
   (budgets to validate, not achieved claims): warm no-change Debug <=30 seconds; UI-edit Debug
   <=120 seconds on the named Mac; cold Release <= the measured equivalent iOS baseline +20%.
   A miss requires phase evidence and a revised measured plan before release, not hidden bypasses.
8. Keep compile-time and runtime performance separate. For visible animations measure static,
   each animation and combined states, CPU, parent-surface frames and GPU/compositor cost under
   matched display/power conditions, including paused/hidden/restored/input/resize/accessibility.
   Verify actual motion; no frozen/blank passing surface and no lowered update cadence as a fix.

Official compilation guidance: https://kotlinlang.org/docs/native-improving-compilation-time.html
Apple target configuration: https://developer.apple.com/documentation/xcode/configuring-a-multiplatform-app-target
Apple focus model: https://developer.apple.com/documentation/uikit/about-focus-interactions-for-apple-tv

## Progress ledger

- Research and implementation sequence written before target/product changes.
- Actual public BASS archive inspected; complete add-on chain and license confirmation remain open.
- Compose version mismatch identified; plugin not enabled in the production graph.
- No tvOS app launch, playback, signing, physical acceptance or build-performance acceptance is
  claimed yet. Original-app upgrade acceptance is recorded separately below.

### Milestone 1 results

Implemented `scripts/tvos-feasibility.py`, an offline metadata inventory, and
`scripts/probe-tvos-bass.py`, a hash-pinned Mac link-only probe. Reproduce:

```sh
python3 scripts/tvos-feasibility.py --bass-archive /path/bass-tvos.zip --compose-manifest /path/manifest.json --output build/590-feasibility/inventory.json
python3 -m unittest discover -s scripts/tests -p test_verification_tvos_feasibility.py -v
# On the Mac, with a fresh output directory:
python3 scripts/probe-tvos-bass.py --archive /path/bass-tvos.zip --output /tmp/naviamp-590-bass-probe-new
./gradlew --no-daemon :core:domain:compileKotlinTvosArm64 :core:domain:compileKotlinTvosSimulatorArm64 :core:domain:compileKotlinIosArm64 :core:domain:compileKotlinIosSimulatorArm64 -Pnaviamp.tvos.feasibility=true --max-workers=2
```

All five inventory tests pass, including rejection of iOS masquerading as tvOS, missing simulator,
missing ARM64 and unreviewed manifest substitutions. The actual inventory confirms three supplied
frameworks and eight missing components; neither metadata nor linking claims playback acceptance.

On Xcode 27.2, BASS core links against both AppleTVOS and AppleTVSimulator SDKs. The arm64 device
binary declares minimum tvOS 9.0/SDK 14.3; the arm64 simulator binary declares TVOSSIMULATOR,
minimum 14.0/SDK 14.3. Each probe targets tvOS 17.0 solely for this experiment; this does not select
the product deployment minimum. Linked executables were not executed. FLAC/Opus plist slices
were inventoried but their decoder symbols/behavior have not been tested.

Both new shared-domain tvOS compilation tasks and existing iOS device/simulator tasks pass
(1m26s command wall time, not a controlled performance benchmark). The initial configuration
attempt found deferred `tvosMain` hierarchy creation; explicitly applying the default hierarchy after
declaring the targets fixes this. Existing JVM domain tests, Android domain compilation and the
architecture guard pass (1m15s). No platform production sources changed. Logs and inventories are
under ignored `build/590-feasibility/`: `apple-targets.log`, `existing-targets.log`, `inventory.json`
and `native-link-result.json`. The Mac worktree is `.worktrees/590-tvos-feasibility`.

### Authorized toolchain experiment

The owner explicitly authorized upgrading Compose, Material3 and Kotlin to test both tvOS and the
original app. The first isolated candidate uses Kotlin 2.4.21, Compose 1.12.0 and Material3
1.12.0-alpha03. Material3 is a prerelease; do not present this as an accepted production upgrade.
The Material3 fork's published Maven metadata lists that matching coordinate. Candidate selection
also references https://kotlinlang.org/docs/multiplatform/whats-new-compose-112.html and
https://kotlinlang.org/docs/multiplatform/compose-compiler.html.

Separate checkouts preserve the original catalog: `build/590-upgrade-probe` on Windows and
`.worktrees/590-tvos-upgrade` on the Mac. The Mac resolution experiment adds UI tvOS targets and
pins plugin 1.4.2 with strict mode, disables manifest fetching and pins plugin substitution to 1.12.0.
It resolves `tvosArm64CompileKlibraries` without claiming that UI actual implementations compile.
Windows requests the original app's full shared UI suite and Android/Desktop compilation with the
upgraded catalog, without the tvOS plugin. Acceptance still requires the full existing matrix,
real app rendering and matched cold/warm performance measurements.

#### November 14 experiment outcomes and next implementation step

1. Strict tvOS dependency resolution passed in 1m45s. This is dependency evidence only.
2. The first tvOS UI compilation failed for five missing actuals: `platformCoverArtBytes`,
   `decodePlatformCoverArt`, `formatNaviampDateTime`, `createNaviampLocaleEffect` and
   `PlatformLiveVisualizerSurface`. The existing iOS simulator, JVM and Android UI compile tasks
   completed, but the combined command failed overall, also reporting a strict plugin diagnostic.
3. The combined graph triggered missing-variant diagnostics for lifecycle 2.9.6 UIKit device/simulator,
   savedstate-compose 1.3.6 UIKit device/simulator and navigationevent-compose 1.1.0. Investigate
   whether these are consumed tvOS dependencies or the plugin inspecting non-tvOS/candidate variants.
   Do not disable strict mode to call the combined graph supported.
4. An isolated compile-only audit then attached the existing `src/iosMain/kotlin` native UI effects
   to `tvosMain`. The shared UI compiled for `tvosSimulatorArm64` in 45 seconds with strict mode still
   enabled. This source-directory wiring is experimental, not the proposed production layout.
   The actual effects use Foundation, UIKit/Skia and Metal boundaries. Before retaining the change,
   create the proper shared Apple owner, neutralize host-specific names and compile all original
   targets. Compile success does not establish display, focus input, VoiceOver or runtime performance.
5. The first Windows attempt failed because a fresh worktree lacked the local Android SDK path.
   Retrying with explicit ANDROID_HOME reached Android AAR validation. Published metadata inside
   `androidx.compose.ui:ui-android:1.12.0`, graphics/text and foundation declares compile SDK 37 and
   minimum AGP 9.1.0. Naviamp has SDK 36 and AGP 8.13.2. The candidate therefore fails original-app
   Android compatibility without a build-tool migration. The remaining Windows experiment was
   stopped after this decisive failure; no passing upgraded full UI suite or Desktop app is claimed.

The next work under #590 is a controlled Android build-tool compatibility experiment before
promoting the catalog upgrade: identify the compatible Gradle/JDK/AGP/KMP combination, audit the
Android library/application plugin migration and source-set/test/packaging DSL changes, install
the required SDK in the test environment, and preserve JNI, R8, lint, instrumentation, release
packaging and idle/animation checks. Compare the original app before/after; do not suppress AAR
requirements or force older Android Compose artifacts under a newer common Compose API.
Then audit the plugin diagnostics, move only proven native Apple operations into appleMain and
add opt-in UI tvOS targets. Simulator launch and direct-BASS native acceptance follow; BASSmix
feature parity remains deferred under the owner's November 14 scope decision.

Evidence: `upgrade-existing-targets.log` (missing SDK), `upgrade-existing-targets-retry.log`
(AAR validation failure; interrupted remaining work), `naviamp-590-compose-resolution.log`,
`naviamp-590-compose-compile.log` and `naviamp-590-apple-adapter-audit.log` under
`build/590-feasibility`. At that initial checkpoint, candidate catalogs/settings/build files
remained isolated and the active branch retained Kotlin 2.4.10/Compose 1.11.0/Material3 1.9.0.
Later supported-tooling results below supersede that checkpoint; those early failures alone
authorized neither an upgrade nor release support. No additional issues were created.

## SDK 37 / AGP 9 experiment and original-app gate (November 14)

The owner authorized continuing the build-tool migration and fixing the plugin before any tvOS
host work. Candidate: SDK 37, AGP 9.1.1, Gradle 9.3.1, Kotlin 2.4.21, Compose 1.12.0 and
Material3 1.12.0-alpha03. Keep JDK 21 and NDK 27.0.12077973 fixed initially. Application target SDK
remains 36 and minimum SDK remains 26; compile SDK is independent of runtime opt-in behavior.
Google documents API 37 support and Gradle 9.3.1 as the AGP 9.1.1 minimum:
https://developer.android.com/build/releases/agp-9-1-0-release-notes.

The first experiment explicitly uses `android.builtInKotlin=false` and `android.newDsl=false`.
These documented, deprecated AGP 9 compatibility switches preserve current KMP library variants,
instrumentation and source-set behavior while separating toolchain compatibility from a module
migration. They are removed in AGP 10 and are not a permanent migration strategy. Do not disable
AAR validation, force older Compose Android artifacts or change runtime behavior to pass the gate.
The supported Android KMP plugin/source-set migration remains a separately measured step under
#590 if retaining AGP 9. See https://developer.android.com/build/migrate-to-built-in-kotlin and
https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html.

Initial migration repairs:
- Explicitly enable `resValues` in the application so generated release/test/profileable labels
  survive AGP 9's feature default changes.
- Move the shared UI instrumentation fixture's SDK 34 target into `testOptions.targetSdk`; retain
  the existing pre-enforced-edge-to-edge acceptance conditions.
- Replace removed Gradle `Project.exec` packaging calls with injected `ExecOperations`, preserving
  commands, output/error handling and exit checks for jpackage and macOS signing/privacy setup.
- Explicitly pin the established NDK rather than accepting AGP's changed default native toolchain.

### Compose plugin correction

`build-logic/compose-tvos` contains Apache-2.0 upstream source pinned to
`f1b260751bac8b073519e65675fe2797dab71b8a`, a minimal local build and diagnostic corrections.
It is not published as an upstream artifact. Read its README and retained LICENSE.
Metadata rules run for all platforms and for losing conflict candidates. The original global
empty-discovery summary therefore reports gaps that the tvOS graph never selected.

The local fix collects selected module coordinates only when a configuration's native target
attribute is tvOS, and scopes empty-discovery errors to that set. It retains unmapped fork-edge
failures and normal Gradle resolution failures. Once scoped, the summary must not suppress a
selected gap merely because an unrelated graph found a working alternative version. Selection
state is reset between builds. Strict mode remains enabled. Configuration-cache reuse is not
accepted for this experiment; the upstream static bookkeeping limitation must be addressed before
claiming configuration-cache support.

Retained upstream diagnostic tests plus regressions cover original-platform/losing candidates,
genuinely selected missing variants (including another graph's working version), fork-coordinate
normalization and state reset. The combined Apple/Android/JVM compile is the integration gate;
passing only unit tests or a tvOS-only build is insufficient.

### Required original-app acceptance before tvOS host work

1. Full shared UI/JVM behavior suites, Android debug/unit tests, storage migration and architecture.
2. Android release/R8/JNI/resource validation and lint; inspect the actual R8 version after AGP
   resolution rather than assuming the older override wins. Keep native symbols/capabilities intact.
3. Stage and launch Windows: primary connection, independent Aurral account, browse/search,
   nonempty queue, real native playback state progression, pause/seek and restart persistence.
4. Pixel and Android TV with preserved profiles: connection, remote/touch navigation, playback,
   lifecycle and companion provisioning. Restore test profiles and owned network fixtures.
5. iOS device and simulator compilation, native playback/TLS/Keychain regressions, actual app
   launch; retain physical-device/audio limits honestly. Inspect mixed-platform resolved graphs
   to confirm original targets continue to use official artifacts.
6. Recheck the actual visible idle/animation budgets and matched cold/warm build measurements;
   a dependency upgrade is not automatically a runtime or compilation performance improvement.

Logs for this phase live under `build/590-feasibility`; failed setup attempts remain evidence and
are not passing compatibility results. Do not begin the tvOS host until these gates are satisfied.

The detailed reproduction patch, plugin correction, original-app test results and remaining gates
are recorded in [the SDK 37 experiment report](verification/590-sdk37-compose-upgrade.md).
That original experiment remained isolated while performance acceptance was measured. The later
stable candidate below uses matching public releases, rather than changing app versions merely
to make older community binaries resolve.

The first cold/warm iOS archive comparison of the original alpha-based experiment passed
validation, with an 8.6% cold archive cost and fast up-to-date warm builds. That stack was **not
accepted**: Windows first-show captures were blank until input/resize in some trials. Later
untouched launches and the owner's physical-display confirmation passed on the compatible Skiko
patch diagnostic stack, but do not establish the cause of those earlier failures. The unlocked
Pixel follow-up passed with its original profile restored. All unproven rendering workarounds were
removed. The newer stable candidate below supersedes that alpha dependency choice; it still needs
the remaining acceptance gates before creating the tvOS host.

### Stable dependency target clarified by the owner

The owner clarified that upgrades should normally target the newest production-ready stable
releases, then explicitly allowed beta or release-candidate libraries when engineering judgment
shows they are needed to move #590 forward. Record the concrete benefit, support/rollback cost
and full original-app validation for each exception. Do not adopt a beta simply for its larger
version number. The Material3 alpha in the original port experiment remains feasibility evidence
only, not an accepted application dependency. Keep version
pins explicit; do not use dynamic versions or describe a prerelease as a stable update.

As checked on 2026-11-14, the stable targets are Kotlin 2.4.21, Compose Multiplatform 1.12.2,
Material3 Multiplatform 1.9.0 (already used by the ordinary app), AGP 9.5.1 and Gradle 9.9.1.
See the [Kotlin release history](https://kotlinlang.org/docs/releases.html),
[Compose 1.12.2 release](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.2),
[Material3 Maven metadata](https://repo.maven.apache.org/maven2/org/jetbrains/compose/material3/material3/maven-metadata.xml),
[AGP release reference](https://developer.android.com/reference/tools/gradle-api) and
[Gradle current stable metadata](https://services.gradle.org/versions/current).
AGP 9.5 requires Gradle 9.6 or newer and supports API 37. Kotlin's fully tested compatibility
table currently ends at Gradle 9.7.0 / AGP 9.3.1; its documentation permits newer versions with
possible limitations. Test the latest stable combination explicitly and record any concrete
failure rather than treating the compatibility table alone as a failed build.

The tvOS fork is a separate constraint: its UI artifacts currently stop at 1.12.0 and its
Material3 artifacts expose only alpha versions. A stable-original-app experiment can proceed
independently, but a production tvOS target must not silently map newer common APIs to older
forks or adopt Material3 alpha as a workaround. Assess porting the matching stable sources or
waiting for matching upstream artifacts, recording the maintenance cost and acceptance evidence
under #590. Do not hold regular supported upgrades indefinitely without documenting this dependency.

### Newest-stable experiment checkpoint

The isolated stable stack now builds the original Android debug/release and Windows distributions,
passes lint (zero errors, 16 warnings), and passes 3,372 JVM/Android/Desktop cases with four
conditional Linux/live-service skips. All ten real BASS JNI integration cases executed. The Mac
passed 2,590 shared/provider simulator cases, the iOS device Kotlin compile and all 18 prebuilt
native simulator cases including BASS and real mutual TLS. Pixel, TV and Windows functional
checks include main-library and independently authenticated Aurral recommendations, actual
playback, pause/seek, Next and lifecycle/restart. Pixel and TV profiles were restored byte-identically;
owned test windows, reverse ports and fixture servers were cleaned up. See the detailed report
for setup failures, the transient TV offline observation and the limits of capture evidence.

The issue branch now uses the validated stable tooling/dependency candidate; acceptance on main
is still pending. Reproduce that metadata in a disposable baseline checkout with
[the supported migration patch](verification/590-supported-agp-upgrade.patch), excluding local-only
statistics-report settings. The supported Android KMP plugin/DSL migration and eight additional
stable library upgrades now pass the Android/JVM and iOS shared/native matrices. Shared mixer-free
capability and preference preservation passed both the original and upgraded graphs; Domain's
1,152 tests also pass on tvOS. See [the current checkpoint](verification/590-supported-upgrade-checkpoint.md)
for reproduction, exact results, native simulator proof, verified renderer source provenance and
remaining gates. The final shared graph's iOS host/Keychain and fully optimized Release archive
checks now pass. The matched cold archive totals were 855.229s baseline and 859.834s candidate;
one pair is not a statistically significant performance claim. Final Windows visible animation
measurements and API-26 runtime acceptance remain before permanent dependency adoption. The
earlier alpha stack's archive timings must not be reused as newest-stable evidence. The stable
source port now compiles UI/Foundation/Material3 on both tvOS targets and original Android/iOS;
the synthetic consumer also passes packaged-resource tests, Native links and visible rendering.
The checkpoint records the lowered Material3 ABI differences and the rebuilt-consumer evidence;
this does not establish drop-in Native binary compatibility. Finish the producer deployment-target
audit and reproducible artifact/cache pipeline before enabling the host. Retain strict diagnostics
and Core-first ownership. The issue-branch repeat passes all 6,029 broad reported cases with
eight conditional skips and final packaging; its muted real-output follow-up currently fails
Windows driver initialization and remains unresolved.

Keep minimum Android API 26 during this work. Both newest-stable APK manifests retain minimum 26,
target 36 and compile 37. API 29 is a meaningful native compositor boundary, already selected at
runtime where available, but raising the minimum alone does not speed up modern devices and would
exclude Android-9-based Fire OS 7 TVs/sticks. A minimum-version change requires a separate concrete
benefit/device-impact decision within #590; none has been made. Actual API-26 runtime acceptance
remains outstanding.
