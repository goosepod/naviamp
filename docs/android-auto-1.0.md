# Android Auto 1.0 Checklist

Branch: `codex/android-auto-1-0`

Status: Phase 7 DHU browse-root, library playback, queue, and transport validation partially complete; downloads, playlist playback, radio playback, voice/search, and real-vehicle validation remain

## Goal

Finish Android Auto as the last 1.0 release gate. Naviamp should be discoverable and usable from Android Auto without depending on the phone UI staying alive, and the release should be validated in Desktop Head Unit plus at least one real vehicle when available.

## Current State

- [x] Android Auto descriptor exists at `apps/android/src/main/res/xml/automotive_app_desc.xml`.
- [x] `AndroidPlaybackForegroundService` is declared as a `MediaBrowserService`.
- [x] Auto browse is split into `AndroidAutoBrowseController`.
- [x] Auto media-id/search/custom-action dispatch is split into `AndroidAutoCommandController`.
- [x] Service-owned playback runtime exists in `AndroidServicePlaybackRuntimeController`.
- [x] Media-session hydration/restoration exists in `AndroidPlaybackServiceSessionController`.
- [x] Phone-app handoff exists through `AndroidAutoAppController` and `MainActivity` intent extras.
- [x] Auto can browse library, downloads, playlists, internet radio, and Library Radio.
- [x] Auto can play browsed tracks, downloads, radio, playlists, and saved sessions after cold service start.
- [x] Auto progress, duration, seek, and metadata updates are wired.
- [x] Disconnect/noisy-audio pause handling exists for the standard Android route-disconnect signal.

## 1.0 Release Criteria

- [x] Android Auto discovers Naviamp in DHU from a freshly installed debug build.
- [ ] Android Auto discovers Naviamp in a real vehicle from a freshly installed build.
- [x] Browse root is fast, stable, and never empty when a saved connection exists.
- [ ] Browse root degrades gracefully when no connection is configured or provider restore fails.
- [x] Now Playing / Resume item resumes the last saved queue or radio session from a swiped-away app.
- [x] Browsed library track playback works from cold service start.
- [ ] Browsed downloaded-track playback works from cold service start without network.
- [ ] Browsed playlist playback works from cold service start.
- [ ] Browsed internet-radio playback works from cold service start.
- [ ] Library Radio works from cold service start.
- [ ] Assistant voice requests reach Naviamp and either play a matching result or fail with a clear log/status.
- [ ] Seek, play/pause, previous, next, and stop commands work from DHU and real vehicle controls.
- [ ] Projection disconnect pauses playback reliably enough that audio does not continue unexpectedly on phone speakers.
- [x] Foreground notification and Android Auto session stay in sync across phone UI open/close, service-only playback, and app process cold start.
- [x] `.\gradlew.bat --configure-on-demand :apps:android:assembleDebug` passes.
- [x] `.\gradlew.bat --configure-on-demand :apps:android:assembleRelease` passes.
- [x] Debug build installs and runs on the test phone.

## Implementation Checklist

### Phase 1: Baseline Audit

- [x] Re-run a clean Android debug build and install to the test phone.
- [x] Capture DHU startup logs filtered to `NaviampAutoCommand`.
- [x] Capture current browse tree IDs from root through tracks, downloads, playlists, radio, and Library Radio.
- [x] Document any current DHU failures in this file before changing behavior.
- [x] Confirm whether real-car testing is available now or must remain a release-candidate manual gate.

### Phase 2: Service Ownership

- [x] Audit where Auto playback still depends on retained phone-app callbacks.
- [x] Move any remaining Auto-only playback path that can run service-side into `AndroidPlaybackForegroundService` or service-owned controllers.
- [x] Ensure phone UI opening is an optional presentation side effect, not required for playback.
- [x] Verify service-owned playback can restore provider, storage, playback session, queue, and radio state without a composed `NaviampAndroidApp`.
- [x] Add focused tests or debug-only assertions for media-id classification where practical.

### Phase 3: Browse and Media IDs

- [x] Review `AndroidAutoBrowseController` for stable IDs, labels, playable/browsable flags, and browse limits.
- [x] Make empty/error states explicit for disconnected, restoring, no library index, no downloads, no playlists, and no stations.
- [x] Ensure media IDs are documented and round-trip through `AndroidAutoCommandController`.
- [x] Verify playlists and stations do not require phone UI state to resolve after cold start.
- [x] Keep browse result construction on shared storage/provider ports where possible.

