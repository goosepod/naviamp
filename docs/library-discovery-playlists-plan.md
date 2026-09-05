# Library, Discovery, and Playlist Features Plan

**Branch:** `feature/library-discovery-playlists`

**Started:** 2026-09-04

**Status:** Active

**Scope correction:** Television work is not part of this branch. TV devices, remote-specific
acceptance, and prescribed TV resolutions are not delivery gates. Display-size regression tests
record shared/Desktop rendering evidence only. Earlier TV observations below are historical.

This plan promotes four approved items from the follow-up backlog into active development. Product
behavior, state, navigation, and UI belong in shared Core code. Platform modules may contain only
the smallest unavoidable native integration. Every new user-facing string must be defined in the
shared string resources and in each maintained translation.

## Windows hands-on feedback (active)

- [x] User confirmed the locally indexed album jump and continuous Home-settings drag scrolling work.
- [x] Center Library alphabet labels and expand the shared rail from 18dp to 36dp. Make the entire
  width clickable and add vertical padding in Artists, Albums, and Songs. Regression checks both
  edges of the click target and centered glyphs in every view (`build/alphabet-targets-check.log`).

- [x] Wire Home section dragging to the actual Settings scroll viewport. Holding near the top or
  bottom edge scrolls continuously, keeps the grabbed row under the pointer, and includes consumed
  scroll distance in the final reorder. Drop/cancel removes the scroll effect. Playlist management
  already has edge scrolling; the newly combined Home settings list had omitted that connection.
- [x] Sustained drag to both ends, saved drop order, release behavior, Android/Desktop compilation,
  and architecture checks passed (`build/home-drag-scroll-check.log`). Windows package:
  `build/home-drag-scroll-package.log`.

- [x] Replace the separate Section order page with direct dragging in Home Screen settings.
  Rows open section details, where a translated Visible switch controls Home visibility. Hidden
  sections remain accessible and labeled in the list. Preserve existing layout choices and settings
  persistence/sync. Add accessible move-up/down actions alongside dragging. Verified drag persistence,
  hide/show restoration, and 300px rendering (`build/home-settings-inline-check.log`;
  `core/ui/build/reports/home-settings/`).

- [x] Add the missing Favorite Artists entry to Home Screen settings, including visibility, home/page
  layout, and section ordering. New label translated in English/Spanish. UI validation is recorded
  in `build/favorite-artist-settings-check.log`.

### Persistent album catalog (active, 2026-09-05)

Approved after the album-letter-jump API investigation. Core owns a complete lightweight catalog
snapshot; shared SQLDelight persists it independently from opportunistic album/detail cache rows.
No audio files are cached by this feature.

- [x] Index all provider album pages in batches of 200 (the shared provider limit); deduplicate IDs and reject stalled paging.
- [x] Persist a complete snapshot atomically, scoped by saved source, provider namespace, and selected
  libraries. Keep the previous snapshot after failures, cancellation, or a stale source/view load.
- [x] Use one deterministic display-title order for browsing, local filtering, and A–Z jumps.
  Numbers/non-A–Z initials are grouped under #; articles are retained (The → T, Les → L).
  Equal titles use artist and album ID tie-breakers; no per-page sorting guesses the global order.
- [x] Show partial results and translated count progress on first indexing; keep cached results usable
  during later refreshes. Queue a cold-index jump until completion; warm jumps require no requests.
- [x] Reload snapshots after restart. Refresh on view entry when older than 15 minutes and on explicit
  Refresh; complete replacement removes deleted albums and applies renamed titles.
- [x] Persist album favorite changes, including changes made while a refresh is running.
- [x] Add the table to unreleased migration 24 (main ends at 23), rather than adding a development
  migration. Clear snapshots with library maintenance and cascade on source deletion.
- [x] Common index/controller/UI tests, storage persistence/migration checks, Android/Desktop
  compilation, and architecture checks passed (`build/album-index-check.log`). iOS requires a macOS runner.
- [x] Package Windows with the new album catalog and inline Home settings (`build/home-settings-inline-package.log`).
  The existing Windows development database received only the new table; schema version remains 25.
- [ ] Hands-on Windows acceptance: initial Albums index, G jump, search, and warm/restart use.


### Album letter-jump correction (2026-09-05)

- [x] Follow-up screenshot reproduced against the server's exact album order: the previous
  fallback stopped at 200 albums because the page ended with `The Aquabats!` (sorted under A).
  The UI then chose `Les Années 80…` at index 183, exactly matching the screenshot.
  Fallback now requires the requested letter to actually appear, or exhausts paging before
  selecting the nearest available following group. Read-only replay reaches `G I R L` at
  index 1131 after loading 1150 albums. Added an article-sorting controller regression.
- [x] Tests, Android/Desktop compilation, architecture checks, and packaging passed
  (`build/album-article-jump-check.log`, `build/album-article-jump-package.log`).
