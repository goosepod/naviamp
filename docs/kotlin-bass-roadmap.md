# Kotlin BASS Playback Roadmap

This document tracks the plan for adding BASS to the Kotlin/Compose app while preserving the existing Kotlin UI as the source of truth.

## Goal

Use BASS as the desktop playback backend in the Kotlin app, behind the existing playback interface, so Naviamp can gain fast startup, broad codec support, gapless playback, crossfade, visualizers, and future native audio features without rebuilding the UI.

Kotlin/Compose is the reference desktop UI.

## Current State

- The Kotlin app already has a playback abstraction in `core/domain/src/commonMain/kotlin/app/naviamp/domain/playback/PlaybackEngine.kt`.
- Desktop chooses an engine in `apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/playback/DesktopPlaybackEngineFactory.kt`.
- Desktop now constructs the BASS engine directly through `DesktopPlaybackEngineFactory`.
- The old desktop mpv/JLayer runtime fallback paths have been removed from the active app.
- `DesktopPlaylistEngine` already understands engine capabilities and can use `QueueAwarePlaybackEngine` hooks for prepare-next behavior.
- BASS native libraries are already available in the repo through the Rust app vendor tree for macOS ARM64 and Windows x64.

## Direction Decisions

- Preserve the Kotlin UI. Do not redesign screens as part of BASS work unless playback behavior requires a UI affordance.
- Add BASS as a new engine implementation, not a replacement for the playback interface.
- Keep the playback interface boundary, but make BASS the only active playback engine for the Kotlin app.
- Use JNI underneath the shared BASS facade on desktop and Android. The early desktop JNA spike has been removed after JNI playback was manually proven.
- Keep the Kotlin/domain playback interface clean. Avoid leaking BASS handles or BASS-specific details into UI/domain code.
- Treat BASSmix as the likely path for serious gapless/crossfade support.
- Treat live PCM/FFT access as the future visualizer path; do not revive fake/precomputed visualizers as the final solution.
- BASS is the target playback engine across desktop and Android. The goal is the same behavior and feature set everywhere.
- mpv should not remain bundled or used by the active Kotlin app.
- Waveform generation should use BASS decode streams, not shell helpers.
- Implement as much ReplayGain support as BASS and available metadata allow.

## Phase 1: Native Binding Spike

- [x] Decide JNA vs JNI for desktop BASS bindings.
- [x] Add desktop dependency/build setup for the chosen binding approach.
- [x] Create a small `DesktopBassNative` wrapper for init, free, stream create, play, pause, stop, seek, volume, duration, position, and errors. Removed after the JNI connector replaced it.
- [x] Load `libbass.dylib` on macOS from bundled app resources or development vendor path.
- [x] Load `bass.dll` on Windows from bundled app resources or development vendor path.
- [x] Add focused tests around platform/library path resolution where possible.
- [x] Document local BASS library expectations in `docs/desktop-playback.md`.

## Phase 1.5: Production Binding Direction

- [x] Design a JNI binding surface for BASS that covers playback, BASSmix, PCM/FFT, tags, plugin loading, and error reporting.
- [x] Retain the old desktop JNA binding as a comparison spike until JNI-backed desktop playback is manually proven.
- [x] Add native build layout for macOS, Windows, and Android.
- [x] Decide how generated native artifacts are versioned and copied into app packages.
- [x] Add initial Kotlin/native JNI contract for BASS version and error diagnostics.
- [x] Move `DesktopBassPlaybackEngine` to JNI after stream/control/mixer/FFT/tag/plugin parity is in place.
- [x] Move desktop waveform generation from mpv process decoding to BASS decode streams.
- [x] Remove the old desktop JNA/native connector after JNI-backed desktop playback is manually proven.
  - Manual macOS smoke testing has now passed for crossfade, waveform generation, queue jumping, scrub-bar seeking, and instant playback after scrub.
  - Manual Windows smoke testing has now passed for playback, crossfade, volume, scrub-bar seeking, fast repeated scrubbing, and fast track changes.
  - `DesktopBassNative` and JNA-only BASS support/tests have been removed; JNI is the only active desktop BASS connector.

Design notes live in `docs/bass-jni-design.md`.
Native scaffold lives in `native/bass-jni`.

## Phase 2: Basic Engine

