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
The separate `ios_fixture_packet_diagnostic` input enables that ordinary-job capture without
launching the paired comparison jobs. This provides a control without the paired job's extra
fixture processes or pre-test host snapshots. Both inputs default to false and neither changes
which standard jobs are required.

The next comparison, [42347030189](https://github.com/goosepod/naviamp/actions/runs/42347030189),
source `08dc265a7`, passes its observer lifecycle check. Its persistent-daemon condition then
stops before native execution: the optional pre-test JVM `ps` snapshot exceeds ten seconds, and
the service-log observation exceeds 45 seconds during cleanup. Its individual summary and owned
device cleanup are preserved, but it supplies zero native comparison cases. Host pressure reports
31% free memory before that attempted native run. This is a diagnostic failure, not evidence that
persistent daemons cause the HTTP timeout.

All optional resource and service-log observations now retain timeout/error text and continue to
the native experiment. Native command, boot, ownership and comparison checks remain strict.
The ordinary native observer's process inventory also had no deadline; it now has a bounded
five-second lookup and treats unavailable inventory as missing diagnostics, preserving the native
command result. This addresses demonstrated host-tool stalls without lengthening any native test
deadline. All 26 selected helper tests pass, including optional snapshot and inventory timeouts.

Subsequent comparisons collect resource snapshots only after each native suite. Pre-test process
inspection both delayed the experiment and could perturb an idle fixture's residency, so it is
removed from the aged/fresh baseline. The packet observer's live, unreaped `Popen` child handle
and isolated process-group identity establish ownership without reading paged-out process arguments.
The ordinary-job-only control at
[42347900114](https://github.com/goosepod/naviamp/actions/runs/42347900114), source `c609ce1fd`,
passes its complete iOS job, including all 17 native cases, app build and Keychain check. It retains
`native-command-result.json` with exit code zero and packet metadata with zero kernel drops. On
the initial control connection, the 175-byte GET is followed by the server's first 79 response
bytes after approximately 2.177 seconds, then its complete body. This is a passing timing baseline,
not a captured reproduction of the original timeout or proof of its cause.

## Captured failure and corrective lifecycle

Cancellation of the long-running `08dc265a7` comparison preserves three completed suites in the
single-use-daemon condition and an incomplete fourth. The first aged suite executes all 17 cases
and fails only at fixture bootstrap. Its fixture is 1094 seconds old at native-command preparation.
Two fresh fixtures (91 and 158 seconds old at preparation) each pass all 17 cases, with the same
native executable hash `eb551e6eac1c9b092175113a522906a9ce79c21f28b9fb22987e25b4e68f693c`.
The fourth aged suite has no completed native result and is not counted. The comparison remains
partial; it does not establish that every aged fixture fails.

The failed control connection provides the missing evidence:

- `22:51:40.076434`: native client sends its 175-byte GET; the host acknowledges it immediately.
- `22:51:50.242033`: client cancels the connection after its existing ten-second deadline.
- `22:51:59.076583`: the host emits its first 79 response bytes, **19.000149 seconds after the GET**.
- No response body is delivered on that connection. The post-test host probe succeeds in 0.401s.
- The two fresh fixtures' first response bytes arrive after 0.174s and 4.243s, respectively.

This locates the reproduced failure on the fixture/host response path: the response is sent after
the client's deadline, not discarded by a functioning client before that deadline. It also occurs
without a retained Gradle daemon. The precise reason the long-lived JVM was delayed (paging,
scheduling, or another host/JVM effect) is not established by this packet trace.

The ordinary iOS workflow now starts its fixture **after** common compilation and simulator boot,
immediately before the native contract needs it. The server is no longer kept idle through the
build. The live native fixture check, ten-second bootstrap deadline, negative authentication,
positive authentication, streaming and seek assertions remain unchanged. A regression checks the
compile → boot → fixture → native ordering; all 28 selected helper tests pass. PR #531 uses
`verify:ios-native-cold` for three independent complete native suites (initial plus two replays),
with packet metadata on each. Replay fixtures also start after their own simulator boots, and a
failed-boot regression proves that no fixture starts and the owned device is still cleaned up.
Exact-source hosted acceptance is recorded on PR #531; this document preserves the reproduction
and verification plan without treating earlier passing controls as corrective-head acceptance.

## Corrective-head acceptance checkpoint

Head `78c4cb7c0`, [Verify 42350790835](https://github.com/goosepod/naviamp/actions/runs/42350790835),
passes the ordinary 17-method native suite and the first independent cold replay (another 17).
Their initial control response bytes arrive after approximately 76ms and 32ms. No native assertion
fails in these 34 executions. The second replay never enters native testing: it reaches the old
90-second fixture-startup limit. That is incomplete acceptance, not a third passing suite.

The ordinary job log measures about 83 seconds from fixture-runtime preparation finishing to live
fixture readiness. The fixture builds two independent certificate sets using 26 separate keytool
JVM invocations before publishing its bootstrap. The replay's 90-second setup limit leaves very
little margin; its discarded readiness output does not distinguish late bootstrap publication
from an unhealthy control probe. The preserved failure is not classified more narrowly than that.

Cold fixture provisioning now has a separate bounded 180-second budget and retains
`fixture-startup.json` (bootstrap-publication time, probe durations/results, total duration/status)
and `fixture-readiness.log`. An exited process or setup deadline still fails. This changes only
pre-test provisioning, not the native ten-second HTTP deadline or any TLS/playback assertion.
Three regressions cover bootstrap publication after 95 seconds, bounded missing-bootstrap failure,
and immediate failure of an exited fixture. All 31 selected local helper tests pass. Exact-head
acceptance still requires all three independent native suites and the full required matrix.

## Certificate generation and final resource staging

[Verify 42353532414](https://github.com/goosepod/naviamp/actions/runs/42353532414), head `0669f587e`,
fails before native execution while starting the fixture after simulator boot. The JVM reports
`Disposable certificate generation timed out` at the RSA key-pair generation operation: one
keytool child exceeds its existing 45-second limit. This is not another native bootstrap timeout,
and no native result is counted for that job.

The final staging is therefore **compile → prepare/start fresh fixture → boot → native tests**.
The fixture is kept out of the long compile, and its 26 keytool subprocesses finish before cold
simulator services start. This matches the successful fresh-fixture controls in the paired job.
The ordinary iOS job also inherits the repository's global single-use Gradle policy instead of
retaining the measured approximately 2.3 GiB build daemon into simulator execution. An aged
fixture already failed without a retained daemon, so daemon retention is not claimed as the
sole cause; removing the unnecessary retained heap provides measured resource headroom.

Both the per-keytool 45-second deadline and native HTTP/authentication/playback deadlines stay
unchanged. The separate bounded provisioning budget and startup trace remain. The lifecycle
regression now protects compilation before fixture creation and certificate preparation before
boot; boot-failure coverage checks cleanup of both the prepared fixture and its owned device.
All 31 selected local helper tests pass. Hosted exact-head acceptance remains required.
