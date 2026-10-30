# Android performance continuation — October 30, 2026

Tracking: #408. Product source is the combined `0f95e6a4f` candidate; integration head
`f4da8bbc5` adds only verification documentation. The owner made the physical Pixel available
for measurements and deferred visible macOS testing. These are initial performance findings,
not a release acceptance pass or proof of a regression relative to an older build.

## Device and measurement conditions

Pixel 10a, Android 17/API 37, portrait 1080×2424, density 420 dpi. Display snapshots before
component testing and after the actual-app matrix both report 60 Hz. Refresh policy, brightness,
volume, network, battery policy and accessibility settings are not changed. Charging at 100%;
battery temperature moves from 27.2°C to 28.3°C, thermal status remains 0. No Bluetooth route
or physical iPhone is available. All evidence remains local under ignored
`build/performance-408` in `/private/tmp/naviamp-overnight-review`.

## Shared component fixture

The existing opt-in `AndroidAnimationProbeTest` runs real shared title and progress components
with three overflowing metadata rows and a library sibling. Two clean runs pass in 76.474s
and 76.223s. Each state warms for five seconds and measures ten seconds. Instrumentation
checks visible marquee change before its measured interval. Its screenshots compare the full
screen, so that assertion alone does not establish crop-specific movement for every state.

| State | App CPU, first / repeat (% of one core) | Root / sibling draws |
| --- | ---: | ---: |
| Static | 0.050 / 0.390 | 0 / 0 |
| Marquee | 6.988 / 6.668 | 0 / 0 |
| Progress | 5.119 / 4.989 | 0 / 0 |
| Combined | 6.399 / 6.039 | 0 / 0 |
| Restored static | 0.040 / 0.070 | 0 / 0 |

The Android window FrameMetrics listener receives no frames during these intervals. Its
zero frame/GPU-duration totals do not measure the native SurfaceControl animation's GPU or
compositor cost. Root/sibling draw counters establish no unrelated Compose repaint stream;
they do not establish cheap process CPU or cheap SurfaceFlinger composition.

Probe APK SHA-256: `e8cf9761689da86711c6aecac4b963c11f040c50ec957bfcd1bd603276b76586`.
It is installed only as disposable `app.naviamp.ui.test`.

## Actual optimized application

Ordinary `:apps:android:assembleBenchmark` succeeds without the animation-probe flag, with
R8/resource shrinking enabled. The separate `app.naviamp.android.benchmark` package is
non-debuggable, profileable, version code 60. Its APK SHA-256 is
`77eec8471c0747b1dd6e4714556009381451c2bab863a4b1ef8f60e80f87ef2d`.
No normal-app upgrade, data clear or saved-account edit is performed.

A fresh disposable profile connects only to the loopback fixture through the explicitly selected
phone's ADB reverse mapping. Two 600-second WAV tracks have short and overflowing metadata.
The fixture retains its WAV header and substitutes silent PCM payload, exercising decoding and
output without changing phone volume. The password-manager offer to save its public fixture
password is declined. No existing server, password entry or library is used.

Direct `/proc/PID/stat` CPU deltas use device uptime and the reported 100 Hz CPU tick rate.
Process start time guards against PID reuse. Each interval lasts about 10.1 seconds; no native UI
queries, screenshot captures or tracing occur inside it. Screenshots and package-scoped
`dumpsys gfxinfo` captures bracket the interval. Thread-counter snapshots cover a slightly
wider interval and are diagnostic, not an exact partition of the process sample.

| State | App CPU (% of one core) | SurfaceFlinger CPU (% of one core) | Window frame delta |
| --- | ---: | ---: | ---: |
| Initial static A | 2.277 | 0.891 | 0 |
| Initial static B | 5.539 | 0.692 | 1 |
| Later warm static | 0.298 | 0.695 | 0 |
| Progress A | 10.584 | 9.397 | 1 |
| Progress B | 11.619 | 9.335 | 0 |
| Paused marquee A | 6.859 | 20.676 | 0 |
| Paused marquee B | 6.825 | 19.090 | 1 |
| Combined A | 11.519 | 21.053 | 0 |
| Combined B | 10.792 | 19.406 | 0 |
| Background playback | 6.423 | 1.087 | 0 |
| Restored combined | 11.485 | 19.505 | 0 |

