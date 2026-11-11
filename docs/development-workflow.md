# Naviamp Development Workflow

## Canonical repository

GitHub became Naviamp's primary repository and source of truth on 2026-09-11. GitHub is canonical
for source, issues, pull requests, checks, tags, and releases. Forgejo is a secondary mirror that the
project owner updates manually after accepted GitHub changes and releases.

Do not merge independent work, create canonical tags, or publish releases on Forgejo. A divergent
Forgejo ref is an error to investigate, never a reason to force or delete remote history.

## Work tracking

- Create a GitHub issue for every feature, bug fix, or meaningful update before implementation.
- Document the problem, intended behavior, scope, acceptance criteria, platform impact, and relevant
  evidence in the issue. Keep design decisions and material scope changes in the issue so it remains
  the durable record of the work.
- Assign each issue and pull request a release-note category when applicable: Feature, Improvement,
  Bug fix, Upgrade note, or Known issue. A change may contribute to more than one section, but its
  public description should not be duplicated unnecessarily.
- Use GitHub milestones to identify the issues proposed for a particular release. An issue being in a
  milestone expresses release intent, not permission to ship incomplete work.
- Link pull requests to their issue. Use GitHub's closing keywords when merging the pull request
  should close the issue automatically.

Small repository-only maintenance changes may use a lightweight issue, but should still be tracked
when they affect released behavior, dependencies, packaging, security, or user documentation.

## Branches and pull requests

- Give each issue its own short-lived branch, normally named `feature/<issue>-<slug>`,
  `fix/<issue>-<slug>`, or `chore/<issue>-<slug>`.
- Keep a branch limited to its issue. If implementation uncovers unrelated work, create and link a
  separate issue instead of silently expanding the branch.
- Open a pull request against `main`, link the issue, and keep the issue's acceptance criteria and
  the pull request's verification results current.
- Merge only complete, reviewed work whose required automated checks and proportionate manual tests
  pass. Incomplete features should remain on their issue branch unless they are safely disabled by a
  deliberate feature flag.
- Prefer squash merging for a focused issue branch unless preserving multiple commits adds genuine
  diagnostic or historical value.
- Delete merged issue branches after their pull requests land. Git history and the linked issue keep
  the durable record.

`main` should remain releasable. Integrating completed work continuously avoids a long-lived release
integration branch, large late merge conflicts, and fixes that exist only in one release line.

## Release flow

1. Select completed work with a GitHub milestone and confirm it is merged into `main`.
2. Add appropriate automated performance and regression tests for every release-scoped change, and
   require those tests to pass before creating the release tag.
3. Cut `release/<version>` from the accepted `main` commit for focused stabilization.
4. Limit that branch to release blockers, documentation, versioning, and packaging fixes, each linked
   to an issue and pull request when substantive.
5. Merge each release fix back into `main` immediately, or fix `main` first and cherry-pick it.
6. Run the complete release verification matrix against the release branch.
7. Create `.github/releases/vX.Y.Z.md` from `.github/RELEASE_TEMPLATE.md`. Audit `README.md`
   and the public Naviamp website (including feature, compatibility, download and installation
   pages) against the accepted source and live distribution status. Correct stale information and
   record source/publication links, checks and remaining limitations in the release readiness
   document. This audit is required for every release before publication; pending releases must
   not be presented as already available. Update and render-verify the shared About changelog and
   run all changelog/About regression suites before tagging.
8. Tag the accepted commit. The tag workflow builds all artifacts and creates a draft GitHub Release.
9. Review the draft and verify its name is exactly `Naviamp vX.Y.Z`, with no descriptive suffix.
   Publish it, verify the published name, then create an Announcements Discussion linking to the release.
10. Close the milestone when its shipped state is accurate.
11. Mirror the accepted commits and tags to Forgejo using the procedure below.

The pull-request Verify workflow records immutable evidence for the exact Git tree that passed the
complete cross-platform matrix. When a release tag points at that same tree, the tag workflow
validates the evidence and the successful originating workflow run before reusing it. If the tree or
evidence does not match, the tag workflow runs the complete matrix again. Release packaging runs in
parallel with this gate, but the draft release cannot be created unless reused or fresh verification
and every packaging job succeed.

If an issue must be removed after the release branch is cut, prefer fixing or reverting that issue's
complete pull request rather than assembling a release from an undocumented collection of commits.

### Scope freeze and concurrent contributions

