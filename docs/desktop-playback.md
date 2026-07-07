# Desktop Playback

Naviamp uses a small playback abstraction so queue behavior, UI controls, and provider code do not depend on a specific native audio engine.

## Current Engine

Desktop uses `DesktopBassPlaybackEngine` as the production playback engine. The desktop factory always constructs BASS; if native BASS loading fails, playback reports a BASS error instead of switching to another audio stack.

## Engine Resolution

`DesktopPlaybackEngineFactory` accepts `NAVIAMP_PLAYBACK_ENGINE=bass` and `-Dnaviamp.playback.engine=bass` for compatibility with earlier testing scripts, but BASS is the only desktop engine path.

## Direction

The production desktop target is bundled BASS so users do not need a separate install. Desktop playback stays behind the current playback interface and uses the shared BASS facade with JNI below the desktop adapter.

## BASS Packaging

The Kotlin desktop app has its active JNI-backed BASS binding under:

```text
apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/playback/bass
```

The first Kotlin BASS implementation used JNA to prove loading, packaging, and basic playback quickly. Production BASS work now uses JNI so Naviamp can keep native callbacks, BASSmix, PCM/FFT visualizers, gapless playback, and crossfade behavior fast and consistent across desktop and Android. The old JNA BASS connector has been removed; JNI is the only active desktop BASS connector.

The JNI production binding design is tracked in `docs/bass-jni-design.md`.

The BASS engine resolves libraries in this order:

1. JVM property: `-Dnaviamp.bass.dir=/path/to/bass`
2. Environment variable: `NAVIAMP_BASS_DIR=/path/to/bass`
3. Bundled desktop resources under `playback/bass/<platform>`
4. Development vendor fallback under `apps/desktop/vendor/bass/<platform>`

The desktop Gradle build copies the current platform BASS libraries from:

```text
apps/desktop/vendor/bass/<platform>
```

into generated desktop resources at:

```text
playback/bass/<platform>
```

For Compose native packages, the same BASS files are copied into app resources. A macOS `.app` currently contains them at:

```text
Naviamp.app/Contents/app/resources/playback/bass/macos-arm64
```

Windows package resources are generated at:

```text
apps/desktop/build/generated/desktopBassApp/windows-x64/playback/bass/windows-x64
```

The Windows x64 set currently includes `bass.dll`, `naviamp_bass.dll`, and available BASS add-ons such as FLAC, HLS, mix, Opus, WebM, DSD, APE, ALAC, AAC, MPC, WMA, FX, and SSL. The macOS ARM64 set currently includes `libbass.dylib`, `libnaviamp_bass.dylib`, and available add-ons. The Linux x64 vendor set currently includes BASS core plus FLAC, HLS, mix, Opus, WebM, WavPack, DSD, APE, ALAC, AAC, AC3, MPC, SPX, TTA, MIDI, loudness, and FX add-ons; `libnaviamp_bass.so` is built from source on a Linux host.

The intended original-stream coverage is:

| Format | macOS ARM64 | Windows x64 | Linux x64 | Notes |
| --- | --- | --- | --- | --- |
| MP3 | BASS core | BASS core | BASS core | Direct provider streams should play without add-ons. |
| FLAC | `bassflac` | `bassflac` | `bassflac` | Includes Ogg FLAC where the add-on reports it. |
| Opus | `bassopus` | `bassopus` | `bassopus` | Direct Opus streams should remain original. |
| AAC/M4A | CoreAudio/BASS plus HLS where applicable | `bass_aac` plus BASS core | `bass_aac` plus BASS core | If a macOS AAC/MP4 edge case fails, fall back to provider transcode until the matching add-on is vendored. |
| ALAC | CoreAudio/BASS where available | `bassalac` | `bassalac` | macOS currently relies on platform/CoreAudio support; Windows and Linux have the explicit add-on. |
| Vorbis/Ogg | BASS core | BASS core | BASS core | Native BASS stream type reports Ogg/Vorbis. |
| WavPack | `basswv` | not currently in the Windows vendor set | `basswv` | Windows should fall back to provider transcode until `basswv.dll` is added. |
| APE | `bassape` | `bassape` | `bassape` | Direct playback requires the add-on. |
| MPC | `bass_mpc` | `bass_mpc` | `bass_mpc` | Direct playback requires the add-on. |
| DSD | `bassdsd` | `bassdsd` | `bassdsd` | Direct playback requires the add-on. |
| HLS | `basshls` | `basshls` | `basshls` | Live/radio streams should be treated as non-seekable and unknown-duration. |
| WMA | not packaged | `basswma` | not available | macOS and Linux should fall back to provider transcode. |
| WebM | `basswebm` | `basswebm` | `basswebm` | Direct playback requires the add-on. |

