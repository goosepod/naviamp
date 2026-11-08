# Android acceptance — October 30, 2026

Tracking: #408; regression fixes #421 / #422 (PRs #425 / #424).

## Source and isolation

Tested combined source `3b1fa20cc` on a physical Pixel 10a (USB serial recorded in private local evidence), v2.10.0/build 60. Both fix PRs remain unmerged at test time. These results describe this combined candidate, not accepted main or a published store artifact.

The normal Google Play installation `app.naviamp.android`, v2.9.0/build 57, remains separate. Tests use only the disposable `.v2test`, `.v2test.test`, `app.naviamp.ui.test`, and optional `.benchmark` packages. An already-running emulator is not targeted. Synthetic loopback fixtures supply TLS credentials and silent valid WAV audio; no live provider account is used. Raw logs, certificates, snapshots and test APK hashes remain in ignored `build/android-421-422/`.

## Automated build and unit results

JBR 21 Gradle invocation:

```sh
./gradlew -Pnaviamp.animationProbe=true verifyCoreFirstArchitecture \
  :core:presentation:testDebugUnitTest :core:ui:testDebugUnitTest \
  :apps:android:testDebugUnitTest :apps:android:testReleaseUnitTest \
  :apps:android:assembleDebug :apps:android:assembleDebugAndroidTest \
  :core:ui:assembleDebugAndroidTest :apps:android:verifyDebugBassNativePackage
```

Build succeeds. Android unit results: presentation 527, UI 142, Android app debug 4 and release 4; **677 tests, zero failures/errors/skips**. Architecture and packaged native-library verification pass.

## Physical-device results

- Native instrumented suite: **23 active passes, 20 assumption skips**, zero failures. The runner reports 43 cases; skipped opt-in cases are not passes. Coverage includes packaged BASS decoding/seeking, shared file callbacks, mixer/equalizer, Android Keystore, Connect setup/credential persistence, real mutual TLS playback and native foreground-service startup.
- Shared rendered suite: **35 active passes, one failing test**, plus two rejected selector errors. The local class selector initially included two non-test `*Kt` helper classes and produced ClassNotFoundException; the selector was corrected. The valid tests include 34 default rendered cases (animation, raster, artist, search, smart-playlist UI) and the passing immediate-Escape dialog case. No skipped cases are counted as passes.
- `NaviampDialogInitialFocusTest.initialFocusAllowsTabActivationButRespectsEscapeDisabled` fails: Confirm is not focused after Escape/Tab. Isolated rerun: **one pass, one identical failure**. This test forces the shared owned-window dialog path and does not establish failure of Android's normal native dialog presentation. PR #425 is returned to draft with this evidence; no production fix is made during this testing request.
- Opt-in interrupted-playback recovery: **one pass**. Real native playback pauses/resumes, survives unavailable-provider rapid selections beyond the foreground-start deadline, recovers with moving progress when the provider returns, and survives background/restore.
- Separately installed, non-debuggable, R8-shrunk benchmark APK: **one pass**. Fresh fixture connection, browsing, real moving playback progress, pause, midpoint seek, explicit resume with moving progress, About v2.10.0/changelog rendering, and cold-launch connection retention all pass. `:apps:android:assembleBenchmark` without the animation probe flag succeeds.
- Restored paused-seek device follow-up: **passes through the actual visible UI**. Disable Start Playing on Launch in Experience (captured switch off), explicitly pause, force-stop only the test package, and relaunch. The restored player shows Play; seeking to 0.5 stays paused after a settling interval. Another force-stop/relaunch retains slider fraction 0.5 and Play. Explicit Play produces moving progress, then Pause and a seek to 0.25 retain Play. A further force-stop/relaunch retains 0.25 and Play. Snapshot text checks assert the corresponding slider fractions within 0.01 and absence of Pause. These are process-restart checks, not graceful Android shutdown tests.
- Rejected black-box setup attempts are retained locally: immediate-window readiness, expanded-player navigation, wrong settings category, an assumed checkable sibling for the custom switch, missing activity launch, and attempting an active native MediaSession assertion before restored paused playback had started. Corrected UI steps pass. These attempts do not establish product failures; native-session restoration before first playback remains unverified.

## Performance limits

The visible animation probe confirms compositor pixels change. Measured process CPU (one core = 100%): static 0.050%, marquee 5.669%, waveform 5.039%, combined 6.059%. Root and sibling draw counters and parent-window FrameMetrics report zero during these samples. Zero parent GPU/frame samples do not measure child-surface compositor cost. These measurements do not accept the real app against its animation budget; #414 remains open. This run is not the complete static/paused/hidden/restored/resize/input/accessibility performance matrix.

## Setup and remaining scope

TLS fixture startup initially waited for synthetic key generation; its readiness endpoint subsequently succeeded before native playback tests. SDK XML/deprecation compiler warnings do not fail the build. No platform production source is changed by this verification.

The normal Google Play APK hash matches its pre-test baseline; versionCode 57 and installer `com.android.vending` are unchanged. Only the four task-installed test packages are removed, and only the five task-owned adb reverse mappings are removed (none remain). Both disposable local fixtures are stopped. The TLS `/stop` request returns an empty response as the process shuts down; endpoint/listener follow-up confirms it is gone. Screen timeout remains 60000 ms and secure lock timeout remains unset, matching baseline. No emulator or normal-app data is cleared. APK preservation does not establish a before/after hash comparison of the normal app's private data.

**Disposition:** not full Android release acceptance. The initial shared keyboard-test failure is resolved by the follow-up below; animation budget #414 and unexercised live/provider, hardware, and extended lifecycle coverage remain open. No store submission, tag, merge, or release is performed.


## Dialog keyboard regression follow-up

PR #425 commit `a9690d7dc4a18a971f756924539c83cc861bc3db` corrects the test setup and adds the dialog cases to the explicit standard Android CI class list. Tests execute against the earlier combined candidate plus that two-file change (local combined commit `d7e54e84a`). No product source changes in this follow-up.

Compose's synthetic key injection bypasses Android's native transition from touch to keyboard input. Button focusability follows that mode, so the original Tab assertion was exercising keyboard navigation while the host remained in touch mode. Explicitly requesting `InputMode.Keyboard` through the shared input-mode contract makes the same focus/activation assertions pass; there is no forced button focus or weakened expectation. The test additionally verifies forward Tab and Shift+Tab before Enter and retains immediate Escape and disabled-dismissal coverage.

- Physical Pixel 10a/API 37: isolated final dialog run **2 passes**; selected standard non-animation rendered suite plus dialog cases **35 passes**, zero failures/skips. The animation probe is not repeated for this test-only correction; its earlier measurements and limits above remain applicable.
- macOS/JVM: dialog (2), owned-window activation/focus (1), and popup surface (7): **10 passes**, zero failures/errors/skips.
- `verifyCoreFirstArchitecture` and `:core:ui:assembleDebugAndroidTest` with `-Pnaviamp.animationProbe=true` pass after the runner class-list update. The final source diff changes only the shared rendered test and `core/ui/build.gradle.kts`.
- The task-installed `app.naviamp.ui.test` package is removed. The only Naviamp package remaining is the normal Play Store installation; its profile is not cleared or reconfigured. No new reverse mapping or fixture is used for this follow-up.

PR #425 is ready for review again. Its full hosted matrix is running for the new revision; the preceding hosted revision had a separate iOS native mutual-TLS playback failure. Local dialog results do not waive the hosted gate or establish complete release acceptance. This follow-up does not rerun the ordinary native Mac app because its production source and binaries are unchanged; the new keyboard assertions do run on the macOS/JVM renderer.
