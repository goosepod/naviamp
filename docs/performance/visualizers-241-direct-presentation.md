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
chasing those remaining points. Keep 60 FPS as the shared default; 45 FPS remains a probe option,
not a new setting. No export/import or translated UI copy changes are introduced.

Keep the umbrella issue open for other effects, Windows/Linux, full-app acceptance, physical iOS
measurements and future improvements. iOS compilation alone does not establish its performance.
The requested future direction is a full-player/background visualizer behind the normal Now Playing
controls. Implement this in common UI as a backdrop layer beneath artwork, text and controls, with
shared scrim/contrast treatment, unchanged hit testing and accessibility, and the same visibility and
FPS policy. Measure the real full-player surface at actual display resolution: the 640-pixel probe
supports the direction but does not establish full-screen GPU cost. The current foreground surface
must not simply cover controls. MilkDrop/projectM integration remains a separate step.

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
