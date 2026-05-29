# Desktop Main Reduction Worksheet

This tracks the follow-up branch after the shared-core extraction and first desktop split. The goal is to keep reducing the desktop app composition file while continuing to move duplicated desktop/Android product behavior into shared modules when it is not platform-specific.

Branch: `codex/desktop-main-reduction`

## Goals

- [ ] Keep `apps/desktop/.../app/Main.kt` as a small desktop entry/window shell.
- [ ] Reduce `DesktopNaviampApp.kt` by extracting cohesive orchestration into feature controllers.
- [ ] Check desktop/Android duplication before extracting platform-local helpers.
- [ ] Move shared Navidrome/provider, playback, navigation, or UI rules into common/provider modules when both apps need them.
- [ ] Keep desktop and Android builds green after each meaningful slice.

## Work Done

- [x] Merged `codex/shared-core-extraction` into `main`.
- [x] Created `codex/desktop-main-reduction`.
- [x] Split the desktop entry point back into a small `Main.kt`.
  - `Main.kt` now owns app/window setup, window-size persistence, title bar configuration, shutdown, and wiring into `NaviampApp`.
  - The large Compose app body now lives in `DesktopNaviampApp.kt`.
- [x] Shared Navidrome connection preparation between desktop and Android.
  - Added `prepareNavidromeConnection` in `providers/navidrome`.
  - Desktop and Android now share saved-credential reuse, password connection creation, and native-token refresh/fallback behavior.
  - Moved the connection-preparation tests to provider common tests.

## Planned Work

- [x] Extract desktop connection/session startup orchestration from `DesktopNaviampApp.kt`.
  - `connectToServer`
  - saved-session restore wiring
  - connection success/error state updates
  - post-connect refresh triggers
- [x] Extract active connection reset/delete helpers.
  - `clearActiveConnectionState`
  - `resetDatabase`
  - `deleteConnection`
  - shared deleted media-source active-connection update rules through `core/domain`
- [x] Extract media-detail playback actions.
  - album/detail play helpers
  - search/related/popular track play helpers
  - metadata update, favorite, and rating helpers
  - shared pure playback selection and track metadata update rules through `core/domain`
- [x] Extract desktop download orchestration from `DesktopNaviampApp.kt`.
  - track/album/playlist download helpers
  - download removal
  - downloaded-track playback setup
- [x] Extract desktop playlist orchestration from `DesktopNaviampApp.kt`.
  - playlist refresh and selected-playlist detail refresh
  - add-to-playlist and add-to-queue actions
  - playlist play/open/rename/delete actions
  - shared playlist state transitions between desktop and Android through `core/domain`
  - left smart-playlist credential refresh in app composition until connection auth is split further
- [x] Start splitting desktop `PlaylistEngine` into shared and platform layers.
  - shared queue/session/repeat/prepared-next coordinator now lives in `core/domain`
  - desktop keeps playback-target resolution, cache/sidecar prep, local tag reading, and Stats for Nerds cache runtime details
- [x] Move Android activity playback queue control onto the shared coordinator.
  - activity queue append, playback start queue selection, shuffle toggling, and prepared-next tracking now use `PlaybackQueueController`
  - Android still keeps platform playback URL/cache/sidecar work and foreground-service Auto queue handling locally
- [x] Move Android foreground-service Auto queue control onto shared queue helpers.
  - service-owned adjacent navigation, repeat-current replay, shuffle, hydration, and saved-session queue sync now use `PlaybackQueueController`
  - media-session publishing and notification behavior remain Android-local
- [x] Extract shared playback target and sidecar planning.
  - engine/provider start-position split is shared for desktop and Android track playback
  - sidecar prep track window and lyrics-load decision are shared through `core/domain`
- [x] Extract desktop smart-playlist auth/save orchestration from `DesktopNaviampApp.kt`.
  - desktop credential refresh remains desktop-local
  - provider save/update/load status rules stay shared in `core/domain`
- [x] Extract remaining desktop radio wrappers from `DesktopNaviampApp.kt`.
  - library/genre/decade/popular/track radio calls now delegate through `DesktopRadioController`
  - random album, artist, and album radio seed lookup now lives with desktop radio orchestration
  - shared radio request/seed selection rules remain in `core/domain`
- [x] Move shared playlist and queue planning rules out of desktop helpers.
  - home playlist ordering
  - playlist detail refresh shaping
  - playlist detail refresh interval
  - create playlist/add missing track mutations
  - queue append status and de-duplication planning
- [x] Move shared connection form/provider status rules out of desktop helpers.
  - connection form validation
  - Navidrome TLS form normalization
  - Navidrome success status text
- [x] Extract app-state side effects where possible.
  - shared search result/status loading rules
  - shared library sync status, auto-sync gate, and freshness polling interval
  - shared stats refresh route/interval rules
  - shared now-playing sidecar status, lyrics, waveform, and queue prep decisions
