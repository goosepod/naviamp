# Final Aurral acceptance: November 5, 2026

## Owner scope decisions

The owner accepts the current Aurral discovery scope and explicitly defers native
acquisition cancel/retry controls to the next milestone. The current handoff to
Aurral is acceptable; it is not an implementation gap for this milestone. Keep
this decision with the existing #383/#478 work rather than creating a new UI ticket.
The owner plans the Windows check for the weekend of November 7–8; #493 is not
accepted or closed by today's Mac work.

Application under test: local common implementation at `94e0e363`, running as the
isolated Aurral review r6 app with its existing private profile. This acceptance
increment changes tests/documentation only and does not require another app rebuild.
The server freshly reports Aurral 2.13.0, API v1 and eleven features. Credentials
remain in the existing secure local reference and never enter this document.

## Executed read-only and native checks

All **13 configured production-client live read methods** pass with zero failures,
errors or skips. The feedback mutation method is explicitly excluded. This includes:

- Bootstrap/version/capabilities; Recommendations and both catalog search scopes;
  artist, related artist, release, edition and track reads. This run reports 455
  recommendations, two artist search matches and twenty related artists; these are
  live observations, not fixed expected counts.
- Trending: 100 songs, 25 albums, 25 artists and nine genres; current blocked genres;
  real preview bytes and cover-image transport; validated artist/Library identities.
- Initial and nonzero authenticated original-file ranges (206, correct Content-Range,
  1024 bytes); embedded tags and 44100 Hz / 16-bit FLAC properties with caching off.
- Normal-account server lyrics (46 nonempty plain lines) and actual artwork bytes.
  The playlist read is a valid empty collection, not a positive playlist-edit test.
- Two newly constructed provider/native user sessions recover the same existing
  recording ownership and validated Library identity, with readable request history.
  This proves fresh-session reconnection; it does not force a real token expiry.
- The account lacks Flow permission. Chart controls report `canSave=false` and an
  attempted client save returns Forbidden before any mutation request. Positive
  chart-save behavior is not counted as tested.

All **three selected native contracts** pass with no failures/errors/skips:

- Reopen the SQL database for the already-approved Radiohead request, preserve exact
  accepted identities across simulated connection loss, isolate another account,
  recreate the provider, and reconcile to validated playable Library media.
- Offline ownership cannot advertise a stale playable Library bridge: an Available
  record becomes Complete until validation succeeds again. It retains its request
  identity and repeat-submission protection. This is the existing common policy.
- Stream the existing 26,836,983-byte recording and use real BASS/JNI at volume zero
  for play/pause/seek/resume. The two separate native preview contracts retain the
  independent main queue/index/session and paused position, with explicit resume.

These checks never re-submit the recording, replay the Airbag request, change genre
blocks, alter permissions, create/edit/delete playlists, or submit a listen. They do
not establish audible output or physical input/accessibility. The test account and
installed Naviamp application are preserved.

The native recovery harness initially failed while re-inserting the same local
fixture source. It now reuses that isolated source, so the saved database can be
reopened without clearing acceptance history. A subsequent assertion incorrectly
expected cached Available status during an offline ownership lookup; the assertion
now follows the existing current-validation requirement above. Both raw failures
are retained; neither required an application production change or deadline change.

Evidence:

- `/private/tmp/aurral-final-consolidated-readonly.log` (13 methods, one configured run).
- `/private/tmp/aurral-final-native-acceptance-verified.log` (three native cases).
- `build/aurral-local-acceptance/final-live-native-results` (preserved native XML).
- `/private/tmp/aurral-final-native-acceptance.log` and
  `/private/tmp/aurral-final-native-acceptance-fixed.log` (raw harness/assertion failures).

## Approved qualifying live listen: passed

The owner explicitly approved one test play on November 5. The configured native
case executed against existing Radiohead “A Punchup at a Wedding (edit)” on account
`test`, with no acquisition or playlist action. The real native clock crossed the
shared `NaviampListenReporting` threshold of **116.5 seconds** at volume zero. The
case passed in **126.456 seconds**; repeated observations did not resubmit a listen.
Read-only server SQL verified this account/track's play-event count increased from
**0 to 1**, exactly the approved one-play delta. The guarded no-replay marker remains
in place; this approval is consumed and the test must not be replayed.

The separate preview-policy probe crossed a simulated qualification threshold and
emitted zero presence/listen calls. That probe is fixture evidence, distinct from
the real native main-track clock and server-verified play event. Muted playback does
not establish audible output or delivery to an external scrobbling service.

Evidence: `/private/tmp/aurral-approved-qualified-listen.log`,
`build/aurral-local-acceptance/listen-test-summary.json`, and preserved XML under
`build/aurral-local-acceptance/final-live-qualified-listen-results`. Gradle passes
**57 tasks, 17 executed, 2m39s** at test commit `8b9c81aa`. Together with the earlier
batch this is **13 live read-only cases, four unique native cases, and 17 shared
listen-policy regressions**, not repeated-run totals. No application production
source or running review profile changed; another rebuild is unnecessary.

## Remaining acceptance

- **Positive waiting-for-release:** no future-dated album exists in the current
  library lookup, and current request history contains no waiting/unreleased entry.
  No new album request was made solely to manufacture test data. Shared waiting and
  persistence fixture coverage remains separate from a positive live example.
- **Positive chart save and playlist editing/cover revisions:** the dedicated account
  has `accessFlow=false` and no visible playlists. Permission-denied/empty reads pass;
  positive writes require an authorized target and suitable account permission.
- **Actual application pending-request restart, audible preview/main-queue walkthrough,
  physical touch/remote and spoken accessibility:** remain distinct from controller,
  SQL reopen, muted BASS and rendered UI evidence. Owner Mac feedback covers the
  behaviors explicitly reported in the conversation, not all of these scenarios.
- **Windows #493:** owner plans weekend testing. Native timing #497 and iOS mTLS #454
  remain investigations; a successful native retry does not identify their cause.
- Required exact-source hosted checks, PR integration and release acceptance remain
  open. This local batch creates no issue, push, hosted run or release.

The repaired SQL/native recovery case also passes a second execution from the
successfully available saved record, proving fixture reuse and reconnect recovery
without clearing the database. The **17 shared listen-policy regressions** pass as
well. These cover eligibility, qualification, preview exclusion, interruption,
offline retry and timeline fallback with fixture providers; they remain separate
from the native/server-verified one-play check above. Log: `/private/tmp/aurral-final-repeated-recovery-and-listen-policy.log`
(**60 tasks, 20 executed, 1m4s**). The configured 13-method read-only run takes 16s;
the successful three-case native run takes 1m14s. Repeated executions are not counted
as extra unique acceptance cases. No product production source changed.
