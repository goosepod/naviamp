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
- Audio/track caching is now a priority because it will matter for fast desktop skips, network handoff, and the future Android app.
- Offline downloads are separate from cache files. They can reuse cache/download plumbing, but user-selected downloads should live in their own storage area and should not be evicted by normal cache cleanup.
- The app should remember state across screens where it feels natural: search query/results, navigation, session queue, window size, and similar context.

UI convention:

- Prefer recognizable icons instead of text labels for compact actions where the meaning is standard, such as edit, delete, back, menu, playback, and navigation controls. Keep accessible content descriptions on icon-only controls.

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
- Player layout/actions:
  - Full player metadata order is album art, waveform/scrub bar, track title, artist, album/year, rating controls, codec/quality, volume, transport controls, then bottom actions.
  - The player volume control is a custom line control with no percent label.
  - The old `Gapless / No crossfade` capability label is removed from the player screen.
  - The collapse arrow is centered in the bottom action row, with a dedicated current-track radio icon on the left and a current-track overflow menu on the right.
  - The current overflow menu includes Start track radio, Track details, Go to artist, and Go to album. Playlist actions can extend this menu later.
  - Track details opens a modal with song metadata, provider IDs, favorite/rating state, audio codec/bitrate/sample/depth/content type, and replay-gain fields when available.
  - Track details also reads embedded tags from the cached audio file when available. Common tags are ordered first (`Title`, `Artist`, `Album Artist`, `Album`, track/disc/date/genre/etc.), and extra tags are shown alphabetically below them.
  - Embedded tag parsing currently covers ID3v2 text/comment frames for MP3-like files and FLAC Vorbis comments. Add MP4/M4A atom parsing later if needed.
  - Lyrics V1:
    - Provider contract includes lyrics lookup by track ID.
    - Navidrome/OpenSubsonic uses `getLyricsBySongId` and prefers synced structured lyrics when available.
    - Provider lyrics are cached in SQLite by source and remote track ID so repeat plays can render server lyrics without another provider request.
    - Current-track lyrics fall back to embedded cached-file tags (`USLT`, `SYLT`, `LYRICS`, `SYNCEDLYRICS`, etc.) when the server does not return lyrics.
    - Playback settings include an LRCLIB fallback toggle. When enabled, Naviamp queries LRCLIB only if current provider/embedded lyrics are missing or unsynced.
    - LRCLIB results are cached separately from Navidrome/provider lyrics so disabling the toggle removes LRCLIB from selection without disturbing server lyrics.
    - If existing lyrics are unsynced, LRCLIB only replaces them when it finds synced lyrics. If no lyrics exist, LRCLIB can provide synced or plain lyrics.
    - The player bottom row has a lyrics toggle next to the hamburger menu. In compact mode it swaps album art for lyrics; in wide mode it shows lyrics alongside the `UP NEXT` / `RELATED` area.
    - Timed lyrics highlight slightly ahead of the exact timestamp to feel aligned with vocals, auto-scroll so the active line stays near center, and click-to-seek works both forward and backward. Unsynced lyrics render as scrollable text.
    - Seek handling updates playback progress immediately and ignores short-lived stale mpv progress after a deliberate seek so scrub bar and lyric state can jump backward cleanly.
  - Starting track radio from the player screen does not restart the current track; it preserves playback and replaces the upcoming queue with radio recommendations.
  - `UP NEXT` rows have the shared overflow menu treatment. The first action starts track radio from that queued song.
  - The player hamburger menu includes lyrics toggle, track details, track radio, go to album, and go to artist. Add-to-playlist is present as a disabled placeholder until that flow exists.
  - The experimental visualizer was removed from the player because precomputed waveform/spectrum data did not feel like a real music-reactive surface. Bring visualizer support back only when the player can provide live PCM/FFT data.
  - Settings are organized into categories: Connections, Playback, Cache, Local data, and Diagnostics.
  - Settings Connections now shows saved server connections as a list with edit/connect actions plus a new-connection action, instead of exposing only raw connection text fields.
  - Connection details are hidden by default and only open when creating or editing a saved connection.
  - Saved connections support an optional user-supplied display name. If the name is blank, Naviamp uses the normalized server URL.
  - Saved connections can be deleted from Settings after a confirmation dialog. Deleting the currently connected source clears the active provider/session state.
  - Saved connection edit/delete row actions use icon-only controls instead of text buttons.
  - Navidrome connections support self-hosted networking options: default platform TLS, an explicit insecure certificate-verification bypass, a user-supplied trusted certificate/CA file, and mTLS through a PKCS12 client certificate store.
  - Navidrome TLS settings are persisted with saved media sources and applied to JVM HTTPS defaults so API calls, cover art, cached audio/downloads, and URL-based playback share the same trust/client-certificate behavior.
  - Settings Cache exposes audio caching on/off, prefetch depth, current audio cache usage, and max audio cache budget presets.
  - Popup menus use a shared dark Feishin-inspired treatment through `NaviampDropdownMenu` / `NaviampDropdownMenuItem`.
  - The `RELATED` tab is active. It loads same-album and same-artist tracks from the local source-scoped library index, excluding the current track and deduplicating by provider track ID.
  - Related rows can start playback immediately from the related list, and their row menu can start track radio.
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
  - Indexed track rows store enough audio metadata for player codec/bitrate display when playback starts from local-index-backed flows such as instant radio seeds.
  - Library lists use `LazyColumn`, load more rows as the list nears the bottom, and include an independently scrollable right-side `#`/`A-Z` jump rail for artists/albums.
  - Settings exposes separate local-data actions: clear image/API cache, clear local artist/album/track index, and a guarded full database reset that removes saved servers too.
  - Future Android/iOS work should reuse SQLDelight with platform drivers rather than introducing a separate storage stack.
