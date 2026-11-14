# Issue 590: SDK 37 and Compose toolchain experiment

Issue: https://github.com/goosepod/naviamp/issues/590

This is an original-application compatibility experiment before creating the tvOS host. It does
not establish tvOS runtime support, audio output, App Store eligibility or release readiness.
All work remains under #590. No additional issues were created.

Status: the Compose plugin correction and broad compatibility checks pass, but Windows startup
capture/presentation acceptance does not. Keep the upgrade isolated and hold tvOS host work.
The ordinary app retains its original dependency versions. Pixel interaction is pending unlock.

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
did not change the main TV app profile. The Pixel reports API 37; it was not interactively accepted.

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
5. Finish unlocked Pixel acceptance and the supported Android KMP/AGP DSL migration. Keep all this
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

Complete unlocked Pixel runtime checks and repeat comparable iOS Release measurements before
production acceptance. The first fresh-output/warm pair above is complete.
Keep Release optimization, PMCS, the 6 GiB archive heap, arm64 and all
existing unsigned IPA/privacy/version/native validators. Do not use experimental Release binary
caches, narrower framework exports or reused prebuilt packages to make a timing appear better.
Retain failed setup runs separately. Do not claim a performance improvement from compilation or
dependency warming alone. A full matched idle/marquee/progress/combined visible-window matrix,
with GPU/compositor measurements and lifecycle checks, remains required before accepting any
animation performance claim. Do not create the tvOS host until these gates have a recorded decision.
