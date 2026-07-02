# Multi-Library Support

Naviamp should treat Navidrome libraries/music folders as first-class connection scope. A new user with the default Navidrome library should be able to add a server and immediately see only that default library without needing to know that library IDs exist.

## Current Slice

- [x] Store selected Navidrome music folder IDs on saved connections.
- [x] Persist selected music folder IDs in desktop and Android storage.
- [x] Include selected music folder IDs in the provider cache namespace.
- [x] Query Navidrome `getMusicFolders.view` for accessible library IDs and names.
- [x] Show a named multi-select instead of requiring raw library IDs.
- [x] Auto-select the first returned library when no selection exists.
- [x] Keep at least one library selected when library choices are available.
- [x] Apply selected libraries to album, artist, album-list, and random-song provider calls.

## Architecture Decision

Treat selected libraries as the active connection scope, not as a temporary visual filter. The app should only browse, sync, search, queue from discovery, and create new local indexes for libraries that are currently selected. Data from removed libraries may remain in local storage, but it must not appear in active library/search/home/playlist surfaces unless that library is selected again.

This decision is intended to support both multi-library Navidrome servers and future cross-server connections:

- A physical server account is not the same thing as an active music scope.
- A future active scope may contain multiple servers, multiple libraries from one server, or a mix of both.
- Local entities need enough identity to answer: provider, server/account, library/music-folder, remote entity id, and active source/scope.
- User-facing filters should be hidden when only one library is active.

## Scope Identity Model

Use these terms consistently while building the feature:

- **Server connection**: credentials and endpoint settings for one provider account, such as one Navidrome server/user.
- **Library**: a provider-owned library/music-folder, such as a Navidrome `musicFolderId`.
- **Source scope**: the active set of libraries from one server connection. The current implementation approximates this by including selected music folder IDs in `cacheNamespace`.
- **Composite source**: future active scope that can include libraries from multiple server connections.
- **Item origin**: the immutable origin for a row or queued item: provider id, server connection id, library id when known, and remote item id.

Long term, local library tables should move from only `source_id + remote_id` identity toward item-origin-aware identity. That lets Naviamp keep removed-library data locally while filtering active views by selected libraries, and it prevents collisions when two servers or libraries expose the same remote ids.

## Sync Behavior

- Adding a selected library should mark the active scope as stale and kick off a library sync.
- Removing a selected library should hide that library from active views immediately, without deleting its cached/indexed data.
- Re-selecting a previously removed library should reuse retained data when it is still fresh, then refresh in the background.
- User-initiated connection changes should force a sync for the selected scope.
- Startup restore should auto-sync only when the active scope is missing or stale.
- Existing queues may continue playing deselected-library tracks, but new browse/search/home results should respect the active scope.

## Cache Retention

- Do not eagerly delete library data just because a library was deselected.
- Use cache cleanup policy to prune old unused scopes by age/size later.
- Downloads should remain visible in Downloads even if their source library is deselected, but should carry source/library origin so the app can explain where they came from.
- Provider response cache keys must include enough source-scope information to avoid mixing different selected libraries or servers.

## Interim Strategy

The current code isolates selected-library sets by including selected folder IDs in `NavidromeProvider.cacheNamespace`. This avoids showing stale rows from deselected libraries, but it can create multiple local `media_source` rows for the same physical server as users change library combinations.

That is acceptable for the first slice, but it should be replaced or refined when the storage model gains explicit server/library/item-origin tables. Do not build major new UX assumptions around selected-set cache namespaces being the permanent model.

## Next Work

- [x] Keep connection and library-selection interfaces as cross-platform as possible, with shared state/models and shared UI before adding platform-specific wrappers.
- [x] Add Android library-name lookup and multi-select parity.
- [x] Verify Navidrome exposes an explicit default-library marker. It does not appear in the documented `getMusicFolders` response, so keep treating the first returned library as the default candidate.
- [x] Add tests for default auto-selection and one-required multi-select behavior.
- [x] Show selected library names in saved connection summaries and diagnostics.
- [x] Add connection migration/backfill behavior for existing saved connections with no selected library IDs.
- [x] Decide whether empty selected IDs should remain a compatibility fallback or become invalid once library discovery succeeds.
- [x] Add explicit server/library/item-origin metadata to local storage before deep playlist/download/history filtering work.
- [x] Add stale-scope detection so selected-library changes can reuse retained local data and refresh automatically.
- [x] Add cache cleanup for unused source scopes instead of deleting deselected-library data immediately.

Empty selected library IDs remain a compatibility fallback only when music-folder discovery fails or returns no usable IDs. When discovery succeeds, older saved connections are backfilled to the first returned library and the prepared connection is persisted through the normal source/session save path.

The current storage slice records explicit server/account and selected-library scope keys on each `media_source`. That makes source-scope origin queryable and keeps re-selected library combinations tied to the same retained local data. Per-track/per-playlist music-folder origin still needs provider/domain support before playlist, download, and history filtering can move beyond source-scope identity.

Source scopes auto-sync when their local index is empty, has never completed a sync, or has a newer sync start than completion. Connection startup also prunes old inactive source-scope rows after a retention window, but skips the active source and any source with downloads.

## Playlist And Smart Playlist Filtering

Navidrome appears to support library/music-folder filtering beyond basic browsing. Track each endpoint before applying filters so playlist behavior does not become inconsistent.

- [x] Verify `getPlaylists.view` supports a `musicFolderId` filter and determine whether multiple selected libraries require fan-out/merge.
- [x] Verify `getPlaylist.view` supports library filtering or whether returned entries must be filtered client-side.
- [ ] Verify native `/api/playlist` list/detail endpoints expose or accept library filters.
- [x] Add library filters to regular playlist list and detail refresh.
- [x] Add library filters to smart playlist list, load, save, and edit flows where supported by Navidrome.
- [x] Ensure playlist add/create operations do not accidentally cross selected-library boundaries.
- [ ] Add UI filters where users naturally expect them: Playlists, smart playlist editor, add-to-playlist dialogs, and Stats for Nerds.
- [ ] Hide library filter controls when only one library is selected, since filtering is unnecessary and confusing in that case.

Regular Subsonic playlist list/detail calls now pass `musicFolderId` for each selected Navidrome library and merge duplicate playlist/track IDs. Playlist detail also filters returned entries client-side when Navidrome includes `musicFolderId` on tracks, while preserving tracks with unknown folder metadata.

Smart playlist save/update now injects a `library_id` rule for the active selected libraries unless the definition already contains an explicit library rule. This scopes new or edited smart playlists at the Navidrome rule level. Loading a smart playlist for edit strips the exact injected active-library rule back out so the editor shows the user's actual criteria. Native `/api/playlist` list/detail library query support still needs verification before list/load behavior can be filtered beyond the regular Subsonic playlist endpoints.

Add/create playlist mutations now skip tracks that expose a known `musicFolderId` outside the active selected libraries. Tracks without folder metadata are preserved because older/cached responses may not carry enough origin data yet.

## Multi-Server Follow-Up

- [ ] Make library selections part of the broader multi-server source model.
- [ ] Keep source/library identity on tracks, albums, artists, playlists, queue items, downloads, and cached responses.
- [ ] Add conflict-safe merge rules for identically named libraries across servers.
