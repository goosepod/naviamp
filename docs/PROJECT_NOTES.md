# Project Notes

This file is the durable handoff for future chats. Start here when opening a new issue-focused conversation.

## How To Use This With Codex

For a new chat, say something like:

```text
We are working in the Naviamp repo. Please read docs/PROJECT_NOTES.md, CONTRIBUTING.md, and recent git history first. Today's issue is: <short issue>.
```

Keep each chat focused on one issue or feature. Commit when the issue is working. Update this file when a decision, quirk, or roadmap item should survive into future chats.

Project shorthand: when the user says "ship it", commit the currently modified project code. Do not push unless explicitly asked; the user will handle pushing by default.

## Project Shape

Naviamp is a Kotlin Multiplatform / Compose Multiplatform music client currently focused on desktop and Navidrome.

Current priorities:

- Windows desktop works and is the main live test path right now.
- Android is now an active target. The first app milestone is a thin native Android shell that can connect to Navidrome, search tracks, and play a selected stream through Media3.
- Naviamp should be truly multiplatform. When new work can be platform-agnostic, implement it in shared/domain/app/UI modules first and keep only OS-bound code in the platform app modules.
- Default bias: if behavior is not inherently tied to OS APIs, keep it platform-agnostic. Visual behavior, color derivation, layout decisions, screen state, queue/action models, and pure transformations should live in shared modules unless there is a concrete platform API boundary. If platform-specific-looking code is encountered during other work and cannot be moved immediately, add a note here so it is not forgotten.
- Navidrome is the first provider, but the app should stay provider-oriented.
- Playback uses mpv on desktop when available.
- Playback direction has changed: BASS is the target engine for desktop and Android. The current Kotlin desktop BASS path starts with JNA, but production should move to JNI for visualizers, BASSmix, crossfade, gapless playback, and platform parity. mpv should not remain bundled once BASS is stable.
- Audio/track caching is now a priority because it will matter for fast desktop skips, network handoff, and the future Android app.
- Offline downloads are separate from cache files. They can reuse cache/download plumbing, but user-selected downloads should live in their own storage area and should not be evicted by normal cache cleanup.
- The app should remember state across screens where it feels natural: search query/results, navigation, session queue, window size, and similar context.

UI convention:

- Prefer recognizable icons instead of text labels for compact actions where the meaning is standard, such as edit, delete, back, menu, playback, and navigation controls. Keep accessible content descriptions on icon-only controls.

Main source areas:

- `core/domain`: provider contracts and provider-neutral domain models.
- `core/domain/src/commonMain/kotlin/app/naviamp/domain/playback`: shared playback contracts, playback state/progress models, replay-gain settings, and engine capability shape.
- `core/ui`: shared Compose Multiplatform UI primitives used by desktop and Android. Keep cross-target visual language here first: colors, bottom navigation, transport icons, shared transport controls, cover-art abstraction, popup menu treatment, and row overflow menu primitives.
- `providers/navidrome`: Navidrome/Subsonic API implementation and mapping.
- `apps/desktop`: Compose desktop UI, desktop settings/cache, mpv/JLayer playback engine integration, and desktop tests.
- `apps/android`: Early Android app shell, Android Compose UI, and Media3 playback engine.

Useful docs:

- `docs/architecture.md`
- `docs/desktop-playback.md`
- `docs/kotlin-bass-roadmap.md`
- `docs/roadmap.md`
- `docs/setup.md`

## Current Build Notes

