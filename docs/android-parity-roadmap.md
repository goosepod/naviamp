# Android Parity Roadmap

This branch tracks the work needed to make the Android app feel like the same product as the desktop app, both in behavior and in visual polish.

## Maintenance Tenets

- Keep Kotlin, Compose, Android Gradle Plugin, SQLDelight, coroutines, serialization, and AndroidX dependencies on the newest stable releases that build cleanly for the repo.
- Prefer stable releases for daily-driver branches. Use EAP/beta toolchains only on short-lived experiment branches with an explicit rollback path.
- Treat dependency freshness as product work, not chores: upgrade in small batches, run Android and desktop validation, and document any temporary pins so they do not become invisible tech debt.
- When package upgrades expose deprecations, either fix them in the same slice when low-risk or add a named checklist item here.
- Build shared behavior in common domain/UI first, then wire platform-specific details only where needed. Android parity work should also be reflected on desktop whenever the feature makes sense there.

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
  - [x] Replace deprecated Compose dependency aliases in Android and shared UI build files with direct dependency coordinates.
  - [x] Add a recurring package review pass before Android parity milestones.
    Before each Android parity milestone, run a dependency review against `gradle/libs.versions.toml`, prefer newest stable packages, and record any intentional pins in this section.

- [ ] **Android release build confidence**
  - [x] Define the build command we will treat as canonical for local Android release validation: `.\gradlew.bat --configure-on-demand :apps:android:assembleRelease`.
  - [x] Confirm the local unsigned release APK is produced at `apps/android/build/outputs/apk/release/android-release-unsigned.apk`.
  - [x] Verify release packaging completes with BASS native build steps for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
  - [x] Verify BASS native libraries are packaged for supported ABIs by inspecting the APK contents.
    `libbass*`, `libnaviamp_bass.so`, and `libc++_shared.so` are present for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
  - [x] Verify the local non-minified release build keeps JNI entry points, Compose/shared UI code, and packaged native libraries.
  - [ ] Add a separate minified release validation pass before enabling Android shrink/minify for daily use.
  - [x] Document APK/AAB output locations, signing expectations, and install commands.
    Debug installs use `.\gradlew.bat --configure-on-demand :apps:android:installDebug`.
    Local unsigned release APKs build to `apps/android/build/outputs/apk/release/android-release-unsigned.apk`.
    Signed release artifacts still need a release keystore/signing config before distribution.

- [ ] **Now Playing visualizer parity**
  - Desktop has the GPU-backed visualizer catalog in `PlatformLiveVisualizerSurface.jvm.kt`.
  - [x] Android has Canvas renderers for every shared `NaviampVisualizer` mode as polished fallbacks to the desktop shader renderers.
  - [x] Persist selected visualizer on Android like desktop does through `VisualizerSettings`.
  - [ ] Tune Android visualizer style and performance on device once phone testing resumes.

- [ ] **Playback settings parity**
  - `AndroidBassPlaybackEngine` reports `supportsReplayGain = true` and `supportsCrossfade = true`.
  - [x] `AndroidSettingsStore.loadPlaybackSettings()` no longer forces `replayGainMode = Off` and `crossfadeDurationSeconds = 0`.
  - [x] Add shared stream/download quality settings for full quality vs Navidrome transcode.
  - [x] Add Android mobile-data policy controls for streaming quality and download allowance.
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
  - [x] Move shared Settings to the same category drill-down model as desktop so Android does not collapse everything into one long page.
  - [x] Surface cache/library sync sizes through the shared Settings cache category.

- [ ] **Library sync and refresh parity**
  - Android indexes artists and albums, and stores media source scan signatures.
  - [x] Use the same lightweight Navidrome scan-signature refresh behavior as desktop.
  - [x] Add an obvious manual refresh/re-sync path for changed Navidrome libraries.
  - [x] Ensure Android does not run a full import on every startup once a usable index exists.
  - [x] Add a reusable shared Library search bar for filtering artists.

- [ ] **Lyrics parity**
  - Android uses provider lyrics and LRCLIB fallback.
  - [x] Add a shared ID3v2 embedded lyrics parser for `USLT` and lyric-flavored `TXXX` frames.
  - [x] Android reads embedded lyrics from already downloaded/cached audio and feeds them into the shared lyrics resolver.
  - Confirm the shortened synced-lyrics lead timing feels correct on Android touch screens.

- [ ] **Downloads and cache management parity**
  - Android can download audio into app storage and clear cache/download data.
  - [x] Shared Downloads route exposes downloaded tracks with play and remove actions on Android.
  - [x] Downloads can save original or transcoded versions based on the shared download quality setting.
  - [x] Android blocks mobile-data downloads unless explicitly allowed.
  - [x] Verify Downloads route exposes all desktop actions, including add-to-playlist where applicable.
  - [x] Add Android playlist download actions matching desktop playlist download behavior.
  - [ ] Move downloads/cache/storage toward shared interfaces with Android and desktop engines behind the same capability contracts.
  - [ ] Use shared download/cache services so desktop and Android apply the same product rules for download planning, status, stats, and playback-source resolution.
    Initial downloads and re-downloads after saved-file quality changes now use shared `DownloadService` over narrow download/replacement repository ports on both platforms.
    Low-level byte/file-store operations still need to move behind shared service ports.
  - Confirm offline playback from downloaded files works after app restart and network loss.
  - [x] Expose cache budgets and storage pressure clearly enough for Android users.

