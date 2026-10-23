# Windows popup transparency investigation (#325)

Release target: **v2.10.0**, [issue #325](https://github.com/goosepod/naviamp/issues/325).
The revised candidate preserves the OPENGL primary constructor and selects SOFTWARE_FAST for future popup constructors.
Manual acceptance of the revised actual v2.10.0/build 60 package is complete.
Exact-source cross-platform CI and merge status are tracked on
[main fix #326](https://github.com/goosepod/naviamp/pull/326) and
[release integration #327](https://github.com/goosepod/naviamp/pull/327).
Production heads: `69043432094e8b9e00ea5b01a445ef822077e9f6` and
`727542258ecba425d2fc9694c65a584e330d4499`; corresponding Verify runs
[40021038596](https://github.com/goosepod/naviamp/actions/runs/40021038596) and
[40021020247](https://github.com/goosepod/naviamp/actions/runs/40021020247).
The detailed follow-up below records the completed measurements and stock-launcher
smoke test, including unverified spoken screen-reader output and rejected attempts.
Pending statements in older checkpoints are historical, not the current manual-test
status. Existing visualizer performance remains tracked separately in
[#241](https://github.com/goosepod/naviamp/issues/241).

The successful Windows CI job also retained the selected candidate’s release ZIP.
Its SHA-256 is `cabbecc0f837a9d687dbe0667e9245895631652dade64d6f2635b04aa6ab136c`;
archive integrity passed, the original launcher selects OPENGL primary and SOFTWARE_FAST
popup constructors, and its Temurin 21.0.12.1 runtime includes jdk.accessibility.
This is the CI-built artifact, distinct from the Sunshine-built ZIP measured below;
no claim is made that different build environments produce byte-identical archives.

The original v2.9.0 Store submission remains unchanged and is in certification as of
October 23, 2026. This record does not publish or approve a release tag.

## Revised candidate — October 23, later checkpoint

The earlier **global SOFTWARE_FAST main-window configuration is rejected** as the
shipping fix. On the selected v2.10.0/build 60 payload, Audio Sphere used 95.620%
of one core and 694 parent frames/10 s. It is a Skia shader fallback, not a native
GLSL preset (`shaderDefinition=false`). Merely changing the already-created main
layer to OPENGL still used 95.150%; an OPENGL enum alone does not establish hardware
initialization. Original all-OpenGL startup identified NVIDIA RTX 5060 Ti and used
29.217% with 599 frames for this visualizer. That existing visualizer cost is not
small-animation acceptance, and the software regression must not ship.

The replacement keeps the original **OPENGL primary-window construction**, then
selects **SOFTWARE_FAST for future popup constructors** in the existing native
raster host, before shared content can create a popup. Pinned Skiko 0.144.6 reads
the default for each layer constructor. There is no live renderer switch, change
to animation cadence, or platform-specific menu/tooltip behavior. Explicit global
Gradle renderer overrides retain their diagnostic behavior; an independent
`naviamp.windows.popupRenderApi` override is available.

Native production file: `core/ui/src/jvmMain/kotlin/app/naviamp/ui/DesktopRasterPresenter.kt`.
Its exact boundary is AWT event-thread window construction and Skiko renderer
initialization; this cannot compile in common Kotlin. It verifies an opaque primary
AWT window and existing Skiko layer, then sets only the future-layer default.
Android/iOS production files are unchanged. Four native constructor/guard tests
verify that existing layers retain their backend, new layers capture the new one,
owned popups cannot initialize it, missing primary layers fail, and absent/default
configuration has no effect. All 46 focused native-window/shared popup, keyboard,
semantics and raster tests passed locally; architecture and packaging input checks
passed. The first test attempt correctly exposed SOFTWARE_FAST as unsupported on
macOS; non-Windows constructor tests now use SOFTWARE_COMPAT and Windows retains
its exact SOFTWARE_FAST assertion.

An actual release ZIP was built with the installed Sunshine MSVC/Windows SDK/CMake
and Temurin 21 toolchain, from the selected candidate plus this change:
`Naviamp-v2.10.0-windows-x64-release.zip`, SHA-256
`634c9474b241093fb850d9d200faaa8a5d72c7eaabd84b59bcf53ec9c94862a3`.
Its unchanged trimmed runtime includes jdk.accessibility. A disposable launcher
wrapper adds the test observer without substituting the runtime or production
JARs; captures remain disabled and Computer Use supplies visual observations.
ProcessHandle supplies CPU from java.base; compilation time is explicitly unavailable
in this trimmed runtime. The original launcher configuration is preserved separately.
The observer records actual backend/vendor and will not overwrite a completed
sample after restart.

At 934 x 601 client pixels, the real window reports OPENGL/NVIDIA and the owned
menu reports SOFTWARE_FAST. Combined scrolling title/progress: 1.562% CPU, zero
parent frames; menu sample: 0.781%, zero parent/owned frames; restored sample:
1.094%, zero parent frames. External intervals reported 2.151/0.955/1.201% CPU,
app GPU engine sums 0.01269/0.01086/0.00991, DWM engine sums
1.12482/1.11403/1.13462. These are individual observations; confirm a held menu
for the entire matched interval before accepting the final overlay comparison.
DWM process CPU (0.306/5.829/6.465%) is whole-desktop streaming activity, not app
attribution. Native screenshots show scrolling text and advancing progress; a
constant-tone fixture produces a uniformly tall waveform, not an alpha defect.

The owner checked this revised package and reported: **“Tooltips and popup menus
look good.”** This supersedes the earlier owner check of the 2.9.0 diagnostic copy.
Test playback is kept around 3% volume. The fixture now supplies its complete WAV
immediately (`--burst-seconds 600`) because the recovery fixture's default throttling
made late cached seeks wait for buffering. Copied-profile/stalled startup attempts
are diagnostics, not motion/performance passes.

The preceding release-candidate CI matrix passed all platforms, but it covered the
superseded global-software configuration. The revised source still requires its own
CI, remaining visualizer/lifecycle measurements and original-launcher smoke check.
Keep both PRs draft until those gates pass. Historical sections below retain their
original scope; they are not acceptance of the revised configuration.


### Revised-package follow-up measurements

The held Track actions menu remained painted through Computer Use observations at
0:05, 0:15 and 0:25; title position and progress changed beneath it. Its ten-second
process CPU was 0.625%, parent frames zero; external CPU 0.480%, app GPU engine sum
0.01212, DWM engine sum 1.11546. Native constructor defaults identified the parent
as NVIDIA/OpenGL and menu as SOFTWARE_FAST.

Audio Sphere (Skia fallback) measured 25.778% CPU with 598 parent frames/10 s;
external CPU 28.060%, app GPU engine sum 1.35989. This is comparable to the unchanged
OpenGL startup’s 29.217% / 599 frames, avoiding the rejected software regression.
Analog Signal Failure explicitly logged `available=true`, `libraryAvailable=true`,
`shaderDefinition=true`, `shaderTranslates=true` and visibly moved. Closed/open-menu
CPU was 57.028/47.493%, parent frames 585/601; external CPU 54.836/47.463%, app GPU
engine sums 3.10274/2.64016, DWM sums 1.43214/1.77680. The menu remained painted while
its image animated. These visualizer samples are functional/regression evidence,
not claims that visualizer rendering meets the cheap text/progress budget.

Paused: 0.000% CPU and parent frames zero (external CPU/app GPU zero). Minimized
while playing: 0.625%, parent frames zero, external CPU 0.481%, app GPU zero;
Computer Use confirmed the candidate window was absent. Restoring showed progress
at 3:14, then 3:45, with moving/clipped metadata and intact content: 1.094%, zero
parent frames; external CPU 1.197%, app GPU sum 0.01684. Maximize/restore preserved
rendering and clipping. Maximized 2560 x 1369 sample: 2.656% CPU, zero parent frames;
external CPU 1.202%, app GPU sum 0.01183. Window size differs, so it is not a matched
small-window CPU comparison. The larger surface is retained as lifecycle evidence.
Nested Track actions/artist submenu rendered correctly. A later keyboard action
started fixture radio and returned no additional tracks; this is not modal/input
acceptance. The fixture profile is disposable and the original application is untouched.

The first attempt to run the revised synthetic matrix reused the SSH session’s
Gradle daemon. The window was invisible and DirectComposition returned access denied,
with excessive CPU/redraws. Reject the entire run (`matrix-current-325.log`). Rerun
uses `--no-daemon` in the interactive scheduled task, with Computer Use visibility
checks and automated Robot pixel/input actions disabled. The corrected interactive matrix passed: static/marquee/waveform/combined/menu/
restored/paused/hidden/restored/resized CPU was respectively
0.000/0.156/0.312/0.312/0.780/0.625/0.000/0.000/0.000/0.780% of one core,
with zero parent frames in every ten-second sample. Main layer was OPENGL;
menu constructor was SOFTWARE_FAST. Baseline bounds 1000 x 740 (client 984 x 701),
scale 1, 60 Hz, Balanced power/AC; resize 1040 x 760. Computer Use observed marquee,
waveform, combined motion, the painted menu, restored animation and resize/clipping.
Not every individual measured phase has its own physical before/after image pair;
automated pixel/input actions stayed disabled. The real-app held-menu observation
supplies an independent paired overlay-motion check.

External per-phase CPU was 0.000/0.206/0.244/0.000/0.000/0.773/0.000/0.000/0.000/0.815%
over 6.0–7.7 seconds. App GPU engine sums were zero; DWM engine sums for moving
states were 1.03784/1.11775/1.12444/1.12145/1.11679/1.12173/1.13739; static/paused
were zero, minimized 0.03738. This attribution does not mean cached animation has
no compositor cost. Read-only external observations include whole-desktop streaming.
The hidden phase verified ICONIFIED, no visible owned raster windows and no parent
redraws. Logs: `matrix-interactive-325.log` and indexed GPU JSON on Sunshine;
local collected record `/private/tmp/naviamp-325-collect-interactive.log`.

Matching modal closed/open/restored CPU: 0.000/0.156/0.156%, zero parent frames;
external CPU 0.000/0.245/0.000%, app GPU zero, DWM engine sums
1.09558/1.09168/1.10396. Computer Use image pairs show the modal and scrim remaining
painted while text and waveform move beneath them. Log `modal-interactive-325.log`,
local `/private/tmp/naviamp-325-collect-modal.log`. Both Gradle probe runs succeeded.
Reproduce on an interactive Windows desktop using the current source:
`gradlew.bat --no-daemon :core:ui:playerAnimationProbe` with
`NAVIAMP_PROBE_INTEGRATED=true`, `NAVIAMP_PROBE_EXPECT_RENDER_API=OPENGL`,
`NAVIAMP_PROBE_VERIFY=false`, `NAVIAMP_PROBE_POPUPS=true`,
`NAVIAMP_PROBE_MENUS=true`, `NAVIAMP_PROBE_LIFECYCLE=true`.
For the modal comparison set MENUS=false, DIALOGS=true, LIFECYCLE=false and
PHASES=combined,popup-combined,restored-combined. Use Computer Use to observe actual
motion and capture GPU/compositor intervals as above; CPU alone is insufficient.

Finally restored the package’s original launcher configuration, removing the observer
main-class wrapper and extra probe classpath. The original trimmed runtime and jars
started successfully and resumed playback with the same approximately 3% volume.
Computer Use showed the actual Cast modal with a translucent scrim and moving
metadata/progress beneath it, then successful Escape dismissal and restored content.
An incidental firewall prompt was canceled without granting additional access; no
cast session was started. Keyboard focus/activation of existing controls was observed,
but tab order differs with enabled controls and is not a fixed-count assertion.
The disposable app and loopback fixture were then stopped; fixture port 18080 was
confirmed closed. Original user app/profile remain untouched.

Shared popup semantics/keyboard tests passed in the focused native-window suite.
The trimmed package includes jdk.accessibility. Spoken Narrator output was not
verified through Moonlight, and no claim of comprehensive screen-reader acceptance
is made. This native-constructor fix changes no shared semantics, labels or input
policy; full accessibility investigation remains separate from the rendering defect.
Revised-source CI was pending when these manual measurements finished; use the exact-source run links above for its final outcome.

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
Actual-app long-title/combined coverage, remaining reader
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


### Owner-prepared real playback/menu comparison

After the owner replied ready, Computer Use showed the actual full-player
Short fixture playing with Track actions open. Same 2560 x 1369 client, scale,
refresh and power conditions as its paused checkpoint; no resize or playback
change between menu and restored samples. The visible position advanced from
0:53 to 1:15 behind the menu and from 1:16 to 1:45 after Escape dismissed it.
The title fits and does not scroll; this tests real progress playback, not the
long-title plus progress case.

| State | Observer CPU (10 s, one core) | Parent frames | External CPU (~6.3 s) | App GPU engine sum | DWM engine sum |
| --- | ---: | ---: | ---: | ---: | ---: |
| playing, menu open | 0.156% | 0 | 0.494% | 0.01171 | 1.19352 |
| playing, menu dismissed | 0.625% | 0 | 0.987% | 0.01127 | 1.19560 |

Both observers remained attached; owned surface frames were also zero. The
restored sample includes a 99 x 50 hover window (Track actions tooltip), confirmed
visually afterward with normal rounded padding/shadow. This is a menu-versus-tooltip
comparison rather than a perfectly overlay-free baseline. No menu CPU increase
was observed, and the unchanged parent remained visible with moving progress.
DWM process CPU was 5.242%/6.156%; this is whole-desktop work while streaming,
not application attribution. Retain it alongside the engine counters rather
than omit that compositor cost.

Return reopened Track actions, Tab traversed its items, and selecting Change
visualizer opened the nested preset menu without obscuring the parent. Selecting
a preset dismissed it correctly. The artwork region did not visibly demonstrate
a moving native visualizer, so this is only nested menu/input coverage, not a
visualizer-rendering pass. Screen-reader coverage remains unverified.

Raw logs: real-app/observer/playing-short-menu and playing-short-restored, plus
their GPU JSON files. Automated pixel gates remained disabled. This extends the
unchanged 2.9.0 payload/configuration checkpoint; the selected release payload
and trimmed runtime must still be tested before shipping the fix.
