# Naviamp Connect Product Plan

## Goal

Naviamp Connect makes another Naviamp device feel like the selected audio output, while that device
remains the real playback owner. The controller keeps Naviamp's ordinary browsing, Now Playing, and
queue experience. The playback device fetches and decodes media directly from the configured server,
owns the authoritative queue and clock, displays its own complete Now Playing experience, and is not
dependent on the controller remaining connected.

This is remote playback, not Bluetooth audio forwarding or casting. Phone and Desktop can each be a
controller or a playback device. Television is a playback device only.

## Current Release Scope

This branch is an **Android TV preview candidate**, not general Naviamp Connect availability.
Merging the shared implementation does not claim that the complete cross-platform topology matrix
is accepted. A preview release remains gated by representative physical Google TV validation;
general availability additionally requires the phone/Desktop target, Apple host, recovery, and
cross-platform acceptance rows below.

## Settled Product Decisions

- [x] The selected playback device owns playback, provider reporting, the authoritative queue, and
  the playback clock.
- [x] The controller continues to use its normal browsing, search, detail, Now Playing, and queue
  surfaces.
- [x] The playback device obtains streams directly from the media server; authenticated stream URLs
  are never relayed by the controller.
- [x] Losing or deliberately closing the controller session does not stop playback.
- [x] A newly connected controller wins without a conflict prompt and replaces the previous live
  controller session.
- [x] Trust is durable. **Stop controlling** ends the live session without forgetting or revoking the
  device, and a remembered device reconnects without repeating pairing.
- [x] Television can be controlled but cannot control another playback device.
- [x] Phone and Desktop can both control and be controlled, subject to advertised capabilities.
- [x] A device owns its advertised friendly name. Each controller may also save a local alias without
  changing the other device.
- [x] Display-name priority is local alias, then the device's advertised name, then a generated
  platform-neutral fallback. Names never participate in cryptographic identity or trust.
- [x] Physical settings remain local to the playback device, including its audio output, hardware
  volume integration, storage paths, permissions, and display configuration.

## Seamless Playback Checklist

### Capabilities and connection lifecycle

- [x] Separate stable device capabilities from the controller/target role used by one authenticated
  session, preserving unambiguous command direction and cryptographic transcript binding.
- [x] Carry controller and playback-target device modes in the version-1 wire model and DNS-SD
  metadata, with a playback-target fallback for advertisements from older version-1 builds.
- [x] Replace the shared runtime's fixed phone/Desktop-controller and TV-target lifecycle with
  capability-based operation: phone and Desktop support control plus remote playback, while TV
  supports remote playback only.
- [x] Reuse one durable trust and resumption credential when two dual-capability devices reverse
  controller/playback roles; do not create a second trusted-device record.
- [x] Add one shared selected-playback-device owner used by Android, Desktop, and iOS.
- [x] Make trusted devices automatically reconnect when reachable, with bounded retry and clear
  connected, reconnecting, unavailable, and incompatible states.
- [x] Enforce newest-controller-wins in the shared session owner and notify the displaced controller
  that it returned to local playback mode.
- [x] Keep target playback and queue intact when a controller disconnects, stops controlling, exits,
  changes network, or is displaced.
- [x] Complete shared self-name, local-alias, revoke, and reconnect actions.
- [ ] Add a dedicated shared Connect diagnostics surface beyond the current actionable status text.
- [x] Track command completion per request ID, keeping acknowledgement, protocol rejection,
  timeout, disconnection, and outbound write failure distinct under concurrent out-of-order
  completion.
- [x] Preserve queue occurrence identity through remote Now Playing actions and reconcile stale
  positional reorder operations against a fresh authoritative snapshot.
- [x] Turn outbound write exceptions into immediate session teardown and bounded reconnect, while
  retaining only commands allowed by the shared idempotence policy for retry.

### Starting remote playback

- [x] Selecting a trusted playback device arms remote-output mode without immediately replacing
  either device's queue.
- [x] On the controller's first Play or new playback selection, atomically transfer the controller's
  current queue, selected occurrence, position, Play Next prefix, groups, repeat/shuffle state, and
  portable playback profile, then start playback on the target.
