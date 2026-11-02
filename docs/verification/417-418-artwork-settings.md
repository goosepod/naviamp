# Album artwork display settings

Tracking: #417 (square album corners) and #418 (filled Now Playing artwork), selected for v2.11.0.

## Square album artwork

`InterfaceSettings.squareAlbumArtwork` defaults to false and is serialized through the existing
common settings document, local value store and trusted-device settings sync. The shared shape
policy applies right-angle corners to Album and Track artwork, including placeholders. Artist
portraits remain circular; playlists and radio stations retain their own shapes. Shared artwork
and row contracts carry explicit media kinds, including the TV grid and hero components.

The control appears under Experience > Albums and in TV Display settings. All 17 maintained
string catalogs include its label and explanation. There are no schema or platform-production changes.

Validation: the settings mapping suite (14 cases), settings value-store suite (8 cases), and
selected artwork/continuity/rendered suite (8 cases) passed. After the final row-contract audit,
the shape-policy and rendered corner-toggle cases passed again, along with common Android, JVM,
iOS ARM64 and iOS Simulator ARM64 compilation. Architecture verification passed. The rendered
test checks both loaded images and placeholders, preserves artist circles, and confirms that
toggling the preference does not reload the image.

Existing owner AVDs contain valuable data and must not be treated as disposable. This work uses
shared compilation and desktop rendering; any future destructive Android acceptance needs a
separately created test AVD.

## Filled Now Playing artwork

`NowPlayingDisplaySettings.fillArtworkArea` defaults to false. The shared cropped artwork layer
has a remembered dark gradient, with existing metadata and playback controls in the foreground.
The background contains one decoded image and does not read playback progress. Per owner review,
filled artwork applies only to the single-pane player. Wide Adaptive, Full, and docked Standalone
layouts use their previous artwork-and-controls presentation, even when the preference is enabled.
Visible lyrics and visualizers take precedence. TV retains its existing wide presentation.
Both preferences are available together under Experience > Player; TV Display exposes square corners.

All 17 maintained string catalogs were updated. Settings serialization, independent combinations
of the two preferences, old-export defaults, normalization and durable round trips are tested.
No Android, Desktop or iOS production files changed.

Validation: 15 settings mapping, 8 settings-store and 5 selected shared artwork/rendered test cases
passed. Common UI compiled for Android, JVM, iOS ARM64 and iOS Simulator ARM64. Architecture
verification and the staged macOS app build passed. Rendered tests exercise 320×720 (including
1.4 font scale), 640×420 and 1200×760 layouts, transport actions, 60 progress updates without an
artwork reload, and TV transport focus. Captures were inspected in
`/private/tmp/naviamp-artwork-captures/`.

The visible Mac review app is `/private/tmp/naviamp-artwork-review-ogzelx7l/Naviamp Artwork Review.app`,
with its own bundle ID, executable and fresh profile. Both switches were verified in that app.
The owner connected the test app and reviewed real artwork while resizing. The initial connection
reported “No route to host”; retrying the same entries succeeded with Local Network access enabled.
CPU/parent-surface/compositor measurements and hide/restore checks are still pending. Rendered tests and
a cached graphics layer do not establish a live application performance budget pass. The temporary
measurement wrapper was removed from the review launcher before handoff. PR acceptance remains
open for connected playback and the owner's visual review.

## Owner review corrections

The owner's Home screenshots exposed a card-level rounded clip outside the shared artwork.
Home album/track cards and the legacy album Mix card now resolve their outer clip through the
shared shape policy. Artist cards retain their existing outer shape and circular portraits.
A rendered Home-card test checks all four corners of loaded covers and placeholders while toggling
the setting, and verifies that artist corners remain transparent.

The owner's final layout direction supersedes the initial wide-fill implementation: filled artwork
is limited to single-pane Adaptive layouts below the wide breakpoint. Regression cases include the
876×728 transition, docked Standalone, Full, and TV with the setting enabled; these must contain no
filled backdrop. No platform production files changed.

Final correction validation: all 6 selected UI test cases passed, alongside Android/JVM/iOS ARM64/
iOS Simulator ARM64 compilation, architecture verification and staged Mac packaging. The final
log is `/private/tmp/naviamp-artwork-owner-fixes-final.log`; captures include docked 400×760 and
wide 876×728/1200×760 fallbacks. The same Mac review bundle was updated in place after quitting it;
its connected profile was preserved and backed up beside the app before replacement.

## Filled viewport corners

A second owner screenshot showed the rounded thumbnail preference clipping the filled player
background. Filled artwork now clips only to rectangular viewport bounds, independently of the
square-album preference. A rendered regression checks all four outer pixels with the preference
both off and on and asserts identical coverage. All 7 selected artwork tests passed; both iOS
compiles, architecture verification, Mac packaging and Android debug assembly passed in
`/private/tmp/naviamp-artwork-viewport-phone.log`.

The Mac review bundle was updated and relaunched with the connected profile preserved and backed
up. A visible screenshot of the same narrow player confirmed full top-corner coverage with square
corners off. The debug APK signature was verified and the APK installed successfully on the owner's
connected Pixel 10a as `app.naviamp.android.v2test` (Naviamp v2 Test), alongside the existing release
package. Installation and package version were verified; no app data was cleared and no AVD was
changed. Phone visual acceptance remains with the owner.

## Filled-player control spacing

The owner accepted Android keyboard behavior and both artwork corner modes, then reported that the
filled player packed controls too tightly at the bottom. A physical Pixel screenshot confirmed
minimal gaps between the waveform, identity, rating/audio information and transport groups.
Filled-player details now use 8 dp group gaps (6 dp at compact heights), 4 dp identity-line gaps,
and 4 dp between rating and audio information. Existing button sizes and other player layouts are
unchanged. The regression checks minimum rendered gaps and visible bottom actions, including a
412×818 usable Pixel viewport and 320 dp width with 1.4 font scale; both layout tests passed.
The 412×818 capture was inspected. No platform production files changed.
