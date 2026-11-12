# Android Auto refresh — issue #350

## Scope and owner direction

All features, changes, and bugs discovered during this refresh remain under
[issue #350](https://github.com/goosepod/naviamp/issues/350), per the owner's November 12,
2026 instruction. Do not split this work into new issues.

Prioritize quick listening while driving: playlists (including smart playlists), Favorites,
recent listening, and a small useful Radio selection. Exclude genre/decade/random-album station
builders. Prioritize current-song Radio among extra Now Playing actions. Include Internet Radio
per-song covers and their refresh when the announced song changes.

## Initial environment, November 12, 2026

- Shared source baseline: `f1f5aaa726adaf10ffcb7add9f700936f0b886c8` (current main).
- Physical phone: Pixel 10a, Android 17 / API 37.
- Android Auto: `18.0.664264-release`.
- Desktop Head Unit: Google's native macOS ARM64 DHU 2.0,
  build `2022-03-30-438482292`, default touch/display configuration.
- Connection: USB accessory mode (`desktop-head-unit --usb=<device serial>`).
  This projects Android Auto from the phone; it is not an AAOS emulator.
- Follow-up navigation uses DHU's `-i hybrid` mode to exercise touch and rotary input.
- Installed regular Naviamp: v2.11.0, version code 61.
- Existing separate test app: v2.10.0-v2test, version code 60.

USB negotiation and the visible Android Auto dashboard, app launcher, and Naviamp Now Playing
screen were observed. Screenshots stay local because the dashboard includes personal map data.
No physical-car acceptance or audible-playback acceptance is claimed by these observations.

### Reproducing the USB session

`scripts/android-auto-dhu.sh usb` launches the installed app through USB accessory mode without
rebuilding or force-stopping Android Auto to start its developer server. Set `DEVICE` to the
phone's serial from `adb devices -l` when multiple devices are attached. The existing ADB-tunnel
commands remain available. Close the head-unit window or enter `quit` in its console to end a USB
session; the script's `stop` command applies to the ADB developer-server path.
Options after `usb` are forwarded to DHU; use `usb -i hybrid` for combined touch/rotary input.

Script verification: `bash -n`, help output, and `git diff --check` pass. The native `--usb=<serial>`
operation underlying this command was exercised with the visible Pixel session above.

## Initial findings

| Area | Evidence | Proposed work / verification |
| --- | --- | --- |
| Current-song Radio | Shared Now Playing already dispatches `StartRadio`; external playback has no corresponding action or capability. Installed v2.11.0 DHU shows Favorite, Shuffle, Repeat. | Extend common external controls and action priority; reuse shared transactions; verify eligible/unsupported media, races, failure preservation, and actual primary/overflow placement. |
| Live controls | Installed Radio Paradise screen displays previous/next, favorite, shuffle/repeat, and `0:00` duration. Shared live-radio model disables these capabilities, but Android's action mask/custom actions are unconditional. | Publish capability-aware controls and live metadata through the native adapter. |
| Browse priorities | Common root order is Now Playing, Queue, Recently Played, Recent Albums, Playlists, Radio; entries depend on loaded shared content. | Put playlists first; design Favorites/Recent entry points; exercise cold start and full smart-playlist availability. |
| Radio browse | Common external catalog exports all Home stations, including genre and decade stations. | Filter through an explicit shared car policy rather than changing Home or inferring from display text. |
| Internet Radio covers | Owner observes a generic record for Radio Paradise Main Mix in installed v2.11.0. Main already includes the v2.11.1 ICY StreamUrl fix; see `583-streamurl-artwork.md`. | Reproduce on current source; follow metadata → shared artwork selection → external snapshot → Android content URI → painted DHU cover. Verify multiple songs and missing/cleared covers. |
| Search | Common external search filters queue/Home/playlists/radio rows and selects the first match. | Audit provider-wide search and disambiguation; keep existing voice work in view without creating another issue. |
| Browse refresh | Publication planner invalidates browse sections on current media ID/queue changes, not catalog-only changes. | Verify playlists/recent/stations update while playback stays unchanged. |
| Copy | Shared browse labels and Android action labels include hardcoded English. | Use maintained shared translations for changed car-facing copy. |

## Baseline installation attempt

The official GitHub v2.11.1 APK was downloaded to update the old regular app without clearing data.
SHA-256: `e6eb5219468e6c9538e3e249932fb2324a43731dcf77821abaeed7adaeb2c3b3`.
Android rejected `adb install -r` with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: the installed package's
signing key differs. The regular app was not uninstalled or reset.

Current main then built successfully with `:apps:android:assembleDebug` (4m 36s) and installed with
`adb install -r` into the existing `app.naviamp.android.v2test` package, preserving its data.
The phone confirms `v2.11.1-v2test`, version code 62. Test APK SHA-256:
`b16af1a114b70ac14a9e57b2b8719cd6c211c933cfb6c56dddb30a5d8f9bd786`.
The test app was launched on the phone. Build success is not yet artwork/playback acceptance.
In the visible current-source DHU session, its restored library-track cover painted correctly.
Back from Now Playing opens browse tabs: Queue, Recently Played, Recent Albums, More. Playlists
is consequently not a primary tab in this layout. This confirms the proposed priority change
against the real host, rather than only the source ordering.

The local Gradle registry contains daemon records pointing to a removed CI JDK. A single-use
daemon with the installed JDK (`--no-daemon -Dorg.gradle.java.home=...`) completed the build,
although its expiration monitor still logged warnings about the stale records. No global Java
configuration or daemon registry was changed.

## Remaining acceptance

- Current-source DHU baseline, including Radio Paradise track covers and changes between songs.
- Playing/paused current-track Radio, repeat taps, track/source/output changes, failed generation,
  queue preservation, and continuation/refill.
- Small browse tree with full playlist/smart-playlist data, favorite listening, recent listening,
  empty/loading/error/offline states, catalog refresh, and stable queue occurrence selection.
- Touch and rotary, narrow/wide displays, day/night, background/cold start, disconnect/reconnect,
  interruptions, and normal phone/notification/Bluetooth regression checks.
- Physical head-unit acceptance while parked; DHU does not substitute for it.

## First browse implementation

All production changes are in the existing common external-playback bridge; no Android, Desktop,
or iOS production file changes are needed for this slice.

- Put Playlists, Recently Played, and Radio before Recent Albums and the duplicate Queue/Now Playing
  root entries. The head unit's native Queue button remains available.
- Use the shared Radio screen's full saved station catalog instead of Home's recent Internet Radio
  subset. Home's recent-only projection was confirmed in the mapper, explaining the missing stations
  in the initial test-app Radio list.
- Keep Library Radio as one immediate choice; omit genre, decade, and random-album builders from
  browsing/search and reject their stale media IDs at playback selection.
- Include independent catalog content in external snapshots, including while idle, so additions,
  renames, removals, and source clearing can notify browse subscribers without a track/queue change.
  Unchanged catalog/progress does not request extra metadata/notification publication.

Common regressions cover priority, a saved station absent from recents, artwork forwarding, valid
station dispatch, excluded/stale media IDs, and idle catalog additions/removal without unrelated
playback publication. All 22 external-playback JVM cases pass with zero failures/errors/skips.
Presentation's common production code compiles for Android, JVM, iOS arm64, and iOS simulator arm64
(`naviamp-350-common.log`, 3m 14s). Core-first architecture verification and the updated APK build
pass (`naviamp-350-device-build.log`, 26s). No platform production file, settings, schema, or UI copy
was changed by this slice.

The updated APK was installed into the test package successfully; SHA-256:
`883191c296ce20f09436ebcd742b413684f98c3f6c634b560aff0835e54932fb`.
The visible DHU now shows primary tabs **Playlists**, **Recently Played**, **Radio**, **More**;
Recent Albums/Queue/Now Playing are secondary entries. The Radio tab displays the full saved
catalog, including stations never played in the test app; genre/decade/random-album entries are
absent from the projected catalog (covered by common tests).

### Radio Paradise artwork acceptance

The owner initially continued listening through regular v2.11.0 after the test app was installed.
`dumpsys media_session` confirmed that old package still owned the playing Radio Paradise session;
the generic cover there was not evidence of a failure in the test build.

After selecting Radio Paradise Main Mix from the updated test app's full station list, Android
reported the test package as the media-button owner with a playing session; the regular package
reported paused. The DHU visibly painted the Foxwarren cover for **Foxwarren — Deadhead**, then
changed both title and cover for **Man/Woman/Chainsaw — Nosedive**. The owner also confirmed,
“And I am seeing album covers.” This verifies visible per-song artwork and one live metadata/cover
transition on the physical Pixel → DHU path. It does not claim every station or a physical-car test.
Player captures are retained locally as `naviamp-auto-current-main-mix.png` and
`naviamp-auto-main-mix-next-cover.png` under `/private/tmp`.

## Current-track Radio implementation

The shared external snapshot now projects eligible Radio/Shuffle/Repeat capabilities and ordered
custom actions, with Radio first. Track-only controls and seek/duration are omitted for live streams.
The shared external Radio command binds track, source, and rendered queue at acceptance. Common
transactions reject concurrent repeated builds and refuse delayed results after queue replacement,
including a new occurrence of the same track. Existing queue/progress/failure semantics are reused.
Shared labels come from existing resource vocabulary; 115 missing translations for those reused
labels were added in 13 catalogs without replacing existing community translations. All action/queue
labels now exist in all 17 maintained catalogs, and resource validation passes.

The common implementation passed its shared tests and Android/JVM/iOS compilation before adding
Android wiring. The final build passes **64 targeted cases** (24 external playback, 38 Now Playing
media transactions, two native MediaSession mapping cases), with zero failures/errors/skips, plus
Core-first architecture verification, Android packaging, and iOS arm64/simulator compilation.
Final log: `/private/tmp/naviamp-350-final.log` (3m 25s). Final APK SHA-256:
`ca56fda8be293459f63dca9bb2eab32354823438bb38f4760f7dbd6fd2252a13`.

The final test APK is installed on the Pixel. Live playback painted Bitter:Sweet's per-song cover
and advertised no custom track actions or previous/next/seek controls. The host still paints a
`0:00` total while playing live media even though the native metadata omits duration; investigate
the host's live-media presentation separately within #350.

Favorites is directly available from the primary Playlists tab; selecting it started its 241-track
queue. The native session advertises Start Radio first, followed by Unfavorite, Shuffle, Repeat.
After refreshing Android Auto's cached icons, its shared Radio glyph is visibly a primary Now
Playing control. Tapping it while **Bass Head — Bassnectar** played changed the native queue from
241 to 11 entries, retaining the current song and uninterrupted displayed progress (2:20 before,
2:39 after). Queue view shows Bass Head followed by generated choices including **Let Me Go —
Blurrd VZN** and **Probable Cause — Bassnectar & ill.Gates**. No phone dialog or browse detour was
required for the Radio action. Captures: `naviamp-auto-radio-button-clean-icons.png`,
`naviamp-auto-radio-button-after.png`, `naviamp-auto-radio-queue.png`, all under `/private/tmp`.

Reinstalling development APKs with the same version code left Android Auto with stale drawable
IDs; restart Android Auto's process and reconnect DHU before judging the icons. No Android Auto
data was cleared. The final window is left connected to the Pixel and the test app's Now Playing.

This first iteration is not the complete umbrella audit. Physical-car acceptance, other screen/
input layouts, long-running Radio continuation, network/lifecycle/output cases, a direct Favorites
entry, further translation/browse cleanup, and the remaining acceptance matrix stay under #350.

### Platform diff accountability

- `apps/android/src/main/kotlin/app/naviamp/android/AndroidNaviampPlaybackService.kt` maps shared
  capabilities/actions into Android MediaSession bit masks, custom-action IDs/drawables, queue titles,
  and native callbacks. Availability, ordering, validation, Radio generation, and races stay common.
- `apps/android/src/main/res/drawable/ic_radio_24.xml` is the Android vector resource required by
  MediaSession; it reproduces the shared Radio glyph. Native adapter unit tests exercise real ABI
  flag/resource mappings. No Desktop or iOS production source changed.

## Owner's release-preparation note

For the v2.12 release/store submission, refresh the screenshots across stores, using plausible
fictional album artwork instead of the previous Mac App Store placeholder covers. For Naviamp's
Navidrome app listing, the owner proposes a multiplatform hero image showing several systems,
followed by individual screenshots from different operating systems. Capture actual finished
release-candidate interfaces. This is a release-preparation requirement/proposal, not a claim that
new artwork, captures, or listing updates have been completed during the Android Auto audit.
