# Shared Storage And Cache Architecture

This worksheet tracks the next architecture goal: platform-agnostic storage, cache, and download services with platform-specific engines behind shared interfaces.

The intended shape is the same idea as a PHP app using one cache/storage interface with different local/prod drivers. Naviamp should let shared services ask for bytes, metadata, cached provider responses, downloads, local library rows, and sidecars without knowing whether the backing engine is Android app storage, desktop disk, SQLite, S3, Redis, or a future platform store.

## Next Up

- [x] Create this worksheet as the active tracker for storage/cache/download architecture.
- [x] Extract shared downloaded/cached/provider-stream playback-source resolution.
- [x] Replace remaining direct playback audio lookup call sites with the shared resolver.
  - Android foreground service cold-start playback path.
  - Android prepare-next, audio prefetch, waveform, and lyrics lookup paths.
- [x] Sweep remaining local-audio lookup sites for places that should become narrower repository ports instead of concrete storage calls.
  - Desktop now-playing sidecar analysis.
  - Desktop replay gain tag lookup.
  - Storage internals that can stay engine-owned.
- [x] Introduce narrow shared audio asset lookup ports so callers do not depend on broad `DesktopCache` / `AndroidStorage` types.
- [x] Split shared audio asset lookup ports away from `DesktopCache` and `AndroidStorage`.
- [x] Make downloaded-track identity source/track based for playback and download reuse.
- [x] Move shared download loop/status orchestration into common domain helpers.
- [x] Prompt before changing download quality when existing local downloads are present.
- [x] Add shared re-download orchestration for replacing existing downloads at the new quality.
- [x] Extract shared download orchestration over narrow download/audio repositories.
  - Keep playlist/album/track downloads de-duplicated by `sourceId + trackId` so overlapping collections do not create duplicate files.
  - Treat download quality as a stored attribute, not part of the user's duplicate identity for a downloaded song.
  - When the download quality setting changes and existing downloads are present, prompt the user to keep existing files or re-download local tracks at the new quality.
  - Prefer downloaded local audio over provider streams for playlist, radio, search, album, and direct track playback, regardless of where the playback request originated.
- [x] Split low-level byte/file storage ports from higher-level media repositories.
  - `DownloadService` now uses platform-agnostic download/replacement repository ports, but `DesktopCache` and `AndroidStorage` still own concrete byte download and file move details internally.
  - Download audio byte writes/deletes now go through the shared `DownloadAudioByteStore` port with desktop and Android implementations.
- [x] Extract shared provider-response cache orchestration so desktop and Android get the same cached/live behavior.
  - First slice: search responses now use common `ProviderResponseService` over `ProviderResponseCacheRepository` on desktop and Android.
  - Second slice: desktop and Android app album-detail loading now use `ProviderResponseService.album`.
  - Third slice: desktop and Android app artist-detail loading now use `ProviderResponseService.artist`.
  - Fourth slice: desktop and Android home album sections now use cached album-list, genre, and year responses through `ProviderResponseService`.
  - Fifth slice: desktop and Android home artist/playlist sections now use cached list responses through `ProviderResponseService`.
  - Sixth slice: desktop and Android playlist list/detail read paths now use cached playlist list and track responses through `ProviderResponseService`.
  - Seventh slice: playlist mutation refreshes now invalidate shared cached playlist list/track responses before reloading.
  - Eighth slice: desktop/Android downloads and generated-radio album/artist detail helpers now use `ProviderResponseService`.
  - Ninth slice: Android Auto browse/search flows now use cached provider list/detail/search responses through `ProviderResponseService`.
  - Tenth slice: internet radio station list responses now use `ProviderResponseService` with mutation invalidation.

## Decision

- Product behavior should depend on shared ports/interfaces, not on `DesktopCache` or `AndroidStorage` directly.
- Desktop and Android should select concrete implementations at the composition root.
- Platform engines may use different file systems, databases, TLS defaults, and OS APIs, but the app capabilities exposed above them should be the same whenever possible.
- Environment-style configuration is allowed conceptually. On desktop it can be files/system properties/settings; on Android it may be build config, app settings, or dependency construction. The pattern matters more than literal `.env` files.

## Existing Pieces

- `core/domain/cache/CacheRepositories.kt` already defines several shared repository contracts:
  - `ImageCacheRepository`
  - `ProviderResponseCacheRepository`
  - `AudioCacheRepository`
  - `AudioWaveformCacheRepository`
  - `DownloadRepository`
  - `PlaybackHistoryRepository`
  - `LocalLibraryIndexRepository`
  - `CacheMaintenanceRepository`
