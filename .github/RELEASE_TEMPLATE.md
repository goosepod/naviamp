# Naviamp X.Y.Z

One or two sentences explaining the release's purpose and its most important user-visible outcome.
Write this for someone deciding whether to update, not as an internal development summary.

## Highlights

- The most important change in plain language.
- A second important change, if needed.
- A third important change, if needed.

## Features

- Describe the user-visible capability and why it is useful. ([Issue #123](https://github.com/goosepod/naviamp/issues/123), [PR #456](https://github.com/goosepod/naviamp/pull/456))

## Improvements

- Describe a meaningful improvement to existing behavior. ([Issue #123](https://github.com/goosepod/naviamp/issues/123), [PR #456](https://github.com/goosepod/naviamp/pull/456))

## Bug fixes

- State what was broken and the behavior that is now restored. ([Issue #123](https://github.com/goosepod/naviamp/issues/123), [PR #456](https://github.com/goosepod/naviamp/pull/456))

## Upgrade notes

- Explain migrations, changed defaults, compatibility requirements, or actions users must take.
- Write `No special upgrade steps are required.` when there are none.

## Known issues

- List important known limitations and affected platforms.
- Write `No new known issues.` when there are none.

## Downloads and details

- [Download Naviamp X.Y.Z](https://github.com/goosepod/naviamp/releases/tag/vX.Y.Z)
- [See every change since X.Y.W](https://github.com/goosepod/naviamp/compare/vX.Y.W...vX.Y.Z)
- [View the release milestone](https://github.com/goosepod/naviamp/milestone/NUMBER?closed=1)

<!--
Release-author guidance:

- Remove this comment and every unused example before publication.
- Never use emoji in Naviamp release notes or announcements.
- Use the previous public release as the comparison baseline. Do not treat intermediate branch or
  prerelease behavior as something users received.
- Present a first-time platform or feature as a complete new capability. Fold its prerelease fixes,
  navigation revisions, visual refinements, and acceptance discoveries into that feature description
  instead of publishing them as Improvements or Bug fixes.
- Lead with the changes that most expand or redefine the product, even when smaller work was completed
  more recently or produced a larger diff. A new platform or multi-device capability should dominate
  the opening summary and Highlights.
- Put an item under Improvements or Bug fixes only when it changes behavior that existed in the
  previous public release.
- Keep the opening summary and Highlights useful on their own because notification clients may show
  only the beginning of the release.
- Keep bullets short, independent, and free of deep nesting. Avoid tables; they do not transfer well
  to compact notification surfaces.
- Use ordinary sentence case for headings and prose.
- Link user-facing bullets to their GitHub issue and pull request. Link a specific commit only when
  that exact commit is materially useful; the pull request already provides the normal commit trail.
- Use absolute GitHub URLs so links work both on GitHub and when the body is forwarded to Discord.
- Put internal refactors under Improvements only when users experience a meaningful result. Omit
  purely internal work from the public notes unless it affects compatibility, security, packaging,
  or future maintenance in a way users should understand.
- Do not duplicate every bullet in Highlights. Highlights are a short orientation; the categorized
  sections are the complete user-facing record.
-->
