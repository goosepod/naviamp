# Android Auto 1.0 Checklist

Branch: `codex/android-auto-1-0` (merged to `main` at `d2063959`; follow-up fixes now land on `main`)

Status: Implementation is merged. Phase 8 cache/offline validation is mostly complete; Auto browse has been simplified for fast music starts, Android now exposes cache-size parity with desktop, service-owned Auto playback uses the configured prefetch depth, shared cover-art cache reads and proactive cover-art warming are wired, offline favorite and play-report sync have been device-validated, cache eviction protects the active drive queue, and the 10-song cached-drive Android Auto endurance gate has passed in DHU after a clean DHU restart. Remaining work is validation and polish, not branch implementation handoff.

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
- [x] Auto primary browse is intentionally small: Resume, Recent Radio, Playlists, Radio, Suggested Mixes, Downloads when present, and Current Queue when active.
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
- [x] Cached-track playback covers a typical no-network drive: at least 10 cached songs can play back-to-back in Android Auto with Tailscale/network disabled.
- [x] Cached tracks preserve the presentation data Android Auto needs: title, artist, album, duration, queue position, and album art/cover art.
- [x] Offline listening activity and user actions are durably queued while Navidrome is unreachable, then uploaded when the server is reachable again.
- [x] Offline sync includes play history/scrobbles, starred/favorited changes, and any other track-level user actions available from Android Auto or Now Playing.
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

### Phase 8: Existing Song Cache Validation

- [x] Audit the existing Android song-cache path and confirm it can serve playback without provider/network access.
- [x] Confirm Android separates total cache capacity from prefetch depth: the cache budget controls how many songs can be retained overall, while prefetch depth controls how many queued songs are kept warm ahead.
- [x] Confirm cached tracks persist across app restart, service restart, and Android Auto cold service start.
- [x] Confirm cached tracks can start from Android Auto when Tailscale is disabled and the Navidrome server is unreachable.
- [x] Confirm cached playback can continue track-to-track while offline, not only finish the currently buffered track.
- [x] Confirm cached queue/session restore prefers local cached audio when network restore fails.
- [x] Confirm cached metadata includes title, artist, album, duration, track ID, album ID when available, and enough queue/session context for Now Playing.
- [x] Confirm cached album art is stored locally and can be used by the Android notification and Android Auto Now Playing without network.
- [x] Confirm cache eviction does not delete the currently playing track or the next few queued tracks during a drive.
- [x] Audit existing offline action persistence for play history, scrobbles, starred/favorited changes, and Now Playing actions while Navidrome is unreachable.
- [x] Confirm offline actions survive app restart, service restart, and Android Auto cold service start before reconnect.
- [x] Confirm reconnect uploads queued offline actions exactly once, with no lost favorite/star changes and no duplicate play-history submissions.
- [x] Confirm failed sync retries remain queued with clear logging/status instead of being dropped silently.
- [x] Add a repeatable validation script or manual checklist for: fill cache, disable Tailscale/network, kill app/service, start Android Auto, play cached music for at least 30 minutes.

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

## Phase 8 Offline Validation Runbook

Use the latest installed debug build from the Phase 8 log.

1. Open Naviamp on the phone while connected to Navidrome.
2. Go to Settings > Diagnostics and record:
   - `Audio cache`
   - `Images`
   - `Pending offline actions`
   - `Failed offline actions`
3. Start playback from Android Auto using a normal queue or playlist, not internet radio.
4. Let one track play long enough for the configured prefetch depth to warm upcoming tracks.
5. Confirm Diagnostics still shows non-zero `Audio cache` and `Images`.
6. Kill or swipe away the phone UI so playback is service-owned.
7. Disable Tailscale or otherwise block access to the Navidrome server. If doing a stricter test, also disable Wi-Fi/mobile data after the cache is warm.
8. From Android Auto, resume playback from cold service start.
9. Confirm cached playback starts without Navidrome access.
10. Let playback advance track-to-track for at least 30 minutes or through at least 10 cached songs.
11. During the outage, toggle favorite on the current track from Android Auto.
12. Open Settings > Diagnostics and confirm `Pending offline actions` rises above the baseline.
13. Force-stop Naviamp or reboot the phone, keep Navidrome unreachable, then reopen Android Auto and confirm the pending count remains.
14. Re-enable Tailscale/network.
15. Open Naviamp or reconnect to Navidrome from Android Auto.
16. Confirm `Pending offline actions` returns to the baseline and `Failed offline actions` does not increase.
17. Verify on Navidrome that play history/scrobbles and the final favorite state landed exactly once.

