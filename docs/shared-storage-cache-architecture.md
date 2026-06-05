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
  - `DownloadService` now uses platform-agnostic download/replacement repository ports.
  - Audio cache/download byte writes/deletes now go through shared `AudioByteStoreService` over platform `AudioByteStore` implementations.
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
- [x] Add platform app dependency registries for concrete service construction.
  - Desktop app/window lifecycle stays in `Main.kt`, while `DesktopAppDependencies` owns settings, playback engine, storage dependencies, playback audio assets, sidecar services, waveform service, discovery clients, playlist engine construction, and library sync construction.
  - Android activity lifecycle/state stays in `MainActivity`, while `AndroidAppDependencies` owns settings, playback runtime, storage dependencies, playback audio assets, sidecar services, discovery clients, and playlist engine construction.
  - Platform-only boundaries remain explicit: desktop owns JVM `Path`/window app shell details; Android owns `Context`, foreground service/runtime state, and app-private `File` handling.

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
  - Audio cache/download bytes are behind `AudioByteStoreService` and platform `AudioByteStore` adapters.
  - Image/object bytes now have `ObjectByteStoreService` and platform `ObjectByteStore` adapters; desktop and Android still back images with the existing SQL image table until a storage-engine migration moves image blobs out of metadata rows.
- [x] Split metadata database ports from byte storage.
  - SQLite/SQLDelight can remain the first engine, but shared code should depend on repository contracts.
  - First slice: saved media-source metadata is behind `MediaSourceRepository`, implemented by desktop and Android storage engines.
  - Second slice: playback-session metadata is behind `PlaybackSessionRepository`, implemented by desktop settings and Android storage.
  - Third slice: Android Auto recent-track browse reads go through `PlaybackHistoryRepository` instead of broad storage calls.
  - Fourth slice: desktop library refresh/sync/clear orchestration uses `LocalLibraryIndexRepository`, `MediaSourceRepository`, and `CacheMaintenanceRepository` instead of broad `DesktopCache`.
  - Fifth slice: Android library sync/freshness orchestration uses `LocalLibraryIndexRepository` and `MediaSourceRepository` instead of broad `AndroidStorage`.
  - Sixth slice: Android cache/library/database maintenance helpers use `CacheMaintenanceRepository` and `LocalLibraryIndexRepository` instead of broad `AndroidStorage`.
  - Seventh slice: Android artist/album detail fallback reads use `LocalLibraryIndexRepository` instead of broad `AndroidStorage`.
  - Eighth slice: Android download actions and persistence effects use `DownloadRepository`, `DownloadReplacementRepository`, and `CacheMaintenanceRepository` instead of broad `AndroidStorage`.
  - Ninth slice: Android connection startup writes source metadata through `ProviderMediaSourceRepository` instead of broad `AndroidStorage`.
  - Tenth slice: desktop and Android playback audio asset adapters use `DownloadRepository` and `AudioCacheRepository` instead of broad storage/cache types.
  - Eleventh slice: desktop artist/album detail fallback reads use `LocalLibraryIndexRepository` and provider caching uses `ProviderResponseCacheRepository` instead of broad `DesktopCache`.
  - Twelfth slice: desktop download actions use `DownloadRepository`, `DownloadReplacementRepository`, and `ProviderResponseCacheRepository` instead of broad `DesktopCache`.
  - Thirteenth slice: desktop home loading uses `HomeLibraryRepository` and `ProviderResponseCacheRepository` instead of broad `DesktopCache`.
  - Fourteenth slice: desktop search uses `ProviderResponseCacheRepository` instead of broad `DesktopCache`.
  - Fifteenth slice: desktop media actions persist updated track metadata through `TrackMetadataRepository` instead of broad `DesktopCache`.
  - Sixteenth slice: desktop radio seed selection uses `LocalLibraryIndexRepository` instead of broad `DesktopCache`.
  - Seventeenth slice: desktop smart playlist auth refresh writes source metadata through `ProviderMediaSourceRepository` and invalidates provider responses through `ProviderResponseCacheRepository` instead of broad `DesktopCache`.
  - Eighteenth slice: desktop now-playing waveform, lyrics, and related-track loading uses shared sidecar, audio waveform, playback audio asset, and library-index ports instead of broad `DesktopCache`.
  - Nineteenth slice: Android now-playing lyrics and waveform sidecar status writes use `SidecarStatusRepository` instead of broad `AndroidStorage`.
  - Twentieth slice: desktop and Android BASS waveform analyzers implement a shared `AudioWaveformAnalyzer` contract.
  - Twenty-first slice: desktop and Android waveform analyzers now use shared float-PCM bucketing over BASS decode-stream length/read primitives.
  - Twenty-second slice: shared `BassAudioBackend` port now hides desktop `DesktopBassJniBinding` and Android `AndroidBassJni` for waveform decode-stream access.
  - Twenty-third slice: Android playlist sidecar/prefetch orchestration now receives waveform/audio-cache ports and a cache lambda instead of broad `AndroidStorage`.
  - Twenty-fourth slice: Android Auto foreground-service helpers now use shared library-index, provider-response, media-source, playback-session, playback-history, and cover-art lookup ports where practical; the service still owns an `AndroidStorage` instance as its composition/runtime adapter.
  - Twenty-fifth slice: desktop connection opening now uses `CacheMaintenanceRepository` and `ProviderMediaSourceRepository`, and the connection panel uses `ProviderResponseCacheRepository`, instead of taking `DesktopCache` directly.
  - Twenty-sixth slice: desktop and Android cache/storage stats now use shared `StorageCacheStats` from common domain instead of platform-specific stats models.
  - Twenty-seventh slice: desktop connection lifecycle now receives cache-maintenance, media-source, and provider-media-source repository ports instead of direct `DesktopCache`.
  - Twenty-eighth slice: desktop composition now uses `DesktopStorageDependencies`, keeping direct `DesktopCache` construction inside the platform dependency holder.
  - Twenty-ninth slice: Android app and foreground-service composition now use `AndroidStorageDependencies`, keeping direct `AndroidStorage` construction inside the platform dependency holder.
  - Thirtieth slice: image byte orchestration now uses `ObjectByteStoreService`; the current platform adapters still store image blobs in SQL, which is an engine-internal migration boundary rather than a product-code dependency.
  - Thirty-first slice: provider-response cache SQL access now lives in focused desktop/Android provider-response stores below common `ProviderResponseCacheService`.
  - Thirty-second slice: image/object byte SQL access now lives in focused desktop/Android object-byte stores below `ObjectByteStoreService`.
  - Thirty-third slice: sidecar status SQL writes now live in focused desktop/Android sidecar-status stores below common `SidecarStatusService`.
  - Thirty-fourth slice: media-source metadata and library sync-marker SQL access now lives in focused desktop/Android media-source stores.
  - Thirty-fifth slice: audio cache/download metadata rows now live in focused desktop/Android audio stores below shared audio byte-store orchestration.
  - Thirty-sixth slice: library index, search, related-track, popular-track, library year, and library-stat rows now live in focused desktop/Android library-index stores.
  - Thirty-seventh slice: provider/embedded and LRCLIB lyrics sidecar rows now use common `LyricsSidecarCacheService` over focused desktop/Android lyrics stores.
  - Thirty-eighth slice: Android playback-session and playback-history rows now live in `AndroidPlaybackStore`; desktop playback-session persistence remains in desktop settings through the shared session repository.
  - Thirty-ninth slice: cache clear/download clear/all-clear row operations and stats aggregation now live in focused desktop/Android maintenance stores, with file deletion and hot-image memory cleanup left at the platform facade edge.
  - Remaining broad storage internals are mostly composition/facade concerns plus platform-specific file deletion, hot image memory management, waveform row trimming, and desktop provider-response track-metadata payload updates.
