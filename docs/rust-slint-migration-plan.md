# Rust/Slint Desktop Migration Plan

This plan moves Naviamp toward a small, responsive Rust/Slint desktop app while preserving the cross-platform architecture lessons from the Kotlin codebase. The immediate targets are Windows and macOS. Linux should stay structurally easy, and Android should remain possible, but Android is not the first Rust/Slint production target.

## Goals

- Ship small portable desktop builds.
- Keep the app responsive from launch through playback.
- Preserve the provider-oriented architecture from the Kotlin app.
- Keep core behavior platform-neutral whenever possible.
- Focus on Windows and macOS first, with Linux and Android considered at every boundary.
- Migrate feature-by-feature without breaking the current Kotlin app.

## Current Proof Point

The first Rust/Slint spike exists in `apps/desktop-slint`.

It currently:

- Builds a Windows release exe around 6.23 MiB.
- Saves a basic Navidrome connection.
- Searches Navidrome tracks through `search3`.
- Plays a selected track through native BASS playback.

This proves the size/startup direction is viable, but it is not yet architected as the production desktop app.

## Repository Shape

Target shape:

```text
naviamp/
  apps/
    desktop/             Kotlin Compose desktop, kept working until replacement is ready
    desktop-slint/       Rust/Slint production desktop app
    android/             Kotlin Android app for now
  rust/
    naviamp-core/        Platform-neutral Rust domain logic
    naviamp-provider/    Provider traits and provider-neutral API models
    naviamp-navidrome/   Navidrome/Subsonic implementation
    naviamp-storage/     SQLite schema, repositories, migrations
    naviamp-playback/    Playback contracts and BASS/native audio control
  docs/
```

The `rust/` crates do not need to be created all at once. Start with modules inside `apps/desktop-slint/src`, then extract to crates once boundaries stabilize.

## Separation Of Concerns

### Domain Core

Owns provider-neutral models and pure behavior:

- `Artist`, `Album`, `Track`, `Playlist`, `Genre`, `InternetRadioStation`
- IDs and source identity
- Stream quality and transcode choices
- Playback state, progress, queue state
- Rating/favorite state
- Lyrics models and timestamp parsing
- Radio seed and queue rules
- Home section models
- Formatting helpers that are not UI toolkit-specific

Rules:

- No Slint types.
- No OS paths.
- No HTTP client.
- No SQLite connection.
- Unit-test heavily.

### Provider Boundary

Owns a Rust equivalent of Kotlin `MediaProvider`:

- Validate connection.
- Search artists/albums/tracks.
- Fetch album/artist details.
- Fetch home sections and album lists.
- Fetch playlists and playlist tracks.
- Fetch genres and internet radio stations.
- Create stream URLs only at playback/cache time.
- Favorite/rating mutations.
- Lyrics lookup.
- Play reporting/scrobble APIs.

Rules:

- Provider implementations map native API payloads into domain models.
- UI never builds provider URLs directly.
- Authenticated stream URLs stay short-lived and are not persisted.

### Storage Boundary

Owns SQLite and filesystem persistence:

- Saved media sources.
- Cached provider responses.
- Cached images.
- Local artist/album/track library index.
- Playback sessions.
- Playback history.
- Audio cache.
- Downloads.
- Lyrics cache.
- Waveform cache.

Rules:

- Use a single Rust migration system, likely `sqlx` migrations or `rusqlite` plus checked migration files.
- Keep schema conceptually aligned with `core/storage/.../NaviampStorage.sq`.
- Store provider IDs and display metadata, not stream URLs.
- Keep audio cache separate from user downloads.
- Keep platform paths behind a `StoragePaths` adapter.

### Playback Boundary

Owns playback engines and queue interaction:

- `PlaybackEngine` trait.
- `BassPlaybackEngine` for native playback.
- `DROP` as the future public/project name for Naviamp's BASS integration layer and runtime module.
- Future alternate engines only when a platform needs one.
- Progress polling.
- Pause/resume/seek/volume.
- Stream metadata.
- Future queue-aware/crossfade engine.
- Future media-key and OS now-playing integration.

