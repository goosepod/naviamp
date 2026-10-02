# Android TV account switcher: issue #53

The profile entry in the shared TV navigation opens large account cards with a current-user
indicator, Add account, and Cancel. Selection validates stored credentials before publishing the
new provider session. Success resets account-scoped playback, navigation, search, mix builders,
library, playlists, and detail state; failure retains the usable previous session and offers a
translated error with focus returned to the attempted card. Back cancels an outstanding attempt.

All product behavior and UI live in common Core/provider code. Existing saved-account metadata
and secure-secret storage are reused. No setting or persistence migration is introduced. Cache
maintenance now retains dormant configured accounts, pruning only superseded library scopes.

## Local acceptance, October 2, 2026

A fresh Android 16 ARM64 TV AVD named `Naviamp_53_Acceptance` was created under
`/private/tmp/naviamp-53-avds`; no existing AVD user data was copied. The local fixture server
supplies two distinct users, catalogs and playlists. No real credentials are used.

The real application runtime, SQLDelight, Android Keystore and shared TV shell passed:

- Add Alice, then add Bob through the existing connection form while retaining Alice.
- Navigate to Accounts using directional keys, open the chooser and identify Bob as current.
- Reject Alice's credentials; preserve Bob's session, catalog and both stored accounts, show an
  error and return focus to Alice for retry.
- Allow Alice, retry using the remote, load Alice's catalog and restore profile-button focus.
- Stop the process and reopen: restore Alice from secure storage, with Bob still configured.

Commands (with `ANDROID_HOME` set and `ANDROID_SERIAL` selecting the disposable AVD):

```sh
python3 scripts/android-tv-fixture.py --accounts --tracks 2 --albums 2
adb -s "$ANDROID_SERIAL" reverse tcp:18080 tcp:18080
./gradlew :apps:android:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.naviamp.android.AndroidTvAccountSwitcherAcceptanceTest \
  -Pandroid.testInstrumentationRunnerArguments.tvAccountFixture=true
```

The app acceptance test is opt-in and requires an empty fixture installation. For the restart
check, install the built app/test APKs and run the same instrumentation directly so test cleanup
does not uninstall the fixture data; then force-stop the app and rerun with
`-e tvAccountFixtureMode reopen`. Never clear an existing user's app data for these tests.

Shared rendered UI checks cover directional selection, adding, busy cancellation, retry focus,
empty-account entry and Back. Native screenshot capture records 1920 × 1080 dialog bounds.
Common behavior tests cover unavailable/expired sessions, rollback, cancellation, account-state
reset, stale mix result rejection, provider publication failure and dormant-account retention.

Final verification passed: 457 shared UI, 430 presentation, 62 storage, 179 Navidrome and 33
Jellyfin JVM tests; six TV chooser instrumentation tests; shared Android/iOS device/simulator
compilation and Core-first architecture verification. All seven new strings validate in all
17 maintained locales. The final 1080p capture was visually inspected for contrast and clipping.

A startup blocker on the fresh TV image is separately fixed by #198 / PR #200: the unused native
Cast adapter must not load Google's absent sender module during construction. This branch includes
that prerequisite for local acceptance; merge #200 first.

Raw local logs: `/private/tmp/naviamp-53-real-app4.log`,
`/private/tmp/naviamp-53-persistent-acceptance.log`, `/private/tmp/naviamp-53-reopen.log`,
`/private/tmp/naviamp-53-final-shared.log`, `/private/tmp/naviamp-53-final-tv-ui.log`.

## Emulator feedback update

The navigation button now uses the shared person-in-circle vector instead of the account initial.
Choosing the current account now invokes the same Home-navigation action as a successful change
to another account, without reconnecting. Failed or unavailable selections do not navigate.

All 457 shared UI and 430 presentation JVM tests passed, including navigation notifications for
current/different accounts and no navigation on failure. Android/JVM and iOS device/simulator
compilation, Android APK packaging and Core-first architecture verification passed. The updated
APK was installed in place on emulator-5556 and reopened, preserving saved accounts. All changed
production files are common Core files; no native host production file changed.

Log: `/private/tmp/naviamp-53-feedback-build.log`.
