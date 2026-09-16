# Whole-window animation redraw investigation (#102)

## Status

Unresolved. The earlier waveform mitigation in PR #106 reduced CPU by disabling continuous
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

The local animation experiment was discarded. It did not deliver a convincing reduction in
combined-animation cost. Reducing offscreen rows did not materially improve CPU, so this
measurement does not justify a Home virtualization rewrite as the solution. Software rendering
was substantially worse; changing the product's rendering backend is not a fix.

## Reproduce the probe

```sh
./gradlew :core:ui:playerAnimationProbe
NAVIAMP_PROBE_ROWS=10 ./gradlew :core:ui:playerAnimationProbe
SKIKO_RENDER_API=SOFTWARE_COMPAT NAVIAMP_PROBE_ROWS=10 ./gradlew :core:ui:playerAnimationProbe
```

The probe opens its own window and exits after all four cases. It does not load or change
user data, playback, settings, or provider credentials. It is excluded from ordinary tests and
release packaging. Keep window size, visibility, display scale, refresh rate, backend, and
power conditions constant for comparisons. Run repeated trials before claiming improvements.

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
