# Keep build setup outside native iOS execution

## Preserved failure

PR #539 Verify `42434276813`, attempt 1, source
`367bbcf19bb2f2473d2b76d04838a75e6b4fafdb`, iOS job `128589934330`, failed the native
step's five-minute workflow guard. Artifact `13599177814` and the complete job log are preserved
privately under `build/ios-gh-investigation/pr539-367-ios-failed`.

The native assertions passed: the report contains 17 cases, zero failures/ignored, the mTLS runner
bootstrap marker, and a native-command exit code of zero. The native Gradle task took 43.793 seconds.
Its enclosing second Gradle invocation took 4m28.12s, including 1m1.00s startup and 1m32.98s project
configuration. Launcher/observer overhead added further time. The workflow timed out at 01:53:34 UTC;
the fresh successful native report was generated at 01:53:59. The app and Keychain steps consequently
did not run, so this is not successful complete iOS acceptance.

A process-list diagnostic also timed out after five seconds. These observations support a slow host
exposing a fragile CI budget: Gradle startup after simulator boot consumes native execution time.
They do not prove why that host slowed down or explain every historical TLS failure. #454's original
TLS-flight diagnosis, #497's macOS output clock and #512's cache population work remain separate.

## Execution boundary

The compile stage now stages the BASS frameworks alongside the linked test binary, before fresh
fixture startup and dedicated simulator boot. The native stage launches this exact prebuilt binary
through `xcrun simctl spawn <owned-device-id>`; it does not start Gradle. The simulator has Apple trust
services, and the existing `SIMCTL_CHILD_NAVIAMP_MTLS_FIXTURE_BOOTSTRAP` propagation is retained.
No native test code, assertion deadline, fixture response or production implementation changes.
The workflow's five-minute native guard stays unchanged. The launcher has an additional 240-second
bound covering inventory and execution; timeouts fail and terminate only its own command group.
Existing caller cleanup still owns simulator shutdown/deletion and fixture release.

The binary lists its own tests before executing them without filters, using Kotlin/Native's standard
TeamCity logger. The complete inventory must exactly match `scripts/ios-native-required-tests.json`:
all 17 current native cases, including real mTLS/BASS, callbacks, storage and platform capability
checks. Additions or intentional removals require reviewing this manifest alongside the native test
change. An omitted suite cannot silently reduce acceptance. The logger protocol and inventory flags
come from the Kotlin compiler version used by this repository:
[test runner](https://github.com/JetBrains/kotlin/blob/v2.3.21/kotlin-native/runtime/src/main/kotlin/kotlin/native/internal/test/TestRunner.kt),
[TeamCity logger](https://github.com/JetBrains/kotlin/blob/v2.3.21/kotlin-native/runtime/src/main/kotlin/kotlin/native/internal/test/TeamCityLogger.kt).

The launcher rejects missing, unexpected, duplicate, ignored, failed, malformed or truncated results,
nonzero process exits, absent bootstrap provenance, changed executable bytes and existing output
directories. It records the binary SHA256, source inventory, executed case identities, device ID,
timestamps, raw output and elapsed time. Fresh JUnit XML and a small HTML report are written only
after the complete result is validated. Failure preserves raw output and failed execution metadata;
it cannot publish a partial success report. Reports live under `build/ios-native-execution` and are
uploaded explicitly. Historical release-source recovery retains its previous Gradle path when that
older checkout predates the launcher; a current-source PR cannot silently use that fallback.

The opt-in cold replay path uses the same launcher, with new fixture/simulator/output ownership for
each invocation. It compares exact executed case identities with the ordinary run and preserves
every execution independently. It does not reuse a native test result or retry a failed run.

## Validation

The network Apple Silicon Mac compiles the isolated native binary, stages frameworks and prepares
the fixture in 2m6s on source `09619fc436f6b64169b5e3acdd27e52c1232cc11`. Its first launcher
control exits 111 during inventory, before any test starts; raw failure is retained under that
Mac worktree's `build/ios-native-execution`. Kotlin's simulator command includes a literal `--`
between the executable and native flags. The launcher now preserves that boundary, with a fake
CoreSimulator regression requiring it. This setup failure is not native acceptance. The owned
fixture and uniquely named simulator were released; the owner checkout/profile was untouched.

Portable helper regression batch: 36/36 passed under WSL, zero failures/skips. Coverage includes
protocol escaping, incomplete/duplicate/failed/ignored cases, manifest omissions, nonzero native
exit despite success-shaped output, stale-directory rejection, real process-group timeout cleanup,
owned replay boot-failure cleanup, fixture lifecycle and existing diagnostic ownership.

Hosted acceptance must demonstrate the initial complete 17-case run plus two independent cold
17-case runs, production app build and Keychain test, followed by the complete cross-platform merge
matrix and exact-source evidence. Local helper tests do not establish that the simulator launch or
dynamic framework resolution works; the PR stays draft until hosted results are audited.

No Android, Desktop or iOS production files are changed. This is a CI execution-boundary correction;
it does not restore caches or claim that hosted runners never become slow.
