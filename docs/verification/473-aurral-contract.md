# Expanded Aurral contract foundation

Tracking: [#473](https://github.com/goosepod/naviamp/issues/473), under
[#383](https://github.com/goosepod/naviamp/issues/383), selected for v2.11.0.
Research date: November 2, 2026. This record separates source review, synthetic fixtures, and live acceptance.

## Work order

1. #473: pin contracts, identities, compatibility, and provider fixtures.
2. #476: establish the common navigable artist destination used by every discovery entry point.
3. #477: establish release details, isolated samples, and permission-aware requests.
4. #474: build full Recommendations on those destinations, preserving the Home rail.
5. #475: add catalog search using the same artist/release destinations.
6. #478: follow acquisition through reconciliation and actual Library availability.

Recommendations and search can be developed after #473, but completing artist/release navigation
first avoids duplicating detail state and allows their end-to-end acceptance. Each issue keeps its
own branch/PR; this foundation adds no new route or UI. #478 is last because it depends on request
correlation and must distinguish accepted requests, imported files, and validated playable media.

## Evidence and version boundary

The latest public GitHub release observed is v2.12.0, published October 27, 2026. Its annotated tag
resolves to **2179971922ab3afe5df5dfe9b7777eb0b89cced4**. Upstream main inspected at
**d828a84b4c6126e215032d63e421768496434021** is a separate compatibility target, not a released
minimum. No earlier release was checked; 2.12.0 is the tested source baseline, not a claim that
these routes first appeared in 2.12.0.

Primary references:

- [Released discovery serializer](https://github.com/lklynet/aurral/blob/2179971922ab3afe5df5dfe9b7777eb0b89cced4/backend/routes/discovery/handlers/main.ts)
  and [paging/context owner](https://github.com/lklynet/aurral/blob/2179971922ab3afe5df5dfe9b7777eb0b89cced4/backend/services/discovery/userDiscovery.ts).
- [Released API features](https://github.com/lklynet/aurral/blob/2179971922ab3afe5df5dfe9b7777eb0b89cced4/backend/config/apiContract.ts)
  and [permission guards](https://github.com/lklynet/aurral/blob/2179971922ab3afe5df5dfe9b7777eb0b89cced4/backend/middleware/requirePermission.ts).
- [Catalog serialization](https://github.com/lklynet/aurral/blob/2179971922ab3afe5df5dfe9b7777eb0b89cced4/backend/services/searchService.ts),
  [artist details](https://github.com/lklynet/aurral/blob/2179971922ab3afe5df5dfe9b7777eb0b89cced4/backend/routes/artists/handlers/details.ts),
  [similar artists](https://github.com/lklynet/aurral/blob/2179971922ab3afe5df5dfe9b7777eb0b89cced4/backend/routes/artists/handlers/similar.ts),
  and [release details/tracks](https://github.com/lklynet/aurral/blob/2179971922ab3afe5df5dfe9b7777eb0b89cced4/backend/routes/artists/handlers/releaseGroup.ts).
- [Album request/status/cancel routes](https://github.com/lklynet/aurral/blob/2179971922ab3afe5df5dfe9b7777eb0b89cced4/backend/routes/library/handlers/albums.ts),
  [manager-specific request results](https://github.com/lklynet/aurral/blob/2179971922ab3afe5df5dfe9b7777eb0b89cced4/backend/services/libraryManager.ts),
  [track request routes](https://github.com/lklynet/aurral/blob/2179971922ab3afe5df5dfe9b7777eb0b89cced4/backend/routes/library/handlers/downloads.ts),
  [request collection](https://github.com/lklynet/aurral/blob/2179971922ab3afe5df5dfe9b7777eb0b89cced4/backend/routes/requests.ts),
  and [activity serialization/visibility](https://github.com/lklynet/aurral/blob/2179971922ab3afe5df5dfe9b7777eb0b89cced4/backend/services/aurralHistoryService.ts).
- [Newer serializer](https://github.com/lklynet/aurral/blob/d828a84b4c6126e215032d63e421768496434021/backend/routes/discovery/handlers/main.ts)
  and [Recommended grouping policy](https://github.com/lklynet/aurral/blob/d828a84b4c6126e215032d63e421768496434021/frontend/src/pages/recommendedRails.ts).
- Living references reviewed: [app contract](https://docs.aurral.org/api/app-contract/) and
  [endpoint reference](https://docs.aurral.org/api/endpoints/). Pinned source wins when living docs
  describe newer behavior. Upstream main also changes server framework; framework changes alone
  do not establish a new API version or imply equivalent payloads.

## Route / payload / permission / version matrix

Paths are relative to the configured Aurral base, including any reverse-proxy subpath. All new
calls reuse the existing user-session transport and custom headers. There is no installation API
key fallback, credential export, administrator rebuild, or automatic mutation retry.

| Consumer | Route and payload contract | Permission/capability/configuration | Source baseline and fallback |
| --- | --- | --- | --- |
| All | GET `/api/health/bootstrap`; `api.version`, `api.features`; POST `/api/auth/login`; `token`, `expiresAt`, user permissions | Existing native client requires API v1 + `discover` and password session. Features, account permissions, and configured services are separate facts. | 2.12.0. Unknown API versions/missing discover => Unsupported; no sign-in attempt. |
| #474 | GET `/api/discover?limit=1..200&offset=N`; `recommendations`, `recommendationCount`, `basedOn`, `topGenres`, `topTags`, `lastUpdated`, `discoveryRunId` | Authenticated account, personalized result. GET can cause the server's own stale-cache refresh; Naviamp never calls administrative rebuild. | 2.12.0. Preserve server rank. Missing total stays unknown; never substitute page length as total. |
| #474 | GET `/api/discover/status`; `isUpdating`, phase/progress, error, lastUpdated | Same user session; status independent of cached usable results | Existing Home client already reads this. Full-page loading/building/stale UI belongs to #474. |
| #474 | Artist `matchedTags`, `sourceArtist(s)`, supporting seed names, sourceType, discoveryTier | Explanations are server context, not instructions to recompute score | 2.12.0. Preserve strings/rank without inventing recommendation scoring. |
| #474 | Artist `discoveryDepth`, `firstDiscoveredAt` | No separate API v1 feature flag guarantees these fields | Absent in released serializer; present on pinned upstream main. Null stays null. Hide New/First seen/depth grouping when unavailable; #474 validates timestamps and cohort boundaries. |
| #475 | GET `/api/search?q=...&scope=artist\|album&limit=N&offset=N`; scope/query/count/offset/items; albums also hasMore | No acquisition permission needed to read catalog; metadata provider must be usable. Root auth still applies. | 2.12.0. Do not require unified search. Blank query makes no request. Scope/offset mismatch or malformed rows container is an unavailable response. |
| #475 | Artist id/name/image/tags/disambiguation; album id/title/artistName/artistMbid/releaseDate/primaryType/secondaryTypes/coverUrl/inLibrary/status | `inLibrary`/status are hints. In this release artist search serializes inLibrary=false; album lookup consults Lidarr and is not a complete canonical ownership proof. | Keep hints separate from validated Library navigation. Detail identity requires a real MBID; labels cannot merge editions/artists. #475 adds disambiguation presentation. |
| #476 | GET `/api/artists/:mbid?mode=core`, `/preview?artistName=...`, `/similar?artistName=...&limit=1..20` | Metadata/Deezer/similarity services can fail independently. Read access does not imply request/monitoring access. | 2.12.0. Reuse current details and sample transport; similar/details sections recover independently. |
| #477 | GET `/api/artists/release-group/:mbid`; id/title/primary-type/secondary-types/first-release-date/artist-credit/overview/genres/coverUrl/releases | Metadata service; returned identity must match requested release-group MBID | 2.12.0. Missing optional overview/art/edition information does not invent data. Foundation retains core details/credits; edition UI is #477. The released server also emits 404 during metadata cooldown: map that operation to Unavailable, not Unsupported. |
| #477 | GET `/api/artists/release-group/:mbid/tracks`; **top-level array**; recording mbid/title/discNumber/trackNumber/length in milliseconds; previewProvider/previewTrackId/preview_url | Preview availability is per row; track ID and preview ID are different namespaces | 2.12.0. Only numeric Deezer preview IDs with provider=deezer and a preview URL enable the existing preview path. Unknown providers/missing preview stay unavailable. |
| #477/#478 | POST `/api/library/albums/request`; artist/release-group identities, names, optional triggerSearch/managedBy; HTTP 201 result with album/artist/status, optional requestGroupId/jobIds/albumStatus | `addAlbum`; adding a missing artist may also require `addArtist`; source and manager configuration checked server-side. 403 is permission denial, 409 can represent ownership/manager conflict. | 2.12.0. Saved, queued, searching, available and missing source are distinct. Retain explicit identities; request acceptance is not playable media. |
| #477/#478 | POST `/api/library/downloads/track`; track/artist names and supported identifiers; 200 alreadyOwned or 202 queued/alreadyQueued, nullable jobId | `addAlbum` + `trackDownloads`; configured source still required | Existing request client. Never use Deezer preview ID as a recording MBID. Unknown outcome => Uncertain, no automatic replay. |
| #478 | GET `/api/requests`; **top-level array** of heterogeneous activity, opaque id/status, optional jobId/requestGroupId/canonicalAlbumId/albumMbid | `activity` feature and authenticated user; collection applies server visibility rules, not a guarantee every row was requested by that user | 2.12.0. Preserve unknown status and absent correlation. Completed activity alone cannot enable playback. No name-based joining. |
| #478 | GET `/api/library/albums/aurral/:canonicalId/status`; canonicalId/status/counts/recovery/requestGroupId | Aurral-managed canonical album only; manager must be known | 2.12.0. Missing/unknown manager => no invented route. Lidarr status uses its separate downloads/status contract; validate against that manager. |
| #478 | POST `/api/library/albums/aurral/:canonicalId/cancel`; queued/active cancellation | `addAlbum` in released route. `downloadCancel` is not proof of every manager-specific permission/route. | No cancellation implementation in this foundation. #478 must check exact route, manager and permission, preserve kept/imported files, and handle unknown results. Review/approve/deny remain external. |
| #476/#478 | Existing `/api/library/lookup/:mbid`, canonical available-only response, then actual Subsonic response | Canonical identities and Subsonic identities must agree; metadata presence alone is insufficient | Existing bridge remains authoritative. New catalog/request contracts cannot manufacture ArtistId/AlbumId/TrackId. |

## Important source findings

- Recommendations paginate only when an explicit limit is supplied; the server caps it at 200.
  The client always supplies a bounded limit. Page advancement uses raw returned rows before
  deduplication/filtering, so duplicates or invalid rows cannot shift offsets backward. An empty
  page stops progress even when a stale total claims more. Missing totals use a full-page probe,
  not an invented exact count. Collection loading/retry/deduplication across pages belongs to #474.
- `topGenres` and `topTags` are string arrays, not weighted objects. `basedOn` contains seed objects;
  supportingSeeds contains artistName, which explains recommendations but is not a navigation ID.
- The released Lidarr branch of `requestAlbumFromSearch` can omit top-level managedBy and job IDs.
  Aurral's branch returns richer job/group data. Do not assume those schemas are identical or infer
  the manager from a bare numeric id. Preserve explicit canonicalId separately; an unknown manager
  leaves the managed ID unresolved. #478 needs a validated lookup or external fallback for that case.
- Activity `source` often denotes a download service, not a library manager. Activity albumId
  cannot be silently treated as canonicalAlbumId or as a Lidarr ID. Activity visibility includes
  library/shared activity, so label it according to actual server scope rather than claiming it
  is a private list of only the user's submissions. Status routes can return cached results;
  #478 must reconcile and distinguish an uncertain outcome from authoritative completion.
- Artist/release/recording MBIDs, Deezer IDs, canonical album/artist IDs, manager-qualified album
  IDs, request groups, and jobs have separate shared types. Existing Subsonic IDs are resolved
  only through the existing validation bridge. Every client/session is bound to one connection;
  future Core destinations must additionally key state by source/account and reject stale results.

## Implementation and acceptance boundaries

`ProviderDiscovery` gains default Unsupported contracts, preserving existing providers and the Home
rail. Aurral implements read-only recommendation pages, artist/album catalog pages, release details,
release tracks, and activity reads in provider commonMain using the existing session owner. Existing
request operations retain correlation data without changing the visible request workflow. No new UI,
settings, schema, strings, background polling, or platform production code is introduced.

Synthetic fixtures in `AurralCatalogContractTest` follow the pinned serializers and cover 205 picks,
duplicates/filtered rows, rank/unknown counts, older and newer metadata, two seeds/genres, Unicode
query encoding, reverse-proxy subpaths/custom headers, recording versus preview identities, mixed
manager/canonical/job correlation, absent features, typed failures, cancellation, and account-bound
sessions. Existing Aurral tests cover ordinary/restricted permission combinations, missing source,
expiry, unsupported bootstrap, mutation ambiguity/no retry, feedback and validated Subsonic identity.
These are synthetic tests, not captured live responses. No acquisition request is sent to a server
as part of this research.

Local verification and hosted cross-platform results are recorded below as they complete. Windows
can compile the common iOS graphs here; Apple binary linking and simulator/device execution require
macOS. The owner supplied a test connection for read-only verification, with certificate validation
disabled only for that explicit test connection. Credentials and its private address are not stored
in this record or the fixtures. Earlier #359 acceptance is not counted as verification of these new contracts. The follow-up UI
issues retain responsibility for rendered/live route acceptance, stale-query rejection, lifecycle
policy, sample/main-queue isolation and request-to-actual-playback acceptance.

Fresh direct HTTP comparison confirmed app version 2.12.0, API v1, all seven advertised baseline
features, and a non-administrator account. Its permissions allow artist/album additions and
monitoring but deny Flow access and deletion. No permission was exercised through a mutation.
Discovery reported 337 recommendations, seed objects, string genre arrays, and the released artist
serializer without firstDiscoveredAt/discoveryDepth. Album catalog shape matches the pinned source.
The following production-client live regression is separate from this direct HTTP comparison.

Artist catalog search for Radiohead returned count=0/items=[], while album search returned releases
whose title matches that query (not necessarily releases by that artist). Do not assume album search
is an artist-name filter. The metadata-detail route returned HTTP 404 with the explicit message
`Metadata provider rate limit cooldown is active`; an existing cached artist-details response was
still readable. No source configuration was changed and no rebuild was triggered. The empty artist
result's cause is not established by that response alone.

The first live test rejected its assumption that artist search must return a match. The second
exposed the metadata 404 mapping. Both findings are retained here: the fixture now covers a valid
empty catalog and the release read paths distinguish unavailable metadata from an unsupported API.
The live harness compares fallback status with the underlying HTTP failure and fails if a successful
payload is instead lost during parsing. A fallback pass is not successful release-payload acceptance;
that still requires a healthy metadata provider. #473 remains open while this acceptance is pending.

## Verification results

- 31 deterministic provider regressions pass: 11 catalog contract, 9 existing discovery, 5 action,
  and 6 native-detail tests. The six opt-in live methods also return without running when no test
  connection is configured; those early returns are excluded from the deterministic count.
- Both shared domain contract tests pass.
- Common Android/JVM/iOS ARM64/iOS Simulator ARM64 compilation and `verifyCoreFirstArchitecture`
  pass. Local log: `build/473-final-shared.log`. No platform production files changed.
- A separate configured production-client live test passes its read-only/availability assertions:
  recommendationCount=337, artistCount=0, release=Unavailable, tracks=Unavailable. It reads two
  recommendation pages, both search scopes and activity, retains one session, and permits no POST
  except login. Underlying metadata failures justify the unavailable results; successful release
  details/tracks are **not** claimed. Local log: `build/473-live.log`; JUnit output records the result.
- Required hosted cross-platform checks remain pending the PR. Native Apple execution and successful
  live release payloads remain acceptance gates, not inferred passes.

Reproduce deterministic checks with the installed JDK 21 and Android SDK:

```sh
./gradlew :core:domain:jvmTest --tests '*ProviderDiscoveryCatalogTest*' \
  :providers:navidrome:jvmTest --tests '*Aurral*' \
  :providers:navidrome:compileDebugKotlinAndroid \
  :providers:navidrome:compileKotlinIosArm64 \
  :providers:navidrome:compileKotlinIosSimulatorArm64 verifyCoreFirstArchitecture
```

For fresh live comparison, use the existing opt-in environment contract in `AurralLiveTest` and
select only `*AurralLiveTest.expandedCatalogContractsUseReadOnlyRoutesAndKeepIdentityNamespaces`.
Do not enable `NAVIAMP_AURRAL_TEST_FEEDBACK` for contract research. Live credentials remain local.
