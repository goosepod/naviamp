# Whole-window animation redraw investigation (#102)

## Status

The production player now renders metadata and waveform pixels once in shared Kotlin and presents
their motion through narrow platform compositor adapters. The shared UI still owns animation
timing, clipping, visibility, seeking, artist-link hit testing, keyboard input, and accessibility.
macOS and iOS consume declarative Core Animation keyframes, Android updates cached SurfaceFlinger
surfaces without redrawing Compose. The initial Windows/Linux implementation retained the Compose
fallback by default; the isolated Skia presenter required an opt-in environment variable. Windows
measurements later showed that opt-in was expensive. See the
[Windows DirectComposition follow-up](windows-raster-compositor-2026-09-19.md) for the correction
and measured replacement. Linux acceptance remains outside that Windows follow-up.

The macOS app passed playback, full/split transitions, menu and dialog layering, clipping,
accessibility inspection, and restored-surface checks. A first synchronous AWT/AppKit bridge could
deadlock during accessibility queries; the final bridge copies all presentation data before the
native boundary and queues layer mutations without waiting across the two event loops. The physical
Pixel probe passed visible-motion assertions with zero root, sibling, or window frames during every
measured animation state.

The earlier waveform mitigation reduced CPU by disabling continuous
interpolation on macOS and Android. It did not isolate redraws, address scrolling metadata,
or preserve the same smooth progress behavior on every platform. This branch removes that
platform policy and restores the same shared smooth-progress behavior on every platform.

The existing finding remains the starting point: animation can cause the entire application
surface to redraw. Caching waveform geometry or moving text through a graphics-layer transform
does not, by itself, establish an independent redraw boundary.

## Reproduction and measurements

On the local Apple Silicon macOS machine, the existing local-test app was paused at a stable
playback position with a long, visibly scrolling track title in the split player/Home layout.
Five stabilized `top` samples were 32.3%, 47.4%, 43.0%, 26.9%, and 22.9% CPU. A five-second
native sample included substantial Skia RenderNode/SkRecord replay. This observation happened
while build work was running, so it is reproduction evidence, not a controlled performance
comparison. A minimize attempt did not produce a verifiable native window-state change;
its samples are not accepted as minimized-window evidence.

An opt-in real-window probe now separates static UI, three scrolling metadata lines, a smooth
waveform, and both animations. It uses synthetic content, no provider or audio engine, a
1000 x 740 dp window, five seconds of warm-up per case, and ten seconds of measurement.
CPU is JVM process CPU time divided by elapsed time; 100% means one CPU core. WindowServer
CPU and GPU energy are not included. Single runs are diagnostic, not regression thresholds.

| Implementation/backend | Eager rows | Static | Marquee | Waveform | Both |
| --- | ---: | ---: | ---: | ---: | ---: |
| Original shared smooth animations, default macOS backend | 150 | 0.27% | 23.68% | 22.88% | 16.47% |
| Cached waveform path + text layer translation experiment, default backend | 150 | 0.81% | 21.55% | 16.85% | 16.80% |
| Same experiment, default backend | 10 | 0.41% | 20.17% | 17.14% | 21.60% |
| Same experiment, SOFTWARE_COMPAT | 10 | 0.16% | 84.60% | 101.42% | 104.69% |
| Separate ComposePanel / Metal surface | 150 | 0.46% | 23.24% | 19.15% | 21.11% |
| Direct Skia child surface (simplified progress line) | 150 | 0.99% | 13.68% | 14.79% | 12.00% |
| Cached text images / AWT repaint (simplified progress line) | 150 | 1.58% | 16.42% | 13.74% | 16.27% |
| Core Animation cached layers, trial 1 (simplified progress line) | 150 | 0.32% | 0.32% | 0.29% | 0.28% |
| Core Animation cached layers, trial 2 (simplified progress line) | 150 | 0.36% | 0.34% | 0.31% | 0.29% |
| Core Animation, shared motion and real waveform raster | 150 | 0.22% | 0.26% | 0.28% | 0.28% |

