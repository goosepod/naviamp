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

The recording showed the popup moving and previously painted field outlines appearing at the wrong
positions. Owned dialogs now record the entire fixed viewport from their first frame, as owned
menus already do. Their existing 200 ms entrance animation runs on the popup's frame clock rather
than the cached owner window's clock. Animation duration and continuous playback animations are
unchanged. Initial floating labels keep their Material behavior. Native Windows visual confirmation
remains required; a semantic geometry test alone cannot establish that native cached drawing is
correct.

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
   Watch for window jumps, duplicated outlines, cut-off floating labels or background movement.
   Repeat after resizing, minimizing/restoring and changing display scale where available.
5. Check preview failure/retry, Sync library progress/Stop and changed rules. Opening saved playlist
   tracks must continue to work independently of local preview sync. Repeat relevant Android and
   macOS checks; previous candidate acceptance does not establish acceptance of these changes.

No host production code, setting, persistence schema or migration is changed. No release is
authorized. Automated results and the exact packaged candidate are recorded in issue #318 and the
linked pull request; owner visual acceptance remains open.

The focused JVM run passed 192 tests with zero failures, errors or skips: four domain-preview
tests, 123 Navidrome provider tests, 20 playlist-browsing/source-race tests and 45 smart-playlist/popup
UI tests. XML validation confirmed the removed notice is absent and the compact-row resource is
present exactly once in every one of the 17 catalogs. The native Windows recording must still be
retested; the passing UI geometry checks are not a substitute for that visual check.

The changed shared code also compiled for Android, iOS ARM64 and iOS Simulator ARM64, and
`verifyCoreFirstArchitecture` passed. No platform production file changed. Windows packaging
continues to reuse the unchanged CI-built native DLLs with their hashes checked, as documented
for the preceding candidate; the local workstation does not have the C++ toolchain.