All startup samples are retained; the low later static value does not erase them. Playback CPU
includes real decoding/output and shared runtime activity, not just animation. No guessed audio,
JIT or observation overhead is subtracted. SurfaceFlinger is phone-wide CPU, not per-window GPU
energy. The paired observations show substantial motion-associated compositor CPU under these
conditions; they do not quantify attributable GPU capacity or energy.

Images confirm painted content. Targeted pixel comparison of the metadata rectangle changes
19,860–21,879 pixels during paused marquee samples, with unchanged progress. Progress-only
samples change 90 pixels within the progress rectangle and zero metadata pixels. Combined
samples change both regions; restored combined does too. Static samples change neither region.
The background sample is a lifecycle diagnostic with the launcher visible, not a visible-motion
pass. Window frame counts are Android gfxinfo totals, not shared root draw counters.

## Separate trace diagnostics

A separate 85-second Perfetto scheduling/gfx/view capture runs the same component sequence.
The 32 MiB capture reports two `traced_chunks_discarded`; one bounded 128 MiB retry reports
three. Both report no nonzero error-severity stats. Neither capture is labeled loss-free, and
their quantitative CPU attribution is not accepted as clean evidence. Both instrumented
sequences complete successfully; profiled CPU values are kept separate from the clean tables.
Raw traces are local only and their on-phone files are removed. No third retry is performed.
FrameMetrics and these diagnostics do not establish full GPU acceptance.

## Cleanup and remaining work

Both disposable packages are removed, leaving only `app.naviamp.android`. The fixture server
and its exact ADB reverse mapping are stopped/removed. Temporary on-phone XML/trace files are
removed. The normal app reopens with the original track paused at 2:23; Play is present and Pause
absent. The original account/profile, offline media, system settings and normal APK are untouched.

Android performance remains open: investigate retained app-main/runtime and compositor costs,
obtain validated GPU/compositor attribution, and complete the applicable lifecycle/input/clipping/
accessibility matrix on the eventual fix. No animation is disabled, cadence reduced, production
code changed, high baseline accepted, or release-performance pass claimed.

Source inspection identifies a hypothesis to test: `AndroidRasterRegion.updateSurfaces` submits
crop/transform/scale/position commands for every visible layer on each running tick and marks
the transaction changed without comparing the resulting native geometry with its previous value.
That can resubmit equivalent native operations during quantized progress or marquee hold frames.
This is not proof of the measured cost's cause. Any experiment must preserve smooth motion,
common policy ownership, real-app behavior and the full measurement matrix.

The current-source Mac release bundle also builds successfully without the probe flag. A fresh
isolated observer copy is staged, with all 87 production JAR hashes verified and a separately
compiled visibility guard. No stale JARs are retained. Its observer launcher uses a fresh isolated
home; no normal Mac profile is copied. The owner requested phone-only availability, so the Mac
test app is not launched and no new visible Mac CPU/GPU result is claimed.

The release-integration hosted macOS run separately times out waiting for initial main-stream
progress at `DesktopPreviewPlaybackIntegrationTest.kt:52`; it does not reach the preview completion
assertion. The main-integration matrix passes the same native test. A local muted real-output
reproduction also passes (one test, no skips): paused main position 2.49s stays 2.49s through
preview completion, then resumes to 3.458s. This headless native test does not require an
undisturbed visible Mac window and is not an animation performance result. Original hosted
failure logs/reports are retained; no timeout or assertion is weakened.

The one hosted retry subsequently fails the sampled-position assertion (`0.991875 -> 2.2`).
[The #412/#413 native-test correction](412-native-preview-clock.md) replaces a fixed-sleep
assumption with agreement between the paused sample and actual native position, and adds direct
native before/after assertions. Its finalized local native suite passes 65 cases with no failures
and two Linux-only skips. The two earlier handle-recording adapter iterations are retained in
that report. Final protected hosted verification remains required; no further unchanged-source
retry or branch-protection bypass is used to declare success.
