# Library search layout acceptance

Issue: [#304](https://github.com/goosepod/naviamp/issues/304), v2.10.0.

The Library loading panel appears below the view selector and search field. Loading,
refresh, jump and error messages must not move either control or interrupt typing.
The existing loading indicators and search behavior are preserved in shared Core UI.
Query lookups do not refresh the library or display its refresh panel. Server searches
still find matches beyond the loaded pages. Clearing a search restores the previous
browse data and paging position without rebuilding the artist index or genres.

Manual checks on each available platform:

- Open Library and test Artists, Albums and Songs.
- Type several characters while results load. Confirm the field and view selector stay
  in place, the caret remains focused and every character is retained.
- Clear using the clear button, then type again. Repeat with no matching results.
  Confirm clearing does not start a refresh and restores the unfiltered browse list.
- Refresh from the Library menu. Confirm loading feedback appears below the search
  controls and clears when finished without moving those controls.
- Scroll the catalog and select an alphabet letter. Loading feedback remains visible
  above the results; the selector and search field stay in place.
- Repeat in a narrow and resized window, and with a long-label translation.
- Open a result, return, and confirm existing search and keyboard navigation still work.

Record the tested commit and package hash in [#280](https://github.com/goosepod/naviamp/issues/280).
Owner testing is pending; this change does not authorize a release or tag.