- `core/domain/cache/DownloadPlans.kt` already owns shared download gating/status rules.
- `DesktopCache` and `AndroidStorage` implement many of these shared contracts, but both are still broad concrete classes with mixed responsibilities.

## Target Shape

```text
core/domain
  storage/cache/download interfaces
  product rules and service orchestration

apps/desktop
  desktop file/db implementations
  desktop dependency construction

apps/android
  Android app-private file/db implementations
  Android dependency construction
```

Shared services should receive contracts:

```kotlin
interface ByteStore {
    suspend fun read(key: String): ByteArray?
    suspend fun write(key: String, bytes: ByteArray)
    suspend fun delete(key: String)
}

interface ObjectStore<T> {
    suspend fun get(key: String): T?
    suspend fun put(key: String, value: T)
    suspend fun delete(key: String)
}
```

Then higher-level repositories can be composed from those stores:

- image cache
- provider response cache
- audio cache
- download repository
- waveform/lyrics sidecar repository
- local library index
- playback session/history repository

## Refactor Checklist

- [x] Split low-level byte/file storage ports from higher-level media repositories.
  - Examples: local disk/app-private files, temporary cache files, persistent download files.
  - First completed slice: persistent download audio bytes are behind `DownloadAudioByteStore`.
  - Remaining byte-store work can apply the same pattern to audio cache, images, lyrics, waveform, and sidecar bytes.
- [ ] Split metadata database ports from byte storage.
  - SQLite/SQLDelight can remain the first engine, but shared code should depend on repository contracts.
  - First slice: saved media-source metadata is behind `MediaSourceRepository`, implemented by desktop and Android storage engines.
  - Second slice: playback-session metadata is behind `PlaybackSessionRepository`, implemented by desktop settings and Android storage.
  - Third slice: Android Auto recent-track browse reads go through `PlaybackHistoryRepository` instead of broad storage calls.
  - Fourth slice: desktop library refresh/sync/clear orchestration uses `LocalLibraryIndexRepository`, `MediaSourceRepository`, and `CacheMaintenanceRepository` instead of broad `DesktopCache`.
  - Fifth slice: Android library sync/freshness orchestration uses `LocalLibraryIndexRepository` and `MediaSourceRepository` instead of broad `AndroidStorage`.
  - Sixth slice: Android cache/library/database maintenance helpers use `CacheMaintenanceRepository` and `LocalLibraryIndexRepository` instead of broad `AndroidStorage`.
- [ ] Replace direct `DesktopCache` dependencies in desktop controllers with narrower interfaces.
  - `DesktopHomeController`
  - `DesktopSearchController`
  - `DesktopLibraryController`
  - `DesktopNowPlayingController`
  - `DesktopDownloadsController`
  - `PlaylistEngine`
  - `DesktopAlbumController`
  - `DesktopArtistController`
  - `DesktopMediaActionsController`
- [ ] Replace direct `AndroidStorage` dependencies in Android controllers with narrower interfaces.
  - `AndroidConnectionController`
  - `AndroidLibraryController`
  - `AndroidDownloadController`
  - `AndroidArtistController`
  - `AndroidPlaylistsController`
  - `AndroidPlaybackSessionController`
  - `AndroidMaintenanceController`
  - `MainActivity` remaining wrappers
- [x] Extract a shared download service.
  - Keep mobile-data policy and download quality rules shared.
  - Keep platform network/storage execution behind injected repositories.
  - Make desktop and Android download flows call the same service.
  - Initial download and re-download orchestration now use `DownloadService`; low-level byte/file storage ports remain a separate extraction.
- [x] Extract a shared provider-response cache service.
  - Desktop currently has cache-backed album/artist/search helpers.
  - Android can gain the same behavior through the same interface instead of platform-specific copies.
- [ ] Extract a shared audio-cache/download resolution service.
  - Given source, track, quality, and cache settings, choose downloaded file, cached file, or provider stream.
  - Return a platform-neutral playback target where possible, with platform adapters converting to engine URLs/paths.
- [ ] Extract shared sidecar storage contracts.
  - Embedded lyrics, LRCLIB lyrics, waveform, ReplayGain/audio tag metadata, and sidecar status records should use shared repository names and status rules.
- [ ] Create a platform dependency registry/composition object.
  - Desktop builds repositories from desktop paths/settings.
  - Android builds repositories from app context/settings.
  - Shared services receive only interfaces.