- [x] Add `DesktopBassPlaybackEngine` implementing `PlaybackEngine`.
- [x] Support URL playback from Navidrome provider streams.
- [x] Support local file playback from cached/downloaded audio paths.
- [x] Implement pause, resume, stop, seek, and software volume.
- [x] Poll progress and duration into `PlaybackProgress`.
- [x] Report playback states consistently through the shared playback contract.
- [x] Map BASS error codes into useful `PlaybackState.Error` messages.
- [x] Keep `NAVIAMP_PLAYBACK_ENGINE=bass` and `-Dnaviamp.playback.engine=bass` compatible for development scripts.
- [x] Use BASS as the desktop engine without mpv/JLayer fallback.
- [x] Verify real Navidrome library playback manually on macOS.
- [x] Verify internet radio playback manually on macOS.

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
- [x] Advance app queue state at the correct time without double-starting or losing play reporting.
- [x] Reset prepared-next state on seek, stop, queue jump, and source changes.
- [x] Verify album playback with known gapless albums.

## Phase 7: Crossfade

- [x] Implement configurable crossfade duration through existing playback settings.
- [x] Use BASSmix mixer channels with equal-power fade curves.
- [x] Disable or alter crossfade for live radio and unsupported streams.
- [x] Ensure previous/next/seek cancels active fades cleanly.
- [x] Expose accurate capability flags so the Playback settings screen no longer says crossfade is unavailable.

## Phase 8: ReplayGain And Loudness

- [x] Determine what BASS provides natively versus what Naviamp must apply from provider/tag metadata.
- [x] Support existing ReplayGain Off, Track, and Album modes.
- [x] Read ReplayGain from provider metadata where available.
- [x] Read ReplayGain from local file tags where available.
- [x] Apply gain consistently for BASS-backed engines; Android remains capability-gated until the Android BASS engine lands.
- [x] Avoid clipping when applying gain and crossfade together.
- [x] Surface exactly which ReplayGain source is active in Stats for nerds.

## Phase 9: Visualizers

- [x] Add a JNI-backed BASS PCM/FFT data path.
- [x] Use BASS decode data for cached waveform generation on desktop.
- [x] Feed live visualizer data into shared UI state without coupling UI to BASS.
- [x] Reintroduce a real music-reactive visualizer surface.
- [x] Keep waveform scrubber separate from live visualization.
- [x] Update Now Playing so the live visualizer is not always visible: tapping album art should replace it with the visualizer, tapping the visualizer should return to album art, and the bottom-right Now Playing overflow menu should include the same visualizer toggle.

## Phase 9.5: Android BASS

- [x] Add Android BASS native library packaging.
- [x] Vendor Android BASS core and add-on libraries for JNI work.
- [x] Add Android BASS runtime loader diagnostics.
- [x] Implement Android JNI loading and lifecycle handling.
- [x] Replace or wrap the current Media3 engine with a BASS-backed Android playback engine.
- [x] Preserve Android foreground service and notification behavior.
- [x] Verify Android playback controls, seek, volume/session behavior, and stream metadata at build/API level.
- [x] Align Android capabilities with desktop where supported: BASS playback, ReplayGain, stream metadata, visualizers, gapless, and crossfade are active through BASS/BASSmix.

## Phase 10: Stabilization

