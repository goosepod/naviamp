# Aurral discovery expansion: local integration

Owner instruction, November 5: carry out the existing-stack integration, Recommendations,
Search, Trending, genre blocking and final acceptance as substantial local batches. Run
builds and tests locally; do not dispatch GitHub for each change. Existing issue scopes
#383/#474/#475/#476/#477/#478/#502/#503/#504/#505/#506 remain authoritative.

## Baseline

The local acquisition stack includes artist #488, release #489, native queue #498,
waiting-for-release #503 and durable requests #478. Accepted main `cafcee8cd` was merged
without conflicts. The baseline focused command passed locally; some unchanged test tasks
were up to date. It is not a fresh execution of every native assertion.

GitHub evidence inspected before this batch: exact artist/release heads passed Verify;
native queue #498 failed `IosMutualTlsPlaybackIntegrationTest.realMutualTlsAuthenticatesAndSeeksThroughNormalPlayback`
(1 of 17 native iOS methods). Android and all Desktop native jobs passed. Preserve that
failure; its cause is not established by another passing execution.

## Delivery and acceptance

- Keep complete portable behavior, UI, navigation, policy and protocol in common code.
- Reuse shared artist/release/request/preview owners and validated Library identities.
- Use bounded reads, retained last usable data and source/account generations.
- Acquisition, genre blocking and playlist saving remain explicit actions. Never replay an
  uncertain mutation; reconcile by authoritative reads.
- No live acquisition target has been selected for this batch. Do not repeat the earlier
  one-shot Airbag request. Test an explicit new target only with target-specific authorization.
- Physical touch/TV remote, spoken screen-reader and audible live output are distinct from
  rendered semantics, muted native fixtures, transport bytes and emulator execution.
- No live-test environment variables are currently configured. Only an Android emulator is
  attached; no physical Android device or booted Apple simulator was found during inventory.
- Final exact-source native iOS, packaged Desktop and Android acceptance, current-source
  rendering, and live/device evidence remain required. Record pass/fail/not run precisely.

## New local work

#474 adds full paginated Recommendations, For you/All, rank/name/first-seen sorting,
seed/genre and validated new/direct/further filters, exclusive For you groups, explanations,
loading/building/failure/partial counts and Back retention. Older metadata does not invent
first-seen or discovery depth. Reads never request an administrator rebuild. All new copy
is defined in English and all 16 maintained translations; no portable setting is added.

Verification commands and results will be recorded after the local batch completes.

## November 5 local implementation and acceptance

Local checkpoints: Recommendations `18411e248`, Search `f0754b41b`, Trending
`a2a25eb91`, genre blocking `e82332a11`. The acceptance branch is
`test/506-aurral-local-acceptance` in the existing `aurral-478` worktree. None of
these new increments has been pushed or dispatched to GitHub.

Recommendations retain the compact Home rail and read the complete collection in bounded
100-row pages, at most ten pages per pass, with explicit further loading for larger sets.
For you uses the server rank, seed explanations, initial-cohort exclusion and validated
depth/first-seen fields; groups do not duplicate artists. Older metadata does not invent
new/direct status. Search is explicitly submitted, source scoped, paginated and retained
through artist/release/Back; album navigation validates release metadata and artist credits.
Unresolved IDs remain informational with an Aurral catalog fallback.

Trending preserves nullable sections, ranks, movement, history, region and update time.
A chart key alone never establishes a preview or Library identity. Only the exact known
native preview route plus a positive Deezer ID enables the isolated preview engine.
Unmatched chart entries use explicit catalog selection. Saving a songs chart is deliberate,
requires the current user's Flow permission, reconciles existing saves before POST, and
blocks uncertain repeats. Saving can cause Aurral to acquire playlist tracks; it is not
proof of playable files.

Genre support is established by a safe GET probe. Older servers hide the action. Only
server-approved genre values are offered for blocking, and the shared comparison contract
matches the released server's case/whitespace/hyphen/underscore equivalence. The server owns
normalization, recommendation filtering and storage. Confirmed changes refresh Home and
Recommendations; uncertain mutations require read reconciliation. No Library filtering,
portable preference, new schema or platform production code is added by these four increments.

