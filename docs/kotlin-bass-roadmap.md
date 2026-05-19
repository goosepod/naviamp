# Kotlin BASS Playback Roadmap

This document tracks the plan for adding BASS to the Kotlin/Compose app while preserving the existing Kotlin UI as the source of truth.

## Goal

Use BASS as the desktop playback backend in the Kotlin app, behind the existing playback interface, so Naviamp can gain fast startup, broad codec support, gapless playback, crossfade, visualizers, and future native audio features without rebuilding the UI.

The Rust/Slint app can remain a parallel experiment, but Kotlin/Compose is the reference UI.

## Current State

- The Kotlin app already has a playback abstraction in `core/domain/src/commonMain/kotlin/app/naviamp/domain/playback/PlaybackEngine.kt`.
- Desktop chooses an engine in `apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/playback/PlaybackEngineFactory.kt`.
- Desktop currently prefers mpv through `MpvProcessPlaybackEngine`.
- JLayer remains a fallback path.
- `PlaylistEngine` already understands engine capabilities and can use `QueueAwarePlaybackEngine` hooks for prepare-next behavior.
- BASS native libraries are already available in the repo through the Rust app vendor tree for macOS ARM64 and Windows x64.

## Direction Decisions

- Preserve the Kotlin UI. Do not redesign screens as part of BASS work unless playback behavior requires a UI affordance.
- Add BASS as a new engine implementation, not a replacement for the playback interface.
- Keep engine selection configurable during development so mpv remains available as a fallback until BASS is stable.
- Use JNA only as the first desktop spike. Move to JNI for the production BASS binding, especially for low-latency visualizers, crossfade/gapless control, and platform parity.
- Keep the Kotlin/domain playback interface clean. Avoid leaking BASS handles or BASS-specific details into UI/domain code.
- Treat BASSmix as the likely path for serious gapless/crossfade support.
- Treat live PCM/FFT access as the future visualizer path; do not revive fake/precomputed visualizers as the final solution.
- BASS is the target playback engine across desktop and Android. The goal is the same behavior and feature set everywhere.
- mpv should not remain bundled once BASS is stable. Keep it only as a temporary development fallback while the BASS implementation matures.
- Waveform generation should use BASS decode streams, not mpv or shell helpers.
- Implement as much ReplayGain support as BASS and available metadata allow.

## Phase 1: Native Binding Spike

- [x] Decide JNA vs JNI for desktop BASS bindings.
- [x] Add desktop dependency/build setup for the chosen binding approach.
- [x] Create a small `BassNative` wrapper for init, free, stream create, play, pause, stop, seek, volume, duration, position, and errors.
- [x] Load `libbass.dylib` on macOS from bundled app resources or development vendor path.
- [x] Load `bass.dll` on Windows from bundled app resources or development vendor path.
- [x] Add focused tests around platform/library path resolution where possible.
- [x] Document local BASS library expectations in `docs/desktop-playback.md`.

## Phase 1.5: Production Binding Direction

- [x] Design a JNI binding surface for BASS that covers playback, BASSmix, PCM/FFT, tags, plugin loading, and error reporting.
- [x] Keep the current JNA binding as the comparison spike until JNI reaches feature parity.
- [x] Add native build layout for macOS, Windows, and Android.
- [x] Decide how generated native artifacts are versioned and copied into app packages.
- [x] Add initial Kotlin/native JNI contract for BASS version and error diagnostics.
- [x] Keep `BassPlaybackEngine` on JNA until JNI playback parity is proven.
- [x] Move desktop waveform generation from mpv process decoding to BASS decode streams.

Design notes live in `docs/bass-jni-design.md`.
Native scaffold lives in `native/bass-jni`.

## Phase 2: Basic Engine

- [x] Add `BassPlaybackEngine` implementing `PlaybackEngine`.
- [x] Support URL playback from Navidrome provider streams.
- [x] Support local file playback from cached/downloaded audio paths.
- [x] Implement pause, resume, stop, seek, and software volume.
- [x] Poll progress and duration into `PlaybackProgress`.
- [x] Report playback states consistently with the current mpv engine.
- [x] Map BASS error codes into useful `PlaybackState.Error` messages.
- [x] Add engine selection with `NAVIAMP_PLAYBACK_ENGINE=bass` and `-Dnaviamp.playback.engine=bass`.
- [x] Use BASS by default when the native library is available, with mpv fallback if BASS is unavailable.
- [x] Verify real Navidrome library playback manually on macOS.
- [ ] Verify internet radio playback manually on macOS.

## Phase 3: Packaging

- [x] Add Gradle copy tasks for BASS native libraries under desktop generated resources.
- [x] Bundle macOS ARM64 BASS libraries in `.app` packages.
- [x] Bundle Windows x64 BASS libraries in Windows packages.
- [x] Verify packaged apps launch without external BASS install paths.
- [x] Ensure BASS libraries do not open terminal windows or require shell wrappers.
- [x] Update app packaging docs with BASS library locations and licensing notes.

## Phase 4: Metadata And Radio

- [x] Request ICY metadata for internet radio streams where BASS requires explicit flags/options.
- [x] Verify BASS resolves playlist-backed radio URLs such as PLS and M3U endpoints on macOS.
- [x] Convert BASS stream metadata into `PlaybackStreamMetadata`.
- [x] Preserve station title as secondary text while updating current song title from ICY metadata.
- [x] Verify SomaFM and Navidrome internet radio stations update metadata.
- [x] Handle unknown duration and live streams without scrubber glitches.

## Phase 5: Codec Coverage