- [x] Use compatible transferred media metadata immediately after source-identity validation so the
  first track can start promptly. The target must create its own stream request and may enrich or
  validate metadata asynchronously; it must never receive a controller-authenticated stream URL.
- [x] If provisioning or source validation is required, complete it before changing playback
  authority and preserve the controller's local queue on failure.
- [x] Dismiss TV pairing/setup overlays once another device has completed setup and taken control,
  especially once playback begins.

### Ordinary controller experience

- [x] Show a persistent, accessible **Playing back on _Device Name_** indicator on the controller's
  Now Playing surface.
- [x] Put **Stop controlling _Device Name_** first in the Now Playing three-dot menu without
  revoking trust; retain the matching action in Settings.
- [x] Expand the indicator into an output selector once multiple remembered playback targets can be
  selected from Now Playing.
- [x] Keep the controller's normal Now Playing navigation and dismissal behavior available while it
  is in remote-output mode.
- [x] Route Play/Pause, Previous/Next, seek, favorite, repeat, shuffle, queue selection, Play Next,
  add-to-queue, reorder, remove, clear, and radio/catalog starts to the selected playback device.
- [x] Let the controller view and edit the authoritative target queue through the ordinary queue UI,
  including swipe/pointer/keyboard removal appropriate to that controller's host.
- [x] Reconcile every target snapshot into the controller without requiring the user to revisit the
  Controllers settings page.
- [ ] Preserve the controller's local browse/navigation state when entering or leaving remote mode.
- [x] Remove **Share connection**, **Send queue**, and **Bring queue here** from the normal workflow.
  Keep Settings > Controllers focused on discovery, trust, naming, reconnect, revoke, and diagnostics.

### Playback-device experience

- [x] Open or reveal the playback device's full Now Playing surface when remote playback starts.
- [ ] Keep its scrubber, lyrics, artwork, queue, transport, and local hardware/media controls live.
- [ ] Apply local actions on the playback device immediately and publish the resulting authoritative
  snapshot back to the controller.
- [ ] Apply the controller's portable playback intent, including gapless/crossfade, repeat, shuffle,
  ReplayGain, and queue behavior, while retaining the playback device's physical-output settings.
- [ ] Define which DSP choices are portable. Default EQ and output-device calibration to target-local
  unless a future portable-profile contract explicitly includes them.
- [ ] Continue playback normally after every controller has disconnected.

### Cross-device acceptance

Validated on 2026-09-02 with the physical Pixel 10a and Android TV emulator through the real
encrypted session: retained trust reconnected after both app updates without pairing again; phone
Play and Pause changed only the TV MediaSession; TV snapshots updated the phone's mini player; and
Play Next from a phone Search result resolved the track on the TV and inserted it into the TV's
authoritative queue. A fresh target stream played through the TV's native BASS engine, and stopping
control from the phone's Now Playing menu closed only the Connect socket while TV playback and the
remembered trust continued. The ADB port bridge remains test-environment routing, so the complete
unmodified-LAN topology stays open below.

Validated the first-Play authority flow on 2026-09-02 with different queues already visible on the
two devices: the armed phone retained its local **Flagpole Sitta** queue while the TV retained its
older **Virgo** queue; pressing Play on the phone transferred the full 38-item phone queue, selected
Flagpole Sitta on the TV, and started real native playback. Subsequent phone Pause and Resume actions
changed the TV MediaSession between paused and playing. This run also exposed and fixed a shared
authenticated outbound-sequence race between target acknowledgements and playback snapshots; the
same handoff/transport run then completed with an empty TV crash buffer.

Extended the recovery pass on 2026-09-03 after restarting both apps with durable trust intact. The
Pixel reauthenticated without pairing, a forced relay interruption moved the target back to its
listening state, and restoring the route triggered the bounded automatic retry successfully. The
TV retained its current track and all 51 persisted queue occurrences through controller restart,
network loss, deliberate **Stop controlling**, and reconnect. Starting the already-trusted Desktop
controller then displaced the Pixel immediately; the Pixel displayed **Another controller took
over this TV**, returned to local-output mode without reconnecting, and Desktop held the only live
target socket. This also exposed and fixed a shared layout defect that hid Settings content when a
provider was disconnected, ensuring Controllers and trusted-device recovery remain accessible.

