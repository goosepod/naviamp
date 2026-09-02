# Naviamp Connect Protocol

Status: protocol version 1 draft; shared protocol, session state machines, Core pairing runtime and
lifecycle controller, Android/Desktop J-PAKE, authenticated-channel policy, AES-256-GCM, framed TCP,
durable identity proof, Android trust persistence, and Android pairing UI are implemented. Playback
state now has a Core-owned authoritative snapshot projection. Android pairing retains the encrypted
session and supports capability-gated transport, seeking, favorites, repeat, shuffle, and queue
commands with revisioned reconciliation. Connected targets render through the shared phone/Desktop
Now Playing controller surface. Same-source local-to-target queue handoff and controller-side
catalog/Internet Radio playback routing, assisted connection provisioning, and reverse
target-to-controller handoff are implemented. Revoke/rename management, non-Android host effects,
and Apple key lifecycle remain pending.

## Purpose

Naviamp Connect lets one Naviamp device discover and control another Naviamp playback device on the
same local network. Phone and Desktop may advertise controller capability, playback-target
capability, or both. Android TV and tvOS advertise playback-target capability only. The target
remains the authoritative owner of playback, its provider session, queue, playback clock, and
reporting.

The first acceptance topology is a physical Android phone controlling the Android TV emulator. The
same protocol must support Android, iPhone, macOS, Windows, and Linux controllers and playback
targets without platform-specific product behavior. The complete experience checklist is in
[`naviamp-connect-product-plan.md`](naviamp-connect-product-plan.md).

## Version 1 contract

- Discovery advertises `_naviamp-connect._tcp` with only a random instance ID, display name,
  supported protocol range, capabilities, port, and target fingerprint. Provider and account data
  never appear in discovery metadata.
- Every wire message uses a versioned envelope with a session ID, monotonically increasing sequence
  number, and optional request/correlation IDs.
- Protocol negotiation selects the highest mutually supported version. Incompatible peers fail
  before pairing or command exchange.
- Capabilities are negotiated explicitly. A controller rejects unsupported commands locally and a
  target validates them again.
- The target publishes authoritative snapshots with monotonically increasing revisions. Controllers
  ignore stale snapshots and reconcile from the target after reconnect.
- Mutating requests carry their expected target revision. A conflict returns the current snapshot
  rather than applying a command against unknown state.
- Request IDs make completed requests deduplicatable. Reusing an ID for a different command is an
  error. Only absolute, idempotent commands may be retried automatically after reconnect.
- Queue entries use unique occurrence IDs in addition to provider media IDs, preserving duplicates,
  groups, Play Next priority, current selection, position, repeat, shuffle, and playback-profile
  intent.
- Queue handoff and catalog starts are accepted only for a compatible canonical source identity.
  Authenticated stream URLs are never transferred. Targets use their own authenticated provider
  session to create stream requests; compatible transferred metadata may establish the queue
  immediately while target-side enrichment or validation continues asynchronously.

## Pairing and transport security

The short code shown on the TV must bootstrap a reviewed password-authenticated key exchange. The
code is consumed by the pairing operation and is never sent as an ordinary password, serialized in
protocol messages, logged, or retained in controller state. Pairing additionally requires:

- explicit visible approval on the TV;
- expiring codes and rate-limited attempts;
- mutual device authentication and explicit key confirmation;
- channel binding between pairing and the encrypted session;
- authenticated encryption and replay protection for every session;
- durable private keys and session credentials stored through Keystore, Keychain, or the Desktop
  secure-value adapter; and
- revocable, non-secret trust records that may be listed and renamed in Settings.

