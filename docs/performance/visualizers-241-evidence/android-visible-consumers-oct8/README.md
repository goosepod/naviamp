# Android visible-consumer checkpoint, October 8

Physical Pixel 10a, API 37, profileable/minified `app.naviamp.android.benchmark`.
APK SHA-256: `6fe2df293d7c8e77d27d9f61acdf7188a81d782372948cd9592d1f1a53d8ad31`.
1080×2424 display at 60 Hz, density 420, 1006×814 visualizer, USB charging at 100%,
overall thermal status 0 in every recorded interval. Fixture: 600-second mono 16 kHz WAV,
static artwork placeholder, local server through ADB reverse. This includes native resampling;
it does not measure battery life or a representative decoded music/artwork workload.

`measurements.csv` records process CPU as a percentage of one core, duration, and parent-window
frames from gfxinfo. Trace summaries come from a separate equal-duration interval immediately
following each CPU interval, using `scripts/visualizer-probe/presentation-trace.sql`.
Driver durations are wall time, including waits; parent frame counts do not measure separate-surface
GPU cost. Native GPU/compositor timing remains necessary for complete performance acceptance.

`live-short-plain` is the first short-track capture; its 7.22% CPU is retained, but the warmed
30-second repeat at 5.01% is the steady-state checkpoint. Long scrolling playback remains expensive
at 11.18%; the paused scrolling title consumes 4.05%. Neither is accepted as a completed animation
performance fix. Background artwork is 3.48% (short) and 3.90% (long); selected Analog is 3.86%,
versus 5.83% in the prior matching selected-Analog background run. All new background traces have
no GLES draws, rendering callbacks or hardware bitmap uploads. No meaningful artwork-only
background CPU reduction is established versus the preceding clean 3.54% run.

Raw screenshots and system traces stay local. Motion checks compare only owned test content:
Analog active changed 777,985 of 818,884 visualizer pixels; the paused scrolling title changed
13,128 of 36,216 title pixels. Short-track progress advanced 20 pixels over the initial 15-second
capture. Frozen/blank startup captures are excluded. Tests total 111 passing shared regressions;
Android/JVM/iOS arm64/iOS simulator arm64 compilation, architecture guard and benchmark build pass.
See the parent performance report for remaining acceptance work and platform-boundary audit.

Analog restored at 59.2 FPS, with 778,469 changed pixels; paused Analog used 0.32% CPU with an
unchanged cached picture and no rendering callbacks or GLES draws. Ocean active/restored measured
17.55%/17.12%, both 40 actual FPS and zero parent frames; restored motion changed 124,165 pixels.
Earlier visible CPU samples included scrolling metadata, so these are not matched GPU improvement
claims. The `before-*` summaries preserve clean pre-change selected-Analog/artwork background evidence
from committed a8833f04 (APK SHA-256 `4fb8f4119884a87f0701c9e810b577aeccd44fccf80af191c44b33a315a95a56`).