Rules:

- Queue logic is not inside BASS FFI code.
- BASS library resolution is platform-aware but isolated.
- BASS is the long-term desktop playback backend. Runtime libraries and codec plugins are distributed with Naviamp builds.
- The BASS integration should eventually move into its own directory/module named `DROP`, with its runtime libraries colocated there.
- Windows/macOS/Linux differences stay behind the same engine trait.
- Android should have a separate playback implementation later if Slint Android becomes serious.

### App State Boundary

Owns long-lived state and async orchestration:

- Active source/provider.
- Current route.
- Search state.
- Library state.
- Home state.
- Queue and now-playing state.
- Settings state.
- Background sync jobs.
- Cache/download jobs.

Rules:

- Slint callbacks should call app-controller methods, not directly perform business logic.
- Keep `main.rs` thin.
- Use a simple message/action pattern before state gets large.

### UI Boundary

Owns Slint components only:

- Shell/navigation.
- Media rows.
- Now playing views.
- Settings forms.
- Dialogs.
- Menus/popups.
- Artwork display.
- Lyrics display.
- Waveform/scrub controls.

Rules:

- `.slint` files receive view models, not provider/storage objects.
- UI components stay reusable: `TrackRow`, `AlbumRow`, `ArtistRow`, `MiniPlayer`, `NowPlaying`, `SettingsSection`, etc.
- Keep dense, playback-focused design as a visual target.

## Build And Tooling Plan

### Windows

- Build with stable Rust MSVC.
- Require Visual Studio C++ Build Tools.
- Release command:

```powershell
cd apps/desktop-slint
cargo build --release
```

- Portable output:

```text
apps/desktop-slint/target/release/naviamp.exe
```

- Add a `dist` task/script later that copies:
  - `Naviamp.exe`
  - bundled BASS runtime libraries and plugin DLLs
  - license/notice files

### macOS

- Build on macOS with stable Rust.
- Support both `aarch64-apple-darwin` and `x86_64-apple-darwin`.
- Start with unsigned `.app` or raw binary for local dev.
- Later add signing/notarization.
- Bundle BASS runtime libraries inside the app bundle once macOS packaging begins.

### Linux

- Keep dependencies Linux-friendly while coding.
- Prefer `rustls` TLS, not native TLS, unless a platform feature requires otherwise.
- Build on Linux in CI once Windows/macOS are stable.
- Decide AppImage/tarball/deb later.

### Android

- Slint can target Android with Rust, but this should be a later proof point.
- Keep Android Kotlin/Compose app alive for now.
- If desktop Rust core becomes valuable, consider sharing Rust logic with Android through FFI only after the core stabilizes.

## Implementation Phases

### Phase 0: Stabilize The Spike

- [x] Rename binary and package metadata from `naviamp-slint` to production naming.
- [x] Add `README.md` with prerequisites for Windows and macOS.
- [x] Remove throwaway state and move code out of `main.rs`.
- [x] Add a simple `cargo test` baseline.
- [x] Add `cargo fmt` and `cargo clippy` expectations.
- [x] Decide whether to commit or revert the Kotlin Compose ProGuard experiment.
- [x] Keep generated `target/` and `dist/` ignored.

Decision: leave the Kotlin Compose ProGuard experiment in the legacy Kotlin desktop path for now. Rust/Slint desktop work continues separately on this branch.

Exit criteria:

- `cargo build --release` works on Windows.
- The app can still connect, search, and play.
- The exe remains small enough to justify the route.

### Phase 1: Production Skeleton

- [x] Create `src/domain`.
- [x] Create `src/provider`.
- [x] Create `src/provider/navidrome`.
- [x] Create `src/storage`.
- [x] Create `src/playback`.
- [x] Create `src/app`.
- [x] Create `src/ui` or organize `.slint` files under `ui/`.
- [x] Replace global/thread-local track storage with explicit app state.
- [x] Replace blocking network calls with an async runtime or a controlled worker queue.
- [x] Add an app controller for UI callbacks.
- [x] Introduce a `PlaybackEngine` trait so BASS stays isolated and can be swapped for platform-specific engines if needed.

