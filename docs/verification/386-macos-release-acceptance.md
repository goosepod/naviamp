# macOS and iOS simulator acceptance — October 29, 2026

Tracking: #386, release acceptance #280, navigation defect #388 / PR #390, and media accessibility #389 / PR #392.

## Source and scope

Baseline `505dd7e8334d4ff5f218ac7ee46a5796242a3330`, v2.10.0/build 60. The original Mac release ZIP SHA-256 is `c1a7a94e9298bef356174c14a761153faeefaabc07f28e6e8cf8b85ec5699e49`.
The common navigation correction is `0fc1587d`. The performance investigation still used the original baseline: copied replacement JARs had different content-hashed filenames, and the retained launcher referenced the original JARs. That provenance error was caught by the native edit retest; these samples are not fixed-source evidence. The disposable observer copy changes only bundle identity, signature, launcher and isolated home. Production JAR hashes are retained. This is not an installed-app upgrade test.

No release, tag, store submission, mirror or merge is performed. No Windows performance result is inferred from this Mac. The owner has no physical iPhone and explicitly requested simulator testing.

## Automated baseline results

- Complete local gate: 7,497 reported cases, zero failures/errors, two Linux-only MPRIS skips. Includes shared/provider JVM, Desktop native, Android debug/release unit tests, migration verification, the 60% coverage gate and Android native-package verification. The silent real-output preview case passed, retaining the main stream's paused position.
- Verification helper tests: 11 pass, including the four loopback HTTP range cases. The initial sandboxed listener failure was rerun with loopback access; it is not a product failure.
- iOS common simulator: 2,282 cases, zero failures/errors/skips. Device-target application compilation also passes.
- iOS native simulator: all 15 cases pass, including real certificate rejection, valid identities, artwork, uncached BASS playback, seeking and stop. The report confirms `mTLS runner bootstrap supplied`.
- Production Xcode iOS app build and the signed Keychain host test pass. Result bundle reports one passed test and no runtime warnings. Xcode emitted a framework-copy diagnostic despite returning success; the diagnostic is retained, not hidden. The produced app installs and launches.
- Native AppKit popup ownership/stacking: one test passes.
- Navigation fix: baseline rendered regression observes two Home controls instead of one. After the common fix, all 615 UI and 531 presentation cases pass; architecture, JVM, Android and both iOS UI compilations and Mac release packaging pass.

These are reported test executions across targets, not a sum of unique behaviors. Optional live entry points without configured credentials are not live-server passes.

## Visible observations

Mac baseline: TLS track clock advances; paused seek reaches 1:34; resume advances through 1:39. Ordinary loopback original audio also reaches about 4:59 while paused and resumes through 5:19. Native menu opens and Escape dismisses it. About reports v2.10.0/build 60; the Aurral-first shared changelog renders and scrolls through its final entry.

Isolated settings: artist visibility and independent List/Grid choices respond. Native folder export and JSON import work; import restores both Grid choices, Top Tracks visibility and a 25-track limit. Passwords are omitted and reauthentication is requested. Native typing shows a visible caret. Pointer operation of the maximum slider works; attempted keyboard adjustment is not accepted because focus was not established reliably.

The first hand-built HTTP fixture retained inconsistent identity data after its TLS URL was replaced. Its resulting duplicate-source/reconnection errors are excluded from product findings. The disposable source was repaired directly; no compatibility migration or user data change was introduced.

Live snapshot: Library artist search and detail return preserve a known `Brock` query; artist-matched album search returns titles without a literal title match, and album covers remain square. Artist portraits are circular. The artist-playlist lookup finishes. Existing playlists/favorites are not edited. Specific saved-source routing and cross-account results are not inferred merely from a connection label.

Production iPhone simulator: About/build, the full release changelog via real swipe gestures, portrait/landscape layout, Home backgrounding and app-switcher restoration pass. The dedicated simulator was cloned from an existing template, so this is not fresh-profile installation acceptance. Native roles and labels are inspected through Xcode Device Hub. Physical iPhone and spoken VoiceOver acceptance are not claimed.

## Findings and performance investigation

#388: importing while split Now Playing is selected shows both outer and workspace navigation during connection editing. The shared shell now passes its effective visibility decision to connected content; TV keeps its existing outer player owner. No platform production file changes.

