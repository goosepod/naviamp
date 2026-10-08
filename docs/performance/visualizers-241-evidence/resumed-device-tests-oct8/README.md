# October 8 resumed physical-device tests

See the [performance report](../../visualizers-241-direct-presentation.md#october-8-physical-device-resumption)
for interpretation, limitations, native-boundary audit and excluded samples. CSV values are process
CPU as a percentage of one core, not whole-device utilization or battery consumption. Android CPU
intervals and their following Perfetto intervals are separate. Mac GPU traces also run separately
from CPU samples; no Gradle build overlaps accepted Mac CPU intervals.

- Pixel 10a, physical USB device, Android 17/API 37, 1080 × 2424, density 420, active 60 Hz,
  charging (98% at resumption), no reported thermal throttling. Benchmark package is profileable
  and minified. Retained APK SHA-256:
  `9858174f62f533beca6ef9261bf07144b26474d392c28497d4815839956537d3`.
- M1 Mac, macOS 27.0.1, AC power, two 2560 × 1440 displays at 75 Hz/1×. Measurements use the
  disposable packaged real application and isolated development profile. Native visibility guards
  must pass for visible acceptance. Minimized captures intentionally fail visibility.
- Local fixture: two ten-minute mono 48 kHz WAV tracks, short and overflowing metadata, muted.
  No production account or user library is involved. Fresh tracks are selected after reaching the
  ten-minute end; ended captures do not establish a successful pause interaction.
- Shared UI: 528 JVM tests, zero failures/errors/skips; Android/JVM/iOS device and simulator
  compilation, architecture guard, minified Android benchmark build and native Metal test pass.
  There is no physical iOS performance evidence.

The `resume-view-*` Android rows are **rejected** View translation experiments; their names retain
the attempted paused setup, but captures show playback active. They repeatedly submit parent
buffers and their source is removed. `resume-sc-long-paused` is a startup sample; use its warm
repeat and `resume-long-paused-final` for steady scrolling cost. Initial shader transitions remain
listed separately from warmed samples. Raw screenshots, traces and stack profiles remain local.

Mac CSV acceptance labels distinguish visible, hidden and excluded captures. The first wide Sphere
sample includes two resize-related parent frames. The initial paused long-metadata sample includes
two transition frames; its settled repeat is the scrolling-only result. Neither outlier is silently
discarded. GPU JSON contains only the test application's exported interval summary; channel sums
can overlap and do not quantify utilization or energy.

`android-compositor-untraced.csv` records the follow-up direct process-counter comparison, with
Perfetto inactive. It is the compositor budget evidence; graphics-trace compositor CPU is only a
diagnostic because instrumentation adds overhead. SurfaceFlinger is system-wide, so its CPU must
not be described as exact Naviamp attribution. No active virtual display/mirroring process was
found. Initial direct-counter rows include startup/loading; the `-warm` rows establish the static
and scrolling comparison. The app-only summaries therefore do **not** establish Android acceptance.

Reproduce captures with `scripts/visualizer-probe/android-player-sample.py`, then query the retained
trace using `scripts/visualizer-probe/presentation-trace.sql`. Mac setup and control-agent commands
are documented in `scripts/animation-probe/README-visible-app.md`. Keep playback state, metadata,
window size, refresh, scale, power and fixture rate fixed across comparisons, and verify motion
plus unchanged sibling regions independently of the CPU and frame counters.
