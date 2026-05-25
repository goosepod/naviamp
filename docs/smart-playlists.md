# Smart Playlists

Naviamp should support Feishin-style smart playlist creation while keeping the rule model shared across desktop, Android, and Android Auto.

## Current Navidrome Behavior

- Smart playlists are JSON definitions stored as `.nsp` files.
- Navidrome imports `.nsp` files from the music library or configured playlist path during scans.
- Smart playlists refresh when a UI or Subsonic client accesses them, subject to Navidrome's refresh delay.
- Navidrome does not currently expose a standard Subsonic API for creating or editing `.nsp` files directly.
- Feishin provides a useful reference UI: a playlist builder that writes Navidrome-compatible rules.

References:

- https://www.navidrome.org/docs/usage/features/smart-playlists/
- https://github.com/WB2024/Navidrome-SmartPlaylist-Generator-nsp
- https://github.com/jeffvli/feishin

## Implementation Plan

- [x] Add a shared domain model for Navidrome `.nsp` definitions.
- [x] Add JSON generation for common operators, nested `all` / `any` groups, limits, percentage limits, multi-field sort, public visibility, and default `.nsp` filenames.
- [x] Add tests for the shared model and core templates.
- [x] Add a shared smart playlist draft/editor state model for UI screens.
- [ ] Add desktop playlist-screen entry point for creating a smart playlist draft.
- [ ] Add Android playlist-screen entry point using the same draft model.
- [ ] Add export/save flow:
  - [ ] Save `.nsp` to a user-selected local file on desktop.
  - [ ] Share/export `.nsp` on Android.
  - [ ] Document that the file must be copied into Navidrome's music library or playlist path until a server API exists.
- [ ] Detect and label imported smart playlists in normal playlist lists if Navidrome exposes enough metadata.
- [ ] Add optional presets:
  - [x] Recently Played
  - [x] Favorites
  - [x] 80s Top Songs
  - [x] Random Library Songs
  - [ ] Recently Added
  - [ ] High Rated
  - [ ] Unplayed Favorites
  - [ ] Genre radio seed list

## UI Shape

The first UI should live on the existing playlist screen behind a create/smart-playlist action. It should feel like a simple rule builder:

- Name and optional comment.
- Match mode: all rules or any rules.
- Rows with field, operator, and value controls.
- Optional nested group support after the basic builder works.
- Sort field(s), sort direction, limit, and public toggle.
- Preview generated JSON before export.

Keep the generated JSON common. Platform code should only handle file picking, sharing, and any future Navidrome-specific save integration.
