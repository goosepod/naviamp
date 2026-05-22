# GPU Visualizer Roadmap

This tracks the work needed to replace the current Compose Canvas visualizer with GPU-rendered visualizers that stay smooth, keep CPU use low, and can eventually support a user-selectable visualizer library.

## Goals

- Start by matching the current waveform-style visualizer visually and behaviorally.
- Move visualizer rendering off Compose Canvas draw calls and onto a GPU-backed renderer.
- Keep audio analysis cadence, smoothing, and rendering cadence explicit and measurable.
- Make the visualizer system extensible so users can flip through multiple GPU-rendered effects.
- Preserve low CPU use when the visualizer is hidden, the app is on Home, or playback is paused.

## Current Baseline

- The current visualizer is rendered in `core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampNowPlayingUi.kt` with Compose `Canvas`.
- Desktop visualizer data is supplied by `apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/Main.kt`.
- The current macOS optimized cadence samples visualizer frames every 125 ms.
- Recent profiling shows the remaining visualizer cost is mostly Skiko/Metal drawing of the point/line shapes, not FFT extraction or lyrics.
- The first GPU-backed baseline uses a JVM Skia runtime shader behind `PlatformLiveVisualizerSurface`, with Android still using the Canvas fallback.
- An embedded desktop `SwingPanel`/`SkiaLayer` surface was tested and rejected for the Player art area because it did not compose cleanly on macOS: the surface showed white/black backing rectangles, swallowed clicks that should toggle back to album art, and could remain layered over album art after toggling.

## Phase 1: Renderer Spike For Current Visualizer

- [x] Pick the GPU rendering integration path for the first desktop Compose spike.
  - Candidate: Skiko/Compose interop with a GPU surface.
  - Candidate: OpenGL/Metal/Vulkan bridge hidden behind a desktop renderer abstraction.
  - Candidate: multiplatform graphics layer if it keeps Android viable later.
- [x] Create a small platform visualizer renderer seam.
- [x] Feed the current `PlaybackVisualizerFrame.bands` data into the renderer without making the full now-playing UI recompose.
- [x] Recreate the current visualizer as the first GPU-backed visualizer baseline.
- [x] Preserve click/tap behavior for toggling album art and visualizer.
- [x] Keep visualizer hidden-state cost at zero or near zero.
- [ ] Decide whether the shader baseline is enough, or replace it with a dedicated GPU surface/scene for the first production visualizer.

## Phase 2: Timing And Data Flow

- [ ] Separate audio analysis cadence from render cadence.
- [x] Add interpolation/smoothing on the render side so 8 Hz audio frames can drive smoother visuals.
- [ ] Target a measured 60 fps render path for active visualizers, with adaptive fallback only if profiling shows it is needed.
- [x] Switch back to album art while playback is paused/stopped, then restore the visualizer automatically when playback resumes if the user had it enabled.
- [ ] Add optional visual decay for buffering/loading transitions without continuing full-rate rendering indefinitely.
- [ ] Add lifecycle handling so GPU resources are released when the visualizer leaves composition.

## Phase 3: Visualizer Catalog

- [x] Define the first visualizer catalog entries.
- [x] Add a visualizer selection control in the Player UI.
- [ ] Persist the selected visualizer in settings.
- [ ] Support per-visualizer settings without cluttering the Player screen.
- [x] Add at least two follow-up GPU visualizer concepts after the current visualizer is ported.
- [x] Current shader catalog: Reactive bars, Fluid gradient, Audio sphere, Audio tunnel, Ribbon trail, Frequency terrain, Particle field, Wave interference, Vinyl groove.

## Deferred Visualizer Concepts

These are not implemented yet and should be treated as follow-up work rather than part of the current SkSL fragment-shader catalog.

- FFT terrain/mountain with true time-history depth. The current Frequency terrain is a procedural shader approximation; the full version needs a retained FFT history texture or mesh buffer.
- Particle galaxy with attractors, clustering, and richer emission behavior. The current Particle field is the first lightweight particle-style shader.
- Album-art-integrated visualizer: reactive warping, depth/parallax from luminance, and particle emission from bright image regions. This needs album-art texture upload into the renderer.
- Raymarching/SDF scenes: fractals, organic blobs, surreal geometry, and richer lighting. This is likely Phase 3+ because it has the highest GPU cost and needs performance gates.
- Volumetric effects and full procedural 3D scenes. These should wait until the renderer abstraction can expose quality/performance settings.

## Phase 4: Performance Gates

- [ ] Measure Player with visualizer off, lyrics off.
- [ ] Measure Player with current GPU visualizer on, lyrics off.
- [ ] Measure Player with current GPU visualizer on, lyrics on.
- [ ] Measure Home playback with no focused Naviamp window interaction.
- [ ] Compare macOS packaged Metal build against Windows packaged renderer behavior.
- [ ] Record CPU, memory, thread count, and sample hotspots in `docs/desktop-performance-optimization.md`.

## Open Questions

- Can the renderer stay shared enough for Android, or should the first pass be desktop-only?
- Should the first GPU renderer use the same 32-band data, or should the analyzer expose richer spectrum data?
- How much motion should be generated from audio data versus visualizer-specific animation state?
- What renderer backend gives the best balance of smooth visuals, packaging simplicity, and maintainability?
