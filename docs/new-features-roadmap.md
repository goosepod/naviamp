# New Features Roadmap

Status: Living feature backlog. The original `new-features` branch work has mostly landed; unchecked items below are follow-up candidates and should be split into focused branches before implementation.

Keep implementation cross-platform by default: shared domain models, shared UI, and platform adapters only where OS/audio/provider access requires it.

## Prioritized Feature Candidates

These are the current high-value follow-up ideas to consider after Android Auto 1.0 validation settles. Split each into a focused branch before implementation.

### Practical Next Branches

- [x] Add Offline Mode dashboard.
  - Show downloaded albums/playlists/tracks, cache health, available-offline status, pending offline actions, failed syncs, and a clear "ready for car/offline" signal.
  - Tie this to Android Auto/offline validation so cached-drive behavior is visible instead of only diagnostic-log based.
  - First implementation upgrades the Downloads view into an Offline Mode dashboard across desktop and Android/shared UI, showing offline readiness, download storage use, playback cache health, and the current limitation that pending/failed sync actions are not yet persisted.
- [x] Add smart playlist templates.
  - Candidate templates: Recently Played, Never Played, High Rated, Favorite Albums, Recently Added but Unplayed, and Long-Unheard Favorites.
  - Reuse the existing playlist creation/update flow where possible.
  - First implementation adds the templates to the shared smart playlist builder and includes Navidrome 0.62 smart playlist field/operator updates.
- [x] Add generated-mix playlist save.
  - Cover Sonic Path, Sonic Mix, generated radio queues, and other discovery outputs.
  - Treat this as the durable version of discovery: play now first, then save the good result.
  - First implementation covers Sonic Path and Sonic Mix using the shared playlist creation flow.

### Product-Defining Bets

- [ ] Add radio tuning controls inspired by Plexamp DJ-style controls.
  - Candidate controls: familiar/discovery, narrow/broad, same decade/any decade, favorites bias, deep cuts, similar artists, and genre spread.
  - Persist selected tuning options and pass them into radio/mix generation.
  - Discuss the exact tuning model before implementation; this is likely large enough for its own planning branch.
- [ ] Add `Naviamp Connect` style remote renderer support.
  - Phone/desktop acts as controller; Android TV / Google TV / Fire TV acts as renderer.
  - See `docs/naviamp-connect-roadmap.md`.
- [ ] Add cross-device app state sync.
  - Candidate state: recent radio, recent generated mixes, settings, and possible playback handoff.
  - First investigate whether Navidrome can store app state; if not, evaluate app-managed sync keyed by server/user plus export/import as a fallback.

### Focused Polish

- [ ] Improve artist-page local-library confidence.
  - Make source context clearer when provider metadata and local library matches are combined.
  - Consider showing local library albums, provider bio, similar artists, popular tracks, and a small "matched from your library" signal for ambiguous artist names.
- [x] Add queue rules as settings.
  - Candidate rules: shuffle upcoming only, remove played tracks, keep radio queue filled, start radio when queue ends.
  - First implementation adds a Queue rules section with persisted remove-played-tracks behavior and moves Sonic autoplay into the queue rules area.
- [ ] Add a replay gain inspector toggle.
  - Show track/album gain, peak, selected mode, and active applied gain when available.

## Lyrics

- [x] Add per-track synced lyrics timing offset.
  - Default offset is `0.0s`.
  - Lyrics screen includes controls to decrease/increase offset in `0.1s` increments.
  - Offset is saved per track and applied when highlighting synced lyric lines.
- [x] Rename the LRCLIB-specific lyrics setting to generic copy such as `Download lyrics for tracks`.

## Mix Builders and Radio

- [x] Add Artist Mix Builder.
  - Shared cross-platform screen.
  - Search bar at the top.
  - Random artist grid with artist image and name.
  - Selecting an artist moves it to a selected row under the search bar.
  - Once selected, refresh suggestions with similar artists plus a few random artists at the end.
  - Only artists that are present in the user's library are shown as selectable artists.
  - Show a bottom `Play Mix` action when at least one artist is selected.
  - Selected artists preload matched popular songs in the background and use those songs as the front of the generated mix queue.
  - Home includes a dedicated `Mix Builders` category for Artist Mix, Album Mix, and Genre Mix entry points.
- [x] Add Album Mix Builder.
  - Same interaction model as Artist Mix Builder, but with albums and album art.
  - Random album grid starts the builder.
  - Selecting an album moves it to the selected row, preloads a few tracks from the album, and refreshes suggestions with albums from similar library artists plus a few random albums.
  - Play Mix starts with the preloaded selected-album tracks in a mixed random order, then continues with album radio.
- [x] Add Genre Mix Radio.
  - Shared cross-platform screen below Artist Mix Builder and Album Mix Builder.
  - Select genres into a selected row.
  - Remove selected genres from the main list.
  - List is a scrollable list of all genres supplied by source.
  - Use genre search if the provider supports it.
  - Show `Play Mix` once at least one genre is selected.
