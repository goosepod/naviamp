# One Android app for phone, tablet, and TV

Naviamp uses one Android application module (`:apps:android`) and one release package ID
(`app.naviamp.android`). There are no phone/TV product flavors. Android's UI-mode configuration
selects the shared Standard or Television surface at runtime. Both launcher entries, the TV banner,
and optional leanback/touchscreen declarations live in the same manifest.

The former “Naviamp TV” setup/restoration text was product copy inside that shared app, not a
separate application. Both maintained translations now call it Naviamp. Debug builds still use
`app.naviamp.android.v2test` and “Naviamp v2 Test” so they can coexist with the installed release;
that distinction applies equally to phone and TV. Benchmark/profileable variants are development
tools, not device-specific distributions.

## Build and distribute

Run `./gradlew :apps:android:stageReleaseArtifacts` to produce both formats from the same release:

- `apps/android/build/release-artifacts/Naviamp-<version>-android.apk` for direct installation on
  phones, tablets, and TVs.
- `apps/android/build/release-artifacts/Naviamp-<version>-android.aab` for Google Play.

`make android-release` uses this same task. `make android-play-release` additionally requires the
release signing environment before invoking it. `stageReleaseApk` remains a compatibility alias. The tag release workflow
requires the existing signing environment, builds both formats in one Android job, and attaches
both artifacts to the draft release. Local outputs without configured release signing are not
publishable signed releases. No Play upload or publication is performed by this change.

For Google Play, use one Naviamp listing and package with TV support enabled. Supply the TV
listing assets and complete its quality review. Separate TV tracks are optional; the unified
bundle can serve TV through the mobile track. See [Google Play's form-factor distribution guide](https://support.google.com/googleplay/android-developer/answer/13295490?hl=en).

Before shipping, install the same built APK on phone and TV, verify automatic surface selection
and D-pad/touch interaction, and complete the pending real-device performance and recovery checks.

## Verification — #82, September 14, 2026

- All 366 shared UI JVM tests passed, including maintained-translation parity. Shared UI compiled
  for Android, JVM, iOS ARM64, and iOS Simulator ARM64; the architecture guard passed.
- `stageReleaseArtifacts` produced both versioned release formats. The APK declares package
  `app.naviamp.android`, label `Naviamp`, and both launcher categories. Both APK and AAB contain
  arm64-v8a, armeabi-v7a, x86, and x86_64 native libraries. Local release outputs were unsigned;
  the release workflow continues to require signing credentials.
- `bundleDebug`, now included in CI verification, passed. Both changed workflow files parsed as
  valid YAML, and both Make release shortcuts resolve to `stageReleaseArtifacts`.
- Installed the exact same debug APK on the Pixel 10a and Android TV ARM64 emulator, preserving
  their data. Pixel opened the standard touch interface; TV opened the television setup interface
  with the heading “Set up Naviamp.” TV D-pad navigation moved focus to Show pairing code.
  The emulator initially showed a black window; waking/restarting the test app restored its UI.
- Tested APK SHA-256: `a8d469c149dd0c79293f6121ec52a73dc802e4defdc2d6972729fff923e5f860`.

This verifies packaging and automatic interface selection. It does not replace the pending physical
TV performance/recovery pass or Google Play qualification. No platform production Kotlin changed;
the Android changes are packaging configuration and shared localized copy.