- Settings easter egg:
  - Triple-click the Settings connection-status line to open a separate "Stats for nerds" window.
  - It shows app/runtime details, connection/provider info, saved media source details, library import status, DB/cache counts, playback capabilities, queue state, stream metadata for the current track, and a redacted recent Navidrome API call history.
- Local data controls:
  - Settings can clear image/API cache, clear the local artist/album/track index, or run a guarded full database reset that removes saved servers too.
- Mini player behavior:
  - Tapping the mini-player row opens the full player, but its transport buttons should only control playback and should not navigate away from the current screen.
- Playback session restore:
  - Saved sessions include the queue, current index, and last known playback position.
  - When playback is resumed after app restart, the first play action passes the saved position into the playback request so mpv starts at that offset. Do not rely on an immediate post-play IPC seek; mpv may not be ready yet.
- Radio:
  - Artist, album, and track rows expose "Start radio" menu actions where those rows appear.
  - Track radio starts playback immediately from the selected track, then appends recommendation results in the background.
  - Album radio starts playback from a random track from that album, then appends recommendation results in the background.
  - Artist radio starts playback from a random track from that artist, then appends recommendation results in the background.
  - Album/artist seed selection prefers the local library index for speed and falls back to provider details if needed.
  - Navidrome radio uses Subsonic/OpenSubsonic recommendation endpoints first: `getSimilarSongs2` for artist radio and `getSimilarSongs` for album/track radio.
  - If the server returns no recommendations, Naviamp falls back to local seed-adjacent queues from the artist/album details it can fetch.
  - Active radio queues auto-refill when 10 or fewer upcoming tracks remain by asking for track radio from the currently playing track, filtering duplicates, and appending fresh tracks.
- Navidrome play reporting:
  - Provider contracts now expose play reporting as a capability so future Jellyfin/Plex-like providers can implement their own equivalent APIs.
  - Naviamp sends `scrobble.view?id=<trackId>&submission=false` when playback starts so Navidrome's Now Playing view can reflect current playback.
  - Naviamp sends `scrobble.view?id=<trackId>&submission=true&time=<epochMillis>` once the playback session reaches the play threshold.
  - The current play threshold is 50% of the track or 4 minutes, whichever comes first. Each playback session reports at most one submitted play.
