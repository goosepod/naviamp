# Windows popup transparency investigation (#325)

Release target: **v2.10.0**, [issue #325](https://github.com/goosepod/naviamp/issues/325).
A candidate packaging fix selects SOFTWARE_FAST before Windows surface creation.
It remains under verification; this record does not claim completed acceptance. The original v2.9.0 Store submission
remains unchanged and is in certification as of October 23, 2026.

## Established evidence

The owner reproduced black app backgrounds behind popups and black tooltip padding
on the physical Windows display with Moonlight disconnected, and in both the MSIX
and unchanged standalone release. The defect does not require streaming or MSIX.
The host has AMD Radeon Graphics and NVIDIA GeForce RTX 5060 Ti adapters. The
isolated popup probe below identifies its OpenGL renderer as NVIDIA; this does not
retroactively identify the adapter for every earlier application surface.

Direct3D crashed in Skiko's native PictureRecorder path. OpenGL with COMPONENT
popup layers removed the black regions, but playing with the popup open increased
application CPU from 2.175% to 18.487% of one core, reversing to 1.483% after closing.
Neither diagnostic is an accepted workaround. Source evidence and measurement
limitations are in issue #325 and the #323 packaging verification record.

## Ownership and next experiment

Shared owners are `NaviampPopupSurface.kt` (menu/dialog geometry, transitions, input,
scrim and semantics), `NaviampTooltip.kt` (tooltip interaction/content), and
`NaviampAnimatedRaster.kt` (cached content, native presentation and fallback policy).
The Desktop host and JVM adapters supply AWT ownership and native compositor effects.
There is no reason to create a Windows-specific menu or dialog controller.

The pinned Skiko 0.144.6 source's `transparentWindowBackgroundHack` documents a
Windows OpenGL/software workaround using `DwmEnableBlurBehindWindow`. Its Windows
OpenGL redrawer receives the layer transparency flag when creating the GL context.
This is a diagnostic lead, not proof of a DWM or GPU-driver root cause. See upstream
[Skiko window utility](https://github.com/JetBrains/skiko/blob/master/skiko/src/awtMain/cpp/windows/window_util.cc)
and [SKIKO-1090](https://youtrack.jetbrains.com/issue/SKIKO-1090).

The existing real-window animation probe now accepts
`NAVIAMP_PROBE_POPUP_RENDER_API=OPENGL` or `SOFTWARE_FAST` on Windows. It changes
only Skia layers in native windows owned by the probe (including nested children).
The main window, raster presenter, global renderer default and released app are
untouched. It logs requested/actual popup backend, transparency, geometry and
renderer information. Unset means observe the original configuration.

Compare original OpenGL with software-rendered **owned popup windows**, retaining
OpenGL and the native compositor for the main window. Software uses the same
Windows transparency workaround, so it may also fail; that result is useful for
separating GL rendering from the window-composition path. Do not adopt software
rendering without visual, CPU, frame and compositor measurements. The diagnostic
explicitly excludes the already crashing Direct3D path.

The tooltip phase additionally compares its native window's padding corners with
the same screen pixels before opening. An opaque black border fails; a tooltip
outside the captured parent cannot count as a pass. Failing physical captures are
now retained before the visibility assertion, so a black result remains inspectable.
Tests cover the black-border rejection, valid transparent corners, capture bounds,
backend allowlist and exclusion of unrelated native windows.

## Reproduction

Use a visible, unlocked Windows desktop at fixed size, scale, refresh and power.
Use a separate process for each variant, close unrelated probes, and retain logs.
These are developer test processes: no provider, user profile or audio engine loads.

```powershell
$env:NAVIAMP_PROBE_INTEGRATED = 'true'
$env:NAVIAMP_PROBE_POPUPS = 'true'
$env:NAVIAMP_PROBE_VERIFY = 'true'
$env:NAVIAMP_PROBE_MENUS = 'true'
$env:NAVIAMP_PROBE_DIALOGS = 'false'
$env:NAVIAMP_PROBE_LIFECYCLE = 'true'
# First run without a popup override; then repeat with SOFTWARE_FAST.
Remove-Item Env:NAVIAMP_PROBE_POPUP_RENDER_API -ErrorAction SilentlyContinue
.\gradlew.bat :core:ui:playerAnimationProbe --console=plain
$env:NAVIAMP_PROBE_POPUP_RENDER_API = 'SOFTWARE_FAST'
.\gradlew.bat :core:ui:playerAnimationProbe --console=plain
```

For dialogs, swap MENUS/DIALOGS. For tooltip padding, set POPUPS/MENUS/DIALOGS to
`false`, TOOLTIPS to `true`, and LIFECYCLE to `false`. Run each backend variant.
The automated developer probe performs native input and physical pixel capture;
when operating through Computer Use, keep VERIFY disabled and use Computer Use for
screenshots/input. That observation-only run does not pass the automated pixel gates.
Keep the native window visible when collecting the existing CPU/parent-frame samples.
Use `scripts/animation-probe/measure-windows.ps1` for matched app/DWM GPU observations.

## Acceptance remains open

No application production code changes are included in this first investigation step.
Before a fix is accepted, repeat actual-app visual checks, nested popup/input/keyboard
and accessibility checks, resize/clipping, minimize/restore, and the static,
individual-animation and combined-animation matrix. Confirm visible motion and
collect process CPU, parent frames and relevant GPU/compositor cost under matched
conditions. The existing real-app budget is no continuous parent redraw stream and
at most one additional CPU percentage point for an overlay. Do not freeze animation,
reduce playback updates or relax the budget to declare a pass.

## Initial verification — October 23, 2026

Four focused JVM tests passed on macOS (zero failures/errors/skips), including the
AWT ownership check that excludes unrelated windows and native animation surfaces.
No production source changed; Android/iOS behavior and platform wiring are unchanged.

An isolated Windows launcher initially failed because the released trimmed runtime
omits java.management. This was a harness startup failure, not a rendering result.
Stopped only that test process. Downloaded official Temurin 21.0.12.1+1 for the probe
and verified SHA-256 `f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e`.
It is unpacked inside the probe directory; no system Java setting changed. The
probe references the released dependencies/native libraries, with current compiled
Core UI/domain and test classes. It is not the packaged production application.

Native Computer Use through Moonlight independently showed the original owned
OpenGL menu above an opaque black parent in the isolated fixture. The popup's
Skiko renderer log identifies NVIDIA Corporation / GeForce RTX 5060 Ti, transparency
true, native popup 984 x 701 at (56,79). Parent window 1000 x 740 at (48,48), scale
1.0, 60 Hz. Pixel verification and programmatic native input were disabled; the
visible image came from Computer Use. Its reported 0.156% CPU and zero parent
frames do **not** pass acceptance: the background is black and animation is hidden.
No GPU/compositor budget is claimed by this short diagnostic.

Host files: `naviamp-msix-323/popup-probe-325/original.stdout.log` and
`original.stderr.log`; the separate `run-popup-probe-325.ps1` starts the test with
a one-time, non-elevated interactive task. No music profile loads.

The software comparison logged `actual=SOFTWARE_FAST`, `transparent=true`, with the
main window still OPENGL. Its first short run ended before visual inspection, so it
was not counted as a pass. A longer observation run reproduced the same black parent
behind the menu in Computer Use. The prior logs were retained as `software-first.*`;
current logs are `software.stdout.log` / `software.stderr.log`. The renderer switch
occurs at WINDOW_OPENED, after the window's initial OpenGL setup. This rejects a
simple live per-popup renderer swap; it does not prove that software selected before
native window creation would behave identically, or establish the exact root cause.
Both runs leave the intended parent animation hidden and fail visual acceptance.

Next: examine the native transparency/initial-window setup and compare a renderer
selected before creation, using the isolated fixture. Avoid expanding product policy
or altering user driver/system settings on the strength of this result. Any resulting
fix must retain native cached animation and meet the full matrix above.

Both probe processes exited and both one-time scheduled tasks were removed. Logs
were copied to the worktree's ignored `build/verification-325` directory; the
isolated Windows JDK/fixture remain available for follow-up. The original standalone
application remains open. No released files, installed package, certificate trust,
user profile or driver preferences were changed by this investigation.

## Candidate fix: select the surface renderer before creation

Software selected before native window creation preserved the patterned parent below
the menu; the late per-popup switch did not. Its original three ten-second intervals
reported 0.468%, 0.156%, 0.624% of one CPU core, zero parent frames. The popup and main
surface both logged SOFTWARE_FAST. This supports an initialization-sensitive
OpenGL/transparent-window failure on this machine; it does not establish a precise
Skiko/driver bug.

The Windows packaging default now selects `SOFTWARE_FAST` before JVM UI startup.
Owned popup windows remain enabled. Shared cached motion still uses the existing
DirectComposition presenter, and native visualizers keep their OpenGL option. No
shared menu, tooltip, animation cadence, native compositor or user setting changes.
The explicit diagnostic Gradle override remains available. Distributable verification
checks the actual launcher renderer and native visualizer options. The probe matches
the package default and can explicitly reject a mismatched expected main renderer.

The first attempted longer matrix and a subsequent motion run accidentally selected
OPENGL because an external PowerShell harness compared a string with an array.
Their low CPU/GPU numbers are **rejected candidate evidence**; black pixels hid the
background. Their logs are retained with `rejected-opengl-` prefixes on the host.
The harness now uses explicit membership/conditions, and the repository probe checks
`NAVIAMP_PROBE_EXPECT_RENDER_API` before timing. Do not use the earlier counter
summary in place of the corrected SOFTWARE_FAST matrix below.

In the corrected `candidate-motion` run, native Computer Use observations at two
different times showed the three title rows scrolling and waveform reveal advancing
while the menu remained open. Both main and popup logged SOFTWARE_FAST. Parent
content remained visible and menu shadows did not become black rectangles.

### Shared checks and packaging configuration

29 targeted JVM tests passed: 22 raster-interaction, three tooltip-hover and four
popup-diagnostic tests. Android shared UI, iOS Arm64, iOS simulator Arm64 compilation
and `verifyCoreFirstArchitecture` passed. The backend guard compiled and its focused
four tests passed separately. Gradle release-version metadata/configuration passed.
The branch's existing VERSION is v2.10.1/build 59; it is unchanged by this work. The
issue remains scoped to milestone v2.10.0 and integration into the selected release
candidate must be reconciled through the release workflow; no tag/package publication
is authorized or performed here.

The existing Windows idle CI check counted only the launcher. It now includes its
child process family, rejects a changed family/missing CPU counter, and builds through
distributable verification. The existing CI CPU limit is unchanged; it is a coarse
regression gate, not the stricter visible-animation budget. Native read-only validation
found both known released PIDs 6900 and 9696, exactly matching the helper's result;
PowerShell parsing reported zero errors. No user process was modified by that check.

### Actual application checkpoint

An isolated copy of the unchanged v2.9.0 app references the original release libraries
with SOFTWARE_FAST selected before launch. Its APPDATA, LOCALAPPDATA and user.home
point inside the test directory, with the development profile enabled. A loopback-only
fixture provides short/long identities and synthetic WAV audio; no personal server or
profile loads. The instrumented JVM uses the verified separate Temurin JDK so management
modules are available. This is not a rebuilt 2.10.0 package or its trimmed runtime.

The real app connected through keyboard traversal, loaded the fixture and played its
short track. The Home Refresh menu rendered above a visible background and Escape
dismissed it. Space paused playback and the window could switch split/full player
layouts. With the short track paused, the attached observer reported 0.156% CPU,
zero parent frames, and confirmed the observer remained attached on the visible
2560 x 1369 surface. The separate five-sample interval reported 0.246% CPU and zero
app/DWM GPU engine sum at counter resolution. Independent Computer Use showed the
paused surface. These checks do not certify the playing-overlay delta.

The agent supports `-Dnaviamp.probe.capture=false`, used here so all physical
observations/input use Computer Use. `physical_captures=false/false` correctly records
that automated pixel gates were not run. Moonlight keyboard works; pointer actions
return `noWindowsAvailable`. On October 23 the owner opened an actual menu and
hovered an icon in this isolated application and replied “Looks good,” confirming
the visible parent and absence of the black tooltip border. This is manual owner
acceptance of those two visual defects, not an automated tooltip pixel pass.

### Corrected native matrix

Both main/popup report SOFTWARE_FAST and the expected-backend guard passed.
Windows probe: 1000 x 740 (984 x 701 client), scale 1.0, 60 Hz, Balanced power plan;
resize to 1040 x 760. Other app windows were idle. Ten-second one-core CPU intervals:

| State | CPU | Parent frames |
| --- | ---: | ---: |
| static | 0.312% | 0 |
| marquee | 0.156% | 0 |
| waveform | 0.468% | 0 |
| combined | 0.156% | 0 |
| popup-combined | 0.624% | 0 |
| restored-combined | 0.312% | 0 |
| paused | 0.156% | 0 |
| hidden-combined | 0.624% | 0 |
| restored-combined | 0.000% | 0 |
| resized-combined | 0.312% | 0 |

The restored rows are after menu dismissal and after minimize, respectively.
The hidden state is a lifecycle/counter check, never a visual pass. Native lifecycle
assertions confirmed minimize and hidden owned windows. Motion was independently
observed in the separate held-menu and modal runs and the restored combined state;
not every ten-second phase received its own physical image pair.

Application GPU engine-sum observations were zero at counter resolution in the
corrected matrix. DWM means were about 1.143 combined, 1.139 menu-open and 1.140
restored, with static/paused/hidden near zero. DWM is whole-desktop activity; engine
sums are not total GPU capacity percentages or application-attributed energy. The
separate modal three-state run measured 0.000% / 0.312% / 0.624% CPU and zero parent
frames for combined/open/restored. Its external six-second restored subinterval was
1.031% CPU: retain that shorter-interval outlier rather than claim uniform sub-1%
behavior. Matched modal DWM engine-sum means stayed around 1.152.

Computer Use showed the modal's intended translucent scrim with the patterned parent
and moving titles/progress beneath it; Escape dismissed it and restored the parent.
Held visual runs containing keyboard input are diagnostics, not replacement steady
CPU samples. The tooltip fixture showed text, rounded surface and normal shadow
without a black rectangle. This is a direct surface fixture; the separate actual
application menu/hover check was subsequently confirmed by the owner above.

Raw files are under `naviamp-msix-323/popup-probe-325` and local ignored
`build/verification-325/candidate`. Corrected files use `candidate-menu`,
`candidate-motion`, `candidate-dialog`, `candidate-dialog-visual` and
`candidate-tooltip-visual`; rejected OpenGL files are explicitly prefixed.

Initial PR CI failed the new AWT ownership test on the headless shared runner
(HeadlessException). It now follows the existing native-window test policy: require
a native window server for that single test. Headless retest: three diagnostic tests
passed, one native-window test skipped, zero failures/errors. The earlier local
window-server run passed all four. The initial Windows, macOS, Linux and both Android emulator jobs passed; iOS was
still running at that checkpoint. At d8c9caacd, shared/Android, Windows, macOS, Linux
and both Android emulator jobs passed. The iOS mutual-TLS playback test timed out
(14 of 15 native tests passed); one failed-job rerun was requested. Do not classify
that timeout as unrelated or passed without the rerun result.

No Android/Desktop/iOS production source files changed. Production differences are
Windows packaging JVM options and distributable validation, justified by native
Skiko/AWT surface initialization. Shared product/animation behavior is unchanged.
Full actual-app playing-overlay CPU/frame comparison, remaining input/reader
checks, native visualizer coverage and the final selected 2.10.0 package/runtime
remain unverified; keep #325 and PR #326 open/draft until these gates are resolved.


### Actual playback measurement follow-up

Owner manual menu/hover confirmation is recorded above. A playback-start sample
reported 3.281% CPU, 92 ms compilation and two parent frames; its separate six-second
counter interval reported 1.725% CPU. This includes startup activity, so it is not
a steady-state acceptance result. The next interval reported zero observer CPU and
parent frames, but the following Computer Use image showed playback paused at 0:25;
reject it as proof of moving playback. Retain these logs as diagnostics. The owner
has been asked to leave playback and the menu open for a valid comparison.
