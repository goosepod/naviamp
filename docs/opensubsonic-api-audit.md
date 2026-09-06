# OpenSubsonic API usage audit

Audited 2026-09-05 at commit `c4d9c5160a67ea24324397169301ba58a484a175`.

The findings below record the original source audit. The working tree now implements the corrections in shared Kotlin; source line numbers in the findings refer to the original commit, not the edited files.

## Implementation status

| Finding | Resolution |
| --- | --- |
| 1. Playlist integrity | Fetch playlists once without undocumented folder parameters. Preserve every ordered song occurrence, including duplicates and tracks outside the selected library folders. |
| 2. Original audio | Original playback sends `format=raw`; original offline downloads use `download`. Transcoded downloads continue to use `stream`. |
| 3. Listen reporting | Serialize lifecycle reports. Use `scrobble` presence and qualifying listen submissions when the playback-report extension is absent or fails. Persist qualifying submissions before sending, retaining the original timestamp; discard stale queued presence updates. |
| 4. Binary errors | Inspect a bounded prefix before writing audio. HTTP 200 XML/JSON API errors are rejected; valid audio retains all bytes. |
| 5. Playlist replacement | Use `createPlaylist(playlistId, songId...)` for nonempty replacement. Clear a playlist with explicit occurrence-index removals: the live Navidrome test showed that omitting all song IDs on `createPlaylist` leaves contents unchanged. Negotiate form POST to avoid large query URLs on supporting servers. Retain Bandcamp's explicit serial mutation behavior. |
| 6. Album metadata | Fetch ID3 metadata with `getAlbumInfo2` directly. |
| 7. Artist IDs | Negotiate `topSongsByArtistId` independently of Navidrome ID migration. |
| 8. Seeking | Send `timeOffset` only with advertised audio offset support; otherwise seek through the shared engine plan. |
| 9. Lyrics | Prefer main structured lyrics over translations and fall back to legacy artist/title lookup. Reuse known song metadata. |
| 10. Folder ranking | Merge ranked album results by their available timestamps/play counts/year. Random results are shuffled; absent ranking fields use a balanced merge. |
| 11. Permissions | Retain playlist owner/public/editability through common cache/UI models; gate playlist editing. Respect an explicit account download denial before obtaining a download URL. |
| 12. Request efficiency | Share a short-lived starred response, invalidate it after mutations, reuse one Home random-album pool, and bound radio fallback traversal. Unsupported radio endpoints may fall back; authentication, rate-limit, network, and cancellation failures propagate. The inactive sync helper now uses bulk song pages when supported. |
| Added: artwork continuity | Retain the displayed bitmap while a new artwork URL or decode size loads, avoiding a placeholder flash after a transition. |
| Added: ontology genre browsing | `getSongsByGenre` pages songs for the exact library tags selected by the ontology. A shared Browse songs flow retains continuation state, deduplicates overlapping genres, retries failed pages, and plays selected results. Random mixes continue to use `getRandomSongs(genre=...)`. |

All production changes are in common source sets. No database migration is required. No release, commit, or push is part of this change.

Protocol limits remain: form POST depends on server advertisement; an unadvertised legacy server may impose a URL-length limit on large playlist writes. Ranked multi-folder merges depend on the server supplying the ranking fields. A connection lost after a server accepts a scrobble but before its response arrives cannot provide exactly-once delivery without server-side idempotency.

## Confirmed findings

Source locations below refer to the audited commit. **Provider** means [NavidromeProvider.kt](../providers/navidrome/src/commonMain/kotlin/app/naviamp/provider/navidrome/NavidromeProvider.kt).

### 1. High: folder-scoped playlists lose occurrences and can produce incorrect edits

**Evidence:** Provider lines 798–837 and 875–887 call `getPlaylists` and `getPlaylist` once per selected folder, supplying `musicFolderId`. `playlistTracks` then filters entries and applies `distinctBy(track.id)`, even with only one selected folder. [NaviampCorePlaylistTransactionController.kt](../core/presentation/src/commonMain/kotlin/app/naviamp/presentation/NaviampCorePlaylistTransactionController.kt), lines 190–203, uses that projected list as the current playlist for replacement.