- Home V1:
  - Home now loads a richer provider-backed dashboard from Navidrome/Subsonic: mixes, recently added albums, recent/frequent/random album sections, playlists, genre spotlight, and a fixed 2000s decade spotlight.
  - Home station rows can start Library Radio, Random Album Radio, Genre Radio, and Decade Radio. Artist/Album Mix Builder rows jump into the matching Library tab for seed selection.
  - Provider contracts now include album lists, playlists, genres, playlist tracks, and random-song queries so other providers can implement the same Home surface.
- Audio cache V1:
  - Desktop now has a source-scoped file-backed audio cache with SQLite metadata for source, remote track ID, stream quality, local file path, byte count, content type, created time, and last access time.
  - `PlaylistEngine` prefers cached local files when present and otherwise resolves a fresh provider stream URL.
  - Playback starts background prefetch for up to 10 upcoming queue items and cancels that work when the queue/session changes.
  - Clear cache removes prefetched audio files in addition to images and provider responses.
  - The cache stores provider IDs and local paths, not long-lived authenticated stream URLs.
  - This opens a practical path to precomputed per-track analysis: waveform scrubber buckets, silence/intro/outro detection, loudness hints, beat/energy markers, cache-hit reporting, and better future offline/network-handoff behavior.
- Waveform scrubber V1:
  - Desktop now has `cached_audio_waveform` metadata keyed by source, remote track ID, and stream quality.
  - `AudioWaveformAnalyzer` uses resolved/bundled mpv as a decoder, writes a temporary mono 8 kHz WAV, parses 16-bit PCM amplitude peaks, normalizes them into compact buckets, then removes the temp file.
  - The current-track waveform is generated only after a cached audio file exists; the UI keeps the normal Material slider until analysis is available.
  - The current-track waveform path actively caches/analyzes the now-playing file; it does not depend on the upcoming-track prefetch job finishing.
  - Restored sessions should keep an already loaded waveform when playback starts for the same track; only clear/reload waveform UI state when the track ID changes.
  - The Now Playing scrubber draws a compact Compose waveform with played/unplayed coloring and supports click/drag seeking through the same `onSeek` path as the old slider.
  - Stats for nerds shows current-track waveform status (`Waiting`, `Loading`, `Cached`, `Generated`, or `Unavailable`) and bucket count so cache-hit behavior is easier to debug.
  - Stats for nerds also shows current-track audio cache status, size, and local path without touching cache access time.
  - Stats for nerds reports playback source (`Cached file`, provider stream, or provider stream with cache disabled) and audio prefetch counters.
  - Waveform generation is intentionally current-track only for now so upcoming prefetch does not become a CPU-heavy analysis queue.
  - Waveform rows are a separate cache from audio files. Audio eviction should not delete waveform analysis, because the waveform is small and can make repeat plays render instantly.
  - The waveform cache is size-capped at 32 MiB as a rough budget for about 10,000 analyzed tracks, with least-recently-used eviction based on waveform access time.
  - Stats for nerds reports waveform count, size, and budget. The Settings clear image/provider/audio cache action also clears waveform rows.
- Audio cache settings:
  - Audio caching can be disabled. When disabled, playback resolves fresh provider stream URLs and upcoming-track prefetch does not run.
  - Audio prefetch depth is persisted in settings and clamped between 0 and 25 tracks.
  - The audio cache disk budget is persisted in settings, applied to `DesktopCache`, and trims least-recently-used cached audio files when lowered.
- Downloads V1:
  - Downloads are stored separately from cached audio, with their own `downloaded_audio` SQLite rows, local file paths, byte accounting, and app-data `downloads` directory.
  - Playback checks for a downloaded file before checking the audio cache or resolving a provider stream URL.
  - The player hamburger menu has a Download track action that downloads the current track for offline use.
  - The Downloads tab now lists downloaded tracks, can play from that list, and can remove individual downloaded files.
  - Settings Cache exposes a separate download storage budget. New downloads are blocked when they would exceed that budget; normal cache clear/trim does not delete user-selected downloads.
  - Download rows persist the track metadata needed to render/play the list without depending on cached-audio rows.