The separate ComposePanel, direct Skia surface, AWT repaint, and Core Animation probes all recorded
zero parent Compose frames during stabilized animation. The first two child Skia surfaces still
rendered approximately 750 frames per ten seconds. Isolation alone did not remove the CPU cost.
Core Animation instead receives cached image contents and declarative translation/reveal keyframes;
there is no application timer or rendering callback for each animation frame. This matches Apple's
[cached-layer animation model](https://developer.apple.com/library/archive/documentation/Cocoa/Conceptual/CoreAnimation_guide/CoreAnimationBasics/CoreAnimationBasics.html).

In trial 2, a label's native presentation translation changed from approximately -173 to 0 points,
and the progress layer width changed from 60.66 to 70.00 points during its measurement interval.
The native region had nonzero 280 x 660 point bounds. These checks rule out a frozen or zero-sized
surface as the reason for the low process CPU. They do not replace visual quality or system energy
measurement. The reproducible probe now asserts movement and zero parent frames, uses the shared
motion specification, and renders the real shared waveform into cached layers.

The local animation experiment was discarded. It did not deliver a convincing reduction in
combined-animation cost. Reducing offscreen rows did not materially improve CPU, so this
measurement does not justify a Home virtualization rewrite as the solution. Software rendering
was substantially worse; changing the product's rendering backend is not a fix.

## Production acceptance measurements

The packaged macOS app was measured in the split Home/player layout during real FLAC playback with
smooth progress active. After artwork and Home content stabilized, four consecutive samples were
5.5%, 4.3%, 5.0%, and 5.3% process CPU. This includes decoding, audio output, provider activity, and
the rest of the application; the earlier 16–47% reproduction samples were dominated by animation
redraw. The production result is therefore a real-app acceptance range, not a direct substitute for
the synthetic compositor's approximately 0.3% animation-only result. After pausing and restoring the
same window, four stabilized static samples were 0.5%, 0.8%, 0.4%, and 0.7%.

The dedicated probe ran on a physical Pixel, not an emulator. Static measured 0.46%
process CPU; marquee 4.87%; smooth waveform 3.74%; combined 3.96%; and restored static 0.05%.
Every state recorded zero parent-root draws, zero sibling draws, and zero Android window frames.
Two screenshots 750 ms apart also verified that the cached marquee pixels actually moved. Android
therefore pays for small SurfaceFlinger geometry transactions while avoiding the original whole-app
Compose redraw.

## Reproduce the probe

```sh
./gradlew :core:ui:playerAnimationProbe
NAVIAMP_PROBE_ROWS=10 ./gradlew :core:ui:playerAnimationProbe
SKIKO_RENDER_API=SOFTWARE_COMPAT NAVIAMP_PROBE_ROWS=10 ./gradlew :core:ui:playerAnimationProbe
NAVIAMP_PROBE_ISOLATED=true ./gradlew :core:ui:playerAnimationProbe
NAVIAMP_PROBE_RAW=true ./gradlew :core:ui:playerAnimationProbe
NAVIAMP_PROBE_RASTER=true ./gradlew :core:ui:playerAnimationProbe
NAVIAMP_PROBE_COMPOSITOR=true ./gradlew :core:ui:playerAnimationProbe
```

The probe opens its own window and exits after all four cases. It does not load or change
user data, playback, settings, or provider credentials. It is excluded from ordinary tests and
release packaging. Keep window size, visibility, display scale, refresh rate, backend, and
power conditions constant for comparisons. Run repeated trials before claiming improvements.

The compositor mode requires macOS, a JDK, and Apple's command-line compiler. Gradle builds its
test-only JNI adapter automatically under `core/ui/build/animation-probe`; no manually prepared
`/tmp` library is required. This native adapter is not included in the application or releases.

## Production integration

The independent surface remains an implementation detail behind the shared raster contract. Compose
retains the actual background, every pointer and keyboard target, scrub gestures, artist links, and
the accessibility tree. Native pixels are hidden whenever an owned menu or dialog covers the player,
then restored afterward. Rounded clipping and layout bounds are supplied by shared UI. Window and
activity lifecycle adapters only publish visibility and own the native surface lifetime.

`AGENTS.md` now requires measured low-cost small animations, proof of actual motion and redraw
scope, shared ownership, lifecycle/interaction checks, and real-app acceptance before closing a fix.

The shared-motion/real-waveform run passed all movement and parent-frame assertions. Targeted
common-policy and Compose interaction tests cover cached-pixel reuse, moving artist-link hit tests,
keyboard and accessibility actions, and hide/restore lifecycle behavior. Android, Desktop, and iOS
hosts only install their platform presenter and publish native lifecycle facts.

## Acceptance gate

The reproducible probe and shared tests remain the regression gate. Continuous animation changes
must keep pixels cached, keep parent/sibling/window frame counts at zero, prove visible motion, and
retain seeking, links, accessibility, clipping, overlays, and hide/restore behavior. Platform hosts
may only provide the native presentation and lifecycle boundary. Disabling animation, reducing
playback updates, or moving product behavior into a platform module does not satisfy the gate.