### Preserved findings

- An early local run overlapped incremental edits and failed compilation; it is not acceptance
  evidence. Later runs use fixed source snapshots.
- The first new chart fixture compared a read-status enum with an operation-status enum and
  failed test compilation. Corrected the assertion without changing production behavior.
- Four shared controller regressions exposed retained completed coroutine jobs cancelling a
  later caller. The owner now clears only its own completed job in `finally`; the regression
  batch passes without widening deadlines or weakening assertions.
- A real isolated packaged Mac app connected using the dedicated ordinary test account and
  displayed discovery and Library content. Opening full Recommendations then remained at
  `0 of ?` despite healthy HTTP reads. Production provider decorators are recreated per lookup;
  reference equality incorrectly discarded valid results. A new recreated-wrapper regression
  reproduces this failure before the correction. The fix uses the existing shared provider
  identity contract. The failed window and failing regression remain evidence, not a pass.
- The same walkthrough exposed the inappropriate artist-list label `All releases`. The new
  collection uses translated `All`; chart epoch timestamps are interpreted as readable UTC
  timestamps. Focused action labels now retain visible foreground/background contrast.
- Computer use initially timed out opening the separate review app, later exposed its real
  accessibility tree and screenshot, and subsequently had intermittent capture/action failures.
  These tooling failures do not establish an application crash or a successful walkthrough.

### Local results so far

The initial complete matrix at `e82332a11` passes: 375 tasks, 124 executed, 251 up to date,
16m16s. It covers Android debug packaging, verified Desktop packaging, both iOS host/common
compilation graphs, native iOS test-binary linking, SQLDelight migration verification and the
architecture guard. Its deterministic JVM reports contain 186 cases with no failures/errors/
skips: domain 18, SQL storage 5, shared controllers/navigation 59, rendered UI 26 and provider/
transport 78. This result predates the recreated-wrapper walkthrough correction and does not
validate that subsequent source. The first focused wrapper regression deliberately fails.

Nine new rendered captures cover Recommendations, charts and genre management at 360, 1100
and 1280 pixels wide. Inspected narrow chart and wide genre captures; controls, explanatory
copy and the focused unblock action remain readable. These are rendered layouts and semantics,
not spoken screen-reader or physical input acceptance.

### Configured read-only comparison

Fresh direct HTTP reads through an encrypted SSH tunnel confirm Aurral 2.13.0, API v1 and
11 features. The ordinary test account has album-add permission and lacks Flow access; no
administrator or permission change was attempted. All 456 recommendations load over five
pages with 456 distinct IDs and one run ID, 450 direct and six second-hop records, all with
first-seen metadata. Charts return 100 songs, 25 albums, 25 artists and nine genre charts;
the native MP3 preview path matches the production transport contract. The user's blocklist
is initially empty. Request activity has 312 rows (processing/completed/failed/pending);
Naviamp intentionally bounds its displayed/read history and does not claim it is a private
list of only local submissions.

These direct HTTP results are separate from production-client, actual-window, audible,
physical-device and mutation acceptance. Passwords, private addresses, tokens and raw
configuration are excluded from this record. Production-client execution against the final
candidate and the final native/output results are recorded below when complete.

## Final local source and results

Code checkpoint `85716735b9fe071845d6b13f88fa2c0a34e17640` fixes recreated-provider
identity and the associated labels/timestamps. The failing wrapper regression now passes;
all 32 discovery-controller cases pass, including stale-source rejection. No assertions,
timeouts or playback cadence were weakened.

The updated full local matrix passes: **374 tasks, 101 executed, 273 up to date, 9m13s**.
It builds the Android debug APK and verified Desktop app, compiles both iOS hosts and
common dependency graphs, links the real native iOS test binary, verifies the single
unreleased migration and passes `verifyCoreFirstArchitecture`.

