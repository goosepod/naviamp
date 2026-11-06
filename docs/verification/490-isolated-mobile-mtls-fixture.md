# Isolated mobile mutual-TLS fixtures

Tracking: #490. The API 30 retry on `8aed7b037` failed before testing because control
port 59974 was occupied. That failure did not establish who owned the listener, and did
not validate the earlier smart-playlist test failure.

## Change

The shared testkit endpoint model consumes four distinct, validated ports from the
fixture bootstrap. JVM fixtures bind control and all three TLS listeners to port zero
and retain those sockets until cleanup. There is no find-free-port/close/rebind window.
Generated certificates, endpoint ports and a per-run stop token are published together.
The standalone process atomically publishes its bootstrap only after listeners start.

The launcher gives each invocation its own private directory and validates the bootstrap
against the live control response. Startup failure terminates only its own child process.
Cleanup checks the same run identity and supplies its stop token; it cannot blindly send
Stop to the old fixed port or a different fixture on a recycled port. Bootstrap material
is disposable test material and is not included in uploaded diagnostic logs.

Android CI reverses exactly the published ports without replacing existing mappings,
passes the control port through instrumentation arguments, and removes only mappings it
created. The Android test adapter reads that runner argument; all TLS roles and assertions
remain in the common contract. iOS receives the generated manifest through its existing
simulator environment boundary. Desktop native contract tests receive their own in-process
fixture bootstrap. No platform production code, schema, settings or UI strings changed.

Certificate trust, missing/untrusted identity, wrong password, wrong hostname, positive
HTTPS, native playback, streaming-byte, range/seek and stop assertions remain intact.
Timeouts were not increased and no native test was skipped.

## Reproduction

With Java 21 and the Android SDK configured:

```sh
./gradlew :core:testkit:jvmTest \
  :core:testkit:compileDebugKotlinAndroid \
  :core:testkit:compileKotlinIosArm64 \
  :core:testkit:compileKotlinIosSimulatorArm64 verifyCoreFirstArchitecture
python3 -m unittest discover -s scripts/tests -p 'test_verification_*.py'
bash scripts/start-mobile-mtls-fixture.sh
```

The launcher prints the path to its bootstrap. Use that exact path below:

```sh
python3 scripts/mobile-mtls-fixture.py ports <bootstrap-path>
python3 scripts/mobile-mtls-fixture.py control-port <bootstrap-path>
python3 scripts/mobile-mtls-fixture.py stop <bootstrap-path>
```

For a manual Android run, reverse each published port, then pass
`-Pandroid.testInstrumentationRunnerArguments.naviamp.mtls.controlPort=<control-port>`
to the connected-test invocation. Remove those reverse mappings afterward. For iOS,
set `SIMCTL_CHILD_NAVIAMP_MTLS_FIXTURE_BOOTSTRAP` to the bootstrap file contents when
launching the existing booted-simulator native TLS test.

## Verification

Local checks on 2026-11-03 pass:

- 10 JVM testkit tests, including two concurrently allocated fixtures with all four legacy
  ports occupied, independent bootstrap identities, unrelated-listener preservation, rejected
  foreign stop tokens and closure of all eight retained sockets.
- Nine common testkit tests executed on iOS Simulator ARM64; zero skipped or failed.
- Android/JVM/iOS ARM64/iOS Simulator ARM64 common compilation and
  `verifyCoreFirstArchitecture`.
- Android instrumentation compilation (`:apps:android:compileDebugAndroidTestKotlin`).
- The existing real Desktop shared TLS/native BASS playback contract on macOS with
  `-Pnaviamp.bass.test.outputDevice=0` (silent output); all authentication, hostname,
  streaming, range and seek checks remained enabled.
- All 33 verification-script tests and shell syntax validation.
- Two actual standalone launcher invocations coexisted with eight distinct ports. Each
  live bootstrap matched its own file, both identity-checked shutdowns succeeded, and
  all eight sockets closed. This also exercised atomic publication and per-run directories.

Local logs: `build-490-check.log`, `build-490-native.log`, and
`build/490-launcher-{1,2}.log`; the latter bootstrap directories contain private generated
material and must not be committed or uploaded. Initial sandbox-restricted attempts could
not access Gradle caches or open loopback sockets; authorized runs outside that sandbox
passed. No failing product test was retried or weakened to obtain these results.

No Android device/emulator or booted iOS native-test simulator was available locally.
Hosted API 30/API 35 and native iOS startup, TLS/playback and cleanup remain required.
The issue must remain open until that matrix passes on the final source.
