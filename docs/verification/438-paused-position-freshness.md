# Paused checkpoint freshness (#438)

## Confirmed common regression

On main production source `62851e1c3` (unchanged by documentation-only main update
`880919147`), two new shared controller regressions fail; the other 40 controller tests pass.
The failures save 280.297 instead of 282.700 seconds, and 31.0 instead of 31.5 seconds.

The sequence is playing progress, native Paused, then a final paused clock sample inside
the session save interval. Paused force-saves the old sample. The newer sample reaches
shared live state but its persistence is throttled. A cold controller therefore restores
the older checkpoint. This reproduces a mechanism consistent with the physical TV evidence;
it does not establish that every native timing condition in that report has been reproduced.

The shared BASS engine publishes Paused directly after successful native pause. Its polling
loops also publish state before progress from a snapshot. The backend uses audible mixer
position while playing and decoded position while paused, so those samples can differ.

## Change

`NaviampCorePlaybackController` forces persistence when a known paused position changes.
Playing progress retains its existing throttle. Unchanged paused samples and unknown clock
samples do not force writes. No native production files, UI strings, settings, migrations,
polling frequency or animation policy change.

Shared regressions cover both callback orders, immediate and delayed fresh-controller restore,
queue/current-track preservation, no automatic start, duplicate and unknown paused samples,
and resuming the playing-save throttle. The existing accepted paused-seek regression remains.

The initial fix passes all 42 controller tests. With the final edge cases, 100 focused JVM tests
pass: 43 controller, 44 playback adapter and 13 session controller methods, with no failures,
errors or skips. Shared presentation Android compilation and the Core-first architecture guard
pass. Both iOS targets and the
complete native matrix require the PR's CI; Windows cannot execute the Apple toolchain.

## Physical acceptance still required

Keep #438 open until signed physical acceptance passes. Do not contact the paused living-room
TV without owner authorization. A phone test can add evidence but cannot establish Dream/TV
behavior. Use an isolated test profile and retain the original comparison tolerance.

For foreground playback and playback after dismissing Dream, record callback ordering, native
paused position, shared progress and the persisted checkpoint. Terminate immediately after the
final paused sample and after a controlled delay; cold launch must preserve the same track,
queue, paused intent and checkpoint. Also record immediate termination after the Pause callback
before a final sample: this change does not synchronously acquire a native position on Pause.
Do not loosen tolerance or infer physical acceptance from synthetic callback tests.

Local logs are under ignored `build/verification/438/`; no private endpoint or owner data is
needed for these shared tests.