- [ ] Add fake/in-memory implementations for common tests.
  - This is the equivalent of swapping cache/storage engines in a PHP test environment.

## Candidate Abstractions

- `MediaSourceRepository`
  - saved sources, active/latest source, connection metadata, TLS fields.
- `ProviderResponseRepository`
  - cached album/artist/search/home/provider responses.
- `LibraryIndexRepository`
  - artists, albums, tracks, years, search, stats, scan signatures.
- `MediaByteStore`
  - cache/download byte writes and reads.
- `ImageRepository`
  - cover art bytes, hot memory cache optional.
- `AudioAssetRepository`
  - cached audio, downloaded audio, audio metadata.
- `SidecarRepository`
  - lyrics, waveform, embedded tags, sidecar success/error status.
- `PlaybackSessionRepository`
  - persisted queue/session/progress/history.
- `MaintenanceRepository`
  - clear provider cache, clear media cache, clear downloads, clear all, stats.

## Current Duplication / Redesign Targets

- `DesktopCache` and `AndroidStorage`
  - Both mix SQL metadata, byte-file management, image cache, audio cache, downloads, library index, media sources, and stats.
  - Target: split into narrow repository implementations.
- Desktop and Android download controllers
  - Both plan downloads, apply policy, write audio files, refresh stats, and report status.
  - Target: shared download service over `DownloadRepository` and byte/file-store ports.
- Desktop `PlaylistEngine` and Android playback adapters
  - Both decide downloaded vs cached vs stream source.
  - Target: shared playback-source resolver over audio asset repositories.
- Desktop now-playing controller and Android sidecar prep
  - Both load/prepare waveform, lyrics, embedded tags, and cover art.
  - Target: shared sidecar service over sidecar/audio/image repositories.
- Desktop search cache and Android provider search
  - Desktop uses cached search; Android calls provider directly.
  - Target: shared provider response cache interface so both platforms can choose cached or live search consistently.
- Cache/stats/settings surfaces
  - Desktop and Android show related but platform-shaped cache/download stats.
  - Target: shared stats model with platform implementation details attached only where useful for diagnostics.

## Guardrails

- Do not move Android `Context`, permissions, foreground service, or app-private directory creation into common code.
- Do not move desktop OS path selection, JVM file APIs, or BASS/native library file resolution into common code.
- Do not hide useful platform diagnostics. Keep them as platform detail fields under a shared stats model.
- Avoid a single giant `Storage` interface. Prefer small ports that describe one capability.
- Shared code should own decisions; platform code should own execution.

## Suggested First Slice

1. Create small shared interfaces for audio asset lookup:
   - downloaded audio lookup
   - cached audio lookup
   - cache audio write/fetch
2. Implement adapters over existing `DesktopCache` and `AndroidStorage`.
3. Move downloaded/cached/provider-stream source selection into shared domain.
4. Point desktop `PlaylistEngine` and Android playback start at the shared resolver.

This is a strong first slice because playback-source selection currently affects both platforms and is visible to users.

## Progress

- 2026-05-31: Added shared playback-source resolver in `core/domain/playback`.
  - Desktop `PlaylistEngine` now uses the shared resolver for downloaded file, cached file, and provider stream selection.
  - Android foreground app playback now uses the same resolver for downloaded file, cached file, and provider stream selection.
  - Platform code still owns path/file URI conversion, which keeps OS-specific storage details out of common code.
- 2026-05-31: Extended the shared resolver through Android service/background paths.
  - Android Auto / foreground-service restored playback now uses downloaded/cached audio before provider streams.
  - Android prepare-next, prefetch, waveform, and lyrics sidecar paths now share the same downloaded-vs-cached lookup rule.
- 2026-06-01: Extended the shared resolver through the remaining desktop local-audio lookup sites.
  - Desktop now-playing sidecar analysis now uses the common downloaded-vs-cached rule before reading tags, lyrics, or waveform data.
  - Desktop ReplayGain tag lookup now uses the same resolver before reading local audio tags.
- 2026-06-01: Added a narrow shared `PlaybackAudioAssetRepository` port.
  - Desktop and Android now adapt `DesktopCache` / `AndroidStorage` into a local-audio lookup interface for playback-source resolution.
  - This keeps the resolver and playback-facing code from knowing about broad platform storage engines.
