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

- 82 deterministic regressions pass: 36 presentation/controller/back-policy cases, 14 rendered/UI
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
- The synthetic shared-component review window launched successfully. Computer Use inspection hit
  an app-access approval timeout, so no manual input or live window capture is counted as accepted.
  Full production-host navigation, physical touch/TV remote and screen-reader acceptance remain
  open. The component probe is not a replacement for those checks; #476 must remain open/draft.

Final review also verifies release-request feedback on the release destination and two abandoned
work cases: Back must not retain a sample spinner whose request was superseded, and an in-flight
feedback toggle must be reconciled before another toggle is offered. These focused reruns and
common compilations pass in `build/476-request-feedback.log` and `build/476-final-controller.log`.
The synthetic review process was stopped after the app-access timeout. No input was sent to an
owner application. PR #487 merged after all nine required hosted checks passed; its accepted main
tree matches the foundation used for these tests.
