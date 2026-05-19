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

## Bundling mpv

For local packaging, place the platform mpv executable in:

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

The production desktop target is bundled mpv/libmpv so users do not need a separate install. The process engine keeps development moving, but the crossfade path likely requires a richer engine implementation, either libmpv integration or a controlled two-player strategy.

The next desktop playback investigation is BASS inside the Kotlin app, tracked in `docs/kotlin-bass-roadmap.md`. The intent is to keep the existing Kotlin UI and put BASS behind the current playback interface.

## BASS Spike

The Kotlin desktop app has an initial JNA-based BASS binding spike under:

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

## Waveform Generation

Desktop waveform generation uses BASS decode streams against cached or downloaded audio files. It no longer shells out to mpv, and packaged desktop apps do not bundle an mpv executable for waveform generation.

The desktop Gradle build copies the current platform BASS libraries from:

```text
apps/desktop-slint/vendor/bass/<platform>
```

into generated desktop resources at:

```text
playback/bass/<platform>
```

Override the platform for packaging with:

```shell
./gradlew -Pnaviamp.bass.platform=macos-arm64 :apps:desktop:packageDmg
```

Once BASS is stable, mpv should no longer be bundled. The mpv process engine can remain temporarily as a development fallback during the transition.