Deterministic JVM coverage is **187 cases**, all passing with no errors/skips: domain 18,
SQL storage 5, controllers/navigation 60, rendered UI/resource behavior 26, provider and
transport 78. The standalone live run subsequently replaces the provider task's JUnit
report; do not add the same provider cases twice or interpret replaced reports as failures.

A separate configured execution runs **nine active read-only production-client methods**,
all passing with no skips. The feedback mutation method is excluded by explicit method
selection, rather than counted as an early-return pass. These cover bootstrap, catalog
paging/search, artist/release/edition/track reads, validated owned-artist mapping, real cover
images, MP3 preview bytes, initial/nonzero authenticated ranges, Trending and a genre chart,
and the user's blocklist. No acquisition, feedback, monitoring, playlist-save, genre or
server-configuration mutation was submitted.

Fresh native execution passes **all 17 iOS methods** against an owned fixture/simulator,
including the mTLS playback case that failed on earlier #498 CI, plus **both real Desktop
preview contracts**. The Desktop fixtures are muted and synthetic; they verify queue,
index, paused position and saved-session isolation. A passing execution does not identify
or close the intermittent #454/#497 causes. Existing Windows-first-frame #493 and other
unavailable platform findings remain open.

Local logs:

- `/private/tmp/aurral-506-wrapper-before.log` — reproduced real-window identity failure.
- `/private/tmp/aurral-506-final-local-matrix.log` — final deterministic/build matrix.
- `/private/tmp/aurral-506-native-and-live.log` — configured live and native execution.
- `build/aurral-local-native-diagnostics` — bounded native observation artifacts.
- `build/aurral-local-acceptance` — sanitized result summary and candidate manifest.
- `/private/tmp/aurral-browser-captures` — inspected narrow/wide/TV component renders.

The debug APK is `apps/android/build/outputs/apk/debug/android-debug.apk`, 52,679,516 bytes,
SHA-256 `1a5b55d1d4b3a91957508b872bf0f247c5abe5e529b6264670d7ca0b6a604e19`.
Repository version/build remain v2.10.0/60; these are local development artifacts for the
selected future scope, not a new release. No version, tag, release or announcement was made.

## Open acceptance gates and handoff

The corrected isolated Mac review app is at
`/private/tmp/aurral-acceptance-review-r2/Naviamp Aurral Acceptance r2.app` and uses a fresh
private test profile. The installed application and its profile were preserved. The computer-use
service timed out repeatedly, including a final call that ignored its requested timeout for
approximately 75 minutes. Further native UI automation was stopped. **The corrected full-route
walkthrough is not accepted** merely because the wrapper regression and builds now pass.

Still open: actual Recommendations/Search/chart-to-artist/release/Back walkthrough after the
correction, audible live previews with a nonempty main queue, physical touch/TV remote and spoken
screen-reader checks, a positive live unreleased/waiting example, source/account restart/reconnect
checks in the real app, and the remaining upgraded-server lyrics/cover/playlist/listen-reporting
matrix. Initial AX information and rendered semantics do not establish spoken accessibility.

Target-specific approval was requested for one new Radiohead recording from a different single
and a temporary rock block/unblock on the dedicated account. Read-only identity/ownership checks
prepared the exact candidate and confirmed no owned files. **Approval remains pending; neither
mutation was sent.** The earlier Airbag acquisition was not repeated. The account lacks Flow
permission, so a positive live playlist-save path is unavailable without a separately scoped
account/permission decision; no permission was changed.

Keep #383/#474/#475/#476/#477/#478/#502/#503/#504/#505/#506 and dependent drafts open where their
acceptance criteria remain unmet. This local batch neither updates GitHub issues/PRs nor replaces
protected exact-source checks. No new hosted runner was dispatched and no branch was pushed.

All new product, UI, protocol, paging, normalization and lifecycle behavior is common. This batch
adds no platform production change. The six existing #478 repository-injection adapters and their
concrete OS/database lifetime boundaries remain audited in
[478-aurral-acquisition-tracking.md](478-aurral-acquisition-tracking.md#platform-diff-audit).
