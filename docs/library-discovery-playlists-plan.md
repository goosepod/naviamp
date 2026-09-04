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
- [ ] Add explicit per-view focus restoration and Back-navigation acceptance coverage.
- [x] Use the provider-neutral artist, album, and track paging/search contracts and reject stale
  responses after a source, query, or selected-view change.
- [x] Render artists and albums with shared collection rows and songs with standard shared track
  rows and actions.
- [x] Make search labels, empty states, A-Z navigation, loading, load-more, and refresh behavior
  accurately describe the selected catalog type.
- [ ] Provide deterministic keyboard and D-pad selector behavior, Back behavior, accessible state
  labels, and per-view focus restoration without creating a Television-only Library.
- [ ] Cover the common controller and shared UI on phone, Desktop, iOS, and Television, including
  large libraries and 720p, 1080p, and native 4K acceptance.

## Expanded Artist Discography Sections

- [x] Add **Mixtapes** to the shared release classifier using provider metadata and known synonyms,
  never title inference; retain Albums, EPs, Singles, Live Releases, Compilations, Remixes,
  Soundtracks, and Other Releases.
- [x] Define a provider-neutral, source-scoped discography contract that distinguishes primary
  releases from albums and tracks where the artist is a contributor.
- [ ] Complete provider coverage: Jellyfin uses a stable-ID provider query; add the shared-storage
  credit-index fallback for providers without a reverse-credit query.
  matching stable source artist IDs whenever possible.
- [ ] Define Appears On inclusion and de-duplication for featured tracks, compilations,
  various-artists releases, roles, aliases, missing IDs, and releases also classified as primary.
- [x] Present appearance albums normally and list their matching credited tracks directly in
  **Appears On**, so isolated credits and the exact matching tracks remain visible.
- [ ] Keep loading, empty, error, paging, section ordering, and navigation in Core, with shared UI
  and large-library/Television coverage.

## Track Membership in Playlists

- [ ] Define a source-scoped provider-neutral membership query with explicit loading, unavailable,
  and failure states, backed by a bounded Core loader/cache or optional reverse-membership provider
  capability.
- [ ] Add one Core editor and coordinator reused from current-track and queue-item menus on every
  platform.
- [ ] Show existing membership and allow multi-select additions and removals, then apply only the
  membership diff without changing unrelated tracks or ordering.
- [ ] Remove every occurrence of the selected media identity unless a later UI explicitly offers
  occurrence-level removal.
- [ ] Reconcile authoritative provider state after saving; keep the editor open during work and
  report partial failures per playlist without discarding successful mutations.
- [ ] Specify duplicate, unavailable/deleted, smart-playlist, concurrent-edit, stale-response,
  source-change, and empty-library behavior.
- [ ] Hide or honestly disable remote membership editing until Connect negotiates and routes an
  explicit capability for the playback device's active source.
- [ ] Verify touch, pointer, keyboard, TV remote, Apply/Cancel, Back, accessibility, and focus
  restoration.

## Favorite Artists Home Section

- [ ] Confirm provider favorite/favorited-at support and a source-scoped local fallback. Hide the
  section and setting when artist favorites are unsupported.
- [ ] Add a source-scoped artist-radio last-played value/query to shared storage, consolidating the
  change into the next migration after the current release baseline.
- [ ] Record last played only after successful Artist Radio, or successful track-seeded radio when
  the stable artist ID belongs to a currently favorited artist.
- [ ] Cover artist attribution, unfavorited artists, failed/cancelled launches, and clock behavior
  in common tests.
- [ ] Add shared Home presentation and persisted sorting by name, date favorited, and date last
  played, with deterministic missing/equal timestamp ordering.
- [ ] Verify source switching, favorite changes, restart persistence, settings-sync classification,
  navigation, and shared phone/Desktop/iOS/Television rendering.

## Release gate

- [ ] All new visible copy is present in shared string resources and maintained translations.
- [ ] No platform production file contains portable product behavior.
- [ ] Common behavior and failure tests pass.
- [ ] Android, Desktop/JVM, and iOS targets compile and their relevant suites pass.
- [ ] Phone and Television interaction testing is recorded.
- [ ] Release notes and the required GitHub Announcement are prepared only when this work ships.