- [x] Normalize playback local-audio file boundaries.
  - Shared services should consume platform-neutral local-audio descriptors or store ports instead of `java.io.File` or `java.nio.file.Path` directly.
  - Android can keep `File` and desktop can keep `Path` inside platform adapters because output streams, atomic moves, directory walking, and delete behavior are OS/runtime details.
  - First local-audio target slice is complete: common playback helpers now own local-audio-or-provider-stream URL selection for Android playback start, Android prepare-next, Android foreground-service restored playback, desktop `DesktopPlaylistEngine`, and shared waveform generation.
  - `PlaybackAudioAssetRepository`, `PlaybackAudioSourcePlan`, and `AudioWaveformService` now use the platform-neutral `PlaybackLocalAudio` descriptor with local path, local URI, and optional size.
  - Android `File` and desktop `Path` conversion now happens at the platform playback-audio asset adapters and platform-only tag/lyrics reads.
- [x] Extract a shared audio cache/download store service.
  - `AudioByteStoreService` now owns provider audio dispatch, source/track/quality file naming, content-type extension selection, zero-byte failure cleanup, and duplicate/in-flight guards.
  - Android and desktop both use the service for cached audio writes, downloaded audio writes, replacement downloads, storage-limit rollback, download replacement cleanup, download removal, and cached-audio trim deletion.
  - Platform byte stores only create temp/final files, expose an output sink, move temp files into place, delete files, and return the neutral stored path/size result.
- [x] Replace direct `DesktopCache` dependencies in desktop controllers with narrower interfaces.
  - [x] `DesktopHomeController`
  - [x] `DesktopSearchController`
  - [x] `DesktopLibraryController`
  - [x] `DesktopNowPlayingController`
  - [x] `DesktopDownloadsController`
  - [x] `DesktopPlaylistEngine`
  - [x] `DesktopAlbumController`
  - [x] `DesktopArtistController`
  - [x] `DesktopMediaActionsController`
  - [x] Desktop connection lifecycle, connection opening, and connection panel album loading
