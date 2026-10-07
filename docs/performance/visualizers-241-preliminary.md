# Audio Sphere and Analog Signal Failure: preliminary Mac investigation

Issue: https://github.com/goosepod/naviamp/issues/241

This is a preliminary investigation and shared shader correctness corrections, not a completed
performance fix. Testing is local to the Mac, as requested by the owner. Android and iOS checks
here establish compilation, not device performance. The Onn 4K Pro is not part of this pass.

## Rendering paths and required work

Audio Sphere uses a runtime shader drawn into the Compose/Skia canvas on Desktop and iOS, and
Android RuntimeShader on API 33+. Older Android versions use a Canvas approximation. Analog Signal
Failure has canonical GLSL in common code and prefers native rendering: GLSurfaceView on Android,
Metal on Mac/iOS, and OpenGL on Windows. Linux and unavailable native renderers use its translated
Skia fallback. These are two shader families, rather than equivalent presentation backends.

The native Desktop and iOS image paths submit a GPU render, synchronously wait for completion,
read the pixels into CPU memory, construct a raster image, and draw/upload that image through Skia.
See `NativeMetalVisualizerHost.jvm.kt`, `IosMetalVisualizerRenderer.kt`, and
`native/visualizer-metal/src/naviamp_visualizer_metal.mm`. The Windows image path similarly calls
`glFinish` and `glReadPixels`. Android's GLSurfaceView presents directly and should not be replaced
with a CPU readback path merely to make the backend names match.

Both examples drive a host-owned Compose frame-clock loop. On the measured 75 Hz display, their
full-quality 16 ms threshold frequently accepts every second display tick. The fixture measures a
roughly 26 ms median visualizer drawing interval while the parent renders near 75 Hz. Incoming
band updates also invalidate drawing independently. Reducing the target rate would not resolve
this mismatch or the continuous parent rendering.

The larger fix needs a common owner for frame preparation, timing, lifecycle, visibility, smoothing,
capability interpretation, and fallback, with thin adapters reporting native presentation facts
and presenting without readback. Reuse the platform boundary for direct GPU surfaces, preserve
clipping/input/accessibility and popup ordering, and measure the real app. A native surface is only
a hypothesis until that measurement passes. Existing host-owned product decisions and duplicated
rendering orchestration must move into common code when those paths are changed.

Moving Analog to the current Skia path is a useful comparison and potential intermediate step for
Desktop/iOS, but does not eliminate parent rendering. Moving Audio Sphere into the existing native
readback path would introduce that path's overhead. Prefer a shared shader/input/presentation
contract and copy-free presentation, rather than selecting one backend for its name alone.

## Conditions and limits

Apple M1, macOS 27.0.1 (26A434), Java 21.0.12.1, Metal, AC power, primary 2560x1440 display at 75 Hz and 1x scale.
Window 1000x740 at (800,400), client 1000x712, visualizer 358x358. Both backends use full quality.
No audio device, provider account, production settings, or user data is accessed by the fixture.
The test supplies the same deterministic 32-band signal at 20 Hz independently of rendering.

The baseline UI comes from accepted main `584ae4b2`; fixture and observer code are test-only.
The staged native library is identified by its retained artifact hash. Baseline and candidate
artifact hashes distinguish the actual measured binaries.

`NaviampVisualizerPerformanceProbe.kt` exercises the production visualizer surfaces in a visible,
floating real window. `ApplicationRenderProbe.java` samples ten-second one-core process CPU,
compilation time, and actual Skiko parent frames. Physical before/after captures are outside the
CPU interval. The native guard verifies ownership and obstruction; it explicitly recognizes the
fixture's floating window as well as ordinary windows. Failed captures remain failed evidence.
Draw duration is wall time of draw submission, not GPU duration. Drawing interval statistics span
the entire fixture state, including warmup, and are distinct from the observer's ten-second samples.

This fixture has no application shell or audio engine. Its combined state exercises marquee and
smooth progress with the fixture's inline animation environment, not the packaged app's native
raster host. It is useful for comparing these visualizer paths, but cannot certify full-app CPU,
popup ordering, input, accessibility, hidden/minimized/restored behavior, resize, thermal stability,
or Android/iOS GPU performance. Those remain required before accepting the performance fix.

Initial obscured-window samples and samples taken before the visibility guard recognized floating
windows are rejected. One static repeat overlapped an exploratory GPU trace and is excluded from
the static CPU baseline. Retain compilation outliers; do not subtract compilation to claim a pass.

## Initial visible baseline

The native-enabled run uses Skia for Audio Sphere and native Metal readback for Analog. Disabling
native rendering makes Analog use the runtime shader. Values are medians of visible measurements;
the evidence directory retains individual samples and capture failures.

