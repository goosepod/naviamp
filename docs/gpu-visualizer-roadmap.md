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

## Phase 1: Renderer Spike For Current Visualizer

- [ ] Pick the GPU rendering integration path for desktop Compose.
  - Candidate: Skiko/Compose interop with a GPU surface.
  - Candidate: OpenGL/Metal/Vulkan bridge hidden behind a desktop renderer abstraction.
  - Candidate: multiplatform graphics layer if it keeps Android viable later.
- [ ] Create a small desktop-only visualizer renderer abstraction.
- [ ] Feed the current `PlaybackVisualizerFrame.bands` data into the renderer without making the full now-playing UI recompose.
- [ ] Recreate the current visualizer as the first GPU visualizer.
- [ ] Preserve click/tap behavior for toggling album art and visualizer.
- [ ] Keep visualizer hidden-state cost at zero or near zero.

## Phase 2: Timing And Data Flow

- [ ] Separate audio analysis cadence from render cadence.
- [ ] Add interpolation/smoothing on the render side so 8 Hz audio frames can drive smoother visuals.
- [ ] Decide whether visualizer render cadence should target display refresh, 60 fps, or an adaptive lower rate.
- [ ] Ensure playback pause, buffering, and stopped states decay visually instead of freezing awkwardly.
- [ ] Add lifecycle handling so GPU resources are released when the visualizer leaves composition.

## Phase 3: Visualizer Catalog

- [ ] Define a visualizer descriptor model with id, display name, capabilities, and settings.
- [ ] Add a visualizer selection control in the Player UI.
- [ ] Persist the selected visualizer in settings.
- [ ] Support per-visualizer settings without cluttering the Player screen.
- [ ] Add at least two follow-up GPU visualizer concepts after the current visualizer is ported.

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
