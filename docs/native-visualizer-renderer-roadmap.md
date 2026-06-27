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

- Windows now has an experimental offscreen OpenGL renderer packaged as `naviamp_visualizer_opengl.dll`.
- The Windows path renders GLSL effects into a hidden OpenGL context, reads BGRA pixels back, and draws them through the existing Compose visualizer area so it avoids embedded native child-window layering issues.
- Packaged Windows builds enable the experimental path with `-Dnaviamp.visualizer.windowsOpenGl=true`.
- The default Windows `balanced` profile keeps native OpenGL visualizers at full internal resolution; only the explicit `constrained` profile downscales heavy shaders.
- A direct `SwingPanel`/AWT OpenGL surface was tested on Windows and rejected for the Player visualizer because it rendered above Compose menus and other overlay UI.
- The offscreen Windows OpenGL path defaults to at least `2x` pixel scale; it can be overridden with `-Dnaviamp.visualizer.windowsPixelScale=1..4`.
- Desktop visualizer FFT/audio frame sampling now uses a desktop-specific `33ms` cadence while visible and active; the older shared `125ms` cadence made audio-reactive motion feel closer to 8fps even when shader animation frames were rendering faster.
- Windows packaged builds should use Skiko's default renderer. Forcing `-Dskiko.renderApi=OPENGL` reduced idle CPU in an earlier no-track test but made visualizers visibly choppy; the default renderer kept detailed visualizers fluid while staying around 8-9% CPU in Task Manager during manual testing.
- Linux should stay on SkSL until the Windows OpenGL path is validated and the native module is generalized beyond WGL.

### macOS

Metal is the right long-term target, but it is not as simple as "use GLSL":

- GLSL does not run directly on Metal.
- A Metal backend needs MSL shaders or a translation step.
- A JVM desktop app probably needs either a native bridge or a library that exposes Metal cleanly.

Pragmatic path:

- Keep SkSL/Skiko as the macOS default renderer until the native view host is complete.
- Do not use the LWJGL/AWT OpenGL bridge in-app; macOS testing showed driver crashes and AWT/AppKit run-loop hangs when embedded in the Compose window.
- Use a deterministic GLSL-to-MSL translation step for the canonical native shader sources instead of hand-maintaining a second shader catalog.
- Keep the macOS Metal backend opt-in while it is being built (`-Dnaviamp.visualizer.macosMetal=true`) so stable desktop behavior does not change until the host can render into the app safely.
- Use an offscreen Metal render target and draw the returned BGRA image through Compose Canvas. Avoid a heavyweight `SwingPanel`/`CAMetalLayer` overlay in the Player because it interferes with Compose layering and input.
- The Metal host consumes translated MSL, uploads the 32-band frequency texture, frame uniforms, palette colors, and quality controls, and keeps SkSL as the fallback path. Native visualizer shaders should not blend album art into the effect background.
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
- [x] Add a macOS Metal shader translation step for the canonical native GLSL sources.
- [x] Gate the experimental macOS native renderer path behind an explicit system property.
- [x] Add opt-in Apple Metal compiler verification:
  `./gradlew :core:ui:jvmTest -Dnaviamp.visualizer.metalCompilerTest=true --tests app.naviamp.ui.NativeMetalShaderTranslatorTest.translatedMetalShadersCompileWhenToolchainTestIsEnabled`.
- [x] Add a macOS native Metal JNI library and package `libnaviamp_visualizer_metal.dylib` with the desktop app image.
- [x] Add an offscreen macOS Metal host that renders translated native shaders into a BGRA image drawn by the Compose Player visualizer area.
- [x] Upload frame uniforms, 32-band frequency texture, palette colors, and quality controls to the Metal host.
- [x] Keep the macOS Metal path opt-in and fall back to SkSL/Canvas when the property, library, or host initialization is unavailable.
- [x] Smoke-test the staged macOS app with `-Dnaviamp.visualizer.macosMetal=true`.
- [x] Add an experimental Windows offscreen OpenGL host for native GLSL visualizers.
- [x] Package the Windows OpenGL visualizer library with desktop Windows app images.
- [x] Reject direct embedded Windows AWT/OpenGL surface rendering after it overlaid Compose menus.
- [x] Increase desktop visualizer FFT/audio sampling cadence from `125ms` to `33ms` while visible and active.
- [x] Restore the default Windows Skiko renderer for packaged builds after visualizer testing showed it was smoother than forced Skiko OpenGL.
- [ ] Smoke-test the staged Windows app with the packaged OpenGL renderer and playback active.
- [ ] Manually validate all native-only visualizers in the Player with playback active, including resize/toggle behavior and layer ordering over album art.

## Acceptance Criteria

- Existing SkSL visualizers still work and remain the desktop default.
- Android can run GLSL visualizers without Skia RuntimeShader.
- Hidden/inactive visualizer cost remains near zero.
- Shader compile errors are visible in logs and fall back cleanly.
- Heavy shaders can be quality-gated instead of making the Player unusable.