| State | CPU, % of one core | Parent frames / 10 s | Draw submission, ms |
| --- | ---: | ---: | ---: |
| Static, native-enabled run, uncontaminated repeats | 0.385 | 0 | — |
| Audio Sphere, native-enabled run | 15.023 | 764 | 0.063 |
| Analog, native Metal readback | 24.234 | 760 | 1.850 |
| Analog, runtime shader | 14.586 | 756 | 0.081 |
| Audio Sphere plus inline marquee/progress, native-enabled run | 19.336 | 762 | — |
| Analog plus inline marquee/progress, native readback | 19.165 | 744 | — |
| Analog plus inline marquee/progress, runtime shader | 10.333 | 745 | — |
| Paused Audio Sphere, native-enabled run | 0.936 | 0 | — |
| Paused Analog, native readback | 1.012 | 0 | — |

These are exploratory runs, not randomized controlled trials. The runtime path has substantially
less draw-submission overhead, but both active examples still have a material CPU baseline and
continuous parent rendering. Neither path is accepted as performant.

In separate ten-second Metal traces, Analog's native run recorded 74.090 ms of app Fragment work,
8.716 ms Vertex, and 19.532 ms Compute. Its runtime-shader run recorded 90.750 ms Fragment and
5.633 ms Vertex. These channel intervals can overlap; they are not total GPU utilization or energy.
Whole-desktop WindowServer activity is retained separately and cannot be attributed entirely to
the fixture. The comparatively small GPU work supports investigating presentation/scheduling
overhead before rewriting these effects as substantially simpler visuals.

## Rejected sampling trial