- Downloads V1.1:
  - Track downloads are available from Now Playing, Search track rows, album detail track rows, and full-player `UP NEXT` / `RELATED` row menus.
  - Album downloads are available from Home album rows, Search album rows, Library album rows, Artist detail album rows, and Album detail.
  - Playlist downloads are available from Home playlist rows.
  - Album and playlist downloads resolve their track lists first, then download tracks sequentially against the same download storage budget.
  - Download progress is currently a simple status string and should be replaced with a richer queue/progress surface before large offline-library workflows.
- Player queue history:
  - The full-player `BACK TO` tab is real playback history, ordered most-recent first. It is not just the original queue slice before the current index.
  - Jumping to an upcoming track moves the current track into `BACK TO` and leaves skipped upcoming tracks in `UP NEXT`.
  - Jumping to a `BACK TO` item behaves like browser history: the selected older track becomes current, and later history moves forward into `UP NEXT`.
  - Settings > Playback includes a previous-button behavior toggle. `Restart first` seeks to the start when more than 10 seconds into the current track, then goes previous when clicked near the start. `Always previous` always navigates back in history.
  - Settings > Playback also includes an `Up Next selection` toggle with an info affordance. `Move selected` plays the clicked upcoming track while keeping skipped upcoming tracks queued; `Skip to selected` treats the click like advancing through the queue so skipped upcoming tracks become history.
  - Selecting a song from `UP NEXT` should scroll the list back to the top so the first visible row is the actual next song after the newly current track.
- Detail-page and overflow-menu actions now use leading/icon-only controls where appropriate. Keep menu text, but include recognizable leading icons for radio, download, details, album, artist, and playlist actions.
- Desktop mpv crossfade attempt:
  - A dual-mpv-process crossfade attempt caused regressions in seek, pause, progress polling, and track advancement.
  - `MpvProcessPlaybackEngine` was restored to the stable single-process mpv path and reports `supportsCrossfade = false` again.
  - Settings still has a persisted crossfade duration shape, but capability filtering forces it off for the current mpv process engine.
  - `ExperimentalCrossfadeMpvPlaybackEngine` is now available as an isolated opt-in prototype via `NAVIAMP_PLAYBACK_ENGINE=mpv-crossfade-prototype` or `-Dnaviamp.playback.engine=mpv-crossfade-prototype`.
  - Enable Settings > Playback > Debug logging to write prototype logs to `%TEMP%/naviamp/mpv-crossfade-prototype.log`. `NAVIAMP_PLAYBACK_TRACE=true` and `-Dnaviamp.playback.trace=true` still work as startup defaults.
  - The prototype follows Feishin's web-player crossfade shape: two players, muted prepared next player, equal-power fade, reset transition on pause/seek, and queue advancement after overlap resolution.
- Kotlin/IDE housekeeping:
  - Kotlin version catalog bumped to `2.3.0`.
  - Generated `apps/desktop/bin/` output is ignored.

## Reference Research

- Feishin waveform scrubber:
  - Feishin's `playerbar-waveform.tsx` uses `@wavesurfer/react`/`wavesurfer.js` to render a compact waveform in the player bar.
  - The waveform path resolves a stream URL with `useSongUrl`, delays loading briefly, loads the URL into a separate muted audio element, and uses WaveSurfer as visualization-only UI.
  - It keeps normal seek behavior outside the waveform engine: mouse/touch drag calculates a ratio from pointer position, seeks WaveSurfer for preview, then calls the player seek action with the target timestamp.
  - For Naviamp, do not copy the web implementation directly. Prefer a native/cache-first design: analyze cached audio files into compact amplitude buckets, store the analysis by source/track/quality, then draw the waveform in Compose as the scrub bar. Fall back to the normal scrub bar while analysis is missing.
