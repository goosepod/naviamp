# Mac App Store packaging

`scripts/package-macos-appstore.py` prepares an existing release application for
Mac App Store / TestFlight distribution without changing its application version,
build number, or shared application code. Use a fresh, unmodified release bundle.
Keep certificates, private keys, and provisioning profiles outside the repository.

The store package uses JBRSDK 21 with the original runtime's module set. In the
visible 2.9.0 sandbox acceptance check, the release's OpenJDK runtime rendered the
connection form but ignored both typing and paste. The JetBrains runtime accepted
typing and paste and completed demo-server sign-in, playback, and pause. This is
an observed packaging difference; no upstream root cause has been established.

SQLite and JNA native libraries are signed and installed in the application
bundle. Their JVM paths point to that location rather than temporary extracted
libraries. Executable helpers inherit the application's sandbox. The main app
and runtime carry their matching Apple-issued provisioning profiles.

This store package targets Apple Silicon and macOS 12 or later. Universal native
libraries with an arm64 slice are reduced to that slice so obsolete 32-bit BASS
architectures are excluded. The direct-download release artifact remains intact.

Example (substitute local paths and signing identities):

```sh
python3 scripts/package-macos-appstore.py \
  --input-app /tmp/release/Naviamp.app \
  --output /tmp/naviamp-store \
  --java-home /path/to/jbrsdk-21/Contents/Home \
  --app-profile /path/to/Naviamp_Mac_App_Store.provisionprofile \
  --runtime-profile /path/to/Naviamp_Mac_Runtime_App_Store.provisionprofile \
  --signing-identity 'Apple Distribution: YOUR NAME (TEAMID)' \
  --installer-identity '3rd Party Mac Developer Installer: YOUR NAME (TEAMID)' \
  --team-id TEAMID --version 2.9.0 --build 57

PATH=/usr/bin:/bin:/usr/sbin:/sbin xcodebuild -exportArchive \
  -archivePath /tmp/naviamp-store/Naviamp.xcarchive \
  -exportPath /tmp/naviamp-store/export \
  -exportOptionsPlist /tmp/naviamp-store/export-options.plist \
  -allowProvisioningUpdates
```

The restricted command PATH prevents Xcode's Apple `rsync` from invoking an
incompatible Homebrew `rsync` while copying the archive. Export options explicitly
disable Xcode's automatic version/build-number management.

Before upload, verify the app's version/build and strict nested code signatures,
and exercise a separately development-signed copy with App Sandbox still enabled.
Check typing, paste, server login, library navigation, playback, and pause in a
visible window. App Store distribution profiles do not authorize a pre-store
local launch; test through development signing or TestFlight instead. Also check
offline downloads, file selection, visualizers, background playback, and restored
sessions before accepting the complete Mac App Store release.

The script creates a signed installer, an Xcode archive, and export options. It
does not upload a build or submit store review. Export compliance must be assessed
for the Mac's JVM cryptography separately from the iOS platform implementation.
