# BASS JNI Binding Design

This document defines the production direction for Naviamp's BASS integration. Desktop and Android route active BASS work through JNI so they can share the same native control surface. The older desktop JNA BASS layer has been removed after proving JNI-backed desktop playback.

## Goals

- Keep BASS behind the existing `PlaybackEngine` boundary.
- Support desktop and Android with the same behavior wherever BASS supports it.
- Expose low-latency APIs for visualizers, gapless playback, and crossfade.
- Keep BASS handles, native pointers, and plugin details out of UI and domain code.
- Make native packaging explicit so each app bundle contains the BASS libraries it needs.

## Kotlin Surface

The Kotlin side should expose two layers:

- A shared app-facing BASS facade in common code for behavior that desktop and Android should use identically.
- Platform/native bridge adapters below that facade, where desktop and Android now both wrap JNI.

The shared facade slices are `BassAudioBackend` and `BassStreamHandle`. The facade started with decode-stream waveform reads and now also models playback streams, active state, stream metadata, FFT, seek/progress, volume slides, BASSmix channel creation/add/remove, end sync, stream release, version diagnostics, plugin diagnostics, and shared playback helper operations. App-level playback, waveform, visualizer, gapless, and crossfade code should use this facade before any platform connector details.

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

The first committed JNI contract exposes BASS version and last-error diagnostics through:

- Android: `app.naviamp.android.playback.AndroidBassJni`
- Desktop: `app.naviamp.desktop.playback.bass.DesktopBassJniBinding`

Android startup logs the packaged JNI diagnostic line after load, including BASS version, BASSmix version, and last error code. Desktop has JNI integration tests that load the packaged `naviamp_bass` library and exercise version, init/free, stream creation, play/pause/stop, seek, position/duration, volume, decode reads, FFT, and mixer creation.

Playback and waveform analysis now use the shared `BassAudioBackend` facade. Desktop wraps JNI below `DesktopBassAudioBackend`; Android wraps JNI below `AndroidBassAudioBackend`. Android debug APKs now build and package `libnaviamp_bass.so` for all vendored ABIs through the Android CMake build, and `:apps:android:verifyDebugBassNativePackage` verifies the packaged JNI/BASS library set. The old desktop native/JNA connector has been removed; JNI is the only active desktop BASS connector.

## Playback And Mixer Model

Basic playback can continue to use one BASS stream per current item. Gapless and crossfade should move to BASSmix instead of trying to coordinate two independent output channels from Kotlin.

The intended model:

- Create a mixer channel for the active output.
- Decode current and prepared-next sources as decode channels.
- Add decode channels to the mixer at exact positions.
- Use mixer envelopes for fade curves.
- Keep queue advancement in `DesktopPlaylistEngine`, driven by native callbacks or high-confidence progress events.

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

Android packages include ABI-specific libraries under `jniLibs` and generated Gradle outputs:

- `arm64-v8a`
- `armeabi-v7a` if needed
- `x86_64` for emulators if supported by the BASS license/package.

Android BASS libraries downloaded from Un4seen are vendored for the JNI work under `native/bass-jni/vendor/android`. The current set covers BASS core plus AAC, AC3, FX, MPC, TTA, ALAC, APE, DSD, FLAC, HLS, MIDI, mix, Opus, WebM, and WavPack across `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.

The app should fail with a clear diagnostic when the JNI library or BASS core library is missing. BASS is the active playback path; legacy fallback behavior should not hide native packaging or load failures.

## Artifact Policy

Third-party BASS binaries are treated as vendored inputs and live under `native/bass-jni/vendor`. Generated Naviamp JNI artifacts are not committed. They are built into Gradle output directories and copied into app packages from generated build paths.

Current packaging policy:

- Desktop BASS libraries are copied from the desktop vendor tree into generated desktop resources.
- Desktop `naviamp_bass` JNI libraries are built from `native/bass-jni` by Gradle for desktop packages and copied beside the BASS libraries.
- Android BASS libraries are packaged from `native/bass-jni/vendor/android` through the Android app's `jniLibs` source set.
- Android `naviamp_bass` JNI libraries are built from `native/bass-jni` by Gradle for debug APKs and packaged beside the vendored BASS dependency libraries.
- `:apps:android:verifyDebugBassNativePackage` fails the build if any debug APK ABI is missing `libnaviamp_bass.so`, `libbass.so`, `libbassmix.so`, or `libc++_shared.so`.

## Migration Plan

1. Keep the shared `BassAudioBackend` facade as the app-facing BASS contract.
2. Keep desktop and Android native loading/package differences below platform backend adapters only.
3. Continue validating stream creation, playback, pause, stop, seek, volume, duration, position, metadata, FFT, and mixer behavior through shared helpers.
4. Keep desktop manual playback validation focused on JNI-backed playback, waveform generation, gapless/crossfade, ReplayGain, metadata, and plugin diagnostics.
5. Keep obsolete connector-specific desktop native/JNA code out of the active app; JNI is now the only desktop BASS connector.

## Next Handoff

Desktop JNI-backed playback has been manually smoke-tested on macOS and Windows for crossfade, waveform generation, queue jumping, scrub-bar seeking, fast repeated scrubbing, volume changes, track changes, and instant playback after scrub. The old desktop JNA/native BASS connector has been removed.

The next major BASS work is Android/device hardening: sleep/wake, server disconnects, bad URLs, unsupported formats, and Android gapless/crossfade transitions on device or emulator.