- 2026-06-02: Switched downloaded local audio lookup and download reuse to `sourceId + trackId` identity.
  - Playback now prefers any downloaded local copy before cache/provider streams, even when the current playback quality differs from the stored download quality.
  - Desktop and Android download calls return the existing local file for a track before requesting another copy, so overlapping album/playlist/track downloads do not duplicate files across quality settings.
  - Desktop download removal now removes all local copies for the selected source/track, matching Android's track-level removal behavior.
- 2026-06-02: Moved download loop/status orchestration into `core/domain/cache`.
  - Desktop and Android track-list downloads now share planning, de-duplication, starting/progress/completed/error statuses, and completed-count reporting.
- 2026-06-02: Added a shared download replacement service boundary.
  - `DownloadService` now owns the re-download loop used when saved-file quality settings change.
  - Desktop and Android expose their storage engines through the narrow `DownloadReplacementRepository` port, so platform code owns file replacement while common code owns provider/source gating, de-duplication, status, and completed-count behavior.
  - The remaining download extraction target is low-level byte/file-store ports for initial downloads and replacement writes.
  - Platform controllers still own provider/source selection, mobile-data detection, IO dispatch, storage stats refresh, and concrete repository calls.
- 2026-06-02: Moved initial download writes behind the shared download service.
  - Android and desktop single-track, album, and playlist download flows now call `DownloadService.downloadTracksWithStatus`.
  - Common code owns initial download planning, mobile-data blocking, de-duplication, status, completed-count behavior, and calls into the platform-agnostic `DownloadRepository` port.
  - Platform controllers still select provider/source, derive platform network policy inputs, and refresh platform stats after the shared result.
- 2026-06-02: Split persistent download audio byte storage behind a shared port.
  - Added `DownloadAudioByteStore` and `StoredAudioBytes` in common domain.
  - `DesktopCache` and `AndroidStorage` now keep download metadata/database ownership while delegating persistent audio byte writes and deletes through platform byte-store implementations.
  - This keeps repository behavior behind common ports while preserving platform-specific file APIs and app-private/desktop path details in platform code.
- 2026-06-02: Started shared provider-response cache orchestration.
  - Added common `ProviderResponseService` for typed cached provider responses.
  - Desktop and Android search now share cached/live behavior through `ProviderResponseService.search` and the platform `ProviderResponseCacheRepository` implementations.
- 2026-06-02: Extended shared provider-response cache orchestration to app album details.
  - Added `ProviderResponseService.album`.
  - Desktop album-detail opening and Android album-detail opening now share cached/live album detail behavior through `ProviderResponseCacheRepository`.
  - Android artist album-track loading also uses the shared album service.
  - Remaining album-response callers include desktop download/radio helpers, playlist mutations, Android background playback/Auto paths, and library sync detail loading.
- 2026-06-02: Extended shared provider-response cache orchestration to app artist details.
  - Added `ProviderResponseService.artist`.
  - Desktop and Android artist-detail opening now share cached/live artist detail behavior through `ProviderResponseCacheRepository`.
  - Popular-track and similar-artist enrichment remain separate services layered after the cached artist detail load.
- 2026-06-02: Extended shared provider-response cache orchestration to home album sections.
  - Added `ProviderResponseService.albumList`, `albumsByGenre`, and `albumsByYear`.
  - Desktop and Android home/browse loading now use the shared service for newest/random/recent/frequent album sections, genre spotlight albums, and decade albums.
  - Home artists, playlists, radio stations, and Android Auto browse lists remained as follow-up provider-response slices at this point.
- 2026-06-02: Extended shared provider-response cache orchestration to home artist and playlist sections.
  - Added `ProviderResponseService.artists` and `playlists`.
  - Desktop and Android home/browse loading now use the shared service for artist and playlist list responses.
  - Playlist detail/mutation refreshes, radio stations, and Android Auto browse lists still need to move through typed shared provider-response service calls.
- 2026-06-02: Extended shared provider-response cache orchestration to playlist detail read paths.
  - Added `ProviderResponseService.playlistTracks`.
  - Desktop and Android playlist detail opening, playlist playback loads, list refreshes, and preload reads now use shared cached playlist list/track responses.
  - Playlist mutation refreshes still needed explicit cache invalidation at this point.
- 2026-06-02: Added shared provider-response cache invalidation for playlist mutations.
  - `ProviderResponseCacheRepository` now exposes narrow invalidation by provider/resource type and exact provider/resource id.
  - `ProviderResponseService` owns playlist-list and playlist-track invalidation helpers.
  - Desktop and Android add-to-playlist, rename, delete, and smart-playlist save/update flows now invalidate affected cached playlist responses before reusing the shared cached refresh/read paths.
