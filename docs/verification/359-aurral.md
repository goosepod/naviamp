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


## Artist card activation and additional native actions

The full actionable artist card now opens its native popup: portrait, name, genres and card background
share one accessible button action. The separate View artist control is removed. External-only cards
keep their existing external destination; unresolved cards do not invent a destination. The regression
case taps portrait, name and card background separately, verifies the same native identity each time,
and checks external and unresolved behavior. No new user-facing strings or platform code are added.

The following native APIs underpin the popup actions now implemented below:

- Artist sample metadata and Deezer preview URLs are exposed by the artist-preview API. Availability
  depends on the returned tracks; a missing sample must not masquerade as full-library playback.
- Native track and album requests can ask the server to acquire music. This is distinct from Naviamp
  offline downloads of existing Library files. Requests need the appropriate user permission and
  configured server sources; capability flags alone do not guarantee success.
- The popup offers More/Less/Block feedback, a verified owned Library link, Open in Aurral,
  native samples, acquisition requests and artist monitoring. Full release/track drill-down and
  ongoing activity management remain follow-on work.

References: [endpoint reference](https://docs.aurral.org/api/endpoints/) and
[app contract](https://docs.aurral.org/api/app-contract/). Server-side requests should preserve the
normal user session, permissions, identity namespaces, configured-manager behavior and truthful queue
status; they must not require the installation administrator API key.

Artist-card activation verification: all six focused shared UI tests passed, including three physical
tap locations, external/unresolved cases, circular portrait pixels and independent rail navigation.
Android and iOS simulator UI compilation, architecture checks and Mac packaging passed. The new
isolated test copy passed Apple Development deep/strict signature verification and retains the
previous test profile and local-network metadata. Its foreground owner acceptance remains pending.


## Release covers, filters, previews, requests and monitoring

Release rows now show lazy square covers from cached cover URLs or the authenticated native
release-group cover endpoint. Primary and secondary MusicBrainz types use the same shared classification
as Library albums. All releases and the present type chips sit immediately above the responsive release cover grid, below artist information and samples. Filter
selection is transient UI state, not a new exported preference. Artist images remain circular.

Opening or refreshing native artist details automatically loads available Deezer previews. Inline play/pause uses the common queue and engine path without opening the full Player, with
reserved sample IDs and the server's stable public preview-media endpoint. Full-track duration and
Library identities are never copied onto a preview. Sample audio bypasses owned/cached audio, audio
prefetch, disk recovery downloads, library sidecars, presence/scrobbling and Sonic Autoplay seeds.
Now playing keeps transport controls but hides favorite/rating/radio, playlist and lyrics actions for
samples; queue menus are restricted to queue management. Sample artwork is a remote cover URL.

Request track and Request album use user-authorized Aurral acquisition APIs, separate from offline
Library downloads. They respect session permissions, the track-download capability and normal manager
selection. Results distinguish queued/searching, already queued, available/owned, saved and missing
sources. Requests reserve shared busy state at dispatch, reject duplicate submissions, and never retry
a mutation automatically. A failed or ambiguous result directs the user to check Aurral; that action
remains blocked until an explicit refresh. Dismissed dialogs and changed accounts reject stale commands
and results. No admin API key or automatic download-client setup is used.

Artist monitoring uses the current server manager. Aurral-managed artists use server-default monitoring
rules; Lidarr-managed artists use its all/none monitor option. The control requires the relevant user
permissions, and new artists also require add-artist permission. A successful write is verified by
reading monitoring state again. Stopping monitoring does not issue any delete-file request.

New action, status and filter strings are present in all 17 maintained catalogs. No platform production
source, settings schema or database migration was added. Common tests cover covers/types, preview
eligibility/media routing, permission and capability failures, operation outcomes, no automatic retries,
manager-aware verified monitoring, stale/duplicate commands, playback engine routing, cache/reporting
isolation, type filtering, square covers, busy controls and typed UI action callbacks.

Read-only live checks decoded release cover images and fetched MP3 preview bytes through the real
provider connection/TLS transport. They also rechecked discovery images and the verified owned-artist
bridge. Acquisition and monitoring mutations were tested with HTTP fixtures, not issued against the
owner's live library. Foreground audible playback and owner acceptance of the new request/monitoring
controls remain pending. Hosted CI is required for the new feature head.

Final local verification passed: the provider fixtures and four enabled read-only live cases, eight
rendered/capability UI cases, ten Home controller cases, 38 playback adapter cases, seven action-graph
cases, 17 listen-reporting cases, five presence-reporting cases, release classification/audio-source
fixtures and 12 Sonic Autoplay cases. The feedback-mutation live case was not enabled. Android shared
presentation/provider and iOS simulator presentation/device provider compilation, architecture checks
and Mac packaging passed. The rendered popup was inspected with type filters, square release art,
sample/request/monitor controls and status/busy states. This does not establish foreground audible
playback or live acquisition/monitoring mutation acceptance.


## Inline samples and responsive release grid

Artist details now auto-load samples after publishing artist metadata. Loading, empty and failed sample
states are explicit; the Load samples command/button and its obsolete translations are removed.
Shared generation/source guards reject late sample responses after another artist/account is selected.
Refresh reloads the artist and its samples; loading samples never starts audio automatically.

The compact sample presentation follows the reference's numbered title/album rows: two columns on
wide panels, one on narrow screens, in column-major order. The active sample is highlighted, with
play/pause/resume and a separate acquisition icon. Playback uses the existing shared engine and keeps
the artist popup open. Normal full-player queue starts retain their existing navigation. Discovery
receives only track identity and transport state, not position ticks; there is no new animation timer
or continuously repainted progress indicator.

Release filters now follow artist information and samples, directly above the albums. Releases are
lazy, responsive rows of square cover cards with title/year/type below and a permission-gated request
icon. Available width determines the column count, with no nested unbounded scrolling grid. The same
shared panel is used in the popup and desktop/phone rendering fixtures. Artist portraits retain the
circular shared shape and releases keep the album shape policy.

No persisted setting or platform production source is introduced. New loading copy uses every
maintained translation. Regression checks cover automatic loading/stale response rejection, inline
play/pause/resume/switching without Player navigation, progress-independent state projection, sample
column order, filter placement, release cover shape and metadata placement, responsive phone/desktop
rendering, and acquisition action/busy behavior.

Inline/grid verification: all 10 focused Aurral UI cases, 11 Home controller cases and six queue
playback cases passed. The shared panel was rendered and inspected at 1100×900 and 390×844;
geometry checks verify two-column samples on wide layouts, one column on phones, square covers in
adaptive grid rows, metadata below artwork, filtering and separate acquisition controls. Android and
iOS simulator presentation compilation, architecture verification and Mac packaging passed. These
checks add no live acquisition mutations and do not replace foreground audible playback acceptance.

Sample transport icon regression: the connected shared Home entry point was omitting live playback
state, leaving sample controls at their default Play state. It now forwards the compact playback
projection, and Home/TV Home require that input so future callers cannot silently omit it. The route
regression switches live state through playing, paused, stopped, a different sample and a Library
track, verifying Pause only on the playing sample and Play otherwise. The focused Aurral and TV Home
UI suites, Android/iOS presentation compilation, architecture checks and Mac packaging passed.
No new strings, settings or platform production changes.

## Independent sample playback and review fixes (October 27)

This supersedes the earlier queue-based sample-playback checkpoints above. Samples now use a
separate Core-owned BASS channel and their own inline playback state. Starting or resuming a sample
pauses main playback when it is playing/loading, retaining its current track, full queue, native
stream and position. Samples never enter main Now Playing, saved playback sessions, sidecars,
prefetch, scrobbling or Library mutations. Finishing, failing, dismissing the artist, changing
artists/accounts or closing Core stops/releases sample resources and does not resume main audio
implicitly. Explicit main playback restarts stop samples first, including routed playback intents.

`BassPreviewPlayback` owns only its additional channel. The main engine retains device ownership;
stopping/replacing a preview cannot free the device or main/prepared channels. Shared audio focus,
ducking and wake-lock handling target the active sample while main remains paused. Native end
callbacks drive sample completion; no new progress timer or per-frame UI update was introduced.
Provider-authenticated sample transport has its own relay leases and cancellation generation, so
sample cleanup cannot invalidate paused main playback's authenticated byte source.

The two review regressions are corrected: feedback results update the latest artist-detail state
instead of restoring the pre-request snapshot, and queue-to-playlist writes filter synthetic preview
identities even when a Library track is current. Defensive preview restrictions remain for older
branch-test sessions. The former queue-replacement sample entry point was removed.

Sample play/pause remains available while acquisition or feedback is in flight, without clearing or
changing the mutation's busy/result state. A regression checks sample playback during a pending
album request, and rendered UI checks verify controls remain enabled during both request and feedback.

Regression probes reproduced both failures on `57c267bc` before editing. Updated acceptance:

- Shared engine: 14 focused cases, including independent channel lifetime, stale end callbacks,
  main resume/release, focus interruptions, ducking, and paused-main polling during preview playback.
- Shared presentation: 68 focused Home, sample controller, queue, playback, output-routing and
  Connect command-routing cases. These include feedback/sample ordering, mixed queue filtering,
  preserved queue/track/position, errors/retry, source changes and late URL resolution cancellation.
- Shared rendered UI: all 11 Aurral cases pass; desktop (1100×900) and phone (390×844) captures
  retain the inline controls, responsive columns, circular portrait and square release covers.
- Android shared presentation compilation and `verifyCoreFirstArchitecture` pass.
- Real Windows BASS/JNI acceptance: a muted main stream was paused at 2.4 seconds; a separate HTTP
  sample played to its native end; the main stream remained paused at exactly 2.4 seconds, then
  resumed to 2.7395 seconds. This case ran without a no-sound device override and was not skipped.
  The integration test explicitly skips no-sound CI, where native playback clocks do not advance.

Local logs: `build/aurral-sample-fix-acceptance.log` and
`build/aurral-sample-fix-interruption-checks.log` in the primary workspace; captures are in
`build/verification-359-independent-samples/ui`. The native test's XML records measured positions.
iOS cannot be compiled on this Windows host; verification of this update on Apple platforms belongs
to hosted CI. The preceding head's iOS mTLS timeout was not attributed to Aurral and is not claimed
fixed by these changes. Foreground audible acceptance and owner-approved acquisition/monitoring
mutation acceptance remain separate from this muted channel-lifetime check.

All production changes remain in common code; no Android/Desktop/iOS production file changed. The
only added Desktop source is a real BASS/JNI integration test. No settings/schema change, new UI
string, live acquisition/monitoring mutation, release tag or published build is introduced.
