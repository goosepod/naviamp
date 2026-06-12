# Naviamp

Naviamp is a cross-platform music client for Navidrome, built around a compact, playback-first experience. The current app is written in Kotlin Multiplatform with Compose Multiplatform, uses shared domain/provider/UI modules, and targets desktop first while keeping Android and future mobile targets in view.

The project is still being shaped privately, but the intent is to make it a capable, open-source-friendly music player for people who self-host their libraries and want a polished native client rather than a browser tab.

## What It Does

Naviamp connects to a Navidrome server, browses the library, streams music, manages playback, and presents a focused now-playing experience. It is designed to feel closer to a small native music app than a general media dashboard.

Current work includes:

- Navidrome login and saved connection settings.
- Library home views for albums, playlists, tracks, artists, genres, recent/frequent/random content, and search.
- Queue-based playback with shuffle, repeat, seek, previous/next, volume, and playback progress.
- BASS-backed desktop playback with bundled native libraries for packaged builds.
- ReplayGain, crossfade settings, and playback diagnostics.
- Internet radio and track radio flows.
- Album art loading, album-art-derived player colors, and cached cover art.
- Waveform generation for the scrub bar.
- Embedded and provider-backed lyrics work.
- Stats and diagnostics for playback, cache, provider, and runtime state.
- GPU-backed desktop visualizers with selectable shader effects.
- A desktop settings store that remembers playback, navigation, window, search, session, cache, recent radio, and selected visualizer state.

## Visualizers

Naviamp has a growing GPU visualizer catalog on desktop. Visualizers use the current BASS FFT/waveform data, album-art-derived colors, and Skia runtime shaders. The app switches back to album art when playback is paused or stopped, then restores the visualizer when playback resumes if the user had it enabled.

Current visualizers include:

- Reactive bars
- Fluid gradient
- Audio sphere
- Audio tunnel
- Ribbon trail
- Spectral ridge
- Mountains
- Frequency terrain
- Particle field
- Particle galaxy
- Album art
- Wave interference
- Vinyl groove

Visualizer selection is available from the visualizer context menu and the now-playing hamburger menu. The last selected visualizer is persisted and restored the next time the desktop app opens.

## Project Structure

```text
apps/
  android/        Android app target
  desktop/        Compose Multiplatform desktop app
core/
  domain/         Shared models, playback contracts, queue/radio/settings logic
  storage/        Shared SQLDelight storage
  ui/             Shared Compose UI and platform UI seams
providers/
  navidrome/      Navidrome provider implementation
native/           Native support code
docs/             Architecture notes, roadmaps, performance notes, setup docs
```

## Technology

- Kotlin Multiplatform for shared app code.
- Compose Multiplatform for desktop UI.
- Android app target sharing the same core/UI modules where practical.
- SQLDelight for local storage.
- Navidrome/Subsonic-compatible provider work for the first media source.
- BASS for production desktop playback, native format support, FFT data, and future gapless/crossfade work.
- Skia runtime shaders for current desktop GPU visualizers.
- Gradle as the primary build system, with memorable Makefile shortcuts for local and future CI/release use.

## Local Development

Use the checked-in Gradle wrapper:

```shell
./gradlew check
```

Common local commands are exposed through `make` so the important workflows are easier to remember than raw Gradle task names:

```shell
make help
make desktop-test
make macos-test
make macos-standalone
make android-debug
make clean
make clean-generated
```

Useful outputs:

- `make macos-test` builds, stages, and opens `build/local-test/Naviamp.app`.
- `make macos-standalone` creates a release zip under `apps/desktop/build/compose/distributions`.
- `make android-debug` builds the debug APK through the Android Gradle plugin.
- `make desktop-test` runs the desktop test task.

Windows packaging tasks are present, but must run on Windows because `jpackage` needs the target OS:

```shell
make windows-test
make windows-standalone
make windows-installer
```

`make windows-installer` creates Windows MSI/EXE installers through `jpackage`. It must run on Windows with WiX Toolset 3.x available on `PATH`.

## Release Direction

The desired release path is a Forgejo-based automation that builds all supported targets when changes merge into `main`. The long-term goal is for release jobs to gather merged PRs, tickets, and other change metadata, build a changelog, and produce all expected release artifacts.

Planned artifacts include:

- macOS app bundle and standalone zip.
- Windows standalone zip plus MSI/EXE installers.
- Android APK/AAB release outputs.
- Linux packages or archives once Linux desktop support is formalized.
- iOS artifacts if/when an iOS target becomes realistic.

The Makefile is intended to stay as the stable human and CI entry point, even if Gradle task names or packaging internals change.

## Roadmap

Near-term work:

- Keep improving the desktop release path without relying on ProGuard for the app image.
- Add measured FPS/frame-time instrumentation for active visualizers.
- Add per-visualizer settings and quality controls without cluttering the player UI.
- Continue optimizing idle playback, visualizer rendering, lyrics, and now-playing recomposition.
- Harden BASS playback behavior around gapless playback, crossfade, stream formats, and native diagnostics.
- Improve release packaging for macOS and Windows.

Medium-term work:

- Linux desktop target.
- Android polish, including mobile-specific playback behavior and Android Auto direction.
- Offline downloads and cache management.
- Provider expansion beyond Navidrome, while keeping Navidrome the first-class path.
- Better changelog/release automation through Forgejo.
- Installer support, signed artifacts, and repeatable release builds.

Long-term possibilities:

- iOS target.
- More advanced visualizers, including raymarching/SDF scenes, volumetric effects, album-art depth/parallax, and quality presets.
- More provider backends such as Plex, Jellyfin, or other Subsonic-compatible servers.
- A fully automated multi-target release pipeline for zips, installers, APKs/AABs, and platform-specific packages.

## Documentation

Useful project docs:

- [docs/setup.md](docs/setup.md)
- [docs/architecture.md](docs/architecture.md)
- [docs/roadmap.md](docs/roadmap.md)
- [docs/desktop-playback.md](docs/desktop-playback.md)
- [docs/desktop-performance-optimization.md](docs/desktop-performance-optimization.md)
- [docs/gpu-visualizer-roadmap.md](docs/gpu-visualizer-roadmap.md)
- [docs/learning-kotlin.md](docs/learning-kotlin.md)
- [docs/decisions.md](docs/decisions.md)

## Status

Naviamp is under active development. The current priority is making the desktop app feel solid, fast, and releasable, while preserving a clean path to Android, Linux, and future iOS work.
