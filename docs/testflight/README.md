# TestFlight preparation

Tracking: [#355](https://github.com/goosepod/naviamp/issues/355). Prepared October 27, 2026.
This packet covers iOS and macOS separately and preserves the pending public App Store submissions.
It does not select the unreleased v2.10.0 candidate for beta distribution.

## Current evidence

| Platform | Retained evidence | Still needs live verification |
| --- | --- | --- |
| macOS | App Store Connect app 6821135881; bundle app.naviamp.desktop; 2.9.0 (57). Local October 20 resubmission record says Waiting for Review. Packaging PR #249 targets Apple Silicon/macOS 12+. | TestFlight processing/beta status, compliance, actual eligible build, expiry, internal install and external approval. Production review status is not beta status. |
| iOS | Issue #248 records a successful independent 2.9.0 (57) upload. Repository bundle identifier is app.naviamp.ios. | App Store Connect app ID, uploaded bundle/version/build, device/OS requirements, beta status, compliance, expiry and real installation. |

The earlier October 9 Mac upload-processing record is superseded by the October 20 local
production-review record. Neither establishes current TestFlight availability. Verify the actual
uploaded binary metadata; repository/Xcode default version fields and ad-hoc Mac ZIPs are not proof.

Live access is currently blocked: computer control reports `Sky Computer Use native pipe startup
failed`; browser inventory is empty and the in-app browser is unavailable. No Apple connector is
exposed. No App Store Connect API-key environment variables or key files were found in the standard
checked locations. Do not extract browser/Xcode session credentials to bypass this limitation.

## Owner choices and private inputs

- Distribution: public TestFlight link, with separate Mac/iOS cohorts for platform-specific feedback.
- Feedback email: supplied by the owner; keep the actual value in the local private preparation file
  and enter it into Apple's Feedback Email field. This also becomes the invitation reply-to address.
- Proposed group names: `Naviamp Internal`, `Naviamp iOS Public Beta`, `Naviamp Mac Public Beta`.
  Reuse suitable existing groups; do not create duplicates. Confirm whether Apple app records are
  separate before applying groups or links.
- Proposed starting public-link limit: 100 per cohort, adjustable after initial feedback. This is a
  proposal, not an already configured limit. Apply platform/device/OS criteria from the chosen build.
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
   URL. No link has been created yet; never invent an Apple public URL.
7. Share the verified link when the owner asks to announce/invite. No email, social announcement,
   GitHub release or pending production submission is sent/changed by this preparation.
8. Track crashes/feedback by platform/build. Record build expiry and replacement plan; notify testers
   deliberately when switching builds. Do not assume a later version is automatically accepted.

## Installation acceptance matrix

All rows are pending; earlier development-signed or simulator checks are not TestFlight installs.

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
