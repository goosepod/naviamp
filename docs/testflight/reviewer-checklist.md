# Reviewer and operator checklist

Complete separately for each Apple app record. This is an operator template, not finished review
metadata. Fill private values directly in App Store Connect or the ignored local preparation file.

## Required verification

- [ ] Actual Apple app/platform/bundle ID, version/build, beta eligibility, expiry and device/OS support.
- [ ] Beta description, feedback email, platform-specific What to Test and accurate support/privacy URLs.
- [ ] Existing reviewer first/last name, email and phone checked; supply missing details privately.
- [ ] Sign-in requirement reflects the app. Provide a dedicated, reachable, least-privilege test server
  and account when review needs one. Verify access on the selected build and keep it available for review.
- [ ] Any demo mode/server is actually present and usable in this binary; do not invent a demo button.
- [ ] Encryption-compliance answers reviewed for the actual platform/package, not copied blindly.

## Suggested review instructions (complete before saving)

1. Open Naviamp and use its connection setup to select the supported server type.
2. Enter the dedicated test-server URL and credentials in Apple's private review fields.
3. Connect, browse the provided test library and play a track.
4. Pause/resume, seek, move to the next track and reopen the app to verify the saved connection.
5. For Mac, test keyboard/paste and the sandboxed file-picker flows available in this build.

Replace these generic steps with exact labels from the selected build. Explain any special server,
certificate, network or account requirement privately. Review must not depend on an unconfigured
personal music library or a reviewer obtaining their own server subscription.

## Before public-link distribution

- [ ] Internal TestFlight install passes recorded acceptance checks.
- [ ] First external build approved for beta testing; correct build assigned to the intended group.
- [ ] Owner-provided feedback email saved and review information complete.
- [ ] Proposed initial 100-tester limit and platform/OS criteria confirmed/configured as appropriate.
- [ ] Public landing page verified; actual URL recorded with platform/app/build association.
- [ ] No pending production submission replaced and no v2.10.0 feature advertised in the initial beta.

The initial packet was documentation-only. See [the operations checkpoint](status-2026-10-27.md)
for live follow-up: Mac beta review submitted, existing iOS public link verified, new Mac public-link
confirmation awaiting explicit approval. No tester email or announcement was sent.
