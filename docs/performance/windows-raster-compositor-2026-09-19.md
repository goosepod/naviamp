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

The first compositor screenshots showed actual text/waveform content; pixel changes were 7,777
for marquee, 176 for waveform, and 7,934 combined. A later run was obscured by the always-on-top
review app and is rejected. The strengthened probe checks occlusion, independently checks both
moving elements, checks unchanged sibling pixels, tests popup transitions and exits nonzero on
failure. Its verification mode keeps the probe on top and excludes the external cursor halo from
pixel comparisons. The popup-retention run independently observed text and waveform movement,
zero sibling pixel changes and 12 successful popup visibility samples. Static CPU varied during
startup; the probe now primes screen capture before the warm-up interval so first-use capture/JIT
work does not start at the beginning of its CPU sample. Final real-app idle measurements remain
the idle acceptance gate.

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
than being omitted. Final popup/lifecycle and combined-app verification is recorded below when run.

## Reproduction

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

Fourteen targeted tests cover shared motion, pixel reuse, input, keyboard/accessibility actions,
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
