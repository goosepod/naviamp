# Naviamp Cast receiver registration (#217)

Google's Default Media Receiver application name and styling cannot be customized.
Naviamp uses a registered Styled Media Receiver to display Naviamp while retaining
Google's hosted player and the existing shared media and control protocol.

## Owner registration

1. Sign in to the [Google Cast SDK Developer Console](https://cast.google.com/publish)
   with the account that should own Naviamp's receiver. Google charges a one-time $5 fee if
   that account has not already registered as a Cast developer.
2. Choose **Add New Application**, then **Styled Media Receiver**.
3. Set **Name** to **Naviamp**. Leave **Skin URL** empty to use Google's standard styles.
   This displays the registered application name during loading and idle without hosting CSS.
4. Leave **Supports relay casting** unchecked: Naviamp's media endpoint requires the sender
   and receiver to be reachable on the local network. Enable audio-only device support for
   the music receiver; physical speaker acceptance remains separate from TV acceptance.
5. Save and copy the Google-assigned **Application ID**. It is a public identifier, not a secret.
6. For unpublished receiver testing, register the Onn's **Cast software serial number** under
   Devices. Android TV's hardware serial number is not the Cast software serial number.
   Wait until it is ready for testing (Google specifies 15 minutes), then restart the receiver.

Google documents these fields and device registration in
[Registration](https://developers.google.com/cast/docs/registration) and
[Styled Media Receiver](https://developers.google.com/cast/docs/styled_receiver).

## Shared sender configuration

The sole production receiver identity belongs in
`core/app/src/commonMain/kotlin/app/naviamp/app/NaviampCastReceiver.kt`.
The owner supplied the registered Naviamp Application ID `C0A3069A` on 2026-10-03.
Both senders use this ID. No fallback to the Default Media Receiver masks registration failures.

Core's channel sender uses this identity for both LAUNCH and receiver-status matching. The
Android SDK adapter must read the same shared identity. Registration is application configuration,
not a user preference or a device-local setting.

## Acceptance and publication

- Build the desktop sender and Android adapter with the same registered ID.
- On the registered Onn, confirm loading/idle identifies the receiver as Naviamp, then verify
  provider track playback, artwork, pause/resume, seek, queue advancement, and return to local.
- Repeat the physical sender tests on Linux, Windows, and Android. Shared fake-transport tests
  verify an alternative ID but do not prove Google registration or branded rendering.
- Before enabling the ID in a public release, complete the console's required listing/sender
  details and publish the receiver so unregistered user devices can launch it.
  Publishing the receiver is separate from releasing Naviamp.

Status: shared sender configuration is implemented with the owner-supplied ID; branded
physical-device verification and public receiver availability remain to be confirmed.
Work is tracked in https://github.com/goosepod/naviamp/issues/217.

Local verification on 2026-10-03 passed 269 shared JVM tests and 248 Android unit tests,
Android app compilation, and Linux packaging/runtime verification with the registered ID.
Core iOS compilation tasks were requested but skipped because the Linux host disables Apple
targets; a macOS runner is still required.

The Linux physical probe discovered Living Room TV, authenticated it, and received
`LAUNCH_ERROR` with reason `NOT_FOUND` for the registered receiver. The owner confirmed
the receiver is unpublished and will register/restart the Onn. The updated Linux app is open
for that retry; branded rendering and provider playback are not yet verified.

The only changed platform production file is
`apps/android/src/main/kotlin/app/naviamp/android/AndroidNaviampCastOptions.kt`: Google's
Android-only `OptionsProvider`/`CastOptions` API needs a native adapter to pass the shared ID.
The desktop probe changes are test diagnostics for receiver display name and launch error reason.
