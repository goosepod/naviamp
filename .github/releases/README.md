# Versioned Release Bodies

Create one file named after the exact release tag, such as `v2.5.0.md`, by copying and completing
`.github/RELEASE_TEMPLATE.md`. Commit it to the release branch before creating the tag.

The tag workflow publishes this file verbatim as the GitHub Release body. If the versioned file is
absent, it generates a draft from labeled merged pull requests using `.github/release.yml`, then
uses the matching `CHANGELOG.md` section only as a final compatibility fallback. The Release
announcement workflow sends the matching `vX.Y.Z-announcement.md` plain-list copy
to Discord on publication, using the existing channel webhook. Keep the legacy native GitHub
release webhook inactive to avoid duplicate or incorrectly formatted announcements.

Do not add a versioned file until its release contents and links are known. Remove all template
instructions and unused sections before publication.

Every substantive bullet should link its accepted GitHub issue and merged pull request. Review the
generated draft for product-significance ordering and wording before publishing it, then create the
required Announcements Discussion linking to the release.