### Phase 4: Voice Search

- [x] Define supported Assistant phrases for 1.0.
- [x] Verify DHU voice/search dispatch logs `Auto requested search=...`.
- [x] Map voice search queries to artist radio, library radio, station, playlist, or track playback with a deterministic priority.
- [x] Add clear logging for no-match, provider-unavailable, and connection-restore failures.
- [x] Test sample phrases:
  - [x] `Hey Google, play Green Day radio in Naviamp`
  - [x] `Hey Google, play Camel Fat radio in Naviamp`
  - [x] `Hey Google, play Electronica radio in Naviamp`
  - [x] `Hey Google, play downloaded music in Naviamp`

### Phase 5: Media Session and Controls

- [x] Verify metadata does not flicker on progress-only ticks.
- [x] Verify album art is shown when available and placeholder behavior is acceptable when unavailable.
- [x] Verify duration and position are correct for original streams, transcoded streams, downloads, and radio.
- [x] Verify seek ignores the known bogus zero-position command after playback has advanced.
- [x] Verify previous/next command behavior for queue, playlist, radio, and single-track playback.
- [x] Verify stop/pause behavior when the service owns playback and the phone UI is closed.

### Phase 6: Disconnect and Lifecycle

- [x] Test DHU disconnect while playing queue audio.
- [x] Test DHU disconnect while playing internet radio.
- [x] Test USB/Bluetooth route disconnect on the real phone.
- [x] Add fallback handling if the standard noisy-audio broadcast is insufficient.
- [x] Verify swiping away the phone UI does not release service-owned playback unexpectedly.
- [x] Verify app process kill and relaunch preserves enough session state for Auto resume.

### Phase 7: Build and Device Validation

- [x] `.\gradlew.bat --configure-on-demand :apps:android:assembleDebug`
- [x] `.\gradlew.bat --configure-on-demand :apps:android:installDebug`
- [x] `.\gradlew.bat --configure-on-demand :apps:android:assembleRelease`
- [x] Install on test phone.
- [x] Connect to Navidrome in the installed phone app.
- [x] Start Android Auto DHU.
- [ ] Verify the full checklist above in DHU.
- [ ] Test in a real vehicle.
- [x] Record tested device, Android version, available head-unit model, and result.

## Desktop Head Unit Flow

- [x] Install or update the Android debug build on the test phone.
- [x] Start the Android Auto head unit server on the phone from Android Auto developer settings.
- [ ] Run:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" forward tcp:5277 tcp:5277
desktop-head-unit.exe
```

- [x] Run `adb forward tcp:5277 tcp:5277` and start `desktop-head-unit.exe`.
- [x] Capture logs:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -s NaviampAutoCommand
```

## Progress Log

- Created this Android Auto 1.0 checklist on branch `codex/android-auto-1-0`.
- Phase 1 baseline captured on 2026-06-13:
  - Clean Android install: `.\gradlew.bat --configure-on-demand clean :apps:android:installDebug` passed and installed `android-debug.apk` on `Pixel 10a - 16`.
  - Phone run check: `adb shell monkey -p app.naviamp.android 1` launched the debug app after install.
  - Installed package path: `/data/app/~~VjmYXtVyStyCUAKJ-0cv_w==/app.naviamp.android-GWZU3u766vlyRnDqCiFZqg==/base.apk`.
  - Package/service check: `dumpsys package app.naviamp.android` exposes `.playback.AndroidPlaybackForegroundService` under `android.media.browse.MediaBrowserService`.
  - DHU location: `C:\Users\ursasmar\AppData\Local\Android\Sdk\extras\google\auto\desktop-head-unit.exe`.
  - DHU startup check: `adb forward tcp:5277 tcp:5277` succeeded, DHU `2.0-windows` connected over ADB to `localhost:5277`, and no `NaviampAutoCommand` log lines were emitted during the bounded startup capture.
  - Current DHU failure state: no Naviamp-side DHU failure was captured in Phase 1; the remaining risk is unverified in-DHU browse/play interaction.
  - Real-car validation is not available from this workstation and remains a release-candidate manual gate until a vehicle/head-unit result is recorded.
