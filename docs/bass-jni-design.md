# BASS JNI Binding Design

This document defines the production direction for Naviamp's BASS integration. The current desktop JNA layer is useful as a spike, but the long-term binding should be JNI so desktop and Android can share the same native control surface.

## Goals

- Keep BASS behind the existing `PlaybackEngine` boundary.
- Support desktop and Android with the same behavior wherever BASS supports it.
- Expose low-latency APIs for visualizers, gapless playback, and crossfade.
- Keep BASS handles, native pointers, and plugin details out of UI and domain code.
- Make native packaging explicit so each app bundle contains the BASS libraries it needs.

## Kotlin Surface

The Kotlin side should expose a small internal API under `apps/desktop` and the future Android player module. It should not become part of the shared domain model.

```kotlin
internal interface BassBinding {
    fun init(device: Int = -1, sampleRate: Int = 44100): BassDeviceInfo
    fun shutdown()

    fun loadPlugin(path: String): BassPluginHandle
    fun createUrlStream(url: String, headers: Map<String, String>, flags: BassStreamFlags): BassStreamHandle
    fun createFileStream(path: String, flags: BassStreamFlags): BassStreamHandle
    fun freeStream(stream: BassStreamHandle)

    fun play(stream: BassStreamHandle, restart: Boolean = false)
    fun pause(stream: BassStreamHandle)
    fun stop(stream: BassStreamHandle)
    fun setVolume(stream: BassStreamHandle, volume: Float)
    fun seekSeconds(stream: BassStreamHandle, seconds: Double)
    fun positionSeconds(stream: BassStreamHandle): Double
    fun durationSeconds(stream: BassStreamHandle): Double?

    fun streamInfo(stream: BassStreamHandle): BassStreamInfo
    fun tags(stream: BassStreamHandle): BassTags
    fun fft(stream: BassStreamHandle, binCount: Int): FloatArray
    fun pcm(stream: BassStreamHandle, frameCount: Int): ShortArray
}
```

Handles should be inline value classes around `Long` on Kotlin/JVM and Kotlin/Android. The JNI layer owns validation and returns clear error objects when a handle is invalid or BASS reports an error.

## Native Surface

The C/C++ JNI library should be a thin adapter around BASS, not a second playback engine. Its responsibilities are:

- Translate Kotlin calls to BASS calls.
- Own callback registration and thread attachment.
- Normalize BASS errors into stable error codes and messages.
- Keep plugin loading deterministic.
- Avoid allocating on high-frequency FFT/PCM paths where practical.

The native library name should be stable, for example `naviamp_bass`, with platform builds:

- macOS: `libnaviamp_bass.dylib`
- Windows: `naviamp_bass.dll`
- Android: `libnaviamp_bass.so`

The initial CMake scaffold lives in `native/bass-jni`.

## Playback And Mixer Model

Basic playback can continue to use one BASS stream per current item. Gapless and crossfade should move to BASSmix instead of trying to coordinate two independent output channels from Kotlin.

The intended model:

- Create a mixer channel for the active output.
- Decode current and prepared-next sources as decode channels.
- Add decode channels to the mixer at exact positions.
- Use mixer envelopes for fade curves.
- Keep queue advancement in `PlaylistEngine`, driven by native callbacks or high-confidence progress events.

This keeps crossfade and gapless timing in native audio code rather than coroutine polling.

## Metadata

The JNI binding should expose raw tag blocks and normalized fields:

- ICY current title and URL for internet radio.
- BASS stream info such as sample rate, channel count, codec/plugin, and flags.
- File tags needed for ReplayGain when provider metadata is missing.
- Current add-on/plugin availability.

Stats for nerds should show which source supplied metadata, ReplayGain, codec support, and current stream format.

## ReplayGain

ReplayGain should be applied app-side through BASS channel attributes unless a BASS add-on provides better native support for a format.

Priority order:

1. Provider metadata from Navidrome when available.
2. File tags from BASS/tag parsing for cached or downloaded media.
3. No gain adjustment.

The engine should expose the active gain source and applied dB value for Stats for nerds.

## Visualizers

Visualizers are the main reason to leave JNA behind. JNI should provide a low-allocation path for:

- FFT bins for spectrum views.
- PCM windows for waveform or oscilloscope views.
- Optional peak/RMS values for lightweight meters.

The UI should consume shared visualizer state, not call BASS directly. Android and desktop should use the same visualizer model even if native buffer timing differs.

## Packaging

Desktop packages should include:

- BASS core library.
- Required BASS add-ons for library codec coverage.
- `naviamp_bass` JNI library.

Android packages should include ABI-specific libraries under `jniLibs` or generated Gradle outputs:

- `arm64-v8a`
- `armeabi-v7a` if needed
- `x86_64` for emulators if supported by the BASS license/package.

Android BASS libraries downloaded from Un4seen are vendored for the JNI work under `native/bass-jni/vendor/android`. The current set covers BASS core plus AAC, AC3, FX, MPC, TTA, ALAC, APE, DSD, FLAC, HLS, MIDI, mix, Opus, WebM, and WavPack across `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.

The app should fail over with a clear diagnostic when the JNI library or BASS core library is missing. During migration only, desktop may still fall back to mpv.

## Migration Plan

1. Keep the current JNA `BassPlaybackEngine` as a behavior reference.
2. Add JNI project layout and native build tasks.
3. Port init, stream creation, playback, pause, stop, seek, volume, duration, and position.
4. Switch desktop `BassPlaybackEngine` from JNA to JNI behind the same Kotlin API.
5. Add metadata, plugin inventory, and Stats for nerds rows.
6. Add BASSmix prepare-next support.
7. Add ReplayGain support.
8. Add FFT/PCM visualizer path.
9. Add Android BASS engine using the same JNI binding shape.
10. Remove mpv packaging when BASS covers known desktop and Android scenarios.
