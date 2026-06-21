# New Features Roadmap

Status: Living feature backlog. Completed feature branches are tracked below so the roadmap does not keep re-listing shipped work as active work.

Keep implementation cross-platform by default: shared domain models, shared UI, and platform adapters only where OS/audio/provider access requires it. Start each feature on its own branch, let it be tested, then merge before starting the next feature.

## Active Backlog

### Product-Defining Bets

- [ ] Add `Naviamp Connect` style remote renderer support.
  - Phone/desktop acts as controller; Android TV / Google TV / Fire TV acts as renderer.
  - MVP target is Android TV / Google TV / Fire TV, not Roku.
  - See `docs/naviamp-connect-roadmap.md`.
- [ ] Add cross-device app state sync.
  - First-run setup should ask whether the user wants to set up a new server directly or choose a shared settings-sync folder.
  - When a shared settings file is imported during first-run setup, treat that folder as the default sync location for future settings writes.
  - Importing a synced server profile should still ask for the Navidrome password on the new device, then store secrets locally.
  - Keep the shared file as a portable sync document, not the only runtime source of truth; import into local platform storage and export changes back to the selected sync folder.
  - First implementation in progress: shared sync document/planner/mapping plus desktop folder selection with manual import/export.
  - Candidate synced state: logical server profiles, secondary URLs, non-secret custom header definitions, playback behavior, replay gain mode, gapless/crossfade, queue behavior, streaming/download quality preferences, lyrics options, visualizer selection, radio tuning and DJ presets, smart playlist editor drafts/templates if app-owned, and cross-platform UI preferences that are not window-size-specific.
  - Do not sync secrets by default: passwords, tokens, native auth tokens, custom header values that are secret, and client certificate passwords should stay in OS/platform-local secret storage unless an explicit encrypted-secrets feature is designed later.
  - Keep device-local state local: cache/download directories, downloaded file records, cache sizes unless explicitly opted in, local library indexes, pending offline provider actions, diagnostics/dev flags, and platform-specific layout/window state.
  - Prefer Navidrome for state it can own cleanly, such as playlists, smart playlists, favorites, ratings, and play/scrobble history; use the shared file for Naviamp-specific state Navidrome does not know about.
- [x] Add additional Plexamp-style station entries when the current Mix Builders and DJ flow need more depth.
  - Artist Mix Builder, Album Mix Builder, Genre Mix Radio, and DJ presets are the current first pass.

### Focused Polish

- [ ] Add desktop update checking.
  - Desktop app can manually check whether a newer Naviamp release is available.
  - Keep the first pass simple: clear status for up-to-date, update available, and unable to check.
  - Prefer a source that works with the eventual Forgejo release/tag flow.
- [ ] Add secondary server URL support for saved server profiles.
  - Let each saved Navidrome/server config store a primary URL plus an optional secondary URL.
  - Use case: connect directly to a local LAN URL at home, but fall back to a VPN, Tailscale, or external URL when away.
  - Allow optional custom HTTP headers per connection for reverse proxies, auth gateways, VPN/proxy routing, or other self-hosted network setups.
  - Keep credentials, TLS settings, cached source identity, and offline sync tied to the same logical server profile, not duplicated profiles.
  - Prefer automatic reachability/fallback when safe, with clear status text showing which URL is active.
- [ ] Add desktop installer options.
  - Keep the standard bundled-runtime installer as the reliable default.
  - Add a clearly named thin smart installer that can use a compatible installed Java runtime when available.
  - Windows installers should let users choose whether to add Naviamp to the Start Menu.
- [ ] Improve desktop visualizer performance.
  - Profile the current desktop visualizer pipeline before changing rendering behavior.
  - Target smooth 60 fps motion where hardware/display support it, with diagnostics for frame time and dropped frames.
  - Preserve existing visualizer styles while reducing choppy 5-10 fps behavior.
- [x] Add Forgejo merge-to-main release builds.
  - Forgejo Actions builds run on pushes to `main` and manual dispatch.
  - Android produces unsigned APK/AAB release artifacts.
  - Windows produces a standalone zip plus MSI/EXE installers.
  - macOS produces a standalone `.app` zip plus DMG installer.
  - See `docs/release-builds.md`; signing and notarization remain follow-up distribution work.
- [x] Improve artist-page local-library confidence.
  - Make source context clearer when provider metadata and local library matches are combined.
  - Consider showing local library albums, provider bio, similar artists, popular tracks, and a small "matched from your library" signal for ambiguous artist names.
- [x] Add a replay gain inspector toggle.
  - Show track/album gain, peak, selected mode, and active applied gain when available.
  - Maybe only show this info if ReplayGain is set to Album or Track.
- [x] When Offline Mode is enabled, limit searches to downloaded tracks.
- [x] Hold per-source profiles until additional providers like Jellyfin or Plex are active.

