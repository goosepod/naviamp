# Interruption and audio route recovery (#590)

This checkpoint stays local on `codex/590-apple-system-media`. The existing real provider/TLS/range
suite gains a shared audio-session recovery contract, driven through real Core commands, the Apple
notification adapter, AVAudioSession and the public tvOS BASS backend. No workflow is dispatched,
artifact uploaded, source pushed or release claim changed.

## Shared ownership

`NaviampExternalPlaybackLifecycleCoordinator` remains the product owner. It now remembers the
interrupted media ID/queue position, requests one pause while its acknowledgment is pending, and
defers an allowed resume until a Paused snapshot arrives. Its surface-session owner feeds snapshots
through `onSnapshot`. Observed idle/loading, item replacement, queue-position change or capability
loss clears eligibility. A new begin revokes a preceding deferred end. Route disconnection and
explicit native transport intent cancel pending resume. Repeated ends cannot replay the track.

These changes address asynchronous engine state publication: a native callback can return before
Core has acknowledged its pause. Repeated begin callbacks must not schedule duplicate pauses,
and an early end must not be discarded merely because the old Playing snapshot is still visible.
An unrelated replacement item must not inherit the interrupted item's resume permission.

Common regressions exercise delayed acknowledgments, duplicate begin/end, another begin before
acknowledgment, denied resumption, paused playback, route loss, explicit intent, capability/queue/item
changes, terminal states and surface-session delivery. Existing command validation, publication,
artwork cancellation, focus and closed-lifetime behavior retain their common owners. No setting,
persistence schema, user-facing string or animation behavior changes.

`NaviampAudioRecoveryContract` owns the full integration scenario. It composes the real Core
streaming services, external playback bridge and surface session with a local 90-second PCM fixture.
It checks the actual native cursor, frozen paused position, advancing resumed position, retained
track/queue and persisted queue identity. Output is muted; these checks do not establish audibility.

The eight required completion phases are:

- Unrelated/unknown/malformed notifications leave playing audio advancing.
- Duplicate begin/end with permitted resume retains position after real session deactivation.
- Denied or missing resume options keep playback paused; a late allowed end cannot undo denial.
- An already paused track stays paused through an interruption.
- Output disconnection pauses playback; a newly available route does not autoplay. Explicit Play recovers.
- Disconnection during an interruption cancels automatic resume. Explicit Play recovers.
- Stop during an interruption prevents a late end from restarting the source.
- Closing the surface session removes native observers; later notifications cannot resume playback.

## Native boundary and runner

`TvosAudioRecoveryIntegrationTest` provides only Foundation private-file IO/main-run-loop delivery,
AVAudioSession deactivation/notification posting, the real ApplePlaybackSurface and native BASS
adapter/cursor. A delegating backend records the handle without replacing any BASS operation.
No new platform production source is changed. The Apple native adapter test adds missing/denied
options, malformed/unknown events, unrelated route reasons and route notifications after close.

The existing opt-in `naviamp.tvos.transportTests=true` graph now includes that native test and the
two ApplePlaybackSurface adapter tests. Its strict inventory is eight cases: four original direct
BASS cases, the existing mTLS contract, the new audio contract and two Apple surface cases. Normal
direct-BASS inventory remains four when the flag is absent. iOS native-runner defaults are unchanged.

`run-tvos-transport-tests.py` still verifies the reviewed Compose bundle and source snapshot, checks
the complete graph, compiles TV device/common targets and the simulator binary, verifies public BASS
linkage, and starts a fresh authenticated fixture before allocating its simulator. Native execution
must have exactly eight passing cases, no failures/errors/skips, all three TLS recovery markers and
all eight audio recovery markers. Old five-case evidence cannot satisfy this checkpoint. Failed
attempts remain separate evidence, with fixture stop and independent simulator shutdown/deletion.
The local hosted workflow drafts inherit the expanded qualification gate. They remain unpushed.

Reproduction uses a fresh output directory and the already reviewed local bundle:

```sh
export JAVA_HOME=/absolute/JDK21/Contents/Home
export ANDROID_HOME=/absolute/Android/sdk
export GRADLE_USER_HOME=/absolute/dedicated/app-gradle
export KONAN_DATA_DIR=/absolute/compatible/app-native
python3 scripts/run-tvos-transport-tests.py --artifacts /absolute/verified/compose-artifacts \
  --expected-manifest-sha256 970fd6056b5169f65d6477ed365556f0a0dc02791b3d39e2c6f102ab4912d746 \
  --runtime com.apple.CoreSimulator.SimRuntime.tvOS-27-2 --build-timeout 7200 \
  --output build/tvos-audio-recovery-new
```

## Results and limits

Ignored local evidence is in `build/590-audio-recovery-1117`. `common-final.log` records 649
presentation and 16 testkit JVM cases, zero failures/errors/skips, plus Android and iOS Simulator
compilation of the common owner/contract before native test wiring. `contract-final.log` repeats
the shared checks after changing initial test playback to select a queue item through Core. The full 72-case
TV tooling checks pass, including rejection of missing audio phases and obsolete native inventory.
`contract-preflight.log` then passes the complete scenario through real Core with a virtual clock
engine and fake native surface, catching fixture setup/persistence errors without a native link.
The final shared total is 649 presentation plus 17 testkit cases. That preflight is not OS or BASS
proof; the native case must independently pass its real notification/cursor checks.
The final `tvos-v4/summary.json` passes on tvOS Simulator 27.2: exactly eight native cases, zero
failures/errors/skips, all eight audio recovery markers and all three TLS recovery markers. The
native log records successful AVAudioSession deactivation before allowed resume. Graph validation
takes 27.513 seconds, the full simulator/device build 574.722 seconds and native execution 37.992
seconds. The public BASS executable remains mixer-free. Native inventory, JUnit/HTML, source hashes,
fixture telemetry and complete logs remain in that evidence directory.

Fixture stop, simulator shutdown and deletion return zero with no cleanup errors. The final device
is `C47A2FD6-251E-4048-882F-6AF6D5AB2A30`. `final-resource-audit.json` independently verifies all
thirteen owned simulators from delivery/preparation/transport/recovery checkpoints are absent,
all three fixtures from this checkpoint have stopped and no checkpoint runner/fixture process
remains. Unrelated simulators/processes are untouched. `checkpoint-summary.json` verifies that all
watched compiled inputs match the working tree and records the 666 shared, eight native and 72
tooling passes. Workflow lint and `git diff --check` pass. No push or hosted execution occurs.

The first native build (`tvos`) fails on two missing backend-type imports in the test adapter,
before allocating a simulator. The next (`tvos-v2`) builds successfully and inventories all eight
cases, then rejects the audio contract's initial setup: a displayed initial queue does not create
an engine playback lease. The fixture now selects the next item through Core's production selection
command and requires nonempty shared session-persistence observations. Other native cases pass;
this attempt remains failed evidence, and fixture/shutdown/delete return zero with no cleanup errors.

`tvos-v3` then reaches native playback but rejects setup-era persistence observations for the preceding
queue selection. Recovery observations now begin after the selected lease is active, followed by a
real pause/resume to require a nonempty persisted baseline for the selected item. The invariant still
rejects any subsequent queue/index change. This failed run also cleans up its fixture and simulator.

These tests inject the existing `AVAudioSessionInterruptionNotification` and route-change
notification payloads through NSNotificationCenter, and explicitly deactivate the actual audio
session between pause and allowed resume. They do not create a physical HDMI/Bluetooth/AirPlay
route change, Siri/another-app preemption, app suspension, or a media-services reset. Hardware output,
route format/device renegotiation, accessibility and minimum-runtime acceptance remain open.

The installed TV SDK is 27.2. Its headers and Apple's [interruption documentation](https://developer.apple.com/documentation/avfaudio/handling-audio-interruptions)
introduce distinct inactive/resumption-context notifications in OS 27 and deprecate the existing
begin/end notifications. This checkpoint tests the existing compatibility path; migration to those
new lifecycle payloads and genuine system-generated interruption coverage remain explicit follow-up.
Apple also documents [route-change handling](https://developer.apple.com/documentation/avfaudio/responding-to-audio-route-changes).
No fabricated hardware or modern-payload result is presented as acceptance.