- [x] Replace direct `AndroidStorage` dependencies in Android controllers with narrower interfaces.
  - [x] `AndroidConnectionController`
  - [x] `AndroidLibraryController`
  - [x] `AndroidDownloadController`
  - [x] `AndroidArtistController`
  - [x] `AndroidPlaylistsController`
  - [x] `AndroidPlaybackSessionController`
  - [x] `AndroidMaintenanceController`
  - [x] `AndroidPlaylistEngine`
  - [x] Android Auto foreground-service helper paths
  - Remaining broad storage ownership is limited to Android composition/runtime roots such as `MainActivity` and `AndroidPlaybackForegroundService`.
- [x] Extract a shared download service.
  - Keep mobile-data policy and download quality rules shared.
  - Keep platform network/storage execution behind injected repositories.
  - Make desktop and Android download flows call the same service.
  - Initial download and re-download orchestration now use `DownloadService`; low-level byte/file storage ports remain a separate extraction.
- [x] Extract a shared provider-response cache service.
  - Desktop currently has cache-backed album/artist/search helpers.
  - Android can gain the same behavior through the same interface instead of platform-specific copies.
- [x] Extract a shared audio-cache/download resolution service.
  - Given source, track, quality, and cache settings, choose downloaded file, cached file, or provider stream.
  - Return a platform-neutral playback target where possible, with platform adapters converting to engine URLs/paths.
- [x] Extract shared sidecar storage contracts.
  - Embedded lyrics, LRCLIB lyrics, waveform, ReplayGain/audio tag metadata, and sidecar status records should use shared repository names and status rules.
  - Waveform generation itself should be split into a shared analyzer interface instead of being hidden inside platform storage.
    - First analyzer slice is complete: `AudioWaveformAnalyzer` and `AudioWaveformAnalysisSource` now live in common domain, with `DesktopAudioWaveformAnalyzer` and `AndroidAudioWaveformAnalyzer` as BASS-backed implementations.
    - First BASS unification slice is complete: both waveform analyzers now create BASS decode streams, read float PCM chunks, and call common `normalizeFloatPcmWaveform(...)`.
    - First BASS access-port slice is complete: waveform analyzers depend on shared `BassAudioBackend`; platform adapters wrap desktop and Android JNI below that boundary.
    - Shared waveform service slice is complete: `AudioWaveformService` now composes cached waveform lookup, local/downloaded audio preference, optional audio caching, provider-stream fallback, analyzer calls, and persistence.
    - Desktop now-playing analysis, desktop current/upcoming sidecar prep, and Android sidecar prep now call the shared waveform service while platform adapters supply local-file URL conversion, TLS preparation, and concrete storage/cache engines.
    - Android is no longer forced into the older storage-shaped `AudioWaveformRepository.ensureAudioWaveform(sourceId, trackId, quality)` contract; the shared service receives `Track`, provider stream context, and playback cache behavior.
    - Shared sidecar status helpers now record success/failure rows for desktop and Android sidecar work through the same `SidecarStatusRepository` path.
    - `AudioWaveformServiceResult` now owns waveform status text mapping, so callers do not rebuild cached/generated/unavailable labels independently.
    - Shared lyrics sidecar orchestration now lives in `LyricsSidecarService`: provider lyrics, embedded/local-file lyrics, LRCLIB fallback, preference selection, and lyrics row persistence use the same service on desktop and Android.
    - Shared audio-tag sidecar orchestration now lives in `AudioMetadataSidecarService`: platform adapters only read local audio bytes from `Path` / `File`, while common code owns embedded-lyrics and ReplayGain extraction from parsed tags.
    - Android `AndroidStorage` now implements the shared lyrics sidecar repository so provider, embedded, and LRCLIB lyrics use the same SQL sidecar rows as desktop.
    - Desktop now-playing analysis, desktop current/upcoming sidecar prep, Android now-playing lyrics, and Android upcoming sidecar prep all use the shared lyrics/audio-metadata sidecar services.
    - First Android playlist-engine extraction slice is complete: `AndroidPlaylistEngine` now owns Android audio prefetch, sidecar prep, prepare-next, and waveform service composition instead of `MainActivity`.
    - Prepare-next queue planning is now shared: desktop and Android both use common progress/capability/next-track gating before queueing prepared playback.
    - Android's external active-queue sync for prepare-next now uses `PlaybackQueueController` instead of local index math in `AndroidPlaylistEngine`.
    - Playlist-engine extraction target is complete: `PlaybackSidecarService`, `runAudioPrefetch`, common prefetch stats, and `preparedNextPlaybackRequest` now sit below desktop `DesktopPlaylistEngine` and Android `AndroidPlaylistEngine`; platform wrappers provide jobs, logging/UI callbacks, foreground-service state, and concrete cache/audio adapters.
