# Remote transport and recovery coverage (#590)

This follow-up stays local on `codex/590-apple-system-media`. No push, hosted run, artifact
promotion or release is performed. The approved Compose manifest remains
`970fd6056b5169f65d6477ed365556f0a0dc02791b3d39e2c6f102ab4912d746`.
The [recorded Simulator acceptance scope](../tvos-implementation-plan.md#decision-and-current-evidence)
and [OS 27 callback limitation](590-os27-audio-lifecycle.md) remain unchanged.

## Common contract

`core:testkit` owns `verifyNaviampRemoteTransport`. It uses real Core, provider resolution,
streaming playback services, queue/session persistence, the external-playback surface session and
an injected output engine. Commands enter the callback registered by the common session; the
scenario never calls bridge transport methods directly. Thin hosts supply only file, native
session/notification and output-clock effects.

The mandatory phases are:

- Play, pause, and both toggle directions, with moving/stable output clocks.
- Next and previous selection, persisted queue identity/index and rejection at queue boundaries.
- Absolute seek while paused and playing, publication of the new cursor, and rejection of NaN,
  infinities, negative, overflowing and beyond-duration positions without moving paused output.
- Shuffle on/off and all three repeat modes, including idempotent set requests and queue restoration.
- Rejection of Favorite for a provider that does not support it.
- Stop followed by a late resume recommendation and explicit restart.
- A deliberately failed stream resolution followed by successful Play through the same callback.
- An explicit Pause during interruption prevents late automatic resume; Toggle remains usable.
- Every retained command callback rejects after close, including controls that change queue modes.

The JVM preflight runs this complete scenario against both legacy and OS 27 lifecycle facts.
Shared surface regressions additionally cover all six explicit transport intents during an
interruption, capability validation and queued-intent invalidation. No product behavior, setting,
string resource, schema, playback cadence or animation policy is added by this coverage.

## Native boundary and evidence limits

Two native production files change, both strictly at the Apple SDK boundary:

| File | Native boundary |
| --- | --- |
| `platforms/apple-media/src/appleMain/kotlin/app/naviamp/apple/media/ApplePlaybackSurface.kt` | Registers MediaPlayer handlers, retains/removes native target tokens and translates SDK payload facts into existing common commands; registration and payload reads become injectable effects. |
| `platforms/apple-media/src/appleMain/kotlin/app/naviamp/apple/media/AppleRemoteCommandPayloads.kt` | Reads the position, shuffle and repeat properties of concrete SDK event classes; production defaults always use these typed reads. |

MediaPlayer types, Apple block ABI, SDK event properties and native resource handles prevent these
operations from compiling in common Kotlin. No validation, scheduling, recovery or playback policy
moves to the adapter. Playback behavior is unchanged.

Native component tests capture the handler passed to the real public command registration API.
They exercise all ten command mappings, missing payloads, rejected shared intents, capability flags,
close, a replacement registration and cleanup after partial native registration failure. Apple
rejects external construction of `MPRemoteCommandEvent` at runtime, despite Kotlin exposing an
inherited initializer. The first native run records that `NSGenericException` and successful owned
cleanup in `acceptance`; it remains failed evidence, not an accepted run. The subsequent
`acceptance-public` run passes the native callback test but exposes an incomplete test-label map
in the shared scenario. The fixture now provides the required Favorite label even when unsupported,
and its JVM surface preflight checks label completeness. No product fallback or hardcoded UI label
is added to work around the fixture error.

The corrected test uses explicit fixture payload facts for seek/shuffle/repeat, and delegates missing
facts to the production reader. It never constructs or counterfeits an SDK event. The actual getters
are compiled against the SDK; delivery of genuine typed system events is not established by this
component test. No private MediaRemote injection, KVC or private selectors are used. Existing native
tests still exercise actual Now Playing publication and command flags. Apple documents the
[registration/token lifetime contract](https://developer.apple.com/documentation/mediaplayer/mpremotecommand/addtarget%28handler%3A%29)
and [typed position event](https://developer.apple.com/documentation/mediaplayer/mpchangeplaybackpositioncommandevent).

The TV integration runs the same common scenario through the real Apple surface, Core BASS
engine and an advancing native output cursor. This establishes common dispatch and native playback
outcomes; captured callback tests separately establish event translation. It does not claim that
all ten commands were delivered by a physical remote or the system Control Center. Existing
foreground/background Play/Pause system-input proof remains in
[the system-media checkpoint](590-apple-system-media.md). Physical input feel, audibility,
HDMI/route changes and genuine OS 27 audio-session contexts remain unverified.

## Acceptance and reproduction

The transport gate requires eleven exact native cases, the nine remote phases above, the native
callback marker, all existing TLS/audio phases, zero failures/errors/skips, unchanged watched
inputs and successful owned-resource cleanup. Earlier nine-case evidence cannot qualify it.
The local hosted workflow drafts inherit this expanded gate; the trusted hosted pin stays pending.
The receipt continues to distinguish `os27SimulatorQualified` from `os27ContextQualified`.

```sh
export JAVA_HOME=/absolute/JDK21/Contents/Home
export ANDROID_HOME=/absolute/Android/sdk
export GRADLE_USER_HOME=/absolute/dedicated/app-gradle
export KONAN_DATA_DIR=/absolute/compatible/app-native
python3 scripts/run-tvos-transport-tests.py --artifacts /absolute/verified/compose-artifacts \
  --expected-manifest-sha256 970fd6056b5169f65d6477ed365556f0a0dc02791b3d39e2c6f102ab4912d746 \
  --runtime com.apple.CoreSimulator.SimRuntime.tvOS-27-2 --build-timeout 7200 \
  --output build/tvos-remote-new
```

Use a fresh output directory. Evidence for this follow-up is under `build/590-remote-1117`.
The shared preflight passes 652 presentation and 18 testkit cases (670 total), with zero
failures/errors/skips. The contract executes against both lifecycle protocols. Android and iOS
Simulator common compilation pass; `common-queue-fixed.log` records the 9m21s full shared build.
After correcting the fixture label map, `common-final.log` again passes all 18 testkit cases and
Android/iOS compilation in 1m23s; the 652-case presentation suite's inputs are unchanged.
All 77 TV tooling tests, 19 iOS runner regressions and actionlint v1.7.12 pass. The native gate
rejects missing command phases, missing translation evidence and older native inventories.

`platform-production.diff` isolates the registration/payload-effect changes against the preceding
accepted Simulator snapshot; playback decisions and lifecycle logic are unchanged.
The fresh `acceptance-final/summary.json` passes all eleven exact native cases with zero
failures/errors/skips, all nine remote phases, the native callback marker and every existing
TLS/audio recovery phase. Graph validation takes 49.306 seconds, build/device compilation 741.458
seconds and native execution 45.379 seconds. Fixture stop, simulator shutdown and deletion each
return zero. The receipt records `passed=true`, `scope=tvos-simulator`,
`os27SimulatorQualified=true` and `os27ContextQualified=false`; audibility remains unverified.
All 24 watched inputs remain unchanged during execution.

The first older-iOS check compiles both device targets and verifies all seven new lifecycle symbols
are weak imports, but times out in its initial metadata case after a cold 167-second boot. Its
fixture-free simulator shutdown/deletion succeed. `ios-replay` separately reruns the same immutable
binary with an owned-process stack capture if it stalls, to distinguish environment from adapter
behavior. The original failure is retained rather than counted as compatibility acceptance.
`ios-replay/summary.json` passes all four adapter cases on iOS Simulator 26.5 with zero
failures/errors/skips. It uses the same immutable executable as the failed 120-second attempt,
with a separate 180-second diagnostic budget. Execution takes 120.328 seconds; its metadata case
accounts for 110.711 seconds, callback translation 0.012 seconds, notifications 0.004 seconds and
legacy availability 1.270 seconds. The stack capture locates the delay in the image fixture's
`UIGraphicsImageRenderer` → `UIScreen` application-initialization path in the headless process,
after adapter construction/registration has returned. This is not an adapter callback deadlock.
No production change or private initialization API is used to make the replay pass. Retain the
measured headless fixture startup cost as follow-up; the tested fixture itself remains unchanged.
The real application's initialization/activation latency is not established by this component run.

All seven lifecycle symbols remain weak imports. All 587 production inputs match the iOS snapshot;
the only later common testkit change supplies a complete fixture label map and is not linked by the
Apple-media compatibility executable. The replay's owned simulator is shut down and deleted.

`final-resource-audit.json` independently confirms all 31 owned simulator IDs from this and prior
checkpoints are absent. No Simulator is booted, all owned fixture servers and run processes have
stopped, and the build lease is released. `current-gate-source-audit.json` verifies all 24 current
watched inputs match the passed TV gate. `checkpoint-summary.json` aggregates final results and
review-input identities. Failed attempts remain recorded separately; no source is pushed or CI run.
