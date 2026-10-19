# Smart-playlist summary and preview follow-up (#314)

The favorites playlist in the owner’s 2026-10-19 screenshot was summarized as “Favorite is yes”.
Its preview could not apply the selected-library restriction because some cached tracks had no
library membership. Showing every favorite instead would silently change the playlist’s meaning.

The summary now says **Favorite tracks**, explains sort direction in ordinary words, omits redundant
single-rule ALL/ANY wrappers and shows selected libraries by name. Multiple-rule logic and sort
precedence remain explicit. Favorite albums/artists, artwork, compilation and missing-file filters
also use readable positive and negative phrases. The definition, rule operands, IDs and saved JSON
are not changed by summary rendering.

Navidrome metadata sync now prefers its existing native song mapping, which retains library IDs,
favorites and ratings. If the native API cannot be read, the existing Subsonic enumeration remains
available; a library ID is only filled from a known scoped request, never guessed. An explicit
numeric Library ID condition is also evaluated as a number rather than compared with a string.

Before the cache is refreshed, the preview explains what is missing and points to **Sync library**.
If the server still omits the information, preview remains unavailable and says why. It never
ignores the selected libraries or reports zero matches based on missing metadata. Other unsupported
fields use their translated names rather than wire identifiers. Sync remains an explicit action.

## Manual retest

1. Open the same saved favorites playlist. Expect **Favorite tracks**, **Sort by: Favorite tracks
   first**, **At most 500 tracks**, and the selected library names. Confirm it still saves/reopens
   with the same rules, sort, limit and library restrictions.
2. If the old cache blocks preview, read the explanation and choose **Sync library** once. When the
   server supplies library membership, examples should return. Verify examples belong to selected
   libraries and meet the favorite rule. Samples remain local and do not promise final server order.
3. Check a non-favorite condition and a nested ALL/ANY playlist, including multiple sort priorities,
   a playlist reference, a date/number range and an exclusion. Options/Advanced collapse must retain
   values. Empty or unsupported local data must remain distinguishable from a valid zero-match result.
4. Check a narrow window and a long-label translation; the summary and preview must wrap readably.

## Verification

- **204 focused JVM tests passed:** 73 domain smart-playlist tests, 122 Navidrome provider tests,
  and nine rendered summary/preview tests. Zero failures, errors or skips. Coverage includes native
  metadata, native failure fallback, missing membership, numeric library filtering, effective boolean
  negation, nested logic, sort precedence, named libraries, unchanged definitions and the Sync-library
  preview recovery action.
- Domain, provider and shared UI compiled for Android, JVM, iOS ARM64 and iOS Simulator ARM64.
  `verifyCoreFirstArchitecture` passed.
- All 21 new string keys are present in the 17 maintained catalogs; XML parsing and duplicate-key
  checks passed. The rendered missing-metadata explanation was visually inspected in
  `core/ui/build/reports/smart-playlist-library-preview.png`.
- [Six phone checks](android-2.10.0-2026-10-19.md) passed on the preceding candidate `95345b34`.
  Those establish that candidate’s Android startup, recovery and certificate playback behavior;
  they are not a physical-device acceptance claim for the revised summary.

Product changes are confined to domain, shared UI and provider `commonMain`. No Android, Desktop or
iOS production file, setting or schema changes. Owner acceptance of #314 and full current-source CI
remain separate gates. No release or tag is authorized.