#389: loaded bitmap descriptions replace media titles with `Album art` in the native AX tree. A real-bitmap regression reproduces the loss. Common semantics now preserve title/subtitle/metadata and treat bitmaps as decorative. All 616 UI and 531 presentation tests pass, including loaded-image favorite/nonfavorite regressions; architecture, Android and both iOS UI compilations and Mac packaging pass. Native inspection of the packaged `f49c3bf0` build confirms real circular portraits retain artist names and metadata, Home cards retain titles, and row overflow opens and dismisses normally. Spoken VoiceOver is not inferred from the AX tree. No platform production file changes.

Conditions: Apple M1, macOS 27.0.1 (26A434), Metal, packaged JetBrains Runtime 21.0.11, AC power, two 2560×1440 displays at 75 Hz and 1× scale; frame 1000×740, parent surface 1000×712. Initial position (800,300), then (48,600) to avoid tool overlays. No animation or playback cadence changes.

A fully guarded static sample records 0.354% of one core and zero parent frames. Other full-client visibility checks reject the Computer Use pointer indicator or a small WindowServer cursor window. Rejected samples stay diagnostic; zero parent frames and app-only screenshots do not override these rejections. Diagnostic CPU samples (% of one core): progress 3.005, marquee 0.357, combined 5.848, combined after menu 3.814, restored 3.774. Each records zero parent-surface frames. The marquee visibly shifts while paused. A minimized sample confirms the native iconified flag and zero parent frames, but background compilation prevents CPU comparison. The earlier immediate AX query restored the window and is excluded from minimized acceptance. Menu Escape and native zoom/restore pass; a border-drag attempt does not establish narrow resize acceptance.

Metal System Trace attaches and exports successfully. No attributable Naviamp active GPU intervals are present in the export. Whole-desktop WindowServer compute/fragment/vertex totals are 309.375/2364.178/478.000 ms; other apps, capture and simulator activity prevent app attribution. This does not demonstrate zero GPU cost. The full performance gate remains open; no animation cadence or budget is relaxed.

## Local evidence and outstanding work

Evidence is in ignored `build/macos-complete-2026-10-29` in `/private/tmp/naviamp-386-macos`: complete suite log/summary, iOS logs/result bundle, native popup log, display/power metadata, original payload hashes, isolated profiles, probe phases, before/after visibility checks and diagnostic source. Credentials and raw private profile copies are not committed or uploaded.

## Fixed-source package and live provider acceptance

The final acceptance app uses the complete `f49c3bf0` production bundle and its matching launcher configuration, with only an isolated home appended. Every selected classpath JAR matches the validated production bundle hash. It keeps the production bundle identifier and uses the existing Apple Development identity. Renamed disposable identities could not reach the LAN host despite TLS skipping, while the production identity connects. This is evidence about the test packaging/OS boundary, not proof of a general server or Local Network permission defect. No OS trust, network or privacy settings were changed.

Native connection editing with split Now Playing selected hides the player and presents one navigation bar; opening the source page exposes the edit form with the same single bar. Cancelling restores the split player and one bar. This verifies the common #388 correction on the actual fixed-source package. Baseline JSON import/export and its regression test cover the corresponding import-triggered state.

Three configured provider live tests pass: native discovery and OpenSubsonic share the connection/TLS policy, samples/release covers use the provider transport, and an owned artist bridges through verified library identity. The supplied account connects in the packaged app with TLS skipping enabled only for its test profile. Recommendations, trending, artist details, sample Play → Pause → Play, and Singles filtering work. One single-release album request returns `Request queued.`; independent read-only inspection in the owner's already-open Aurral browser confirms the exact artist/title in its server queue. This confirms submission, not completed acquisition. Existing monitoring, feedback and favorites are not modified.

The original installed profile's settings, database and window-properties hashes remain unchanged. No test password or raw private profile is committed or uploaded.

Remaining limits: strict visible-animation/GPU acceptance, physical-device behavior, unavailable receivers, completed remote acquisition, spoken accessibility and a fresh-profile iOS install are not established. The owner requested simulator-only iPhone testing. Android native emulator verification on this Mac is being attempted on a separate disposable API 34 ARM image; the existing user emulator remains untouched. The complete hosted cross-platform matrix for #390 passes, including iOS simulator/native/production/Keychain checks. The #392 hosted Mac and Linux native jobs pass; remaining jobs are pending at the last observation.