- [x] Extract shared internet-radio playback helpers.
  - station-to-track shaping and stream-title metadata updates now live in `core/domain`
  - recent internet-radio station de-duplication/limits are shared by desktop and Android
- [x] Extract desktop artist-detail orchestration from `DesktopNaviampApp.kt`.
  - artist detail navigation, load/fallback, similar-artist lookup, popular-track lookup, and external-link handling now live in `DesktopArtistController`
  - status and fallback rules remain shared with Android through `core/domain`
- [x] Extract desktop library orchestration from `DesktopNaviampApp.kt`.
  - snapshot refresh, pagination, letter jumps, sync/freshness polling, and cache/library clearing now live in `DesktopLibraryController`
  - shared auto-sync, freshness, status, and paging rules remain in `core/domain`
- [x] Extract desktop internet-radio orchestration from `DesktopNaviampApp.kt`.
  - station refresh, recent-station persistence, live playback setup, stream metadata, station save/update, and station delete now live in `DesktopInternetRadioController`
  - shared station-to-track, metadata update, and recent-station rules remain in `core/domain`
- [x] Extract desktop playback-control orchestration from `DesktopNaviampApp.kt`.
  - shuffle/repeat controls, session position saves, seek handling, previous/next eligibility, now-playing reports, and played reports now live in `DesktopPlaybackController`
  - shared playback control, seek, progress-save, and report-submission rules remain in `core/domain`
- [x] Extract desktop album-detail orchestration from `DesktopNaviampApp.kt`.
  - album detail route selection, provider load, local-library fallback, and track-to-album navigation now live in `DesktopAlbumController`
  - shared album fallback and status rules remain in `core/domain`
- [x] Fold remaining desktop connection startup into `DesktopConnectionLifecycleController`.
  - form validation, TLS/display-name preparation, connect coroutine, saved-session restore, post-connect refresh triggers, and error handling now live with connection lifecycle orchestration
  - shared connection form validation, TLS normalization, success status, and restore planning remain in common/provider helpers
- [x] Share recent generated-radio stream ordering.
  - recent radio stream de-duplication/limits now live in `core/domain`
  - desktop and Android foreground-service radio history use the shared helper
- [x] Extract desktop home-content orchestration from `DesktopNaviampApp.kt`.
  - async `HomeService` loading, home status, source id, recent radio inputs, and desktop cache repository wiring now live in `DesktopHomeController`
  - `HomeService` remains shared between platforms
- [x] Extract desktop now-playing sidecar orchestration from `DesktopNaviampApp.kt`.
  - waveform/audio-tag/lyrics analysis, related-track loading, and JVM cover-art preloading now live in `DesktopNowPlayingController`
  - cover-art preload queue-window planning is shared through `core/domain`
- [x] Extract desktop search orchestration from `DesktopNaviampApp.kt`.
  - query persistence, debounce, disconnected/blank-query handling, cache-backed search, and search status updates now live in `DesktopSearchController`
  - result wrapping, status text, debounce timing, and query normalization remain shared through `core/domain`
- [x] Move shared search session orchestration out of `DesktopSearchController`.
  - normalized-query handling, disconnected/blank-query behavior, loading/searching state, and result/status application now live in common `SearchSessionController`
  - desktop injects settings persistence plus `DesktopCache.search`; Android injects its `MediaProvider.search` flow
- [x] Share playlist-detail auto-refresh orchestration.
  - selected playlist/provider gating and refresh-loop error swallowing now live in common playlist helpers
  - desktop keeps route gating and desktop state application, while Android keeps its content/navigation state application
- [x] Share data-maintenance status text.
  - cache clear, library index clear, and database reset status messages now come from common app helpers
  - platforms still own storage deletion, playback stopping, file-cache cleanup, and UI state resets
- [x] Share playback-settings change planning.
  - engine capability normalization and lyrics-sidecar reload decisions now live in common settings helpers
  - desktop and Android keep their own persistence and lyrics reload/cache invalidation wiring

## Shared-Code Watchlist

