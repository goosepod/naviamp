# Shared Aurral artist browsing

Tracking: #476, under #383, selected for v2.11.0. Depends on #473 / PR #487.

## Implementation

`NaviampCoreDiscoveryController` is the common owner for artist loading, related-artist navigation,
release-detail entry, feedback, monitoring, requests and preview lifecycle. The existing Home owner
delegates these actions rather than keeping a second implementation. The shared artist destination
accepts source-scoped artist identities, validates MusicBrainz IDs, and preserves the existing
canonical/Subsonic check before Open in library. Catalog/recommendation entry points added by later
issues can use `BrowseDiscoveryArtist` without reconstructing artist state or provider behavior.

Standard and TV Home render the same full-page destination. Artist portraits and placeholders use
the shared Artist shape policy; release cards and detail covers use Album. Existing samples,
biography, genres, release-type filters, feedback, monitoring, request and external/Library actions
remain available. Related artists use the supported native similar endpoint and the same destination.
The added heading exists in all 17 maintained string catalogs. No portable setting or schema changes.

Back follows release → artist → prior artists → origin route. The history retains the artist's
viewport and release filter; the underlying Home rail or prior search state is retained. View state
is scoped to source/account generation as well as artist identity, including when provider decorators
are recreated for a stable connection. Switching artists, opening a release, leaving discovery or
changing source stops the isolated preview. The main playback queue and scrobble path are unchanged.
When leaving an in-flight mutation, the retained page records an uncertain result and blocks the
same action, rather than offering an automatic repeat when the user returns.

Artist metadata, samples and similar artists retain separate results. Samples and similar artists
load concurrently after the primary metadata attempt; metadata failure does not disable those
optional sections. Shared generation checks reject stale metadata/actions after navigation or
source changes. Release requests keep their existing permission and ambiguity policy.

The release entry is a basic native metadata destination needed by artist browsing. Full edition
context, release-track preview controls and richer acquisition behavior remain #477; ongoing request
status/reconciliation remains #478. This change does not implement those issues by implication.

## Architecture review

All production changes are in Core commonMain or provider commonMain. No Android, Desktop or iOS
production file changed. The Gradle task and JVM test probe create an actual desktop review window
for the existing shared UI using synthetic fixtures; they are test infrastructure, not a host-owned
feature implementation. The probe does not load an owner profile, authenticate, acquire music, or
change server settings.

## Verification

The controller regression suite covers deep related-artist back navigation, release return,
viewport/filter restoration, slow sample independence, partial metadata failures, invalid identities,
stale account results, mutation ambiguity and recreated provider wrappers. Existing Home/controller
regressions continue to exercise shared request, feedback, sample and source-reset behavior. Shared
system-back and navigation tests cover the new page and preview cleanup when leaving it.

The rendered page test exercises 360×780, 1100×850 and TV 1280×800, sample keyboard activation,
release-card navigation, Back and related-artist actions. It produces captures when
`NAVIAMP_DISCOVERY_CAPTURE` is set. Existing localized artist-panel regressions and string-resource
validation remain in the focused matrix. Compilation, results and manual acceptance are recorded
below as they complete; an unfinished check is not a pass.

Interactive reproduction:

```sh
./gradlew :core:ui:discoveryArtistWindowProbe
```

This is a visible shared-component probe with synthetic data, not a claim of full production-host
or physical TV acceptance. The production Aurral API contract has separate live evidence in
`473-aurral-contract.md`. Fresh live similar-artist and complete application route acceptance must
be recorded separately; previous #359 acceptance does not cover the new navigation behavior.

## Recorded results

- 85 deterministic regressions pass: 36 presentation/controller/back-policy cases, 17 rendered/UI
  and resource cases, and 32 provider cases. The six opt-in live methods that return early without
  configured credentials are excluded from this total.
- Common Android/JVM/iOS ARM64/iOS Simulator ARM64 compilation and architecture verification pass
  in `build/476-acceptance.log`. No platform production files changed.
- Fresh production-client live verification reports 20 similar artists with Ready status, 337
  recommendations, successful release details/tracks and Ready activity, retaining one user session.
  The transport permits no POST except login. Log: `build/476-live-similar.log`.
- Narrow, wide and TV captures were generated and visually inspected. The artist placeholder is
  circular, release placeholders square, and title, filters, sample and release controls remain
  visible/reachable in the tested layouts. Tests activate samples and Back by keyboard and exercise
  native release/related entry callbacks.
- The earlier synthetic shared-component review window hit an app-access approval timeout. A later
  isolated **production desktop app** session was successfully inspected and operated through
  Computer Use. The previous timeout is not counted as acceptance evidence.

Final review also verifies release-request feedback on the release destination and two abandoned
work cases: Back must not retain a sample spinner whose request was superseded, and an in-flight
feedback toggle must be reconciled before another toggle is offered. These focused reruns and
common compilations pass in `build/476-request-feedback.log` and `build/476-final-controller.log`.
The synthetic review process was stopped after the app-access timeout. No input was sent to an
owner application. PR #487 merged after all nine required hosted checks passed; its accepted main
tree matches the foundation used for these tests.

Follow-up acceptance found that the TV artist destination bypassed Home's content-focus handoff.
The common destination now focuses Back on artist/release entry and when the sidebar hands focus
back to content, and acknowledges the shell's entry generation. A rendered regression checks initial
entry, release entry, keyboard Back and sidebar re-entry. Both page tests, Android/JVM/both iOS
common compilation and architecture verification pass in `build/476-tv-focus.log`. All changed
production files remain common Kotlin.