- [x] Expand the shared BASS facade beyond waveform reads.
  - Playback streams, decode streams, active state, stream metadata/tags, FFT visualizer reads, seek/position/duration, volume slides, and mixer channel creation/add are now modeled on `BassAudioBackend` and implemented by desktop and Android adapters.
  - Shared BASS playback stream selection now chooses file/URL and direct/decode/playback-decode creation through `BassAudioBackend`; platforms only resolve local file paths.
  - Android playback now consumes `BassAudioBackend` for these primitives instead of raw `AndroidBassJni`; runtime still owns JNI loading and wraps it in the adapter.
  - Desktop playback now consumes `BassAudioBackend` for stream/control/progress/metadata/FFT/mixer primitives and diagnostics through `DesktopBassJniBinding`; the old `DesktopBassNative` connector has been removed.
  - End sync is now modeled on `BassAudioBackend`; byte conversion remains inside platform seek implementations where it is needed.
  - Crossfade duration normalization and mixer queue-source decisions now live in common playback transition helpers and are used by desktop/Android playback where applicable.
  - Gapless/crossfade prepare-next capability/window/duplicate-prep decisions now live in common playback transition helpers; desktop and Android still own platform-specific URL/replaygain resolution.
  - ReplayGain mode selection, gain-to-volume conversion, peak clipping guard, and max-volume clamping now live in common playback helpers; desktop keeps diagnostics labels while Android applies the shared volume factor.
  - Prepared-playback duplicate checks and metadata reset/failure defaults now live in common playback helpers; platforms still free native handles locally.
  - Prepared-playback adoption eligibility now lives in common playback helpers so desktop and Android use the same active-stream, prepared-match, and mixer-capability gate before taking a queued BASS source.
  - Active playback stream reset defaults now live in common playback helpers so desktop and Android clear stream/source/crossfade/ReplayGain state consistently after stop, release, and cleanup.
  - Playback volume application now uses common BASS backend helpers: direct streams receive user volume multiplied by ReplayGain, while mixer playback keeps user volume on the mixer and ReplayGain on the source.
  - Prepared mixer transition planning now lives in common playback helpers, including queued-next volume, crossfade initial/final source volume, duration, and current-source fade eligibility.
  - Prepared mixer transition application now has a shared raw-handle overload so desktop and Android do not build transition `BassStreamHandle` values locally.
  - BASS mute/restore for delayed start-seek now uses a shared backend helper; Android supplies platform focus/ducking state and the backend applies stream/source volume.
  - Playback finished-position tolerance now lives in common playback helpers so platform engines share the same progress-at-end boundary.
  - FFT visualizer bucketing/gain normalization now lives behind common BASS backend helpers; desktop and Android ask the backend for playback visualizer frames.
  - BASS active-state constants and labels now live in common BASS helpers; platform engines use them for polling, diagnostics, and logging.
  - BASS active-state to playback-state mapping now lives in common playback helpers; platform engines keep stopped/end-of-track handling local.
  - BASS polling finished-state detection now lives in common playback helpers, combining active-state and progress-at-end checks.
  - Android now performs BASS seconds-to-bytes conversion inside its seek implementation, matching desktop without exposing unused byte-conversion facade methods.
  - Android now exposes mixer-channel removal through `BassAudioBackend`, matching desktop cleanup primitives.
  - Android now exposes BASS channel info through `BassAudioBackend` and uses it to size mixer playback from source frequency/channels like desktop.
  - Shared mixer creation planning now chooses source frequency/channels, fallback defaults, and queue-source policy for both desktop and Android.
  - Shared BASS stream-release helpers now remove mixer membership before freeing unique non-zero streams on both desktop and Android.
  - Shared single-stream release helpers now accept raw BASS Int handles, so platform engines do not manually wrap release handles.
  - Shared BASS Int-handle helper extensions now wrap `BassStreamHandle` conversion for playback/control/progress calls; desktop and Android playback no longer carry local duplicates.
  - Android now applies the shared backend `configureInternetStreams` path before stream creation, matching desktop's BASS playlist/meta/depth network configuration.
  - BASS core version is now exposed through `BassAudioBackend` so diagnostics do not need to reach below the platform adapter for that primitive.
  - BASSmix version is now exposed through `BassAudioBackend`; Android and desktop both wrap `BASS_Mixer_GetVersion` through JNI.
  - Desktop and Android waveform URL analysis now call `BassAudioBackend.configureInternetStreams` before creating remote BASS decode streams.
  - BASS version label formatting now lives in common BASS helpers instead of desktop-only diagnostics code.
  - BASS error-code labels now live in common BASS helpers and are used by desktop diagnostics plus Android backend/playback errors.
  - BASS backend failure-message formatting now lives in common BASS helpers; Android backend/playback errors use that shared path.
  - BASS native-library directory and BASSmix load-error diagnostics now hang off `BassAudioBackend`; desktop populates them and Android keeps the shared defaults.
  - Desktop active-stream diagnostics now ask `BassAudioBackend` for active state instead of reaching directly into native connector details.
  - Desktop loaded/failed plugin reporting now uses shared `BassPluginDiagnostic` rows exposed by `BassAudioBackend` through JNI.
  - Desktop gapless/crossfade support flags now read mixer capability from `BassAudioBackend` instead of raw native connector details.
  - Desktop playback and waveform composition now receive `BassAudioBackend` through the same facade boundary; app-level playback/waveform code no longer constructs a raw desktop native connector.
  - Android gapless/crossfade support flags now also read mixer capability from `BassAudioBackend` instead of assuming mixer support.
  - Playback source-handle selection now lives in common playback helpers, so desktop and Android use the same source-vs-output handle rule for seek/progress reads.
  - Playback user-volume factor calculation now lives in common playback helpers, with Android passing its audio-focus ducking factor through the shared path.
  - Direct BASS playback creation now uses a shared backend helper that returns a common playback/source handle result shape for desktop and Android.
  - Mixer BASS playback creation now uses a shared backend helper for decode-stream selection, mixer sizing, source ReplayGain application, mixer creation, and channel attachment.
  - Playback polling now reads active state, source active state, progress, and stream metadata through a shared BASS backend snapshot helper.
  - Prepared/queued BASS source creation now uses a shared backend helper, with platforms only supplying local file paths and whether local decode streams need playback-decode flags.
  - Prepared-next mixer source setup now uses a shared backend helper for queued source creation, transition planning/application, and crossfade-active reporting.
  - Direct-vs-mixer BASS playback creation now flows through a shared backend selector; platforms only decide whether mixer playback applies for the request.
  - Playback start-seek position filtering now lives in common playback helpers, so desktop and Android use the same positive-position boundary before asking BASS to seek.
  - BASS stream active-state diagnostic labeling now lives in common backend helpers instead of desktop formatting raw active-state values directly.
  - ReplayGain scalar extraction now has a common helper for BASS call sites that only need the final volume factor.
  - Playback source seek now uses a common backend helper so desktop and Android select and seek the active BASS source/output handle consistently.
  - BASS stop-and-release cleanup now uses a shared backend helper so desktop and Android stop output streams and release playback/source/prepared handles through the same path.
  - Prepared-source adoption now uses a common BASS helper for releasing the replaced source only when the current source differs from the queued source.
  - Direct-vs-mixer playback selection now uses a common planning helper; Android's media-id requirement remains an explicit platform constraint instead of an inline fork.
  - Prepared-source adoption now reapplies the shared BASS volume plan after handoff so a faded-in queued source cannot remain silent once it becomes the active track.
  - Prepared crossfade transitions now use shared BASS volume slides for fade-in/fade-out instead of BASSmix volume envelopes, avoiding envelope-position ambiguity while keeping desktop and Android behavior aligned.
  - Unused BASSmix volume-envelope facade methods have been removed from common, desktop, and Android JNI/backend adapters after the slide-based transition path was validated.
  - Prepared crossfade slide failures now fail source preparation directly instead of flowing through obsolete envelope fallback warnings.
  - BASS playback polling continuation now uses a shared active-state predicate so desktop and Android stop polling on the same output-stopped boundary.
  - Prepared-next mixer-source eligibility now uses a common helper so desktop and Android require the same active playback handle, active source handle, and mixer support before queueing a BASS source.
  - Gapless and crossfade support flags now map from BASS mixer capability through a shared playback feature-support helper.
  - Prepared-source adoption now uses a shared BASS helper for replaced-source release plus volume restoration, keeping the validated crossfade handoff behavior in one backend path.
  - Desktop no longer keeps a separate stale prepared-stream take path; prepared playback handoff flows through the shared adoption helper.
  - Prepared BASS source results now only expose handoff state; platform engines keep ReplayGain metadata in their existing prepared-track fields while shared helpers still use ReplayGain for transition planning.
  - Still to normalize further: crossfade transition state reset and remaining transition application details should continue moving from platform playback engines into shared planning/services.
  - Keep JNI/native-loader details under platform adapters unless a single native loader is proven simpler across all targets.
