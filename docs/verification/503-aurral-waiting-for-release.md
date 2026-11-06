# Aurral albums waiting for release

Tracking: #503, under #383 / v2.11.0; depends on #477/#489 and compatibility #502/#508.
Local branch: `feature/503-aurral-waiting-for-release`, based on preview acceptance
`f3659b1c5f3c9656b7348228e79a817f3f9cd5d3`. The owner requested local implementation,
builds and tests until a larger integration batch is ready. This branch has not been pushed,
no PR has been opened, and no GitHub Actions run has been dispatched for this increment.

## Released contract and shared ownership

Reviewed Aurral 2.13.0 at `3f01fa586f363f416f2e40c234a95550b87d74f7`:

- `backend/services/aurralAlbumJobs.ts`: active/complete jobs take precedence; otherwise an
  authoritative unreleased album reports `status: "unreleased"`.
- `backend/services/libraryManager.ts`: `_finishAurralAlbum` does not queue missing tracks
  for an unreleased album. `requestAlbumFromSearch` retains nested `albumStatus`, manager,
  album/canonical IDs and correlation context; an empty job list is a valid result.
- `backend/routes/library/handlers/albums.ts`: the successful request returns HTTP 201,
  with `queued` based on actual job IDs for Aurral-managed albums.

Core adds `WaitingForRelease` and a shared context containing the manager and server date.
The provider recognizes only a successful Aurral album request whose authoritative nested status
is unreleased, without queued/owned evidence, job IDs, contradictory managers or explicit
unmonitoring. Unknown/partial dates are display metadata only; no future date or clock change
establishes waiting or availability. Dates are bounded strings; missing, blank, non-string or
oversized values use the undated label. Older 2.12 responses, Lidarr, unknown states, permission
and ambiguity handling retain their existing outcomes. Available request/canonical/manager IDs
are retained for correlation, without creating jobs or playable Library identities.

The common controller owns repeat-submission blocking at dispatch and execution. Waiting context
survives related-artist/release Back and closing/reopening within the source/account session.
An album already waiting also blocks recording acquisition on that release, while previews remain
independent. A source/account change clears the in-memory ledger and rejects stale commands.
Advancing past the release date causes no POST, timer, search or polling loop.

The shared artist card and release page show waiting and its date, with suitable disabled request
labels and a statement that Aurral manages the request. Every maintained catalog contains the three
new strings. A confirmed availability refresh supersedes stale waiting presentation; a Library
destination still requires the existing canonical/Subsonic bridge. Request correlation alone never
enables playback. Artist artwork remains circular and release artwork square.

All production changes are in Core/provider commonMain. No platform production file, schema,
setting, animation or host wiring changed. No server mutation was submitted during verification.

## Local behavior verification

120 focused JVM methods pass without failures/errors/skips: 33 Home/discovery controller,
67 Aurral/provider transport and 20 rendered UI methods. The final suite includes the existing
rejection/conflict, session, ownership and release contracts alongside these new cases:

- HTTP 201 monitored waiting with no jobs, authoritative manager/date and retained correlation.
- Unknown/partial/oversized dates, future-year-only metadata, unknown states and manager conflicts.
- Explicitly unmonitored responses, wrong routes, non-success responses, queued/already-owned
  precedence, missing sources and older-server fallbacks.
- Waiting across Back/reopening, exact recording request blocking, source changes/stale commands,
  and 400 days of virtual time without a second acquisition.
- Narrow 360x780, wide 1100x850 and TV 1280x800 artist/release presentation, with/without dates,
  disabled album/track requests, availability refresh and no invented Open in Library action.

Nine rendered captures were generated locally. Narrow artist/release, wide artist and undated TV
release captures were visually inspected: waiting labels/dates are readable, artist portraits are
circular, and the release retains square artwork. These tests are rendered layouts and semantics,
not physical TV/touch or screen-reader speech acceptance.

Reproduction (set the local JDK/Android SDK environment for the checkout):

```sh
./gradlew :core:presentation:jvmTest \
  --tests app.naviamp.presentation.NaviampCoreDiscoveryControllerTest \
  --tests app.naviamp.presentation.NaviampCoreHomeControllerTest \
  :providers:navidrome:jvmTest --tests 'app.naviamp.provider.aurral.*' \
  --tests app.naviamp.provider.navidrome.NavidromeHttpErrorResponseTest \
  :core:ui:jvmTest --tests app.naviamp.ui.NaviampAurralDiscoveryUiTest \
  --tests app.naviamp.ui.NaviampDiscoveryArtistPageTest \
  --tests app.naviamp.ui.NaviampDiscoveryReleasePageTest \
  --tests app.naviamp.ui.NaviampDiscoveryWaitingForReleaseUiTest \
  verifyCoreFirstArchitecture
```

Set `NAVIAMP_WAITING_RELEASE_CAPTURE` to a local directory to retain the nine captures.

## Local build and native results

The final local build passes `:apps:android:assembleDebug` and
`:apps:desktop:stageLocalTestApp`, including desktop native-resource/runtime verification.
The APK is `apps/android/build/outputs/apk/debug/android-debug.apk`; the staged desktop app
is `build/local-test/Naviamp.app`, both relative to this worktree. No app was installed or launched
into an existing profile. Shared presentation, provider and testkit compile for iOS ARM64 and
iOS Simulator ARM64; JVM and Android compilation are covered by the builds and focused tests.

Both `DesktopDiscoveryPreviewCoreIntegrationTest` and `DesktopPreviewPlaybackIntegrationTest`
execute successfully on this exact implementation, two tests with zero failures/errors/skips.
They use muted synthetic audio and verify native preview/main-queue/session isolation. Deadlines
and assertions are unchanged; this passing run does not establish the cause of #497 or live
Aurral speaker output. Architecture verification passes. The final build/test command was:

```sh
./gradlew -Pcompose.desktop.packaging.checkJdkVendor=false \
  :apps:android:assembleDebug :apps:desktop:stageLocalTestApp \
  :core:presentation:compileKotlinIosArm64 :core:presentation:compileKotlinIosSimulatorArm64 \
  :providers:navidrome:compileKotlinIosArm64 :providers:navidrome:compileKotlinIosSimulatorArm64 \
  :core:testkit:compileKotlinIosArm64 :core:testkit:compileKotlinIosSimulatorArm64 \
  :platforms:desktop:desktopTest --tests '*DesktopDiscoveryPreviewCoreIntegrationTest*' \
  --tests '*DesktopPreviewPlaybackIntegrationTest*' verifyCoreFirstArchitecture
```

No remote push, issue/PR update, CI dispatch, simulator boot, live mutation or owner-profile change
was performed for #503. Logs, test XML, nine captures and both app artifacts remain local.

## Acceptance boundaries

Durable source/account persistence and authoritative waiting-to-queued/imported/available status
reconciliation remain #478. This increment neither schedules release-date acquisition nor claims
restart reconciliation. The mapper tests consume independent synthetic server responses; they do
not resubmit a live request to observe a transition. Live upgraded-server audible/device acceptance
remains #506, and the earlier single-use acquisition must not be repeated. Physical input,
screen-reader and hosted exact-source CI remain separate gates for the eventual integration batch.
