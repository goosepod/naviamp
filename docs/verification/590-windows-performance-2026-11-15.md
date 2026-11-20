# Windows audio and performance, November 15, 2026

Refs #590 / draft PR #606. The desktop was available for this experiment. Audio output now
enumerates `2 - BenQ GL2706PQ (AMD High Definition Audio Device)`. All **13** real-output
tests passed without skips: `DesktopBassJniBindingIntegrationTest` (11),
`DesktopPreviewPlaybackIntegrationTest` (1), and `DesktopRecentRadioPlaybackTest` (1).
They used `-Pnaviamp.bass.test.outputDevice=-1`; the tests are muted. This verifies real
driver initialization and playback operations, not a listener's subjective audio-quality check.
Log: `build/590-feasibility/windows-real-output-2026-11-15.log`.

## Inputs and method

The current CI Windows package is from `9958353df1d4e439e02cefe46b3826755d40916d`;
`c25094661e142f5dc96129c972ed0a07d0c95810` changes no app production source. Package SHA-256:
`c50339af4ad1aac937f0f032e26222827371e03507fe639efe7f167c9760d2fe`.
The original dependency reference is the previous disposable Compose 1.11.0 / Kotlin 2.4.10 /
Skiko 0.144.6 package. It predates the subsequent waveform/visualizer common extractions;
**this is not an identical-source dependency-only comparison**. The current package uses
Compose 1.12.2 / Kotlin 2.4.21 / Skiko 0.150.2.

Ryzen 7 5800H / AMD Radeon Graphics, 2560×1440 at 60 Hz, scale 1.0, unchanged My Custom Plan 1.
Measured parent surfaces were maximized 2560×1369, OpenGL. Production JVM memory/GC options
were retained. Separate synthetic profiles and a loopback fixture on port 18087 supplied two
silent 600-second WAVs, artwork, and short/long metadata; volume zero. Copied profiles initially
referenced an old fixture port, corrected only in these disposable copies before measurement.
No real profile, phone, or TV state was changed.

`ApplicationRenderProbe` measured ten-second process CPU and parent frames with its capture
disabled. Computer Use independently verified painting/motion before and after intervals.
The verified task-owned observation helper was closed before each CPU/GPU interval; accessibility
remained enabled. A five-second target-filtered UIA ETW trace after helper exit recorded zero
requests and zero lost events. Compiler elapsed counters bracketed each interval; compiler
elapsed time is not process CPU and was never subtracted. Separate eight-sample GPU intervals
recorded process and whole-desktop compositor cost. No builds ran during accepted measurements.

## Results

CPU means percent of one core; parent frames are per ten seconds.

| Visible full-app state | Original CPU | Current CPU | Original/current parent frames |
| --- | ---: | ---: | ---: |
| Static, short metadata, paused | 0.156% | 0.156%, 0.156% | 0 / 0 |
| Long metadata, paused, text moving | 0.625% | 0.469% | 0 / 0 |
| Short metadata, playback progress | 2.968% | 3.906%, 2.187% | 0 / 0 |
| Long metadata and playback progress | 1.250% | 2.031% | 0 / 0 |
| Playing, minimized | not repeated | 2.187% | — / 0 |
| Restored, playing | not repeated | 1.718% | — / 0 |
| Active music visualizer | **120.283%** | **102.957%** | **596 / 593** |

| Separate GPU interval | Original/current app CPU | Original/current app GPU sum | Original/current DWM CPU | Original/current DWM GPU sum |
| --- | ---: | ---: | ---: | ---: |
| Paused moving text | 0.871 / 0.824% | 0 / 0 | 7.967 / 7.925% | 2.204 / 2.236 |
| Progress | 3.133 / 2.048% | 0.03631 / 0.04141 | 6.080 / 7.398% | 2.170 / 2.179 |
| Combined | 2.584 / 2.662% | 0.04110 / 0.04659 | 8.917 / 7.009% | 2.268 / 2.205 |
| Active visualizer | **110.392 / 97.783%** | **16.826 / 16.570** | 9.701 / 9.314% | 6.921 / 7.052 |
| Current minimized | — / 2.720% | — / 0 | — / 6.082% | — / 2.637 |

GPU engine sums are neither percentages of whole-GPU capacity nor energy. DWM covers the
whole desktop. Individual samples do not establish statistical equivalence or CPU attribution.
Equal current visible/minimized progress CPU does not prove every CPU cost comes from audio.

Retained outliers: first static GPU intervals used 11.192% original and 33.397% current app CPU.
Current unchanged repeat used 0.526%, zero app GPU, 3.575% DWM CPU and zero DWM GPU sum.
The cause of the first-interval spikes is **not established**. The first current progress observer
interval used 3.906% versus 2.187% on repeat; both had zero parent frames. Compiler brackets
were 110.189/70.784 ms respectively, not a numerical decomposition of process CPU.

## Visible checks and remaining gates

The current normal launcher painted connection setup. The instrumented fixture launcher painted
artwork, controls, and Home. Long title/artist offsets visibly changed while playback advanced;
pausing held current elapsed position at 2:17 while text continued moving. The original paused
long position held at 1:13 with changing text offsets. Album text ellipsized within its viewport.
Current short playback advanced, restored from minimized with elapsed position advanced to 4:52,
and pointer seek moved from roughly 7:06 to 2:00. Space/pointer playback actions worked.

Resize is **not fully accepted**. The original restored to a 936×633 window at (55,48), briefly
showed stale artwork, then painted correctly without input. Current restore earlier produced a
partly offscreen 2380×1293 capture at (-182,147). A later relaunch/restore trial exposed inconsistent
native Maximize/Restore accessibility state versus full-size capture geometry; repeated captures
did not establish a clean normal-window transition. Native maximize-hover snap-layout popups were
also observed and excluded from timing. These are different stored geometries, not a matched
resize regression proof. Diagnose the current window/capture/placement discrepancy before signoff.

The native accessibility tree exposes window chrome, not the internal shared Compose controls.
Tab input was exercised; complete native focus traversal and spoken-reader acceptance remain open.

The active visualizer visibly moved in **both** apps and consumed roughly a CPU core with about
60 parent frames/sec and substantial GPU cost. Both include the native OpenGL visualizer library.
This is pre-existing behavior, **not an acceptable cheap-rendering result** and not evidence that
the upgrade caused it. Preserve animation cadence; investigate native image transfer and parent
draw ownership rather than disabling animation or accepting the cost.

The normal static/marquee/progress path passes the no-continuous-parent-repaint check. Real audio
output is no longer blocked. Full Windows performance/release acceptance remains open for the
visualizer budget, unexplained outliers, resize, and native accessibility coverage. These results
permit common host preparation; they do not authorize tvOS shipping or a ready-to-merge claim.

Raw evidence: `build/590-windows-2026-11-15/{current,baseline}` (phase text, compiler brackets,
GPU JSON, logs), `inputs.json`, `fixture-profile-correction.json`, `observer-cleanup.jsonl`, and
`final-inventory.json`. Both apps and the verified fixture/helper exited. Production launcher
configs were restored; all **282 current / 278 original** inventoried files matched SHA-256.
