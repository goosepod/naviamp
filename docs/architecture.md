# Architecture

## Principles

- Keep platform-independent behavior in shared modules.
- Keep platform-specific playback, storage, and OS integration behind narrow interfaces.
- Treat cache, downloads, local library indexes, sidecars, playback sessions, and media-source storage as shared capabilities with platform-specific engines behind shared ports.
- Treat Navidrome as the first media provider, not as a hard-coded assumption throughout the app.
- Make domain behavior testable without UI, network, or audio devices.
- Prefer explicit services and contracts over global state.

## Proposed Module Shape

```text
naviamp/
  apps/
    desktop/
    android/
  core/
    domain/
    providers/
    playback/
    queue/
    radio/
    lyrics/
    settings/
    theme/
  providers/
    navidrome/
  platform/
    desktop/
    android/
```

This layout may change once the project is scaffolded, but the boundaries should remain.

## Core Domain

The shared domain layer owns app concepts that should not belong to any one server API:

- artist
- album
- track
- playlist
- genre
- library item
- play queue
- playback state
- radio seed
- replay gain mode
- lyrics
- stream quality preference
- download quality preference

Provider implementations translate their native API responses into these domain models.

## Media Provider Boundary

The app should depend on a provider contract rather than a Navidrome client directly.

Example responsibilities:

- authenticate
- validate server capabilities
- fetch recently added albums/tracks
- fetch playlists and recent playlists
- search
- browse artists, albums, tracks, and genres
- fetch album art
- produce stream URLs
- produce transcode stream URLs when supported
- expose track lyrics when the provider can read or serve embedded lyrics
- create radio candidates from an artist or track seed
- expose provider-specific capabilities

Navidrome is the only planned first provider. Future providers should be added by implementing the same contract.

## Playback Boundary

Playback should be split into shared state/queue logic and platform playback engines.

Shared logic:

- queue management
- shuffle/repeat
- radio continuation
- current track state
- crossfade preference
- replay gain preference
- persistence of playback settings

Platform-specific logic:

- decoding
- audio output
- gapless support
- crossfade execution
- media keys
- OS now-playing integration
- Android foreground service
- Android Auto

## Storage, Cache, And Downloads Boundary

Storage should follow a port/adapter model. Shared code should depend on narrow repository or store interfaces; desktop and Android should provide concrete engines at the app composition root.

Shared contracts should cover capabilities such as:

- provider response cache
- image cache
- audio cache
- offline downloads
- local library index
- sidecar storage for lyrics, waveform, embedded tags, and status
- playback session/history
- cache/download/library stats and maintenance

Platform-specific engines can use different details:

- desktop disk paths, SQLite/JDBC, JVM file APIs, and desktop diagnostics
- Android app-private files, Android SQLite driver, mobile-data policy inputs, and app storage stats

The product behavior above those engines should remain shared wherever possible. See `docs/shared-storage-cache-architecture.md`.

## Streaming And Transcoding

Initial behavior:

- stream original files directly from Navidrome
- store one active Navidrome connection

Planned behavior:

- mobile streaming quality setting
- optional transcoding for downloads
- download original or transcoded media
- codec and bitrate settings when transcoding is selected

The UI should model this as provider capabilities because not every future provider will expose transcoding the same way.

## Radio

Radio should start from an artist or track seed and produce a queue of similar tracks.

Likely first implementation:

- use Navidrome data where available
- combine artist, album, genre, starred/favorite, play history, and randomization heuristics
- keep the algorithm in shared core so it is testable

If Navidrome exposes better similarity data later, the Navidrome provider can feed richer candidates into the same radio service.

## Lyrics

Lyrics should be handled as a separate service with multiple sources.

Initial priority:

- read lyrics embedded in the user's tagged media files through Navidrome or provider metadata when available
- support unsynced lyrics
- support synced/timestamped lyrics if present
- fall back to online lookup only when embedded lyrics are missing

Likely provider shape:

- embedded lyrics source from the active media provider
- online lyrics source such as LRCLIB
- lyrics resolver that chooses the best result based on track title, artist, album, duration, and whether synced lyrics are requested

The UI should not know where lyrics came from. It should receive a domain lyrics model with source metadata, sync type, and text/timestamp lines.

## Theming

Album-art-based theming should be handled as a separate service:

- extract dominant and accent colors from artwork
- produce a small theme palette
- expose stable values to UI
- avoid making the whole interface one-note or overly flashy

## Testing Strategy

Required test areas:

- provider contract tests with mocked Navidrome responses
- domain model mapping tests
- queue behavior tests
- radio selection tests
- lyrics source fallback tests
- synced lyric parsing tests
- settings serialization tests
- playback state tests
- UI smoke tests once desktop shell exists
