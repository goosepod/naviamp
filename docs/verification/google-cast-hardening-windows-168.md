# Windows Google Cast hardening checkpoint (#168)

Issue: https://github.com/goosepod/naviamp/issues/168

## Scope and conditions

On October 4, 2026, the owner moved physical macOS Cast work to
[#221](https://github.com/goosepod/naviamp/issues/221) and requested focused testing with
the Windows desktop application and living room TV. This checkpoint exercises the actual
sender UI; automated tests and sender state do not prove audible receiver playback.
TV observations must be supplied by the owner and recorded separately.

Baseline: accepted GitHub main commit 22762b0c, isolated chore/168-cast-hardening worktree.
The staged app uses the development data profile. Existing queues/provider configuration
are used without changing favorites, saved playlists or provider credentials.

The fresh worktree initially needed its ignored Android SDK path. A native Visual Studio
OpenGL compiler check failed creating its deeply nested scratch/tracker directory. Native
sources and vendored inputs match the existing build, so the unchanged tested audio/graphics
DLLs were reused for this checkpoint. This is not a clean native rebuild. A test-filter
quoting error in an intermediate build invocation was corrected; failed setup invocations
do not count as passing test runs.

## Automated checks

Cast-filtered common app, presentation and desktop checks: 53 tests, zero failures/errors.
The filter also matches BouncyCastle and some Connect tests; this is not 53 independent
Cast acceptance cases. Architecture verification and staged Windows packaging passed. Receiver probes are opt-in;
the physical probe was not enabled, so its early-return test does not establish a physical
receiver pass. Actual-app receiver checks are recorded below.

Command, after staging the unchanged native DLLs described above:

```powershell
cmd /d /c 'gradlew.bat :core:app:jvmTest --tests "*Cast*" :core:presentation:jvmTest --tests "*Cast*" :platforms:desktop:desktopTest --tests "*Cast*" :apps:desktop:stageLocalTestApp verifyCoreFirstArchitecture -x :platforms:desktop:configureDesktopVisualizerOpenGl -x :platforms:desktop:buildDesktopVisualizerOpenGl -x :platforms:desktop:configureDesktopBassJni -x :platforms:desktop:buildDesktopBassJni --console=plain'
```

## Physical test record

| Check | Windows sender evidence | Owner TV evidence | Result |
| --- | --- | --- | --- |
| Discover/select living room receiver | Picker discovered GoogleTV8565 and Living Room TV; selected Living Room TV, connected and displayed its output badge | Correct track and artwork confirmed by owner | Passed |
| Load paused queue and start playback | Paused local track handed off at 3:35; sender retained paused controls and position. Play changed controls to Pause and receiver progress advanced to 4:04 | Owner confirmed correct Bad Company track/artwork, initially paused with no audio; then TV audio playing with no duplicate desktop audio | Passed |
| Pause/resume and seek | Pause held at 4:10. Pointer click near 18% and drag to 50% both left sender at 0:00 instead of requested nonzero positions | Owner confirmed pause held, then TV also showed 0:00 and remained paused | Pause passed; nonzero seeking needs investigation |
| Queue next/previous and artwork | Next loaded Breakdown with Greatest Hits artwork, paused. Previous from Dream On at 0:26 restarted it; Previous from 0:00 loaded Breakdown. Play resumed | Owner confirmed correct track/artwork and audio on Breakdown after Previous | Passed |
| Paused return to local | Cast badge removed; Breakdown kept 1:21 and paused controls | Owner confirmed TV exited Cast and stayed silent | Passed |
| Playing return to local | Cast badge removed around 2:21; local controls remained playing and position advanced to 2:25 | Owner confirmed TV stopped and desktop continued at the correct point without overlap or restart | Passed |
| Minimize/restore during Cast playback | Minimized at about 1:25; restored about 36 seconds later at 2:01 with Cast output and playing controls retained | Owner confirmed uninterrupted TV audio while minimized | Passed for this short interval |
| Sender close/reopen | Closed playing Dream On around 0:26 using the window Close control. Staged process exited; reopening restored queue/position paused locally with no Cast badge | Owner confirmed TV music kept playing after sender closed | Failed receiver shutdown; local paused restore passed |
| Receiver loss/recovery | After physical power loss, progress stopped at 1:06 and controls settled paused; queue remained, no local playback began. Discovery omitted Living Room TV while off, rediscovered it after boot, and explicit selection resumed Breakdown near 1:06 | Owner confirmed Cast box itself was unplugged, then powered back on; TV resumed correct track/artwork/audio near 1:06 without desktop duplicate | Passed; disconnect detection latency was not timed precisely |

At the end of the checkpoint the sender was paused and returned to local output,
keeping Breakdown at 2:33. No production source files changed during this checkpoint.

## Remaining hardening scope

Sender shutdown left audible receiver playback running. Reopening the sender restored
the local queue paused at its last published position rather than reattaching to the
still-playing receiver. Investigate common shutdown orchestration and completion of
the receiver STOP effect before the host cancels its scope/exits. Do not treat local
queue restoration as proof that receiver shutdown completed.

The nonzero seek result on the FLAC source is unresolved. A contrasting pointer seek
on Breakdown (MP3 source) succeeded at 1:21 and the owner confirmed that position and
paused state on the TV. The shared Cast controller requests MP3 streaming when the
provider supports transcoding; source format shown by the UI does not prove the wire
codec. Distinguish command handling and receiver/provider stream seekability before
assigning a cause. A preceding local seek
on a restored paused queue also started playback; that separate observation has not
been isolated or reproduced sufficiently to attribute it to Cast.

This first physical pass does not establish signed Cast certificate-revocation validation,
offline/downloaded-media policy, synchronized lyrics, EQ/normalization/crossfade behavior,
gapless transitions, Android screen-lock lifetime or iOS support. These remain tracked in
#168. Branding/publication remains #217; physical macOS verification belongs to #221.

Run each targeted case once, then investigate a concrete failure or missing observation.
Do not repeat unchanged cycles merely to increase the test count. Never print provider
credentials or signed media URLs into the public evidence.
