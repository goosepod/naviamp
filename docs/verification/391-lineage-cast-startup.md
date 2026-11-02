# Optional Cast sender startup failure

Tracking: [#391](https://github.com/goosepod/naviamp/issues/391), selected for v2.11.0.
Implementation baseline: GitHub main `334fbd46d`.

## Failure and ownership

The reported LineageOS 23.2 / Android 16 crash is a synchronous Cast sender initialization
failure. Core starts the native session effect during application construction. The Android
adapter loads `CastContext` lazily, and a missing Google Play Services Cast Dynamite module
throws a `RuntimeException` wrapping `ModuleUnavailableException`. This previously escaped
Core construction. Deferring the SDK load until `start()` only protected unused TV adapters.

Core now treats failed sender startup as unavailable, invalidates callbacks, cleans up the
partial effect, and leaves local playback or an unrelated remote output intact. Cancellation
still propagates. The composition only exposes Cast discovery, native picker and playback
routing after session startup succeeds. There is no automatic retry loop. A new process can
initialize Cast again after native support becomes available.

The Android adapter retains the lazy SDK handle, so stopping/disconnecting a failed or unused
adapter cannot cause another module load. Removing listeners still happens for an initialized
SDK, and adapter references are cleared even if native listener removal fails.

## Reproduction

On 2026-11-02 a read-only clone of the local Pixel 8 API 34 AVD (Android 14, arm64) reproduced
the report's exact exception after disabling `com.google.android.gms` for that disposable
session. The pre-existing v2.10.0/build 60 test package crashed from
`AndroidNaviampCastSessionEffect.start` through `NaviampCoreCastController.start`:

```text
RuntimeException: ...ModuleUnavailableException: ...DynamiteModule$LoadingException:
No acceptable module com.google.android.gms.cast.framework.dynamite found.
Local version is 0 and remote version is 0.
```

This establishes the missing-module failure path, not a claim that this emulator is LineageOS
or that all MicroG configurations fail. The original reporter's exact OS/device remains a
separate acceptance limit. The existing APK's source tree was not identified beyond its version;
the stack and exact exception were captured as reproduction evidence.

## Reproducible checks

Shared lifecycle regressions cover failed/partial cleanup, stale callbacks, explicit retry,
cancellation, output isolation, successful startup and the actual Core composition's local
browsing/playback path. Existing shared Cast handoff and picker tests remain applicable.

```sh
./gradlew \
  :core:app:jvmTest --tests '*NaviampCastSessionControllerTest*' \
  :core:presentation:jvmTest --tests '*NaviampCoreCast*Test*' \
  :core:presentation:compileDebugKotlinAndroid \
  :core:presentation:compileKotlinIosArm64 \
  :core:presentation:compileKotlinIosSimulatorArm64
```

`AndroidCastAvailabilityInstrumentedTest` compares the real SDK initialization with the
shared controller, exercises cleanup/restart, and launches/recreates the actual Activity.
The optional `expectedCastAvailable=true|false` instrumentation argument makes a particular
device configuration mandatory rather than silently accepting either branch. Run in separate
instrumentation processes after changing Play Services availability. Use disposable profiles;
never disable Play Services or clear data on the owner's real device for this test.

```sh
adb -s "$TEST_SERIAL" shell am instrument -w \
  -e class app.naviamp.android.AndroidCastAvailabilityInstrumentedTest \
  -e expectedCastAvailable false \
  app.naviamp.android.v2test.test/androidx.test.runner.AndroidJUnitRunner
```

## Results (2026-11-02)

- The first shared pass: 25 tests passed without failures or skips; Android, JVM, iOS ARM64
  and iOS Simulator ARM64 compilation passed before Android production wiring changed.
- The broader shared Cast/Core pass: 80 tests passed (51 Core app, 29 presentation), with
  no failures or skips. All four Android debug unit tests and `verifyCoreFirstArchitecture`
  passed. The app and instrumentation APKs built successfully.
- With Play Services disabled in the disposable Android 14 emulator, the new real SDK/Core/
  Activity regression and the existing unused-adapter regression both passed. SDK availability
  was explicitly required to be false; failed initialization, cleanup, repeated start and
  Activity recreation all completed without crashing.
- In that same unavailable configuration, the explicitly enabled existing
  `AndroidTvLifecycleFixtureInstrumentedTest#preparePlayback` passed against
  `scripts/android-tv-fixture.py --silent --tracks 3`, forwarded with `adb reverse`.
  The actual Core connected, loaded library tracks, started BASS playback, and advanced at least
  five seconds. This is silent synthetic playback acceptance, not an audible listening claim.
- After re-enabling Play Services and resetting only the disposable test-app profile, the new
  regression passed again with SDK availability explicitly required to be true. Cast remained
  available in Core and Activity recreation succeeded. An actual receiver connection was not
  exercised; shared handoff/session regressions passed.

Only the read-only emulator clone and its `app.naviamp.android.v2test` profile were modified.
The physical phone and main installation were not changed. The original LineageOS 23.2 / Android 16
device and a physical MicroG installation remain untested. No release was tagged or published.

## Platform diff accountability

- `apps/android/src/main/kotlin/app/naviamp/android/AndroidNaviampCastSessionEffect.kt`:
  accesses the Android Google Cast `CastContext`/`SessionManager` SDK and manages its native
  listener lifetime; its initialized-handle check prevents cleanup from loading a missing module.

The Android instrumentation test exercises actual SDK/Activity boundaries. All availability,
callback invalidation, retry and application fallback policy stays in common Core code.
