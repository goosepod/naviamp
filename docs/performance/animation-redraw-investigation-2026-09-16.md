# Whole-window animation redraw investigation (#102)

## Status

The real-app fix is unfinished. A Core Animation probe now demonstrates smooth motion near idle
process CPU without Compose parent frames. Native presentation-layer samples verify that both
the labels and progress edge move. Production integration, overlap/input/accessibility verification,
and system compositor/GPU measurements remain acceptance requirements.

The earlier waveform mitigation in PR #106 reduced CPU by disabling continuous
interpolation on macOS and Android. It did not isolate redraws, address scrolling metadata,
or preserve the same smooth progress behavior on every platform. This branch removes that
platform policy and restores the original shared smooth-progress behavior while the actual
rendering fix is investigated. Do not merge it as a completed CPU fix.

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

## Production integration constraint

An independent native surface is not sufficient for the player. Compose's `SwingPanel` documentation
states that native components normally sit above Compose content; interop blending changes this
stacking behavior. A production adapter must preserve menus/dialogs, rounded clipping, the actual
player background, artist-link hit targets, scrub gestures, and accessibility. The probe does not
yet establish those properties. Do not enable a native overlay in the application based solely on
the process CPU result. The remaining work is a compositing integration, not further waveform-path
micro-optimization.

`AGENTS.md` now requires measured low-cost small animations, proof of actual motion and redraw
scope, shared ownership, lifecycle/interaction checks, and real-app acceptance before closing a fix.

The shared-motion/real-waveform run passed all movement and parent-frame assertions. Eleven
targeted common-policy and Compose UI tests passed, along with Android and iOS Simulator
compilation. No Android, Desktop, or iOS production adapter was changed for this experiment;
the Objective-C++/JAWT adapter exists only under `jvmTest/native`.

## Next implementation and acceptance gate

1. Establish a measurable independent presentation boundary for animated regions. A Compose
   graphics layer within the same root scene is not sufficient evidence of native surface isolation.
2. Define the common rendering/content contract and animation, visibility, input, and accessibility
   behavior first. Native adapters may only own the actual child surface and its native lifetime.
   Do not implement title or scrubber product behavior again in platform code.
3. Prove with frame/draw instrumentation that title and waveform animation do not redraw the static
   parent surface; measure CPU/GPU impact on the real app as well as the synthetic probe.
4. Preserve smooth movement, seeking, layout direction, resizing, scale changes, popovers, clipping,
   accessibility, and keyboard/touch interaction. Stop work when a surface is hidden or detached.
5. Verify title-only, artist-only, album-only, scrubber-only, combined, paused, minimized, restored,
   and visualizer states on macOS and Android, with iOS compilation and adapter verification.

The issue and PR remain open until that evidence exists. Disabling animation, reducing playback
updates, or moving behavior into a platform module does not satisfy this gate.
