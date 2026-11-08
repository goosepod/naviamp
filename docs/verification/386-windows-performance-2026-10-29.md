# Windows progress timeline performance — October 29, 2026

Latest: [UI Automation attribution and corrected measurements](386-windows-uia-attribution-2026-10-29.md)
identify the Computer Use requester. Observation-connected samples below remain historical diagnostics;
the requesting-client question is resolved. Remaining CPU/compositor limits are recorded in the latest report.

Tracking: #386; follows the [earlier Windows investigation](386-windows-release-stabilization.md)
and [combined candidate acceptance](386-windows-resume-2026-10-29.md). Work starts from
`ddbf9743` on dedicated branch `fix/386-windows-performance`. Frozen release scope, v2.10.0/build 60.

## Reproduced work and shared correction

Routine progress telemetry rebuilt an equivalent finite reveal animation and submitted it to the
native compositor again. The existing rendered waveform regression was extended to require that
an update within the existing prediction tolerance retain the compositor timeline. It failed
before the change: cached pixels and zero parent redraws alone had missed redundant submissions.

The shared predictor now retains its timeline revision while telemetry matches its prediction.
The shared waveform keeps the corresponding layers. Seeks outside the existing tolerance, pauses,
resumes, duration changes and invalid durations still update immediately. Neither playback update
frequency nor compositor motion is reduced. An explicit shared monotonic reveal epoch lets Core
rebase the remaining finite timeline when native presentation is recreated or resized; the shared
fallback initializes from that same epoch, then advances on Compose's frame clock and still
invalidates its own drawing frames.

All production edits are in common UI code. No Android/Desktop/iOS production adapter changes,
OS accessibility changes, renderer switch, native-library changes, setting, schema, or version bump.

## Measurement conditions and baseline

Disposable fixture-only profile copied from the October 29 Windows test profile; output muted,
cache/prefetch disabled; loopback mixed metadata, two tracks/two albums, ten-minute WAVs.
The normal installed app, accounts and data are excluded. The baseline observer uses the previous
packaged source `319f6913`; later editorial edits do not change animation behavior. Its production
payload hashes are retained locally. The observer only counts existing Skiko parent frames and
process CPU, with its screenshot capture disabled. Independent Computer Use observations confirm
visible metadata/clock movement. CPU is a percentage of one logical core, over ten seconds.

Main canvas 934×601, OpenGL; 2560×1440 display at 60 Hz, AMD Radeon Graphics driver
31.0.21923.11000; existing My Custom Plan 1 power plan. No build runs during the settled intervals.
Before/after window size, renderer, display, scale and power conditions are held constant.

| Settled baseline state | CPU / one core | Parent frames / 10s |
| --- | ---: | ---: |
| Static, short metadata paused | 3.906% | 0 |
| Marquee only, long metadata paused | 3.437% | 0 |
| Progress only, short metadata playing | 5.780% | 0 |
| Combined, long metadata playing | 6.874% | 0 |

The initial diagnostics (3.906% static, 9.842% first progress, 8.748% later progress) and
5.468% marquee sample with a hovered tooltip are retained, not substituted for settled states.
The progress diagnostics include compiler activity and are not the matched final comparison.

A separate five-sample baseline combined GPU run reports app engine-sum mean 0.046962,
DWM engine-sum mean 14.9151 and whole-desktop DWM CPU 30.5408%. DWM includes other applications;
these values do not certify isolated compositor energy or a low whole-desktop baseline.

Native thread diagnostics identify the AWT Windows event thread and an additional UI Automation
worker. The worker's separate ten-second diagnostic used 1.560% CPU (reported kernel time);
its wall-clock stacks contain UIAutomationCore calls. Wall-clock instruction snapshots are not
on-CPU attribution, and export symbol names may be approximate. This evidence does not justify
disabling accessibility or accepting the elevated full-app CPU baseline.

