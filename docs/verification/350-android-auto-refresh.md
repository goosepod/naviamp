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

## Follow-up: stable Radio placement beside Favorite

The owner requested Radio immediately left of the Favorite heart, then observed that the first
track had put it in the unavailable Previous slot. Android Auto documents that custom actions can
fill unavailable transport slots unless the MediaSession reserves them.

Common snapshot policy now reserves previous/next positions for non-live playback with a current
item. It uses the explicit live-media flag, not duration availability, so an unknown-duration library
track keeps the same layout. Live radio and empty playback do not reserve those spaces. Android's
existing service only translates this policy into the two `MediaConstants` reservation extras.

All 25 shared external-playback tests pass, including first/middle/last/single-track boundaries and
layout-policy publication changes. Shared Android/JVM/iOS arm64/simulator compilation passes
(`/private/tmp/naviamp-350-slot-common.log`, 3m 47s). Both Android mapping tests, the architecture
check, and APK packaging pass (`naviamp-350-slot-device.log`, 47s).
Installed test APK SHA-256: `e371803397ba77d408727cedd95c100d74f5072d84861a127f8aac3313b5383c`.

Visible Pixel → DHU verification: selecting Favorites started its first song, **Pardon Me — Incubus**,
with Previous unavailable and Radio immediately left of the heart. The owner confirmed “now it sin
the right spot.” Tapping Next advanced to **Criminal — Fiona Apple**; Previous became available,
while Radio and the heart stayed in the same positions. Local captures:
`naviamp-auto-slot-first-track.png` and `naviamp-auto-slot-second-track.png` under `/private/tmp`.
Only `AndroidNaviampPlaybackService.kt` changed in platform production code for this follow-up;
its concrete boundary is Android `MediaSessionCompat.setExtras` and `Bundle`/`MediaConstants` keys.

### Follow-up: full-window backgrounds, artwork resolution, Repeat glyphs

Owner reported white status/navigation bands in the development phone app and clarified that the
background itself must extend across the full window. History identifies the Core-level
`naviampContentInsets()` introduced with #483 as the point where the entire shell, including its
background, was constrained. A solid dark outer backing removed white but did not restore the
intended presentation; that intermediate approach was discarded. Shared shells now paint their
background before applying content insets. Filled Now Playing keeps one full-window cached artwork
layer and applies system-bar insets to its foreground controls; non-filled layouts inset their
foreground normally. Keyboard insets remain consumed once by the shell.

Large current-track and album-header artwork now requests `CoverArtSize.Hero` at 2048 pixels.
Queue/list artwork continues using 512-pixel thumbnails. The shared decoder's maximum follows the
Hero size; decoded images have a 64 MiB LRU budget as well as the existing 240-entry limit. Album
information images are fallbacks rather than replacements for available server artwork. Separate
resolution URLs retain distinct cache keys, so old thumbnail bytes cannot satisfy a Hero request.
Source artwork smaller than the requested resolution is not enlarged during decoding.

Android Auto custom Repeat actions now use the shared rounded loop/return glyph: dim Off, plain
loop for Queue, centered 1 for Track. The only native production change is
`AndroidNaviampPlaybackService.kt`: it maps shared Repeat states to Android drawable resource IDs
required by `PlaybackStateCompat.CustomAction`. The vector XML assets contain matching paths, with
a path-level alpha for Off so host icon tint does not erase the state. Native mapping tests compare
the Android vector paths with the shared ImageVectors.

Shared checks passed: 178 selected tests (9 layout/cache/header cases, 39 Now Playing cases,
130 Navidrome cases), with Android, JVM/Desktop and iOS simulator compilation. Follow-up checks
passed: 6 decode/transition/continuity cases, one Jellyfin resolution case, three Android action/vector
mapping cases, Core-first architecture guard and APK assembly. Logs:
`/private/tmp/naviamp-350-edge-quality-common.log` (5m 20s) and
`/private/tmp/naviamp-350-edge-quality-device.log` (1m 37s).

