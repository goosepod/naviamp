# Shared Architecture Cleanup

Status: Discovery complete; first implementation slice selected

Branch: `codex/shared-architecture-cleanup`

## Why this exists

Recent feature work touched too many files for behavior that should have one shared path. Favorites, ratings, metadata edits, queue saves, sleep timer behavior, mix builders, and playback controls all exposed the same problem: platform code still owns too much business logic and app orchestration.

The most visible symptom is `DesktopNaviampApp.kt` hitting the JVM method-size limit. The deeper problem is that the app has too many duplicated Android/Desktop controller paths and too much state/render wiring in platform roots.

## Ground rules

- Pause new feature work until this checklist is substantially complete.
- Prefer shared/domain call paths whenever logic does not require platform APIs.
- Platform code should adapt OS, lifecycle, storage, audio backend, notifications, and filesystem concerns only.
- Keep behavior unchanged while refactoring unless a checklist item explicitly calls out a product correction.
- Verify each extraction before stacking more changes.
- Build in the Unix style: small focused services with one clear job, composed into larger application flows.
- Shared services should compose other shared services where possible instead of duplicating orchestration in platform code.
- Files prefixed with `Desktop` or `Android` should be thin by default. If one grows, it needs a written reason.

## Regression Notes

- [ ] Lyrics offset is no longer available in packaged Mac and Windows builds after the recent cleanup work.
- [x] Restore the small circular handle at the end of the volume bar in packaged Mac and Windows builds.
  - Fixed in shared UI by making the volume thumb draw as a larger high-contrast knob with a halo and inner marker.

## Feature Notes

- [x] Add shared Related-tab track menu actions on the now-playing screen: `Play Next` and `Add to Queue`.
  - This must be implemented through shared UI/domain action plumbing because the Related tab and queue intents are not platform-specific.
  - Implemented through shared now-playing row action specs and shared playback queue mutations, with Android/Desktop only resolving the selected related item to a track.

## Success criteria

- Platform-prefixed files are thin adapters, not business-logic owners.
- Shared behavior lives in platform-agnostic services with focused responsibilities and explicit inputs/outputs.
- Larger application behavior is built by composing smaller shared services, not by copying logic into Android and Desktop routes/controllers.
- Provider calls, storage updates, playback decisions, queue operations, media metadata mutations, radio/mix generation, connection orchestration, diagnostics mapping, and settings decisions all have shared service entry points unless they truly require platform APIs.
- Android and Desktop use the same call path for the same user intent. Divergence must be limited to OS/lifecycle/UI shell differences.
- Adding or changing a shared behavior should usually touch shared domain/app/UI code plus at most a thin platform adapter, not parallel Android/Desktop implementations.
- Root app files and route composables are small enough that compiler method-size limits are not a risk. `DesktopNaviampApp.kt` should not need Compose compiler bytecode workarounds to build.
- The codebase has clear dependency direction: platform adapters call shared services; shared services do not reach upward into platform modules.
- The service layer itself stays modular: small services can be tested independently and composed into larger coordinators.

## Phase 1: Map and Freeze

- [x] Inventory every `Android*` and `Desktop*` file and classify its responsibilities.
  - Discovery started. No implementation changes should happen during this phase.
  - Initial scan found 147 platform-prefixed Kotlin files.
  - Android: 55 files.
  - Desktop: 92 files.
- [x] Inventory duplicated platform controller, route, shell, cache, settings, playback, radio, playlist, library, diagnostics, and media responsibilities.
  - Initial normalized-name scan found 32 Android/Desktop pairs with matching names after removing the platform prefix.
  - These pairs are not automatically wrong; they are the first places to inspect for duplicated orchestration versus legitimate platform adapters.
- [x] Tag each responsibility as shared domain, shared UI, platform adapter, or platform lifecycle.
- [x] Mark every platform-prefixed file as one of:
  - thin adapter already
  - candidate for extraction
  - temporary coordinator to split
  - legitimate platform boundary
- [x] Identify direct mutations of shared media state from platform files for the first five traced user intents.
- [x] Identify direct provider mutation calls from platform files for the first five traced user intents.
- [x] Identify places Android and Desktop rebuild equivalent UI/domain models separately at the bucket level.
- [x] Identify places Android and Desktop perform equivalent async orchestration separately at the bucket level.
- [x] Document the desired dependency direction for app, domain, provider, storage, UI, and platform modules.
- [x] Define a short decision rule for future work: shared service first, platform adapter second.

### Phase 1 Discovery Log

This section records discovery findings only. It should not imply implementation approval until the map is complete.

#### Platform-prefixed package counts

| Area | File count | Initial read |
| --- | ---: | --- |
| android/playback | 20 | Heavy Android lifecycle/audio/session surface. Likely mix of legitimate adapters and shared playback orchestration candidates. |
| desktop/playback | 18 | Heavy desktop playback surface. Likely mix of BASS/JVM adapters and shared playback orchestration candidates. |
| desktop/cache | 14 | Mostly storage adapter shapes, but should be checked for duplicated repository behavior. |
| android/storage | 13 | Mostly storage adapter shapes, but should be checked against desktop/cache one-to-one pairs. |
| desktop/app | 12 | High-risk orchestration area; includes `DesktopNaviampApp.kt`. |
| android/app | 9 | High-risk orchestration area; includes shell/state/support/dependencies. |
| desktop/media | 9 | Includes media actions and route/detail UI. Metadata/action logic likely not thin enough. |
| desktop/connection | 6 | Mix of form UI, session, lifecycle; likely shared connection coordinator candidate. |
| desktop/radio | 5 | Heavy radio orchestration; has Android counterpart. |
| desktop/playlists | 5 | Playlist orchestration and UI; has Android counterpart. |
| desktop/downloads | 5 | Desktop-specific filesystem/UI plus possible shared download mutations. |
| desktop/settings | 4 | Store/panel/aliases; distinguish persistence adapter from shared settings decisions. |
| desktop/library | 3 | Sync/controller/panel; has Android counterpart. |
| android/media | 2 | Media actions and artist controller; has desktop counterpart. |
| android/library | 2 | Sync/controller; has desktop counterpart. |
| android/radio | 2 | Radio controller/playlist; has desktop counterpart. |
| desktop/search | 2 | Search controller/panel; has Android controller counterpart. |
| desktop/stats | 2 | Diagnostics/stats mapping may have Android counterpart. |
| desktop/navigation | 2 | Likely UI/platform navigation adapter. |
| desktop/lyrics | 2 | Audio tag and LRCLIB client; has Android counterpart. |
| android/connection | 1 | Connection controller; desktop has multiple connection files. |
| android/diagnostics | 1 | Has desktop stats/diagnostics counterpart. |
| android/discovery | 1 | Popular tracks HTTP client; has desktop counterpart. |
| android/downloads | 1 | Download controller; desktop has multiple download files. |
| android/lyrics | 1 | LRCLIB client; has desktop counterpart. |
| android/playlists | 1 | Playlist controller; desktop has controller plus UI/mutations. |
| android/settings | 1 | Settings store; has desktop counterpart. |
| desktop/discovery | 1 | Popular tracks HTTP client; has Android counterpart. |
| desktop/home | 1 | Home controller; Android home loading may be in app shell/support. |
| desktop/theme | 1 | Likely platform UI styling. |

#### Normalized Android/Desktop pairs found

The first pass found 32 matching pairs after stripping `Android` and `Desktop` prefixes:

- `AppConstants`
- `AppDependencies`
- `AppEffects`
- `AppSupport`
- `ArtistController`
- `AudioStore`
- `AudioTagReader`
- `AudioWaveformAnalyzer`
- `AudioWaveformStore`
- `BassAudioBackend`
- `BassPlaybackEngine`
- `FileTreeCleaner`
- `LibraryController`
- `LibraryIndexStore`
- `LibrarySync`
- `LrclibLyricsClient`
- `LyricsOffsetStore`
- `LyricsSidecarStore`
- `MediaActionsController`
- `MediaSourceStore`
- `ObjectByteStore`
- `PlaybackAudioAssets`
- `PlaylistEngine`
- `PlaylistsController`
- `PopularTracksHttpClient`
- `ProviderResponseStore`
- `RadioController`
- `SearchController`
- `SettingsStore`
- `SidecarStatusStore`
- `StorageDependencies`
- `StorageMaintenanceStore`

Initial classification rule:

- Storage stores, native audio backends, JNI bindings, platform filesystem, notifications, activity/service lifecycle, and window UI are likely legitimate platform adapters.
- Controllers, mutation helpers, route orchestration, status/error decisions, provider call sequencing, queue construction, refresh behavior, and state reducers are likely shared-service candidates unless proven otherwise.

#### Normalized pair responsibility matrix

Labels:

- `shared-service candidate`: mostly shared behavior hiding in platform files.
- `legitimate platform adapter`: mostly OS, storage, native, lifecycle, or UI boundary code.
- `mixed, split required`: contains both platform boundary code and shared orchestration/policy.
- `already thin enough`: acceptable adapter shape for now; keep watching as surrounding code changes.

This table labels the 32 normalized Android/Desktop pairs. The labels are discovery output only; implementation should wait until the remaining Phase 1 intent-flow tracing is complete.

| Pair | Phase 1 label | Keep platform-specific | Extract or standardize shared behavior |
| --- | --- | --- | --- |
| `AppConstants` | mixed, split required | Android Auto/session/download limits, desktop window/progress/preload constants, platform UI intervals. | Shared radio counts/thresholds, similar-radio expansion counts, popular-track seed limits, library page defaults, previous/restart thresholds if used by both playback stacks. |
| `AppDependencies` | mixed, split required | Context-bound Android runtime, desktop playback factory, concrete storage construction, close/dispose behavior. | Dependency graph composition for popular tracks, similar artists, sidecar services, waveform service, playback assets, and library sync should be assembled through a shared dependency builder where possible. |
| `AppEffects` | mixed, split required | Compose effect wiring, Android lifecycle persistence, desktop JVM cover-art byte loader, platform dispose behavior. | Now-playing heartbeat, visualizer polling decisions, equalizer/crossfade application decisions, library freshness timer decisions, storage stats refresh gating. |
| `AppSupport` | mixed, split required | Android context/network checks, Android Auto seek normalization, desktop route enum conversion and JVM-only stats map. | Shell model construction, now-playing UI model mapping, route restoration decisions, provider track fallback helpers, directory-cleaning status policy where not OS-specific. |
| `ArtistController` | shared-service candidate | Android external URL intent, platform navigation/state assignment, desktop route callbacks. | Artist detail loading, similar artist search, popular track loading, album detail fallback, album-track loading, artist-album radio start planning. |
| `AudioStore` | legitimate platform adapter | Database/file locations, Android file handles, desktop paths, platform filesystem moves/deletes. | Cache key helpers, downloaded-audio row mapping, trim policy, max-byte enforcement decisions. |
| `AudioTagReader` | legitimate platform adapter | JVM/Android tag-reader implementation and local file access. | Parsed tag model and replay-gain/lyrics interpretation already belongs in shared `AudioMetadataSidecarService`; keep new parsing policy there. |
| `AudioWaveformAnalyzer` | legitimate platform adapter | Platform decoding, temp/cache file handling, native analyzer access. | URL-to-local-file safety rules and waveform request policy should remain shared if duplicated grows. |
| `AudioWaveformStore` | legitimate platform adapter | Persistence implementation only. | None beyond shared repository contract. |
| `BassAudioBackend` | legitimate platform adapter | JNI/native binding, library loading, platform audio stream handles. | Stream property parsing and ICY title parsing should be checked for shared helper extraction if it remains duplicated. |
| `BassPlaybackEngine` | mixed, split required | Audio focus, JNI handles, stream creation, native callbacks, platform lifecycle. | Playback request lifecycle policy, equalizer band conversion, replay-gain/log formatting, prepared-next adoption rules, transition decisions, end-of-track command planning. |
| `FileTreeCleaner` | legitimate platform adapter | Recursive filesystem cleanup and platform path APIs. | None beyond shared interface contract. |
| `LibraryController` | shared-service candidate | Scroll/list state, platform cache repository calls, direct platform state writes. | Sync gating, freshness check orchestration, scan-signature update plan, progress/result status decisions, paging limit decisions. |
| `LibraryIndexStore` | legitimate platform adapter | SQLDelight driver usage and platform database access. | Row-to-domain mapping, search normalization, library boundary helpers, popular-track row mapping. |
| `LibrarySync` | mixed, split required | Platform progress object shape, platform repository adapters, Android browse-state loading if API-specific. | Sync loop, provider paging, index upsert plan, progress labels, mark-scan-checked flow, library freshness lookup. |
| `LrclibLyricsClient` | legitimate platform adapter | HTTP client binding and per-platform diagnostics history. | Endpoint labeling, sanitized URL display, shared HTTP call mapping where diagnostics should match. |
| `LyricsOffsetStore` | legitimate platform adapter | Persistence implementation only. | None beyond shared repository contract. |
| `LyricsSidecarStore` | legitimate platform adapter | Persistence implementation only. | None beyond shared repository contract. |
| `MediaActionsController` | shared-service candidate | Android notification update, downloaded file lookup, desktop playback invocation, platform state assignment. | Known-track lookup, playback selection, queue append application, popular-track action planning, add-to-playlist mutation flow, metadata mutation composition and statuses. |
| `MediaSourceStore` | legitimate platform adapter | Persistence implementation only. | Row-to-domain mapping if duplicated. |
| `ObjectByteStore` | legitimate platform adapter | Persistence implementation only. | None beyond shared repository contract. |
| `PlaybackAudioAssets` | already thin enough | Local asset lookup over platform storage/cache APIs. | Shared resolver already exists; keep platform files as narrow adapters. |
| `PlaylistEngine` | mixed, split required | Platform playback engine calls, Android service/session integration, desktop sidecar/prefetch callbacks. | Queue command planning, transition/gapless/crossfade decisions, prepared-next policy, sidecar start policy, previous/next/repeat/shuffle decisions. |
| `PlaylistsController` | shared-service candidate | Dialogs, navigation, recent playlist persistence, playback execution adapter, direct state writes. | Refresh/preload, detail load, save queue, add target, rename/delete, smart playlist save/update/load, selected playlist reducers, home playlist reducers. |
| `PopularTracksHttpClient` | legitimate platform adapter | Ktor client binding and per-platform call history objects. | Endpoint labels, sanitized Deezer URL logic, diagnostics call mapping. |
| `ProviderResponseStore` | legitimate platform adapter | Persistence implementation only. | None beyond shared cache service contract. |
| `RadioController` | shared-service candidate | Playback engine invocation, queue-controller adapter calls, platform state assignment, coroutine scope ownership. | Seeded radio orchestration, refill planning, generated queue append/replace decisions, recent radio stream updates, status/result messages, expansion sequencing. |
| `SearchController` | already thin enough | Query persistence, content state writes, coroutine launch helper. | Existing `SearchSessionController` is the target shape; optionally add shared query clear/update result models later. |
| `SettingsStore` | mixed, split required | Android SharedPreferences, desktop JSON file, desktop window settings. | Settings normalization, EQ preset serialization policy, connection/password reuse decisions, playback/cache/local-data defaults where shared. |
| `SidecarStatusStore` | legitimate platform adapter | Persistence implementation only. | None beyond shared repository contract. |
| `StorageDependencies` | mixed, split required | Context/path/database creation, driver lifecycle, concrete store construction. | Shared storage service composition and repository bundle contracts so app dependencies do not hand-compose equivalent services twice. |
| `StorageMaintenanceStore` | legitimate platform adapter | Persistence deletion/stat implementation. | Cache/local-data clearing result models and status text should live in shared app/cache services. |

Matrix summary:

- Shared-service candidates: `ArtistController`, `LibraryController`, `MediaActionsController`, `PlaylistsController`, `RadioController`.
- Mixed, split required: `AppConstants`, `AppDependencies`, `AppEffects`, `AppSupport`, `BassPlaybackEngine`, `LibrarySync`, `PlaylistEngine`, `SettingsStore`, `StorageDependencies`.
- Legitimate platform adapters with small shared-policy checks: `AudioStore`, `AudioTagReader`, `AudioWaveformAnalyzer`, `AudioWaveformStore`, `BassAudioBackend`, `FileTreeCleaner`, `LibraryIndexStore`, `LrclibLyricsClient`, `LyricsOffsetStore`, `LyricsSidecarStore`, `MediaSourceStore`, `ObjectByteStore`, `PopularTracksHttpClient`, `ProviderResponseStore`, `SidecarStatusStore`, `StorageMaintenanceStore`.
- Already thin enough: `PlaybackAudioAssets`, `SearchController`.

#### Initial orchestration hot spots by size

These files are not automatically wrong because of size, but they are too large to assume they are thin adapters:

| File | Lines | Discovery concern |
| --- | ---: | --- |
| `DesktopNaviampApp.kt` | 1970 | Root state/render/orchestration is too large; already hit JVM method-size pressure. |
| `AndroidAppShell.kt` | 883 | Large platform shell; likely owns app orchestration that should be shared. |
| `AndroidRadioController.kt` | 604 | Radio and generated queue orchestration likely overlaps desktop. |
| `DesktopRadioController.kt` | 465 | Radio and generated queue orchestration likely overlaps Android. |
| `AndroidPlaylistsController.kt` | 395 | Playlist mutation/detail/playback orchestration likely overlaps desktop. |
| `DesktopPlaylistsController.kt` | 378 | Playlist mutation/detail/playback orchestration likely overlaps Android. |
| `AndroidMediaActionsController.kt` | 392 | Media lookup, queue append, metadata mutation, and playlist add logic mixed together. |
| `DesktopMediaActionsController.kt` | 201 | Smaller than Android, but still combines playback selection, queue append, metadata mutation, and state update logic. |
| `DesktopSettingsPanel.kt` | 277 | UI panel, but settings decisions should be checked for shared extraction. |
| `DesktopAppRouteContent.kt` | 244 | Route assembly may duplicate shared UI model construction. |

#### Initial duplicate responsibility candidates

- Media actions: track lookup, selected playback, queue append plans, metadata updates, favorites, ratings, add-to-playlist.
- Playlists: refresh, detail loading, play playlist, save queue as playlist, rename/delete, smart playlist save/update/load.
- Radio: seeded radio, generated queues, refill behavior, artist/album/track/genre mix radio.
- Library: sync/freshness and paging orchestration.
- Search: provider search, status handling, cached state update.
- Connection: form state, saved connection selection, provider lifecycle, credential reuse, reconnect behavior.
- Playback: queue engine behavior, previous/next/seek/shuffle/repeat decisions, sleep timer integration, session restoration.
- Downloads/cache: mutation plans versus platform filesystem/cache adapters.
- Diagnostics/stats: mapping app state to diagnostics views.

#### Provider and status reference scan

Broad text scans found:

- 570 provider-related references in Android/Desktop platform code.
- 274 status-related references in Android/Desktop platform code.

These are intentionally rough counts. They include imports, UI reads, capability checks, and legitimate adapter usage, so they are not defect counts. They do show that provider access and status mutation are spread widely enough to require focused inspection before implementation.

