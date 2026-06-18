# Release Builds

Naviamp builds release artifacts through Forgejo Actions on every push to `main`, which covers normal merge-to-main release candidates. The workflow also supports manual runs through `workflow_dispatch`.

Workflow:

```text
.forgejo/workflows/release-builds.yml
```

## Artifacts

- Android: release APK and release AAB, signed when the Android signing environment is configured.
- Windows: standalone app-image zip plus jpackage MSI/EXE installers.
- macOS: standalone `.app` zip plus jpackage DMG installer.

The current desktop installers are unsigned and the macOS DMG is not notarized.

## Versioning

Naviamp uses SemVer for the user-facing app version:

```text
VERSION
```

Android also requires a monotonically increasing integer version code for Google Play:

```text
VERSION_CODE
```

Gradle reads these root files for Android and desktop packaging:

- Android `versionName` comes from `VERSION`.
- Android `versionCode` comes from `VERSION_CODE`.
- Desktop installer/package version is derived from `VERSION`.

Native desktop installers built through jpackage do not accept a `0.x.y` package version on every platform, so pre-1.0 Naviamp versions are encoded with a positive native package major version for installer metadata. For example, app version `0.9.1` is packaged with native installer metadata version `1.9.1`. The source-of-truth user-facing version remains `VERSION`.

Current version:

```text
0.9.1
```

Before merging a feature branch into `main`, bump the version on the branch:

```shell
make bump-version PART=patch
make bump-version PART=minor
make bump-version PART=major
```

Default feature work should normally use `patch`. Larger user-facing feature sets can use `minor`; `major` is reserved for compatibility-breaking or 1.0-level milestones.

Validate the version files locally with:

```shell
make version-check
```

Forgejo runs `.forgejo/workflows/version-check.yml` on feature-branch pushes and pull requests to `main`. That check verifies `VERSION` is valid SemVer and `VERSION_CODE` increases relative to `origin/main`. To enforce this rule, make the version check a required status for merges into `main` in Forgejo branch protection.

The release workflow also validates the version files before building artifacts from `main`.

## Google Play Testing

Use Google Play testing tracks before public production release:

- Internal testing: fastest path for up to 100 trusted testers by email.
- Closed testing: invite-only prerelease testing through email lists or Google Groups.
- Open testing: public opt-in testing that can be found on Google Play when the listing is ready.

Recommended Naviamp rollout path:

1. Start with an internal testing release for local QA and a few trusted testers.
2. Move to a closed testing release when the Android Auto and playback flows are stable enough for broader feedback.
3. Use open testing only when the store listing, privacy details, support channel, and crash reporting are ready for public visibility.

Testers receive the highest `VERSION_CODE` available on a track they are eligible for, so every uploaded Android bundle must use a new `VERSION_CODE`.

## Android Release Signing

Google Play internal testing requires a signed Android App Bundle (`.aab`). Naviamp's Android release signing is configured through environment variables so keystore secrets stay out of git.

Generate an upload key once and keep it backed up securely:

```shell
mkdir -p "$HOME/Documents/keys"

keytool -genkeypair \
  -v \
  -keystore "$HOME/Documents/keys/naviamp-upload.jks" \
  -alias naviamp-upload \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Create a local signing env file from the example:

```shell
cp .env.android-signing.example .env.android-signing
```

Edit `.env.android-signing` with the passwords you entered when creating the keystore, then load it into your shell:

```shell
source .env.android-signing
```

Use single quotes around password values in `.env.android-signing`. Double quotes can change passwords that contain shell-special characters such as `$`, backticks, or backslashes.

Build the signed release APK and AAB:

```shell
make android-release
```

For the Google Play upload path, use the guarded target that fails when signing variables are missing:

```shell
make android-play-release
```

Upload this file to Google Play internal testing:

```text
apps/android/build/outputs/bundle/release/android-release.aab
```

The matching signed APK is also produced for local sideload testing:

```text
apps/android/build/outputs/apk/release/android-release.apk
```

Forgejo release builds can produce signed Android artifacts after adding these secrets to the repository or runner environment:

```text
NAVIAMP_ANDROID_KEYSTORE
NAVIAMP_ANDROID_KEYSTORE_PASSWORD
NAVIAMP_ANDROID_KEY_ALIAS
NAVIAMP_ANDROID_KEY_PASSWORD
```

The keystore file itself must also be available on the runner at `NAVIAMP_ANDROID_KEYSTORE`. For a private runner, keep it outside the repository and point the secret/env var to that path.

## Runner Requirements

- Android job: Linux runner with Java 17. The workflow installs Android platform 36, build tools 36.0.0, and CMake 3.22.1 through `sdkmanager`.
- Windows job: Windows runner with Java 17 and Chocolatey. The workflow installs WiX Toolset 3.14 when `candle.exe` is missing, because jpackage needs WiX for MSI/EXE output.
- macOS job: Apple Silicon macOS runner with Java 17. The packaged platform is `macos-arm64` because the repo currently vendors macOS ARM64 BASS libraries.

If Forgejo uses different runner labels than `ubuntu-latest`, `windows-latest`, or `macos-latest`, update `runs-on` in the workflow without changing the Gradle tasks.

## Local Equivalents

```shell
make android-release
make macos-standalone
make macos-installer
```

On Windows:

```powershell
make windows-standalone
make windows-installer
```

## Google Play Internal Test Upload

For the first internal test release in Play Console:

- Release name: `0.9.1 internal test 2`
- Upload: `apps/android/build/outputs/bundle/release/android-release.aab`
- Release notes:

```text
<en-US>
Internal testing release for Naviamp 0.9.1.

Includes waveform detail controls and the new About page with version, build, library, and changelog details.
</en-US>
```

## Follow-Up Before Public Distribution

- Create the Google Play Console app entry and set up an internal testing track.
- Complete Google Play store listing, content rating, data safety, and privacy policy requirements.
- Add Windows code signing for MSI/EXE.
- Add macOS Developer ID signing and notarization for the DMG.
- Decide whether build artifacts should also be attached to Forgejo releases for tagged builds.