- Use the Gradle wrapper, not a system Gradle install.
- On Windows, set `JAVA_HOME` to the installed JDK before running Gradle if needed.
- The project Kotlin version is aligned to the latest stable `2.3.x` line; as of this Android parity work it is `2.3.21`.
- Dependency freshness is a project tenet. Prefer keeping Kotlin, Compose, Android Gradle Plugin, SQLDelight, coroutines, serialization, and AndroidX on current stable releases in small, validated batches so package drift does not become tech debt.
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
  - Shared `DesktopMediaRow`, `DesktopAlbumRow`, `DesktopArtistRow`, and `DesktopTrackRow` components back search, home, album detail, and up-next rows.
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
- Android persistence/storage step 4 has started:
  - `apps:android` now applies SQLDelight with the Android driver and has an app-local `NaviampAndroidDatabase`.
  - The initial Android schema mirrors the key desktop persistence boundaries: saved media sources, image/API cache tables, evictable audio cache, separate downloaded audio, waveform cache, and lyrics caches.
  - `AndroidStorage` owns app-scoped cache/download directories; downloads are under app files storage and are intentionally separate from evictable cache files.
  - Android Navidrome login now upserts a saved media source with token/salt into SQLDelight, and app startup can restore the latest saved source without needing the plaintext password path. SharedPreferences remains as a temporary migration/form fallback.
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
  - Android now persists a source-scoped playback session in SQLDelight with the shared `PlaybackSessionSettings` payload.
  - Android restore is conservative: reconnecting restores the saved queue/current track or last internet radio station into Now Playing and waits for the user to press Play; it does not autoplay.
  - Android track-session resume passes the saved position into the first Media3 playback request.
- Internet radio stream metadata:
  - `PlaybackStreamMetadata.fromProperties` in shared domain code normalizes common stream-title keys such as `icy-title`, `StreamTitle`, and `title`.
  - Desktop mpv and Android Media3 both feed raw stream metadata through the shared normalizer.
  - Android's Media3 HTTP data source sends `Icy-MetaData: 1`; many internet-radio servers do not send current-song metadata unless the client requests ICY metadata.
  - Android now updates the radio Now Playing title and notification title from Media3 ICY/current media metadata, matching the desktop behavior of showing the currently advertised stream title while keeping the station name as the secondary line.
  - Desktop mpv now observes `metadata` and `media-title` property-change events on a persistent IPC connection, so internet-radio title updates do not have to wait for the normal progress polling loop.
- Radio:
  - Radio queue behavior is now centralized in shared `core/domain` through `RadioService`. Desktop and Android should call that service for library, genre, decade, album, artist, and track radio instead of duplicating recommendation/seed/queue rules.
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
  - Home now loads a richer provider-backed dashboard from Navidrome/Subsonic: mixes, recently added albums, recent/frequent/random album sections, playlists, rotating genre shortcuts, and a data-driven decade spotlight selected from indexed album years when available.
  - Home station rows can start Library Radio, Random Album Radio, rotating Genre Radio shortcuts, and Decade Radio when backing content exists. Artist/Album Mix Builder rows jump into the matching Library tab for seed selection.
  - Provider contracts now include album lists, playlists, genres, playlist tracks, and random-song queries so other providers can implement the same Home surface.
- Audio cache V1:
  - Desktop now has a source-scoped file-backed audio cache with SQLite metadata for source, remote track ID, stream quality, local file path, byte count, content type, created time, and last access time.
  - `DesktopPlaylistEngine` prefers cached local files when present and otherwise resolves a fresh provider stream URL.
  - Playback starts background prefetch for up to 10 upcoming queue items and cancels that work when the queue/session changes.
  - Clear cache removes prefetched audio files in addition to images and provider responses.
  - The cache stores provider IDs and local paths, not long-lived authenticated stream URLs.
  - This opens a practical path to precomputed per-track analysis: waveform scrubber buckets, silence/intro/outro detection, loudness hints, beat/energy markers, cache-hit reporting, and better future offline/network-handoff behavior.
- Waveform scrubber V1:
  - Desktop now has `cached_audio_waveform` metadata keyed by source, remote track ID, and stream quality.
  - `DesktopAudioWaveformAnalyzer` uses BASS through `DesktopBassNative` to decode audio and normalize compact waveform buckets.
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
  - The canonical `PlaybackQueue` and `RepeatMode` now live in shared `core/domain/queue`, with common tests for history/up-next/jump behavior. Desktop and Android should use this shared queue model instead of platform-local queue lists.
  - Settings > Playback includes a previous-button behavior toggle. `Restart first` seeks to the start when more than 10 seconds into the current track, then goes previous when clicked near the start. `Always previous` always navigates back in history.
  - Settings > Playback also includes an `Up Next selection` toggle with an info affordance. `Move selected` plays the clicked upcoming track while keeping skipped upcoming tracks queued; `Skip to selected` treats the click like advancing through the queue so skipped upcoming tracks become history.
  - Selecting a song from `UP NEXT` should scroll the list back to the top so the first visible row is the actual next song after the newly current track.
