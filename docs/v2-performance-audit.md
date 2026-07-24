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
| Background active playback | median <= 10%, p95 <= 18% | Android/iOS <= 225 MB; Desktop <= 600 MB |
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

## Android active-playback optimization sample — 2026-07-24

This release-like sample accepts the Android background active-playback baseline. It does not replace
the required five-minute and one-hour completion runs in the full matrix.

- Device: Pixel 10a (`stallion`), Android 17 / API 37, physical USB connection.
- Application: non-debuggable, profileable `benchmark` build of
  `app.naviamp.android.v2test`; 51-track queue; visualizer closed; 16-point sinc sample-rate
  conversion; background playback.
- Tools: `adb shell top`, `top -H`, `dumpsys meminfo`, and `dumpsys media_session`.
- Before optimization, the release-like build used roughly 35–55% CPU (median about 45%) and could
  lose its foreground-service start permission during a transient idle state between tracks.
- Core now owns a one-second engine polling policy for every host, visualizer frame sampling is disabled
  while the visualizer is closed, Core debounces transient playback-service release decisions, and
  Core supplies BASS's 100 ms native update policy to the thin native adapters.
- After optimization, a clean 40-second background run sampled 3–16.5% CPU with a median of about
  10%. A recent matching memory capture reported 155,598 KB PSS and 278,440 KB RSS. A separate clean
  run reported 102,584 KB PSS after the background process settled.
- Automatic playback advanced successfully within the retained 51-track queue while backgrounded;
  the Android 17 foreground service remained alive across the transition.
- Paused playback sampled normally at 0–2%, confirming that no unrelated idle loop remained.

For a same-device reference, Plexamp (`tv.plex.labs.plexamp`) was freshly installed, played in the
background with no visualizer, and measured using the same 40-second `top` cadence. It sampled
7.5–15% CPU with a median of about 9.5–10%, 292,029 KB PSS, and 424,472 KB RSS. Naviamp therefore
reached practical CPU parity with another production BASS player while using substantially less
memory in these short controlled samples.

Result: **Android background active-playback baseline accepted**. Further CPU improvements remain
desirable, but are not a blocker before completing the remaining v2 work and the final full-duration
cross-platform audit.

## Preliminary macOS optimization sample — 2026-07-24

This is a short release-like local-app sample, not the final five-minute Desktop matrix pass.

- Application: staged `build/local-test/Naviamp.app` produced by the verified Desktop distributable
  tasks, with the visualizer closed.
- Tool: macOS `top`, 15 samples at a two-second cadence.
- Foreground idle: 0.3–1.2% CPU after the initial sample, median about 0.4%; resident memory remained
  approximately 330–333 MB.
- Foreground active playback initially measured approximately 9.3–21.5% CPU with a median around
  13–14%. Moving Core's default playback poll from 250 ms to one second reduced the matching rebuilt
  sample to 5.5–12% CPU with a median around 7.7%. Resident memory moved through an approximately
  373–388 MB collection cycle rather than growing monotonically.
- Plexamp 4.13.2 was measured on the same Mac with its Electron main process and all three helpers
  aggregated. Active playback sampled approximately 6.2–7.3% CPU with a median around 6.5%, using
  approximately 212–216 MB resident. Naviamp is therefore close to the production BASS reference on
  Desktop CPU, while Plexamp retains a meaningful memory advantage.
- Installed Naviamp 1.5.0 was measured on the same Mac and with the same two-second sampling cadence.
  Active playback sampled 6.4–13.9% CPU after startup, with a median around 10.5%, and used
  approximately 533–549 MB resident. After playback stopped and the process settled, it sampled
  0.4–0.6% CPU and approximately 532 MB resident. The optimized v2 sample therefore used about 27%
  less median CPU and 30% less resident memory during active playback; both versions had similarly
  low settled idle CPU, while v2 retained about 38% less idle memory.

Result: **Desktop foreground-idle and active-playback baselines accepted**. The long-duration
Desktop matrix remains outstanding.

## macOS background-active and sidecar stress sample — 2026-07-24

This release-like staged-app sample accepts the short-duration Desktop background-active and
prefetch/sidecar baselines. It does not replace the required one-hour completion run.

- Application: staged `build/local-test/Naviamp.app`, visualizer closed, playing while minimized.
- Tools: macOS `top` at a two-second cadence, `vmmap`, and JVM native-memory/heap diagnostics.
- A five-minute background-active run sampled mostly 4–10% CPU, with a median around 6–7% and p95
  around 13%. Resident memory cycled between approximately 368 and 524 MB as periodic reclamation
  occurred instead of growing monotonically.
- Starting a new radio initially exposed an unpaced waveform decode burst of roughly 100–350% CPU,
  delayed cancellation responsiveness, and a transient process-memory peak above 1 GB in `top`.
- Core's waveform analyzer now checks coroutine cancellation between PCM chunks and applies an 8 ms
  cooperative delay between chunks. The Desktop host JVM uses a 320 MB maximum heap, 192 MB soft
  target, and 30-second G1 periodic collection policy.
- With the paced sidecar active, CPU remained normally around 4–11%, with a brief approximately
  18.6% decoder-completion sample. Thread count returned from 148 to 77 after analysis completed.
- After settling, `vmmap` reported a 591.6 MB physical footprint and a 729.8 MB peak, both within the
  matching background-active and foreground-transition gates. The JVM heap reported 150 MB
  committed and 95 MB used.
- A shared regression test cancels waveform analysis after its first native read and verifies that
  no second read occurs, covering prompt cancellation when a queue or radio is replaced.

Result: **Desktop background-active and prefetch/sidecar short-duration baselines accepted**. The
one-hour playback/route-cycle run and the remaining full Desktop matrix scenarios are still required
before the cross-platform performance gate can be declared complete.