Android and Desktop use one Kotlin adapter over Bouncy Castle's Java J-PAKE implementation, based
on [RFC 8236](https://www.rfc-editor.org/rfc/rfc8236.html), with the NIST 3072-bit group. Its third
round performs mutual explicit key confirmation before Core releases any session secret. Naviamp
then derives a distinct 256-bit session root with HKDF-SHA-256, binding the protocol version,
pairing-session ID, ordered device identities, roles, and all six canonical round payloads. The
short-code input is cleared after Bouncy Castle takes ownership; transcript buffers and the derived
secret have explicit destruction paths. Malformed, reordered, cross-session, wrong-code, and
tampered exchanges fail closed.

This is the approved Android/Desktop primitive, not a cross-platform completion: Java cannot serve
Kotlin/Native Apple targets, so an interoperable reviewed Apple implementation and test vectors are
still required. The previously evaluated RustCrypto SPAKE2 package is not being used.

Naviamp will not implement a PAKE or other cryptographic primitive itself. Android playback commands
now remain on the retained authenticated session and run through shared executors and authorization
policy. Connection provisioning is capability-gated, encrypted inside that retained session, and
requires explicit target approval. Core excludes local certificate paths and device-only settings,
validates the offered connection through the normal provider owner before saving or applying
portable settings, and preserves the existing source if validation fails. Completed provisioning
requests deliberately cannot be replayed or retained in the request-deduplication cache. The target
returns an encrypted provisioning result so the controller reports validation success, failure, or
rejection instead of remaining on the approval prompt.

After J-PAKE confirmation, Android and Desktop derive independent controller-to-target and
target-to-controller AES-256-GCM keys and nonce prefixes from the session root. Core binds protocol
version, pairing-session ID, sequence, and direction as authenticated data, requires contiguous
directional sequence numbers, and closes the channel on authentication, replay, gap, session, or
payload failure. The JVM/Android socket effect uses a bounded four-byte big-endian frame length and
contains no pairing or command policy.

Successful initial pairing also retains the confirmed PAKE root behind Core's secure-value
boundary, separate from the non-secret trust record. While the TV explicitly advertises pairing
mode, a remembered controller may use that credential to open a fresh session without another code
or another trust record. The resume exchange binds both durable identities, the advertised target
fingerprint, a fresh target-generated session ID, and encrypted mutual confirmations; a missing or
mismatched credential fails closed. Android encrypts this credential with an AES-GCM key held in
Android Keystore before persisting it.

Each peer then signs one canonical proof binding the negotiated protocol, pairing-session ID,
ordered controller and target device IDs, fingerprints, and public keys. Core verifies that each
fingerprint is actually the SHA-256 digest of its supplied public key, validates the ECDSA proof,
and requires a final encrypted acknowledgement from both sides before creating trust. A target
removes the display code from retained state when approval begins; a failed handshake requires a
fresh pairing attempt rather than retaining the code.

## Platform boundaries

Shared Core owns advertisements as data, discovery state, pairing and trust state, compatibility,
commands, authoritative snapshots, retries, reconciliation, handoff validation, and all user-facing
status policy.

Hosts provide only the native effects that cannot live in common Kotlin:

- Android: DNS-SD/mDNS through `android.net.nsd.NsdManager`, local-network permission behavior,
  sockets, and Android Keystore operations.
- Apple: Bonjour/Network framework discovery, local-network privacy declarations, sockets, and
  Keychain operations. Local-network privacy must be verified on physical Apple hardware because
  the simulator does not model it completely.
- Desktop: the selected OS/JVM DNS-SD, socket, and secure-value implementations.

Discovery does not imply trust. An unpaired target advertises only while its explicit pairing screen
is active, and discovery alone never enables commands or connection provisioning.

## Delivery sequence

1. Shared versioned protocol, pairing/session state machines, validation, and deterministic fake
   transport tests. **Implemented.**
2. Shared discovery/advertising coordinators and narrow native DNS-SD adapters, with no command
   socket. **Shared coordinators plus Android browsing and registration implemented and verified
   between a physical Pixel and the Android TV emulator; other platforms pending.**
3. Reviewed PAKE, identity, secure-storage, and encrypted-session adapters. **Android/Desktop
   J-PAKE, AES-256-GCM, bounded framed TCP, Core pairing orchestration, and Android Keystore identity
   implemented; Android durable trust persistence is implemented; Apple adapters remain pending.**
4. TV pairing UI and phone/Desktop playback-target selection, permission, expiry, revoke, and
   recovery flows.
   **Android TV pairing/approval and Android phone discovery/code entry are wired to the shared Core
   lifecycle. Rename/revoke, stronger recovery presentation, and Desktop/Apple host wiring remain.**
5. Shared playback projection, target command executor, and shared remote Now Playing controller
   surface wired to the existing shared playback owner. **Implemented for transport, seeking,
   favorites, repeat, shuffle, queue selection, Play Next, reorder, removal, catalog playback, and
   Internet Radio.**
6. Assisted connection provisioning and same-source atomic queue handoff. **Implemented in shared
   Core, including explicit target approval, portable-settings filtering, failure rollback, and
   controller-to-target plus target-to-controller transfer.**
7. Physical Pixel controller to Android TV emulator acceptance, followed by the capability-based
   phone/Desktop controller-and-target matrix and Apple targets.

## Current test coverage

The common tests cover protocol negotiation, serialization, source matching, snapshot validation,
code lifetime and rate limiting, explicit TV approval, capability rejection, request deduplication,
revision conflicts, stale-snapshot rejection, local target mutations, reconnect retry rules, and
same-source queue/catalog rejection, atomic queue replacement, source-identity projection, and
catalog action routing. JVM tests additionally complete matching-code J-PAKE exchanges,
compare derived secrets, verify caller-code and secret destruction, and reject wrong codes, altered
confirmations, malformed payloads, out-of-order rounds, and cross-session messages. Android
instrumentation additionally covers DNS-SD translation,
registration, browse lifecycle, real Pixel-to-TV-emulator discovery, and a durable Keystore EC
identity whose signatures verify against its exported public key. The complete matching-code
J-PAKE exchange also passes on the physical Pixel 10a, including confirmation and equal 32-byte
session roots for both roles. JVM coverage additionally verifies bidirectional authenticated
envelopes, directional key separation, session binding, replay/gap rejection, tamper rejection,
secret destruction, bounded TCP framing, clean peer closure, and oversized-frame rejection. The
bounded framed-TCP effect also round-trips in both directions on the physical Pixel 10a. The
complete Android pairing path also passes from the physical Pixel controller to the Android TV
emulator target with separate Keystore identities and an encrypted post-pair ping/pong. The
emulator's private NAT address required a temporary test-only host TCP relay; discovery and both
protocol endpoints remained on the actual devices. The shared settings surfaces compile for
Android, Desktop/JVM, and iOS Simulator ARM64. Android now injects the real effects: the TV
Controllers page starts a bound listener, advertises its actual port and identity, displays the
short code, and requires explicit approval; the standard Android settings page discovers targets
and accepts the code. A production smoke run verified both screens on the TV emulator and physical
Pixel 10a. The emulator's NAT topology prevents the unmodified phone UI from reaching its private
target address, so the relayed instrumentation test remains the authoritative physical-device
encrypted-pairing acceptance for that topology.
The retained-session acceptance now sends encrypted Play, a same-source queue handoff, a same-source
album start, an Internet Radio station start, and a provisioning offer, verifying acknowledgements
and authoritative updated snapshots on the physical Pixel 10a. Common tests cover remote
Now Playing projection and translation of shared UI actions into absolute Connect commands; Android,
Desktop, and iOS Simulator builds compile the same controller surface.
