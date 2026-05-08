# Desktop Playback

Naviamp uses a small playback abstraction so queue behavior, UI controls, and provider code do not depend on a specific native audio engine.

## Current Engine

Desktop currently uses `MpvProcessPlaybackEngine` when an `mpv` executable is available. It launches mpv as a child process and controls it over JSON IPC.

The process engine supports:

- Direct/original Navidrome streams
- Pause and resume
- Seeking
- Progress and duration polling
- Gapless mpv mode through `--gapless-audio=yes`

It does not claim crossfade support yet. The `PlaylistEngine` has prepare-next hooks for crossfade-capable engines, but the current process engine does not overlap two streams.

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