- [ ] Repeat Windows Albums → G acceptance. Persistent local album indexing is recorded in
  [follow-up ideas](v2-follow-up-ideas.md#persistent-local-album-index).


- [x] Replace raw Unicode title comparisons in the shared quick index with A–Z groups.
  A curly apostrophe in `’90s Rock Essentials` previously qualified as greater than G,
  selecting the numbered section even when G albums were loaded. Missing letters now
  choose the nearest following letter group; number/symbol titles cannot intercept them.
- [x] Keep fallback paging past pages ending in number/symbol titles, and compare native
  Navidrome sort keys in lowercase so bracketed titles retain their server ordering.
- [x] Shared regressions, Android/Desktop compilation, and architecture checks passed
  (`build/album-letter-jump-check.log`). Windows package: `build/album-letter-jump-package.log`.
- [ ] Hands-on acceptance: freshly open Albums, click G, and confirm G albums appear first.
  Server inspection was read-only; the music library's G boundary starts with `G I R L`.


- [ ] W1: Verify artwork speed after letter jumps against the live LAN server. Shared artwork now
  coalesces identical requests before taking a load slot, releases cancelled owners safely, and
  retains recently used decoded images. Regressions prove one fetch for twelve simultaneous
  identical requests, bounded concurrency, and recovery after cancellation. This removes client
  waste; it does not establish the cause or magnitude of the reported delay.
- [x] W2: Provide an always-accessible return-to-search action that scrolls to and focuses Library search.
- [x] W3: Fit Artists / Albums / Songs at narrow Desktop widths, including the app font scale.
- [x] W4: Open the same membership picker from song menus and Now Playing, without starting playback.
  Show existing membership, clear save instructions, and a secondary New playlist action.
- [x] W5: Make the Artist detail favorite heart visibly reflect its current state.

Finamp reference inspected: [playlist list](https://github.com/finamp-app/finamp/blob/redesign/lib/components/AddToPlaylistScreen/add_to_playlist_list.dart)
and [song-menu entry](https://github.com/finamp-app/finamp/blob/redesign/lib/menus/components/menuEntries/add_to_playlist_menu_entry.dart).
Its picker exposes membership and per-row updates, plus New playlist after existing playlists.
Naviamp retains explicit Save changes for its batched editor; new-playlist creation is an immediate,
clearly labeled create-and-add action. Both entry points share Core state and transactions.

Verification: 184 shared UI JVM tests and 254 presentation JVM tests passed. Coverage includes
opening membership from Library and artist appearances with no Now Playing UI, dismissing an
in-flight load without accepting its late response, favorite selected state, playlist selection/save/
creation controls, and return-to-search focus. Library rendering checks include 288px content width
within the 320px Desktop minimum and the app's 1.08 font scale, with complete label text verified.
Android and Desktop compilation and `verifyCoreFirstArchitecture` passed. No platform production
files changed. iOS compilation still requires macOS. Live Windows acceptance remains a user retest.
Logs: `build/desktop-feedback-check.log` (UI pass), `build/desktop-feedback-final-check.log`
(controller tests and target compilation), and `build/desktop-feedback-package.log` (Windows package).

### Follow-up visual adjustments

- [x] Keep Library search pinned while browsing deep in Artists, Albums, or Songs.
- [x] Show playlists containing the song first, with stable order while editing unsaved selections.
- [x] Compact membership rows and use the standard blue pill for New playlist.
- [x] Try red filled favorites across tracks, albums, and artists, with no selected backdrop on detail hearts.
- User confirmed Library list spacing looks good; retain its row spacing.

Validation: all 184 shared UI tests passed, including confirmed-membership ordering, stable
unsaved row positions, compact row height, red favorite pixels, search remaining visible/focusable
at a deep scroll position, and jump targets landing below the pinned header. Android/Desktop
compilation and the architecture check passed; no platform production files changed. Evidence:
`build/favorite-search-check.log` and `build/favorite-search-package.log`. Red hearts are a visual
trial awaiting the user's preference after testing the Windows build.

### Missing Favorites playlist investigation

- Confirmed read-only against the server: RÜFÜS DU SOL's Underwater (Solace) is present in
  Favorites and Hearted Tracks. Favorites selects loved songs or ratings above three in its music
  library; Hearted Tracks selects loved songs. Both are smart playlists. The server has 69 playlists,
  so the editor's 100-playlist limit was not responsible.
- [x] Show smart playlists and their actual membership as labeled read-only rows instead of hiding
  them; guard both toggles and mutation commands. Preserve membership-first ordering.
- [x] Treat explicit null smart-playlist metadata as absent in Navidrome mapping.
- No server playlist, rule, rating, favorite, or media file was changed during diagnosis.

Validation: 185 UI, 254 presentation, and 133 Navidrome JVM tests passed (572 total), plus
Android/Desktop compilation and the architecture check. Regressions cover checked read-only
smart rows, blocked toggles and mutations, failed membership reads, and null smart metadata on
ordinary playlists. No platform production changes. Logs: `build/smart-membership-check.log`
and `build/smart-membership-package.log`.

### New playlist form visibility

- [x] Replace the clipped inline creation form with a dedicated dialog view that focuses the
  name field, keeps creation controls visible, and returns to membership after success.
- [x] Preserve unsaved membership choices on cancel/success, and preserve the entered name on failure.
- [x] Bound the playlist list to the space remaining above its actions; test 70 playlists in narrow
  and short windows, including creation failure and retry.

Validation: 187 shared UI tests passed, plus Android/Desktop compilation and the architecture
check. The new cases use 70 playlists at 640×360 and 360×480, verify initial name-field focus,
cancel preserving selections, visible failure/retry with the name retained, and success returning
to the newly created checked playlist without losing other pending selections. No platform
production code changed. Logs: `build/playlist-creation-check.log` and
`build/playlist-creation-package.log`.

### Library search appearance and fixed controls

- Use exactly the Search page's shared compact search field, including its translucent fill,
  rounded corners, height, and horizontal inset. Remove the extra opaque rectangular backing.
- Keep Artists / Albums / Songs and search outside the scrolling results, always visible.
  Letter jumps and focus restoration now address result rows directly, without header offsets.
- Validated: all 187 UI tests, Android/Desktop compilation, and the architecture check passed.
  Deep-scroll tests keep all three selectors and search visible and preserve the result position
  when focusing search. Letter jumps, short-window alphabet scrolling, and Back/focus restoration
  passed. Narrow-width rendering was inspected. No platform production changes.
  Logs: `build/library-fixed-header-check.log` and `build/library-fixed-header-package.log`.

## PR review corrections (2026-09-04)

These checks supersede earlier completion claims where the review found missing behavior or
coverage. Check an item only after its regression checks pass; record target/tooling limitations.

- [x] R1: Remove playlist membership using targeted occurrence removal, never whole-playlist
  replacement. Cover duplicate occurrences, unrelated entries, and provider failures.
- [x] R2: Load Jellyfin favorite artists through valid bounded pages, including the 500-item Home request.
- [x] R3: Scope the entire letter jump to its source, view, query, and generation before any suspension;
  reject out-of-order offsets and stop stale/failed fallback paging.
- [x] R4: Consume each completed jump once so paging and detail/Back do not reset the viewport.
- [x] R5: Restore focus only to an attached, current target; cover catalog replacement and missing rows.
- [x] R6: Exclude primary releases before limiting stored Appears On results; test large primary catalogs.
- [x] R7: Distinguish playlist discovery failure from an empty collection and provide translated retry.
- [x] R8: Consolidate membership orchestration in a dedicated common coordinator; cover cancellation,
  source changes, partial failures, deleted/smart playlists, bounded loading, and dismissal during save.
- [x] R9: Complete favorite sort persistence, timestamp ordering, live refresh, and partial-cache reconciliation.
- [x] R10: Gate remote membership actions until Connect can route them; preserve playlist creation access.
- [ ] R11: Complete loading/status and large-library discography behavior and shared UI acceptance.
- [x] R12: Review the global 8% font-scale experiment separately from feature correctness.
- [x] R14: Propagate favorite changes through Library Albums, appearance albums/tracks, and Home artist caches.
- [ ] R13: Reconcile this plan and the follow-up notes with actual tests and final platform verification.
- [x] R15: Map native Navidrome catalog artwork, fractional duration, and annotation fields separately
  from Subsonic responses; verify Library Songs/Albums covers on the phone.
- [x] R16: Reconcile an already-open playlist detail after membership edits, including stale in-flight loads.
  Common regression checks and the resumed physical-phone retest passed.
- [x] R17: Suppress the empty Library message during initial loading; common UI regression passed.
- [x] R18: Publish playlists created through membership into an already-loaded playlist list; common
  regression checks and the physical-phone test passed without manual Refresh.
- [x] R19: Reject list refreshes started before membership reconciliation, update registry and picker
  counts, and clear superseded list/detail loading status. Common success/failure race tests passed.
- [x] R20: Preserve expanded Appears On results when album/player content temporarily covers the artist;
  reset expansion for a different artist. Shared UI regression and Android/Desktop compilation passed.
- [x] R21: Interpret Navidrome's optional `getArtist` participation results using stable album-artist
  credits before treating releases as primary. Preserve collaborations and unknown legacy IDs;
  common regression and 133 provider tests passed, as did Android/Desktop builds. The phone still
  shows the Snoop Dogg/Dirty Audio case because Navidrome explicitly supplies both as album artists.
  Read-only server/file inspection confirmed a missing-album-artist-tag fallback, not a client defect.

### Remaining acceptance gates

This is the current outstanding work; earlier chronological notes below retain historical results.

- [x] Shared loading-panel, large-discography expansion, and overlapping letter-jump regressions.
- [x] Navidrome phone Library artwork/loading, independent search, letter navigation, and playlist
  membership/creation reconciliation using disposable playlists.
- [x] R20 navigation-state regression and final Android/Desktop builds after that change.
- [x] Shared Library layout at 1280x720, 1920x1080, and 3840x2160 physical pixels at 1x density,
  plus 3840x2160 at 2x density; keyboard selector/search navigation and letter-index activation.
- [ ] Hands-on large Appears On catalog: the phone's tested artist has only three primary releases.
- [ ] Desktop interaction and layout acceptance.
- [ ] iOS compilation/tests on macOS; the Windows checkout disables the required native targets.

R11 and R13 remain open until their outstanding acceptance gates have evidence. Jellyfin Quick
Connect remains a recorded follow-up idea, outside this branch's four approved features.

Latest validation: 427 UI/presentation JVM tests passed with zero failures/errors/skips, including
125 appearances retained across content removal/re-entry and an artist change resetting the limit.
The playlist overlap test covers both a stale successful response and a stale failure, with assertions
for list rows, picker counts, shared lookup state, loading status, and unaffected playlists.
Android assembly, Desktop compilation, `verifyCoreFirstArchitecture`, and whitespace checks passed.
These changes touch shared production code only; no host production adapters changed.
The resulting `app.naviamp.android.review` APK was installed over the isolated review copy on the
connected Pixel 10a; Home rendered with favorite artists and artwork. This startup check does not
substitute for the outstanding large-catalog/device acceptance. Changes remain uncommitted.

Display-size follow-up: all 179 UI tests passed, including four new rendering checks with 200 album
rows. The tests assert exact render dimensions, pinned loading text above visible rows, unchanged
scroll position when loading starts/finishes, and Enter activating the Z index item. Snapshots under
`core/ui/build/reports/library-display-sizes/` were visually inspected. These exercise the shared
Library content directly, not the full Desktop host shell.

Phone follow-up found eight primary releases and two credited tracks for Snoop Dogg. A Dirty Audio
release was rendered among primary albums. Navidrome's
[source](https://github.com/navidrome/navidrome/blob/master/server/subsonic/helpers.go) documents
that its `ArtistParticipations` option includes track-artist albums in `getArtist`; OpenSubsonic album
`artists` entries map album-artist identities. The R21 correction excludes only albums whose known
album-artist IDs all disagree with the selected artist. It does not infer identity from display names.
Navidrome appearances continue to use the existing bounded shared credit index and therefore depend
on indexed tracks; this change does not claim a complete native reverse-credit query.
The updated review APK was installed and the same artist reopened. Its count remained eight and
the Dirty Audio release remained primary. Album detail shows Dirty Audio as album artist and Snoop
Dogg on one track, but the visible UI does not expose the server's stable-ID fields. At that stage
the phone observation was unresolved. The authorized server investigation below establishes why
the existing classification is correct for the supplied structured credits.
No playlists/favorites were changed and playback remained paused. A proposed database export was
rejected by automatic approval review because the full database could contain private metadata or
secrets; no export occurred. Inspection continued through the app UI.

### R21 server and file diagnosis

With the user's authorization, inspected `entertain` and the specific files under
`/mnt/media/media/music/Singles/` using read-only queries and Navidrome 0.63.2's `inspect` command.
No music files, server configuration, or database records were changed; no rescan was triggered.

- `Dirty Audio feat. Snoop Dogg/Rules.mp3`: display `ARTIST` is `Dirty Audio feat. Snoop Dogg`;
  multi-value `ARTISTS` contains both artists. `ALBUMARTIST`, `ALBUMARTISTS`, and the album-artist
  MusicBrainz ID are absent. Both track-artist MusicBrainz IDs are present.
- `Dirty Audio, URE97, & Gold/REBORN.mp3`: `ARTISTS` contains Dirty Audio; album-artist tags are absent.
  Both files share `ALBUM=TRAAP (Hard Trap) (LDS 245)` and `compilation=0`.
- Navidrome's current `inspect` output maps both artists from Rules into its `albumartist`
  participants, while its scalar `albumArtist` displays only Dirty Audio. The stored album credits
  match that fresh interpretation. An initial ffprobe view flattened the multi-value fields and
  was therefore insufficient; the complete Navidrome tag inspection supersedes it.
- Naviamp correctly retains a release when the selected artist appears in the structured
  album-artist IDs, even if the scalar display name names somebody else. Filtering on the scalar
  name or only the first ID would remove legitimate collaborations.
- The provider regression fixture now also uses a scalar display artist that omits the selected
  secondary album artist, reproducing this response shape without requiring private server data.
  All 133 Navidrome JVM tests passed after that fixture update
  (`build/phone-review/album-credit-diagnosis.log`).

**Resolution:** The client diagnosis is complete. To change this particular classification, correct
the files' album-artist metadata to the intended release identity and then rescan them in Navidrome.
That is a separate library-data change, not a branch delivery blocker. The files' intended release
identity (Dirty Audio release versus a various-artists compilation) must be established before
retagging; this investigation does not infer it from the album title.

Implementation and validation log for these corrections follows the hands-on validation log below.

## Delivery order

1. Switchable Complete Library Views
2. Expanded Artist Discography Sections
3. Track Membership in Playlists
4. Favorite Artists Home Section

Each feature must receive focused common tests before it is considered complete. The branch must
also pass the shared Android, Desktop/JVM, and iOS compilation/test gates, followed by hands-on phone
coverage and Desktop interaction checks.

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
- [x] Show a translated loading indicator when an Album or Song quick-index letter requires a
  server-backed page that has not loaded yet. Keep the selected letter visible and prevent
  duplicate jump requests until the load succeeds or fails.
- [x] Provide deterministic keyboard and D-pad selector behavior, Back behavior, accessible state
  labels, and per-view focus restoration in the shared Library.
- [ ] Cover the common controller and shared UI on phone, Desktop, and iOS, including
  representative large libraries.

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
  and large-library coverage.

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
- [x] Hide or honestly disable remote membership editing until Connect negotiates and routes an
  explicit capability for the playback device's active source.
- [ ] Verify touch, pointer, keyboard, Apply/Cancel, Back, accessibility, and focus
  restoration.

## Favorite Artists Home Section

- [x] Add bounded native favorite-artist queries for Navidrome and Jellyfin, hide the Home section
  when artist favorites are unsupported, and provide deterministic name ordering.
- [x] Add a source-scoped local fallback that preserves a stable first-observed favorite timestamp
  when providers omit one and remains available after a provider lookup failure.
- [x] Add deterministic date-favorited/date-last-played ordering when timestamps are missing or equal.
- [x] Add a source-scoped artist-radio last-played value/query to shared storage, consolidating the
  change into the next migration after the current release baseline.
- [x] Record last played only after successful Artist Radio, or successful track-seeded radio when
  the stable artist ID belongs to a currently favorited artist.
- [x] Cover stable artist attribution, unfavorited artists, failed/cancelled launches, and
  injected-clock behavior in common tests.
- [x] Add shared Home presentation, translated section labeling, and Artist Detail navigation.
- [x] Add persisted sort selection for name, date favorited, and date last played.
- [ ] Verify source switching, favorite changes, restart persistence, settings-sync classification,
  navigation, and shared phone/Desktop/iOS rendering.

## Release gate

- [x] All new visible copy is present in shared string resources and maintained translations.
- [x] No platform production file contains portable product behavior.
- [x] Common behavior and failure tests pass.
- [ ] Android, Desktop/JVM, and iOS targets compile and their relevant suites pass.
- [ ] Phone and Desktop interaction testing is recorded.
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

## PR correction implementation log

- **2026-09-04 — Implementation:** Targeted membership removal is a shared provider contract.
  Jellyfin removes matching stable entry IDs; Navidrome reads the unfiltered canonical playlist to
  obtain occurrence positions, preserving duplicates and entries outside the selected music folders.
  Subsonic index-based deletion cannot make a concurrent third-party reorder atomic; the protocol
  has no revision precondition in this adapter. No whole-playlist rewrite is used for membership.
- **Coordinator:** One common membership coordinator handles fresh discovery, four concurrent reads,
  a 100-playlist bound, per-playlist authoritative reconciliation, retry, cancellation, source changes,
  and creation using the editor's captured track. Queue UI now uses the same editor as current-track UI.
  Loading and saving block dialog dismissal. No Connect transport exists in this checkout: the shared
  membership affordance now defaults off and is explicitly enabled by the local Core presenter.
- **Catalog:** Offset lookup and page loading share a generation/source/view/query lifetime; completed
  jumps are consumed by retained viewport state. Missing focus targets fall back to the selector.
  Returning to an already loaded view preserves its pages, while source changes clear all catalogs.
- **Favorites:** Name, date-favorited, and last-radio-played sorts use deterministic name/ID tie breaks
  and put missing timestamps last. Sort choice belongs to synced `InterfaceSettings`; activity history
  remains source-scoped local data. Favorite/radio changes request a shared Home refresh. A capped
  500-artist result is treated as incomplete and cannot deactivate unreturned cached favorites.
- **Discography:** Primary-release exclusion occurs before the appearance limit. Optional appearance
  failures keep primary discography available, truncation is explicit, and shared UI reveals appearances
  in batches of 50. Final large-library/device interaction acceptance remains open.
- **Typography review:** The existing 8% shared font-scale experiment is preserved because the branch
  records an explicit phone validation of it. It is not required by these feature corrections. Its
  Desktop/iOS clipping and accessibility acceptance remains a separate unchecked release item;
  this pass does not claim that a phone screenshot establishes those results.
- **Validation:** The first focused provider/storage/presentation run passed before the later coordinator,
  favorite-sort, and UI changes. The final combined verification result will be recorded below when
  complete. Earlier failed attempts included an unwritable default Gradle cache, a duplicate XML entry,
  a missing test import, and an ambiguous Desktop task name; these are not counted as passing checks.

- **Additional review correction:** Registry favorite updates now include the new Library Albums,
  appearance album/track, and Home favorite-artist surfaces; otherwise repeated toggles could read
  stale favorite state even after a successful provider mutation.

### Correction regression evidence

| Correction | Regression coverage |
| --- | --- |
| R1/R2 | `JellyfinProviderTest`: targeted duplicate occurrence deletion, missing entry ID refusal, 500-favorite paging; `NavidromeProviderTest`: canonical unfiltered removal indices, no re-additions. |
| R3 | `NaviampCoreCatalogControllerTest`: late offsets, source changes during offset lookup, failed fallback requests stopping after one call. |
| R4/R5 | `NaviampLibraryFocusUiTest`: page append and detail/Back after a consumed jump; replacement of the focused row. |
| R6/R9 | `StorageCriticalStoresTest`: primary catalogs larger than the appearance limit; incomplete favorite snapshots; existing source/timestamp persistence tests. |
| R7/R8/R10 | `NaviampCoreNowPlayingMediaControllerTest`: retry, partial success, cancellation, source switch before mutation, dismissal during save, smart exclusion, 100-playlist cap, editor-track creation. |
| R9 | `FavoriteArtistsTest`: three deterministic sorts, missing/equal timestamps, serialization defaults, and settings-sync inclusion. |
| R11 | `JellyfinProviderTest`: failed appearance query preserves primary discography. Large-library device rendering remains an acceptance item. |
| R14 | `NaviampCoreNowPlayingMediaControllerTest`: favorite updates reach the new registry surfaces. |

No connected Android device was available in the correction session (`adb devices -l` returned an
empty list). This Windows host disables iOS native/cinterop targets; macOS validation is still required.

- **Final combined verification (2026-09-04):** `verifyCoreFirstArchitecture`,
  `:core:storage:verifySqlDelightMigration`, all six affected JVM suites, `:apps:android:assembleDebug`,
  and `:apps:desktop:compileKotlinDesktop` passed together. The JVM reports contain **1,463 tests,
  zero failures, zero errors, zero skipped** (domain 845; storage 43; presentation 248; UI 172;
  Jellyfin 24; Navidrome 131). The Desktop native BASS library also configured and built successfully.
  Gradle used the existing user cache and the Android SDK's installed CMake executable via `CMAKE_EXE`.
  Logs: `build/review-final-verification.log`. All 14 new resource keys exist in English and Spanish,
  with no duplicate resource keys. No Android/Desktop/iOS production source files changed.
- **Remaining acceptance:** R11 large-library/device rendering, R13 iOS/macOS validation and physical
  phone/Desktop interaction, typography on remaining form factors, and membership focus restoration on real
  input devices remain unchecked. The iOS gate and device testing are not inferred from JVM success.

- **Final membership follow-up:** The presentation suite passed again after adding the explicit
  deleted-playlist assertion (`build/review-membership-final.log`). `git diff --check` passed.

### Post-correction physical-phone verification (2026-09-04)

- [x] Pixel 10a, Android API 37: all three `AndroidNativeBoundaryInstrumentedTest` tests passed
  (zero failures/errors/skips): Android Keystore encryption and tamper rejection; packaged BASS
  decoding, seeking, and sample reads; Core 5.1-to-stereo matrix applied by the native mixer.
- [x] Current corrected build installed as isolated `app.naviamp.android.review` (Naviamp Review),
  and visually verified at the connection setup screen. Existing production and `v2test` data remain
  untouched. The installed `v2test` certificate matches neither this computer's debug key nor the
  located upload key, so Android rejected an in-place update. A temporary ignored Gradle init script
  changes only the review package suffix and launcher label; no production build configuration changed.
- [ ] Sign into the review copy and verify the corrected Library jumps, detail/Back restoration,
  favorite sorting, and current-track/queue playlist membership with a disposable playlist.
- [ ] Complete Desktop/input-device acceptance and macOS/iOS validation. Native boundary success does
  not establish acceptance of the Library/playlist UI.

Evidence: `build/phone-review/isolated-build.log`, `build/phone-review/startup.png`, and
`apps/android/build/outputs/androidTest-results/connected/debug/TEST-Pixel 10a - 17-_apps_android-.xml`.
The earlier no-device note above describes the JVM correction run; this subsequent phone pass
supersedes that availability limitation, but leaves the signed-in feature checks open.
Startup error-level log contained one `Invalid resource ID 0x00000000` message; the connection
screen rendered and the process remained live. No fatal exception appeared in that captured log.
Treat this as an observation to investigate if it recurs with a visible UI problem, not a clean-log claim.


### Signed-in correction pass (2026-09-04, Pixel 10a/API 37)

- [x] Favorite Artists loaded on the fresh review database. Switching from Name to Date favorited
  changed the order; Date favorited ordering persisted across force-stop/relaunch.
- [x] Artists M jump and artist detail/Back restored the same viewport. Albums displayed `Loading S…`,
  loaded S, preserved an additionally scrolled viewport through Sanctum EP detail/Back, and jumped
  backward to A. Screens contained real artist/discography and album metadata.
- [x] Reproduced blank Library Songs artwork while the same track had artwork in Now Playing.
  Native `/api/song` and `/api/album` responses were incorrectly passed through Subsonic-only field
  interpretation. Added dedicated provider-common mapping for `mf-`/`al-` artwork identifiers,
  fractional durations, native favorite timestamps/flags, ratings, sample rate, library ID and play date.
  Native model references: [MediaFile](https://github.com/navidrome/navidrome/blob/master/model/mediafile.go),
  [artwork identifiers](https://github.com/navidrome/navidrome/blob/master/model/artwork_id.go), and
  [annotations](https://github.com/navidrome/navidrome/blob/master/model/annotation.go).
- [x] Native catalog regression test passed with the Navidrome JVM suite; Android assembly and Desktop
  compilation passed (`build/phone-review/artwork-fix-build.log`). Installed the fix over the signed-in
  review copy and visually confirmed populated Song and Album covers. Evidence:
  `library-artwork.png` (before), `library-artwork-fixed.png`, `album-artwork-fixed.png` under
  `build/phone-review/`.
- [x] Created `AAA_Naviamp_Review_20260904` from current-track membership with only `"C" Section`.
  The queue action opened membership for `"C" T.H. S (outro)` while the current song remained paused.
  Added the queued song to that disposable playlist, then removed the original song using membership.
  Reopening the playlist confirmed only the queued song remained. Deleted the disposable playlist
  and observed the successful deletion result. No pre-existing playlist was deliberately changed.
- [ ] The already-open playlist detail initially retained its old row until reopened after membership
  editing. R16 tracks immediate reconciliation of that snapshot and rejection of older detail requests.
- [ ] Songs M jump was observed pending but was interrupted during this pass; completion/latency and
  page-append interaction still need a focused rerun. Do not count this as a completed Songs jump test.

**Phone testing paused at the user's request.** Playback was paused; disposable-playlist cleanup was
complete. The phone has the artwork fix. Subsequent local-only changes have not been installed or
claimed as physically verified. Desktop/iOS and remaining phone acceptance checks stay open.


**Local R16 follow-up:** Membership reconciliation now publishes verified playlist contents to the
common browse owner before download invalidation. It updates list counts, the selected media registry,
and the open detail without navigating; superseded detail loads cannot restore stale tracks. Common
regressions cover successful reconciliation amid a partial mutation failure, an older in-flight detail
load, and updates to a different playlist leaving the selected detail intact. Presentation JVM tests,
Android assembly, Desktop compilation, and `verifyCoreFirstArchitecture` all passed together
(`build/phone-review/playlist-refresh-build.log`). `git diff --check` passed. This follow-up is built
locally only; the installed phone version remains the earlier artwork fix. No platform production
source files were changed for either correction.


### Resumed physical-phone pass (2026-09-04)

At the user's request, resumed testing and installed the locally verified R16 build over the existing
review package without clearing data.

- [x] Songs M jump completed at `M!ssundaztood`; artwork and durations rendered. Scrolling moved
  into the Macarena rows without replaying the jump. A backward A request completed at
  `A B-Boy’s Alpha`. This confirms completion, not a precise latency measurement; exhaustive
  page-append and rapid repeated-jump acceptance remains separate.
- [x] R16 exact regression: created `AAA_Naviamp_Refresh_Test` with the paused current song, opened
  its detail, opened Now Playing over it, removed membership, then returned directly to the same
  detail. It immediately showed `0 tracks` with no old row; no reopen/manual detail refresh was needed.
  Evidence: `build/phone-review/playlist-reconciled.png` and matching XML.
- [x] Songs search for `Dear Mama` returned matching 2Pac tracks. Switching to Albums showed its
  independent empty query; switching back restored the Songs query and results. Cleared test search.
- [x] Deleted the disposable playlist with its explicit named confirmation; UI reported deletion.
  Playback stayed paused throughout this pass. Existing playlists were not deliberately modified.
- [ ] R18 reproduced: the new playlist was absent from an already-loaded playlist list until manual
  Refresh, although creation succeeded on the server. This is separate from the now-verified R16 fix.

Remaining gates include R17/R18, exhaustive paging/rapid-jump and larger discography UI acceptance,
Desktop/input-device checks, and macOS/iOS validation. Evidence from the earlier paused session is retained
above; this pass supersedes its R16 installation/device-retest limitation.


### Prominent Library loading feedback (2026-09-04)

Following phone feedback that the title-row loading message was practically unnoticeable, replaced
it with a full-width accent panel pinned below the Library heading, outside the scrolling catalog.
Letter navigation shows a large letter badge, bold status text, and an indeterminate progress bar;
ordinary catalog loading uses a spinner and view-specific loading text. Status changes use a polite
accessibility live region. Errors remain visible in the same fixed area after progress stops.
Removed loading/status rows from the catalog's item offsets so showing progress cannot introduce
an extra row into jump/focus calculations. The empty-results message is suppressed while loading.
All new copy is maintained in English and Spanish. Device verification awaits reconnection; no phone
actions were performed for this change.

Validation: the complete shared UI JVM suite, Android debug assembly, Desktop compilation, and
`verifyCoreFirstArchitecture` passed (`build/library-loading-panel.log`). The loading-panel regression
checks visibility while scrolled, unchanged item position when the panel appears/disappears, and
loading-to-empty transitions. Resource parity and `git diff --check` passed. No platform production
source changed; iOS validation remains unavailable on this Windows host.


### Playlist creation publication follow-up (2026-09-04)

The membership coordinator now notifies the shared playlist browse owner when creation succeeds.
The browse owner inserts the server-returned playlist into the existing list, media registry and
mutation choices, deduplicates by ID, and prevents a pre-creation refresh from overwriting it. The
current detail and navigation remain intact. Common regressions cover publishing the creation result,
preserving unrelated playlists, duplicate notifications and leaving the selected detail unchanged.


### Loading panel and creation fix: phone acceptance (2026-09-04)

- [x] The updated review APK installed in place after presentation JVM tests, Android assembly,
  Desktop compilation and architecture checks passed (`build/phone-review/created-playlist-build.log`).
- [x] Captured the new high-contrast loading panel during a real Songs M jump. The large M badge,
  bold text and animated bar were clearly visible; the panel cleared on completion. Evidence:
  `build/phone-review/prominent-loading.png`. Earlier shared UI tests also verify the panel remains
  visible over a scrolled catalog and preserves item position when appearing/disappearing.
- [x] Continued scrolling after the M jump through `Me & Mr Jones` to `Metamorphosis` without
  replaying the initial jump. Saved `songs-paged.xml` and `songs-paged-further.xml`. This is a sustained
  scrolling check; no exact server request count or complete-catalog traversal is inferred from it.
- [x] R18 exact regression: loaded Playlists first, opened current-track membership, created
  `AAA_Naviamp_List_Test`, then returned directly to the existing list. The new playlist appeared
  immediately without Refresh and its detail contained the correct current track. Deleted the
  disposable playlist through its named confirmation; UI reported deletion. Playback stayed paused.
- [x] 2Pac detail rendered Top Tracks followed by Discography/Albums/Compilations with covers and
  metadata; Back returned to Library. This account returned three primary releases, so this sample
  does not validate large discographies or appearance pagination. Evidence: `discography-phone.png`.

Remaining acceptance: large-discography/Appears On pagination, rapid overlapping jump requests on
real input devices, Desktop interaction, and macOS/iOS build/testing. R17/R18 are now closed;
older unchecked entries in historical logs describe the findings before their later fixes.


### Large discography and overlapping-request follow-up (2026-09-04)

- Found and fixed an Appears On paging regression: its expansion state was keyed to the entire track
  list, so a favorite/metadata change reset an expanded list to 50 items. Expansion is now scoped to
  the artist, preserving the user's visible window through metadata changes and resetting for another
  artist. This change remains in common UI.
- Added a shared UI regression with 125 appearance albums and 125 tracks. It exercises 50/100/all
  expansion, disappearance of Show more at the end, a metadata update after expansion, selection of
  the final album, and resetting the window when switching artists. It also verifies primary releases
  remain present with appearance failure and truncation notices.
- Added the deferred-response regression for R18: a playlist refresh started before creation cannot
  erase the published playlist or leave the list stuck refreshing.
- Physical phone: while Songs S was loading, requested A. The panel changed to `Loading A…` and
  completed at `A B-Boy’s Alpha`; the older S request did not replace the result. Evidence:
  `build/phone-review/overlapping-jump-result.xml`. No playlists, favorites, or playback were changed
  during this follow-up.

These synthetic large-discography checks complement the earlier three-release physical sample;
they do not claim real-server appearance pagination or Desktop/macOS/iOS acceptance. The backlog
section-order description now matches the approved implementation: Top Tracks before Discography,
then Appears On.


Final validation for this follow-up: full UI and presentation JVM suites, Android debug assembly,
Desktop compilation and architecture verification passed together (`build/phone-review/discography-final.log`).
The updated review APK installed successfully in place. The large-appearance behavior was validated
with synthetic shared UI data; installing it does not claim that the phone's server supplied that case.
No platform production files changed. The tree remains uncommitted.