Exit criteria:

- `main.rs` only wires dependencies and starts the UI.
- Provider, playback, and UI can be tested independently.

### Phase 2: Navidrome Provider Parity V1

Port the Kotlin provider behavior in layers.

- [x] Auth/token/salt model.
- [x] Connection validation.
- [x] `search3` artists/albums/tracks.
- [x] `getAlbum`.
- [x] `getArtist`.
- [x] `getArtistInfo2`.
- [x] `getPlaylists`.
- [x] `getPlaylist`.
- [x] `getGenres`.
- [x] `getInternetRadioStations`.
- [x] `getRandomSongs`.
- [x] `getSimilarSongs` / `getSimilarSongs2`.
- [x] Stream URL generation for original quality.
- [x] Transcoded stream URL generation.
- [x] Favorite/rating mutation APIs.
- [x] Lyrics API.
- [x] Now-playing/play scrobble APIs.
- [x] Tests with checked JSON fixtures.

Exit criteria:

- Rust provider can satisfy the same major use cases as Kotlin `MediaProvider`.
- No UI code knows Subsonic/Navidrome response shapes.

### Phase 3: Settings And Source Storage

- [x] Define Rust `SavedMediaSource`.
- [x] Normalize base URLs.
- [x] Generate stable source IDs/cache namespaces.
- [x] Store token/salt instead of plaintext password after validation.
- [x] Support multiple saved connections.
- [x] Add edit/delete/connect flows.
- [x] Put settings persistence behind a `SettingsStore` trait.
- [x] Add TLS setting model, even if only default TLS works initially.
- [x] Add import/export-safe config layout.
- [x] Persist window size and last route.

Exit criteria:

- App starts into a saved source without needing password reentry.
- Settings can manage multiple sources.

### Phase 4: Playback Engine V1

- [x] Replace external `mpv` process playback with native `BassPlaybackEngine`.
- [x] Add BASS runtime resolution:
  - `NAVIAMP_BASS_DIR` env var
  - adjacent app folder
  - bundled app resources
  - ignored local dev vendor folder
  - project-local bundled runtime folder
- [x] Add BASS plugin loading for common modules:
  - FLAC
  - Opus
  - ALAC
  - APE
  - DSD
  - MPC
  - HLS
  - WebM
  - MIDI
  - mixer/fx
  - Windows Media on Windows
- [x] Add pause/resume.
- [x] Add seek.
- [x] Add stop.
- [x] Add volume.
- [ ] Fix BASS lifecycle bug where pressing Stop prevents starting a later track in the same app session.
- [x] Add playback snapshot API for BASS position/progress.
- [x] Add playback snapshot API for BASS duration.
- [x] Wire playback snapshot polling into app/UI state.
- [ ] Replace temporary playback status text with real scrubber/player progress UI.
- [ ] Add stream metadata polling for internet radio.
- [ ] Add error reporting that does not close the whole app.
- [x] Remove Windows named-pipe/child-process dependency from the Rust app.
- [x] Research BASS licensing enough to proceed for this free open-source app.
- [x] Add `BassPlaybackEngine` behind the existing `PlaybackEngine` trait.
- [x] Add a local BASS runtime prep script for Windows.
- [ ] Add a future visualizer backend abstraction; start with Slint/software rendering, then evaluate GPU-backed rendering only if needed.

Exit criteria:

- Search result playback supports play, pause, seek, next, previous, volume.
- Rapid skip testing does not crash playback on Windows.

### Phase 5: Queue Engine

Port Kotlin `PlaylistEngine` behavior in Rust.