Neither playlist endpoint documents a folder filter. A conforming server may return the same full playlist for every request. A playlist `[A, B, A]` becomes `[A, B]`. If B is outside the selected folders, it becomes `[A]`; replacement then removes index 0 from the full server list, leaving entries that the client assumed were removed. This can produce unintended playlist contents, as well as incorrect playback and copying.

**Recommendation:** fetch each playlist once; preserve its ordered occurrences and original positions. Keep any folder-filtered display separate from the authoritative editing representation. Do not use a filtered view as a full replacement. Retain any demonstrated Bandcamp-specific filtering behavior only in its common provider profile.

**Verification:** duplicate IDs, one and multiple folders, missing folder metadata, out-of-scope entries between visible entries, and subsequent reorder/copy/remove operations.

Sources: [getPlaylist](https://opensubsonic.netlify.app/docs/endpoints/getplaylist/), [getPlaylists](https://opensubsonic.netlify.app/docs/endpoints/getplaylists/).

### 2. High: “Original” does not explicitly request original audio

**Evidence:** Provider lines 1324–1340 sends only `id` for `StreamQuality.Original`. [StorageAudioStore.kt](../core/storage/src/commonMain/kotlin/app/naviamp/storage/StorageAudioStore.kt), lines 207–255, also obtains download URLs through `streamUrl` and records quality/content type from the requested quality.

A server with default transcoding can therefore return converted audio for an Original request. The saved quality label and metadata can disagree with the actual bytes.

**Recommendation:** request `stream` with `format=raw` for original playback. Introduce a shared download-purpose contract so original downloads can use `download`, which guarantees original media, while transcoded downloads continue using `stream`. Account for distinct server stream/download permissions and verified provider exceptions.

**Verification:** server default transcoding enabled; verify actual codec, sample rate, content type, and original-file bytes for downloads.

Sources: [stream](https://opensubsonic.netlify.app/docs/endpoints/stream/), [download](https://opensubsonic.netlify.app/docs/endpoints/download/).

### 3. High: no standard scrobble fallback or offline submission path

**Evidence:** Provider lines 1302–1321 return without sending anything unless `playbackReport` was advertised. The provider contains no `scrobble` implementation, although generic Subsonic and Navidrome profiles declare play reporting support. Existing tests explicitly assert the no-op behavior when the extension is absent.

Explicit now-playing reporting and cached/offline listen submission are consequently missing for servers without that extension. A server may independently count streamed playback; that does not cover cached playback or make this implementation complete. Extension-based reporting also has no durable historical scrobble path for offline sessions.

**Recommendation:** keep `reportPlayback` for supported online sessions; implement standard `scrobble(submission=false)` for fallback now-playing and `scrobble(submission=true, time=...)` for qualifying listens. Put listen eligibility, timestamps, deduplication, persistence, and replay scheduling in Core. Avoid double-counting sessions already accounted for through timeline reporting.

**Verification:** extension absent/present, cache-only playback, offline reconnect, retries, skips, and repeated plays of the same track.

Sources: [scrobble](https://opensubsonic.netlify.app/docs/endpoints/scrobble/), [reportPlayback](https://opensubsonic.netlify.app/docs/endpoints/reportplayback/).

### 4. High: binary API failures can be stored as successful audio downloads

**Evidence:** [KtorNavidromeHttpClient.kt](../providers/navidrome/src/commonMain/kotlin/app/naviamp/provider/navidrome/KtorNavidromeHttpClient.kt), lines 180–244, validates HTTP status but streams every successful HTTP response into the audio writer. It does not inspect the content type or detect an API error document. Storage then records the result as downloaded audio.

The media endpoints can return XML error documents. With an HTTP-success error response, expired credentials or denied access can leave an XML document registered as an audio download.

**Recommendation:** recognize protocol error responses before committing audio, decode their API error, and discard partial artifacts. Keep parsing in the common provider transport. Do not reject valid audio solely because a server uses a generic binary content type.

**Verification:** HTTP 200 XML failure, JSON failure where returned by a server, valid generic binary audio, interrupted transfers, and no completed download row after failure.

Source: [download response contract](https://opensubsonic.netlify.app/docs/endpoints/download/).

### 5. Medium: playlist replacement reconstructs a dedicated operation and uses unbounded GET URLs

**Evidence:** Provider lines 976–1011 sends all old indices as `songIndexToRemove` and all desired IDs as `songIdToAdd`. The shared transaction controller first retrieves the current playlist. All standard API calls use GET through lines 1585–1608; `formPost` is not negotiated.

`createPlaylist(playlistId=..., songId=...)` already supports updating an existing playlist's contents. Using it removes the old-index payload and, for a true whole-playlist replacement, the read needed only to enumerate removal indices. This does not provide conflict detection by itself. Large creates, appends, and replacements also risk proxy/server URL-length limits.

**Recommendation:** use `createPlaylist` for full replacement, `updatePlaylist` for incremental edits and metadata. Negotiate `formPost` and send repeated arguments in an encoded form body when supported. Preserve Bandcamp's tested serial-mutation restriction; do not blindly chunk a replacement into partial writes. Resolve finding 1 before changing replacement semantics.

**Verification:** long playlists through a restrictive proxy, empty replacement, duplicates, order, preservation of playlist identity/metadata, and concurrent-edit policy.

Sources: [createPlaylist](https://opensubsonic.netlify.app/docs/endpoints/createplaylist/), [updatePlaylist](https://opensubsonic.netlify.app/docs/endpoints/updateplaylist/), [formPost](https://opensubsonic.netlify.app/docs/extensions/formpost/).

### 6. Medium: album metadata uses the directory-oriented endpoint first

**Evidence:** Provider lines 1539–1556 tries `getAlbumInfo` before `getAlbumInfo2`, passing the ID3 album ID used by `getAlbum` and `getAlbumList2`.

The documented ID3 endpoint is `getAlbumInfo2`. Servers that distinguish directory and ID3 identities can reject the first request or resolve the wrong identity. A successful empty object from the first call also stops the fallback before useful metadata is found.

**Recommendation:** prefer `getAlbumInfo2`; permit a narrowly justified compatibility fallback when necessary. Never assume a directory ID equals an ID3 album ID.

**Verification:** different directory/album IDs, successful empty responses, unsupported endpoints, and errors that should not trigger compatibility fallback.

Source: [getAlbumInfo2](https://opensubsonic.netlify.app/docs/endpoints/getalbuminfo2/).

### 7. Medium: generic servers cannot use their advertised top-songs-by-ID extension

**Evidence:** Provider lines 173–183 couples `topSongsByArtistId` support to `canonicalIdMigrationSupport`; lines 736–738 sends `id` only when that migration flag is Confirmed. The generic Subsonic profile disables canonical ID migration.

A generic server can advertise the extension and still receive only the artist name. That loses the extension's identity precision, especially for artists with identical names.

**Recommendation:** store the negotiated endpoint capability independently of Navidrome ID migration. Use artist ID whenever the connected server advertises it, irrespective of brand; retain name fallback otherwise.

**Verification:** generic-provider connection with the extension, duplicate artist names, and a server without the extension.

Source: [getTopSongs](https://opensubsonic.netlify.app/docs/endpoints/gettopsongs/).

### 8. Medium: audio time offsets are sent without negotiating their extension

**Evidence:** Provider lines 1335–1338 adds `timeOffset` for any positive start position. Connection validation never records `transcodeOffset` support.

The base parameter applies to video; audio support comes from the extension. A generic server may ignore it and restart audio from the beginning while the client expects the requested position.

**Recommendation:** expose negotiated audio-offset support through the shared playback contract. Use it only when supported, with native seeking or another validated fallback otherwise.

**Verification:** transcoded seek with and without extension support, resume, fractional offsets, and actual decoded start position.

Source: [Transcode Offset](https://opensubsonic.netlify.app/docs/extensions/transcodeoffset/).

### 9. Medium: lyrics omit legacy fallback and choose one enhanced track without considering its kind

**Evidence:** Provider lines 1279–1300 always tries `getLyricsBySongId`, even when version 1 is not advertised. It negotiates version 2 correctly, but ranks every returned lyric track by cues/timing/length and returns only the first. It does not prefer `kind=main`. It never calls `getLyrics`.

Legacy server lyrics can be missed, and an enhanced translation/pronunciation track can win over the main lyrics. External/embedded fallbacks elsewhere do not replace server-provided legacy lyrics.

**Recommendation:** negotiate both versions, use a bounded compatibility probe if needed, and fall back to `getLyrics(artist,title)` with existing track metadata. Preserve main-language/translation choices in a shared result model or at least prefer main tracks explicitly.

**Verification:** legacy-only lyrics, absent extension, unsupported vs transient failure, main plus translation/pronunciation entries, and multiple languages.

Sources: [getLyricsBySongId](https://opensubsonic.netlify.app/docs/endpoints/getlyricsbysongid/), [getLyrics](https://opensubsonic.netlify.app/docs/endpoints/getlyrics/).

### 10. Medium: multi-folder album lists favor the first folder over the requested ordering

**Evidence:** Provider lines 238–250 concatenates each selected folder's result, deduplicates, and takes the requested limit. Each folder receives the full limit. It does not merge by newest/recent/frequent/year order or shuffle random album results.

If folder A supplies eight albums, the eight-album Home row contains none from B, even when B contains the newest albums. Random album rows have the same first-folder bias. Native alphabetical library paging avoids this for its own path, but Home uses this separate list implementation.

**Recommendation:** implement a common merged-list policy per list type, using suitable timestamps/counts when available; merge random candidates before sampling. Document limitations when standard payloads cannot support an exact global ranking. Keep per-folder requests where the API only accepts one folder ID.

**Verification:** interleaved dates/years, a full first folder, random selection across folders, and missing ranking metadata.

Source: [getAlbumList2](https://opensubsonic.netlify.app/docs/endpoints/getalbumlist2/).

### 11. Medium: API metadata needed for edit permissions is discarded

**Evidence:** Provider `toPlaylist`, lines 1906–1916, and the shared `Playlist` model keep no owner/public/editability information. `getUser` is unused. Shared playlist mutation flows do not have an owner field to check.

The playlist list includes items the user may play, whereas updates require ownership. Provider-wide capability defaults cannot express whether a particular playlist is writable or an account has download permission.

**Recommendation:** retain ownership/visibility, add common per-item action eligibility, and use account-role information where supported. Permission errors must still be handled because rights can change. Avoid turning missing optional account metadata into a connection failure.

**Playlist-dialog correction:** `getUser.playlistRole` describes playlist creation, not permission to edit existing playlists. Using it as a global edit gate hid every playlist from the membership dialog on affected servers. The shared provider now honors the playlist's explicit `readonly` field first and falls back to ownership against the server-returned username when that field is absent. Download-role handling remains separate. Regression coverage includes a false creation role, editable shared playlists, explicitly locked owned playlists, and canonical usernames. See the [user response](https://opensubsonic.netlify.app/docs/responses/user/) and [playlist response](https://opensubsonic.netlify.app/docs/responses/playlist/) contracts.

Sources: [getPlaylists](https://opensubsonic.netlify.app/docs/endpoints/getplaylists/), [updatePlaylist](https://opensubsonic.netlify.app/docs/endpoints/updateplaylist/), [getUser](https://opensubsonic.netlify.app/docs/endpoints/getuser/).

### 12. Lower priority: repeated complete responses and error-driven fallback work

**Evidence:** Provider `favoriteTracks` and `favoriteArtists`, lines 839–873, separately fetch the complete `getStarred2` response and discard the other categories. Home loads favorite artists, while its sonic supplement requests favorite tracks. `playlistsForMusicFolder` also performs smart-playlist discovery for every folder. `HomeService.load`, lines 157–166, requests two random album lists with different limits. Radio fallback methods, lines 1410–1464, convert all failures to empty results and can then fetch every album by an artist, including a second fetch of the seed album.

**Recommendation:** share a short-lived, source/folder-scoped favorites snapshot with mutation invalidation and coalesce concurrent requests. Fetch playlist metadata once. Consider sharing a random pool if the two Home rows need no independent sampling. Distinguish unsupported/empty recommendations from authentication, cancellation, rate-limit, and transient failures; bound fallback work and reuse already loaded album details.

These are source-derived request opportunities, not measured latency claims. Caches and feature eligibility affect how often they occur.

Source: [getStarred2](https://opensubsonic.netlify.app/docs/endpoints/getstarred2/).

## Bulk sync: real inefficiency in an inactive helper

[LibrarySync.kt](../core/domain/src/commonMain/kotlin/app/naviamp/domain/library/LibrarySync.kt), lines 65–104, pages album summaries and then retrieves every album to build a track index when `includeAlbumTracks=true`. OpenSubsonic explicitly supports blank-query `search3` for offline metadata synchronization.

However, the audited tree contains no production caller of this helper beyond its wrapper. The active [catalog controller](../core/presentation/src/commonMain/kotlin/app/naviamp/presentation/NaviampCoreCatalogController.kt), lines 243–289, uses `albumsPage` and `tracksPage`; the provider already implements blank-query song paging and uses Navidrome native paging when available. Do not describe the old helper as a current app-wide network bottleneck.

Before reusing the helper, replace album-by-album track enumeration with a shared bulk-sync contract and a provider implementation using paged `search3`. Preserve any album-level metadata enrichment that song pages alone cannot supply. For illustration, 2,000 albums containing 20,000 songs require 2,000 uncached album-detail calls versus approximately 40 song pages at 500 songs/page, plus termination and any enrichment calls. This is arithmetic, not a measured benchmark or a mandated `search3` page size.

The helper's offset API also calls Provider `albums`, lines 376–390, which requests `limit + offset` from each selected folder. That eventually exceeds `getAlbumList2`'s 500-item maximum and can truncate a future sync. Use bounded pages and continuations rather than ever-growing prefixes.

Sources: [search3](https://opensubsonic.netlify.app/docs/endpoints/search3/), [getAlbumList2](https://opensubsonic.netlify.app/docs/endpoints/getalbumlist2/).

## What Naviamp already gets right

| Naviamp goal | Current API usage | Assessment |
| --- | --- | --- |
| Connection and folder selection | `ping`, `getOpenSubsonicExtensions`, `getMusicFolders` | Appropriate foundation; extend negotiated capabilities as described above. |
| Artist catalog/details | `getArtists`, `getArtist`, `getArtistInfo2` | Correct ID3 family. Local pagination of the complete artist catalog is reasonable. |
| Album catalog/details and Home rows | `getAlbumList2`, `getAlbum` | Correct dedicated operations; fix folder merging and album-info choice. |
| Search and song enumeration | `search3` with separate counts/offsets; unused result categories set to zero | Appropriate. Blank-query enumeration does not promise global title ordering. |
| Favorites and ratings | `getStarred2`, starred album list, `star`, `unstar`, `setRating` | Uses dedicated endpoints and correct song/artist/album parameter names. Share favorite snapshots. |
| Popular and related artists | `getTopSongs`, `getArtistInfo2` | Does not reconstruct popularity from discography. Separate top-song capability from migration. |
| Artist, album, and track radio | `getSimilarSongs2` for artist; `getSimilarSongs` for album/song | Valid for OpenSubsonic: the latter explicitly accepts artist, album, and song IDs. Do not blindly replace every call with the `2` variant. |
| Genre/decade/library radio | `getRandomSongs` with genre/year/folder filters | Correct random-sampling endpoint. |
| Sonic Mix/Path/related playback | `getSonicSimilarTracks`, `findSonicPath` | Uses server sonic operations, parses top-level `sonicMatch` and scores, and negotiates sonic support. |
| Playlists | `getPlaylists`, `getPlaylist`, `createPlaylist`, `updatePlaylist`, `deletePlaylist` | Good endpoint coverage; representation, replacement, and transport need fixes. |
| Internet radio | `getInternetRadioStations` and create/update/delete station endpoints | Dedicated operations are used. Re-listing after create is reasonable because create returns no station entity. |
| Artwork | `getCoverArt` with requested size | Correct server resizing request. |
| Lyrics | `getLyricsBySongId`, enhanced version 2 when advertised | Good modern support, including offset normalization; complete fallback and selection. |
| Playback status | `reportPlayback` with named state and milliseconds | Correct wire fields; complete legacy/offline handling and session ordering validation. |
| Library scan inspection | `getScanStatus` | Appropriate available primitive; no need to trigger a scan merely to browse. |

Supporting specifications: [getArtists](https://opensubsonic.netlify.app/docs/endpoints/getartists/), [getSimilarSongs clarification](https://opensubsonic.netlify.app/docs/endpoints/getsimilarsongs/), [getSonicSimilarTracks](https://opensubsonic.netlify.app/docs/endpoints/getsonicsimilartracks/), [findSonicPath](https://opensubsonic.netlify.app/docs/endpoints/findsonicpath/).

## Unused endpoints: opportunities versus unnecessary scope

Using every endpoint is not the objective. The following classifies the remaining endpoint families against Naviamp's music-player goals, rather than treating absence as a defect.

| Endpoint(s) | Recommendation |
| --- | --- |
| `download`, `scrobble`, `getLyrics`, `getUser` | Address the concrete existing-feature gaps above. |
| `getSongsByGenre` | Implemented for paged song browsing from ontology selections. `getRandomSongs(genre=...)` remains the random-mix endpoint. |
| `getPlayQueue`, `savePlayQueue`, `getPlayQueueByIndex`, `savePlayQueueByIndex` | Useful if cross-client queue handoff becomes a goal. Prefer negotiated index-based queues to preserve duplicate occurrences. Local Back To/Up Next/Related state is richer and still needs a shared local owner. No evidence that current settings sync is recreating this API operation. |
| `getTranscodeDecision`, `getTranscodeStream` | Optional future improvement for capability-based format selection when `transcoding` is advertised. Fixed user-selected formats do not require an extra decision request for every track. |
| `tokenInfo` / API-key authentication | Compatibility expansion: current connection schema is username/token/salt. Add a common authentication variant if API-key-only servers are in scope. |
| `getNowPlaying` | Useful for displaying server-wide listening activity; not a replacement for reporting this client's playback. |
| `getBookmarks`, `createBookmark`, `deleteBookmark` | Useful for audiobook/long-form resume synchronization, not necessary for ordinary local music queue state. |
| `createShare`, `getShares`, `updateShare`, `deleteShare` | Optional public sharing feature; no need to synthesize public links from private stream URLs. |
| `startScan` | Optional explicit server-rescan action; should respect account authorization. |
| `getIndexes`, `getMusicDirectory`, `getAlbumList`, `getArtistInfo`, `getStarred`, `search`, `search2` | Directory-oriented/older alternatives; not preferred replacements for the current ID3 catalog. Use only for a deliberate legacy/folder-browser feature. |
| Podcast list/detail/download/create/delete/refresh endpoints | Separate product feature, not required for the current music-library workflow. Includes `getPodcastEpisode` and `getNewestPodcasts`. |
| `getVideos`, `getVideoInfo`, `getCaptions`, `hls` | Video/subtitle features outside the current music-player goal. |
| `jukeboxControl` | Controls the server's audio output; does not replace Naviamp's local BASS engine. |
| `getAvatar`, chat endpoints | Optional social/account presentation. |
| `getLicense`, `getUsers`, create/update/delete user, `changePassword` | Server/account administration rather than ordinary playback. |

Sources: [endpoint catalog](https://opensubsonic.netlify.app/docs/endpoints/), [getSongsByGenre](https://opensubsonic.netlify.app/docs/endpoints/getsongsbygenre/), [index-based queue](https://opensubsonic.netlify.app/docs/extensions/indexbasedqueue/), [transcoding](https://opensubsonic.netlify.app/docs/extensions/transcoding/), [API-key authentication](https://opensubsonic.netlify.app/docs/extensions/apikeyauth/).

Navidrome's native `/api/album` and `/api/song` calls provide server-side sorted pagination and totals, including selected libraries. Standard song enumeration has no equivalent title-sort/total contract. Native smart-playlist rules likewise have no standard OpenSubsonic endpoint. These native calls are justified additions in provider `commonMain`, not evidence of unnecessarily recreating an OpenSubsonic operation.

## Updated validation

- JVM: 1,647 tests passed across Navidrome (150), Jellyfin (24), Domain (856), App (120), Presentation (258), UI (194), and Storage (45).
- Android: provider, domain, app, and presentation unit tests passed during validation; the Review APK and opt-in instrumentation APK compile successfully.
- Shared iOS arm64: Domain, App, UI, Presentation, and Navidrome Kotlin compilation succeeded. Full iOS host/native BASS linking and device validation are unavailable on this Windows host.
- The artwork regression holds a replacement decode pending and verifies the existing cover remains rendered rather than becoming the placeholder.
- HTTP transport tests confirm that an HTTP 200 XML error produces zero audio writes and that prefix inspection preserves a 100,000-byte audio response exactly.
- Pixel 10a live API audit passed against the saved Navidrome account: duplicate preservation, nonempty replacement, explicit clearing, genre retrieval, and original download. The temporary playlist was deleted. In-place installation used the existing Review package/signing key, preserving its app data.
- Device UI: selected the Ambient ontology branch, which resolved to Ambient and Tribal Ambient; Browse songs loaded real tracks, Load more fetched another page, and selecting a song started playback.
- Captured 24 consecutive frames around a manual change between different album covers and 24 around an automatic end-of-song transition; no second artwork disappearance appeared in the sampled frames. Playback was paused after testing.
- The opt-in Android live test uses the saved Android Keystore-backed connection and deletes its temporary playlist in a finally block. Enable it with the instrumentation argument `liveOpenSubsonicAudit=true`; it is skipped in ordinary test runs.

## Original audit validation and implementation order

Ran `gradlew.bat :providers:navidrome:jvmTest`: **134 tests passed, zero failures/errors/skips**. This checks the current implementation, not interoperability. Several passing tests explicitly preserve findings: folder parameters on playlist calls, remove-all/add-all replacement, directory-first album info, and no reporting without the extension. Those expectations need specification-driven revisions.

No Android/iOS build or live-server probe was run for this documentation-only audit. No credentials, playlists, libraries, or server settings were changed.

Recommended sequence:

1. Preserve authoritative playlist occurrences and positions; correct whole-playlist replacement; support negotiated form POST.
2. Make Original explicit, separate download intent, and reject API error documents before storing audio.
3. Complete reporting fallback/offline submissions. Validate ordered `starting → playing → paused/stopped` delivery and same-track restarts: the current shared controller launches start and state reports independently, so wire ordering is not guaranteed.
4. Correct album-info choice, independent extension flags, seek capability, lyrics selection/fallback, and permission metadata.
5. Fix multi-folder ranking and share complete-response caches; retire or modernize the inactive sync helper before use.
6. Test against a Navidrome server, a second OpenSubsonic implementation, an extension-poor legacy server, and Bandcamp's restricted profile. Include folder selection, default server transcoding, long playlists, duplicates, and offline playback. Capture request counts for a cold and warm Home load, playlist open/edit, and library page load.

All fixes belong first in shared domain/presentation/storage contracts and provider `commonMain`. The audit identifies no need to implement these product behaviors separately in Android, Desktop, or iOS.