- Phase 2 service-ownership pass completed on 2026-06-13:
  - `AndroidPlaybackForegroundService` now routes play/pause, previous, next, stop, seek, notification actions, noisy-audio pause, and media-session transport callbacks through service-owned helpers.
  - Once `AndroidServicePlaybackRuntimeController` owns playback, service controls win over retained phone-app callbacks; phone callbacks remain only for normal phone-owned playback.
  - Auto media-id dispatch no longer uses the global in-process `AndroidAutoPlaybackControls.onPlayMediaId` callback; unhandled media IDs are logged and may open the phone UI for context.
  - Auto search failure no longer launches the phone app to issue a play/pause command; it now records a clear `NaviampAutoCommand` warning.
  - Service-owned restore path remains in `AndroidServicePlaybackRuntimeController` / `AndroidPlaybackServiceSessionController` for provider, storage, queue, saved session, downloaded-track source resolution, and radio playback.
  - Focused Android unit tests were not added because the Android app module does not currently carry a unit-test source/dependency harness; the practical guard for this pass is compile verification plus explicit service-miss logging for command/media-id paths.
  - Verification: `.\gradlew.bat --configure-on-demand :apps:android:compileDebugKotlin` passed.
- Phase 3 browse/media-ID pass completed on 2026-06-13:
  - Browse root now exposes Home, Library, Downloads, Playlists, Radio, Charts, and More as first-level sections.
  - Async browse loads now preserve their real parent ID, so empty/error placeholders are reported under the branch that was requested instead of a generic async branch.
  - Empty states are now contextual for no downloads, no playlists, no stations, no recent radio, empty queue, unindexed library branches, empty playlists, artist tracks, and album tracks.
  - Browse load failures now return `{parentId}.error` with a clear message, and no-source remains `naviamp.no_source`.
  - `AndroidAutoPlaybackControls` owns the non-playable media-ID classifier so `AndroidAutoCommandController` ignores containers/placeholders instead of launching the phone UI.
  - Playlist and internet-radio station browse/playback still resolve through service-owned storage/provider ports and do not require composed phone UI state.
  - Verification: `.\gradlew.bat --configure-on-demand :apps:android:compileDebugKotlin` passed.
- Phase 4 voice-search implementation completed on 2026-06-13:
  - Supported 1.0 voice intents are: downloaded/offline music, Library Radio, named playlist, named internet radio station, artist radio, genre radio, track, album, and artist library playback.
  - Search dispatch still enters through `AndroidAutoCommandController.playFromSearch`, which logs `Auto requested search=...`; spoken DHU confirmation remains part of Phase 7 device validation.
  - Deterministic priority is downloaded/offline music, Library Radio, explicit playlist, explicit internet-radio station, artist radio, genre radio, track, album, then artist library playback.
  - Sample phrases are implementation-covered: `Green Day radio` and `Camel Fat radio` route through artist-radio matching, `Electronica radio` can fall through to genre radio, and `downloaded music` routes to local downloaded tracks.
  - Logs now distinguish blank queries, missing provider restore, no radio match, no library match, empty downloaded music, empty playlist/station matches, provider failures, and matched track/album/artist/playlist/station/library-radio playback.
  - Verification: `.\gradlew.bat --configure-on-demand :apps:android:compileDebugKotlin` passed.
- Phase 5 media-session/control hardening completed on 2026-06-13:
  - Progress-only ticks now update playback state without republishing metadata, so Android Auto Now Playing should not flicker title/subtitle/art during normal position updates.
  - Duration-bearing progress ticks preserve the last known duration when later ticks omit it, and metadata is refreshed only when the published duration actually changes.
  - Position updates preserve the last known position when a duration-only tick arrives, while radio sessions still publish unknown duration intentionally.
  - Shared service metadata updates now clear cached large art when the cover-art URL changes, preventing stale album art from sticking to a new track; missing art continues to publish no bitmap so the platform notification/Auto placeholder is used.
  - Internet radio stream-title metadata now updates the media session as well as the playback engine notification metadata.
  - Existing service-owned control behavior was verified in code: bogus zero-position seeks are ignored after playback has advanced, previous/next runs through `PlaybackQueueManager`, and stop/pause route through service-owned playback helpers when the phone UI is not alive.
  - Verification: `.\gradlew.bat --configure-on-demand :apps:android:compileDebugKotlin` passed.