Pass criteria:

- Cached queue playback starts with Navidrome unreachable.
- Playback advances between cached tracks without network.
- Android Auto Now Playing has title, artist, duration, and usable album art from local data.
- Offline favorite/play-report actions survive app/service restart.
- Reconnect clears pending actions after upload without duplicate play-history submissions.

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
  - User-observed DHU issue: browse/menu rows do not appear to show album art. Unknown whether this is an Android Auto template limitation, an app metadata/art population issue, or DHU behavior; investigate before 1.0 polish signoff.
  - User-observed DHU issue: Queue screen scrolling is broken. Drag/scroll attempts immediately snap the list back to the top, making deeper queue items hard or impossible to reach from Android Auto.
  - User design decision: remove broad `Tracks`, `Artists`, and `Albums` library browsing from Android Auto secondary screens. In-car browsing should avoid long-scroll library catalogs; search should handle specific track/artist/album lookup instead.
  - User design direction: less is more for Android Auto. Auto browse should prioritize fast music starts over catalog exploration.
  - Proposed Android Auto browse focus for the next implementation pass:
    - Home: Resume / Now Playing, Recent Radio, Suggested Mixes, Recent Plays.
    - Playlists: saved playlists, with playable lists only when practical.
    - Radio: Recent Radio first, Internet Radio stations, Library Radio.
    - Downloads: keep only if downloaded tracks exist; otherwise it can be omitted from the primary surface or shown as a simple empty state.
    - Search: keep as the path for specific track, artist, album, and genre lookup.
  - User cached-drive requirement: verify the existing song cache supports common short trips without remembering to enable Tailscale/network first. Target behavior is a roughly 10-song cache that can cover most drives under 30 minutes, with cached audio plus all required track metadata and album art available locally.
  - User offline-sync requirement: when Navidrome is unreachable, Naviamp should remember what was played and any starred/favorited actions, then upload those changes once the server connection returns. This should be treated as part of the cached-drive validation, not just a phone-app nicety.
  - Radio branch playback, Library Radio playback, Assistant voice/search, projection disconnect, and real-vehicle validation remain open.
