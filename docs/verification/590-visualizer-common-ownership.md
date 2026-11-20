# Issue 590: shared UI closure and visualizer ownership

The actual shared Naviamp UI and presentation modules now compile for both TV targets and the
original iOS simulator in a **disposable diagnostic checkout**. This is more than the earlier
synthetic Material3 fixture, but it is not a complete TV application/host link or launch.

## Target and artifact experiment

The starting source snapshot is `13bfb69f21b064aa3648b4796299f1b0fe51192e` (archive SHA-256
`2ef256897ddbfff0fddad93c596b1577072ac0b8bad7c1dabcd04721bcab1f3e`). Its owned checkout uses
the prebuilt resources Gradle plugin, strict local redirect plugin and sealed private repositories
recorded in [artifact delivery](590-compose-artifact-delivery.md). Ordinary builds are unchanged:
no TV UI target or private Compose repository/plugin is enabled in production settings.

`scripts/probe-compose-tvos-shared-ui.init.gradle` adds two TV library targets only to UI and
presentation in that checkout. It ignores included plugin builds and requires the explicit
feasibility flag. It reuses the existing UIKit/Metal/Skia effect sources to diagnose API availability;
permanent host wiring must put genuinely shared Apple effects in an Apple source set, preserving
the original iOS entry points. Reusing this diagnostic source path is not the final host design.

The guard inspects actual resolved graphs, rejects snapshot/community-binary leakage, requires
official Lifecycle 2.11 and rejects private port artifacts in iOS. Actual selections:

| Module | Original iOS simulator | TV device / simulator |
| --- | --- | --- |
| UI | 130 components, zero private | 131 components each, 16 private |
| Presentation | 132 components, zero private | 133 components each, 16 private |

All six requested library compilations passed initially in 3m22s (51 executed tasks). The source
overlay with the ownership extraction below then passed in 1m16s (17 executed, 34 up-to-date).
These are different warm workloads, not a cold-build comparison or speed claim. First setup
attempts exposed the Windows-created archive's CRLF launcher and an init callback running in an
included plugin build; only the owned launcher was normalized and the callback was scoped.
Their failed logs remain separate. The upstream repositories and tracked source in the user's
existing Mac checkout were untouched; project edits stayed in owned build checkouts.

Example after preparing a fresh owned checkout with those verified plugin/repository inputs:

```sh
./gradlew --max-workers=2 --no-configuration-cache --no-configure-on-demand \
  '-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8' \
  --init-script /absolute/naviamp/scripts/probe-compose-tvos-shared-ui.init.gradle \
  -Pnaviamp.tvos.enabled=true verifyFullSharedUiProbeVariants \
  :core:ui:compileKotlinTvosArm64 :core:ui:compileKotlinTvosSimulatorArm64 \
  :core:ui:compileKotlinIosSimulatorArm64 \
  :core:presentation:compileKotlinTvosArm64 :core:presentation:compileKotlinTvosSimulatorArm64 \
  :core:presentation:compileKotlinIosSimulatorArm64
```

## Common ownership repair before host wiring

The adapter review found portable visualizer scheduling/history rules and Canvas UI in platform
source sets. Those now live in Core common code rather than being copied for TV:

- `VisualizerFrameClock` owns display-clock gating, inactive reset, restart/cancellation and the
  returned `LongState`. Native draw callbacks read state without reading it in parent composition.
  Existing target intervals and FFT acquisition frequency remain unchanged. The mounted Android
  GL surface retains its original continuous callback lifetime, including idle native rendering.
- `VisualizerHistoryBuffer` owns eligibility, unchanged-input suppression, the existing 0.006
  change threshold, cadence, row aging and ARGB/RGBA encoding. RGBA serialization is cached and
  updated by row; unchanged input does not request new native history images.
- Canvas preset fallback drawing/sampling and the basic bars fallback moved into shared UI.
  Existing fallback presentations are preserved. Native code delegates instead of drawing a
  second portable UI. Existing common spectrum/lyric surfaces use the same clock owner.
- Pixel-history nearest-neighbor selection is common. iOS previously always used linear history
  filtering; its two pixel presets now follow the same nearest selection as Android/Desktop.
  This is an intended parity correction, not a claim of physical shader-image acceptance.

There are no new settings, export/sync keys, user-facing strings, schemas or Android minimum changes.

## Validation and limits

