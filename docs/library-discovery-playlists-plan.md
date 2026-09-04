# Library, Discovery, and Playlist Features Plan

**Branch:** `feature/library-discovery-playlists`

**Started:** 2026-09-04

**Status:** Active

This plan promotes four approved items from the follow-up backlog into active development. Product
behavior, state, navigation, and UI belong in shared Core code. Platform modules may contain only
the smallest unavoidable native integration. Every new user-facing string must be defined in the
shared string resources and in each maintained translation.

## Delivery order

1. Switchable Complete Library Views
2. Expanded Artist Discography Sections
3. Track Membership in Playlists
4. Favorite Artists Home Section

Each feature must receive focused common tests before it is considered complete. The branch must
also pass the shared Android, Desktop/JVM, and iOS compilation/test gates, followed by hands-on phone
and Television coverage where applicable.

## Switchable Complete Library Views

- [x] Replace the artist-only Library with one shared selector for **Artists**, **Albums**, and
  **Songs**.
- [x] Give each view independent query, paging, refresh status, and scroll restoration.
- [x] Add explicit per-view focus restoration and Back-navigation acceptance coverage.
- [x] Use the provider-neutral artist, album, and track paging/search contracts and reject stale
  responses after a source, query, or selected-view change.
- [x] Render artists and albums with shared collection rows and songs with standard shared track
  rows and actions.
- [x] Make search labels, empty states, A-Z navigation, loading, load-more, and refresh behavior
  accurately describe the selected catalog type.
- [ ] Show a translated loading indicator when an Album or Song quick-index letter requires a
  server-backed page that has not loaded yet. Keep the selected letter visible and prevent
  duplicate jump requests until the load succeeds or fails.
- [x] Provide deterministic keyboard and D-pad selector behavior, Back behavior, accessible state
  labels, and per-view focus restoration without creating a Television-only Library.
- [ ] Cover the common controller and shared UI on phone, Desktop, iOS, and Television, including
  large libraries and 720p, 1080p, and native 4K acceptance.

## Expanded Artist Discography Sections

- [x] Add **Mixtapes** to the shared release classifier using provider metadata and known synonyms,
  never title inference; retain Albums, EPs, Singles, Live Releases, Compilations, Remixes,
  Soundtracks, and Other Releases.
- [x] Define a provider-neutral, source-scoped discography contract that distinguishes primary
  releases from albums and tracks where the artist is a contributor.
- [x] Complete provider coverage: Jellyfin uses a stable-ID provider query, while providers without
  a reverse-credit query use the shared-storage credit index keyed by source and stable artist ID.
- [x] Define Appears On inclusion and de-duplication for featured tracks, compilations,
  various-artists releases, roles, aliases, missing IDs, and releases also classified as primary.
- [x] Present appearance albums normally and list their matching credited tracks directly in
  **Appears On**, so isolated credits and the exact matching tracks remain visible.
- [ ] Keep loading, empty, error, paging, section ordering, and navigation in Core, with shared UI
  and large-library/Television coverage.

## Track Membership in Playlists

- [x] Define a source-scoped provider-neutral membership query with explicit loading, unavailable,
  and failure states, backed by a bounded Core loader/cache or optional reverse-membership provider
  capability.
- [x] Add one Core editor and coordinator reused from current-track and queue-item menus on every
  platform.
- [x] Show existing membership and allow multi-select additions and removals, then apply only the
  membership diff without changing unrelated tracks or ordering.
- [x] Remove every occurrence of the selected media identity unless a later UI explicitly offers
  occurrence-level removal.
- [x] Reconcile authoritative provider state after saving; keep the editor open during work and
  report partial failures per playlist without discarding successful mutations.
- [x] Handle duplicates, unavailable/deleted playlists, smart playlists, concurrent authoritative
  re-reads, stale responses, source changes, empty collections, and a 100-playlist safety bound.
- [ ] Hide or honestly disable remote membership editing until Connect negotiates and routes an
  explicit capability for the playback device's active source.
- [ ] Verify touch, pointer, keyboard, TV remote, Apply/Cancel, Back, accessibility, and focus
  restoration.

## Favorite Artists Home Section

- [x] Add bounded native favorite-artist queries for Navidrome and Jellyfin, hide the Home section
  when artist favorites are unsupported, and provide deterministic name ordering.
- [x] Add a source-scoped local fallback that preserves a stable first-observed favorite timestamp
  when providers omit one and remains available after a provider lookup failure.
- [ ] Add deterministic date-favorited/date-last-played ordering when timestamps are missing or equal.
- [x] Add a source-scoped artist-radio last-played value/query to shared storage, consolidating the
  change into the next migration after the current release baseline.
- [x] Record last played only after successful Artist Radio, or successful track-seeded radio when
  the stable artist ID belongs to a currently favorited artist.
- [x] Cover stable artist attribution, unfavorited artists, failed/cancelled launches, and
  injected-clock behavior in common tests.
- [x] Add shared Home presentation, translated section labeling, and Artist Detail navigation.
- [ ] Add persisted sort selection for name, date favorited, and date last played.
- [ ] Verify source switching, favorite changes, restart persistence, settings-sync classification,
  navigation, and shared phone/Desktop/iOS/Television rendering.

## Release gate

- [ ] All new visible copy is present in shared string resources and maintained translations.
- [ ] No platform production file contains portable product behavior.
- [ ] Common behavior and failure tests pass.
- [ ] Android, Desktop/JVM, and iOS targets compile and their relevant suites pass.
- [ ] Phone and Television interaction testing is recorded.
- [ ] Release notes and the required GitHub Announcement are prepared only when this work ships.