- Feishin stream URL/cache behavior:
  - `use-stream-url.tsx` resolves stream URLs through React Query and remembers the current track URL briefly to avoid restarting playback when transcode settings change.
  - Feishin's visible cache settings clear React Query and Electron/browser cache. The inspected code did not show a source-scoped ahead-of-play audio-file cache like Naviamp's `cached_audio` table.
  - Naviamp's current file-backed cache is therefore a stronger base for Android/offline work than Feishin's browser-cache clearing path.
- Feishin crossfade/gapless behavior:
  - Feishin's web and WaveSurfer players use two player instances, track which player is active, start the next player near the end of the current duration, and crossfade volumes from progress callbacks.
  - Their newer web player includes multiple fade curves: linear, equal-power, exponential, and S-curve.
  - They explicitly reset transition state on seek, pause, current-song changes, and queue-clear paths. This is the main lesson for Naviamp's experimental mpv crossfade class.
  - Naviamp's audio cache helps crossfade by making the next local file available early, but it does not by itself solve dual-player state, seek/pause reset, queue advancement, or volume curve correctness.

## Important Current Behavior

- `PlaybackProgress.mergeWith(previous)` intentionally preserves known position/duration through brief unknown reads from mpv and ignores large backward jumps that are likely polling glitches.
- `playlistCallbacks.onTrackStarted` resets `playbackProgress` to `PlaybackProgress.Unknown` before entering the player.
- Search state lives in `Main.kt` and query persistence lives in `DesktopSettingsStore`.
- Album detail navigation currently falls back Home in some paths; future screen-state work should preserve deeper route state more deliberately.
- Track heart/star mutations currently update Navidrome from the player screen; shared row controls can broaden this later.
- Library page scroll/search state is still mostly in-memory in `Main.kt`; future route-state work should preserve the selected Library tab, search query, and scroll/jump position if needed.
- Authenticated stream URLs should be treated as short-lived secrets. Cache/provider tables should persist provider IDs and display metadata, then refresh stream URLs only when playback or prefetch needs them.

## Roadmap Items From The User

Top-of-mind work the user wants:

- Add crossfading to the player without regressing basic mpv transport behavior.
- Continue modularizing reusable UI pieces where screens still carry one-off media UI.
- Broaden starring and favoriting controls beyond the player, including reusable row-level controls.
- Continue refining the scrub bar.
- Add a Plexamp/Feishin-style waveform scrubber backed by cached-track analysis, not a second live stream where possible.
- Revisit visualizer support in the experimental player instead of bolting it onto the current mpv IPC path.
  - A true live spectrum analyzer needs live PCM/FFT data from the playback engine or platform audio APIs.
  - Feishin's desktop visualizer uses WebAudio plus `audiomotion-analyzer`; for mpv/local playback it captures system audio with Chromium `getDisplayMedia` and feeds that live stream to WebAudio.
  - Android has a native `android.media.audiofx.Visualizer` API, but it is Android-only and requires `RECORD_AUDIO`; evaluate it separately when the Android player exists.
  - Current Compose waveform libraries mostly draw precomputed waveform/progress rather than live spectrum data, which overlaps with Naviamp's cached waveform path.
  - Compose Media Player-style libraries may expose audio levels, but they are full playback stacks rather than a drop-in visualization layer for the existing mpv engine.
- Continue refining lyrics support: add MP4/M4A embedded lyrics parsing, improve provider/cache controls, and consider richer LRCLIB management.
  - For LRCLIB fallback, prefer pulling synced lyrics when the existing provider/embedded lyrics are missing or unsynced.
  - Investigate whether Navidrome can write synchronized lyrics back to files or sidecar metadata. If possible, consider an explicit user-controlled action to save LRCLIB lyrics back to the library.
- Improve play reporting with an offline retry queue and local history table so failed scrobbles can be retried and Home can use local play data without depending entirely on server history.
- Improve the upcoming queue further as needed.
- Expand row menus for `UP NEXT` queue items beyond Start track radio as more queue actions are added.
- Redesign the full player layout again:
  - Order should be album art, waveform/scrub bar, track title, artist, album/year, rating controls, codec/bitrate/quality, then volume.
  - Track title should remain bold and be slightly larger than album metadata.
  - Album/year metadata should use subtly differentiated color so the hierarchy is clear without looking dramatic.
  - Rating controls should remain provider-aware: Navidrome gets heart/stars; Jellyfin/Plexamp-like sources may need different controls.
