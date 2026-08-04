# Sideloading Naviamp on iPhone or iPad

GitHub release builds include an **unsigned iOS device IPA** for people who already have a way to
sign and sideload iOS applications. The artifact is compiled for physical arm64 iPhone and iPad
devices, but it is intentionally not signed with the Goosepod developer identity.

## Before installing

- The downloaded IPA is not directly installable. Sign it locally with your own Apple identity or
  development team using a tool that supports re-signing unsigned IPA files.
- Enable Developer Mode on the destination device when iOS requests it.
- Keep Apple credentials and signing material on your own computer. Naviamp and its GitHub workflow
  do not request, receive, or store them.

A free Apple Account can provision apps through Xcode using a Personal Team, but the provisioning
profile expires after seven days and must then be renewed. Apple currently limits a Personal Team
to three registered devices and three installed apps per device, with up to ten temporary App IDs.

Members of the paid Apple Developer Program can instead create longer-lived development or Ad Hoc
signatures. An Ad Hoc IPA only works on device identifiers included in its provisioning profile;
Apple allows up to 100 registered iPhones and 100 registered iPads per membership year.

## Supported alternatives

- Build the repository in Xcode and select your own Team to install directly on a connected device.
- Use a locally trusted signing/sideloading tool to sign the release's unsigned IPA with your own
  identity before installation.
- Use the official TestFlight build once Goosepod's App Store Connect distribution is available.

The unsigned artifact is a convenience for experienced testers, not a replacement for TestFlight
or an App Store release.