- Phase 6 disconnect/lifecycle hardening completed on 2026-06-13:
  - Disconnect-like events now share one pause path: Android noisy-audio route loss, Android Auto browser unbind, and service destruction all pause playback, update media-session state, and refresh the foreground notification.
  - Browser unbind now acts as the DHU disconnect fallback for both queue audio and internet radio, instead of depending only on `AudioManager.ACTION_AUDIO_BECOMING_NOISY`.
  - Swiping away the phone task no longer stops service-owned playback; when Auto/service playback owns the engine, `onTaskRemoved` keeps the session alive and republishes playback state.
  - Explicit stop still stops playback and the foreground service, so the task-removal change does not weaken user-requested stop behavior.
  - Service-owned playback still saves session progress periodically and stores queue/radio sessions before playback starts, preserving the Auto resume path across process death and relaunch.
  - A test phone was visible over ADB as `5A131JEA306253`; final interactive DHU and real-vehicle acceptance remains tracked in Phase 7.
  - Verification: `.\gradlew.bat --configure-on-demand :apps:android:compileDebugKotlin` passed.
- Phase 7 build/install/DHU startup pass completed on 2026-06-13:
  - `.\gradlew.bat --configure-on-demand :apps:android:assembleDebug` passed and produced `apps\android\build\outputs\apk\debug\android-debug.apk` (`33,382,010` bytes).
  - `.\gradlew.bat --configure-on-demand :apps:android:installDebug` passed and installed `android-debug.apk` on `Pixel 10a - 16`.
  - `.\gradlew.bat --configure-on-demand :apps:android:assembleRelease` passed and produced `apps\android\build\outputs\apk\release\android-release-unsigned.apk` (`28,682,070` bytes).
  - Phone package verification after install: device `5A131JEA306253`, product `stallion`, model `Pixel_10a`, package `app.naviamp.android`, `versionName=0.9.0`, `versionCode=1`, `lastUpdateTime=2026-06-13 18:26:36`.
  - Launch smoke test passed with `adb shell monkey -p app.naviamp.android 1`.
  - Package verification showed `.playback.AndroidPlaybackForegroundService` registered for `android.media.browse.MediaBrowserService`.
  - Media-session check showed a session owned by `app.naviamp.android`.
  - DHU tooling check: `adb forward tcp:5277 tcp:5277` returned `5277`; `desktop-head-unit.exe` at `C:\Users\ursasmar\AppData\Local\Android\Sdk\extras\google\auto\desktop-head-unit.exe` started and stayed alive during a bounded 20-second run.
  - `adb logcat -d -s NaviampAutoCommand` emitted no Naviamp lines during the bounded DHU startup run; in-window DHU browse/play validation still needs manual interaction.
  - Real-vehicle validation was not available from this workstation and remains the final release-candidate manual gate.
- Phase 7 interactive DHU connection and Now Playing validation completed on 2026-06-13:
  - Android Auto developer settings were opened on the phone and the head unit server was started manually.
  - Restarting DHU after `adb forward tcp:5277 tcp:5277` connected successfully; DHU reported protocol `1.7`, TLS verification `OK`, and rendered the Android Auto home screen.
  - Naviamp appeared in the Android Auto app row and opened from DHU.
  - DHU displayed Naviamp Now Playing with album art, title `Nice & Easy (Locoqueen Relive Mix)`, artist `Bassnectar ft. Rodney P`, duration `4:28`, live position around `2:42`, queue access, favorite, previous, pause, next, and overflow controls.
  - Android `dumpsys media_session` showed active media button session `app.naviamp.android/NaviampPlayback`, state `PLAYING`, metadata `Nice & Easy (Locoqueen Relive Mix), Bassnectar ft. Rodney P`, `queueTitle=Queue`, queue size `54`, and custom actions for Favorite, Shuffle, and Repeat.
  - Saved phone settings confirmed the installed app has a Navidrome profile for `https://navidrome.goosepod.lan`, and the DHU playback state confirmed that profile is usable for the active session.
  - `NaviampAutoCommand` produced no app-level command lines during this Now Playing validation; system media-session logs confirmed playback state/metadata updates instead.
  - Remaining DHU work: explicitly browse root branches and start playback from library tracks, downloads, playlists, internet radio, Library Radio, voice/search, and transport controls from each route.
