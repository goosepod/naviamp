# Phone and macOS Connect acceptance — September 14, 2026

This records the physical Pixel 10a and macOS development-app run for
[issue #78](https://github.com/goosepod/naviamp/issues/78), following the missing standard target-UI
finding in the [Connect product plan](naviamp-connect-product-plan.md). It is a bounded LAN smoke
pass, not closure of the full Connect general-availability matrix.

## Implementation

The standard shared Controllers page now exposes capability-driven target code display and
cancellation, incoming controller approval/rejection, and incoming source-setup approval/rejection.
It delegates to the existing Core actions and authorization model. Outgoing pairing does not cause
the target button to claim that an incoming offer is active. Target setup actions remain independent
of outgoing setup state; Core already guards duplicate approval and owns cancellation.

Pairing labels, consent, and applicable status messages use device-neutral resource copy. Both new
resource keys and revised consent are present in every resource locale. The maintained English and
Spanish resources retain matching keys and format arguments.

All production changes are in `core:ui/commonMain`. No Android, Desktop, or iOS production adapter,
setting, schema, or provider behavior changed.

## Environment and evidence

- Physical Pixel 10a, Android 17, updated in place to `v2.5.0-v2test` (version code 52).
- Current macOS ARM64 development app staged under `build/local-test/Naviamp.app`, using the isolated
  development profile. The installed release app was not replaced.
- Existing Pixel NaviDoom source; its source and portable settings were provisioned to the Mac through
  Connect. No password was entered into the test tooling or re-entered during the successful offer.
- Same LAN. A live Mac-to-Pixel TCP connection used the devices' private IPv4 addresses. A live
  Pixel-to-Mac connection used their LAN IPv6 ULA addresses. Connect used no emulator route override,
  ADB forwarding, reverse tunnel, or relay. ADB was used only for authorized phone UI control.
- Observations came from the real app UIs, playback clocks, and host socket inspection. They establish
  product state and transport behavior, not acoustic output quality or instrumented latency bounds.

## Results

| Check | Observed result |
| --- | --- |
| Standard target UI on both hosts | Show/stop pairing visible and operable; code displayed and removed. |
| LAN discovery and pairing | Pixel discovered the Mac and authenticated its displayed code; both retained trust. First automatic source setup was interrupted, as noted below. |
| Mac → Pixel trusted reconnect | Connected without entering another pairing code, including after detaching. |
| Mac → Pixel controls | Pause held “Save My Soul” at approximately 0:42; Resume advanced to 0:51; Next changed both clients to “Why Me?”. |
| Pixel independent playback | After Mac detach, “Why Me?” continued from the earlier 0:09 observation to 0:28. |
| Incoming source rejection/approval | Mac displayed the requesting Pixel and NaviDoom source. Reject discarded the offer. A later approved offer connected the Mac and populated Home. |
| Pixel → Mac queue handoff | Pixel paused “Jumpin’ Jack” at 1:34; Mac began the transferred queue at approximately 1:35 and continued advancing. The Mac queue retained subsequent popular tracks. |
| Pixel → Mac controls | Pause was reflected on both around 1:56–1:57; Resume and Next changed both to “Go Daddy-O”. Repeat queue/current-track state reached the Mac; repeat-off was restored. |
| Mac independent playback | Pixel detach left “Go Daddy-O” playing on the Mac, advancing from the pre-detach 1:16 observation to 1:35. |
| Pixel → Mac trusted reconnect | Reconnected without code entry while Mac playback continued; Pixel showed the current “Go Daddy-O” track. |

At completion both app queues were paused, controllers detached, explicit pairing modes stopped,
and repeat set back to Off. Trust and the provisioned development connection remain available for
future testing. The Mac development layout was returned to split view.

## Remaining findings

- [#79: stale output indication after a role switch](https://github.com/goosepod/naviamp/issues/79).
  After a disconnected outgoing session and subsequent incoming control, the Pixel played locally
  but still named the Mac as its playback device. Explicitly selecting local playback cleared it.
- [#80: initial setup interruption and rejection recovery](https://github.com/goosepod/naviamp/issues/80).
  First code pairing created trust, but automatic setup ended in a disconnected/reconnecting state.
  The Mac process remained alive; no root cause was established. Later explicit approved setup
  succeeded. An explicitly rejected offer also produced a misleading password-validation prompt on
  the Pixel; cancelling and resending the existing source succeeded without a new password.

Fresh automatic setup, the role-switch presentation, reverse-direction queue transfer, exhaustive
queue mutations, network/process failure recovery, additional hosts, and physical-TV acceptance
remain outside this completed smoke pass. Incoming pairing approval/rejection delegation has
shared automated coverage; physical code-consent pairing automatically authorizes the peer as designed.

## Automated verification

- All 365 shared UI JVM tests passed with no failures, errors, or skips, including six new target UI
  regression tests and resource parity validation.
- Shared UI Android, JVM, iOS ARM64, and iOS Simulator ARM64 compilation passed.
- `verifyCoreFirstArchitecture` passed.
- Android debug assembly and macOS development app packaging/staging passed.
