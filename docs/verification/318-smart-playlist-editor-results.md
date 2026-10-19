# Smart-playlist editor follow-up (#318)

The owner requested library names, more compact examples and a stable editor after reviewing a
Windows recording. The explanatory notice about favorites, ratings and listening history is
removed entirely, including its resource entry in all 17 maintained string catalogs.

Playlist browsing now loads the account's library names through the shared provider contract and
passes them to both playlist editors. Navidrome uses the existing account-readable music-folder
endpoint, without requiring administrator access. The definition continues to store stable library
IDs. Failed optional name lookups preserve previously loaded names and do not prevent browsing;
switching source clears them, and a late response cannot repopulate another source's editor.

Local previews now include up to 50 matching examples, respecting a smaller playlist limit. They
appear in a bounded, independently scrollable list. Each row reads `Track - Artist - Album`, with
ellipsis for long rows and the complete text retained in accessibility semantics. The examples
remain explicitly described as local samples, not a guarantee of the server's final order.

## Native window correction after owner retest

The owner reported that candidate `aa483795` still jumped. A real AWT window probe reproduced
an owned scene-layer window growing from 1168 × 755 at `(56, 79)` to 1168 × 1257 at `(56, -134)`
while the main window stayed at 1184 × 794 at `(48, 48)`. Scrolled-out field drawing was expanding
the native scene-layer window. A layout-only Compose test could not detect this. Standard Compose
Dialog and offscreen viewport clipping alone also reproduced the native growth; neither experiment
is the final implementation.

Core now supplies the viewport dimensions and all existing content, entrance animation, pointer
handling, keyboard dismissal and focus behavior through a shared owned-surface contract. Desktop
executes that contract with an explicitly sized AWT ComposeDialog, anchored to its owner's client
area. Its size is independent of descendant drawing extents. The existing 200 ms entrance remains;
no continuous playback animation or update frequency changes.

Changed Desktop production files and their native boundaries:

- `core/ui/src/jvmMain/kotlin/app/naviamp/ui/DesktopOwnedDialogSurface.kt`: creates, anchors and
  disposes an AWT owned window; translates shared dimensions and observes native owner movement.
- `core/ui/src/jvmMain/kotlin/app/naviamp/ui/DesktopRasterPresenter.kt`: supplies the current AWT
  window to the surface adapter. Product behavior remains in common UI.

The common contract and behavior compiled for Android, JVM and both iOS targets before native
wiring. The native probe checks both windows' bounds and transient move/resize events through
scrolling. It also checks displayed content changes and restores, and rejects screen captures whose
fixture marker is absent. Its temporary topmost fixture is test-only; application windows retain
the shared normal stacking policy. No user profile or server data is loaded by the probe.

Library names are also loaded at editor inspection/reference entry, independently of a prior
playlist refresh. Summary restrictions and Library ID conditions resolve names, and the condition
picker shows names while retaining IDs. Missing catalogs expose existing translated loading/retry
feedback instead of silently presenting bare IDs. Late metadata must not reset an edited draft.

The generic sync hint is shown only when an empty local index needs metadata. It is hidden during
sync, after a successful sync in the open editor, and when reopening with indexed tracks. The Sync
library button remains available for an explicit refresh. The old favorites/ratings/history notice
remains deleted from all 17 string files.

## Sync and server results

The editor does not schedule a periodic preview sync. Sync updates the local track metadata used by
draft examples; it downloads no audio. Refresh it when examples need to reflect server additions,
removals, favorites, ratings or listening-history changes. A complete successful sync also removes
obsolete entries from this source's derived index, as described in the #316 verification record.

Opening a saved playlist already requests its tracks directly from the server. That does not
require the local preview index. Navidrome's saved smart-playlist evaluation honors its own refresh
delay. Its native routes and song filters do not expose an equivalent read-only endpoint for
evaluating an arbitrary unsaved smart-playlist definition. The editor therefore keeps draft
examples local. It does not silently save draft rules, create temporary server playlists or show
old saved results as though they represented changed rules. Unsupported local fields continue to
produce an explicit limitation instead of a false empty result.

Primary references inspected: Navidrome `server/nativeapi/native_api.go`,
`server/nativeapi/playlists.go`, `persistence/sql_restful.go`,
`persistence/mediafile_repository.go` and `persistence/smart_playlist_repository.go` on its upstream
master branch on 2026-10-19. Server-version differences remain possible.

## Manual retest

1. Open a library-scoped smart playlist from both the playlist list and its details page. Confirm
   the summary and Options show the actual library names. Save/reopen and confirm the same library
   selections remain. Try a regular account with multiple libraries.