Initial provider/status hot spots:

- `DesktopNaviampApp.kt` directly references provider state and provider calls across connection, radio artwork, mix builders, route content, and capability gating.
- `AndroidRadioController.kt` directly obtains `state.provider` and builds `RadioService` flows in multiple radio/mix paths.
- `DesktopRadioController.kt` appears to own similar radio/mix orchestration through a desktop controller shape.
- `AndroidPlaylistsController.kt` and `DesktopPlaylistsController.kt` both own playlist refresh/mutation/status flows.
- `AndroidMediaActionsController.kt` and `DesktopMediaActionsController.kt` both own media mutation/status/state update paths.
- `DesktopConnectionSession.kt`, `DesktopConnectionLifecycle.kt`, and `AndroidConnectionController.kt` should be inspected together for shared connection lifecycle extraction.

Discovery hypothesis:

- Provider calls should mostly move behind shared coordinators that return typed results.
- Platform code should translate those results into UI state and platform side effects.
- Status strings should be produced by shared result/planner services when they describe shared behavior.
- Platform-only statuses should remain platform-only only when they describe OS/lifecycle/file/dialog behavior.

#### Pair inspection: controller hot spots

This pass inspected representative high-risk Android/Desktop pairs deeply enough to identify where the platform files are doing real platform adaptation versus shared orchestration. The goal is to turn discovery into a work checklist without changing code during Phase 1.

| Pair | Current responsibilities seen | Keep platform-specific | Move shared | Candidate shared pieces | Risk | Suggested order |
| --- | --- | --- | --- | --- | --- | --- |
| `RadioController` | Both sides start seeded radio, refill generated queues, build artist/album/genre/track/popular radio requests, track radio sessions, write status strings, remember recent radio streams, and bridge generated queues into playback. Desktop is a class around `DesktopPlaylistEngine`; Android is top-level functions over `AndroidAppState` and `PlaybackQueueController`. | Playback engine invocation, queue-controller adapter calls, platform state assignment, coroutine scope ownership, UI callbacks. | Seed selection, request construction, refill session plan, generated queue append/replace decisions, recent-radio update data, status/result messages, expansion-count sequencing. | `RadioQueueCoordinator`, `SeededRadioCoordinator`, `RadioRefillPlanner`, `RadioStatusMessages`, `RecentRadioStreamReducer`. | High because it touches playback and generated queue behavior. | After metadata/playlist extraction, because it needs a stable shared queue command surface. |
| `PlaylistsController` | Both sides refresh playlists, preload playlist tracks, load details, play playlists, save queue as playlist, rename/delete playlists, update selected playlist state, update home playlists, and manage pending playback action. Android also owns smart playlist save/update/load; desktop has a separate smart playlist controller. | Dialog choice, route/navigation changes, platform settings persistence for recent playlists, playback adapter call, platform state writes. | Playlist refresh/preload orchestration, queue-to-playlist save, rename/delete mutation flow, smart playlist save/update/load, selected playlist state update, home playlist composition, pending playback gating. | `PlaylistMutationCoordinator`, `PlaylistRefreshCoordinator`, `PlaylistPlaybackPlanner`, `PlaylistSelectionReducer`, `SmartPlaylistMutationCoordinator`. | High because duplicated provider mutations can diverge and stale cache invalidation is easy to miss. | First or second implementation slice; behavior is already partially factored into domain helpers. |
| `MediaActionsController` | Both sides resolve known tracks, select playback queues, append tracks, apply metadata updates, toggle favorites/ratings, update known artist/album collections, and add tracks to playlists. Desktop already wraps `MediaMetadataMutationController`; Android still has extra known-track lookup, downloaded-track lookup, notification favorite updates, and add-to-playlist mutation flow in the platform file. | Android notification update, downloaded file lookup, desktop playback invocation, platform state writes, platform-specific extra collections wiring. | Known-track lookup rules, queue append plan application, popular-track playback/radio intent planning, add-tracks-to-playlist mutation flow, metadata mutation composition and status handling. | `MediaActionPlanner`, `KnownMediaResolver`, `QueueAppendCoordinator`, `AddTracksToPlaylistCoordinator`, expanded `MediaMetadataMutationController` composition. | Medium-high because this is the smell that triggered the cleanup and affects many UI actions. | First implementation slice. |
| `LibraryController` | Both sides start sync, check freshness, apply shared library freshness/status helpers, mark scan checked, and write progress/status. Desktop additionally handles paging, letter jumps, cache clearing, and Compose list scrolling. Android updates home artists during sync progress. | Scroll/list state, platform cache clearing repository calls, platform progress rendering, Android home-progress updates if UI-specific. | Sync gating, freshness check orchestration, scan-signature update plan, progress/result status decisions, library snapshot limit decisions where not UI-specific. | `LibrarySyncCoordinator`, `LibraryFreshnessCoordinator`, `LibraryPagingPlanner`, `LibraryMaintenanceCoordinator`. | Medium because existing shared helpers reduce risk, but sync progress spans provider/storage/UI. | Early once media/playlist proves the pattern. |
| `SearchController` | Both sides already use shared `SearchSessionController`. Desktop persists query settings and clears UI state. Android updates content state/tracks and launches search from scope. | Query persistence, route/content state writes, coroutine launch helper. | Possibly shared query update/clear result models; otherwise keep as example of acceptable thin adapters. | Existing `SearchSessionController`, optional `SearchStateReducer`. | Low. | Use as reference pattern, not first extraction. |

#### Cross-cutting discovery from inspected pairs

- The codebase already has useful shared helpers in `core/domain`, but many platform files still compose them manually. The cleanup should not only create new helpers; it should move orchestration into shared coordinators that compose the existing helpers.
- Status strings are often emitted directly from Android/Desktop controllers even when the behavior is the same. Shared coordinators should return typed results with status data instead of asking each platform to recreate wording.
- Provider response cache invalidation is duplicated in playlist and media paths. A shared mutation coordinator should own the invalidation and refresh plan.
- Playback actions are frequently mixed with provider loading. Shared services should produce playback commands or queue plans, while platform adapters execute the command through the local playback engine/session.
- Android's top-level function style and desktop's class-controller style hide duplication. Matching by user intent is more useful than matching by class shape.
- Search is the current positive example: a shared session controller handles the behavior and the platform code mostly adapts state.
- A controller-only scan found 722 references matching provider access, status assignment, state mutation, or setter calls. This count is rough and includes legitimate adapter work, but it confirms the next discovery pass should trace intent flows instead of only matching file names.

#### Unmatched platform-prefixed files: first read

The normalized-pair scan also found platform-prefixed files without a same-name Android/Desktop counterpart:

- Android-only: 23 files.
- Desktop-only: 60 files.

These are not automatically wrong. Desktop naturally has window chrome, panels, menus, and tests; Android naturally has foreground services, notification controls, Auto integration, and activity/service lifecycle. Still, unmatched files are important because duplicated behavior can hide under different names.

Initial buckets to inspect:

| Bucket | Files observed | Initial read |
| --- | --- | --- |
| App shell and route orchestration | `AndroidAppShell`, `AndroidAppState`, `DesktopNaviampApp`, `DesktopAppActions`, `DesktopAppRouteContent`, `DesktopAppControllerEffects`, `DesktopAppDialogs`, `DesktopAppMenus`, `DesktopAppNavigation` | High-priority hidden duplication area. Some UI/window/menu code is platform-specific, but route state, provider lifecycle intent handling, and action dispatch should move to shared app coordinators. |
| Connection | `AndroidConnectionController`, `DesktopConnectionForm`, `DesktopConnectionFormStateHolder`, `DesktopConnectionLifecycle`, `DesktopConnectionPanel`, `DesktopConnectionSession` | The previous connection issue showed platform divergence. Form UI can differ, but credential reuse, validation, reconnect, saved connection selection, and provider/session lifecycle need shared contracts. |
| Playback orchestration | Android Auto controllers, notification controls, foreground/service/session/runtime controllers; desktop now-playing, playback controller, callback coordinator, progress, BASS resolver/factory/diagnostics | Native audio, service, notification, and window/runtime details are platform boundaries. Playback commands, queue behavior, sleep timer expiry, session restoration decisions, repeat/shuffle/seek gates, and now-playing sidecar orchestration should be shared where possible. |
| Downloads/cache | `AndroidDownloadController`, `DesktopDownloadMutations`, `DesktopDownloadsController`, `DesktopDownloadsPanel`, `DesktopDownloadsRoute`, `DesktopCache`, `DesktopHotImageCache`, `DesktopTrackMetadataStore`, `AndroidStorage` | Filesystem and persistence adapters are platform boundaries. Download mutation plans, cache maintenance decisions, track metadata mutation policy, and status/result text should be shared. |
| Media/detail UI | `DesktopAlbumController`, `DesktopAlbumDetailPanel`, `DesktopArtistDetailPanel`, `DesktopMediaDetails`, `DesktopMediaRows`, `DesktopCoverArtThumb` | Some desktop panels are legitimate UI. Detail loading, similar/popular artist orchestration, action menus, and row action catalogs should use shared models/helpers. |
| Internet radio | `DesktopInternetRadioController`, `DesktopInternetRadioPanel`, `AndroidRadioPlaylist`, `DesktopRadioQueueUpdates`, `DesktopRadioSeeds` | Internet radio mutation/playback plans and radio queue update/seed helpers should be checked for platform-agnostic extraction. UI panels and platform playback execution remain adapters. |
| Smart playlists | `DesktopSmartPlaylistsController`, Android smart playlist functions inside `AndroidPlaylistsController` | Same user intent currently split by shape, not by platform need. Save/update/load orchestration should be shared. |
| Diagnostics/stats | `AndroidDiagnostics`, `DesktopStatsForNerdsWindow`, `DesktopStatsMapping` | Data mapping should be shared where it describes the same diagnostics; presentation/windowing can stay platform-specific. |

#### Unmatched platform-prefixed classification

This section finishes the first unmatched-file classification. Each row classifies the responsibility bucket, not every individual function. Implementation still needs intent-flow tracing before code changes.

| Bucket | Classification | Legitimate platform-only files/responsibilities | Hidden shared work to extract or standardize |
| --- | --- | --- | --- |
| App shell and route orchestration | mixed, split required | `AndroidAppShell` composables, `AndroidAppState` Compose holder, `DesktopNaviampApp` window/root composables, desktop menu/dialog/navigation UI, desktop window support. | Shared route restoration, action dispatch, route/content state reduction, provider lifecycle intent handling, shared shell UI model construction, app-level maintenance results. |
| Connection | mixed, split required | Desktop connection panel/form composables, Android/desktop state assignment, platform credential storage adapters. | Connection form state model, display-name logic, saved connection selection, credential reuse, validation/reconnect orchestration, provider media-source conversion, playback-session restoration after connection. |
| Playback orchestration | mixed, split required | Android foreground service, notification controls, Auto browse/command controllers, audio focus, desktop native/JVM runtime hooks, BASS library resolver, platform progress publishing. | Playback session save/restore decisions, play-track/play-radio orchestration, progress handling, previous/next/seek/repeat/shuffle command planning, now-playing sidecar and metadata update plans, stream-cover-art lookup. |
| Downloads and cache | mixed, split required | Desktop downloads panels/routes, Android content resolver/file APIs, desktop filesystem paths, concrete cache stores. | Download mutation plans, redownload/remove status results, downloaded-track playback selection, clear-cache/clear-library/reset-database result models, cache stats refresh decision rules. |
| Media/detail UI | mixed, split required | Desktop detail panels, row composables, cover-art thumb rendering, desktop-only layout. | Album/artist detail loading, detail back-route decisions, track-to-artist/album fallback models, action catalog/menu mapping, duration/summary labels, biography normalization if shared UI uses it. |
| Home | hidden shared-service candidate | Desktop state assignment and route callbacks. | Home refresh/load orchestration should use shared `HomeService`; Android home loading hidden in app shell/support should be pulled to the same coordinator. |
| Internet radio | mixed, split required | Desktop internet radio panel UI, platform playback invocation. | Station CRUD orchestration, stream URL resolution, recent-station reduction, internet-radio start plan, status/error messages. |
| Radio helper files | hidden shared-service candidate | Desktop adapter calls into `DesktopPlaylistEngine`. | `DesktopRadioQueueUpdates` and `DesktopRadioSeeds` mirror shared `RadioQueueRules`/`RadioSeeds` concepts; consolidate into shared radio coordinators/planners. |
| Smart playlists | hidden shared-service candidate | Desktop dialog/UI state and Android state assignment. | Save/update/load orchestration, provider media-source conversion, cache invalidation, playlist-track updates, status/error mapping. |
| Playlist UI and add-to-playlist dialog | mostly legitimate platform UI with shared helper leaks | Desktop playlist panels/dialogs and row UI. | Playlist action catalog conversion, summary/duration labels, add-to-playlist target resolution/mutation should be shared with Android paths. |
| Diagnostics/stats | mixed, split required | Desktop stats window presentation, Android diagnostics composable state, platform API call history collection. | Diagnostics model mapping, API call summary formatting, playback/cache/source stats rows, capability labels, stream stats mapping. |
| Settings UI | mostly legitimate platform UI with shared helper leaks | Desktop settings panel, category layout, platform sliders, connection rows/dialogs. | Cache-size conversions, detent snapping, connection settings form model, TLS/mTLS decision/status models, shared settings validation/defaults. |
| Navigation UI | mostly legitimate platform UI | Desktop bottom navigation composable and icons. | Route mapping between `DesktopAppRoute`, `SharedRoute`, and `NaviampRoute` should be shared or constrained behind a single route contract. |
| Native/audio infrastructure | legitimate platform adapter | Android BASS loader/JNI bridge/TLS, desktop BASS resolver/JNI binding, native integration tests. | Keep policy out of these files; only shared parser/log helpers if duplication remains obvious. |
| Platform tests | legitimate platform-only verification | Desktop BASS resolver, connection form, media details, download mutations, playback progress, settings store tests. | Shared behavior tests should move into common tests as services are extracted. |

Classification summary:

- Real platform boundaries: UI panels/windows, foreground service/notification/Auto integration, native BASS loading/JNI, concrete file/database/storage adapters, platform tests.
- Hidden duplicated behavior under different names: connection lifecycle, playback orchestration, downloads/cache mutations, media/detail loading, home loading, internet radio, radio helpers, smart playlists, diagnostics mapping, route/action/state reduction.
- Shared-helper leaks in otherwise platform files: labels, URL sanitization, cache keys, row/action conversions, duration summaries, settings normalization, route mapping, cache-size conversions.

#### Intent-flow traces: provider, status, and state mutation

This trace follows five concrete user intents across Android, Desktop, and shared domain code. It records where the call enters, where provider/storage work happens, where status is chosen, and where state is mutated. This is still discovery only.

| Intent | Android path | Desktop path | Shared code already used | Provider/storage mutations | Status/state mutation points | Extraction target |
| --- | --- | --- | --- | --- | --- | --- |
| Media metadata action: favorite/rating | `MainActivity` handlers call `toggleAndroidCurrentFavorite`, `setAndroidCurrentTrackRating`, `toggleAndroidArtistFavorite`, or `toggleAndroidAlbumFavorite` in `AndroidMediaActionsController`. Android builds `MediaMetadataMutationController` with platform state lambdas and notification side effects. | `DesktopAppRouteContent`/now-playing actions call `DesktopAppActions`, then `DesktopMediaActionsController`, which builds `MediaMetadataMutationController` with desktop state lambdas and metadata repository update. | `MediaMetadataMutationController`, `MediaMetadataStateUpdater`, `MediaTrackMetadataStateUpdater`, `favoriteTrackUpdate`, `favoriteArtistUpdate`, `favoriteAlbumUpdate`, `ratedTrackUpdate`, known artist/album helpers. | Shared mutation controller calls `MediaProvider.setTrackFavorite`, `setArtistFavorite`, `setAlbumFavorite`, or `setTrackRating`. Desktop also updates `TrackMetadataRepository`; Android updates notification metadata. | Android writes `state.status`, `state.nowPlaying`, `contentState`, `tracks`, mix selections, notification favorite metadata, and playback notification metadata. Desktop writes connection status, now playing, search results, home content, detail state, playlist engine track, and repository cache. | Keep Android notification and desktop repository adapter side effects platform-specific. Move construction of known media, mutation controller composition, status/result model, and post-mutation state update plan into a shared metadata action coordinator. |
| Save queue to playlist | `MainActivity.saveQueueAsPlaylist` calls `saveQueueAsPlaylistFromState` in `AndroidPlaylistsController`. It reads `state.playbackQueue.tracks`, sets `playlistActionStatus`, and refreshes `homeState.playlists`. | `DesktopNaviampApp` passes `playlistsController::saveQueueAsPlaylist`; `DesktopPlaylistsController.saveQueueAsPlaylist` reads `playlistEngine.queue.tracks`, sets connection status, and refreshes playlist/home state. | `MediaProvider.saveQueueAsPlaylistAndRefresh`, `createQueuePlaylist`, provider playlist refresh helpers. | Shared provider extension creates playlist and refreshes playlists through `ProviderResponseService`. | Android writes `playlistActionStatus`, `status`, and `homeState.playlists`. Desktop writes connection status, `playlists`, and home playlists. Error strings are duplicated in platform controllers. | Shared `QueuePlaylistSaveCoordinator` should accept queue tracks/name/provider/cache service and return a typed save result with refreshed playlists and status. Platform adapters should only read queue, apply state, and show dialogs. |
| Seeded radio start/refill | `MainActivity` dispatches to `startAndroidTrackRadio`, `startAndroidArtistRadio`, `startAndroidAlbumRadio`, mix radio functions, and `refillAndroidRadioIfNeeded`. Android functions build `RadioService`, mutate `state.radioQueueActive`, `radioRefilling`, queue state, recent streams, and status. | `DesktopAppActions` dispatches to `DesktopRadioController` for seeded starts, generated queue refill, and `convertCurrentTrackToRadio`. Desktop builds `RadioService`, calls `DesktopPlaylistEngine`, remembers recent streams, and sets radio flags/status. | `RadioService`, `RadioRequests`, `RadioQueueRules`, `RadioSeeds`, `RecentRadioStreams`, generated queue helpers, radio request label helpers. | Provider radio calls are `artistRadio`, `albumRadio`, `trackRadio`, random album list, album/artist seed lookup, and provider-response cached lookups. | Android writes `state.status`, queue controller, `playbackQueue`, `radioQueueActive`, `radioRefilling`, `lastRadioRefillSeedId`, `relatedTracks`, recent streams. Desktop writes connection status, queue through playlist engine, radio session/refill flags, recent streams, open-player flag. | Shared `SeededRadioCoordinator` plus `RadioRefillPlanner` should own seed selection, request loading, expansion counts, generated queue append/replace plans, recent stream updates, and status results. Platform code executes playback/queue commands. |
| Connection save/reconnect | Android form updates use shared `ConnectionFormState`; `startNavidromeConnectionFromForm` validates through shared form validation, then `startNavidromeConnection` builds `NavidromeProvider`, validates, saves connection/media source, and mutates app state. | Desktop uses `DesktopConnectionFormState`/holder, `DesktopConnectionLifecycleController`, `openDesktopConnectionSession`, saved connection selection, credential reuse, provider validation, media-source save, and playback session restore. | `ConnectionFormState`, `validateConnectionForm`, `ProviderMediaSourceConnection`, media-source repository contracts, `connectionMediaSourceUpdates`. | Both create/validate `NavidromeProvider`, save settings/connection, save provider media-source connection, and sometimes restore session state. Desktop and Android both convert `NavidromeConnection` to `ProviderMediaSourceConnection` in platform code. | Android writes connection fields, `state.status`, provider, active source, saved connection state. Desktop writes connection form state, saved connection for login, connection status, provider, active source, form open state, library/media source state, playback restoration state. | Shared `ConnectionCoordinator` should own form normalization, saved credential reuse, provider validation result, media-source conversion, source update result, reconnect statuses, and session-restore request. Platform adapters should supply credential storage, provider factory, and playback restore executor. |
| Playback command/progress | `MainActivity` and Android service call `playAndroidTrack`, `playAndroidInternetRadioStation`, `handleAndroidPlaybackProgressChanged`, adjacent-track helpers, service pause/seek/next paths, and `AndroidPlaylistEngine`. Android owns service/notification and state updates. | Desktop uses `DesktopPlaybackController`, `DesktopPlaylistEngine`, `DesktopPlaybackCallbackCoordinator`, and `DesktopNowPlayingController` to play, seek, previous/next, shuffle, repeat, save sessions, report now-playing/played, and update sidecars. | `PlaybackQueueController`, `PlaybackControlDecisions`, `PlaybackTargetPlan`, `PlaybackAudioSourceResolver`, `planPlaybackStart`, `planPlaybackTrackStarted`, `planPlaybackTrackStartEffects`, `planPlaybackProgressUpdate`, `planPlaybackSeek`, report decision helpers. | Provider calls include stream URL resolution, report now playing, report played, and local audio cache access. Storage calls save playback session and read/write sidecars. | Android writes `state.status`, queue, now-playing/station, notification controls, progress, pending seek/restore, radio flags, related tracks, sidecar/prefetch jobs. Desktop writes playback progress, repeat/shuffle snapshots, pending seek, open-player flag, saved session, submitted report session. | Shared `PlaybackCommandCoordinator` should own command/result planning for play, seek, previous/next, shuffle/repeat, progress updates, report gating, and session-save decisions. Platform adapters execute engine/service/notification/audio-focus side effects. |

