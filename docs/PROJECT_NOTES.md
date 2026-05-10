# Project Notes

This file is the durable handoff for future chats. Start here when opening a new issue-focused conversation.

## How To Use This With Codex

For a new chat, say something like:

```text
We are working in the Naviamp repo. Please read docs/PROJECT_NOTES.md, CONTRIBUTING.md, and recent git history first. Today's issue is: <short issue>.
```

Keep each chat focused on one issue or feature. Commit when the issue is working. Update this file when a decision, quirk, or roadmap item should survive into future chats.

## Project Shape

Naviamp is a Kotlin Multiplatform / Compose Multiplatform music client inspired by Plexamp, currently focused on desktop and Navidrome.

Current priorities:

- Windows desktop works and is the main live test path right now.
- Navidrome is the first provider, but the app should stay provider-oriented.
- Playback uses mpv on desktop when available.
- The app should remember state across screens where it feels natural: search query/results, navigation, session queue, window size, and similar context.

Main source areas:

- `core/domain`: provider contracts and provider-neutral domain models.
- `providers/navidrome`: Navidrome/Subsonic API implementation and mapping.
- `apps/desktop`: Compose desktop UI, settings, playback engine integration, and desktop tests.

Useful docs:

- `docs/architecture.md`
- `docs/desktop-playback.md`
- `docs/roadmap.md`
- `docs/setup.md`

## Current Build Notes

- Use the Gradle wrapper, not a system Gradle install.
- On Windows, set `JAVA_HOME` to the installed JDK before running Gradle if needed.
- The project Kotlin version is aligned to `2.3.0` because resolved dependencies already pull `kotlin-stdlib:2.3.0`.
- The old VS Code `fwcd.kotlin` language server may report false Kotlin metadata errors. This workspace disables that language server locally in `.vscode/settings.json`, which is gitignored.

Common check command on this Windows machine:

```powershell
$env:JAVA_HOME=[Environment]::GetEnvironmentVariable('JAVA_HOME','Machine')
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat check
```

## Recently Landed

- Windows mpv playback controls: play, pause, seek, progress polling, and executable resolution.
- Desktop player visual polish: larger floating album art, denser typography, codec/bitrate line, heart/star placeholders, centered scrub times.
- Scrollable player queue: `UP NEXT` can show the full upcoming queue, and the player screen scrolls vertically in short windows.
  - Up-next rows are clickable and jump playback directly to the selected queue item.
- Search screen:
  - Freeform Navidrome `search3` integration.
  - Results grouped into artists, albums, and tracks.
  - Clicking a track starts playback from the search result queue.
  - Search query is persisted and restored.
- Scrub bar stability:
  - Protects against transient unknown progress reads.
  - Resets progress when a new track starts so the next track does not inherit the old position.
- Navigation memory:
  - The player down arrow returns to the last non-player screen instead of always going Home.
  - Album detail tracks the route that opened it, so Back returns to Search when opened from search results.
- Track favorite/rating metadata:
  - Navidrome `starred` and `userRating` fields map into provider-neutral track metadata.
  - The player can toggle track favorites and set/clear 1-5 ratings through Navidrome.
  - Search, album detail, and queue state are patched locally after successful changes.
- Reusable desktop media rows:
  - Shared `MediaRow`, `AlbumRow`, `ArtistRow`, and `TrackRow` components back search, home, album detail, and up-next rows.
- Artist detail:
  - Search artist results open an artist page.
  - Navidrome `getArtist` supplies the artist album list; `getArtistInfo2` may supply biography and artist image URLs.
  - Artist pages currently show returned albums together; release-type grouping can come later if provider metadata supports it.
- Player metadata navigation:
  - Navidrome song `artistId` and `albumId` map onto tracks and are persisted in the saved playback session.
  - The player artist and album text open their detail pages when those IDs are available.
- Album release years:
  - Navidrome album/song `year` maps onto albums and tracks.
  - Album rows, album detail, and the player album line show release years when available.
