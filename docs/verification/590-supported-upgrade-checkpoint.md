# Issue 590: supported Android tooling and stable dependency checkpoint

Updated November 14, 2026. This records the isolated experiment, not a release or tvOS acceptance.
The normal branch catalog still uses its existing versions. The candidate lives in the disposable
`build/590-stable-probe` checkout; the matching Mac experiment is `.worktrees/590-latest-stable`.
All work belongs to [issue 590](https://github.com/goosepod/naviamp/issues/590).

## Candidate and reproduction

Kotlin 2.4.21, Compose Multiplatform 1.12.2, Material3 1.9.0, AGP 9.5.1, Gradle 9.9.1,
JDK 21, compile SDK 37, target SDK 36, minimum SDK 26, NDK 27.0.12077973 and PMCS.
The Android 8 minimum is preserved. Use AGP's bundled R8 9.5.29; no old R8 override.

The additional stable batch upgrades lifecycle 2.9.6 → 2.11.0, serialization 1.11.0 → 1.13.0,
Ktor 3.5.2 → 3.6.1, SQLDelight 2.3.2 → 2.4.1, Bouncy Castle 1.85.2 → 1.87,
JmDNS 3.6.3 → 3.6.5, dbus-java 5.2.0 → 5.2.2 and Kover 0.9.9 → 0.9.11.
[The dependency audit](590-dependency-audit.json) records the inspected Maven metadata URLs,
original and selected stable versions for 18 catalog coordinates. It does not claim to audit
every transitive dependency, native vendor binary or development tool.

[The supported migration patch](590-supported-agp-upgrade.patch) includes the final stable pins,
Android plugin migration, release-unit-test enablement and CI metadata. Apply to a disposable
checkout of `f52687f67`, which already contains the #590 feasibility target and raster test.
Use `git apply --unidiff-zero` for this context-free patch. It supersedes the earlier bridged `590-latest-stable-upgrade.patch`; do not apply both.
Supply the existing SDK/native libraries. The local Kotlin FUS report-directory isolation used
to avoid scanning 83,470 historical reports is deliberately excluded from the production patch.

## Supported AGP graph

Nine KMP libraries use `com.android.kotlin.multiplatform.library` and `kotlin.android {}`.
Standalone Android hosts use AGP's built-in Kotlin. Neither `android.builtInKotlin=false` nor
`android.newDsl=false` remains. Android resources, host tests, UI device tests, the existing
instrumentation package and runner, Navidrome consumer keep rules and JVM 17 targets are retained.

The old `withAndroidTarget()` hierarchy predicate silently stopped matching the new Android
target: Core App's Android compile initially had no sources. The predicate now matches the
Android JVM compilation platform explicitly. `verifySharedAndroidSourceGraph` checks that every
Android main/test compilation inherits Common Main/Test. It failed before the fix and passed
for all 19 compilations afterward. Source-free compilation is not accepted as compatibility.

Two other migration failures were fixed: Android host tests require explicit `kotlin-test-junit`,
and AGP's variant API must explicitly enable the existing Release unit-test task. The first
additional-batch invocation stopped on that missing task; the successful run below includes it.
These are build metadata repairs; no platform product behavior was added.

CI now installs SDK 37 and exercises API 26, 30 and 35. API 26 runs the native app suite and
three representative UI classes in separate invocations so the UI class filter cannot silently
filter the app tests. Core UI uses `connectedAndroidDeviceTest`; the app keeps its existing task.
Result collection includes the new Core UI directory structure. Eighteen relevant Python workflow
checks and actionlint 1.7.12 passed locally. Hosted CI and actual API-26 execution remain pending.

## Completed compatibility checks

The final additional-library Windows matrix passed in **31m05s**, with 607 tasks, 581 executed.
The init script forced test bodies to execute. Across JVM/Desktop, Android host and Android app
Debug/Release reports: **5,989 tests, 0 failures/errors, 4 existing conditional skips**. These
include both JVM and Android runs of common tests; the count is not unique test behaviors.
All ten BASS JNI integration cases ran. The skips remain Linux MPRIS and opt-in real-service
Aurral checks. Coverage report/verification, Core-first architecture, Android source graph,
optimized release packaging, debug native packaging, lint, UI instrumentation APK assembly and
desktop distributable creation passed. Local log:
`build/590-feasibility/all-stable-dependencies-release-test-fixed.log`.
The inspected additional-batch unsigned Release APK is 27,943,062 bytes and retains all 72 native
libraries across ARMv7, ARM64, x86 and x86_64. Its manifest reports min SDK 26, target SDK 36,
compile SDK 37. Lint reports zero errors/fatals and 16 warnings. This packaging evidence precedes
the later shared mixer-free commit; repeat final packaging after adopting the accepted graph.

The matching Mac additional-library shared/provider simulator matrix and device Kotlin compile
passed in **7m06s**, 187 tasks, 179 executed. The native test binary linked and its BASS frameworks
staged. After the mixer-free common changes, **2,596 shared/provider iOS simulator cases** passed
with no failures/errors/skips, together with `:apps:ios:compileKotlinIosArm64` (5m02s).
The fresh-inventory native replay then executed **all 18 cases** with no failures/errors/skips,
including the real mutual-TLS contract and existing iOS mixer/downmix path; owned fixture and
simulator cleanup completed. Its first invocation omitted `--fresh` and stopped before executing
because no prior Gradle native XML inventory existed; that setup failure is retained separately.

The common implementation separates normalized portable preferences from effective engine
settings, preserves those preferences during import/reexport and unrelated settings edits,
derives downmix availability from the native contract, hides unsupported downmix controls in
both shared settings surfaces, and avoids calling a missing mixer on direct-stream release.
The upgraded Windows shared follow-up passed Android compilation, Android host tests, JVM
tests, both rendered settings regressions, architecture and the 19-compilation source-graph guard.
It reports 1,152 Domain JVM, 628 Presentation JVM, 764 UI JVM, 1,152 Domain Android host and
616 Presentation Android host cases, all passing. The original toolchain also passed Domain,
Presentation and UI JVM tests and Android Domain/Presentation compilation (7m42s). The opt-in
tvOS Domain simulator target passed all 1,152 cases (1m35s); its simulator inventory returned
to the same shutdown states. Shared implementation commit: `0880bc342`. No Android/Desktop/iOS
platform production source files changed. Logs: `direct-bass-common-windows-*` and
the Mac's `/tmp/naviamp-590-direct-bass-common-{ios,native-fresh}.log`.

Before the eight additional library changes, the supported AGP candidate also passed an iOS
host build/install/launch and a real Keychain case on a newly owned iOS 26.5 simulator. The
startup screenshot showed the complete connection UI; all eleven BASS frameworks were embedded.
The owned simulator was removed. Xcode's quiet log contains framework-copy messages described
as an error despite exit code 0; keep that diagnostic with the successful build/launch evidence,
and repeat the final host/archive validation before adopting the candidate.

The final graph (all eight additional upgrades plus shared mixer-free changes) then passed a
fresh iOS 26.5 host build/install/launch and the real Keychain test again, this time with full
Xcode logs. The startup screenshot was inspected and showed the connection UI, all provider
choices, credentials fields and shared navigation. The xcresult summary confirms exactly one
test passed, zero failures/skips. Shutdown and deletion returned 0. Evidence:
`.worktrees/590-latest-stable/build/590-final-stable-host`; local inspected screenshot:
`build/590-feasibility/final-stable-ios-startup.png`. The fully optimized Release archive pair
below also passed; the earlier quiet-log diagnostic is retained rather than relabeled a failure.

The earlier stable stack's physical Pixel/Android TV and Windows packaged runtime results are
in [the original experiment report](590-sdk37-compose-upgrade.md). Those checks predate the
additional-library batch and cannot substitute for its final runtime acceptance.

## Release build performance

A clean committed baseline at `57c266612` ran the existing unsigned Release archive command,
with an isolated native/cache home consistently used from the start, `--cold-compile`, full
optimization, PMCS, unchanged exports and all existing native inputs/validators.

| Same baseline checkout/cache | Total seconds | Optimized ARM64 link seconds | Validated |
| --- | ---: | ---: | --- |
| Fresh outputs | 855.229 | 610.572 | yes |
| Warm repeat | 27.484 | 0.032 (up-to-date) | yes |
| Candidate fresh outputs | 859.834 | 637.809 | yes |
| Candidate warm repeat | 23.636 | 0.018 (up-to-date) | yes |

Reports are under the Mac root's `build/590-stable-archive-baseline-{cold,warm}`. Both report
`sourceStable=true` and exit 0. The exported header stayed 876,770 bytes/9,929 lines,
339 interfaces/79 protocols; static framework library 221,109,176 bytes.
The candidate pair also reports `sourceStable=true`, exit 0, and all existing archive/IPA/native/
privacy/version/unsigned ARM64 validators passed. Cold total time was 0.54% higher and optimized
link time 4.46% higher; warm total time was 14.0% lower. One local pair does not establish a
statistically significant speed change. Both keep full optimization, PMCS, unchanged exports and
all eleven native frameworks. One in-progress candidate thread diagnostic confirmed LLVM
optimization, followed by native clang++ linking, rather than a statistics cleanup stall.
The candidate exported header is 876,404 bytes/9,910 lines, with the same 339 interfaces and
79 protocols; its static library is 231,224,168 bytes (4.57% larger). No export-narrowing or reduced
optimization workaround was used. Repeat on hosted CI before claiming general build performance.
The candidate is frozen at clean local-only Mac commit
`e61f9e0b1107a5d45ec7c542afb97f5becbfb0ee` in `.worktrees/590-stable-archive-candidate`,
with a distinct `build/590-stable-archive-candidate-cache` and the same archive command/validators.
It freezes the stable metadata, existing raster test and mixer-free shared changes; it is not a
published branch. Do not reuse alpha-stack timings or omit archive validators.

## Remaining work

- Finish original-app runtime checks for the accepted graph. Final iOS host/Keychain and both
  optimized Release archives passed; the final visible Windows comparison is in progress.
- Measure visible idle/individual/combined animation costs and lifecycle behavior with matching
  window, refresh, scale and power conditions. Functional rendering does not establish a budget.
- Resolve the stable Compose/Material3 tvOS port mismatch without hiding strict diagnostics or
  mapping old fork binaries beneath newer common APIs. The fork delta is 306 files, not a plugin
  switch. No new tvOS app support or release readiness is claimed.
- The public direct-BASS tvOS simulator probe passed all 17 native controls/lifecycle checks;
  BASSmix was absent, and shutdown/delete returned 0. It used a silent WAV/zero volume and does
  not establish acoustic output, FLAC/Opus decoding, full Naviamp integration or physical-device
  acceptance. Runtime core version: 2.4.18.14. Reproduce with `scripts/probe-tvos-bass-runtime.py`
  and the hash-pinned public archive/header directory. Evidence: Mac root
  `build/590-tvos-direct-bass-runtime-validated`. Early probe setup failures (incompatible simulator
  model, legacy UIKit lifecycle) and a C BOOL/JSON result-classification bug were corrected;
  do not count those attempts as passing. All created simulators were removed.
- Connect only native ABI/audio-session effects to the complete shared application after the
  remaining original-app and stable-renderer prerequisites pass.

## Stable renderer source feasibility

The 306-file tvOS port delta from `f29d2f99f3beafc60992c6a73dbbaa9f841c5d4c` to
`6e266fe9e511c413e63d51011be1659c3696fee6` passed `git apply --cached --check --3way`
against official Compose 1.12.2 (source commit `5f017375d926c73f1937408601b6cb28c12a8812`,
annotated tag object `2ccc76492a770d84008c93664a4f0c008f378107`) using an
isolated index. At that initial checkpoint no source checkout was modified or compiled. Some new/renamed
files used direct application when Git could not find an ancestor blob; the complete check returned
0 with no unresolved conflicts. This makes a stable UI source port plausible; it does not prove
ABI, Gradle/resource-plugin compatibility or runtime behavior. Keep strict dependency diagnostics.

Stable Material3 1.9.0 is a separate source-provenance problem: its official Maven POM references
Compose 1.9.1 and Kotlin 2.1.21, and its source JAR contains 327 Kotlin files across common, Darwin,
native, non-JVM, Skiko, JVM and web source sets. It must not be replaced by the port's Material3
alpha under a stable coordinate. The inspected public source JAR SHA-256 is
`a21ae02b0ec8ef4ef0eaead0d743f61474a5db85f6b193c15af401e78297c420`.
All **327/327** published Kotlin files matched the Git blob hashes in official tag `v1.9.1`,
source commit `6ba48befdf216eca527ea4209ec37bdf2ed838c9` (annotated tag object
`183fbac3a71a953e3d1bb1bd7c2bcbfbc48e91a0`), with no missing/mismatched files.
The comparison mapped each JAR `<sourceSet>/<package>.kt` entry to
`compose/material3/material3/src/<sourceSet>/kotlin/<package>.kt` and computed the Git blob
SHA-1 over its complete original bytes. This is full published-source provenance, not a sample.
The subsequent source/native build evidence below supersedes that initial apply-only checkpoint.

Use this as the port sequence: apply the tvOS UI/native delta to official UI 1.12.2; build stable
Material3 1.9.0 from its verified 1.9.1-tag sources with tvOS/Darwin source-set wiring, rather than
using the newer alpha Material3 implementation; port the matching Compose 1.12.2 Gradle/resources
plugin; publish only to an isolated local experiment repository with reviewed hashes/metadata;
resolve the complete opt-in Naviamp tvOS graph under strict diagnostics and prove original-target
configurations still use official artifacts. Then compile, format and test the changed upstream
modules, compare public/common and native APIs, and render shared Television pages in the simulator.
The source checks alone are not permission to label an artifact as official or release-ready.

Primary inputs:

- [Official Material3 1.9.0 POM](https://repo.maven.apache.org/maven2/org/jetbrains/compose/material3/material3/1.9.0/material3-1.9.0.pom)
- [Official Material3 1.9.0 source JAR](https://repo.maven.apache.org/maven2/org/jetbrains/compose/material3/material3/1.9.0/material3-1.9.0-sources.jar)
- [Verified official Material3 source commit](https://github.com/JetBrains/compose-multiplatform-core/tree/6ba48befdf216eca527ea4209ec37bdf2ed838c9/compose/material3/material3)

An actual uncommitted source worktree now exists at the Mac root's
`build/590-compose-core-stable-source`, based on the peeled official 1.12.2 commit above.
The pinned tvOS patch applied there without unresolved conflicts. No upstream commit or artifact
publication was made. Stable Material3 overlay preparation and build checks are described below.

The stable overlay is now prepared: all 327 published Kotlin files were copied byte-identically
into the corresponding seven source sets, 37 alpha-only Kotlin files in those same sets were
removed, and the stable annotation/UI-graphics dependencies were restored in the newer build DSL.
The tvOS/Darwin target wiring remains. The first `help` configuration failed because the old
`:annotation:annotation` project no longer exists in the selected upstream graph. The dependency
now uses the existing stable `androidx.annotation:annotation:1.9.1` coordinate; its official metadata
includes both ARM64 tvOS variants. Corrected configuration passed in 34 seconds, with configuration
on demand disabled because upstream warns it is unsupported for KMP. No publication task ran.

An initial native compile started with the default native home; changing the native home after
that left old absolute platform-library paths in cached metadata. The subsequent 2m41s run failed
on duplicate KLIB identities from the old/new homes, before providing accepted renderer evidence.
The source experiment now uses one consistent dedicated native home from startup, **fresh**
Gradle/project/output directories and `--no-build-cache`. No existing user cache was removed.
Preserve these failed logs; do not diagnose the mixed-cache failure as missing tvOS support.

Current layout at the Mac root: `build/590-compose-port-gradle-isolated`,
`build/590-compose-port-project-cache-isolated`, `build/590-compose-port-output-isolated` and
`build/590-compose-port-konan`. Native compile log:
`/tmp/naviamp-590-compose-stable-native-fresh-layout.log`. The opt-in
`scripts/probe-compose-tvos-source-graph.init.gradle` guard verified **20** device/simulator paths
across ten required modules. Eight modules inherit real Common Main Kotlin sources. Runtime and
Runtime Saveable deliberately use empty redirect proxies: the guard instead proves their resolved
official AndroidX 1.12.1 variants have the exact requested native target attribute. An empty,
unbacked compilation is rejected. Foundation has 354 common Kotlin files, Foundation Layout 32,
and stable Material3 218 (327 across all seven overlaid source sets).

The actual native build found one source incompatibility: the tvOS UIKit container supplied Skiko's
theme enum where current Compose expects its own enum. It now uses the same shared
`asComposeSystemTheme()` conversion as the current iOS container. Both tvOS device and simulator
UI, Foundation and stable Material3 compiled successfully in **2m00s**, 148 tasks, 53 executed.
All **403** added/modified Kotlin files were then formatted with upstream `:ktCheckFile`; the
post-format native rebuild also passed in **2m24s**, 148 tasks, 109 executed, with the 20-path
guard passing again. Logs: `/tmp/naviamp-590-compose-stable-native-theme-fixed.log`,
`/tmp/naviamp-590-compose-all-source-format.log` and
`/tmp/naviamp-590-compose-stable-native-formatted.log`. Formatting can change whitespace from the
original source-JAR bytes; the 327/327 provenance result describes the verified input overlay.
Public/native API checks and an actual renderer remain gates; original-target source results follow.

The original-target source build subsequently passed in **2m19s**, 168 tasks, 33 executed:
UI, Foundation and stable Material3 on Android and the iOS ARM64 simulator. The upstream rules
name the former `compileDebugKotlinAndroid` task; task discovery showed this source tree now uses
`compileAndroidMain`. The first invocation failed on that absent legacy task. The next failed
because SDK 27.2 rejects the upstream Objective-C helper's iOS 13 deployment target. A build-only
init-script override adds `IPHONEOS_DEPLOYMENT_TARGET=17.2` immediately before that helper's
`xcodebuild` execution, matching Naviamp's existing minimum. An earlier override was overwritten
during task configuration; the corrected execution-time override produced the passing build.
No Naviamp platform requirement or production source changed. Log:
`/tmp/naviamp-590-compose-source-original-targets-sdk27-fixed.log`.

The first UI/Foundation `klibApiCheck` invocation **failed**, as expected for an unchanged baseline
that does not contain the added targets, but its differences must still be reviewed. The UI dump
includes tvOS target declarations, the private library unique name, UIKit APIs shared with tvOS,
tvOS clipboard types and an additive `KeyEvent.isRepeat` property. Existing drag/drop declarations
become explicitly restricted to the original targets. Do not overwrite golden files and call this
compatibility proof. Foundation's check was not reached. The stable Material3 API reference must
also come from its verified stable source, not the current alpha baseline. Evidence:
`/tmp/naviamp-590-compose-source-api-check.log` and the source experiment's `build/api` outputs.

`scripts/compare-klib-api-targets.py` projects BCV declarations onto explicitly named original
targets, expanding aliases and preserving nested/member restrictions. Five regression cases
exercise scope isolation, member overrides, target renaming, missing inputs and unknown targets.
The actual UI comparison found **zero removed declarations** on iOS device/simulator, macOS ARM64,
JS and Wasm. Each target has exactly two added declarations: `KeyEvent.isRepeat` and its getter.
The tool still exits nonzero for additions; this is reviewed evidence, not a weakened API gate.
It compares declaration inventories, not runtime behavior or binary link compatibility. JSON:
`build/590-compose-stable-port-check/ui-original-target-api-comparison.json` on the Mac.

Foundation's separate raw check also failed on the new targets/private library name. Its explicit
per-target comparison found **zero added or removed declarations** on all five original targets.
Material3's first full ABI merge found a missing `PlatformDateFormat` actual for JS/Wasm: its
stable `webCommonW3C` source was not in the seven source-JAR directories overlaid earlier. The
missing file was restored from the same verified stable source commit into the current `webMain`
source set and formatted with upstream `:ktCheckFile` (30 seconds). The subsequent complete
ABI merge passed in **1m04s**, 288 tasks, 22 executed. The 327 source-JAR provenance claim must
not be treated as proof that platform sources outside that JAR were also complete.

Stable Material3 comparison requires care: the current upstream ABI extractor hides
`ExperimentalMaterial3Api` declarations, whereas the older reference includes them. The first
unadjusted comparison therefore reported 894 removals and one addition per original target; that
is not evidence of 894 deleted source APIs. The comparison-only
`scripts/probe-material3-stable-api.init.gradle` explicitly includes those declarations on all
seven KLIB targets and refuses normal check/golden-update tasks. An initial callback failed to
bind the task properties; its corrected, verified extraction ran all seven API tasks and passed
in 33s. The complete report still exits nonzero: 398 removed compiler-generated `$stableprop`
declarations, one changed lowered `SliderDefaults.Thumb` signature, and three added lowered
signatures (Thumb plus two tooltip overloads). Preserve these results and compare the actual
published stable binary with equivalent extraction settings before approving compatibility.
Do not claim source API deletion or binary compatibility from the raw historical golden alone.

Official Skiko 0.150.2 Maven metadata already exposes both `skiko-tvosarm64` and
`skiko-tvossimulatorarm64` API/source/metadata variants. Those exact coordinate metadata files
were fetched successfully; an older Skiko fork or the rejected 0.153 override is not required
merely to resolve those targets. Native linking/rendering and dependency closure still need tests.
Keep any source-port build/cache pipeline separate from Naviamp's normal application builds:
ordinary Android/Windows/iOS development must not rebuild AndroidX or resolve tvOS forks.

## Matching resources and Gradle plugin source port

The second public repository is `sajidalidev/compose-multiplatform`, not the former redirected
`compose-multiplatform-tvos` name. Its pinned tvOS commit is
`9897904fab44c811affcd818b14b653bac0cf1ea`, based on
`d67175a3766c51caa673b220c74a4fb68a61fd31`. Its 17-file delta (581 additions, 129 deletions)
passed a private-index three-way apply check against official 1.12.2 source commit
`ab304f2538657d9f7077bdb40797f5db618b4c84`, then applied without unresolved conflicts to
`build/590-compose-resources-stable-source` on the Mac. No upstream commit or publication was made.

Do not accept that patch unchanged. It pins 1.12.0/Material3 alpha versions, fetches a moving
manifest through a remote redirect plugin, and comments out **11** test source-set blocks.
The experiment restores all 11 blocks, pins Compose/deploy 1.12.2 and Material3 1.9.0, and uses
the reviewed local Naviamp redirect-plugin source with manifest fetching disabled and strict
diagnostics enabled. Explicit same-version mappings use the private `app.naviamp.tvosprobe`
namespace, not official coordinates. Repositories and Gradle/Native/Maven caches are isolated;
no user Maven cache or normal app dependency graph is overwritten. Configuration/compilation,
resource-reader tests and actual framework resource copying remain to be verified.

The first configuration was intentionally stopped through its dedicated Gradle user home after
building the local plugin: its local source artifacts did not exist yet, so continuing remote
discovery could not prove a working graph. The interrupted log is retained at
`/tmp/naviamp-590-compose-resources-frozen-configure.log`. The next step stages only explicit
UI/Foundation/animation/ripple/Material3/NavigationEvent modules, umbrella metadata and the two
tvOS publications into `build/590-compose-port-output-isolated/repository`. This is a local
consumer experiment under `app.naviamp.tvosprobe`, not acceptance of the still-unreviewed API
baseline, a central publication or an alteration to original-target app resolution.

The initial staging succeeded in **3m39s**, 863 tasks (731 executed), and the resource library
actually compiled on both tvOS targets, Desktop, Android and the iOS simulator in **8m07s**,
37 tasks (17 executed), with strict diagnostics. Those compiles are **not accepted final-graph
evidence**: inspection found NavigationEvent's unpinned `9999.0.0-SNAPSHOT` publication. No central
publication occurred. Preserve the first repository and log instead of silently relabelling it.

`scripts/probe-compose-tvos-local-publication.init.gradle` now requires an explicit canonical local
path inside the experiment, the private namespace, and exact versions for all **48** publications
from 16 selected modules. It rejects the real snapshot configuration before staging. The corrected
producer pins `jetbrains.publication.version.NAVIGATION_EVENT=1.1.0`; the guard passed in 25s.
That JetBrains stable wrapper delegates to AndroidX **1.1.1**, as its official POM confirms.
Its own source JAR is an empty wrapper, not implementation provenance. All **6/6** implementation
Kotlin files in Google's AndroidX 1.1.1 source JAR match the pinned official UI source commit;
SHA-256 `25e36c0afa796e71ea2979ae37fff10db56bc3154df0bd90a0f3f1a19693ccaa`.
Google's metadata also has real tvOS variants. The source module is a full redirect proxy to that
official AndroidX coordinate; do not describe it as a new NavigationEvent implementation.

Guarded staging into the fresh `build/590-compose-port-repository-reviewed` passed in **54s**,
864 tasks (256 executed), producing 48 module metadata files in a 54-MiB local repository.
The resource consumer now points only at this repository for private artifacts. Its repeated
strict original/tvOS compiles passed in **6m09s**, 37 tasks (5 executed), with dependency refresh
explicitly requested. Its restored test suite then **linked** for tvOS ARM64 simulator in
**1m24s**, 34 tasks (12 executed). This establishes a real Native linker closure for this test
binary, beyond individual KLIB compilation.

On a disposable tvOS 27.2 simulator the suite then executed **51 actual cases**, **0 failures,
errors or skips**, verified from seven XML reports. The warm test invocation took **36s**,
35 tasks (4 executed). Tests cover shared resource selection, strings/plurals, font caches and
vector XML; fixtures/fake readers are used where the tests specify them. This does not establish
full host/framework bundle copying or visible television rendering. Owned simulator shutdown and
delete both returned 0. Evidence: `/tmp/naviamp-590-compose-resources-native-test-link.log`,
`/tmp/naviamp-590-compose-resources-native-tests.log`,
`build/590-compose-resources-native-test-run/summary.json` and
`build/590-compose-stable-port-check/resources-native-test-summary.json` on the Mac.

The guarded repository audit verified **48** metadata modules and **34** KLIBs (32 main plus
two UIKit C-interop artifacts), exact stable versions, no snapshot metadata, the correct tvOS
device/simulator native manifests and SHA-256 hashes. The first audit expected only 32 KLIBs and
correctly stopped on the extra two; inspection identified the required interop artifacts before
the corrected inventory passed. Record: `build/590-compose-stable-port-check/guarded-artifact-inventory.json`.
Next validate a Kotlin 2.4.21 consumer and actual renderer/resource bundle before wiring Naviamp.

Reproduction flags for the source producer (retain its official Kotlin 2.3.20/Gradle 9.5 toolchain):

```text
-Pcompose.platforms=KotlinMultiplatform,Android,IosArm64,IosSimulatorArm64,TvosArm64,TvosSimulatorArm64
-Pjetbrains.publication.version.COMPOSE=1.12.2
-Pjetbrains.publication.version.COMPOSE_MATERIAL3=1.9.0
-Pjetbrains.publication.version.NAVIGATION_EVENT=1.1.0
-Ppublication.coordinateRoot=app.naviamp.tvosprobe
-Pnaviamp.tvos.probeRepository=/absolute/owned/experiment/repository
--init-script /absolute/path/scripts/probe-compose-tvos-local-publication.init.gradle
```

Run `verifyTvosCommonSourceGraph` with its separate init script and
`verifyTvosProbePublicationCoordinates` before selected umbrella/device/simulator publications.
Do not run a whole-repository release task or stage other Material3-group alpha modules beneath
the stable main-module version. Keep configuration on demand off, explicit cache roots from job
startup, limited workers and full logs. The app consumer must still prove compatibility with
Kotlin 2.4.21; do not upgrade an upstream source producer solely to match its consumer version.