Raw local evidence: ignored `build/windows-performance-oct29`, including all phase outputs,
thread snapshots, native worker samples, GPU JSON and payload hashes.

## Regression development record

The first focused run caught two new assertions assuming a mathematically exact 210,000 ms end;
existing float-to-millisecond conversion yields 209,999 ms for that input. The assertions now
require the original exact timeline end minus elapsed time, preserving that production behavior.
The corrected focused selection passes 36 cases and Android compilation.

The first full affected-suite run reports 622 UI cases with one failure in the existing
`playedWaveformAdvancesBetweenUpdatesAndStopsWhenPaused` test. The new fallback had advanced
from wall-clock time rather than the Compose frame clock. It was corrected to capture the reveal
epoch offset on fallback entry and advance with frame timestamps. The existing assertion remains
unchanged; the added pixel regression also checks entry at the current position and subsequent
frame-clock movement. The failed intermediate candidate is not accepted or packaged as a pass.

## Final candidate checks and visible measurements

Product candidate `9243c813` passes all 622 shared UI cases and 531 presentation cases, with
zero failures/errors/skips, architecture verification, shared Android UI compilation, distributable
validation and Windows release packaging. The final gate succeeds in 3m58s. JVM/Android are
verified here; Apple compilation remains for hosted verification. No host wiring is added.
The test-only observer compiles with JDK 21 and records 1.0x1.0 scale on the candidate window.
All 270 production payload files in its disposable copy match the rebuilt package; only the
launcher configuration and observer JAR differ.

Release ZIP SHA-256: `FD0D8C018D35DB9379182AA430A0BC267140E833618D387C0A190515F9508165`.

| Candidate state | CPU / one core | Parent frames / 10s |
| --- | ---: | ---: |
| Static, fully painted after resize | 0.312% | 0 |
| Marquee only | 0.312% | 0 |
| Progress only, first / warmed | 4.062% / 2.031% | 0 / 0 |
| Combined, first / after menu | 1.406% / 1.094% | 0 / 0 |
| Menu + combined, first | 5.156% | 0 |
| Menu + combined, three warmed repeats | 2.187% / 2.031% / 1.719% | 0 / 0 / 0 |
| Paired closed state after repeats | 2.187% | 0 |
| Minimized playback | 2.187% | 0 |
| Restored combined motion | 4.687% | 0 |
| Combined after maximize/restore | 2.968% | 0 |
| Final warmed static state | 0.000% recorded | 0 |

The active menu surface also records zero frames. Disposed observer entries with unavailable
scale or `SkiaLayer isn't initialized yet` are not counted as active painted surfaces.
The recorded zero static CPU delta reflects process-counter resolution over that interval;
it does not assert literally zero CPU work.

The warmed menu brackets record 10.244, 112.898 and 44.695 ms aggregate compiler elapsed time;
the paired closed bracket records 88.230 ms. Warmed progress records 106.330 ms; minimized
45.398 ms; restored 15.200 ms; post-resize 37.150 ms; final static zero. The new read-only
`measure-windows-application-phase.ps1` obtains HotSpot counters outside the observer interval
using the full JDK's `jcmd`, even though the packaged runtime lacks `java.management`.
These bracketed compiler times are not an exact decomposition of process CPU. In particular,
compilation does not explain the complete restored CPU outlier.

| Separate candidate GPU control | App engine-sum mean | DWM engine-sum mean | Whole-desktop DWM CPU |
| --- | ---: | ---: | ---: |
| Static | 0.000000 | 13.7801 | 28.528% |
| Marquee only | 0.000000 | 14.3552 | 31.508% |
| Progress only | 0.037164 | 14.0124 | 27.983% |
| Combined | 0.033861 | 13.8739 | 29.520% |