### Android Auto

- [x] Polish Android Auto browse categories.
  - Surface recent generated radio.
  - Surface saved mixes/DJs if useful in car mode.
  - Surface downloaded music.
  - Surface smart playlists and templates.

## Completed

### Discovery, Radio, And Mixes

- [x] Add radio tuning controls inspired by Plexamp DJ-style controls.
  - First pass added persisted familiarity, artist spread, and same-decade tuning across desktop and Android radio entry points.
  - Final direction folded standalone Radio Tuning into DJ Builder.
  - Saved DJs are stored in SQLite with migration `8.sqm`.
  - DJ presets support mixed artists, single-artist mode, and configurable same-artist/other-artist blocks.
  - Selecting a DJ from Now Playing rebuilds the upcoming radio queue without interrupting the current track.
  - `Default radio` and clicking the active DJ both clear the active preset.
- [x] Add generated-mix playlist save.
  - First implementation covers Sonic Path and Sonic Mix using the shared playlist creation flow.
- [x] Add Artist Mix Builder.
  - Shared cross-platform screen with search, random artist suggestions, selected row, similar-artist refresh, and library-only selectable artists.
  - Selected artists preload matched popular songs and use those songs as the front of the generated mix queue.
  - Home includes a dedicated `Mix Builders` category for Artist Mix, Album Mix, and Genre Mix entry points.
- [x] Add Album Mix Builder.
  - Same interaction model as Artist Mix Builder, but with albums and album art.
  - Play Mix starts with preloaded selected-album tracks in mixed random order, then continues with album radio.
- [x] Add Genre Mix Radio.
  - Shared cross-platform screen with selectable genres, selected row, removal, provider genre search when available, and `Play Mix`.

### Playlists

- [x] Add smart playlist templates.
  - Candidate templates include Recently Played, Never Played, High Rated, Favorite Albums, Recently Added but Unplayed, and Long-Unheard Favorites.
  - Shared builder can load each template into an editable draft before save.
  - Includes Navidrome 0.62 smart playlist field/operator updates.
- [x] Add bulk playlist tools.
  - Playlist detail pages expose bulk tools for copying a playlist, copying a deduplicated playlist, and creating a new playlist from the current playlist.
  - Existing add-to-playlist flows continue to cover merging playlist tracks into another playlist.
  - Destructive in-place tools remain deferred until the provider abstraction supports playlist item removal and reordering.
- [x] Add `Save queue as playlist` to the now-playing hamburger menu.
  - Opens the shared playlist-name modal.
  - Saves the current active queue in order as a provider playlist, including songs in the Back To list.

### Playback And Audio

- [x] Add queue rules as settings.
  - First implementation adds persisted remove-played-tracks behavior and moves Sonic autoplay into the queue rules area.
- [x] Add a sleep timer.
  - Stop after a duration, current track, current album, or queue end.
  - Shared UI and shared timer state, with platform playback adapters.
- [x] Add a 10-band equalizer.
  - Includes common presets and applies through the shared BASS/audio backend where possible.
  - EQ is global for this first version.

### Offline And History

- [x] Add Offline Mode dashboard.
  - Existing Downloads route now shows offline readiness, download storage use, playback cache health, and the current limitation that pending/failed sync actions are not yet persisted.
- [x] Add listening history view if Navidrome exposes enough data.
  - First pass adds a Home `Recently Played` track section using server-provided `lastPlayed` and `playCount` metadata from the local library index.
  - This is a summarized track-level history, not a complete scrobble-event log.
  - A full cross-client event history remains a future enhancement if Navidrome exposes native scrobble history through an API or a companion plugin/service becomes worthwhile.

### Lyrics

- [x] Add per-track synced lyrics timing offset.
  - Default offset is `0.0s`.
  - Lyrics screen includes controls to decrease/increase offset in `0.1s` increments.
  - Offset is saved per track and applied when highlighting synced lyric lines.
- [x] Rename the LRCLIB-specific lyrics setting to generic copy such as `Download lyrics for tracks`.

### Metadata, Artists, And Connection

- [x] Add favorite/unfavorite support for artists and albums.
  - Use Navidrome/Subsonic provider support where available.
  - Surface consistently in lists, search, home, and detail screens.
- [x] Expand track details with provider metadata when available.
  - BPM, mood, rating, play count, and last played.
  - Hide unavailable fields.
- [x] Add `Track details` to every shared track-row overflow menu.
- [x] Make the similar artists button a toggle.
  - Press once to show/load similar artists.
  - Press again to hide them.
- [x] Remove provider-specific external artist copy.
  - Replace `View on Deezer` style text with generic `View in browser`.
- [x] Simplify the connection form.
  - Initially show only URL, username, and password.
  - Hide TLS/certificate/mTLS fields behind `Show Advanced`.
  - Toggle to `Hide Advanced` when expanded.
