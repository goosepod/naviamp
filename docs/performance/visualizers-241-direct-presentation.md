# Direct GPU visualizer presentation, iteration 1

Tracked by [issue #241](https://github.com/goosepod/naviamp/issues/241) and
[draft PR #242](https://github.com/goosepod/naviamp/pull/242).

The shared owner now supplies Audio Sphere, Analog Signal Failure and Ocean of Ink to persistent
GPU surfaces. It owns audio snapshots, smoothing, uniforms, elapsed time, 60/45 FPS deadlines,
visibility, clipping and bounded fallback. Frames do not request the Compose frame clock or
publish per-frame Compose state. Native adapters upload inputs and present output; the Mac and
iOS direct paths do not wait for GPU completion or read pixels back through the CPU.

Audio Sphere's native source is generated from its existing shared body and palette. A rendered
comparison checks active frames and the inactive ring. Analog and Ocean retain their canonical
GLSL. Other effects retain their existing rendering path during this migration. Android and iOS
use the same shared presentation owner; Windows/Linux still use the existing fallback.

## Mac measurements

Apple M1, AC power, two 2560x1440 displays at 75 Hz and 1x scale. The first fixture was 1000x740;
the larger fixture was 1280x960 (1280x932 client), moved by the owner to the second display at
2780,140. No Gradle build or GPU trace overlaps accepted CPU intervals. Input remains 32 bands
sampled independently at 20 Hz. CPU percentages refer to one logical core, not the entire machine.

Only samples with both physical captures passing contribute to the following figures. The larger
fixture's final static baseline was 0.46–0.88% CPU (median 0.67%).

| Effect | Pixels | Requested FPS | Process CPU | Parent frames / 10s |
| --- | ---: | ---: | ---: | ---: |
| Audio Sphere | 358x358 | 60 | 7.94% median | 0 |
| Analog Signal Failure | 358x358 | 60 | 8.13% (one fully visible sample) | 0 |
| Analog Signal Failure | 358x358 | 45 | 6.87% median | 0 |
| Analog Signal Failure | 640x640 | 60 | 8.14% median | 0 |
| Ocean of Ink | 358x358 | 60 | 8.36% median | 0 |
| Ocean of Ink | 358x358 | 45 | 6.52% median | 0 |
| Ocean of Ink | 640x640 | 60 | 8.37% median | 0 |

The 45 FPS Sphere samples ranged from 5.93% to 10.17%; the latter included 128 ms of JVM
compilation and two parent frames. They do not establish a stable 45 FPS CPU advantage for Sphere.
Earlier warmed 1000x740 comparisons measured the old native Analog path at 23.57–25.24% CPU and
the old native Ocean path at 21.91–22.36%, with roughly 750 parent frames per ten seconds. These
are exploratory comparisons, not randomized trials or identical-size full-app acceptance.

The 640-pixel Ocean submission intervals had a 16.49 ms median and 20.11 ms 95th percentile.
Before/after captures prove substantial motion at both sizes; the unchanging sibling crops stayed
identical in the active samples. The owner also inspected the large Ocean output and reported
that it looks very smooth. Pixel count and submission cadence are not end-to-end presentation
timestamps, and they are not a substitute for that visual check.

A separate large-Ocean Metal trace recorded 632 Fragment intervals across 11.00 seconds: mean
8.48 ms, median 8.43 ms, p95 10.87 ms, p99 12.16 ms, maximum 13.35 ms. Channel durations can
overlap; these are neither total GPU utilization nor whole-frame presentation latency. GPU work
increases with pixel count even though CPU stays nearly constant. A separate paused trace recorded
no GPU intervals attributed to the owned fixture. Earlier warmed paused samples were 0.49–0.65%
CPU. Later paused samples included compilation/visibility disturbances and remain retained.

## Pixel 10a measurements

Physical Pixel 10a, Android API 37, 1080x2424 display, charging, isolated debug/instrumentation
package. These are single ten-second intervals after five seconds of warmup, not release-build
full-app acceptance. CPU percentages refer to one core. Static measured 0.06–0.17% CPU.

| Effect | Pixels | Requested FPS | Process CPU | Accepted submissions / 10s | Parent frames |
| --- | ---: | ---: | ---: | ---: | ---: |
| Audio Sphere | 358x358 | 60 | 47.24% | 600 | 0 |
| Audio Sphere | 358x358 | 45 | 34.56% | 450 | 0 |
| Analog Signal Failure | 358x358 | 60 | 41.44% | 600 | 0 |
| Analog Signal Failure | 358x358 | 45 | 30.99% | 450 | 0 |
| Analog Signal Failure | 640x640 | 60 | 36.50% | 600 | 0 |
| Ocean of Ink | 358x358 | 60 | 36.42% | 600 | 0 |
| Ocean of Ink | 358x358 | 45 | 29.43% | 450 | 0 |
| Ocean of Ink | 640x640 | 60 | 33.65% | 600 | 0 |

The valid old Sphere baseline measured 58.38% CPU and 600 parent frames. Old inline Analog and
Ocean were blank (one crop color, zero changed pixels), so their CPU results are excluded. They
cannot establish either a regression or an improvement. The direct path required one native root
pre-draw traversal to mount its SurfaceView while Compose remains idle; it now starts successfully.

Before/after captures establish motion in every active direct phase and unchanged sibling content.
Large Analog changed 390,015 of 409,600 pixels; large Ocean changed 37,728. Paused Analog changed
zero pixels and measured 0.07% CPU, zero submissions and zero parent frames during the interval.
The comparison ignores differences of at most two channel levels. All 60 FPS submission medians
were 16.45–16.63 ms, with p95 17.66–17.91 ms. Submission timing does not measure presentation latency.
Zero parent GPU duration describes the Compose window only, not GPU work on the separate GLES surface.
These measurements precede removal of startup diagnostic logs and delegation of the Android host
to the existing common popup/visibility environment; the frame path is unchanged.
Android CPU still needs release-build profiling and broader lifecycle/overlay acceptance; do not
apply the Mac's accepted budget decision to Android or claim all-platform performance is complete.

## Scope decision

The original aspiration was at most five CPU percentage points above static playback, approximately
5.67% for this larger fixture. The measured 60 FPS results are about 2–2.7 points above that
aspiration; 45 FPS Analog/Ocean are about 0.8–1.2 points above it. On 2026-10-07 the owner explicitly
accepted getting close enough for this iteration and iterating later. Do not spend this iteration
chasing those remaining points. That checkpoint kept 60 FPS as the shared default and 45 FPS as a
probe option. The October 8 working-tree candidate below evaluates a shared 45 FPS default; its
Android performance acceptance remains open. No new setting, export/import or translated UI copy
changes are introduced.

Keep the umbrella issue open for other effects, Windows/Linux, full-app acceptance, physical iOS
measurements and future improvements. iOS compilation alone does not establish its performance.
The requested future direction is a full-player/background visualizer behind the normal Now Playing
controls. Implement this in common UI as a backdrop layer beneath artwork, text and controls, with
shared scrim/contrast treatment, unchanged hit testing and accessibility, and the same visibility and
FPS policy. Measure the real full-player surface at actual display resolution: the 640-pixel probe
supports the direction but does not establish full-screen GPU cost. The current foreground surface
must not simply cover controls. MilkDrop/projectM integration remains a separate step.

## Real-player integration follow-up

The isolated physical Pixel benchmark application exposed costs absent from the small fixture.
At a 1006 × 814 Ocean of Ink surface with scrolling metadata and playback active, repeat
10-second samples used 53.65% and 52.18% of one CPU core. Removing an unused playback-progress
subscription alone did not improve this case (58.98%). The combined shared fixes separate FFT
samples from application-state publication and dispatch the existing native presentation deadlines
on Main without Compose's parent-window frame batching. Playback updates and the 60 FPS ceiling
are preserved.

After the combined fixes, the matching active player used 26.14% of one CPU core and recorded
zero parent-window frames. Its 15-second Perfetto trace recorded 596 GLES draws, approximately
40 FPS rather than the requested 60 FPS. Mean onDrawFrame duration was 14.89 ms and mean
eglSwapBuffers duration was 6.71 ms: driver backpressure remains at this larger resolution.
These are exploratory before/after samples on the same charging phone and layout; baseline thermal
state was not recorded, so this is not a temperature-controlled causal estimate. The owner judged
the resulting Ocean animation “much smoother.” Further large-background GPU tuning is deferred.

Switching effects also exposed a native-view lifetime bug: a replacement region did not replace
the view remembered by AndroidView.factory. The common mount now keys Content by region, with
a shared regression test checking replacement and disposal. During visual comparison, Android's
Analog trace appeared flatter than the Mac's. Toggling its surface off and on restored the expected
wavy style, confirmed by the owner. An initial claim that the image was completely frozen was
incorrect: the comparison selected the wrong image rows. Corrected comparisons show changing
pixels both before and after remounting. The rebuilt physical-device app then passed
Analog → Sphere → Ocean → Analog switching without blank surfaces or manual remounting.
Analog returned with its wavy trace; captures showed 778,494 changed visualizer pixels out of
818,884. A subsequent 10-second active Analog sample used 25.17% of one CPU core with zero
parent-window frames. The nine shared UI regression tests and architecture check passed, and
the benchmark APK built successfully. These checks establish the surface replacement and live
rendering result; they do not establish identical pixels at different sizes and animation times.

Desktop Main dispatch requires the coroutines Swing runtime; the shared UI JVM dependency now
includes it, with a regression test asserting dispatch on the AWT event thread. No platform production
behavior was added in this integration follow-up. Common Android/JVM/iOS compilation and the
architecture guard passed; sample-stream and progress-subscription regression tests passed.
Frozen/loading samples and obscured Mac captures are excluded from acceptance. Full-player
lifecycle checks and physical iOS performance remain outstanding, so this work stays open.

## Android ordinary-playback baseline investigation (2026-10-08)

The owner requested fixing ordinary playback overhead after a 12.17% CPU sample with the artwork placeholder
and long metadata with a scrolling title. That number describes the whole process, not artwork rendering
alone. A short-metadata track, with the artwork placeholder and smooth progress but no scrolling, measured 8.35%
and 7.72% of one core. The same short track playing behind the launcher measured 4.07%; paused
in the visible player measured 0.42%. These are CPU measurements, not battery-life measurements.

Conditions: physical Pixel 10a, Android 17/API 37, isolated minified/profileable benchmark app,
1080 × 2424 display, density 420, active 60 Hz mode, USB charging at 100%, thermal status 0.
Audio is the local synthetic ten-minute WAV fixture; real codecs and streaming conditions need
separate battery testing. CPU intervals are 15 seconds unless stated otherwise; the 7.72% repeat
used 20 seconds. Perfetto intervals are separate 15-second captures. Parent-window frames were
zero in the steady samples; that does not establish zero compositor or GPU cost.

The plain-player trace recorded 446 animation callbacks in 15 seconds. Profiles placed substantial
main-thread work in `AndroidRasterPresenter.doFrame`, crop submission and SurfaceControl transaction
application. A 1,000-pixel progress bar on a ten-minute track advances only about 1.7 pixels per
second. Common code now caches the integer source/destination crops and plans the next visible
pixel deadline. Android only posts/removes Choreographer callbacks and applies changed SurfaceControl
geometry. Playback update frequency is unchanged; marquee translation retains its existing cadence.
Buffer replacement, clipping, seek, resize and hide/restore invalidate the cached presentation.

The pixel-deadline APK (`4fb8f4119884a87f0701c9e810b577aeccd44fccf80af191c44b33a315a95a56`)
measured 6.41% and 5.73% CPU in short-metadata playback. The progress trace dropped from 446
animation callbacks to 17 in 15 seconds; main-thread scheduled CPU dropped from 0.826 seconds
to 0.329 seconds. Captures show the progress edge advancing about 20 pixels during the 15-second
CPU interval, consistent with the bar width and ten-minute duration. Parent frames stayed zero.
The once-per-second elapsed-time text still generated 15 hardware bitmap allocations/uploads per
trace; optimizing that content replacement and the underlying audio baseline remains open.

Static short-metadata pause measured 0.59% CPU with no animation callbacks/uploads. Background
playback measured 4.98%, also with no raster callbacks/uploads; do not claim an improvement over
the earlier 4.07% background sample. Long-metadata pause (scrolling title only) measured 3.82%,
versus the earlier 5.27%. The warmed combined state measured 8.79% over 30 seconds; an earlier
15-second interval after selecting the track measured 13.02%. Marquee motion/hold phases and
startup work make short CPU windows variable. These exploratory samples do not establish a
precise causal percentage reduction for the combined state. The ordinary-player baseline remains
above idle, and this is not an accepted battery-life fix or completion of Android performance work.

An intermediate crop-only fix measured 6.87% CPU in warmed short-metadata playback but still
woke around 30 times per second. This intermediate result is not the final scheduler acceptance.
A spectrum-uniform upload experiment measured 34.27% CPU against 34.61% for active Analog;
it was discarded because that difference did not justify changing the shader transport.

Ocean with long metadata measured 25.37% CPU, with 597 GLES draws in 15 seconds (39.8 FPS),
mean onDrawFrame 15.31 ms and mean eglSwapBuffers 6.71 ms. Before/after captures changed
110,932 of 818,884 visualizer pixels. This is consistent with the earlier roughly 40 FPS GPU-limited
result, not a new visualizer performance gain. Paused Ocean recorded zero GLES draws and no
bitmap uploads; its scrolling title remained active (5.44% process CPU in that interval).

Seven new common crop/deadline tests and twelve existing GPU/shader tests passed, followed by the
two mount/progress-subscription regressions. Common Android, JVM, iOS arm64 and simulator arm64
compilation and the architecture guard passed before the final Android callback wiring. The final
Android build and architecture guard passed afterward. This increment changes only one platform
production file: `androidMain/.../AndroidRasterPresenter.kt`, justified by Android Choreographer,
SurfaceView/SurfaceControl transactions and the native raster buffer lifetime. Pixel geometry,
duplicate suppression and deadline/input/cancellation policy are shared.

During lifecycle/menu exploration, some visualizer mounts were blank and are excluded; a fresh
fixture start restored Ocean, and Ocean → Analog switching then produced the continuous wavy
trace. The exact blank-mount trigger remains unproven. Full lifecycle/resize acceptance and the
remaining ordinary/background playback cost stay open; do not treat this checkpoint as merge-ready.

After resuming testing, restored Analog measured 34.06% CPU with 892 GLES draws in 15 seconds
(59.5 FPS) and 778,907 changing visualizer pixels. Collapsing the full player stopped GLES draws
in a five-second trace, and selecting another track reopened the live visualizer. The shared
progress crop responded immediately to pointer seeking, but playback subsequently stayed loading
with this synthetic stream. That run is excluded from performance acceptance; seek/playback recovery
is not validated by the immediate crop response. A final warmed plain-playback repeat measured 5.33% CPU, 17 progress callbacks in 15 seconds,
zero parent frames and an advancing progress edge. Main-thread CPU was 0.272 seconds in its
separate trace. The final shared check ran all 21 tests together,
compiled Android/JVM/both iOS targets and passed the architecture guard.

Excluded samples: `baseline-short-artwork` actually had Ocean enabled, and `uniform-analog`
actually showed artwork. Track selection preserves visualizer visibility, while choosing an effect
does not enable an inactive visualizer. Their filenames do not establish their test state.
Fresh-install/warm-up CPU intervals are also excluded from steady-state comparisons.

Reproduce a sample after explicitly checking the visible player and letting startup settle:

```shell
python3 scripts/visualizer-probe/android-player-sample.py \
  --serial SERIAL --phase UNIQUE_PHASE --output /private/tmp/naviamp-player-perf --trace
```

The script leaves the UI untouched, captures before/after screenshots outside CPU intervals,
records process CPU, thermal/battery state and parent frames, and optionally captures a separate
Perfetto interval. Run `presentation-trace.sql` with Perfetto trace_processor for thread CPU,
actual GLES draws, driver wait durations, animation callbacks and bitmap uploads. Check visible
motion and matching power/display conditions before interpreting a result. Keep raw screenshots
and system traces local; they may include unrelated private content.

## Platform placement audit

Shared model, scheduler, elapsed time, frame assembly, visibility and fallback are in
`NaviampGpuVisualizer.kt`; effect conversion is in `NaviampGpuVisualizerShaders.kt`.

| Changed production file | Concrete native boundary |
| --- | --- |
| `androidMain/.../AndroidGpuVisualizerPresenter.kt` | Android GLES3, GLSurfaceView/SurfaceView, EGL resources and native view lifetime |
| `androidMain/.../AndroidRasterPresenter.kt` | Activity/context capability supplies the native GPU adapter |
| `jvmMain/.../DesktopGpuVisualizerPresenter.kt` | AWT canvas/JAWT and JNI calls into the Mac Metal layer bridge |
| `jvmMain/.../DesktopRasterPresenter.kt` | Desktop window and native-library availability supply the adapter |
| `iosMain/.../IosGpuVisualizerPresenter.kt` | Metal command encoding, CAMetalLayer drawables and CALayer resources |
| `iosMain/.../IosRasterPresenter.kt` | UIKit application visibility notifications and parent CALayer supply native facts |
| `native/visualizer-metal/src/naviamp_visualizer_metal.mm` | JAWT/Cocoa/Core Animation/Metal ABI and asynchronous native resource lifetime |

Native adapters do not own frame loops, audio acquisition, smoothing, retries, quality selection or
UI behavior. The shared common tests were defined and compiled for all three targets before host
wiring. Six common contract tests and six JVM shader tests passed; Android, JVM, iOS arm64 and
iOS simulator arm64 compilation and the architecture guard passed again after the final host audit.
Physical Android instrumentation passed its complete 216-second visible fixture plan. Full-app
resize, overlays, input, accessibility and hide/restore checks remain required before merge.

## Evidence and reproduction

Raw accepted and rejected CPU/draw records, selected Mac captures and GPU timing metadata are in
`visualizers-241-evidence/direct-presentation`. Locked/off-screen/obscured samples are excluded,
not interpreted as efficient rendering. Raw system traces remain in `/private/tmp/naviamp-241`;
only summaries for the owned process and WindowServer are published.

Use the existing visible fixture and sampler. `direct-*` selects the shared presenter;
`direct45-*` requests 45 FPS; `direct-large-*` uses 640 pixels. Inline baseline phases use the
existing `skia-*`/`native-*` overrides. Keep window/display conditions fixed and use a fresh prefix.
The pixel comparison accepts an optional size argument for the larger fixture.

The opt-in Android fixture is built with `./gradlew -Pnaviamp.visualizerProbe=true :core:ui:assembleDebugAndroidTest`. It installs as `app.naviamp.ui.visualizer241.test`, preserving
all installed Naviamp app packages. Its shared plan records process CPU, root/sibling draws and
accepted submissions; native instrumentation records window FrameMetrics and screenshots outside
CPU intervals. FrameMetrics GPU duration covers the parent window, not the separate GLES surface.
The test explicitly rejects an active direct phase that silently falls back.

## October 8 follow-up: visible consumers and cached hidden content

The next shared-code increment addresses two sources of hidden playback work found in the real
Android player. The visualizer display preference previously enabled FFT sampling even after the
player was collapsed or its host window was hidden. Mounted shared visualizer surfaces now own
sampling demand through the common action graph and playback controller. Demand follows window
visibility, clipping, overlays and playback activity; multiple consumers are independent. The
engine adapter also cancels its sampling timer on non-playing states. Suspension retains the last
spectrum; explicitly switching the visualizer off clears it.

The changing elapsed-time raster previously continued measuring text and drawing bitmaps in a
hidden window. The shared raster observer now retains cached pixels without observing changing
content while hidden or fully clipped, then renders the latest content on return. This does not
change the playback polling interval, visible animation cadence, progress behavior or semantics.

Regression coverage includes duplicate acquisition/release, two consumers, display toggles,
paused sampling with no repeating timer, retained samples, shared action routing, native mount
replacement, hidden raster updates and restore catching up to the latest value. A live common
visibility signal lets running effects suspend without waiting for recomposition or a UI frame.
The Android, AWT and UIKit hosts publish their existing native lifecycle/window facts through
that signal; scheduling and lifetime decisions remain common.

Device verification uses the same physical Pixel, benchmark package, 1006x814 visualizer,
60 Hz display, charging state and 16 kHz mono WAV fixture as the preceding checkpoint. The fixture
uses a static artwork placeholder. Its resampling cost is part of these CPU measurements; these
figures are not a battery-life estimate or a decoded-music/artwork workload measurement.

The pre-change background artwork samples measured 3.54–4.42% of one core; selecting Analog before
backgrounding measured 5.83%, with no GLES draws. A separate 10-second background artwork profile
attributed 42% of sampled process CPU to AudioTrack/BASS, 31% to the main thread (including hidden
raster text measurement), and 12% to a worker including playback-session persistence. One early
background artwork trace overlapped a foreground transition and is excluded; its preceding CPU
interval and the separate stack profile remain valid. The repeat trace has no foreground transition.

An initial recomposition-driven version compiled and passed its ordinary visibility tests but did
not establish a background CPU reduction (5.59% in a fresh-install interval). Android may suspend
recomposition before effects see a hidden-window composition local. The final signal must be
observed directly by running effects; the revised regression toggles host visibility with no UI
frame/recomposition and checks cancellation and catch-up. The initial APK and measurements are
retained locally and excluded from final performance claims.

Native-boundary audit for the live signal:

- `core/ui/src/androidMain/kotlin/app/naviamp/ui/AndroidRasterPresenter.kt`: Android Activity
  lifecycle callbacks publish their STARTED state through a flow rather than only Compose state.
- `core/ui/src/jvmMain/kotlin/app/naviamp/ui/DesktopRasterPresenter.kt`: AWT window callbacks
  publish `isShowing` and native Frame iconification facts; Compose WindowState remains a fallback.
- `core/ui/src/iosMain/kotlin/app/naviamp/ui/IosRasterPresenter.kt`: UIKit application active/resign
  notifications publish visibility through a flow rather than only Compose state.

These adapters do not acquire sampling demand, choose polling intervals or schedule rendering.
The common presentation effects consume the signal, cancel work, retain cached content and resume.

The live-signal APK was then tested on the visible physical Pixel. Warmed plain playback with
short metadata measured **5.01% of one core** over 30 seconds, with zero parent frames. Its first
15-second capture was 7.22%; retain that variability rather than treating the startup interval as
steady state. The progress edge advanced 20 pixels in that first capture. Long combined scrolling
playback measured 11.18% over 30 seconds; paused scrolling measured 4.05%, with 450 callbacks in
15 seconds and 13,128 changed title pixels. Visible scrolling remains an unresolved CPU cost.

Background artwork measured 3.48% with short metadata and 3.90% with long metadata. With Analog
selected, background playback measured **3.86%**, compared with the pre-change 5.83% selected-Analog
sample. The new background traces contain no GLES draws, animation callbacks or bitmap uploads;
main-thread trace CPU is 0.148 seconds (short artwork) versus 0.155 seconds (selected Analog) per
15 seconds. This establishes removal of the selected-visualizer background overhead, while the
artwork-only baseline is essentially unchanged from the prior clean 3.54% sample. Playback itself
and remaining shared work still require profiling; no battery-life improvement is quantified.

Visible Analog with short metadata measured 27.76%, 885 actual draws in 15 seconds (59.0 FPS),
and zero parent frames; restored Analog measured 26.40%, 888 draws (59.2 FPS), and real motion
(778,469 changed visualizer pixels). The earlier Analog samples included scrolling metadata, so
these visible CPU numbers are not a matched GPU performance improvement claim.

[Process summaries, conditions and shared test totals](visualizers-241-evidence/android-visible-consumers-oct8/README.md)
record the accepted captures. All 111 selected shared regressions pass, with Android/JVM/both iOS
compilation, the architecture guard and the minified benchmark build. Physical iOS and new Mac
lifecycle measurements, remaining combined-animation budgets and full interaction acceptance
remain open; this checkpoint does not complete #241.

An interrupted run lost its ADB reverse connection to the local fixture; stalled/blank playback
captures from that period are excluded. Restoring forwarding and selecting a fresh track restored
playback. Recovery also exposed an Android foreground-service startup deadline crash, tracked
separately as [#243](https://github.com/goosepod/naviamp/issues/243); its fix is outside this branch.

Paused Analog with short metadata measured **0.32% of one core**. Its cached picture was unchanged
across 15 seconds (zero changed visualizer pixels); the following trace had no GLES draws, raster
uploads or rendering callbacks. This is the expected retained paused image, not an active-motion
performance result. The long-title paused result above isolates the substantial marquee cost.

Ocean of Ink's active/restore smoke checks also passed: 17.55% / 17.12% CPU with short metadata,
600 actual draws per 15-second trace (40 FPS), zero parent frames, and 124,165 changed visualizer
pixels after restore. Its frame-rate limit remains unresolved. Benchmark playback was paused at
the end of this capture matrix.

### October 8 follow-up experiments (not acceptance)

The working tree now tests a shared 45 FPS policy and an aspect-preserving 262,144-pixel raster
budget for Ocean of Ink. Analog and Sphere retain their full raster dimensions. Shared code owns
the raster dimensions and clipping conversion; native adapters apply those dimensions to their
drawable or buffer. Shader bodies are unchanged. Shared shader quality selection also replaces
duplicated Android/OpenGL switches, and hidden accessibility observation follows the existing
shared live visibility signal. All 528 UI JVM tests pass, Android/JVM/iOS device and simulator
sources compile, the architecture guard passes, and the native Metal test passes. These checks do
not establish runtime performance on every platform.

The 45 FPS Ocean candidate on the physical Pixel measured **20.81% CPU** over a clean 15-second
interval with short metadata, zero parent frames and actual visualizer motion. The separate trace
contains 662 draws over a 14.733-second draw span, approximately 44.9 FPS. This costs more CPU
than the preceding 40 FPS checkpoint; it is a smoothness result, not a CPU improvement. Long
metadata measured 26.28% over 30 seconds. Earlier default-config 30-second traces retained only
part of the interval and lost process metadata; their frame counts must not be divided by 30.
The probe now records explicit process snapshots in a 128 MiB trace and checks PID continuity.

At 48 kHz, warm short-metadata artwork playback measured 5.20% CPU over 30 seconds and background
playback measured 3.34%. Startup intervals were higher and are excluded from steady-state claims.
These are not matched comparisons against the earlier 16 kHz fixture. A translation-only
SurfaceControl marquee candidate measured 3.32% while paused, still a material animation cost.
A cached-bitmap SurfaceView canvas trial regressed to 15.35% and was discarded. A subsequent
ImageView/RenderNode translation experiment compiled; its later physical-device rejection is
recorded below. Neither rejected path remains in production source.

On the visible Mac test app, small Ocean (289 × 271 physical pixels) measured 9.90% CPU, then
**8.88%** warm over ten seconds, with zero parent frames and visible ink motion. JVM compilation
time was respectively 148 ms and 71 ms; it has not been subtracted. A separate Metal trace
contains 727 fragment intervals over 16.205 seconds (about 44.9 per second): mean 3.696 ms,
median 3.683 ms, p95 3.877 ms and p99 4.036 ms. Interval durations are not a battery-energy or
GPU-utilization measurement. Conditions: M1 Mac, macOS 27.0.1, AC power, two 2560 × 1440
75 Hz displays at 1× scale. A matched artwork baseline and larger-window results remain pending.
Later captures failed the visibility guard because the Mac locked; they are excluded. One earlier
progress sample was interrupted by a UI mutation and is also excluded.

Testing then paused at the user's request and because the Mac locked. Both devices became
available again for the resumption below. No physical iOS performance measurement has been made.
These experiments do not complete #241.

Native-boundary audit for this follow-up:

- `AndroidGpuVisualizerPresenter.kt`: applies shared dimensions through SurfaceHolder/GLES and
  converts shared clipping into native GL scissor operations.
- `AndroidRasterPresenter.kt`: executes Android Bitmap, SurfaceControl fixed viewport/fractional
  positioning and Choreographer operations; shared code supplies geometry and motion policy.
- `PlatformLiveVisualizerSurface.android.kt`: delegates shader policy to common code before
  invoking the existing Android GLES renderer.
- `DesktopGpuVisualizerPresenter.kt`: passes shared dimensions across the JVM/native Metal ABI.
- `NativeOpenGlVisualizerHost.jvm.kt`: delegates shader policy to common code for the native
  OpenGL host.
- `IosGpuVisualizerPresenter.kt`: applies shared raster dimensions to CAMetalLayer.drawableSize.
- `naviamp_visualizer_metal.mm`: applies the extended JNI geometry array through CoreAnimation
  and Metal drawable sizing.

### October 8 physical-device resumption

The retained Android APK SHA-256 is
`9858174f62f533beca6ef9261bf07144b26474d392c28497d4815839956537d3`.
Rebuilding after removing the View experiment produced this identical APK. The source compiles,
the architecture guard passes and the benchmark build succeeds. The fixture remains 48 kHz and
both devices use their preceding display/power conditions.

Paused SurfaceControl scrolling measured **3.72% CPU** over 30 seconds. Its preceding 15-second
startup interval was 13.77% and is retained separately. That interval's separate trace contains
444 animation callbacks with no parent swaps; 11,168 title pixels changed while all 704,200
sampled artwork pixels remained unchanged. The View experiment measured **34.61%**, then
**34.79%** warm, with 1,560 RenderThread queueBuffer events in its 15-second trace. Its capture
showed playback active despite the attempted pause, so it is not a matched paused comparison.
The parent presentation regression is sufficient to reject it. Its source was removed, the
retained SurfaceControl APK restored, and normal accessibility/UI interaction recovered.

| Pixel state | Process CPU, one core | Interval | Presentation evidence |
| --- | ---: | ---: | --- |
| Analog, short metadata, warm | 24.60% | 30 s | Separate trace: 661 draws / 15 s, no parent swaps |
| Sphere, short metadata, warm | 24.10% | 30 s | Separate trace: 667 draws / 15 s, no parent swaps |
| Sphere restored | 24.15% | 15 s | 348,713 changed visualizer pixels; title unchanged |
| Sphere selected, background playback | 2.40% | 15 s | No draws, animation callbacks or bitmap uploads |
| Sphere paused | 0.30% | 15 s | No draws/callbacks/uploads; zero changed visualizer pixels |

Both active effects retain approximately 45 FPS and Analog's continuous trace. The Android GPU
thread costs about 1.83–1.86 CPU seconds per 15-second trace, with eglSwapBuffers averaging
1.45–1.48 ms wall time. Active CPU remains a material unresolved cost; these results do not
establish that the 45 FPS policy meets Android's final performance acceptance.

| Mac state | Process CPU, one core | Parent frames / 10 s | JVM compilation |
| --- | ---: | ---: | ---: |
| Artwork, 1280 × 960 window | 1.68% | 0 | 27 ms |
| Ocean, 505 × 493 display pixels | 7.59% / 9.91% | 0 / 0 | 69 / 301 ms |
| Analog, 505 × 493 display pixels | 4.13% | 0 | 4 ms |
| Sphere, 505 × 493 display pixels | 4.27% | 0 | 17 ms |
| Artwork, 2000 × 1200 window | 2.16% | 0 | 13 ms |
| Sphere, 800 × 770 display pixels | 9.12% | 2 | 25 ms |
| Sphere, 800 × 770, settled repeat | 7.37% | 0 | 15 ms |
| Ocean, 800 × 770 display pixels, bounded raster | 7.03% | 0 | 10 ms |

Visible captures passed the native guard. Analog, Sphere and Ocean moved, while their neighboring
metadata region remained pixel-identical. Wide Ocean changed 52,481 of 616,000 pixels with
zero changed pixels in the 275,000-pixel metadata region. A separate 505 × 493 Ocean GPU trace
recorded 694 fragment intervals over 16.076 seconds, averaging 8.397 ms (p95 9.557 ms). The first
wide Sphere sample includes two resize-related parent frames and requires a settled repeat.
A planned Mac pause check instead encountered the fixture's ten-minute end; its 0.36% stopped
sample is not accepted as a pause interaction test. The preceding Sphere capture still showed
active playback at 9:53. Fresh fixture playback was selected for the wide-window matrix.

Further warm/lifecycle checks:

- Ordinary Android short-metadata playback measured **4.19%** over 30 seconds, with no parent
  swaps. Its separate 30-second trace contains 35 scheduling callbacks and 29 elapsed-label
  bitmap replacements, not a continuous parent rendering loop. Progress pixels changed while
  all 818,884 artwork pixels remained unchanged.
- Android paused marquee repeated at **3.61%** over 30 seconds. Its separate trace contains
  909 animation callbacks, zero parent swaps and no bitmap replacements. The title changed
  11,855 pixels while artwork remained unchanged. Combined scrolling/progress measured
  **7.66%**, with 913 callbacks and 30 elapsed-label replacements in its separate trace; title
  and progress moved while artwork remained unchanged. These are not matched-rate A/B claims
  against earlier 16 kHz runs. A separate sampled stack profile locates the largest main-thread
  costs in Choreographer/SurfaceControl transaction IPC; its instrumented CPU is not substituted
  for clean CPU intervals.
- Wide Mac Ocean paused at **0.70%**, minimized at **0.96%**, and restored while paused at
  **0.72%**, with zero parent frames. Its paused image was identical; minimized captures were
  intentionally unavailable. Resumed playback measured **7.32%**, zero parent frames and
  61,947 changed visualizer pixels. A first subsequent resize sample failed its final visibility
  capture and is excluded; the unobscured repeat measured **6.86%**, zero parent frames.
- Mac long-metadata combined artwork playback measured **2.45%**, zero parent frames. The first
  paused transition measured 3.19% with two parent frames; its settled repeat measured **0.76%**
  with zero parent frames and 10,685 changed title pixels while artwork remained unchanged.
  Combined scrolling/progress/Ocean measured **7.16%**, zero parent frames, with both title and
  ink moving. These checks preserve smooth animation and normal playback update cadence.
- Wide Ocean's separate GPU trace contains 667 fragment intervals over 15.750 seconds, averaging
  **8.641 ms** (p95 9.974 ms), and 695 vertex intervals over 15.740 seconds. Raster work remains
  bounded despite displaying at 800 × 770; interval counts are not a presentation-latency metric.
- Wide Sphere's settled repeat had 331,340 changed visualizer pixels and unchanged metadata.
  A verified fresh-track pause measured **0.71%**, zero parent frames and zero changed
  visualizer pixels. The Mac app was left paused at its normal window size, and the Android
  benchmark was stopped after the capture matrix.

[App-only process summaries and GPU intervals](visualizers-241-evidence/resumed-device-tests-oct8/README.md)
retain startup/transition outliers, rejected experiments and visibility failures. Mac results support
the bounded direct presentation path. Android active visualizer and scrolling costs remain open;
physical iOS, Windows/Linux and other effects still require runtime verification. Keep #241 and
its draft PR open.

The compositor check adds a material qualification to the app-only results. Perfetto showed
SurfaceFlinger CPU of roughly 30% of one core during marquee motion, versus 2.6–4.6% in paused
short-metadata/artwork traces. Graphics tracing itself adds overhead, so the probe now supports
`--compositor`: it reads SurfaceFlinger's process CPU counters around the **untraced** CPU interval,
records the actual counter duration and kernel clock rate, and rejects a compositor restart.

| Untraced matched player state | Naviamp process CPU | SurfaceFlinger process CPU |
| --- | ---: | ---: |
| Short metadata, paused, warm (30 s) | 0.37% | 0.82% |
| Long metadata, paused, warm (30 s) | 3.42% | 21.01% |

No active virtual display or mirroring process was found. SurfaceFlinger manages the whole device;
these totals are not exact per-app attribution, but the repeated foreground state comparison
shows why zero parent redraws and low Naviamp CPU cannot establish an Android battery/performance
fix. The first direct-counter samples (long: 12.83% app / 23.63% compositor; short: 2.41% / 1.55%)
include startup/track-loading work and remain excluded from steady app claims. Compositor and
native presentation work must be included in subsequent renderer comparisons. Neither the 45 FPS
candidate nor scrolling presentation has final Android acceptance.
