# Android 2.10.0 checks — 2026-10-19

The connected Pixel 10a passed **six device tests** on Android 17 (API 37). These checks used
candidate `95345b34194fe563fec68553d68bb2a9e46e8a2c`, v2.10.0/build 60, in a separate debug
installation named **Naviamp 2.10 Acceptance** (`app.naviamp.android.accept2100`). The normal
Naviamp installation and its accounts, downloads and settings were not modified.

This records the tested candidate, not blanket Android or release acceptance. The later summary
and preview changes in #314 require their own checks. macOS owner testing remains planned for
2026-10-20; no macOS result is claimed here.

## Results

- **Startup/service handling: four tests passed, 56.067 seconds.** Real Android Activity and Service
  boundaries were exercised. The background-start rejection check injects Android’s rejection at
  the Context boundary; it does not claim to force a new OS rejection during this run.
- **Connection-loss recovery: one test passed, 46.939 seconds.** Real BASS playback paused and
  resumed, survived an unavailable provider and three rapid selections beyond the foreground-service
  startup deadline, then recovered with advancing position. Background/restore also passed.
- **Certificate-protected playback: one test passed, 6.228 seconds.** The production Android TLS,
  private-file import and BASS adapters connected to a disposable mutual-TLS server, browsed artists,
  fetched artwork and started uncached audio before the complete file arrived. Pause, a range seek
  to 90 seconds, resume, moving progress and prompt stop passed. Missing/untrusted client identity,
  wrong identity password, untrusted server CA and hostname mismatch were rejected. A private-CA-only
  server also passed. The source certificate files were removed after import, and test-owned private
  copies were removed in cleanup. No OS trust-store change was made.
- The separate app also launched visibly to its connection screen; the screen rendered correctly.

These tests use synthetic WAV media and disposable loopback providers. They do not establish
real-account Radio/Autoplay behavior, every compressed format, a production APK upgrade, settings
sync between physical devices, or complete visual acceptance of every screen.

## Reproduction and evidence

Use the dedicated source checkout and the isolated debug application ID above. Build the app and
instrumentation APKs with the init-script method in [Android recovery reproduction](../android-playback-startup-243.md#reproduction).
Do not reuse or clear a connected user profile: recovery deliberately requires an empty test profile.

Run these instrumentation classes with `androidx.test.runner.AndroidJUnitRunner`:

```text
app.naviamp.android.AndroidPlaybackServiceStartupInstrumentedTest
app.naviamp.android.AndroidPlaybackRecoveryInstrumentedTest
app.naviamp.android.AndroidMutualTlsPlaybackInstrumentedTest
```

Recovery requires `-e playbackRecoveryFixture true`, `scripts/android-tv-fixture.py --burst-seconds 4 --tracks 3`
and an ADB reverse for port 18080. The certificate check uses `:core:testkit:prepareMobileMutualTlsFixture`,
the generated JVM argument file and ADB reverses for ports 59974–59977. All listeners bind to loopback;
only disposable fixture data is used. Restore the fixture, stop playback and remove only the test-owned
reverse mappings afterward.

Local evidence in the workspace `build` directory:

- `android-accept2100-build.log`
- `android-accept2100-startup.log`
- `android-accept2100-recovery.log`
- `android-accept2100-mtls.log`
- `android-accept2100-launch.png`

APK SHA-256:

```text
615ad6cc7a13060cf5d55d5da839ab53f0850152449bdc01bfc5b2c8969e9401  android-debug.apk
86f3b2d1f63ba50cd8fc785467001bbc6fdf34cd81cb3ed5a8f7fba2af5b7fa8  android-debug-androidTest.apk
```

No platform production files changed for these tests. No release, tag, announcement or mirror was created.