- Detail-page and overflow-menu actions now use leading/icon-only controls where appropriate. Keep menu text, but include recognizable leading icons for radio, download, details, album, artist, and playlist actions.
- Shared UI extraction is underway:
  - `core/ui` owns the shared app colors, navigation icons, bottom navigation bar, transport icon vectors, Android cover-art abstraction, popup/dropdown menu styling, row overflow menu primitive, and the first shared transport control row.
  - Desktop `AppColors`, `NavigationIcons`, `TransportIcons`, `AppNavigation`, and popup menus are now thin adapters over `core/ui` where possible.
  - When adding Android functionality, prefer moving the existing desktop visual primitive into `core/ui` and having both targets call it, rather than recreating a similar-looking Android-only version.
- Playlists V1:
  - Playlists are server-backed through the provider contract. Navidrome uses Subsonic `createPlaylist`, `updatePlaylist`, and `deletePlaylist` for create, append, rename, and delete.
  - A top-level Playlists screen sits between Home and Library. It lists playlists alphabetically or by locally tracked recent play, with play, shuffle, rename, delete, download, and add-to-playlist actions.
  - Playlist detail behaves like an album detail surface: it can play/shuffle the full playlist, act on individual tracks, and renders a dynamic Compose cover collage from distinct album artwork in the playlist.
  - Add-to-playlist is a shared dialog that can append to an existing playlist or create a new server playlist. Artist and album additions expand to all current tracks before appending.
  - Add-to-playlist entry points are wired through Search, Home, Library, artist detail, album detail, playlist detail, Downloads, Now Playing, `BACK TO`, `UP NEXT`, and `RELATED`.
  - Home keeps the existing recent playlists section and adds a locally persisted Recently Played Radio section. Library, genre, decade, artist, album, random album, and track radio launches are recorded and can be restarted from Home.
- Internet Radio V1:
  - Internet radio stations are server-backed through the provider contract. Navidrome uses Subsonic `getInternetRadioStations`, `createInternetRadioStation`, `updateInternetRadioStation`, and `deleteInternetRadioStation`.
  - The top navigation includes an Internet Radio screen between Search and Downloads for symmetry. Stations can be added, edited, deleted, and played.
  - Internet radio playback uses the station stream URL directly through the playback engine instead of `DesktopPlaylistEngine`, so stations are not treated as normal library tracks or submitted as play reports.
  - The last played internet radio station is saved in the playback session so reopening the app can return to the Player with that station ready to play again.
  - Internet radio playback is treated as live: seeking is disabled, the scrubber shows a live/radio line instead of elapsed/duration playback info, and mpv-backed playback polls stream metadata so ICY/Shoutcast titles can surface when stations provide them.
  - When internet radio is playing, the Now Playing side panel swaps `BACK TO` / `UP NEXT` / `RELATED` for a compact saved station list and highlights the current station.
  - Stats for Nerds includes internet-radio station details, stream URL, home page URL, current ICY/media title, and raw stream metadata properties when available.
  - Home shows a Recent Internet Radio section only after a station has been played. Recent station entries are persisted locally.
