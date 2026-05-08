# Roadmap

## Milestone 0: Project Foundation

- Decide build stack.
- Scaffold repository.
- Add license and contribution docs before public release.
- Add CI-ready test commands.
- Document architecture and provider boundaries.

## Milestone 1: Desktop Navidrome Client

- macOS, Windows, and Linux desktop app.
- First-launch connection screen for one Navidrome server.
- Persist server URL and login/session details securely where possible.
- Browse recently added music.
- Browse artists, albums, tracks, and playlists.
- Basic search.
- Direct stream playback.
- Now-playing view with album art, track info, file info, volume, scrubber, and queue.
- ReplayGain preference where supported by the playback path.
- Basic lyrics panel using embedded lyrics when exposed by the provider.

## Milestone 2: Playback Polish

- Gapless playback.
- Configurable crossfade in seconds.
- Media keys.
- OS now-playing integration.
- Visualizers from album art click/tap.
- Album-art-based color theming.
- Recent playlists and useful home screen sections.

## Milestone 3: Radio

- Artist radio.
- Track radio.
- Queue continuation.
- Tunable similarity rules.
- Tests for selection behavior.

## Milestone 4: Lyrics

- Embedded lyrics from tagged files.
- Unsynced lyric display.
- Synced lyric display when timestamped lyrics are available.
- LRCLIB fallback lookup when embedded lyrics are missing.
- Source indication and refresh behavior.
- Tests for lookup matching and fallback order.

## Milestone 5: Android

- Shared core reused from desktop.
- Android playback service.
- Mobile streaming quality/transcode settings.
- Android Auto investigation and implementation.
- Mobile-appropriate now-playing and queue views.

## Milestone 6: Offline

- Download manager.
- Download original files.
- Download transcoded files where provider supports it.
- Codec and bitrate selection.
- Offline library view.
- Sync status and storage controls.