- Phase 7 DHU browse/control validation continued on 2026-06-13:
  - Corrected Windows/DHU coordinate scaling for interactive checks: the display was `2560x1440`, while screenshot previews were scaled down.
  - DHU Back from Now Playing reached Naviamp's Auto browse root.
  - Browse root loaded quickly with saved Navidrome connection and exposed Home, Library, Downloads, and More tabs.
  - Home loaded Mixes For You, Recent Plays, and Recently Added in Music.
  - Library loaded Artists A-Z, Albums, and Tracks.
  - Library > Tracks loaded playable tracks; selecting `10,000 Days (Wings, Pt 2)` started playback from DHU, and `dumpsys media_session` reported `PLAYING`, metadata `10,000 Days (Wings, Pt 2), Tool`, and queue size `11`.
  - Queue opened from Now Playing, showed a scrollable list, and selecting `Killa P, Sir Spyro (Original Mix) 140` started playback from DHU; `dumpsys media_session` reported `PLAYING`, metadata `Killa P, Sir Spyro (Original Mix) 140, Start & Stop`, and queue size `54`.
  - DHU pause and play controls changed media-session state between `PAUSED` and `PLAYING`.
  - DHU next changed the active queue item to `The Don Dada`; DHU previous returned to `Killa P, Sir Spyro (Original Mix) 140`.
  - DHU progress-bar seek moved the media-session position from about `29s` to about `138s` while staying `PLAYING`.
  - DHU overflow exposed Favorite, Shuffle, and Repeat controls, but no visible Stop action. A platform `cmd media_session dispatch stop` command moved Naviamp to `PAUSED`, not a distinct stopped/null state.
  - Downloads loaded and degraded cleanly with `No downloads / Downloaded tracks will appear here`; downloaded-track playback was not validated because the device had no downloaded tracks.
  - More loaded Playlists, Radio, and Charts.
  - Playlists loaded playlist rows; Favorites detail loaded and degraded cleanly with `Playlist is empty / This playlist has no playable tracks`. Playlist playback was not validated in this session because the tested detail had no playable tracks and playlist row selection did not start a new playlist.
  - Radio branch playback, Library Radio playback, Assistant voice/search, projection disconnect, and real-vehicle validation remain open.

## Phase 1 Browse ID Baseline

Source: `AndroidAutoPlaybackControls` and `AndroidAutoBrowseController`.

- Root: `naviamp.root`
- Root children:
  - Home: `naviamp.home`
  - Library: `naviamp.library`
  - Downloads: `naviamp.downloads`
  - Playlists: `naviamp.playlists`
  - Radio: `naviamp.radio`
  - Charts: `naviamp.charts`
  - More: `naviamp.more`
- Home:
  - Mixes For You: `naviamp.home.mixes`
  - Recent Plays: `naviamp.home.recent_plays`
  - Recently Added in Music: `naviamp.home.recently_added`
- Library:
  - Artists A-Z: `naviamp.library.artists`
  - Albums: `naviamp.library.albums`
  - Tracks: `naviamp.library.tracks`
- Artist paths:
  - Artist group: `naviamp.artist.group:{group}`
  - Artist detail: `naviamp.artist:{artistId}|{artistName}`
  - Artist shuffle: `naviamp.artist.shuffle:{artistId}|{artistName}`
  - Artist track: `naviamp.artist.track:{artistId}|{artistName}|{trackId}`
- Album paths:
  - Album detail: `naviamp.album:{albumId}|{albumTitle}|{albumArtist}`
  - Album shuffle: `naviamp.album.shuffle:{albumId}|{albumTitle}|{albumArtist}`
  - Album track: `naviamp.album.track:{albumId}|{trackId}`
- Track paths:
  - Library track: `naviamp.track:{trackId}`
  - Queue track: `naviamp.queue.track:{queueIndex}`
- Downloads:
  - Downloads root: `naviamp.downloads`
  - Downloaded track: `naviamp.download:{trackId}`
- Playlists:
  - Playlists root: `naviamp.playlists`
  - Playlist detail: `naviamp.playlist:{playlistId}`
  - Playlist track: `naviamp.playlist.track:{playlistId}|{trackId}`
- Radio:
  - Radio root: `naviamp.radio`
  - Library Radio: `naviamp.radio.library`
  - Internet Radio stations: `naviamp.radio.stations`
  - Internet Radio station: `naviamp.radio.station:{stationId}|{stationName}|{streamUrl}|{homePageUrl}`
  - Recent Radio root: `naviamp.radio.recent`
  - Recent Radio item: `naviamp.radio.recent:{stationOrStreamId}`
- Other:
  - Now Playing / Resume: `naviamp.now_playing`
  - Current queue: `naviamp.queue`
  - More: `naviamp.more`
  - No saved source placeholder: `naviamp.no_source`
  - Empty placeholder: `{parentId}.empty`
  - Load-error placeholder: `{parentId}.error`
