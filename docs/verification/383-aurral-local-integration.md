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
