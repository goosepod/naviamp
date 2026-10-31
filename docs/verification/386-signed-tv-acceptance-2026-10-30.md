# Signed physical-TV acceptance continuation — October 30, 2026

Refs #386 and #280. The owner resumed TV testing after the Windows accessibility work.
The signed candidate is built from accepted main `33e0eb30da95dfc3fff329f19cccc8e7819a227a`,
tree `2c7ecdac985244619f30c8ab1618188eea55d4f5`, by branch build
[41165530811](https://github.com/goosepod/naviamp/actions/runs/41165530811).
The workflow input has no release tag; this is artifact preparation, not publication.

## Device and data isolation

The explicitly selected onn 4K Pro runs Android 14. Its physical display is 3840×2160,
with the existing 1920×1080 override and density 320 preserved. Initially the launcher is
foreground, the existing `.v2test` package is stopped, and the production package and test
instrumentation are absent. No display, Wi-Fi, HDMI, audio-volume or accessibility settings
are changed for preparation.

The stopped `.v2test` profile is privately backed up before testing, including its existing
credentials and queue. Raw backups and captures remain in the ignored local
`build/tv-final-acceptance` directory and must not be uploaded. The signed production package
uses a separate synthetic-only profile; no owner account or queue is copied into it.
The unchanged native acceptance harness has the same source hashes as accepted main and
executes in the separate `.v2test` UID. It rejects same-UID audio-focus competition.

## Results

- The public v2.9.0 APK (build 57) installs for TV user 0 and connects through the normal TV form
  to the loopback fixture. Selecting its first mix starts a two-track WAV queue. Native pause
  and the UI establish the first track paused around 37 seconds before upgrade.
- The current APK is `Naviamp-v2.10.0-android.apk`, build 60, SHA-256
  `C69F95A02A13A3BBDF1681BFADA9E349B8EAEC335C996BB43BDCBE046006C631`.
  `apksigner verify` passes; its certificate SHA-256 is
  `84ce75e58fe0a75a045bbf830f16c547b92368c83fd118ca87b874d029623c43`, identical to
  the previous public APK. It exposes the Leanback launcher and all four expected Android ABIs.
- In-place signed upgrade succeeds without clearing data. The saved synthetic connection remains
  usable, and the first track restores paused at 0:37. D-pad Up focuses Play. The queue shows
  the same first and second fixture tracks in order. Explicit Play produces native PLAYING with
  queue count two/index zero and visibly advancing progress.
- Real Android audio focus is requested by the separate `.v2test` UID. Both requests are granted.
  Transient loss pauses the signed app; abandoning it resumes the same queue/current item.
  Permanent loss pauses it, and it remains paused after focus is abandoned and a further
  three-second observation. No same-UID approximation is counted.
- Disabling only the loopback server closes fixture streams and rejects new requests. A native
  near-end seek plus Play fails while offline; requests increase and the signed app reaches
  STOPPED with the two-item queue/current item retained. Restoring the server and immediately
  activating the focused Play button with D-pad Up/Center recovers playback with moving progress.
  **Recovery restarts at the beginning** (native position 0.079 seconds), rather than preserving
  a previous or attempted position. Explicit Pause ends this scenario at 2.027 seconds. This is
  a limited manual-recovery pass, not position-preserving recovery; follow-up is
  [#434](https://github.com/goosepod/naviamp/issues/434). The failed seek never established a
  successful nine-minute position, and the fixture/provider offset contract remains to be checked.
- Real remote navigation reaches About, which displays v2.10.0/build 60 and the Aurral-first
  changelog. All 32 entries receive D-pad focus in order, including the accessibility fix and
  the final Known issues entry. The complete traversal passes in 6.635 seconds.

## Probe failures retained

The first upgrade probe incorrectly expects an active native MediaSession before the cold paused
app starts playback. The UI has correctly restored the track at 0:37, but no playback service is
running yet. The corrected remote/UI check passes; the original assertion remains a failed probe.

The first recovery command incorrectly uses the native active-session list after failure has made
the session inactive. Another recovery probe waits too long between revealing and clicking Play,
so auto-hide removes that control before activation. Its log shows no click was performed. The
passing recovery uses immediate D-pad Up/Center and verifies actual playback afterward. An About
probe similarly waits for an off-screen sidebar entry without scrolling; seven real D-pad Down
events reach it successfully. These driver failures are not product regressions or passing whole
scenarios. The early capture named `signed-first-launch` belongs to the previous public APK;
current-candidate evidence uses `current-*` names after the recorded signed upgrade.

## Cleanup and owner-profile preservation

The existing test app is returned to its original stopped state. Read-only comparisons of stopped
profile copies show both databases pass `quick_check`, every preference file is byte-identical,
and the owner queue and saved position are unchanged. No rows are added or removed. Only waveform
access times, the playback-state update timestamp, and normal source login renewal fields
(`last_connected_at_epoch_millis`, token and salt) differ. Source identities/configuration and
credentials outside those normal renewal fields are preserved. No backup is restored over the
profile.

Only the newly created synthetic production app and instrumentation are removed for user 0;
the original `.v2test` installation remains. The TV returns to its launcher. The owned ADB reverse
mapping is removed, and the loopback fixture is stopped after its PID, start time and command are
verified. No listener remains on port 18080. Private backups, captures and logs remain local.

## Cross-platform source verification

Main PR [#432](https://github.com/goosepod/naviamp/pull/432) and release backport
[#433](https://github.com/goosepod/naviamp/pull/433) merge with all nine protected checks passing.
The release retry retains an initial missing-client-identity TLS timeout as failed evidence;
one targeted iOS retry passes compilation, common/native TLS/audio, production app and Keychain
checks without changing assertions. Main `33e0eb30d` and release `7ba4cc64c` share the candidate
tree recorded above. The Windows accessibility result is documented separately in the
[final Windows report](386-windows-final-acceptance-2026-10-30.md).

## Scope limits

The earlier [physical TV report](386-physical-tv-functional-2026-10-29.md) retains the live
library, search, natural/background queue-end and paused-restoration results for its debug
candidate. They are not implicitly repeated against this signed candidate. Simulated server
transport loss must not be presented as real Wi-Fi loss/reassociation. HDMI/CEC, subjective
listening, spoken screen-reader acceptance and overnight endurance require distinct evidence.
The owner explicitly skips physical HDMI/CEC testing for this continuation. No actual Wi-Fi
loss/reassociation or endurance pass is claimed. No production source file changes.