Intent-flow summary:

- All five flows already use shared helpers, but the user-intent orchestration still lives in platform files.
- The next implementation work should be coordinator extraction, not broad model invention.
- Shared coordinators should return typed results containing status text, state reducers, provider/cache invalidation work, and playback/queue commands.
- Platform files should provide dependencies, execute OS/audio/storage side effects, and apply shared result objects to platform state.
- The lowest-risk first implementation slice remains media metadata because a shared mutation controller already exists and the platform-only side effects are easy to name.

#### Existing shared service inventory from inspected areas

The cleanup should compose and extend existing shared services rather than replacing them wholesale:

- Search already has `SearchSessionController`.
- Playlists already have shared helpers in `PlaylistMutations.kt` for queue save, add-to-playlist, playback planning, pending playback, selected playlist updates, smart playlist statuses, and playlist delete/rename status text.
- Radio already has `RadioService`, `RadioRequests`, `RadioQueueRules`, `RadioSeeds`, `RecentRadioStreams`, and internet radio playback/playlist helpers.
- Library already has `LibraryFreshness` and `LibraryPaging` helpers.
- Media metadata already has `MediaMetadataMutationController`, `MediaMetadataStateUpdater`, and `MediaTrackMetadataStateUpdater`.
- Mix builders already have shared `ArtistMixBuilderService`, `AlbumMixBuilderService`, and `GenreMixBuilderService`.

Implication: several implementation slices should be coordinator/composition work, not new domain logic from scratch.

#### Discovery-to-implementation checklist

This checklist reconciles completed discovery outputs with future implementation work. Completed items are documentation/discovery only. Unchecked items are implementation candidates and should not be treated as already approved code changes.

- [x] Finish the Phase 1 matrix for all 32 normalized Android/Desktop pairs.
  - Expected output: each pair marked as shared-service candidate, legitimate platform adapter, mixed, or already thin.
  - Verification: doc-only diff; no source files touched.
- [x] Map unmatched platform-prefixed files and attach them to a responsibility bucket.
  - Expected output: platform-only files are marked as real OS/lifecycle/UI boundaries or hidden duplicates under different names.
  - Verification: doc-only diff; no source files touched.
- [x] Trace direct provider and status mutation flows by user intent.
  - Start with media actions, playlists, radio, connection, and playback.
  - Expected output: intent-flow maps showing where provider calls happen, where status is chosen, and where platform state is mutated.
  - Verification: doc-only diff; no source files touched.
- [x] Decide the first implementation slice.
  - Decision: start with media metadata actions.
  - Verification: doc-only diff; no source files touched.
- [x] Define shared result models for user-intent operations.
  - Covers: loading, success, failure, cache invalidation, refreshed state, playback command, and status text.
  - Verification target after implementation: shared unit tests before platform rewiring.
- [x] Extract media action orchestration first.
  - Move shared lookup, queue append, add-to-playlist, metadata mutation composition, and common status handling out of `AndroidMediaActionsController.kt` and `DesktopMediaActionsController.kt`.
  - Keep Android notification updates and downloaded file lookup as platform adapters.
  - Expected platform result: both controllers become thin wiring around shared services plus platform side effects.
- [x] Extract playlist mutation and refresh orchestration.
  - Move queue-save, add tracks to playlist, rename/delete, playlist detail refresh, smart playlist save/update/load, preloading decisions, cache invalidation, and home playlist reduction into focused shared services.
  - Keep dialogs, route changes, recent playlist persistence, and playback execution as platform adapters.
- [x] Extract library sync/freshness orchestration.
  - Move sync gating, freshness evaluation orchestration, scan-check marking plan, progress/status result modeling, and paging limit calculations where shared.
  - Keep scrolling, platform cache repositories, and UI progress application outside the shared service.
- [x] Extract radio and generated queue orchestration.
  - Move seed selection, `RadioRequest` construction, refill planning, generated queue append/replace decisions, recent radio stream result data, and expansion sequencing into shared services.
  - Keep actual playback engine calls and platform state assignment in adapters.
- [x] Standardize playback command execution.
  - Define a shared command/result surface for play, pause, resume, seek, previous, next, shuffle, repeat, volume, queue replace, and queue append.
  - Keep BASS/native/session/notification behavior in platform adapters.
- [ ] Decompose root app shells after the services exist.
  - Shrink `DesktopNaviampApp.kt` and `AndroidAppShell.kt` by wiring shared coordinators instead of inlining provider calls and state choreography.
  - Remove compiler method-size workarounds only after the root files are actually small enough.
- [ ] Add architecture regression checks to future slices.
  - Each slice should report which platform-prefixed files got thinner, which shared services were created or composed, and which platform-only boundaries remain.

#### Discovery-only next steps

- [x] Start a responsibility matrix for the 32 normalized pairs.
- [x] For each pair, label each responsibility as shared-service candidate, mixed split, legitimate platform adapter, or already thin enough.
- [x] Start the unmatched platform-prefixed file map.
- [x] Finish deciding whether unmatched platform-prefixed files represent real platform-only behavior or hidden duplicated behavior under a different name.
- [x] Trace provider/status/state mutation flows for the first five user intents: media metadata action, queue save to playlist, seeded radio start/refill, connection save/reconnect, and playback command.
- [x] Decide the first implementation slice after the matrix is complete.

## Phase 2: Shared Service Shape

- [x] Define service categories and naming conventions.
  - intent planners: pure services that turn state plus user intent into a plan
  - mutation coordinators: services that call providers/storage and return result models
  - state reducers: pure services that apply results to app state models
  - adapters: platform code for OS, lifecycle, audio session, filesystem, notifications, and window/device concerns
- [ ] Create shared result types for loading/status/error outcomes instead of hand-rolled platform strings.
- [ ] Prefer pure functions for state transitions and small classes for side-effectful coordination.
- [ ] Ensure service constructors take small dependencies rather than whole app shells.
- [ ] Add focused unit tests for each shared service before replacing platform callers.

### Shared Service Vocabulary

Use these names consistently so new shared pieces stay small, focused, and composable.

#### Planner

Planner code is pure. It accepts current state plus a user intent and returns a plan. It should not call providers, storage, playback engines, clocks, filesystem APIs, platform APIs, or mutate state.

Use `Planner` when the output is a description of work to do next:

- queue append or replace plans
- playback command plans
- radio refill plans
- save/update/delete request plans
- cache invalidation plans
- validation and status plans

Planner naming:

- Function: `planPlaybackStart`, `queueAppendPlan`, `radioRefillPlan`.
- Class: `PlaybackCommandPlanner`, `RadioRefillPlanner`.
- Return type: `PlaybackCommandPlan`, `QueueAppendPlan`, `RadioRefillPlan`.

#### Coordinator

Coordinator code is the shared side-effect boundary. It can call providers, shared repositories, shared cache services, and other shared services. It should return typed results instead of directly writing platform app state.

Use `Coordinator` when the operation performs work:

- provider mutations
- provider reads with cache invalidation
- playlist save/rename/delete flows
- connection validation flows
- library sync orchestration
- metadata mutation flows

Coordinator naming:

- Class: `MediaMetadataMutationController` is grandfathered, but new side-effectful services should use `Coordinator` unless there is a strong reason not to.
- Preferred names: `QueuePlaylistSaveCoordinator`, `PlaylistMutationCoordinator`, `ConnectionCoordinator`, `LibrarySyncCoordinator`.
- Return type: `QueuePlaylistSaveResult`, `PlaylistMutationResult`, `ConnectionResult`, `LibrarySyncResult`.

#### Reducer

Reducer code is pure. It accepts shared state-shaped inputs plus a result and returns updated state-shaped data. It should not launch coroutines, call providers, call storage, or update platform state directly.

Use `Reducer` when the output is a changed model:

- home content after refreshed playlists
- search results after metadata mutation
- selected album/artist details after metadata mutation
- queue state after append/replace
- settings models after form changes

Reducer naming:

- Function: `withUpdatedTrack`, `withUpdatedAlbum`, `applyPlaylistMutationResult`.
- Class: `HomeContentReducer`, `PlaylistDetailReducer` only when several related pure functions need grouping.
- Prefer extension functions for small one-model updates.

#### Adapter

Adapter code lives at the platform boundary. It translates Android/Desktop/OS objects into shared service inputs and executes platform-only side effects from shared results.

Adapter code may:

- update Android notifications and media sessions
- call desktop window, tray, filesystem, or JVM-only APIs
- call BASS/native playback backends
- manage Android services, activities, permissions, and lifecycle
- read/write platform persistence primitives behind shared repository interfaces
- assign results into platform app state

Adapter code should not:

- choose provider mutation behavior
- duplicate status/error strings
- duplicate queue/playback/radio/playlist decision logic
- build a second version of a shared result model
- call providers directly unless a shared coordinator does not exist yet and the file is marked as temporary

Adapter naming:

- Platform files may keep `Android*` and `Desktop*` prefixes.
- New adapter classes should end with `Adapter`, `Store`, `Backend`, `LifecycleController`, or `Shell` depending on the boundary they represent.
- Avoid `Controller` for new platform code unless the file is explicitly a temporary coordinator scheduled for extraction.

#### Result Model

Result models are the contract between shared coordinators and platform adapters. They should be typed enough that platform code can react without re-deciding the operation.

Result models should include only what the caller needs:

- updated domain models
- status text
- refreshed lists or details
- cache invalidation requests
- playback/queue commands to execute
- whether platform-only side effects should run

Result naming:

- `*Result` for side-effectful coordinator outcomes.
- `*Plan` for pure planner outputs.
- `*Update` for reducer-style state changes.

Avoid Boolean-only coordinator returns when the caller needs to distinguish success, unsupported capability, missing input, provider failure, refreshed data, or platform side-effect behavior.

#### Composition Rules

- Platform adapter calls shared coordinator.
- Shared coordinator composes planners, provider/cache services, and reducers.
- Planner and reducer stay pure and can be tested without coroutines or fakes.
- Coordinator owns coroutine-friendly side effects and is tested with fake providers/repositories.
- Shared services never import from `apps/android` or `apps/desktop`.
- Shared UI may call shared domain/UI helpers, but it should not become an orchestration layer.
- If Android and Desktop need the same status string, provider sequence, queue decision, or metadata mutation behavior, that belongs in shared code.

#### Future Slice Checklist

Every implementation slice should record:

- Which new planner/coordinator/reducer/adapter names were added.
- Which platform-prefixed files got thinner.
- Which platform-only responsibilities intentionally remained.
- Which shared tests prove the new call path.
- Whether any temporary platform direct-provider calls remain, and why.

## Phase 3: Shared Metadata Actions

- [ ] Move all favorite/rating/metadata mutation decision logic into shared domain services.
- [ ] Make `MediaMetadataMutationController` the single entry point for artist, album, and track metadata updates.
- [ ] Replace duplicated status/error handling in platform media controllers with shared result models.
- [ ] Reduce Android/Desktop media action controllers to adapter wiring.
- [ ] Add shared tests for mutation state transitions and provider capability handling.

## Phase 4: Shared Playlist Mutations

- [x] Create a shared playlist mutation coordinator for save queue, add track, rename, and delete flows.
- [x] Keep platform code responsible only for dialogs, text input, and user intent dispatch.
- [x] Replace duplicated Android/Desktop queue-save paths with a single shared flow.
- [x] Add tests for queue-to-playlist request construction and local state refresh.

Phase 4 close-out:

- Playlist mutation, refresh, and playback preparation logic now lives behind shared provider/domain helpers in `PlaylistMutations.kt`.
- Covered shared flows include list refresh/preload, detail refresh, playlist playback preparation, selected-detail playback, queue-save, add-to-playlist, rename/delete, smart playlist save/update, selected-playlist reducers, pending playback, and playlist list/home projection.
- Remaining playlist platform code is intentionally adapter-shaped: coroutine/lifecycle ownership, provider availability checks, dialogs/text input, Android content-state writes, Desktop route/dialog/auth side effects, recent-playlist persistence, and playback execution.
- `DesktopPlaylistMutations.kt` still resolves desktop add-to-playlist UI targets into concrete tracks. That should move with the broader media-actions extraction because Android reaches the same intent through media action paths rather than through a matching playlist controller.
- Do not keep adding tiny playlist wrappers unless a future feature exposes duplicated behavior again; the next cleanup area should be media actions or radio.

## Phase 5: Shared Playback Orchestration

- [x] Define a shared playback command surface for play, pause, resume, seek, previous, next, shuffle, repeat, and volume.
- [x] Keep BASS interaction behind the existing playback engine boundary.
- [x] Move sleep timer expiry and playback command decisions into shared domain/application code.
- [ ] Reduce platform playback controllers to lifecycle/audio-session/notification adapters.
- [x] Add tests for sleep timer, repeat/shuffle, seek gating, and previous/next decisions.

## Phase 6: Shared App Orchestration

- [ ] Extract shared coordinators for connection/provider lifecycle.
- [ ] Extract shared coordinators for library sync/freshness.
- [ ] Extract shared coordinators for home loading and refresh.
- [ ] Extract shared coordinators for artist, album, playlist, search, downloads, and internet radio flows.
- [ ] Extract shared coordinators for artist mix, album mix, genre mix, and future generated queues.
- [ ] Make Android/Desktop app shells compose shared coordinators rather than owning equivalent orchestration.

## Phase 7: Platform Root Decomposition

- [ ] Split `DesktopNaviampApp.kt` into smaller state holders and route coordinators.
- [ ] Split oversized Android app shell/activity responsibilities using the same boundaries.
- [ ] Extract connection/provider setup into a desktop adapter plus shared connection coordinator.
- [ ] Extract library sync/freshness orchestration.
- [ ] Extract now-playing/session restoration orchestration.
- [ ] Extract mix-builder state wiring into shared application state where possible.
- [ ] Remove desktop Compose compiler bytecode workaround once root composables are small enough.
- [ ] Verify desktop compile fails no method-size limits without workaround.

## Phase 8: Shared UI and Route Contracts

- [ ] Audit shared UI models for platform-specific leakage.
- [ ] Move duplicated action catalog/menu item construction into shared UI/domain helpers.
- [ ] Ensure Android/Desktop route content takes shared state models instead of rebuilding equivalent models separately.
- [ ] Keep platform-specific layout only where viewport, window chrome, or lifecycle genuinely differs.

## Phase 9: Verification and Diff Discipline

- [ ] Add a lightweight architectural checklist to feature work: one shared path first, platform adapters second.
- [ ] Add focused tests around shared services before platform wiring.
- [ ] Establish expected build commands for each cleanup slice.
- [ ] Track file-count spread for representative changes.
- [ ] Before merging each slice, record what platform duplication was removed.
- [ ] For each slice, report how many platform-prefixed files got thinner.
- [ ] For each slice, report which shared services were created or composed.

## Verification commands

Use these after meaningful slices:

```powershell
.\gradlew.bat :core:domain:allTests
.\gradlew.bat :apps:android:assembleDebug
.\gradlew.bat "-Pnaviamp.bass.platform=windows-x64" :apps:desktop:compileKotlinDesktop
.\gradlew.bat "-Pnaviamp.bass.platform=windows-x64" :apps:desktop:packageReleaseDistributable
```

## First Implementation Slice

Decision: start with media metadata actions.

Why this slice first:

- Discovery labels `MediaActionsController` as a shared-service candidate.
- The five-flow trace shows metadata actions already use shared domain pieces, so the work is coordinator extraction rather than broad invention.
- `MediaMetadataMutationController`, `MediaMetadataStateUpdater`, `MediaTrackMetadataStateUpdater`, and `TrackMediaActions.kt` already provide most of the shared behavior.
- Platform-only side effects are easy to name: Android notification state and playback notification metadata; desktop track metadata repository and playlist-engine track replacement.
- The blast radius is smaller than playback, radio, connection, or app-shell decomposition.

Goal:

- Make Android and Desktop metadata actions call one shared media metadata action path for favorite/rating mutations and post-mutation state update planning.
- Leave platform files as adapters that provide dependencies, execute platform-only side effects, and apply shared results to platform state.

Non-goals for this first slice:

- Do not refactor queue append, add-to-playlist, or playback selection yet.
- Do not restructure `DesktopNaviampApp.kt`, `AndroidAppShell.kt`, or `MainActivity.kt` beyond the minimum wiring required.
- Do not redesign provider APIs.
- Do not change product behavior or status wording unless preserving behavior requires one shared wording source.

Files to inspect before editing:

- `core/domain/src/commonMain/kotlin/app/naviamp/domain/media/MediaMetadataMutationController.kt`
- `core/domain/src/commonMain/kotlin/app/naviamp/domain/media/TrackMediaActions.kt`
- `apps/android/src/main/kotlin/app/naviamp/android/media/AndroidMediaActionsController.kt`
- `apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/media/DesktopMediaActionsController.kt`
- `apps/android/src/main/kotlin/app/naviamp/android/app/MainActivity.kt`
- `apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/app/DesktopAppActions.kt`

Implementation checklist for the first slice:

- [x] Add or extend a shared metadata action result model.
  - It should represent mutation success/failure, updated media item, status text, and whether platform-only side effects should run.
- [x] Add focused common tests for track favorite, artist favorite, album favorite, and track rating mutation results.
- [x] Move common status/error handling into the shared metadata action path.
- [x] Move known track/artist/album lookup composition out of platform controllers where practical.
- [x] Keep Android notification optimistic toggle/revert and notification metadata update as Android-only adapter behavior.
- [x] Keep desktop `TrackMetadataRepository.updateTrack` and `DesktopPlaylistEngine.updateTrack` as desktop-only adapter behavior.
- [x] Reduce `AndroidMediaActionsController.kt` and `DesktopMediaActionsController.kt` to construction/adaptation around the shared path.
- [x] Verify behavior with common tests plus Android/Desktop compilation.
- [x] Update this plan with exact files reduced and the remaining platform-only responsibilities.

Progress notes:

- Added `MediaMetadataMutationResult` in `MediaMetadataMutationController.kt` with typed track, artist, album, failed, and skipped outcomes.
- Kept the existing Boolean mutation methods as wrappers, but moved provider capability handling, missing-item status, mutation failure status, and update application through the shared result path.
- Added `MediaMetadataMutationControllerTest.kt` coverage for track favorite, artist favorite, album favorite, track rating, missing item status, and unsupported provider capability skips.
- Android optimistic notification favorite revert now reads `shouldRunPlatformSideEffects` from the shared result.
- Desktop platform-only side effects remain adapter-only: `TrackMetadataRepository.updateTrack` and `DesktopPlaylistEngine.updateTrack`.
- Remaining platform-only Android behavior: notification favorite state and playback notification metadata refresh.
- Remaining platform-only Desktop behavior: desktop playlist-engine replacement and metadata repository persistence.
- Extracted shared media track lookup composition into `MediaTrackLookupSources`, `knownTracksForMediaActions`, `findKnownTrack`, `selectedTrackPlayback`, and `searchOrAlbumTracksForMediaActions`.
- Android now adapts `AndroidAppState` into shared lookup sources instead of owning track lookup and playback-context rules.
- Desktop metadata lookup now uses `knownTracksForMediaActions` instead of hand-building its own queue/album/search/related list.
- Added `TrackMediaActionsTest.kt` coverage for shared lookup order, playback-context selection, and known-track fallback behavior.
- Added shared `addTracksToPlaylistAndRefresh` in `PlaylistMutations.kt` so add-to-playlist provider mutation, deduplication, status/update selection, cache invalidation, and refreshed playlist loading use one call path.
- Android media actions now apply the shared add-to-playlist result instead of owning provider mutation and refresh decisions.
- Desktop add-to-playlist orchestration was in `DesktopPlaylistsController.kt`; it now resolves target tracks as a platform/UI adapter step and delegates mutation/refresh to the shared path.
- Removed the duplicated desktop `addTargetTracksToPlaylist` mutation helper.
- Added `PlaylistMutationsTest.kt` coverage for creating a playlist through the shared add-to-playlist path and adding missing tracks to an existing playlist.
- Added shared `PlaybackQueueManager` as the queue-level service boundary for app queue decisions.
- `PlaybackQueueManager` now owns append/start-empty/dedupe/status decisions and returns `PlaybackQueueUpdate`.
- Android media actions now adapt queue append results into `PlaybackQueueController` and app state instead of owning queue branching.
- Desktop media and playlist queue additions now use `PlaybackQueueManager`; `DesktopPlaylistEngine` exposes a thin `replaceQueue` adapter so it still owns desktop queue callbacks.
- Added shared `mediaMetadataMutationController` construction in `MediaMetadataMutationController.kt`.
- Android and Desktop media actions now share controller construction, known track/artist/album lookup composition, state updater construction, and status handling through the domain factory.
- Platform media actions still own the correct side effects: Android notification favorite/metadata refresh and Desktop playlist-engine/repository track updates.
- Added `MediaMetadataMutationControllerTest.kt` coverage for factory-applied track state and the platform track-update callback.
- Size/reduction note for this slice:
  - `AndroidMediaActionsController.kt` and `DesktopMediaActionsController.kt` no longer hand-build `MediaMetadataMutationController` from raw known-media lookup functions and state updater callbacks.
  - Shared domain grew intentionally with the controller factory so platform files pass adapter getters, setters, and callbacks instead of recreating the same mutation wiring.
- Verification:
  - `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.media.MediaMetadataMutationControllerTest --tests app.naviamp.domain.media.TrackMediaActionsTest`
  - `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`
  - `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`
- Reviewed the remaining media-action surface after the shared metadata factory landed.
- No additional media-action extraction was taken in this pass because the remaining code is adapter-owned:
  - Android downloaded-track lookup maps UI/download state back to cached files.
  - Android `MainActivity` still owns UI callback routing for row actions.
  - Desktop media actions still own playback invocation through `DesktopPlaylistEngine` and `PlaybackEngine` settings.
  - Public metadata apply hooks remain for platform callback entry points.
- Closed the media-action orchestration checklist item because shared lookup, queue append, add-to-playlist, metadata mutation composition, and common status handling now have shared paths.
- Reconciled the shared user-intent result model checklist against the completed implementation slices.
- Existing shared models now cover the requested categories:
  - Loading/status text: playlist, add-to-playlist, smart-playlist, media-detail, internet-radio, and queue status helpers.
  - Success/failure/skipped outcomes: `MediaMetadataMutationResult`, `DownloadTracksResult`, and playlist mutation/update results.
  - Cache invalidation/refreshed state: `AddToPlaylistRefresh`, `QueuePlaylistSaveRefresh`, playlist list/detail refresh updates, and related state application models.
  - Playback commands/results: `PlaybackQueueNavigationCommand`, `PlaybackQueueFinishedCommand`, queue mutation/selection/finished updates, and playlist playback work/application models.
- Closed the shared result-model checklist item as a cross-slice checkpoint rather than adding an abstract umbrella type; the current codebase uses focused result models per user intent.
- Future library, radio, and connection extraction should add result models only where those domains still have duplicated provider/status/state choreography.
- `queueAppendPlan` now delegates to shared queue append helpers to keep existing callers on the same decision path.
- Added `PlaybackQueueManagerTest.kt` coverage for starting an empty queue, appending to an existing queue, deduping, and no-change statuses.
- Extended `PlaybackQueueManager` to own repeat cycling and upcoming-shuffle decisions with typed `PlaybackShuffleUpdate` results.
- Android shell shuffle/repeat and Desktop shuffle/repeat now use `PlaybackQueueManager`; platform code only applies queue state and playback-engine repeat settings.
- `nextRepeatMode` now delegates to `PlaybackQueueManager` so older callers share the same repeat decision path.
- Added `PlaybackQueueManagerTest.kt` coverage for repeat cycling, shuffle toggle/restore, and no-change shuffle outcomes.
- Added shared `PlaybackQueueNavigationCommand` planning for previous, next, jump, restart-current, and no-op navigation outcomes.
- `PlaybackControlDecisions.kt` now delegates legacy previous/next/adjacent helpers to `PlaybackQueueManager`, keeping existing Android call sites on the shared path.
- Desktop previous, next, and queue-index selection callbacks now route through `DesktopPlaybackController`, which applies shared queue-navigation commands to desktop playback side effects.
- Size/reduction note for this slice:
  - `DesktopNaviampApp.kt`: relevant direct next/jump callback logic was removed from the root app and replaced with controller calls; net diff was small because a local queue-index adapter was added.
  - `DesktopPlaybackController.kt`: grew intentionally because it is now the desktop playback adapter boundary for shared navigation commands.
  - Android platform files stayed flat because Android already used the shared adjacent-action helper; the helper now delegates into the manager.
  - Shared domain grew intentionally in `PlaybackQueueManager.kt` and tests because navigation policy moved out of platform-local decisions.
- Added shared `PlaybackQueueFinishedCommand` planning for finished-track outcomes: play next, replay current, or stop at the end of the queue.
- `PlaybackQueueController.finishedSelection`, `nextGaplessQueueIndex`, and prepared-next checks now delegate queue finish/prepare policy to `PlaybackQueueManager`.
- Android service-owned playback now uses `PlaybackQueueManager.finishCurrentTrack` instead of its own repeat-one branch and adjacent-next fallback.
- Size/reduction note for the finished-track slice:
  - `AndroidPlaybackForegroundService.kt`: shrank by removing the `repeatOne` adapter callback.
  - `AndroidServicePlaybackRuntimeController.kt`: grew slightly because it now applies typed shared finished commands to service playback side effects.
  - Desktop platform files stayed flat because `DesktopPlaylistEngine` already goes through `PlaybackQueueController`; the controller now delegates the policy to the manager.
  - Shared domain grew intentionally in `PlaybackQueueManager.kt`, `PlaybackQueueController.kt`, and tests because end-of-track and prepare-next queue policy moved into the shared queue service path.
- Added shared adjacent queue selection through `PlaybackQueueManager.selectAdjacent`.
- `PlaybackQueueController.adjacent` now delegates adjacent queue policy to the manager.
- Android service-owned previous/next now uses `PlaybackQueueManager.selectAdjacent` instead of calling `PlaybackQueueController.adjacent` directly.
- Split `PlaybackQueueManager.kt` composition into smaller focused files:
  - `PlaybackQueueUpdates.kt` owns queue update DTOs and command/result sealed types.
  - `PlaybackQueueAppend.kt` owns append filtering and append-status helpers.
  - `PlaybackQueueManager.kt` now reads as the shared queue service implementation instead of a mixed model/helper/service file.
- Size/reduction note for the adjacent/split slice:
  - `AndroidServicePlaybackRuntimeController.kt`: stayed roughly flat because direct controller adjacent policy was replaced by manager command application and the same service side effects remain.
  - `PlaybackQueueController.kt`: shrank its adjacent decision body and now delegates policy to the manager.
  - `PlaybackQueueManager.kt`: shrank substantially by moving DTOs and append helpers into focused shared files, while adding `selectAdjacent`.
  - Desktop platform files stayed flat because desktop manual navigation already routes through `DesktopPlaybackController` and `DesktopPlaylistEngine`.
- Added shared current, next, previous, and jump selection helpers in `PlaybackQueueManager`.
- `PlaybackQueueController.next`, `previous`, `jumpTo`, and `playCurrent` now delegate queue selection policy to the manager and only apply session/state updates.
- Decision: direct `PlaybackQueueController` calls in `DesktopPlaylistEngine` remain acceptable because the engine is the desktop playback side-effect adapter and the controller now delegates policy into shared manager methods.
- Size/reduction note for the desktop navigation inspection slice:
  - Desktop platform files stayed flat by design; changing them would only add adapter churn now that `PlaybackQueueController` owns the shared call path.
  - `PlaybackQueueController.kt` shifted from inline queue selection decisions to shared manager calls.
  - `PlaybackQueueManager.kt` grew intentionally with the remaining manual selection policy.
- Added shared queue mutation updates through `PlaybackQueueManager` for append, update-track, and replace-upcoming operations.
- `PlaybackQueueController.appendTracks`, `updateTrack`, `replaceUpcomingTracks`, and `toggleUpcomingShuffle` now delegate mutation policy to the manager and only apply queue/prepared-next state.
- Size/reduction note for the queue mutation slice:
  - Platform files stayed flat because both Android and Desktop already pass through `PlaybackQueueController` or `PlaybackQueueManager`; no adapter churn was needed.
  - `PlaybackQueueController.kt` now acts more clearly as the queue state/session applier instead of owning mutation rules.
  - `PlaybackQueueManager.kt` grew intentionally with the remaining shared mutation rules.
  - `PlaybackQueueUpdates.kt` grew with the typed mutation update DTO.
- Added shared lifecycle queue updates through `PlaybackQueueManager` for start, restore, and clear.
- `PlaybackQueueController.start`, `restore`, and `clear` now delegate queue validation/shape decisions to the manager and keep session application locally.
- Decision: `PlaybackQueueController.replaceQueue` remains a state-sync method because callers intentionally provide already-shaped queue state from platform/session/radio adapters.
- Size/reduction note for the lifecycle queue slice:
  - Platform files stayed flat because existing adapters already call the controller/engine boundary.
  - `PlaybackQueueController.kt` moved start/restore/clear queue-shape policy into the manager while retaining session updates.
  - `PlaybackQueueManager.kt` grew intentionally with lifecycle queue planning.
- Reviewed `PlaybackQueueManager.kt` after the lifecycle additions and split it by operation family instead of letting the shared service become a new large catch-all.
- Added focused shared queue services:
  - `PlaybackQueueLifecycleManager` owns start, restore, and clear queue shape decisions.
  - `PlaybackQueueMutationManager` owns append, update-track, replace-upcoming, and upcoming-shuffle mutation decisions.
  - `PlaybackQueueControlManager` owns repeat and previous/next/jump command decisions.
  - `PlaybackQueueSelectionManager` owns current/adjacent/manual/finished-track selection decisions and prepare-next policy.
- `PlaybackQueueManager` is now a composed facade, so existing callers keep one shared queue entry point while the implementation is made of smaller services.
- Size/reduction note for the queue-manager composition slice:
  - Platform files stayed flat because this was an internal shared-service decomposition.
  - `PlaybackQueueManager.kt` shrank substantially and now delegates by operation family.
  - New focused shared manager files grew intentionally and are each small enough to test or reason about independently.
  - Existing `PlaybackQueueManagerTest.kt` continues to verify behavior through the public facade.
- Added shared `PreparedNextPlaybackCoordinator` and `PreparedNextPlaybackSettings` so Android and Desktop use the same prepared-next planning and request-building path.
- Android `AndroidPlaylistEngine.prepareNextIfNeeded` and desktop `DesktopPlaylistEngine.prepareNextIfNeeded` now delegate transition gating, prepared-index checks, source resolution, replay-gain mode selection, and request construction to the shared coordinator.
- Platform engines still own the correct platform boundary: session-token gating, queue prepared-index mutation, and `QueueAwarePlaybackEngine.prepareNext`.
- Added common coordinator tests for building prepared-next requests and skipping already prepared queue indexes.
- Size/reduction note for the prepared-next coordinator slice:
  - `AndroidPlaylistEngine.kt` and `DesktopPlaylistEngine.kt` both lost their parallel local plan/request construction branches.
  - Shared domain grew intentionally in `PreparedNextPlaybackService.kt` with a focused coordinator around the existing planner functions.
  - `PreparedNextPlaybackServiceTest.kt` now verifies the coordinator entry point that both platform playlist engines call.
- Added shared `PreparedNextPlaybackWork` so prepared-next planning carries the queue index to mark as prepared together with the request-building plan.
- Android and Desktop playlist engines now call `PreparedNextPlaybackCoordinator.work` and mark `work.markPreparedNextIndex` instead of manually threading the plan index.
- Platform playlist engines still own coroutine launch, session-token checks, and the actual `QueueAwarePlaybackEngine.prepareNext` call.
- Added common `PreparedNextPlaybackServiceTest.kt` coverage for the prepared-next work item and request path.
- Size/reduction note for the prepared-next work slice:
  - `AndroidPlaylistEngine.kt` and `DesktopPlaylistEngine.kt` each lost one more piece of parallel prepared-next plan/index handling.
  - Shared domain grew intentionally with a small DTO and helper around existing prepared-next coordinator behavior.
- Added shared `PreparedBassPlaybackPlan` and adoption planning for the Android/Desktop BASS playback engines.
- Android and Desktop BASS engines now use the same shared gates for reusing prepared playback, checking mixer preparation support, choosing mixer preparation, and choosing desktop-only direct prepared-stream fallback.
- Platform BASS engines still own native side effects: BASS initialization, stream handle creation/release, mixer channel wiring, end sync callbacks, audio focus, wake locks, and diagnostics.
- Added common `PreparedBassPlaybackPlannerTest.kt` coverage for prepared reuse, mixer preparation, direct fallback, replay-gain factor planning, and prepared adoption.
- Size/reduction note for the prepared-BASS planner slice:
  - `AndroidBassPlaybackEngine.kt` lost its local reuse/can-prepare/adoption gate branching and now applies shared prepare/adopt plans.
  - `DesktopBassPlaybackEngine.kt` lost parallel reuse/can-prepare/adoption gate branching while preserving its direct fallback behavior.
  - Shared domain grew intentionally with a small planner that composes existing transition/replay-gain helpers.
- Extended `PreparedBassPlaybackPlan` to carry the full replay-gain adjustment, not just the volume factor.
- Added shared prepared-BASS state updates for prepare success, prepare failure, and prepared-source adoption.
- Android and Desktop BASS engines still own native prepared stream creation/adoption calls, but prepared result/error/adoption state assignment now flows through shared update models.
- Added common `PreparedBassPlaybackPlannerTest.kt` coverage for prepared success, failure, and adoption update contracts.
- Size/reduction note for the prepared-BASS state slice:
  - `AndroidBassPlaybackEngine.kt` no longer hand-builds prepared success/failure/adoption state after native calls.
  - `DesktopBassPlaybackEngine.kt` no longer recomputes prepared replay-gain adjustment in `prepareNext` or hand-builds prepared success/failure/adoption state.
  - Shared domain grew intentionally in `PreparedBassPlaybackPlanner.kt` with small result DTOs and helper functions.
- Added shared `BassPlaybackPollingState` and `planBassPlaybackPollingUpdate` to reduce duplicated BASS snapshot interpretation.
- Android and Desktop BASS polling loops now share active-state, progress, metadata, continue/stop, and optional source-end finish decisions.
- Platform BASS engines still own coroutine loops, polling interval, logging, end-sync callbacks, notification/wake-lock side effects, and stream cleanup.
- Added common `BassPlaybackPollingTest.kt` coverage for active-state/progress/metadata emission, duplicate progress policy, source-end finish policy, and stopped-output polling termination.
- Size/reduction note for the polling reducer slice:
  - `AndroidBassPlaybackEngine.kt` keeps its every-tick progress emission and source-end finish behavior, but delegates snapshot interpretation to the shared reducer.
  - `DesktopBassPlaybackEngine.kt` keeps changed-only progress emission and its existing finish-after-loop behavior, while both normal and adopted playback polling loops use the same reducer.
  - Shared domain grew intentionally with a small reducer and focused tests.