- [ ] **Artist detail parity**
  - Shared artist detail supports popular tracks.
  - Desktop artist detail has richer controls and similar artists.
  - [x] Android artist radio, popular play/radio/add-to-queue, album navigation, and similar artists match desktop.
  - [x] Shared Android artist details expose add-artist-to-queue, add-artist-to-playlist, popular-track download/add-to-playlist, and album row radio/download/queue/playlist actions.
  - [ ] Verify artist-level bulk actions against a large artist library on device.

- [ ] **Album detail parity**
  - [x] Shared album details expose play, shuffle, radio, download, add-to-queue, and add-to-playlist actions.
  - [x] Shared album track rows expose download, add-to-queue, and add-to-playlist actions.
  - [ ] Verify album bulk download/playlist actions on device.

- [ ] **Polish and platform behavior**
  - [x] Use the active Now Playing album-art gradient as the app background across routes, not only inside full Now Playing.
  - Verify full Now Playing layout on phone portrait, phone landscape, tablet portrait, and tablet landscape.
  - Check touch target sizes after the enlarged footer icon changes.
  - Confirm album-art palette extraction is fast enough on Android and cached correctly.
  - Confirm notification controls, Android back behavior, and route restoration feel native.

- [ ] **Android Auto**
  - [x] Add the Android Auto media app descriptor and declare the playback service as a media browser service.
  - [x] Reuse the existing Android playback media session so Android Auto can discover Naviamp and control current playback.
  - [x] Expose an initial safe browse root with Now Playing, Library, Radio, and Downloads entries.
  - [x] Document the local Desktop Head Unit launch flow: start Android Auto head unit server, then run `adb forward tcp:5277 tcp:5277` and `desktop-head-unit.exe`.
  - [x] Wire first-pass Library browsing to indexed artists, albums, tracks, and downloaded tracks.
  - [x] Wire the Library Radio shortcut to the existing shared radio action.
  - [x] Support Android Auto play-from-media-id for browsed tracks, downloads, and Library Radio while the phone app process is active.
  - [x] Add a cold-start handoff that launches the phone app with the selected Android Auto media id and plays once provider restoration completes.
  - [x] Promote the Android BASS playback engine into a process-stable runtime so swiping away the phone UI does not immediately release playback.
  - [x] Start Library Radio, library tracks, and downloaded tracks directly from the Android Auto service when the phone UI is not active.
  - [x] Publish playback position and duration to the Android Auto media session so Auto can show a progress bar.
  - [x] Keep progress ticks from republishing unchanged album-art metadata to reduce full-screen Android Auto artwork flicker.
  - [x] Handle Android Auto seek commands through the shared Android seek path, including transcoded stream restart-on-seek behavior.
  - [x] Show the last saved playback session as the Android Auto Now Playing/Resume item.
  - [x] Resume the last saved queue/radio session from Android Auto play commands after a cold app launch.
  - [x] Pause playback when Android reports the active audio route is disconnecting, so car/DHU disconnects do not intentionally continue on phone speakers.
  - [x] Add Android Auto playlist browsing backed by Navidrome playlist APIs.
  - [x] Add Android Auto internet radio browsing backed by Navidrome station APIs.
  - [ ] Test Assistant voice routing in DHU/AAE and a real vehicle:
    say `Hey Google, play Green Day radio in Naviamp`, `Hey Google, play Camel Fat radio in Naviamp`, and `Hey Google, play Electronica radio in Naviamp`; verify `NaviampAutoCommand` logs show `Auto requested search=...`.
  - [ ] Verify cold-start Android Auto playback from a fully swiped-away app in DHU and a real vehicle.
  - [ ] Verify Android Auto/DHU disconnect pause behavior; some projection disconnects may not send the standard noisy-audio broadcast.
  - [ ] Move Android Auto playback ownership into a true long-lived playback service instead of retained activity callbacks.
  - [ ] Verify in Android Auto desktop head unit or a real vehicle once device testing resumes.

## First Implementation Order

1. Validate Android release packaging and BASS/JNI keep rules.
2. Enable and verify ReplayGain/crossfade settings instead of forcing them off.
3. Bring similar artists into shared artist details.
4. Add Android diagnostics/stats for API calls and playback/cache state.
5. Upgrade Android visualizer rendering toward the desktop catalog.
6. Expand Android Auto browsing and play-from-media-id using shared library/radio/download services.

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
- [ ] Full-quality vs transcoded streaming on Wi-Fi/wired
- [ ] Transcoded streaming on Android mobile data
- [ ] Mobile-data download block/allow behavior
- [ ] Lyrics from provider
- [ ] LRCLIB fallback
- [ ] Popular tracks on artist details
- [ ] Similar artists on artist details
- [ ] Downloads route and offline playback
- [x] Settings diagnostics section
- [x] Settings category drill-down
- [x] Stats/diagnostics API call list
- [x] Downloads route lists downloaded tracks
- [ ] Now Playing portrait and landscape visual check
- [ ] Android Auto discovers Naviamp
- [x] Android Auto notification/session controls work
- [x] Android Auto browse root loads safely
- [x] Android Auto browses indexed artists, albums, tracks, downloads, and Library Radio
- [x] Android Auto starts playback from a browsed track/download/radio item
- [x] Android Auto starts playback from a browsed track/download/radio item after a cold service start
- [x] Android Auto shows playback progress and sends seek commands
