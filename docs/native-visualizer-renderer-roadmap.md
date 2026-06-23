# Native Visualizer Renderer Roadmap

This branch explores a second visualizer rendering pipeline for shader effects that do not fit well inside the current Skia RuntimeShader/SkSL model.

## Goal

Support richer visualizers, including full GLSL-style effects, without manually approximating imported shaders per platform.

The new renderer should stay as platform-agnostic as practical at the app boundary:

- Shared visualizer selection, settings, audio analysis, tempo, color palette, and frame input.
- Shared shader metadata, uniform names, and canonical shader identity.
- Platform-specific GPU context and surface ownership.
- Platform-specific shader dialects where necessary.

## Why SkSL Is Not Enough

The current renderer is useful for portable fragment effects, but it has hard limits:

- It is tied to Skia/RuntimeShader instead of a general programmable graphics pipeline.
- It does not expose normal GLSL texture/sampler workflows directly.
- It makes multi-pass effects, framebuffer history, and reduced-resolution passes awkward.
- It does not give us direct control over GPU resources, render targets, or pipeline quality.
- It makes full raymarching/water/volumetric shaders harder to tune per platform.

## Target Architecture

Keep the existing SkSL renderer as the stable default while adding a native renderer path behind the same Player UI.

```text
Now Playing UI
  -> shared visualizer state
  -> VisualizerFrameInput
       bands[32]
       bass/mids/highs/energy
       tempo
       palette colors
       active/paused state
       elapsed time
  -> platform renderer
       Android: OpenGL ES surface
       Desktop simple effects: Compose/Skia RuntimeShader surface
       Desktop imported shaders: native host required before the effect is supported
```

## Backend Strategy

### Android

Start here because it is the current pain point.

- Use OpenGL ES 3.x.
- Prefer a contained native visualizer `SurfaceView`/`GLSurfaceView` embedded under a Compose wrapper.
- Upload the 32-band spectrum as a small 1D/2D texture so GLSL files can use `u_frequencyTexture`.
- Drive rendering from the existing `VisualizerRenderPolicy`.
- Pause the render thread when the visualizer is hidden or playback is inactive.
- Support reduced internal render resolution for heavy shaders.

### Windows And Linux Desktop

- Keep using SkSL until there is a platform-native surface that composes safely inside the existing app window.
- If this becomes a priority, evaluate OpenGL through a purpose-built renderer module, not by embedding AWT into Compose.
- Keep the Compose integration isolated because embedded native surfaces can have layering/input issues.

### macOS

Metal is the right long-term target, but it is not as simple as "use GLSL":

- GLSL does not run directly on Metal.
- A Metal backend needs MSL shaders or a translation step.
- A JVM desktop app probably needs either a native bridge or a library that exposes Metal cleanly.

Pragmatic path:

- Keep SkSL/Skiko as the macOS renderer for this branch.
- Do not use the LWJGL/AWT OpenGL bridge in-app; macOS testing showed driver crashes and AWT/AppKit run-loop hangs when embedded in the Compose window.
- Spike a real Metal backend only if we are ready to own a native view bridge and MSL/translation workflow.
- If Metal becomes too expensive to maintain, keep macOS on SkSL for stable/simple visualizers and mark imported native shaders unsupported on macOS instead of approximating them.

## Shader Contract

Use a GLSL-oriented contract for the native pipeline:

```glsl
uniform float u_time;
uniform vec2 u_resolution;
uniform float u_energyLevel;
uniform float u_bassLevel;
uniform float u_midLevel;
uniform float u_trebleLevel;
uniform float u_spectralCentroid;
uniform float u_tempoBpm;
uniform float u_beatDetected;
uniform sampler2D u_frequencyTexture;
```

The first pass can derive `u_beatDetected` and `u_spectralCentroid` from the current 32-band frame. A later pass can improve this with real onset/BPM analysis.

## Implementation Phases

### Phase 1: Renderer Boundary

- [x] Add a shared native visualizer capability flag or renderer mode.
- [x] Define a `VisualizerFrameInput` data model independent of Skia.
- [x] Keep existing SkSL visualizers unchanged.
- [x] Route only selected experimental visualizers to the native backend.
- [x] Add shared native shader definitions so imported effects have canonical shader identities independent of platform renderer code.
- [x] Stop mapping imported native-only visualizers to desktop SkSL approximations.
- [x] Move canonical GLSL source text out of platform renderer files and into shared shader assets/source consumed by each native backend.

### Phase 2: Android OpenGL ES Spike

- [x] Add an Android native visualizer surface behind the Player visualizer area.
- [x] Compile one simple GLSL shader with the standard uniform contract.
- [x] Upload the frequency texture each frame.
- [x] Add lifecycle handling for pause/resume/context loss.
- [x] Add logcat compile errors and FPS/draw timing with the existing `NaviampVisualizerPerf` shape.
- [x] Blend the native GL surface background with album-art/player colors instead of rendering a black backing area.
- [x] Add album-art texture sampling to the native GL renderer so effects can react to the actual cover image, not only extracted player colors.

### Phase 3: Heavy Shader Support

- [x] Run full `Fluidic Nebulae` through the native GLSL backend.
- [x] Run full or near-full `Ocean of Ink` with quality controls.
- [x] Add `Analog Signal Failure` through the native GLSL backend with album-art/player palette colors.
- [x] Add `Liquid sphere` through the native GLSL backend with album-art/player palette colors.
- [x] Add render resolution scale for heavy shaders.
- [x] Add max raymarch step settings per platform/tier.

### Native Upgrade Candidates

- `Audio tunnel`: best next candidate. The current Skia version is constrained by 2D drawing, while the desired effect is a camera-moving-through-geometry shader with stable ribs, depth, turns, and audio deformation.
- `Wave interference`: strong candidate. It has shown visual stability issues in the Skia path and would map cleanly to a native full-screen fragment shader.
- `Particle galaxy` / `Particle field`: optional candidate. Native rendering would allow more particles and better blending, but current behavior is already acceptable.
- `Spectral ridge`, `Mountains`, `Pixel mountains`, and `Pixel ridge`: lower priority. These were tuned on the existing path and currently feel good; move them only if Android/Windows profiling shows real cost.
- `Reactive bars`, `Fluid gradient`, `Vinyl groove`, `Ribbon trail`, and `Album art`: keep on Skia for now unless a specific visual limitation appears.

### Phase 4: Desktop Renderer Decision

- [x] Evaluate embedded LWJGL/AWT OpenGL surface integration.
- [x] Reject the embedded LWJGL/AWT path for the in-app macOS renderer after testing exposed Apple driver crashes and AWT/AppKit run-loop hangs inside the Compose window.
- [x] Remove the desktop LWJGL/AWT implementation and dependencies from the active app path.
- [x] Replace desktop SkSL approximations for imported shaders with a native-renderer-required placeholder.
- [ ] Add a real desktop native shader host so `Analog signal failure`, `Fluidic nebulae`, `Ocean horizon`, `Ocean of ink`, and `Liquid sphere` can run from their canonical shader sources.
- [ ] Revisit a true macOS native renderer only as a Metal/AppKit bridge, not as AWT-embedded OpenGL.

## Acceptance Criteria

- Existing SkSL visualizers still work and remain the desktop default.
- Android can run GLSL visualizers without Skia RuntimeShader.
- Hidden/inactive visualizer cost remains near zero.
- Shader compile errors are visible in logs and fall back cleanly.
- Heavy shaders can be quality-gated instead of making the Player unusable.
