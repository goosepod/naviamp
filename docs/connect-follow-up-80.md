# Connect setup recovery follow-up (#80)

## Shared changes

Setup results now carry an additive `rejected` flag. Explicit target rejection ends the outstanding
offer with the existing localized rejection message and no password prompt. The existing source can
be offered again. Legacy results without the flag retain their validation-failure interpretation;
older targets cannot distinguish rejection, so both peers should be updated for the corrected copy.
A missing result now reports a localized setup timeout instead of requesting another password.
Actual validation failures still offer credential recovery. No settings or storage schema changed.

Core emits structured lifecycle/timing lines prefixed `NaviampConnect`, with epoch milliseconds,
an event kind, and public session/setup correlation IDs. Events cover session entry/exit, setup
offers, validation, rejection/results, setup heartbeats, and timeouts. The logger never receives
provider forms, passwords, pairing codes, trust keys, device names, or exception messages. A supplied
trace sink can collect these events; its failure cannot interrupt product behavior. The default
sink writes to the process console (Android logcat or captured Desktop stdout).

## Automated evidence

- Two complete shared Core peers reproduce explicit rejection, retry with saved credentials,
  validation failure, source rollback, and a 20-second validation delay with working heartbeats.
- A JVM adapter test uses real PAKE and authenticated encryption for fresh code pairing, automatic
  setup after a 20-second validation delay, and secure reconnect without replaying source credentials.
- Protocol round-trip coverage verifies the additive rejection field and legacy defaults.
- Timeout recovery no longer displays the password prompt.

The physical retest reproduced the disconnect and established its cause. On session
`0a8e2268-2fd3-4313-a80e-5f308ee35105`, the target started at epoch 1789402869694 ms;
the controller sent setup at 1789402869969 ms, timed out at 1789402884892 ms, and the target
only received the offer at 1789402885812 ms (16.118 seconds after target-session start).
Provider validation had not begun when the controller timed out.

A second pairing's JVM thread snapshot found `AWT-EventQueue-0` parked in
`DNSStatefulObjectSemaphore.waitForEvent → ServiceInfoImpl.waitForCanceled →
JmDNSImpl.unregisterService → DesktopNaviampConnectNetwork.stopAdvertising →
NaviampConnectAdvertisingController.stop → resetPairingOfferKeepingListener →
startPairingMode → finishPairing`.

Core now rotates the consumed pairing offer while retaining the valid DNS-SD advertisement and
listener after pairing. The registration keeps its original expiry; explicit new-code requests and
expired advertisements still renew normally. A regression asserts that fresh encrypted pairing and
automatic setup do not unregister or re-register the target after Welcome. No platform production
code or timeout budget changed. Slow native DNS teardown during explicit stop/renew remains a
separate lifecycle concern in [#81](https://github.com/goosepod/naviamp/issues/81); the physical fix
targets the confirmed post-pairing teardown.

## Physical retest

The updated Pixel 10a (Android 17) and macOS ARM64 development build passed the bounded retest on
September 14. A second empty temporary Mac profile was used for the fixed-build fresh pairing;
the normal development profile and installed release app were not replaced.

| Check | Result |
| --- | --- |
| Fresh code pairing and automatic NaviDoom setup | Passed; no second approval or password entry. Mac Home populated. |
| Post-pairing timing | Target started at 1789403450338 ms, received setup at 1789403450447 ms (109 ms later), and finished validation at 1789403450614 ms. |
| Controller completion | Controller started at 1789403450555 ms and received setup success at 1789403450928 ms (373 ms). Cross-device clocks have a small offset; durations are computed within each host. |
| Explicit rejection | Both apps reported rejection; Pixel showed no credential prompt and kept the live session. |
| Retry with existing source | Passed after Mac approval, without entering another password. |
| Playback handoff | Pixel transferred its paused “Jumpin’ Jack” queue to the Mac and Mac playback advanced. |
| Role reversal, remote Pause/Resume, detach, trusted reconnect | Passed; details in the [#79 report](connect-follow-up-79.md). |
| Transport | Real LAN; live Mac-to-Pixel TCP `192.168.10.238:54093 → 192.168.10.175:38967`. ADB forwarding/reverse lists were empty. |

At completion the Pixel was paused at 3:14, its controller detached, and explicit pairing stopped.
The temporary Mac queue was paused at 2:22 and the test process closed. Temporary test profiles,
correlated logs, and trust remain available for further investigation; no source credentials were
entered into tooling. The normal Mac development data remains available on its next ordinary launch.

The watched device audio/render performance pass remains deferred. #81 and the broader recovery
matrix remain open; this report does not claim full Connect general availability.

The affected JVM suites passed: domain 919, app 210, presentation 375, and UI 366
(1,870 total, zero failures/errors/skips). Shared Android, JVM, iOS ARM64, and iOS Simulator ARM64 compilation passed, as did the architecture
guard and Android/macOS packaging. After the final advertisement-lifetime change, all 375 presentation
tests and those compilation/packaging checks passed again; the unchanged domain/app/UI results stand.
