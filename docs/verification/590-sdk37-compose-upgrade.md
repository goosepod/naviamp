# Issue 590: SDK 37 and Compose toolchain experiment

Issue: https://github.com/goosepod/naviamp/issues/590

This is an original-application compatibility experiment before creating the tvOS host. It does
not establish tvOS runtime support, audio output, App Store eligibility or release readiness.
All work remains under #590. No additional issues were created.

Current status: the later newest-stable experiment passed Windows startup and original-app
runtime checks. The supported AGP migration and additional stable-library batch are recorded in
[the current upgrade checkpoint](590-supported-upgrade-checkpoint.md). Ordinary app dependencies
remain unchanged until final acceptance. Earlier experiment sections below retain their historical
results; the initial Windows failure and temporary AGP bridge are not the current candidate state.

## Frozen inputs and reproduction

The original application baseline is `57c26661247facd5d3ddf43319b7f119d6683c22` (merged #605).
The working #590 branch adds the opt-in domain target and feasibility documentation independently.
The app-source implementation is unchanged by this experiment.

| Input | Original | Candidate |
| --- | --- | --- |
| Kotlin | 2.4.10 | 2.4.21 |
| Compose Multiplatform | 1.11.0 | 1.12.0 |
| Material3 | 1.9.0 | 1.12.0-alpha03 |
| Android Gradle plugin | 8.13.2 | 9.1.1 |
| Gradle | 8.14.5 | 9.3.1 |
| Android compile SDK | 36 | 37 |
| Android target/minimum SDK | 36 / 26 | 36 / 26 |
| Native GC | PMCS | PMCS |
| JDK / Android NDK | 21 / 27.0.12077973 | unchanged |

Material3 remains a prerelease. Keep the candidate isolated until all acceptance gates below pass;
it is not an accepted production dependency upgrade merely because compilation succeeds.
The application target SDK stays 36, preserving the current runtime compatibility opt-ins.
AGP's documented API 37 support and required Gradle version are described in the
[AGP 9.1.1 release notes](https://developer.android.com/build/releases/agp-9-1-0-release-notes).

[590-sdk37-upgrade.patch](590-sdk37-upgrade.patch) records the complete original-app build-metadata experiment. Apply it
to a disposable checkout of the baseline above, not to an arbitrary newer branch:

```text
git worktree add --detach build/590-upgrade-probe 57c26661247facd5d3ddf43319b7f119d6683c22
git -C build/590-upgrade-probe apply --unidiff-zero ../../docs/verification/590-sdk37-upgrade.patch
```

Install platform `android-37.0`; preserve JDK 21 and NDK 27.0.12077973. The Windows experiment
uses the configured SDK at `%LOCALAPPDATA%/Android/Sdk`. Set `ANDROID_HOME` explicitly in the new
checkout and `CMAKE_EXE` to that SDK's installed CMake 3.22.1 executable. The project wrapper
downloads Gradle 9.3.1. Keep the app's existing BASS/native input provisioning.

## Required compatibility repairs

Compose 1.12's Android artifacts reject compile SDK 36 / AGP 8.13 at AAR metadata validation.
Do not suppress that check or substitute older Android Compose artifacts beneath the new UI.

The controlled AGP experiment uses `android.builtInKotlin=false` and `android.newDsl=false`.
These deprecated AGP 9 migration switches preserve existing KMP variants, Android tests and
source-set behavior. They are removed in AGP 10. A supported Android KMP plugin/DSL migration
is still required before treating this as the permanent toolchain. See Google's
[built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin) and
the [KMP AGP 9 migration](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html).

Other repairs are confined to build metadata:

- Explicitly enable Android `resValues`, retaining generated application labels.
- Pin NDK 27 instead of silently changing to AGP 9's new default native compiler.
- Move the UI instrumentation fixture's target SDK 34 into `testOptions.targetSdk`.
- Replace the removed `Project.exec` calls with injected `ExecOperations` for Windows installers
  and macOS bundle signing/privacy configuration; preserve commands and exit-code checks.

No Android, Desktop or iOS production Kotlin/Swift source files change. Their native adapters,
shared product behavior, translations, settings and migrations are unchanged.

## Compose tvOS plugin correction

The locally retained Apache-2.0 plugin lives in `build-logic/compose-tvos`, pinned to upstream
`f1b260751bac8b073519e65675fe2797dab71b8a` (1.4.2). Read its README and retained LICENSE.
It is not installed into ordinary app builds and is not published under upstream coordinates.

The upstream diagnostics accumulated component-metadata discoveries from every platform and
losing dependency-version candidate. A combined iOS/tvOS/Android/JVM invocation could therefore
fail strict mode even though the resolved tvOS graph was usable.
The correction records selected module coordinates from configurations with the tvOS native
target attribute, then scopes empty-variant discoveries to those coordinates. A genuinely selected
missing variant still fails, including when another graph selected a working older version.
Unmapped fork-edge and normal dependency-resolution failures remain fatal. State resets between
builds. Configuration-cache reuse remains unaccepted; use `--no-configuration-cache` for probes.

The Mac experiment includes the local plugin through `pluginManagement.includeBuild`, keeps
`strictMode=true`, disables remote manifest fetching (`manifestUrl=""`) and pins
`composeGradlePluginVersion="1.12.0"`. The manifest used in the inventory is separately hash pinned
in the main feasibility plan. Do not enable the remote mutable manifest for reproducible builds.

Evidence:

- 25 plugin unit cases passed, including retained upstream diagnostics/bookkeeping tests and
  three new selected-graph regressions.
- Gradle's `validatePlugins` passed with an empty validation report.
- A single strict invocation compiled shared UI for tvOS simulator, iOS simulator, JVM and
  Android, plus the existing iOS device host. It passed in 4m31s: 20 injected tvOS modules,
  zero skipped injections, no false strict errors.
- A separate resolution assertion observed 193 selected tvOS module coordinates. This rules out
  a vacuous pass caused by an inactive selection collector.
- Resolved original iOS simulator (126 modules), JVM (77) and Android debug (101) UI graphs
  contained official Compose UI modules and no `dev.sajidali` modules with the port plugin active.
- The temporary tvOS UI source audit points at existing Apple actuals only in the disposable Mac
  checkout. It is not a production source layout or a new tvOS host. Move portable Apple actuals
  into a neutral `appleMain` owner when that later step is accepted.

Run the plugin tests with the candidate wrapper:

```text
gradlew -p build-logic/compose-tvos test --no-configuration-cache
```

## Original-application evidence (November 14, 2026)

Windows initial command, from the upgraded checkout:

```text
gradlew :core:ui:jvmTest :apps:android:assembleDebug :apps:android:testDebugUnitTest :apps:desktop:stageLocalTestApp verifyCoreFirstArchitecture --max-workers=2 -Pkotlin.native.enableKlibsCrossCompilation=false -Pnaviamp.bass.platform=windows-x64
```

Passed in 17m30s (305 executed tasks), including all 761 shared UI cases with no failures,
errors or skips. Packaging uses the upgraded checkout's binaries, not the older root staged app.

The subsequent `desktopTest`, provider/storage/app JVM suites, Android release assembly and
`lintRelease` invocation passed in 19m04s (406 executed, 39 up-to-date tasks). Its reports contain
1,601 cases, zero failures/errors, eight conditional skips. Those skips include Linux MPRIS,
unconfigured external live-service checks and real-output cases excluded by the initial CI-style
`-Pnaviamp.bass.test.outputDevice=0` setting. Real-output checks are run separately; do not count
skipped native-clock cases as playback acceptance.

A second invocation added domain/presentation JVM suites and reran the BASS classes against a real
Windows output device (without `naviamp.bass.test.outputDevice=0`). It passed in 5m57s. All ten JNI
binding cases, preview/main-channel lifetime, shared discovery preview and persisted Radio replay
passed. Across both invocations, deduplicated by suite, there are **3,375 cases, zero failures or
errors, and four conditional skips**: two Linux MPRIS cases and two unconfigured external service
acceptance cases. Synthetic companion behavior is separately exercised through the visible app.
The upgraded compiler did not cause the real-output cases to be silently skipped.

Android release inspection:

- R8 mapping header reports **9.1.57**, confirming the project optimizer override is still selected.
- The shrunk unsigned release APK packaged successfully (29,143,082 bytes in this run).
- Each of arm64-v8a, armeabi-v7a, x86 and x86_64 contains 18 native libraries.
- Lint 9.5.1 reports 16 warnings and zero errors; no suppression was added to pass the upgrade.

Mac: Xcode 27.2, SDK 27.2, JDK 21.0.13.0.3. All seven shared/provider iOS simulator suites passed:
2,590 cases, zero failures/errors/skips. A freshly provisioned simulator then executed all 18
binary-inventoried native BASS/TLS/mTLS cases successfully, with zero skips; its fixture and
simulator were cleaned up by the existing replay runner. The production Debug app built,
installed and launched on a separately owned iPhone simulator. After settling, its real connection
screen rendered. The Swift Keychain boundary test passed (one test, zero failures/skips).
The Xcode log contains an ICU object's deployment-version warning and a copy-tool diagnostic
claiming failure with exit code zero; neither failed that build. Retain these diagnostics and
recheck deployment compatibility before release rather than raising the app minimum to hide them.
Both ICU data archive members have no undefined symbols (`xcrun nm -u`), so that particular warning
does not itself demonstrate a dependency on an iOS 18.5 system API. This is an inference from symbol
inspection, not a substitute for running the app on the oldest supported iOS version.

Manual acceptance uses synthetic loopback-only primary and independently authenticated companion
fixtures, with muted audio:

- Windows: main library/home artwork; independent companion login/custom header and recommendations;
  nonempty queue; BASS playback time progression; pause and seek; original launcher restart retaining
  the connection, queue and paused position; resize and restored rendering.
- Android TV: library and companion recommendation rendering; playback progression from 0:02 to
  0:40; pause; D-pad focus and activation of Next into the second track; Home/background and return
  retaining the paused second track. Its original profile was restored and all nine backed-up files
  verified byte identical. This is runtime smoke coverage, not a new phone-to-TV pairing claim.
- Pixel: upgraded debug APK installation succeeded. Interactive acceptance is pending unlock;
  the lock screen is not a passing app screenshot. All 13 freshly backed-up profile files were
  restored and verified byte identical rather than leaving the fixture profile on a locked phone.
- iOS: simulator UI startup plus the native suites above. No physical iPhone audio/output claim.

Windows render-observer samples use the actual packaged app in a disposable staged copy,
934x601 parent surface, scale 1.0, AMD OpenGL. The original launcher was tested separately.
Settled paused CPU was 1.87% of one core; progress-only CPU was 2.34%; parent Compose frames were
zero during both ten-second intervals, while playback time and the progress bar visibly moved.
Earlier cold samples were 8.75% paused and 3.28% playing and are retained. A separate GPU sample
reported app engine-sum mean 0.046 and DWM 20.95, with DWM CPU 36.94%. Builds and other desktop
content were active, so these are preliminary observations, not a matched animation-budget or
compositor performance acceptance. No animation implementation or cadence was changed.

Raw local evidence is under `build/590-feasibility`, `build/590-runtime`, and the Mac's
`build/590-stack-archive-pair`. Generated profiles and logs are not committed.

## Windows startup interoperability investigation

Fresh copies of the same synthetic saved-queue profile produced Computer Use window captures with
a blank parent and only the native progress region after upgrading. Resize or one Tab input
revealed the loaded UI. These use per-window Windows Graphics Capture, so independently corroborate
the physical display before attributing the failure exclusively to application presentation rather
than capture interoperability. The observed capture/first-presentation gate is unresolved either
way and is not a passing real-window result. The baseline app
rendered, and forcing the upgraded app's Skia raster adapter rendered, isolating the native Windows
attachment path. Earlier successful resized/restarted sessions did not close this startup gate.

Trials that gated native attachment on a recorded parent frame, forced a synchronous initial
presentation, or waited for DWM did not reliably fix repeated startup. They were removed; do not
treat their passing unit tests or individual startup trials as acceptance. A discarded prototype
also produced a valid iOS archive, which is retained as exploratory evidence rather than evidence
for the final app source.

The existing shared `NaviampRasterRegion.whenReady` contract already invalidates drawing and
resubmits unchanged pixels after attachment. A new shared rendered regression verifies this causes
a refresh and then returns to quiescence without a repaint loop. It passed with the original
Compose stack before native wiring. A Windows adapter trial published committed DirectComposition
attachment through that callback after the AWT recording pass, with native handle-lifetime guards.
It also failed to establish reliable startup and was removed. The shared regression remains useful
coverage of the contract; it is not proof that the Windows native boundary is fixed.

The retained regression passed on both the original and upgraded JVM stacks (one case, zero
failures/errors/skips each) and on physical Android TV API 34 using the upgraded standalone
instrumentation APK: one test, zero failures, 3.436 seconds of test execution.
The temporary `app.naviamp.ui.test` fixture was absent before the run, was removed afterward, and
did not change the main TV app profile. The Pixel API 37 runtime follow-up is recorded below.

**Decision: do not adopt the version upgrade or start the tvOS host yet.** Windows first-presentation
acceptance is a real blocker. All speculative rendering changes were removed from production code,
and the original dependency stack was repackaged successfully. Only the shared contract's explanatory
comment and rendered test remain from this investigation. No platform production files change in
the final handoff, and no animation cadence, renderer default or fallback policy is changed.

Next work under #590:

1. Preserve this reproduction: original unmodified launcher, fresh copy of the saved-queue synthetic
   profile, native Windows raster adapter enabled, first show/activation before resize or input.
   Compare the baseline and upgraded Skiko versions under identical geometry/display conditions.
2. Instrument actual native attachment, AWT visibility/focus, picture recording, GPU presentation and
   compositor completion as separate events. The Skiko `onRender` callback records a picture, so it
   is insufficient evidence of presentation. Capture a small Windows native interoperability repro
   and correlate it with application render/frame and compositor telemetry.
3. Locate the exact missing or reordered native presentation event before changing the adapter.
   Keep the complete behavior in the shared surface/raster owner; adapters publish native facts and
   execute the concrete AWT/Skiko/DirectComposition effects. Do not add repaint loops, arbitrary
   delays, disabled animation or a costly permanent fallback to hide the regression.
4. Require repeated untouched cold/warm startup, minimize/restore, resize, clipping, keyboard/input,
   accessibility, and the matched static/marquee/progress/combined CPU/frame/compositor matrix.
   A resized window, a frame request, or a passing policy unit test is not startup acceptance.
5. Finish the supported Android KMP/AGP DSL migration. Keep all this
   work on #590; then make an explicit original-app acceptance decision before the tvOS host.

The follow-up archive setup initially switched a checkout from normal `~/.konan` test compilation
to an isolated archive native home and failed on duplicate platform-library unique names. A fresh
checkout with one consistent native home passed. Do not relax duplicate-KLIB checks or reuse
project outputs across native homes; this reinforces the consistent-home rule in the main plan.

## Remaining acceptance gate

Both fresh-output and warm Release archive pairs validated successfully. The compact source,
toolchain, task-outcome and workload evidence is in [590-toolchain-timings.json](590-toolchain-timings.json).

| Measurement | Baseline | Candidate | Interpretation |
| --- | ---: | ---: | --- |
| Fresh-output Release archive | 721.763 s | 783.813 s | Candidate 8.60% slower |
| Executed optimized framework link | 621.873 s | 667.454 s | Candidate 7.33% slower |
| Warm no-change Release archive | 25.806 s | 24.148 s | Framework links up to date in both |
| Unsigned IPA bytes | 20,635,110 | 20,914,550 | Candidate about 1.35% larger |
| Static framework archive bytes | 221,108,552 | 232,063,288 | Not installed app size |

Cold product native compile/link tasks executed from new outputs, with task-output caching disabled;
dependency/compiler downloads were seeded. Warm runs retained those product outputs, explicitly
verified their compile/link tasks were `UP-TO-DATE`, and rebuilt/validated the Xcode archive and IPA.
Do not describe a warm up-to-date framework as a faster fresh optimized link. The generated framework
header remained 876,770 bytes / 9,929 lines / 339 interfaces / 79 protocols for both toolchains;
framework exports were not narrowed to reduce optimization work.

Source is the frozen baseline above and candidate `385cd5aa7b38816e37bd9b1fbd7535d880d7bd62` in
the Mac experiment checkout. Both source trees were clean and stable. Same Apple M1 / 16 GiB / eight
logical CPUs, Java 21, Xcode/SDK, arm64, Release optimization, PMCS and 6 GiB heap. The version stack
changes together; this is not a Kotlin-only attribution. One local pair, other developer apps and
Spotlight activity, no peak-RSS capture, and no hosted run limit the inference. Repeat comparable
samples before accepting a production upgrade or performance claim. The candidate is below the
plan's provisional +20% cold ceiling (866.116 s), with a measured cold cost rather than a speedup.

Repeat comparable iOS Release measurements before
production acceptance. The first fresh-output/warm pair above is complete.
Keep Release optimization, PMCS, the 6 GiB archive heap, arm64 and all
existing unsigned IPA/privacy/version/native validators. Do not use experimental Release binary
caches, narrower framework exports or reused prebuilt packages to make a timing appear better.
Retain failed setup runs separately. Do not claim a performance improvement from compilation or
dependency warming alone. A full matched idle/marquee/progress/combined visible-window matrix,
with GPU/compositor measurements and lifecycle checks, remains required before accepting any
animation performance claim. Do not create the tvOS host until these gates have a recorded decision.

## Unlocked Pixel follow-up and Windows isolation

After the owner unlocked the Pixel 6a (API 37), the same upgraded debug APK passed touch navigation,
main-library artist/album browsing, Aurral recommendations with independent companion credentials
and a required custom header, album playback, pause, seek, Next, background/return and paused queue
restoration after force-stop/restart. Playback visibly advanced from 0:01 to 0:20, pause stopped at
0:31, seeking reached 6:35, and Next selected the second fixture track. Its crash log was empty.
Only synthetic loopback services and the `.v2test` package were used. Initial discovery failure
was traced to stopped fixture processes; restarting the fixtures and refreshing loaded the artist.
This setup failure is not an app regression or a passing network-failure behavior test.

At the existing 1.3 font scale, the full player's `10:00` total-duration label wraps. Reinstalling
the original SDK 36 debug APK against the same synthetic profile reproduced the identical wrapping;
it is baseline behavior, not an upgrade regression. The upgraded APK was then reinstalled. All 13
original profile files were restored byte-identically from a fresh backup, both owned ADB reverses
were removed, the phone was returned Home, and both loopback fixtures were stopped.
Local screenshots/XML, fixture logs and restoration evidence are under `build/590-runtime` with
the `pixel-followup-` prefix. No phone system settings or production profile were changed.

A disposable native probe now isolates a plain Skiko OpenGL parent from a DirectComposition raster
region using the real packaged JNI library. The candidate painted a blue parent and magenta native
region when attachment occurred during the first recording pass. A second run first settled the
blue parent, then attached the region with an explicit test key. The region appeared without any
additional parent recording callback: native attachment alone did not reproduce the blank surface.
These are bounded window captures, not independent physical-display corroboration.

Two untouched full-candidate launches through a disposable profile launcher also rendered correctly
in this session. This does not supersede the earlier intermittent failures or close acceptance.
The baseline/candidate SkiaLayer, OpenGL redrawer and OpenGL context Kotlin sources were identical
across Skiko 0.144.6 and 0.150.1; this rules out a simple change in those Kotlin bodies only.

Crossing Compose 1.12 with Skiko 0.144.6 fails on missing `Paragraph.getUnresolvedCodepoints()`.
Crossing Compose 1.11 with Skiko 0.150.1 fails on removed `GradientStyle`. Neither mixed stack is
a supported workaround or accepted candidate. Production dependencies remain unchanged.

Skiko 0.152 contains an upstream
[first-show presentation change](https://github.com/JetBrains/skiko/pull/1237), and
[0.153.0](https://github.com/JetBrains/skiko/releases/tag/v0.153.0) retains it. Testing that newer
runtime is a separate isolated experiment; do not attribute the earlier failures to that upstream
bug or accept a newer Skiko merely from the release notes.

The Skiko 0.153-only experiment was rejected: all 762 shared UI cases ran, with 405 failures
caused by missing `RenderNodeContext(boolean)` at rendered-scene creation. Compilation alone did
not detect this binary incompatibility. The failed test XML was preserved separately. No runtime
acceptance is claimed for this stack, and no product adapter was changed to hide the failure.
The owning Gradle invocation was canceled after reporting the failures while awaiting build
cleanup; this is not a successful build. A later thread sample isolated slow Kotlin statistics
cleanup, rather than establishing a rendering-worker hang.

Published Maven metadata also rules out upgrading the shared UI arbitrarily: Compose 1.12.2 uses
Skiko 0.150.2, while Compose 1.13.0-beta02's `ui-skiko-desktop` uses 0.156.1. The tvOS UI fork
currently publishes 1.12.0 and 1.12.0-beta01 only. Do not substitute that older tvOS UI beneath
newer common APIs or disable strict missing-coordinate diagnostics. A Skiko 0.150.2 patch-runtime
experiment was tested separately against the existing Compose 1.12 candidate. Its upstream
release updates Skia and does not claim to include the 0.152 first-show change.

The 0.150.2 patch-runtime run passed all **762 UI cases, zero failures/errors/skips**, and
`:apps:desktop:createDistributable` succeeded. Five separate launches from fresh copies of the
same saved-queue fixture profile rendered without keyboard input or resizing. The owner independently
confirmed normal artwork, player controls and Home content on the physical display during the first
run. Minimize/restore, maximize/restore and subsequent keyboard input also rendered correctly.
The window remained 936x633 for startup; maximize was only tested afterward. The source app and
native adapter were unchanged; a disposable launcher set the synthetic profile environment.
Two 0.150.1 runs through that launcher also passed earlier, so these observations do not establish
that 0.150.2 alone fixed the earlier intermittent failure. No new animation/performance acceptance
is claimed. Its earlier Material3 alpha still prevents treating this diagnostic stack as the
newest-stable application candidate.

### Newest-stable candidate and build cleanup

The owner's current policy is newest stable by default, with RC/beta exceptions permitted by
engineering judgment when they concretely advance #590 and pass full validation. A fresh detached
worktree at `f52687f67`, `build/590-stable-probe`, now tests Kotlin 2.4.21, Compose 1.12.2,
Material3 1.9.0, AGP 9.5.1 and checksum-pinned Gradle 9.9.1. The Gradle distribution SHA-256 is
`d8a18a7a841dd5df825d35a685fad6f2d7f12ac0c280e57b67c27b4a520ba228`.
Original native inputs, JDK 21, NDK 27, PMCS, min SDK 26 and target SDK 36 are preserved.
The obsolete R8 9.1.57 buildscript override was removed there so AGP can use its bundled 9.5.29;
release shrinker validation inspects the resulting artifact below.
This candidate remains isolated and is not adopted by the normal branch; the completed functional
checks and remaining acceptance work are recorded below.

Windows thread samples found Kotlin's `BuildFinishBuildService.findV2FusFilesByBuildId` repeatedly
reading last-modified times while sorting the existing 83,470 `~/.gradle/kotlin-profile` files.
This consumed minutes after tests had finished. See
[the Kotlin service source](https://github.com/JetBrains/kotlin/blob/v2.4.21/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/statistics/BuildFinishBuildService.kt).
The experiment isolates reports using the internal, explicitly test-only
`kotlin.session.logger.root.path` property in the disposable worktree; it does not change compiler
optimization, task execution, tests, caching or PMCS. Do not put this internal testing switch into
production defaults or compare timings across different reporting-directory workloads silently.
The existing user-home reports were not deleted or moved.

The first latest-stable `help` invocation failed because PowerShell split an unquoted dotted `-P`
argument into a task path. Its long cleanup exhibited the same statistics overhead. That setup
failure is not a dependency compatibility result. The corrected retry places the test-only reporting
path in the disposable `gradle.properties` and removes the obsolete R8 override before further work.
It passed `help` in 46 seconds. Dotted command-line property arguments are quoted for subsequent
PowerShell invocations.

Both initial SDK-37 debug and release APKs were re-inspected with `aapt2`: minimum API 26 and target
API 36 are unchanged. Compilation uses API 37. This preserves the declared Android 8.0 install
floor; the actual runtime checks cover API 34 and 37, not an API-26 device. Recheck the final merged
manifest, lint/API calls and an oldest-supported runtime when accepting the newer candidate.

### Newest-stable functional results

The [newest-stable reproduction patch](590-latest-stable-upgrade.patch) applies to `f52687f67`
and the current issue branch. It contains build metadata only and deliberately excludes the
machine-specific internal statistics-report isolation property. `git apply --check` passed against
the ordinary branch. The default catalog, wrapper and platform production code remain unchanged.

The Windows build invocation passed with the following tasks:

```powershell
./gradlew.bat :core:ui:jvmTest :apps:android:assembleDebug `
  :apps:android:testDebugUnitTest :apps:android:assembleRelease :apps:android:lintRelease `
  :apps:desktop:createDistributable verifyCoreFirstArchitecture --max-workers=2 `
  '-Pkotlin.native.enableKlibsCrossCompilation=false' `
  '-Pnaviamp.bass.platform=windows-x64' --no-configuration-cache
```

It took **25m12s, 657 tasks executed**. A subsequent shared/provider/native JVM invocation took
**8m26s, 45 tasks executed and 34 up-to-date**, and the remaining Desktop host suite took
**23s, 24 executed and 42 up-to-date**. These are functional build observations, not a matched
performance comparison. The SDK, JDK and existing CMake executable must be configured in the
invoking process. Retained failed host-suite launches document sandbox cache denial, an unset
Android SDK and an unset CMake executable; the configured retry passed without source changes.

XML results across these invocations:

| Suite | Reported cases | Failures/errors | Skips |
| --- | ---: | ---: | ---: |
| Core domain | 1,148 | 0 | 0 |
| Core presentation | 626 | 0 | 0 |
| Core app | 298 | 0 | 0 |
| Core storage | 75 | 0 | 0 |
| Core UI | 762 | 0 | 0 |
| Desktop native/platform | 72 | 0 | 3 |
| Navidrome/Aurral provider | 344 | 0 | 1 |
| Jellyfin provider | 36 | 0 | 0 |
| Android host | 7 | 0 | 0 |
| Desktop host | 8 | 0 | 0 |
| **Total** | **3,376** | **0** | **4** |

All ten `DesktopBassJniBindingIntegrationTest` cases executed, including decode/seek, mixer/FFT,
equalizer, downmix, end callbacks and audio-output reinitialization. The four skips are the two
Linux MPRIS/session-bus tests and two opt-in real-service Aurral tests. They are not native-audio
skips or evidence that those external services passed. The separate credential-validating fixture
runtime checks below exercise the main/companion separation without real service credentials.

The newest-stable release APK is **29,055,557 bytes**, with all four existing ABIs and **18 native
libraries per ABI**. Its real R8 mapping header identifies **9.5.29**, minimum API 26. Release lint
reports **zero errors, 16 warnings**. `aapt2` inspection of both debug and release APKs confirms
compile API 37, target API 36 and minimum API 26. The debug APK is 49,919,957 bytes. These are
unsigned/local artifacts, not release publication or store acceptance.

On the unlocked Pixel 6a (API 37), the newest-stable debug APK passed main artist/album browsing,
independent Aurral recommendations, playback progression, pause, seek, Next, background/return and
paused queue restoration after force-stop/restart. The existing 1.3-font-scale duration wrapping
remains covered by the baseline comparison above. All **13 original profile files** were restored
byte-identically, owned reverse ports removed, and the device returned Home.

On the Android TV (API 34), the same APK passed D-pad library/album navigation, actual rendered
artwork and advancing playback, pause, Next, background/return and restart. The companion fixture
required its separate credentials and custom header, and its recommendations loaded successfully.
An initial fixture startup entered offline state; saving the synthetic connection recovered it.
The original SDK-36 APK did not reproduce that startup observation in the comparison run, and
neither did the subsequent settled newest-stable startup. This does not establish an upgrade
regression or prove a fixture-only cause. Preserve the failed evidence and reproduce under
controlled conditions if it recurs. Fixture setup now explicitly force-stops the test package
after installation before replacing its database. All **nine original profile files** were
restored byte-identically, owned reverse ports removed, and the TV returned Home. Only the
`.v2test` package was exercised; the production app and device settings were not changed.

The newest-stable Windows packaged copy rendered its first untouched startup at 936x633 with
artwork, controls and Home content. The main artist and album libraries loaded. The separate
Aurral account and required custom header authenticated and loaded one recommendation. Playback
advanced from 4:39 to 4:52, pause settled at 5:03, seeking while paused reached 7:45, and Next
selected track two and began playback. After pausing at 0:18, minimize/restore and app restart
retained that paused position and track. The restarted window also painted without input/resize.
Only a copied package and disposable synthetic profile were used. Both owned windows were closed,
and both loopback fixture servers were stopped after testing. This is functional rendering
evidence; the owner's independent physical-display confirmation above applies to the earlier
Skiko-patch diagnostic stack, not this newer complete stack. No animation budget or causal
first-show-fix claim follows from these observations.

On the configured ARM64 Mac, all **2,590 shared/provider iOS simulator cases passed**, with zero
failures/errors/skips, and `:apps:ios:compileKotlinIosArm64` passed in the same invocation
(6m04s, 147 tasks executed). The prebuilt native simulator binary then passed **all 18 inventoried
tests**, with zero failures/errors/skips, including real mutual TLS, shared file callbacks,
decode/seek, codec registration and mixer/downmix. The replay used a fresh owned simulator and
recorded `cleanup-complete`. Two prior replay setup failures are retained separately: the first
omitted `:core:testkit:prepareMobileMutualTlsFixture`; the second linked the binary without
`:platforms:ios:stageIosSimulatorTestBassFrameworks`, so dyld could not load BASS before test
inventory. Running both existing prerequisite tasks resolved these setup failures without changing
the binary or native adapter. The successful binary SHA-256 is
`daf01f075a3c5ef95eccd7919d31a931b59b400e289e57d216dc59863bca2448`.

Local Windows/Android evidence is under `build/590-feasibility/latest-stable-*` and
`build/590-runtime/*-stable-*`; the Mac checkout is `.worktrees/590-latest-stable`, with successful
native replay evidence in `build/590-stable-native-replay-staged`. Device restoration JSON files
confirm file equality. Logs are local verification data, not new production dependencies.

### Android minimum-version decision and remaining gates

Keep the Android 8.0/API-26 floor for this experiment. Android 8.0 was released on
[August 21, 2017](https://android-developers.googleblog.com/2017/08/introducing-android-8-oreo.html).
Raising the floor can remove compatibility paths and reduce the oldest-runtime test burden, but
the tested stable dependencies do not require doing so. Android 10/API 29 is a concrete native
rendering boundary because [SurfaceControl became public at API 29](https://developer.android.com/reference/android/view/SurfaceControl).
`AndroidRasterPresenter` already selects that path on supported devices; increasing `minSdk`
alone does not accelerate those devices. An API-29 floor would exclude Android-9-based Fire OS 7
devices, including the 2021 Fire TV Stick 4K Max in
[Amazon's device list](https://www.developer.amazon.com/docs/device-specs/identify-fire-tv-devices.html).
No minimum-version increase is authorized or made here. An actual API-26 runtime check remains
necessary before claiming full oldest-supported-device acceptance.

Before permanent adoption and tvOS host work:

1. Replace the temporary AGP legacy compatibility bridge with the supported KMP Android library
   plugin/DSL; preserve source-set behavior, resources, tests, native packaging and architecture
   checks. The passing bridged experiment is not proof that the deprecated graph is a maintainable
   upgrade. Retest every original target after that migration.
2. Repeat the newest-stable iOS host launch/Keychain and validated Release archive checks. The
   earlier alpha experiment's launch, Keychain and archive timings are not evidence for this stack.
   Measure matched fresh-output and warm archives with consistent native homes, optimization,
   PMCS, exports and native inputs; do not narrow the product to improve timing.
3. Complete visible idle/marquee/progress/combined CPU, parent-frame and GPU/compositor comparisons
   and relevant lifecycle checks. No renderer, animation policy, cadence or performance claim was
   changed by the functional tests above.
4. Resolve the mismatch between the stable official UI/Material3 coordinates and the published
   tvOS fork. Retain strict mapping diagnostics and prove the original-platform graph remains
   official when tvOS is enabled. Do not silently downgrade common APIs or accept an alpha-only
   dependency to bypass the gap.
