# October 8 window-attachment experiment

Decision: retain the existing SurfaceView/SurfaceControl cached-raster adapter. The public window attachment alternative was built and physically tested, then completely removed from production source.

All figures are percent of one CPU core. CPU samples are untraced; each optional Perfetto interval follows the CPU interval separately. SurfaceFlinger is system-wide, not exclusively attributable to Naviamp. Same Pixel 10a, 1080 × 2424, density 420, 60 Hz, charging, light thermal status 1. Frequency snapshots do not establish interval clock residency.

| Paused scrolling metadata | App | SurfaceFlinger |
| --- | ---: | ---: |
| Initial original attachment | 4.28% | 29.66% |
| Window attachment, warmed | 3.73% | 22.05% |
| Original attachment, warmed crossover | 3.60% | 21.83% |

The crossover eliminates the apparent improvement. Removing three SurfaceView hierarchies did not produce a repeatable cost reduction. No backend/attachment switch is justified by these results.

The discarded candidate used shared region/window placement and clipping, with native public View/AttachedSurfaceControl and SurfaceControl operations on Android API 33+. Common tests and Android/JVM/iOS device/simulator compilation passed before native wiring; its complete shared UI test run passed 531 tests and the architecture gate. Candidate APK SHA-256: `9de438a3b72002db4afb8c8d13672ebfb7804fac97d7bb2222d66d48af44459a`. Original crossover APK: `9858174f62f533beca6ef9261bf07144b26474d392c28497d4815839956537d3`. Initial original-path APK was the shader diagnostics build, with the visualizer disabled and the same original cached-raster adapter.

Physical candidate checks: title motion changed 10,889/36,216 pixels; combined playback changed 11,604 title pixels and 1,006 progress/time pixels. No app buffer queues or parent-window buffers appeared during the paused or combined steady trace intervals. Track-actions menu input, visual occlusion and accessibility labels passed. Background paused cost was 0.53% app / 1.56% SurfaceFlinger. Restored metadata was visible before further track selection. Combined warmed playback measured 8.46% app / 22.37% compositor, but there is no equivalent original-path combined crossover, so no improvement is claimed. The first short/long playback samples include track/startup warmup and are not steady improvement claims.

A later sample named `retained-short-active-settled-oct8` actually captured paused Home after a failed fixture-selection action. It is excluded from playback results. The helper action failure and package-replacement launch race did not produce passing playback evidence.

Raw traces, screenshots, native diagnostics and rejected source are retained locally under `/private/tmp/naviamp-241-followup-oct8`; this folder contains only app-specific summaries. The experiment adds no production changes. The issue remains open because Android continuous-animation compositor cost is still material.


The earlier cached-canvas APK was also revisited with compositor counters: paused scrolling measured **10.14% app / 17.15% SurfaceFlinger**, versus the original SurfaceControl crossover's **3.60% / 21.83%**. This does not justify a switch: the increased app cost outweighs the system compositor reduction in these samples. Its separate trace has 910 small SurfaceView dequeues at 30.55 fps and no VRI parent dequeues; RenderThread queueBuffer totals alone must not be described as parent-window redraws.

Verified retained-build short-track playback measured **5.16% app / 3.42% SurfaceFlinger** at 48 kHz. The full-player accessibility tree contained Short fixture and Pause before the capture. Its separate trace has no parent buffer queues, and 30 allocation/upload events for the one-second time label. The earlier failed-selection Home sample is not used as playback evidence.


A final native upload-only candidate reused `Surface.lockHardwareCanvas()` for changed cached bitmaps on API 34+, instead of copying each changed bitmap into a new immutable hardware bitmap. It preserved common timing and geometry, and the existing SurfaceControl animation path. Its first visible frame and verified Short fixture playback passed. The native diff reduced adapter code but introduced no shared behavior.

Warm playback measured **4.88% app / 2.98% SurfaceFlinger**, versus **5.16% / 3.42%** in the retained-build sample. That is too small and insufficiently repeated to establish a meaningful improvement. Its separate trace removes the 30 hardware-bitmap allocation/upload events, but adds 30 small cached-raster buffer dequeues (~0.99 fps) and 60 RenderThread queueBuffer events; parent VRI queues remain absent. The work moved to another native upload path rather than demonstrating a material saving. The candidate was removed and the original APK restored. Candidate SHA-256: `3abe48d7c230f1527430ca742b3bf68e95962e1dae4475281ce96856deea8cc5`.

Before that native-only test, the retained common implementation passed all **528 UI JVM tests**, Android/JVM/iOS device/simulator compilation, and `verifyCoreFirstArchitecture`. The candidate benchmark build also passed the architecture gate. No production code changes from the window or upload experiments remain.

The final Mac review app was refreshed with the verified retained common build. Its first paused capture contains startup compilation and one parent frame; the settled visible paused repeat measured **0.52%** CPU, 5 ms compilation and **zero parent frames**, with both physical visibility checks passing. The previously installed user Mac application was untouched.


After restoring the original Android APK, Short fixture was verified paused in the full player. The final visible paused check measured **0.07% app / 1.49% SurfaceFlinger**, with no app buffer queues in its separate trace. The phone and Mac review app are left paused. Cached-canvas APK provenance: SHA-256 `872844beb2ffd871ee439970858380c60052308db593ddabfbea68995e0b75cd`.
