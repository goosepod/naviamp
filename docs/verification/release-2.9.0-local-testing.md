# v2.9.0 release-branch testing

Tracking: [#224](https://github.com/goosepod/naviamp/issues/224).

This preparation sets VERSION to v2.9.0 and VERSION_CODE to 57, updates CHANGELOG.md,
the prepared release notes, and the shared English About-page changelog. The comparison
baseline is the published v2.8.0 release. Desktop Cast and its pre-release acceptance fixes
are presented as one new capability. Deferred macOS/Linux physical checks remain documented.

After the preparation PR is verified and merged, cut release/2.9.0 from accepted main.
Keep that branch available for owner testing; it is not a release tag or publication.
The release milestone stays open until the actual release ships.

## Windows test app

The local staged app is built with the development data profile:

```text
.windows-testing/release-prep-2.9/build/local-test/Naviamp/Naviamp.exe
```

It uses `%APPDATA%/Naviamp Development`, including the existing development queue and
saved connections used for Cast verification. The installed production app uses a separate
profile. Close older staged development builds before opening this one so only one process
uses the development database. Reopening this candidate should restore the queue paused.

In Settings > About, confirm version v2.9.0/build 57. Open Changelog and check that desktop
Cast leads, followed by album/artist downloads, fullscreen, Linux media controls, TV accounts
and album artwork choices. The upgrade and known-issue entries should remain readable.

Suggested owner checks: normal library browsing and playback, Cast transport and handoff,
fullscreen exit/restore, menus/dialogs, DJ creation/selection, album artwork settings and
downloads appropriate to the test profile. Record concrete failures for release stabilization.

Existing downloads are retained by the database migration. Returning to a v2.8.0 binary
requires a pre-upgrade database backup while preserving the audio files; do not downgrade
the sole copy of a migrated profile.

## Build and verification

Shared changelog regression and rendered About UI tests cover the important entries and
their order. Shared Android/iOS compilation and Core-first architecture verification are
part of preparation, followed by the protected GitHub matrix.

Windows staging uses `:apps:desktop:stageLocalTestApp`. The local build reuses the unchanged
audio and OpenGL DLLs from the verified Cast build because this workspace previously hit
a deep-path Visual Studio scratch-directory failure. This is not a clean local native rebuild;
the protected matrix independently builds and checks the native platforms.

No Android, Desktop or iOS production adapter is changed by release preparation. Changelog
content stays in shared Core; existing navigation/control labels stay localized.

Before eventual publication, remove the testing notice from the prepared release notes,
add the release download link, complete release acceptance and follow the tagging and
announcement steps in docs/development-workflow.md.