2. Confirm the removed favorites/ratings/history notice is absent. Read the remaining local-preview
   explanation and coverage count separately.
3. Use a playlist with at least 50 matches. Scroll within the example list to the last sample. Check
   the one-line layout, long titles and a track without an album. Confirm the dialog buttons stay
   reachable in a narrow/short window and keyboard navigation still works.
4. Reproduce the recording: open the editor, immediately scroll through templates and filters,
   return to Name/Comment, expand Options and Advanced, then dismiss and reopen another playlist.
   Watch for window jumps, duplicated outlines, overlapping or cut-off labels or background movement.
   Repeat after resizing, minimizing/restoring and changing display scale where available.
5. Check preview failure/retry, Sync library progress/Stop and changed rules. Opening saved playlist
   tracks must continue to work independently of local preview sync. Repeat relevant Android and
   macOS checks; previous candidate acceptance does not establish acceptance of these changes.

No new setting, persistence schema or migration is introduced. The two Desktop native-boundary files are listed above. No release is
authorized. Automated results and the exact packaged candidate are recorded in issue #318 and the
linked pull request; owner visual acceptance remains open.

The final focused run passed 88 tests with zero failures, errors or skips: 39 playlist-browsing,
transaction and source-race tests, plus 49 smart-playlist/popup UI tests. Coverage includes metadata
arriving after edits, named numeric/text library conditions, preserved rule IDs, hiding the hint
after sync and after reopening with indexed tracks, nested dropdown Escape behavior, and the shared
surface contract. Existing translations are reused; the removed owner notice remains absent from
all 17 catalogs.

The changed shared code compiled for Android, JVM, iOS ARM64 and iOS Simulator ARM64, and
`verifyCoreFirstArchitecture` passed. Shared contract verification preceded native adapter wiring.

The native geometry probe passed at 1184 × 794 and scale 1.0 with the normal application stacking
policy. Both owner and owned surface stayed fixed through five scroll positions, including the
return to the top, with no transient move/resize events. The surface also matched its owner client
after resize to 1000 × 740, moving the owner, and minimize/restore. These measurements reproduce
and detect the exact failure that the earlier semantic-layout test missed.

Composited pixel acceptance is **not established** by these results. Strict screen-capture attempts
could not validate the fixture marker on this workstation, so those attempts were rejected and
were not used as passing evidence. Skia picture dumps are not a substitute for displayed pixels.
The native probe defaults to geometry/lifecycle checks; set
`NAVIAMP_SMART_PROBE_VERIFY_PIXELS=true` to request its separate strict visible-pixel assertions on
an unobscured interactive desktop. The owner must still retest displayed drawing, initial labels,
mouse/keyboard input and other dialog interactions in the packaged app. macOS and other display
scales remain manual checks. No continuous-animation performance acceptance is claimed.

Windows packaging reuses unchanged, hash-verified CI native DLLs because this workstation lacks
the C++ toolchain. Only native configure/build steps are excluded; tests remain enabled. Exact
candidate/package hashes and logs are recorded in #280 and #318. No release or merge is authorized.


## Fixed field labels after owner retest

The owner confirmed that candidate `3fa1e02b` resolves the screen jumping, but provided new
screenshots showing outlines crossing the floating labels. The editor now uses a shared field
component with a fixed 12 sp caption above the outlined input, separated by 4 dp. Empty, filled
and focused inputs use the same caption placement. This applies to ordinary text, dropdowns,
genres, date operands, reference search, JSON import and password prompts. Existing translated
labels are reused; no copy, setting or schema is added. Input accessibility names remain attached
to editable controls. No platform production file or native window behavior changes in this follow-up.

Regression checks compare caption and input bounds before and after typing/clearing, at normal
and 150% text size, and render the shared field fixture. Existing editor/picker/popup tests and the
native geometry probe protect input and scrolling behavior. The owner should reopen the editor,
check Name and empty Comment, scroll through Match/Field/Operator/Value, open dropdowns and a date
picker, and check larger text. Packaged Windows visual acceptance remains required.


This follow-up passed all 51 focused smart-playlist/popup UI tests with zero failures, errors or
skips, shared Android/JVM/iOS ARM64/Simulator compilation, Core architecture verification and the
native geometry/lifecycle probe. Both rendered field fixtures (100% and 150% text size) were
inspected and show captions above unobstructed outlines. Native displayed-pixel acceptance remains
manual; the probe did not request pixel assertions. The preceding 39 presentation results above
are historical evidence and were not rerun for this UI-only change.
