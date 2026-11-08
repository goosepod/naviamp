# Current-source Windows acceptance — October 30, 2026

Tracking: [#386](https://github.com/goosepod/naviamp/issues/386),
[#408](https://github.com/goosepod/naviamp/issues/408), release acceptance #280.

**Disposition: automated verification and the restored-output native preview regression pass;
complete Windows functional/performance acceptance remains open.** See the continuation below for
the owner's cleared-picker/working-output follow-up. No production implementation, animation cadence,
test assertion or deadline is changed.
This report does not certify Windows performance or authorize release publication.

## Exact source and environment

- Main `91d49ef56c740054aa781bde958228930fd40463`, tree
  `ecc056a72d408bf9dd36d36a56e7a4a13baa750d`, v2.10.0/build 60. This is also the source tree of
  release commit `ccef381040dab003525e3b89de654af78c9ac32a` accepted through PR #426.
- Worktree `.worktrees/windows-final-2100`, branch `test/386-windows-final-acceptance`.
- Windows 11 Home, build 26300; AMD Radeon Graphics driver `31.0.21923.11000`,
  2560×1440 at 60 Hz, unchanged `My Custom Plan 1` power scheme.
- Installed Temurin JDK **21.0.13+9**, MSVC 19.44.35229, Windows SDK 10.0.26100.0, CMake 3.22.1,
  Python 3.14.7. The historical JDK 21.0.12 directory no longer contains Java. The first invocation
  fails before Gradle starts; its log is retained. The successful build uses the installed JDK.
  These are not matched-runtime comparisons with the older Windows measurements.

The normal installed app/profile is excluded. A copied fixture profile is accepted only after a
read-only SQLite check establishes exactly one source with base URL `http://127.0.0.1:18080`.
Sync is disabled and playback volume is zero in that disposable profile. No real provider account,
cloud folder or store submission is used.

## Automated build and regression results

The complete run succeeds in **14m24s**, all 149 tasks executed:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot'
$env:CMAKE_EXE = 'C:/Users/ursasmar/AppData/Local/Android/Sdk/cmake/3.22.1/bin/cmake.exe'
./gradlew.bat -Pkotlin.native.enableKlibsCrossCompilation=false `
  -Pnaviamp.bass.platform=windows-x64 -Pnaviamp.bass.test.outputDevice=0 `
  verifyCoreFirstArchitecture jvmTest desktopTest `
  :core:ui:compileDebugKotlinAndroid :core:presentation:compileDebugKotlinAndroid `
  :apps:desktop:verifyDesktopDistributable :apps:desktop:packageReleaseDistributable `
  --continue --max-workers=4 --console=plain
```

| Suite | Reported cases | Failures/errors | Skips |
| --- | ---: | ---: | ---: |
| Core domain | 1,071 | 0 | 0 |
| Core app | 287 | 0 | 0 |
| Core storage | 64 | 0 | 0 |
| Core UI | 624 | 0 | 0 |
| Core presentation | 537 | 0 | 0 |
| Core testkit | 6 | 0 | 0 |
| Navidrome | 252 | 0 | 0 |
| Jellyfin | 35 | 0 | 0 |
| Desktop platform | 65 | 0 | 3 |
| Desktop host | 8 | 0 | 0 |
| Total | 2,949 | 0 | 3 |

The skips are two Linux MPRIS cases and the real-output preview case, which deliberately excludes
the no-output test device. Skips are not passes. Optional unconfigured live-provider entry points
do not establish live provider acceptance. Four real HTTP range-response tests and the Windows
process-ownership regressions also pass. Core architecture, shared Android compilation, package
inputs/native resources and distributable verification pass. Windows cannot compile the Apple
targets locally; PR #426's separate complete hosted matrix remains the cross-platform evidence.

XML reports are copied before the targeted native reruns overwrite their normal Gradle result
directory. Aggregate counts and exact skip names are retained in
`build/windows-final-acceptance/complete-suite-summary.json`.

## Packaged artifact and observer integrity

Archive: `apps/desktop/build/compose/distributions/Naviamp-v2.10.0-windows-x64-release.zip`.

SHA-256: `187A535DCA44C0996DFA8CFC9596F72CEC8E74C0887469083223120688159305`.

The canonical payload has 271 files. A disposable observer copy preserves the hashes of all 270
production files other than `app/Naviamp.cfg`; that configuration changes only its main entry point,
the extra observer JAR classpath and probe options. The main renderer remains OPENGL and future
popup renderer SOFTWARE_FAST. No renderer override, runtime replacement or cadence adjustment is
introduced. The observer is prepared but is not accepted as a functioning visible measurement.

The ordinary, unchanged release launcher is started separately with APPDATA/LOCALAPPDATA directed
to the fixture profile. The loopback server receives normal connection/catalog requests, including
ping, extensions, folders, album lists, playlists and album lookup. No stream request occurs in
this check. Network requests alone do not prove the UI paints or playback works.

## Real-output preview failure and audio environment

The separately requested native case fails with a ten-second timeout at the **initial main-stream
progress check**, before Pause or preview is invoked:

```powershell
./gradlew.bat -Pkotlin.native.enableKlibsCrossCompilation=false `
  -Pnaviamp.bass.platform=windows-x64 -Pnaviamp.bass.test.outputDevice=-1 `
  :platforms:desktop:desktopTest `
  --tests app.naviamp.desktop.playback.bass.DesktopPreviewPlaybackIntegrationTest
```

Temporary, test-only diagnostics preserve every assertion and deadline. They establish shared
state `Error(message=BASS_Init failed: driver error, reason=null)`, unknown progress and no native
stream. BASS and BASSmix load, and the codec plugins load. The backend enumerates only a generic
Default output, not an initialized physical endpoint. The current-thread error code queried later
is not interpreted as the original failing call's error.

Read-only Windows checks find AudioEndpointBuilder and Audiosrv running, and both the test shell
and Explorer in session 1. PnP's AudioEndpoint class contains only `Microphone Array (AMD Audio
Device)`, with no output endpoint. The owner is asked whether an output is connected and working.
No service, output device, driver, system volume or Windows setting is changed.

The initial failure and diagnostic repetitions are retained; none is a preview or paused-position
pass. This establishes unavailable native output in this test environment, not a confirmed regression
in preview/pause behavior. Temporary diagnostic source changes are reverted after their patch and
results are saved locally. There is no production fix or weakened test.

## Rejected visible probe and ordinary-window observations

The two-cycle component/menu/lifecycle probe retains pixel verification:
`NAVIAMP_PROBE_INTEGRATED=true`, `NAVIAMP_PROBE_VERIFY=true`, `NAVIAMP_PROBE_POPUPS=true`,
`NAVIAMP_PROBE_MENUS=true`, `NAVIAMP_PROBE_LIFECYCLE=true`, `NAVIAMP_PROBE_CYCLES=2`,
task `:core:ui:playerAnimationProbe`.

The first setup also supplies `NAVIAMP_PROBE_ACTIVATE=true`. Java Desktop rejects
APP_REQUEST_FOREGROUND on Windows before measurement; this is a setup error. Removing only that
unsupported option leaves pixel/motion/lifecycle assertions intact. The corrected invocation then
rejects its first visibility check: `marker=8d8d8d expected=775533 active=true`. Neither invocation
supplies an accepted CPU phase, visible-motion result or lifecycle matrix.

Computer Use selects the exact ordinary test executable/window. Two captures show a gray client
surface with the native title bar, rather than usable application content. UI Automation exposes
only the window shell. After an attempted maximize, the tool rejects input because the point belongs
to `PickerHost.exe`, not Naviamp. Explicit target activation, fresh observation and the permitted
single retry produce the same rejection. Windows identifies `C:/Windows/System32/PickerHost.Exe`;
it is not exposed as a targetable app window. The owner is asked to clear any open system picker.
No picker interaction, permission action or unrelated process termination is attempted.

These observations do not distinguish an actual application paint defect from an obstructed
capture/desktop environment. The older package comparison, keyboard/focus, restored paused seeks,
normal-close persistence, resize/clipping, menus and visible playback retests remain unperformed.
Gray/blank content is not accepted as a low-cost surface. Earlier valid Windows acceptance keeps
its original source/runtime scope and is not relabeled as acceptance of this artifact.

## Scoped graphics-capture diagnostic

The current Windows account already has enabled Performance Log Users membership. Intel's signed
standalone PresentMon 2.3.1 console executable is downloaded from its official GitHub release to the
ignored test tools directory. Signature verification is valid, signer Intel Corporation; SHA-256
`364E5D98D4D134BD54DD25C22ED2CA2F4883F8BC3ED6502BEE0C151E3436D30C`.
No service, driver, group membership, elevation or installed software configuration is changed.

A ten-second capture uses the verified disposable child PID, a unique session name,
`--no_track_input --no_console_stats --timed 10 --terminate_after_timed` and a local output path.
It starts and stops successfully. The tool warns that elevated privilege would be required to
identify some other-account/short-lived processes; those are not the selected target. It produces
no frame CSV for this gray-window interval. **No frame records is not zero GPU/compositor cost.**
This checks the capture path only; it is not performance acceptance or an isolated attribution of
Naviamp's cached-content transforms. Computer Use is reset before the interval, ending its helper.

Reproduction references: [PresentMon console options](https://github.com/GameTechDev/PresentMon/blob/v2.3.1/README-ConsoleApplication.md),
[observer procedure](../../scripts/animation-probe/README-visible-app.md), and
[prior UI Automation attribution](386-windows-uia-attribution-2026-10-29.md).

## Remaining work at the first checkpoint (superseded in part below)

Clear the blocking system picker and provide a working output endpoint before retrying dependent
Windows checks. Reconfirm real paint and moving pixels first, then repeat static, individual,
combined, overlay and lifecycle CPU/parent-frame/compositor measurements under recorded matching
conditions. Retain outliers and establish GPU attribution; neither prior measurements nor this
diagnostic closes the budget. Complete actual keyboard/focus/accessibility and packaged playback
reproductions. Release-wide hardware, store and immutable-tag gates remain unchanged.

Logs, XML, source/payload manifests, synthetic profile and trace outputs stay in ignored
`build/windows-final-acceptance`. No raw credentials, native traces or private profile content is
uploaded. Cleanup closes only the identity-verified disposable child application, its automatically
exiting launcher and the task-owned loopback fixture. The app is stopped as a process after blocked
native input; this is explicitly not graceful-close/persistence acceptance. The system picker and
other applications are untouched. The observation helper is released. Normal settings, SQLite
database and window-state SHA-256 hashes all match their three pre-test values. The synthetic
profile, archive and raw evidence remain local for reproduction.

## Continuation after working-output / cleared-picker confirmation

The owner confirms working audio and dismisses the picker. A fresh check now finds the enabled
`2 - BenQ GL2706PQ (AMD High Definition Audio Device)` output endpoint, alongside the microphone;
PickerHost has exited. The earlier endpoint result describes that earlier interval, not the PC's
permanent audio configuration. No Windows audio/service/driver setting is changed by this task.

The unchanged real-output `DesktopPreviewPlaybackIntegrationTest` passes in 7.4 seconds, with
one case and zero failures/errors/skips. Its native log holds the main stream at 2.4 seconds while
the independent preview completes, then resumes the main stream to 2.911125. This supersedes the
environment blocker for that regression; the prior failed attempts remain retained. A first retry
is rejected by Gradle argument parsing before testing; quoting the dotted `-P` arguments in
PowerShell fixes the invocation without changing any test. The corrected run is BUILD SUCCESSFUL.

The ordinary, unchanged packaged launcher now paints the shared Home/player correctly. Scrolling
metadata visibly changes between captures. A waveform click while paused changes the position to
2:37, which remains stable in a later capture with the Play control still shown. Alt+F4 exits the
original JVM; a new wrapper/child identity starts on relaunch. A read-only query of the disposable
database records `position_seconds=157.21925497055054`. This persistence observation does not by
itself certify the restored UI or subsequent playback; those checks are recorded separately below.

The application code is unchanged from the original packaged source. Continuation branch
`test/386-windows-acceptance-resume` starts at main `530915df0`; its only differences from application
source `91d49ef56` are verification documents. New local logs and identities are under
`build/windows-final-acceptance/resume`; the passing native XML is copied there before any further
Gradle invocation. Display/driver/refresh/power conditions match the original report.

### Accepted visible component regression

The original pixel-verified two-cycle menu/lifecycle command now completes successfully in
5m41s, without `NAVIAMP_PROBE_ACTIVATE`. All twenty intervals record zero parent frames. Visible
text/progress motion, nonblank content, unchanged sibling pixels, alpha/background checks, stable
menu geometry and actual menu paint pass. Both minimize/restore and resize sequences pass; hidden
intervals are lifecycle observations, not visible-motion passes. Twelve repeated popup transitions
retain visible text, and the waveform receives the injected pointer event. The Computer Use helper
is released before the first CPU interval and remains released throughout the run.

| Component phase | Cycle 1 CPU / one core | Cycle 2 CPU / one core |
| --- | ---: | ---: |
| Static | 1.092% | 0.156% |
| Marquee | 0.156% | 0.312% |
| Progress | 1.250% | 0.156% |
| Combined | 0.781% | 0.156% |
| Menu + combined | 1.561% | 0.468% |
| Restored after menu | 0.625% | 0.781% |
| Paused | 0.000% | 0.312% |
| Minimized combined | 1.249% | 0.936% |
| Restored after minimize | 0.624% | 0.468% |
| Resized combined | 0.780% | 0.625% |

Normal size is 1000×740, parent 984×701, scale 1, OPENGL; owned transparent menus use
SOFTWARE_FAST. Resized size is 1040×760. Refresh remains 60 Hz. Cycle 1 static/progress/menu CPU
exceeds 1%; associated JVM compilation is 140/28/118 ms. These samples remain in the result and
are not discarded or subtracted. Passing the probe's pixel/frame assertions does not mean every
CPU sample meets the strict budget, or establish real-app/per-window GPU acceptance.

### Ordinary packaged application follow-through

After the probe exits, the relaunched ordinary package visibly restores 2:37 paused. Explicit Play
advances to 2:38 and then 2:50; Pause holds at 2:51. About displays v2.10.0/build 60 and renders
the release changelog with Aurral first, followed by certificate-protected servers and artist
playlists; the Improvements section scrolls with correctly clipped text. This is actual release
UI inspection, additional to the automated changelog regression in the complete suite.

The player menu paints, Track details opens with fixture metadata, and immediate Escape dismisses
it without an initial Tab. Two transition captures report `no monitor found for window`; each is
recovered through fresh window selection/state before any subsequent input. The recovered views
show the intended page/dialog. Windows UI Automation exposes shell/pane nodes rather than named
shared controls; no spoken accessibility or complete Tab/Shift+Tab/Enter matrix is claimed.

### Matched real-application CPU/frame and graphics controls

The verified observer copy now paints and plays correctly. Parent size remains 934×601, scale 1,
OPENGL, on the same 60 Hz display and power scheme. All 271 canonical payload hashes and the
270 unchanged observer payload hashes are checked before launch. Only the previously documented
test entry point/configuration and observer JAR differ. The observer records process CPU and
frames without changing application scheduling, playback or render delegates. Computer Use is
reset before every CPU/GPU interval and reconnected afterward for visible verification.

Each CPU sample lasts ten seconds. Compilation counters bracket the interval; they are aggregate
compiler elapsed milliseconds, not process CPU, and are not subtracted. Parent frames are zero
in every row; the render delegate is attached. Automatic physical captures are deliberately off,
so `physical_captures=false/false` is not an automated pixel pass. Separate Computer Use captures
verify painted content and changed title/progress before/after the intervals.

| Actual app state | CPU A / one core | CPU B / one core | Compiler bracket A/B, ms |
| --- | ---: | ---: | ---: |
| Paused long title / marquee | 0.781% | 0.156% | 10.965 / 6.773 |
| Combined, tooltip present (diagnostic) | 1.406% | 2.812% | 99.822 / 105.762 |
| Combined, hover dismissed | 2.656% | 2.187% | 72.951 / 106.446 |
| Short-title playback / progress | 3.124% | 2.812% | 67.313 / 63.671 |
| Paused short title / static | 0.625% | 0.312% | 0.635 / 0.161 |

The first combined run retains a visible Play/Pause tooltip and is not mislabeled as an
overlay-free sample. After dismissing hover, captures show no tooltip; the observer retains its
old 66×50 component entry with unavailable scale/uninitialized SkiaLayer. That stale entry is not
evidence of a painted tooltip or of owned-surface GPU cost. Its frame count is zero. The active
parent remains initialized/painted in every observation. Combined playback advances from 2:56 to
3:54, then from 4:02 to 4:58. The short fixture advances from 0:07 to 1:06; when paused, its 1:11
position holds through the static intervals. Metadata fitting/overflow and clipping remain visible.

Graphics controls use the existing `measure-windows.ps1`, both wrapper/JVM PIDs, and five valid
samples per state, separately from CPU intervals. They take roughly 14–16 seconds including
counter setup. Every counter status is checked; no missing counter is converted to zero.

| State | App GPU engine-sum mean | DWM engine-sum mean | DWM CPU / one core | App CPU during graphics collection |
| --- | ---: | ---: | ---: | ---: |
| Marquee | 0.000000 | 4.047439 | 11.563% | 1.647% |
| Combined, tooltip present | 0.038044 | 2.093364 | 11.533% | 3.221% |
| Combined, hover dismissed | 0.039197 | 2.090603 | 13.386% | 2.774% |
| Progress | 0.036069 | 2.029958 | 12.148% | 1.898% |
| Static | 0.000000 | 0.000000 | 0.607% | 0.997% |

Engine sums are not percentages of total GPU capacity or energy. DWM covers the entire desktop;
these matched observations expose motion-associated compositor activity but do not isolate the
window's compositor energy or establish a causal attribution for every DWM sample. The static
control is consistent with a quiet desktop; unrelated user activity cannot be categorically
excluded. The higher compositor CPU is retained, not accepted as a harmless baseline. No new
PresentMon run is needed to reinterpret the earlier empty capture as zero graphics cost.

### Current remaining gates and cleanup

Working native output and actual package paint are no longer blocking this Windows pass. The
native preview/pause/resume case, persisted restored seek, immediate dialog Escape, About rendering
and two-cycle component pixel/frame/lifecycle regression now have current-source evidence.
Performance remains open: active playback is around 2–3% CPU even without observation polling,
with zero parent frames, and compositor activity rises during motion. Next work should attribute
the native/playback/background CPU and compositor cost, preserving smooth cached motion and the
existing budgets; it should not reduce update frequency or manufacture a pass from selected low
samples. Complete the real-app nested-menu/focus/resize matrix and spoken accessibility, plus the
separately tracked physical-device, cross-device sync, TV/store and immutable-tag release gates.

The observer and ordinary launcher exit through normal Alt+F4. Cleanup finds no remaining
disposable Naviamp process, fixture process or loopback listener. All three normal-profile hashes
match the continuation's pre-test hashes. Raw logs, endpoint/process identities, compiler counters
and synthetic profile remain local. This continuation changes only verification documents; no
platform production file, product behavior, renderer, setting, schema, test assertion or deadline
changes. No release/tag/store/mirror action is taken.
