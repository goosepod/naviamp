# Issue 590: supported Android tooling and stable dependency checkpoint

Updated November 15, 2026. This records the issue-branch candidate, not a release or tvOS acceptance.
The supported tooling/dependency patch is now applied to `codex/590-tvos-feasibility` for normal
branch verification and CI. Main/release acceptance is still pending. The original isolated
candidate remains in `build/590-stable-probe`; its Mac counterpart is `.worktrees/590-latest-stable`.
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

CI now installs SDK 37 using its actual stable package ID, `platforms;android-37.0`, and exercises
API 26, 30 and 35. The installed SDK's `package.xml` confirms that ID; `android-37` would not
select the same package. API 26 runs the native app suite and
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
compile SDK 37. Lint reports zero errors/fatals and 16 warnings. This earlier packaging evidence
precedes the shared mixer-free commit; the final issue-branch repeat is recorded next.

### Repeat on the issue branch

After applying the supported metadata to the issue branch, the full Android/JVM/Desktop matrix
was repeated with test bodies forced to execute. The first invocation stopped after **11m10s**
because the shell could not locate CMake for Desktop JNI; the preceding shared tests passed.
Supplying the installed SDK's `cmake/3.22.1/bin/cmake.exe` through the existing `CMAKE_EXE`
input fixed the tool lookup, without a source change. The continuation passed in **6m37s**,
327 tasks (195 executed), including Desktop/app Debug and Release tests and coverage verification.

The union of the active tasks' XML reports contains **6,029 cases, zero failures/errors and
eight conditional skips**, across 22 tasks/914 report files. This count includes both JVM and
Android executions, plus 40 Testkit/TLS-Testkit cases omitted from the older report collector.
Windows used the CI no-sound output device (`0`): four real-output cases were skipped in addition
to the four Linux/live-service conditional cases. The no-sound run does not establish real-output
clock/recovery acceptance. Reports were preserved before the selected native follow-up overwrote
the Desktop XML directory: `build/590-feasibility/root-upgrade-broad-test-reports.zip` and
`root-upgrade-test-summary.json`. Logs: `root-upgrade-verification.log` and
`root-upgrade-verification-native-fixed.log`.

The first 13-case muted real-output follow-up **failed**: 11 failures at driver initialization or
dependent playback timeouts, with `BASS_ERROR_DRIVER` (3). An outside-sandbox repeat using the
default output, rather than explicit `-1`, also failed (2m19s, 57 tasks). The Windows audio services
were running, but BASS's inventory listed only no-sound and the default placeholder. Direct
initialization was inconsistent: two byte-identical current/previously passing BASS DLLs yielded
one success and one driver failure. Those attempts do not establish an upgrade regression or
real-output acceptance. Evidence:
`root-upgrade-real-output.log`, `root-upgrade-real-output-default.log`,
`root-real-output-driver-failure-reports.zip` and `root-windows-audio-endpoint-check.json`.

After the owner confirmed normal visible rendering, BASS's inventory included the BenQ monitor's
real audio endpoint and default initialization succeeded. The unchanged muted real-output suite
then **passed all 13 cases, zero failures/errors/skips**, in **2m19s**, 57 tasks (17 executed): all
ten JNI cases plus preview, shared discovery preview and persisted radio playback. No output
assertion, native source, system setting or test volume was changed. Log:
`build/590-feasibility/root-upgrade-real-output-connected.log`; exact XML inventory:
`root-real-output-connected-summary.json`. Keep the earlier failures as evidence of the
unavailable/intermittent endpoint. This validates native clock/control behavior, not acoustic
output: generated WAV fixtures are silent and test volume is zero.

Computer Use also observed the real upgraded packaged window after bringing it to the foreground:
artwork, title/metadata, player/volume controls, progress and Home panel were painted normally.
An earlier capture included the overlapping Codex window; it is not rendering acceptance.
The owner's independent physical-display confirmation agrees with the unobscured capture.
This resolves functional presentation, while motion/compositor measurements remain outstanding.
Replacing the same 13 task/class/method entries in the preserved broad inventory gives **6,029
cases, zero failures/errors and four remaining conditional Linux/live-service skips**. This is
a combined run inventory, not 6,029 plus 13 distinct behaviors. Evidence:
`root-upgrade-combined-test-summary.json` and `root-real-output-connected-reports.zip`.

