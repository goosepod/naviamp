# Android TV and Naviamp Connect Branch Review

Date: 2026-09-04

Branch: `feature/android-tv`

Reviewed range: `main...feature/android-tv`

## Summary

This branch has a strong architectural foundation and unusually broad test coverage for work of
this size. The dedicated Television experience and Naviamp Connect behavior are predominantly
owned by shared Core code, while platform modules remain focused on concrete host boundaries such
as DNS-SD, secure credential storage, sockets, Android configuration, and native playback.

The three identified Connect correctness blockers—per-request completion, stable remote queue
identity, and outbound-write lifecycle handling—have been resolved and verified. The remaining
risks are maintainability in several very large shared files and the still-incomplete
physical-device and cross-platform acceptance matrix.

The branch adds approximately 24,637 lines and removes 396 lines across 178 files. About 6,970 of
the added lines are tests across 61 test files, and about 1,946 lines are documentation.

### Blocker resolution update — 2026-09-04

The three merge blockers identified by this review are now resolved in shared Core code:

- Controller requests retain bounded terminal results keyed by request ID. Acknowledgement,
  protocol rejection, timeout, disconnection, and outbound write failure are distinct outcomes;
  command waiters no longer infer success from the shared `lastError` field. Tests cover two
  simultaneous requests completing successfully and unsuccessfully in both orders.
- Remote Now Playing rows carry their queue occurrence ID and rendered revision as an explicit
  action target. Selection, removal, favorite, and Play Next resolve the occurrence rather than the
  latest positional index. A stale positional reorder requests a fresh authoritative snapshot
  instead of moving a potentially different occurrence.
- A transport exception before or during an outbound write ends the controller session
  immediately, publishes an actionable reconnecting state, and retains only idempotent pending
  commands for the established retry path. A receive-side disconnect after a completed write is
  recorded separately from write failure.

The Core app, presentation, storage, domain, and UI JVM suites pass after these changes. Android
Core compilation, iOS Simulator ARM64 Core compilation, Desktop tests, and Android debug assembly
also pass. The physical Pixel 10a and Android TV emulator pass their local Connect instrumentation
suites and the full identity-bound cross-device pairing and encrypted command sequence. Direct
DNS-SD discovery remains unavailable across the emulator/physical-device network boundary, so the
cross-device protocol run used the existing source-restricted test relay and explicit-host path.

### Merge and release scope decision — 2026-09-04

This branch is ready to be evaluated as an **Android TV preview candidate**, not as general Naviamp
Connect availability. Merge readiness is based on the resolved shared correctness blockers and the
completed phone/Desktop-to-TV-emulator coverage. A preview release still requires representative
physical Google TV validation. General availability remains gated by phone/Desktop playback-target
acceptance, Apple hosts, broader topology coverage, and the recovery matrix listed below.

### Protocol security gate update — 2026-09-04

