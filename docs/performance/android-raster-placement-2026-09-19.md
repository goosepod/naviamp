# Android raster placement verification — 2026-09-19

Issue: https://github.com/goosepod/naviamp/issues/112

## Cause and fix

The previous adapter applied geometry transactions to SurfaceView's framework-owned surface.
Android explicitly documents that control as effectively read-only: the framework can overwrite
its properties. The adapter now uses it only as the parent of app-owned SurfaceControl children.
Each cached image is uploaded once, with subsequent presentation updating child geometry and
visibility. Window coordinates are translated into the native parent's coordinates.

Reference: https://developer.android.com/reference/android/view/SurfaceView#getSurfaceControl()

The common contract documents window-pixel coordinates. A shared rendered test checks that
moving and resizing layout updates the bounds supplied to the native presenter. Animation
models, timing, clipping intent, input, and accessibility remain in common code. The sole changed
platform production file is `core/ui/src/androidMain/kotlin/app/naviamp/ui/AndroidRasterPresenter.kt`:
Android SurfaceView/SurfaceControl/Surface APIs require native child-surface creation, transactions,
coordinate translation, and resource lifetime management.

## Phone regression evidence

Pixel 10a, Android 17/API 37, portrait 1080×2424, density 420, active 60 Hz display mode,
USB power, battery 100%; temperatures 27.3°C before and 27.1°C after the initial tests.
No display, refresh, power, or system-font settings were changed between measurements.

`AndroidRasterPlacementTest` fails against the original renderer when restoring a region and
passes with this fix. It checks pixels at nonzero shared bounds, moving/resizing the region,
clipping outside it, removal/restoration, and changing pixels inside the intended animated region.
This supplements the older motion probe, whose global pixel-change assertion could pass even
when the pixels were misplaced.

`AndroidAnimationProbeTest` passed with the fix. Each phase warms for 5 seconds and measures
for 10 seconds. CPU percentages use one full core as 100%.

| Visible fixture | Baseline trial 1 | Baseline trial 2 | Fixed |
| --- | ---: | ---: | ---: |
| Static | 0.06% | 0.05% | 0.41% |
| Three scrolling titles | 6.26% | 5.09% | 5.30% |
| Smooth waveform | 4.55% | 4.47% | 4.63% |
| Combined | 5.55% | 5.24% | 5.64% |
| Restored static | 0.05% | 0.05% | 0.04% |

Every measured phase recorded zero parent-root draws, zero sibling draws, and zero window frames.
Window FrameMetrics GPU duration was zero; this does not measure SurfaceFlinger GPU work.
SurfaceFlinger process CPU over the entire approximately 78-second probe, including setup and
warmups, was 7.66% baseline and 7.59% fixed. These are `/proc` CPU tick deltas at CLK_TCK=100;
they measure compositor CPU, not GPU energy. No claim of zero system compositor cost is made.

The placement fix's regression gate is preserved visible motion, zero animation-driven parent
and sibling redraws, static CPU below 1%, and animated/compositor CPU within the same-session
baseline range allowing one percentage point of run variation. This is a nonregression gate
for this placement repair, not a claim that the earlier animation implementation has no further
optimization opportunities.

## Real player

The separate review app preserves its saved login and does not replace the user's regular app.
The title and waveform render in their intended positions while paused and playing. Seeking,
pause, background/restore, menu overlap, and accessible title/artist/album/Play controls were verified.
The paused player measured 0.10% app CPU, 0.49% compositor CPU, and zero window frames over
10.24 seconds. Playing measured 13.88% app CPU, 8.37% compositor CPU, and 11 window frames over
10.16 seconds; this includes decoding, audio, and time-label updates and is not an isolated
animation measurement. A same-device baseline playback run using the same track and Standard
fonts measured 13.92% app CPU, 9.61% compositor CPU, and 40 window frames over 10.20 seconds.
The full-player gate is no increase over that baseline beyond one percentage point of CPU
variation and no increase in window frames; the fix passes. Whole-player playback CPU remains
an opportunity for separate investigation, rather than being represented as animation-only cost.

A local integration of the separate font-size and renderer branches also passed all five
`NaviampFontSizeRenderingTest` tests on the phone. That combined review app is installed with
playback paused and both font preferences Standard. Hidden app CPU measured 0.49% over 10.14
seconds, and the player restored with its title and waveform in place. The source branches remain
independent; this integration is only for device testing.

## Reproduction

```powershell
.\gradlew.bat :core:ui:jvmTest --tests app.naviamp.ui.NaviampRasterInteractionTest --console=plain
.\gradlew.bat '-Pnaviamp.animationProbe=true' :core:ui:assembleDebugAndroidTest --console=plain
adb install -r core/ui/build/outputs/apk/androidTest/debug/ui-debug-androidTest.apk
adb shell am instrument -w -r -e class app.naviamp.ui.AndroidRasterPlacementTest app.naviamp.ui.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r -e class app.naviamp.ui.AndroidAnimationProbeTest app.naviamp.ui.test/androidx.test.runner.AndroidJUnitRunner
```

Common rendered tests, common metadata compilation, Android compilation, device regression
tests, and the Android application build passed. Native iOS execution is unavailable on Windows;
this repair changes no iOS or Desktop adapter. Local measurement logs and screenshots are in
`build/font-size-review/` and are not committed.