- [ ] Queue model.
- [ ] Play from list/index.
- [ ] Next/previous.
- [ ] Jump to queue item.
- [ ] Repeat off/track/queue.
- [ ] Shuffle upcoming.
- [ ] Preserve back-to/up-next lists.
- [ ] Queue-aware session IDs to ignore stale async callbacks.
- [ ] Track-start callback.
- [ ] Playback-finished auto-advance.
- [ ] Progress merge behavior equivalent to `PlaybackProgress.mergeWith`.
- [ ] Unit tests for all queue transitions.

Exit criteria:

- Search results can become a real queue.
- Now-playing state survives rapid user actions.

### Phase 6: Core UI Shell

Build the Slint equivalent of the current app shell.

- [ ] App frame.
- [ ] Left/nav or bottom nav variant for desktop.
- [ ] Home route placeholder.
- [ ] Search route.
- [ ] Library route placeholder.
- [ ] Playlists route placeholder.
- [ ] Internet radio route placeholder.
- [ ] Downloads route placeholder.
- [ ] Settings route.
- [ ] Mini player.
- [ ] Full player route.
- [ ] Back/collapse behavior.
- [ ] Route state preservation.

Exit criteria:

- The app feels like Naviamp, not a test form.
- Navigation does not reset search/player state.

### Phase 7: Search Screen Parity

- [ ] Search query persistence.
- [ ] Group results into artists, albums, and tracks.
- [ ] Track rows start queue playback.
- [ ] Album rows open album detail.
- [ ] Artist rows open artist detail.
- [ ] Row overflow actions.
- [ ] Favorite/rating state display.
- [ ] Loading and empty states.
- [ ] Cached search responses.

Exit criteria:

- Search can replace the Kotlin desktop search workflow.

### Phase 8: Album And Artist Detail

- [ ] Album detail route.
- [ ] Album track list.
- [ ] Play album.
- [ ] Play selected track from album queue.
- [ ] Album radio action.
- [ ] Artist detail route.
- [ ] Artist albums list.
- [ ] Artist radio action.
- [ ] Preserve route back-stack source.

Exit criteria:

- Search to artist/album to playback works smoothly.

### Phase 9: Now Playing V1

- [ ] Album art.
- [ ] Track title.
- [ ] Artist and album links.
- [ ] Album year.
- [ ] Codec/bitrate line.
- [ ] Favorite toggle.
- [ ] Rating controls.
- [ ] Scrub bar.
- [ ] Volume control.
- [ ] Transport controls.
- [ ] Up Next list.
- [ ] Back To list.
- [ ] Related placeholder.
- [ ] Track details dialog.
- [ ] Current-track overflow menu.

Exit criteria:

- The player is usable as a daily-driver playback view.

### Phase 10: Images And Theming

- [ ] Cover art URL model.
- [ ] Image download/cache.
- [ ] Decode images for display.
- [ ] In-memory hot image cache.
- [ ] Persist image bytes in SQLite.
- [ ] Album-art color sampling.
- [ ] Shared palette model.
- [ ] Apply restrained player/background theming.
- [ ] Add visualizer/waveform rendering plan without assuming Chromium/Vulkan/SwiftShader dependencies.
- [ ] Evaluate GPU-backed rendering only after a Slint/software visualizer proves insufficient.

Exit criteria:

- Album art loads quickly after first view.
- UI has Naviamp’s visual personality without one-note color wash.

### Phase 11: Home V1

Port Home in slices:

- [ ] Recently added albums.
- [ ] Random albums.
- [ ] Frequent albums.
- [ ] Recent albums.
- [ ] Playlists section.
- [ ] Genre shortcuts.
- [ ] Decade/year spotlight from local index.
- [ ] Library radio.
- [ ] Random album radio.
- [ ] Home loading/cache behavior.

Exit criteria:

- Home is useful after connection restore.
- Expensive provider calls run in background.

### Phase 12: Library Index And Browse

- [ ] Library sync job.
- [ ] Index artists.
- [ ] Index paged albums.
- [ ] Index album tracks.
- [ ] Sync progress state.
- [ ] Local artist browse.
- [ ] Local album browse.
- [ ] Local track search.
- [ ] A-Z jump rail equivalent.
- [ ] Manual refresh.
- [ ] Clear local index.

