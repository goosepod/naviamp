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
- [ ] Define shared result models for user-intent operations.
  - Covers: loading, success, failure, cache invalidation, refreshed state, playback command, and status text.
  - Verification target after implementation: shared unit tests before platform rewiring.
- [ ] Extract media action orchestration first.
  - Move shared lookup, queue append, add-to-playlist, metadata mutation composition, and common status handling out of `AndroidMediaActionsController.kt` and `DesktopMediaActionsController.kt`.
  - Keep Android notification updates and downloaded file lookup as platform adapters.
  - Expected platform result: both controllers become thin wiring around shared services plus platform side effects.
- [ ] Extract playlist mutation and refresh orchestration.
  - Move queue-save, add tracks to playlist, rename/delete, playlist detail refresh, smart playlist save/update/load, preloading decisions, cache invalidation, and home playlist reduction into focused shared services.
  - Keep dialogs, route changes, recent playlist persistence, and playback execution as platform adapters.
- [ ] Extract library sync/freshness orchestration.
  - Move sync gating, freshness evaluation orchestration, scan-check marking plan, progress/status result modeling, and paging limit calculations where shared.
  - Keep scrolling, platform cache repositories, and UI progress application outside the shared service.
- [ ] Extract radio and generated queue orchestration.
  - Move seed selection, `RadioRequest` construction, refill planning, generated queue append/replace decisions, recent radio stream result data, and expansion sequencing into shared services.
  - Keep actual playback engine calls and platform state assignment in adapters.
- [ ] Standardize playback command execution.
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

- [ ] Create a shared playlist mutation coordinator for save queue, add track, rename, and delete flows.
- [ ] Keep platform code responsible only for dialogs, text input, and user intent dispatch.
- [ ] Replace duplicated Android/Desktop queue-save paths with a single shared flow.
- [ ] Add tests for queue-to-playlist request construction and local state refresh.

## Phase 5: Shared Playback Orchestration

- [ ] Define a shared playback command surface for play, pause, resume, seek, previous, next, shuffle, repeat, and volume.
- [ ] Keep BASS interaction behind the existing playback engine boundary.
- [ ] Move sleep timer expiry and playback command decisions into shared domain/application code.
- [ ] Reduce platform playback controllers to lifecycle/audio-session/notification adapters.
- [ ] Add tests for sleep timer, repeat/shuffle, seek gating, and previous/next decisions.

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
- [ ] Reduce `AndroidMediaActionsController.kt` and `DesktopMediaActionsController.kt` to construction/adaptation around the shared path.
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
- Verification passed: `.\gradlew.bat :core:domain:allTests`, `.\gradlew.bat :apps:android:compileDebugKotlin`, and `.\gradlew.bat "-Pnaviamp.bass.platform=windows-x64" :apps:desktop:compileKotlinDesktop`.

Success criteria for the first slice:

- One shared call path owns metadata mutation decisions for Android and Desktop.
- Platform controllers no longer duplicate mutation status/error decisions.
- Platform controllers do not choose provider capability behavior beyond passing provider/capability data into shared services.
- Android and Desktop still update their platform-specific side effects correctly.
- The diff should be narrow: shared domain/media files, common tests, and thin platform media controller wiring.
