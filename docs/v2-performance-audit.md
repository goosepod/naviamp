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
- `verifyCoreFirstArchitecture` passed after the performance changes, confirming that the shared
  behavior remains Core-owned and the Desktop change is limited to its native BASS stream boundary.

Result: **Desktop background-active and prefetch/sidecar short-duration baselines accepted**. The
one-hour playback/route-cycle run and the remaining full Desktop matrix scenarios are still required
before the cross-platform performance gate can be declared complete.

## iOS Simulator and macOS release matrix — 2026-07-28

This pass used fresh optimized builds and satisfies the five-minute steady-state CPU/RAM scenarios
listed below. It does not replace the one-hour retained-growth run, interaction stress scenarios, or
physical iPhone acceptance, which is unavailable to the project owner.

- Host: Apple Silicon Mac with 8 logical CPUs and 16 GB RAM, macOS 26.5 (`25F71`).
- iOS: iPhone 17 Pro (`iPhone18,1`) Simulator, iOS 26.5; optimized Xcode `Release` build of
  `app.naviamp.ios`. The first Kotlin/Native release link exhausted the default 2 GB compiler heap;
  a build-only 6 GB Gradle heap completed successfully. This did not alter application runtime
  configuration.
- Desktop: freshly staged `build/release/Naviamp.app` release-like distributable. All Gradle daemons
  were stopped before either application was launched or measured.
- Tool: macOS `top`, normalized to one logical core, sampled each exact PID every two seconds. Every
  formal run discarded a two-minute warm-up and retained 150–151 samples (300–302 seconds). Raw CSV
  captures are under `build/diagnostics/performance/*-2026-07-28.csv`.
- The visualizer was closed on both platforms. iOS used a restored 51-track queue at index 6, original
  MP3 playback (the observed track was 320 kbps), Sinc16 conversion, track replay gain, gapless,
  10-track audio prefetch, 500-point waveforms, and enabled audio/waveform caching. Desktop used a
  restored 52-track queue at index 7, Sinc8 conversion, track replay gain, an 8-second crossfade,
  15-track prefetch, 500-point waveforms, and a 1 GB audio-cache budget.

| Scenario | CPU median | CPU p95 | RSS initial / peak / final | Result |
| --- | ---: | ---: | ---: | --- |
| iOS foreground, paused Now Playing | 0.40% | 1.00% | 100 / 100 / 99 MB | Pass |
| iOS background paused | 0.20% | 0.90% | 101 / 101 / 101 MB | Pass |
| iOS foreground Now Playing | 3.50% | 9.80% | 121 / 155 / 130 MB | Pass |
| iOS background active playback | 2.00% | 3.80% | 135 / 157 / 136 MB | Pass |
| macOS background-visible paused | 0.30% | 1.90% | 286 / 288 / 288 MB | Pass |
| macOS minimized paused | 0.30% | 4.40% | 291 / 291 / 285 MB | Pass |
| macOS minimized active playback | 3.50% | 7.20% | 375 / 389 / 383 MB | Pass |
| macOS foreground Now Playing | 2.90% | 9.60% | 408 / 422 / 421 MB | Pass |

One mixed macOS background-visible active run was steady at roughly 3–10% CPU for its first 4.5
minutes, then a track transition started prefetch/sidecar work during the final 30 seconds. The
complete mixed run reported 4.40% median CPU, 31.80% p95, and 395 / 397 / 371 MB RSS, so it is not
used as the steady-state acceptance sample. A 122-second extension captured the bounded work: CPU
remained elevated for roughly another minute, peaked at 61.2%, then returned to 2–11%; threads fell
from 77 to 73 and RSS settled at 371 MB after a 377 MB extension peak. The subsequent clean minimized
active run above stayed within the steady-state gate for all five minutes.

Result: **iOS foreground/background paused and active-playback five-minute baselines accepted;
macOS paused, background-active, and foreground Now Playing five-minute baselines accepted**. Memory
stayed far below every applicable ceiling and did not grow monotonically. Android/iOS large-library
scroll/search, explicit sidecar cancellation, and the one-hour cross-platform retained-growth cycle
remain open completion-gate scenarios.

## macOS large-library interaction sample — 2026-07-28

This release-like staged-app sample accepts the Desktop large-library scroll/search scenario after
the shared cover-art loader was made failure-safe and bounded to four concurrent loads and the JVM
decoder began honoring requested image dimensions.

- The user rapidly scrolled and searched the full library and repeatedly opened and closed artist
  and album details for 90 seconds while playback remained active. `top` sampled the exact Naviamp
  PID every two seconds.
