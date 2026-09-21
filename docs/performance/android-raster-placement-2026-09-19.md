# Android raster placement verification — 2026-09-19

Issue: https://github.com/goosepod/naviamp/issues/112

## Cause and fix

The previous adapter applied geometry transactions to SurfaceView's framework-owned surface.
Android explicitly documents that control as effectively read-only: the framework can overwrite
its properties. The adapter now uses it only as the parent of app-owned SurfaceControl children.
The native parent is hosted inside the shared layout through a narrow AndroidView adapter,
so SurfaceView's render-thread position tracking follows scrolling with the Compose content.
Each cached image is uploaded once, with subsequent presentation updating child geometry and
visibility. Window coordinates are translated into the region's local coordinates.

Reference: https://developer.android.com/reference/android/view/SurfaceView#getSurfaceControl()

The common contract documents window-pixel coordinates. A shared rendered test checks that
moving and resizing layout updates the bounds supplied to the native presenter. Animation
models, timing, clipping intent, input, and accessibility remain in common code. The sole changed
platform production file is `core/ui/src/androidMain/kotlin/app/naviamp/ui/AndroidRasterPresenter.kt`:
Android SurfaceView/SurfaceControl/Surface APIs require native child-surface creation, transactions,
coordinate translation, and resource lifetime management.

## Initial placement verification

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

## Follow-up: scrolling and image transitions

User testing found wobbling during Now Playing scrolling and repeated waveform blinking when
loading songs or replacing the fallback scrubber with waveform data. The initial settled-position
tests did not cover the frames between stable positions.

The new moving-frame regression reproduced a 10-pixel separation between cached pixels and an
ordinary Compose reference. The final adapter embeds its native parent in the shared region's
layout. Shared code publishes layout before drawing, coordinates native readiness with fallback
content, and supplies the draw handoff. Android owns only AndroidView/SurfaceView attachment,
native buffers, transactions, and their resource lifetimes. Layout-only updates preserve motion
time. Same-sized images reuse their existing buffers, while different-sized replacements retire
their old layers together with presentation of the replacements. On API 31+, presentation
transactions join the root window's draw; older supported adapters apply them from the shared
draw pass. Only API 37 was physically verified in this session.

The two Android regression tests pass on the Pixel. They cover settled position/clipping,
resize, hide/restore, actual motion, and 45 captured moving frames during image replacement
every 80 ms. Replacement images exercise both equal and different dimensions. Every sampled
frame contains the raster and stays within two pixels of its Compose reference, with actual
movement required. Six shared interaction/readiness tests and common metadata/Android compilation
also pass.

The final visible-window probe records:

| State | App CPU | Parent/sibling draws | Window frames |
| --- | ---: | ---: | ---: |
| Static | 0.06% | 0 / 0 | 0 |
| Three scrolling titles | 6.01% | 0 / 0 | 0 |
| Waveform | 4.75% | 0 / 0 | 0 |
| Combined | 5.94% | 0 / 0 | 0 |
| Restored static | 0.04% | 0 / 0 | 0 |

SurfaceFlinger CPU is 8.14% over the complete 77.63-second probe, within the initial baseline's
one-percentage-point nonregression allowance. Window GPU duration remains zero, with the same
compositor-GPU caveat above. Display, density, refresh, USB power, and battery level are unchanged;
the phone reports 27.8°C after the probe.

The combined font/renderer review app was exercised with real Now Playing drags, seek input,
and the transition from Mustard Plug's You to Yesterday. Drag captures keep the waveform aligned
with the Compose time labels; track title and waveform remain present after the song change.
Saved font preferences and login are preserved.

Initial post-install playback samples varied (17.09%, then 15.79%, then 14.15% on replay), so
acceptance uses a controlled reinstall comparison rather than comparing an established process
with a newly installed one. Both builds use the same saved settings, You FLAC track, seek to about
10 seconds, 10-second warmup, and 15-second measurement with playback confirmed in the UI:

| Real player | Previous renderer | Final renderer |
| --- | ---: | ---: |
| App CPU | 15.84% | 16.16% |
| Main-thread CPU (included above) | 9.50% | 9.37% |
| SurfaceFlinger CPU | 7.85% | 7.98% |
| Window frames | 16 | 15 |

This passes the documented one-percentage-point/non-increasing-frame regression gate. Audio and
other app work are included; these are not animation-only percentages. A separate paused sample
measured 0.89% app CPU. The fixed combined review APK is restored after the comparison and left
paused. Raw evidence is under `build/font-size-review/scroll-ab-*` and `renderer-anchor-*`.
The final background/restore check measures 0.39% hidden app CPU over 10.13 seconds and restores
the complete player correctly, paused on the original track at about 0:11.

## API 34 transaction-pacing follow-up

The combined rendering branch exposed a repeatable API 34 emulator failure after the initial
layout transaction: cached pixels were placed correctly, but marquee and waveform motion remained
frozen. The adapter waited for a transaction-committed callback before allowing free-running
geometry updates. That callback did not release the wait on this emulator. It was also introduced
in API 33 even though the adapter entered the path on API 31, leaving API 31 and 32 exposed to a
missing platform method.

The adapter no longer gates complete geometry updates on that callback or on a later root draw.
The SurfaceView render thread already synchronizes movement of the native parent with the window;
child layout, image, and animation transactions carry complete current bounds and clipping and
commit directly on every supported API. This also avoids a headless API 35 compositor that accepts
`applyTransactionOnDraw` but never presents the queued first frame.

On the Android 14/API 34 arm64 emulator, both placement tests pass after the correction. They cover
45 moving image-replacement frames, settled placement and clipping, resize, removal/restoration,
and actual compositor motion. The visible probe also passes with the following diagnostic emulator
measurements; emulator CPU is not a substitute for the physical-device acceptance numbers above.

| State | App CPU | Parent/sibling draws | Window frames |
| --- | ---: | ---: | ---: |
| Static | 0.11% | 0 / 0 | 0 |
| Three scrolling titles | 1.65% | 0 / 0 | 0 |
| Waveform | 2.99% | 0 / 0 | 0 |
| Combined | 2.10% | 0 / 0 | 0 |
| Restored static | 0.02% | 0 / 0 | 0 |

The device workflow now runs these three raster tests on API 30 and API 35, covering both the
oldest SurfaceControl runtime used by the adapter and the current Android compositor behavior.

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
