# Apple TV implementation and feasibility plan — issue #590

Owner: https://github.com/goosepod/naviamp/issues/590. All work in this plan belongs to that
issue; do not split it into new issues. Research baseline: main `57c266612`, November 13, 2026.
Working branch: `codex/590-tvos-feasibility`. This is an implementation plan, not a support announcement.

## Decision and current evidence

Build one shared Naviamp application with an additional tvOS host. Reuse the Television surface,
Core runtime, providers, queue, connection provisioning (including Aurral), navigation and settings.
No second tvOS controller, provider implementation or persistence policy is permitted.

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
The actual public `https://www.un4seen.com/stuff/bass-tvos.zip` was downloaded successfully:
801,783 bytes, SHA-256 `8f3bef0a67c20fdf1b65a7b6164b25bfb3a75e47e8bd5f1094eb0fad1ac3bbe8`.
Its entries contain BASS, BASSFLAC and BASSOPUS XCFrameworks with device arm64 and simulator
arm64/x86_64 directory names. Validate plist platform declarations and Mach-O build commands as
well; directory names and the shared arm64 architecture alone are insufficient.

The archive does not contain BASSmix, MIDI, WavPack, DSD, WebM, HLS, APE or MPC. Naviamp's current
iOS build links all of those. BASSmix is a playback-chain feasibility gate, not merely an optional
format gap. Do not copy an iOS binary into a tvOS slice. Confirm current versions, required symbols,
SDK minimums, device/simulator ABI and redistribution terms for every component before vendoring.
No vendor communication has been sent. If private vendor access is required, prepare the exact
missing inventory for the owner rather than asserting that a license/build is available.

Sources:
- https://www.un4seen.com/forum/?topic=10910.1100 (historical announcement)
- https://www.un4seen.com/forum/?topic=10910.1120 (historical licensed-build discussion)
- https://www.un4seen.com/stuff/bass-tvos.zip (inspected archive, not committed)
- `platforms/ios/vendor/bass/README.md` (Naviamp's existing eleven-component inventory)

### Local tools

The configured ARM64 Mac `naviamp-mac` has Xcode 27.2 and tvOS device/simulator SDKs 27.2.
No available tvOS simulator runtime was listed; an old tvOS 14.3 runtime is unavailable. SDK
compilation can start now; launch tests require an installed compatible runtime. Physical Apple TV
availability, signing/provisioning and distribution entitlement acceptance remain unverified.

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

- Obtain compatible BASSmix and resolve each missing format component/redistribution condition.
  Until then, keep full playback explicitly unproven; do not ship a silent capability regression.
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
- No app launch, playback,
  signing, physical acceptance or build-performance acceptance is claimed yet.

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
add opt-in UI tvOS targets. Simulator launch and the BASSmix/vendor inventory gate follow.

Evidence: `upgrade-existing-targets.log` (missing SDK), `upgrade-existing-targets-retry.log`
(AAR validation failure; interrupted remaining work), `naviamp-590-compose-resolution.log`,
`naviamp-590-compose-compile.log` and `naviamp-590-apple-adapter-audit.log` under
`build/590-feasibility`. Candidate catalogs/settings/build files remain in the isolated worktrees;
the active branch retains Kotlin 2.4.10, Compose 1.11.0 and Material3 1.9.0. These results authorize
neither production upgrade nor release support. No additional issues were created.

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
The original-app upgrade remains isolated while performance acceptance is measured; the ordinary
branch catalog has not been changed merely to make the community port resolve.