- Continue refining Library browsing, including genres and richer artist/album grouping.
- Improve Home radio seeds with richer picker/detail flows for artists, albums, genres, and decades.
- Improve packaged app startup speed. The generated Windows executable opens noticeably slowly; profile cold start, runtime image startup, settings/database initialization, restored connection work, and first Home/library loading so the shell appears quickly and background work stays backgrounded.
- Phase 2 Home/personalization:
  - Add local playback history so Home can support Recent Plays, History, Most Played This Month, and better personalized mixes without depending on server-specific smart playlists.
  - Record enough play context to build useful Home sections later: source/server, track ID, album ID, artist ID, started timestamp, completed/skip signal, duration/position, and radio/playlist context when available.
  - Add richer Home detail pages or carousels for playlists, genres, decades, generated stations, and history sections.
  - Replace the fixed 2000s decade spotlight with dynamic decade/year picks from the indexed library.
  - Use local history plus indexed library metadata for Mixes For You, On This Day, More From recently played artists/labels/genres, and dynamic decade/year modules.
- Continue downloads/offline support:
  - Add artist download actions if desired, with clear confirmation because artists can be very large.
  - Add clear/remove controls for downloaded albums/playlists that are intentionally separate from cache clear.
  - Make offline mode explicit later: surface whether a track is playable from downloads, and avoid provider calls when the user is intentionally offline.

## Design Preferences

- Compact, playback-focused, Plexamp-inspired.
- Dense UI is preferred over roomy whitespace.
- Keep text readable; avoid muted grey for important secondary metadata.
- Keep provider-specific logic behind provider modules.
- Prefer small, focused changes and tests where behavior is nontrivial.
- Future UI should preserve screen state and scroll/search position when possible.

## Suggested Next Issues

Good next slices:

- Phase 2C follow-up: harden audio cache behavior for mobile/offline use, including expiry rules, partial download cleanup, and provider-specific refresh hooks if Android needs them.
- Downloads follow-up: add a clearer download queue/progress surface for multi-track jobs, plus downloaded indicators on rows/albums/playlists.
- Lyrics follow-up: investigate whether LRCLIB synced lyrics can be written back to Navidrome-managed files or sidecar lyric metadata, and only add this as an explicit user-controlled action if Navidrome supports it safely.
- Waveform follow-up: add cache-hit/status reporting for waveform generation in Stats for nerds.
- Waveform follow-up: consider queue-aware/background waveform analysis for likely-upcoming tracks after measuring CPU impact.
- Queue actions follow-up: add per-row overflow menus in `UP NEXT`, starting with Start track radio.
- Player actions follow-up: extend the current-track overflow menu with Track details, lyrics, add-to-playlist, and provider-specific actions.
- Related tab V1: define and populate provider-aware related content for the full player.
- Visualizer follow-up: prototype a real live PCM/FFT path in the experimental player, then wire the player UI to that capability once it behaves well.
- Crossfade follow-up: revisit `ExperimentalCrossfadeMpvPlaybackEngine` with cached local next files, explicit transition reset on seek/pause/skip/queue clear, and configurable fade curves inspired by Feishin's web player.
- Broaden reusable row-level favorite/rating controls beyond the player.
- Add Library genres and richer artist/album grouping.
- Add lyrics domain model and Navidrome/provider capability shape before building the UI.
- Re-approach crossfade with an isolated engine/prototype and explicit playback debug tracing.
- Profile and improve packaged Windows startup time so the app window appears quickly before connection/library work continues.
- Phase 2A: add a SQLite playback-history table and record play/skip/completion events from the desktop player.
- Phase 2B: build Home sections from local history: Recent Plays, History, Most Played This Month, and dynamic decade/year modules.
