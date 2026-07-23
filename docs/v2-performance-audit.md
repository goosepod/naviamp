# Naviamp v2 Performance Audit

This document is the repeatable CPU and memory completion gate for the cross-platform migration.
The migration cannot be declared complete until release-like Android, Desktop, and iOS builds pass
every required scenario below. Functional acceptance and host promotion do not waive this gate.

## Acceptance thresholds

Measurements begin after a two-minute warm-up. CPU is normalized to one logical core; memory is
resident/PSS after an explicit return to the measured state. Each steady-state scenario is sampled
for at least five minutes and each growth scenario for at least 60 minutes.

| Scenario | CPU threshold | Memory threshold |
| --- | ---: | ---: |
| Foreground idle | median <= 3%, p95 <= 8% | Android/iOS <= 250 MB; Desktop <= 650 MB |
| Background paused | median <= 2%, p95 <= 5% | Android/iOS <= 200 MB; Desktop <= 550 MB |
| Background active playback | median <= 12%, p95 <= 20% | Android/iOS <= 225 MB; Desktop <= 600 MB |
| Foreground Now Playing | median <= 20%, p95 <= 35% | Android/iOS <= 300 MB; Desktop <= 750 MB |
| Prefetch/sidecar work | no unexplained busy loop; returns to the matching steady-state threshold within 30 seconds of completion/cancellation | peak <= 1.5x steady state |
| Large-library scroll/search | no sustained work after interaction settles | peak <= 1.5x foreground idle |
| One-hour playback/route cycle | matching steady-state threshold after each transition | <= 10% retained growth after warm-up |

Any unexplained sustained CPU use, monotonic retained growth, crash, ANR, or failure to return to the
matching steady state is a failure even when the numeric ceiling is not crossed. A threshold may be
changed only with an explicit rationale and a new recorded baseline on all affected platforms.

## Required matrix

For Android, Desktop, and iOS, record:

- release identifier, device/hardware, operating-system version, build type, and profiler/tool;
- source library size, queue size, cache/prefetch/lyrics/waveform settings, audio format, sample-rate
  conversion and matching settings, and visible visualizer;
- foreground idle, paused/background, active background playback, foreground Now Playing,
  queue-prefetch plus lyrics/waveform sidecars, large-library scrolling/search, and repeated route
  and Now Playing transitions;
- median and p95 CPU, initial/peak/final resident memory, retained growth, sampling duration, and
  whether the process returned to its steady-state baseline;
- raw capture location or the exact command/tool configuration used.

## Preliminary Android promotion sample — 2026-07-23

This is a diagnostic debug-build sample, not the final release-like pass.

- Device: Pixel 10a (`stallion`), Android 17 / API 37, build
  `CP2A.260705.006`; physical USB connection.
- Application: promoted `app.naviamp.android.v2test` debug build, 38-track queue, BASS playback,
  Android Auto Desktop Head Unit attached during the foreground sample.
- Tools: `adb shell top`, `adb shell top -H`, `adb shell dumpsys meminfo`, `dumpsys audio`,
  `dumpsys power`, `dumpsys media_session`, and service diagnostics.
- Foreground active playback: approximately 22–28% of one core; 256,112 KB total PSS and
  392,740 KB total RSS.
- Background active playback: approximately 30–45% of one core; 161,534 KB total PSS and
  298,404 KB total RSS. Per-thread sampling attributed most managed work to the process/main and a
  `DefaultDispatcher` worker, with the native `AudioTrack` thread around 2%.
- Background paused: normally 0–2% with one 7% sample. This proves the sustained load is tied to
  active playback rather than an always-running idle loop.
- Audio focus was held by `AndroidFocusedBassPlaybackEngine`; the active AAudio route was 48 kHz
  stereo and the `Naviamp:Playback` partial wake lock was held only for active playback.
- The first sample exposed native-surface churn on every Core progress tick. Core now owns a tested
  publication planner: Android rebuilds session content, browse catalogs, and notifications only
  when their semantic state changes, while playback position is republished at a bounded five-second
  drift interval.

Result: **not accepted**. Active-playback CPU exceeds the provisional threshold and the sample is a
debug build. Profile the release-like BASS decode/mixer/sample-rate-conversion path, compare original
versus converted sample rates and visualizer collection, resolve or justify the load, then repeat the
full Android matrix. Desktop and iOS results remain outstanding; iOS cannot pass until its thin host
and production playback engine exist.
