# Windows popup transparency investigation (#325)

Release target: **v2.10.0**, [issue #325](https://github.com/goosepod/naviamp/issues/325).
This is an investigation, not an accepted fix. The original v2.9.0 Store submission
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
