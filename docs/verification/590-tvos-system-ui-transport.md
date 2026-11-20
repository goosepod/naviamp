# tvOS system transport delivery (#590)

The scene runs Naviamp against the local main/Aurral fixture on an owned Apple TV Simulator.
The provider returns distinct tracks so the test can observe a real change in Now Playing
metadata following a system transport command. The test uses the TV remote and Apple TV
Control Center after backgrounding Naviamp. It checks focus before sending Select and uses
the app's accessible playback clock after foreground return. Apple documents the
[Now Playing tab in Control Center](https://support.apple.com/en-mide/guide/tv/atvbef12564e/27/tvos/27).

## Observed delivery

The full tvOS 27.2 scene passed. A focused **Next** selection changed the track in
Control Center while Naviamp stayed in the background. The test paused the new track with
Control Center's playback button after its cursor advanced past the shared Previous restart
threshold. **Previous** reset that paused track to zero. A second **Previous** restored the
original track, and the app's accessible playback clock advanced after foreground return.
The existing scene also verifies remote Play/Pause, background playback, main/Aurral account
setup, relaunch, and encrypted cache recovery.

The expanded Control Center panel exposed Play/Pause, Next and Previous for the fixture.
An Up press from the paused playback button focused the track-information row and skipped the
elapsed-time display. Direct absolute seek, Stop, Shuffle, Repeat and Favorite remain outside
the observed system delivery coverage on this Simulator. The earlier native transport suite
covers the callback and Core behavior for those commands. The Previous restart assertion
establishes delivery of Previous and a rewind outcome.

## Playback startup race

Two scene attempts reached the recovery mix and authenticated the fixture stream, but BASS
failed before reading the audio. The same fixture and XCTest runner reproduced the failure
with the previously accepted app bundle. An isolated native trace showed the playback
backend receiving BASS error 14 (already initialized) with device `UINT_MAX` (unavailable),
then error 8 (uninitialized) from its file open. A separate waveform backend finished
initialization and obtained device 1 approximately 300 ms later. One additional trace attempt
ended in an XCTest channel disconnect before launching a case.

The production change for this follow-up is in
`platforms/apple/src/appleMain/kotlin/app/naviamp/apple/playback/AppleBassPlayback.kt`.
Playback and waveform adapters share a process-local Foundation lock around AVAudioSession
configuration and the synchronous BASS device initialization call. An already-initialized
result requires a usable BASS device. The native lock protects the Apple SDK and BASS C ABI
calls. Core still owns when playback and waveform work run, recovery policy, and queue state.
[BASS initialization contract](https://www.un4seen.com/doc/bass/BASS_Init.html).

The fixture and Swift changes are test code. The fixture supplies two distinct, streamable
tracks and the scene exercises their transitions through XCTest. The setup waits for exact
keyboard value publication and checks the Home card's position above the mini player. This
avoids timing failures from asynchronous text publication and touch hit-point lookup for a
static label in a remote-driven test. Earlier command callback and recovery coverage
remains in [the native transport verification](590-tvos-remote-transport.md).

## Local evidence

The current run uses the verified Compose manifest
`970fd6056b5169f65d6477ed365556f0a0dc02791b3d39e2c6f102ab4912d746` and the tvOS 27.2 Simulator.
The full tvOS tooling suite has 78 passing tests, including the new fixture check. The shared
Apple BASS source compiles for iOS device, iOS Simulator and tvOS device. The scene build
passes the native deployment audit.

Ignored evidence is under `build/590-system-ui-1118`. The passing `scene-previous-paused`
result includes one XCTest with zero failures (262.952 seconds), a system accessibility tree,
the unified MediaPlayer log, authenticated fixture telemetry, and a cleanup receipt. The initial failures are retained in
`scene-first`, `scene-diagnostic`, `scene-bass-probe`, `prior-app-comparison`, and
`initialized-device-replay`. Each owned device was shut down and deleted.

The passing receipt matches all 25 watched source inputs. The resource audit independently
confirmed deletion of all nine Simulators created for this follow-up, fixture shutdown, and
release of the build lease. The separately booted iOS device named Naviamp Issue 43 belongs to
other work. Source and results remain local.
