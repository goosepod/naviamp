# OS 27 audio lifecycle migration (#590)

This work remains local on `codex/590-apple-system-media`. No source is pushed, hosted workflow
dispatched, artifact promoted or release published. The approved local Compose inventory stays
`970fd6056b5169f65d6477ed365556f0a0dc02791b3d39e2c6f102ab4912d746`.

## Contract and common ownership

The Apple host selects the new activation-lifecycle notification family on OS 27 and later, and
the existing interruption family on older runtimes. It never subscribes to both interruption
families simultaneously. Route notifications remain shared with both paths. Active, inactive and
resumption facts are delivered to the same common surface session and coordinator.

Common code now models deactivation source (application/system/unknown), optional interruption
reason and resumption recommendation (resume/stay paused/unknown). Core owns the decisions:

- System inactivity pauses an eligible playing item and records its interruption eligibility.
- Application deactivation clears pending resume without manufacturing an interruption.
- Unknown-source or disconnected-output inactivity pauses and clears automatic resume eligibility.
- Session activation alone never starts playback.
- A resume recommendation acts only on an eligible interrupted item. Denied/unknown recommendations,
  explicit native transport intent, route loss, replaced/terminal sources and closed sessions retain
  their existing safeguards.
- A missing or malformed modern payload is ignored. It is not the legacy missing-options case,
  which remains a denied legacy end. A later valid modern recommendation can still be handled.

The shared recovery preflight exercises both notification families against real Core with a virtual
clock. Common surface tests cover source/recommendation policy and activation without autoplay.
No new product setting, user-facing string, schema, provider protocol or animation behavior changes.

These semantics follow Apple's [audio interruption contract](https://developer.apple.com/documentation/avfaudio/handling-audio-interruptions).
Recommendations can arrive independently of the old interrupting-app end signal. Core chooses
whether to play; the Apple BASS effect explicitly activates AVAudioSession before its native play
operation, so session activation no longer depends on a deprecated end notification. No native retry or
resume policy is introduced.

## Public SDK bridge and native placement audit

The installed SDK exposes the OS 27 declarations, but Kotlin/Native's bundled platform AVFAudio
klib does not. The initial direct Kotlin compile fails on those missing declarations; its log is
retained as `build/590-os27-1117/apple-compile.log`.

`NaviampAudioLifecycle.h` uses the public SDK's typed Objective-C context classes and properties,
guards every new symbol with `@available`, and translates native enums into neutral ABI values.
It does not construct system contexts, invoke private selectors, change OS versions, or make a
playback decision. A narrow cinterop definition exposes these functions to Kotlin. New SDK symbols
must remain weak imports, and an older runtime must execute the legacy observer family.

Changed platform production files and their exact boundaries:

| File | Native boundary |
| --- | --- |
| `platforms/apple-media/src/nativeInterop/cinterop/include/NaviampAudioLifecycle.h` | Typed reads of public AVAudioSession notification/context APIs unavailable in the bundled Kotlin platform klib, with native OS availability guards. |
| `platforms/apple-media/src/appleMain/kotlin/app/naviamp/apple/media/AppleAudioSessionLifecyclePayloads.kt` | Calls the Objective-C bridge and translates its ABI values into common facts. |
| `platforms/apple-media/src/appleMain/kotlin/app/naviamp/apple/media/ApplePlaybackSurface.kt` | Registers the runtime-supported NSNotificationCenter observers and releases their tokens. |
| `platforms/apple/src/appleMain/kotlin/app/naviamp/apple/playback/AppleBassPlayback.kt` | Activates the concrete AVAudioSession before calling the BASS play ABI chosen by Core. |

The `.def` and Gradle changes describe cinterop/build metadata. Synthetic payload decoding stays in
Apple test sources; production defaults always use the typed public SDK reader.

## Missing callbacks: Simulator capability investigation