## Hands-on validation log

- **2026-09-04 — Pixel 10a:** The physical-phone pass loaded real Artists, Albums, and Songs
  catalogs; verified view-specific search labels, a song-only `Dear Mama` query, independent query
  state, and restoration of that Songs query after visiting Albums. The 2Pac detail screen loaded
  shared Discography, Albums, Compilations, and Top Tracks sections. The current-track playlist
  membership editor loaded the server's editable playlists, allowed a local selection, and Cancel
  discarded it without applying a provider mutation. Apply/removal and queue-origin interaction
  remain for a disposable playlist test.
- **2026-09-04 — Android schema check:** A clean isolated install started with migration 24 and no
  SQLite/runtime errors. The existing `v2test` database is already at schema 25 from earlier
  unreleased branch work but lacks this branch's `favorite_artist_activity` table; its data was
  left untouched. Favorite Artists phone acceptance therefore remains pending on a clean install
  with provider credentials or an explicitly approved reset of that test database.
- **2026-09-04 — Pixel 10a UI follow-up:** Installed the shared 8% font-scale experiment and
  confirmed representative Library labels grew by the expected amount relative to the preceding
  build. Home once again rendered its provider-backed sections even though the preserved `v2test`
  database still lacks the optional favorite-artist activity table. The 2Pac artist page showed
  Top Tracks before Discography, with Albums and Compilations following it. The missing-table error
  remains intentionally contained; Favorite Artists persistence acceptance is still pending under
  the schema-check conditions above. A subsequent pass confirmed Library Songs sort by title as
  pages load and selecting `All Mixed Up` starts playback from that catalog. On the 17-track
  `Greatest Hits ’93–’03` album, one- and two-digit track labels retained their periods and shared
  the same right edge. Playback was paused after validation.
- **2026-09-04 — Pixel 10a catalog-ordering follow-up:** Library Songs now uses Navidrome's one
  globally title-sorted catalog across the selected libraries, matching the server view's quoted,
  punctuation-prefixed, and numbered opening titles. Albums use the same global paging model and
  retain native album-artist/year metadata. Direct letter-offset lookup was verified with Songs
  `S` (`S.O`, `S.O.S.`, `Sabbra Cadabra`) and Albums `M` (`MACHINA/the machines of God`,
  `Mack Daddy`, `The Mack of the Century…`). Track menus now show the translated labels **Play
  After Current Group** and **Play Immediately Next**. Focused provider/Core tests, the full
  Desktop/JVM suite, Android assembly, and affected iOS simulator compilations passed.
- **2026-09-04 — Library focus and Back coverage:** Library now retains separate shared viewport
  and focused-row state for Artists, Albums, and Songs while detail routes temporarily replace the
  catalog. Shared Compose acceptance covers per-view restoration, a detail/Back round trip,
  translated selector state, and deterministic left/right/down keyboard navigation. The Pixel 10a
  exposed the three selectors with the selected Artists state. On the 1080p Television emulator,
  Tab entered at Artists, D-pad Right focused Albums, Center selected it, and D-pad Down focused
  the album-scoped search field. The complete shared UI JVM suite and Android, Desktop, and iOS
  simulator compilation passed.
- **2026-09-04 — Artist discography provider coverage:** Jellyfin advertises its native stable-ID
  reverse-credit query. Providers without that capability now use a source-scoped shared-storage
  artist-credit index populated during library sync; stale credits are replaced on track updates,
  and primary releases are excluded from fallback appearances. Focused and full storage,
  presentation, Jellyfin, and Navidrome JVM suites passed, together with Android, Desktop, and
  affected iOS simulator compilation.
- **2026-09-04 — Appears On reconciliation:** Core now treats the native reverse query or shared
  credit index as the authority for credited membership, so role labels, aliases, and missing
  mapped IDs do not incorrectly discard a returned track. It removes duplicate media identities,
  excludes releases already owned by a primary section, retains standalone credited tracks, and
  only shows appearance albums backed by an included track. Shared policy, controller, and
  Jellyfin regressions cover compilation/Various Artists, alias, missing-ID, duplicate, standalone,
  and primary-overlap cases. Full affected JVM suites and Android, Desktop, and iOS simulator
  compilation passed; the final build was preserve-installed on the Pixel 10a and started without
  AndroidRuntime or SQLite errors.
- **2026-09-04 — Library quick-index paging correction:** Album and Song letter selections no
  longer scroll within the stale currently loaded page. Core replaces the catalog with the
  provider-backed page beginning at the first title at or after the requested letter, publishes a
  view-scoped jump completion, and only then lets shared UI position the list. Navidrome uses its
  native globally sorted catalog, Jellyfin now binary-searches its server-sorted catalog, and the
  provider-neutral fallback resets to the beginning before paging forward so backward jumps remain
  correct. Full affected Core UI/presentation/domain and provider JVM suites passed, together with
  Android, Desktop, and iOS simulator compilation. The build was preserve-installed on the Pixel
  10a; selecting **M** in Albums replaced the catalog with the expected server-backed sequence
  beginning at `M!ssundaztood`, followed by `Machina II`, `MACHINA/the machines of God`, and
  `Mack Daddy`. Song jumps retain automated Core/UI/provider coverage and remain available for the
  user pass.
