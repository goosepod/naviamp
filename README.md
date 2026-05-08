# Naviamp

Naviamp is a planned cross-platform music client for Navidrome, inspired by the compact, playback-focused experience of Plexamp.

The project is private while it is being shaped, but should be ready to open source when we decide to publish it.

## Goals

- One client experience across desktop and mobile.
- Desktop first: macOS, Windows, and Linux are the initial testing targets.
- Android support after the desktop foundation is stable, including a path toward Android Auto.
- Navidrome-first integration.
- A provider-oriented architecture so Plex, Jellyfin, Subsonic, or other sources can be added later without rewriting the app.
- Direct streaming as the initial playback path.
- Future support for mobile streaming transcode settings.
- Future support for offline downloads, either original files or transcoded copies.
- Gapless playback and configurable crossfade.
- ReplayGain support.
- Lyric support, preferring lyrics embedded in file tags and falling back to online lookup when needed.
- Compact now-playing experience with queue access, album art, track/file details, volume, scrubbing, and visualizers.
- Color theming based on album art.

## Current Direction

The current preferred technology path is Kotlin Multiplatform with Compose Multiplatform:

- shared core modules for service contracts, Navidrome API access, playback state, queue logic, radio logic, settings, and tests
- Compose Multiplatform desktop app for macOS, Windows, and Linux
- Android app later using the same shared core, with platform-specific playback and Android Auto integration
- possible future iOS target without making it part of the first milestones

## Development

This repo is being built as a Kotlin learning project. Code should be clear, documented where useful, and covered by focused tests.

Planned local commands:

```shell
./gradlew check
./gradlew :apps:desktop:run
```

The Gradle wrapper is committed to the repo. Use `./gradlew` instead of a system Gradle install for project commands.

See [docs/setup.md](docs/setup.md), [docs/architecture.md](docs/architecture.md), [docs/roadmap.md](docs/roadmap.md), and [docs/learning-kotlin.md](docs/learning-kotlin.md).