The original gate required genuine application/system context notifications from TV Simulator 27.2.
That was an incorrect Simulator prerequisite. The owner's November 15 acceptance decision in the
[implementation plan](../tvos-implementation-plan.md#decision-and-current-evidence) permits device-only
behavior to remain unverified; it does not require physical Apple TV testing to complete #590.

Apple documents that Simulator does not reproduce most cross-process audio-session interactions,
including interruptions, mixing and hardware route changes in its
[Audio Session Programming Guide](https://developer.apple.com/library/archive/documentation/Audio/Conceptual/AudioSessionProgrammingGuide/OptimizingForDeviceHardware/OptimizingForDeviceHardware.html).
Its [Core Audio overview](https://developer.apple.com/library/archive/documentation/MusicAudio/Conceptual/CoreAudioOverview/CoreAudioEssentials/CoreAudioEssentials.html)
also distinguishes device session behavior from Simulator testing. These are archived documents;
current runtime evidence below independently corroborates this limitation for this environment.
This is not a claim that every present or future Simulator behaves identically.

Ignored investigation evidence is in `build/590-os27-callbacks-1117`:

- Environment: Xcode 27.2 (27B52), TV Simulator 27.2 (24K150), macOS 27.0.1 (26A434).
  SDK/platform/target selection is verified; there is no connected physical device.
- `observer-probe`: retained Objective-C observers on the posting and main queues both receive a
  separate control event. Public activation/deactivation and volume-one PCM playback succeed,
  with an advancing clock, but no actual lifecycle event is observed.
- `swift-probe`: a UIKit scene uses Swift typed observers, named observers and notification async
  sequences. Typed/named notification names match. Successful public activation/deactivation and
  advancing audio still yield no lifecycle event, independent of Kotlin or the cinterop bridge.
- `two-apps`: two separate UIKit apps with nonmixing playback categories play overlapping audio.
  Both report `otherAudioPlaying=false`, despite the other app playing, and neither receives an
  interruption. Their runtime log reports `Unsupported property: SupportsInterruptionEndedAfterActivationError`
  from `AVAudioSessionImpl_Simulator.mm`, and separate simulator audio devices. This supports the
  conclusion that Simulator audio-session arbitration is the missing capability, rather than a
  Naviamp observer-registration defect. Each probe shuts down and deletes its owned simulator.

The earlier failed genuine-context attempts remain in `build/590-os27-1117/tvos` and `replay-system`.
Their two failures are retained as evidence, not relabeled as passes. There is no public simctl
interruption injection mechanism in this toolchain. No private context constructor, selector,
counterfeit SDK context, or fabricated genuine-event marker is used.

## Simulator acceptance and separate genuine-context diagnostic

The default runner now implements the approved Simulator scope. It now requires all eleven exact native
cases with zero failures/errors/skips, all three TLS fault/recovery phases, all nine supported audio
recovery phases, the OS 27 notification availability/observer-disposal marker, and the
[remote transport follow-up](590-tvos-remote-transport.md)'s nine remote phases/native callback marker. It exercises real
Core playback through native BASS, actual AVAudioSession activation/deactivation, advancing cursors,
retained queue/position, and deterministic modern notification facts. Duplicate, missing, denied,
application-source, route-loss, stopped-source and closed-session safeguards remain mandatory.

Apple's modern context objects are system-created and have no public initializer. Deterministic
edge cases use explicitly tagged test payloads; production uses the public typed SDK reader.
Synthetic events validate adapter/common behavior, not system-created-context delivery. Observer
selection/disposal and rejection of legacy events on OS 27 are exercised through NSNotificationCenter.

After the source snapshot remains unchanged, the fixture stops and the owned simulator is deleted,
a successful default receipt records:

```json
{
  "passed": true,
  "scope": "tvos-simulator",
  "os27SimulatorQualified": true,
  "os27ContextQualified": false,
  "systemContextDelivery": "unverified",
  "audibilityVerified": false
}
```

Both local workflow drafts require the Simulator receipt before accepting transport evidence or
uploading a Compose candidate. They do not claim device validation. Regression tests reject any
missing supported phase/observer marker, old inventories, failures or skipped tests. Simulator
evidence is also tested against the separate strict context validator and must fail it.

`--require-system-context` retains the stronger diagnostic for an environment that actually delivers
the documented callbacks. It additionally builds/signs a separate UIKit audio app, gates its launch
with a run-specific token, and requires genuine system deactivation/resumption, application
context delivery and retained-position playback recovery. That diagnostic remains failing on the
installed Simulator and is not the required Simulator CI gate. `os27ContextQualified` can become
true only after its additional evidence and cleanup succeed. The former `--component-only` split
is removed: supported Simulator acceptance is the default, with no opt-out for a required phase.

```sh
export JAVA_HOME=/absolute/JDK21/Contents/Home
export ANDROID_HOME=/absolute/Android/sdk
export GRADLE_USER_HOME=/absolute/dedicated/app-gradle
export KONAN_DATA_DIR=/absolute/compatible/app-native
python3 scripts/run-tvos-transport-tests.py --artifacts /absolute/verified/compose-artifacts \
  --expected-manifest-sha256 970fd6056b5169f65d6477ed365556f0a0dc02791b3d39e2c6f102ab4912d746 \
  --runtime com.apple.CoreSimulator.SimRuntime.tvOS-27-2 --build-timeout 7200 \
  --output build/tvos-os27-new
```

Use a fresh evidence directory, an ARM64 Mac and Xcode with the OS 27 SDK declarations. The runner
verifies the Compose manifest before building an isolated source snapshot, enforces the strict
native inventory and stops fixture/helper work before shutting down and deleting its own device.
It never shuts down unrelated devices. The heavier build is serialized by the local build lease.

## Verification and remaining scope

The fresh default gate passes in `build/590-os27-callbacks-1117/acceptance/summary.json`:
all nine native cases, nine supported audio phases and three TLS recovery phases pass with zero
failures/errors/skips. Graph validation takes 37.439 seconds, native/device build 253.372 seconds,
and native execution 37.333 seconds. The receipt records `scope=tvos-simulator`,
`os27SimulatorQualified=true`, `os27ContextQualified=false` and `systemContextDelivery=unverified`.
Fixture/shutdown/delete each return zero; all 22 watched inputs remain unchanged during execution.

All 77 TV tooling tests, 19 iOS runner regressions and actionlint v1.7.12 pass. The executable
workflow-receipt regression accepts successful Simulator evidence while rejecting failed runs,
component receipts, strict diagnostic receipts and unqualified Simulator receipts.
`prior-production-source-audit.json` verifies 586 production inputs match both earlier tested
snapshots (excluding testkit sources, whose modern-only diagnostic split is documented below).
No production file changes in this follow-up.

`final-resource-audit.json` independently checks all 26 owned simulator IDs from this and earlier
checkpoints: none remain, and no simulator is booted. All owned fixture servers and run processes
are stopped, and the heavy-build lease is released. Investigation cleanup and fresh gate cleanup
both succeed; unrelated devices and processes are untouched. `checkpoint-summary.json` aggregates
these results and hashes the final review inputs without changing earlier failed evidence.

Unchanged production/common evidence from `build/590-os27-1117` remains applicable: 652 presentation
and 17 testkit cases pass, with Android and iOS compilation. Both device targets compile.
`ios-compatibility/summary.json` passes all three native adapter cases on iOS Simulator 26.5;
`modern-symbols.txt` verifies weak imports for both new context classes, both context keys and all
three new notification names. The immutable older test snapshot predates the modern-only diagnostic
split; production source and the exercised legacy path are unchanged. `tvos-components` previously
passed all nine native cases and nine supported audio phases, without claiming genuine context proof.

This gate correction changes test scope and receipts, not production behavior. The native placement
audit above still covers the local migration's production changes. Hosted execution, the real
attestation round trip, trust-pin promotion and distribution validation remain pending. No source is
pushed, CI dispatched, artifact promoted or release published.

Genuine system-created context delivery, physical HDMI/Bluetooth/AirPlay changes, route-format/device
renegotiation, media-services reset recovery, audibility and hardware performance remain unverified.
Under the owner's Simulator acceptance decision, device-only checks are limitations rather than
mandatory gates. Other required Simulator UI, accessibility, security and distribution checks remain
in the implementation plan.