- SQLite-backed caching:
  - Desktop uses SQLDelight with SQLite for cover art/artist image bytes and common provider responses.
  - Cached provider responses currently include recently added albums, album details, artist details, and search results.
  - A small bounded in-memory hot-image cache sits above SQLite for decoded/recent image bytes.
  - Cache keys include provider namespace details so different servers/users do not mix cached data.
  - Connected media sources are now represented in SQLite so future library rows can reference a stable source/server/user.
  - The schema includes a slim source-scoped artist/album/track library index intended for local browse/search. Store provider IDs and display/search metadata here, not full authenticated stream URLs.
  - Navidrome library import starts in the background after connection/restore. It indexes artists, paged alphabetical albums, then album tracks by crawling album details. Settings also has a manual Refresh library action.
  - Restored startup uses the local index instead of reimporting when a source already has indexed rows and the last completed sync is recent. Manual Refresh library still forces a sync.
  - Library import runs on `Dispatchers.IO`; UI status updates hop back to the Compose coroutine context instead of `Dispatchers.Main`, because the desktop app does not install a coroutines Main dispatcher.
  - The Library tab reads from the local SQLite index and can locally search indexed artists/albums/tracks while sync is running.
  - Library browsing currently exposes Artists and Albums tabs only. Track rows remain indexed for search/supporting flows but are not a top-level Library tab.
  - Library lists use `LazyColumn`, load more rows as the list nears the bottom, and include an independently scrollable right-side `#`/`A-Z` jump rail for artists/albums.
  - Settings exposes separate local-data actions: clear image/API cache, clear local artist/album/track index, and a guarded full database reset that removes saved servers too.
  - Future Android/iOS work should reuse SQLDelight with platform drivers rather than introducing a separate storage stack.
- Settings easter egg:
  - Triple-click the Settings connection-status line to open a separate "Stats for nerds" window.
  - It shows app/runtime details, connection/provider info, saved media source details, library import status, DB/cache counts, playback capabilities, queue state, stream metadata for the current track, and a redacted recent Navidrome API call history.
- Mini player behavior:
  - Tapping the mini-player row opens the full player, but its transport buttons should only control playback and should not navigate away from the current screen.
- Kotlin/IDE housekeeping:
  - Kotlin version catalog bumped to `2.3.0`.
  - Generated `apps/desktop/bin/` output is ignored.

## Important Current Behavior

- `PlaybackProgress.mergeWith(previous)` intentionally preserves known position/duration through brief unknown reads from mpv and ignores large backward jumps that are likely polling glitches.
- `playlistCallbacks.onTrackStarted` resets `playbackProgress` to `PlaybackProgress.Unknown` before entering the player.
- Search state lives in `Main.kt` and query persistence lives in `DesktopSettingsStore`.
- Album detail navigation currently falls back Home in some paths; future screen-state work should preserve deeper route state more deliberately.
- Track heart/star mutations currently update Navidrome from the player screen; shared row controls can broaden this later.
- Library page scroll/search state is still mostly in-memory in `Main.kt`; future route-state work should preserve the selected Library tab, search query, and scroll/jump position if needed.

## Roadmap Items From The User

Top-of-mind work the user wants:

- Add crossfading to the player.
- Modularize reusable UI pieces such as track cards, artist cards, album cards, and similar provider-neutral components.
- Broaden starring and favoriting controls beyond the player, including reusable row-level controls.
- Continue refining the scrub bar.
- Add a music visualization on the player screen, activated by clicking album art.
- Add lyrics support.
- Improve the upcoming queue further as needed.
- Continue refining Library browsing, including genres and richer artist/album grouping.
- Add quick radio playback from Home, seeded by genres, decades, and artists.
- Add Settings controls to delete image/data cache or reset the local database so the app starts a fresh server scan.

## Design Preferences

- Compact, playback-focused, Plexamp-inspired.
- Dense UI is preferred over roomy whitespace.
- Keep text readable; avoid muted grey for important secondary metadata.
- Keep provider-specific logic behind provider modules.
- Prefer small, focused changes and tests where behavior is nontrivial.
- Future UI should preserve screen state and scroll/search position when possible.

## Suggested Next Issues

Good next slices:

- Wire Navidrome starred/favorite fields into domain models and player/search/album rows.
- Extract reusable `TrackRow`, `AlbumCard`, and `ArtistRow` components from existing screens.
- Add Library genres and richer artist/album grouping.
- Add lyrics domain model and Navidrome/provider capability shape before building the UI.
- Investigate mpv crossfade options or a two-player strategy for desktop crossfade.