- Settings uses a responsive layout: wide windows keep the two-column category/detail view, while narrow windows show a compact category list and open each settings group in its own scrollable detail view with a back control.
- Now Playing should bounce long track titles left and right instead of leaving important title text permanently clipped or using a continuous loop marquee.
- Now Playing has small shuffle/repeat controls around the main transport controls. Shuffle only reorders `UP NEXT` and keeps a snapshot so turning shuffle off restores the previous upcoming order. Repeat cycles Off -> Queue -> Current track -> Off.
- Android foundation:
  - `core:domain` and `providers:navidrome` now compile for Android as well as JVM.
  - Navidrome's provider logic is shared; platform-specific MD5, URL encoding, HTTP, and TLS handling live in JVM/Android source sets.
  - `apps:android` builds a debug APK with a minimal Compose shell for Navidrome connection, track search, and direct stream playback.
  - `AndroidMedia3PlaybackEngine` implements the shared `PlaybackEngine` contract with ExoPlayer and a Media3 session. It currently supports play, pause, resume, seek, stop, volume, progress polling, and basic metadata.
  - Android now applies the active Navidrome TLS settings to Media3 stream playback so self-signed/custom/mTLS connection settings also cover the final media URL.
  - Shared app/session state extraction has started in `core/domain/app`. Android now uses shared navigation/content selection state for route, search, album detail, artist detail, and playlist detail state.
  - Android does not yet have saved connections, session restore, background notification controls, local cache/downloads, or full queue-aware desktop parity.

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

## Platform-Agnostic Extraction Worklist

Default stance: implement behavior in shared code unless it needs OS APIs, a platform playback engine, platform storage, native file/certificate picking, notification/background service plumbing, or desktop windowing.

1. Shared app/session state layer:
   - Extract the route, connection, selected album/artist/playlist, search, home, station, now-playing, queue, and status state currently split between desktop `Main.kt` and Android `MainActivity.kt`.
   - Target shape: a shared app controller/view-model in common code that accepts a `MediaProvider`, playback facade, settings repository, and cache/history interfaces.
   - Keep Compose window setup, Android activity lifecycle, and desktop window state in platform modules.
2. Shared queue/playback orchestration:
   - Move browser-history `BACK TO`, `UP NEXT`, previous-button behavior, up-next selection behavior, shuffle snapshot/restore, repeat mode, queue append/replace, and radio refill policy out of desktop `DesktopPlaylistEngine`.
   - `DesktopPlaylistEngine` should become a platform adapter around a shared queue session plus a platform `PlaybackEngine`.
   - The existing tiny `core/domain/queue/PlayQueue` is not enough for current app behavior and should be replaced or expanded from the proven desktop queue behavior.
3. Shared playback session persistence models:
   - Move `PlaybackSessionSettings`, saved track/album/artist/station DTOs, recent radio streams, recent playlist IDs, navigation/search settings, and playback/cache setting models out of desktop settings into common code.
   - Platform settings stores should only read/write those common serializable models using platform storage.
   - Initial shared settings models now live in `core/domain/settings`: connection form state, playback/cache settings, previous/up-next behavior enums, navigation/search settings, recent radio streams, saved media DTOs, and playback session restoration. Desktop keeps only the JSON store, window settings, and provider-token connection wrapper locally.
4. Shared playlist workflows:
   - Move playlist details loading, play/shuffle, rename/delete, add-to-playlist target expansion, duplicate handling, and recent-playlist tracking into a shared playlist service.
   - Desktop and Android should only provide UI callbacks and confirmation surfaces.
   - Smart playlists: the shared rule model and Compose builder now live in common code, with desktop and Android saving through the shared provider contract. Navidrome uses `.nsp`-compatible JSON rules, but Feishin shows that direct saves can use Navidrome's native `/auth/login` and `/api/playlist` endpoints with a native bearer token. Reference Navidrome docs: https://www.navidrome.org/docs/usage/features/smart-playlists/, the generator library: https://github.com/WB2024/Navidrome-SmartPlaylist-Generator-nsp, and Feishin's working smart-playlist UI.
5. Shared internet-radio service:
   - Move playlist/stream URL resolution from Android `MainActivity` into common code behind a small HTTP client interface.
   - Share station CRUD orchestration, recent-station tracking, live-stream session restore, and station-to-now-playing model conversion.
   - Keep actual live audio playback and metadata polling in platform playback engines where required.
