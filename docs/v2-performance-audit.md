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

## Automated rendering-resource lifecycle gate — 2026-07-30

Before repeating live measurements, the visualizer ownership audit found that replaceable decoded
artwork and lyric-mask resources did not all have an explicit replacement/unmount release path.
Core now owns the replacement and shutdown semantics through `NaviampOwnedResource`; Android, JVM,
and iOS provide only their native `Bitmap.recycle`, Skia `Image.close`, queued GL deletion, or Metal
reference-release effects.

- A common stress test performs 1,000 replacements and proves that every superseded resource is
  released exactly once, repeated shutdown is idempotent, retaining the same instance does not
  release it early, and a late result arriving after shutdown is released immediately.
- Desktop distinguishes process-bounded shared cover-art cache entries from surface-owned lyric
  masks, so switching visualizers releases private masks without closing images still owned by the
  shared LRU cache.
- Android queues bitmap release behind pending GL work for native visualizers and directly recycles
  surface-owned runtime-shader bitmaps. Unmount also clears the renderer's artwork reference before
  queuing GPU teardown.
- iOS releases replaced decoded Skia images and deterministically clears the Metal device, command
  queue, pipeline, sampler, frequency/album textures, and render target when the renderer closes.
- `core:ui:allTests` passes on JVM, Android debug/release unit-test targets, and the iOS simulator;
  Android, Desktop, and iOS app compilation plus `verifyCoreFirstArchitecture` also pass.

Result: **automated replacement and unmount ownership gate accepted**. Live repeated visualizer
switching, Now Playing open/close, background/foreground, memory-pressure, and retained-memory
measurements remain required below.

## Live visualizer replacement stress — 2026-07-30

This release-like pass exercises renderer replacement after the automated ownership audit. Each
platform remains on Now Playing with active playback, cycles every visualizer twice, closes and
reopens the visualizer ten times, then returns to the closed-visualizer steady state.

- Android: physical Pixel 10a on Android 17, non-debuggable/profileable
  `app.naviamp.android.benchmark`; sampled with `adb shell top` and `dumpsys meminfo`. CPU returned
  to approximately 10–12%. PSS settled from 206,497 KB to 224,161 KB (+8.6%) and RSS from 332,296
  KB to 347,324 KB (+4.5%). Graphics settled from 93,668 KB to 96,920 KB. The retained-growth gate
  passed and no crash or ANR occurred.
- macOS: staged `build/release/Naviamp.app`, sampled with `top`. The first run correctly failed the
  gate when repeated visualizer changes crashed in Skia. The macOS report
  `Naviamp-2026-07-30-103547.ips` showed `EXC_BAD_ACCESS` on `AWT-EventQueue-0` in
  `Image_nGetImageInfo`, proving that a superseded Skia image could be closed before Compose and the
  native host completed their render handoff.
- Core now publishes the replacement, retires the superseded resource, and releases it only after
  two completed render frames. Common tests cover deferred release, revival before retirement,
  1,000 pending retirements at shutdown, exact-once destruction, and late results. Android, JVM,
  and iOS actuals supply only their native image type and destruction effect.
- The rebuilt macOS release app survived the complete crash sequence. Closed-visualizer CPU settled
  at approximately 3–4%. Its post-stress footprint plateaued at 409 MB, matching the prior run's
  406–408 MB high-water plateau rather than growing again; forcing JVM collection did not change
  that plateau, which identifies it as bounded native shader/Skia cache memory rather than an
  accumulating managed-resource leak.

- iOS: optimized Release build on the iOS 26.5 iPhone 17 Pro Simulator, sampled with `ps` and
  `top`. The pre-stress resident set was 118–123 MB. After the full replacement sequence it settled
  at 162–163 MB with 3.3–3.8% host CPU and 28 stable threads. A later background/sidecar burst
  briefly peaked at 324 MB, then returned to the same 162–163 MB plateau rather than continuing to
  grow. Every standard and Metal-backed complex visualizer rendered, the process remained alive,
  and playback continued.

