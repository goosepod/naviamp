# New Features Roadmap

Status: Living feature backlog. Completed feature branches are tracked below so the roadmap does not keep re-listing shipped work as active work.

Keep implementation cross-platform by default: shared domain models, shared UI, and platform adapters only where OS/audio/provider access requires it. Start each feature on its own branch, let it be tested, then merge before starting the next feature.

## Active Backlog

### Product-Defining Bets

- [ ] Add `Naviamp Connect` style remote renderer support.
  - Phone/desktop acts as controller; Android TV / Google TV / Fire TV acts as renderer.
  - MVP target is Android TV / Google TV / Fire TV, not Roku.
  - See `docs/naviamp-connect-roadmap.md`.
- [x] Add additional Plexamp-style station entries when the current Mix Builders and DJ flow need more depth.
  - Artist Mix Builder, Album Mix Builder, Genre Mix Radio, and DJ presets are the current first pass.

### Focused Polish

- [x] Add Now Playing display customization.
  - Let users independently show or hide the album year, bitrate/audio information, and volume bar.
  - Add independent toggles for scrolling long track titles, artist names, and album names.
  - Keep the existing long-track-title scrolling behavior, and add matching scrolling behavior for long artist and album text when their toggles are enabled.
  - Avoid scrolling short text, and keep each disabled field static with graceful truncation when it does not fit.
  - Keep sensible defaults and preserve responsive layouts across small desktop windows, larger desktop players, and Android.
  - Store the choices as app preferences and include portable display choices in settings sync where appropriate.
- [x] Add optional automatic playback resume at startup.
  - Add a setting that starts playing the last active song when Naviamp launches.
  - Restore the saved queue, current track, and playback position before starting playback.
  - Keep automatic playback opt-in so launching the app does not unexpectedly produce audio by default.
- [ ] Add interface language selection.
  - Default behavior should follow the system language.
  - Users should be able to choose a specific app interface language from Settings.
  - Persist the selected language as an app preference and make it eligible for settings sync once translation support exists.
  - Keep provider metadata, artist names, album names, track titles, and user-authored playlist names in their original language.
- [x] Add first-pass update checking.
  - Check GitHub Releases at startup and every 24 hours, with a persisted Experience toggle that defaults on.
  - Compare the latest stable release tag with the running app version and show a modal linking to the specific release.
  - Keep network failures silent and leave download and installation choices to the user.
  - Future updater work can show explicit status for up-to-date, unable to check, downloading, and ready to install.
  - Start with download/open-installer behavior for native installers before attempting silent or in-place updates.
  - Keep platform differences explicit: macOS DMG, Windows MSI/EXE, and Linux DEB/RPM/AppImage-style archive handling are different update paths.
  - Require signed/notarized desktop artifacts before promoting automatic background installs as a normal user-facing behavior.
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

### Settings Sync And Connectivity

- [x] Add cross-device app state sync.
  - Added a portable `naviamp-settings.json` sync document for logical server profiles and supported app preferences.
  - First-run setup can import an existing settings-sync folder and save it as the default sync location.
  - Desktop reads and writes the shared settings file in a real filesystem sync folder.
  - Android keeps an app-private local mirror as the on-device source of truth, then treats the selected Storage Access Framework folder as best-effort provider transport.
  - Android provider handling checks create/write capabilities where available, keeps virtual/provider-backed files eligible for stream access, and reports clear provider failures for unavailable streams, invalid JSON, missing write/create support, and revoked or unavailable provider access.
  - Auto-sync coverage includes playback settings, visualizer selection, recent radio streams, recent internet radio stations, DJ presets, and saved server profile changes, including Android foreground-service recent-radio writes.
  - Imported server profiles are persisted without secrets; the first imported profile opens the connection form so the user can enter the local Navidrome password before connecting.
  - Secrets and device-local state remain local: passwords, tokens, native auth tokens, secret custom header values, certificate passwords, cache/download directories, downloaded-file records, local indexes, pending offline actions, diagnostics, and platform-specific layout/window state.
- [x] Add secondary server URL support for saved server profiles.
  - Saved Navidrome profiles can store a primary URL plus fallback secondary URLs.
  - Naviamp validates the primary URL first, then configured fallback URLs; connection status reports the active URL when a fallback is used.
  - Saved profiles support non-secret custom HTTP headers for reverse proxies, auth gateways, VPN/proxy routing, and other self-hosted network setups.
  - Non-secret custom header values are included in settings sync; secret header values stay local.
  - Credentials, TLS settings, cached source identity, and offline sync remain tied to the same logical server profile instead of duplicated profiles.

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
- [x] Add audio output device selection.
  - Desktop builds expose `Follow System Output` plus per-device routing.
  - macOS enumerates playback devices and lets users pin a specific output while keeping the default system-output option available.
  - Android keeps the setting hidden and continues to follow system output.
  - The preference remains device-local because output device IDs are OS-local.

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
