# Android Auto Polish Checklist

Branch: `codex/android-auto-polish-dhu`

Status: active DHU polish pass based on the 2026-06-17 Mac DHU session and the Symfonium reference screenshots in `/Users/jbmcmichael/Screenshots`.

## Observed Issues

- [x] Queue opened from Now Playing should start at the current track, or be ordered with the current track first, instead of always showing the start of the queue.
- [ ] Queue browsing should not snap back to the top while the user scrolls.
  - 2026-06-17: reduced redundant media-session progress updates while paused/unchanged; retest in DHU.
  - 2026-06-17 DHU retest: queue artwork now appears, but the queue screen still cannot be scrolled.
- [x] Queue rows need album art.
  - 2026-06-17: queue rows now provide cached bitmap artwork when Naviamp has already cached the cover.
  - 2026-06-17 DHU retest: confirmed artwork appears in the queue.
- [x] Album art should appear consistently throughout Android Auto browse results where the data is available.
  - 2026-06-17: browse rows now provide cached bitmap artwork in addition to URL fallback; authenticated Navidrome URLs still need retesting in DHU.
- [x] Internet radio stations need artwork in Android Auto browse and recent-radio rows.
- [x] Now Playing overflow "Start song radio" should use the same Radio icon treatment as the rest of the app.
- [x] Now Playing overflow "Start song radio" should leave the current track playing and rebuild the queue from the current track as the seed.
- [x] Artist search results should open playable artist tracks for artists such as Blues Traveler and No Doubt.
- [x] Artist search results should show artist thumbnail images where the server exposes artwork.
- [ ] Now Playing should expose a queue control in Android Auto when a queue is active.
- [x] Now Playing radio icon visually matches the rest of the app.
- [ ] Now Playing scrub bar should show movement and allow seeking on the first played track, not only subsequent tracks.
  - 2026-06-17: first-track duration fallback added; latest pass also republishes media-session metadata after artwork loads.
- [ ] Playlist detail pages should show playable tracks and must not interrupt playback.
  - 2026-06-17 DHU retest: opening a playlist still showed no items; playback stopped while the screen appeared to refresh multiple times.

## Symfonium-Inspired Screen Direction

- [ ] Explore top-level Auto tabs or equivalent sections for Home, Recent, Library, and Favorites.
- [ ] Home should prioritize compact launch rows such as Album mix, Rediscover albums, Random artists, Random albums, Recently added albums, Playlists, and Track mix.
- [ ] Recent should consider an album-art grid with quick alphabet navigation when Android Auto templates allow it.
- [ ] Library should expose Album artists, Albums, Playlists, Genres, All artists, and Internet radios.
- [ ] Favorites should expose Favorite playlists, Favorite artists, Favorite albums, Favorite tracks, and Favorite internet radios.
- [ ] Keep the design dense, glanceable, and Android Auto appropriate; avoid adding phone-app-only controls.

## Validation Checklist

- [x] Build Android debug successfully.
- [x] Install on the USB-debug phone used for DHU testing.
- [ ] Relaunch DHU and verify queue ordering from Now Playing.
- [ ] Verify queue/artwork rows in Home, Library, Recent, Radio, and search.
- [ ] Verify internet radio station art and fallback icons.
- [ ] Verify "Start song radio" updates the queue without interrupting the current track.
- [ ] Verify artist search can play artists with local albums.
