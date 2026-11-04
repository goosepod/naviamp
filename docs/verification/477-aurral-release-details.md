# Shared Aurral release details and previews

Tracking: #477, selected for v2.11.0, dependent on #476 / #488. This branch remains a draft until
the acquisition and ownership acceptance below is complete. Its base is the artist-page branch;
do not merge it ahead of that dependency.

## Common implementation

The existing common discovery controller owns release metadata and track loading independently.
A slow or unavailable metadata section does not prevent a ready track list and its previews from
working. Tracks sort by disc then track position, with original response order as the stable tie
breaker and unknown positions last. Generation checks discard abandoned release/account work.
Deferred preview commands are additionally bound to the release revision so an old command cannot
start an artist sample after navigation. The existing isolated preview engine remains the owner of
play/pause/end/stop; no release recording or edition ID becomes a playable Library track ID.

The common provider model distinguishes release-group, edition, recording and Deezer preview IDs.
The Aurral client retains edition metadata and track edition MBIDs, and sends the supported artist,
album title, type and date context to the tracks route for preview enrichment. Missing recording
IDs and missing previews stay separate. The release page shows editions, disc/track positions,
durations and explicit missing-preview text. New copy exists in all 17 maintained catalogs.

Recording requests require an artist MBID, release-group MBID and recording MBID. Their payload
carries the available edition MBID, disc, track position and duration and excludes the preview ID.
Missing identities return a definite Unavailable result before any mutation. Supplied artist credits
must match the MBID-bound artist destination; when credits are omitted, the existing verified artist
and discography context is retained. Conflicting or unverified supplied credits disable acquisition.
Whole-release and per-recording requests use common permission/capability checks. Copy explicitly
describes acquisition into the server library; it is distinct from local offline downloads.

Release actions carry source/account generation and release identity. Mutations require an accepted
dispatch and cannot be replayed by executing a rejected or stale command. The common request ledger
retains operation IDs and blocks queued, owned, saved, missing-source, conflicting or uncertain
requests across Back and reopening an artist. Cancellation after submission is uncertain, not an
invitation to repeat the mutation. Source changes clear this active-source ledger and invalidate old
commands/results. Durable request reconciliation remains #478.

Ownership uses the released batch lookup POST as a read. Native canonical and manager-qualified
IDs remain separate. Known zero files, partial files and available files have distinct states; absent
file counts remain unknown. Only a matching canonical album row and matching nonempty Subsonic
album response enable Open in Library. A native number, MBID or name alone cannot enable playback.
Owned recording hints disable their acquisition action but never turn them into playable tracks.

Native calls serialize session creation/renewal rather than entire metadata routes. Each call retains
its immutable session/capability context; an older 401 cannot clear a newer session. Mutations retain
a separate serialized protocol boundary, including feedback read/modify/write operations. Artwork
requests retain the correct origin-scoped header snapshot without sending it to external services.
Acquisition responses preserve HTTP 201/202 and correlation IDs. Only the released album HTTP 409
`album_owner_conflict` envelope establishes a conflict; its response IDs cannot establish ownership.
Unknown or ambiguous failures are never automatically replayed or treated as downloaded media.

All changed production code belongs to Core/provider commonMain. No platform production file,
schema or setting changed.

## Protocol evidence

The release contract remains pinned to Aurral v2.12.0, commit
`2179971922ab3afe5df5dfe9b7777eb0b89cced4`:

- `backend/routes/artists/handlers/releaseGroup.ts` defines details/edition summaries and track
  `mbid`, `releaseMbid`, `discNumber`, `trackNumber`, millisecond `length` and separate preview fields.
- `backend/services/providers/brainzmashMappers.ts` retains edition ID/title/date/status.
- The released `ReleasePage.tsx` supplies artist and album context for preview enrichment.
- `backend/services/libraryTrackRequests.ts` and `downloadJobs/downloadTracker.ts` distinguish
  `trackMbid`, `albumMbid`, `releaseMbid` and track/disc/duration metadata in acquisition requests.

## Verification

The expanded matrix passes 221 deterministic tests, including provider and HTTP mappings,
permission/ownership conflicts, simultaneous reads, serialized mutations, stale sessions, duplicate
taps, cancellation/timeouts, retained request identities, source changes, main/preview engine
isolation and 360×780, 1100×850 and TV 1280×800 rendering. Android/JVM/both iOS common target
compilation and architecture verification pass. Log: `build/477-acquisition.log`. Earlier read and
ownership increments are recorded in `build/477-shared.log` and `build/477-ownership.log`.
Unconfigured opt-in live methods are excluded from the deterministic total.