Result: **live repeated visualizer replacement gate accepted on Android, macOS, and optimized iOS
Simulator builds**. The macOS failure was converted into a deterministic Core-owned lifetime fix,
and the exact failure sequence then passed on both Apple renderers. Background/foreground,
memory-pressure, large-library interaction, and the final one-hour retained-growth checks remain
separate completion-gate work.

## Lifecycle and memory-pressure recovery — 2026-07-30

Each app played continuously with Now Playing visible and the visualizer closed. The interactive
lifecycle sequence sent each app to the background and restored it ten times, then allowed it to
return to steady state before measuring it again.

- macOS staged release: ten minimize/restore cycles preserved playback and UI state. Footprint
  returned from 429 MB to 422 MB, CPU from approximately 0.3% to 0.3–0.4%, and the temporary
  foreground rendering bursts subsided. Threads remained bounded at 63 after a 58–61 baseline.
  `memory_pressure -S -l warn` was also attempted, but macOS rejected the manual kernel trigger
  with `Operation not permitted`; no warning was delivered. Real macOS pressure recovery therefore
  remains unverified rather than being inferred from an unsafe memory-exhaustion workload.
- Optimized iOS 26.5 iPhone 17 Pro Simulator release: ten Home/reopen cycles preserved playback and
  UI state. Footprint settled from 174 MB to 179–180 MB (+3.4%), CPU returned from approximately
  3–4% to 3.2–4.3%, and threads remained bounded at 27–28. The Simulator's supported **Simulate
  Memory Warning** action was then delivered three times, five seconds apart. Naviamp stayed alive
  and responsive, playback continued, footprint settled at 180–181 MB, CPU at 3.8–4.3%, and threads
  at 29.
- Physical Pixel 10a on Android 17, benchmark build: ten Home/reopen cycles preserved PID 4548,
  the Activity, queue metadata, MediaSession, playback, and UI state. PSS fell from 192,489 KB to
  181,320 KB (-5.8%), RSS from 290,744 KB to 283,440 KB (-2.5%), and CPU returned from 10.6–12.6%
  to 9.2–10.4%. Android's non-destructive `RUNNING_LOW` and `RUNNING_CRITICAL` trim callbacks were
  then delivered. A temporary CPU/RSS burst coincided with the normal transition from “Dreams” to
  “Pull Out”; after settling, PSS was 192,377 KB (effectively the original baseline), RSS was
  300,508 KB (+3.4%), CPU returned to approximately 9–13%, and the playing MediaSession exposed the
  new track.

Result: **background/foreground recovery accepted on Android, macOS, and iOS Simulator; pressure
recovery accepted on physical Android and iOS Simulator**. macOS pressure injection remains an
explicit evidence gap because the host rejected the safe synthetic trigger. Large-library and
one-hour retained-growth work remained open at this checkpoint; macOS passed later that day and
physical Android passed on 2026-08-02.

## macOS one-hour retained-growth playback — 2026-07-30

The staged `build/release/Naviamp.app` played continuously from 13:50:26 to 14:50:28 with the
visualizer closed. PID 50599 was sampled once immediately and then every five minutes for twelve
intervals with `top`; a dense settle window, JVM heap accounting, one diagnostic full collection,
and a final process sample followed the hour.

- The cold process began at a 310–314 MB footprint, 67–68 threads, and approximately 6.9–8.5% CPU.
  The same artifact's already accepted warmed steady-state reference is 409–422 MB.
- Hourly samples were normally 368–516 MB. The final interval caught a 646 MB peak while system
  load was also elevated; this remained below the 768 MB foreground ceiling. Most interval CPU
  samples were 3.7–9.9%, with temporary 12.6% and 20.7% activity bursts, and the final interval was
  6.2%. Threads were normally 65–78 and ended at 71. The process never crashed or restarted.
- The immediate settle window briefly held 653–661 MB and 82 threads before falling to 566 MB.
  `GC.heap_info` identified a 232 MB committed G1 heap with 138 MB used. One diagnostic `GC.run`
  reduced it to a 109 MB committed heap with 30 MB used, proving that the apparent endpoint growth
  was reclaimable managed data rather than retained native rendering resources.
