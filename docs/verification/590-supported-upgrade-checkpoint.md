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
against official Compose 1.12.2 (`2ccc76492a770d84008c93664a4f0c008f378107`) using an
isolated index. No source checkout was modified, compiled, formatted or published. Some new/renamed
files used direct application when Git could not find an ancestor blob; the complete check returned
0 with no unresolved conflicts. This makes a stable UI source port plausible; it does not prove
ABI, Gradle/resource-plugin compatibility or runtime behavior. Keep strict dependency diagnostics.

Stable Material3 1.9.0 is a separate source-provenance problem: its official Maven POM references
Compose 1.9.1 and Kotlin 2.1.21, and its source JAR contains 327 Kotlin files across common, Darwin,
native, non-JVM, Skiko, JVM and web source sets. It must not be replaced by the port's Material3
alpha under a stable coordinate. The inspected public source JAR SHA-256 is
`a21ae02b0ec8ef4ef0eaead0d743f61474a5db85f6b193c15af401e78297c420`.
All **327/327** published Kotlin files matched the Git blob hashes in official tag `v1.9.1`,
commit `183fbac3a71a953e3d1bb1bd7c2bcbfbc48e91a0`, with no missing/mismatched files.
The comparison mapped each JAR `<sourceSet>/<package>.kt` entry to
`compose/material3/material3/src/<sourceSet>/kotlin/<package>.kt` and computed the Git blob
SHA-1 over its complete original bytes. This is full published-source provenance, not a sample.
Native target/build wiring and the separate Compose resources/Gradle plugin still require builds.

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
- [Verified official Material3 source commit](https://github.com/JetBrains/compose-multiplatform-core/tree/183fbac3a71a953e3d1bb1bd7c2bcbfbc48e91a0/compose/material3/material3)