Common implementation/tests compiled for JVM, Android and original iOS before platform delegation.
Final JVM reports **12** focused passes; Android host reports **11**. The extra composition-runtime
scenario calls the real platform tracing API, so Android executes its shared scenario through a
device-test wrapper rather than a mockable host stub. That wrapper compiles and is included in
the existing emulator class list. Its single headless scenario also passed on the connected
**Pixel 6a**, `OK (1 test)` in 0.097s; the prior test APK was restored successfully. The normal
Naviamp application was not replaced and the connected Android TV was untouched. Canonical
emulator results for source `5e3a5c351a26e31a799df4a777b5eadf5252dd0c` passed on API 30
and 35 (one new case each, 0.046s and 0.026s). The complete Verify run
[43716519403](https://github.com/goosepod/naviamp/actions/runs/43716519403) passed,
including Windows/macOS/Linux native tests, original iOS and all three Android versions.
The separate tvOS foundation and Flatpak runs passed too.

Review of the raw Android artifact ZIPs found that API 26's explicit class list omitted this
new case: its four existing UI cases passed, but that was insufficient evidence for the new
behavior. API 26 now includes the wrapper explicitly. A verification collector also requires
exactly one executed, passing, non-skipped composition-clock case on every API, fresh app/UI
JUnit XML and no test failures/errors. Missing or stale reports fail the job. Existing optional
app-native skips remain visible rather than being counted as passes.

The collector retains XML under short portable names with source paths and SHA-256 in a summary;
module HTML is retained as separate ZIPs and emulator logs are staged under the same build root.
The previous raw ZIPs already contained their XML, despite incomplete extraction observed with
the Windows download command. That extraction discrepancy is not evidence of missing CI reports
or a proven path-length defect. Replay of those real reports correctly rejects API 26's missing
case and accepts API 30/35; six collector regressions and actionlint pass. Acceptance of the
expanded API 26 list now passes on source `9958353df1d4e439e02cefe46b3826755d40916d`:
five UI cases, zero failures/errors/skips. API 30 and 35 each pass 47 UI cases. All three fresh
collector summaries pass and retain the app-native optional skips (24/24/22 respectively).
Source-replay helper work does not change the production animation implementation or replace
the real-window performance gate below.
The reusable init script also passed a missing-opt-in rejection and a positive resolution check,
including its included-build guard.

The strict owned-simulator repeat executed **210 TV 27.2** and **211 iOS 26.5** UI cases, including
all twelve new regressions, zero failures/errors/skips. The tests cover cadence, pause, cancellation,
restart, settled parent-composition isolation, history changes/aging, equivalent native encodings,
cached RGBA updates, filter choice and fallback sampling. The original Android Debug APK, desktop
compilation, iOS device compile/Debug simulator framework link, Core architecture and 19 shared
Android source compilations pass. Both owned simulators shut down/deleted with exit codes 0.

The initial recomposition assertion expected exactly one composition from startup and observed two
on JVM/TV. Its XML/logs were retained. The final test settles one initial frame, then requires **each**
subsequent moving frame to advance without any additional parent composition. It does not hide or
count startup as a zero-work state. The Android host's initial real-Trace stub failure and the local
desktop build's missing CMake executable are retained too; final builds use the installed CMake.

This is **not** runtime performance acceptance. The November 14 real-window measurements predate
this extraction and must not be reused as its sign-off. Repeat visible static, individual/combined
motion, paused, hidden/restored, resize/input/clipping/accessibility and CPU/parent-frame/GPU checks
under matched conditions. Keep #590/PR 606 open and draft until the actual budget is met; no
animation slowdown, playback-frequency reduction or accessibility disabling is accepted.

## Separate native production diff audit

| Changed platform production file | Concrete native boundary retained |
| --- | --- |
| `core/ui/src/androidMain/kotlin/app/naviamp/ui/PlatformLiveVisualizerSurface.android.kt` | Android Bitmap/RuntimeShader, GLES/GLSurfaceView, native font measurement and Android diagnostic logging; common code owns clock/history/Canvas behavior |
| `core/ui/src/jvmMain/kotlin/app/naviamp/ui/PlatformLiveVisualizerSurface.jvm.kt` | Native Skia/Metal/OpenGL drawing resources, AWT font measurement and JVM native-library integration; common code owns clock/history/Canvas behavior |
| `core/ui/src/iosMain/kotlin/app/naviamp/ui/PlatformLiveVisualizerSurface.ios.kt` | Apple Metal/Native Skia image, shader and canvas lifetimes; common code owns clock/history/filter selection |

No platform controller/repository or product state owner was added. The three files shrink by more
than 500 lines in total. The earlier SQL and provider native-file audits are in
[the shared foundation report](590-shared-tvos-foundation.md).

Evidence: Mac `build/590-full-shared-ui-probe`, `590-full-shared-ui-probe-root-guard.log`,
`590-visualizer-common{,-canvas}-ios-compile.log`, `590-visualizer-final-native{,-steady}`;
local `build/590-feasibility/visualizer-*.log`, failed startup XML and final Native summary.
Source hashes in the accepted Native summary cover the exact production/test overlay.
Pixel evidence: `build/590-feasibility/visualizer-pixel/instrumentation.txt`; test APK SHA-256
`cdd9161042dd39b2d7d9f625f3ddf22e2ed9ed0b96d07ceebaee5f8b32dae954`.

The remaining Windows default-output test is still a hardware/session prerequisite: outside-sandbox
enumeration reports running Audiosrv/AudioEndpointBuilder services and only a present microphone
endpoint, with no present playback endpoint. The earlier real BASS default-output probe failed
driver initialization (error 3). Do not replace it with a no-sound decode result and call it physical
output acceptance.
