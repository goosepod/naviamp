# Naviamp Development Workflow

## Canonical repository

GitHub became Naviamp's primary repository and source of truth on 2026-09-11. GitHub is canonical
for source, issues, pull requests, checks, tags, and releases. Forgejo is a secondary mirror that the
project owner updates manually after accepted GitHub changes and releases.

Do not merge independent work, create canonical tags, or publish releases on Forgejo. A divergent
Forgejo ref is an error to investigate, never a reason to force or delete remote history.

## Work tracking

- Create a GitHub issue for every feature, bug fix, or meaningful update before implementation.
- Record the problem, intended behavior, scope, acceptance criteria, platform impact, and evidence.
- Assign applicable release-note labels: Feature, Improvement, Bug fix, Upgrade note, or Known issue.
- Use GitHub milestones to express intended release scope.
- Link each pull request to its issue with a closing keyword when merging should close the issue.

Small repository-only maintenance may use a lightweight issue, but released behavior, dependencies,
packaging, security, and user documentation must remain tracked.

## Branches and pull requests

- Use a dedicated short-lived branch such as `feature/<issue>-<slug>`, `fix/<issue>-<slug>`, or
  `chore/<issue>-<slug>`.
- Keep the branch limited to its issue and open a linked pull request against `main`.
- Merge only complete, reviewed work after all required GitHub checks and proportionate manual tests
  pass. Keep `main` releasable.
- Prefer squash merging unless preserving multiple commits has genuine diagnostic value.
- Delete merged issue branches; GitHub issues and pull requests retain the durable record.

## Release flow

1. Select completed work with a GitHub milestone and confirm it is merged into `main`.
2. Add appropriate automated performance and regression tests for every release-scoped change, and
   require those tests to pass before creating the release tag.
3. Cut `release/<version>` from the accepted `main` commit for focused stabilization.
4. Limit that branch to release blockers, documentation, versioning, and packaging fixes, each linked
   to an issue and pull request when substantive.
5. Merge each release fix back into `main` immediately, or fix `main` first and cherry-pick it.
6. Run the complete release verification matrix against the release branch.
7. Create `.github/releases/vX.Y.Z.md` from `.github/RELEASE_TEMPLATE.md`.
8. Tag the accepted commit. The tag workflow builds all artifacts and creates a draft GitHub Release.
9. Review and publish the draft, then create an Announcements Discussion linking to the release.
10. Close the milestone when its shipped state is accurate.
11. Mirror the accepted commits and tags to Forgejo using the procedure below.

Release notes compare against the previous public release, lead with product significance, and link
substantive changes to accepted GitHub issues and pull requests. First-time capabilities are described
as complete additions rather than lists of prerelease fixes.

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
Normal non-fast-forward rejection is the safety check that prevents overwriting newer Forgejo work.
Compare retained refs after each mirror and investigate any mismatch.

## Hotfixes

Branch from the affected release tag, verify and release the smallest safe patch, and merge the same
fix back into `main`. Use a GitHub issue, milestone, pull request, release notes, and Announcement as
for a normal release.

## Cutover record

- GitHub/Forgejo `main` and all retained release tags matched at the write freeze.
- Forgejo had no open issues, pull requests, or releases requiring migration.
- GitHub issue and pull-request templates, release labels, milestone support, signing secrets,
  Actions permissions, release webhook, and Announcements category were verified.
- GitHub rules require pull requests and the cross-platform verification checks on `main` and active
  `release/*` branches; force pushes and deletion are blocked.
- Forgejo CI definitions were removed from the canonical repository.
- GitHub issue numbers are the permanent work identifiers used in branch names and documentation.
- Historical documents retain dated Forgejo references as historical evidence.