- [x] Normalize platform file/class names.
  - Shared/common abstractions keep generic names.
  - Platform adapters and platform-owned service files should use `Desktop` / `Android` prefixes.
  - First cleanup slice: common `AudioWaveformAnalyzer`, desktop `DesktopAudioWaveformAnalyzer`, Android `AndroidAudioWaveformAnalyzer`.
  - Verification sweep on 2026-06-05: Android platform files are prefixed with `Android`; desktop playback, cache, radio, and search platform adapters are prefixed with `Desktop`. The remaining unprefixed desktop files are theme/UI helpers (`DetailActionIconButton`, `PlaybackFormatting`, `TransportIcons`) rather than platform service adapters.
- [x] Create a platform dependency registry/composition object.
  - Desktop builds repositories from desktop paths/settings.
  - Android builds repositories from app context/settings.
  - Shared services receive only interfaces.
  - `DesktopAppDependencies` and `AndroidAppDependencies` now sit above the platform storage dependency holders and provide symmetrical construction roots for app-level services.
- [x] Add fake/in-memory implementations for common tests.
  - This is the equivalent of swapping cache/storage engines in a PHP test environment.
  - First fake engine: `InMemoryObjectByteStore` backs common `ObjectByteStoreService` tests.

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
  - Both still act as composition facades, but provider responses, object bytes, sidecar status, media-source metadata, audio cache/download metadata, library index rows, lyrics sidecar rows, Android playback rows, and maintenance/stat aggregation now have focused adapters underneath the broad facades.
  - Target: keep splitting into narrow repository implementations until the broad classes are thin composition wrappers only.