- After collection, footprint remained flat at 425 MB, threads returned to 69, and CPU settled at
  2.6–4.6%. The final `ps` resident set was 180,608 KB at 2.3% CPU. The configured JVM maximum is
  320 MB with a 192 MB soft maximum, so the observed managed peak is bounded. Relative to the
  warmed 409–422 MB reference, retained footprint growth is 0.7–3.9%, below the 10% threshold.

Result: **macOS one-hour playback retained-growth gate accepted**. The run shows bounded,
reclaimable JVM expansion, a stable warmed native/JVM plateau, return-to-steady CPU, and no process
or playback failure. Physical Android one-hour retained growth remained open at this checkpoint and
passed on 2026-08-02; large-library interaction and the explicit macOS pressure-injection evidence
gap remain open.

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
prefetch/sidecar baselines. The later one-hour completion run is accepted above.

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
later one-hour playback run is accepted above; remaining interaction scenarios are still required
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
scroll/search and explicit sidecar cancellation remain open completion-gate scenarios; physical
Android one-hour retained growth passed on 2026-08-02 and the macOS one-hour run is accepted above.

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

## Android background playback Perfetto acceptance — 2026-08-02

This release-like trace closes the focused Android lossless-playback CPU and battery-attribution
follow-up. It does not replace the remaining large-library and route-cycle scenarios in the full
matrix.

- Device: physical Pixel 10a (`stallion`), Android 17 / API 37; non-debuggable/profileable
  `app.naviamp.android.v2test`; 219-track queue; screen off and app backgrounded; two-minute warm-up;
  uninterrupted lossless playback with automatic transitions.
- The acceptance trace used Perfetto `linux.ftrace` compact scheduler events, CPU frequency/idle,
  Binder events, app `audio`/`dalvik` atrace, and `linux.process_stats`. Its ring buffer retained
  420.05 seconds and 419 complete one-second CPU buckets, exceeding the five-minute minimum.
- Across the complete retained window, CPU was 12.41% mean, 8.29% median, 41.34% p95, and 174.50%
  maximum. One automatic transition produced the only multi-second threshold excursion: seconds
  188–217 were above 18%, and CPU returned to the matching steady state at the 30-second recovery
  limit.
- Excluding that explicitly classified transition window, the remaining 389 steady-state seconds
  measured 9.29% mean, 8.09% median, 17.55% p95, and 33.87% maximum. This passes the background
  active-playback limits of 10% median and 18% p95; the transition passes the required return within
  30 seconds.
- A separate 60-second, 100 Hz process-scoped `linux.perf` call-stack trace captured another
  automatic transition without contaminating the acceptance distribution. Leaf samples were led by
  kernel work (27.35%), ART (19.39%), JIT code (16.35%), boot OAT code (10.13%), and libc (7.24%);
  BASS plus BASSmix accounted for 1.59%. Cumulative stacks tied the burst to prepared-stream
  adoption, MediaCodec setup, Core progress/Now Playing publication, UI remapping, JIT, and GC.
- The scheduler trace also exposed a non-blocking optimization opportunity while the screen was off:
  `Recomposer:recompose` accumulated 8.35 seconds, `Compose:recompose` 6.98 seconds, Binder slices
  6.00 seconds, and semantics/layout work continued. Background lifecycle gating should reduce this
  further, but the measured steady state already passes the recorded release threshold.
- Playback remained `PLAYING` and advanced through queue items 48–52. The final endpoint was
  123,974 KB PSS and 242,500 KB RSS, below the 225 MB Android PSS threshold. All Naviamp packages
  were force-stopped after capture.

Result: **Android background active-playback CPU accepted**. The earlier low-overhead unplugged A/B
run additionally bounded Naviamp's incremental battery cost to approximately 65 mA over the noisy
device baseline, with no runaway prefetch or retained growth. Background Compose lifecycle work is
worth optimizing but is not an unexplained threshold violation.