The `:platforms:desktop:prepareDesktopAurralAppFixture` test task seeds a **new** directory supplied
with `-Pnaviamp.aurral.fixtureDirectory=...`. It reads the existing `NAVIAMP_AURRAL_TEST_URL`,
`NAVIAMP_AURRAL_TEST_USER`, `NAVIAMP_AURRAL_TEST_PASSWORD` and optional
`NAVIAMP_AURRAL_TEST_INSECURE` environment variables. Preparation prohibits network access and uses
the production shared schema/source store. The application applies its normal credential protection
on first launch. Never point it at an owner profile. Launch the production desktop app with child-only
`APPDATA=<fixture>/Roaming`, `-Duser.home=<fixture>` and the existing development-profile option;
isolate both paths so the credential key and settings are also disposable. No private server address
or credentials belong in a committed fixture or command example.

The committed init script makes this isolation explicit, including the development-profile flag:

```sh
./gradlew :platforms:desktop:prepareDesktopAurralAppFixture -Pnaviamp.aurral.fixtureDirectory=<new-absolute-directory>
./gradlew :apps:desktop:run -I scripts/desktop-aurral-acceptance.init.gradle -Pnaviamp.aurral.fixtureDirectory=<same-directory>
```

Windows production-host acceptance (2026-11-02 local): the fresh test profile connected, rendered
Home recommendations/trending artists, and opened Munknörr. Artist artwork was circular; samples,
permissions, release filters and square release covers loaded. Opened Aegishjalmur, confirmed its
title/artist/cover, and returned to the same artist viewport. Started a sample and observed its pause
control while the main player remained Nothing Playing; this does not prove audible output or
nonempty-queue preservation. Opened Gealdýr through Similar artists; its loaded metadata included a
validated Open in Library action. Back restored Munknörr at the related rail. Keyboard Enter on Back
returned to discovery Home. No request, monitoring or feedback mutations were submitted. The
disposable app was closed afterward; the owner's packaged Naviamp process/profile was untouched.
Profile preparation and native/app build passed (`build/476-profile-prepare.log`,
`build/476-production-app.log`). The first launch painted only after resize; the second launch
painted normally. This is recorded as a local presentation observation, not an animation/performance
acceptance result.

Physical touch/TV remote and screen-reader acceptance remain open. The Windows inspection tool
exposed only window-level accessibility nodes; it cannot establish a screen-reader pass. The shared
rendered tests provide narrow/wide/TV layout and keyboard/focus coverage, not physical-device or
assistive-technology acceptance. #476 remains draft with these limits recorded.

The final release-page review also restored the available release date/type context from the
discography/details models, reusing existing translated release categories. The same rendered test
now asserts this context on each layout. Focused rendered tests, Android/JVM/both iOS common
compilation and architecture verification pass in `build/476-release-context.log`. The first hosted
follow-up run failed in SDK setup while unpacking the NDK archive, before shared tests ran; it is
neither a product-test failure nor accepted verification of the follow-up tree.

The complete hosted matrix passed on `924ca4777` (run `41585040510`, all nine checks). Subsequent
automated acceptance found that the isolated TV page did not handle directional focus traversal.
It now delegates D-pad directions to the shared focus manager, allowing unhandled edge navigation
to reach the surrounding shell. Tests enter from Back, reach a sample without pointer focus, return
up to Refresh, and activate the sample with D-pad center. Semantic tests assert artist/release and
section headings, named preview/request actions, disabled requests while busy, and hidden requests
when permission is absent. These are Compose semantics and injected-key checks, not proof of
screen-reader speech. Shared page tests, Android/JVM/both iOS common compilation and architecture
verification pass in `build/476-accessibility.log`; the additional Up/Down regression is recorded in
`build/476-dpad-return.log`.

The user selected continued automated coverage with physical-device checks left open. No test
package was installed on the connected TV device, and its installed Naviamp app was not modified.
Keep the PR draft for physical remote/touch and assistive-technology acceptance; #477 can proceed
on its own dependent branch while this gate remains open.

## November 4: integrate accepted Aurral 2.13 compatibility

Integrated accepted main `c5e119f2f49c721c6816f6b6b6000ab3ae82b996`, including #508.
The artist branch had already moved discovery behavior out of Home, so the merged request policy
now belongs to `NaviampCoreDiscoveryController`: recognized rejections allow an explicit retry;
conflicts and uncertain submissions remain blocked, including at execution time and after related
artist/release navigation and Back. Home continues to delegate rather than regaining a duplicate
implementation. All maintained catalogs retain the artist heading and both request-status messages.

The new navigation regression failed before the controller fix because Conflict was not blocked.
After the fix, 86 focused JVM methods pass without failures, errors or skips: 23 Home/discovery
controller, 47 Aurral/provider HTTP, and 16 rendered artist/discovery UI methods. The imported Home
fixture now uses a valid artist MBID, matching this destination's existing identity validation;
its no-replay and explicit-retry assertions remain intact. Architecture verification and shared
Android, JVM, iOS ARM64 and iOS Simulator ARM64 compilation pass. No platform production file changed.

Reproduction: run `:core:presentation:jvmTest` filtered to `NaviampCoreDiscoveryControllerTest` and
`NaviampCoreHomeControllerTest`, `:providers:navidrome:jvmTest` filtered to `app.naviamp.provider.aurral.*`
and `NavidromeHttpErrorResponseTest`, and `:core:ui:jvmTest` filtered to `NaviampAurralDiscoveryUiTest`
and `NaviampDiscoveryArtistPageTest`, together with `verifyCoreFirstArchitecture`. Compile presentation
and Navidrome with `compileDebugKotlinAndroid`, `compileKotlinIosArm64` and
`compileKotlinIosSimulatorArm64`. The rendered tests exercise narrow/wide/TV layouts; they do not
establish physical-device or screen-reader acceptance. No live mutation was submitted. Current-head
CI and the previously recorded physical acceptance gates remain open; this update is not a merge
or release acceptance claim.