- Replace the 32-entry linear uniform lookup with a balanced, constant-index lookup. This preserves
  [AGSL's array indexing restrictions](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference)
  and bounds the decision depth at five.
- Restore normalized linear frequency-texture sampling in the GLSL-to-SkSL translator. The old
  fallback selected whole bands, unlike the native linear texture, producing a different stepped
  trace. The trial also interpolates Audio Sphere's angular bands for a smoother contour.

The trial did not establish a CPU improvement: median standalone CPU was 20.638% for Sphere,
17.485% for runtime-shader Analog and 24.585% for native Analog, with continuous parent rendering.
Several Sphere combined captures were obscured and are rejected. Analog's runtime-shader GPU
Fragment work increased from 90.750 ms to 139.077 ms in separate ten-second traces, despite fewer
Fragment intervals (795 versus 637). Linear interpolation doubles the uniform-lookup calls;
fewer source-level decisions are not proof of lower GPU cost.

The combined sampling trial was rolled back rather than accepted as an optimization. Its measurements,
artifact hashes and [experimental patch](visualizers-241-evidence/rejected-sampling-trial.patch)
remain available. The patch includes a subsequent angular-seam correction whose final Sphere
performance was not measured, and applies to the recorded main baseline rather than the final
retained test changes. Future work should evaluate hardware frequency-texture sampling through
the common rendering contract, instead of assuming uniform-array interpolation is cheap.

## Retained shared change and probes

- Replace reversed `smoothstep` edges in these two effects with ascending edges and an explicit
  complement. Reversed edges are undefined in [GLSL ES 3.00](https://registry.khronos.org/OpenGL/specs/es/3.0/GLSL_ES_Specification_3.00.pdf);
  the intended descending envelope is explicit.
- Add rendered tests for all band slots, out-of-range indices/amplitudes, the Sphere body/rim/fade,
  shader compilation, and a focused nonblank/moving Analog native-Metal output test.

The owner identified that the envelope-only runtime-shader output was visibly broken into disconnected
segments, rather than the expected continuous Analog trace. Inspection confirmed the fallback used
whole-band sampling while the native sampler linearly filters its frequency texture. The final
shared correction restores normalized linear sampling (32 texels, half-texel centers, clamp-to-edge)
in the GLSL-to-SkSL translator. A rendered alternating-extremes test checks centers, interpolated
positions and clamped edges. This is retained for visual correctness, not as a performance gain.
The owner confirmed the corrected continuous trace in the visible Mac fixture. The balanced lookup
and Sphere angular interpolation trial remain rolled back. Preserve that continuous trace as the
visual reference for subsequent performance work; broken segments are not an acceptable shortcut.

No production Android, Desktop, or iOS adapter is changed. Backend selection,
playback updates and render-quality tiers are unchanged. This is a correctness/measurement
checkpoint, not the larger presentation fix.

## Envelope-only checkpoint measurements

These precede the owner's continuous-trace correction and are not measurements of the final
interpolated fallback. Two visible ten-second samples per state gave these medians:

| State | CPU, % of one core | Parent frames / 10 s |
| --- | ---: | ---: |
| Static | 1.630 | 0 |
| Audio Sphere | 20.932 | 750.5 |
| Analog, runtime shader | 18.349 | 762 |
| Analog, native Metal readback | 25.714 | 764.5 |
| Audio Sphere plus inline marquee/progress | 19.380 | 766 |
| Analog runtime shader plus inline marquee/progress | 19.381 | 765 |
| Paused Sphere | 1.050 | 0 |
| Paused Analog | 0.511 | 0 |

Compilation and run-order variation prevent a reliable speedup claim. Separate Metal traces
recorded 77.873 ms Fragment / 6.510 ms Vertex for runtime Analog and 57.826 ms Fragment /
10.628 ms Vertex / 30.649 ms Compute for native Analog. These overlapping channel totals are not
GPU utilization. Physical captures showed motion in each active example (121,932–127,649 changed
visualizer pixels), with zero changes in the static sibling region. Motion alone does not certify
smooth cadence or presentation isolation.

A separate 15-second runtime-Analog JFR profile recorded 646 execution/native samples, 643 in
`skiko-dispatcher-to-block-on`. The leading leaf methods were `DisplayLinkThrottler.waitVSync`
(377) and `MetalContextHandler.makeMetalRenderTarget` (244). Native samples can include waits;
this identifies presentation code to investigate, not a CPU-time attribution to waiting.

## Continuous-trace correction measurements

The final fallback preserves the accepted continuous trace. Separate visible runs on the same Mac
record its CPU/frame behavior and GPU cost in `continuous/` and the corresponding Metal summary.
One early standalone sample overlapped the final Gradle validation; it remains retained but is
excluded from the final standalone median. A fresh pair of samples follows the GPU recording,
with neither Gradle nor GPU tracing running during their CPU intervals.
| State | CPU, % of one core | Parent frames / 10 s |
| --- | ---: | ---: |
| Static | 0.707 | 0 |
| Corrected Analog, runtime shader, warmed isolated repeats | 14.899 | 764 |
| Analog, native Metal readback | 24.014 | 747 |
| Corrected Analog plus inline marquee/progress | 20.960 | 762 |
| Paused Analog | 0.953 | 0 |

The separate ten-second GPU trace records 109.427 ms Fragment and 5.440 ms Vertex work.
Interpolation therefore has a measurable shader cost; it must be preserved while optimizing
presentation and eventually evaluating hardware frequency-texture sampling. The corrected
before/after capture changes 127,410 visualizer pixels and zero static sibling pixels.

These are correctness evidence and performance investigation data, not an accepted optimization.

## Proposed acceptance budget for the next implementation

For this Mac reference at 358x358 and the same display/window/power conditions, aim for at most
five CPU percentage points above static playback, no sustained parent-surface rendering caused
by the visualizer, and smooth display-paced motion without lowering playback update frequency.
Record actual per-frame GPU/presentation percentiles and missed frames; cumulative GPU channel
totals are not a substitute. Apply the combined, lifecycle, input, clipping and accessibility
matrix in the full app before accepting this budget. Revisit larger sizes with explicit evidence,
not a silent reduction in animation quality. Android/iOS need their own later runtime measurements.

## Reproduction

Build the observer/visibility guard as described in `scripts/animation-probe/README-visible-app.md`.
Run `:core:ui:visualizerPerformanceProbe` with `NAVIAMP_VISUALIZER_PROBE_OUTPUT` set to an empty
output directory. Set `NAVIAMP_VISUALIZER_PROBE_AGENT` to `probe.jar=OUTPUT`,
`NAVIAMP_VISUALIZER_PROBE_GUARD` to the guard executable, and `NAVIAMP_VISUALIZER_METAL_DIR` to
the built native-library directory. `NAVIAMP_VISUALIZER_PROBE_NATIVE=true|false` selects the
initial Mac backend. A staged app can instead use `naviamp.visualizer.probe.output` as a JVM property.

Run `python3 scripts/animation-probe/sample-visualizers.py OUTPUT --prefix UNIQUE`. States beginning
with `native-` or `skia-` recreate the production surface with that test-only backend override,
allowing paired comparisons in one process. Use a fresh prefix; the sampler refuses to overwrite
retained measurements. Keep the fixture physically unobscured and check both capture flags and
actual pixel movement. Finish CPU sampling before recording GPU traces.

## Validation and remaining work

The rejected shared trial compiled for Android, JVM and both iOS Arm64 targets. Its rendered
sampling tests and focused moving-pixel Analog Metal test passed, demonstrating that functional
tests alone do not establish performance. The final shared corrections pass 15 focused JVM tests, including rendered frequency interpolation
and the opt-in moving-pixel Analog native Metal test. Shared UI compilation for Android, JVM,
iOS Arm64 and iOS Simulator Arm64 and `verifyCoreFirstArchitecture` pass. Visible measurements
and exact measured artifact hashes are retained in the evidence directory; they do not establish
the full-app acceptance budget.

An explicitly enabled broader native test failed for existing Audio Tunnel Metal translation
(two-argument GLSL `atan`, among runtime compiler errors). The optional command-line Metal compiler
test also failed because this Mac lacks the separately installed Metal Toolchain. These are retained
findings, not silently passing native coverage. Runtime Metal compilation of the focused Analog
shader is checked separately. Do not expand this pass into other visualizer fixes without tracking
the work and updating scope.

Keep #241 open. Next: common rendering/presentation ownership, a copy-free isolated surface, explicit
capability-aware fallback, display-paced scheduling, and the full-app performance/visual matrix.