- 2026-06-02: Extended shared provider-response cache orchestration to downloads and generated-radio helper reads.
  - Desktop album and playlist downloads now load album details and playlist tracks through `ProviderResponseService`.
  - `RadioService` can use `ProviderResponseService` for album/artist detail fallback reads while leaving generated radio/random-song provider calls live.
  - Desktop and Android generated-radio seed/fallback helpers now pass the shared service for album, artist, and random-album list reads.
- 2026-06-02: Completed shared provider-response cache orchestration for Android Auto and internet radio station lists.
  - Android Auto playlist, album, artist, browse, saved-radio, and fallback search reads now use `ProviderResponseService`.
  - `ProviderResponseService` now supports cached internet radio station lists.
  - Shared `HomeService`, desktop internet-radio refresh, and Android Auto saved-radio browsing use the shared station-list service.
  - Desktop internet-radio create/update/delete flows invalidate the cached station list before refreshing.
  - Generated radio, random-song, and provider-specific radio endpoints remain live because those responses are intentionally dynamic.
- 2026-06-02: Started splitting metadata database ports from byte storage.
  - Added shared `MediaSourceRepository` for saved media-source lookup/list/delete metadata.
  - Desktop `DesktopCache` and Android `AndroidStorage` now implement the same media-source metadata port while keeping platform-specific database construction local.
  - Android keeps `latestNavidromeSource` as a compatibility alias over the shared `latestMediaSource` binding.
- 2026-06-02: Added shared playback-session metadata storage port.
  - Added `PlaybackSessionRepository` for loading and saving persisted playback sessions.
  - Desktop `DesktopSettingsStore` implements the port for its single current-session settings file.
  - Android `AndroidStorage` implements the same port with required source-scoped SQL rows.
  - Desktop playback/internet-radio controllers and Android app playback-session helpers now depend on the shared session port instead of concrete storage/settings types for session persistence.
- 2026-06-02: Started using the playback-history metadata port at Android Auto browse call sites.
  - Android Auto recent-play and chart-track browse sections now read through `PlaybackHistoryRepository<AndroidPlaybackHistoryItem>`.
  - `AndroidPlaybackForegroundService` still owns media-browser item shaping and service lifecycle, while the SQL-backed history query is exposed through the shared metadata repository port.
- 2026-06-02: Moved desktop library orchestration onto shared metadata repository ports.
  - `DesktopLibraryController` now receives `LocalLibraryIndexRepository`, `MediaSourceRepository`, and `CacheMaintenanceRepository` instead of broad `DesktopCache`.
  - `LibrarySync` now writes library artists/albums/tracks through `LocalLibraryIndexRepository`.
  - Library sync album-detail fetches use shared `ProviderResponseService`, keeping provider-response cache behavior shared while index writes stay behind the library metadata port.
  - Desktop-specific letter offset remains a local injected function because it is UI paging support, not a cross-platform repository contract yet.
- 2026-06-02: Moved Android library sync and freshness checks onto shared metadata repository ports.
  - `startAndroidLibrarySync` and `syncAndroidLibrary` now write library artists/albums and scan markers through `LocalLibraryIndexRepository`.
  - `checkAndroidLibraryFreshness` reads media-source scan metadata through `MediaSourceRepository` and writes scan markers through `LocalLibraryIndexRepository`.
  - Android storage remains the concrete implementation supplied by app composition, while the library orchestration code depends on shared metadata contracts.
- 2026-06-02: Moved Android maintenance helpers onto shared repository ports.
  - Cache and database clears now call `CacheMaintenanceRepository<AndroidStorageStats>`.
  - Library index clears now call `LocalLibraryIndexRepository`.
  - Android file-cache deletion and UI state reset remain platform-local.
- 2026-06-02: Added Android playlist download actions.
  - The shared playlist list and detail UI now expose download actions.
  - Android uses selected/preloaded playlist tracks when available and falls back to a provider playlist-track load before calling the shared bulk download path.
- 2026-06-02: Added shared download-quality change confirmation when local downloads exist.
  - Changing the saved-file quality with existing downloads now asks the user to keep existing local files and use the new quality only for future downloads, or cancel the setting change.
- 2026-06-02: Added shared re-download orchestration for quality changes.
  - Android and desktop both use the common redownload helper for status/progress/de-duplication and refresh decisions.
  - Platform storage engines still own replacing a single track file safely in Android app storage or desktop cache storage.
