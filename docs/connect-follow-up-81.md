# Connect network lifecycle follow-up (#81)

## Implementation

Core now runs native discovery and advertising operations on one ordered background worker.
The two effects share a queue because Desktop JmDNS shares a synchronized native network instance;
moving registration alone would still allow a discovery call to block the UI on that lock.
Stopping immediately invalidates the request's callbacks. Obsolete queued starts are skipped,
and callbacks return to the Core owner's coroutine context before changing product state.

Shutdown closes the queue and drains native cleanup independently of cancellation of the product
owner. Cleanup attempts both effects even when one throws. Native stop still takes its native
amount of time; active playback, authenticated sessions, heartbeat handling, and UI work no longer
wait for it. A new advertisement can remain in Starting while an earlier unregister finishes.
Discovery refresh also preserves the current incoming or outgoing authenticated session. Previously,
“Find Naviamp devices” explicitly closed it before browsing; selecting another target remains the
action that replaces the session. No heartbeat budget, pairing lifetime, settings, exported data, or
schema changed.

## Native boundary audit

- `apps/android/src/main/kotlin/app/naviamp/android/AndroidNaviampConnectAdvertisingEffect.kt`:
  Android `NsdManager` identifies registration/unregistration requests by native listener identity;
  each registration now owns its listener so delayed callbacks cannot be delivered to a replacement.
- `apps/android/src/main/kotlin/app/naviamp/android/AndroidNaviampConnectDiscoveryEffect.kt`:
  Android `NsdManager.DiscoveryListener` and `ResolveListener` own asynchronous native request
  lifetimes; callbacks retain their originating browse handle, and an in-flight legacy resolution
  retains its native slot across browse restarts.

All scheduling, callback invalidation policy, expiry, pairing/session state, and UI behavior remain
shared. Desktop and iOS production files are unchanged.

## Verification

Shared regressions cover ordered start/stop/restart, obsolete queued starts, stale successful and
failed callbacks, expiry during registration, native start failure, owner cancellation, and cleanup
when one stop throws. A JVM regression blocks native unregister on a latch while proving that
owner work and close return before releasing native cleanup. Two complete Core peers exercise
explicit stop/restart, discovery refresh on both peers, and repeated expiry renewal across live
heartbeats and playback updates,
asserting that no reconnect occurs.

All 217 app JVM tests and 376 presentation JVM tests passed (593 total, zero failures, errors,
or skips). Android, JVM/Desktop, iOS ARM64, and iOS Simulator ARM64 compilation passed, as did
`verifyCoreFirstArchitecture`, Android debug/test packaging, and macOS local-test packaging.
Five native DNS-SD instrumentation tests passed on the Pixel 10a running Android 17, including
rapid registration stop/restart before native unregistration callbacks finish.

## Physical LAN retest — September 14, 2026

The final builds used the existing temporary Mac profile and Pixel test app data. The installed
Mac release and normal development profile were not replaced.

| Check | Result |
| --- | --- |
| Pixel controls Mac: explicit stop, overlapping restart and discovery refresh | Passed; final stop-to-AX-state round trip was 936 ms. Native registration subsequently returned to Ready. |
| Playback through native teardown | Passed; Pixel queue handoff, Play and Pause reached the Mac. A JVM thread snapshot placed native DNS cleanup on `DefaultDispatcher-worker-4`; `AWT-EventQueue-0` was waiting for its next UI event. |
| Discovery refresh on both peers | Passed without closing the authenticated session. The initial retest exposed the old explicit-close behavior; the final shared fix was rebuilt and retested on both devices. |
| Forward session continuity | Same session for 97.432 seconds through the lifecycle actions, until deliberate role reversal. |
| Mac controls Pixel: stop, restart, discovery refresh, Play/Pause | Passed; the same reverse session lasted 179.560 seconds until deliberate detach. Pixel playback advanced and paused on Mac command. |
| Transport | Real LAN TCP: forward `192.168.10.238:55219 ↔ 192.168.10.175:56708`; reverse `192.168.10.238:55474 → 192.168.10.175:43207`. ADB forward and reverse lists were empty. |

The final test sessions had no heartbeat timeout or automatic reconnect during these actions.
The forward session started at 1789405326587 ms and ended at 1789405424019 ms for role reversal;
the reverse session started at 1789405424174 ms and ended at 1789405603734 ms for explicit detach.
Expiry/automatic renewal was covered with virtual time in shared tests; this physical pass did not
wait out a full five-minute pairing lifetime.

At completion the Pixel was paused at 0:48 and the temporary Mac local queue at 3:29, both on
“Jumpin’ Jack.” Explicit pairing was stopped on both devices and the Mac test process was closed.
The watched real-device audio/render performance pass and the broader recovery/topology matrix
remain deferred; this bounded lifecycle pass does not claim full Connect general availability.