Extended Desktop-to-TV acceptance on 2026-09-03 through the real product UI. Desktop automatically
reconnected through retained trust, first Play transferred its queue and started target-owned BASS
playback, Pause/Resume, drag seek, shuffle, Repeat All, and queue removal synchronized, and **Stop
controlling** detached Desktop while TV playback continued. Cover-art handoff now carries complete
track identity and resolves artwork locally on the target; Navidrome artwork cache identity excludes
rotating token/salt parameters and lazily promotes older authenticated-URL entries, so a renewed
target session can reuse persisted covers. The TV
emulator rendered the transferred track's cover and artwork-derived palette with its Navidrome LAN
route deliberately unavailable. Physical Google TV and the remaining topology/recovery matrix stay
open.

Remote playback activation now routes through the shared target command executor into the shared
navigation owner. A successful playing handoff, catalog/radio start, queue selection, Play, resumed
toggle, Previous, or Next reveals the playback device's ordinary full Now Playing surface; Pause,
failed starts, and queue-only edits leave its current navigation untouched. Shared policy tests and
the Android debug build pass. Live revalidation exposed Android DNS-SD resolving the emulator to an
IPv6 link-local address rather than its bridged IPv4 address. The shared debug-only endpoint adapter
now recognizes scoped and unscoped link-local addresses; the physical Pixel then automatically
reauthenticated its retained trust through the restricted emulator relay and the product UI reported
the TV as connected without another pairing code.

A controller with no active local provider connection now keeps a connected target's remote mini
player and full Now Playing surface available. Previously, the connection form hid those controls
even though the authenticated target session was live. The shared UI policy has JVM coverage. A
physical Pixel 10a-to-TV-emulator pass then transferred **No Division**, started target-owned
playback, and automatically revealed the TV's full Now Playing surface. The same pass confirmed
that transferred artwork identity is retained when the TV already knows a sparser copy of the
track. With the emulator's stale system proxy repaired, the TV used its transferred Navidrome
session to fetch the cover directly from the server and applied its artwork-derived palette.
The emulator-only HTTPS relay was then corrected to preserve TLS data pipelined with CONNECT and to
handle half-closes without truncating the opposite direction. On a fresh queued track, target-owned
BASS playback remained active past 85 seconds, the TV scrubber advanced from 0:20 to 1:25, Pause
held the position, and Resume advanced it to 1:40. This closes the emulator sustained-streaming
blocker without introducing a production routing workaround.

Target snapshot publication now ignores progress-only ticks and sends a full authoritative queue
only for structural playback changes: track/station, queue, play state, repeat, or shuffle. This
prevents a controller from falling minutes behind while decoding redundant full-queue snapshots;
the playback device remains the owner of the live scrubber. Common tests cover the policy. In the
physical Pixel 10a-to-TV-emulator acceptance pass, phone Pause/Resume controlled the TV, phone Next
changed both devices to **Vidmahe** within five seconds, and TV-local Next changed both devices to
**I Alone** within five seconds while target playback continued to advance.

- [x] Android phone -> Android TV emulator through the product UI, using only the test-build route
  override required to bridge the emulator's private NAT address.
- [ ] Android phone -> physical Google TV, including direct LAN, audio, MediaSession, sleep/wake, and
  process recovery.
- [x] Desktop -> Android TV emulator.
- [ ] Desktop -> physical Google TV.
- [ ] Android phone <-> Android phone.
- [ ] Desktop <-> Android phone.
- [ ] Desktop <-> Desktop across macOS, Windows, and Linux where available.
- [ ] iPhone/iPad <-> Android phone and Desktop.
- [ ] Phone/Desktop -> tvOS.
- [ ] Verify that TV never advertises or enters controller mode.
- [ ] For every topology, verify first pairing, remembered reconnect, newest-controller-wins,
  controller detachment, target restart, controller restart, interrupted command recovery, queue
  editing, source mismatch, and continued target playback.

