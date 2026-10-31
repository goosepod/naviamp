# Naviamp v2.10.0 final scope and release pass

Tracking: [#440](https://github.com/goosepod/naviamp/issues/440). This record supersedes historical release holds in earlier readiness reports where they conflict with the owner's October 30 decisions. The release remains **v2.10.0 / build 60**, compared with the previous public release, v2.9.0.

## Frozen scope

Ship the completed application already integrated into main at `081b8f1b12dd823539c5b354a7702dd114590598`, plus this final release-content update. The release notes, repository changelog and shared About changelog describe Aurral discovery and sample/request support first, followed by certificate-protected connections, artist playlists/settings, smart-playlist editing and preview, Library search, Radio and playback recovery, Windows popup/credential fixes, shared accessible player sliders and live storage totals. No additional feature implementation is selected.

The five remaining open PRs are unfinished drafts: Windows Store packaging #324, Mac App Store packaging #249, visualizers #242, Siri #174 and Google Assistant #173. They are excluded. Store submissions and the existing store review state are separate from direct-download release preparation; this pass does not replace a submission.

## Accepted deferrals

The owner explicitly accepts releasing with the outstanding Android performance and macOS testing, and previously accepted deferring the TV position findings. These are unverified or known limitations, not passing tests:

- #434: native TV playback can restart at zero after ordinary stream failure, failed far seek or Wi-Fi outage. Owner closed this issue before this pass; that closure is not evidence of a fix.
- #438: cold restart restored a paused position approximately 2.4 seconds earlier in the strict physical test. Root cause and appropriate timing tolerance remain unconfirmed.
- #414: material Android scrolling/progress CPU/compositor cost remains open. No animation budget is claimed met, and animation cadence is unchanged.
- #342: current-source visible macOS acceptance/performance remains incomplete. Earlier Mac owner acceptance and automated native/package results retain their narrower documented scope.
- #333: physical natural/background continuation succeeded in later acceptance, but the original reported TV Radio stop is not established fixed. Keep the existing public warning and follow-up.
- #330: intermittent API 35 initial raster visibility has retained failure evidence and unchanged successful reruns; root cause remains open.

Physical HDMI/CEC testing remains explicitly skipped by the owner. A physical iPhone, spoken screen-reader acceptance and other unavailable manual scenarios are not supplied by simulator, rendered-fixture or packaged Java accessibility results. Windows playback CPU work was deprioritized by the owner; earlier observations remain recorded. None of these accepted limitations is represented as a repaired defect or an independently successful test.

## Premature tag audit

GitHub has no public v2.10.0 or v2.10.1 release. The existing annotated tags were created before the release was accepted:

| Tag | Original tag object | Original source commit |
| --- | --- | --- |
| v2.10.0 | `1a465962eb146fe0824b1de2bc92aad9291d2666` | `6e602832abb7c3827481de3430a8593a3495fbca` |
| v2.10.1 | `51628af2aeca736d285918da2afb5f5d33302e86` | `fd17ca0bc1eedd77dcb0c26cbd151aee60c21c21` |

Both commits are ancestors of the accepted main baseline: `git log main..v2.10.0` and `git log main..v2.10.1` are empty. The v2.10.1 commit is the premature metadata/CI validation change #284/#285; later mainline work already restores v2.10.0/build 60 while retaining validation. There is no unique product change to recover from either tag. No work is discarded or silently reserved for a nonexistent public v2.10.1 release.

The owner has explicitly corrected the release identity to v2.10.0. The old tag identities above are retained for audit. After the Windows walkthrough and final acceptance, reconcile the mistaken unpublished refs to the accepted v2.10.0 source; a future v2.10.1 must be based on the published v2.10.0 plus its selected fixes. Do not release old tagged source or publish a higher version to evade this correction. This preparation pass does not publish, mirror or replace either tag before the walkthrough.

## Final gates

- Update and render the shared About changelog, including the deferred findings.
- Pass the protected full cross-platform matrix on the final shared content.
- Align `release/2.10.0` with the accepted main tree through a linked PR.
- Build all release packages from the accepted source with no release-tag input, retaining exact source and workflow provenance.
- Build Windows locally and launch its release app for the owner's final manual walkthrough. The owner must supply the outcome; do not infer it from launch success.
- Following acceptance, reconcile the unpublished tags, tag v2.10.0, inspect the draft and verify the exact title **Naviamp v2.10.0** before publication. Publication requires the matching release notes and an Announcements Discussion. Store submissions and any manual Forgejo mirror remain separate actions.

No Android, Desktop or iOS production file is changed by this release-content pass. The only application change is the English shared release changelog, which is exempt from translation catalogs under AGENTS.md.
