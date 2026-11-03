# Native TLS test event-loop liveness

Tracking: #454, v2.11.0; unblocks #467. Production TLS and playback behavior are unchanged.

## Preserved failure and demonstrated harness defect

PR #467 head `6765c9b6ac239742d761065fc317a992b01033cc`, Verify
[41682705435](https://github.com/goosepod/naviamp/actions/runs/41682705435), attempt 1:
the native iOS suite ran 15 tests and failed the shared mTLS contract's **missing client identity
rejection** phase. The 25-second outer deadline cancelled validation; playback had not started
(Idle, null progress). All other platform jobs and the common iOS compile/test stage passed.
The native network log shows the initial secure ping waiting until cancellation, without HTTP
request/response bytes. This is not evidence that a missing identity authenticated.

The native test entered through `runBlocking` on the main thread. Moving its coroutine body to
Dispatchers.Default does not service Foundation's main run loop while the test waits. A direct
native regression queues a callback from Default to Main and waits for it. Under the blocking
harness it deadlocks: even its one-second coroutine timeout cannot finish delivery on the blocked
main queue. The owned simulator had to be stopped. A two-second process sample records the main
thread in `BlockingCoroutine.joinBlocking` / `Worker.park`. Raw artifacts:
`/private/tmp/454-blocking-main-probe.log` and `/private/tmp/454-blocking-main-sample.txt`.

This demonstrates a harness liveness defect. It does not identify the private CFNetwork routine
involved in every historical timeout. Earlier passing TLS runs and retries remain baseline data,
not fixes. Apple's [main-run-loop explanation](https://developer.apple.com/documentation/xcode/understanding-hangs-in-your-app)
describes why blocked main-thread work prevents delivery of other callbacks.

## Correction and ownership

- Common testkit's `NaviampEventLoopTestRun` owns coroutine lifetime, completion, cancellation and
  failure propagation. Pending/cancelled work cannot become a passing result; the native test
  thread rethrows asynchronous failures.
- The iOS test adapter services the concrete Foundation main run loop while that shared run is
  pending. It contains only the unavoidable native run-loop effect and result delegation.
- The shared mTLS bootstrap phase now requires a real native main-thread callback before testing
  authentication. Thin Android/iOS test hosts execute their actual Looper/Foundation callback.
- Shared bounded TLS/context diagnostics remain available on timeouts. Temporary production TLS
  instrumentation used during investigation was removed; no platform production file changes.

All five negative cases remain: missing/untrusted client identity, wrong identity password,
untrusted server and wrong hostname. Their 25-second deadlines are unchanged, as are the existing
bootstrap/playback deadlines. Positive authentication, metadata/artwork, native BASS streaming,
pause/resume, seek position, client identity/range audits and wrong-host HTTP exclusion remain.

## Validation

- Five common runner regressions pass: pending work, asynchronous success, original failure
  propagation, cancellation cleanup and parent cancellation. Android/JVM/iOS ARM64/iOS Simulator
  ARM64 compilation and Android test-host compilation pass; architecture verification passes.
  Final preparation log: `/private/tmp/454-final-shared-build.log`.
- Two native run-loop regressions pass in 0.096 seconds, proving main callback delivery and that
  asynchronous failure after a native callback reaches the test assertion. The callback regression
  has a five-second CI budget; it does not change any TLS deadline.
- Fresh-fixture/fresh-simulator acceptance requires three complete native suites, each executing
  all 17 methods with no failures/errors/skips. Per-run fixture state is independent so cumulative
  byte/identity audits cannot contaminate repetitions. Results are recorded on PR #509 and in
  `/private/tmp/454-fixed-cold-suites.log`, with its per-run JUnit/log artifact directories.
- Exact-source hosted results are tracked by PR #509's required checks. The diagnostic revision's
  API 35 job failed while unpacking an emulator system-image ZIP, before tests ran; that setup
  failure remains separate evidence. A passing diagnostic baseline is not a fix.

## Reproduction and merge gate

Use JDK 21, the Android SDK and an available Apple simulator runtime. Compile common tests, fixture
and native binary before booting the disposable test device:

```sh
./gradlew :core:testkit:jvmTest --tests '*NaviampEventLoopTestRunTest*' \
  :core:testkit:prepareMobileMutualTlsFixture \
  :platforms:ios:linkDebugTestIosSimulatorArm64 \
  :platforms:ios:stageIosSimulatorTestBassFrameworks
```

Use the existing Verify workflow's generated fixture/bootstrap and owned-device boot steps. With
`SIMCTL_CHILD_NAVIAMP_MTLS_FIXTURE_BOOTSTRAP` set to that fixture's generated bootstrap text:

```sh
./gradlew -Pnaviamp.ios.testDevice="$device_id" \
  :platforms:ios:iosSimulatorArm64Test --rerun
```

For independent repetitions create a new fixture and device each time, preserve JUnit/log results,
then stop only that run's fixture and delete only its owned device. No real account, certificate or
server configuration is used. Merge only after local native evidence and required hosted checks
pass; then refresh #467 from accepted main and run its required matrix again.
