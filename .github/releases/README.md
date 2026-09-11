# Versioned release notes

Copy `.github/RELEASE_TEMPLATE.md` to `vX.Y.Z.md` and edit it before creating the release tag.
Curated versioned notes are published verbatim. If a versioned file is absent, the tag workflow
generates a draft from merged pull requests using `.github/release.yml`, grouped by release-note
labels; `CHANGELOG.md` remains only a final compatibility fallback.

Every substantive bullet should link its accepted GitHub issue and merged pull request. Review the
generated GitHub draft for ordering and wording before publishing it. Publishing the release sends
the same body to Discord; then create the required Announcements Discussion linking to the release.