- Connection preparation is now shared through `providers/navidrome`.
- Connection form validation and deleted-source active-connection updates are now shared through `core/domain`.
- Navidrome TLS form normalization and connection success status text are now shared through `providers/navidrome` and used by both desktop and Android connection flows.
- Media action rules for track selection, favorite/rating provider updates, and propagating updated track metadata through search, album, list, and queue state are now shared through `core/domain`.
- Playback button/save-position decisions, pending-seek defaults, now-playing heartbeat cadence, visualizer frame cadence, playback target start-position planning, sidecar prep planning, and playback queue controller state are now shared through `core/domain`; platforms keep source-specific seek replay wrappers and platform cache/sidecar playback plumbing.
- Playlist ordering, playlist refresh shaping, selected-playlist state updates, recent-playlist updates, playlist delete state updates, create-or-add track mutations, queue append planning, and playlist preload planning are now shared through `core/domain`.
- Search result wrapping/status text and storage stats refresh route/interval decisions are now shared through `core/domain`.
- Library sync status text, initial auto-sync decision, and library freshness polling cadence are now shared through `core/domain`.
- Now-playing sidecar type keys, lyrics loading/error rules, waveform status labels, online-lyrics fetch decisions, and sidecar queue filtering are now shared through `core/domain`.
- Internet-radio station track shaping, metadata title updates, and recent-station ordering/limits are now shared through `core/domain`.
- Artist detail fallback, status text, popular-track update, and similar-artist update rules are shared through `core/domain`; platforms keep route and external-link handling locally.
- Library auto-sync gating, freshness polling updates, sync status text, and paging limits are shared through `core/domain`; platforms keep their storage-specific sync runners.
- Internet-radio live playback is desktop-local, while station-to-track shaping, stream-title handling, and recent-station ordering stay shared through `core/domain`.
- Playback button rules, seek planning, playback-position save decisions, and play-report thresholds are shared through `core/domain`; desktop now keeps only the platform wiring in `DesktopPlaybackController`.
- Album detail fallback and load-status rules are shared through `core/domain`; desktop route wiring now lives in `DesktopAlbumController`.
- Desktop connection startup still owns desktop-specific state wiring, but pure validation/TLS/status/restore rules stay shared through `core/domain` and `providers/navidrome`.
- Recent generated-radio stream ordering/limits are shared through `core/domain` alongside the existing recent-stream creation/action helpers.
- Home content composition remains shared through `HomeService`; desktop now keeps platform state wiring in `DesktopHomeController`.
- Now-playing waveform/lyrics status rules, online-lyrics decisions, sidecar prep filtering, and cover-art preload queue-window planning are shared through `core/domain`; desktop keeps JVM audio-tag/waveform/cache plumbing in `DesktopNowPlayingController`.
- Search query normalization, debounce timing, disconnected/blank-query handling, loading/searching state, and result wrapping/status rules are shared through `core/domain`; desktop keeps settings/cache wiring in `DesktopSearchController`, and Android uses the same `SearchSessionController`.
- Playlist detail refresh shaping, selected-playlist state updates, refresh cadence, and auto-refresh loop orchestration are shared through `core/domain`; platforms inject their selected route/state and provider refresh application.
- Cache clear, library index clear, and database reset status text is shared through `core/domain`; storage deletion and platform UI reset wiring remain local.
- Playback settings normalization and lyrics-sidecar reload decisions are shared through `core/domain`; platforms keep settings persistence and UI/cache invalidation wiring.
- Remaining connection startup still differs by platform because desktop owns BASS/JVM TLS defaults, `DesktopCache`, window route state, and playlist engine restoration, while Android owns foreground service/playback runtime, `AndroidStorage`, and activity navigation state.
- Before extracting each helper from `DesktopNaviampApp.kt`, compare Android equivalents and move pure request/status/state-transition rules into `core/domain`, `core/ui`, or `providers/navidrome` instead of creating a new desktop-only duplicate.

## Verification

- [x] `.\gradlew.bat "-Pnaviamp.bass.platform=windows-x64" :apps:desktop:compileKotlinDesktop`
- [x] `.\gradlew.bat "-Pnaviamp.bass.platform=windows-x64" :providers:navidrome:allTests :apps:desktop:desktopTest :apps:desktop:compileKotlinDesktop :apps:android:assembleDebug`
- [x] `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`
- [x] `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:desktopTest`
- [x] `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:allTests :apps:desktop:compileKotlinDesktop :apps:android:assembleDebug`
- [x] `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:allTests :providers:navidrome:allTests :apps:desktop:desktopTest :apps:android:assembleDebug`
- [x] `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:allTests :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`
- [x] `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:allTests :apps:desktop:compileKotlinDesktop`
- [x] `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:allTests :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`
- [x] `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:allTests :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`
- [x] `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:allTests :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`
- [x] `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:allTests :apps:desktop:compileKotlinDesktop`
- [x] `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`
- [x] `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:allTests :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`
- [x] `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:allTests :apps:desktop:compileKotlinDesktop`
- [x] `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:allTests :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`
- [x] `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:allTests :apps:desktop:compileKotlinDesktop`
- [x] `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:allTests :apps:desktop:compileKotlinDesktop`
- [x] `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:allTests :apps:desktop:compileKotlinDesktop`
- [x] `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:allTests :apps:desktop:compileKotlinDesktop`
- [x] `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:allTests :apps:desktop:compileKotlinDesktop`

## Follow-Up Ideas

- [ ] Reminder: revisit the artist-selection feature idea after this branch. Capture the exact workflow before implementation so it can be designed cleanly across desktop and Android.
