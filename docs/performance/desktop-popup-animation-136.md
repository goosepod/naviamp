# Popup animation cost: issue #136

Issue: https://github.com/goosepod/naviamp/issues/136

Current tracking decision (October 4, 2026): the owner requested closing #136 as completed
at the Windows checkpoint and moving remaining macOS and Linux work into separate tickets:
[macOS #219](https://github.com/goosepod/naviamp/issues/219) and
[Linux #220](https://github.com/goosepod/naviamp/issues/220). Earlier instructions below to
keep #136 open describe the acceptance decision at those historical checkpoints and are
superseded by this scope split. Measurements, CPU outliers and unverified accessibility
remain recorded; closing the ticket does not turn them into passing measurements.

## Candidate and acceptance budget

Core retains each native raster scene while the host reports separate owned popup windows.
Shared menus and modal surfaces occupy the fixed window viewport, keeping native geometry
independent of entrance transforms and hover drawing. Menus position a bounded shared surface
inside that viewport and dismiss outside clicks; modals draw their own scrim above the raster scene. Material menu items, dialog
content slots, common input, and semantics remain shared. Hosts without separate popup windows
continue to delegate to their existing Material/Compose presentation.

The sole platform production change is `core/ui/src/jvmMain/kotlin/app/naviamp/ui/DesktopRasterPresenter.kt`.
Its concrete boundary is Compose Desktop's native-window layer configuration and AWT/AppKit
owned-window stacking. It publishes these facts to Core; it contains no menu/dialog controller.
No Android/iOS production file or Linux native raster implementation changes.

Acceptance is deliberately pending. A passing build or an unverified surface is insufficient:

- On this macOS machine, stabilized synthetic static, individual, combined, menu-open, modal-open,
  and restored ten-second samples must use at most 1% of one process CPU core and zero parent
  frames. Text and progress must visibly move, with no unrelated pixel changes.
- In the real application, opening an overlay must add no continuous parent redraw stream and no
  more than one percentage point of sustained process CPU versus its equivalent closed state.
  Legitimate provider/playback/UI updates must be recorded rather than misclassified as animation.
- Record compositor cost under the same size, scale, refresh and power conditions. WindowServer
  is a whole-desktop observation, not an isolated measurement of Naviamp GPU work. Reject samples
  interrupted by unrelated window activity.
- Check nested menus/dialogs, first hover, scrolling, repeated anchor clicks, checkbox and label
  clicks, cancellation, scrims, player input, resize/clipping, idle/paused, minimize/hide and restore,
  keyboard and accessibility. Repeat native acceptance on Windows and Linux before closing #136.

## Local baseline: October 2, 2026

Apple M1, Metal, AC power, 1000 × 740 window with 1000 × 708 parent surface, 150 library rows.
Both active displays reported 2560 × 1440 logical/pixel resolution and 75 Hz through CoreGraphics.

| State | Process CPU | Parent frames / 10 seconds |
| --- | ---: | ---: |
| Static | 0.39% | 0 |
| Marquee | 0.48% | 0 |
| Waveform | 0.22% | 0 |
| Combined | 0.25% | 0 |
| Popup + combined | 21.72% | 698 |

The first four phases passed visible-motion and unchanged-sibling assertions. Another application
covered the probe before the popup's final screen capture, so the complete run failed its visibility
gate. The popup CPU/frame result is diagnostic reproduction, not a fully accepted baseline sample.
A separate candidate attempt was also obscured during warm-up and supplies no performance evidence.
Do not count either failed run as candidate acceptance.

The screen comparison ignores per-channel differences of at most two values: the initial static
captures showed one-value display-color dithering with zero parent draws. Movement, nonblank content,
and sibling-content assertions remain enabled.

## Visible macOS candidate: October 2, 2026

The original owned-menu run checked moving parent content but did not assert visible menu pixels.
A later physical-display check found the menu absent; its CPU/frame numbers below are diagnostic
only and are withdrawn as menu acceptance. The shared-modal run passed its scrim/motion gate under
the same machine, window dimensions, scale, refresh and AC conditions as the baseline. Every row below recorded
zero parent frames per ten seconds. Cached text and waveform motion, unchanged sibling pixels,
stable popup geometry, twelve reopen samples, and waveform pointer input passed. These checks
did not prove that the original menu itself was painted.

| State | Menu run CPU | Modal run CPU |
| --- | ---: | ---: |
| Static | 0.47% | 0.30% |
| Marquee | 0.58% | 0.33% |
| Waveform | 0.25% | 0.30% |
| Combined | 0.34% | 0.35% |
| Popup + combined | 0.36% | 0.38% |
| Restored combined | 0.28% | 0.43% |

The dark scrim visibility marker allows six channel values of display color conversion; only
rapid reopen captures accept the marker's known brown tint at intermediate entrance opacity.
Steady-state movement and sibling comparisons retain the two-value tolerance. A partially entered
but correctly moving modal must not be mistaken for an obscured surface.

Whole-desktop WindowServer samples during the menu run were polluted by other desktop activity
and provide no accepted isolated compositor-cost result. Real-application, idle/paused,
hide/minimize/restore, resize, keyboard and full accessibility acceptance remain pending.
Windows and Linux acceptance also remain pending; these synthetic results do not close #136.

Logs: `/private/tmp/naviamp-136-menu-candidate.log` and
`/private/tmp/naviamp-136-dialog-accepted.log`.

## Stronger menu paint gate and fixed viewport

Physical real-app captures exposed blank menus despite accessible menu nodes and moving parent
content. The synthetic probe originally missed this because it asserted parent visibility only.
The strengthened gate requires more than 5,000 menu-background pixels and 50 bright label-edge
pixels on the physical display. Both a popup-frame-clock change and layout padding alone failed
that gate; neither is accepted as a fix.

A fixed viewport with shared bounded placement passed the stronger popup-only run:
menu + combined 0.88% CPU, restored combined 0.30% CPU, zero parent frames in each ten-second
sample. It recorded 64,307 menu-background pixels and 1,272 label pixels, visibly moving text and
waveform, unchanged siblings, stable geometry, twelve reopen checks and pointer input. The menu
capture was visually inspected. The normal opacity/scale entrance remains enabled. Full-matrix
and real-app acceptance of this final candidate are recorded separately below when complete.

Local log: `/private/tmp/naviamp-136-menu-viewport.log`.

## Reproduction

Keep the probe visibly unobscured. Do not run rendered UI tests or change desktop spaces during
measurements. Logs and PNGs are local diagnostics and are not committed.

```sh
# Previous same-canvas policy, with the current common implementation's delegated fallback.
JAVA_TOOL_OPTIONS=-Dcompose.layers.type=SAME_CANVAS \
  NAVIAMP_PROBE_INTEGRATED=true NAVIAMP_PROBE_POPUPS=true NAVIAMP_PROBE_VERIFY=true \
  ./gradlew :core:ui:playerAnimationProbe

# Owned-window candidate; repeat with NAVIAMP_PROBE_DIALOGS=true instead of MENUS.
NAVIAMP_PROBE_INTEGRATED=true NAVIAMP_PROBE_POPUPS=true NAVIAMP_PROBE_VERIFY=true \
  NAVIAMP_PROBE_MENUS=true ./gradlew :core:ui:playerAnimationProbe
```

The menu/dialog modes verify the same cached text and waveform under actual shared overlays,
stable native geometry, unchanged siblings and zero parent frames after entrance transitions.
The default popup mode also checks tooltip-like surfaces.

### Real application sampler

`scripts/animation-probe/ApplicationRenderProbe.java` is an opt-in test agent. It observes existing
Skiko render delegates and process CPU; it creates no UI and does not alter playback. Compile it
with JDK 21, create a JAR with `Premain-Class: ApplicationRenderProbe`, and launch the staged
application JARs with that JDK and `-javaagent:/path/to/probe.jar=/path/to/measurements`.
Use `-Dskiko.renderApi=METAL -Dnaviamp.visualizer.macosMetal=true` on macOS and
`-Dnaviamp.developmentDataProfile=true` for the separate development data profile. This test launch
uses the staged application and native resources with the full JDK; packaged-launcher smoke checks
must be recorded separately.

Write a distinct phase name to the sampler directory's `phase` file after each state stabilizes.
Ten seconds later, the agent writes `<phase>.txt` with process CPU and parent/owned-surface frame
counts and before/after physical-display PNGs. It recognizes SkiaLayer subclasses and records
minimized state. Physical captures require an active probe window; an inactive app may be on
a different desktop space. `physical_captures=false` is not visibility acceptance. Inspect the
overlay itself as well as moving player content: semantics alone
do not prove that a native surface painted. Keep native windows visibly unobscured and inspect actual motion separately. Observe
compositor cost alongside each sample; the agent does not measure GPU energy.

## Verification checkpoint

- Initial common behavior tests: 15 passed, including nested owned popups, native-scene reuse,
  hide cleanup, bounded/RTL placement, hover/toggle and modal checkbox input.
- Full shared UI suite: 458 tests passed, including the final shared dialog entrance refinement.
- The final pointer-click and dialog accessibility regression passed in a focused rerun.
- Common Android, JVM, iOS device and iOS simulator compilation passed before native host wiring.
- Desktop tests, architecture check and staged macOS packaging passed.
- Final shared/native rebuild, all common-target compilation, architecture check and staged
  packaging passed. The modal synthetic measurement passed its original checks; menu acceptance was withdrawn
  after the stronger physical menu-paint assertion failed.
  Real application acceptance, compositor samples and Windows/Linux native acceptance remain pending.

Raw local logs: `/private/tmp/naviamp-136-baseline2.log`, `/private/tmp/naviamp-136-candidate.log`,
`/private/tmp/naviamp-136-shared-tests2.log`, `/private/tmp/naviamp-136-package.log`,
`/private/tmp/naviamp-136-final-build.log`.

### Metal activity

Use `xcrun xctrace record --template 'Metal System Trace' --attach PID --time-limit 10s
--output recording.trace` separately from CPU samples. Export `metal-gpu-intervals` with xctrace,
then run `python3 scripts/animation-probe/summarize-metal.py intervals.xml --process Naviamp
--process WindowServer`. It reports top-level active intervals by process and GPU channel.
Channels overlap, so their durations are not additive GPU utilization or energy. WindowServer
includes all desktop composition. Raw traces contain environment metadata: retain them locally;
publish only the narrow summary.

## Final local checkpoint

The final common implementation preserves Compose's actual anchor bounds inside the fixed
viewport and handles arrow keys, Escape/Back and outside clicks in shared code. All 460 shared
UI tests passed, including painted labels and keyboard focus/cancellation. Android, iOS device
and simulator compilation, architecture verification and staged macOS packaging passed.

The anchor-preserving popup-only run passed physical menu paint (64,307 background pixels,
1,272 label pixels), moving text/waveform, unchanged siblings, stable native geometry, reopen
checks and pointer input. It measured 0.95% menu CPU with zero parent frames. Its restored sample
used 3.11% CPU with zero parent frames and does not meet the documented 1% sample budget.
The earlier fixed-viewport run measured 0.88% menu and 0.30% restored CPU with zero parent frames.
These samples alone did not establish full final synthetic budget acceptance. The subsequent
unobscured full-matrix repetitions are recorded below.

A staged fixed-viewport app visibly painted menu labels through the native-window capture, and a pointer
click outside dismissed it. At that checkpoint, the final unobscured full matrix and real-app CPU/GPU/lifecycle
acceptance could not be completed: Windows App covered the physical display, and the computer-use
service timed out during a desktop-activation attempt. The strict visibility gate rejected those
runs. The sampler now recognizes SkiaLayer subclasses, records minimized state and saves physical
captures only while a probe window is active. The synthetic probe saves PNGs only after validating
its known visibility marker.

Earlier real-app diagnostic samples recorded idle 0.38% CPU / zero parent frames, combined playback
4.16% / ten frames, and menu 4.34% / ten frames per ten seconds. The original menu was not physically
painted, so its result is rejected as overlay acceptance. A separately recorded ten-second Metal
trace attributed 0.132 ms Vertex and 0.594 ms Fragment activity to Naviamp; WindowServer recorded
25.828 ms Vertex, 271.769 ms Fragment and 3.359 ms Compute. Channels can overlap, WindowServer
includes the whole desktop, and the menu visibility failed. These are diagnostics, not accepted
final-candidate GPU/compositor evidence. Raw trace and narrow XML/JSON summaries remain local.

Current conditions: Apple M1; two displays at 2560 × 1440, 75 Hz; AC power. Synthetic window
1000 × 740 / parent 1000 × 708; real-app window 950 × 640 / parent 950 × 608.

Logs: `/private/tmp/naviamp-136-menu-viewport.log`, `/private/tmp/naviamp-136-menu-anchor.log`,
`/private/tmp/naviamp-136-anchor-final-build.log`,
`/private/tmp/naviamp-136-menu-final-cycles.log`,
`/private/tmp/naviamp-136-menu-final-cycles2.log`.

The subsequent unobscured full menu matrix with `NAVIAMP_PROBE_CYCLES=2` completed successfully.
Both cycles recorded zero parent frames and unchanged sibling pixels in every phase. Menu paint
checks recorded 64,309 background pixels and 1,272 label pixels in both captures of each menu
phase; the physical screenshot was also visually inspected. Text/progress motion, stable owned
window geometry, twelve reopen captures and player pointer input passed.

| State | Menu cycle 1 CPU | Menu cycle 2 CPU |
| --- | ---: | ---: |
| Static | 3.32% | 0.32% |
| Marquee | 0.29% | 0.31% |
| Waveform | 0.44% | 0.28% |
| Combined | 0.29% | 0.29% |
| Menu + combined | 0.63% | 0.53% |
| Restored combined | 0.33% | 0.45% |

The settled second cycle meets the synthetic CPU budget throughout. The first static sample
exceeds it and is retained as an outlier, not silently accepted. The earlier 3.11% restored result
did not recur in either restored sample. These repetitions support low steady animation cost but
do not establish the cause of either outlier or replace real-app and GPU acceptance.
Log: `/private/tmp/naviamp-136-visible-menu-cycles.log`.

The matching two-cycle modal matrix also completed successfully. Both cycles passed every
CPU sample, zero parent frames, visible motion, unchanged siblings, stable geometry, twelve
reopen captures and pointer input. The physical dialog capture visibly contains its title,
body and confirmation button above the scrim.

| State | Modal cycle 1 CPU | Modal cycle 2 CPU |
| --- | ---: | ---: |
| Static | 0.43% | 0.31% |
| Marquee | 0.30% | 0.30% |
| Waveform | 0.27% | 0.34% |
| Combined | 0.30% | 0.28% |
| Modal + combined | 0.39% | 0.43% |
| Restored combined | 0.30% | 0.32% |

Log: `/private/tmp/naviamp-136-visible-dialog-cycles.log`. These visible synthetic runs provide
CPU and parent-frame evidence; they do not provide new GPU/compositor measurements.

`NAVIAMP_PROBE_POPUP_ONLY=true` shortens diagnostic iteration but does not replace the full matrix.
Complete real-app static/paused/playing overlays, minimize/hide/restore, resizing,
keyboard and accessibility, and matching GPU observations. Windows and Linux acceptance remain
required. Keep #136 and its PR open until these budgets and interactions are verified.

## Windows checkpoint: October 2, 2026

Windows 11, Ryzen 7 5800H, AMD Radeon Graphics, one 2560 x 1440 display at
60 Hz, scale 1.0, CustomPlan1 power plan. Power source was not established.
The branch was integrated with main at 35472796. All 475 shared UI tests pass;
Android compilation, iOS device/simulator klib compilation and the architecture
check pass. These are build/regression results, not native accessibility acceptance.

Earlier visible OpenGL menu and modal probes passed physical paint, animation
motion, unchanged siblings, stable geometry, twelve reopen captures and pointer
input. Parent frame counts were zero in every sampled phase. Process CPU varied
between repetitions and is not accepted as a steady performance budget result.

A local diagnostic runtime added java.instrument, jdk.management and jdk.jfr to
observe the actual development app; the production package was not changed.
At a 2560 x 1369 client size, paused closed samples used 8.12% and 10.78% of one
core with zero parent frames; paused menu used 5.62% with zero parent frames.
Playing closed samples used 13.12% and 12.81%, and playing menu 9.06%; each
recorded 10 or 11 parent frames per ten seconds, consistent with elapsed labels
rather than continuous parent animation. Menus and progress physically painted.
Track transitions, startup activity and CPU variability prevent treating those
numbers as a matched overlay delta or a performance pass.

External Windows GPU engine-sum observations were 0.462 with a menu and 0.434
closed, with DWM engine sums 16.82 and 16.48. DWM includes the whole desktop;
these diagnostic samples do not establish isolated compositor acceptance.
The Compose content was absent from the available native UIA tree, so a native
screen-reader pass is not claimed. Real-app modal interaction, complete lifecycle
and repeatable matched CPU/GPU acceptance remain open. Do not close #136 from
these partial Windows results.
The generic Windows probe initially selected Direct3D, consumed roughly one CPU
core and crashed in native rendering at popup entry. That backend is not used by
the packaged Windows app. The probe task now explicitly selects OpenGL on Windows
to match the application; Direct3D results are retained only as failed diagnostics.
### Windows native positioning follow-up

The AWT adapter now skips an identical JWindow bounds assignment. Core still owns
scene content and animation policy; the adapter only compares and applies native
window coordinates. This does not alter animation rate, cached content or motion.
Two matched OpenGL menu cycles used a 1000 x 740 native window (984 x 701 client),
scale 1.0 and 60 Hz. Each passed physical menu paint, visible text/progress motion,
unchanged siblings, stable bounds, twelve reopen captures and pointer input;
all sampled parent frame counts were zero.

| State | Previous cycle 1 | Guarded cycle 1 | Previous cycle 2 | Guarded cycle 2 |
| --- | ---: | ---: | ---: | ---: |
| Static | 1.40% | 1.72% | 2.81% | 4.37% |
| Marquee | 0.47% | 0.47% | 4.06% | 2.50% |
| Waveform | 0.78% | 1.87% | 4.69% | 3.59% |
| Combined | 1.09% | 1.09% | 4.84% | 4.37% |
| Menu + combined | 6.40% | 1.72% | 10.62% | 2.03% |
| Restored combined | 6.86% | 6.86% | 6.08% | 4.53% |

The menu observations improved, but static/restored CPU remains variable and too
high to establish full animation acceptance. This is an incremental candidate,
not a completed performance fix. Twenty-six shared popup/input/raster regressions
and verifyCoreFirstArchitecture pass after the native change.

The lifecycle probe now resets native size/position before each repeated cycle and
records geometry, scale and refresh for every phase. Modal alpha/pattern gates
account for the settled 60% black scrim without relaxing pixel/motion tolerances;
opaque black or a missing patterned parent still fail the check.

The final guarded OpenGL modal matrix passed both complete cycles, including paused,
minimized, restored and resized phases, zero parent frames, alpha/pattern checks,
visible text/progress, unchanged siblings, twelve reopens and pointer input. Physical
captures visibly contain the title, body, Confirm button and scrim. Modal CPU was
3.28% and 2.03%; restored samples ranged 5.15–7.34%. These results strengthen
functional acceptance but do not resolve the CPU baseline or real-app budget gate.

## Windows verification: October 3–4, 2026

Tested the candidate at d59680b3 on the same Ryzen 7 5800H / Radeon Windows machine.
OpenGL, 2560 × 1440 display, 60 Hz, scale 1.0 and My Custom Plan 1. The final
GetSystemPowerStatus check reports AC power and no battery. Synthetic windows were
1000 × 740 with 984 × 701 client surfaces, resized to 1040 × 760. Real-app performance
samples used a 1264 × 964 window and 1262 × 932 client; subsequent interaction checks
also used a 1001 × 964 window. These are local Windows results, not cross-platform acceptance.

### Measurement interference and probe changes

The active computer-use session generated frequent native WM_GETOBJECT accessibility
requests. A temporary test-only Windows message observer identified that traffic; it
forwarded every message and removed its hooks after sampling. Releasing the automation
session before timing reduced an isolated real-app paused sample to 0.31% CPU. Earlier
runs with sustained accessibility polling, concurrent builds or JFR are retained as
diagnostics, not steady animation-budget evidence. Do not disable accessibility in the
product to improve measurements. Test reader interaction separately from an idle interval.

The synthetic probe now records per-thread CPU and compilation activity alongside process
CPU. The real-app observer reattaches if a render delegate is replaced and records whether
its observer remains attached. Every accepted frame sample below has an attached observer.
Visible elapsed labels can advance with zero parent frames: NowPlayingPositionLabel uses
NaviampRasterValueText, which updates the cached raster independently of the parent canvas.

The packaged Windows launcher creates a child application process. Include both PIDs in
measure-windows.ps1; a wrapper-only sample is invalid. The script now rejects an unavailable
CPU counter for any requested PID instead of allowing an aggregate to conceal it.

### Full synthetic matrices

Both two-cycle matrices completed with the automation session released, no JFR and the
physical visibility gates enabled. All 40 ten-second samples recorded zero parent frames.
Physical menu/dialog captures were visually inspected. Moving text and progress, unchanged
sibling pixels, stable popup geometry, scrim/alpha/pattern checks, paused motion, minimize,
restore, resize/clipping, twelve reopen checks per run and waveform pointer input passed.
Menu captures contained over 65,000 background pixels and 1,000 label-edge pixels.

CPU percentages below mean one process CPU core. Reported zero is counter resolution,
not a claim that rendering performs no work.

| State | Menu cycle 1 | Menu cycle 2 | Modal cycle 1 | Modal cycle 2 |
| --- | ---: | ---: | ---: | ---: |
| Static | 0.94% | 0.00% | 0.94% | 1.09% |
| Marquee | 2.96% | 0.47% | 0.16% | 0.31% |
| Waveform | 0.78% | 0.47% | 0.78% | 0.78% |
| Combined | 0.94% | 1.25% | 1.25% | 0.31% |
| Overlay + combined | 0.78% | 1.25% | 0.47% | 0.00% |
| Restored after overlay | 0.62% | 0.47% | 0.31% | 0.47% |
| Paused | 0.31% | 0.31% | 0.31% | 1.25% |
| Minimized | 0.31% | 0.78% | 0.16% | 0.78% |
| Restored after minimize | 0.62% | 1.56% | 0.62% | 0.31% |
| Resized | 0.62% | 0.16% | 0.78% | 2.18% |

Opening either overlay did not increase CPU relative to the immediately preceding combined
sample. Nevertheless, eight samples exceed the 1% reference used for the macOS synthetic
budget. The first marquee outlier coincided with 337 ms of JVM compilation; that does not
explain all outliers. These results pass rendering/interaction gates and do not establish
uniform low-CPU acceptance. Further repetitions without an implementation change or a new
diagnostic hypothesis are not useful.

Settled Windows GPU engine-sum observations during modal cycle 2 reported zero for the probe
process at counter resolution. DWM means were 5.139 for combined, 2.211 for modal-open and
2.052 for restored phases; restored combines both restored intervals. Static, paused and
minimized phases reported zero DWM engine sum. Sampling used phase seconds 6–14, excluding
entrance and capture work. DWM includes the whole desktop, and engine sums are not percentages
of total GPU capacity. These observations do not isolate compositor energy or establish a
GPU budget by themselves.

### Real application and packaged launcher

The diagnostic JVM physically painted menus and track-details dialogs, including the scrim.
Playback and waveform position advanced under both overlays. Isolated paused samples measured
0.31% closed, 0.00%/0.16% with a settled menu, and 0.47%/0.78% with a modal. A first menu sample
used 1.56% and is retained as a transient outlier. Consecutive playing samples on one track used
5.62% closed, 2.34% menu-open and 3.91% modal-open. Every sampled parent frame count was zero;
these variable single intervals do not establish a repeatable matched CPU delta.

The uninstrumented staged Naviamp.exe also launched successfully in its configured development
profile. A long track title and progress visibly advanced, with painted menus and dialogs.
Separate read-only counter intervals included both the wrapper and actual app process:

| Packaged state | Process CPU | App GPU engine sum | DWM GPU engine sum |
| --- | ---: | ---: | ---: |
| Playing combined, closed | 4.73% | 0.04125 | 2.093 |
| Playing combined, menu | 3.09% | 0.04115 | 2.122 |
| Playing combined, modal | 4.35% | 0.04205 | 2.128 |
| Paused, minimized | 0.30% | 0.00000 | 0.000 |
| Paused, restored | 0.86% | 0.00000 | 3.058 |

These ten-counter intervals lasted approximately 15–17 seconds including counter initialization;
they are separate from the diagnostic agent's ten-second frame intervals. No parent frame count
is claimed for the uninstrumented launcher. A wrapper-only zero-CPU interval was rejected.

Real-app pointer playback, waveform seeking, outside-click dismissal, menu Escape cancellation,
track-details modal opening/closing, modal Tab/Return activation and Escape dismissal were
checked. At the narrower native size, the visualizer submenu painted, scrolled within its
viewport and cancelled with Escape. Minimize/restore preserved the paused player and visible
title motion. Native UIA did not expose the Compose content; a native screen-reader pass and
complete keyboard traversal are not claimed. No user playlists, favorites or visualizer choices
were changed by these interaction checks. Test-owned app/probe processes were stopped afterward.

### Build results and remaining gate

All 475 shared UI tests passed. JVM test compilation, Android shared UI compilation, iOS device
and simulator klib compilation and verifyCoreFirstArchitecture passed. iOS native host execution
is unavailable on this Windows machine. Staged Windows packaging passed using existing unchanged
native binaries: the initial native rebuild failed because CMake was absent from that shell's
PATH, so this is not a clean native-build result. The staged launcher smoke test passed separately.
This verification follow-up changes test probes and documentation only; it changes no Android,
Desktop or iOS production file.

Keep #136 and PR #197 open/draft. Remaining acceptance is repeatable real-app CPU behavior,
native screen-reader/full keyboard verification and the outstanding macOS/Linux physical matrix.
Do not accept the CPU outliers, lower animation frequency, freeze motion or broaden the budget
to declare completion. Investigate a specific cause before spending more time on repeated cycles.

Local logs: windows-136-20261003-menu-isolated.log,
windows-136-20261003-modal-isolated.log, windows-136-20261003-build.log,
windows-136-20261003-common-build.log and windows-136-20261003-package.log.
Physical captures and counter JSON remain under build/issue-136-20261003, outside version control.

To reproduce either full matrix in PowerShell, release any active UI inspector before timing:

```powershell
$env:NAVIAMP_PROBE_INTEGRATED = 'true'
$env:NAVIAMP_PROBE_POPUPS = 'true'
$env:NAVIAMP_PROBE_VERIFY = 'true'
$env:NAVIAMP_PROBE_LIFECYCLE = 'true'
$env:NAVIAMP_PROBE_CYCLES = '2'
$env:NAVIAMP_PROBE_MENUS = 'true'
$env:NAVIAMP_PROBE_DIALOGS = 'false' # Swap these two flags for the modal matrix.
.\gradlew.bat :core:ui:playerAnimationProbe --console=plain
```

## October 4 merge preparation

The owner completed the Windows checkpoint and authorized merging PR #197. Physical
macOS work is tracked in #219 and Linux work in #220, coordinated with #203. Earlier
instructions to retain the ticket/PR open describe the earlier acceptance state; the
recorded outliers and unverified screen-reader coverage remain limitations, not passing
measurements. No additional unchanged physical measurement cycles were run for merge
preparation.

Merging accepted main 22762b0c exposed conflicts only in NaviampPlayerAnimationProbe.kt.
The resolution preserves fullscreen/windowed/restored modes from main together with
popup cycle selection, scrim-aware captures, menu paint and geometry checks. Capture
names include both window mode and cycle so neither run overwrites the other's evidence.
Production files merged cleanly; resolution changes only the probe and this record.

Post-resolution verification passed: 492 shared UI JVM tests, zero failures/errors/skips;
JVM probe compilation; Android shared UI compilation; iOS Arm64 and simulator Arm64
shared UI klib compilation; and Core-first architecture verification. Required GitHub
verification remains the final merge gate. Existing Windows physical evidence and its
native-build qualification above remain applicable.

Native production boundary remains core/ui/src/jvmMain/kotlin/app/naviamp/ui/DesktopRasterPresenter.kt:
Compose Desktop/AWT owned-window configuration, JWindow bounds and AppKit/CALayer stacking
facts. Shared Core owns popup geometry, motion, input, transitions and lifecycle policy.


## October 5 macOS raster rendering follow-up (#227)

Issue #227 addresses the native image boundary and a reproduced resize-positioning bug.
Core's raster contract explicitly supplies sRGB pixels in window top-left coordinates.
The AppKit adapter prepares named-sRGB premultiplied CoreGraphics images before enqueueing
CALayer presentation, retains them as native contents, and rejects decode/allocation failures
through the existing common fallback. It does not change animation cadence or product policy.
A renamed Boolean JNI entry point also avoids treating an older void-returning library as the
new adapter. Cached fallback pixels, actions and semantics remain common.

The physical alpha golden needed correction: macOS display compositing is not the probe's
fixed sRGB arithmetic. Independent named-sRGB CALayer.backgroundColor primitives produce
0x9e3864 for the half-transparent red reference on this setup; normalized image contents
produce 0x9d3864. Retain the original two-level per-channel tolerance. Check opaque white,
opaque red, opaque mixed color, alpha and the bare patterned parent. Capture the subject
before attaching calibration layers as well, so a reference cannot repair the tested scene.
The earlier original-decoder observation of 0x9e2764 remains in the raw record. Today's matched
original-decoder controls produce 0x9e3864 and pass the calibrated alpha check, including the
original simple fixture. They do not reproduce the earlier 17-level green discrepancy. This
is not evidence that normalization alone repaired that earlier color discrepancy.

Resize did reproduce a concrete defect: changing 1000x740 to 1040x760 shifted cached native
pixels downward by 20 pixels while their shared coordinates stayed fixed. Native positions
were calculated against an unflipped parent's old height. CALayer.autoresizingMask now anchors
the cached region to the parent's top edge. Test reference layers use the same native anchoring,
and top-edge plus center pixel checks prevent two misplaced layers validating one another.
The corrected visible resize run passes alpha, top-origin placement, text/progress motion,
clipping, unchanged siblings, twelve popup transitions and shared waveform input.

Conditions: Apple M1, Metal, AC power, two LG HDR QHD displays at 2560x1440/75 Hz, scale 1.0;
1000x740 windows with 1000x708 parent surfaces unless explicitly resized. CPU is percent of
one process core. Every measured synthetic state reported zero parent frames. Visible
samples were checked for moving content; minimized samples are not visual passes.

| Synthetic state | Process CPU | Notes |
| --- | ---: | --- |
| Static | 0.332% | Visible, unchanged pixels |
| Marquee | 0.285% | 9,107 changed text pixels |
| Progress | 0.914% | 218 changed progress pixels |
| Combined | 0.811% | Text and progress moved; siblings unchanged |
| Menu + combined | 1.027% | Above the 1% budget; retained |
| Paused | 0.261% | Unchanged pixels |
| Minimized, menu run | 0.621% | Native parent hidden; zero frames |
| Modal + combined | 1.117% | Scrim/alpha and motion passed; above budget |
| Minimized, modal run | 1.035% | Above budget; zero frames |
| Restored, modal run | 0.974% | Explicitly raised test window; visible motion |
| Combined, resize-fix run | 2.865% | 226 ms compilation; above budget, retained |
| Resized, resize-fix run | 0.988% | 1040x760, correct placement and motion |

The first restore capture was rejected as obscured and was never saved or counted as passing.
The test-only activation option now raises the restored test-owned window before capture.
The original resize failure is retained with its physical captures; the corrected run is
separate. No budget was broadened and no animation was slowed to obtain these results.

The first synthetic matrix recorded whole-desktop WindowServer CPU and whole-device AGX
utilization alongside the phases. Those counters are not application GPU attribution and
cannot close the compositor-cost gate. Raw evidence is retained under build/verification-227.
Native VoiceOver and complete keyboard traversal are not claimed. Keep #219 open for CPU
outliers, application-attributed compositor/GPU cost and remaining native accessibility work.

Production native-boundary audit:

- core/ui/src/jvmMain/kotlin/app/naviamp/ui/DesktopRasterPresenter.kt translates the JNI
  acceptance result and closes the native handle; shared Core owns fallback behavior.
- native/visualizer-metal/src/naviamp_raster_compositor.mm invokes AppKit PNG decoding,
  attaches CoreGraphics images to CALayers and applies native parent-resize anchoring.
- native/visualizer-metal/src/naviamp_raster_image.hpp requires the CoreGraphics image,
  bitmap-context and color-space ABI to provide premultiplied native storage.

No Android or iOS production file changes. The common owner is NaviampAnimatedRaster;
its documentation defines the native presentation contract. Verification passed 493 shared
UI JVM tests, five Desktop tests, Core-first architecture verification, Android shared UI
compilation, both iOS Arm64 shared UI klib compilations and staging the local Mac app.
The native CoreGraphics regression checks dimensions, named-sRGB tagging, exact premultiplied
bytes, transparent RGB, near-zero/near-opaque alpha and null input. Its differently tagged
input also rejects converting shared sRGB values instead of preserving them.


### Staged app evidence

The rebuilt app used an isolated development profile and loopback-only synthetic Subsonic
tracks, with volume zero. No real Cast receiver was invoked. The observer was attached to
the real packaged application's surfaces; all visible phases had successful before/after
physical captures. The minimized phase correctly has no physical captures.

| Staged state | Process CPU | Main parent frames / 10 seconds |
| --- | ---: | ---: |
| static | 0.765% | 0 |
| marquee | 0.630% | 0 |
| waveform | 1.763% | 0 |
| combined | 1.845% | 0 |
| modal | 6.419% | 20 |
| restored | 2.195% | 0 |
| paused | 0.762% | 0 |
| minimized | 0.539% | 0 |
| restored-window | 0.431% | 0 |
| resized | 0.446% | 0 |

The statistics modal also redrew its own surface ten times in the interval. Its 6.419% CPU
versus 1.845% combined closed state, plus twenty main-parent frames, fails the real-app
popup budget and remains with #219. Provider/playback behavior was left at its normal cadence.
No full performance acceptance is claimed by #227.

Physical pixel comparisons confirmed 3,245 changed title pixels in the paused marquee state,
130 progress pixels in the progress-only state, and both title (3,216) and progress (130)
movement in the combined state. The artwork crop was unchanged in all compared normal-size
phases. After modal dismissal, pause and restore, title motion remained visible. The 900x700
resized app was inspected separately; its layout and cached text stayed aligned. The old
normal-size crop was not used to claim resize motion.

Whole-desktop WindowServer samples were approximately 47–48% CPU and whole-device GPU
utilization approximately 8–12% in the closed states. All raw per-phase counter samples are
retained, including modal values. These are background-inclusive observations, not isolated
Naviamp GPU measurements, and they cannot certify the remaining GPU gate.

Native regression reproduction after staging on macOS Arm64:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :apps:desktop:stageLocalTestApp -Pnaviamp.bass.platform=macos-arm64 -Pcompose.desktop.packaging.checkJdkVendor=false
ctest --test-dir platforms/desktop/build/generated/DesktopVisualizerMetalBuild/macos-arm64 --output-on-failure
```

For the visible top-origin regression, set NAVIAMP_PROBE_INTEGRATED=true,
NAVIAMP_PROBE_VERIFY=true, NAVIAMP_PROBE_ACTIVATE=true, NAVIAMP_PROBE_LIFECYCLE=true and
NAVIAMP_PROBE_PHASES=combined,resized-combined; run :core:ui:playerAnimationProbe with the
staged resources path and naviamp.visualizer.macosMetal=true. Use a visible unlocked desktop;
black, obscured or frozen captures must be rejected. Menu/modal matrices use the existing
NAVIAMP_PROBE_POPUPS, NAVIAMP_PROBE_MENUS and NAVIAMP_PROBE_DIALOGS options.