- Desktop and Android download controllers
  - Both plan downloads, apply policy, write audio files, refresh stats, and report status.
  - Target: shared download service over `DownloadRepository` and byte/file-store ports.
- Desktop `DesktopPlaylistEngine` and Android playback adapters
  - Both decide downloaded vs cached vs stream source.
  - Target: shared playback-source resolver over audio asset repositories.
- Desktop now-playing controller and Android sidecar prep
  - Both load/prepare waveform, lyrics, embedded tags, and cover art.
  - Target: shared sidecar service over sidecar/audio/image repositories.
- Desktop search cache and Android provider search
  - Desktop uses cached search; Android calls provider directly.
  - Target: shared provider response cache interface so both platforms can choose cached or live search consistently.
- Cache/stats/settings surfaces
  - Desktop and Android share the same `StorageCacheStats` model and now aggregate SQL-backed counts in focused maintenance stores.
  - Platform facades still attach platform diagnostics such as paths, byte limits, database size, and hot-image memory details.

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
4. Point desktop `DesktopPlaylistEngine` and Android playback start at the shared resolver.

This is a strong first slice because playback-source selection currently affects both platforms and is visible to users.

## Progress

- 2026-05-31: Added shared playback-source resolver in `core/domain/playback`.
  - Desktop `DesktopPlaylistEngine` now uses the shared resolver for downloaded file, cached file, and provider stream selection.
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
  - Added the first audio byte-store port and `StoredAudioBytes` in common domain.
  - `DesktopCache` and `AndroidStorage` kept download metadata/database ownership while delegating persistent audio byte writes and deletes through platform byte-store implementations.
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
  - `DesktopLibrarySync` now writes library artists/albums/tracks through `LocalLibraryIndexRepository`.
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
- 2026-06-02: Moved Android artist/album detail fallbacks onto the shared library-index port.
  - Artist detail, album detail, now-playing album navigation, and artist-album track loading now read offline fallback tracks through `LocalLibraryIndexRepository`.
  - Provider response caching remains a separate shared port, with Android storage only supplied as the concrete adapter at composition.
- 2026-06-02: Moved Android download actions and persistence effects onto shared repository ports.
  - Download, re-download, and remove-download helpers now compose `DownloadService` from `DownloadRepository` and `DownloadReplacementRepository`.
  - Storage stats refresh uses `CacheMaintenanceRepository<AndroidStorageStats>`.
  - Downloads-route refresh reads downloaded rows through `DownloadRepository`.
- 2026-06-02: Moved Android connection startup source metadata writes onto a shared repository port.
  - Added `ProviderMediaSourceRepository` and `ProviderMediaSourceConnection` in common domain.
  - `startNavidromeConnection` now upserts source metadata through the shared port and uses `ProviderResponseCacheRepository` for home browse loading.
  - Android storage remains the concrete SQL adapter supplied by app composition.
- 2026-06-02: Moved playback audio asset adapters onto shared repository ports.
  - `AndroidPlaybackAudioAssets` and `DesktopPlaybackAudioAssets` now take `DownloadRepository` and `AudioCacheRepository`.
  - Android track playback receives a `PlaybackAudioAssetRepository` from composition instead of broad `AndroidStorage`.
  - Playback audio asset lookups now return `PlaybackLocalAudio`, keeping Android `File` and desktop `Path` behind platform adapters.
  - Desktop playback and now-playing call sites pass `DesktopCache` only as concrete implementations of the narrow ports.
- 2026-06-02: Moved desktop artist/album detail fallback loading onto shared repository ports.
  - `DesktopArtistController` and `DesktopAlbumController` now receive `LocalLibraryIndexRepository` and `ProviderResponseCacheRepository`.
  - Offline artist/album fallback reads no longer depend on broad `DesktopCache`.
