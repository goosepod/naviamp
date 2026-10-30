# Windows idle-desktop follow-up — October 29, 2026

Tracking: #386 and draft PR #406. Follows [the timeline fix and administrator CPU capture](386-windows-performance-2026-10-29.md).
Branch `fix/386-windows-performance`, starting commit `6b51411a`; product package remains
`9243c813`. This pass changes verification evidence only, with no production/platform edits.

## Conditions and integrity

The owner explicitly agrees to leave this Windows desktop idle. The visible checks use an isolated
copy of the muted loopback fixture profile, mixed metadata, two tracks/two albums and ten-minute
WAVs. The real profile/accounts are excluded. Display remains AMD Radeon Graphics,
31.0.21923.11000, 2560×1440 at 60 Hz, scale 1.0, My Custom Plan 1. No builds or WPR recordings
run during the measurements. GPU counters and native thread diagnostics run separately from the
observer CPU intervals. Computer Use screenshots bracket the visible state checks; no input is
sent during a measurement. Continuous foreground/occlusion is not independently instrumented.

Release ZIP SHA-256 remains
`FD0D8C018D35DB9379182AA430A0BC267140E833618D387C0A190515F9508165`.
All 270 production payload files in the observer copy match the canonical package; its launcher
configuration and extra observer JAR are the only test additions. The observer directory is new,
with no inherited phase command. CPU is a percentage of one logical core over ten seconds.
Compiler elapsed counters bracket each phase and are not a subtraction from process CPU.

Local evidence: ignored `build/windows-performance-followup`, including phase text/JIT outputs,
GPU summaries, thread deltas and normal-profile hashes. No raw stack data is uploaded.

## Startup and visible lifecycle

The unchanged production launcher starts and paints the complete Home/player interface without a
resize. It restores the long fixture identity paused at 7:13. The production control is then closed.
The observer launcher starts against the same fixture profile and paints the same complete UI
before its first measurement command. The first attachment/counting phase also retains the fully
painted interface. The historical white observer startup does not reproduce either before or after
attachment. This is a passing startup smoke check, not an explanation of the earlier rejected run;
that observation stays in the original record. No speculative production rendering fix is made.

At the original 934×601 parent size, long metadata visibly scrolls. Starting playback advances the
clock through 7:39, 7:49 and 8:11. The tooltip over Pause is visibly painted and is dismissed before
the no-hover interval. Minimize records `iconified=true`; restoring returns moving content and the
correct position, through 8:37 and 9:04. Seeking backward to 2:18 keeps the same track for subsequent
checks. Maximize paints the complete large layout; all three metadata rows now fit, so their lack
of scrolling is the expected fit policy and is not accepted as marquee evidence.

Pausing in the maximized window retains 3:15 through the static checks. Restoring the original
size resumes overflow scrolling while progress remains paused. Resuming playback advances through
3:21 to 4:31, with correct clipping and no blank/stale parent surface. Keyboard close works.
This pass does not establish screen-reader speech or repeat the prior menu/input regression matrix.

## CPU repeats and compiler brackets

All rows below have zero parent frames. The tooltip's live owned surface also has zero frames;
after dismissal, the disposed/uninitialized surface entry is ignored. No animation cadence,
accessibility behavior, native renderer or CPU budget is changed.

| State | CPU / one core | Compiler elapsed ms |
| --- | ---: | ---: |
| Paused marquee, initial | 3.125% | 7.599 |
| Combined, tooltip hover A | 5.937% | 82.496 |
| Combined, tooltip hover B | 16.716% | 77.104 |
| Combined, tooltip dismissed | 5.156% | 169.529 |
| Minimized playback | 2.343% | 149.367 |
| Restored combined A | 10.935% | 54.276 |
| Restored combined B | 7.342% | 41.250 |
| Maximized static, fitting metadata | 3.437% | 0 |
| Original size after resize, paused marquee | 3.749% | 0 |
| Original size after resize, combined | 4.999% | 39.569 |

The tooltip-hover rows are retained as hover diagnostics, not substituted for no-hover steady
state. The restored outlier reproduces under the owner's idle-desktop condition. The zero-JIT
static and paused-marquee rows show that compilation cannot account for the persistent baseline.
These results fail to clear the sustained CPU concern despite zero unrelated parent redraws.

A separate, non-WPR ten-second static/maximized thread-delta diagnostic records 4.213% process CPU
(421.875 ms). AWT-Windows thread 27188 accounts for 218.750 ms, including 171.875 ms kernel time.
Native thread 28884 accounts for 171.875 ms, including 140.625 ms kernel time. Together these are
390.625 ms, about 92.6% of this process delta. The two other nonzero retained threads each record
15.625 ms; one is the test observer. Windows CPU counters have coarse quanta, and thread snapshots
exclude exited/new threads, so these are diagnostic deltas rather than an exact causal partition.
The JVM thread dump identifies AWT-Windows and the observer; 28884 is not a Java thread.

After all acceptance intervals, the existing bounded native sampler examines thread 28884 for
five seconds. It records 1.558% one-core CPU and a UIAutomationCore native stack. Most instruction
snapshots are at a wait syscall; their frequency is not on-CPU attribution, and export names are
approximate. Combined with the prior symbolized on-CPU ETL, this supports UI Automation/native
message work as a specific next investigation target. It does not identify the requesting client,
prove the cost is entirely external, or justify disabling accessibility. No native sampler or
jcmd attachment runs inside an observer CPU interval.

## Separate compositor controls

Each row contains five valid GPU counter samples. Maximize changes the parent to 2560×1369;
its two states are matched to each other, not compared as equal-size CPU samples with the small
window. Long text fits at this size, so the playing row is progress-only. The original-size
combined row is additional diagnostic evidence, without a new matching small-window GPU control.

| State | App engine-sum mean | DWM engine-sum mean | DWM CPU / one core | App CPU during counter collection |
| --- | ---: | ---: | ---: | ---: |
| Maximized progress, fitting text | 0.034750 | 12.695720 | 21.204% | 5.958% |
| Maximized static, fitting text | 0.000000 | 12.704227 | 21.050% | 2.757% |
| Original-size combined | 0.031726 | 15.435832 | 31.015% | 3.844% |

The matched progress/static pair shows no material increase in whole-desktop DWM engine sum at
this sample resolution, with low directly charged app GPU activity. Engine sums are not total GPU
capacity percentages or energy measurements. DWM retains a substantial baseline even with the
opaque maximized app; its counters still cover the desktop and cannot isolate Naviamp's individual
DirectComposition surfaces. This pass narrows the observation but does not certify per-window
compositor energy or the complete static/individual/combined GPU matrix.

## Result and next action

Startup, motion, pause, seek, minimize/restore, maximize/restore and clipping checks pass in this
run. The sustained CPU gate remains open: warmed restored and zero-JIT static readings are still
high. No production change is supported by this follow-up alone. Keep PR #406 draft rather than
repeating the same checks as if a successful build cleared the runtime concern.

The next diagnostic should identify the client/request pattern driving the hot Windows UI
Automation/message threads, then repeat a paired application measurement with equivalent
observation and desktop conditions. The requesting client is currently unknown. Preserve
accessibility and animation behavior. Isolated compositor attribution still requires a suitable
per-surface/native diagnostic; whole-desktop PDH counters cannot supply that proof.

The disposable app, launcher and loopback fixture are closed. All three normal-profile hashes
(settings, database and window state) match before/after. No platform production file, package,
OS setting, release version, tag or release publication changes. Existing 1,153 passing shared
UI/presentation tests remain the validation for the unchanged product fix; they are not rerun for
this evidence-only update.