Configured read-only live context verification returned one edition and ten tracks for the selected
release, with zero previews. This establishes the metadata contract and a real missing-preview
case, not a successful audible preview. The narrow/wide/TV captures were inspected; that inspection
caught an inherited icon-tint error, which was corrected before the final matrix passed.

The opt-in recording-candidate probe permits only GET, login and the released read-only lookup
POST. It verifies artist credits, missing ownership and exact recording/edition identities before
writing a private review artifact; credentials and private addresses are excluded. A separate
mutation probe requires explicit approved recording and release-group IDs matching that artifact,
fresh preflight checks and an atomic, single-use attempt marker created before transmission.
Uncertain replies cannot be retried by rerunning the probe. Do not set its approval variables without
the user's specific permission. Its result is recorded separately; an absent URL, absent approval,
configured server failure or unexecuted mutation is not a passing live acquisition check.

## Remaining acceptance

The configured read-only candidate run verified Radiohead / OK Computer / Airbag against catalog,
artist credits, ownership and exact recording/edition IDs. Its real preview returned 479,827 bytes.
This verifies release-preview transport, not audible device output. The candidate is recorded in
the private ignored `build/477-candidate.json`; no server address or credentials are included.
The initial catalog candidate call returned Unavailable; direct read diagnostics subsequently
returned HTTP 200 scoped pages and a fresh provider-client run passed. No cause is asserted for
the earlier temporary failure. Final narrow/wide/TV captures confirm visible preview/request icons,
partial-ownership context, per-track queued feedback and the explicit missing-preview label.
Evidence: `build/477-live-candidate-retry.log` and `build/477-captures`.

- Live acquisition acceptance passed on 2026-11-03. With explicit owner authorization, the guarded
  probe submitted exactly one Radiohead / OK Computer / Airbag recording request. Aurral returned
  HTTP 202, Queued, and one job ID. The test executed once with zero failures; its atomic attempt
  marker and private result artifact remain in place. Evidence: `build/477-approved-acquisition.log`
  and `build/477-candidate.json.result.json`. Correct request/response handling completes Naviamp's
  acquisition acceptance; downstream download completion is Aurral's responsibility.
- Complete hosted verification of the final source tree and keep the PR dependent on #488 until
  that artist-page increment is accepted.
- Physical touch/remote and screen-reader acceptance remain open at the user's request. Automated
  semantics and injected keys do not establish speech or physical-device acceptance.

Hosted run `41598648177` passed all three Desktop jobs and Android API 35. Shared UI verification
found that the new release test asserted the queued message without scrolling it into view; the
assertion now scrolls before checking visibility. Android API 30 completed 37 tests with no test
failures but failed device cleanup with `device offline`. iOS failed its existing mutual-TLS BASS
playback assertion. These remain hosted verification gates, separate from the passed live request.
Both release-page tests pass locally after the assertion correction, including all three viewport
variants. Evidence: `build/477-ui-visibility.log`.

## November 4: accepted 2.13 compatibility integration

Integrated the refreshed artist branch and accepted main
`c5e119f2f49c721c6816f6b6b6000ab3ae82b996` / #508. Acquisition retains successful HTTP status and
correlation IDs, serialized mutations and independently serialized session renewal. The request
mapper uses the released 2.13 route/status envelopes: HTTP 400 `Invalid request` is Rejected;
album HTTP 422 `ambiguous_identity` is Rejected; album HTTP 409 `album_owner_conflict` is Conflict.
Those album-specific codes on recording routes, wrong statuses, generic/malformed/oversized or
partial errors remain Uncertain. Conflict/error identities are discarded rather than promoted to
canonical, manager or playable Library identities. The transport retains #508's bounded complete
error body and privacy/cancellation handling. Read-side canonical resolution keeps its own path.

The common execution guard now accompanies dispatch checks. Tests verify explicit retry after
rejection and no resubmission after Conflict/Uncertain, including exact recording requests after
closing and reopening the artist/release. Request ledgers remain scoped to source/account.
All catalogs retain one translated conflict message and the new rejection message, alongside the
release strings; no duplicate resource keys remain.

112 focused JVM methods pass with zero failures/errors/skips: 32 Home/discovery controller,
62 Aurral/provider HTTP and 18 rendered artist/release/discovery UI methods. Architecture verification
and shared Android, JVM, iOS ARM64 and iOS Simulator ARM64 compilation pass. The focused command
uses the same class filters recorded for #476 plus `NaviampDiscoveryReleasePageTest`; provider tests
include the release contract/ownership/mutation cases. No platform production file, setting, schema
or animation policy changed. No live acquisition or feedback mutation was submitted, and the earlier
single-use live acquisition must not be repeated. Current-head CI, audible live previews, physical
input and screen-reader acceptance remain open; these local results do not close #477 or #506.