- [x] Run desktop tests.
- [x] Run manual playback smoke tests on macOS.
- [x] Run manual playback smoke tests on Windows.
- [x] Test cached audio, downloaded audio, direct provider streams, and internet radio.
- [x] Test sleep/wake, server disconnects, bad URLs, unsupported formats, and Android gapless/crossfade transitions on device/emulator.
  - The obsolete desktop JNA/native connector has been removed; this is the next BASS hardening focus.
  - First hardening slice: shared BASS mixer/prepared-next helpers now release newly-created source/mixer handles when setup fails partway through, preventing leaked BASS streams after bad URLs, unsupported formats, or failed transition setup. Verified with common tests, Android BASS native package verification, and emulator install/launch.
  - Second hardening slice: Android BASS playback now mirrors desktop's playback-session guard so stale async play/seek/crossfade work from superseded sessions cannot overwrite active stream state after rapid skip/scrub or failed starts. Verified with common tests, Android compile/package verification, and emulator install/launch.
  - Third hardening slice: desktop and Android now both consume the common BASS active-state mapping during playback polling, so stalled network streams surface the same loading/buffering state instead of silently diverging by platform. Verified with desktop and Android compile.
  - Fourth hardening slice: shared BASS playback-source selection now has regression coverage for direct playback stream creation failures, mixer playback decode-source failures, and prepared-next decode-source failures. These cover bad URL / unsupported-format failures before platform engines can publish active handles.
  - Emulator smoke coverage now passed on Android debug: start playback from Now Playing, natural end-of-track queue advance, seekbar scrub, rapid Next stress, cached local stream playback, and sleep/wake while playing. Logs showed BASS streams opening from local cache, active playback settling after rapid skips, audio focus duck/restore during sleep/wake, continued session position saves, and no `AndroidRuntime`, `FATAL`, JNI, or BASS crash lines.
  - Fifth hardening slice: emulator network-off testing while a cached local stream was playing kept playback in `PLAYING`, continued session position saves through the outage, restored network afterward, and left the process alive with no app/BASS crash lines.
  - Sixth hardening slice: force-stop/relaunch restored the current track and queue position, resumed playback from the saved position, opened BASS from the cached file URL, applied the restored seek (`seconds=154.78`), and advanced normally afterward.
  - Seventh hardening slice: Android near-end transition testing sought to `228s`, advanced the queue at source end, adopted the next BASS source at the expected crossfade offset (`position=8.05`), reacquired the wake lock, and continued prefetch/session saves without stale-source or crash lines.
  - Eighth hardening slice: live provider-stream interruption was tested by removing the next track's cached file through `run-as`, confirming BASS opened the `https://.../stream.view` provider URL, then disabling emulator wifi/data for 30 seconds. The stream stayed in `PLAYING`, media/session positions advanced from roughly `41s` to `74s`, network restored cleanly, and logs showed no `AndroidRuntime`, `FATAL`, `NaviampBass` error, or stalled-state lines. This confirms buffered live remote playback does not crash, leak, or leave stale state during a device network outage; a server-side socket-kill test remains optional if we need to force BASS into an actual stalled/error state.
  - Android BASS hardening matrix:
    - [x] Sleep/wake while playing: playback should either continue with the foreground service/wake lock or resume cleanly without losing queue/progress state.
    - [x] Cached/local playback during device interruption: active cached streams should continue without requiring network access.
    - [x] Bad provider URL / source open failure contract: playback should fail with a useful BASS error message, stop foreground-service playing state, and leave no active/prepared handles.
    - [x] Unsupported format / decode-source failure contract: provider-stream playback should surface the codec/format failure and leave the queue usable for skip/next.
    - [x] Rapid skip/scrub during playback: stale async playback work should not overwrite the active stream state.
    - [x] Server disconnect during live remote playback: live provider streams should continue when buffering covers the outage, or enter a clear playback error/stalled state without leaking prepared BASS sources. Emulator network-off testing verified the buffered-provider path through a 30-second outage without app/BASS crash or stale state; server-side socket-kill remains optional diagnostic coverage for forcing a hard remote stall.
    - [x] Android gapless transition: queued BASSmix source should adopt without restarting audio or leaving the old source audible.
    - [x] Android crossfade transition: current source should fade out, next source should fade in, ReplayGain should stay source-local, and rapid skip/scrub should reset stale transition state.
    - [x] Android process/app restart restore: restored playback should prefer cached/downloaded audio, seek to the saved position, and recover cleanly if the saved stream can no longer be opened.
    - [x] Emulator/device diagnostics: capture `NaviampBass`, `NaviampPlayback`, `NaviampCache`, and `AndroidRuntime` logs for each scenario.
- [x] Fix rapid skip stress case where crossfade could leave an older BASS source audible after quick forward/backward navigation.
- [x] Make BASS the default desktop engine.
- [x] Remove the active desktop mpv/JLayer fallback path.
- [x] Remove Android Media3 playback fallback so Android remains fully BASS-backed.
- [x] Build Android debug APK and compile desktop after stabilization changes.

## Active Feature: Artist Popular Tracks

- [x] Add a local database model for artist popular-track metadata, including source, rank, fetched timestamp, and matched local track ID.
- [x] Use Deezer as an enrichment source for artist top tracks while keeping the local library as the source of playable truth.
- [x] Match Deezer popular tracks to local library tracks by normalized artist/title, with album and duration as secondary confidence checks when available.
- [x] Show a Popular Tracks section on artist detail screens when local matches exist.
- [x] Add actions for Play Popular Tracks, Add Popular Tracks to Queue, and Start Popular Tracks Radio.
- [x] For Popular Tracks Radio, seed playback from one popular local track, generate radio from each matched popular track, append the results, and dedupe the queue.
- [x] Add per-track overflow actions for individual popular tracks once the shared Add to Queue menu work lands.

## Future Feature: Cached Sidecar Prep

- [x] When audio prefetch caches upcoming tracks, also run sidecar prep for waveform generation, tag reading, provider/embedded lyrics, and LRCLIB fallback so Now Playing metadata is ready before playback starts.
- [x] Prefer cached audio files for waveform analysis on Android and desktop so track changes can display the waveform immediately instead of decoding over the network during playback.
- [x] Track sidecar-prep status per cached track so failed lyrics or waveform attempts can be retried without blocking playback.

