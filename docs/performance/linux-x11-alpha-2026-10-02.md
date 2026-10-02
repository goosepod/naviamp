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
  Core's timelines and playback update cadence are unchanged. The later verification below also skips native wakeups while all integral Xlib arguments remain identical.
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

## Original alpha-fix probe results

All three original native trials passed pixel, movement, input, popup, stacking, and lifecycle
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
matrix. The full-app checks below retain the unmet CPU gate and native accessibility limitation. The
unexplained original idle outlier remains acceptance work on #153; require the full matrix for
each proposed commit before accepting it.


## Full-app verification after shared live bindings

The real application's one-second playback updates initially produced 30 parent frames per
30-second interval. The [initial diagnostic](linux-x11-alpha-2026-10-02/full-app/initial-diagnostic.txt)
recorded 8.27% CPU, but used Java accessibility inspection; it is not a controlled ordinary-launch
CPU baseline. Redraw traces identified the shared snapshot-observer path. Shared raster content,
progress semantics, and the cached position label now consume live values without subscribing
unrelated composition or drawing content. Native updates reuse the waveform images. Semantics,
keyboard seeking, focus indication, wrapping, and fallback remain in common Kotlin.

The X11 adapter retains Core's scalar keyframes and interpolates the same native geometry. It
waits until an integral `XMoveWindow`/`XMoveResizeWindow` argument or mapped visibility can change;
a new shared submission interrupts that wait. Repeated AWT `show()` operations are also omitted
when the adapter-owned window is already visible. These changes neither disable animation nor
reduce playback update frequency. The native geometry test checks holds, reverse motion,
repetition, reveal visibility, and every skipped integer-coordinate interval; Release assertions
remain enabled.

### Conditions and measurement limits

These are **virtual-machine measurements**, not physical-hardware CPU or energy results. The
underlying host load, scheduling, virtual GPU, and power conditions are not observable. Absolute
CPU values and isolated outliers are therefore uncertain. Repeated runs use the same guest,
XFCE compositor, 1280 × 800 / 74.99 Hz display, scale 1, 96 DPI, software renderer, and packaged
runtime. CPU includes all app threads and JIT compilation; compiler ticks are retained as a
diagnostic and are never subtracted to pass a budget. Parent frames and actual changed pixels
provide direct evidence independent of CPU timing precision.

The final packaged image ran with an isolated `/tmp` profile against the long-metadata loopback
fixture: three 600-second, 16 kHz mono WAV tracks. Volume was zero while audio playback remained
active. Logical window dimensions were reset to 1000 × 740 between launches (actual frame
992 × 735 at 40,25). Each measurement warmed for five seconds and sampled for 30 seconds. The
same scrolling settings were used within each group. Static means paused with metadata scrolling
off; waveform means playing with metadata scrolling off. Paused metadata intentionally keeps
scrolling when enabled. No product animation behavior was changed for measurement.

A test-only JVMTI observer delegates the packaged app's actual Skia renderer and counts its
parent frames. Ordinary timing runs avoid activating accessibility; native accessibility is
checked separately. Three additional runs launched the same image **without** the observer,
with CPU and fixture-only screenshots collected by a separate process. Those calibration runs
cannot count parent frames. [Artifact hashes](linux-x11-alpha-2026-10-02/full-app/artifact-sha256.txt)
identify the shared UI jar and native library used for this matrix. The final cache-correction
artifact and its narrower confirmation checks are recorded separately below.

### Thirty-second full-app matrix before the popup-cache correction

Raw metrics, commands, lifecycle replies, accessibility trees, and fixture-only image pairs are
retained under [full-app](linux-x11-alpha-2026-10-02/full-app).

