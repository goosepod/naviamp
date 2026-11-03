# iOS mTLS rejection reliability

Tracking: #454, selected for v2.11.0 to unblock #467. This is an active investigation; a passing
retry does not establish a fix.

## Preserved failure

PR #467 head `6765c9b6ac239742d761065fc317a992b01033cc`, Verify
[41682705435](https://github.com/goosepod/naviamp/actions/runs/41682705435), attempt 1:
the native iOS suite ran 15 tests and failed only the shared mTLS contract's **missing client
identity rejection** phase. The 25-second outer deadline cancelled validation; playback had not
started (Idle, null progress). All other platform jobs and the common iOS compile/test stage passed.
The assertion matches earlier #454 reports; attribution to #467 is not established.

The native CFNetwork log records the first ping starting a secure connection and then being
cancelled by the test, with no HTTP request/response bytes. That does not establish which native
authentication challenge was reached. Raw failure artifacts are retained in the hosted run and
privately under `/private/tmp/review-467-ios-failure`.

## Diagnostic source and local reproduction

The shared contract now records a bounded last-sixteen challenge trace and whether it was creating,
validating or closing the negative-check client. Timeout errors include those facts. No TLS policy,
certificate validation, timeout, assertion or negative case has changed. No platform production
file is modified.

The initial real native simulator run passes one executed integration method in 7.105 seconds,
including all five negative cases, positive mTLS controls, decoded streaming, pause/resume and seek
identity/range audits. This first device was booted while the binary compiled and is not evidence
that a cold-start failure has been fixed. Separate newly created iOS 26.5 profiles are being tested;
CI uses its runner-selected runtime. Preserve the environment distinction.

## Acceptance still required

- Reproduce or characterize fresh-boot failures with actual challenge/transport observations.
- Implement a demonstrated correction in the common owner, with shared behavior/failure tests.
  A native adapter may only execute an irreducible Apple operation if evidence requires one.
- Preserve missing/untrusted client, wrong identity password, untrusted server and wrong hostname
  rejection, positive authentication, streaming/ranges/seek and cancellation.
- Do not increase deadlines, skip a check, count a timeout as rejection or claim a retry fixed it.
- Run common regressions, Android/JVM/both iOS compilation, repeated fresh-device native verification
  and the required exact-source CI matrix before merging. Then refresh #467 from accepted main and
  re-run its CI.
