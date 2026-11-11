# October 8 renderer comparison

Physical Pixel 10a, minified profileable benchmark package, 1080 × 2424 at 60 Hz,
48 kHz synthetic audio and short metadata. CPU and following Perfetto intervals are separate.
SurfaceFlinger CPU is system-wide and is not exact Naviamp attribution. All CPU percentages
refer to one core. Raw screenshots, traces, experimental source patches and APKs remain local.

The native Skia experiment reuses the authoritative shared shaders and packed frame inputs.
Android supplies RuntimeShader, RenderNode, HardwareRenderer and SurfaceView effects only.
Shared creation fallback, uniform mapping and clipping were tested before native wiring;
Android/JVM/iOS device and simulator compilation passed, followed by all 534 shared UI tests.
These experimental production changes were removed after the comparison.

The display-pulse experiment keeps cadence selection, rounding, missed-frame handling and
cancellation in Core; Android supplies a Choreographer timestamp. Tests cover 30, 60, 75, 90
and 120 Hz. All 533 UI tests and platform compilation passed. It did not improve CPU and
its production changes were also removed. Neither experiment lowers the 45 FPS target.

APK SHA-256 identifiers:

- Retained GLES: `9858174f62f533beca6ef9261bf07144b26474d392c28497d4815839956537d3`.
- Native Skia initial/Analog: `e55c525bda1e193ed27ec2dd91e8cd170a0245697cb620a025158e4266bbd762`.
- Native Skia creation diagnostics/Sphere/Ocean/crossover:
  `27ce5e3506b208e2125f31e68b70e731c09f9c6b6c3766afbca7a0bacf6cd4ee`.
- Display-aligned GLES: `2c3d27a0cbffe30f783a6c39a222f64275ebfd77b5082f75b9a62805258f6f2c`.

Analog's first native Skia interval has thermal status 0; later verified Sphere/Ocean and
matched comparisons have light throttling (status 1). That status alone does not establish
equal CPU clock rates. Ocean's early Skia advantage narrowed substantially in a crossover
after testing GLES, so it does not establish a durable backend improvement.

Two rows retain misleading original phase names but are explicitly **excluded**: the test
helper tapped the disabled checked Sphere item, leaving the menu open, then selected Audio
Tunnel with the next tap. Their verified effect is Audio Tunnel, whose legacy GLES path is
expected. They establish neither a Sphere result nor a failed native shader backend. Other
blank/setup captures remain local and are excluded from visualizer acceptance. The helper now
refuses disabled controls; later Sphere captures show the canonical sphere and native shader
submissions, not a tunnel.

Trace summaries distinguish SurfaceView buffer queues from the parent VRI window. HardwareRenderer
uses RenderThread for its own surface; that thread's queueBuffer count alone is not evidence
of parent repainting. Frame-timeline packets are sparse on this device, so native buffer names
provide an additional check. Buffer submission rates are not end-to-end presentation latency.
Before/after crops independently confirm motion and unchanged neighboring metadata.

The probe now records CPU policy frequency snapshots and frequency/idle events in separate
traces. Snapshots do not measure interval residency. A CPU-cycle counter trial emitted native
permission warnings and is not used for acceptance. Keep the existing issue and draft PR open
for Android scrolling/compositor cost and the remaining platform runtime matrix.
