# Plexamp 4.50.3 Opportunity Audit

- **Status:** Idea
- **Reviewed:** 2026-08-31
- **Primary source:** [Plexamp v4.50.3: Ready or not](https://forums.plex.tv/t/plexamp-v4-50-3-ready-or-not/942338)
- **Supporting history:** [Plexamp seamless-download requests](https://forums.plex.tv/t/feature-request-plexamp-seamless-downloads/718064)

## Purpose

Plexamp 4.50.3 is useful competitive evidence for Naviamp because its headline product change is a
substantial offline and downloads overhaul. The important lesson is not to copy Plex-specific UI or
infrastructure. It is to make locally available music behave like the user's real library instead
of exposing downloads as an isolated list with fewer navigation and search capabilities.

This document records opportunities only. It is not an implementation plan or a commitment to
match every Plexamp change. The upstream post describes beta software, so its detailed behavior and
platform support may still change.

## Upstream Themes

The material product themes in the Plexamp release and its discussion are:

- A richer offline experience in which downloaded music remains useful through familiar library
  navigation rather than only through a separate downloads silo.
- Better search and browsing while the server or network is unavailable.
- A reworked download system with more observable progress and failure information.
- Greater convergence of the mobile and Desktop experiences, accompanied by appearance, Home, and
  platform-compatibility changes.
- Explicit download diagnostics and telemetry intended to make failed transfers supportable.

The discussion also contains requests that are not release features, notably identifying the
originating library in cross-library search results and editing server metadata from the player.
Those should be evaluated independently rather than attributed to Plexamp 4.50.3.

## Naviamp Baseline

Naviamp already has much of the machinery needed for a stronger offline product:

- `NaviampCoreDownloadsController` owns download actions, visible jobs, cancellation, retry,
  storage limits, and keep-downloaded reconciliation in shared Core.
- `DownloadJobs.kt` models per-item queued, downloading, completed, failed, and cancelled states.
- `NaviampCoreConnectionController` restores a saved source into an offline state when an eligible
  connection failure occurs.
- Playback resolution can prefer downloaded files, use cached audio, stream from the provider, and
  fall back to a downloaded file when the provider stream fails.
- Keep-downloaded policies already cover favorites, regular playlists, and smart playlists.
- Offline provider mutations can be retained and replayed after reconnection.
- Download quality, mobile-data permission, storage budget, storage location, cache behavior, and
  downloaded-versus-server playback preference are shared settings.

The visible experience is less complete than this foundation:

- `NaviampDownloadsContent` renders downloaded media as one flat track list.
- `offlineTrackSearchResults` returns only tracks and matches only title, artist name, and album
  title.
- `offlineModeEnabled` is persisted and exposed by Settings, but no non-Settings production caller
  currently consumes it. The existing control therefore does not yet select an offline catalog or
  search policy.
- Download jobs are held in the controller's in-memory list and do not survive process death or an
  application restart.
- Refresh prunes missing files, but there is no complete integrity, partial-file, or corruption
  repair workflow visible in the shared controller.

## Candidate Opportunities

### P0: Seamless Offline Library

Keep the normal Home, Albums, Artists, Playlists, Search, and Now Playing surfaces available when
the active source cannot be reached. Filter or mark content according to local playability instead
of redirecting the user into a separate, reduced application mode.

Core should own the availability projection and navigation behavior. Hosts should contribute only
connectivity signals and unavoidable storage effects.

### P0: Rich Local Catalog

Derive Albums, Artists, Playlists, Tracks, and optionally Genres from downloaded media and cached
metadata. Preserve the collections through which each track was downloaded so the same audio file
can appear in several albums or playlists without duplicating its bytes.

This likely needs explicit collection-membership records for ordinary downloads. Existing
keep-downloaded policy membership is useful but does not represent every manual album, artist, or
playlist download as a durable local browse entity.

### P0: Persistent and Resumable Download Jobs

Persist the download request, item states, quality, destination, completed-byte information, and
safe retry metadata in shared storage. On restart, Core should reconcile persisted jobs with files
on disk and resume, retry, complete, or explain them deterministically.

Do not make resume claims until the provider and byte-store boundaries can prove whether partial
HTTP transfers can continue safely. Restarting only the incomplete track is still valuable when
range requests are unavailable.

### P0: Complete Offline Search

Search locally available albums, artists, playlists, genres, and tracks. Results should use the
normal shared result models and navigation destinations so selecting an offline album or artist
does not lead back to a network-only page.

The existing track matcher is a useful low-level building block, but it is not currently wired to
the persisted offline-mode preference and does not provide a complete offline search experience.

### P1: Automatic Local-First Connectivity Policy

Prefer a valid local file automatically, stream when appropriate, and continue through transient
server loss without asking the user to predict network conditions. A manual local-only override
can remain useful for conserving data, but ordinary offline recovery should not depend on finding a
Settings toggle in advance.

Define Core-owned states for connected, degraded, explicitly local-only, and disconnected with
local content. Avoid platform-specific interpretations of those states.

### P1: Item-Level Retry, Integrity, and Repair

Expose the failed item, sanitized failure category, retryability, and retry action. Add verification
for missing, zero-length, truncated, mismatched, and unreadable files, plus stale database rows and
orphaned files created by interrupted transfers.

Repair must be targeted. It should not delete unrelated downloads or user data to recover a single
failed job.

### P1: Expanded Download Subscriptions

Extend keep-downloaded behavior to candidates such as:

- Albums and artists.
- Genres or library folders.
- Recently added music with a configurable limit.
- A bounded number of recent or highest-rated favorites.
- Generated mixes when they have a durable provider-backed identity.

Every subscription needs explicit eviction, ordering, storage-budget, provider-capability, and
reconnection rules. Avoid silently turning an unbounded server collection into an unbounded device
download.

### P1: Offline Home

Compose useful local shelves such as Downloaded Recently, Offline Favorites, Downloaded Albums,
Downloaded Playlists, and Continue Listening. Where a normal Home section has no locally playable
content, hide it or explain its unavailability without replacing the entire Home experience.

Offline radio or discovery should be considered only where locally available data can produce an
honest result. Do not label a small downloaded subset as equivalent to a full server-generated
radio station.

### P2: Download Planning

Before a large transfer, show the track count, selected quality, estimated new bytes, duplicates
already stored, available storage, and applicable network policy. The estimate must distinguish
logical collection membership from physical audio bytes so one track referenced by multiple
playlists is not counted repeatedly.

### P2: Private Diagnostics

Make download failures supportable with local and explicitly exportable diagnostics covering:

- Job and item state transitions.
- Sanitized HTTP and provider failures.
- Throughput, elapsed time, retry counts, and database timing.
- Storage destination, free-space, write, rename, and integrity failures.
- Reconciliation decisions after restart.

Do not copy content-identifying telemetry by default. Track titles, filenames, stream URLs,
credentials, tokens, and listening behavior are not necessary for aggregate product telemetry. A
user-requested diagnostic export may include sanitized item labels only when clearly disclosed.

### P2: Vehicle and Multi-Source Parity

Expose locally available albums, artists, playlists, favorites, queue actions, and search through
the shared vehicle catalog used by Android Auto and a future CarPlay adapter. When search spans
multiple Naviamp sources, add a source badge where otherwise-identical results would be ambiguous.

## Ideas Not Recommended for Copying

- Do not retire Naviamp Connect or a future headless/receiver option merely because Plexamp directs
  headless users to another product.
- Do not accept loss of downloads or user settings as a normal upgrade consequence. Migration and
  rollback acceptance should explicitly preserve both wherever formats remain compatible.
- Do not make content-identifying transfer telemetry the default price of reliable downloads.
- Do not create separate Android, Desktop, and iOS offline catalogs. The catalog, policies, state,
  actions, navigation, reconciliation, and user-facing statuses belong in shared Core.
- Do not add Plex-specific concepts such as Plex libraries, Plex Pass gates, or Plex server
  telemetry when the transferable user need is provider-neutral.

## Proposed Implementation Sequence

1. Define a shared offline availability and local-catalog model covering tracks, albums, artists,
   playlists, genres, and collection membership.
2. Add shared-storage queries and repositories for the projections, preserving the current
   source-scoped download records and migration baseline.
3. Route the normal catalog, details, Home, and search controllers through local projections when
   disconnected or explicitly local-only.
4. Replace the flat-only Downloads experience with collection views while retaining storage,
   activity, diagnostics, and destructive management controls.
5. Persist download jobs and reconcile them safely across restart, network changes, storage
   changes, cancellation, and provider reconnect.
6. Add integrity checking, item-level repair, planning estimates, and private diagnostic export.
7. Expand keep-downloaded subscriptions only after the shared collection and eviction contracts
   are stable.
8. Verify the same Core behavior on Android, Desktop, and iOS before adding or changing vehicle
   host adapters.

## Acceptance Shape

A promoted implementation should prove at minimum:

- Cold launch without the server still reaches a usable locally backed Home and Library.
- Offline album, artist, playlist, genre, and track search opens locally backed detail pages.
- A single audio file referenced by multiple collections occupies physical storage once.
- Download jobs and completed items recover correctly after forced process termination.
- Failed items can be diagnosed and retried without re-downloading successful items.
- Corrupt, partial, missing, and orphaned files are reported and repaired without broad deletion.
- Reconnection refreshes subscribed collections and replays pending provider actions without
  duplicating downloads or reports.
- Storage and network limits remain enforceable during planning, transfer, resume, and repair.
- Android, Desktop, and iOS compile against and consume the same shared product behavior.

## Promotion Recommendation

Promote **Seamless Offline Library** as the headline initiative. Naviamp's playback resolver,
download storage, keep-downloaded policies, pending provider actions, and offline connection
restoration make this primarily a shared catalog, navigation, persistence, and presentation
project rather than a ground-up download rewrite. Persistent jobs should be the second slice so the
new experience remains dependable under real mobile lifecycle and connectivity conditions.