- [x] Confirm original Navidrome streams play for MP3, FLAC, Opus, AAC/M4A, ALAC, Vorbis, WavPack, APE, MPC, DSD, and HLS where libraries are available.
- [x] Add required BASS add-ons to macOS package.
- [x] Add required BASS add-ons to Windows package.
- [x] Decide which formats should fall back to provider transcoding if the native add-on is unavailable.
- [x] Update Stats for nerds with BASS engine name, add-on availability, current stream info, and error detail.

## Phase 6: Gapless

- [x] Implement `QueueAwarePlaybackEngine.prepareNext` for BASS.
- [x] Use BASSmix queued channels so BASS owns track-to-track transitions instead of waiting for app-level end polling.
- [x] Adopt already-queued BASS sources when the app queue advances naturally, keeping UI state in sync without restarting audio.
- [x] Prepare repeat-queue wraparound transitions so looping albums can queue the first track before the last track ends.
- [x] When audio prefetch caches upcoming tracks, also run sidecar prep for waveform generation, tag reading, provider/embedded lyrics, and LRCLIB fallback so Now Playing metadata is ready before playback starts.
- [x] Advance app queue state at the correct time without double-starting or losing play reporting.
- [x] Reset prepared-next state on seek, stop, queue jump, and source changes.
- [ ] Verify album playback with known gapless albums.

## Phase 7: Crossfade

- [ ] Implement configurable crossfade duration through existing playback settings.
- [ ] Use two prepared channels or mixer channels with equal-power fade curves.
- [ ] Disable or alter crossfade for live radio and unsupported streams.
- [ ] Ensure previous/next/seek cancels active fades cleanly.
- [ ] Expose accurate capability flags so the Playback settings screen no longer says crossfade is unavailable.

## Phase 8: ReplayGain And Loudness

- [ ] Determine what BASS provides natively versus what Naviamp must apply from provider/tag metadata.
- [ ] Support existing ReplayGain Off, Track, and Album modes.
- [ ] Read ReplayGain from provider metadata where available.
- [ ] Read ReplayGain from local file tags where available.
- [ ] Apply gain consistently across desktop and Android.
- [ ] Avoid clipping when applying gain and crossfade together.
- [ ] Surface exactly which ReplayGain source is active in Stats for nerds.

## Phase 9: Visualizers

- [ ] Add a JNI-backed BASS PCM/FFT data path.
- [x] Use BASS decode data for cached waveform generation on desktop.
- [ ] Feed live visualizer data into shared UI state without coupling UI to BASS.
- [ ] Reintroduce a real music-reactive visualizer surface.
- [ ] Keep waveform scrubber separate from live visualization.

## Phase 9.5: Android BASS

- [x] Add Android BASS native library packaging.
- [x] Vendor Android BASS core and add-on libraries for JNI work.
- [x] Add Android BASS runtime loader diagnostics.
- [ ] Implement Android JNI loading and lifecycle handling.
- [ ] Replace or wrap the current Media3 engine with a BASS-backed Android playback engine.
- [ ] Preserve Android foreground service and notification behavior.
- [ ] Verify Android playback controls, seek, volume/session behavior, and stream metadata.
- [ ] Align Android capabilities with desktop: gapless, crossfade, ReplayGain, and visualizers where supported.

## Phase 10: Stabilization

- [ ] Run desktop tests.
- [ ] Run manual playback smoke tests on macOS.
- [ ] Run manual playback smoke tests on Windows.
- [ ] Test cached audio, downloaded audio, direct provider streams, and internet radio.
- [ ] Test sleep/wake, server disconnects, bad URLs, unsupported formats, and rapid track skipping.
- [ ] Make BASS the default desktop engine after stability is proven.
- [ ] Keep mpv fallback available until BASS covers the known edge cases.

## Future Feature: Artist Popular Tracks

- [ ] Add a local database model for artist popular-track metadata, including source, rank, fetched timestamp, and matched local track ID.
- [ ] Use Deezer as an enrichment source for artist top tracks while keeping the local library as the source of playable truth.
- [ ] Match Deezer popular tracks to local library tracks by normalized artist/title, with album and duration as secondary confidence checks when available.
- [ ] Show a Popular Tracks section on artist detail screens when local matches exist.
- [ ] Add actions for Play Popular Tracks, Add Popular Tracks to Queue, and Start Popular Tracks Radio.
- [ ] For Popular Tracks Radio, seed playback from one popular local track, generate radio from each matched popular track, append the results, and dedupe the queue.

## Future Feature: Queue Actions

- [ ] Add first-class queue append operations for tracks, albums, artists, playlists, search results, radio seeds, and popular-track groups.
- [ ] Add Add to Queue to overflow menus throughout the Kotlin UI, following the existing Kotlin menu/icon patterns.
- [ ] Add direct Add to Queue buttons where the Kotlin UI already presents high-level album or artist actions.
- [ ] Keep queue operations explicit: Play Now replaces the queue, Start Radio creates a generated queue, Add to Queue appends, and Play Next can be added later as an insert-after-current action.

## Open Questions

- What is the smallest JNI surface that gives us playback, BASSmix, tags, and FFT without overbuilding the native layer?
- Which BASS add-ons are required for the formats in the real library, and which are optional?
- Which mpv fallback paths can be deleted once BASS is stable?
- How much ReplayGain can be implemented directly through BASS versus app-side metadata/tag handling?
- What is the Android BASS packaging/licensing shape for release builds?

## Done

- [x] Preserve an engine interface boundary in Kotlin.
- [x] Confirm Kotlin UI remains the visual source of truth.
- [x] Merge Android separation and Rust/BASS exploration branches into `main`.
- [x] Cut `codex/kotlin-bass-playback` from updated `main`.