Progress notes:
- Desktop prefetch already runs sidecar prep for cached upcoming tracks: waveform generation, tag reading, embedded lyrics, provider lyrics, and LRCLIB fallback.
- Android now starts sidecar prep for the current track and first few upcoming tracks, prefers downloaded/cached audio for waveform analysis, writes waveform rows into the shared cache table, respects the configured Navidrome TLS behavior, and warms provider/LRCLIB lyrics when lyrics work is enabled.
- Desktop Stats for nerds now shows sidecar prep completion/failure counts and the latest sidecar error separately from audio cache prefetch failures, so waveform/lyrics prep problems are visible without interrupting playback.
- Sidecar prep now records durable per-track status for waveform and lyrics work. Desktop records waveform, provider lyrics, embedded lyrics, and LRCLIB separately; Android records waveform and lyrics status through the shared storage table.
- Remaining polish is Android embedded-lyrics tag extraction.

## Future Cleanup: Shared Network Clients

- [x] Move LRCLIB request construction, response parsing, and selection behavior into common code with only a tiny platform HTTP adapter.
- [x] Move Navidrome request construction, response parsing, API call history, and endpoint behavior further into common code; keep platform-specific TLS/certificate handling behind an HTTP adapter.
- [x] Reuse the Deezer popular-track client shape as the model: common feature client plus platform `GET` implementation.

Progress notes:
- Added a shared common `SharedHttpClient` GET adapter contract and common URL encoding helper.
- Deezer popular tracks and LRCLIB now use the shared HTTP contract. LRCLIB URL construction and headers moved into common code; Android and desktop only provide platform GET implementations.
- Desktop Deezer API call history is still preserved by wrapping the shared desktop HTTP adapter with Deezer-specific call recording.
- Navidrome API call history sanitization and call record construction now live in common code; platform clients only perform transport and TLS/certificate handling.

## Future Feature: Queue Actions

- [x] Add first-class queue append operations for tracks, albums, artists, playlists, search results, radio seeds, and popular-track groups.
- [x] Add Add to Queue to overflow menus throughout the Kotlin UI, following the existing Kotlin menu/icon patterns.
- [x] Add direct Add to Queue buttons where the Kotlin UI already presents high-level album or artist actions.
- [x] Keep queue operations explicit: Play Now replaces the queue, Start Radio creates a generated queue, Add to Queue appends, and Play Next can be added later as an insert-after-current action.

Progress notes:
- Desktop has a first Add to Queue pass for track, album, artist, playlist, search, library, playlist detail, album detail, home album, and popular-track surfaces.
- Android has Add to Queue for popular-track groups, individual track rows in search/album/playlist/popular tracks, and high-level album/playlist detail actions; broader artist/home row actions remain follow-up polish.
- Queue semantics are now explicit in implementation and UI labels: play actions replace, radio actions generate, and Add to Queue appends. Play Next remains a later optional command.

## Future Optimization: Radio Startup

- [x] Profile radio generation paths. Playback starts immediately from the seed track, but similar-track generation can take more than five seconds and should be made visibly faster.
- [x] Decide on radio seed expansion caching.
- [x] Consider progressive radio queue loading: start playback with the seed track, append the first small batch as soon as it is available, then continue filling the queue in the background.
- [x] Surface radio generation status without blocking transport controls or making the app feel stalled.

Progress notes:
- Popular Tracks Radio now limits expansion to the first five popular-track seeds and fetches each seed radio in parallel on desktop and Android, instead of running each related-track request serially before adding the generated queue.
- Seeded track/album/artist radio now requests only 10 similar tracks for the first response, starts with those results, then expands in the background with larger 25/50-count requests and appends only newly discovered tracks.
- Desktop and Android now show background radio queue-building status while expansion requests are still appending results, then clear back to the normal playback state.
- We are intentionally not adding persistent similar-track caching yet; repeated seeds would benefit, but one-off radio starts could grow the cache without much payoff.

## Open Questions

- What is the smallest JNI surface that gives us playback, BASSmix, tags, and FFT without overbuilding the native layer?
- Which BASS add-ons are required for the formats in the real library, and which are optional?
- Which old docs/tests still mention retired playback engines and should be cleaned as follow-up documentation debt?
- How much ReplayGain can be implemented directly through BASS versus app-side metadata/tag handling?
- What is the Android BASS packaging/licensing shape for release builds?

## Done

- [x] Preserve an engine interface boundary in Kotlin.
- [x] Confirm Kotlin UI remains the visual source of truth.
- [x] Merge Android separation and Rust/BASS exploration branches into `main`.
- [x] Cut `codex/kotlin-bass-playback` from updated `main`.