Exit criteria:

- Library can browse/search locally after sync.

### Phase 13: Playlists

- [ ] Playlist list.
- [ ] Playlist detail.
- [ ] Play playlist.
- [ ] Add to playlist dialog.
- [ ] Create playlist.
- [ ] Rename playlist.
- [ ] Delete playlist.
- [ ] Add track from row/player menus.

Exit criteria:

- Playlist workflows reach Kotlin desktop parity.

### Phase 14: Internet Radio

- [ ] Station list.
- [ ] Play station.
- [ ] Current stream title metadata.
- [ ] Create station.
- [ ] Edit station.
- [ ] Delete station.
- [ ] Persist last station session.

Exit criteria:

- Radio can be used as a first-class playback source.

### Phase 15: Lyrics

- [ ] Lyrics domain models.
- [ ] LRC parser.
- [ ] Provider lyrics lookup.
- [ ] LRCLIB fallback.
- [ ] Lyrics cache.
- [ ] Current-track lyrics resolver.
- [ ] Synced lyrics display.
- [ ] Unsynced lyrics display.
- [ ] Click-to-seek.
- [ ] Seek/progress stale-read handling.

Exit criteria:

- Lyrics behavior matches current Kotlin player expectations.

### Phase 16: Audio Cache And Downloads

- [ ] Audio cache paths.
- [ ] Stream-to-file cache download.
- [ ] Cache lookup before provider stream.
- [ ] Prefetch upcoming tracks.
- [ ] Cache size budget.
- [ ] Cache eviction.
- [ ] Downloads table.
- [ ] Download album/playlist.
- [ ] Downloaded track playback.
- [ ] Remove downloads.
- [ ] Offline mode rules.

Exit criteria:

- Cached playback improves skip speed.
- Downloads are never evicted as normal cache.

### Phase 17: Radio Service

- [ ] Track radio.
- [ ] Album radio.
- [ ] Artist radio.
- [ ] Genre radio.
- [ ] Decade/year radio.
- [ ] Local fallback queues.
- [ ] Duplicate filtering.
- [ ] Auto-refill when up-next gets low.
- [ ] Tests for seed behavior.

Exit criteria:

- Radio behavior is centralized and testable.

### Phase 18: Playback History And Reporting

- [ ] Report now playing on track start.
- [ ] Submit scrobble at 50% or 4 minutes.
- [ ] One submitted play per session.
- [ ] Local playback history.
- [ ] Failed scrobble retry queue.
- [ ] Skip/completion signal.
- [ ] Home sections from local history.

Exit criteria:

- Navidrome now-playing/scrobble behavior matches Kotlin app.
- Home can personalize from local history.

### Phase 19: Platform Integration

Windows:

- [ ] Portable dist folder/script.
- [ ] Optional installer later.
- [ ] Bundled BASS runtime/plugin packaging.
- [ ] Media keys.
- [ ] Taskbar/app icon.
- [ ] File locations under `%APPDATA%`/`%LOCALAPPDATA%`.

macOS:

- [ ] `.app` bundle.
- [ ] App icon.
- [ ] Bundled BASS runtime/plugin packaging.
- [ ] Media keys / Now Playing integration.
- [ ] Config/cache paths under Application Support/Caches.
- [ ] Signing/notarization plan.

Linux:

- [ ] Tarball/AppImage decision.
- [ ] X11/Wayland smoke tests.
- [ ] Config/cache paths.

Exit criteria:

- Windows and macOS builds are testable from clean folders.

### Phase 20: Final Cutover

- [ ] Define feature parity checklist versus Kotlin desktop.
- [ ] Use Rust/Slint desktop as daily driver for one week.
- [ ] Fix crashers and rough navigation.
- [ ] Freeze Kotlin desktop feature work.
- [ ] Keep Android Kotlin app alive.
- [ ] Decide whether `apps/desktop` remains legacy or is removed.
- [ ] Update README and setup docs.
- [ ] Add CI for Windows/macOS release builds.