- Remaining work in this playback slice: inspect repeated stream cleanup/reset blocks in Android/Desktop BASS engines and decide whether shared reset DTOs already cover enough, or whether a small shared cleanup application helper would reduce duplication without owning native releases.
- Added shared `BassPlaybackCleanupReset` and `clearBassPlaybackCleanupState` so active stream state and prepared playback metadata clear through one shared reset plan.
- Android and Desktop BASS engines still own native stream release, coroutine cancellation, notifications, wake locks, and end-sync callback cleanup, but repeated field-reset blocks now flow through small platform appliers fed by the shared cleanup reset.
- Added common `BassPlaybackCleanupTest.kt` coverage for the combined cleanup reset contract.
- Size/reduction note for the cleanup reset slice:
  - `AndroidBassPlaybackEngine.kt` removed duplicated prepared/full cleanup field assignments in stop, prepared adoption, prepare failure, and free-prepared paths.
  - `DesktopBassPlaybackEngine.kt` removed duplicated prepared/full cleanup field assignments in polling finally, stop, prepared adoption, prepare failure, and free-prepared paths.
  - Shared domain grew intentionally by one small reset DTO/function in `PlaybackTransitions.kt`, keeping native release behavior platform-owned.
- Added shared `BassPlaybackPollingPolicy` with Android service and desktop engine presets for polling interval, duplicate progress emission, source-end finish behavior, and finish-after-loop-stop behavior.
- Android and Desktop BASS polling loops still host their own coroutines, logging, notifications, wake locks, and cleanup side effects, but the loop policy knobs are now shared and named instead of hidden as platform-local booleans/constants.
- Added common `BassPlaybackPollingTest.kt` coverage for the Android and desktop policy presets.
- Size/reduction note for the polling policy slice:
  - `AndroidBassPlaybackEngine.kt` no longer hard-codes its 100 ms polling delay or source-end finish/duplicate-progress flags.
  - `DesktopBassPlaybackEngine.kt` no longer hard-codes its 250 ms polling delay or changed-only/finish-after-stop flags.
  - Shared domain grew intentionally with a small policy DTO and presets in `BassPlaybackPolling.kt`.
- Added shared `BassPlaybackCreationPlan` and `BassPlaybackActivationUpdate` for initial BASS playback creation decisions.
- Android and Desktop BASS engines now share mixer-use selection, replay-gain adjustment calculation, local-file URL classification, and created-playback state mapping.
- Platform engines still own platform path conversion (`Uri`/`File`) and native stream creation/release.
- Added common `BassPlaybackCreationPlannerTest.kt` coverage for mixer selection, replay-gain planning, local-file URL classification, and activation mapping.
- Size/reduction note for the playback creation slice:
  - `AndroidBassPlaybackEngine.kt` no longer calculates initial playback replay gain or mixer-use policy locally.
  - `DesktopBassPlaybackEngine.kt` dropped its local `CreatedPlayback` wrapper and initial replay-gain helper.
  - `PreparedBassPlaybackPlan` now also carries local-file URL classification so prepared creation paths share the same local/remote decision.
- Added shared `BassPlaybackStartPolicy`, `BassPlaybackStartPlan`, and `BassPlaybackPrePlayPlan` for start-position seek behavior.
- Android and Desktop BASS engines now share start-position normalization and policy for seek-before-play versus retry-after-play/mute-before-play behavior.
- Platform engines still own native seek/play/mute calls, Android retry timing, wake locks, logging, and polling startup.
- Added common `BassPlaybackStartPlannerTest.kt` coverage for Android retry/mute start seek policy, desktop seek-only policy, missing/zero start positions, and successful pre-play seek handling.
- Size/reduction note for the playback start slice:
  - `AndroidBassPlaybackEngine.kt` no longer decides start-position retry/mute behavior locally.
  - `DesktopBassPlaybackEngine.kt` no longer calls raw start-position normalization directly.
  - Shared domain grew intentionally with a small start planner that composes existing start-position normalization.
- Added shared `AudioPrefetchWork` and `planAudioPrefetchWork` so playlist prefetch source/provider/quality/depth eligibility and upcoming-track selection use one shared path.
- Android and Desktop playlist engines now ask the shared prefetch planner for executable work instead of locally repeating source-id, provider, quality, prefetch-depth, and upcoming-track checks.
- Platform playlist engines still own the correct side effects: job cancellation/launch, cache repository calls, Android logging, desktop cache stats publication, and sidecar execution.
- Added common `PlaybackPrefetchTest.kt` coverage for prefetch work creation and skip conditions.
- Size/reduction note for the prefetch planning slice:
  - `AndroidPlaylistEngine.kt` no longer locally builds the track prefetch list or initial stats for its audio prefetch job.
  - `DesktopPlaylistEngine.kt` no longer locally validates source/provider/quality/depth or builds its upcoming prefetch list.
  - Shared domain grew intentionally in `PlaybackPrefetch.kt` with a small work DTO and planner around the existing `audioPrefetchTracks` and `initialAudioPrefetchStats` helpers.
- Added shared `CurrentTrackSidecarWork` and `currentTrackSidecarWork` so current-track sidecar eligibility, radio-track skipping, source/provider/quality capture, and lyrics-load decisions use one shared work description.
- Android and Desktop playlist engines now consume the shared current-track sidecar work instead of separately choosing the current track, provider, stream quality, and lyrics visibility policy.
- Platform playlist engines still own the correct side effects: Android state maps and sidecar failure recording, desktop current-audio caching and callbacks, coroutine jobs, and actual waveform/tags/lyrics service calls.
- Added common `NowPlayingSidecarsTest.kt` coverage for current-track sidecar work creation and skip conditions.
- Size/reduction note for the current-track sidecar planning slice:
  - `AndroidPlaylistEngine.kt` no longer calls `sidecarPrepPlan` for the current-track sidecar path or re-reads quality before each sidecar step.
  - `DesktopPlaylistEngine.kt` no longer performs parallel provider/quality/current-track validation before launching current-track sidecars.
  - Shared domain grew intentionally in `NowPlayingSidecars.kt` with a focused work DTO and planner.
- Added shared `runCurrentTrackSidecars` so Android and Desktop use the same current-track sidecar execution sequence: active-session gate, optional audio prep, waveform prep, optional audio-tag prep, optional lyrics prep, and per-step failure callbacks.
- Android and Desktop playlist engines now pass platform-specific lambdas into the shared runner instead of owning parallel waveform/tags/lyrics sequencing.
- Platform playlist engines still own result application and platform-only behavior: Android state maps/logging/status rows, desktop current-audio cache call and ready callback.
- Added common `NowPlayingSidecarsTest.kt` coverage for runner ordering, inactive skips, lyrics gating, and audio-prep aborts.
- Size/reduction note for the current-track sidecar runner slice:
  - `AndroidPlaylistEngine.kt` no longer owns the imperative waveform -> tags -> lyrics control flow; it supplies sidecar service calls and state appliers.
  - `DesktopPlaylistEngine.kt` no longer owns its cache -> waveform -> lyrics sequence and dropped the local metadata-sidecar helper.
  - Shared domain grew intentionally with one callback-based runner that keeps platform effects out of domain.
- Added shared `PlaylistTrackStartWork` and `planPlaylistTrackStartWork` so Android and Desktop build the same engine playback request for selected tracks.
- Android track playback and Desktop playlist playback now share media id, replay-gain support gating, engine start-position filtering, cover-art carrying, playback source, and sidecar/prefetch start flags through one work model.
- Platform playback paths still own stream URL resolution, TLS/app state updates, queue mutation, notification/callback updates, and `playbackEngine.play`.
- Added common `PlaybackTargetPlanTest.kt` coverage for playlist track-start work and replay-gain support gating.
- Size/reduction note for the playlist track-start work slice:
  - `AndroidPlaybackOrchestration.kt` no longer hand-builds the track `PlaybackRequest` for normal track playback.
  - `DesktopPlaylistEngine.kt` no longer hand-builds the track `PlaybackRequest` or locally applies replay-gain support gating.
  - Shared domain grew intentionally with a focused DTO/function that composes existing playback target/effects planning.
- Added shared `PlaybackTrackStartEffectApplier` and `applyPlaybackTrackStartEffects` so Android and Desktop consume the same track-start effect sequence.
- Android and Desktop now share the gates and ordering for shuffle/radio clearing, now-playing presentation, favorite state, session save/report/open behavior, sidecar/progress resets, related/tags/lyrics loading, prefetch/sidecar prep starts, and notification metadata updates.
- Platform playback paths still own the correct state writes and side effects: Android notification controls/session restore/radio refill calls and Desktop route/callback/waveform state mutations remain adapter-owned.
- Added common `PlaybackTrackStartEffectsTest.kt` coverage for effect ordering and disabled-effect skips.
- Size/reduction note for the track-start effects application slice:
  - `AndroidPlaybackOrchestration.kt` no longer owns a long inline chain of effect-gated app state mutations after planning track start.
  - `DesktopPlaybackCallbackCoordinator.kt` no longer duplicates the same effect gates for now-playing/reset/report/refill behavior.
  - Shared domain grew intentionally with a callback-based applier that keeps platform effects outside domain while standardizing the command surface.
- Extended shared `planPlaybackProgressUpdate` so desktop playlist progress handling can use the same progress-planning vocabulary as Android.
- Desktop playlist callbacks now delegate pending-seek ignore/clear decisions, stable progress merging, save/report flags, and UI update throttling to the shared progress plan.
- Android keeps its existing progress call path unchanged, while the shared planner now supports both Android Auto/external progress publication and desktop UI-throttled playlist progress.
- Added common `PlaybackProgressTest.kt` coverage for stable desktop merge behavior, unknown-progress preservation, save/report flags, and UI update gating.
- Size/reduction note for the playlist progress planning slice:
  - `DesktopPlaybackCallbackCoordinator.kt` no longer directly combines pending-seek helpers, `mergeWith`, save/report calls, and UI throttling decisions.
  - `DesktopNaviampApp.kt` dropped stale direct imports for lower-level progress helpers.
  - Shared domain grew intentionally by extending one existing progress planner instead of adding a desktop-specific planner.
- Extended shared progress planning for desktop internet radio progress.
- `DesktopInternetRadioController` now delegates live-stream progress UI gating to `planPlaybackProgressUpdate` instead of directly calling the lower-level UI-throttle helper.
- The shared planner can preserve incoming live-stream progress without filling missing duration from previous playlist progress, keeping radio streams durationless.
- Added common `PlaybackProgressTest.kt` coverage for preserving incoming live-stream progress, disabling played reporting, and keeping prepare-next off for radio progress.
- Size/reduction note for the internet-radio progress slice:
  - `DesktopInternetRadioController.kt` no longer owns local progress UI-throttle decisions.
  - Shared domain grew by one small merge-mode option on the existing progress planner rather than creating a separate radio-only path.
- Added shared `InternetRadioStartApplier` and `applyInternetRadioStart` so Android and Desktop consume the same radio-start effect sequence.
- Android and Desktop now share the gates and ordering for recent-station persistence, radio/shuffle clearing, playback queue clearing, now-playing/station/metadata/progress/queue/status application, session save, open-now-playing, and notification metadata.
- Platform playback paths still own the correct state writes and side effects: Android session/TLS/notification-control updates and Desktop presentation-track/sidecar reset/route/settings writes remain adapter-owned.
- Added common `InternetRadioPlaybackTest.kt` coverage for the shared radio-start application sequence and the desktop presentation-track override.
- Size/reduction note for the internet-radio start application slice:
  - `AndroidPlaybackOrchestration.kt` no longer hand-applies the radio-start plan field by field.
  - `DesktopInternetRadioController.kt` no longer duplicates the same plan gates for recents, queue clearing, status, and route/session behavior.
  - Shared domain grew intentionally with a callback-based applier that keeps platform effects outside domain while standardizing the command surface.
- Added shared `InternetRadioMetadataUpdatePlan`, `planInternetRadioMetadataUpdate`, and `applyInternetRadioMetadataUpdate`.
- Android and Desktop now share radio metadata update decisions for stream metadata, optional presentation-track mutation, blank-title handling, and notification metadata updates.
- Platform playback paths still own the correct side effects: Android writes notification metadata, while Desktop mutates its synthetic now-playing radio track and leaves notifications alone.
- Added common `InternetRadioPlaybackTest.kt` coverage for notification-title planning, desktop presentation-track planning, blank-title skips, and applier ordering.
- Size/reduction note for the internet-radio metadata slice:
  - `AndroidPlaybackOrchestration.kt` no longer directly performs radio metadata title checks before notification updates.
  - `DesktopInternetRadioController.kt` no longer calls `internetRadioTrackWithMetadata` directly in its playback callback.
  - Shared domain grew intentionally with a small planner/applier around existing radio metadata mapping.
- Added shared `InternetRadioPlaybackRequestPlan` and `planInternetRadioPlaybackRequest` for radio playback request construction.
- Android and Desktop now share radio media id and replay-gain-off request decisions while keeping stream URL resolution platform-owned.
- Added common `InternetRadioPlaybackTest.kt` coverage for resolved stream URL, start-plan media id, and radio replay-gain-off behavior.
- Size/reduction note for the internet-radio playback request slice:
  - `AndroidPlaybackOrchestration.kt` no longer builds radio `PlaybackRequest` directly after playlist URL resolution.
  - `DesktopInternetRadioController.kt` no longer hand-builds its radio `PlaybackRequest` from the station URL.
  - Shared domain grew intentionally with a tiny request plan that composes the existing radio start plan.
- Split the shared internet-radio domain helpers into focused files by responsibility.
- `InternetRadioPlayback.kt` now owns only radio start planning and start effect application.
- `InternetRadioMetadata.kt`, `InternetRadioPlaybackRequest.kt`, `InternetRadioRecents.kt`, and `InternetRadioTracks.kt` now own metadata updates, playback request planning, recent-station list decisions, and synthetic radio track mapping.
- Size/reduction note for the internet-radio domain composition slice:
  - No platform behavior changed; this is a module-shape cleanup after the shared radio playback extraction.
  - Shared radio logic is now easier to compose and test as small focused pieces instead of accumulating in one broad file.
- Added shared `InternetRadioStationManager` for internet-radio station refresh, save, delete, cache invalidation, and reload-after-mutation behavior.
- Android and Desktop now use the same shared create-vs-update decision for internet-radio station saves.
- Platform code still owns the correct boundaries: coroutine launch, provider availability checks, and applying station/status state into Android or Desktop UI state.
- Added common `InternetRadioStationManagerTest.kt` coverage for create/update/delete mutation flows, cache invalidation, reload behavior, and shared status labels.
- Size/reduction note for the internet-radio station management slice:
  - `MainActivity.kt` no longer owns create-vs-update, invalidate, and reload orchestration for station save/delete.
  - `DesktopInternetRadioController.kt` no longer owns provider mutation sequencing for station save/delete, and refresh now goes through the same shared manager.
  - Shared domain grew intentionally with one focused manager around provider/cache operations.
- Added shared `InternetRadioRecentStationPlan`, `InternetRadioRecentStationApplier`, `planRememberInternetRadioStation`, and `applyRememberInternetRadioStation`.
- Android foreground service, Android app playback orchestration, and Desktop internet-radio controller now share recent internet-radio station ordering, saved-recents conversion, duplicate removal, and limit behavior.
- Platform code still owns the correct state side effects: Android app updates home state, Android foreground service persists Auto recents only, and Desktop mirrors recents into both local UI state and home content.
- Added common `InternetRadioPlaybackTest.kt` coverage for the shared recents applier.
- Size/reduction note for the internet-radio recents slice:
  - `AndroidPlaybackForegroundService.kt` no longer calls the raw saved-recents helper directly.
  - `DesktopInternetRadioController.kt` no longer uses the broader radio-start plan just to remember a station.
  - `InternetRadioPlayback.kt` now composes the shared recents plan instead of rebuilding recent station state inline.
- Added shared `renamePlaylistAndRefresh` and `deletePlaylistAndRefresh` helpers beside the existing queue-save and add-to-playlist mutation helpers.
- Android and Desktop playlist controllers now share playlist rename/delete provider mutation, cache invalidation, and refreshed playlist loading behavior.
- Platform code still owns UI state application: pending dialogs, selected playlist state, home playlist mirroring, route changes, and status display.
- Added common `PlaylistMutationsTest.kt` coverage for rename/delete refresh results.
- Size/reduction note for the playlist mutation orchestration slice:
  - `AndroidPlaylistsController.kt` no longer performs raw rename/delete provider calls, playlist cache invalidation, and playlist reload inline.
  - `DesktopPlaylistsController.kt` no longer performs raw rename/delete provider calls or launches a second playlist refresh after mutation.
  - Shared domain grew intentionally by extending the existing playlist mutation helper surface instead of adding a platform-specific service.
- Added shared `PlaylistListRefresh`, `playlistListRefresh`, `refreshPlaylistsAndPlanPreload`, and `loadPlaylistTracksForPreload`.
- Android and Desktop playlist controllers now share playlist-list refresh, cache/no-cache loading, and track-preload target planning.
- Platform code still owns state application: Android writes home state and async track maps; Desktop writes playlist state, home playlist projections, status, and async track maps.
- Added common `PlaylistMutationsTest.kt` coverage for playlist-list refresh and preload planning.
- Size/reduction note for the playlist refresh/preload slice:
  - `AndroidPlaylistsController.kt` no longer performs raw playlist list loading or preload target selection in its refresh path.
  - `DesktopPlaylistsController.kt` no longer duplicates cache/no-cache playlist loading branches or preload target selection.
  - Shared domain grew intentionally by composing existing playlist preload rules with provider/cache loading helpers.
- Added shared playlist playback start/load/ready planning with `preparePlaylistPlayback`.
- Android and Desktop playlist controllers now share pending playback gating, provider/cache track loading, selected-vs-loaded track selection, shuffle ordering, empty-status results, and recent-playlist ID planning.
- Platform code still owns playback execution and UI state writes: Android applies loaded tracks into `AndroidAppState`; Desktop applies loaded tracks, status, and calls the playlist engine.
- Added common `PlaylistMutationsTest.kt` coverage for playback start plans, load plans, ready queue plans, and the shared preparation path.
- Size/reduction note for the playlist playback preparation slice:
  - `AndroidPlaylistsController.kt` is net smaller for this slice (`33` added, `38` removed) because the controller no longer builds playlist load/ready behavior inline.
  - `DesktopPlaylistsController.kt` is still net larger (`38` added, `27` removed), mainly because the remaining desktop playback adapter path still has explicit state application and playback-engine side effects.
  - Shared domain and tests grew intentionally, but the next playlist cleanup should target larger controller deletion by moving UI-state application plans into shared reducers rather than adding smaller wrappers.
- Added shared playlist mutation application updates for queue-save, rename, delete, and home playlist projection.
- Android and Desktop playlist controllers now share queue-save status text, rename selected-playlist update decisions, delete selected-playlist/track-map/recent-ID decisions, and desktop home playlist projection.
- Platform code still owns the correct boundaries: applying values into Android state, applying values through Desktop setter lambdas, route changes after deleting the selected playlist, and playback execution.
- Added common `PlaylistMutationsTest.kt` coverage for queue-save, rename, delete, and home playlist application updates.
- Size/reduction note for the playlist state application slice:
  - `AndroidPlaylistsController.kt` shrank for this slice (`19` added, `27` removed).
  - `DesktopPlaylistsController.kt` shrank for this slice (`23` added, `37` removed).
  - `DesktopNaviampApp.kt` dropped two playlist-controller wiring arguments because `DesktopPlaylistsController` no longer needs radio-recents inputs to rebuild home playlists.
  - Shared domain and tests still grew, but this slice is the intended direction: fewer platform dependencies and less controller-owned mutation policy.
