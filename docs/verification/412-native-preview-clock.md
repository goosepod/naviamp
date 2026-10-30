# Native preview acceptance clock synchronization

Tracking: #412; release consolidation #408/#409/#410.

The first release macOS job timed out waiting for initial main-stream progress. Its one targeted
retry reached the preview but failed the sampled-position comparison: `0.991875 -> 2.2`.
The corresponding main matrix and local native reproduction passed. Both hosted failures remain
recorded; no further unchanged-source retries are used to obtain a passing result.

Inspection began in common `CoreBassPlaybackEngine`, `BassPlaybackExecutionCoordinator`,
`BassAudioBackend.bassPlaybackSnapshot`, and the common polling planner. Pause invokes the native
effect and publishes Paused; the progress snapshot is updated by the independently scheduled
poller. Playing snapshots use the audible clock, while paused snapshots use source position.
The native test's fixed 1.1-second sleep does not establish that the paused callback has run.
Comparing an older playing snapshot to a later paused snapshot therefore does not establish that
the native paused stream moved during preview.

The test now records the source actually queried by Core and the first native playback handle.
After pause it verifies native Paused, reads native source position, and waits up to ten seconds
for the sampled position to agree within the existing 0.15-second tolerance. It retains the
original sampled-position comparison across preview and additionally verifies that the real
native position and native Paused state remain unchanged. Preview completion, shared Paused
state, resumed moving progress and callback-release assertions all remain. Existing initial,
preview and resume deadlines, output selection and real-output requirement are unchanged.

The first two local recording-adapter iterations did not observe the actual playback-decode
creation path and failed on missing test handles before the assertions. They are harness failures,
not product regressions or passing acceptance. Recording the position-query boundary removes
that creation-path assumption. The corrected targeted run passes with no skips: native position
`2.52 -> 2.52`, sampled position `2.52 -> 2.52`, then resumed progress `3.5516875`.

Only the desktop native test changes. Its concrete boundary is real BASS/JNI stream handles,
clock queries and native channel state, so it belongs in the platform adapter test suite.
No production implementation, playback cadence, setting, string resource, schema, timeout or
acceptance tolerance is relaxed. The full local desktop native suite passes all 65 reported cases with zero failures/errors and
two Linux-only skips. Hosted integration remains required; the local passes alone do not clear
the release gate.
