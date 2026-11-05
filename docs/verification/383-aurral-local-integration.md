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