When a format is not covered by BASS core or a packaged add-on on the current platform, Naviamp should request provider transcoding instead of surfacing a codec error to the user. Stats for nerds shows the loaded BASS add-ons, current stream info, and latest BASS error through the shared backend/JNI path.

Override the platform for packaging/copy verification with:

```shell
./gradlew -Pnaviamp.bass.platform=macos-arm64 :apps:desktop:packageReleaseDistributable
./gradlew -Pnaviamp.bass.platform=windows-x64 :apps:desktop:copyDesktopBass :apps:desktop:copyDesktopBassAppResources
./gradlew -Pnaviamp.bass.platform=linux-x64 :apps:desktop:copyDesktopBass :apps:desktop:copyDesktopBassAppResources
```

## Windows Local Build Workflow

For local Windows testing, use the normal Compose distributable, not the release distributable:

```powershell
.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :apps:desktop:createDistributable
```

This produces a packaged app at:

```text
apps/desktop/build/compose/binaries/main/app/Naviamp/Naviamp.exe
```

To avoid accidentally launching an older internal Compose output, use the staged local test app as the standard test location:

```powershell
.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :apps:desktop:stageLocalTestApp
.\build\local-test\Naviamp\Naviamp.exe
```

That task rebuilds the normal non-release distributable and syncs it into:

```text
build/local-test/Naviamp/Naviamp.exe
```

Use this folder for day-to-day testing. Treat `apps/desktop/build/compose/binaries/...` as Gradle internals.

To produce a movable zip that keeps `Naviamp.exe`, `app/`, and `runtime/` together:

```powershell
.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :apps:desktop:packageLocalDistributable
```

The zip is written to:

```text
apps/desktop/build/compose/distributions/Naviamp-windows-x64-local.zip
```

For deployable test builds, use the explicit release zip task:

```powershell
.\gradlew.bat --configure-on-demand "-Pnaviamp.bass.platform=windows-x64" :apps:desktop:packageReleaseDistributable
```

That task deliberately uses the same non-ProGuard app image as local testing and writes:

```text
apps/desktop/build/compose/distributions/Naviamp-windows-x64-release.zip
```

Avoid Compose Desktop's `createReleaseDistributable`, `runRelease`, and `packageReleaseDistributionForCurrentOS` tasks for Naviamp deploy testing. The desktop app uses Compose Desktop plus native JNI playback bindings, and ProGuard can break runtime-only paths such as native method lookup, callback dispatch, and generated Compose bytecode even when compilation succeeds.

For Windows installer artifacts, use the Makefile wrapper instead of the zip task:

```powershell
make windows-installer
```

That target calls Compose Desktop's installer packaging path intentionally, after the app-image and native-resource packaging setup has been wired into Gradle. It requires WiX Toolset 3.x on `PATH` because `jpackage` uses WiX to create MSI/EXE installers. Keep using `packageReleaseDistributable` / `make windows-standalone` for deployable app-image zip testing.

## macOS Local Build Workflow

For local macOS testing, use the same normal Compose distributable path as Windows:

```shell
./gradlew --configure-on-demand -Pnaviamp.bass.platform=macos-arm64 -Pcompose.desktop.packaging.checkJdkVendor=false :apps:desktop:stageLocalTestApp
open build/local-test/Naviamp.app
```

That task rebuilds the normal non-release `.app` bundle and syncs it into:

```text
build/local-test/Naviamp.app
```

Use this staged app for day-to-day CPU and playback testing. Treat `apps/desktop/build/compose/binaries/...` as Gradle internals.

To produce a movable zip that keeps the `.app` bundle intact:

```shell
./gradlew --configure-on-demand -Pnaviamp.bass.platform=macos-arm64 -Pcompose.desktop.packaging.checkJdkVendor=false :apps:desktop:packageLocalDistributable
```

The zip is written to:

```text
apps/desktop/build/compose/distributions/Naviamp-macos-arm64-local.zip
```

For deployable test builds, use the explicit release zip task:

```shell
./gradlew --configure-on-demand -Pnaviamp.bass.platform=macos-arm64 -Pcompose.desktop.packaging.checkJdkVendor=false :apps:desktop:packageReleaseDistributable
```

That task deliberately uses the same non-ProGuard app image as local testing and writes:

```text
apps/desktop/build/compose/distributions/Naviamp-macos-arm64-release.zip
```

## Linux Local Build Workflow

Linux support is tracked in `docs/linux-desktop-support.md`. The packaging path follows the same non-ProGuard app image used by macOS and Windows, but it must run on Linux because Compose Desktop and `jpackage` create native packages for the current OS only.

For local Linux testing, use:

```shell
make linux-test
./build/local-test/Naviamp/bin/Naviamp
```

To produce a movable zip:

```shell
make linux-standalone
```

The zip is written to:

