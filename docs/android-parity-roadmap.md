# Android Parity Roadmap

This branch tracks the work needed to make the Android app feel like the same product as the desktop app, both in behavior and in visual polish.

## Maintenance Tenets

- Keep Kotlin, Compose, Android Gradle Plugin, SQLDelight, coroutines, serialization, and AndroidX dependencies on the newest stable releases that build cleanly for the repo.
- Prefer stable releases for daily-driver branches. Use EAP/beta toolchains only on short-lived experiment branches with an explicit rollback path.
- Treat dependency freshness as product work, not chores: upgrade in small batches, run Android and desktop validation, and document any temporary pins so they do not become invisible tech debt.
- When package upgrades expose deprecations, either fix them in the same slice when low-risk or add a named checklist item here.

## Current Baseline

Android already uses the shared Compose UI shell for the main app surface, so many recent desktop visual changes should carry over automatically:

- Shared home, search, library, album, artist, playlist, radio, settings, mini player, and full Now Playing UI.
- Shared album-art-derived Now Playing background and transport/scrubber/volume accent colors.
- Shared lyrics panel, rating/favorite controls, Up Next/Back To/Related queue panels, and playlist add dialogs.
- Android BASS playback engine with original-stream preference, foreground service controls, seeking, software volume, live metadata, visualizer FFT frames, gapless prep, crossfade hooks, and ReplayGain hooks.
- Android cache/storage layer for provider responses, images, audio cache, downloads, waveforms, lyrics bytes, playback history, local library indexing, and artist popular tracks.
- Deezer popular-track matching is wired through `ArtistPopularTracksService`.
- LRCLIB fallback exists through `AndroidLrclibLyricsClient`.

## Known Parity Gaps

- [ ] **Dependency freshness**
  - [x] Move Kotlin from `2.3.0` to stable `2.3.21`.
  - [ ] Replace deprecated Compose dependency aliases in Android and shared UI build files with direct dependency coordinates.
  - [ ] Add a recurring package review pass before Android parity milestones.

- [ ] **Android release build confidence**
  - [x] Define the build command we will treat as canonical for local Android release validation: `.\gradlew.bat --configure-on-demand :apps:android:assembleRelease`.
  - [x] Confirm the local unsigned release APK is produced at `apps/android/build/outputs/apk/release/android-release-unsigned.apk`.
  - [x] Verify release packaging completes with BASS native build steps for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
  - [x] Verify BASS native libraries are packaged for supported ABIs by inspecting the APK contents.
    `libbass*`, `libnaviamp_bass.so`, and `libc++_shared.so` are present for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
  - Verify release/minified builds do not strip JNI entry points or Compose/shared UI code.
  - Document APK/AAB output locations, signing expectations, and install commands.

- [ ] **Now Playing visualizer parity**
  - Desktop has the GPU-backed visualizer catalog in `PlatformLiveVisualizerSurface.jvm.kt`.
  - Android currently renders a simple Canvas bar visualizer in `PlatformLiveVisualizerSurface.android.kt`.
  - Implement Android renderers for the shared `NaviampVisualizer` modes or deliberately map unsupported modes to a polished fallback.
  - Persist selected visualizer on Android like desktop does through `VisualizerSettings`.

- [ ] **Playback settings parity**
  - `AndroidBassPlaybackEngine` reports `supportsReplayGain = true` and `supportsCrossfade = true`.
  - [x] `AndroidSettingsStore.loadPlaybackSettings()` no longer forces `replayGainMode = Off` and `crossfadeDurationSeconds = 0`.
  - Confirm gapless, crossfade, and ReplayGain behavior match desktop for local files, direct provider streams, and prepared-next transitions.

- [ ] **Similar artists on Android**
  - Desktop has a Deezer-backed similar-artist flow with local library matching and external Deezer links.
  - [x] Shared artist UI now exposes similar artists with image, local-library status, and external-link affordance.
  - [x] Android wires Deezer similar artists through `SimilarArtistsService`.
  - [x] Android local-library matches open the in-app artist detail page.
  - [x] Android non-library matches open the Deezer artist page with a recognizable external-link icon.
  - [ ] Verify Deezer related-artist quality and local matching on device with several artists.

- [ ] **Stats and diagnostics parity**
  - Desktop has Stats for nerds with provider capabilities, API calls, cache stats, library sync stats, stream stats, playback engine diagnostics, Deezer calls, and LRCLIB calls.
  - [x] Add an Android diagnostics section under Settings.
  - [x] Include playback state, ReplayGain/crossfade settings, visualizer visibility, cache sizes, provider features, and library index counts.
  - [x] Include Navidrome, Deezer, and LRCLIB API calls where available.
  - [x] Include BASS load status and active stream info.

- [ ] **Library sync and refresh parity**
  - Android indexes artists and albums, and stores media source scan signatures.
  - Confirm it uses the same lightweight scan-status refresh behavior as desktop.
  - Add an obvious manual refresh/re-sync path for changed Navidrome libraries.
  - Ensure Android does not run a full import on every startup once a usable index exists.

- [ ] **Lyrics parity**
  - Android uses provider lyrics and LRCLIB fallback.
  - Embedded lyrics from downloaded/cached audio are not currently selected in the Android visible lyric path.
  - Decide whether Android should read local audio tags for embedded lyrics like desktop sidecar prep.
  - Confirm the shortened synced-lyrics lead timing feels correct on Android touch screens.

- [ ] **Downloads and cache management parity**
  - Android can download audio into app storage and clear cache/download data.
  - Verify Downloads route exposes downloaded tracks with the same actions as desktop.
  - Confirm offline playback from downloaded files works after app restart and network loss.
  - Expose cache budgets and storage pressure clearly enough for Android users.

- [ ] **Artist detail parity**
  - Shared artist detail supports popular tracks.
  - Desktop artist detail has richer controls and similar artists.
  - Ensure Android artist radio, popular play/radio/add-to-queue, album navigation, and similar artists match desktop.

- [ ] **Polish and platform behavior**
  - Verify full Now Playing layout on phone portrait, phone landscape, tablet portrait, and tablet landscape.
  - Check touch target sizes after the enlarged footer icon changes.
  - Confirm album-art palette extraction is fast enough on Android and cached correctly.
  - Confirm notification controls, Android back behavior, and route restoration feel native.

## First Implementation Order

1. Validate Android release packaging and BASS/JNI keep rules.
2. Enable and verify ReplayGain/crossfade settings instead of forcing them off.
3. Bring similar artists into shared artist details.
4. Add Android diagnostics/stats for API calls and playback/cache state.
5. Upgrade Android visualizer rendering toward the desktop catalog.

## Validation Checklist

- [x] `:apps:android:assembleDebug`
- [x] Android release build command: `:apps:android:assembleRelease`
- [x] Install on device/emulator
- [ ] Connect to Navidrome
- [ ] Play direct original stream
- [ ] Seek/scrub during playback
- [ ] Next/previous queue playback
- [ ] Gapless transition
- [ ] Crossfade transition
- [ ] ReplayGain Track and Album modes
- [ ] Lyrics from provider
- [ ] LRCLIB fallback
- [ ] Popular tracks on artist details
- [ ] Similar artists on artist details
- [ ] Downloads route and offline playback
- [x] Settings diagnostics section
- [x] Stats/diagnostics API call list
- [ ] Now Playing portrait and landscape visual check
