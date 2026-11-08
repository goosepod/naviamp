# Windows UI Automation requester attribution — October 29, 2026

Refs #386 and draft PR #406. Work begins at `047743ee` on `fix/386-windows-performance`.
Product package remains `9243c813`; this update changes only test diagnostics and verification.
Follows the [idle-desktop repeats](386-windows-performance-followup-2026-10-29.md).

## Finding and controlled comparison

The recurring requester is `codex-computer-use.exe`, the Computer Use observation helper.
The administrator PowerShell left open by the owner is not used: the existing Windows credential
context can enable the user-mode UIAutomationCore performance provider without administrator
kernel-profile privileges. No terminal UI input, UAC automation or OS setting change is performed.

A first broad provider capture around an isolated production startup reports 46,064 lost events.
Its counts are rejected as quantitative evidence. It supplies the lead that the receive events
carry `PeerProcessId`. Subsequent captures use only target-PID events 523/524/545/546 and performance
keyword `0x10000`; they report zero lost events. ETW event schemas are read from this Windows
installation, and TraceEvent 4.0.0 decodes the local captures.

The first clean ten-second capture of production JVM 22960 records 790
`ServerConnection_RequestReceived` events, all with peer PID 29600, verified as
`codex-computer-use.exe`. There are 330 request execution start/stop pairs. Execution methods
include provider entry-point lookup (160 starts), property values (50), properties/patterns (40),
bulk fetch (40), pattern provider (30) and point/focus drilling (10). Incoming transport request
events and execution calls are different counts; 790 is not a count of distinct rendered frames
or user actions. No explicit Computer Use UI calls run during this capture.

Releasing the Computer Use JavaScript session through its supported kernel-reset tool ends its
helper. The next ten-second target-only capture records zero events and zero losses. No app,
accessibility setting, animation or native renderer is changed. Two separate non-profiled CPU
samples of the same production JVM, paused with long metadata, record 0.624% and 0.000% of one core.
The zero means no recorded CPU quantum in that interval, not literally no work or a frozen surface.

Reconnecting Computer Use, selecting the same window and taking a screenshot restores polling.
The app is fully painted with visibly changed scrolling text and the same paused 4:41 position.
A separate ten-second CPU sample rises to 2.964%; its two nonzero native threads account for
156.250 and 140.625 ms. A separate loss-free ten-second trace records 790 incoming request events,
all from the newly started `codex-computer-use.exe` PID 19016. The executable name/start time is
verified while that process is alive. Releasing the session again removes the requests.

This controlled change identifies a substantial source of measurement overhead. It does not
numerically explain every older high CPU sample or authorize subtracting guessed overhead from
the recorded results. Earlier observation-connected samples remain diagnostics; they are not clean
application baselines. The Windows accessibility implementation stays enabled and unchanged.

## Corrected application measurement procedure

The same hash-matched disposable observer package is then measured at parent 934×601, scale 1.0,
OpenGL, AMD Radeon Graphics 31.0.21923.11000, display 2560×1440 at 60 Hz and My Custom Plan 1.
The fixture remains muted, loopback-only, two tracks/two albums with short/long metadata and
600-second WAVs. A separate fixture profile excludes the real user accounts/data. All 270
production payload hashes match the canonical package. Only the observer JAR/configuration differs.

Before each state, Computer Use establishes and visibly checks it, moves off hover controls, then
its JavaScript session is released. CPU samples run with no UI calls and no Computer Use helper.
Reconnecting afterward checks paint, progress position and scrolling movement; it is released again
before the next interval. The packaged-app observer only counts frames/CPU, with screenshot capture
disabled; jcmd compiler counters bracket CPU intervals. WPR does not run. GPU/native-thread
measurements and diagnostic compilation run outside the observer CPU intervals.

Two separate filtered traces on observer JVM 24540 confirm zero UI Automation events/losses while
the session is released. A process inventory also finds zero Computer Use helpers then. The final
reusable tool is tested while observation is connected: five seconds, 395 incoming events, all
attributed to a live Computer Use helper, 165 execution starts and zero losses. Its released-session
five-second control reports zero events/losses. Raw ETL and arbitrary payloads remain local.

## CPU and visible lifecycle results

Every phase below reports zero parent frames. CPU is one-core percentage over ten seconds.
Compiler elapsed time brackets the interval and is not an exact CPU partition or a value to subtract.
All samples are retained, including initial/high/compiler-active readings and the low late sample.

| Phase | CPU / one core | Compiler elapsed ms |
| --- | ---: | ---: |
| quiet-combined-a | 3.281% | 139.533 |
| quiet-combined-b | 2.500% | 90.811 |
| quiet-combined-c | 2.500% | 71.212 |
| quiet-combined-d | 2.343% | 61.069 |
| quiet-combined-final-a | 2.343% | 102.913 |
| quiet-combined-final-b | 2.031% | 38.660 |
| quiet-marquee-a | 0.469% | 10.518 |
| quiet-marquee-b | 0.156% | 4.731 |
| quiet-minimized | 1.719% | 56.699 |
| quiet-progress-a | 2.031% | 83.466 |
| quiet-progress-b | 2.031% | 48.414 |
| quiet-resized-a | 4.530% | 293.854 |
| quiet-resized-b | 4.843% | 319.787 |
| quiet-resized-warm-a | 2.031% | 140.968 |
| quiet-resized-warm-b | 0.625% | 19.177 |
| quiet-restored-a | 2.500% | 53.368 |
| quiet-restored-b | 2.343% | 99.249 |
| quiet-static-a | 0.625% | 0.272 |
| quiet-static-b | 0.312% | 0.000 |