The release-preparation PR is a focused update for versioning, changelogs, contributor credits, and
release evidence after the selected product PRs have merged into `main`. It is not an integration
branch for outstanding feature PRs. Unmerged work moves to a later milestone rather than blocking
the release or being merged indirectly through its preparation PR.

Record the accepted `main` commit and selected scope when cutting `release/<version>`. After that
cut, normal contributor PRs still target `main` and can merge for a later release. Do not merge the
advancing `main` branch into a frozen release branch: bring across only individually reviewed
release blockers and required release metadata through focused PRs. A release branch does not need
to contain every commit currently on `main` to ship.

Fix blockers in `main` first and port the accepted fix to the release branch when practical. A
release-first fix must return to `main` promptly through a focused PR, without pulling unrelated
release version or changelog edits over newer mainline metadata. Verify the actual ported diff;
conflict resolution can change behavior even when the original fix passed.

Any late scope addition requires an explicit scope decision, updated notes and contributor credits,
and fresh verification of the changed candidate. Never inherit passing status from an earlier tree.
Before merging a contributor PR, compare it with current `main`, resolve conflicts on its own
branch, and rerun the affected checks. Resolving a conflict must not silently broaden release scope.

## Release notes and Discord announcements

GitHub release names must be exactly `Naviamp vX.Y.Z` (for example, `Naviamp v2.9.0`).
Do not append subtitles, feature descriptions, codenames, or other suffixes. Put those details
in the release body or announcement text.

Create `.github/releases/vX.Y.Z.md` from [`.github/RELEASE_TEMPLATE.md`](../.github/RELEASE_TEMPLATE.md)
for the GitHub Release body. The tag workflow uses that versioned file verbatim when it exists. If
it is absent, GitHub generates a draft from labeled merged pull requests before the workflow uses
the matching `CHANGELOG.md` section as a compatibility fallback. Discord publication uses
`.github/workflows/release-announcement.yml` and the versioned
`.github/releases/vX.Y.Z-announcement.md` plain-list copy, preserving Thanks without detailed
per-change GitHub references. The existing Discord channel webhook is reused through the
`NAVIAMP_RELEASE_DISCORD_WEBHOOK` Actions secret; its legacy native GitHub release hook must be
inactive before publication so each release is announced once. Verify this configuration and a
`send-release-announcement.py --dry-run` result in release readiness. The published-event workflow
checks out the immutable release tag. Require a matching versioned announcement and keep it within
the single-message description limit. A failed or uncertain POST is not retried automatically;
inspect channel delivery before retrying. A template edit alone does not configure delivery.

- Begin with a plain-language summary followed by no more than three highlights. The beginning must
  remain useful if a notification surface shows only part of the body.
- Write from the perspective of someone upgrading from the previous public release. Development-only
  iterations are not release changes and should not appear in the published notes.
- Describe a platform or feature that has never shipped before as one cohesive new addition. Do not
  split its prerelease focus fixes, layout adjustments, navigation changes, or other acceptance work
  into public improvement and bug-fix bullets.
- Rank content by product importance. New platforms and major capabilities lead; polish and fixes to
  previously released behavior follow.
- Group the complete notes under Features, Improvements, Bug fixes, Upgrade notes, and Known issues.
  Omit an empty Features, Improvements, or Bug fixes section. State explicitly when there are no
  special upgrade steps or no new known issues.
- Keep bullets concise and independently understandable. Avoid tables, deep nesting, raw issue-title
  dumps, and internal implementation language.
- Do not use emoji.
- In GitHub release notes, link each substantive change to its GitHub issue and pull request. Prefer those durable records to
  individual commit links because they contain the rationale, acceptance criteria, verification, and
  complete commit history.
- Add a GitHub comparison link for the complete commit-level changelog. Link an individual commit
  only when that exact commit is important to understand or audit.
- In GitHub release notes, credit community contributors on each relevant change by public GitHub handle, distinguishing
  reporting, implementation, testing, documentation, and translation work. Keep the originating
  issue and implementation PR links next to that change; use a commit link when neither exists.
- Include a deduplicated **Thanks** section for community contributions in the GitHub release,
  About-page release changelog, Discord announcement, and Announcements Discussion. Include people
  whose release contributions are not represented by commit authorship, and honor anonymous credit
  requests. Omit the section when there are no community contributors to thank.
- Welcome first-time contributors in Thanks. Verify prior release acknowledgments and repository
  contribution history, including non-code contributions, rather than trusting GitHub's generated
  first-PR list alone. First-time means the person's first release acknowledgment for contributing
  to Naviamp. Use previous release Thanks sections as the durable acknowledgment record, and credit
  only contributions included in the release.
