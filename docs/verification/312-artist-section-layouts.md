# Artist playlist and appearance album layouts (#312)

Settings → Experience → Artist details offers independent **Playlist layout** and
**Appears on album layout** choices. Each offers List and Grid and defaults to List.
The discography's existing layout remains independent. Appearance tracks always remain
rows and have no grid setting, as requested by the owner.

Both preferences are shared Core settings and are included in local persistence, settings
export/import and sync. Older documents default to List; unsupported enum values are
rejected using the existing settings import validation. No database migration is required.

The shared grid tile accepts an explicit media kind and uses the shared artwork shape policy.
Playlist collage artwork, selection, refresh/retry and partial-result messages are preserved.
Appearance album selection, menus, favorites and the existing Show more appearances state
work in both layouts. Layout changes do not reload the artist or change the server lookup.
Partial-appearance failure and truncation warnings are preserved when settings remap the page.

## Manual checks

1. Open Settings → Experience → Artist details. Select List and Grid for each section in
   all four combinations. Confirm changes apply when returning to the already-loaded artist.
2. Verify the main Albums/EPs/Singles layout is independent. Appearance tracks must remain
   rows in every combination; there is no track-grid option.
3. At narrow and wide window sizes, check covers, playlist collages, long names and row/grid
   wrapping. Open a playlist and return to the artist. Select an appearance album, return,
   and try its menu and favorite action.
4. Refresh playlists after changing membership on the server. Check loading, retry, partial
   results and empty results in both layouts. Disabling playlists hides the section and
   disables its layout choices without losing the selected layout.
5. On an artist with more than 50 appearances, use Show more appearances, change layouts,
   open an album and return. The expanded count should remain for that artist.
6. Restart, export/import and sync settings to another platform. Confirm both independent
   choices survive and an older export uses List. Check keyboard navigation and a translated UI.

## Scope and verification

The two existing standard shared artist-page entry points receive the preferences directly
from shared interface settings. No Android/Desktop/iOS production adapters are changed.
The television artist page already uses its own fixed horizontal catalog and does not expose
these playlist/appearance sections; this change does not add those missing television sections.

Common domain tests cover older defaults, all combinations, validation and export/import/sync.
Presentation tests cover local persistence. Rendered common tests cover controls, responsive
grids, selection, refresh/retry, favorites, pagination and keeping tracks as rows. Manual
acceptance and full platform CI remain separate from local checks. No release is authorized.
