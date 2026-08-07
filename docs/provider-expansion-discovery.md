# Provider Expansion Discovery

**Status:** Discovery
**Branch:** `feature/provider-expansion`
**Baseline:** 2026-08-06

## Goal

Add explicit server/provider selection and expand Naviamp in this order:

1. Generic Subsonic/OpenSubsonic servers.
2. Jellyfin.
3. Bandcamp's Subsonic beta.

The integrations must remain one shared product. Provider protocol behavior, authentication,
capabilities, mapping, persistence policy, connection UI, and session routing belong in common
Kotlin. Android, Desktop, and iOS may only supply unavoidable network, TLS, secure-storage, and
host-lifecycle effects.

## Existing Architecture

Several foundations already support multiple providers:

- `MediaProvider` is a shared, provider-neutral product contract.
- `SavedMediaSource` and SQLDelight storage already persist a stable `providerId`.
- Settings Sync already includes `providerId` and defaults older profiles to `navidrome`.
- Source identity and cache keys include the provider ID, preventing two providers at the same URL
  from sharing identity accidentally.
- The connection lifecycle and connection-attempt policy are already shared.

The current blocker is session construction. `NaviampCoreProviderSessionPort` is implemented by
`NavidromeCoreProviderSessionPort`, and all three hosts instantiate it directly. Host compositions
also cast the active provider to `NavidromeProvider` and locate the latest "Navidrome" source. New
providers must not multiply those host-specific branches.

The connection form also lacks a provider ID. Adding provider choice does not require a database
migration, but it does require carrying the selected provider through shared form state, validation,
connection inventory, editing, and settings synchronization.

## Provider and Protocol Model

Do not treat a branded provider and its wire protocol as the same concept. Use two common concepts:

- **Provider kind:** the user-facing, persisted choice: `navidrome`, `subsonic`, `jellyfin`, or
  `bandcamp`.
- **Protocol family:** implementation metadata used for code reuse: Subsonic/OpenSubsonic or
  Jellyfin.

Navidrome, generic Subsonic, and Bandcamp therefore have distinct provider kinds and presentation,
while sharing a Subsonic protocol engine. Jellyfin uses a separate protocol adapter.

A common provider catalog should own each provider's stable ID, display name, icon key, protocol,
URL guidance, editable connection fields, authentication shape, and factory. Existing saved sources
with a missing or blank ID continue to resolve to Navidrome.

## Connection Experience

Show a row or responsive grid of selectable server tiles above the connection fields. Each tile
should contain an icon and visible text; icons alone are ambiguous and inaccessible. Selection uses
the same shared UI on every platform.

Initial tiles:

| Tile | URL behavior | Credentials |
| --- | --- | --- |
| Navidrome | User-entered server URL | Username and password; native token remains an internal session detail |
| Subsonic | User-entered server URL | Username and password/token compatible with the server |
| Jellyfin | User-entered server URL | Username and password exchanged for a Jellyfin access token |
| Bandcamp | Prefill `https://bandcamp.com/api/subsonic` | Credentials generated in Bandcamp Fan Settings |

Provider-specific guidance can change labels and helper text, but the form and validation policy
remain Core-owned. A tile can be disabled only when its integration is not compiled or intentionally
feature-gated; unsupported features after connection are handled by provider capabilities, not by
disabling the provider itself.

Before bundling third-party logos, verify each project's current brand-asset terms. Neutral,
Naviamp-owned server icons plus visible provider names are a safe first implementation and can later
be replaced with approved brand marks.

### Multi-server client icon audit

Reviewed on 2026-08-06:

| Client | Provider selection | Icon treatment | Lesson for Naviamp |
| --- | --- | --- | --- |
| [Feishin](https://github.com/jeffvli/feishin) | A segmented control for Jellyfin, Navidrome, and OpenSubsonic | Bundles each project's recognizable raster logo above a visible text label; the same marks identify saved servers | Closest match to the proposed tiles and confirms that icon plus text scans well. Its GPL repository does not by itself establish a separate trademark grant, so Naviamp should not copy those files as licensing proof. |
| [Supersonic](https://github.com/dweymouth/supersonic) | Horizontal `Subsonic` and `Jellyfin` radio buttons | Text only in the add/edit dialog | A clear, low-risk fallback. Provider choice remains understandable without official marks. |
| [Amperfy](https://github.com/BLeeEZ/amperfy) | An API selector offering Auto-Detect, Ampache, Subsonic, and legacy Subsonic | Text menu; generic field icons identify URL, user, and password rather than provider brands | Supports keeping protocol choice explicit while reserving imagery for field meaning. It also demonstrates that auto-detection can be an option rather than silently overriding an explicit choice. |

The comparison supports Naviamp's tile layout but does not remove the need for per-brand permission.
The initial implementation therefore uses distinct Naviamp-owned line icons and always-visible names.
Official marks can replace them individually only after the source asset, copyright license,
trademark policy, required attribution, allowed color/shape changes, and package redistribution are
recorded. Bandcamp needs particular caution: its current terms describe company content as protected
and do not provide a general redistribution license for the Bandcamp logo.

## Protocol Comparison

| Area | Generic Subsonic/OpenSubsonic | Jellyfin | Bandcamp beta |
| --- | --- | --- | --- |
| Transport | `/rest/*.view`, normally JSON | Native Jellyfin REST API | Subsonic endpoint at a fixed Bandcamp URL |
| Authentication | Username plus salted MD5 token, legacy password, or advertised OpenSubsonic mechanism | `POST /Users/AuthenticateByName`, then access-token/device authentication | Generated Fan Settings credentials; exact supported auth modes must be probed |
| Browse model | ID3 artists, albums, songs, genres, and music folders | Heterogeneous items filtered to audio, artists, albums, genres, and user views | User collection; its mapping to standard browse calls is not yet documented |
| Paging/search | Offset/count APIs such as `getAlbumList2` and `search3` | Rich `/Items` queries and `/Search/Hints` | Must be measured, especially for large collections |
| Streaming | `stream`/`download`, with optional transcode extensions | Direct/universal audio, download, and transcoding routes | Streaming and downloading are announced |
| Playlists | Standard Subsonic CRUD | Native Jellyfin playlist routes | Create/edit is announced and syncs with Bandcamp web/app |
| Favorites/ratings | Standard endpoints, subject to server support/permissions | User favorite and rating routes | Unverified |
| Playback reporting | `scrobble` or advertised playback-report extension | Session playing/progress/stopped routes | Unverified |
| Lyrics | Plain Subsonic lyrics; structured/synchronized lyrics when advertised | Native audio lyrics route | Unverified |
| Similar/radio | Standard similar/top calls and optional sonic similarity | Similar-item and instant-mix routes | Unverified |
| Libraries | Subsonic music folders | Jellyfin user views/libraries | Likely one collection view; unverified |
| Capability discovery | API version plus public `getOpenSubsonicExtensions`; endpoint/permission failures still need graceful handling | Server info plus supported routes and user permissions | Probe the advertised Subsonic surface and cache the result per source |

### Generic Subsonic

The current Navidrome provider already uses many standard Subsonic calls: ping, music folders,
artists, albums, songs, album lists, search, favorites, ratings, genres, random and similar songs,
playlists, internet radio, cover art, streaming, and scan status. That transport and mapping should be
extracted into a reusable Subsonic/OpenSubsonic module rather than copied.

Navidrome-specific behavior must remain a profile layered on that module. In particular, native
`/auth/login` and `/api/playlist` behavior used for rotating Navidrome tokens and smart-playlist JSON
is not part of generic Subsonic and must never be called for another server.

OpenSubsonic extensions are optional. Naviamp should call the public
`getOpenSubsonicExtensions`, store the negotiated versions in the active provider session, and gate
structured lyrics, playback reporting, sonic similarity, transcoding, and other extension behavior
accordingly. A plain Subsonic 1.16.1 server remains useful without any extension.

### Jellyfin

Jellyfin is not a Subsonic variant. Its login, item model, library selection, streaming decisions,
playback reporting, playlists, favorites, ratings, artwork, lyrics, and instant-mix routes all need a
dedicated common adapter and mappings into Naviamp's existing domain models.

The first Jellyfin slice should favor direct or universal audio playback and a music-specific subset
of the item API. Full device-profile-driven transcoding should follow only after the direct playback,
token renewal/revocation, library filtering, and progress-reporting contracts are stable.

Implementation note (2026-08-07): connection, music browsing/search, original and transcoded
playback, scrubbing, waveforms, instant mixes, favorites, play reporting/recently played, playlist
CRUD and reordering, synchronized lyrics, downloads, and offline playback have been exercised
against Jellyfin 10.11.11 on macOS. Downloads use the authenticated provider session so the saved
connection's TLS policy applies consistently to API, playback, artwork, and downloaded media.
Multiple-library selection and revoked-token recovery have also been verified on macOS. Revoked
sessions produce explicit reconnect guidance while preserving local downloads. A full macOS process
termination and relaunch has verified restoration of the expected screen, saved queue, Jellyfin
session, online playback, downloaded media, and offline playback without another password prompt.
On the iOS simulator, a normally ad-hoc-signed build has verified Keychain-backed connection
restoration, Home and mix loading, all-library artist browsing, direct FLAC playback, waveforms and
scrubbing, favorite round trips, Opus 128 downloads and downloaded playback, and cold process
restoration at the saved track and position. On a physical Pixel 10a, Android has verified secure
connection storage, Home and mix loading, all-library artist browsing, direct FLAC playback,
waveforms and scrubbing, favorite round trips, original-format downloads, downloaded-track
playback, and online cold restoration. Android offline cold restoration previously fell back to a
blank Navidrome form after the connection timeout and did not expose the
Downloads screen until connectivity returned. The shared restoration fix now enters a local-only
session for saved-source reachability failures while still requiring reconnection for explicit
credential, authorization, and TLS failures.

### Bandcamp

Bandcamp announced an open-beta Subsonic implementation on 2026-07-16. The official announcement
only guarantees collection streaming/downloading and playlist creation/editing, and only names
Amperfy, Feishin, and Submariner as supported test clients. It warns that large collections may be
slow.

Bandcamp should therefore be a provider profile backed by the generic Subsonic engine, not a fork of
it. Before enabling product actions, capture authenticated responses for ping, version/extensions,
browse, paging, search, folders, playlists, stream/download, artwork, favorites, ratings, scrobble,
lyrics, and similarity. Unknown capabilities default to unavailable rather than relying on a call to
fail after the user selects an action.

Implementation note (2026-08-07): connection, Collection-folder selection, search, original-stream
playback, playlist creation, and serial playlist additions have been exercised against the live
beta. Bandcamp currently acknowledges playlist replacement requests without reliably applying track
reordering; the same failure is reported by other Subsonic clients. Naviamp therefore permits the
safe append/removal subsets but rejects reorder replacements before sending any mutation. Revisit
that guard after Bandcamp announces or demonstrates corrected `updatePlaylist` behavior. On a
physical Pixel 10a, Android has verified connection restoration, Home and mix loading, Collection
artist search/browse, album loading, MP3 256 kbps playback, waveforms and scrubbing, downloads, true
offline playback, online cold restoration, and loading an existing 31-track playlist. A local
playlist reorder produced one explanatory Bandcamp warning, sent no mutation, and was successfully
undone. The temporary downloaded track was removed after testing. The Android offline fixes were
then verified with a fresh Bandcamp download and two consecutive radio-disabled process restarts:
Naviamp restored the saved source, Downloads access, the Now Playing screen, queue position,
waveform, and persistently cached artwork, and resumed the local file without a provider session.

## Capability Contract Gaps

`ProviderCapabilities` currently covers transcoding, generated radio, favorites, ratings, play
reporting, smart playlists, and sonic similarity. It does not fully describe other UI-visible
features already present on `MediaProvider`, including:

- playlist read/write behavior;
- internet-radio read/write behavior;
- music folders/libraries;
- search and browse variants;
- plain, line-synchronized, and word-synchronized lyrics;
- direct stream, download, and transcoding modes;
- cover art and scan state.

Expand the shared capability model before exposing additional providers. Capabilities should be
fine-grained enough for Core to hide or disable actions intentionally and to explain why a feature is
unavailable. Default empty lists and runtime exceptions are not a sufficient product contract.

## Implementation Sequence

### 1. Common provider catalog and selector

- Add serializable provider-kind IDs and common descriptors.
- Carry provider kind through `ConnectionFormState`, saved/editable connection records, validation,
  and Settings Sync mapping.
- Default existing and legacy profiles to Navidrome.
- Render the shared selectable tiles and provider-specific connection guidance.
- Add common tests for selection, editing, persistence mapping, legacy defaults, and validation.

### 2. Shared multi-provider session router

- Add a common provider-session factory contract and registry/router implementing
  `NaviampCoreProviderSessionPort`.
- Route saved and new connections by persisted provider ID.
- Move active-source selection and provider capability access out of host compositions.
- Keep host wiring limited to secure credentials, HTTP/TLS engines, databases, and native playback.
- Remove every `NavidromeProvider` cast and `latestNavidromeSource` assumption from Android,
  Desktop, and iOS.

### 3. Generic Subsonic/OpenSubsonic provider

- Extract shared request building, authentication, response envelopes, errors, mappings, and standard
  endpoints from the current Navidrome module.
- Negotiate API version and OpenSubsonic extensions.
- Add fixtures and contract tests for original Subsonic plus at least two current OpenSubsonic server
  implementations.
- Retain Navidrome-only login and smart-playlist behavior in a narrow Navidrome layer.

### 4. Jellyfin provider

- Implement authentication/token storage and server identity.
- Map music libraries, artists, albums, tracks, genres, search, artwork, playback, playlists,
  favorites, ratings, progress reporting, lyrics, and instant mixes incrementally.
- Add captured response fixtures and provider contract tests before shared UI actions are enabled.

### 5. Bandcamp profile

- Add the fixed URL guidance and generated-credential copy.
- Run the authenticated endpoint probe against the beta and record the observed capability matrix.
- Enable only verified features through the generic Subsonic engine.
- Preserve Bandcamp playlist semantics and do not assume unsupported server-library administration.

This sequence follows the requested product order. Bandcamp still reuses the generic Subsonic work;
its final enablement is intentionally held until after Jellyfin so the beta can be rechecked closer
to implementation.

## Verification

Each provider needs common contract tests for connection errors, expired/invalid credentials,
capability negotiation, empty and large libraries, paging, stable IDs, mapping failures, stream URL
construction, and unsupported actions. Captured fixtures must remove credentials and private library
data.

Cross-platform acceptance should use the same account/source on Android, Desktop, and iOS and cover
connect/edit/delete, source switching, cold restoration, browse/search, playback/scrubbing, artwork,
downloads, playlists, favorites/ratings, lyrics, radio/similarity, offline cache isolation, and
Settings Sync. Host-specific test work is limited to native secure storage, TLS/network engines,
audio integration, and lifecycle restoration.

## Remaining Acceptance and Compatibility Testing

The following work is intentionally recorded after the Alpha 4 hands-on pass. It is not evidence
that the listed behavior is broken; it identifies combinations and edge cases that have not yet
received enough independent acceptance coverage.

### All providers

- Run the complete provider matrix on Windows and Linux, including connection storage, source
  switching, browse/search, playback and seeking, artwork, playlists, downloads, offline playback,
  and cold restoration.
- Repeat the complete matrix on a physical iPhone or iPad. Current Apple provider acceptance is
  primarily on the iOS simulator; native Keychain behavior also needs release-signed or locally
  signed physical-device coverage.
- Exercise empty libraries, libraries with enough items to require multiple pages, unusually large
  queues and playlists, missing artwork and metadata, duplicate names, deleted remote items, and
  partial or malformed provider responses.
- Verify switching repeatedly among multiple saved providers, including providers sharing a server
  address, then edit, delete, and recreate each connection without leaking credentials, caches,
  downloads, queues, history, artwork, or selected-library state across sources.
- Round-trip Settings Sync with every provider type, selected library/folder, TLS option, and legacy
  saved connection; verify that an older client preserves unknown provider data safely.
- Exercise connection loss and recovery during browse, stream, download, playlist mutation, and
  playback reporting, including retry, cancellation, rate limiting, and server restart behavior.
- Run longer playback sessions through queue transitions, crossfade/gapless modes, backgrounding,
  process death, and network changes while checking reporting, waveform, artwork, and offline
  fallback state.

### Generic Subsonic/OpenSubsonic

- Establish a compatibility baseline against original Subsonic behavior and at least two current
  non-Navidrome OpenSubsonic implementations; the current live acceptance server is not sufficient
  to represent the protocol family.
- Cover salted-token and permitted legacy-password authentication, older API versions, absent or
  malformed extension discovery, folder paging, transcoding differences, and unsupported endpoint
  responses.
- Confirm that Navidrome-only smart-playlist, canonical-ID, and sonic behavior never leaks into a
  generic Subsonic session when a server advertises only standard or unrelated extensions.

### Jellyfin

- Repeat revoked/invalid-token recovery on Android and iOS. It is covered by common fixtures and was
  accepted on macOS, but mobile acceptance should verify reconnect guidance, preserved downloads,
  and successful recovery without destructive local cleanup.
- Exercise restricted Jellyfin users and library permissions, no-music-library accounts, multiple
  selected libraries with overlapping names, library removal, and server-side item deletion.
- Expand real-server playlist mutation coverage on iOS and test simultaneous or out-of-band edits,
  duplicate occurrences, large playlists, and permission failures.
- Test additional source codecs and Jellyfin transcode containers beyond the direct formats and
  Opus settings already accepted, including mid-stream server failure and transcode-session cleanup.
- Verify favorite changes for artists and albums on every host. Track favorites are accepted;
  Naviamp's separate star/rating action remains intentionally unavailable until a reliable Jellyfin
  mapping is selected and tested.

### Bandcamp beta

- Test an empty collection and a substantially larger collection for search completeness, folder
  selection, pagination, response time, throttling, and retry behavior.
- Recheck playlist create, rename, append, removal, deletion, duplicate-track, and concurrent web
  edit behavior as Bandcamp changes the beta. Keep reorder replacement blocked until the server can
  be shown to apply it reliably.
- Probe favorites, ratings, play reporting, lyrics, similarity/radio, artwork variants, and format
  negotiation periodically. These features remain unavailable unless the beta advertises and
  reliably implements them.
- Verify credential regeneration, leading/trailing whitespace handling, invalid credentials, and
  revoked credentials on every host without logging or retaining generated secrets incorrectly.
- Confirm whether Bandcamp continues to cap collection streams and downloads at MP3 256 kbps and
  update quality presentation if the beta later exposes lossless purchases.

### Release packaging

- Verify Alpha 4 artifacts produced by the tag workflow: signed Android APK, macOS ZIP/DMG, Windows
  ZIP/MSI/EXE, Linux ZIP/DEB/RPM, and unsigned iOS IPA. Install and launch each artifact where a
  representative machine or signing environment is available.
- Confirm that every package reports `v2.0.0-alpha.4`, includes its required native BASS inventory,
  and can upgrade an Alpha 3 data set without losing saved sources, queues, settings, or downloads.

## Open Questions

- Which generic servers should form the compatibility baseline? A practical initial set is original
  Subsonic behavior, Navidrome, and one non-Navidrome OpenSubsonic implementation.
- Should generic Subsonic offer an optional detected-server hint after ping while preserving the
  user's explicit provider choice?
- Which Jellyfin direct/universal audio parameters provide reliable first-slice playback without a
  full device profile?
- Does Jellyfin expose a stable token refresh model, or should an invalid token require a fresh
  credential exchange?
- Which Bandcamp endpoints and OpenSubsonic extensions are present in the beta, and what rate and
  paging behavior appears with a large collection?
- Which third-party logos may be redistributed in Naviamp packages, and under what attribution or
  modification rules?

## Primary Sources

- [Original Subsonic REST API](https://www.subsonic.org/pages/api.jsp)
- [OpenSubsonic overview](https://opensubsonic.netlify.app/docs/)
- [OpenSubsonic changes](https://opensubsonic.netlify.app/docs/opensubsonic-changes/)
- [OpenSubsonic extensions](https://opensubsonic.netlify.app/docs/extensions/)
- [`getOpenSubsonicExtensions`](https://opensubsonic.netlify.app/docs/endpoints/getopensubsonicextensions/)
- [Bandcamp Subsonic beta announcement](https://blog.bandcamp.com/2026/07/16/discover-improvements-and-subsonic-implementation/)
- [Jellyfin server source and API controllers, v10.11.11](https://github.com/jellyfin/jellyfin/tree/v10.11.11/Jellyfin.Api/Controllers)
- [Jellyfin `UserController` authentication routes, v10.11.11](https://github.com/jellyfin/jellyfin/blob/v10.11.11/Jellyfin.Api/Controllers/UserController.cs)