6. Shared lyrics service:
   - Move LRCLIB query construction, response parsing, LRC parsing, provider/embedded/LRCLIB preference rules, and cache selection into common code.
   - Initial extraction is in place in `core/domain/lyrics`: generic LRC/plain lyric parsing, lyric tag-key detection, provider/embedded/online preference rules, a `LyricsProvider` contract, and an LRCLIB provider base.
   - Desktop and Android LRCLIB clients now implement the shared LRCLIB provider by supplying only HTTP transport and URL encoding.
   - Desktop embedded-file tag reading stays platform-specific, but embedded lyric tag detection/parsing now delegates into the shared lyrics parser.
   - Android Settings now exposes the same LRCLIB lyrics toggle as desktop and gates online lyric fallback through it.
   - Keep HTTP transport and embedded-file tag readers behind interfaces because Android and desktop read files/network differently today.
7. Shared home/browse orchestration:
   - Move Home content assembly, decade/genre station selection, provider-backed sections, and UI model mapping into common code.
   - Initial extraction is in place in `core/domain/home`: provider-backed Home sections, genre rotation, decade candidate selection, Home station IDs/parsing, and a cache-backed album-year repository contract.
   - Android and desktop now both load Home through the shared `HomeService`; desktop supplies album-year data from `DesktopCache`, while Android uses the shared fallback decade policy until its SQLDelight library repository is wired in.
   - Android shared-shell Home UI mapping now uses the common Home content and station model mapper in `core/ui`.
   - Desktop cache-backed library index can be one implementation of a shared library repository; Android should later use the same contract with SQLDelight Android drivers.
8. Shared media UI models and mappers:
   - Move repeated `Track`/`Album`/`Artist`/`Playlist` to UI row mapping into common code so Android does not keep local copies of desktop behavior.
   - Initial mapper extraction is in place in `core/ui/MediaUiMappers.kt`: artist/album/playlist/station media items, track rows, now-playing queue items, playlist choices, details, and search result UI mapping.
   - Android now delegates its repeated media UI model mapping to shared `core/ui` mappers and only keeps provider cover-art URL resolution locally.
   - Continue putting reusable Compose surfaces in `core/ui` and only use platform-specific `expect/actual` for cover art/image loading or OS-required pieces.
9. Shared formatting utilities:
   - Move duration labels, audio-quality labels, stream stats, bytes labels, decade labels, rating labels, and playlist total-duration labels to common code.
   - Initial extraction is in place in `core/ui/Formatting.kt`: track/int/double duration labels, byte/storage-size labels, stream-quality labels, compact audio-info labels, rating labels, replay-gain decimal labels, and playlist total-duration labels.
   - Android detail/now-playing audio labels, shared media UI mappers, and desktop cache/download/stats byte labels now use shared formatting helpers.
   - This reduces small UI mismatches and keeps Android/desktop wording consistent.
10. Shared waveform model and pure analysis helpers:
   - Keep platform decoders separate: mpv/WAV decode on desktop, MediaCodec on Android.
   - Share bucket normalization, waveform cache metadata shape, scrubber UI model, and seek math.
   - Initial extraction is in place in `core/domain/waveform`: shared `AudioWaveform`, waveform cache metadata, stream-quality waveform cache keys, bucket normalization for decoded peaks/PCM samples, playback fraction, and seek-target math.
   - Desktop still decodes through mpv/WAV and Android still decodes through MediaCodec, but both now delegate pure bucket normalization to shared code and use the same waveform model.
11. Shared cache/download contracts:
   - Define common repository interfaces for image/API cache, audio cache, waveform cache, downloads, local library index, and playback history.
   - Desktop can keep its current SQLite/file implementation; Android should implement the same contracts with SQLDelight Android driver and app-scoped storage.
   - Initial contract extraction is in place in `core/domain/cache`: shared interfaces for image cache, provider response cache, audio cache, waveform cache, downloads, playback history, local library index, and cache maintenance.
   - Desktop `DesktopCache` now explicitly implements the shared contracts it already supports, and the library snapshot/index stat models are shared domain types.
   - Android `AndroidStorage` now implements the shared contracts on top of SQLDelight and app-scoped cache/download directories, including cached images/responses/audio/waveforms, downloads, local library indexing, playback history, and cache maintenance.
   - Shared SQLDelight schema now lives in `core/storage` as `NaviampStorageDatabase`; Android and desktop only provide platform drivers and file locations, so storage table/query changes are made once.
