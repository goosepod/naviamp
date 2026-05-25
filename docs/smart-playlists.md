# Smart Playlists

Naviamp should support Feishin-style smart playlist creation while keeping the rule model shared across desktop, Android, and Android Auto.

## Current Navidrome Behavior

- Smart playlists are JSON definitions stored as `.nsp` files.
- Navidrome imports `.nsp` files from the music library or configured playlist path during scans.
- Smart playlists refresh when a UI or Subsonic client accesses them, subject to Navidrome's refresh delay.
- Navidrome does not currently expose a standard Subsonic API for creating or editing `.nsp` files directly.
- Feishin saves smart playlists through Navidrome's native API, using `/auth/login` for a native bearer token and `/api/playlist` for create/update.

References:

- https://www.navidrome.org/docs/usage/features/smart-playlists/
- https://github.com/WB2024/Navidrome-SmartPlaylist-Generator-nsp
- https://github.com/jeffvli/feishin

## Implementation Plan

- [x] Add a shared domain model for Navidrome `.nsp` definitions.
- [x] Add JSON generation for common operators, nested `all` / `any` groups, limits, percentage limits, multi-field sort, public visibility, and default `.nsp` filenames.
- [x] Add tests for the shared model and core templates.
- [x] Add a shared smart playlist draft/editor state model for UI screens.
- [x] Add the documented Navidrome built-in fields and operators to the shared editor catalog.
- [x] Add desktop playlist-screen entry point for creating a smart playlist draft.
- [x] Add Android playlist-screen entry point using the same draft model.
- [x] Add platform-agnostic save flow:
  - [x] Add shared provider methods for smart-playlist create/update.
  - [x] Add Navidrome native auth token capture during password connection.
  - [x] Save smart playlists via Navidrome's native `/api/playlist` endpoint from desktop and Android.
  - [x] Persist the native token with saved Navidrome sources.
- [ ] Detect and label imported smart playlists in normal playlist lists if Navidrome exposes enough metadata.
- [x] Make the UI a dropdown-driven smart playlist builder instead of a preset picker.
- [x] Add editable rule groups with per-group all/any matching.
- [x] Refresh saved smart playlists from the server and prefer Navidrome playlist cover art when available.
- [x] Re-fetch playlist details from Navidrome when opening/playing playlists and periodically while a detail view is open, so dynamic smart playlists can catch up as favorites, ratings, or recently added tracks change.

## UI Shape

The first UI should live on the existing playlist screen behind a create/smart-playlist action. It should feel like a simple rule builder:

- Name and optional comment.
- Match mode: all rules or any rules.
- Rows with field, operator, and value controls.
- Sort field, sort direction, track-count/percentage limit, and public toggle.
- Preview generated JSON before save.

Keep the generated JSON and editor state common. Platform code should only handle connection lifecycle and presentation; provider-specific save details belong behind the shared `MediaProvider` contract.

Current limitations:

- The builder supports one level of editable rule groups. Deeper nested groups are represented in the shared model, but they are not editable in the UI yet.
- Playlist membership filters require manually entering the Navidrome playlist ID until playlist-picker integration is added.
- Custom, user-defined Navidrome tags can be used by the rule model, but the builder does not yet expose an arbitrary custom-field row.
- Existing saved connections that do not have a native token must reconnect with a password before saving smart playlists.
- `.nsp` import remains a fallback workflow for servers or providers without a native smart-playlist save API.