Each GPU control contains five valid samples, collected separately from the ten-second observer
CPU intervals. App CPU during these counter runs is respectively 0.547%, 3.773%, 3.699% and
5.152%; the higher readings are retained too. Engine sums are not percentages of total GPU
capacity, and DirectComposition work can be charged to DWM. Whole-desktop DWM activity includes
other applications and does not prove isolated Naviamp compositor energy acceptance.

Visible observations confirm short-track clock movement, long title/artist/album motion, a painted
menu above moving content, Escape dismissal, pause at 3:36, paused seek to 5:18, and resumed
progress through 6:00. Minimization/restoration returns moving content at the retained position.
Maximize/restore while playing preserves the new position and correct fitting/overflow clipping.
One post-restore Computer Use capture reports `no monitor found for window`; refreshing the
returned window and activating it once recovers capture. Windows UIA exposes the native shell
rather than the full Compose tree; spoken accessibility is not established by these observations.

## Startup rejection, fresh control and remaining trace

The first observed candidate launch leaves its parent surface white until maximize/restore.
Its initial 0.469% sample is explicitly rejected as a visible-performance pass. Native raster
pixels alone are not a valid parent surface. Resizing paints the full application, and subsequent
measurement screenshots confirm actual motion. Two separate launches through the unmodified
production launcher paint correctly without the observer; this does not erase the rejected startup
observation or establish its cause.

The pre-fix baseline is relaunched under the later conditions. Its fresh static samples record
2.187%, 0.937% and 5.780%, all with zero parent frames, and compiler brackets of 153.441, 17.701
and 4.934 ms respectively. It visibly paints the complete application. CPU variation therefore
also exists in the unchanged source. Lower candidate idle readings are not attributed entirely
to the common change, and compilation alone cannot explain the variation.

The command shell is 32-bit. Native WPR is actually present through `C:/Windows/Sysnative/wpr.exe`,
despite the missing PATH/System32 command. This corrects the earlier tooling-unavailable assumption.
The account belongs to Performance Log Users but has a medium-integrity, non-administrator token.
Starting a named twelve-second CPU trace fails with `0xc5585011`,
`Failed to enable the policy to profile system performance`. WPR confirms no recording remains.
This is an operating-system privilege failure, not an automatic approval-review rejection.

An administrator capture helper is prepared at `scripts/animation-probe/capture-windows-native-cpu.ps1`.
It validates an existing disposable Naviamp PID, leaves the app unelevated, bounds the trace, and
only stops/cancels its unique recording instance. It is syntax-checked; its administrator capture
path has not been executed in this session. Raw traces stay local. User assistance with this
administrator-only operation is requested before further native CPU attribution.

The shared regression proves redundant compositor submissions are removed, while real-window
checks establish retained motion and zero continuous parent redraws. Warmed menu repeats add no
sustained CPU over the immediately paired closed state. The strict performance gate remains open:
high first/restore/post-resize samples remain, isolated compositor attribution is unavailable,
and the startup-white observation requires reconciliation. No cadence, animation, accessibility
behavior, renderer or acceptance budget is relaxed.

All disposable app and loopback fixture processes are closed while the administrator handoff is
pending. Normal settings, database and window-state hashes match the pre-run values. The original
checkout and unrelated research file remain untouched. No canonical merge, tag, release, store
submission, mirror update or OS policy change is performed.


## Owner-run administrator capture and native analysis

After the handoff above, the disposable muted fixture is reopened with the uninstrumented
production package from `9243c813`: child JVM PID 2836, launcher 29592, loopback fixture 23236.
The recorded target start time is `2026-10-29T18:16:16.2508288-07:00`. The owner runs the helper
for 30 seconds in an administrator PowerShell and reports completion. The app remains unelevated.
The capture succeeds and the target remains alive; the prior administrator handoff is resolved.

