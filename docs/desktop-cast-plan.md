# Desktop Google Cast (#171)

Issue: https://github.com/goosepod/naviamp/issues/171

Implementation branch: `feature/171-desktop-google-cast`, started from GitHub `main` on
2026-10-03. Physical receiver acceptance uses the owner's Onn 4K Pro. Windows, macOS, and
Linux remain unverified until each runs the actual app against a receiver.

## Existing ownership and missing pieces

`NaviampCastSessionController` and `NaviampCoreCastController` already own output identity,
queue handoff, local playback suppression, commands, receiver progress, queue advancement,
and return to local playback. `NaviampCastMediaEndpointController` and its lease/request
controllers own scoped media access. `NaviampCoreCastMediaByteSource` obtains authenticated
provider bytes without exposing provider credentials to the receiver.

Android delegates discovery and session operations to Google's sender SDK and opens its
native route picker. Desktop composition currently supplies no Cast services. Google's
[sender SDK overview](https://developers.google.com/cast/docs/overview) lists Android, iOS,
and Web senders; its [Web sender setup](https://developers.google.com/cast/docs/web_sender)
requires a Cast-supported browser. It does not provide a native Kotlin/JVM desktop sender.

Desktop therefore needs a transport implementation, not an Android SDK dependency or
desktop-specific playback controller. Keep discovery interpretation, protocol messages,
request correlation, timeouts, heartbeat scheduling, connection lifecycle, and session
decisions in common Core. JVM effects only perform DNS-SD, TLS/socket operations, secure
random generation, and receiver-reachable HTTP binding.

## Implementation sequence

1. Add the common DNS-SD result/effect contract and discovery controller. Interpret Cast
   `id` and `fn` TXT attributes in Core, deduplicate receiver IDs across interfaces, preserve
   surviving endpoints on service loss, expire stale results, and reject old scan callbacks.
   Add explicit common selection to the existing session effect/controller, preserving the
   Android SDK's native selection path. **Implemented and locally verified.**
2. Implement the common Cast channel codec and sender session controller over an injected
   transport. Cover receiver launch, virtual connections, heartbeat, request IDs, bounded
   messages, receiver/media status, and command acknowledgements with deterministic fake
   transport tests. Specify receiver authentication rather than treating a self-signed TLS
   connection as authenticated. Do not declare a load accepted merely because bytes were sent.
3. Add a shared receiver picker and presentation actions. Core owns scan lifetime, expiry
   scheduling, selection, loading/failure state, dismissal, and return-to-local actions.
   Put every new user-facing label in all maintained translations. The Android native
   picker remains supported through its existing effect.
4. Compile/test common code on Android, JVM, and iOS before host wiring. Add mechanical
   desktop DNS-SD, TLS, HTTP binding, and secure-token effects. Bind media to the LAN interface
   that reaches the selected receiver; arbitrary VPN/virtual-interface selection is insufficient.
   Reuse existing shared endpoint authorization/range handling, without adding provider logic
   to the desktop host. Exercise native resource cleanup with adapter tests.
5. Wire Cast services into desktop composition and verify the visible shared picker. Run the
   real sender/receiver matrix, keep a draft PR open, and merge only after acceptance passes.

Discovery actions and native callbacks must run on the common owner's serialized context.
The DNS-SD adapter must include interface identity in `serviceKey`, refresh resolved records
while scanning, and publish removal callbacks. Core exposes `refreshExpiry` for shared
scheduling and rechecks expiry when looking up a row for selection. Advertisement metadata
is a discovery hint, not receiver authentication.

## Acceptance matrix

First-slice local verification on 2026-10-03:

```powershell
.\gradlew.bat :core:app:jvmTest :core:app:testDebugUnitTest :core:app:compileKotlinIosArm64 :core:app:compileKotlinIosSimulatorArm64 --console=plain
```

Passed: 258 JVM tests, 237 Android unit tests, and both Core iOS compilations. The Cast-specific
discovery, session, lease, request, and endpoint suites contain 24 passing tests. This is common-code
verification, not native iOS host compilation or physical desktop receiver acceptance. No platform
production files changed in this slice.

For each desktop OS, use online Navidrome and Jellyfin media where available:

- Discover the Onn, select it, load a track and artwork through scoped URLs, and confirm
  audible receiver playback with visible progressing position.
- Pause/resume, seek, adjust volume, advance a multi-track queue, stop casting, and return
  to local at the receiver position. A paused receiver must return to paused local playback.
- Test failed launch/load/commands, loss during playback, stale replies after reselection,
  sender close, hidden/restored window, and no duplicate local playback.
- Check HTTP GET, HEAD, ranges, invalid/expired/revoked tokens, provider/account changes,
  and credentials absent from receiver-visible URLs and diagnostics.
- Record OS/app revision, LAN conditions, receiver, provider, media format, actual results,
  and limitations. Do not mark another desktop OS supported based solely on a Windows test.

Downloaded/offline media, synchronized lyrics, local audio processing, and wider recovery
remain follow-up #168. No release tag or announcement is part of this implementation step.
