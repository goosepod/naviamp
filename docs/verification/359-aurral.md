# Aurral connections and native discovery

Tracking: [#359](https://github.com/goosepod/naviamp/issues/359).

## Baseline and release scope

The branch `feature/359-aurral` starts from accepted main commit
`30dc00e6e9c36c0f4b276bbce15cfdc0b82838a6` (PR #287). Main now contains the combined
unreleased v2.10.0 candidate and identifies itself as v2.10.0/build 60. The latest public release
remains v2.9.0; the old v2.10.1 metadata was an erroneous unpublished bump, not a public release.
No release or tag was created or changed during this work.

The owner may include Aurral in v2.10.0 depending on app-store approval timing. This branch does
not bump the version, select final release scope, or add unfinished work to main. Update the shared
About changelog and release notes when its release scope is accepted, before any release tag.

## Connection and discovery foundation

- Aurral is a distinct shared connection identity, immediately after Navidrome, with the
  owner-supplied official icon. The SVG and raster provenance are retained under
  `docs/assets/service-icons/`.
- OpenSubsonic browsing, playback transport, favorites, playlists and lyrics reuse the existing
  shared engine. The Aurral profile does not invoke Navidrome native login, smart playlists or
  canonical-ID migration. It supports stream transcoding but original-only downloads and does not
  advertise track ratings.
- Editing a generic Subsonic connection and selecting Aurral explicitly preserves its credentials,
  TLS preference and library selection. The existing preferred-source update retains the source ID
  and cached media. No schema migration is needed and arbitrary servers are not auto-reclassified.
- Aurral's native client lives in provider `commonMain`, in the existing shared Subsonic provider
  module. It reuses the connection's HTTP transport, TLS policy and custom headers. Bootstrap API
  v1 plus the `discover` feature gates native calls. Native login uses a user bearer session held
  only in memory; it never requires the installation administrator API key. Expiry permits one
  sign-in on a subsequent load; a 401 is surfaced without a background sign-in retry loop.
- Core owns Home orchestration, source-change rejection and external navigation. Recommendations
  and trending artists appear independently of ordinary Library/Home content, including the
  shared television presentation. Errors and missing native support do not disable Subsonic.
- Discovery IDs remain separate from playable Library IDs. Open in Aurral is an explicit external
  action for valid MusicBrainz identities. Missing IDs retain an informational card without
  inventing a playback match. Artist images and placeholders use the shared circular shape policy.
- All 14 new labels/statuses, including the service label, exist in all 17 maintained catalogs.
  Existing shared settings serialization carries the provider identity, TLS preference and folder
  selection. Existing secret filtering excludes passwords and bearer/native tokens.

## Verification

The first checkpoint on 2026-10-27 passed **63 focused tests**, with zero failures, errors or
skips: 24 provider/session cases (including the explicitly configured live case), 20 domain/settings
cases, eight Core Home cases, two rendered UI cases and nine shared storage cases. Shared Android
compilation and both iOS ARM64/Simulator ARM64 targets passed for the provider and presentation
graphs, as did `verifyCoreFirstArchitecture` and version validation (v2.10.0/build 60). These are local
results; a full hosted cross-platform Verify run and the live application acceptance below remain
separate gates.

The owner's LAN test server reports Aurral 2.12.0, app API v1, with `discover`, `flows`, `activity`,
`review`, `trackDownloads`, `linkSearch` and `downloadCancel`. The supplied test user is an ordinary
user with `accessFlow=false`. At the first checkpoint, personalized recommendations were queued/building and trending artists
were available. No account permissions, recommendations, library files, favorites or playlists were
modified by the read-only live test.

The opt-in `AurralLiveTest` exercises the production client with the connection's explicit
self-signed-certificate opt-out: Subsonic validation, artist/album browsing, album tracks, artwork,
audio-range transport, native login and personalized discovery. It sends no scrobbles. An audio-range
request establishes transport, not audible playback or full native-player/UI acceptance.

Run it without storing private credentials in source:

```sh
export NAVIAMP_AURRAL_TEST_URL=https://your-aurral-server
export NAVIAMP_AURRAL_TEST_USER=your-test-user
export NAVIAMP_AURRAL_TEST_PASSWORD=your-test-password
export NAVIAMP_AURRAL_TEST_INSECURE=true # only for the explicitly trusted self-signed test server
./gradlew :providers:navidrome:jvmTest --tests '*Aurral*'
```

Without the URL, this opt-in entry point returns without a network test; do not count that as live
acceptance. The test was explicitly configured for the recorded LAN verification.

Common tests cover bootstrap compatibility, missing features, token-only connections, session reuse
and expiry, permission and HTTP failures, malformed JSON, cancellation, missing MusicBrainz IDs,
reverse-proxy subpaths and custom headers. Shared session tests cover Aurral routing/restoration
without Navidrome native operations. Domain/storage tests cover explicit conversion, portable
export/import normalization, secret exclusion and cached-media retention after store recreation.
Core tests cover account changes during discovery, failure isolation, and external actions without
Library navigation. Rendered tests cover icon ordering, connection conversion and loading/building/
permission states; their connection and discovery captures were visually inspected.

Reproduce the focused portable checks:

```sh
./gradlew \
  :providers:navidrome:jvmTest --tests '*Aurral*' --tests '*NavidromeCoreProviderSessionPortTest*' \
  :core:domain:jvmTest --tests '*ProviderCatalogTest*' --tests '*SettingsSyncMappingTest*' \
  :core:presentation:jvmTest --tests '*NaviampCoreHomeControllerTest*' --tests '*NaviampCoreNavigationControllerTest*' --tests '*NaviampCoreActionGraphTest*' \
  :core:ui:jvmTest --tests '*NaviampAurral*' \
  :core:storage:jvmTest --tests '*StorageMediaSourceStoreTest*' \
  :core:presentation:compileDebugKotlinAndroid :providers:navidrome:compileDebugKotlinAndroid \
  :core:presentation:compileKotlinIosArm64 :core:presentation:compileKotlinIosSimulatorArm64 \
  :providers:navidrome:compileKotlinIosArm64 :providers:navidrome:compileKotlinIosSimulatorArm64 \
  verifyCoreFirstArchitecture
```

Set `NAVIAMP_AURRAL_CAPTURE` to a local directory to capture the rendered fixtures. These are
component renders, not screenshots of a live application window.

## Native artist details and feedback

The next increment adds an in-app artist dialog with biography, release listings and
More like this / Less like this / Block artist toggles. Feedback is read from and saved to the
current Aurral user's session. Pressing a selected toggle removes it. Changing More/Less removes
its opposite first, matching Aurral's web client. A partial mutation failure is not reported as
success: the controls are disabled until Refresh reads the actual server state. Legacy name-only
feedback is recognized, while a different artist's explicit ID is not treated as a name match.
Blocked artists can be unblocked from the still-open dialog after the discovery feed removes them.

Open in Library is enabled only after an exact MusicBrainz match in an available native canonical
artist row and a second identity check against the actual Subsonic artist response. Neither native
numeric IDs nor display-name matches are used as playable IDs. Metadata failures or unavailable
feedback/Library routes retain independent, truthful states. Release rows are virtualized so an
artist with hundreds of releases does not render them all at once.

Core owns dialog requests, cancellation/source-change rejection, Library navigation and feed reloads
after feedback. Dismissed dialogs cannot be reopened by late requests. Queued mutations retain the
account/detail generation captured at dispatch, including equal-valued commands for different
sessions. Older feed reads cannot replace a newer post-feedback feed. Selecting Home explicitly
closes the dialog; returning from an owned Library artist retains the discovery context.

Final local verification of this increment passed **44 focused tests**, with zero failures,
errors or skips: 16 provider cases (including all three explicitly configured live cases), 25 Core
Home/navigation/action cases and three rendered UI cases. Shared Android and both iOS target
compilation, the architecture guard and desktop packaging validation also passed. The earlier
connection/settings/storage checkpoint retains its separate 63-case evidence above.

The configured live tests passed on the owner's server for an owned artist's exact native-to-Subsonic
identity bridge and feedback changes. Each feedback run uses a unique UUID/name, changes only that
temporary record, and removes its entries in a finally block, even after a lost POST response.
No existing feedback is reset, no downloads are requested, and no Library files are modified.
Set `NAVIAMP_AURRAL_TEST_FEEDBACK=true` in addition to the earlier live-test variables to enable this
explicit temporary-mutation check. Without this flag it does not run a feedback mutation.

Protocol behavior was checked against upstream source snapshot `084f0208e737a35e49a96f9de0538be190d8f6e4`
([feedback handling](https://github.com/lklynet/aurral/blob/084f0208e737a35e49a96f9de0538be190d8f6e4/backend/services/discovery/feedback.ts),
[Library lookup](https://github.com/lklynet/aurral/blob/084f0208e737a35e49a96f9de0538be190d8f6e4/backend/routes/library/handlers/misc.ts),
[Subsonic identities](https://github.com/lklynet/aurral/blob/084f0208e737a35e49a96f9de0538be190d8f6e4/backend/services/subsonicLibraryService.ts))
and validated against the owner's deployed 2.12.0 server, rather than assuming current main is shipped.

Eight additional messages are translated in all 17 catalogs (22 new strings in total). No new
portable setting, database schema or platform production code was introduced. The rendered artist
dialog, including its failure state and circular placeholder, was captured and visually inspected.

The desktop package was built and validated with `:apps:desktop:stageLocalTestApp`. A temporary copy
under `/private/tmp/naviamp-aurral-ui/` was given its own empty `user.home`, distinct bundle ID and
ad-hoc signature for visible verification. Computer-use `getApp` timed out with server error -10005
while opening that copy. The copy did start, but no visible-app result is claimed; its exact process
was then stopped. The normal running app/profile was not changed. Component renders and API tests
are not substituted for the remaining live application/device checks.

## Remaining issue scope and release gates

This work does not close #359. Follow-on native work includes release detail pages, broader native
search, permission-aware acquisition/activity and flow management. Do not expose these as available merely
because a server advertises another API feature. Each mutation needs request/response/permission
fixtures and ordinary-user failure coverage before wiring its complete common behavior and UI.

Before release acceptance, finish live application add/edit/convert/restart/playback and account-switch
checks, actual cross-device settings sync, physical Android/iOS/TV interaction, focus/accessibility
and localized/narrow-layout checks. The current read-only live test cannot establish these results.
No continuous-animation policy or cadence changed in this increment.

## Platform diff accountability

The Aurral diff changes no Android, Desktop/JVM or iOS production source. Native HTTP/TLS engines,
secure-secret storage, player and external-URI adapters are reused. New JVM source files are tests.
The previously merged baseline's native adapter review remains in the
[v2.10.0 readiness record](2.10.0-release-readiness-2026-10-26.md#platform-diff-review).


## Discovery portraits and rail navigation

The discovery feed may return `image: null`. The provider now publishes a lazy artwork reference
for valid MusicBrainz artist IDs and resolves it through Aurral's authenticated artist-cover endpoint
when the shared image cache requests its bytes. It prefers the front portrait, accepts the alternate
feed `imageUrl` field, and retains circular placeholders when no cover exists. Native credentials
and custom proxy headers are sent only to the connection's own server, never to remote artwork hosts.
No per-card cover lookups are performed while loading the feed.

Recommendations and trending use independent lazy rows with the shared Home arrow controls. Each
arrow reflects that row's scroll boundary, and scrolling one row leaves the other unchanged. Arrow
accessibility labels now come from all maintained shared translations. Artist portraits retain the
shared circular shape policy. These changes add no platform production code, settings or migrations.

Regression coverage includes missing/alternate artwork fields, authenticated lazy cover resolution,
front-image selection, empty/unsafe cover responses, external credential isolation, rendered portrait
pixels and circular clipping, independent rail navigation and both scroll boundaries. The opt-in live
case resolves missing discovery portraits through the actual provider and decodes the returned bytes.

Validation for the portrait/rail update: 20 focused provider/UI fixture tests passed, including the
rendered pixel/clipping test and independent arrow navigation. Three opt-in read-only live cases
passed against the configured Aurral account, including real discovery-cover byte decoding; the
feedback mutation case was not enabled for this run. Android provider/UI and iOS provider/device plus
UI/simulator compilation passed. Architecture verification and Mac test packaging passed. A separate
updated test copy uses the previously verified isolated profile and local-network metadata, and its
Apple Development deep/strict signature verification passed. Owner verification of this updated
foreground app remains pending.