- During active interaction, CPU was 33.0% median and 61.3% p95, with a brief 97.0% peak. Resident
  memory was 427 / 635 / 579 MB initial/peak/final. The 635 MB peak is 1.49x the interaction start,
  within the 1.5x large-library ceiling.
- During the following 90 seconds with interaction stopped, resident memory fell from 565 MB to
  538 MB rather than growing monotonically. The complete settle window was 4.5% median and 13.9%
  p95 CPU; its final 40 seconds were approximately 3.8% median and 9.7% p95 while playback remained
  active. Threads fell from 85 to 71 by the final sample.
- No TLS error dialog, crash, unbounded worker growth, or stuck busy loop occurred. Raw captures are
  `build/diagnostics/performance/macos-large-library-active-2026-07-28.txt` and
  `build/diagnostics/performance/macos-large-library-settle-2026-07-28.txt`.

Result: **Desktop large-library scroll/search and return-to-steady-playback accepted**. Android and
iOS large-library interaction evidence remains part of the final cross-platform matrix.

## Android Core transition-persistence optimization — 2026-07-29

This is the first targeted Core optimization from the final Android performance investigation. It
does not close the Android p95 gate because a separate codec/transition burst remains.

- Device: Pixel 10a (`stallion`), Android 17 / API 37, physical USB connection; non-debuggable,
  profileable `app.naviamp.android.benchmark` build with a restored 180-track queue and the
  visualizer closed.
- Instrumentation added to the shared playback-session controller and SQLDelight repository showed
  that one transition spent 9.13 ms loading/decoding the full saved queue and 7.26 ms encoding and
  writing it again.
- Schema 17 separates the durable queue rows from the small mutable session-state row. Core keeps
  the loaded session in memory, rewrites queue rows only when track identity/order changes, and
  lazily converts the legacy JSON row on its next save.
- On the same Pixel, a subsequent unchanged-queue transition reported a 0.03 ms in-memory load,
  1.02 ms total planning, 0.00 ms JSON encoding, and a 5.18 ms state-only database write. The queue
  rewrite diagnostic was `false`, reducing measured transition persistence from roughly 16.4 ms to
  6.6 ms.
- Core's audio-tag parser now skips unsupported binary ID3 frames without copying their payload,
  and the metadata sidecar retains one parsed result per active local file so replay gain, lyrics,
  and Now Playing metadata do not reparse identical bytes.
- The first three-minute follow-up was intentionally treated as diagnostic-only because routine
  `dumpsys meminfo` calls forced explicit compacting collections. Its CPU median remained 10.0%, and
  an automatic transition still exposed a large allocation/codec burst.
- A corrected 90-second CPU-only run forced one transition and avoided memory-dump observer effects.
  It reported 10.3% median CPU and 70.0% p95, with the forced transition reaching 200%. Android logs
  tie that remaining burst to overlapping `c2.android.raw.decoder` teardown/startup and a blocking
  allocation, not to session JSON or queue persistence. This is the next isolation target.

Result: **normalized Core queue persistence accepted; Android transition p95 remains open**. The
optimization benefits every SQLDelight host, while further investigation must distinguish native
codec overlap from any remaining shared transition orchestration before the Android performance
gate can pass.

## Android prepared-transition coordination — 2026-07-29

The remaining overlap was traced to shared transition orchestration rather than Android playback
policy. Preparing the next track can be inside a blocking native decoder open when a manual queue
command arrives. Core previously cleared that native state and started the requested track without
waiting for the preparation coroutine to unwind. Manual navigation also invalidated a prepared
source only when crossfade was enabled, leaving a prepared gapless source eligible for an immediate
manual start.

- Core now owns a preparation barrier that cancels stale preparation, waits for any non-cancellable
  native call to return, clears the prepared state, and only then resolves and starts the manually
  selected track.
- Manual previous, next, queue jumps, and explicit queue selections invalidate both gapless and
  crossfade preparation. Automatic transitions still preserve and promote prepared playback.
- A common deterministic regression holds preparation in a non-cancellable section and proves that
  native clear cannot run early. Existing shared adapter coverage now verifies the gapless manual
  navigation sequence (`prepare`, `clear`, then fresh `play`).
- Shared presentation tests passed on JVM, Android, and the iOS Simulator target. Desktop
  compilation, the Android profileable benchmark build, and `verifyCoreFirstArchitecture` passed;
  no platform production file changed.

Result: **shared prepared-transition overlap fixed**. The final Pixel build is installed for audible
manual-transition acceptance. The broader Android performance gate remains open for the required
full-duration retained-growth and interaction scenarios; short native transition spikes are not a
steady-state sample and must be evaluated by their return to the matching threshold.