The Android/JVM version-1 protocol completed its internal threat-model and implementation review.
The review found that trusted resumption originally relied only on a target-selected session ID, so
a recorded target resume offer could induce reuse of an earlier AES-GCM key/nonce schedule. Because
version 1 is unreleased, the wire contract was corrected in place: each controller supplies a fresh
challenge, the target echoes it and contributes its own fresh nonce, both values are bound into the
session/AEAD derivation context, and replayed offers are rejected before channel construction. The
new regression and the existing PAKE, identity, channel, replay, capability, revision, source, and
authority suites pass. The approved threat model and accepted limitations are recorded in
[`naviamp-connect-protocol.md`](naviamp-connect-protocol.md#version-1-security-review--2026-09-04).
Apple remains outside this approval until its implementation and interoperability tests exist.

### Android TV M1 gate update — 2026-09-04

The final standard-content fallback was removed from the Television shell. Home rails now expose a
remote-reachable **View all** card and open a dedicated shared Television collection grid with
deterministic focus and Back behavior. Multiple saved-source switching was also exercised on the TV
emulator in both directions. That pass exposed and fixed a Core offline-restoration defect where the
old inventory preference could override the newly selected unavailable source. Shared tests now
cover both successful and offline two-source transitions.

Controller-local navigation restoration and source-mismatch recovery are now also closed for the
Android phone -> Android TV emulator preview topology. Dismissing remote Now Playing restored the
phone's prior Search route. A deliberately mismatched catalog start left TV playback and both queues
unchanged, displayed a localized shared recovery dialog on the phone, routed into source Settings,
and completed the existing TV approval-gated secure setup flow. A post-setup catalog attempt reached
the target but returned media unavailable because the emulator could not resolve its freshly
provisioned catalog in this network fixture; it did not return source mismatch.

## Findings

### 1. High: command completion is not tracked per request

The controller session removes completed requests from `pendingRequests`, but records failure in
one shared `lastError` value:

- `core/app/src/commonMain/kotlin/app/naviamp/app/NaviampConnectSessionController.kt:211`
- `core/app/src/commonMain/kotlin/app/naviamp/app/NaviampConnectSessionController.kt:224`

Any acknowledgement clears that shared error. The product controller then decides whether a
specific command succeeded by waiting until its request ID disappears and inspecting the global
error:

- `core/presentation/src/commonMain/kotlin/app/naviamp/presentation/NaviampCoreConnectController.kt:1365`

This is incorrect when more than one command is in flight. For example:

1. Command A and command B are pending.
2. The target rejects A, removing A and setting `lastError`.
3. The target acknowledges B, removing B and clearing `lastError`.
4. A waiter can observe that A is absent and `lastError` is null, then treat A as successful.

For commands that activate remote playback authority, this can make the controller switch its
authority state even though the target rejected the playback request.

Recommended change:

- Model a terminal result keyed by request ID, or return an awaitable request handle from `send`.
- Keep acknowledgement, protocol rejection, timeout, disconnection, and write failure distinct.
- Do not let completion of one request clear the outcome of another.
- Add tests with two or more requests completing successfully and unsuccessfully in different
  orders.

### 2. High: remote queue actions can affect the wrong occurrence

Remote queue UI items currently use a positional identity such as `queue:5`:

- `core/presentation/src/commonMain/kotlin/app/naviamp/presentation/NaviampCoreConnectRemoteNowPlaying.kt:38`

Selection and item actions later parse that position and resolve it against the newest target
snapshot:

- `core/presentation/src/commonMain/kotlin/app/naviamp/presentation/NaviampCoreConnectRemoteNowPlaying.kt:147`
- `core/presentation/src/commonMain/kotlin/app/naviamp/presentation/NaviampCoreConnectRemoteNowPlaying.kt:156`

If a new authoritative snapshot inserts, removes, or moves an item between rendering and user
input, the same index may now refer to another occurrence. The resulting Connect command is built
from the newer snapshot, so normal revision-conflict protection will not catch the mistake. Queue
selection, removal, favorite toggling, and Play Next can therefore target an unintended track.

Recommended change:

- Preserve `NaviampConnectQueueOccurrence.occurrenceId` in the UI action target.
- Resolve actions by occurrence identity rather than by the latest positional index.
- Retain the rendered snapshot revision where a positional destination is unavoidable, such as a
  drag reorder, and reject or reconcile stale operations explicitly.
- Add a regression test that renders one snapshot, mutates the queue, and then invokes the old UI
  item's action.

### 3. Medium-high: outbound write failure does not end the connected session

`NaviampConnectControllerSession.send` adds a pending request and allows exceptions from the
transport send to escape:

- `core/app/src/commonMain/kotlin/app/naviamp/app/NaviampConnectSessionController.kt:106`
- `core/app/src/commonMain/kotlin/app/naviamp/app/NaviampConnectSessionController.kt:193`

Product call sites launch these sends without converting a write failure into `sessionEnded` or a
reconnecting destination state:

- `core/presentation/src/commonMain/kotlin/app/naviamp/presentation/NaviampCoreConnectController.kt:1158`
- `core/presentation/src/commonMain/kotlin/app/naviamp/presentation/NaviampCoreConnectController.kt:1186`

A one-way network failure can consequently leave the failed request pending and the UI logically
connected until the independent receive path notices the socket failure. That may be delayed or
may not happen promptly on every network stack.

Recommended change:

- Convert outbound transport exceptions into an explicit session failure.
- Close the authenticated session and enter the existing bounded reconnect policy immediately.
- Retain only requests that the established idempotence policy permits retrying.
- Surface an actionable status while reconnecting.
- Test failure before any bytes are written, during a write, and immediately after a successful
  write but before acknowledgement.

## What Is Good

### Shared-first ownership

The branch follows Naviamp's core-first architecture well. Product policy, Television UI,
capability decisions, pairing state, reconnect behavior, playback authority, command routing,
queue projection, and user-visible Connect state live in shared code. Platform changes are mostly
narrow adapters for legitimate boundaries:

- Android and Desktop DNS-SD discovery and advertising.
- Platform identity keys and secure credential storage.
- JVM TCP and authenticated cipher effects.
- Android configuration and native BASS integration.
- Host composition that injects those effects into shared services.

This provides a credible foundation for phone, Desktop, iOS, and tvOS hosts without requiring each
host to reconstruct Connect behavior.

### Protocol design

The protocol work is deliberate and substantially stronger than a typical first remote-control
implementation. It includes:

- PAKE-based pairing without sending the short code over the wire.
- Durable public identity and proof verification.
- Authenticated encryption bound to session, direction, protocol version, and sequence.
- Strict inbound sequence handling and replay rejection.
- Request IDs and bounded completed-request replay handling.
- Capability negotiation and command-specific capability checks.
- Authoritative target revisions and revision-conflict responses.
- Provider/source identity validation before catalog starts or queue handoff.
- Durable resumption credentials and newest-controller-wins behavior.

The separation between transport, authenticated session, shared session policy, and product
controller is also sound.

### Testing discipline

The branch adds extensive common and platform-adapter coverage, including:

- Wire-model and discovery metadata validation.
- PAKE, identity verification, authenticated channel, and TCP transport tests.
- Pairing, resumption, trust, and controller/target session tests.
- Shared playback destination, command routing, handoff, and snapshot tests.
- Television layout, focus, navigation, queue, detail, settings, and Now Playing policy tests.
- Android discovery, advertising, identity, transport, PAKE, pairing, and cross-device
  instrumentation.

The development notes also record real product-UI validation, process restarts, retained trust,
network interruption, queue persistence, native playback, and newest-controller-wins behavior.
That combination of deterministic shared tests and real-device exercises is a major strength.

### Latest playback-handoff polish

The most recent feature work improves several details coherently:

- Remote queue occurrences preserve the metadata required by the target's Now Playing UI.
- Provider-owned artwork loading remains authenticated on the target.
- Navidrome cache identity excludes rotating token and salt query values.
- Existing authenticated-URL cache entries are lazily promoted to their stable identity.
- Repeat state has one shared mapping across phone, Desktop, and Television.
- Settings remain reachable before a provider connection exists.

The artwork cache work is especially well placed: provider-specific identity is expressed by
`MediaProvider`, generic cache behavior is shared, storage remains in `core:storage`, and hosts
supply only the network-byte effect.

### Planning honesty

The plans distinguish emulator and limited topology success from completed product acceptance.
Physical Google TV, Apple hosts, dual-capability target roles, sleep/wake, MediaSession recovery,
accessibility, resolution coverage, and multiple-source validation remain visible rather than being
implicitly declared complete.

## What Is Lacking or Could Be Better

### Large shared owners need decomposition

Several new production files have become difficult to review and change safely:

- `NaviampTelevisionContent.kt`: approximately 2,404 lines.
- `NaviampTelevisionSettings.kt`: approximately 1,669 lines.
- `NaviampCoreConnectController.kt`: approximately 1,662 lines.
- `NaviampTelevisionAppShell.kt`: approximately 865 lines.

Their placement in Core is correct, but placement alone does not provide maintainability. In
particular, `NaviampCoreConnectController` owns pairing, listener lifetime, discovery, trusted
reconnect, authenticated session replacement, provisioning, playback authority, remote command
waiting, and UI publication.

Recommended decomposition should remain in shared code:

- Pairing/listener lifecycle owner.
- Trusted reconnect/session takeover owner.
- Per-request remote command coordinator.
- Provisioning workflow owner.
- Playback authority and queue-handoff owner.
- Small immutable UI projection layer.

Television UI files can similarly be divided by route or feature while preserving one shared
Television surface and navigation policy.

### Acceptance remains incomplete

The branch is well tested for its current Android phone-to-emulator and Desktop-to-emulator paths,
but the following are still open:

- Android phone to physical Google TV on an unmodified LAN.
- Android phone and Desktop acting as playback targets.
- Android phone-to-phone and Desktop-to-Desktop combinations.
- iPhone/iPad controller and playback-target behavior.
- tvOS target behavior.
- Sleep/wake, process restoration, MediaSession, audio focus, and network transition recovery.
- Product-UI source-mismatch recovery on additional topologies; Android phone -> Android TV emulator
  is now verified for the preview.
- 720p and native 4K visual acceptance.
- Complete repeat-state size, focus, contrast, and accessibility acceptance.
- Representative queue sizes, including payloads near the authenticated-frame limit.

These should stay explicit release gates if the intended release claims general Naviamp Connect
support rather than an Android TV preview.

### Documentation checklist consistency

The product plan records a successful Desktop-to-TV exercise in prose while the corresponding
cross-device checklist remains unchecked. The implementation order also still says to implement
repeat-icon polish after the repeat-icon checklist has been completed.

Recommended change:

- Separate "implemented", "tested on emulator", and "accepted on physical hardware" states.
- Reconcile narrative validation entries with their checklist rows.
- Avoid a single checkbox representing multiple platforms or acceptance levels.

### Localization boundary

Connect lifecycle and status strings are embedded throughout the shared controller. That is
functional today, but it couples state policy to English presentation and will become expensive to
untangle as Connect expands.

Recommended change:

- Publish structured status/error models from the controller.
- Map those models to localized resources in shared UI.
- Keep peer names and other dynamic values as format arguments.

### Artwork cache migration cost and credential-bearing legacy keys

The stable artwork-key migration scans all stored keys on a stable-key miss, then writes a promoted
entry. This is simple and works lazily, but it makes misses proportional to the entire image cache.
It also leaves the legacy authenticated URL entry in storage until ordinary cache eviction, which
means obsolete token/salt-bearing cache keys can persist longer than necessary.

Recommended change:

- Delete a legacy key after successful promotion when it is safe to do so.
- Consider a targeted migration/index strategy if large image caches make full-key scans visible.
- Add a test confirming credential-bearing legacy keys are eventually removed without losing the
  cached bytes.

### Deprecated Compose test API

The JVM suites pass, but many Compose tests use the deprecated first-generation
`runComposeUiTest`. Migrating to the v2 API would reduce warning noise and exercise coroutine
scheduling that more closely resembles production.

This is not a merge blocker for the branch, but it is useful follow-up test infrastructure work.

## Recommended Remaining Order of Work

The correctness blockers, emulator test pass, checklist reconciliation, and Android TV preview
scope decision were completed on 2026-09-04.

1. Split the largest Connect and Television owners without moving behavior out of Core. This is a
   maintainability improvement, not a remaining merge blocker.
2. Complete representative physical Google TV and remaining lifecycle acceptance before publishing
   the Android TV preview.
3. Expand phone/Desktop target and Apple adapters, then complete the wider topology matrix before
   claiming general Naviamp Connect availability.

## Verification Performed During Review

- `git diff --check` passed.
- The worktree was clean before this review document was added.
- Core domain, app, presentation, storage, and UI JVM test suites passed:
  - `:core:domain:jvmTest`
  - `:core:app:jvmTest`
  - `:core:presentation:jvmTest`
  - `:core:storage:jvmTest`
  - `:core:ui:jvmTest`
- A broad `gradlew check` executed 398 tasks before stopping at
  `:platforms:desktop:configureDesktopBassJni` because `cmake` was unavailable in the review
  environment. This was an environment/tooling failure, not a reported compilation or test
  failure.
- iOS native targets were disabled on the Windows review host because the BASS cinterop requires
  an Apple-compatible native toolchain.
- On macOS after the blocker fixes, Android debug and instrumentation APKs built and installed on a
  physical Pixel 10a and the Android TV emulator. Both devices passed the seven-test Connect runtime
  suite covering PAKE, Keystore identity, framed TCP, and DNS-SD lifecycle behavior.
- The physical Pixel 10a and Android TV emulator both passed the full identity-bound pairing test
  through a temporary source-restricted LAN/ADB relay. The encrypted session accepted Play, queue
  handoff, connection provisioning, Internet Radio start, and album start commands. Both crash
  buffers were empty afterward.
- Direct multicast discovery between the physical phone and emulator timed out in both directions;
  advertising itself succeeded. The acceptance harness now lets an explicit host plus the target's
  public identity fingerprint bypass that emulator network limitation without bypassing protocol
  authentication.

## Overall Assessment

This is ambitious, high-quality work with the right architectural center of gravity. The shared
Television implementation is substantial, and Connect already has security, lifecycle, protocol,
and test foundations that are often deferred too long in remote-playback features.

At review time, the branch could not merge without fixing per-request outcome tracking, stale
remote queue-item identity, and outbound write-failure lifecycle handling. Those three blockers are
now resolved and covered in shared tests. The code is in a good position for incremental
decomposition and broader device acceptance rather than fundamental redesign; the remaining
platform combinations, physical Google TV, and lifecycle gates must still be completed for the
intended release scope.
