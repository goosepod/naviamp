# Pixel 10a release-candidate acceptance — October 29, 2026

Tracking: #386 / #280, Mac-hosted ARM investigation #394, Android Auto visibility #395.

## Candidate and isolation

Production source: `c58f0b3aeb5dad7c17ace37b8c444f2a418f737f`, v2.10.0/build 60 (the head of #392). Its complete hosted cross-platform matrix passes. Physical device: Pixel 10a, Android 17/API 37, 1080×2424. The initial battery report is 84%, 27.9°C, reporting AC power. No device power, display, refresh-rate, trust-store or Android Auto security setting is changed.

The normal installed package is `app.naviamp.android`, versionCode 59 / versionName v2.10.1, last updated October 14. It is retained throughout. Test apps use separate IDs: `.v2test`, `.v2test.test`, `.benchmark` and `app.naviamp.ui.test`. The benchmark variant inherits release shrinking/minification and is non-debuggable/profileable; it uses the debug signing identity and a separate package. This is release-style execution, not a production-package upgrade or store-delivery test.

Embedded APK manifests and version metadata are verified before installation. An initial automatic approval rejection incorrectly identified the debug APK as the normal package. The embedded manifest proves `.v2test`; the explicit isolated install then succeeds. No normal-app replacement occurs.

## Automated physical-device results

| Run | Passed | Skipped | Failed/error | Scope |
| --- | ---: | ---: | ---: | --- |
| Native app instrumentation | 23 | 18 | 0 | Real BASS/JNI decoding, callbacks, EQ/downmix, Keystore, native networking/discovery, identity/PAKE, permissions, screen-awake and service startup |
| Selected shared UI/native-raster instrumentation | 34 | 0 | 0 | Visible animation movement, both cached-raster placement cases, playlist summaries/reference picking, artist playlists/search/Top Tracks/layouts |
| Explicit playback-loss recovery | 1 | 0 | 0 | Pause/resume, unavailable transport, rapid selections, foreground-start deadline, recovery with moving progress, background/restore |
| Optimized-package black-box smoke | 1 | 0 | 0 | Non-debuggable APK; fresh fixture setup, Library/Songs, moving playback, pause, midpoint seek/resume, About/build/changelog and connection retention after force-stop/cold launch |

Total: **59 active physical-device checks pass**, with 18 unrelated opt-in native cases skipped. These are executions across distinct test runs, not a count of unique product behaviors. Raw native JUnit reports `OK (41 tests)`; the status stream explicitly identifies 23 passes and 18 assumption skips. The UI runner reports `OK (34 tests)`. Recovery passes in 45.503 seconds; the final optimized smoke passes in 10.998 seconds.

The native mTLS case uses production Android adapters and disposable certificates/media: valid private identity/CA browse/artwork, uncached BASS playback, paused range seek/resume/stop and rejection of invalid identity/password/CA/hostname combinations pass. It does not change the OS trust store. Loopback fixtures and explicit ADB reverse mappings are test-owned.

The optimized smoke uses real Android package/Activity/accessibility APIs against the separate R8 APK; it does not call product internals or read the normal app's credentials. Driver development exposed package-visibility restrictions, nested field labels, cached accessibility snapshots and ambiguous heading/navigation labels. The final driver scopes metadata to the benchmark package, refreshes the native cache, selects enabled controls and verifies resulting state. Unsupported raw injected taps are excluded from acceptance; final smoke uses Android accessibility actions. No product assertion, animation cadence or budget is relaxed.

The emulator's two DNS-SD and two raster-placement failures from #394 do **not** reproduce on the physical phone. This does not identify their emulator cause or close that investigation. The animation test proves visible movement; its old logcat metrics are no longer in the buffer, so no CPU/GPU budget acceptance is inferred from an empty metrics export.

## Android Auto

The owner reports that the truck stopped listing Naviamp after older test apps were removed. The owner confirms the normal app is checked in Customize launcher and Unknown sources is already on. Both settings are left unchanged. The normal APK still has an exported MediaBrowserService and car.application metadata resolving to an automotive resource with `uses name="media"`; Android Auto's notification-listener access is enabled.

Google SDK Desktop Head Unit 2.0 (Mac ARM64, build 2022-03-30-438482292) connects to the physical phone in USB Accessory Mode. Android Auto on the phone is 17.9.664044-release. Its normal protocol TLS handshake verifies successfully. No TLS bypass is applied to Android Auto.

Verified through the actual projected Android Auto UI and native service bindings:

- The normal build-59 `app.naviamp.android` appears in the fresh launcher, opens its real queue, and plays with advancing time; pause works.
- After the owner temporarily enables the benchmark in Customize launcher, the active session initially keeps its old app list. A reconnect refreshes it and exposes the candidate. This is an observed session-refresh requirement, not proof of the truck's original cause.
- The R8 candidate binds as `app.naviamp.android.benchmark` with `com.google.android.projection.gearhead` as the caller. It displays its three-track fixture queue, plays with advancing time, pauses, selects queue items and skips to the next track.
- The Accessory Mode USB transition clears the fixture's ADB reverse mapping. The resulting first local-fixture stream failure is a test-transport setup issue: the empty mapping is observed, restored, and playback then succeeds. It is not counted as a provider regression.
- Before uninstalling the test copies, the normal Naviamp provider is selected again. After removing every test package and reconnecting, the launcher contains one Naviamp icon; the remaining normal provider still opens successfully. Playback is left paused. Existing favorites, queue membership and connection settings are not edited; normal playback/seek position is exercised during the test.

The truck itself is not available to the agent. #395 stays open for the owner's disconnect/reconnect check in that vehicle. No app-code repair, installer-identity spoof, trust relaxation, Android Auto data clear or pairing reset is used. A successful Desktop Head Unit run does not substitute for the truck's own USB/wireless/head-unit behavior.

## Artifacts and cleanup

SHA-256:

```text
741561599cf3a31b576962c042c0c9541d7cd5a828e5ac5eb585b884b30ead64  android-debug.apk
9a9d11e6728da9db7f5510bb6c6e7fafa3344fd3798a425876851262c420beda  android-benchmark.apk
72d18404761a9d0932ce426ed2f42e2f0e70c0de43c7b5dff9d10dd6109a8a5f  ui-debug-androidTest.apk
```

The unchanged normal installed APK SHA-256 is `b340c27520bb7e3bcaec0b2a65d23db698b5a775a908f997d62c9003e8beabad`; version/update metadata also remains unchanged after testing. All four task-installed packages are removed, leaving only the original `app.naviamp.android`. Test reverse mappings, head-unit sessions and loopback fixtures are stopped/removed. The owner's emulator remains untouched.

Private local evidence is retained in `build/phone-acceptance-2026-10-29` in the acceptance worktree: build logs, raw instrumentation and status summaries, final optimized test APK/window capture, artifact hashes, installed APK/manifest inspection and native Android Auto service bindings. Raw logs can contain device identifiers or library metadata and are not committed/uploaded. No release, tag, merge or store publication is performed.

## Reproduction

Use an explicitly selected physical device and empty disposable test packages. Build with `-Pnaviamp.animationProbe=true :apps:android:assembleDebug :apps:android:assembleDebugAndroidTest :core:ui:assembleDebugAndroidTest :apps:android:assembleBenchmark`. Verify the embedded package IDs before installing. Start the mobile mTLS fixture and reverse its four ports; run normal native instrumentation and the selected UI classes configured in `core/ui/build.gradle.kts`.

Recovery opts in with `-e playbackRecoveryFixture true` and a three-track loopback fixture with a four-second burst. The optimized smoke opts in with `-e optimizedPackageSmoke true -e class app.naviamp.android.AndroidOptimizedPackageSmokeInstrumentedTest`; use the separate benchmark APK and a long three-track loopback fixture on port 18080. Do not point it at the normal installation. USB Accessory Mode can clear reverse mappings: recheck them after a head-unit connection. Re-select the normal provider before removing disposable copies, then reconnect and verify the remaining launcher entry.