- [ ] Add radio tuning controls inspired by Plexamp DJ-style controls.
  - Candidate controls: familiar/discovery, narrow/broad, same decade/any decade, similar artists/genre spread.
  - Persist selected tuning options and pass them into radio generation.
  - Discuss the exact tuning knobs before implementation; this is likely large enough to split into its own branch.
- [ ] Leave a note that additional Plexamp-style station entries may be added later; Artist Mix Builder, Album Mix Builder, and Genre Mix Radio are first.

## Playback and Queue

- [x] Add a sleep timer.
  - Stop after a duration, current track, current album, or queue end.
  - Shared UI and shared timer state, with platform playback adapters.
- [ ] Add `Naviamp Connect` style remote renderer support.
  - Long-term feature track.
  - Phone app acts as controller; TV app acts as remote playback renderer.
  - MVP target is Android TV / Google TV / Fire TV, not Roku.
  - See `docs/naviamp-connect-roadmap.md`.
- [x] Add queue rules as settings.
  - Candidate rules: shuffle upcoming only, remove played tracks, keep radio queue filled, start radio when queue ends.
  - First implementation covers remove-played-tracks cleanup plus Sonic autoplay as a queue-end rule.
- [x] Add `Save queue as playlist` to the now-playing hamburger menu.
  - Open shared playlist-name modal.
  - Save the current active queue in order as a provider playlist, including songs in the Back To list.

## Equalizer and Audio Inspector

- [x] Add a 10-band equalizer.
  - Include common presets: Flat, Bass Boost, Treble Boost, Rock, Pop, Jazz, Classical, Dance/Electronic, Hip Hop, Vocal, Acoustic.
  - Apply through the shared BASS/audio backend where possible.
  - EQ is global for this first version.
  - Future idea: saved EQ profiles that can be applied automatically by genre.
- [ ] Add a replay gain inspector toggle.
  - Simple setting to show replay gain details somewhere on the now-playing screen.
  - Show track/album gain, peak, selected mode, and active applied gain when available.
  - Maybe only show this info if the user has already turned on ReplayGain for Album or Track?

## Offline and Sync

- [x] Add Offline Mode dashboard.
  - Dedicated view for downloaded albums/playlists/tracks.
  - Show cache health and available-offline status.
  - First implementation uses the existing Downloads route and adds offline readiness plus cache-health summary cards.
- [ ] Add cross-platform settings and recent radio sync.
  - Prioritize recent generated radio streams and recent internet radio stations.
  - Investigate whether Navidrome can store app state.
  - Prefer a Navidrome-backed storage mechanism if one exists.
  - Assumption to verify: Navidrome probably does not expose a clean app-state storage API.
  - If not, evaluate app-managed sync keyed by server/user, with export/import as fallback.
- [ ] Hold per-source profiles until additional providers like Jellyfin or Plex are active.
- [ ] When Offline Mode is enabled, all searches are limited to downloaded tracks.

## Favorites and Metadata

- [x] Add favorite/unfavorite support for artists and albums.
  - Use Navidrome/Subsonic provider support where available.
  - Surface consistently in lists, search, home, and detail screens.
- [x] Expand track details with provider metadata when available.
  - BPM, mood, rating, play count, and last played.
  - Hide unavailable fields.
- [x] Add `Track details` to every shared track-row overflow menu.

## Artist Details

- [x] Make the similar artists button a toggle.
  - Press once to show/load similar artists.
  - Press again to hide them.
- [x] Remove provider-specific external artist copy.
  - Replace `View on Deezer` style text with generic `View in browser`.

## Connection

- [x] Simplify the connection form.
  - Initially show only URL, username, and password.
  - Hide TLS/certificate/mTLS fields behind `Show Advanced`.
  - Toggle to `Hide Advanced` when expanded.

## Listening History

- [ ] Add listening history view if Navidrome exposes enough data.
  - Investigate provider support first.
  - If Navidrome does not expose usable history data, leave this item as blocked until there is a provider-backed source.
  - Decide placement after data availability is known.
  - Candidate placement: Library tab, Home section, or dedicated History route.

## Playlists

- [x] Add smart playlist templates.
  - Candidate templates: Recently Played, Never Played, High Rated, Favorite Albums, Recently Added but Unplayed, and Long-Unheard Favorites.
  - Shared builder can load each template into an editable draft before save.
- [ ] Add bulk playlist tools.
  - Deduplicate playlist.
  - Remove unavailable tracks.
  - Sort by album, artist, date, or title.
  - Copy playlist.
  - Merge playlists.

## Android Auto

- [ ] Polish Android Auto browse categories.
  - Surface recent generated radio.
  - Surface saved mixes.
  - Surface downloaded music.
  - Surface smart playlists and templates.