Exit criteria:

- Rust/Slint desktop is the recommended desktop app.
- Kotlin desktop is no longer needed for daily use.

## Feature Parity Checklist

Connection and settings:

- [ ] Multiple saved connections.
- [ ] Optional connection display name.
- [ ] Connection edit/delete.
- [ ] TLS options.
- [ ] mTLS options.
- [ ] Playback settings.
- [ ] Cache settings.
- [ ] Local data actions.
- [ ] Diagnostics.

Media browsing:

- [ ] Home.
- [ ] Search.
- [ ] Artist detail.
- [ ] Album detail.
- [ ] Library artists.
- [ ] Library albums.
- [ ] Library local search.
- [ ] Playlists.
- [ ] Internet radio.
- [ ] Downloads.

Playback:

- [x] BASS playback.
- [ ] Pause/resume.
- [ ] Seek.
- [ ] Volume.
- [ ] Previous behavior.
- [ ] Up-next behavior.
- [ ] Repeat.
- [ ] Shuffle upcoming.
- [ ] Queue jump.
- [ ] Session restore.
- [ ] ReplayGain.
- [ ] Gapless.
- [ ] Crossfade later.

Player UI:

- [ ] Mini player.
- [ ] Full now-playing.
- [ ] Album art depth.
- [ ] Scrub bar.
- [ ] Lyrics.
- [ ] Track details.
- [ ] Favorite/rating controls.
- [ ] Up Next.
- [ ] Related.
- [ ] Radio actions.
- [ ] Row overflow menus.

Storage/cache:

- [ ] SQLite app database.
- [ ] Image cache.
- [ ] Provider response cache.
- [ ] Library index.
- [ ] Audio cache.
- [ ] Downloads.
- [ ] Waveform cache.
- [ ] Lyrics cache.
- [ ] Playback history.

Provider:

- [ ] Navidrome validation.
- [ ] Search.
- [ ] Album/artist details.
- [ ] Playlists.
- [ ] Genres.
- [ ] Radio recommendations.
- [ ] Lyrics.
- [ ] Stream/transcode URLs.
- [ ] Favorite/rating mutations.
- [ ] Scrobble/now playing.

Packaging:

- [ ] Windows release exe.
- [ ] Windows portable dist.
- [ ] macOS release binary.
- [ ] macOS app bundle.
- [ ] Linux smoke build.
- [ ] License and attribution files.
- [ ] Slint GPL/open-source compliance.
- [ ] Third-party notices.

## Cross-Platform Rules For Every New Feature

Before implementing a feature, answer:

- Is this domain behavior? Put it in `domain`.
- Is this provider-specific? Put it behind the provider trait.
- Is this persistence? Put it behind storage repositories.
- Is this playback hardware/process behavior? Put it behind playback traits.
- Is this display-only? Put it in Slint UI with view models.
- Does it store a path? Use platform path adapters.
- Does it launch a process? Isolate by OS.
- Does it need network/TLS behavior? Keep provider API stable and isolate TLS setup.
- Could Android use the same model later? Keep the model toolkit-neutral.

## Near-Term Execution Order

Use this order for the next working sessions:

1. Clean up and commit the Slint prototype separately from the Kotlin ProGuard experiment.
2. Restructure `apps/desktop-slint/src` into `domain`, `provider/navidrome`, `playback`, `settings`, `app`, and `ui`.
3. Replace blocking callback logic with an app controller and worker/message flow.
4. Port provider models and Navidrome mapping for search, album detail, and artist detail.
5. Build a real queue engine and player controls.
6. Rebuild the Slint shell around Search + Now Playing.
7. Add persistent saved sources using SQLite.
8. Add image loading/cache so the UI can look like Naviamp.
9. Add album/artist detail routes.
10. Add Windows portable dist script and macOS build notes.

This keeps momentum anchored in the working proof: connect, search, play, then deepen that path until it becomes the real app.