- Added shared smart playlist save/update state application and moved smart playlist cache invalidation into the shared provider helpers.
- Android smart playlist save/update and desktop smart playlist save/update now share refreshed playlist-track map updates, selected-playlist update decisions, and final status text.
- Desktop still owns the correct platform/provider boundary for native-token refresh from password and media-source persistence before saving a smart playlist.
- Added common `PlaylistMutationsTest.kt` coverage for smart playlist save/update application updates.
- Size/reduction note for the smart playlist state application slice:
  - `AndroidPlaylistsController.kt` grew slightly for this slice (`24` added, `21` removed) while adopting the shared state update.
  - `DesktopSmartPlaylistsController.kt` grew slightly (`24` added, `22` removed), but `DesktopNaviampApp.kt` dropped stale smart-playlist imports and one constructor argument (`0` added, `15` removed).
  - Net platform code moved down for the slice, and the duplicated smart playlist mutation policy is now shared.
- Added shared playlist detail open/refresh planning and a combined provider refresh/application helper.
- Android and Desktop playlist detail refresh now share provider/cache loading, playlist list replacement, selected-playlist/tracks updates, track-map updates, recent-ID planning, and detail status/error labels.
- Platform code still owns route changes, now-playing panel state, and local state assignment.
- Added common `PlaylistMutationsTest.kt` coverage for detail open plans, detail application updates, and provider refresh application.
- Size/reduction note for the playlist detail/open slice:
  - `AndroidPlaylistsController.kt` shrank slightly for this slice (`19` added, `21` removed).
  - `DesktopPlaylistsController.kt` shrank slightly (`12` added, `15` removed).
  - Shared domain and tests grew because the provider-refresh/result-composition path is now tested once in common code.
- Added shared playlist list state application and preload track-map application.
- Android and Desktop playlist controllers now share playlist-list status text, refreshed-list state updates, preload target planning, silent preload failure behavior, and cumulative playlist-track map merging.
- Platform code still owns coroutine launch, provider availability checks, Android home-state assignment, Desktop playlist/home/status assignment, and Desktop IO dispatching.
- Added common `PlaylistMutationsTest.kt` coverage for list state updates, error/loading labels, preload target selection, single preload map updates, and cumulative preload map updates.
- Size/reduction note for the playlist refresh state-application slice:
  - `AndroidPlaylistsController.kt` shrank for this slice (`17` added, `29` removed) and no longer owns the preload loop or track-map merge.
  - `DesktopPlaylistsController.kt` shrank for this slice (`19` added, `22` removed) and no longer owns the per-playlist preload loop or preload failure policy.
  - Shared domain and tests grew intentionally so the two platform controllers consume one tested playlist refresh/preload state surface.
- Added shared add-to-playlist state application around the existing provider mutation helper.
- Android media actions and Desktop playlist actions now share track deduplication, empty-track handling, loading/error labels, mutation refresh result shaping, dialog-close intent, playlist refresh application data, and final status fields.
- Platform code still owns the correct boundaries: Android state assignment, Desktop dialog target resolution from UI targets, Desktop home playlist projection, and route/dialog setters.
- Added common `PlaylistMutationsTest.kt` coverage for add-to-playlist state updates, empty-track updates, loading/resolving/error labels, and provider-backed state updates.
- Size/reduction note for the add-to-playlist state-application slice:
  - `AndroidMediaActionsController.kt` shrank for this slice (`9` added, `13` removed) and no longer owns track deduplication or empty-track branching.
  - `DesktopPlaylistsController.kt` shrank for this slice (`8` added, `10` removed) and consumes one shared add-to-playlist state update after platform target resolution.
  - `DesktopNaviampApp.kt` dropped one stale import of the lower-level mutation helper.
- Added shared playlist playback application planning with `preparePlaylistPlaybackApplication`.
- Android and Desktop playlist playback now share loaded-track map updates, loaded-track storage intent, empty-playlist status, playable queue selection, recent-playlist ID planning, and playback error text.
- Platform code still owns the actual playback execution, Android content-state writes, Desktop settings persistence for recent playlist IDs, and pending-action field assignment.
- Added common `PlaylistMutationsTest.kt` coverage for playback application updates, empty-playlist application, provider-level prepare/application composition, and shared error text.
- Size/reduction note for the playlist playback application slice:
  - `AndroidPlaylistsController.kt` shrank slightly for this slice (`15` added, `16` removed) while delegating loaded-track storage and empty/playable branching to shared domain.
  - `DesktopPlaylistsController.kt` shrank slightly for this slice (`11` added, `12` removed) and now consumes the same prepared playback application update as Android.
  - Shared domain and tests grew intentionally so the behavior is tested once and the next playlist step can target larger deletion around selected-playlist playback details.
- Added shared smart-playlist definition loading with `MediaProvider.loadSmartPlaylistDefinition`.
- Android smart-playlist loading and Desktop smart-playlist loading now share the provider ID lookup path for loading rules.
- Added common `PlaylistMutationsTest.kt` coverage for smart-playlist definition loading.
- Closed the playlist mutation/refresh orchestration checklist item because queue save, add-to-playlist, rename/delete, detail refresh, smart playlist save/update/load, preloading decisions, cache invalidation, home playlist reduction, and playlist playback preparation now all have shared paths.
- Remaining playlist code is platform adapter work: dialogs, route changes, settings persistence for recent playlists, platform state assignment, desktop smart-playlist authentication refresh, and playback execution.
- Added shared selected-playlist detail playback application planning.
- Desktop playlist detail playback now shares selected-track queue construction, shuffle handling, recent-playlist ID planning, index coercion, and empty-playlist status with the shared playlist mutation surface.
- Android already reaches selected playlist playback through the broader shared playlist playback application path, so no Android adapter change was needed in this slice.
- Added common `PlaylistMutationsTest.kt` coverage for selected playlist playback queue/index planning and empty-detail status.
- Size/reduction note for the selected playlist detail playback slice:
  - `DesktopPlaylistsController.kt` shrank for this slice (`7` added, `11` removed) and no longer owns selected playlist ready-plan/index coercion directly.
  - Shared domain and tests grew intentionally to keep this selected-detail behavior in the same playlist playback command surface as normal playlist playback.
- Tightened shared playlist detail refresh application with `PlaylistDetailsSelectionApplication`.
- Android and Desktop playlist controllers now consume the same concrete detail-selection application instead of checking a shared boolean and pulling nullable selected playlist fields apart themselves.
- Size/reduction note for the playlist detail selection-application slice:
  - `AndroidPlaylistsController.kt` shrank for this slice (`4` added, `7` removed) and no longer owns the selected-playlist null assertion or the refreshed-detail field mapping.
  - `DesktopPlaylistsController.kt` stayed line-flat (`4` added, `4` removed) but no longer maps refreshed selected playlist/tracks/status fields manually.
  - Shared domain grew by one small DTO that makes the application result more explicit and easier for thin platform adapters to consume.
- Verification passed: `.\gradlew.bat :core:domain:allTests`, `.\gradlew.bat :apps:android:compileDebugKotlin`, and `.\gradlew.bat "-Pnaviamp.bass.platform=windows-x64" :apps:desktop:compileKotlinDesktop`.
- Added shared selected-playlist detail playback preparation with `preparePlaylistDetailPlaybackApplication`.
- Desktop selected playlist detail playback now shares missing-track loading, loaded-track map updates, playable queue selection, requested-index coercion, empty-playlist status, and recent-playlist ID planning through the same provider-backed playlist application surface.
- Platform code still owns the correct side effects: desktop selected-track state assignment, pending playback action lifecycle, recent playlist persistence, radio continuation stop, shuffle snapshot cleanup, and `DesktopPlaylistEngine.playFrom`.
- Added common `PlaylistMutationsTest.kt` coverage for detail playback loading/index coercion and already-loaded selection reuse.
- Size/reduction note for the selected playlist detail playback preparation slice:
  - `DesktopPlaylistsController.kt` no longer uses the separate selected-detail-only application helper and now consumes a provider-backed shared update that can load tracks when detail content has not finished loading.
  - Android already reaches selected playlist playback through the broader shared playlist playback application path, so no Android adapter change was needed.
  - Shared domain replaced the older selected-detail-only helper with one explicit detail-playback update/helper that composes the provider-backed playlist playback preparation path.
- Verification passed: `./gradlew :core:domain:jvmTest --tests app.naviamp.domain.provider.PlaylistMutationsTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Tightened shared playlist delete state application with `PlaylistDeleteSelectionApplication`.
- Android and Desktop playlist delete handlers now consume the same concrete selection application instead of checking the deleted-selected flag and manually applying nullable selected-playlist fields.
- Platform code still owns route/state assignment side effects: Android content-state copy, Desktop route fallback to the playlist list after deleting the selected detail, playlist-track maps, recent IDs, and status assignment.
- Added common `PlaylistMutationsTest.kt` coverage for delete selection application and the no-selection-change case.
- Size/reduction note for the playlist delete selection-application slice:
  - `AndroidPlaylistsController.kt` no longer checks `deletedSelectedPlaylist` or maps selected playlist fields directly in the delete flow.
  - `DesktopPlaylistsController.kt` no longer maps deleted selected-playlist fields directly and only changes routes when the shared delete update emits a selection application.
  - Shared domain replaced exposed delete-selection booleans with a concrete optional selection application, matching the playlist detail refresh shape.
- Verification passed: `./gradlew :core:domain:jvmTest --tests app.naviamp.domain.provider.PlaylistMutationsTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Tightened playlist rename and smart playlist update selection application.
- Android and Desktop rename handlers now consume `PlaylistRenameSelectionApplication` instead of exposing selected-playlist-changed booleans and nullable selected playlist fields.
- Android and Desktop smart playlist update handlers now consume `PlaylistDetailsSelectionApplication` from `SmartPlaylistMutationStateUpdate` instead of checking selected-playlist-changed flags and manually mapping selected playlist/tracks.
- Platform code still owns platform side effects: Android content-state/track assignment, Desktop selected-playlist setters, selected status clearing, home/list state assignment, smart-playlist authentication refresh, and stats refresh ticks.
- Added common `PlaylistMutationsTest.kt` coverage for rename no-selection-change and smart-playlist update no-selection-change cases.
- Size/reduction note for the rename/smart selection-application slice:
  - `AndroidPlaylistsController.kt` no longer consumes selected-change booleans for rename or smart playlist update.
  - `DesktopPlaylistsController.kt` only applies rename selection when the shared update emits a rename selection application.
  - `DesktopSmartPlaylistsController.kt` consumes the same detail-selection DTO used by playlist detail refresh and no longer threads unused selected-track inputs into shared smart playlist update planning.
  - Shared domain now exposes concrete optional selection applications for rename, delete, detail refresh, and smart playlist update, leaving only shared internals to decide whether selection changed.
- Verification passed: `./gradlew :core:domain:jvmTest --tests app.naviamp.domain.provider.PlaylistMutationsTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Added shared playlist list/home application with explicit `PlaylistHomeProjection` policies.
- Android playlist refresh, delete, save-queue, smart-playlist save, and smart-playlist update now apply refreshed playlist lists through `playlistListApplication(..., PlaylistHomeProjection.All)`, preserving Android's full playlist list in home state.
- Desktop playlist refresh, add-to-playlist refresh, save-queue, detail refresh, rename, delete, and smart-playlist save/update now apply refreshed playlist lists through `playlistListApplication(..., PlaylistHomeProjection.RecentLimited)`, preserving the desktop recent-limited home projection.
- Platform code still owns concrete state assignment: Android writes `homeState`, Desktop writes playlist list and home content through setter lambdas.
- Added common `PlaylistMutationsTest.kt` coverage for all-playlists and recent-limited playlist home projections.
- Size/reduction note for the list/home application slice:
  - `DesktopPlaylistsController.kt` and `DesktopSmartPlaylistsController.kt` no longer duplicate `setPlaylists` plus `withPlaylists` mapping at each refreshed-list call site.
  - `AndroidPlaylistsController.kt` no longer directly copies refreshed playlist lists into `homeState.playlists` in playlist controller flows.
  - Shared domain now names the Android/Desktop projection difference instead of leaving it as implicit platform-local list assignment.
- Verification passed: `./gradlew :core:domain:jvmTest --tests app.naviamp.domain.provider.PlaylistMutationsTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Added shared queue-save state composition with `saveQueueAsPlaylistStateUpdate`.
- Android and Desktop queue-save handlers now ask the shared provider extension for the final playlist/status update instead of each composing `saveQueueAsPlaylistAndRefresh` plus `queuePlaylistSaveStateUpdate`.
- Platform code still owns queue-track capture and status target assignment: Android uses `playlistActionStatus`/`status`, Desktop uses connection status.
- Added common `PlaylistMutationsTest.kt` coverage for the new provider-backed queue-save state update.
- Size/reduction note for the queue-save state update slice:
  - `AndroidPlaylistsController.kt` and `DesktopPlaylistsController.kt` both dropped local refresh-to-state composition in the queue-save flow.
  - Shared domain keeps provider mutation, cache invalidation, playlist refresh, and final status text in one tested call path.
- Verification passed: `./gradlew :core:domain:jvmTest --tests app.naviamp.domain.provider.PlaylistMutationsTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Added shared smart playlist provider-backed state composition with `saveSmartPlaylistStateUpdate` and `updateSmartPlaylistStateUpdate`.
- Android and Desktop smart playlist save/update handlers now ask shared domain for the final playlist/track/status/selection update instead of each composing provider refresh plus state update.
- Platform code still owns smart-playlist-specific side effects: desktop authentication refresh, saved media-source persistence, password clearing, status target assignment, and stats refresh ticks; Android keeps state assignment and preload launch ownership.
- Added common `PlaylistMutationsTest.kt` coverage for provider-backed smart playlist save/update state helpers.
- Size/reduction note for the smart playlist state update slice:
  - `AndroidPlaylistsController.kt` and `DesktopSmartPlaylistsController.kt` both dropped local smart playlist refresh-to-state composition.
  - Shared domain now owns smart playlist create/update, cache invalidation, playlist refresh, selected-detail application planning, and final status text through one tested path.
- Verification passed: `./gradlew :core:domain:jvmTest --tests app.naviamp.domain.provider.PlaylistMutationsTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Added shared playlist playback start/completion application with `PlaylistPlaybackStartApplication` and `PlaylistPlaybackCompletionApplication`.
- Android and Desktop playlist playback now share pending-action start assignment, blocked-start status, and completion clearing instead of calling the raw pending-action helper directly from platform controllers.
- Platform code still owns coroutine launch, target status field assignment, track application, recent-playlist persistence, and actual playback execution.
- Added common `PlaylistMutationsTest.kt` coverage for start application and matching completion clearing.
- Size/reduction note for the playlist playback pending-state slice:
  - `AndroidPlaylistsController.kt` and `DesktopPlaylistsController.kt` no longer call `clearPendingPlaybackAction` directly in playlist playback flows.
  - Shared domain now owns the pending-action start/completion rules around playlist playback, leaving platform files to apply the typed result to their local state targets.
- Verification passed: `./gradlew :core:domain:jvmTest --tests app.naviamp.domain.provider.PlaylistMutationsTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Added shared prepared-playlist playback application with `PlaylistPlaybackPreparedApplication` and `PlaylistPlaybackWork`.
- Android and Desktop playlist playback now consume a typed optional playback work item instead of directly interpreting `firstTrack`, playback tracks, playback index, recent IDs, and empty-status fields from provider-backed preparation updates.
- Platform code still owns loaded-track state assignment, selected-playlist status clearing, recent-playlist persistence, and the actual playback engine call.
- Added common `PlaylistMutationsTest.kt` coverage for playable prepared work, empty prepared status, and selected-detail playback index carrying.
- Size/reduction note for the prepared playlist playback application slice:
  - `AndroidPlaylistsController.kt` and `DesktopPlaylistsController.kt` no longer branch directly on `PlaylistPlaybackApplicationUpdate.firstTrack` or manually thread playback tracks/recent IDs from that update.
  - Shared domain now owns the final prepared-playback classification while platform controllers keep only target-specific state writes and playback side effects.
- Verification passed: `./gradlew :core:domain:jvmTest --tests app.naviamp.domain.provider.PlaylistMutationsTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Added shared add-to-playlist application with `AddToPlaylistApplication`.
- Desktop add-to-playlist now consumes a typed application result for dialog close, add-to-playlist status, connection status, and optional playlist list/home projection.
- Platform code still owns target clearing, setter calls, and resolving the selected add target into concrete tracks before the provider mutation.
- Added common `PlaylistMutationsTest.kt` coverage for the add-to-playlist application and recent-limited playlist projection.
- Size/reduction note for the add-to-playlist application slice:
  - `DesktopPlaylistsController.kt` no longer branches directly on `AddToPlaylistStateUpdate.playlists` to decide list/home application.
  - Shared domain now owns the add-to-playlist mutation result shape and optional playlist-list projection, while Desktop applies the typed result to its UI targets.
- Verification passed: `./gradlew :core:domain:jvmTest --tests app.naviamp.domain.provider.PlaylistMutationsTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Added shared rename/delete playlist application wrappers with `PlaylistRenameApplication` and `PlaylistDeleteApplication`.
- Android and Desktop rename/delete handlers now consume typed application results for playlist list/home projection, selection application, updated track maps/recent IDs, and final status.
- Platform code still owns concrete side effects: Android content-state assignment, Desktop pending-dialog clearing, Desktop route changes after deleting the selected playlist, and setter calls.
- Added common `PlaylistMutationsTest.kt` coverage for rename/delete application wrappers and recent-limited playlist projection.
- Size/reduction note for the rename/delete application slice:
  - `AndroidPlaylistsController.kt` and `DesktopPlaylistsController.kt` no longer project raw rename/delete playlist lists directly in those mutation handlers.
  - Shared domain now owns the final rename/delete application shape, while platform files keep only target-specific UI state writes.
- Verification passed: `./gradlew :core:domain:jvmTest --tests app.naviamp.domain.provider.PlaylistMutationsTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.

## Library Sync/Freshness Progress

- Added shared `libraryFreshnessUpdate` in `LibraryFreshness.kt`.
- Android and Desktop library controllers now share provider scan-status loading, media-source previous-signature lookup, and freshness evaluation.
- Removed the desktop-local `MediaSourceRepository.libraryFreshnessFor` helper because shared domain now owns that provider/source composition.
- Platform code still owns the correct side effects: coroutine launch, UI status assignment, Android home-progress assignment during sync, Desktop list scrolling/paging/cache clearing, and marking scan signatures checked in the local index repository.
- Added common `LibraryFreshnessTest.kt` coverage for provider/source-backed freshness updates.
- Size/reduction note for this slice:
  - `AndroidLibraryController.kt` no longer manually builds `LibraryFreshness` from provider and media-source state.
  - `DesktopLibraryController.kt` no longer depends on a desktop-only freshness helper.
  - Shared domain now has one tested freshness orchestration entry point while the broader sync loop remains to be extracted.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.library.LibraryFreshnessTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Added shared library sync loop with `syncLibraryIndex`, `LibrarySyncProgress`, and `LibrarySyncResult`.
