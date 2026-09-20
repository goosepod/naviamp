# Windows cached player compositor (#114)

Issue: https://github.com/goosepod/naviamp/issues/114

## Cause and implementation

Windows selected the shared Compose fallback by default. Both scrolling metadata and smooth
waveform progress therefore repainted the entire application surface at approximately 60 Hz.
The opt-in transparent Skia windows isolated parent redraws but substantially increased CPU.

Core continues to rasterize content, define motion, own visibility policy, handle input and expose
semantics. `NaviampRasterPlacement` converts shared timelines into absolute translation and clip
properties; `NaviampRasterSceneClock` preserves their start time through layout-only updates.
The Windows adapter uploads changed PNGs to cached DirectComposition surfaces and submits linear
keyframes. There is no application rendering callback or timer for each animation frame.

DirectComposition targets the existing Skia canvas HWND. It creates no input window. Owned Compose
popup HWNDs stack above that target. The shared capability `contentBelowOwnedWindows` allows those
presenters to remain attached while tooltips/menus appear; other presenters retain their previous
visibility policy. Hiding the application still disposes native presentation. This capability
addresses the user's intermittent blinking observation in the first review build.

Microsoft references:
- [DirectComposition device creation](https://learn.microsoft.com/en-us/windows/win32/api/dcomp/nf-dcomp-dcompositioncreatedevice2)
- [Surface update lifetime](https://learn.microsoft.com/en-us/windows/win32/api/dcomp/nf-dcomp-idcompositionsurface-begindraw)
- [Compositor repeat segments](https://learn.microsoft.com/en-us/windows/win32/api/dcompanimation/nf-dcompanimation-idcompositionanimation-addrepeat)

## Measurements

CPU percentages are fractions of one core (100% = one core). The synthetic fixture uses a visible
1000 x 740 dp window, 984 x 701 OpenGL parent surface, 150 static library rows, five-second warm-up
and ten-second samples. Workstation display: 2560 x 1440, 60 Hz, AMD Radeon Graphics, existing custom
power plan unchanged. Measurements are diagnostic workstation samples, not universal guarantees.

| Renderer | Static CPU | Text CPU | Waveform CPU | Combined CPU | Parent frames per animated sample |
| --- | ---: | ---: | ---: | ---: | ---: |
| Default fallback | 2.03% | 64.32% | 59.77% | 65.84% | 599–601 |
| Forced Skia windows | 0.00% | 174.66% | 56.49% | 280.83% | 0 |
| First DirectComposition run | 0.47% | 0.16% | 0.47% | 1.09% | 0 |
| Popup-retention verification | 11.86% | 6.71% | 3.28% | 4.06% | 0 |
| Capture primed before warm-up | 34.67% | 28.72% | 26.23% | 7.18% | 0 |

The first compositor screenshots showed actual text/waveform content; pixel changes were 7,777
for marquee, 176 for waveform, and 7,934 combined. A later run was obscured by the always-on-top
review app and is rejected. The strengthened probe checks occlusion, independently checks both
moving elements, checks unchanged sibling pixels, tests popup transitions and exits nonzero on
failure. Its verification mode keeps the probe on top and excludes the external cursor halo from
pixel comparisons. The popup-retention run independently observed text and waveform movement,
zero sibling pixel changes and 12 successful popup visibility samples. Static CPU varied during
startup; the probe now primes screen capture before the warm-up interval so first-use capture/JIT
work does not start at the beginning of its CPU sample. Priming did not resolve the variation:
the final run still had high early samples, while independently verifying both moving elements,
zero sibling changes and all 12 popup transitions. Do not use this run as evidence of low static
CPU. The performance issue remains open for the variation and tooltip cost described below.

Real application, same narrow player window, same track (`13 Women`), Standard font settings,
OpenGL backend and unchanged power/display conditions, with compilation finished:

| Build | App CPU | DWM CPU | App GPU engine sum | DWM GPU engine sum |
| --- | ---: | ---: | ---: | ---: |
| Original review build | 63.96% | 32.18% | 2.33 | 13.13 |
| First compositor review build | 7.48% | 31.19% | 0.061 | 12.75 |

GPU figures are means of 15 one-second engine-utilization samples summed by process; they are not
percentages of whole-GPU capacity. CPU intervals include performance-counter initialization
(approximately 22 seconds). DWM is system-wide and includes other visible applications. Track
positions differ within the same track; these are matched layout/playback samples, not exact-frame
A/B replay. The app CPU reduction is approximately 88%, without an observed DWM/GPU increase.

An initial long-title playback sample was 18.34% app CPU, 39.31% DWM CPU, 0.128 app GPU engine sum,
13.01 DWM GPU engine sum. This preceded the popup-lifetime correction and remains recorded rather
than being omitted.

The final combined review build includes #25, #112 and the popup-retention correction. Same narrow
306 x 714 window, Standard fonts, OpenGL, display and power conditions:

| State | App CPU | DWM CPU | App GPU engine sum | DWM GPU engine sum |
| --- | ---: | ---: | ---: | ---: |
| Paused, fitting title after launch | 4.29% | 31.25% | 0 | 12.42 |
| Long title and waveform playing | 7.90% | 32.04% | 0.059 | 12.44 |
| Paused and minimized | 0.59% | 32.96% | 0 | 12.39 |
| Restored, paused with title scrolling | 7.62% | 33.94% | 0 | 12.90 |
| Paused fitting title, control tooltip visible | 65.32% | 36.70% | 0.610 | 12.55 |
| Same paused fitting title, pointer moved away | 7.99% | 31.73% | 0 | 12.72 |

The long-title track was `You & Me & the Bottle Makes 3 Tonight (Baby)`; fitting-title samples
used `Mr. Pinstripe Suit`. The 7.90% final sample is not a same-track replay of the original
63.96% baseline. The matched-track comparison above remains the stronger before/after evidence.
Minimize/restore, maximized/narrow resize, track changes, menu stacking and dismissal passed visual
checks. Both title movement and waveform advancement were visible; the user reported no scrolling
wobble. These point-in-time observations do not exclude every transient blink.

**Acceptance remains open:** the tooltip-associated spike needs a reproducible isolated profile
and correction, and static CPU variation needs explanation. A 15-second paused thread sample
attributed 469 ms to AWT-Windows, 344 ms to an unidentified native thread, and 109 ms to C2 compilation;
it did not establish a root cause. The packaged runtime lacks `jdk.jfr`, so its attempted JFR
recording could not start. Do not claim this build meets the full performance gate merely because
steady playback and zero-parent-redraw checks improved. No animation or playback-update frequency
was reduced.

## Reproduction

### Tooltip follow-up

The isolated static popup did not reproduce the control-hover spike: static, popup visible and
dismissed samples were 6.71%, 7.03% and 8.27% of one core, with zero parent redraws. Recording the
real player under the full JDK reproduced 49.86% CPU, with native popup creation, resizing and
OpenGL-context creation recurring while the pointer stayed on a player control. This is popup
lifetime churn, rather than continuous repainting by the cached waveform/title timelines.

The hover implementation now lives in common code. A visible tooltip survives a brief pointer
transfer (100 ms dismissal grace), re-entry cancels dismissal without restarting the show delay,
and leaving before the 450 ms show delay cancels the pending tooltip. The grace timer runs only
on a pointer transition; there is no continuous polling. Touch does not trigger hover tooltips.
The three platform tooltip implementations are removed: none required a platform API.

`NAVIAMP_PROBE_TOOLTIPS=true` measures static popup cost; `NAVIAMP_PROBE_HOVER=true` adds an actual
hover target to the fixture. Hover that target; the probe waits for the tooltip, warms up for five
seconds, then measures 30 seconds. Leave the pointer there. It requires a visible owned tooltip and zero native
window open/close events during the sample. `NAVIAMP_PROBE_JFR=<absolute recording path>` enables
JFR on the probe JVM, without requiring a recording module in the packaged app runtime.

The uninterrupted hover probe passed at 0.57% of one core, zero parent frames, zero native window
open/close events, unchanged sibling pixels and a visible tooltip throughout the 30-second sample.
All 12 subsequent popup visibility checks passed. Earlier instrumented runs recorded one initial
opening, or one close/reopen during inspection; they are not passing sustained-hover evidence.
Seventeen targeted tests passed, including three new common hover-state regressions. Common
metadata, Android, JVM and iOS arm64 compilation passed after the shared extraction.
Combined-app verification remains to be recorded below.

### Animation and GPU probes

Build native resources before running the production-integrated probe:

```powershell
$env:CMAKE_EXE = '<installed cmake.exe>'
$env:NAVIAMP_PROBE_INTEGRATED = 'true'
$env:NAVIAMP_PROBE_VERIFY = 'true'
$env:SKIKO_RENDER_API = 'OPENGL'
.\gradlew.bat :platforms:desktop:copyDesktopVisualizerOpenGlResources :core:ui:playerAnimationProbe
```

Keep the left fixture and static sibling rows unobscured. Screenshots are written under
`core/ui/build/animation-probe`. Verification pixel bands currently target the fixture at 1x scale.
For CPU/GPU sampling of the visible app, pass its actual launcher/JVM process IDs:

```powershell
.\scripts\animation-probe\measure-windows.ps1 -ApplicationProcessIds <ids> -SampleCount 15
```

Fourteen targeted tests on the isolated Windows branch (16 on the combined review branch) cover
shared motion, pixel reuse, input, keyboard/accessibility actions,
visibility, popup retention, clipping properties and timeline continuity. Common metadata, Android,
JVM and iOS arm64 Kotlin compilation passed on this host; native iOS execution was not performed.

## Platform boundary audit

- `core/ui/src/jvmMain/kotlin/app/naviamp/ui/DesktopRasterPresenter.kt`: selects available JNI
  presentation and configures AWT-owned popup windows for native HWND stacking.
- `core/ui/src/jvmMain/kotlin/app/naviamp/ui/WindowsRasterPresenter.kt`: converts bitmap/timeline
  arguments to JNI, binds the existing AWT canvas and owns native handles on the AWT event thread.
- `native/visualizer-opengl/src/naviamp_raster_windows.cpp`: uses JAWT, HWND, D3D11, Direct2D, WIC and
  DirectComposition APIs for cached surfaces, clips, timelines and transactional presentation.

No Android or iOS production adapter changes. Linux rendering is outside this Windows issue.
No Play Console operation, release, signing change or application data reset was performed.
