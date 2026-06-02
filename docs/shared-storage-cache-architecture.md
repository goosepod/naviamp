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
- [ ] Extract shared download orchestration over narrow download/audio repositories.
- [ ] Extract shared provider-response cache orchestration so desktop and Android get the same cached/live behavior.

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

- [ ] Split low-level byte/file storage ports from higher-level media repositories.
  - Examples: local disk/app-private files, temporary cache files, persistent download files.
- [ ] Split metadata database ports from byte storage.
  - SQLite/SQLDelight can remain the first engine, but shared code should depend on repository contracts.
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
- [ ] Extract a shared download service.
  - Keep mobile-data policy and download quality rules shared.
  - Keep platform network/storage execution behind injected repositories.
  - Make desktop and Android download flows call the same service.
- [ ] Extract a shared provider-response cache service.
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