Final issue-branch lint, Debug native package verification, optimized Release APK/AAB validation
and Windows release-distributable packaging **passed in 10m01s**, 434 tasks (261 executed).
The Release APK is **27,943,062 bytes**, with min SDK **26**, target **36**, compile **37** and
72 native libraries across ARMv7, ARM64, x86 and x86_64. Lint reports zero errors/fatals and
16 warnings. Log: `build/590-feasibility/root-upgrade-packaging.log`.

The five ABI-comparator, six tvOS feasibility/runtime-parser and six translation/workflow checks
passed, together with actionlint 1.7.12. The three POSIX workflow-scope tests passed on the Mac
with canonical LF checkout semantics; initial Windows WSL-shim access and raw-CRLF transfer
failures were setup failures, not passing test runs. No test or script was weakened.

### First hosted CI run and Android test packaging repairs

[Verify run 884](https://github.com/goosepod/naviamp/actions/runs/43667019529), at
`c7cbf855093002c6b7b81d45d4cd73fae6fdd0e6`, passed shared/Android unit tests and coverage,
Windows/macOS/Linux native jobs and the full iOS simulator/host/Keychain job. All three Android
emulator jobs failed in test setup, so this run does **not** establish API-26 runtime acceptance:

- API 26 stopped compiling the app's instrumented tests because `kotlin.test.Test` was unresolved.
  The standalone app's `androidTestImplementation` also needs explicit `kotlin("test-junit")`,
  just like its migrated unit tests. No test source/import or assertion changed.
- API 30/35 each reported 46 UI cases with 45 failures: the test APK omitted ComponentActivity
  and the three native probe activities, so tests could not launch their host. The added manifest
  appeared in `Sources.manifests.all` but not in `ProcessTestManifest`'s inputs. The primary
  source remained the nonexistent default `src/androidDeviceTest/AndroidManifest.xml`.

The repair uses the public `SingleArtifact.MERGED_MANIFEST` transformation with AGP's manifest
merger to apply the existing selected fixture. It preserves the generated runner, application ID,
SDK values, dependency components and merger diagnostics; it does not reconstruct product UI.
The initial direct-task-input experiment was overwritten by AGP's task creation and was discarded.
The second-stage merger must accept the first stage's generated `<uses-sdk>`: its lenient handling
is limited to that generated input, and the fixture is rejected if it declares `<uses-sdk>`.
The original AGP source-manifest SDK validation remains enabled. No original merge errors or test
assertions were suppressed.

Both instrumented APKs assembled after the test-dependency fix (4m56s, 220 tasks/92 executed).
The final artifact transformation, both APKs and architecture/source ownership guards then passed
in 16s, 223 tasks/23 executed. Actual APK manifest inspection confirms all four fixture activities,
min SDK 26, target SDK 34 and the original runner/package. A separate no-probe manifest invocation
passed in 5s and keeps ComponentActivity without the probe activities. Logs:
`android-device-test-packaging-fixed.log`, `android-device-test-artifact-merge-generated-sdk.log`
and `android-device-test-normal-manifest-fixed.log` in `build/590-feasibility`.
Hosted emulator execution of this repair passed in
[Verify run 885](https://github.com/goosepod/naviamp/actions/runs/43680794888), at
`595f6d45bab1d3fd0166007802a5888580d18fe5`:

| Emulator | App cases executed | App opt-in skips | Shared UI cases executed | Failures/errors |
| --- | ---: | ---: | ---: | ---: |
| API 26 / Android 8.0 | 23 | 24 | 4 | 0 |
| API 30 / Android 11 | 23 | 24 | 46 | 0 |
| API 35 / Android 15 | 25 | 22 | 46 | 0 |

Each app report inventories 47 cases; optional physical-device/live-provider/soak checks retain
their existing explicit opt-ins. API 26's three selected UI classes contain four cases. API 30/35
now execute the previously unlaunchable 46-case UI suite without skips. Preserve the distinction
between API-26 native/UI boundary acceptance and a complete physical-phone/TV feature matrix.
Local inventories: `build/590-feasibility/ci-api{26,30,35}-test-summary.json`; downloaded reports:
`ci-43680794888-api{26,30,35}`. No minimum Android version was raised.

The same run's Windows, macOS and Linux jobs also passed. The Windows integrated shared-widget
probe reports static **0.469%**, marquee **0.469%**, waveform **0.156%** and combined **0.625%** of
one core, each with zero parent frames in its interval. Its hosted window is 1000×740, scale 1.0,
reported refresh 64 Hz; menu hover reports twelve visible-text samples passed. These are hosted
synthetic/shared-widget results, not the missing matched full-application CPU/GPU acceptance.
Evidence: `build/590-feasibility/ci-43680794888-windows/build/windows-{animation,menu-hover}-probe.txt`
(PowerShell UTF-16 output).

The vendored **redirect** plugin's own 25 regressions passed under Gradle 9.9.1/Kotlin 2.4.21
(1m37s, seven executed tasks), covering diagnostic selection/bookkeeping and strict genuine gaps.
This is separate from the resources producer's Compose Gradle plugin. Log:
`build/590-feasibility/pinned-compose-tvos-plugin-tests.log`. The reproduction patch was also
applied to `f52687f67` in an isolated scratch index and exactly reproduced all 18 current metadata
files; the real index/checkout was unchanged.

The local full-app performance attempt did not produce a sample: the instrumented baseline
launcher failed twice to expose a targetable window, even after correcting test-copy CRCRLF
configuration lines introduced during harness editing. Classpaths/runtime versions were checked;
the startup failure remains unclassified. No blank/missing window or zero observation was counted
as a performance pass. Both disposable launcher configurations and synthetic databases were
restored byte-identically to their pre-attempt backups; the newly owned port-18087 fixture was
stopped after verifying its process identity. The Computer Use observation helper was reset.
No installed app/profile, system setting or production JAR/runtime was modified.

The full run subsequently **passed**, including shared unit tests/coverage, iOS simulator/native
checks, the production iOS app build, real Keychain test and verified-source-tree evidence.
Every ordinary verification job passed; the explicitly opt-in diagnostic iOS fixture job was
skipped. This records source commit `595f6d45bab1d3fd0166007802a5888580d18fe5`; later documentation
updates do not retroactively change that source/evidence identity. The PR remains a draft because
the tvOS host and matched full-app animation acceptance are unfinished.

### Mac and shared playback validation

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
which required the final host/archive repeat described next.

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

- Finish original-app runtime checks for the accepted graph. Final iOS host/Keychain, both
  optimized Release archives, Windows native playback and normal visible rendering passed.
  The repaired Android API 26/30/35 runtime gates and full shared/iOS CI run passed.
- Measure visible idle/individual/combined animation costs and lifecycle behavior with matching
  window, refresh, scale and power conditions. The [November 14 full-app comparison](590-windows-performance-2026-11-14.md)
  now records that matrix, visible motion, lifecycle checks, payload hashes and cleanup. It found
  and removed observation-helper interference. Settled combined samples had equal CPU and zero
  parent frames in both graphs; process outliers and differing whole-desktop GPU readings remain
  explicit limits. Final performance acceptance is still required; functional rendering alone
  does not establish a budget.
- The stable Compose/Material3 source port now compiles, links and visibly renders with packaged
  resources, as recorded below. Finish reproducible artifact production/cache identity and audit
  helper/vendor deployment targets before full-app integration. The fork delta is 306 files,
  not a plugin switch. No new tvOS app support or release readiness is claimed.
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

## Shared waveform prerequisite for the Apple BASS adapter

Before moving the iOS native adapter into an Apple owner, its portable waveform bucketing loop
was found duplicated in shared JNI C++. That algorithm now lives in Domain's
`BassWaveformAnalysis.kt`, with the complete `BassAudioBackend` default and a narrow
`readMonoRmsLevel` effect. All three existing hosts use the common duration-weighted RMS buckets,
one-second native windows, normalized bucket limits and fail-fast rewind/read validation.
Zero-length input and invalid/non-finite values have explicit common tests. No partial waveform
is returned after a native failure. The existing waveform service retains its fallback policy.

Core implementation/tests compiled for Android, JVM and both iOS architectures **before**
native wiring. Eleven new common cases bring Domain to **1,163 passing cases on each of JVM,
Android host and iOS simulator**, with zero failures/errors/skips. The actual Windows BASS/JNI
suite reported 11 cases: **ten executed/pass, one real-output recovery case skipped** under the
explicit no-sound device. The new generated-tone test exercises the Core default, native RMS,
rewind of a partly consumed decoder and invalid-handle failure. Android instrumentation APK
assembly passed, followed by actual debug APK native-package verification for every packaged ABI;
the added Android runtime assertion subsequently passed in CI on API 26, 30 and 35.

Verify run **887**, source `649675e20b197c3345fb3300cae191329ca1e3b1`, completed successfully:
shared/coverage, all three Android levels, Windows/macOS/Linux, iOS host/native/Keychain and
verified-source evidence. This verifies the waveform commit, not subsequent uncommitted work.

The unchanged 18-case required iOS native inventory **passed with zero failures/errors/skips**,
including the added waveform assertions, real mutual TLS, codecs, storage and main-queue delivery.
Its fresh prebuilt execution took 7.780s, binary SHA-256
`3589330340f31635f4184411b68eca726d10b90135a97f9ed7303bf2a49c3aa9`.
The first local native invocation omitted TLS bootstrap and failed that case; the first fixture
retry omitted the readiness evidence report and stopped before native execution. Both setup
failures are retained. The corrected invocation supplied the repository's disposable fixture,
fresh readiness reports and bootstrap, and ran all required cases. Owned simulators and fixture
were cleaned up; the existing user simulator was left alone.

Evidence: `build/590-feasibility/common-waveform-{contract-windows,native-windows,android-package}.log`,
`common-waveform-domain-reports-v2.zip`, `common-waveform-desktop-native.xml` and
`common-waveform-ios-summary.json`. The first flattened report ZIP is superseded by the V2 archive
that preserves separate platform directories. Mac evidence is in the isolated
`build/590-apple-native` checkout, including all initial/corrected logs and fresh native XML.

Platform production diff audit:

- `platforms/android/.../AndroidBassJni.kt`: declares the JNI RMS symbol and translates its native
  failure sentinel into a nullable result.
- `platforms/android/.../AndroidBassAudioBackend.kt`: maps that JNI result/error code into the
  shared RMS effect; it no longer owns waveform orchestration.
- `platforms/desktop/.../DesktopBassJniBinding.kt`: declares the Desktop JNI RMS symbol and
  translates its native failure sentinel.
- `platforms/desktop/.../DesktopBassAudioBackend.kt`: maps the Desktop native result/error code
  into the shared effect.
- `platforms/ios/.../IosBassPlayback.kt`: pins the native output buffer and invokes the BASS C ABI;
  the portable bucketing loop was removed.
- `native/bass-jni/{include/naviamp_bass_jni.h,src/naviamp_bass_jni.cpp}`: exports the two JNI entry
  points and performs one BASS mono RMS read; the duplicated C++ bucket algorithm was deleted.

No setting, export schema, user-facing copy, animation cadence or Android minimum changed. This
is an Apple-adapter prerequisite, not a claim that the Naviamp tvOS host is implemented.

## Shared audible-position and internet-stream policy prerequisite

The next adapter audit found audible-position arithmetic duplicated in JNI C++ and iOS, plus
network defaults embedded in native adapters. Both now have common ownership: Domain chooses
the decoded/mixer-history position and direct-output buffer compensation; native effects return
only position/buffer facts. Ten common cases cover invalid/unavailable values, clamping, mixer
fallback and delivery/validation of the complete seven-field `BassInternetStreamPolicy`.
The existing internet defaults are preserved and applied explicitly before URL stream creation.

Do not carry forward `BASS_CONFIG_NET_PREBUF_WAIT` (60): the old JNI code ignored its return.
Actual Windows BASS 2.4.18.3 and iOS both reject writes of 0 and 1 with error 37 (unavailable).
The first iOS attempt exposed this and failed two native cases; that failed evidence is retained.
The corrected adapter applies the seven supported settings, with native read-back assertions.
Windows availability evidence: `build/590-feasibility/windows-prebuffer-wait-availability.json`.

Common compilation on Android/JVM/iOS preceded native changes. Corrected final tests passed:
**1,173 Domain cases each on JVM, Android host and iOS simulator**, zero failures/errors/skips;
Windows native suite **10 executed/pass, four conditional real-output skips** with explicit
no-sound output; Android instrumentation assembly and four-ABI debug native packaging. A separate
real-output attempt failed BASS initialization with driver error 3; it is retained, not counted
as a pass. Physical output/clock recovery still needs a usable Windows endpoint.

All **18 required iOS native cases passed**, including real mutual TLS and the new native
network-setting assertions, in 9.402s. Binary SHA-256:
`4a848f3b8df1bb063d23f904c0d7cffb75c78e9e942e6c4e9330c9c4bb35606e`.
Owned fixture and simulator were cleaned up. Evidence under `build/590-feasibility`:
`apple-read-policy-supported-windows.log`, `apple-read-policy-{windows,ios}-summary.json`,
and `apple-read-policy-real-output-driver-failure.zip`; Mac checkout
`build/590-apple-native/build/apple-read-policy-supported-execution`.

The public tvOS BASS probe now actually passes **21/21 checks on tvOS 27.2**, adding EQ,
mono RMS and set/read-back of all seven network options to the earlier control/callback tests.
This includes `NET_META` (71), which is available in this binary. Version is 2.4.18.14, mixer
absent. Shutdown/delete both returned 0. The silent fixture does not verify acoustic output.
Public archive SHA-256 remains
`8f3bef0a67c20fdf1b65a7b6164b25bfb3a75e47e8bd5f1094eb0fad1ac3bbe8`.
Evidence: `build/590-feasibility/tvos-direct-bass-api-summary.json`; full Mac record
`build/590-tvos-direct-bass-api`. The six Python feasibility/parser checks also pass.

Platform production diff audit for this extraction:

- `platforms/android/.../AndroidBassAudioBackend.kt` and `AndroidBassJni.kt`: translate the
  shared network values and mixer-position read across the Android BASS JNI ABI.
- `platforms/desktop/.../DesktopBassAudioBackend.kt` and `DesktopBassJniBinding.kt`: translate
  the same values/read across the Desktop JNI ABI.
- `platforms/ios/.../IosBassPlayback.kt`: invokes seven BASS configuration operations and reads
  native mixer position; portable position arithmetic was deleted.
- `native/bass-jni/{include/naviamp_bass_jni.h,src/naviamp_bass_jni.cpp}`: implements those JNI
  signatures/BASS calls; deletes native network defaults and audible-position policy.

No new setting, export schema, UI copy or Android minimum change. The next step is the shared
Apple C-ABI adapter and opt-in direct tvOS backend; full UI-host/performance acceptance remains
subject to the separately documented gates.

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

## Kotlin 2.4.21 consumer and visible renderer

A synthetic consumer at `build/590-tvos-renderer-consumer` on the Mac uses Kotlin/Compose
compiler 2.4.21, Gradle 9.9.1, Compose 1.12.2 and stable Material3 1.9.0. Its common Kotlin owns
the entire fixture UI; the Apple factory only calls `ComposeUIViewController`. It has iOS
simulator plus both tvOS targets, packaged XML string resources, a slider, tooltip, button and
checkbox. This is a library/packaging probe, not a second implementation of Naviamp's TV UI.

Its first attempt included the patched Compose Gradle plugin's source build in Gradle 9.9.1.
That failed before compiling the fixture: that plugin producer uses Kotlin 2.2.0 and cannot read
Gradle 9.9's embedded Kotlin 2.4 metadata. No metadata-version check was suppressed. Building the
plugin separately with its pinned Gradle **9.5.0** succeeded in **58s**, 18 executed tasks. It was
published only to the owned `build/590-resources-port-maven` under
`app.naviamp.tvosprobe.compose:compose-gradle-plugin:1.12.2`, with no official plugin marker.
The resources umbrella/two tvOS artifacts also staged there in 1m10s, 68 tasks (44 executed).

The consumer then used that exact prebuilt JAR instead of `includeBuild` for the producer.
Both tvOS and original iOS simulator frameworks **compiled and linked** in **4m47s**, 19 tasks
(15 executed). The cold consumer cost includes first-time configuration/plugin dependencies and
two Native framework links; it is not an app Release archive benchmark. The warm real resource
copy task later passed in **8s**, 20 tasks (14 executed). Keep producer builds separate from
ordinary app development and key cached artifacts by source/compiler/SDK identities.

`scripts/probe-compose-tvos-consumer-graph.init.gradle` verified the actual resolved graphs in 9s:
**91** selected iOS components, with **no private port artifacts**, and **96** tvOS components,
including exact-version private UI/Foundation/Material3/resources Native leaves. It rejects
snapshot/community-binary leakage and missing required coordinates. iOS retained the published
`org.jetbrains.compose.material3:material3-uikitsimarm64:1.9.0` binary. JSON:
`build/590-tvos-renderer-consumer/build/verified-consumer-variants.json` on the Mac.

An owned UIKit scene host invoked the actual `syncComposeResourcesForIos` task with tvOS
platform/architecture inputs. The task copied the generated `.cvr` string bundle into the app's
`compose-resources` directory; no handwritten resource-copy fallback was used. On a newly created
tvOS 27.2 simulator the host installed, launched and remained alive. Its **3840×2160** screenshot
was inspected and visibly shows the resource-backed title/details, Material3 slider, button and
checked checkbox. No missing-resource exception occurred. The simulator was shut down/deleted
with both exit codes 0. The preexisting iOS Issue 43 simulator was left alone. Evidence:
`build/590-renderer-visible-host`, `/tmp/naviamp-590-tvos-renderer-consumer-staged-plugin-link.log`,
and the local inspected `build/590-feasibility/tvos-stable-renderer.png`. The harness wrote
`screenshotInspected=false` before inspection; the observation here records the subsequent review.

Do **not** infer a supported tvOS minimum from that host's test-only 17.0 plist. The actual link
warned that the source producer's Objective-C helpers defaulted to SDK 27.2 deployment, plus an
ICU data object tagged 18.5. Audit/set producer deployment targets and inspect vendor object
requirements before selecting a supported minimum or claiming older-runtime compatibility.
The successful smoke test proves rendering on **27.2 only**. It does not prove physical remote
input, motion/performance budgets, older runtimes, a signed device install or the full Naviamp host.
Android's minimum remains API 26 and is independent of these Apple deployment findings.

The actual published Material3 binary was also extracted with the **same** ABI tool/filter
settings as the port, confirming that the historical signature difference is not just a stale
golden. The published compiler is 2.1.21 / ABI 1.201.0; the source port uses 2.3.20 / ABI 2.3.0.
The 399 removed/3 added lowered declarations are retained in
`build/590-compose-stable-port-check/material3-published-binary-api-comparison.json`. Do not claim
drop-in Native binary compatibility. The Kotlin 2.4.21 consumer rebuild and visible slider prove
the rebuilt graph can compile/link/render; remaining full-app integration must use fresh,
identity-keyed Native caches rather than reuse objects from a different producer/compiler graph.