| State | App CPU | XFWM compositor CPU | Parent frames | Visible behavior |
| --- | ---: | ---: | ---: | --- |
| Static, three samples | 0.37–0.40% | 1.47–1.57% | 0 | No text, waveform, or artwork changes |
| Waveform only, three samples | 2.70–3.60% | 1.87–2.07% | 0 | Waveform moves; text and artwork stay still |
| Combined, three samples | 3.60–4.27% | 5.13–5.47% | 0 | Text and waveform move; artwork stays still |
| Metadata while paused, three samples | 0.97–3.23% | 5.37–5.67% | 0 | Text moves; waveform and artwork stay still |
| Minimized while playing | 2.43% | 2.40% | 0 | Minimized; zero visible owned windows |
| Restored while playing | 3.47% | 5.87% | 0 | Text and waveform move; artwork stays still |
| Resized while playing | 3.57% | 5.70% | 0 | Text and waveform move; artwork stays still |
| Combined without observer, three samples | 3.67–4.73% | 5.13–5.70% | Unobserved | Text and waveform move; artwork stays still |

The 3.23% paused-metadata sample included 570 ms of compiler CPU. Its cause is not established,
and it is retained. Earlier valid measurements and the original 3.50% static outlier are not
replaced with a favorable retry. One observer-free sample was rejected when another window
covered the app. A preceding guarded run also rejected obscured/blank fixture artwork without
an established cause. [Invalid-sample notes](linux-x11-alpha-2026-10-02/full-app/invalid-samples.txt)
retain these failures; covering-window screenshots are excluded. The strengthened visibility
guard requires the fixture's actual artwork color at three points. A fresh observer directory
is mandatory to prevent historical commands from being replayed.

A fresh ten-second lifecycle confirmation collected visibility facts **after** warming: minimize
had zero visible owned windows, restore and resize each had five, and all had zero parent frames.
Restored/resized text and waveform moved with unchanged artwork. With the real native ATK bridge
active, a further ten-second playing sample also had zero parent frames, moving text/waveform,
and unchanged artwork (4.10% app CPU, 6.20% compositor CPU).

The visual and redraw assertions pass. Static CPU passes in this final group, but waveform,
combined, and playing-minimized CPU exceed the documented VM budget. The observer-free range
also exceeds it, so instrumentation is not the sole explanation. **Full-app performance acceptance
remains open.** VM limitations prevent extrapolation to physical hardware; physical-hardware
measurement and further profiling are needed before claiming that budget is met.

### Final cache correction and app confirmation

The first complete component rerun failed popup transition 2: the shared cache was being recreated
with its native binding, leaving the fallback without pixels until asynchronous collection ran.
The [failed trial](linux-x11-alpha-2026-10-02/popup-regression-trial.txt) and
[missing-text capture](linux-x11-alpha-2026-10-02/popup-regression.png) are retained. Cached content
and its live collector now belong to the shared component's lifetime; only the native submission
binding is replaced. A common regression checks twelve popup round trips, pixel visibility,
bitmap identity, no rerasterization of unchanged content, and live content changes through fallback.
A focused resized real-window run then passed twelve popup transitions, pointer input, alpha,
and cover/raise stacking.

The rebuilt packaged app's [final artifact hashes and evidence](linux-x11-alpha-2026-10-02/full-app/post-popup-cache)
are separate from the preceding matrix. The scoped confirmation used the same fixture and window
conditions, with five-second warmups. It did not repeat all three earlier calibration groups:

| Final app state | Duration | App CPU | Parent frames | Result |
| --- | ---: | ---: | ---: | --- |
| Combined playing | 30 s | 4.57% | 0 | Text and waveform move; artwork unchanged |
| Metadata while paused | 10 s | 1.20% | 0 | Text moves; waveform and artwork unchanged |
| Minimized while playing | 10 s | 2.50% | 0 | Zero visible owned windows |
| Restored while playing | 10 s | 8.30% | 7 | Text and waveform move; artwork unchanged |
| Resized while playing | 10 s | 4.00% | 0 | Text and waveform move; artwork unchanged |
| Later steady playing sample | 10 s | 3.90% | 0 | Text and waveform move; artwork unchanged |

The seven restored parent frames and elevated CPU are retained as an **unexplained failed sample**.
A later five-second diagnostic captured no `needRender` calls, and the subsequent steady sample
had zero parent frames. That later sample was after resize and bridge activation; it is not a
controlled repeat of the original restore transition and does not establish the cause. Neither
CPU acceptance nor every restored-state redraw check is reported as passing.