The initial combined states remain around 2.3–3.3%, progress-only repeats 2.03%, and minimized
playback 1.72%. Restored combined repeats are 2.50%/2.34%, considerably lower than the earlier
observation-connected 10.935%/7.342%, but those earlier/later runs are not an exact CPU decomposition.
The initial resize repeats are 4.530%/4.843% with 293.854/319.787 ms compiler brackets. Later repeats
are 2.031%/0.625%; final pause/resume combined repeats return to 2.343%/2.031%. The isolated low
resize sample is not substituted for the full sequence or a universal passing playback baseline.

An additional non-profiled playback thread snapshot measures 2.185% total CPU, spread across
compiler/coroutine/native threads rather than the former two-thread UI Automation concentration.
The observer and compiler are among the retained nonzero threads. This does not establish a
complete causal attribution for the remaining playback cost.

Screenshots confirm scrolling metadata before/after the paused marquee intervals; the paused
position remains 4:41. Combined playback advances through 4:45, 7:24, restored 8:18 and 9:28.
The short fixture progresses from 0:09 to 1:19 and holds at 1:25 when paused. Returning to the long
fixture, maximize/restore keeps playback moving from 0:06 to 0:17 and later 3:13 with correct
fitting/overflow behavior and clipping. A paused marquee GPU control holds at 3:24; the final
combined repeats advance from 3:33 to 4:54. All surfaces remain painted. Minimized samples report
`iconified=true` and are lifecycle diagnostics, not visible-motion passes. Spoken accessibility and
the full menu/input regression matrix are not re-certified by this pass.

## Same-size GPU/compositor controls

Each accepted row has five valid samples, collected separately from CPU. The first combined GPU
collection fails with an invalid PDH sample; its empty output/failure is retained, and one retry
succeeds. No failed sample is converted to zero. All accepted states use the original parent size;
no fitting/maximized state is mislabeled as marquee/combined.

| State | App GPU engine-sum mean | DWM engine-sum mean | DWM CPU / one core | App CPU during GPU counters |
| --- | ---: | ---: | ---: | ---: |
| Static | 0.000000 | 0.000000 | 0.918% | 0.331% |
| Marquee | 0.000000 | 2.383579 | 10.339% | 0.254% |
| Progress | 0.034600 | 2.022817 | 9.133% | 2.095% |
| Combined, retry | 0.041523 | 1.996652 | 10.007% | 3.269% |

Releasing the observation session removes a large measurement confound. These controls also expose
real compositor activity during motion: low app GPU alone is not a full acceptance result. Engine
sums are not percentages of total GPU capacity or energy. DWM still covers the entire desktop;
these paired controls report incremental desktop observations, not per-surface Naviamp GPU energy.
The added DWM CPU/activity is retained and is not waived as an acceptable high compositor baseline.

## Reproduction, validation and handoff

`UiaRequestTrace.cs` adds `--uia-capture` and `--uia-summary` to the existing native diagnostic
project. Capture filters PID/event IDs natively, bounds duration to 5–30 seconds, validates a
checkout-local disposable Naviamp target, retains its process identity, refuses overwriting output
and disposes only its unique trace session. Summary reports target request peers/method identifiers,
with no UI property contents or unrelated process listing. Requester names are used only when the
live process start precedes the trace; exited/reused peers need contemporaneous identity evidence.
The [observer README](../../scripts/animation-probe/README-visible-app.md) records the corrected
protocol and SDK commands.

The final C# sources compile with installed Visual Studio Roslyn, .NET 10.0.14 runtime assemblies
and TraceEvent 4.0.0 DLLs. The exact source's five-second positive/zero-traffic captures and summary
are exercised. The overwrite guard rejects an existing trace without changing its SHA-256; a
non-Naviamp target is rejected without recording. The existing CPU analysis CLI still reports
1,036 samples/318 missing stacks for the saved 15–40 second PID 2836 slice. The SDK project restore
path is documented but not run because no .NET SDK is installed. No product/shared suite is rerun
for these test-only changes; the existing 1,153 passing cases still validate the unchanged product fix.

The requester-identification blocker is resolved. The earlier claim that the requesting client is
unknown is superseded by this record. Strict performance acceptance remains open for the retained
playback/resize CPU variability and compositor cost/attribution. The next work can target those
remaining costs with the observation session released, rather than treating UI Automation overhead
as an application animation defect. No animation cadence, accessibility behavior, renderer, CPU
budget or GPU requirement is relaxed, and no new production workaround is added.

The test app/launcher, fixture and every owned recording are closed. The observation session is
released after cleanup. All three normal settings/database/window-state hashes match before/after.
Raw traces, process-identity records and diagnostic outputs stay in ignored
`build/windows-uia-investigation`. No platform production file, release version, schema, tag,
canonical merge, publication or existing administrator PowerShell session is changed.
