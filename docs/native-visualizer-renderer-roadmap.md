# Native Visualizer Renderer Roadmap

This branch explores a second visualizer rendering pipeline for shader effects that do not fit well inside the current Skia RuntimeShader/SkSL model.

## Goal

Support richer visualizers, including full GLSL-style effects, without forcing every effect through Compose Canvas or SkSL.

The new renderer should stay as platform-agnostic as practical at the app boundary:

- Shared visualizer selection, settings, audio analysis, tempo, color palette, and frame input.
- Shared shader metadata and uniform names where possible.
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
  -> PlatformNativeVisualizerSurface
       Android: OpenGL ES surface
       Windows/Linux: OpenGL surface
       macOS: Metal surface if feasible, OpenGL fallback only as a spike
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

- Use OpenGL first.
- Evaluate LWJGL or a small purpose-built renderer module.
- Avoid tying this to Skiko internals.
- Keep the Compose integration isolated because embedded native surfaces can have layering/input issues.

### macOS

Metal is the right long-term target, but it is not as simple as "use GLSL":

- GLSL does not run directly on Metal.
- A Metal backend needs MSL shaders or a translation step.
- A JVM desktop app probably needs either a native bridge or a library that exposes Metal cleanly.

Pragmatic path:

- Keep SkSL/Skiko as the macOS fallback during Android/Windows bring-up.
- Spike a Metal backend only after the OpenGL path proves the renderer contract.
- If Metal becomes too expensive to maintain, keep macOS on SkSL for stable visualizers and use native GLSL on Android/Windows where it helps most.

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

### Phase 2: Android OpenGL ES Spike

- [x] Add an Android native visualizer surface behind the Player visualizer area.
- [x] Compile one simple GLSL shader with the standard uniform contract.
- [x] Upload the frequency texture each frame.
- [x] Add lifecycle handling for pause/resume/context loss.
- [x] Add logcat compile errors and FPS/draw timing with the existing `NaviampVisualizerPerf` shape.
- [ ] Blend the native GL surface background with album-art/player colors instead of rendering a black backing area.

### Phase 3: Heavy Shader Support

- [ ] Run full `Fluidic Nebulae` through the native GLSL backend.
- [ ] Run full or near-full `Ocean of Ink` with quality controls.
- [ ] Add render resolution scale for heavy shaders.
- [ ] Add max raymarch step settings per platform/tier.

### Phase 4: Desktop Native Spike

- [ ] Evaluate OpenGL desktop surface integration.
- [ ] Verify macOS layering/click behavior before committing to an embedded native surface.
- [ ] Decide whether macOS should use Metal, OpenGL fallback, or stay on SkSL.

## Acceptance Criteria

- Existing SkSL visualizers still work and remain the default.
- Android can run at least one GLSL visualizer without Skia RuntimeShader.
- Hidden/inactive visualizer cost remains near zero.
- Shader compile errors are visible in logs and fall back cleanly.
- Heavy shaders can be quality-gated instead of making the Player unusable.
