# Native preview clock diagnosis (#538)

## Preserved failure

Verify run `42424687852`, attempt 1, job `128563750180`, failed on source
`862f268f798f6920f8708fd351b194af080f3485`. Artifact `13594963826` preserves the
original macOS report. Before the preview starts, the initial ten-second playback check reports
Playing, audible position 0.0, decoded/source position 1.5 seconds and native active state 1.
Neither the native clock, its position mapping nor the polling coroutine has been identified as
the cause. Decoded read-ahead is not evidence that output advanced.

The one permitted unchanged-source control (job `128568709966`, artifact `13596366488`)
passed. It does not resolve the original cause. Do not keep rerunning a failed job until green.

## New observations

`DesktopPreviewPlaybackIntegrationTest` keeps its original playback, pause, preview EOF, retained
native stream/position, resume and callback-release assertions and deadlines. A bounded history of
64 existing decoded/audible reads and state/progress callbacks records monotonic elapsed time and
thread name. It introduces no independent polling, extra native reads during acceptance, sleeps,
device selection or recovery.

After a failure, before engine cleanup, the test reports this history, handles, versions, enumerated
devices, fresh JNI decoded/audible readings, and a bounded thread snapshot. Read-only getters from
the same BASS library report the current thread's device and output-started state, the playback
channel's device, buffered bytes and active state, with immediately sampled per-call error codes.
`BASS_ChannelGetData(NULL, BASS_DATA_AVAILABLE)` queries bytes without draining or decoding audio.
The current thread's device is explicitly separate from the channel's device: diagnostics never
call `BASS_SetDevice`. An unavailable getter is recorded as unavailable, not treated as healthy.
Diagnostic collection cannot replace the original thrown failure.

Setting `NAVIAMP_NATIVE_CLOCK_DIAGNOSTICS=true` also captures a positive snapshot after the resume
assertion, before stop/release. macOS CI enables this so getter availability is checked on the actual
host. The native APIs are described in the official
[output state](https://www.un4seen.com/doc/bass/BASS_IsStarted.html),
[channel device](https://www.un4seen.com/doc/bass/BASS_ChannelGetDevice.html), and
[buffer query](https://www.un4seen.com/doc/bass/BASS_ChannelGetData.html) documentation.

## Bounded hosted experiment

The opt-in PR label `verify:macos-native-clock-repeat` requires ten independent Gradle executions of
both native preview integration classes after the ordinary complete macOS suite passes. Existing
`forkEvery=1` gives each class a fresh JVM. Every execution must pass; the first failure terminates
the experiment and fails the job. This is additional acceptance, not retries of a failed execution.
The label must be present in the source event payload (push a substantive update after attaching it).

The artifact preserves the original full-suite XML/HTML and every subsequent execution's log,
XML and HTML under `build/macos-clock-replays`. XML exposes individual names, failures, skips,
timestamps and diagnostic output. Verify actual task execution and distinct timestamps when auditing;
a cached task or missing required case is not a passing sample. The step has a fifteen-minute bound.

If the failure recurs, compare callback timestamps with the last native audible read and the fresh
post-failure reading. A fresh advancing native reading with stale callbacks points toward polling
or publication; continuously sampled zero audible position with fixed decoded read-ahead narrows
the investigation toward the output clock or native position reporting. These are hypotheses,
not automatic classifications. Device flags and thread stacks supply context, not proof of cause.

No Android, Desktop or iOS production source is changed. Only real BASS adapter tests, CI and this
acceptance record change. A successful diagnostic experiment does not close #538 or establish a
playback fix.

## Windows positive control

On the attached real Windows output, the original native preview acceptance passed in 7.042 seconds
with zero ignored cases. With positive diagnostic capture enabled, both required classes passed
again: core discovery preview 8.378 seconds and native retained-stream preview 7.001 seconds,
zero failures/errors/ignored cases. These are local Windows controls, not hosted macOS evidence.

The captured native main position stayed at 2.6 seconds during preview and resumed to 3.006 seconds.
Every direct diagnostic getter was available and returned error 0: current thread device 1,
output-started state 1, playback channel device 1, buffered bytes 99,936 and channel active state 1.
A fresh JNI snapshot reported decoded position 4.69 seconds and audible position 3.1204375 seconds.
This validates useful read-only capture while real output advances; buffered read-ahead remains
distinct from audible progress. The workflow scope regression checks passed 2/2 under WSL after
normalizing the local shell fixture's CRLF checkout to LF; no shell source change is included.

Hosted macOS execution and the complete cross-platform verification remain required for this PR.