The final artifact again passed native progress focus, real Home/Right seeking to 0:00/~0:06,
artist focus plus Enter opening the artist page, and native album activation opening the album
page. Its shared progress value after Right was 0.01067. Those final native trees are retained
alongside the final app's metrics. Native value assignment remains the separate #202 failure.

### Native accessibility and keyboard results

The installed Java ATK wrapper 0.44.0 was loaded into the disposable packaged process by the
test observer and registered with the real AT-SPI bus. The packaged Linux runtime now includes
`java.management`, which that wrapper requires. This checks the native bridge and shared UI;
it does not establish automatic bridge activation or Orca speech output in an ordinary launch.

The native tree exposes the full overflowing title, artist, and album, current position, and
progress slider. Native focus followed by real Home and Right key events moved actual playback
to 0:00 and about 0:06; MPRIS reported 6.4 seconds and shared progress semantics reported 0.01067.
The [focus screenshot](linux-x11-alpha-2026-10-02/full-app/final-native-a11y/keyboard-progress.png)
shows the waveform outline. Native artist focus plus Enter opened the actual artist page;
native album activation opened the actual album page. Native position queries update during
playback, and eight progress-value change events were observed during an eight-second sample.
No claim of per-second spoken announcements is made.

Native slider value assignment **fails**: AT-SPI reports an empty value range, and successful
method return values alone do not demonstrate seeking. The probe now checks the range and actual
post-assignment value. This separate desktop accessibility defect is tracked in
[issue #202](https://github.com/goosepod/naviamp/issues/202), with the
[rejected native assignment](linux-x11-alpha-2026-10-02/full-app/final-native-a11y/native-value-assignment.txt)
retained. Shared semantics and actual keyboard seeking pass; native value assignment is not
reported as passing.

### Reproduction helpers

Build the observer with:

```sh
bash scripts/animation-probe/build-full-app-observer.sh /tmp/naviamp-observer
python3 scripts/android-tv-fixture.py --tracks 3 --track-seconds 600 --burst-seconds 600 --long-metadata
```

Use an isolated app profile configured for that fixture, reset its `window.properties` before
each launch, and create a **new** observation directory. For the window dimensions above its
`regions.txt` contains `15 451 302 18` and `63 407 206 28`. Add this test-only JVM option when
launching the validated app image (retain the isolated profile's own `user.home`/XDG settings):

```text
-Dnaviamp.probe.skipAccessibility=true
-agentpath:/tmp/naviamp-observer/libobserver.so=/tmp/naviamp-observer/observer.jar,/tmp/new-observation
```

Then use `python3 scripts/animation-probe/full-app-control.py /tmp/new-observation measure combined-1 30`.
The same tool provides minimize, restore, resize, and diagnostic commands. Resize uses frame
1040 × 760 at 60,40; update region lines to `15 466 318 18` and `63 420 222 28` for that fixture
layout. These fixed regions and artwork guards are specific to this verification fixture.

For calibration, launch the app without the agent, identify that disposable launch's PID, and
run the observer externally:

```sh
java -cp /tmp/naviamp-observer/observer.jar FullAppObserver APP_PID combined-1 30 /tmp/new-calibration 40 25 992 735
```

For native accessibility, add `/usr/share/java/java-atk-wrapper.jar` to the agent's colon-separated
jar list and `-Djava.library.path=/usr/lib/x86_64-linux-gnu/jni`, then send `bridge` after launch.
Use `/usr/bin/python3 scripts/animation-probe/inspect-linux-accessibility.py APP_PID` to inspect
only that app; `--focus-slider`, `--focus`, `--action`, `--seek`, and `--watch-seconds` exercise
actual native behavior. Accessibility-active timing must remain separate from ordinary timing.

### Automated validation and platform audit

The complete shared JVM UI suite passed 460 tests during development. The final production tree
passed 41 focused raster/layout/player tests with zero failures or skips, Android/shared metadata
compilation, `verifyCoreFirstArchitecture`, native CTest geometry timing, and Linux app-image
validation. Common tests cover live semantics and pixels, cached native updates without parent
redraws, deferred native commits, large-font wrapping, keyboard seeking and clamping, popup
fallback, readiness/failure, and hide/restore behavior.

For each proposed commit, require the complete Windows/macOS/Linux/Android/iOS matrix in
[PR #199](https://github.com/goosepod/naviamp/pull/199). The earlier commit `15154772` passed that
matrix; those results do not verify subsequent source changes. The PR's checks provide the exact
commit's current matrix result. Local verification does not replace it.

The production platform file audit above still applies. The additional `LinuxRasterPresenter.kt`
change is justified by AWT `Window.show()` raising already-visible native windows and by native
X11 restacking. The C++ timing helper operates only on integral Xlib geometry/map operations.
There are no new Android, iOS, macOS, or Windows production implementations. Shared motion,
visibility, live data binding, semantics, keyboard behavior, and focus visuals remain in Core.


## Final component and fallback reruns

After the cache correction, all three complete visible component trials passed alpha, live
one-second progress updates, moving pixels, clipping, unchanged siblings, zero parent frames,
static/paused/minimized/restored/resized states, twelve popup transitions per trial, actual pointer
delivery, and cover/raise stacking. The environment and all trials are retained:
[environment](linux-x11-alpha-2026-10-02/post-cache-environment.txt),
[trial 1](linux-x11-alpha-2026-10-02/post-cache-trial-1.txt),
[trial 2](linux-x11-alpha-2026-10-02/post-cache-trial-2.txt),
[trial 3](linux-x11-alpha-2026-10-02/post-cache-trial-3.txt).

| Component state | App CPU range | XFWM compositor CPU range | Parent frames |
| --- | ---: | ---: | ---: |
| Static | 0.50% | 1.70–3.10% | 0 |
| Marquee | 0.90% | 5.00–6.40% | 0 |
| Waveform with one-second updates | 0.60% | 1.70–2.10% | 0 |
| Combined | 1.40–1.60% | 5.20–5.90% | 0 |
| Paused | 0.10–0.20% | 1.60–2.70% | 0 |
| Minimized | 0.40–0.50% | 2.30–2.70% | 0 |
| Restored combined | 1.40% | 5.90–6.10% | 0 |
| Resized combined | 1.30–1.60% | 5.80–6.60% | 0 |

These component process-CPU samples satisfy the documented budget in this VM. They do not
resolve the full application's CPU failures, the restored-app sample with seven parent frames,
or the historical unexplained idle outlier. Compositor CPU is retained separately; no claim of
physical GPU cost or XWayland acceptance is made.

The final [no-compositor Xvfb run](linux-x11-alpha-2026-10-02/post-cache-fallback.txt) passed alpha,
visible text, moving text/waveform, unchanged siblings, twelve popup transitions, and actual
pointer delivery through the shared fallback. Its continuous parent frames and high software
CPU are functional evidence only; this unsupported-native environment does not meet the native
performance budget. Xvfb without a window manager does not establish minimize/restore behavior.


## Main-branch integration check

Accepted `main` advanced to `dfb7513a` during verification. The only merge conflict was the
fixture's adjacent options; both `--accounts` and `--long-metadata` are retained. A real loopback
HTTP check verified distinct Alice/Bob catalogs with long metadata. The raster production files
were unchanged by this integration. The merged build again passed the 40 focused UI tests,
common/Android compilation, architecture verification, and app-image validation.

A separate ten-second [merged app confirmation](linux-x11-alpha-2026-10-02/full-app/main-sync)
retains its own artifact hashes and screenshot pair. It verifies moving metadata/progress and
unchanged artwork; the raw result records CPU and parent frames. This integration smoke check
does not replace the earlier repeated matrix or resolve its failed acceptance samples. Require
the complete cross-platform matrix for the final merged PR head.


## Final keyboard direction check

The waveform's visual and pointer timeline remains left to right in RTL layouts. A final shared
input review corrected horizontal keyboard arrows to follow that existing timeline. A common
RTL regression performs a real quarter-width pointer seek, then verifies Right advances and
Left reverses that position. All 41 focused tests and common/Android compilation pass. This
input-only correction does not change raster pixels, animation pacing, cache lifetime, or the
conditions/results of the preceding performance measurements. Its final commit still requires
the complete CI matrix.
