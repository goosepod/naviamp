# Linux X11 raster alpha regression (#153)

## Scope and status

Issue [#153](https://github.com/goosepod/naviamp/issues/153) concerns opaque rectangles in the
official v2.7.1 Linux DEB on XFCE X11. The issue branch corrects the native presentation boundary;
Core continues to own cached pixels, motion, clipping, popup and visibility policy, input, and
accessibility. Keep the issue open until the final packaged application and the cross-platform
verification matrix satisfy its acceptance criteria. No release is tagged by this work.

## Reproduction and cause

The official `Naviamp-v2.7.1-linux-x64.deb` was downloaded from its GitHub release and extracted
under `/tmp`, without replacing the installed application. An isolated profile connected to the
credential-free loopback Subsonic fixture. Now Playing showed black rectangles behind its title
and progress area. The same package with `NAVIAMP_RASTER_FORCE_SKIA=true` removed those rectangles.
Both runs reported `Cannot create Linux GL context` and used software rendering. The Home pane's
appearance is not, by itself, evidence of a separate native defect.

The unchanged integrated probe reproduced the same failure. A known raster over a two-color parent
returned `ffffff` for white, `800000` for half-transparent red, and `000000` for transparent pixels.
The expected latter values are `923456` and `ac6824`. The strengthened regression rejected this
surface despite its nonblank text and zero parent redraws.

X11 child-window redirection is not an alpha-blending operation over an opaque parent. X.Org's
[automatic update implementation](https://github.com/mirror/xserver/blob/master/composite/compwindow.c)
uses `PictOpSrc`. Copying ARGB child pixels through a 24-bit RGB parent loses their alpha before
the desktop compositor sees them. A minimal visible-window experiment confirmed that changing
the parent to ARGB32 preserves the expected blending.

## Native boundary change

- `LinuxRasterPresenter` attaches the existing cached X11 pixmaps to an adapter-owned, transparent
  AWT window instead of the opaque Compose canvas. Its popup window type avoids an XFCE frame
  whose input region otherwise intercepts clicks. Both the native client and raster children have
  empty X Shape input regions. The AWT window is nonfocusable and owned by the application.
  Explicit X11 stacking places it immediately above the owner's real frame, so it is covered
  with the application and returns when its owner is raised.
- The JNI adapter queries the parent's actual XRender format and requires the exact premultiplied
  ARGB32 channel layout. It retains the compositing-manager and Shape checks. Unsupported native
  presentation declines, leaving the existing shared Compose fallback visible and animated.
- Native integral window geometry is cached. Identical moves and resizes are not resent to X11;
  Core's timelines and the existing animation cadence are unchanged.
- The Desktop host forwards Compose Desktop's native `WindowState` to the shared visibility owner.
  AWT emitted no restore notification in the programmatic minimize/restore reproduction. Reading
  `WindowState` prevents a restored window from remaining in the hidden, frozen presentation state.
- Native raster windows cannot guarantee stacking below every owned popup. The existing shared
  popup fallback handles that case rather than adding a second popup policy to the adapter.

## Probe and measurement conditions

Run on a visible X11 desktop:

```sh
scripts/animation-probe/measure-linux.sh
```

The script records display size, refresh rate, scale, renderer, compositing configuration, and
available power/governor facts in `build/linux-animation-probe/environment.txt`. It runs three
trials by default. Each state has five seconds of warm-up followed by ten seconds of measurement.
On XFCE it temporarily inhibits the screensaver and releases that inhibitor on exit; saved desktop
blanking and power settings are not changed. A blank or obscured art sample fails the probe.

This workstation is an Ubuntu x86-64 virtual machine using XFCE/X11, compositing enabled,
1280 × 800 at 74.99 Hz, and 96 DPI (scale 1). Probe windows are 1000 × 740 logical pixels for
the comparable static, marquee, waveform, combined, paused, minimized, and restored states.
The separate move/resize check uses a 1040 × 760 window. JDK is 21.0.12.1. GLX reports virgl
(Mesa Intel Graphics ADL-N), Mesa 26.0.8, accelerated; Skiko nevertheless cannot create its GL
context and the actual parent surface reports `SOFTWARE_FAST`. Guest GPU energy/utilization
counters and a CPU scaling governor are not exposed. Compositor process CPU is recorded instead;
it includes the rest of the desktop, not just Naviamp.

The probe requires:

- Correct fully transparent, half-transparent, and opaque fixture pixels, plus visible patterned
  backgrounds behind all three metadata rows and the waveform.
- Moving metadata and waveform pixels in their individual and combined states; static sibling
  pixels and zero continuous parent-surface frames on the supported native path.
- Paused pixels staying still, minimized raster windows disappearing, and motion returning after
  restore, move, and resize.
- Twelve popup transitions with visible text, followed by a real Robot click reaching the shared
  waveform handler. A separate real window covers the app and then the owner is raised again;
  raster pixels must follow that stacking order and retain their alpha and text.

`NAVIAMP_PROBE_PHASES=hidden-combined,restored-combined,resized-combined` selects a focused lifecycle
diagnostic. `NAVIAMP_PROBE_EXPECT_FALLBACK=true` retains pixel, motion, popup, and input assertions
while allowing the shared fallback's parent redraws. Do not use fallback measurements to claim
that the supported native path meets its performance budget.

The workstation probe budget is at most 2% process CPU in each supported native animation state,
at most 1% in static/paused/minimized states, zero continuous parent frames, and zero changed sibling
pixels. Compositor CPU must be reported alongside those results; it is not interchangeable with
application CPU, and these VM measurements do not establish physical-GPU energy cost.

## Results

All three final native trials passed pixel, movement, input, popup, stacking, and lifecycle
assertions. Retained evidence: [environment](linux-x11-alpha-2026-10-02/environment.txt),
[trial 1](linux-x11-alpha-2026-10-02/trial-1.txt),
[trial 2](linux-x11-alpha-2026-10-02/trial-2.txt), and
[trial 3](linux-x11-alpha-2026-10-02/trial-3.txt).

| State | Process CPU, three-trial range | XFWM compositor CPU range | Parent frames |
| --- | ---: | ---: | ---: |
| Static | 0.10–3.50% | 1.40–1.60% | 0 |
| Marquee | 1.10–1.80% | 4.30–5.60% | 0 |
| Waveform | 0.40% | 1.40–1.90% | 0 |
| Combined | 1.50% | 4.60–5.00% | 0 |
| Paused | 0.10–0.20% | 1.50–1.70% | 0 |
| Minimized | 0.20% | 1.50–1.60% | 0 |
| Restored combined | 1.40% | 4.80–5.10% | 0 |
| Moved/resized combined | 1.40–1.50% | 4.80% | 0 |

The third trial's 3.50% static sample exceeds the documented 1% budget. It is retained rather
than discarded; the other two static samples were 0.60% and 0.10%. Three [additional sequential static samples](linux-x11-alpha-2026-10-02/static-repeat.txt)
were 0.30%, 0.20%, and 0.10%, with zero parent frames. The high sample was not sustained in
this repeat, but its cause has not been established. This report does not claim the complete
performance acceptance gate is satisfied.

All supported native states had zero changed sibling pixels and no native-window creation or
closure during their ten-second measurement intervals. Opaque/half-transparent/transparent fixture
pixels were `ffffff` / `923456` / `ac6824`. Diagnostics selected parent visual `0x6e`, depth 32,
alpha shift 24/mask `0xff`, screen 0, with an active compositing manager. Metadata and waveform
movement were verified from distinct captures; paused movement was zero. Each trial checked twelve
popup transitions, waveform pointer delivery, and cover/raise stacking.

An isolated Xvfb run with no compositing manager rejected native attachment and passed the same
alpha, moving-pixel, sibling, popup, and input assertions through the shared fallback. See
[fallback evidence](linux-x11-alpha-2026-10-02/fallback.txt). Its software animation required
continuous parent frames and high process CPU; this verifies functional fallback only and does
not meet the supported-native performance budget.

Shared accessibility semantics, keyboard and moving-link input, clipping, cached pixel reuse,
native failure, and hide/restore behavior are covered by common UI regression tests. The full
JVM UI suite passed 453 tests before the additional restore test; the final focused raster suites
passed all 15 tests after the production edits. Android shared compilation, common metadata compilation,
and `verifyCoreFirstArchitecture` passed. Linux app-image validation passed for the final native
library. The complete Windows/macOS/iOS/Android CI matrix and native accessibility inspection
remain required before release; no claim about physical GPU energy or XWayland is made.

## Platform placement audit

The production Kotlin changes are limited to these concrete native boundaries:

- `core/ui/src/jvmMain/kotlin/app/naviamp/ui/LinuxRasterPresenter.kt`: AWT transparent window
  creation, native window type/position/lifetime, JAWT attachment, and X11 pixel presentation.
- `core/ui/src/jvmMain/kotlin/app/naviamp/ui/DesktopRasterPresenter.kt`: AWT window notifications,
  Compose Desktop window-state facts, and repositioning adapter-owned native windows.
- `apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/app/Main.kt`: passes its existing native
  Compose Desktop `WindowState` to that adapter.

The C++ change uses Xlib, XRender, X Shape, JAWT, and JNI. No Android or iOS production source,
shared setting, resource string, provider behavior, or persistence schema changes.

## Final packaged-app check

The final validated `jpackage` app image was launched with disposable XDG config/data paths and
connected to the same loopback fixture. On the affected XFCE software-rendered path, its native
parent diagnostics confirmed visual `0x6e`, depth 32, alpha 24/`0xff`. The title and progress area
retained the shared background: [Now Playing](linux-x11-alpha-2026-10-02/packaged-playing.png).
A real pointer seek changed the UI position to 4:46, pause worked, and the player menu rendered
over the raster content: [popup](linux-x11-alpha-2026-10-02/packaged-popup.png). X11 minimization
made the client unmapped; restoring it returned visible transparent metadata. Resizing the client
from 940 × 610 to 1010 × 680 moved and resized the raster presentation with the shared layout:
[resized player](linux-x11-alpha-2026-10-02/packaged-resized.png). Screenshots contain only the
fixture-backed app window.

This is an app-image smoke check, not a newly released DEB or the complete application performance
matrix. Full-app animation budgeting, the unexplained idle outlier, native accessibility inspection,
and the cross-platform checks remain acceptance work on #153.
