# Desktop Playback

Naviamp uses a small playback abstraction so queue behavior, UI controls, and provider code do not depend on a specific native audio engine.

## Current Engine

Desktop currently uses `BassPlaybackEngine` when BASS is available. It can be overridden during development with `NAVIAMP_PLAYBACK_ENGINE=mpv-crossfade-prototype` for the old mpv crossfade prototype, or by removing/unbundling BASS to fall back to mpv/JLayer.

The legacy mpv path uses `MpvProcessPlaybackEngine` when an `mpv` executable is available. It launches mpv as a child process and controls it over JSON IPC.

The process engine supports:

- Direct/original Navidrome streams
- Pause and resume
- Seeking
- Progress and duration polling
- Gapless mpv mode through `--gapless-audio=yes`

It does not claim crossfade support yet. The `PlaylistEngine` has prepare-next hooks for crossfade-capable engines, but the current process engine does not overlap two streams.

## Crossfade Prototype

An isolated opt-in engine exists for crossfade testing:

```powershell
$env:NAVIAMP_PLAYBACK_ENGINE="mpv-crossfade-prototype"
.\gradlew.bat :apps:desktop:run
```

or with JVM properties:

```shell
./gradlew :apps:desktop:run -Dnaviamp.playback.engine=mpv-crossfade-prototype
```

This uses `ExperimentalCrossfadeMpvPlaybackEngine`, not the stable `MpvProcessPlaybackEngine`. The stable engine remains the default and reports no crossfade support.

The prototype follows the shape Feishin uses for its web-player crossfade path: keep two players, prepare the next player muted, start it near the end of the active track, use an equal-power fade curve, reset the transition on pause/seek, and advance app state only after the overlap has resolved. Feishin's mpv path uses mpv playlist prefetch/gapless behavior, while its crossfade path is implemented with two web players.

Debug logging can be toggled from Settings > Playback > Debug logging. The legacy `NAVIAMP_PLAYBACK_TRACE=true` and `-Dnaviamp.playback.trace=true` switches still enable trace logging before saved settings are applied.

When trace is enabled, logs are written to:

```text
%TEMP%/naviamp/mpv-crossfade-prototype.log
```

on Windows, or the platform temp directory equivalent elsewhere.

## Engine Resolution

`PlaybackEngineFactory` asks `MpvExecutableResolver` for an mpv executable. Resolution order is:

1. JVM property: `-Dnaviamp.mpv.path=/path/to/mpv`
2. Environment variable: `NAVIAMP_MPV_PATH=/path/to/mpv`
3. Bundled app paths
4. Development fallback from `PATH`
5. JLayer fallback if mpv is unavailable

Bundled mpv paths are platform-specific:

```text
playback/mpv/macos-arm64/mpv
playback/mpv/macos-x64/mpv
playback/mpv/windows-x64/mpv.exe
playback/mpv/linux-x64/mpv
```

The resolver also checks common native app bundle layouts around the launched application directory.

## Legacy mpv Bundling

mpv is no longer part of the normal packaged app. The Gradle app packaging path only copies BASS resources. The old mpv vendor folder remains as a temporary development fallback while BASS is being stabilized.

For local fallback experiments, place the platform mpv executable in:

```text
apps/desktop/vendor/mpv/<platform>/mpv
```

or on Windows:

```text
apps/desktop/vendor/mpv/windows-x64/mpv.exe
```

The desktop Gradle build copies the current platform executable into generated resources at:

```text
playback/mpv/<platform>/<executable>
```

If no vendor executable exists, the copy task skips cleanly and development builds continue to use `PATH`.

If Gradle is running under a JVM whose architecture does not match the package you want to build, override the platform:

```shell
./gradlew -Pnaviamp.mpv.platform=macos-arm64 :apps:desktop:packageDmg
```

## Direction

The production desktop target is bundled BASS so users do not need a separate install. The mpv process engine is now only a temporary development fallback while BASS covers the remaining edge cases.

The next desktop playback investigation is BASS inside the Kotlin app, tracked in `docs/kotlin-bass-roadmap.md`. The intent is to keep the existing Kotlin UI and put BASS behind the current playback interface.

## BASS Packaging

The Kotlin desktop app has an initial JNA-based BASS binding under:

```text
apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/playback/bass
```

The first Kotlin BASS implementation uses JNA to prove loading, packaging, and basic playback quickly. Production BASS work should move to JNI so Naviamp can keep native callbacks, BASSmix, PCM/FFT visualizers, gapless playback, and crossfade behavior fast and consistent across desktop and Android.

The JNI production binding design is tracked in `docs/bass-jni-design.md`.

The BASS engine resolves libraries in this order:

1. JVM property: `-Dnaviamp.bass.dir=/path/to/bass`
2. Environment variable: `NAVIAMP_BASS_DIR=/path/to/bass`
3. Bundled desktop resources under `playback/bass/<platform>`
4. Development vendor fallback under `apps/desktop-slint/vendor/bass/<platform>`

The desktop Gradle build copies the current platform BASS libraries from:

```text
apps/desktop-slint/vendor/bass/<platform>
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

The Windows x64 set currently includes `bass.dll` plus available BASS add-ons such as FLAC, HLS, mix, Opus, WebM, WavPack, DSD, APE, ALAC, AAC, MPC, WMA, FX, and SSL. The macOS ARM64 set currently includes `libbass.dylib` plus available add-ons and the generated `libnaviamp_bass.dylib` JNI scaffold.

The intended original-stream coverage is:

| Format | macOS ARM64 | Windows x64 | Notes |
| --- | --- | --- | --- |
| MP3 | BASS core | BASS core | Direct provider streams should play without add-ons. |
| FLAC | `bassflac` | `bassflac` | Includes Ogg FLAC where the add-on reports it. |
| Opus | `bassopus` | `bassopus` | Direct Opus streams should remain original. |
| AAC/M4A | CoreAudio/BASS plus HLS where applicable | `bass_aac` plus BASS core | If a macOS AAC/MP4 edge case fails, fall back to provider transcode until the matching add-on is vendored. |
| ALAC | CoreAudio/BASS where available | `bassalac` | macOS currently relies on platform/CoreAudio support; Windows has the explicit add-on. |
| Vorbis/Ogg | BASS core | BASS core | Native BASS stream type reports Ogg/Vorbis. |
| WavPack | `basswv` | not currently in the Windows vendor set | Windows should fall back to provider transcode until `basswv.dll` is added. |
| APE | `bassape` | `bassape` | Direct playback requires the add-on. |
| MPC | `bass_mpc` | `bass_mpc` | Direct playback requires the add-on. |
| DSD | `bassdsd` | `bassdsd` | Direct playback requires the add-on. |
| HLS | `basshls` | `basshls` | Live/radio streams should be treated as non-seekable and unknown-duration. |
| WMA | not packaged | `basswma` | macOS should fall back to provider transcode. |
| WebM | `basswebm` | `basswebm` | Direct playback requires the add-on. |

When a format is not covered by BASS core or a packaged add-on on the current platform, Naviamp should request provider transcoding instead of surfacing a codec error to the user. During the current spike, Stats for nerds shows the loaded BASS add-ons, current stream info, and latest BASS error. Deeper native channel diagnostics should be added through the JNI binding rather than the current JNA playback path.

Override the platform for packaging/copy verification with:

```shell
./gradlew -Pnaviamp.bass.platform=macos-arm64 :apps:desktop:packageDmg
./gradlew -Pnaviamp.bass.platform=windows-x64 :apps:desktop:copyDesktopBass :apps:desktop:copyDesktopBassAppResources
```

The packaged app launches directly through the Compose native launcher. BASS is loaded in-process from bundled resources; there are no terminal wrapper scripts or shell launchers required for BASS.

BASS binaries are third-party redistributables from Un4seen. Keep the downloaded license/readme files with the vendor inputs when producing release packages, and verify the selected BASS license is compatible with the distribution type before shipping public builds.

## Waveform Generation

Desktop waveform generation uses BASS decode streams against cached or downloaded audio files. It no longer shells out to mpv, and packaged desktop apps do not bundle an mpv executable for waveform generation.

## Gapless Playback

The BASS desktop engine implements `QueueAwarePlaybackEngine.prepareNext`. During normal queue playback, `PlaylistEngine` asks BASS to create the next stream shortly before the current track ends.

For gapless playback, the engine uses BASSmix queued decode channels. The active track plays through a BASS mixer, and the prepared next track is queued into that mixer before the current source ends. When BASS advances to the queued source, the app adopts that source as the current track instead of stopping and reopening playback. Prepared streams are cleared on stop, seek, source changes, queue jumps, shuffle/restore changes, or when the next request no longer matches the prepared stream.

For crossfade playback, the engine uses the same BASSmix path without queue mode so the prepared next source can overlap the active source. The next source fades in with an equal-power envelope while the current source fades out. Crossfade is only prepared for finite-duration queued tracks; live radio and unknown-duration streams continue without crossfade.

Audio prefetch also performs sidecar prep for upcoming tracks after caching audio: waveform generation, local tag reading for embedded lyrics, provider lyrics, and LRCLIB fallback. Those steps are best-effort and do not fail the audio prefetch if one sidecar source is unavailable.
