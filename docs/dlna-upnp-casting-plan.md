# DLNA/UPnP casting plan — WiiM first

Planning date: November 14, 2026. Source inspection baseline: `091fb73c4`.

Requested outcome: select a WiiM from Naviamp's output picker, browse and queue music normally,
and play lossless albums with verified gapless transitions. Users have requested this capability;
the owner does not currently have a WiiM. No WiiM compatibility is established by this
document. AirPin receiver evidence is recorded separately below. This remains an unreleased feature; the implementation status below distinguishes shared
foundations from a usable casting route.

Implementation is tracked by [issue #607](https://github.com/goosepod/naviamp/issues/607), targeted
to milestone **v2.12.0**, on branch `codex/607-dlna-upnp`, following
[the development workflow](development-workflow.md). The isolated worktree is based on accepted
`main` commit `57c266612`; this work is separate from the ongoing tvOS work.

## Implementation status — November 15, 2026

Current increment: Android and Desktop now supply the common UPnP service with native discovery,
HTTP engine and a receiver-interface-bound relay. Native JVM multicast discovery and original FLAC
delivery through the shared queue/session/provider relay passed against AirPin STD 5.7.1 on an Onn
Android TV; the owner confirmed audible test tones. AirPin does **not** advertise
`SetNextAVTransportURI`, so it cannot validate our gapless path. Foreground Pixel-to-AirPin discovery,
shared picker, original FLAC relay, audible playback and forward seeking passed with the receiver
awake, the television on and Tailscale temporarily disconnected; Tailscale was restored afterward.
Android background/network lifecycle testing, iOS host adapters/signing/privacy integration,
and real WiiM lossless/gapless acceptance remain open. The minimum Android version remains API 26.
See [the AirPin verification record](verification/607-airpin-2026-11-15.md) for evidence and limits.
The [Pixel/seek follow-up](verification/607-pixel-seek-2026-11-15.md) records the native main-thread
discovery fix, common zero-duration/phone-seek regression, longer FLAC/MP3 timing probes and the
passing awake Pixel test. Earlier zero-time reports were collected without validating power state;
they do not prove that AirPin cannot seek. The tested Tailscale-active setup remains unsupported by
this acceptance evidence. Preserve known provider duration when the renderer
reports zero; do not synthesize position or claim successful audible seeking from an acknowledgment.

The sections below retain the verification history of earlier increments; their statements about
absent native wiring describe those earlier commits, not the current branch.

The initial shared foundation adds UPnP output identity and a protocol-neutral remote action contract
to the existing Core router. Cast uses that contract; Connect retains its existing destination path.
An absent UPnP route fails without starting local playback. No host production files, runtime UI
entry point, permission, setting or minimum Android version are changed. Wire-layer dependencies
are described below.

`NaviampUpnpMediaPlan` parses bounded Sink protocol advertisements and chooses original FLAC or
MP3 bytes only when the encoding has an explicit compatible HTTP advertisement. It preserves the
source sample metadata and rejects mismatched MIME/encoding, wildcard-only support and unsupported
original formats. MIME aliases are understood. Profile-constrained advertisements currently return
`RequiresProfileVerification`; DLNA profile matching, sample-format limits and WAV/ALAC handling
remain work items. A format plan is advertisement evidence, not verified high-resolution hardware
support. Future relay wiring must apply its chosen MIME consistently to metadata and HTTP responses.

`NaviampRemotePreparedHandoff` is a small common reconciliation ledger. It consumes queue-owner
occurrence identities; it does not choose the queue, issue transport commands, renew subscriptions,
or own leases. Preparation tickets reject displaced sessions, queue edits, superseded work and
cross-ledger use. Observed promotion happens once without another Load/Play, using a distinct scoped
URI per occurrence. The last four retired URIs suppress immediate late echoes with bounded storage.
The eventual common protocol reconciler must order event/poll observations, handle GENA sequence
wrap and classify takeover/stop/loss before driving this ledger. An unexpected URI is a signal for
that reconciliation, not permission to seize playback or automatically navigate the queue.

Common tests include an **in-process software renderer** using the real shared format plan, scoped
leases, HTTP authorization/ranges and ledger. A generated 245-byte FLAC fixture is fetched exactly,
including HEAD and range reconstruction, and repeated occurrences promote without a second Load or
Play. An optional-next rejection cannot stage a next resource, and an account-filtering byte-source
fixture denies foreign-source bytes. This tests the relay contract; full provider authentication
isolation retains its existing Cast byte-source coverage and still needs UPnP integration coverage.

The next increment adds common SSDP message parsing, descriptions, SOAP and GENA wire handling,
plus actual HTTP loopback tests described below. No audible gapless or WiiM compatibility claim
follows from these tests. The shared queue/relay integration is recorded below; native host wiring
still remains a separate step.

### Foundation verification

Local Windows validation on the issue branch:

- `:core:app:jvmTest`: **311 tests**, no failures/errors (includes existing Cast/Connect and relay tests).
- `:core:presentation:jvmTest`: **627 tests**, no failures/errors (includes existing Cast routing and provider-byte tests).
- Both modules' `compileDebugKotlinAndroid` and `compileCommonMainKotlinMetadata` pass.
- `verifyCoreFirstArchitecture` and `git diff --check` pass. No platform production files change.
- The embedded FLAC matches the locally generated bytes and decodes successfully with FFmpeg;
  ffprobe confirms 441 samples, stereo, 44,100 Hz and 16 bits. Test execution needs no FFmpeg.

Common-metadata validation exposed five existing calls to JVM's no-argument
`CancellationException()` in `NaviampPlaybackStreamRelay` and
`NaviampCorePlaylistTransactionController`. They now use the portable
`CancellationException(null, null)` factory, preserving the null message/cause and cancellation
behavior. The two arguments disambiguate Native message/cause overloads; the initial single-null
form passed JVM/common metadata but failed iOS CI.
The existing module regression suites cover these owners; metadata compilation exercises the API
restriction that JVM compilation alone missed.

These commands use `-Pkotlin.native.enableKlibsCrossCompilation=false` on Windows. Common metadata
compilation is **not** native iOS compilation or execution; Apple target checks remain required in
CI before native host integration. No TV/phone/device test or live LAN discovery was performed for
this foundation. The milestone records release intent, with the physical release gates still open.

### Wire-level increment and verification — November 15, 2026

The wire layer stays in `core:app/commonMain`:

- `NaviampUpnpDiscoveryWire`: bounded M-SEARCH generation and search/alive/byebye parsing,
  including UPnP 1.1 extension headers, packet-source identity, bounded advertised lifetime and
  rejection of duplicate headers or off-device description URLs. The eventual discovery registry
  must retain source **and interface** identity, expiry and scan generations; byebye is not an
  instruction to remove every endpoint with that UDN.
- Description parsing verifies the selected UDN and namespace, locates embedded renderers and
  resolves relative service/SCPD/event URLs including dot segments and query references. Endpoints
  are conservatively pinned to the selected description origin. Cross-origin service URLs need an
  explicit, tested policy before being supported.
- `NaviampUpnpRendererClient`: SCPD action discovery, ConnectionManager Sink probing, namespaced
  SOAP, DIDL metadata and AVTransport/RenderingControl commands/status. Missing optional actions
  fail before sending; SOAP faults and unexpected response actions fail explicitly. Load/Play
  acknowledgement never grants playback authority or substitutes for observed playback. Time parsing
  accepts signed decimal/rational fractional seconds, truncates to milliseconds and rejects overflow.
- `NaviampUpnpEventSubscriptionClient` supplies subscribe/renew/unsubscribe operations and a
  renewal deadline. `NaviampUpnpEventGate` validates selection/SID/NT/NTS/content type and SEQ,
  rejects duplicate/old events, handles wrap from 4,294,967,295 to 1, and parses bounded nested
  LastChange for InstanceID 0. Missing sequence numbers flag a resync requirement; the eventual
  common session owner must poll/reconcile before treating those observations as authoritative.
- `NaviampUpnpKtorTransport`: a dedicated, unauthenticated client with an injected native engine;
  no provider-client authentication plugins are inherited. Core rejects redirects, bounds control
  response bodies to 256 KiB before decoding UTF-8, and enforces a five-second exchange deadline.
  These limits apply to control XML, **not streamed audio**. The creator owns the engine lifetime.

Added [xmlutil core 1.0.2](https://github.com/pdvrieze/xmlutil/tree/v1.0.2.1#core) and the existing
catalog's Ktor client core to `core:app`; CIO is test-only. The generic XML parser runs the same
implementation on all hosts, without JDK/Android XML wrappers. DTD/entity declarations are rejected
before parsing; namespace-aware traversal limits depth to 32, nodes to 4,096, attributes/namespaces
to 32 per element and events/text/input size. Diagnostics exclude parser payloads/URLs. The
[release notes](https://github.com/pdvrieze/xmlutil/releases/tag/v1.0.2.1) identify 1.0.2.1 as a BOM
publication fix; the core artifact remains 1.0.2. Native behavior is verified in CI, rather than
assuming equivalent behavior from JVM results or the library's platform support statement.

The JVM-only **test renderer** binds a disposable loopback TCP socket. Its HTTP/SOAP service
descriptions, control responses, SOAP faults and NOTIFY callback endpoint exercise the real common
HTTP client, metadata codec, event gate, scoped media relay and handoff ledger together. It verifies:

- Exact original FLAC transfer for repeated occurrences, HEAD, closed/open/suffix ranges and
  revocation over HTTP; original FLAC metadata survives nested DIDL/SOAP escaping.
- Observed STOPPED after load, explicit Play, current/next promotion, duplicate HTTP callbacks,
  status polling, pause/seek/volume/stop and subscription renewal/unsubscribe.
- Exactly one Load and one Play across a prepared transition; unsupported SetNext never sends an
  action, and HTTP 500/UPnP fault 701 cannot be mistaken for successful preparation.
- No redirect follow and bounded reads for both declared and chunked oversized responses.

Local checks pass: **323 app + 627 presentation tests (950 total)**; both modules' Android and
common-metadata compilation; the Core architecture check and diff check. The cancellation-fix CI
run (`43748120294`, commit `fe31a5977`) passed common/device/simulator compilation, simulator tests,
native TLS/BASS execution and production iOS build before this increment was pushed. The new wire
increment must pass its own CI; earlier results do not verify a later source tree.

This renderer models transport/promotion and byte delivery; it does not decode audio or measure
sample-clock continuity. SSDP packet parsing uses fixture datagrams, **not live multicast**. Live LAN
discovery, a third-party renderer, native listener/permission integration, background subscription
scheduling, queue/lease owner extraction, resync/takeover orchestration and WiiM physical tests
remained open at the end of the wire increment. The app still exposes no UPnP picker entry. No
network device was controlled for these tests, and Android remains API 26 minimum.

### Shared queue and media relay integration — November 15, 2026

Incoming PR head `222083d14` passed the complete Verify matrix, including iOS, Windows, macOS,
Linux, Android API 30/35 and coverage; evidence is in
[run 43750537128](https://github.com/goosepod/naviamp/actions/runs/43750537128). That result verifies
the earlier wire increment, not subsequent queue integration changes.

`NaviampCoreRemotePlaybackController` now owns queue handoff, current/prepared media, relay leases,
remote observations and explicit return to local for both Cast and UPnP. The old Cast controller is
a small facade over this owner. `NaviampCoreCastRemoteSession` retains Cast's MP3 compatibility
policy and native SDK translation; `NaviampCoreUpnpSession` supplies UPnP protocol operations and
original-only format negotiation. There is no second UPnP playback queue: all advancement calls
the existing `NaviampPlaybackQueueCoordinator` and updates `NaviampLivePlaybackController`.

The common owner:

- Prepares the next exact queue occurrence with a distinct scoped URL, even for repeated copies
  of one song or repeat-one. Observed Playing on that URI advances the canonical queue once,
  adopts the prepared media, and prepares the following occurrence without another Load/Play.
- Watches structural queue/repeat changes. Superseded work is cancelled and guarded by selection,
  source, queue and preparation revisions; the receiver's old next slot is cleared before its URL
  is revoked. Metadata-only changes preserve playback and use current canonical metadata at
  promotion. Progress updates take the unchanged-queue fast path rather than comparing every track.
- Retains active/prepared leases until explicit release so long pauses and long tracks do not hit
  the normal one-hour expiry. Retention remains in memory and subject to the existing eight-lease
  quota; current/next artwork is included. A bounded previous resource survives replacement until
  the new URI is observed. Selection loss, source changes, stop/replacement and disposal clean up
  owned slots; no persisted renderer URL or credential is introduced.
- Normalizes qualified MIME aliases in the shared HTTP endpoint so DIDL and relay response MIME
  agree. An unexpected lossy upstream type is rejected before bytes are sent; no lossless source
  is silently transcoded. Requests still use the existing authenticated provider byte source and
  source namespace checks, including main/companion provider routing. Original streams are not
  staged as complete temporary files. Playback/seek quality comes from the selected remote plan,
  rather than the previous local engine's quality.
- Verifies the loaded URI before Play or playback authority. Acknowledged-but-ignored loads cannot
  play another controller's content. Polls and accepted events use serialized current-status reads;
  stale observations cannot overwrite a newer load. Observed takeover releases our URLs without
  sending Stop to the other controller. Source loss and UPnP failures pause without automatically
  restarting local audio. Cast's existing return/reconnect behavior remains covered by its tests.
- On explicit return from UPnP, waits for owned receiver cleanup before automatically resuming
  local audio; failed cleanup leaves local paused. Shutdown waits for cleanup, and synchronous
  disposal can revoke the relay even after the caller's scope has been cancelled. Late Cast
  shutdown callbacks cannot displace a newer UPnP selection.

`NaviampCoreServices.upnp` is an optional dependency group for a dedicated LAN HTTP transport,
HTTP listener and secure token source. `NaviampCore.selectUpnpRenderer` describes/probes the chosen
device and assembles the same owner through Core's composition root. It guards out-of-order
selection and closed-Core completion. Hosts do not construct product controllers or duplicate the
flow. Keep the UPnP listener/engine instance separate from Cast's native effect instance; common
policy reuses the existing media endpoint/byte-source implementation (its legacy names remain).
The optional service defaults to absent; no native host currently supplies it and no new picker
entry is exposed. All production edits remain in common Core; minimum Android remains API 26.

The UPnP adapter's common fallback polls every two seconds only while selected/connected; tests
disable the timer and drive observations deterministically. Its event hook validates SID/sequence
through the existing gate and reconciles against status before queue changes. This is not yet a
native GENA callback listener or renewal scheduler. Stop completion requires explicit command
intent or end-position evidence; ambiguous STOPPED observations are not invented track endings.

Common integration tests exercise the real queue coordinator, shared owner, provider byte source,
relay and UPnP client with deterministic wire responses. They cover repeated occurrences, repeat-one,
queue edits, metadata refresh, long pauses, failed/stale preparation, source loss, ignored loads,
takeover, selection races, return-to-local ordering, disposal and the public Core facade. The earlier
TCP loopback tests continue to exercise actual HTTP/SOAP/NOTIFY/ranges. Neither fixture measures
decoded sample continuity or establishes WiiM hardware support.

Remaining: live SSDP/interface/expiry lifecycle and shared picker; native engine/listener/permission
wiring; GENA callback/renewal lifecycle and platform background validation; broader codec/profile and
seek-mode qualification; third-party renderer and physical WiiM lossless/audible-gapless evidence.
The new queue integration requires its own complete CI result before host integration or merging.

Local validation for this increment: **327 app + 640 presentation tests (967 total)**, zero failures
or errors; `compileDebugKotlinAndroid` and `compileCommonMainKotlinMetadata` for both modules;
`verifyCoreFirstArchitecture`; and `git diff --check`. These Windows commands keep native klib
cross-compilation disabled, so the new source still requires native Apple verification in CI. No
Android/Desktop/iOS production files or dependency versions changed in this increment.

## Product scope and protocol roles

Naviamp acts as the UPnP AV **control point**; the WiiM acts as a **MediaRenderer**. Naviamp discovers
the renderer, supplies track metadata and a reachable media URL, and controls transport and volume.
The renderer retrieves the audio over HTTP and performs decoding/output. DLNA adds media
interoperability requirements around this UPnP AV arrangement; it is part of this implementation,
not a second independent casting engine. The
[WiiM Mini manual, section 9.5](https://www.wiimhome.com/pdf/WiiM%20Mini%20UM.pdf)
documents that product's DLNA renderer role. Treat model and firmware support individually.

The first user-ready release includes:

- Shared output selection, discovery/refresh, connection status and explicit return to local output.
- Play, pause, stop, seek and volume where the renderer supports those operations.
- Existing Naviamp queue, album order, next/previous, shuffle and repeat behavior.
- Original lossless audio delivery and actual format reporting.
- Verified gapless playback for a documented set of consecutive, same-format lossless tracks on
  at least one real WiiM model/firmware. A basic one-track prototype is an intermediate milestone,
  not completion of the requested feature.
- Artwork/track metadata and lifecycle/network recovery without duplicate playback.

Initial exclusions: a browsable whole-library DLNA MediaServer, receiving casts into Naviamp,
WiiM account/setup APIs, synchronized multi-room group management, OpenHome extensions, local
system-audio capture, crossfade and universal compatibility with every DLNA device. Generic
renderers can use the same standard implementation, but support claims require their own evidence.
Selecting an already configured WiiM group leader can be investigated separately from managing it.

Ordinary remote playback does not need BASSmix: the renderer receives encoded media, rather than
the local BASS mix. Local EQ, crossfade, software downmix and visualizer processing do not
automatically apply to that remote stream. Advertise capabilities from the selected route.

## Existing code to reuse and generalize

The inspected application contains Google Cast and Naviamp Connect paths, but no UPnP/DLNA
discovery or controller implementation.

| Existing owner | Reuse or required change |
| --- | --- |
| `core/app/.../NaviampPlaybackOutputSelectionController.kt` | Preserve the single active output and generation IDs; add a distinct UPnP output identity. |
| `core/presentation/.../NaviampCoreOutputPlaybackEffects.kt` | Generalize the current Cast-specific route selection to a shared remote-output contract. Preserve local-observer suppression and explicit playback authority. |
| `core/presentation/.../NaviampCoreCastController.kt` | Extract provider-neutral queue handoff and lease coordination without copying a second playback controller. Keep protocol-specific load/status operations separate. |
| `core/app/.../NaviampCastSessionController.kt` | Reuse lifecycle and stale-callback invariants through the shared contract; do not call a UPnP renderer a Google Cast session. |
| `core/presentation/.../NaviampCoreCastPickerController.kt` and shared UI | Extend one picker/model to show protocol-labelled targets and route capabilities; preserve Android's existing Google Cast SDK picker integration. |
| `core/app/.../NaviampCastDiscoveryController.kt` | Reuse expiry/interface/generation patterns, not its DNS-SD wire protocol or Cast TXT interpretation. |
| `core/app/.../NaviampCastMediaEndpointController.kt`, lease/request controllers | Generalize scoped media access, GET/HEAD/ranges, cancellation and cleanup for multiple receiver protocols. |
| `core/presentation/.../NaviampCoreCastMediaByteSource.kt` | Reuse authenticated provider-byte access, temporary storage and source isolation. Keep credentials on the sender. |
| `core/domain/.../Models.kt` | Extend shared format/capability models where needed; avoid platform-specific codec policy. |

The current Cast controller explicitly selects original MP3 or provider-side MP3 transcoding and
labels the receiver media `audio/mpeg`. **That policy must not be inherited by the UPnP route.**
The byte-source and range-serving foundations are useful independently of that policy.

The shared `AudioInfo` model currently includes codec, MIME type, bit depth and sample rate, but
not a channel-count field. `StreamQuality.Transcoded` currently names only Opus, MP3 and AAC and
does not express lossless sample-format constraints. Do not treat this API as an existing
lossless-transcoding implementation.

All protocol parsing, format selection, commands, state transitions, subscription renewal,
timeouts, queue orchestration and UI policy belong in common Core. Existing Cast/Connect behavior
must retain regression coverage throughout the extraction. Proposed contracts can describe a
renderer, its services, capabilities, commands and observations without exposing native socket types.

## Discovery and device control

Implement SSDP discovery per usable LAN interface, device/service description retrieval and
namespace-aware XML/SOAP handling. Preserve device UDN plus interface/endpoint identities;
deduplicate repeated announcements, honor expiry, and reject callbacks from old scans. Resolve
relative service URLs against the device description correctly. Handle network/interface changes,
permission denial, disappearing devices and multiple adapters without selecting an arbitrary VPN
address for the media relay. Discovery is active only while useful; no permanent rapid scan loop.

Read the device's advertised services and action descriptions. Use ConnectionManager protocol
information together with the actual media properties for format selection. Do not infer codec,
seek or next-track support solely from a friendly name or a WiiM manufacturer string.

Map common commands onto AVTransport and RenderingControl. Fetch initial status before taking
playback authority. Add event subscriptions, bounded callback parsing and subscription renewal;
use a conservative shared polling fallback when events are absent or unreliable. Treat command
acceptance and observed playback state separately. Handle SOAP faults and unavailable actions
explicitly rather than accepting every successful HTTP response as playback success.

Extend the shared HTTP boundary, if necessary, to carry the bounded headers/body required for
event callbacks and DLNA media requests. Native adapters transfer data; Core validates subscription
IDs, event sequences, destinations and protocol content. Include external-entity rejection and
size/depth/time limits in the common XML implementation. Reuse existing HTTP dependencies where
appropriate; evaluate any added multiplatform XML dependency for Android 8 and Apple support.

Protocol references: [UPnP device architecture](https://upnp.org/specs/arch/UPnP-arch-DeviceArchitecture-v1.1.pdf),
[AVTransport](https://upnp.org/specs/av/UPnP-av-AVTransport-v1-Service.pdf),
[ConnectionManager](https://upnp.org/specs/av/UPnP-av-ConnectionManager-v1-Service.pdf), and
[RenderingControl](https://upnp.org/specs/av/UPnP-av-RenderingControl-v1-Service.pdf).
These define protocol behavior; they do not establish that a particular WiiM firmware implements
every optional feature correctly.

## Lossless delivery policy

Create one common format-selection plan from source codec/container, MIME aliases, bit depth,
sample rate, channel count when known, renderer protocol information and verified compatibility
records. Missing metadata is unknown, not proof of either incompatibility or high-resolution support.

1. Prefer the original bytes when the renderer can consume them. Original FLAC is the primary
   acceptance case. Add WAV/PCM, ALAC and other formats only with appropriate container/profile
   handling and tests; a file extension alone is insufficient.
2. For a lossless source, do not silently fall back to MP3/AAC/Opus. If the original cannot play,
   report the incompatibility. A future lossless conversion path must explicitly preserve sample
   rate, bit depth and channel layout, use an actually supported provider/encoder, and pass PCM
   equivalence tests. It is not required to make compatible original FLAC work.
3. Original lossy tracks may still play as lossy sources. Converting them into FLAC cannot restore
   missing audio. Keep source and delivered quality distinct in the shared status model.
4. If a later user preference allows lossy compatibility conversion, make it explicit, translatable
   and included in settings export/import/sync, with safe defaults for older exports.

Serve accurate content types, lengths, range responses and any implemented DLNA transfer/profile
headers. Never advertise a profile, byte-seeking, time-seeking or conversion guarantee that the
endpoint does not implement. Test HEAD, initial full reads, open-ended/suffix ranges, repeated
connections and seeks. Keep memory and temporary-storage budgets bounded, but stream large
original lossless tracks without requiring a complete download before starting.

The default delivery path reuses Naviamp's temporary, scoped media relay. This supports provider
authentication, custom headers and certificate-protected servers without handing their credentials
to the renderer. Bind to a receiver-reachable LAN address. Tokens grant temporary track access;
avoid logging them, scope access, expire/revoke leases and validate device-supplied URLs. Do not
equate unauthenticated discovery or ordinary LAN HTTP with Google Cast's device authentication.

An optional direct-provider URL path can be added only where the renderer can reach the source,
handle its TLS and use appropriately scoped authorization without exposing reusable credentials.
Both delivery paths require a viable next-track source. The initial relay/controller mode requires
Naviamp to remain running and reachable; it must not promise that a phone can leave the network.

Distinguish three claims: lossless source, unchanged delivered audio, and bit-perfect physical
output. Passing through a FLAC file establishes neither the WiiM's DSP configuration nor the DAC's
output. Report what is known and reserve bit-perfect output claims for a measured configuration.

## Gapless queue design

Use the renderer's next-resource mechanism to prepare track B while A is playing. Do not wait for
an end-of-track poll and then send an ordinary Play command. The standard makes
`SetNextAVTransportURI` optional; its presence alone is not proof of audible gapless behavior.

Keep a common current/next state machine with separate output-selection, queue and preparation
revisions. Its design must cover:

- Resolve and expose B early enough for prefetch, preserving original album order and quality.
  Maintain both current and next leases, with expiry sufficient for a long paused track and renderer
  read-ahead. Bound further look-ahead independently of those two active resources.
- Associate a prepared slot with the exact queue occurrence, not just track ID: the same song can
  appear repeatedly. Editing, shuffling, changing repeat mode or skipping invalidates/replaces B.
- On renderer promotion from A to B, advance the shared queue exactly once and prepare C. The
  renderer already started B; sending a second load/play would interrupt it.
- Reconcile event and poll observations, including late, missing or duplicated events and stale
  current-URI echoes. Ignore observations from displaced sessions. Repeated identical resources
  need occurrence identity and validated transition evidence, not URL equality alone.
- Differentiate a natural transition, user Stop, remote-controller takeover, buffering and device
  disappearance. Stop/replacement clears the prepared slot before revoking URLs, with bounded
  cleanup if the receiver cannot be reached. Reconnecting must not resurrect stale queued audio.
- Retain metadata and progress coherence across transitions. Artwork requests and delayed range
  requests must not cause premature revocation of an otherwise valid active resource.

Track preparation and conversion, where available, must finish before the transition deadline.
Tests must exercise slow provider resolution, authentication renewal and large files. The existing
Cast transcoded-file preparation limit is not a reason to start a lossy stream or pretend a missed
deadline was gapless.

The initial guaranteed gapless scope is consecutive tracks with the same codec/container, sample
rate, channel layout and supported sample format. Test mixed-format/rate boundaries separately;
receiver output-clock changes may introduce gaps. Unsupported renderers can offer ordinary queue
playback with an honest capability indication, but that does not meet the WiiM gapless release gate.
Crossfade is a different behavior and is not a substitute for gapless playback.

Keep compatibility records keyed by model, firmware and tested media combination. Revalidate after
firmware changes. Prefer standard behavior and narrowly evidenced quirks; do not create a second
WiiM-owned queue or controller. WiiM's
[playback-route explanation](https://forum.wiimhome.com/threads/tech-tuesday-why-your-playback-route-matters.10637/)
also distinguishes lossless audio, gapless transitions and controller/server availability.

## Platform boundaries and lifecycle

| Host | Permitted native work and required checks |
| --- | --- |
| Android, including TV | UDP sockets/interface access, a Wi-Fi multicast lock where required, LAN HTTP binding, permissions and native service/MediaSession lifetime. Validate Android 8/API 26 without raising the app minimum, plus a current Android release. |
| Windows/Linux/macOS Desktop | Native/JVM sockets, interface binding, HTTP listener lifetime and existing desktop packaging/firewall/network-permission integration. Keep protocol and recovery decisions common. |
| iOS | Darwin networking/listeners, local-network permission and multicast capability/signing, and unavoidable host lifecycle. Test real-device background behavior; do not assume an audio background mode authorizes an indefinite silent relay. |
| tvOS | Reuse common logic when that host exists. Audit its own networking and lifecycle rules; this feature must not become a prerequisite for the separate tvOS implementation. |

Apple's [local-network guidance](https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy.md)
requires special handling for iOS multicast, and notes that simulator tests do not validate that
privacy behavior. Do not copy iOS permission assumptions to every Apple host. Request capabilities
only for actual native operations, and handle refusal through the shared UI.

For every host, test pause/resume, screen lock, background/foreground, sleep, Wi-Fi changes and
process termination. A relay cannot guarantee uninterrupted playback after its host is suspended
or killed. Document supported operation and report loss of the route without unexpectedly blasting
audio from the local device. Return-to-local playback remains an explicit shared action.

All new UI copy uses every maintained string catalog. Existing portable settings continue to
round-trip; local device addresses and live sessions are not portable configuration. Preserve shared
artwork shape rules, accessibility and animation budgets in the extended picker/Now Playing UI.

## Implementation stages and exit criteria

| Stage | Work | Required exit evidence |
| --- | --- | --- |
| 1. Shared remote-output contracts | Extract the existing common owners, add UPnP identity/capabilities and the lossless format plan. | Cast/Connect and new failure-case common tests pass; common implementation compiles for Android, JVM and iOS before host wiring. |
| 2. Protocol simulator | Add common discovery, descriptions, SOAP/status/events and a deterministic fake renderer/HTTP source. | Lossless originals survive exact byte/range checks; malformed messages, missing actions and stale sessions fail safely. |
| 3. Native transport integration | Implement minimal socket/listener adapters and the shared picker. Start live integration on Desktop and Android; compile all hosts throughout. | Actual LAN discovery/load/control and resource cleanup against a software renderer; existing Cast/Connect checks remain green. |
| 4. Gapless orchestration | Add the current/next state machine, prefetch/leases and event reconciliation. | Queue mutation, repeat/shuffle, retries, cancellation and duplicate-promotion tests pass with no extra load at a natural transition. |
| 5. WiiM physical acceptance | Borrow hardware or arrange an explicitly authorized contributor test. Select the first exact model/firmware based on availability. | Recorded lossless and gapless matrix below passes on a real WiiM. No universal WiiM-family claim from one model. |
| 6. Platform/release readiness | Complete lifecycle, permission, UI, performance and packaging validation; update public support statements. | Required CI and documented physical tests pass, with accurate model/format/host limitations. Add a second WiiM family before broad compatibility claims. |

Stages 1–4 can progress without the owner buying a WiiM. A software renderer and mocks do not close
stage 5. Intermediate builds may remain an explicit experiment, but public WiiM/lossless/gapless
support waits for hardware evidence. No equipment purchase or outreach is authorized by this plan.

## Verification and hardware evidence

Common tests cover format selection without silent lossy fallback; source-account/credential
isolation; one-output authority; description/metadata escaping; SSDP expiry; action faults; range
semantics; event sequence handling; subscription renewal; queue-occurrence identity; lifecycle
cancellation; and cleanup. Add simulated receivers that omit next-track support, lie about
capabilities, send duplicate/out-of-order events, prefetch late, disconnect during a seek, and allow
another controller to take over. Ensure the test detects a false successful load and a double
advance, not merely that a SOAP request was generated.

Use generated continuous PCM split into independently encoded, consecutive lossless tracks.
Include ordinary CD-rate stereo and supported high-resolution cases, plus long tracks, short tracks,
repeated tracks and mixed-format cases. Compare received original bytes and reconstructed ranges
exactly. For any future conversion, compare decoded PCM and sample parameters separately.

For a physical gapless claim, record digital output at a known fixed format with relevant DSP,
volume, EQ and sample-rate settings documented. Align the whole reference/capture once to account
for fixed delay; do not align each track separately and erase a boundary error. Check for inserted
silence, missing/repeated samples and clipped endings across repeated transitions. Listening to a
live/seamless album is a useful supplementary check. Analog/listening-only evidence must not be
described as a bit-perfect digital capture. A mismatched output format needs its own honest result.

Record per test: Naviamp commit/build/host OS, WiiM model/firmware, provider and delivery route,
source/delivered format, wired/Wi-Fi topology, current/next request timing, output settings, observed
control behavior, transition count, capture method and result. Keep credentials, tokens and personal
network identifiers out of shareable evidence. Mark each case passed, failed, untested or unsupported.

Required matrix before release:

| Area | Acceptance |
| --- | --- |
| Lossless | Original FLAC, accurate bit-depth/rate metadata and no hidden MP3 fallback; additional formats explicitly qualified. |
| Gapless | Repeated same-format transitions with no introduced gap, clipping or duplicate audio, including queue edits and repeated occurrences. |
| Controls | Play/pause/stop/seek/volume and next/previous remain coherent with physical-device controls. |
| Protected providers | Main and companion identities stay separate; custom-header/TLS-protected sources relay correctly where supported. |
| Recovery | Network loss, device restart, background/sleep and controller takeover do not duplicate or unexpectedly relocate playback. |
| Resources | No stale listeners/subscriptions/leases; bounded network, cache and memory use; no rapid idle polling or unrelated UI repainting. |
| Existing outputs | Local BASS, Google Cast and Naviamp Connect behavior and cross-platform builds remain valid. |

The principal unresolved acceptance dependency is access to real WiiM hardware or an authorized
tester with an appropriate capture setup. The first model, supported format combinations and host
background guarantees must be selected from actual evidence, not assumed from the protocol name.

### Discovery and combined picker foundation — November 15, 2026
Incoming head 579b277b2 passed the entire Verify matrix (run 43760143275). The new common discovery owner tracks packet source plus local interface, expiry and scan generations; bounds the registry to 64 entries; fetches/validates renderer descriptions; and rejects completion from stopped or replaced scans. The shared Cast picker can now merge UPnP targets, or operate with UPnP alone, and routes selection through Core's existing UPnP facade. Closing the picker stops discovery without disconnecting playback. Existing translated picker controls are reused.

This increment defines the raw UDP discovery effect but does not yet supply or enable a native implementation. No live multicast discovery or native device test has been performed. Repository implementation order requires common Android/JVM/iOS validation before host wiring; the new discovery/picker source needs its own Apple CI result. Native UDP/interface adapters, Android multicast permission/lock handling and desktop HTTP-engine/listener bootstrap remain unfinished. Continue those after validation, keeping discovery policy in this common owner. Do not describe this as live discovery shipping in the app.

Local verification: 330 app + 641 presentation tests (971), both modules' Android/common-metadata compilation, architecture and diff checks pass. Before enabling UPnP, replace the picker’s Google Cast-specific searching copy with protocol-neutral copy across maintained translations or an existing generic translated resource. Native host wiring and actual LAN validation remain unfinished.

### Android/Desktop native integration and AirPin — November 15, 2026

The preceding common discovery/picker head `db37c49d5` passed the full Verify matrix
([run 43772690595](https://github.com/goosepod/naviamp/actions/runs/43772690595)), including iOS.
Android and Desktop now inject the shared UPnP service with separate HTTP engines/listeners and
native UDP effects. Android adds WifiManager multicast-lock lifetime and normal multicast permissions;
its relay validates and binds the exact discovered local IPv4 interface. Desktop reuses its existing
receiver-address-bound HTTP listener. Protocol, discovery refresh/expiry, format choice and queue
policy remain in Core. The picker now reuses the translated generic local-network-search string.

AirPin STD 5.7.1 on an Onn TV was discovered over actual multicast and requested original FLAC from
the shared queue/relay. The owner confirmed audible tones. Pause, resume and Stop were observed
through independent status requests. Seek was acknowledged, but reported position remained zero;
it is not a passing position/seek result. AirPin omits `SetNextAVTransportURI`, so this receiver does
not provide a physical gapless acceptance test. A common SSDP regression covers its trailing CRLF
response variant. The opt-in live test is skipped in normal CI and leaves the receiver stopped.

See [the detailed evidence, reproduction and platform-diff accounting](verification/607-airpin-2026-11-15.md).
The native adapters compile for Android/Desktop locally. Android sender runtime, iOS wiring/signing,
passive SSDP notifications/network rebinding, GENA callbacks, lifecycle and real WiiM acceptance
remain open. Keep PR 608 draft and #607 open; this increment is not a completed multi-platform or
verified gapless release feature.