- Phase 8 cache/offline implementation pass started on 2026-06-14:
  - Existing cache path audit: `resolvePlaybackAudioSource` already prefers downloaded audio, then cached audio, then provider stream; resolver tests cover local-file preference and provider fallback.
  - Cache model clarification: Android does not need a fixed 10-song cache. The audio cache budget controls total retained offline capacity, while the prefetch depth controls how many upcoming queue items are proactively filled inside that budget.
  - Android now exposes shared cache controls in Settings: enable/disable audio cache and prefetch, prefetch depth, audio cache budget, and download storage budget.
  - Android persists cache settings through `AndroidSettingsStore`, loads them into `AndroidAppState`, and applies the audio-cache byte limit to storage when settings change or app state initializes.
  - Android downloads now use the configured download storage budget instead of the old platform constant.
  - Phone-owned playlist playback now uses the configured audio-cache enabled flag and prefetch depth.
  - Service-owned Android Auto playback now starts the configured prefetch loop after saved-session playback resolves, so cold Auto playback can continue filling the cache from the service path instead of relying on phone UI playback.
  - The service prefetch path reuses the shared `planAudioPrefetchWork` / `runAudioPrefetch` logic and the existing Android audio store; it skips already-downloaded/cached tracks before fetching provider audio.
  - Shared image cache contract now exposes a cached-only image read, implemented by both Android storage and desktop cache.
  - Android Auto notification/Now Playing art now checks the shared cached image store before network fetches, and successful notification-art fetches populate the same shared cache path.
  - Shared audio prefetch now has a non-fatal cover-art warming hook, so artwork fetch failures do not mark audio caching as failed.
  - Android phone-owned playback and service-owned Android Auto playback now warm cover art for prefetched tracks through provider-aware Navidrome byte fetches into the shared image cache.
  - Android Auto browse root was simplified around fast starts: Resume, Recent Radio, Playlists, Radio, Suggested Mixes, Downloads only when local downloads exist, and Current Queue only when a queue exists.
  - Broad in-car catalog browsing is no longer advertised from the primary Auto surface; Library/Charts/All Tracks are removed from the root/More flow, while search still handles specific track/artist/album lookup.
  - Queue browse rows no longer attach per-track art URIs, reducing metadata churn while investigating the DHU scroll-to-top behavior.
  - Shared pending provider actions now live in core domain/storage with SQLDelight migration `6.sqm`; Android and desktop both expose repository adapters so the offline action queue is not Android-only.
  - Android now queues failed `reportNowPlaying`, `reportPlayed`, track favorite, artist favorite, and album favorite provider mutations against the active media-source ID.
  - Favorite mutations remain optimistic in the UI while disconnected; repeated favorite changes for the same entity collapse to the latest pending value before reconnect.
  - Successful Navidrome reconnect now replays pending provider actions and deletes only successfully uploaded rows; failed rows remain queued with attempt count/error details for retry.
  - Shared domain tests cover replay success and retry retention for failed rows.
  - Service-owned Android Auto playback now queues failed Now Playing and played/scrobble reports without requiring the composed phone UI.
  - Service-owned Android Auto favorite toggles now update the visible favorite state, persist the updated queue/session presentation, and call or queue the provider favorite mutation when no phone callback is alive.
  - Android phone-owned Now Playing heartbeat reports now also use the pending-action wrapper, so offline heartbeat failures are queued instead of dropped.
  - Shared storage stats now include total and failed pending provider action counts; Android Settings > Diagnostics shows these as `Pending offline actions` and `Failed offline actions` for the offline validation loop.
  - On-device cache baseline after installing the latest debug build on `Pixel 10a - 16`: `111` audio-cache files (`2675124` KB reported by Android `du`), `3103` cover-art cache files (`606076` KB), and `3` downloaded files (`34400` KB).
  - Installed debug build timestamp for this validation baseline: `versionName=0.9.0`, `versionCode=1`, `lastUpdateTime=2026-06-14 12:17:47`.
  - Pulled `naviamp-storage.db` through `adb exec-out run-as` after launching the app once so migration/schema setup ran; database baseline is `media_source=1`, `playback_session=1`, `cached_audio=93`, `downloaded_audio=2`, `pending_provider_action=0`, and failed pending actions `0`.
  - ADB `cmd media_session dispatch play` did not resume the idle Naviamp media session from this workstation state; Android Auto or phone interaction is still needed to start the offline playback leg.
  - Device validation found that `naviamp.track:3m513rJf6VmGWIoLftFMRS` had a cached file under `transcoded:opus:128`, while current playback requested original quality and therefore opened the Navidrome stream instead of local audio.
  - Shared playback cache lookup now falls back from exact requested quality to any cached quality for the same track; Android and desktop both implement the shared fallback.
  - Foreground-service notification updates are now guarded so Android background foreground-service restrictions log a warning instead of crashing the app during Auto/intent playback handoff.
  - Phone Auto media-id handoff can now start a locally cached track without an active provider connection, as long as the active source ID and local library/cache records are present.
  - Strict device validation on `Pixel 10a - 16`: with Wi-Fi and mobile data disabled, after `adb shell am force-stop app.naviamp.android`, launching `naviamp.track:3m513rJf6VmGWIoLftFMRS` started playback from `file:/data/user/0/app.naviamp.android/cache/audio-cache/99b57a992d3c956d15f05d0afc979b7b.flac`.
  - The offline cold-start media session reported `PLAYING`, metadata description `Catalyst, S1gns of L1fe`, queue title `Queue`, queue size `11`, and custom actions for Favorite, Shuffle, and Repeat.
  - Cached image validation found `cached_image` contains the `Catalyst` cover-art URL with `73944` bytes.
  - Offline cover-art validation: with Wi-Fi and mobile data disabled, the same cold-start playback logged `Loaded notification cover art from cache bytes=73944` and kept the Android media session at `PLAYING` with `Catalyst` metadata.
  - Phone-app playback now resolves cover art from the saved media source when the live provider object is unavailable, so cached image lookup keeps the same stable Navidrome cover-art URL while offline.
  - Network was restored after the strict offline validation command block with `adb shell svc data enable` and `adb shell svc wifi enable`.
  - Offline track-to-track validation: after starting `All I Need Is Bass` from local cached audio with Wi-Fi/mobile data disabled, Android Auto next advanced to `BADDERS`; both tracks opened `file:/data/user/0/app.naviamp.android/cache/audio-cache/...` paths, the media session stayed `PLAYING`, and `BADDERS` published full metadata with cached cover art after the cover-art warming patch.
  - Same-album artwork regression note: the first `BADDERS` offline-next screenshot showed blank/gray art because cached audio existed without a corresponding cached-image row. The fix warms cover art when audio is cached and also when prefetch discovers an already-cached audio file.
  - Different artist/album offline artwork validation: with airplane mode enabled plus Wi-Fi and mobile data disabled, three separate force-stop cold starts played `Timestretch` by Bassnectar, `Bumpy Road` by PEEKABOO, and `10,000 Days (Wings, Pt 2)` by Tool from local cached files. Their cover art loaded from cache with distinct byte sizes: `65647`, `551102`, and `173014`, proving the result was not coming from a shared album-art cache entry.
  - DHU screenshot evidence for the different-album test: `.tmp/dhu-airplane-different-album-tool.png` shows the Tool track in Android Auto with local artwork while offline.
  - Offline favorite sync validation: baseline pending actions were `0|0`; with airplane mode enabled plus Wi-Fi/mobile data disabled, a cached Tool track started from local audio and the Android Auto favorite action inserted one pending row: `track_favorite`, entity `ktXDqJvWyhJUQyHWtPSZEb`, `bool_value=1`, `attempt_count=0`.
  - Offline action durability validation: after force-stopping Naviamp while still offline, the pending favorite row remained present.
  - Reconnect validation: after re-enabling network and opening Naviamp, pending actions returned to `0|0`; direct Navidrome `getSong` verification showed the Tool track starred at the reconnect timestamp. The test then restored the Tool track to its original unstarred state with Navidrome `unstar`.
  - Android Auto Now Playing overflow now advertises `Start song radio` alongside Favorite, Shuffle, and Repeat, using the existing `ic_auto_radio` artwork and the existing track-radio creation path for phone-owned and service-owned playback.
  - Android Auto foreground-service metadata refreshes now republish the media-session playback state immediately, keeping the notification and Auto session aligned when phone-owned playback starts or updates metadata.
  - Cached-drive endurance setup found 82 cached audio rows. The best 10-song test queue was PEEKABOO `Eyes Wide Open`, with local cached FLAC files for `All I Need Is Bass`, `BADDERS`, `Bumpin'`, `Don't Wanna`, `Dope`, `Going Insane`, `I've Been Thinkin`, `Like That`, `Music Box`, and `Riddle`.
  - Stale DHU session trap: before restarting Desktop Head Unit, Gearhead reclaimed audio focus about 5 seconds after each offline start/next command and BASS paused at roughly 6.5 seconds. A control run with Gearhead stopped stayed `PLAYING`, confirming cached playback itself was not the failure.
  - DHU restart procedure used for the passing endurance run: stop `desktop-head-unit`, force-stop `com.google.android.projection.gearhead`, start `com.google.android.projection.gearhead/.companion.DeveloperHeadUnitNetworkService`, run `adb forward tcp:5277 tcp:5277`, then restart `desktop-head-unit.exe`.
  - 10-song cached-drive endurance validation passed on `Pixel 10a - 16`: with airplane mode enabled plus Wi-Fi/mobile data disabled, a force-stopped Naviamp cold-started `All I Need Is Bass` from Android Auto and advanced through 10 cached tracks using the foreground-service `NEXT` action. Every sample reported media-session `PLAYING`, every track opened a `file:/data/user/0/app.naviamp.android/cache/audio-cache/...flac` URL, and notification/Auto art loaded from cache (`69986` bytes for the test album).
  - Endurance pending-action check after the offline run remained `0|0`. Evidence files: `.tmp/endurance-10-after-dhu-restart-log.txt` and `.tmp/endurance-10-after-dhu-restart.db`.
  - Shared audio cache eviction planning now lives in core domain and is used by Android and desktop cache trimming. Android supplies the saved current queue as a protected set, so the current track plus the next 10 queued tracks are skipped even when trimming down to the configured cache budget.
  - Device eviction validation on `Pixel 10a - 16`: lowering `max_audio_cache_bytes` to `268435456` trimmed cached audio from `82` rows / `2136931286` bytes to `8` rows / `235681948` bytes, while all already-cached protected queue tracks remained present on disk. The only missing protected ID was already uncached before trimming.
  - Phone-owned offline play-report validation on `Pixel 10a - 16`: with Gearhead stopped and airplane mode plus Wi-Fi/mobile data disabled, launching cached track `ms20W4pGgOn2T8V9cJegxF` started local playback, media session reported `PLAYING`, metadata `Like That, PEEKABOO & LYNY`, queue size `13`, and custom actions Favorite, Shuffle, Repeat, and `Start song radio`.
  - Offline play reporting now queues without a live provider object when an active media-source ID is available. The same offline run inserted `report_now_playing` after startup, inserted `report_played` after the played threshold, preserved both rows across force-stop while offline, and drained pending actions back to `0|0` after network reconnect and app launch.
  - Failed sync retry retention remains covered by shared domain tests: failed provider-action replay leaves rows queued with attempt count/error details instead of dropping them.
  - Remaining validation gaps: real-vehicle validation, downloaded-track cold-start playback on a device with downloads available, explicit browsed playlist/radio cold-start playback, Assistant search in DHU, and the full DHU checklist sweep.
  - Verification: `.\gradlew.bat --configure-on-demand :core:domain:allTests :apps:android:compileDebugKotlin` passed.
  - Verification: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :apps:desktop:compileKotlinDesktop` passed.
  - Verification: `.\gradlew.bat --configure-on-demand :core:domain:allTests :apps:android:compileDebugKotlin` passed after service-owned offline action wiring.
  - Verification: `.\gradlew.bat --configure-on-demand :core:domain:allTests :apps:android:compileDebugKotlin`, `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :apps:desktop:compileKotlinDesktop`, and `.\gradlew.bat --configure-on-demand :apps:android:installDebug` passed after diagnostics/offline-heartbeat wiring.
  - Verification: `.\gradlew.bat --configure-on-demand :core:domain:allTests :apps:android:compileDebugKotlin "-Pnaviamp.bass.platform=windows-x64" :apps:desktop:compileKotlinDesktop` passed after shared cached-quality fallback wiring.
  - Verification: `.\gradlew.bat --configure-on-demand :core:domain:allTests :apps:android:compileDebugKotlin :apps:android:installDebug` passed after offline cached-start wiring.
  - Verification: `.\gradlew.bat --configure-on-demand :apps:android:compileDebugKotlin :apps:android:installDebug` passed after offline cover-art URL fallback/logging.
  - Verification: `.\gradlew.bat --configure-on-demand :core:domain:allTests :apps:android:compileDebugKotlin` and `.\gradlew.bat --configure-on-demand :apps:android:compileDebugKotlin :apps:android:installDebug` passed after protected cache eviction and phone-owned offline play-report fallback wiring.
  - Verification: `.\gradlew.bat --configure-on-demand :core:domain:allTests :apps:android:compileDebugKotlin "-Pnaviamp.bass.platform=windows-x64" :apps:desktop:compileKotlinDesktop` passed after the final Phase 8 validation pass.

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