12. Shared connection/source management:
   - Move saved connection models, display-name fallback, connection normalization, TLS option models, and source identity concepts into common code.
   - Keep certificate file selection and platform TLS application in platform/provider source sets.
   - Initial extraction is in place in `core/domain/source`: shared TLS option model, saved media source model, media source identity, base URL normalization, display-name fallback, and stable source ID generation.
   - Navidrome keeps provider-specific connection creation and platform TLS application, but its TLS settings now alias the shared domain model and storage returns the same saved source type on Android and desktop.
13. Shared now-playing parity:
   - Keep the current shared `NaviampNowPlayingPanel` as the source of truth for Android and desktop visual behavior.
   - Close remaining visual deltas there first. Android album artwork should get the same kind of drop shadow/depth treatment desktop has.
   - Large now-playing album art depth now lives in shared `NaviampNowPlayingPanel` through a common wrapper around `PlatformCoverArt`, so Android gets the same elevated album-art treatment without hiding that behavior in the Android image loader.
   - Platform cover art should keep the previous bitmap visible while the next URL loads, then crossfade quickly to the new artwork. Android and desktop now both do this through their shared `PlatformCoverArt` actuals.
   - Desktop full now-playing now feeds the shared `NaviampNowPlayingPanel` and only keeps desktop-specific adaptation around it: desktop add-to-playlist routing and track/tag/detail mapping.
   - The shared `NaviampMiniNowPlaying` row is used by the shared shell and desktop mini-player so compact now-playing presentation stays in common UI.
   - Shared `NaviampPlayerColors` / `NaviampAlbumPalette` own album-art palette to gradient selection. Platform actuals should only decode/sample artwork pixels before passing RGB samples into shared code.
   - Old desktop-only full-player helper code has been removed from the desktop adapter now that desktop renders through shared now-playing UI.
14. Shared row/menu action catalog:
   - Centralize the available row actions for tracks, albums, artists, playlists, stations, downloads, queue rows, and search results so each platform does not manually drift.
   - UI can still decide whether an action appears as icon-only, menu item, or disabled option.
   - Initial catalog extraction is in place in `core/ui/NaviampActionCatalog.kt`: shared action IDs, labels, icons, enabled-state specs, and helper catalogs for track, album, artist, playlist, station, download, queue, and now-playing menus. Desktop row menus and shared now-playing/queue menus now consume this catalog while keeping platform callbacks local.
15. Shared settings layout and ui
   - Centralize settings options and layouts so that both desktop and android have a similar interface
   - Find what options can be shared, what isn't needed on android, or what should be added and not on desktop
   - Initial settings extraction is in place in `core/ui/NaviampSettingsUi.kt`: shared settings category metadata, shared Android shell settings content, and a shared playback settings section for ReplayGain, crossfade, previous/up-next behavior, debug logging, and LRCLIB. Desktop now uses the shared category metadata and shared playback section while keeping desktop-only connection management, cache sliders, local data actions, and Stats for Nerds wiring local. Android uses the same playback settings layout; unsupported ReplayGain/crossfade options are visible but disabled, while previous-button behavior, up-next behavior, debug logging, and LRCLIB are persisted in Android settings.
   - Android connection editing now follows the desktop form structure more closely: connection details, optional connection name, username/password row with saved-token reuse, TLS, and mTLS sections.

Known platform-specific boundaries:

