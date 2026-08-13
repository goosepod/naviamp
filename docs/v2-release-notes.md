# Naviamp 2.0 Release Notes

Naviamp 2.0 replaces the former platform-specific application graphs with one shared application
for Android and Desktop plus an unsigned iOS preview. The complete user-facing change list is in
[`CHANGELOG.md`](../CHANGELOG.md#v200).

## Supported releases

- Android APK
- macOS ZIP and DMG
- Windows ZIP, MSI, and EXE
- Linux ZIP, DEB, and RPM
- Unsigned arm64 iPhone/iPad IPA preview for users who provide their own signing

Android, macOS, Windows, and Linux passed extended playback, search, radio, internet-radio,
playlist, smart-playlist, provider, restoration, and upgrade acceptance. Android additionally
passed physical-device download/offline, lifecycle, background playback, battery, and performance
testing. The iOS Simulator passed provider, playback, Keychain, downloads/offline, restoration,
lyrics, waveform, visualizer, settings-sync, and background-playback testing.

Physical-iPhone and signed/TestFlight distribution are deferred because no physical iPhone or
release signing identity is available. The preview IPA is not directly installable until it is
signed; see [Sideloading Naviamp on iPhone or iPad](ios-sideloading.md).

## Upgrading from Naviamp 1.x or a 2.0 prerelease

1. Back up important playlists and export settings before a major upgrade.
2. Install the new package over the existing installation. Windows MSI and Linux package upgrades,
   along with normal macOS and Android replacement installs, have been accepted.
3. Launch Naviamp and confirm the existing connection and queue restore.
4. If a provider requests authentication again, edit the connection and enter the password or
   token. Credentials are never included in settings-sync exports.

Shared SQLDelight migrations preserve supported settings, provider connections, playback state,
downloads, history, and selected provider libraries. The migration chain is verified from the
committed version-1 database before release packaging.

## Platform notes

- Windows and macOS packages are not publisher-signed and may trigger operating-system warnings.
- Linux secure credentials require a Secret Service implementation and `secret-tool`; DEB/RPM
  packages declare the integration dependency.
- Bandcamp currently supplies collection playback through its Subsonic beta as MP3 256 kbps and
  does not reliably support playlist track reordering. Naviamp disables that mutation.
- Jellyfin favorites are supported, while Navidrome-specific star/rating and smart-playlist
  capabilities remain provider-specific.