- Android and Desktop library sync now share sync-start marking, artist loading/upsert, album paging/upsert, optional album-track detail indexing, sync-completed marking, and progress event sequencing.
- Platform code still owns the correct differences: Android maps progress to mobile/home-state labels and skips full track indexing; Desktop maps progress to counter-style labels and enables album-track indexing through `ProviderResponseService`.
- Added common `LibrarySyncTest.kt` coverage for paged artist/album indexing, repository sync markers, progress phases, artist progress payloads, and optional album-track indexing.
- Size/reduction note for this slice:
  - `AndroidLibrarySync.kt` no longer owns artist/album provider paging or index writes.
  - `DesktopLibrarySync.kt` no longer owns the sync loop or album-track detail indexing loop.
  - Shared domain now owns the library index mutation sequence while platform wrappers preserve existing progress label shapes.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.library.LibrarySyncTest --tests app.naviamp.domain.library.LibraryFreshnessTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Added shared post-sync scan signature marking with `syncLibraryIndexAndMarkScanChecked`.
- Android and Desktop library sync no longer call `libraryScanStatus()` directly after a sync; shared domain now owns that provider/status-to-local-index sequence.
- Added common `LibrarySyncTest.kt` coverage for storing the provider scan signature after sync.
- Closed the library sync/freshness checklist item because auto-sync gating, freshness evaluation, scan-check marking, sync progress/result modeling, provider paging, index mutation, and paging-limit helpers now have shared paths.
- Remaining library code is platform adapter work: coroutine ownership, Android home-state progress application, Desktop list scrolling, Desktop snapshot/query state, cache clearing, connection status targets, and concrete UI state writes.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.library.LibrarySyncTest --tests app.naviamp.domain.library.LibraryFreshnessTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.

## Radio Progress

- Added shared non-seeded radio start result shaping with `RadioRequestStartResult` and `radioRequestStartResult`.
- Android and Desktop generic radio starts now share request loading classification, optional duplicate removal, first-track selection, failed/empty result modeling, and recent-radio cover-art enrichment.
- Platform code still owns the correct side effects: loading/empty/error status wording, recent-stream persistence, playback engine invocation, queue controller calls, coroutine ownership, and concrete app-state assignment.
- Added common `RadioRequestsTest.kt` coverage for ready, empty, and failed radio start results.
- Size/reduction note for this slice:
  - `AndroidRadioController.kt` no longer manually dedupes, selects the first loaded track, enriches recent stream cover art, and classifies empty/failure outcomes in the generic radio start helper.
  - `DesktopRadioController.kt` no longer manually loads a `RadioRequest`, checks empty results, enriches recent stream cover art, and catches generic load failures in its non-seeded radio path.
  - Shared domain now owns one tested result shape for non-seeded radio starts while the larger seeded radio, expansion, and refill flows remain to be extracted.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.radio.RadioRequestsTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Added shared seeded radio build/expansion result shaping with `SeededRadioBuildResult`, `SeededRadioExpansionResult`, `seededRadioBuildResult`, and `seededRadioExpansionResult`.
- Android `startAndroidSeededRadio` and Desktop `startSeeded` now share initial generated-queue construction, recent-radio cover-art enrichment, build failure classification, and expansion-load failure classification.
- Platform code still owns the correct side effects: seed-track playback, active-session checks, Android queue-state replacement, Desktop playlist-engine append, status target assignment, radio-refilling flags, and final loaded/cleared status.
- Added common `RadioRequestsTest.kt` coverage for seeded build ready/failure results and expansion ready/failure results.
- Size/reduction note for this slice:
  - `AndroidRadioController.kt` no longer hand-builds the initial generated queue or catches seeded expansion loads inside the reusable seeded-start helper.
  - `DesktopRadioController.kt` no longer hand-loads seeded initial radio tracks or catches seeded expansion loads inside `startSeeded`.
  - Shared domain now owns generated seeded queue/result shaping, while convert-current-track-to-radio and upcoming-track replacement remain as the next generated-queue extraction target.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.radio.RadioRequestsTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Reused shared seeded expansion/build results for radio refill and generated-upcoming queue conversion.
- Android `refillAndroidRadioIfNeeded`, Android `startAndroidTrackRadioQueue`, Desktop `refillIfNeeded`, and Desktop `convertCurrentTrackToRadio` now share provider-load failure classification and generated queue construction through the domain radio result helpers.
- Platform code still owns the correct side effects: Android `PlaybackQueueController` replacement/append, Desktop `DesktopPlaylistEngine` upcoming replacement/append, active-session checks, current-track fallback routing, and concrete status targets.
- Size/reduction note for this slice:
  - `AndroidRadioController.kt` no longer hand-catches refill loads or hand-builds generated track-radio queues in `startAndroidTrackRadioQueue`.
  - `DesktopRadioController.kt` no longer hand-catches refill, initial upcoming replacement, or upcoming expansion loads.
  - Shared domain result helpers now cover non-seeded starts, seeded starts, seeded expansion, refill, and generated-upcoming track-radio loads.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.radio.RadioRequestsTest --tests app.naviamp.domain.radio.RadioQueueRulesTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Added shared seed lookup result classification with `RadioSeedResult` and `radioSeedResult`.
- Android and Desktop artist, album, artist-mix, album-mix, and random-album seed paths now consume the same ready/missing/failed result shape around platform-specific seed adapters.
- Closed the radio/generated-queue checklist item because request construction, seed result classification, generated queue construction, refill planning, append/replace planning, recent-stream enrichment, and radio load/expansion result modeling now have shared paths.
- Remaining radio code is platform adapter work: coroutine ownership, provider/cache adapter construction, Android app-state writes, Android queue-controller calls, Desktop playlist-engine calls, Desktop random-album album lookup, route fallback, playback execution, and status target assignment.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.radio.RadioRequestsTest --tests app.naviamp.domain.radio.RadioQueueRulesTest --tests app.naviamp.domain.radio.RadioSeedsTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.

## Playback Command Progress

- Added shared live volume command planning with `PlaybackVolumeCommand` and `playbackVolumeCommand`.
- Android now-playing volume changes and Desktop volume application now share software-volume support handling and 0-100 clamping before calling the playback engine.
- Platform code still owns the correct side effects: Android UI state assignment, Desktop playback settings persistence, and concrete playback-engine calls.
- Added common `PlaybackCommandsTest.kt` coverage for clamping and unsupported software-volume behavior.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.playback.PlaybackCommandsTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Added shared play/pause toggle command planning with `PlaybackPlayPauseCommand` and `playbackPlayPauseCommand`.
- Android Auto play/pause now asks shared domain whether to pause, resume, start/restore playback, or no-op; platform code still executes restore, internet-radio start, track playback, and engine calls.
- Added common `PlaybackCommandsTest.kt` coverage for play/pause command mapping.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.playback.PlaybackCommandsTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Desktop full-player and mini-player play/pause callbacks now converge on the same shared `playbackPlayPauseCommand` planner before executing engine pause/resume or current-selection playback.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.playback.PlaybackCommandsTest` and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Android Auto foreground-service repeat cycling now routes through shared `nextRepeatMode`; the service-local repeat enum remains only for notification/session labels.
- Existing shared repeat tests cover the Off -> Queue -> Track -> Off policy, and Android compile confirms the service adapter wiring.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.playback.PlaybackQueueManagerTest --tests app.naviamp.domain.playback.PlaybackControlDecisionsTest` and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Closed the playback command checklist item because play/pause, seek, previous/next, shuffle, repeat, volume, queue append/replace/update, sleep timer expiry, progress planning, track-start effects, and BASS start planning now route through shared domain planners or queue controllers.
- Remaining playback platform code is adapter work: concrete playback engine calls, BASS/native implementation, Android foreground service/media-session/notification behavior, audio focus, lifecycle/coroutine ownership, provider/cache adapters, UI state assignment, route changes, and session persistence wiring.
- The next cleanup step should decompose the root app shells now that media actions, playlists, library sync, radio, and playback command planning have shared service boundaries.

## Root App Decomposition Progress

