# TestFlight preparation

Tracking: [#355](https://github.com/goosepod/naviamp/issues/355). Prepared October 27, 2026.
This packet covers iOS and macOS separately and preserves the pending public App Store submissions.
Its original operations applied to v2.9.0/build 57.

The owner accepted the Windows v2.10.0/build 60 candidate on October 31. The
[v2.10.0 distribution handoff](../releases/v2.10.0-readiness.md) now selects that version
for release preparation. This older packet and its console observations remain historical;
they do not establish v2.10.0 upload, review, enrollment or installation. Reuse the existing
app records and groups after checking their live status.

## Historical evidence

Live browser verification resumed October 27 through native Firefox control. See the
[current operations checkpoint](status-2026-10-27.md) for completed changes and remaining gates.

| Platform | Live verified record | Remaining |
| --- | --- | --- |
| macOS | App 6821135881, 2.9.0 (57), internal group exists; external group Naviamp Mac Public Beta created and beta review submitted. Now Waiting for Review; 73 days remaining at inspection. | Beta approval, opening enrollment after approval as needed, actual TestFlight installation/acceptance. |
| iOS | App 6821135545, bundle app.naviamp.ios, 2.9.0 (57), Approved for TestFlight; existing internal group and Naviamp Testers external group/public link (100 limit). Uploaded build minimum iOS 17.2, iPhone/iPad. | Actual installation/acceptance and feedback. Existing public landing page verified; URL retained in private operator record. |

Public App Store states were separately verified: Mac Waiting for Review; iOS Rejected. The iOS
production rejection does not describe its approved TestFlight build. Neither pending production
submission was changed. Historical October 9/20 upload/review notes are superseded by these live
observations, not treated as beta evidence.

An updated Apple Developer Program License Agreement warning was displayed. The Account Holder subsequently
reported accepting it; no agreement was accepted by the agent. Apple nevertheless accepted the
existing Mac build's beta-review submission. Do not infer all future upload/submission operations are
unblocked. Chrome browser-channel creation still failed; native Firefox supplied the working path.

## Owner choices and private inputs

- Distribution: public TestFlight link, with separate Mac/iOS cohorts for platform-specific feedback.
- Feedback email: supplied by the owner; keep the actual value in the local private preparation file
  and enter it into Apple's Feedback Email field. This also becomes the invitation reply-to address.
- Initial proposed group names: `Naviamp Internal`, `Naviamp iOS Public Beta`, `Naviamp Mac Public Beta`.
  Reuse suitable existing groups; do not create duplicates. Confirm whether Apple app records are
  separate before applying groups or links.
- Public-link limit: 100 per cohort. The iOS group already had this limit; the owner explicitly
  approved supported Macs and a 100-tester cap for the Mac group, and those saved settings were
  verified. Apply platform/device/OS criteria from the chosen build when changing eligibility.
- Private reviewer contact name/email/phone, sign-in requirements and dedicated review-server access
  must be verified in App Store Connect. Never commit credentials, tester emails, review contact
  details, tokens or provisioning profiles. Do not grant community testers App Store Connect roles.

The local private file is `build/testflight-prep-355/private-config.json` in the owner's original
workspace. It is ignored and restricted to the owner. Public templates deliberately omit its values.

## Ready-to-paste material

- [Beta description](beta-description.txt): shared English description without unreleased claims.
- [iOS What to Test](what-to-test-ios.txt).
- [Mac What to Test](what-to-test-macos.txt).
- [Reviewer and operator checklist](reviewer-checklist.md): fill private fields directly in Apple.

These drafts apply to the initial 2.9.0/build 57 plan only after confirming the selected binary.
Update them for another explicitly selected build. Do not advertise handoff expansion, Easy Fade,
new Android Auto features or v2.10.0-only changes as available in these uploads.

## App Store Connect runbook

1. Open each app's TestFlight tab. Record app ID, platform, bundle ID, version/build, upload date,
   processing/compliance state, internal/external beta state and expiry. Distinguish public App Review
   from TestFlight App Review. Resolve missing information using the actual build/crypto details;
   do not guess an encryption-exemption answer, especially for the Mac JVM/native package.
2. Confirm the build is eligible and not marked TestFlight Internal Only. Prefer the existing
   2.9.0 (57) upload if valid. Do not upload, replace or renumber it just to create groups.
3. Inspect existing Test Information, review details, privacy/support URLs and sign-in instructions.
   Save the description and owner-supplied feedback address; complete reviewer fields privately.
   Verify all URLs and reviewer access. The draft is not ready for submission with missing fields.
4. Create/reuse the internal group first. Add the eligible build and existing authorized team testers.
   Do not add new team roles to work around external beta review. Install through TestFlight on an
   actual supported device; record the matrix below.
5. Create/reuse the appropriate external group. Add the verified build and platform-specific What to
   Test. Prepare/submit TestFlight review where required, independently of the production submission.
   Keep automatic notifications off while preparing so approval does not immediately notify testers.
6. After beta approval and internal acceptance, enable the public link, verify criteria/limit and
   confirm the actual landing page, app/platform/build eligibility and installation. Record the real
   URL. Check the operations checkpoint before creating a link; reuse existing links and never invent an Apple public URL.
7. Share the verified link when the owner asks to announce/invite. No email, social announcement,
   GitHub release or pending production submission is sent/changed by this preparation.
8. Track crashes/feedback by platform/build. Record build expiry and replacement plan; notify testers
   deliberately when switching builds. Do not assume a later version is automatically accepted.

## Installation acceptance matrix

Installation rows remain pending; successful metadata/group/link setup does not prove an install.
Earlier development-signed or simulator checks are not TestFlight installs.

| Check | iOS | macOS |
| --- | --- | --- |
| Correct version/build and supported OS/device, fresh TestFlight install | Pending | Pending |
| Connect/login to dedicated test server; relaunch retains usable connection | Pending | Pending |
| Browse/search, artwork and queue metadata | Pending | Pending |
| Audible playback, pause/resume, seek, previous/next and queue advancement | Pending | Pending |
| Existing Radio/Autoplay behavior, if exposed, including refill/end of queue | Pending | Pending |
| Network interruption/reconnect, token renewal and helpful errors | Pending | Pending |
| Lock/background, calls/output interruption where supported | Pending | N/A to phone-call flow; test sleep/output changes |
| File/import/export/download behavior if exposed | Pending | Pending, including sandbox dialogs/access |
| Keyboard/paste, menus, resize/minimize/restore, visualizers if exposed | N/A to desktop window flow | Pending |
| Feedback submission with app/build/device and reproducible steps | Pending | Pending |

Retest after build selection changes. Keep test libraries/accounts disposable; redact credentials,
server addresses and personal media from public evidence. Any product fix gets a dedicated issue/PR
and begins in common code. Do not mutate pending Store builds implicitly during beta preparation.

## Verified Apple guidance

- [Test information](https://developer.apple.com/help/app-store-connect/test-a-beta-version/provide-test-information/): beta description and feedback address; private review information is separate.
- [External testing](https://developer.apple.com/help/app-store-connect/test-a-beta-version/invite-external-testers/): internal group first, external groups, first-build review and public-link criteria/limits.
- [Build states](https://developer.apple.com/help/app-store-connect/reference/app-uploads/app-build-statuses/): check eligible/ready/testing/expired status rather than relying on upload success.
- [Internal testing](https://developer.apple.com/help/app-store-connect/test-a-beta-version/add-internal-testers/): existing eligible team members; Internal Only builds cannot go to external groups.

These references were checked October 27, 2026. Public beta approval is separate from public App Store
approval. TestFlight builds have a 90-day testing window; use Apple's displayed expiry for operations.