Local capture: `build/windows-native-cpu/Naviamp-90b6b65cb93f43d48f476ace105ed305/cpu.etl`,
391,118,848 bytes. TraceEvent 4.0.0 reads it successfully: duration 52.910436 seconds, CPU sampling
interval 1 ms, zero reported lost events, 3,112 target CPU samples. The ETL includes WPR setup and
rundown beyond the requested sleep interval. That original helper did not save exact recording
brackets, so the following 15–40 second diagnostic slice is explicitly not a certified steady
state. Future helper runs save interval brackets; no additional administrator recording is claimed.

The reusable analyzer is `scripts/animation-probe/native-cpu-analysis/`. Its final checked-in source
is compiled using installed Visual Studio Roslyn, .NET 10.0.14 runtime assemblies and TraceEvent
4.0.0 DLLs, and run against this ETL. The SDK project is supplied for reproduction but SDK restore
is not run because this machine has no SDK. Matching Windows PDBs are downloaded from Microsoft's
public symbol server. Sandbox TLS fails with a Windows credential-context error; normal Windows
execution succeeds. No security setting, authentication setup or certificate validation is changed.
Analysis/output stays local and is restricted to PID 2836. Its identity is corroborated by helper
metadata and the still-running matching JVM's thread dump.

The 25-second slice contains 1,036 CPU samples; 318 (30.7%) have no associated call stack.
Known thread counts are:

| Target thread | Samples | Interpretation |
| --- | ---: | --- |
| 8472, C2 CompilerThread0 | 222 | JIT compilation |
| 31012, C1 CompilerThread0 | 88 | JIT compilation |
| 20380, AWT-Windows | 128 | Windows message thread |
| 31584, AWT-EventQueue-0 | 93 | Swing/Compose event thread |

The two identified compiler threads account for 310 samples (29.9% of target samples in this
slice). This supports compilation as a contributor in this particular run; it does not explain
away earlier warmed restore outliers with little measured compilation.

Inclusive stack groups overlap and must not be added together: JVM 307, AWT 112, BASS 112,
UIAutomationCore 61, DirectComposition 15, Skiko 12. There are 180 samples with `ntoskrnl!Etwp*`
frames and 150 with `RtlWalkFrameChain`. These demonstrate profiler/event stack-walking overhead
inside the target. They are not an estimate of a removable application CPU budget.

UIAutomationCore appears in 26 AWT-Windows samples and 35 samples on native thread 7820.
Matching symbols resolve paths including `UiaNode::CrossProcess_GetPropertyValues`,
`RemoteUiaNodeStub::Incoming_GetPropertyValues`, `HandleHookMessage`, and
`OverlappedIOManager::IoThreadProc`. This corroborates real on-CPU UI Automation activity, rather
than relying on the previous wall-time wait snapshots. It does not identify the requesting client
or establish a Naviamp accessibility defect. Accessibility remains enabled. JVM/JIT native stacks
remain partially unresolved, so these module counts are not a complete Java function profile.

No new production change is justified by this mixed capture. The existing common timeline fix and
its passing 1,153 tests stand; this follow-up changes only diagnostic tooling and evidence. The
strict performance gate remains open for reproducible warmed CPU outliers, isolated compositor
cost, and startup-white reconciliation. Next acceptance work should pair non-instrumented windows
under identical idle desktop conditions, bracket compilation, and use a lower-overhead targeted
native capture only where needed. The owner was asked to keep the app visible during the trace;
foreground/occlusion was not independently recorded throughout and is not asserted as verified.

After analysis, the disposable app/launcher and fixture are closed. Normal settings, database and
window-state hashes still match the saved pre-run values. Raw ETL, derived ETLX, PDBs and stack
outputs stay in ignored build directories and are not uploaded.


## Subsequent idle-desktop repeats

The owner authorizes a new idle-desktop pass after the native capture analysis. See
[the Windows follow-up](386-windows-performance-followup-2026-10-29.md) for startup controls,
repeated restore/resize CPU samples, zero-JIT static thread attribution and matched maximized
compositor controls. Startup/visible lifecycle checks pass; sustained CPU acceptance remains open.
