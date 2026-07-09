# Smart Playlists

Naviamp should support Feishin-style smart playlist creation while keeping the rule model shared across desktop, Android, and Android Auto.

## Current Navidrome Behavior

- Smart playlists are JSON definitions stored as `.nsp` files.
- Navidrome imports `.nsp` files from the music library or configured playlist path during scans.
- Smart playlists refresh when a UI or Subsonic client accesses them, subject to Navidrome's refresh delay.
- Navidrome does not currently expose a standard Subsonic API for creating or editing `.nsp` files directly.
- Feishin saves smart playlists through Navidrome's native API, using `/auth/login` for a native bearer token and `/api/playlist` for create/update.
- Navidrome 0.62 adds ReplayGain smart playlist criteria fields and `isMissing` / `isPresent` operators for custom tag/role fields.
- Navidrome 0.63 extends `isMissing` / `isPresent` coverage to additional metadata fields, including BPM, bit depth, ReplayGain fields, and many text fields.

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
- [x] Add Navidrome 0.62 smart playlist updates:
  - [x] ReplayGain album/track gain and peak criteria fields.
  - [x] `isMissing` / `isPresent` JSON import and draft round-trip for custom tag/role fields.
  - [x] String boolean normalization when importing boolean criteria.
- [x] Add Navidrome 0.63 smart playlist updates:
  - [x] `isMissing` / `isPresent` support for BPM and bit depth fields.
  - [x] `isMissing` / `isPresent` support for ReplayGain fields.
  - [x] `isMissing` / `isPresent` support for supported text fields in the shared builder catalog.
- [x] Add desktop playlist-screen entry point for creating a smart playlist draft.
- [x] Add Android playlist-screen entry point using the same draft model.
- [x] Add shared smart playlist templates for Recently Played, Never Played, High Rated, Favorite Albums, Recently Added but Unplayed, and Long-Unheard Favorites.
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
- [x] Bypass provider-response cache for active playlist detail refresh and playback preparation so dynamic smart playlist membership/order is fetched from the server.
- [x] Add shared JSON import/validation so `.nsp` or native Navidrome smart playlist JSON can be loaded into the builder before saving.
- [x] Add desktop editing for existing Navidrome smart playlists by loading rules from the native `/api/playlist/{id}` endpoint and saving through update.

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
- Imported Navidrome `isMissing` / `isPresent` rules for custom tag/role fields can be loaded and saved, but new arbitrary custom tag/role rows are not yet discoverable from the field dropdown.
- Existing saved connections that do not have a native token must refresh smart-playlist auth with the password once before saving. Refreshed desktop native tokens are persisted in the saved media-source row, so this should not require repeated full sign-in after the token is captured.
- `.nsp` import currently accepts pasted JSON in the shared builder. A native file picker can wrap the same shared parser per platform.
- Editing existing smart playlists depends on Navidrome returning editable `rules` through the native playlist API. Regular playlists or smart playlists with unsupported custom fields will show a validation/load error.