- Moved desktop surface/window chrome rendering into `DesktopAppSurface.kt` and now-playing route wiring into `DesktopNowPlayingRoute.kt`.
- `DesktopNaviampApp.kt` dropped from 1,958 lines to 1,751 lines in this slice; the extracted files are route/surface adapters and do not change app behavior.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Moved desktop internet-radio stream artwork lookup into `DesktopRadioArtworkEffects.kt`, keeping provider search and artwork-key branching out of the root app.
- `DesktopNaviampApp.kt` dropped to 1,739 lines after the artwork-effect extraction.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Moved desktop sleep-timer snapshot, selection, and expiry polling adapters into `DesktopSleepTimerEffects.kt`; shared domain sleep-timer rules remain in `core/domain`.
- `DesktopNaviampApp.kt` dropped to 1,735 lines after the sleep-timer adapter extraction.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.playback.SleepTimerTest` and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Moved Android shell state/action contracts into `AndroidAppShellContracts.kt`, leaving `AndroidAppShell.kt` focused on shared shell UI mapping and action construction.
- `AndroidAppShell.kt` dropped from 890 lines to 715 lines in this slice.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved the Android `NaviampSharedAppShell` forwarding wrapper into `AndroidAppShellContent.kt`.
- `AndroidAppShell.kt` dropped to 537 lines after separating shell contracts and content forwarding from state/action construction.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved desktop artist/album/genre mix-builder service construction into `DesktopMixBuilderServices.kt`, keeping provider/cache fallback lambdas out of `DesktopNaviampApp.kt`.
- `DesktopNaviampApp.kt` dropped to 1,685 lines after the mix service adapter extraction.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Moved desktop artist/album/genre mix-builder async actions into `DesktopMixBuilderController.kt`; the root now wires mix state and delegates suggestion loading, searching, selection, removal, and reset behavior.
- `DesktopNaviampApp.kt` dropped to 1,509 lines after the mix action controller extraction.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Moved desktop stats-for-nerds info aggregation into `DesktopStatsForNerdsInfoBuilder.kt`, keeping cache/media-source/playback diagnostic model assembly out of the root app.
- `DesktopNaviampApp.kt` dropped to 1,496 lines after the stats helper extraction.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Moved lyrics offset loading and persistence policy into shared core `LyricsOffsetController`; Android and Desktop now call the same saved-offset application and offset-save path.
- `DesktopNaviampApp.kt` is 1,516 lines after the shared lyrics-offset wiring; the platform code now only supplies current source/track/lyrics state and applies the shared result.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.lyrics.LyricsOffsetControllerTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Moved Android artist/album/genre mix-builder service construction into `AndroidMixBuilderServices.kt`, matching the desktop mix-builder service boundary.
- `MainActivity.kt` dropped from 2,112 lines to 2,068 lines after the Android mix service adapter extraction.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android artist/album/genre mix-builder async actions into `AndroidMixBuilderController.kt`; `MainActivity.kt` now wires services and delegates suggestion loading, search, selection, removal, and reset behavior.
- `MainActivity.kt` dropped to 1,857 lines after the Android mix action controller extraction; `AndroidMixBuilderController.kt` is 234 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android sleep-timer snapshot, selection, and expiry polling adapters into `AndroidSleepTimerEffects.kt`, matching the desktop sleep-timer extraction shape.
- `MainActivity.kt` dropped to 1,850 lines after the Android sleep-timer adapter extraction; `AndroidSleepTimerEffects.kt` is 78 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android internet-radio stream artwork lookup into `AndroidRadioArtworkEffects.kt`, matching the desktop radio artwork effect boundary.
- `MainActivity.kt` dropped to 1,830 lines after the Android artwork-effect extraction; `AndroidRadioArtworkEffects.kt` is 43 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android related-track radio loading into `AndroidRadioController.kt`, keeping the root app from calling `RadioService` directly for now-playing related tracks.
- `MainActivity.kt` dropped to 1,820 lines after the related-track radio helper extraction.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android now-playing lyrics loading, lyrics offset persistence, visible-lyrics reloads, and audio tag loading/cache warming into `AndroidNowPlayingSidecarController.kt`.
- `MainActivity.kt` dropped to 1,755 lines after the Android now-playing sidecar controller extraction; `AndroidNowPlayingSidecarController.kt` is 122 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android internet-radio station save/delete provider mutations into `AndroidRadioController.kt`, leaving the root app to delegate shell callbacks only.
- `MainActivity.kt` dropped to 1,717 lines after the Android internet-radio mutation extraction.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android restore/connect/autoconnect orchestration into `AndroidConnectionSessionController`, keeping saved connection/session restore wiring out of `MainActivity.kt`.
- `MainActivity.kt` dropped to 1,694 lines after the Android connection session extraction.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android detail/playlist close behavior, app-back handling, and mix-builder route selection into `AndroidNavigationController.kt`.
- `MainActivity.kt` dropped to 1,641 lines after the Android navigation controller extraction; `AndroidNavigationController.kt` is 60 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android report-now-playing and report-played provider submissions into `AndroidPlaybackReportController.kt`.
- `MainActivity.kt` dropped to 1,599 lines after the Android playback reporting extraction; `AndroidPlaybackReportController.kt` is 67 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android playlist track loading, playlist-to-queue, playlist-to-playlist, and playlist download orchestration into `AndroidPlaylistsController.kt`.
- `MainActivity.kt` dropped to 1,542 lines after the Android playlist track action extraction.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android now-playing go-to-album provider lookup and library fallback into `AndroidArtistController.kt`.
- `MainActivity.kt` dropped to 1,516 lines after the Android now-playing album navigation extraction.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android shell resume, shuffle, current-track radio, and queue-item radio actions into `AndroidShellPlaybackController.kt`.
- `MainActivity.kt` dropped to 1,472 lines after the Android shell playback controller extraction; `AndroidShellPlaybackController.kt` is 82 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android downloaded-track, known-track, playlist-track, and now-playing playlist callback wrappers into `AndroidTrackActionController`.
- `MainActivity.kt` dropped to 1,419 lines after the Android track action controller extraction; `AndroidMediaActionsController.kt` is 457 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android artist detail/popular/similar/album action wrappers into `AndroidArtistActionController`.
- `MainActivity.kt` dropped to 1,348 lines after the Android artist action controller extraction; `AndroidArtistController.kt` is 435 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android track/download/playlist download and remove-download callback wrappers into `AndroidDownloadActionController`.
- `MainActivity.kt` dropped to 1,315 lines after the Android download action controller extraction; `AndroidDownloadController.kt` is 219 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android playlist open/play/mutation/smart-playlist and add-to-playlist callback wrappers into `AndroidPlaylistActionController`.
- `MainActivity.kt` dropped to 1,248 lines after the Android playlist action controller extraction; `AndroidPlaylistsController.kt` is 622 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android connection-form, playback-settings, redownload, cache-clear, library-clear, and reset-database callback wrappers into `AndroidSettingsMaintenanceController`.
- `MainActivity.kt` dropped to 1,209 lines after the Android settings/maintenance controller extraction; `AndroidMaintenanceController.kt` is 178 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android shell selected-track, album, home/recent radio, station mutation, now-playing navigation, and rating callback wrappers into `AndroidShellMediaController`.
- `MainActivity.kt` dropped to 1,088 lines after the Android shell media controller extraction; `AndroidShellMediaController.kt` is 172 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Wired Android search, lyrics, audio-tag, and playback-report callbacks directly to existing controllers instead of root forwarding functions.
- `MainActivity.kt` dropped to 1,064 lines after the direct-controller forwarding cleanup.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android artist/album/genre mix play actions into `AndroidMixBuilderController`.
- `MainActivity.kt` dropped to 1,027 lines after the Android mix play action extraction; `AndroidMixBuilderController.kt` is 284 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android sleep-timer snapshot, selection, cancel, tick, and expiry handling into `AndroidSleepTimerController`.
- `MainActivity.kt` dropped to 998 lines after the Android sleep timer controller extraction; `AndroidSleepTimerEffects.kt` is 122 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android Auto play/pause, command, media-id, pending request, notification-control, and Auto seek handling into `AndroidAutoAppController`.
- `MainActivity.kt` dropped to 868 lines after the Android Auto app controller extraction; `AndroidAutoAppController.kt` is 198 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android playback progress, session saving, play-track, internet-radio playback, seek, adjacent-track, recent-radio stream, and seeded track/album radio wrappers into `AndroidPlaybackAppController`.
- `MainActivity.kt` dropped to 724 lines after the Android playback app controller extraction; `AndroidPlaybackAppController.kt` is 188 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android active-queue lookup, known-track lookup, queue append, related-track loading, artist-detail opening, notification favorite state, metadata update application, and current-favorite toggling into `AndroidMediaAppController`.
- `MainActivity.kt` dropped to 694 lines after the Android media app controller extraction; `AndroidMediaAppController.kt` is 61 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android shell-action callback assembly into `AndroidMainShellActions.kt`, leaving `MainActivity.kt` to compose controllers and pass them into one shell wiring helper.
- `MainActivity.kt` dropped to 611 lines after the Android shell-action wiring extraction; `AndroidMainShellActions.kt` is 132 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved Android current stream-quality selection into `AndroidPlaybackQualityController` and removed the stale root download-quality wrapper.
- `MainActivity.kt` dropped to 605 lines after the Android playback quality extraction; `AndroidPlaybackQualityController.kt` is 13 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved sleep-timer selection, cancel, tick, and expiry coordination into shared core `SleepTimerController`; Android and Desktop now supply platform state/effects to the same controller.
- Moved sleep-timer expiry polling into shared UI `NaviampSleepTimerExpiryEffect`; deleted the platform-specific `AndroidSleepTimerEffects.kt` and `DesktopSleepTimerEffects.kt` wrappers.
- `MainActivity.kt` is 617 lines and `DesktopNaviampApp.kt` is 1,494 lines after the shared sleep-timer wiring; `SleepTimer.kt` is 255 lines and the shared UI effect is 30 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.playback.SleepTimerTest --tests app.naviamp.domain.lyrics.LyricsOffsetControllerTest`, `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`, and `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Restored the packaged desktop volume-bar thumb visibility in shared `NaviampNowPlayingUi`; the common volume handle now has a halo, larger radius, and inner marker so it stays visible against the active track.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:ui:jvmTest :apps:android:compileDebugKotlin :apps:desktop:compileKotlinDesktop`.
- Moved Android root controller-trigger effects into `AndroidMainEffects`, including search loading, audio-tag loading, Auto pending request consumption, notification favorite refresh, back handling, mix-builder initial suggestions, playlist detail auto-refresh, auto-connect, and sleep-timer expiry wiring.
- `MainActivity.kt` dropped from 605 lines to 549 lines after the Android main effects extraction; `AndroidMainEffects.kt` is 107 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Folded the remaining desktop root mix-builder initial-suggestion effects into `DesktopAppControllerEffects`, leaving `DesktopNaviampApp.kt` without direct `LaunchedEffect` wiring.
- `DesktopNaviampApp.kt` dropped from 1,494 lines to 1,480 lines after the desktop controller-effect consolidation; `DesktopAppControllerEffects.kt` is 145 lines.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Removed direct desktop playback forwarding wrappers from `DesktopNaviampApp.kt`; root route wiring now calls `DesktopPlaybackController` directly for seek, previous/next, shuffle, repeat, queue navigation, playback-session save, and reporting callbacks.
- `DesktopNaviampApp.kt` dropped from 1,484 lines to 1,433 lines after the direct-controller forwarding cleanup.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Moved playback-settings maintenance into shared domain `PlaybackSettingsMaintenanceController`; Android and Desktop now use the same path for applying effective playback settings, saving them, reloading lyrics sidecars when LRCLIB changes, and redownloading existing downloads after download-quality changes.
- Deleted the desktop-only settings-maintenance wrapper before committing it; desktop redownload execution now lives on the existing `DesktopDownloadsController`, matching Android's existing download action boundary.
- `DesktopNaviampApp.kt` dropped from 1,433 lines to 1,413 lines after the shared settings-maintenance wiring.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.settings.PlaybackSettingsTest :apps:android:compileDebugKotlin :apps:desktop:compileKotlinDesktop`.
- Replaced Android's duplicated download-refresh result branches with shared `shouldRefreshDownloadsAfter`, matching the redownload path and Desktop's shared download refresh decision.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:android:compileDebugKotlin`.
- Moved artist/album detail provider loading with library fallback, plus track-to-artist/album mapping, from desktop-local media helpers into shared domain media helpers.
- Android artist and album detail loading now use the shared `loadArtistDetails`/`loadAlbumDetails` recovery paths instead of local provider/fallback blocks; Desktop album/artist controllers import the same shared detail loaders.
- `DesktopMediaDetails.kt` dropped to 53 lines and now only contains desktop route-navigation helpers.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.media.MediaDetailFallbacksTest :apps:desktop:desktopTest --tests app.naviamp.desktop.DesktopMediaDetailsTest :apps:android:compileDebugKotlin :apps:desktop:compileKotlinDesktop`.
- Moved artist popular-track and similar-artist fetch/result shaping into shared media detail helpers; Android and Desktop artist controllers now only set loading state, call the shared loader, and apply the returned tracks/artists/status to their platform state.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.media.MediaDetailFallbacksTest :apps:android:compileDebugKotlin :apps:desktop:compileKotlinDesktop`.
- Added shared download execution helpers that wrap `DownloadService`, preserve the shared refresh decision, and optionally return refreshed cache stats; Android and Desktop download controllers now supply only platform inputs and apply the returned refresh/status results.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.cache.DownloadPlansTest :apps:android:compileDebugKotlin :apps:desktop:compileKotlinDesktop`.
- Added shared `applyPlaybackQueueUpdate` so Android and Desktop media actions use the same status-and-replace behavior after queue append/play-next planning; platform code still owns the concrete queue controller/engine calls.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.playback.PlaybackQueueManagerTest :apps:android:compileDebugKotlin :apps:desktop:compileKotlinDesktop`.
- Added shared `addTracksToPlaylistApplication` so Android and Desktop both get the add-to-playlist dialog/status/home-playlist application from core after resolving platform-specific targets.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.provider.PlaylistMutationsTest :apps:android:compileDebugKotlin :apps:desktop:compileKotlinDesktop`.
- Reused shared `applyPlaybackQueueUpdate` in Desktop playlist add-to-queue and play-next paths, removing the remaining local status/change/replacement branch in that controller.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.playback.PlaybackQueueManagerTest :apps:desktop:compileKotlinDesktop`.
- Added shared `saveQueueAsPlaylistApplication` so Android and Desktop both apply saved-queue playlist refreshes through the same playlist-list/home-content application model.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.provider.PlaylistMutationsTest :apps:android:compileDebugKotlin :apps:desktop:compileKotlinDesktop`.
- Reused shared `selectedPlaylistTracksForPlayback` for Android playlist add/download track selection, replacing duplicate selected-playlist-versus-cache branching.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.provider.PlaylistMutationsTest :apps:android:compileDebugKotlin`.
- Removed stale desktop root imports left by earlier extractions and collapsed repeated desktop lyrics-offset assignment wiring into one root helper that still uses the shared `LyricsOffsetController`.
- `DesktopNaviampApp.kt` dropped from 1,413 lines to 1,360 lines without adding another platform wrapper file.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Deleted the single-use `DesktopNowPlayingRoute` pass-through wrapper; `DesktopNaviampApp.kt` now calls the existing `DesktopNowPlayingPanel` directly, and the panel owns its queue-derived now-playing view inputs.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Moved the seek replay source decision from desktop-prefixed code into shared playback decisions; Android and Desktop now use the same helper to decide when provider streams need replay-on-seek, and the stale `DesktopPlaybackProgress.kt` helper was deleted.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.playback.PlaybackSeekDecisionsTest :apps:android:compileDebugKotlin :apps:desktop:compileKotlinDesktop`.
- Moved home-station radio action parsing into shared `homeStationRadioAction`; Desktop route content and Android home-station radio startup now use the same typed station action mapper.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.radio.RecentRadioActionsTest :apps:android:compileDebugKotlin :apps:desktop:compileKotlinDesktop`.
- Moved home mix-builder album candidate selection into shared `HomeContent.mixBuilderAlbumCandidates`; Android and Desktop mix-builder services now share the same random/mix/recent/frequent album policy before provider fallback.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.home.HomeServiceTest :apps:android:compileDebugKotlin :apps:desktop:compileKotlinDesktop`.
- Moved home mix-builder artist candidate selection into shared `HomeContent.mixBuilderArtistCandidates`; Android and Desktop artist mix builders now share home artist de-duping before provider fallback.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.home.HomeServiceTest :apps:android:compileDebugKotlin :apps:desktop:compileKotlinDesktop`.
- Moved Android/Desktop mix-builder service composition into shared artist, album, and genre factory functions; platform files now only adapt local storage lookups and cached provider album details, while shared core owns provider fallback, home fallback, and album-track fallback ordering.
- Changed mix-builder service wiring to pass `HomeContent` as a live lambda so remembered services do not capture stale home snapshots.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:domain:jvmTest --tests app.naviamp.domain.mixbuilder.MixBuilderServiceFactoriesTest :apps:android:compileDebugKotlin :apps:desktop:compileKotlinDesktop`.
- Moved the remaining desktop mix-builder item-id selection/removal and query-reset handling into `DesktopMixBuilderController`, matching the Android controller boundary and trimming `DesktopNaviampApp.kt` to 1,354 lines without adding another wrapper file.
- Moved desktop route queue/add-to-playlist target construction behind typed `DesktopPlaylistsController` methods for tracks, albums, artists, and playlists; route/root files now call controller actions instead of constructing `AddToPlaylistTarget` values throughout the UI wiring.
- Moved desktop connection-form new/edit/connect-saved/cancel handling into the existing `DesktopConnectionLifecycleController`, leaving the root to provide state adapters and trimming `DesktopNaviampApp.kt` to 1,331 lines.
- Moved desktop home item and search track action resolution into `DesktopAppActions`, with `DesktopMediaActionsController` exposing indexed search-track lookup; `DesktopAppRouteContent.kt` now delegates home/search IDs and indices instead of looking up albums, playlists, stations, or tracks directly.
- Finished the remaining `DesktopAppRouteContent` action cleanup by moving current-album fallback actions, selected playlist detail actions, popular-track index lookup, and library-tab refresh/scroll sequencing into existing app, playlist, and library controllers; the route content is now 543 lines and contains no direct item lookup or coroutine scroll logic.
- Removed desktop root radio-forwarding helpers for radio continuation stop and queue refill; `DesktopNaviampApp.kt` now passes `DesktopRadioController` method references directly to internet radio, connection lifecycle, playback callbacks, downloads, and playlist controllers.
- `DesktopNaviampApp.kt` is now 1,326 lines after the direct radio-controller callback cleanup.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Note for the next desktop root slice: larger route/player helper extractions that keep very large composable parameter lists still trip the JVM `MethodTooLargeException` in `NaviampApp`; the next split should reduce root setup/state ownership or use lower-arity state holders instead of adding parameter-heavy composable pass-throughs.
- Moved desktop play/pause command application into a top-level desktop playback helper without adding controller constructor dependencies, so the root no longer imports the core play/pause enum/decision helper directly.
- Moved desktop recent-radio stream/home-content application into a top-level desktop radio helper without adding a parameter-heavy composable or controller pass-through.
- `DesktopNaviampApp.kt` is now 1,323 lines after the compiler-safe playback/radio helper cleanup.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Moved desktop queue-index selection behavior into a small playback helper, and moved desktop mix-builder item UI mapping into mix-builder helpers so `DesktopNaviampApp.kt` no longer owns those shared UI mapper imports.
- `DesktopNaviampApp.kt` is now 1,321 lines after the queue-selection and mix-builder mapper cleanup.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Moved desktop lyrics-offset application and save handling into the existing `DesktopNowPlayingController`, matching Android's now-playing sidecar boundary while still using the shared core `LyricsOffsetController`.
- `DesktopNaviampApp.kt` is now 1,302 lines after the now-playing lyrics-offset cleanup.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Moved desktop mix-builder state into `DesktopMixBuilderController`; the app root no longer owns mix query, selection, suggestion, loading/status, route UI model construction, or mix play queue construction.
- `DesktopNaviampApp.kt` is now 1,209 lines after the mix-builder state ownership cleanup.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Moved desktop search query/results/status/loading state into `DesktopSearchController`; route content, metadata actions, and search effects now read/write through the controller instead of root variables.
- `DesktopNaviampApp.kt` is now 1,200 lines after the desktop search state cleanup.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Moved desktop download status/refresh bookkeeping into `DesktopDownloadsController`, and moved library query/tab/snapshot/status/sync/page-limit state into `DesktopLibraryController`.
- `DesktopNaviampApp.kt` is now 1,168 lines after the download and library state cleanup.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Moved desktop internet-radio station list, status, and recent-station state into `DesktopInternetRadioController`; the root now reads the controller for now-playing, route content, home loading, and action lookup.
- `DesktopNaviampApp.kt` is now 1,158 lines after the internet-radio state cleanup.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Moved desktop playlist list/detail/dialog/pending-playback state into `DesktopPlaylistsController`, with smart-playlist save/update applying through that controller instead of root playlist lambdas.
- Moved desktop album and artist detail state into their existing controllers, including artist popular-track and similar-artist detail state.
- Moved desktop now-playing analysis sidecar state into `DesktopNowPlayingController`, including waveform, audio tags, lyrics/status, related tracks, and waveform reload token.
- `DesktopNaviampApp.kt` is now 1,053 lines after the playlist/detail/now-playing state cleanup.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :apps:desktop:compileKotlinDesktop`.
- Moved desktop now-playing presentation state into `DesktopNowPlayingPresentationState`, covering visualizer selection/visibility, visualizer frame, resolved cover-art URL, radio track artwork lookup cache, and animated player background colors.
- Moved duplicated Android/Desktop radio artwork lookup effects into shared `NaviampRadioArtworkLookupEffect`, and moved visualizer setting lookup/visibility gating into shared UI helpers so both platforms use the same small rules.
- This slice keeps platform helpers limited to shell/presentation state; duplicated Android/Desktop product behavior should continue moving into `core/domain` or `core/ui`, not into platform-only helpers.
- `DesktopNaviampApp.kt` is now 1,047 lines after the now-playing presentation state cleanup; `DesktopNowPlayingPresentationState.kt` is 141 lines.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Moved radio now-playing UI construction into shared `InternetRadioStation.toRadioNowPlayingUi`, so Android and Desktop share stream-title, artwork, state-label, station-list, and radio playback-state mapping while still passing platform capabilities such as volume and play/pause support.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Moved shared now-playing item-list and related-tab label mapping into `core/ui`, so Android and Desktop share back-to/up-next/related row construction and Sonic/Related empty-state copy while preserving platform-specific item IDs.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Moved track now-playing capability decisions and embedded-tag fallback rows into shared `core/ui`, including play/pause eligibility, live-stream gating, seek/volume, repeat, track radio, playlist actions, favorite/rating support, lyrics availability, and the cached-audio loading row.
- Desktop now passes provider track-radio capability into the shared mapper instead of assuming track radio locally; Android and Desktop still supply their platform-specific queue/save/engine capability inputs.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Moved shared track now-playing UI assembly into `Track.toTrackNowPlayingUi`, so Android and Desktop no longer hand-fill the large `NowPlayingTrackUiConfig` in parallel. Platform code now computes platform-specific inputs such as queue IDs, engine support, lyrics state, and playlist choices, then passes them into the shared mapper.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Moved now-playing row target IDs and item-to-track resolution into shared `core/ui` helpers. Desktop no longer parses `queue:`/`related:` IDs or builds a local item-track map in the panel, and Android now-playing row actions resolve through the same target helper instead of assuming raw track IDs at each callback.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Added shared now-playing item action requests so the common now-playing list emits one typed action/target payload for radio, play-next, add-to-queue, playlist-add/create, and download actions. Android and Desktop now handle row actions through that shared request shape while preserving platform-specific playback, playlist, and download adapters.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Moved the remaining now-playing section construction into shared `NowPlayingSectionsUi` builders. Android and Desktop now share back-to/up-next/related row mapping, related/Sonic labels, queue wrap previous/next flags, and shuffle availability, while explicitly preserving Android's raw track row IDs and Desktop's queue/related index row IDs.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Added shared track-row action requests for normal media rows, matching the now-playing request pattern. Album detail, artist popular tracks, search results, and playlist detail rows now route select/add/download/playlist actions through typed `SharedTrackRowActionRequest` handlers inside shared UI instead of repeating direct callback wiring at every row.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Added shared resolved now-playing item actions with source and track resolution. Android and Desktop now consume the same `ResolvedNowPlayingItemAction` shape for queue/related/raw-track actions instead of each platform parsing targets or carrying separate related-item checks.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Extended shared track-row action requests with playlist payloads and a shared dispatcher/resolver. Album and artist popular track dialogs now route playlist add/create through the same typed request path, and Android's album/popular/search track queue/download/playlist wrappers now delegate to one `handleTrackAction` implementation.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Threaded `SharedTrackRowActionRequest` through the shared app shell boundary. Android's shell contracts/content/top-level wiring now expose one typed track-action callback for album, artist popular, search, and playlist row actions, while route-specific selection remains separate.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Added shared media-item action requests and dispatch helpers for non-track rows. `SharedMediaRow` now emits typed select/favorite actions, playlist list rows route play/shuffle/queue/download/playlist/rename/edit/delete through shared media-item requests, and artist-detail album rows route radio/download/queue/playlist/favorite through the same request path.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Added typed row action requests for the remaining downloaded-track and internet-radio station row families. Download rows now route select/add-to-playlist/create-playlist/remove through `DownloadedTrackActionRequest`, and station rows route select/edit/delete through `StationRowActionRequest`.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Added shared media-item row kind metadata and threaded `SharedMediaItemActionRequest` through the shared app-shell boundary. Playlist and artist-album rows now leave shared UI through one typed media-item callback, and Android consumes album, artist, and playlist media-row actions through that shared request shape instead of only route-specific callback clusters.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Removed the redundant shared-shell and Android action fields for artist-album row actions and playlist-list item actions now covered by `onMediaItemAction`. The shared playlist screen and artist detail album rows require the typed media-item dispatcher, shrinking the platform callback surface while preserving playlist-detail actions that still need full detail context.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Collapsed downloaded-track and internet-radio station row actions into typed shell dispatchers. Shared downloads now emit `DownloadedTrackActionRequest`, shared radio rows emit `StationRowActionRequest` for select/delete while keeping create/edit save as the dialog result, and Android consumes those requests through thin platform lookup handlers instead of separate row callback fields.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Finished the desktop side of the remaining row-action collapse. Desktop downloads now route select/add-to-playlist/remove through `DownloadedTrackActionRequest`, and the desktop internet-radio wrapper exposes one `StationRowActionRequest` callback instead of separate play/delete row callbacks.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Collapsed desktop search track rows onto `SharedTrackRowActionRequest` and moved typed row action availability into shared `TrackRow`. Desktop search now exposes one track action callback, while shared row rendering can show typed-action menus without no-op platform callbacks.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Collapsed desktop search and library artist/album rows onto `SharedMediaItemActionRequest`. The desktop artist/album row wrappers now support typed media-item dispatch with explicit action availability, and search/library panels expose one media-item callback instead of copied selected/radio/queue/playlist/download/favorite callback clusters.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Collapsed the remaining desktop detail row clusters onto typed shared actions. Album-detail, artist-detail popular-track, artist-detail album, and playlist-detail track rows now leave their panels as `SharedTrackRowActionRequest` or `SharedMediaItemActionRequest`, with desktop route content doing the thin lookup into selected detail state before invoking platform actions.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Collapsed desktop now-playing queue and related item menu callbacks onto `NowPlayingItemActionRequest`. `DesktopNowPlayingPanel` now exposes one typed queue-item action callback and `DesktopNaviampApp` resolves it against queue/related tracks before invoking the existing platform actions.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Collapsed desktop album-detail header actions plus playlist list/detail header actions onto `SharedMediaItemActionRequest`. Album and playlist header/list UI now emits typed media-item requests for play, shuffle, radio, favorite, download, queue, playlist-add, rename, and delete actions, with desktop route content acting as the thin selected-state/platform adapter.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Collapsed desktop artist-detail header and popular-track group actions. Artist radio, similar-artist lookup, queue, playlist-add, and favorite actions now leave the panel through `SharedMediaItemActionRequest`, while popular-track group play/radio/queue actions use a shared `SharedTrackGroupActionRequest` instead of direct platform callbacks.
- Verification passed: `.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Collapsed desktop now-playing current-track actions onto `NowPlayingCurrentTrackActionRequest`. Favorite, rating, artist/album navigation, track radio, download, and add-to-playlist now leave `DesktopNowPlayingPanel` through one typed shared UI request, with `DesktopNaviampApp` acting as the thin platform adapter.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`.
- Collapsed the remaining `NaviampNowPlayingActions` callback surface into typed request groups. Playback controls, display/lyrics/visualizer actions, current-track actions, queue saves, sleep-timer actions, item selection, and queue-item menu actions now leave shared now-playing UI through typed dispatchers instead of individual callback fields; desktop and shared wrappers translate those requests at their platform boundary.
- Verification passed: `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:ui:jvmTest :apps:desktop:compileKotlinDesktop`.

Success criteria for the first slice:

- One shared call path owns metadata mutation decisions for Android and Desktop.
- Platform controllers no longer duplicate mutation status/error decisions.
- Platform controllers do not choose provider capability behavior beyond passing provider/capability data into shared services.
- Android and Desktop still update their platform-specific side effects correctly.
- The diff should be narrow: shared domain/media files, common tests, and thin platform media controller wiring.
