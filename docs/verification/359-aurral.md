# Aurral connections and discovery — first increment

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

## Implemented scope

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

Final local verification on 2026-10-27 passed **63 focused tests**, with zero failures, errors or
skips: 24 provider/session cases (including the explicitly configured live case), 20 domain/settings
cases, eight Core Home cases, two rendered UI cases and nine shared storage cases. Shared Android
compilation and both iOS ARM64/Simulator ARM64 targets passed for the provider and presentation
graphs, as did `verifyCoreFirstArchitecture` and version validation (v2.10.0/build 60). These are local
results; a full hosted cross-platform Verify run and the live application acceptance below remain
separate gates.

The owner's LAN test server reports Aurral 2.12.0, app API v1, with `discover`, `flows`, `activity`,
`review`, `trackDownloads`, `linkSearch` and `downloadCancel`. The supplied test user is an ordinary
user with `accessFlow=false`. Personalized recommendations are queued/building and trending artists
are available. No account permissions, recommendations, library files, favorites or playlists were
modified by the live test.

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
  :core:presentation:jvmTest --tests '*NaviampCoreHomeControllerTest*' \
  :core:ui:jvmTest --tests '*NaviampAurral*' \
  :core:storage:jvmTest --tests '*StorageMediaSourceStoreTest*' \
  :core:presentation:compileDebugKotlinAndroid :providers:navidrome:compileDebugKotlinAndroid \
  :core:presentation:compileKotlinIosArm64 :core:presentation:compileKotlinIosSimulatorArm64 \
  :providers:navidrome:compileKotlinIosArm64 :providers:navidrome:compileKotlinIosSimulatorArm64 \
  verifyCoreFirstArchitecture
```

Set `NAVIAMP_AURRAL_CAPTURE` to a local directory to capture the rendered fixtures. These are
component renders, not screenshots of a live application window.

## Remaining issue scope and release gates

This increment does not close #359. Follow-on native work includes recommendation feedback/blocking,
artist/release details, a verified native-to-Subsonic identity bridge and Library navigation,
permission-aware acquisition/activity, and flow management. Do not expose these as available merely
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
