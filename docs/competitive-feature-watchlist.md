# Competitive Feature Watchlist

> **Temporary tracking document.** Delete this file after every candidate below has either been implemented, moved to a durable product roadmap, or explicitly rejected, and the recurring scan is no longer needed. Do not leave an empty or fully resolved copy in the repository.

This document tracks useful feature requests seen in other self-hosted music players. It is a source of product signals, not a commitment to copy another client. Any feature selected for Naviamp must fit its Navidrome/OpenSubsonic-first direction, use real provider capabilities where applicable, and preserve consistent desktop and Android behavior where the platforms allow it.

## Status Key

- `Candidate`: Worth product and technical investigation.
- `Investigating`: Provider support, UX, and implementation scope are being evaluated.
- `Planned`: Accepted and moved into an implementation plan.
- `Implemented`: Shipped and verified.
- `Rejected`: Deliberately declined, with the reason recorded here.
- `Moved`: Transferred to a durable roadmap or another tracker.

## Strong Candidates

### Release-Type Discography Sections and Album Layouts

- **Status:** Implemented
- **Sources:** [Navic #291](https://github.com/ssalggnikool/Navic/issues/291), [OpenSubsonic AlbumID3WithSongs](https://opensubsonic.netlify.app/docs/responses/albumid3withsongs/)
- **Idea:** Separate albums, EPs, singles, live releases, compilations, remixes, and soundtracks on artist pages. Display release-type and explicit-content indicators where useful. Let the user choose whether album collections are presented as the current list or as an album-art grid.
- **Why it fits:** Large discographies become substantially easier to browse, OpenSubsonic exposes structured `releaseTypes` metadata, and a visual grid makes cover-oriented browsing faster without removing the more detailed list presentation.
- **Experience settings:** Add an Albums hub under **Settings > Experience**. Put `List` or `Grid` presentation on its own subpage, provide sorting by release year in either direction or alphabetically by title, and let users choose whether albums are separated into release-type sections. Preserve `List` and release year oldest-first as the defaults, allow users to return to one combined Albums section, and apply the preferences consistently on desktop and Android wherever the same album collection UI is used.
- **Implementation notes:** Naviamp consumes OpenSubsonic `releaseTypes` and explicit-status metadata with clean fallbacks when either field is absent. Artist discographies can be grouped into release-type sections or shown as one Albums collection, presented as a list or responsive cover grid, and sorted by release year in either direction or by title. The Experience settings and presentation behavior are implemented consistently on desktop and Android and covered by domain, provider, UI-mapping, and settings-persistence tests.

### Download Activity, Progress, Cancellation, and Retry

- **Status:** Implemented
- **Sources:** [Tempus #755](https://github.com/eddyizm/tempus/issues/755), [Yuzic #131](https://github.com/eftpmc/yuzic/issues/131), [Navic #328](https://github.com/ssalggnikool/Navic/issues/328)
- **Idea:** Add active and recent download jobs to the Downloads view with per-item progress, aggregate progress, cancellation, failure details, and retry.
- **Why it fits:** Naviamp already downloads tracks, albums, and playlists, but visible job state would make longer operations understandable and trustworthy.
- **Implementation notes:** The shared sequential download loop now emits structured job and per-track updates used identically by desktop and Android. Both Downloads views show aggregate progress plus the current or failed track, support overlapping operations with independent cancellation handles, clear successful jobs when complete, preserve failed or cancelled jobs for retry, and retry only the unfinished remainder. Downloaded rows prioritize track and artist, show stored codec and quality including the actual output format for transcodes, provide configurable Play/Remove swipe actions, and retain removal in the overflow menu. The offline dashboard is collapsed by default. Download and audio-cache locations are configurable on both platforms; Android exposes app-specific internal, device, and removable-storage locations, while desktop supports arbitrary writable folders. Cache location remains a first-level Audio Cache option. Cancellation remains safe because audio is written to a temporary file and registered only after an atomic move; cancelled partial files are deleted. Progress is intentionally presented in the Downloads view rather than competing with Naviamp's playback media notification; a dedicated foreground download notification remains a possible follow-up if downloads are moved into durable background work.

### Keep-Downloaded Playlists, Favorites, and Smart Playlists

- **Status:** Implemented
- **Sources:** [Tempus #273](https://github.com/eddyizm/tempus/issues/273), [Navic #329](https://github.com/ssalggnikool/Navic/issues/329), [Musly #209](https://github.com/dddevid/Musly/issues/209)
- **Idea:** Allow a playlist, favorites collection, or smart playlist to be marked for offline synchronization. Download newly added tracks incrementally and define a clear policy for tracks later removed from the collection.
- **Why it fits:** This connects Naviamp's existing favorites, smart playlists, downloads, and offline mode into a useful automatic workflow.
- **Investigation notes:** Include Wi-Fi/mobile-data constraints, storage-budget behavior, background execution limits, reconciliation rules, and opt-in cleanup.
- **Implementation notes:** Playlists and smart playlists expose a Keep downloaded toggle, while favorite tracks can be enabled from the Downloads page menu. Policies and membership snapshots are small, source-scoped local records; media remains API-first. Reconciliation downloads only missing tracks through the existing sequential, mobile-data-aware job pipeline and runs after relevant collection changes or a manual Downloads refresh. Stopping a policy keeps existing files by default. Automated ownership and overlapping-policy membership are tracked separately so future opt-in cleanup can remove only files Naviamp downloaded for synchronization and never files still required by another collection or downloaded manually.

### Artist-Balanced Shuffle

- **Status:** Candidate
- **Source:** [Tempus #519](https://github.com/eddyizm/tempus/issues/519)
- **Idea:** Choose artists uniformly before choosing tracks so artists with large discographies do not dominate a library shuffle.
- **Why it fits:** This offers a genuinely different discovery mode and fits Naviamp's existing Library Radio and Radio DJ tuning better than another global shuffle control.
- **Investigation notes:** Naviamp already supports broad artist spreading by interleaving artists. Compare that behavior with true uniform artist selection and album-balanced selection, define how multi-artist tracks contribute, and make the chosen mode visible.

### Structured Multi-Artist Metadata and Navigation

- **Status:** Candidate
- **Sources:** [Tempus #533](https://github.com/eddyizm/tempus/issues/533), [Navic #410](https://github.com/ssalggnikool/Navic/issues/410)
- **Idea:** Display every credited artist and make each artist independently navigable rather than treating the display credit as one opaque string.
- **Why it fits:** Structured artists improve track details, queues, search results, album pages, and navigation while retaining the current display string as a fallback.
- **Investigation notes:** Verify OpenSubsonic artist arrays on every endpoint Naviamp consumes and decide how contributors versus primary artists should appear.

### Enhanced Lyric Cue Rendering, Translations, and Pronunciations

- **Status:** Investigating
- **Sources:** [Navic #440](https://github.com/ssalggnikool/Navic/issues/440), [OpenSubsonic getLyricsBySongId](https://opensubsonic.netlify.app/docs/endpoints/getlyricsbysongid/)
- **Idea:** Render word- or syllable-level highlighting from enhanced lyric cues and present translations and pronunciations when supplied.
- **Why it fits:** Naviamp already requests capability-gated enhanced lyrics, models and caches cue-level data, and retains provider, embedded, downloaded, synchronized, and unsynchronized fallbacks. The remaining gap is carrying the enhanced data through the shared UI and presenting it well.
- **Investigation notes:** Preserve current line-synced and unsynced rendering, define cue interpolation and scrolling behavior, and decide how alternate lyric kinds and languages are selected without overcrowding Now Playing.

## Smaller Candidates

### Duplicate Warning When Adding to a Playlist

- **Status:** Candidate
- **Source:** [Musly #211](https://github.com/dddevid/Musly/issues/211)
- **Idea:** Summarize duplicates before adding tracks to a playlist, such as `3 already present; add the other 7?`, instead of silently skipping them.
- **Notes:** Naviamp currently deduplicates the request and adds only tracks missing from the destination. First decide whether intentional duplicates are a supported playlist behavior. If they are, offer `Add anyway`; otherwise retain deduplication and present one bulk summary rather than a dialog for every track.

### Runtime Server Endpoint Failover

- **Status:** Candidate
- **Source:** [Musly #187](https://github.com/dddevid/Musly/issues/187)
- **Idea:** Safely switch to another configured endpoint when the active endpoint becomes unreachable after login, including transitions between LAN, WAN, VPN, and proxy addresses.
- **Notes:** Naviamp already supports a primary URL plus prioritized fallback URLs and selects a reachable endpoint while connecting. This candidate is only for runtime health checks and failover. Investigate HTTPS identity, custom certificates, authentication reuse, cache identity, and preventing disruptive switching during active playback or downloads.

### A-Z Fast Scrolling for Large Libraries

- **Status:** Candidate
- **Source:** [Musly #204](https://github.com/dddevid/Musly/issues/204)
- **Idea:** Add an alphabet index or fast-scroller for very large album and artist collections.
- **Notes:** Confirm accessibility, touch behavior, desktop pointer behavior, and compatibility with filtering and non-Latin titles.

### Full-Catalog and Result-Set Playback Actions

- **Status:** Candidate
- **Sources:** [Navic #406](https://github.com/ssalggnikool/Navic/issues/406), [Chora #41](https://github.com/CraftWorksMC/Chora/issues/41), [Yuzic #134](https://github.com/eftpmc/yuzic/issues/134)
- **Idea:** Add missing `Play all` and `Shuffle all` actions for an artist's full catalog and complete search result sets.
- **Notes:** Naviamp's Library Radio already provides random tracks from the full library, so do not create a literal full-library queue. Use paging or server-generated selection for large result sets so the action does not block the UI or create an unbounded in-memory queue.

## Recommended Sequencing

- Implement download activity and persistent job state before keep-downloaded playlists, favorites, or smart playlists.
- Add structured multi-artist metadata before using credited artists in artist-balanced selection or navigation.
- Treat enhanced lyrics as a shared-UI completion project because provider capability detection, cue models, and caching already exist.
- Release-type discography sections and the list/grid album preference can form one cohesive artist-browsing feature, while still allowing the layout preference to be reused by other album collections later.

## Monitored but Currently Lower Priority

Keep watching these themes, but do not prioritize them without a stronger Naviamp use case or a clear maintenance plan:

- Extensive theming and per-control Now Playing layout customization.
- UPnP, DLNA, and Chromecast output.
- WearOS, Android TV, and additional platform-specific clients.
- External music acquisition and downloader integrations.
- Octo-Fiesta-specific behavior and other proxy-specific conventions.
- Generic artist statistics dashboards backed by additional third-party accounts.
- Broad UI imitation that does not solve a demonstrated Naviamp usability problem.

## Already Covered or Substantially Overlapping

Before adding a new candidate, check whether Naviamp already covers it. Current overlap includes:

- Sonic autoplay, Sonic Mix, Sonic Path, track radio, related tracks, and continued playback.
- Android Auto browsing and playback.
- Offline playback, a Downloads view, download quality, storage budgets, and mobile-data controls.
- Favorites and smart playlists, including favorite-based templates.
- Playback-session and queue restoration.
- Synced lyrics and configurable lyric sources.
- Artist biographies supplied by the provider.
- Playlist sorting and creation of deduplicated playlist copies.
- Primary and prioritized fallback server URLs with connection-time endpoint selection.
- Library Radio for random playback from the full library.
- Streaming and download transcoding when the provider reports support.
- Audio cache limits, ReplayGain, gapless playback, crossfade, and sample-rate controls.

An overlapping request may still reveal a missing refinement. Record that narrower gap rather than adding the entire feature again.

## Issue Sources to Scan

Review open enhancement or feature requests from these projects:

- [Navic enhancements](https://github.com/ssalggnikool/Navic/issues?q=is%3Aissue%20state%3Aopen%20label%3Aenhancement)
- [Tempus enhancements](https://github.com/eddyizm/tempus/issues?q=is%3Aissue%20state%3Aopen%20label%3Aenhancement)
- [Castafiore features](https://github.com/sawyerf/Castafiore/issues?q=is%3Aissue%20state%3Aopen%20label%3AFeatures)
- [Yuzic issues](https://github.com/eftpmc/yuzic/issues)
- [Chora enhancements](https://github.com/CraftWorksMC/Chora/issues?q=is%3Aissue%20state%3Aopen%20label%3Aenhancement)
- [Musly enhancements](https://github.com/dddevid/Musly/issues?q=is%3Aissue%20state%3Aopen%20label%3Aenhancement)
- [Wavio enhancements](https://github.com/Joel-Mercier/wavio/issues?q=is%3Aissue%20state%3Aopen%20label%3Aenhancement)

## Recurring Review

- **Cadence:** Run weekly while product work is active; bi-weekly is acceptable during maintenance periods.
- **Scope:** Review issues created or materially updated since the previous scan. Also check whether previously tracked source issues were closed, clarified, or implemented upstream.
- **Triage questions:**
  1. Does this solve a demonstrated Naviamp problem?
  2. Is the behavior supported by Navidrome/OpenSubsonic or an appropriate platform API?
  3. Does Naviamp already implement the feature or its underlying need?
  4. Can it share domain and UI behavior across desktop and Android?
  5. Is the long-term maintenance cost proportionate to the value?
- **Action:** Add only actionable ideas. Link the source, explain the Naviamp-specific value, record provider/platform dependencies, and set a status.
- **Duplicates:** Merge repeated requests into one Naviamp candidate and list all useful source links.
- **No change:** Record the scan date even when nothing is added so the cadence remains visible.

## Scan Log

| Date | Projects reviewed | Result |
| --- | --- | --- |
| 2026-07-14 | Navic, Tempus, Castafiore, Yuzic, Chora, Musly | Created the initial candidate list. Wavio was added for the next recurring scan. |

## Removal Checklist

Delete this document when all of the following are true:

- Every candidate is `Implemented`, `Rejected`, or `Moved`.
- Any accepted unfinished work has been transferred to a durable roadmap or implementation tracker.
- The source list and recurring scan are no longer serving an active product-review need, or their ownership has moved elsewhere.
- Useful decisions and rejection rationale have been preserved in the destination tracker, code, or commit history.
