# Aurral 2.13 compatibility

Tracking: #502, under #383, milestone v2.11.0. Research date: November 3, 2026.

## Pinned source

Aurral 2.13.0: `3f01fa586f363f416f2e40c234a95550b87d74f7`.
Older accepted 2.12.0: `2179971922ab3afe5df5dfe9b7777eb0b89cced4`.
The [foundation matrix](473-aurral-contract.md) retains its historical live results and now separates
these two released contracts.

Synthetic excerpts are derived from the tagged 2.13 files:

- [API registry](https://github.com/lklynet/aurral/blob/3f01fa586f363f416f2e40c234a95550b87d74f7/backend/config/apiContract.ts).
- [User-session login](https://github.com/lklynet/aurral/blob/3f01fa586f363f416f2e40c234a95550b87d74f7/backend/routes/auth.ts).
- [Discovery serializer](https://github.com/lklynet/aurral/blob/3f01fa586f363f416f2e40c234a95550b87d74f7/backend/routes/discovery/handlers/main.ts)
  and [starter source](https://github.com/lklynet/aurral/blob/3f01fa586f363f416f2e40c234a95550b87d74f7/backend/services/discovery/recommendationPipeline.ts).
- [Album validation and HTTP 201](https://github.com/lklynet/aurral/blob/3f01fa586f363f416f2e40c234a95550b87d74f7/backend/routes/library/handlers/albums.ts)
  and [recording HTTP 202](https://github.com/lklynet/aurral/blob/3f01fa586f363f416f2e40c234a95550b87d74f7/backend/routes/library/handlers/downloads.ts).

## Regression boundaries

`Aurral213CompatibilityTest` exercises the production common client with:

- The eleven released features plus a deliberately unknown future feature; independent app/API
  versions; refusal before login for unsupported API or missing discover.
- Released depth/time/chart source, server explanations, nullable art/tier, unknown fields and
  malformed optional rows. Raw row counts retain paging/rank even when rows cannot render.
- The older 2.12 missing-field fallback, without requiring new features.
- Session renewal before expiry and 401 invalidation; no transparent replay, no administrator key.
- Validation/permission/conflict/server errors without replay or fabricated result identities.
  Unknown activity status and Tidal source cannot become a library-manager identity.
- Real Ktor MockEngine HTTP 201 album and 202 recording responses reaching the native client,
  with queued/alreadyQueued distinctions and separate canonical/job namespaces.

These are deterministic synthetic fixtures, not captured server traffic. The existing catalog,
permissions, artist/related, metadata/preview, ownership and origin-header regressions remain part
of the required suite. They do not all become separately pinned 2.13 route fixtures by implication.

## Remaining acceptance

#502 remains open. Definite validation rejection is currently collapsed into the existing Uncertain
operation result for HTTP 400/409/422. The new test records that conservative behavior rather than
claiming that the requested distinction has been implemented. This needs a common result/UI change
with translated copy and tests; a 409 also needs route-specific interpretation rather than a blanket
claim that no server work happened. HTTP 201/202 fixtures prove client transport behavior, not server
validation or successful live acquisition.

Live streaming/range/seek, the complete upgraded route matrix, actual healthy metadata and native
preview/main playback require #506 evidence. The owner reports the upgrade complete. The open review
profile contains a Navidrome source whose configured address does not expose Aurral bootstrap. The
owner subsequently provided a separate test connection and explicitly disabled certificate validation
for that connection only. A fresh direct HTTP read confirms appVersion 2.13.0, API v1 and all eleven
released features. Login returns a user session with integer expiry and non-administrator permissions.
Discovery, both catalog scopes, activity, blocked tags and Trending all return HTTP 200; artist search
for the probe query returns a valid empty catalog. These direct HTTP observations are separate from
the production-client and native acceptance results below. No server acquisition, feedback,
monitoring, configuration, library or account mutation was sent. No credentials or addresses are
recorded here. The private review-profile database was opened read-only.

## Local validation

The deterministic run passes 38 provider regressions (seven new compatibility, eleven catalog,
nine discovery, five native-action and six native-detail). Seven unconfigured opt-in live methods
returned early in that run and are excluded from the count. Android/JVM/iOS ARM64/iOS Simulator
ARM64 compilation and `verifyCoreFirstArchitecture` pass. Log: `/private/tmp/502-shared-final.log`.
The first run failed because the synthetic renewal returned the original expiring timestamp again;
the corrected fixture issues a later expiry for each new session. This was a fixture error, not a
production-client failure. The seven tests pass after the correction.

The configured production-client run passes seven active read-only methods against Aurral 2.13.0
(API v1, eleven features). The eighth feedback method returns early because mutation opt-in is
unset and is excluded from the live count. Results:

- Recommendation count 456, valid empty artist search, readable album search and activity.
- Artist discography, release details and release tracks Ready; successful payload parsing is
  verified rather than accepting an unavailable fallback.
- Release and artist artwork decode, native preview bytes are MP3, owned-artist mapping resolves
  through validated Subsonic identity, and native discovery/Subsonic use the same connection.
- The provider streams exactly 1024 bytes for each of `bytes=0-1023` and `bytes=1024-2047`, with HTTP
  206 and matching Content-Range. This establishes byte-range transport, not audible native seek.
- No feedback, request, monitoring, scrobble, account or server configuration mutation is enabled.

Private local log: `/private/tmp/502-live.log`. Hosted CI is pending. This client-only run does not
establish real-window rendering, audible preview output, queue/session controls or physical device
acceptance. Those remain #506/#477 gates. No credentials or private addresses are committed. Reproduce with JDK 21 and the configured Android SDK:

```sh
./gradlew :providers:navidrome:jvmTest --tests '*Aurral*' \
  :providers:navidrome:compileDebugKotlinAndroid \
  :providers:navidrome:compileKotlinIosArm64 \
  :providers:navidrome:compileKotlinIosSimulatorArm64 verifyCoreFirstArchitecture
```

No platform production files, settings, schema, UI or animation behavior changed in this fixture step.