```text
apps/desktop/build/compose/distributions/Naviamp-linux-x64-release.zip
```

To produce native packages:

```shell
make linux-installer
```

That target creates `.deb` and `.rpm` packages through Compose Desktop and `jpackage`. Linux native playback remains blocked until BASS Linux x64 libraries are vendored under `apps/desktop/vendor/bass/linux-x64` and verified on a Linux host.

## Library Refresh

Desktop keeps a local library index so search, radio fallback, popular-track matching, and library browsing can work without repeatedly crawling the server. A full library import is intentionally not run on every startup once a usable index exists, because it can create sustained local database writes and make the UI feel sluggish.

On connect, Naviamp checks Navidrome's lightweight `getScanStatus` endpoint and stores the last seen server scan signature. If the server scan signature changes later, the app reports that the library changed and leaves the full import to the manual refresh action. A full import still runs automatically when no usable local index exists.

The packaged app launches directly through the Compose native launcher. BASS is loaded in-process from bundled resources; there are no terminal wrapper scripts or shell launchers required for BASS.

BASS binaries are third-party redistributables from Un4seen. Keep the downloaded license/readme files with the vendor inputs when producing release packages, and verify the selected BASS license is compatible with the distribution type before shipping public builds.

## Waveform Generation

Desktop waveform generation uses BASS decode streams against cached or downloaded audio files. Packaged desktop apps do not bundle another decoder for waveform generation.

## Gapless Playback

The BASS desktop engine implements `QueueAwarePlaybackEngine.prepareNext`. During normal queue playback, `DesktopPlaylistEngine` asks BASS to create the next stream shortly before the current track ends.

For gapless playback, the engine uses BASSmix queued decode channels. The active track plays through a BASS mixer, and the prepared next track is queued into that mixer before the current source ends. When BASS advances to the queued source, the app adopts that source as the current track instead of stopping and reopening playback. Prepared streams are cleared on stop, seek, source changes, queue jumps, shuffle/restore changes, or when the next request no longer matches the prepared stream.

For crossfade playback, the engine uses the same BASSmix path without queue mode so the prepared next source can overlap the active source. The next source fades in with an equal-power envelope while the current source fades out. Crossfade is only prepared for finite-duration queued tracks; live radio and unknown-duration streams continue without crossfade.

Audio prefetch also performs sidecar prep for upcoming tracks after caching audio: waveform generation, local tag reading for embedded lyrics, provider lyrics, and LRCLIB fallback. Those steps are best-effort and do not fail the audio prefetch if one sidecar source is unavailable.

## ReplayGain

Desktop BASS applies ReplayGain app-side with BASS channel volume attributes. Naviamp prefers provider ReplayGain metadata when Navidrome/OpenSubsonic returns it, then falls back to ReplayGain tags from downloaded or cached local audio files. Track and Album modes use the matching gain/peak pair, with Album falling back to Track when album-level values are missing.

For mixer playback, the mixer output keeps the user volume and each source channel carries its ReplayGain factor. For direct non-mixer playback, the user volume and ReplayGain factor are multiplied on the active channel. Positive gains are bounded, and peak metadata limits boosts that would clip. Crossfade envelopes are scaled by each source's ReplayGain factor so fades do not discard loudness normalization.

Stats for nerds reports the active ReplayGain mode, metadata source, applied dB/factor, and whether peak limiting prevented clipping.

## Live Visualizers

The shared playback contract now has an optional `VisualizerPlaybackEngine` interface that exposes `PlaybackVisualizerFrame` values. UI code consumes that shared frame model rather than calling BASS directly. Desktop BASS fills the model from live FFT data on the active BASS channel, and Android BASS fills the same model through JNI.

The Now Playing screen renders those live FFT bands as a separate music-reactive surface. The waveform scrubber remains a cached seek/position surface and is intentionally separate from the live visualizer path.

## Android BASS

Android now builds and packages `libnaviamp_bass` from the shared JNI project and loads the bundled BASS Android libraries before constructing a BASS-backed playback engine. BASS is required on Android; JNI/BASS load failures surface as startup/playback diagnostics rather than falling back to another playback engine.

The Android BASS engine owns BASS init/free lifecycle, stream creation, play/pause/stop, seek, volume, ReplayGain, progress polling, ICY/HTTP metadata polling, and FFT visualizer frames. It still uses Naviamp's existing foreground service and notification controls, so notification play/pause/previous/next/stop behavior stays routed through the same app callbacks.

Android BASSmix gapless/crossfade support uses the same shared `QueueAwarePlaybackEngine` contract as desktop. The Android engine creates BASS decode sources, plays them through a BASSmix mixer, queues the next source for gapless playback, and slides source volumes for crossfade when a crossfade duration is configured. Android still uses the existing foreground service and notification callbacks around that BASS-backed engine.
