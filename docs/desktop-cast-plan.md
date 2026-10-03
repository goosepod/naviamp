# Desktop Google Cast (#171)

Issue: https://github.com/goosepod/naviamp/issues/171

Implementation branch: `feature/171-desktop-google-cast`, started from GitHub `main` on
2026-10-03. The owner accepted the desktop feature for merge after Windows and Linux testing
with the Onn 4K Pro. Physical macOS receiver testing remains a follow-up; automated macOS
coverage does not establish physical playback acceptance.

## Existing ownership and missing pieces

`NaviampCastSessionController` and `NaviampCoreCastController` already own output identity,
queue handoff, local playback suppression, commands, receiver progress, queue advancement,
and return to local playback. `NaviampCastMediaEndpointController` and its lease/request
controllers own scoped media access. `NaviampCoreCastMediaByteSource` obtains authenticated
provider bytes without exposing provider credentials to the receiver.

Android delegates discovery and session operations to Google's sender SDK and opens its
native route picker. Desktop composition now supplies native effects to Core's sender. Google's
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
   **Implemented:** bounded protobuf codec, nonce/TLS-bound RSA device proof to Cast roots,
   launch, virtual connections, request correlation, heartbeats, status polling, and controls.
   LOAD waits for usable media status; an IDLE echo cannot activate playback authority.
   Signed Cast CRL/revocation verification remains security hardening follow-up #168.
3. Add a shared receiver picker and presentation actions. Core owns scan lifetime, expiry
   scheduling, selection, loading/failure state, dismissal, and return-to-local actions.
   Put every new user-facing label in all maintained translations. The Android native
   picker remains supported through its existing effect.
   **Implemented:** shared picker/controller, five labels in all 17 resource files, expiry,
   selection/retry/local actions, and shared behavior plus rendered desktop UI tests.
4. Compile/test common code on Android, JVM, and iOS before host wiring. Add mechanical
   desktop DNS-SD, TLS, HTTP binding, and secure-token effects. Bind media to the LAN interface
   that discovered the selected receiver; arbitrary VPN/virtual-interface selection is insufficient.
   Reuse existing shared endpoint authorization/range handling, without adding provider logic
   to the desktop host. Exercise native resource cleanup with adapter tests.
   **Implemented:** isolated JmDNS browsers, cancellable TLS framing, JCA verification, and
   interface-bound TLS and routed JDK HTTP binding. Native tests cover certificate proof, framing/cancellation,
   GET/HEAD/ranges/revocation/closure. Packaging includes and verifies `jdk.httpserver`.
5. Wire Cast services into desktop composition and verify the visible shared picker. Run the
   real sender/receiver matrix, keep a draft PR open, and merge only after acceptance passes.
   **Accepted by the owner on 2026-10-03:** Windows playback was reported working; Linux
   application playback, pause/resume, skip, seek, and return to local were confirmed.
   PR #216 includes the final shared public receiver selection prepared in #218.

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

Second-slice checks on 2026-10-03 passed common JVM and Android app tests, Cast presentation
tests, desktop adapter tests, the visible picker action/layout test, Core-first architecture
verification, and Windows packaged-runtime verification. Both common iOS compilations,
Android app compilation and Android presentation tests passed for the readiness/lease changes.
The subsequent shared discovery-interface contract passed JVM/Android tests and both iOS
compilations before native binding was added. Tests run with no `NAVIAMP_CAST_PROBE` do not
exercise a physical receiver.

Physical Windows probe: the owner identified **Living Room TV** as the acceptance Onn 4K Pro.
It advertises model `onn 4K Pro Streaming Device`; discovery, trusted certificate-chain/device
proof, and Default Media Receiver launch passed. The first quiet WAV playback probe received
an IDLE load echo but no receiver HTTP requests and did not reach playing. Firewall inspection
found Public Wi-Fi with no allowance for this JDK. A temporary receiver/port/program-scoped
rule alone did not fix playback: the TLS route selected Tailscale's `100.114.87.25` source,
which became the media URL host. The discovery link was Wi-Fi at `192.168.10.200`.

Core now preserves the discovery address per endpoint; the native socket binds to that address.
With that fix and the owner-approved temporary rule, the Onn fetched the scoped WAV fixture
using two range requests, reported Playing with advancing position, and acknowledged pause,
seek to 10 seconds, resume, media stop, and application stop. All three desktop adapter tests
passed in that run. The rule's automatic removal was verified afterwards. The owner was not
listening, so audible output is unconfirmed. Provider media, artwork, actual queue/local handoff,
volume, loss/recovery, macOS/Linux, and signed CRL verification still require acceptance.

The probe's optional firewall manifest contains binding metadata only, never scoped URLs or
provider credentials. It and the elevation helper remain in ignored `build/` output. Native
test files are reproducible verification sources; `.windows-testing/` and generated storage
`bin/` files are ignored and excluded from the feature commit.

### Final owner acceptance (2026-10-03)

The owner reported Windows playback working before starting Linux testing. On Linux, the
actual application discovered and cast to Living Room TV. The owner confirmed playback,
pause/resume, skip, seeking, and return to local. A temporary branded receiver test also
displayed Naviamp on the TV, but branding is deferred to #217: both desktop and Android
use Google's public Default Media Receiver `CC1AD845`, which needs no user device registration.
The final public receiver launch probe passed with the Onn and confirmed its application ID
and display name. Common JVM/Android tests and Linux packaging passed after that selection.

The owner explicitly called the desktop Cast feature complete and requested merge. Remaining
physical macOS receiver checks, broader provider/format/artwork/volume/lifecycle and recovery
coverage, and signed Cast CRL/revocation verification are tracked in #168; no unperformed
physical check is claimed as passed. Branded receiver publication and TV styling remain #217.

## Platform diff accountability

- `platforms/desktop/src/desktopMain/kotlin/app/naviamp/desktop/cast/DesktopNaviampCastDiscoveryEffect.kt`: JVM NetworkInterface and
  JmDNS DNS-SD callbacks/resource lifetime cannot run in common Kotlin.
- `platforms/desktop/src/desktopMain/kotlin/app/naviamp/desktop/cast/DesktopNaviampCastTransport.kt`: JDK SSLSocket I/O/binding, certificate
  exposure, and cancellation-driven socket closure require the JVM TLS/socket API.
- `platforms/desktop/src/desktopMain/kotlin/app/naviamp/desktop/cast/DesktopNaviampCastHttpServerEffect.kt`: JDK HttpServer binding,
  HttpExchange byte transfer, and native server/executor lifetime require JVM APIs.
- `apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/app/DesktopComposition.kt`: the desktop host injects the preceding native
  effects and owns their final native resource disposal and OS secure random source.
- `core/app/src/jvmAndAndroidMain/kotlin/app/naviamp/app/JvmNaviampCastCryptoEffect.kt`: JCA X.509/PKIX, RSA
  signatures, and SecureRandom are JVM/Android cryptographic API calls.
- `apps/android/src/main/kotlin/app/naviamp/android/AndroidNaviampCastOptions.kt`: Android-only
  CastOptions/OptionsProvider APIs receive the shared public receiver ID.

No iOS host production file is changed. Protocol, roots/nonce/validity policy,
discovery interpretation, picker, scheduling, playback handoff, and lease cleanup remain common.

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
