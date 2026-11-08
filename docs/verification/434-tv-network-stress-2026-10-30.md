# Android TV network recovery and playback stress — October 30, 2026

Refs [#434](https://github.com/goosepod/naviamp/issues/434), #386 and #280. The owner requested
the remaining TV recovery tests and selected a **30-minute** playback stress run. HDMI/CEC
remains explicitly skipped. This continuation tests the unchanged production-signed build 60
from accepted product source `33e0eb30da95dfc3fff329f19cccc8e7819a227a`; APK SHA-256
`C69F95A02A13A3BBDF1681BFADA9E349B8EAEC335C996BB43BDCBE046006C631`.
Its production behavior is unchanged by the subsequent verification documents and test tooling.
The earlier [signed-upgrade/focus acceptance](386-signed-tv-acceptance-2026-10-30.md) remains
separate evidence.

## Isolation and fixture

Only the selected onn 4K Pro / Android 14 is operated. A fresh stopped-profile backup of the
existing `.v2test` app is retained privately. The signed production package is absent for user 0
at the start; it is installed with a new synthetic-only profile. No owner credentials, library or
queue are copied into it. The same unchanged native instrumentation executes in the separate
test-host UID. Raw profiles, screenshots, Wi-Fi identifiers and logs remain ignored locally in
`build/tv-recovery-434`; they are not committed or uploaded.

The fixture binds explicitly to the selected host's private LAN address. The TV reaches it
directly: there is no ADB reverse tunnel, forwarding proxy or firewall-rule change. It serves
silent, ten-minute, 16 kHz mono WAV tracks at 256 kbps, two albums/four tracks, with finite byte
ranges and a two-second initial burst. The selected first mix supplies a two-track queue.
Silent PCM still exercises the native decoder and AudioFlinger output; it is not listening evidence.

Test tooling in [#437](https://github.com/goosepod/naviamp/pull/437) keeps loopback as the default,
allows an explicit private IPv4 bind, adds silent PCM and a bounded seek/range audit, and can
fail a selected far-byte request after a seek begins. Eight real HTTP fixture tests pass,
including byte bounds, fault/recovery, bind restrictions and exclusion of authentication fields.
No app production source or setting changes are included in that tooling.

The tooling merges into main as `9b186c2fe3b1a16164138e6b3b74aec88d75e868` after all nine
protected checks pass in [run 41169254628](https://github.com/goosepod/naviamp/actions/runs/41169254628),
including native Desktop, Android API 30/35 and iOS compilation/common/native TLS/audio,
production app and Keychain checks. This does not close the product bug in #434.

For reproduction, run the fixture from that accepted tooling with `--bind-address` set explicitly
to the selected test host's private IPv4 address, plus `--silent --track-seconds 600 --tracks 4
--albums 2 --burst-seconds 2`. Connect only a disposable TV profile to that LAN source. The
existing `android-release-acceptance-probe.py` targets the explicitly selected production package
from the separate test host. Use native `seekNearEnd` with `remainingMs=60000` for the valid
baseline and `remainingMs=20000` for the in-flight failure. Arm only the selected fixture track
through `/_test/fail-range?minimum=18400000&id=fixture-track`; audit the triggering request before
restoring the fixture with `/_test/on`. Do not disable Wi-Fi without a separately verified
device-local restore path. Raw evidence stays private.

## Recovery results

The online baseline passes: native PLAYING reaches 540.058 seconds, followed by PAUSED at
540.600/540.800 seconds. The request audit shows raw WAV byte ranges, including
`bytes=17170432-17301503`, with no provider time offset. This establishes a successful seek and
valid position before failure.

| Scenario | Playback/queue recovery | Position retention |
| --- | --- | --- |
| Existing stream resumed while the synthetic server rejects requests | Explicit D-pad Play recovers; two tracks/current index zero retained | **Fails**: resumes from zero after a valid nine-minute position |
| In-flight native seek to 9:40; its selected far-byte request triggers the fault | Explicit D-pad Play recovers; queue retained | **Fails**: resumes from zero, retaining neither the last valid nor attempted position |
| Actual TV Wi-Fi disable/reassociation | Same physical TV rejoins the same saved network; explicit Play recovers; queue retained | **Fails**: resumes from zero |

For ordinary interruption, native state becomes STOPPED with unknown progress within five
seconds and remains stopped during the thirty-second source outage. Explicit recovery is tested
separately from position retention: the queue/playing assertions pass and the position assertion
fails. The app is paused after each result is recorded. A failed seek never established a valid
9:40 position; retaining the prior valid position would also avoid the observed restart from zero.

The Wi-Fi restore mechanism is validated with a harmless detached timer across an ADB disconnect
before use. Device-local scripts then disable Wi-Fi, capture disabled state and native playback,
and enable it about 21 seconds later. A separate 45-second restore guard also completes. The same
physical hardware identity is verified on reconnection. At the immediate pre-cut sample playback
is BUFFERING after the successful seek; it is not described as steady PLAYING at that instant.
Disabled and restored network states are recorded locally. No saved network credentials change.

These failures strengthen #434 beyond the earlier ambiguous failed-seek observation. Root cause
and a product fix remain outstanding. The adapter's fake-engine Error tests do not establish the
actual native stop/cleanup callback sequence observed by this physical path.

## Probe correction

One initial attempt disables the server before issuing the seek. The stream fails so quickly
that it has already left Android's active-session list when the probe attempts transport control;
that seek is never sent. This failed driver attempt is retained and excluded from failed-seek
acceptance. The corrected one-shot fault is armed while the source is healthy and records the
specific subsequent range request that triggers it. No assertion is weakened to produce a pass.

At the end of the stress run, direct Activity launch does not dismiss the TV's active Dream.
The initial final-control probe times out waiting for the app's progress control, before sending
Pause. The corrected driver uses Home, waits for the launcher transition, then launches and awaits
the actual app UI. That explicit pause check passes. The failed first driver attempt is retained;
it is not counted as a passing final-control scenario or evidence of a playback interruption.

## Thirty-minute playback stress

The timed playback run **passes**, lasting 1801.08 seconds with 361 native playback samples
and 31 minute-level process/audio/memory samples. Repeat-all keeps the synthetic queue at two
items. Three natural transitions are observed at 490, 1090 and 1690 seconds, with the same app
process throughout. No sustained stall, changed queue count, process restart or non-advancing
audio-frame sample is detected.

One selected 48 kHz AudioFlinger track remains active in every minute sample; its server frame
counter advances from 6,773,760 to 93,172,800, with zero sampled track underruns. Sampled PSS
ranges from 94,782 to 129,877 KiB and ends at 109,397 KiB. These are observations, not a complete
CPU/GPU performance budget or a long-term memory-leak claim.

The OS screen saver is foreground around minutes eight and nine while audio continues. At
minute ten the native Home action brings the launcher forward without stopping playback. A
separate readiness probe restores the actual player UI around 19:55; the twenty-minute checkpoint
then verifies moving visible progress. Playback continues through the final ten minutes, including
the screen saver appearing again at the end. Foreground state is recorded instead of presenting
all thirty minutes as continuously visible app UI.

The monitor samples the selected native MediaSession every five seconds and records process,
AudioFlinger and PSS observations each minute. Android may keep a fixed base position while
PLAYING; progress is extrapolated using its published speed/update time and device elapsed time,
and independently checked against native audio-frame delivery and visible UI movement. A fixed
base position alone is not classified as a stall. Sustained non-playing/non-moving observations
over fifteen seconds are flagged; no animation or playback cadence is changed.

## Final pause and cold restoration

After dismissing Dream, explicit native Pause succeeds at 282.700 seconds with queue count two;
the UI reports range 0.47116676 over 600 seconds (displayed 4:43) and focuses Play. A controlled
force-stop and cold launch restore the same track, both queue items and paused Play focus.
However, the restored range is 0.46716103 (about 280.297 seconds, displayed 4:40): a roughly
2.403-second rollback. The strict 0.001-range / 0.6-second position comparison **fails** and is
retained without loosening its tolerance. This separate freshness/tolerance investigation is
[#438](https://github.com/goosepod/naviamp/issues/438); it is not the restart-from-zero error in
#434, and it does not invalidate the completed thirty-minute playback observation.

## Cleanup

The synthetic production app and test instrumentation are removed only for user 0; the existing
`.v2test` app remains installed and returns to its original stopped state. All 37 database tables
and every preference file match the fresh before-test copy; both database copies pass
`quick_check`. The owner queue, saved position, sources and credentials are preserved. No profile
backup is restored over the app.

Only capture files created by these native probes and the verified owned device-script directory
are removed. Both Wi-Fi restore scripts have completed; the TV remains enabled/connected to the
same saved network and returns to its launcher. No ADB tunnel was created. The owned LAN fixture
is stopped after its PID, exact start time and command are checked; no listener remains on port
18080. Private profiles and evidence remain local.

## Remaining scope

Position-preserving recovery remains open in #434, with the smaller paused-restoration freshness
observation tracked separately in #438. HDMI/CEC is skipped by owner instruction.
A thirty-minute silent synthetic run cannot establish overnight endurance, subjective HDMI audio
quality, spoken-reader acceptance, every real server/codec or arbitrary router outage behavior.
No release tag, store submission or publication is performed.