## Friendly Device Names

- [x] Add a shared self-name setting that is advertised during discovery and authenticated sessions.
- [x] Generate a stable, useful fallback without exposing sensitive account or network information.
- [x] Add a per-trust-record local alias that overrides only the current device's display of that
  peer.
- [x] Propagate self-name changes on reconnect while preserving local aliases.
- [x] Disambiguate duplicate visible names in selection UI without changing cryptographic identity.
- [x] Cover blank, long, Unicode, duplicate, renamed, revoked, and legacy unnamed devices in common
  tests.

## Fresh-Device Setup Boundary

Fresh setup is part of Connect because it is the first-use path into the trusted-device system.
Ongoing multi-device synchronization and shared history are follow-up work recorded in
[`v2-follow-up-ideas.md`](v2-follow-up-ideas.md#trusted-device-settings-sync-and-shared-listening-activity).

- [ ] Offer **Set up from another Naviamp device** on a fresh phone or Desktop install and on an
  unconfigured TV.
- [ ] Pair and approve through the same trusted-device flow, then transfer the selected provider
  connection and an initial portable-settings snapshot.
- [ ] Encrypt credentials specifically for the receiving trusted device and persist them only
  through that host's secure-secret storage. Credentials must remain separate from ordinary
  settings synchronization.
- [ ] Let the user review which connection and libraries will be installed before committing.
- [ ] Validate the provider connection on the recipient and leave its prior state untouched on
  failure.
- [ ] Make the resulting trust immediately usable for remote playback without a second setup flow.

## Shared Playback-Control Polish

These items are independent of the remote-playback lifecycle but belong in the same tracked work
because every host must consume one shared visual/state decision.

### Television waveform and scrubber

- [x] Remove the fixed blue TV progress color and use the same shared album-art-derived accent-color
  decision used by phone and Desktop, including the same fallback and contrast rules.
- [x] Replace the visibly separated large-display bars with a smooth waveform/progress presentation,
  such as an interpolated connected path or tightly sampled ribbon, while retaining played/unplayed
  state, seek feedback, focus, and accessibility semantics.
- [ ] Verify the result at 720p, 1080p, and native 4K with sparse, dense, missing, and changing
  waveform data and light/dark album artwork. The 1080p dense-waveform pass is complete.
- [ ] Add shared rendering/state tests where practical and visual acceptance captures for TV.

### Repeat icons on every device

- [x] Define one shared repeat icon set consumed by phone, Desktop, and Television.
- [x] Use the repeat-loop symbol with **A** in the center for Repeat All.
- [x] Use the repeat-loop symbol with **1** in the center for Repeat One, based on the current liked TV
  Repeat One treatment.
- [x] Remove the current TV **ALL** word treatment.
- [ ] Verify off, Repeat All, and Repeat One states at every supported control size, including TV
  focus/selected states and accessibility labels.

## Implementation Order

- [x] 1. Generalize protocol and shared session roles into controller/playback capabilities.
- [x] 2. Add the shared selected-playback-device and remote-output state owners.
- [x] 3. Route ordinary Now Playing, queue, and catalog actions through those owners, including
  transport, authoritative remote Now Playing, queue clear/edit/reorder, catalog and radio starts,
  and browse-time grouped queue additions.
- [x] 4. Implement the atomic first-play queue/profile transfer and fast first-track start.
- [x] 5. Add the Now Playing device banner, output selector, and streamlined Controllers settings.
- [x] 6. Add friendly self-names and controller-local aliases.
- [ ] 7. Complete the Android phone/TV product-UI acceptance and recovery matrix.
- [ ] 8. Add phone and Desktop target adapters, then Apple host adapters, without moving product
  policy out of Core.
- [ ] 9. Complete remaining waveform and repeat-icon visual/accessibility acceptance.
- [ ] 10. Add fresh-device setup from a trusted peer, then evaluate ongoing sync/history work.