Installed APK SHA-256: `0cdddc7a7c9d9fd0e815f385c657dcfff1d77c104c42f708354eef5021a0f89a`.
Visible physical Pixel verification confirmed artwork behind the status bar and continuing to the
bottom edge, with foreground controls safely inset. Owner confirmed the result. Separately switched
through Aurora, Album Blur (28dp) and Single Color (#32253F): Home and Settings showed the intended
background continuing through both system-bar areas, without white bands. Restored Aurora/Balanced
and the original filled Now Playing presentation after the checks.

The currently paused Metallica track uses the Apple Music ’90s compilation cover. Both the old
thumbnail response and the newly requested Hero response contain the same 500×500 source image;
requesting 2048 cannot add detail absent from that server artwork. This is a source-resolution
limitation, separate from the fixed request/decoder limits. No authentication URLs were logged.

CI for preceding Radio-position commit `f278ec4ca` passed the full platform matrix, including API35
raster tests (run 43300922718). New follow-up CI is pending until pushed. Visible DHU check after refreshing Android Auto's resource cache confirmed all three Repeat states:
Off is dim, Queue is a bright plain loop, and Track adds a centered 1. Returned to Off after cycling.
Radio remains immediately left of Favorite. Captures are local:
`/private/tmp/naviamp-350-repeat-current.png`, `naviamp-350-repeat-state-1.png`,
`naviamp-350-repeat-state-2.png`, and `naviamp-350-repeat-state-off.png`.
Projection reconnection resumed the previously paused track; paused it again during the check.

### Follow-up: stale Recently Played tracks

Owner found only two old tracks in Auto's Recently Played tab. The common external browse bridge
read Home's recent tracks, populated from imported library-index `last_played_at_iso8601` metadata.
The active Core playback path never recorded actual plays in the existing playback-history store,
so current listening could not enter the list. Home also only republished recents after a full load.

The common playback controller now records a source-scoped library track when it actually reaches
Playing. It deduplicates repeated progress/pause/resume observations and excludes Internet Radio
and discovery previews. The common Home controller serializes persistence/readback away from the
UI thread, guards source changes, updates the shared media registry, Home rows/section/open page,
and consequently Auto's browse catalog. A revision guard prevents slow Home loading from restoring
an older history snapshot. History persistence uses the existing table; only SQL queries changed,
with no schema migration. Recent results are unique by track, newest first, and limited after
deduplication. Clearing/resyncing the library catalog does not fabricate or discard listening events.

Earlier plays missing from the active history path cannot be reconstructed reliably from the queue;
no historical plays are invented. The corrected list repopulates through actual listening.

All implementation remains in shared Domain, Presentation, UI, and Storage. No Android, Desktop,
or iOS production source changes are needed for this follow-up. The first 104 selected tests passed
(51 playback, 14 Home, 25 external bridge, 14 storage), with shared Android/JVM/iOS simulator
compilation. Log: `/private/tmp/naviamp-350-recents-verified.log` (4m 6s). APK/architecture build
passed (`/private/tmp/naviamp-350-recents-device.log`, 1m 32s). Installed APK SHA-256:
`a0a36ce1b8da5e76e813cfefde8379198abbe72451720797546906c72ee4d726`.

Owner tested active playback and confirmed that the new list correctly tracks what was played.
During the same test, owner found that selecting a recent item did not replay it. The bridge used
`Media.TrackAction`, whose controller resolves only Search results, instead of
`Home.RecentTrackAction`. Corrected both tapped and voice-selected history results to use the
existing Home history transaction. The regression test now executes that command through the real
shared track-action controller with empty Search results and checks the resulting queue, current
track and playback-effect selection. All 130 selected Presentation tests passed (51 playback, 14
Home, 25 external bridge and 40 Now Playing/transaction cases), alongside the earlier 14 passing
storage tests. Android, JVM/Desktop and iOS simulator shared compilation passed. Logs:
`/private/tmp/naviamp-350-recents-selection.log` (1m 29s) and
`/private/tmp/naviamp-350-recents-selection-device.log` (APK/architecture, 35s).

Installed final APK SHA-256: `5f91221afa105c45837c50c6397feac438d88a63f309022bf9e428577871bc2b`.
Owner controlled the live playback check (agent stopped concurrent taps). Owner confirmed that the
new list correctly tracks listening, then confirmed that tapping a recent track starts playback after
installing the final replay fix. Both live history and replay have physical-phone acceptance.


## Resumed acceptance walkthrough

Owner reported the changed functionality was working and requested remaining testing or completion.
The final production commit `fd715a14d` was rechecked on the connected Pixel without clearing data.

| Check | Result and limits |
| --- | --- |
| Compact touch display | Google `default_6in.ini`, 800×480 with margins: playlists, persisted recent tracks, full saved Radio catalog and controls painted correctly. |
| Wide display | 1280×720 derived from Google's rotary profile: artwork/transport remained legible, Radio stayed immediately left of Favorite. |
| Day/night | Actual sensor switching changed the wide map's theme; media controls remained legible in both modes. |
| Hardware transport | Official DHU media-pause command paused playback and held the saved position. |
| Rotary | Focus was visibly drawn on the transport/dock, but a complete rotary-only browse/activation walkthrough was not established. Do not mark full rotary acceptance passed. |
| Cold/background launch | Stopped both test app and Auto processes, reconnected with the compact profile, and opened Media. The app process/MediaSession reappeared, restored the saved queue and loaded browse content without opening the phone activity. The phone's top resumed activity was Auto's GhostActivity. |
| History after process recreation | Recent tracks persisted and showed current listening (All That Matters, then SOCO), rather than the old two imported catalog records. |
| Saved stations after cold launch | Complete saved catalog was available immediately in Radio, including never-recent stations. |
| Physical vehicle | Not exercised; emulator/Pixel tests do not establish parked head-unit, real Bluetooth/interruption or Assistant/Gemini acceptance. |

Local captures are `naviamp-350-acceptance-*` in `/private/tmp`. Maps in wide captures contain
location imagery; keep these local rather than publishing screenshots indiscriminately. Emulator
profiles and commands follow the official DHU guide:
https://developer.android.com/training/cars/testing/dhu .

The first reconnect met a stale projection-protocol framing error. Restarting Auto's process (no
data clear) restored the session. This emulator recovery is not recorded as a Naviamp defect.

CI run 43315605811 caught a remaining legacy `MediaUiMappersTest` expectation: it required external
album-information artwork to replace the server cover. The intended quality policy now keeps the
server Hero cover primary and uses the information image as fallback. Updated that existing test to
assert Hero priority and independent information/artist visibility. The complete local shared UI suite
passed: 751 tests, zero failures/errors/skips (`/private/tmp/naviamp-350-full-ui.log`, 2m 25s).
No production behavior was changed to satisfy the outdated assertion. The same source's required
Desktop macOS/Windows/Linux and Android API30/API35 native CI jobs passed; the final CI rerun must
include this corrected test before the work is marked complete.