- 2026-06-02: Moved desktop download actions onto shared repository ports.
  - `DesktopDownloadsController` now composes `DownloadService` from `DownloadRepository` and `DownloadReplacementRepository`.
  - Album/playlist download expansion now uses `ProviderResponseCacheRepository`.
  - Download removal calls the shared download repository contract.
- 2026-06-02: Moved desktop home loading onto shared repository ports.
  - `DesktopHomeController` now receives `ProviderResponseCacheRepository` and `HomeLibraryRepository`.
  - The desktop cache-to-home-library adapter now lives at composition.
- 2026-06-02: Moved desktop search onto the shared provider-response cache port.
  - `DesktopSearchController` now receives `ProviderResponseCacheRepository`.
  - Desktop composition remains responsible for supplying `DesktopCache` as the concrete adapter.
- 2026-06-02: Moved desktop track metadata persistence onto a shared repository port.
  - Added `TrackMetadataRepository` for persisting updated track metadata into cached provider responses.
  - `DesktopMediaActionsController` now receives the repository port instead of broad `DesktopCache`.
- 2026-06-02: Moved desktop radio seed selection onto the shared library-index port.
  - Artist/album radio seed helpers now receive `LocalLibraryIndexRepository`.
  - `DesktopRadioController` no longer depends on broad `DesktopCache`.
- 2026-06-02: Moved desktop smart playlist source/cache writes onto shared repository ports.
  - `DesktopSmartPlaylistsController` now receives `ProviderMediaSourceRepository` and `ProviderResponseCacheRepository`.
  - `DesktopCache` implements provider media-source upserts through the shared port while remaining the desktop SQL adapter at composition.
- 2026-06-02: Moved desktop now-playing analysis onto shared repository ports.
  - Added `AudioWaveformRepository`, `LyricsSidecarRepository`, and `SidecarStatusRepository` in common domain.
  - `DesktopNowPlayingController` now receives waveform, lyrics sidecar, library-index, and playback-audio asset ports instead of broad `DesktopCache`.
  - Desktop cache remains the concrete waveform/lyrics/sidecar adapter supplied by app composition.
- 2026-06-02: Moved Android now-playing sidecar status writes onto the shared sidecar status port.
  - `AndroidStorage` now implements `SidecarStatusRepository`.
  - `MainActivity` records lyrics and waveform sidecar status through the shared port instead of broad `AndroidStorage`.
- 2026-06-02: Added Android playlist download actions.
  - The shared playlist list and detail UI now expose download actions.
  - Android uses selected/preloaded playlist tracks when available and falls back to a provider playlist-track load before calling the shared bulk download path.
- 2026-06-02: Added shared download-quality change confirmation when local downloads exist.
  - Changing the saved-file quality with existing downloads now asks the user to keep existing local files and use the new quality only for future downloads, or cancel the setting change.
- 2026-06-02: Added shared re-download orchestration for quality changes.
  - Android and desktop both use the common redownload helper for status/progress/de-duplication and refresh decisions.
  - Platform storage engines still own replacing a single track file safely in Android app storage or desktop cache storage.
- 2026-06-03: Moved desktop `DesktopPlaylistEngine` off direct `DesktopCache` coupling.
  - `DesktopPlaylistEngine` now receives audio cache, waveform, lyrics sidecar, sidecar status, and playback audio asset ports.
  - `DesktopNaviampApp` remains the composition root that supplies `DesktopCache` as the concrete implementation of those narrow contracts.
  - Playback-source lookup, audio prefetch, waveform prep, provider/embedded/LRCLIB lyrics prep, and sidecar status writes no longer require a broad desktop storage type inside the engine.
- 2026-06-04: Extracted shared audio cache/download byte-store orchestration.
  - Added common `AudioByteStoreService` plus `AudioByteStore` / `AudioByteWriter`.
  - Common code now owns provider audio streaming, source/track/quality stable filenames, content-type extensions, zero-byte cleanup, and in-flight write coalescing.
  - Android and desktop storage now use the same service for audio cache writes, download writes/replacements, rollback cleanup, download removal, and cached-audio trim deletion.
  - Android `File` and desktop `Path` remain below platform byte-store adapters for temp/final file moves and deletion.
- 2026-06-04: Completed shared sidecar service extraction.
  - Added `LyricsSidecarService` for provider, embedded, LRCLIB, and preferred-lyrics selection.
  - Added `AudioMetadataSidecarService` plus platform audio-tag readers so embedded lyrics and ReplayGain extraction share common tag parsing.
  - Android storage now implements `LyricsSidecarRepository`, giving Android and desktop the same cached lyrics/LRCLIB sidecar row behavior.
  - Desktop now-playing, desktop playlist sidecar prep, Android now-playing lyrics, and Android sidecar prep use the shared services; platform code only supplies file/path tag readers and concrete storage adapters.