- Desktop window creation, menu bar, Stats for Nerds separate window, mpv/JLayer engines, mpv executable resolution, desktop tag readers, and desktop filesystem/cache paths.
- Android Activity lifecycle, foreground service/media notification, Media3/ExoPlayer engine, Android scoped storage, notification artwork, Android system volume behavior, and Android-specific TLS/media-source wiring.
- Provider platform source sets for HTTP/TLS/crypto/URL handling may stay split as long as the provider-facing behavior remains shared.

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
- Default architecture rule: provider behavior, queue/radio decisions, domain models, playback contracts, screen models, and reusable Compose UI should be shared whenever possible. Platform modules should mainly own playback engine implementations, notifications/background services, OS storage, file/certificate pickers, desktop windowing, and platform-specific adapters.
- `playlistCallbacks.onTrackStarted` resets `playbackProgress` to `PlaybackProgress.Unknown` before entering the player.
- Search state lives in `Main.kt` and query persistence lives in `DesktopSettingsStore`.
- Album detail navigation currently falls back Home in some paths; future screen-state work should preserve deeper route state more deliberately.
- Track heart/star mutations currently update Navidrome from the player screen; shared row controls can broaden this later.
- Library page scroll/search state is still mostly in-memory in `Main.kt`; future route-state work should preserve the selected Library tab, search query, and scroll/jump position if needed.
- Authenticated stream URLs should be treated as short-lived secrets. Cache/provider tables should persist provider IDs and display metadata, then refresh stream URLs only when playback or prefetch needs them.
- Shared playback types live in `core/domain` so Android can implement a Media3-backed engine against the same `PlaybackEngine` contract. Desktop-specific engine factories and mpv/JLayer implementations stay under `apps/desktop`.

## Android Roadmap

The Android work should be staged so the desktop app keeps working while platform seams move into the right places.

1. Separation of concerns:
   - Keep provider contracts, playback contracts, queue behavior, and provider-neutral models in shared/common code.
   - Keep desktop-only windowing, file pickers, mpv/JLayer engines, JVM image/tag helpers, and desktop settings/cache adapters out of shared modules.
   - Extract reusable Compose panels only after their dependencies are platform-neutral.
2. Android project skeleton:
   - The initial `apps:android` module exists and builds.
   - A new `core:ui` shared Compose module now owns the first cross-platform Naviamp shell, navigation shape, color contract, icons, connection form, home/search/list surfaces, media rows, and mini now-playing surface used by Android.
   - Desktop now consumes the shared UI module for the app color contract, bottom navigation, and navigation/action icons; its existing panels still live in `apps:desktop` until their state dependencies are separated.
   - Next: move desktop panels into shared UI only after their data/state dependencies are platform-neutral; avoid moving desktop-only cache/settings/window/playback code into common code.
3. Android playback:
   - The initial Media3-backed `PlaybackEngine` exists.
   - Next: add proper foreground playback service/notification controls so playback works from background, lock screen, headset controls, and Android Auto-compatible surfaces later.
   - Treat replay gain, crossfade, waveform analysis, and visualizer support as later capability-gated work.
4. Android persistence and storage:
   - Reuse SQLDelight with the Android driver.
   - Initial SQLDelight Android driver/schema/storage adapter is in place.
   - Saved Navidrome media sources now persist to Android SQLDelight and can restore on app startup from token/salt.
   - Add Android settings/storage adapters for saved connections, recent items, sessions, image/API cache, audio cache, and downloads.
   - Respect scoped storage and keep user-selected downloads separate from evictable cache files.
5. Android app milestone order:
   - Connect/search/stream is in place as the first proof point.
   - Android now uses the shared Naviamp-style shell instead of the temporary Android-only Material proof screen, and provider-backed Home, Search, Library, Playlists, and Internet Radio lists are wired into the shared surfaces.
   - Session restore has started: saved queues/stations are persisted per source and restored into Now Playing without autoplay.
   - Next: add fuller queue controls and deepen restored-session polish.
   - Then deepen each screen toward desktop parity: playable album/playlist/library detail flows, station management, downloads, richer settings, and row actions.
   - Add background playback polish, media notification actions, and cache/download behavior before treating Android as daily-driver ready.

## Roadmap Items From The User

Top-of-mind work the user wants:

- Add crossfading to the player without regressing basic mpv transport behavior.
- Continue modularizing reusable UI pieces where screens still carry one-off media UI.
- Broaden starring and favoriting controls beyond the player, including reusable row-level controls.
- Continue refining the scrub bar.
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
- Continue refining Library browsing, including genres and richer artist/album grouping.
- Improve Home radio seeds with richer picker/detail flows for artists, albums, genres, and decades.
- Improve packaged app startup speed. The generated Windows executable opens noticeably slowly; profile cold start, runtime image startup, settings/database initialization, restored connection work, and first Home/library loading so the shell appears quickly and background work stays backgrounded.
- Phase 2 Home/personalization:
  - Home now avoids the fixed 2000s spotlight. The decade module is selected from indexed album release years when available, and genre radio shortcuts rotate through the server's top genres.
  - Add local playback history so Home can support Recent Plays, History, Most Played This Month, and better personalized mixes without depending on server-specific smart playlists.
  - Record enough play context to build useful Home sections later: source/server, track ID, album ID, artist ID, started timestamp, completed/skip signal, duration/position, and radio/playlist context when available.
  - Add richer Home detail pages or carousels for playlists, genres, decades, generated stations, and history sections.
  - Use local history plus indexed library metadata for Mixes For You, On This Day, More From recently played artists/labels/genres, and dynamic decade/year modules.
- Continue downloads/offline support:
  - Add artist download actions if desired, with clear confirmation because artists can be very large.
  - Add clear/remove controls for downloaded albums/playlists that are intentionally separate from cache clear.
  - Make offline mode explicit later: surface whether a track is playable from downloads, and avoid provider calls when the user is intentionally offline.

## Design Preferences

- Compact and playback-focused.
- Dense UI is preferred over roomy whitespace.
- Keep text readable; avoid muted grey for important secondary metadata.
- Album artwork should have depth. Android album art needs a drop shadow/depth treatment matching the desktop player rather than sitting flat on the background.
- Keep provider-specific logic behind provider modules.
- Prefer small, focused changes and tests where behavior is nontrivial.
- Future UI should preserve screen state and scroll/search position when possible.
- When collapsing the player with the down arrow, return to the last-used screen and restore that screen's last vertical scroll position, not just the route.

## Suggested Next Issues

Good next slices:

- Android separation follow-up: continue extracting shared app state and platform-neutral Compose surfaces without moving desktop-only cache/settings/window/playback code into common code.
- Android app follow-up: add Media3 foreground playback service/notification controls and saved session restore.
- Android app follow-up: replace the proof-of-life connection/search screen with the real navigation shell and queue-aware playback flow.
- Phase 2C follow-up: harden audio cache behavior for mobile/offline use, including expiry rules, partial download cleanup, and provider-specific refresh hooks.
- Downloads follow-up: add a clearer download queue/progress surface for multi-track jobs, plus downloaded indicators on rows/albums/playlists.
- Lyrics follow-up: investigate whether LRCLIB synced lyrics can be written back to Navidrome-managed files or sidecar lyric metadata, and only add this as an explicit user-controlled action if Navidrome supports it safely.
- Prefetch sidecar follow-up: because Naviamp already pre-caches upcoming audio, add a bounded background prep pass for those files: build waveform rows, read tags, fetch provider/embedded lyrics, and optionally run LRCLIB fallback before the track starts so Now Playing data appears instantly.
- Queue actions follow-up: keep expanding per-row overflow menus in `UP NEXT`, `BACK TO`, and `RELATED` as new actions become useful.
- Visualizer follow-up: prototype a real live PCM/FFT path in the experimental player, then wire the player UI to that capability once it behaves well.
- Crossfade follow-up: revisit `ExperimentalCrossfadeMpvPlaybackEngine` with cached local next files, explicit transition reset on seek/pause/skip/queue clear, and configurable fade curves inspired by Feishin's web player.
- Broaden reusable row-level favorite/rating controls beyond the player.
- Add Library genres and richer artist/album grouping.
- Profile and improve packaged Windows startup time so the app window appears quickly before connection/library work continues.
- Phase 2A: add a SQLite playback-history table and record play/skip/completion events from the desktop player.
- Phase 2B: build Home sections from local history: Recent Plays, History, Most Played This Month, and dynamic decade/year modules.