- Keep About-page changelogs, Discord posts, and similar announcements as their existing plain
  bulleted lists, adding only Thanks. Do not append issue, PR, or commit references or per-change
  contributor credits to those lists. Prepare this plain-list version for Discord delivery rather
  than forwarding the detailed GitHub release body unchanged.
- Use absolute GitHub URLs so links remain usable outside the repository and in copied release notes.

The GitHub Release is the canonical announcement record. The required GitHub Discussion may use a
shorter editorial introduction, but it should link back to the release rather than maintain a
different list of changes. Derive the plain Discord announcement and its Thanks section from that
same accepted release content; formatting differences must not introduce a different release scope.

## Recovering an existing release build

If an external build-tool or runner change breaks packaging after a verified release tag was
created, fix the workflow through a linked pull request to `main`. Keep the existing tag immutable.
The **Tag release builds** workflow can be dispatched from accepted `main` with `release_tag` set
to that existing tag. It uses the corrected workflow while checking out the tagged source in every
verification and packaging job. The tag must match that source's `VERSION`, the complete verification
matrix still gates packaging, and publication still creates a draft for review. Leaving the input
blank builds the selected branch without creating a release.

```shell
gh workflow run tag-release-builds.yml --ref main -f release_tag=vX.Y.Z
```

## Manual Forgejo mirror

Run from a clean clone whose `origin` is GitHub and whose `forgejo` remote is the secondary server:

```shell
git fetch origin --prune --tags
git push forgejo origin/main:refs/heads/main
git push forgejo --tags
```

Push an accepted release branch explicitly only while it is active:

```shell
git push forgejo origin/release/X.Y.Z:refs/heads/release/X.Y.Z
```

Never use `--mirror`, `--force`, `--force-with-lease`, or remote pruning/deletion against Forgejo.
Normal non-fast-forward rejection prevents overwriting newer Forgejo work. Compare retained refs
after each mirror and investigate any mismatch.

## Hotfixes

For an urgent fix to an already published version, branch from the affected release tag, verify and
release the smallest safe patch, and merge the same fix back into `main`. Use a GitHub issue,
milestone, pull request, release notes, and Announcement just as for a normal release.

## Cutover record

- [x] Declared the GitHub cutover on 2026-09-11 and froze Forgejo writes for final synchronization.
- [x] Verified all retained release tags and branches; the active Android TV branch is tracked by
      GitHub issue #17 and its GitHub pull request.
- [x] Made GitHub the default development remote and updated contributor documentation.
- [x] Configured a GitHub ruleset for `main` and `release/*` requiring pull requests and the full
      cross-platform matrix, including Android API 30 and API 35 emulator checks, while blocking
      deletion and non-fast-forward updates.
- [x] Verified templates, release labels, the v2.5.0 milestone, permissions, signing secrets,
      webhooks, and the Announcements Discussion category.
- [x] Added labeled pull-request release-note generation while retaining curated versioned notes.
- [x] Adopted GitHub issue numbers as permanent work identifiers.
- [x] Documented and exercised non-forcing manual Forgejo mirroring.
- [x] Marked current planning references to Forgejo's former role as historical while preserving
      dated acceptance records.

## Translation-only pull-request verification

Verify classifies changed files rather than the PR author or title. Modifications confined to
existing `core/ui/src/commonMain/composeResources/values-*/strings.xml` locale catalogs use a
lightweight path; documentation may accompany them. New, deleted or renamed catalogs, source
`values/strings.xml`, code, build scripts and workflow changes require the full matrix.

The lightweight path checks the diff and validates the changed catalogs against the source:
XML structure, duplicate/unknown keys, resource types, plural quantities, arrays and indexed
string/integer format arguments. Missing translations may use source fallback. It runs without
Gradle, app builds, emulators or native playback tests. The existing required Shared/Android
checks execute lightweight steps; native platform jobs report skipped checks under their unchanged
names. Invalid catalogs fail the required Shared and Android check, and classification errors fail
closed. No workflow-level path filter or bot exemption is used.

Reusable release verification always runs the full matrix. Only a successful full matrix can
publish verified-source evidence for release reuse; a successful translation-only run cannot.
For local resource validation, run:

```shell
python3 scripts/validate-translations.py core/ui/src/commonMain/composeResources/values-de/strings.xml
python3 -m unittest discover -s scripts/tests -p 'test_verification_*.py' -v
```