- 2026-06-04: Completed the shared playlist-engine orchestration extraction.
  - Added `PlaybackSidecarService` so desktop and Android playlist wrappers share waveform/lyrics sidecar preparation and status writes.
  - Moved prefetch runtime stats and the track-by-track audio prefetch loop into common playback code.
  - Added shared prepared-next request construction so desktop and Android use the same URL resolution, ReplayGain mode gating, and `PlaybackRequest` shaping before calling platform BASS engines.
  - Desktop `DesktopPlaylistEngine` and Android `AndroidPlaylistEngine` remain platform adapters for coroutine jobs, UI/log callbacks, foreground-service state, and concrete cache/audio engines.
- 2026-06-04: Continued storage/cache boundary cleanup.
  - `AndroidPlaylistEngine` no longer depends on broad `AndroidStorage`; it receives waveform/audio-asset ports and a platform cache callback.
  - Android Auto foreground-service helpers now use shared library-index, provider-response, media-source, playback-session, playback-history, and cover-art lookup ports where practical.
  - Added album-title fallback reads to `LocalLibraryIndexRepository` so Android Auto queue restoration does not require a concrete Android storage type for that lookup.
  - Desktop connection opening now depends on `CacheMaintenanceRepository` and `ProviderMediaSourceRepository`, and connection-panel album loading uses `ProviderResponseCacheRepository`, instead of direct `DesktopCache`.
- 2026-06-04: Shared the storage/cache stats model.
  - Added common `StorageCacheStats` and moved desktop/Android cache maintenance repositories to return it.
  - Removed platform stats models from settings, diagnostics, Stats for Nerds, maintenance, download, and app-state consumers.
  - Desktop connection lifecycle now receives cache-maintenance, media-source, and provider-media-source ports instead of direct `DesktopCache`; `DesktopNaviampApp` remains the composition root that supplies the concrete cache engine.
- 2026-06-04: Added platform storage dependency holders.
  - Desktop composition now uses `DesktopStorageDependencies`, which delegates shared repository ports to the concrete `DesktopCache` engine.
  - Android app and foreground-service composition now use `AndroidStorageDependencies`, which delegates shared repository ports to the concrete `AndroidStorage` engine.
  - Direct `DesktopCache` / `AndroidStorage` construction is now isolated to platform dependency holders and concrete engine files.
- 2026-06-04: Split Android foreground-service Android Auto orchestration.
  - `AndroidAutoBrowseController` owns Android Auto browse/search tree shaping while the service keeps lifecycle and hydration calls.
  - `AndroidAutoCommandController` owns media-id, search, and custom-action dispatch for the media-session callbacks.
  - `AndroidPlaybackServiceSessionController` owns foreground-service saved-session hydration and restored now-playing metadata over shared storage/session ports.
  - `AndroidServicePlaybackRuntimeController` owns service-owned play/pause/stop/seek, saved-session playback, adjacent-track handling, and progress/session-position saves.
  - Line-count checkpoint: `AndroidPlaybackForegroundService.kt` 1,509, `AndroidAutoBrowseController.kt` 588, `AndroidAutoCommandController.kt` 65, `AndroidPlaybackServiceSessionController.kt` 80, `AndroidServicePlaybackRuntimeController.kt` 327.
- 2026-06-04: Added shared object-byte storage orchestration.
  - `ObjectByteStoreService` and `ObjectByteStore` now provide the non-audio object-byte boundary.
  - Desktop and Android image caches route through the shared service while keeping existing SQL image rows as the first concrete engine.
  - Desktop cover-art loading now depends on `ImageCacheRepository` instead of broad desktop storage.
  - Added `InMemoryObjectByteStore` and common service tests as the first fake storage engine.
- 2026-06-04: Started splitting broad storage engines into focused adapters.
  - Provider-response cache behavior now uses common `ProviderResponseCacheService` over desktop/Android SQL stores.
  - Object/image byte storage now uses focused desktop/Android `ObjectByteStore` adapters below `ObjectByteStoreService`.
  - Sidecar status writes now use common `SidecarStatusService` over desktop/Android SQL stores.
  - Media-source metadata and library sync markers now use focused desktop/Android media-source stores.
- 2026-06-04: Completed the larger storage-engine row splits.
  - Audio cache/download metadata rows moved into `DesktopAudioStore` and `AndroidAudioStore`.
  - Library index/search/related/popular-track/year/stat rows moved into `DesktopLibraryIndexStore` and `AndroidLibraryIndexStore`.
  - Lyrics sidecar row caching moved into common `LyricsSidecarCacheService` with desktop/Android SQL stores.
  - Android playback-session/history rows moved into `AndroidPlaybackStore`; desktop session persistence remains settings-backed behind `PlaybackSessionRepository`.
  - Cache clear/download clear/all-clear row operations and stats aggregation moved into desktop/Android maintenance stores.
  - The broad platform storage classes now primarily compose focused adapters, coordinate platform file cleanup, and preserve platform-specific diagnostics.
