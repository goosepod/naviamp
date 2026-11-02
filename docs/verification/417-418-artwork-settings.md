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
The owner completed connected Mac and physical Pixel review and accepted the final artwork and
spacing behavior. Live measurement results and limitations are recorded below. The owner review
launcher remains uninstrumented; measurement used a separate synthetic fixture profile.

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
changed. The owner subsequently accepted the artwork behavior on the phone.

## Filled-player control spacing

The owner accepted Android keyboard behavior and both artwork corner modes, then reported that the
filled player packed controls too tightly at the bottom. A physical Pixel screenshot confirmed
minimal gaps between the waveform, identity, rating/audio information and transport groups.
Filled-player details now use 8 dp group gaps (6 dp at compact heights), 4 dp identity-line gaps,
and 4 dp between rating and audio information. Existing button sizes and other player layouts are
unchanged. The regression checks minimum rendered gaps and visible bottom actions, including a
412×818 usable Pixel viewport and 320 dp width with 1.4 font scale; both layout tests passed.
The 412×818 capture was inspected. The owner accepted the updated spacing (“That is much better”).
No platform production files changed.


## Visible application performance — November 2, 2026

Measured production code: integration commit `71b5a77284cf8f910941029b61f034293391142e`,
containing the final #417/#418 UI plus the independently tracked #482 keyboard fix and accepted
#391 startup fix. Updating #418 from main did not change the measured artwork implementation.
The packaged app was staged normally; only a separate copied launcher's entry point was replaced
with the existing `ApplicationRenderProbe`. Production JARs and playback cadence were unchanged.
The normal production entry point was restored and launched separately afterward; the player and
moving metadata rendered successfully. The connected owner review app was never instrumented.

Hardware: Apple M1, Metal, AC power; two 2560×1440 displays at 75 Hz. Every accepted phase used
380×780 outer / 380×748 client bounds, scale 1×1, at (10,80). Native CGWindow guards checked the
full client for occlusion before and after each visible phase. CPU is percentage of one core over
the observer's ten-second interval; the compositor bracket was approximately twelve seconds.
There were no UI calls or profiling attachment during the CPU interval. Visible CUA captures
independently confirmed real cover pixels, text movement and advancing playback. Observer physical
capture flags are false because CUA supplied screenshots; they are not claimed as automatic pixel
acceptance. Compilation attribution is unavailable in this trimmed JVM.

| Phase | App CPU | Parent frames | Whole-device GPU mean | WindowServer CPU |
| --- | ---: | ---: | ---: | ---: |
| Filled, paused, short labels | 0.509% | 0 | 11.00% | 47.70% |
| Filled, progress only, actual playback | 3.709% | 0 | 16.33% | 51.37% |
| Filled, marquee only, paused | 0.458% | 0 | 15.25% | 49.98% |
| Filled, combined, warmed | 3.028% | 0 | 15.00% | 49.06% |
| Previous inset layout, combined | 4.235% | 0 | 14.75% | 50.14% |
| Previous inset layout, confirmation | 3.196% | 0 | 15.08% | 99.73% |
| Filled, restored, paused marquee | 0.544% | 0 | See raw counters | See raw counters |

The scoped acceptance budget is <1% app CPU and no continuous parent redraws for paused static
and marquee states, and <=1 percentage point additional sustained app CPU compared with the
existing layout during actual audio playback. Those comparisons pass; actual audio decoding and
native playback are included in the 3–4% playback figures, which are not a claim of <1% total
playback CPU. The new image layer introduces no independent animation loop. Common animation
policy and native presenters were not modified.

GPU utilization came from `ioreg -r -c AGXAccelerator -l` Device/Renderer/Tiler counters;
WindowServer CPU came from process CPU-time deltas. Both cover the whole desktop, not this app.
The high WindowServer confirmation sample demonstrates background noise and must not be attributed
to Naviamp. Filled/inset GPU counters are comparable but do not prove isolated per-window GPU cost.
A separate twelve-second Metal System Trace attached to the progress PID contained no app Metal
interval rows, consistent with zero parent draws; missing rows are not zero GPU-time proof.
Raw system traces stay local because they contain other processes. Numeric samples are retained in
[418-artwork-performance-2026-11-02](418-artwork-performance-2026-11-02/).

Retained exceptions: the initial combined phase was 6.534% with two parent frames. A repeat on the
same PID after settling was 3.028% with zero frames; no compiler-causation claim is made. The earlier
`filled-static-1` sample was obscured by Firefox and `filled-progress-warm` failed its post-window
guard: neither is accepted. CUA's persistent pointer annotation also caused guard rejection; those
attempts produced no accepted sample. New clean app processes were used with the same fixture and
window geometry, and all samples (including rejected/outlier results) are retained.

The owner manually minimized for twenty seconds and restored for fifteen seconds, confirming the
functional lifecycle check. The observer's wait expired before that action, so **hidden CPU was not
captured and has no passing measurement**. A separate post-restore visible phase passed at 0.544%
and zero parent frames, and moving metadata was observed again. Native minimize/hide automation
attempts did not change CGWindow visibility and are not counted as lifecycle acceptance. This is
an explicit measurement limitation, not a claim of a new hidden-state performance fix. Resize,
clipping, input, pause, font scaling and accessibility are covered by owner review, rendered tests,
and the live accessibility tree; all transport actions remained exposed.

### Reproduction

Use a fresh test-only profile and the existing observer instructions in
`scripts/animation-probe/README-visible-app.md`. Start the loopback synthetic source:

```sh
python3 scripts/android-tv-fixture.py --silent --mixed-metadata --tracks 2 --albums 2 --artwork --port 18081
```

Connect with fixture credentials, enable metadata scrolling, and use the short first track for
static/progress isolation and the long second track for marquee/combined. Pause/play supplies the
two states; repeat with filled artwork disabled at identical size/scale/power. The two repository
PNG fixtures are the exact opaque 600×600 covers used for the accepted captures. Do not substitute
a blank surface. No owner server, profile, phone data or existing AVD is needed or modified.
`--artwork` is opt-in; existing fixture catalogs retain their previous defaults. The fixture's
range, cover decoding, content length, CRC and old/default behavior tests pass (29 verification
script tests overall). Request phases through the observer's phase file, guard native visibility
before and after, and retain failed as well as passing intervals.
