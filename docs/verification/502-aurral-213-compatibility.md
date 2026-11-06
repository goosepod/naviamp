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

## Request outcome interpretation

The common operation result now distinguishes recognized rejection (`Rejected`) and album library
ownership conflict (`Conflict`) from an uncertain submission. The production HTTP transport retains
at most 8192 bytes of complete error-body evidence; oversized or unreadable bodies remain unknown.
Raw bodies never enter exception messages or API history.

Pinned to the released validation middleware, album request handler and library manager:

| Response | Result |
| --- | --- |
| HTTP 400 with `error: "Invalid request"` on the two supported request routes | Rejected |
| Album request HTTP 422 with `code: "ambiguous_identity"` | Rejected |
| Album request HTTP 409 with `code: "album_owner_conflict"` | Conflict |
| Same codes on another route/status, generic 400/409/422, malformed/oversized response, transport/5xx failure | Uncertain |
| HTTP 403 | Existing Forbidden behavior |

No operation automatically retries. A rejection permits another explicit action; conflict and
uncertain actions are blocked for the current detail session at both dispatch and execution. A
conflict does not assert that the album is playable, queued, owned by the current artist, or that no
partial server work occurred. Returned numeric conflict identities are never promoted to playable
or manager identities. Shared UI shows separate messages in all 17 maintained language resources.
HTTP 401 continues to invalidate the user session without transparent mutation replay.

Source: tagged [validation middleware](https://github.com/lklynet/aurral/blob/3f01fa586f363f416f2e40c234a95550b87d74f7/backend/middleware/validate.ts)
and [library manager](https://github.com/lklynet/aurral/blob/3f01fa586f363f416f2e40c234a95550b87d74f7/backend/services/libraryManager.ts).
The recognized album conflict code is deliberately route-specific: other 409 responses can follow
artist creation or monitoring changes and must not be described as a clean preflight rejection.

## Read-route fixtures and remaining acceptance

`Aurral213ReadRoutesTest` drives the real Ktor transport and complete provider against synthetic
2.12/2.13 fixtures. It covers exact artist/core and canonical query parameters, user permissions,
malformed optional release/preview rows, metadata and track nullable/scalar fields, separate
recording/release/preview identities, validated canonical-to-Subsonic artist navigation, unknown
activity status and Tidal source isolation. These augment the existing catalog and recommendation
contracts; a synthetic fixture is not new live or audible evidence.

#502 remains open for compatibility acceptance across the related artist/release feature branches
and the full #506 upgrade matrix. New related-browsing routes must be pinned as those consumers land.
HTTP 201/202 fixtures prove client transport behavior, not successful live acquisition.

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

## November 3 validation

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

The current increment changes only common domain, provider transport/interpretation, controller and UI code, shared tests and translated resources. No platform production files, settings, schema or animation behavior changed.


## November 4 follow-up validation

The outcome/read-route increment passes 46 deterministic provider/transport methods, 13 shared Home
controller methods and 12 shared UI methods, zero failures/errors/skips. Eight unconfigured live
methods returned early in the deterministic run and are excluded. The blocked-action regression
exposed an execution path that bypassed dispatch's block; execution now applies the same guard.
The first compile used a channel cancellation overload without its required argument; corrected
before the passing run. Android and both iOS target compilation pass for provider, shared controller and shared UI;
architecture verification passes. The final route assertions and 401 conservative-outcome cases
also pass. Log: `/private/tmp/502-outcomes-common-targets.log`.

Fresh configured acceptance passes all seven active read-only live methods against Aurral 2.13.0
(API v1, eleven features); the feedback mutation method is disabled and excluded. Metadata and
tracks are Ready, and both byte ranges return HTTP 206 with 1024 bytes. No acquisition, feedback,
monitoring or account/configuration mutation was sent. Log: `/private/tmp/502-outcomes-live.log`.
These results establish current client/transport compatibility, not audible or device acceptance.
Hosted CI must still pass on the final refreshed head before merge.
