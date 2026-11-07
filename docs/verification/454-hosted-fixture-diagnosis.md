# Hosted iOS fixture diagnosis, November 6, 2026

The owner prioritizes explaining the recurring GitHub failures before the remaining milestone
work. This investigation is separate from the earlier demonstrated main-thread harness defect
documented in [454-ios-mtls-rejection.md](454-ios-mtls-rejection.md).

## Preserved current failures

The native suite runs 17 tests and fails its mTLS contract during **fixture bootstrap**, before
certificate import, client authentication or playback. The diagnostic message has Idle/null
progress, `tlsStage=not started` and an empty challenge trace. The runner bootstrap is supplied,
so the required initial main-queue callback completed.

- [Run 42321711902](https://github.com/goosepod/naviamp/actions/runs/42321711902), album profile probe.
- [Run 42339520367](https://github.com/goosepod/naviamp/actions/runs/42339520367), album profile probe.
- [Run 42341439802](https://github.com/goosepod/naviamp/actions/runs/42341439802), paused-position fix.

CFNetwork records successful loopback TCP establishment and a sent 175-byte GET to `/fixture`,
then zero response bytes until cancellation at the existing ten-second deadline. The fixture
JVM still owns its four listeners. Its later failure-only thread dump shows HTTP dispatchers
waiting in KQueue, without an active request handler. These facts do not establish whether
the server failed to respond or the simulator failed to receive/deliver a response.

## Controls and rejected shortcuts

| Verify run | Native result | Ready-step end to native-step start (minutes) |
| --- | --- | ---: |
| 42306716280 | Pass | 18.9 |
| 42321918507 | Pass | 23.3 |
| 42331662843 | Pass | 12.3 |
| 42321711902 | Bootstrap failure | 18.8 |
| 42339520367 | Bootstrap failure | 21.6 |
| 42341439802 | Bootstrap failure | 16.2 |

All six use GitHub-hosted `macos-latest`, not the temporary local Mac worker. The inspected
passing and failing #529 jobs use image `macos-26-arm64` version `20261102.0473.1`, Temurin
21.0.13+9. Fixture age alone therefore does not explain these observations. A successful retry
does not demonstrate a fix. No assertion, authentication case or deadline is relaxed.

## Matched experiment

Opt-in workflow-dispatch input `ios_fixture_lifetime_diagnostic` adds a separately named
diagnostic job; the entire original required Verify matrix remains unchanged. The experiment:

- Starts two independent fixtures before compilation and leaves each idle through compilation
  (the initial experiment also added a 20-minute minimum age).
- Uses two additional fresh fixtures and a separate cold cloned simulator for each of four cases.
- Runs the same complete native binary, recording its SHA-256, preserving failures and retaining
  the original negative authentication, positive authentication and native playback/seek checks.
- Counterbalances aged/fresh ordering. Host HTTP health checks occur only **after** native
  execution, so a heartbeat cannot inadvertently warm an aged baseline.
- Records loopback packet timing on the four owned fixture ports, native reports, fixture
  diagnostics and post-test host health. Packet output contains metadata rather than payloads.
- Cleans only owned devices/processes and excludes generated bootstrap credentials from uploads.

Initial exact-source experiment:
[42343047551](https://github.com/goosepod/naviamp/actions/runs/42343047551), source `39e8ff146`.
That initial diagnostic run links the native binary successfully but stops before native testing:
the first `simctl list devices available --json` exceeds the reused cold-replay helper's 30-second
RPC limit. It supplies **zero native comparison results**. Its raw logs remain preserved. The
normal iOS job on the same revision separately reproduces the bootstrap timeout again.

The corrected experiment provisions CoreSimulator explicitly, with a bounded setup-stage budget
matching cold host initialization rather than changing authentication/playback deadlines. It also
uses the standard complete iOS compilation/common-test graph and compares persistent versus
single-use Gradle daemons. The ordinary iOS job overrides global `GRADLE_OPTS` and drops
`org.gradle.daemon=false`; the initial diagnostic used single-use daemons and native-only linking.
Those are material confounds, so neither is treated as a demonstrated fix. Host memory, swap,
pressure and JVM resident-memory snapshots are recorded alongside packet timing. Aged fixtures
now age naturally during normal compilation; there is no added 20-minute idle in this follow-up.
Diagnostic results cannot replace required merge or release verification.

The new result/capture guard tests pass locally. Existing diagnostic ownership tests initially
fail on Windows because their synthetic POSIX path does not match Windows path resolution;
using a real temporary path and shell quoting corrects that test without relaxing ownership.
The exit-code test now isolates process discovery instead of depending on a Windows `ps` binary.
All 19 selected Python iOS verification-helper tests then pass, including gates preventing the
manual job from displacing standard checks or publishing a verification receipt. Failed/skipped
comparison conditions cannot be erased by a later passing control in the final tool. The broader
51-method local helper run does not pass on this Windows environment: POSIX fake executables,
macOS packaging tools, a newline filename and unavailable WSL Bash are outside its capabilities;
those failures are retained separately rather than counted as passes. No application production
code has changed at this checkpoint.

The follow-up [42344966834](https://github.com/goosepod/naviamp/actions/runs/42344966834),
source `990794cbd`, reaches native execution in its single-use-daemon condition. The first aged
fixture is 879 seconds old and all 17 native cases pass. Packet metadata records response data
on the control connection. The job then fails in diagnostic observer teardown: signalling the
`sudo` parent PID leaves the packet observer running and `wait(15)` expires. Native XML and
packet logs are preserved, but this is only one condition, not a completed comparison or a fix.
The persistent-daemon condition subsequently also passes all 17 cases on its first aged fixture
(1152 seconds old), then encounters the same observer teardown problem. Its post-compilation
Gradle JVM occupies about 2313 MiB resident memory, versus no retained Gradle daemon in the other
condition. This confirms the resource-policy difference, but does not show that it causes the
bootstrap timeout. The ordinary job's native test step also passes in this run.

The observer now starts in its own session and verifies that owned process group before signalling
the group, including the sudo monitor and tcpdump child. Native results are captured before
observer teardown, and device cleanup plus the individual summary are attempted even if collecting
service logs fails. Two local ownership tests cover the correct group and refusal of a changed group;
all 21 selected iOS helper tests pass. The native deadlines and required verification matrix remain
unchanged.
An observer lifecycle check now runs before native compilation without issuing HTTP requests,
so observer setup/cleanup failures are detected early without warming the aged control endpoint.

The ordinary iOS native step can also opt into the same packet metadata capture when the manual
diagnostic input is enabled. This retains its existing fixture, compilation graph, simulator boot,
test command and deadlines, without adding pre-test memory/JVM snapshots or extra fixtures to that
job. The wrapper records the actual native command exit status before observer teardown. Ordinary
PR runs keep their existing invocation. All 23 selected helper tests pass, including rejection of
capture without an owned fixture and preservation of a native failure despite observer failure.
