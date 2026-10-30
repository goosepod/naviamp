# Physical Android TV functional acceptance — October 29, 2026

Refs #386, #332 and #399. Candidate product source: `5df2dcb6` (the Windows candidate
in draft PR #406). This follow-up changes native acceptance tests and their host runner only.
The owner resumed physical TV testing and explicitly authorized a private local backup before
the in-place update. Earlier documents saying physical TV testing is paused are historical.

## Device and installation

The physical onn 4K Pro runs Android 14 and reports the television/Leanback features.
Only its existing `app.naviamp.android.v2test` installation is updated; the normal production
package is absent. The installed original APK and app profile are backed up under the ignored
`build/tv-acceptance-oct29` directory, including a consistent stopped-profile backup before update.
These files contain private credentials/library data and must not be committed or uploaded.

The candidate and original share the signing certificate. The debug APK is installed in place
without clearing data. Saved authentication, the original eleven-track queue, and paused playback
position remain available. The APK SHA-256 is
`693BEB01AD1C41B62108796A34C08BCA559DCAECFB1502ABF47EF3FB6C74014C`.
This is physical debug-candidate acceptance, not a signed/store distribution claim.

## Functional results

- Real D-pad input opens player controls, Settings and Playback. Both Radio batch sliders respond
  horizontally (10 → 20 → 10); vertical input leaves and returns to each slider without changing
  its value. Both original values are restored to 10.
- The final original track plays to its natural end. Autoplay adds ten tracks and starts the first
  addition. A read-only comparison of stopped-profile database copies verifies all eleven original
  remote track IDs remain the exact queue prefix: 11 → 21, current index 11. No whole-profile
  restore or database modification is performed.
- Playback continues with the TV launcher in front. Explicit native MediaSession Pause twice
  remains paused; Play twice remains playing. Returning to the player shows advancing progress.
- Seven native Next commands advance the queue from index 13 through 20, each confirmed by native
  active item ID and a fresh position below ten seconds. Native MediaSession seeking reaches the
  final track's last fifteen seconds. This does not use the deliberately read-only TV waveform.
- With the launcher in front at EOF, Autoplay extends 21 → 31, retains the title multiset including
  duplicate occurrences, and starts index 21. Native session reports PLAYING at 11.806 seconds.
  Returning shows moving progress; explicit Pause reports PAUSED at 17.531 seconds. This corrected
  background acceptance probe passes in 65.554 seconds.
- After that process exits, a cold launch restores the same added track paused at the displayed
  0:18 position. D-pad focus identifies Play rather than Pause. Back exposes the shared navigation
  bar without starting playback. This probe passes in 21.245 seconds.
- Live library artist and album grids populate. D-pad Right and Center select Albums from Artists;
  the resulting album grid contains actual connected-library content. These captures precede a
  failed Search-label assumption in that script, so the whole script is not counted as passing.
- A separate live search submits a known album query through the TV search field and its scoped
  Search button, waits for the exact album result, clears the query, and returns Home. This probe
  passes in 27.537 seconds. Playback remains paused; no library mutations are requested.
- Native AudioFlinger observes an active 44.1 kHz track owned by the selected app UID, with no
  underruns in the selected sample. Server frame count advances from 1,888,362 to 9,877,518 on the
  same native track. This is audio-output delivery evidence, not a subjective HDMI listening test.

The shared TV changelog also receives remote focus for all 30 entries. It is secondary coverage;
functional playback and navigation are the acceptance priority requested by the owner.

## Probe corrections and limitations

The first natural-EOF script fails a native queue-retention assertion even though new audio plays.
Presentation media IDs differ between a current item and its history representation, and history
is presented in reverse order. Comparing those IDs as stable queue identities was invalid.
The corrected native assertion compares title occurrence counts; the original-prefix preservation
claim above independently compares persisted remote track IDs, not titles alone.

A direct queue selection does not mean jumping to the final physical index with the owner's
move-selected-track preference: it moves that track beside the current item. A background probe
using that assumption reaches an ordinary next track and fails its expected extension count.
The passing repeat uses Next and verifies every native index before seeking. A subsequent cold
probe expecting a stale track title is also a driver failure. These failed logs remain local and
are not counted as passing whole scenarios or product regressions.
Library tabs require D-pad activation rather than the generic accessibility click used by the
first browsing probe. The TV search field says “Artists, albums, or tracks”, unlike the generic
search placeholder assumed by another probe. The corrected D-pad captures and separate successful
live-search script establish the narrower results above.

The owner profile's settings and sources are preserved; playback and queue history are exercised.
Autoplay additions remain in the queue. No favorites, playlists, source credentials, global volume,
network/security settings, or server configuration are edited. Raw captures/logs stay local.
The newly added instrumentation package is removed after testing; the updated `.v2test` app is
reopened on Home with its saved paused session. The original APK/profile backups remain local.

This does not close #399: this TV/account/path succeeds, but the earlier physical phone's specific
Radio/similarity timeout still needs its own reproduction. Competing-UID audio-focus interruption,
HDMI/CEC route changes, real Wi-Fi loss/reassociation, overnight endurance, fresh Connect pairing,
and final signed-package acceptance are not established by these checks. Emulator/synthetic fixture
results elsewhere must not be relabeled as physical acceptance of this candidate.

## Harness and reproduction

`AndroidReleaseAcceptanceInstrumentedTest` remains gated by `releaseAcceptance=true`. The runner
now accepts the explicitly selected `.v2test` target, handles Windows UTF-8 input/output, and quotes
the JSON for ADB's second shell parsing layer. Its D-pad operations assert target foreground
ownership. Native MediaSession operations use a scoped shell permission and filter the exact
selected package. Same-UID focus competition is rejected rather than reported as a real external
audio interruption. No platform production file changes.

The new `session` operation supports playing/paused state, exact/minimum queue count, queue index,
title, position bounds, named current/queue snapshots, changed-current and retained-queue checks.
`transport` supports explicit play/pause, next/previous, existing native queue item selection, and
seeking 5–60 seconds before the native duration. Queue selection follows the app's normal policy;
use Next to reach a physical queue end without moving selected tracks. `expectFocus` checks labels
inside the actually focused node; `key` injects only D-pad directions/center. Snapshots record focus.

Build with JDK 21, then install the matching test APK without clearing the owner's app data:

```powershell
.\gradlew.bat -Pkotlin.native.enableKlibsCrossCompilation=false `
  :apps:android:assembleDebug :apps:android:assembleDebugAndroidTest --max-workers=4
python scripts/android-release-acceptance-probe.py --adb PATH_TO_ADB `
  --serial SELECTED_TV_SERIAL --package app.naviamp.android.v2test `
  --steps-file PRIVATE_STEPS_JSON --evidence-dir build/PRIVATE_EVIDENCE --name tv-check
```

The app/test assembly and corrected native instrumented playback run pass. Instrumentation runs
inside the `.v2test` process; completion terminates that process. Cold restoration should therefore
be checked separately and must await actual current UI state instead of assuming a previous title.
The runner performs no installation, clearing, uninstallation or source/account setup itself.
The final instrumentation APK SHA-256 is
`7A28E397B8C152846F20153D84CF2CCD58A02DAEA5F1B52CB43F2D989BC9A3D8`.
