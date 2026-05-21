# Naviamp BASS JNI

This directory is the production native binding layout for BASS.

The current Kotlin desktop player still uses the JNA spike under `apps/desktop`, but JNI work should happen here so desktop and Android can converge on one native BASS control surface.

## Targets

- macOS: `libnaviamp_bass.dylib`
- Windows: `naviamp_bass.dll`
- Android: `libnaviamp_bass.so`

## Planned Modules

- Device init and shutdown
- Stream creation for URLs and files
- Playback control and progress
- BASS add-on/plugin loading
- BASSmix prepare-next, gapless, and crossfade
- Tag and ICY metadata extraction
- ReplayGain metadata and applied gain reporting
- PCM/FFT visualizer buffers

## Vendored Android BASS Libraries

Android BASS libraries are stored under `vendor/android/<abi>` for:

- `arm64-v8a`
- `armeabi-v7a`
- `x86`
- `x86_64`

Each ABI currently has:

- `libbass.so`
- `libbass_aac.so`
- `libbass_ac3.so`
- `libbass_fx.so`
- `libbass_mpc.so`
- `libbass_tta.so`
- `libbassalac.so`
- `libbassape.so`
- `libbassdsd.so`
- `libbassflac.so`
- `libbasshls.so`
- `libbassmidi.so`
- `libbassmix.so`
- `libbassopus.so`
- `libbasswebm.so`
- `libbasswv.so`

BASS headers are stored under `vendor/include`.

## Build Status

The CMake target currently exposes BASS version and last-error diagnostics. It is intentionally not used as the active desktop playback path until the JNI API reaches parity with the JNA spike.
