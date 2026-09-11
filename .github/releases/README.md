# Versioned Release Bodies

Create one file named after the exact release tag, such as `v2.5.0.md`, by copying and completing
`.github/RELEASE_TEMPLATE.md`. Commit it to the release branch before creating the tag.

The tag workflow publishes this file verbatim as the GitHub Release body. GitHub then forwards the
same release through the project's Discord announcements webhook. If the versioned file is absent,
the workflow falls back to extracting the matching section from `CHANGELOG.md` for compatibility
with older release branches.

Do not add a versioned file until its release contents and links are known. Remove all template
instructions and unused sections before publication.
