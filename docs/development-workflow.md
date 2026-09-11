# Naviamp Development Workflow

## Repository transition

Naviamp plans to make the public GitHub repository the primary repository and source of truth.
Forgejo is currently the primary remote and GitHub is its public mirror; that remains true until the
project owner explicitly completes the cutover. After the cutover:

- GitHub is the canonical home for source, issues, pull requests, continuous integration, tags, and
  releases.
- Forgejo is a secondary mirror maintained manually by the project owner.
- Contributors and automation must not treat Forgejo as an independent place to merge changes or
  publish releases.
- Changing Git remotes, branch protection, CI secrets, or release automation is a separate cutover
  operation and must not be inferred from this plan.

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

1. Select the completed issues intended for the release with a GitHub milestone.
2. Confirm that every selected issue is merged into `main`, satisfies its acceptance criteria, and
   has appropriate release-note material.
3. Cut `release/<version>` from the exact accepted `main` commit when the release enters its focused
   stabilization and platform-testing period.
4. Allow only release blockers, documentation, versioning, and packaging fixes onto the release
   branch. Give each substantive release fix an issue and pull request.
5. Merge every release-branch fix back into `main` immediately, or apply the fix to `main` first and
   cherry-pick it into the release branch, so the lines cannot drift.
6. Run the full release verification matrix and required device/platform acceptance against the
   release branch.
7. Tag the accepted release commit, publish all artifacts as one GitHub Release, and create the
   required GitHub Discussion in the **Announcements** category.
8. Close the milestone and update or close its included issues only when their shipped state is
   accurately represented.
9. Mirror the resulting commits and tags back to Forgejo manually. The GitHub release remains the
   canonical published release.

If an issue must be removed after the release branch is cut, prefer fixing or reverting that issue's
complete pull request rather than assembling a release from an undocumented collection of commits.

## Release notes and Discord announcements

Create `.github/releases/vX.Y.Z.md` from [`.github/RELEASE_TEMPLATE.md`](../.github/RELEASE_TEMPLATE.md)
for the GitHub Release body. The tag workflow uses that versioned file verbatim when it exists and
falls back to the matching `CHANGELOG.md` section for older releases. The project's Discord
**announcements** channel receives new GitHub releases through a webhook, so the same release body
must work as both the detailed GitHub page and a compact Discord announcement.

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
- Link each substantive change to its GitHub issue and pull request. Prefer those durable records to
  individual commit links because they contain the rationale, acceptance criteria, verification, and
  complete commit history.
- Add a GitHub comparison link for the complete commit-level changelog. Link an individual commit
  only when that exact commit is important to understand or audit.
- Use absolute GitHub URLs so links continue to work after the release body is forwarded to Discord.

The GitHub Release is the canonical announcement record. The required GitHub Discussion may use a
shorter editorial introduction, but it should link back to the release rather than maintain a
different list of changes. The Discord webhook is notification delivery, not a third changelog that
must be edited separately.

## Hotfixes

For an urgent fix to an already published version, branch from the affected release tag, verify and
release the smallest safe patch, and merge the same fix back into `main`. Use a GitHub issue,
milestone, pull request, release notes, and Announcement just as for a normal release.

## Cutover checklist

- [ ] Declare the GitHub cutover date and freeze writes to Forgejo during the final synchronization.
- [ ] Verify that all branches and tags intended to be retained exist on GitHub.
- [ ] Make GitHub the default development remote and update contributor documentation.
- [ ] Configure branch protection and required checks for `main` and active release branches.
- [ ] Verify issue templates, pull-request templates, labels, milestones, and permissions.
- [ ] Update the tag workflow to populate the new release format from accepted issue and pull-request
      metadata, then verify the resulting draft on both GitHub and the Discord announcements webhook.
- [ ] Verify release secrets and the tag-driven release workflow without copying secrets into the
      repository.
- [ ] Decide whether GitHub issue numbers become the permanent work identifiers in branch names and
      documentation.
- [ ] Document the exact manual Forgejo mirror commands and confirm that mirroring cannot overwrite
      newer GitHub work.
- [ ] Mark historical documentation that calls Forgejo canonical as historical rather than silently
      rewriting past acceptance records.
