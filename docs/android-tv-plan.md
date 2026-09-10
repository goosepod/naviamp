# Naviamp TV Plan

September 10 Connect setup update: the empty-TV flow now completes pairing, validated source
provisioning, and portable settings after code entry, with a one-time password prompt only when
needed. The [follow-up acceptance record](ANDROID_TV_FOLLOW_UP.md#initial-connect-setup) contains
emulator results and the revised session-scoped consent boundary. Earlier approval-flow entries
below are historical; later source replacement still requires target approval.

## Purpose

Build a complete, standalone Naviamp Television experience shared by Android TV, Google TV, and a
future Apple TV/tvOS host. Android TV is the first implementation and acceptance target, not the
owner of the product interface. The TV client connects, browses, searches, plays, restores its
queue, manages essential settings, and reports playback without requiring a phone or computer.
Paired Naviamp clients add convenient remote control but are never required to finish setup or
operate the TV app.

This plan is the active record for product decisions, architecture, milestones, test evidence, and
open questions. Update it as implementation changes; do not preserve obsolete branch-development
states as if they were shipped behavior.

## Product Principles

- The TV is a complete playback owner, not a display attached to another Naviamp process.
- The TV experience is intentionally quieter than phone and Desktop, not artificially incapable.
- Connection creation, editing, deletion, source switching, playback, queue control, recovery, and
  diagnostics remain available with only the TV remote and platform text-entry UI.
- Phone and Desktop controllers browse with their normal full UI while targeting another Naviamp
  playback device. Phone and Desktop may also be playback targets; Television is target-only.
  Closing a controller does not stop target playback.
- When the TV owns playback, only the TV reports its playback lifecycle to the provider.
- Lyrics are the primary living-room presentation enhancement. A high-performance visualizer is a
  later TV playback milestone rather than a prerequisite for the usable browsing and playback UI.
- 1920x1080 and 3840x2160 are required display targets. Layout uses logical density-aware sizing so
  ten-foot typography and controls remain consistent while artwork renders at native sharpness.
- Treat 1280x720, 1920x1080, and 3840x2160 as explicit 16:9 Television acceptance classes. Core
  owns named layout metrics and breakpoint selection; hosts report the unavoidable window/display
  facts. Android density may map 1080p and 4K to the same logical Compose viewport, so placement
  remains consistent while density-aware artwork decoding and native rendering preserve physical
  sharpness.
- Product policy, state, navigation, setup behavior, remote-session semantics, and TV UI live in
  shared Kotlin. The Television surface is both provider-neutral and platform-neutral: it consumes
  shared models and capability flags and never branches on Navidrome, Jellyfin, Android TV, Google
  TV, or tvOS identity to define product behavior.
- Android TV/Google TV and tvOS hosts are thin adapters limited to unavoidable launcher, remote
  input, media-session, secure-storage, network-discovery, audio, and lifecycle boundaries.

## Initial TV Surface

### Primary navigation

Keep the always-visible destinations small:

1. Home
2. Library
3. Search

When playback exists, Now Playing appears as a conditional top-navigation destination while the
persistent mini player remains a non-focusable status strip. Playlists lives within Library, and
Settings uses a compact gear entry rather than another full-width destination. Radio, mixes,
details, and collection pages remain reachable from Home and Library content without becoming
permanent top-level chrome. Downloads are not an initial TV destination.

### Home

TV Home is a dedicated shared-Core presentation rather than the standard phone/Desktop Home placed
in a landscape shell. Every visible Home section is a horizontal carousel with large,
remote-friendly cards; TV does not reproduce the standard surface's Grid or List section layouts.
This is the consistent ten-foot interaction model used across the entire Home screen.

The TV Home screen includes every available section exposed by the standard Home experience except
Mix Builders, which is intentionally not implemented for TV. Sections follow the user's shared
saved order and can be shown, hidden, and reordered from a dedicated Home settings page. TV uses an
eye control for visibility and a remote-friendly move mode; phone and Desktop expose the same
settings through drag interactions.

Core owns the section catalog, visibility, ordering, TV focus behavior, and item limits. The
standard surface's saved layout choice remains untouched and continues to apply to phone and
Desktop only. Each visible TV section is rendered as a carousel, contains at most 30 items, and
honors a smaller shared per-section limit.

### Now Playing and lyrics

- Default to large artwork, title, artist, progress, essential transport controls, and two or three
  lyric lines when lyrics exist.
- Allow a lyrics-first full-screen mode with substantially larger text, current-line emphasis, and
  word-level highlighting from the existing shared lyric model.
- When lyrics are unavailable, use the space for queue context or artwork without placeholder
  noise.
- Use two explicit presentation states. Interactive mode shows transport and secondary actions;
  after about five seconds without remote interaction, listening mode leaves only artwork, track
  context, lyrics when selected, and the waveform/progress presentation.
- The top-bar Now Playing preview is always non-interactive listening mode. Returning to it from
  full screen must not leave a transport row visible without an inactivity timer.
- The first ordinary D-pad press in listening mode restores the controls without also invoking a
  command. Restore the last sensible focus target when possible, with Play/Pause as the safe
  fallback. Hardware media commands remain immediate.
- Reset the inactivity timer for remote interaction and suspend it while an interaction that needs
  sustained focus is active, including scrubbing and open menus.
- Reduce static Now Playing exposure during prolonged listening: after two minutes in listening
  mode, dim the full presentation to 55% and shift its content through eight positions bounded by
  8dp each minute, using two-second transitions. After ten minutes, dim to 35%. Keep shifts inside
  existing content margins. Ordinary remote wake restores brightness and position immediately
  without also issuing playback actions. Open queue interactions suspend the timer; track and
  progress updates do not restart it. The navigation preview uses the same passive policy.
- Validate OLED mitigation with automated policy tests and representative renders; this is
  application-level mitigation, not a guarantee against panel burn-in.

### Focus and selection language

All Television surfaces use one shared focus treatment rather than screen-specific borders:

- Only the artwork or artist image on focused media cards scales approximately 5–7 percent over
  120–160 milliseconds. Labels and card layout remain stationary. Carousels and grids reserve
  overflow space so edge artwork can enlarge without clipping.
- Focused media artwork uses a single shape-matched blue halo plus scale: circular for
  artists and rounded-square for albums. Buttons use filled or neutral raised focus without a blue
  outline or displaced shadow, preventing the nested-border artifact on blue controls.
- Focused content is raised above neighboring content so its position remains unambiguous.
- Transient remote focus and persistent state are distinct. Focus uses scale, elevation, or the
  control's filled focus state;
  selected values such as Lyrics, Repeat, Shuffle, and settings choices retain a quieter persistent
  mark when focus moves away.
- Disabled actions remain visually legible but cannot receive focus.

### Search interaction

- Focusing Search in the top navigation activates the destination without stealing focus or opening
  the keyboard, so the user can continue across the navigation bar. Down enters the query field and
  opens the platform keyboard; returning from results restores the query for refinement.
- Search results may update while the user types. The IME Search action submits the current query,
  dismisses the platform keyboard, and retains logical focus on the query field so the results are
  immediately available to the remote.
- Back dismisses an open keyboard while retaining the query; after submission, Down enters the
  first result. Back from results returns to the query field, and a subsequent Back returns to the
  Search top-navigation entry.
- Results use the shared Television grid and deterministic row-major D-pad navigation.

### Internet Radio Stations

- Add a dedicated remote-friendly Internet Radio Stations page reachable from Library without
  adding another permanent top-navigation destination. The recent Internet Radio Home rail remains
  a shortcut and is not a substitute for the complete station collection.
- Reuse the shared provider-neutral station model and actions. TV users must be able to browse and
  play every saved station, refresh the collection, start station radio where supported, and open
  the existing contextual actions.
- Adding and editing a station must remain possible with the TV system keyboard, including the name,
  stream URL, and homepage fields supported by the shared station editor. Deletion requires
  confirmation and predictable Back/focus restoration.
- Phone/Desktop controllers may browse Internet Radio locally and direct station playback to a
  paired TV through Naviamp Connect. The TV remains the stream and reporting owner.

### Television settings presentation

- Settings opens as a right-side sheet over a dimmed version of the current destination, occupying
  roughly 40 percent of the screen instead of navigating to the dense standard settings page.
- The first level contains only TV-relevant groups: Sources, Home, Playback, Lyrics, Controllers,
  Display, Diagnostics, and About. Each row has an icon, title, current value, and disclosure
  indicator. Home exposes the shared section order and visibility controls.
  Capability-dependent groups such as Controllers remain hidden until their shared feature has real
  state and actions; do not ship dead settings pages.
- Selecting a group replaces the sheet contents with that group's rows. Back returns one level and
  then dismisses the sheet, restoring focus to the Settings entry.
- Choice pages use a persistent checkmark for the selected value and the common Television focus
  treatment for the currently focused row.
- File import, pointer/gesture options, Desktop shortcuts, and other controls without a meaningful
  ten-foot workflow remain absent.

### Visualizer direction

A Television visualizer may be added after the browsing, focus, settings, and listening-mode work
is stable. It must not make the shared Compose UI responsible for drawing a full-resolution effect
frame by frame.

- Core owns visualizer selection, audio-analysis data, lifecycle policy, fallback state, settings,
  and the relationship between the visualization and Now Playing overlays.
- Each host supplies only a narrow native rendering surface and graphics implementation: Vulkan
  with an OpenGL ES fallback on Android TV, and Metal on tvOS.
- The renderer consumes the existing shared waveform/FFT data contracts where suitable. Reuse the
  existing Naviamp shader and visualizer definitions instead of creating a TV-only signal path.
- Render resolution is adaptive. Capable devices may render at native 4K; constrained devices can
  render internally at 1080p and upscale while UI text and artwork remain native-resolution.
- Failure to initialize the preferred graphics backend falls back cleanly to the alternate backend
  or the normal artwork presentation. Renderer diagnostics must never interrupt playback.

### Navigation refinements

- Top-level destinations remain provider-neutral. Any route-on-focus behavior must be deliberate,
  tested, and avoid reloading content while merely crossing the navigation bar.
- Back always closes the most local transient layer first: contextual menu, child settings page,
  settings sheet, result-to-query transition, detail page, then primary-destination policy.

### Standalone setup

The first-run screen offers two equal, complete paths:

1. **Set up on this TV** supports provider, server URL, username, password, connection testing,
   provider-specific fields, library selection, connection naming, and clear recovery from
   validation or network errors through the TV system keyboard.
2. **Set up with another Naviamp device** places the TV in an explicitly time-bounded pairing mode,
   advertises it on the local network, and displays a short code. A phone or Desktop Naviamp client
   discovers the TV, asks the user to enter the displayed code, establishes an authenticated
   encrypted session, and can transfer one selected provider connection plus compatible settings.

The TV validates transferred connection information before accepting setup, stores it locally using
the same secure credential facilities as manual setup, and remains a complete standalone player
after the controller disconnects. Assisted setup never turns the TV into a credential proxy and
never makes the phone or Desktop app necessary for later startup or playback.

### Reduced settings

The TV settings information architecture is:

- Sources
- Home
- Playback
- Lyrics
- Controllers
- Display
- Diagnostics
- About

Settings Sync may supply compatible preferences, but required TV settings remain locally editable.
Bulk cache/download management, global keyboard shortcuts, update-channel controls, swipe actions,
and other pointer/touch-specific preferences do not appear in the TV surface.

## Naviamp Connect Direction

Naviamp Connect is the provider-neutral local playback-target system for phone, Desktop, and future
TV/headless clients. It is more important than Google Cast because it can preserve Naviamp queue
groups, playback profiles, errors, lyrics, and reporting behavior on every Naviamp controller.
The canonical device-neutral product behavior and implementation checklist now live in
[`naviamp-connect-product-plan.md`](naviamp-connect-product-plan.md). This TV plan retains the
Television-specific requirements and evidence.

### Local-network boundary and interoperability

- Naviamp Connect is local-network only. A controller and target must be reachable on the same LAN;
  either device may use Wi-Fi or Ethernet. No Naviamp cloud relay is required or planned for the
  initial protocol.
- Discovery uses one DNS-SD/mDNS service type, provisionally `_naviamp-connect._tcp`. Ordinary
  local-link discovery does not cross routers. Guest-network isolation, VLAN policy, or blocked
  multicast may prevent discovery even when devices appear to use the same Wi-Fi name; the UI must
  distinguish permission denial, no targets found, and a discovered target that cannot be reached.
- The wire protocol is platform-neutral and versioned. Phone and Desktop advertise independent
  controller and playback-target capabilities; Television advertises playback-target capability
  only.
- Shared Core owns discovery results, capabilities, pairing state, trust state, commands, snapshots,
  reconciliation, disconnect behavior, and user-facing status. Android NSD, Apple Bonjour/Network
  framework, and Desktop DNS-SD implementations are narrow discovery and socket effects only.
- Apple hosts declare the Naviamp Bonjour service and explain local-network access. Android hosts
  handle the applicable local-network permission or system NSD picker as target-SDK requirements
  evolve. Permission denial must leave manual TV setup and local playback functional.

Reference constraints:

- Android local-network protection: https://developer.android.com/privacy-and-security/local-network-permission
- Apple local-network privacy: https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy
- Apple Bonjour: https://developer.apple.com/bonjour/index.html

### Ownership

- The selected target owns the authoritative queue, playback clock, provider session, playback
  engine, and reporting lifecycle.
- Controllers send typed commands and consume target state snapshots.
- Target snapshots reconcile every attached controller after local TV-remote actions or another
  controller's command.
- Controller loss does not stop playback. Target loss produces a visible disconnected state; it
  does not silently begin duplicate local playback.
- A controller exposes a prominent **Stop controlling** action. It closes only the live control
  session, preserves durable trust for later reconnect, and leaves the target queue, playback,
  reporting, and standalone operation unchanged.
- Handoff transfers queue occurrences, group/priority state, current occurrence, position, repeat,
  shuffle, and resolved playback-profile intent before changing authority.
- Phone and Desktop browse through their normal full Naviamp interface while another Naviamp device
  is the selected playback target. Browse state can remain local, but playback and queue intents are
  executed by the target and reconciled from its authoritative snapshots.
- Remote control includes play/pause, previous/next, seeking, favorite, repeat, shuffle, queue
  selection and editing, radio/album/playlist playback, and compatible playback preferences. TV
  display selection may also expose explicit commands such as showing Now Playing, Lyrics, or Queue.
- Physical-device settings remain target-local: audio output, storage paths and sizes, OS
  permissions, display calibration, and other settings that cannot safely transfer between devices.

### Discovery, pairing, and trust

1. A TV advertises a minimal unpaired service only while its pairing screen is active. Discovery
   metadata contains a random instance identifier, display label, protocol range, capability flags,
   port, and public-key or certificate fingerprint—never provider credentials, usernames, library
   names, or authenticated stream URLs.
2. The controller shows discovered TVs. Selecting one causes the TV to display a fresh short code;
   the user enters that code on the controller.
3. The code bootstraps a reviewed password-authenticated key exchange rather than being transmitted
   or hashed as an ordinary network password. Android and Desktop share the Bouncy Castle J-PAKE
   adapter with explicit mutual key confirmation and transcript-bound HKDF derivation. Apple still
   needs an interoperable reviewed Kotlin/Native implementation; Naviamp does not implement the
   cryptographic primitive itself.
4. Successful pairing creates long-lived device identities and trust records. Private key material
   and session credentials are stored through platform Keystore, Keychain, or Desktop secure-value
   adapters. The TV Controllers page lists, renames, and revokes trusted devices. A remembered
   target provides a direct authenticated reconnect action that reuses its existing trust record;
   reconnecting never creates another trusted-device row or repeats initial connection setup.
5. Pairing codes expire, attempts are rate-limited, every new controller requires visible TV
   approval, messages are encrypted and authenticated, and sessions use message sequence numbers or
   equivalent replay protection.

Reference:

- J-PAKE: https://www.rfc-editor.org/rfc/rfc8236

### Assisted connection provisioning

- After pairing, a controller may send an encrypted provisioning envelope containing the provider
  type, canonical server URL, account identity, selected libraries/music folders, required TLS
  options, authentication secret, connection name, and explicitly portable settings.
- The target validates the provider connection and chosen libraries before committing it. Failure
  leaves the TV pairing screen active with a recoverable error and does not create a partial source.
- The transferred credential is persisted locally under the TV's credential protection. It is not
  retained in logs, discovery metadata, crash diagnostics, or ordinary settings sync.
- Only portable settings transfer. Interface presentation and compatible playback preferences may
  transfer; storage locations, cache budgets, output-device selection, permissions, Desktop
  shortcuts, and gesture settings do not.

### Connection identity and queue transfer

- Queue transfer is allowed only when controller and target share a compatible source identity.
  Core defines that identity from provider type, canonical server origin, account identity, and
  selected library identifiers. Connection display names, local database IDs, and credential
  rotation do not by themselves make two otherwise identical sources different.
- If identities match, the controller sends provider media identifiers, compatible media metadata,
  and Naviamp queue state, never authenticated stream URLs. The target creates stream requests with
  its own provider session and may enrich or validate transferred metadata asynchronously so queue
  handoff does not unnecessarily delay the first track.
- A handoff preserves duplicate occurrences, queue-group and Play Next priority, current occurrence,
  playback position, repeat, shuffle, and resolved playback-profile intent. Authority changes only
  after the target acknowledges a valid complete handoff; failure leaves source playback unchanged.
- If identities differ, the controller does not silently push the queue. It may offer to provision
  the required connection on the TV after explicit user confirmation, then retry the handoff after
  the target verifies the new source.
- A future source-transfer path may support a provider other than Navidrome, but the first acceptance
  path is a shared Navidrome connection on Android phone and Android TV.

### Protocol and transport shape

- Do not serialize `NaviampCoreCommand` or the complete application state. Define a small,
  serializable, versioned Naviamp Connect envelope with stable protocol commands, target snapshots,
  request IDs, capability negotiation, error codes, acknowledgements, and compatibility rules.
- The existing shared external-playback projection is a useful source for Now Playing and basic
  transport behavior, but Connect owns a dedicated snapshot that preserves Naviamp occurrence IDs,
  queue groups, target identity, capabilities, and revision numbers.
- A persistent encrypted full-duplex session such as WebSocket is appropriate for commands and
  authoritative snapshot updates. The shared protocol and session state machine do not depend on a
  particular WebSocket/server library; each host provides only the unavoidable listener, socket,
  discovery, and secure-key effects.
- Every target state mutation advances a revision. Controllers apply authoritative snapshots in
  revision order, retry only idempotent requests, and reconcile after reconnect instead of assuming
  that a command succeeded.

The version 1 protocol envelope, capability negotiation, connection identity, authority model,
pairing/session state machines, Android/Desktop J-PAKE, authenticated transport, durable Android
identity/credentials, and production Android networking are implemented and covered by common,
JVM, device, and live product-UI tests. Apple still requires an interoperable reviewed PAKE and its
native networking/key-lifecycle adapters. Current details are recorded in
`docs/naviamp-connect-protocol.md`.

## Architecture Placement

### Shared Core/UI

- Application-surface model and TV surface policy
- Provider-neutral Television presentation driven by shared capability contracts
- TV navigation destinations and back behavior
- TV Home composition and item limits
- TV connection/setup presentation and validation
- TV Now Playing and lyrics presentation
- Reduced settings composition
- Playback-target model, pairing state machine, commands, snapshots, handoff, reconciliation, and
  reporting ownership
- Common tests for all of the above

### Android-only boundaries

- `Configuration.UI_MODE_TYPE_TELEVISION` detection and TV launcher manifest metadata
- D-pad/key translation that cannot be expressed through shared Compose focus APIs
- Android TV `MediaSession`, home-screen Now Playing card, audio focus, and TV process lifecycle
- Android Keystore credential/device-key protection
- Android network discovery APIs
- Cast Connect, if added after native TV and Naviamp Connect are stable
- HDMI/CEC/output-route observations and BASS Android ABI loading

No Android TV controller, provider mapping, queue owner, retry scheduler, product settings policy,
or independent navigation graph may be introduced in the Android host.

### tvOS-only boundaries

- Apple TV application lifecycle, launcher metadata, and Top Shelf integration
- Siri Remote events that cannot be represented by shared focus and action contracts
- tvOS Now Playing/media-command, audio-session, Keychain, and network-discovery APIs
- Native audio ABI loading and tvOS output-route observations

No tvOS controller, provider mapping, queue owner, retry scheduler, product settings policy, or
independent navigation graph may be introduced in the Apple TV host.

## Delivery Milestones

### Current implementation gaps

- The dedicated Television shell, Home and Home collection pages, Library, Search, Playlists,
  Settings, and artist, album, and playlist detail pages are in place. No M1 Television route uses
  the standard phone/Desktop content fallback.
- The dedicated Internet Radio Stations collection and shared editor are available from Library,
  including remote-friendly play, refresh, create, edit, delete, focus, and Back behavior.
- The dedicated full-screen Now Playing, listening-mode transition, queue panel, and smoothly
  scrolling line-synced lyrics and idle dimming/pixel shifting are implemented. Word-level karaoke
  highlighting and other physical TV acceptance remain outstanding parts of the complete lyrics direction.
- Direct shared Compose coverage now includes Home collections, Library navigation/detail return,
  Now Playing focus, settings/localization, screen protection, Search submission/re-entry, Internet
  Radio editing, and lyrics timing/scrolling/remote controls. See the
  [UI workflow and artwork contrast audit](android-tv-ui-acceptance.md) for direct screen coverage.
- The Leanback banner and packaged native ABI inventory are implemented and audited. Signed
  distribution and store-console asset/requirements review remain open.

### M0: Emulator foundation

- [x] Create `feature/android-tv` from `main`.
- [x] Confirm the Android TV emulator is reachable: Android 16/API 36, ARM64, 1920x1080.
- [x] Confirm Naviamp packages the complete Android BASS inventory for `arm64-v8a` and emulator
  ABIs.
- [x] Add the shared application-surface model and tested TV navigation policy.
- [x] Add TV launcher metadata and select the shared TV surface from the Android TV configuration.
- [x] Build, install, and launch Naviamp on the emulator.

### M1: Standalone TV shell

- [x] Provide remote-friendly top navigation and focus states.
- [x] Complete local connection setup using the system keyboard.
- [x] Provide Home, Library, Playlists, Search, details, and essential Settings.
- [x] Verify Navidrome connection plus populated Home, Library, and Search browsing on both 1080p
  and native-4K emulator configurations.
- [x] Add the dedicated TV Internet Radio Stations browse, play, add/edit, and delete workflow.
- [x] Replace the remaining standard Home collection-page fallback with a dedicated TV page.
- [x] Verify switching between multiple saved sources on the emulator, including unavailable-source
  offline restoration and switching back to the original source.

### M2: TV playback experience

- [x] Verify BASS playback, transport controls, queue playback, artwork, waveform progress, and
  line-synced lyrics on the Android TV emulator.
- [x] Complete the dedicated shared TV Now Playing, listening-mode transition, Queue panel, and
  line-synced Lyrics presentation.
- [x] Implement and exercise TV queue selection/reordering, repeat/shuffle/favorite, gapless and
  crossfade exclusivity, ReplayGain choices, and sample-rate matching controls.
- [x] Add shared idle dimming and bounded pixel shifting to TV Now Playing; OLED validation uses automated tests and renders; no OLED device is available.
- [ ] Add word-level karaoke highlighting as a post-preview lyrics enhancement.
- [ ] Verify audio focus, background-service retention, process restoration, and `MediaSession`
  behavior on physical Google TV hardware.
- [x] Verify native gapless/crossfade, provider ReplayGain application and exactly-once listen
  reporting in the three-track emulator runs documented in [the interruption audit](android-tv-interruption-audit.md).
- [x] Verify saved playback profiles and longer background playback with repeated interruptions
  and resource sampling in [the soak test](android-tv-soak.md).
- [ ] Verify acoustic transitions and ReplayGain output levels on physical TV/audio hardware.
- [x] Cover Connect session, target pairing, retry policy, and interrupted-route recovery in shared
  deterministic tests.
- [x] Exercise Android native process termination/restoration, background MediaSession, finite
  buffer exhaustion/retry, and live-station reconnect on a disposable TV emulator; see the
  [lifecycle fixture](android-tv-lifecycle-fixture.md) and [interruption audit](android-tv-interruption-audit.md).
- [ ] Complete the physical-TV lifecycle and real network-transition pass.

#### Shared playback-control visual polish

- [x] Use the standard shared album-art-derived accent-color decision for TV waveform/progress
  instead of a fixed blue.
- [x] Smooth the TV waveform/progress geometry so bar gaps are not conspicuous at 1080p or 4K while
  preserving seeking, progress, focus, and accessibility behavior.
- [x] Share the repeat-state icon set across phone, Desktop, and TV: Repeat All uses the repeat glyph
  with **A**, and Repeat One uses the repeat glyph with **1**. Remove the TV **ALL** treatment.
- [x] Capture and exercise waveform/repeat controls at 720p and native 4K, including selected/focus
  state and base-surface text contrast; evidence is in the September 9 progress log.
- [x] Complete automated artwork contrast checks across synthetic bright/color/pattern extremes at
  720p and 4K; see the [contrast audit](android-tv-ui-acceptance.md).
- [ ] Complete physical-TV accessibility acceptance.
  Detailed criteria are tracked in
  [`naviamp-connect-product-plan.md`](naviamp-connect-product-plan.md#shared-playback-control-polish).

### M3: Naviamp Connect

- [x] Approve the Android/JVM version-1 envelope, capability negotiation, connection identity,
  pairing threat model, cryptographic dependency, key lifecycle, replay protection, and authority
  design for the Android TV preview. The decision and accepted limitations are recorded in
  [`naviamp-connect-protocol.md`](naviamp-connect-protocol.md#version-1-security-review--2026-09-04);
  Apple remains subject to a separate implementation review.
- [x] Implement initial shared target/controller state machines and fake-transport tests.
- [x] Implement the shared Android/Desktop J-PAKE adapter, explicit mutual key confirmation,
  transcript-bound session-key derivation, and failure/destruction tests.
- [x] Complete Android TV/phone pairing mode, explicit approval, discovery/code entry, expiring
  short codes, authenticated pairing, durable trust, self-name/local-alias editing, revoke, and
  automatic reconnect.
- [x] Exercise Desktop controller pairing, retained reconnect, control, and detachment against the
  Android TV emulator through the product UI.
- [x] Implement localized pairing diagnostics and permission-recovery presentation with shared
  tests and native Settings adapter coverage; see the September 9 progress log.
- [ ] Exercise permission denial, Settings return and retry on physical Android/TV hardware.
- [ ] Add Apple pairing-management host wiring before general availability.
- [x] Keep Android TV, Android phone, and Desktop discovery, socket, and secure-key effects as narrow
  host adapters with protocol behavior in Core.
- [ ] Add the equivalent narrow iOS and tvOS adapters before general availability.
- [x] Add the first-run assisted connection-provisioning transaction and portable-settings filter.
- [x] Add phone/Desktop playback-target selection and authoritative remote Now Playing snapshots.
  The Core projection from canonical playback/queue state into revisioned Connect snapshots is
  implemented, the post-pair session is retained, and a connected target is projected into the
  existing shared phone/Desktop Now Playing surface. Now Playing exposes the local device and every
  remembered compatible target, including direct detach/reconnect and selected-output state.
- [x] Add remote transport, seeking, favorite, repeat, shuffle, Internet Radio, and queue control.
  Transport, seeking, repeat, shuffle, queue selection, upcoming reordering, and upcoming removal
  now execute through Core. Absolute favorite changes and the capability-limited shared controller
  surface are also implemented. Catalog and Internet Radio playback selections made while browsing
  on the controller now resolve and play through the target's own provider session.
- [x] Add same-source validation plus atomic local-to-TV and TV-to-local queue handoff. Canonical
  provider/server/account/library validation applies in both directions, preserving duplicate
  order, current position, Play Next priority, repeat, shuffle, groups, and playback profiles.
- [x] Verify Android phone to Android TV emulator through the test-build route required by the
  emulator network boundary.
- [x] Verify macOS Desktop to Android TV emulator through the test-build route.
- [ ] Verify Android phone and Desktop against physical Android TV before the preview.
- [ ] Verify Android/iPhone/Desktop controllers and playback targets across supported non-TV and
  tvOS topologies before general availability.

#### Branch review issues before merge

- [x] Preserve complete Jellyfin artist libraries in the repository-backed refresh path. Core now
  consumes validated provider pages to completion before replacing the repository index.
- [x] Bound and cancel accepted Connect sockets during pairing. Core owns deadlines and closure for
  both the initial hello and approved authentication handshake, resumes accepting after incomplete
  requests, and has coverage for stalled, cancelled, and non-cooperative clients.
- [x] Schedule Connect advertisement, pairing-code, and discovery expiry from Core with
  deterministic state/UI transitions and virtual-time tests.
- [x] Clear stale cover art when a replacement URL fails while preserving the intentional grace
  period for a brief empty transition state.
- [x] Keep trusted-device rows truthful. Remembered targets with a secure resumption credential are
  actionable and reconnect through the shared authenticated session flow without creating another
  trust record; records that predate that credential remain non-interactive.
- [x] Add a prominent shared **Stop controlling** action that closes only the live controller
  session, preserves remembered trust, and leaves TV playback and queue state untouched.
- [x] Resolve the apparent Android-to-Android pairing authentication regression on the physical
  Pixel 10a and Android TV emulator. The protocol was not diverging: the temporary ADB forward was
  loopback-only and the target test's 30-second accept window expired during physical-device and
  DNS-SD startup. A LAN-bound test relay plus a 120-second test-only accept window now lets the
  complete identity-bound pairing and encrypted command sequence pass on both devices. Pairing
  failures also retain a non-secret shared handshake stage for actionable diagnostics.

#### Next Naviamp Connect slice

The active sequence is maintained in
[`naviamp-connect-product-plan.md`](naviamp-connect-product-plan.md#implementation-order). Shared
capability-based roles, ordinary Now Playing/queue routing, atomic first Play, output selection, and
friendly names are complete. The next slice closes the Android phone/TV recovery matrix, then adds
and validates non-TV playback targets and Apple host adapters. Desktop-to-emulator live playback is
complete; physical Google TV, direct-LAN, additional Desktop hosts, and Apple coverage remain
explicit acceptance work.

The first recovery-matrix item now has a shared implementation: accepted remote playback-start
commands open the playback device's full Now Playing route through Core navigation. Failed or
pause-only commands do not disturb the target's current screen. Shared presentation tests and the
Android debug build pass. The physical Pixel now reauthenticates its retained TV trust after Android
DNS-SD returns the emulator's IPv6 link-local address: the shared debug endpoint adapter recognizes
that address family and routes it through the source-restricted emulator relay. This is test-build
configuration only and does not alter release LAN routing.

The same live pass exposed a disconnected-provider controller UI gap. A live target session could
report **Controlling** while the local provider connection form hid the remote mini player. Shared UI
now permits remote mini/full Now Playing whenever a target snapshot is available, independently of
the controller's local provider state, with JVM policy coverage. A later physical-Pixel-to-emulator
pass transferred **No Division**, started target-owned playback, and confirmed live Play-to-reveal
with a current target snapshot.

### Android TV preview release gates

This is the authoritative exit checklist for publishing the Android TV preview. General Naviamp
Connect availability has additional topology and fresh-device requirements in
[`naviamp-connect-product-plan.md`](naviamp-connect-product-plan.md).

Physical Android 14 TV testing subsequently found setup friction, overnight playback/display power
issues, Quick Jump readability, transition performance, waveform height, and missing TV controls.
Track their current implementation and acceptance in [Android TV follow-up](ANDROID_TV_FOLLOW_UP.md).
The emulator and automated checks below do not close those physical-device findings.

- [x] Approve the Android/JVM version-1 Connect protocol, pairing threat model, cryptographic/key
  lifecycle, replay protection, and playback-authority design. The review found and fixed replayed
  resume-offer key/nonce reuse before approval.
- [x] Replace the remaining generic Home collection-page fallback and verify multiple saved-source
  switching on the emulator.
- [x] Verify controller browse/navigation restoration and product-UI source-mismatch recovery.
- [x] Implement Android pairing diagnostics and permission-recovery presentation.
- [ ] Complete physical permission-denial/return/retry acceptance.
- [x] Complete emulator process restoration, audio-focus interruption, finite/live stream recovery,
  and short native gapless/crossfade/ReplayGain/listen-report checks.
- [x] Complete saved-profile background soak and resource-growth checks; see [the soak test](android-tv-soak.md).
- [ ] Complete physical/target-independent playback acceptance across the supported controller matrix.
- [x] Capture 720p/native-4K waveform/repeat focus states and Now Playing dimming/pixel shifts.
  Automated tests and renders are the OLED mitigation evidence; no physical OLED test is a maintainer gate.
- [x] Complete automated Search, radio, and lyrics UI workflow coverage and artwork-dependent contrast
  checks; see the [UI acceptance audit](android-tv-ui-acceptance.md).
- [ ] Complete physical-TV accessibility acceptance.
- [ ] Pass physical Google TV direct-LAN discovery, audio/HDMI/downmix, CEC, MediaSession/audio focus,
  sleep/wake, process recovery, performance, and Android phone/Desktop controller acceptance.
- [x] Add the Leanback banner and audit the release AAB launcher metadata/native ABI inventory.
- [ ] Validate signed installation and Google Play store-console assets/requirements.

### M4: Physical-device acceptance

- [ ] Test on representative Google TV hardware.
- [ ] Verify HDMI stereo, downmix policy, CEC remote behavior, sleep/wake, process recovery, and
  performance.
- [x] Implement the TV launcher banner and verify packaged native ABIs.
- [ ] Validate signed release installation and Google Play store-console requirements/assets.
- [ ] Decide whether Cast Connect adds enough value after Naviamp Connect is complete. This is a
  post-preview product decision, not a preview release gate.

## Initial Acceptance Matrix

| Area | Emulator | Physical Google TV |
| --- | --- | --- |
| Layout, focus, D-pad, system keyboard | Required | Required |
| 1080p and 4K layout/rendering | Required | Required |
| Provider connection and browsing | Required | Required |
| Internet Radio browse, edit, and playback | Required | Required |
| BASS decoding and ordinary stereo output | Required | Required |
| Queue, restoration, lyrics, reporting | Required | Required |
| `MediaSession` commands | Required | Required |
| Naviamp Connect discovery, pairing, provisioning, and remote control | Functional fake/AVD coverage | Required |
| HDMI/CEC, surround routes, power behavior | Not authoritative | Required |
| Cast Connect discovery and registration | Deferred product decision | Deferred product decision |

## Open Decisions

- Whether the TV ships as the existing Android application with a TV activity/surface or as a
  separately packaged thin host under the same product listing. Begin with maximum shared runtime
  reuse; decide packaging only after emulator evidence.
- Whether lyrics-first mode is automatic, manually selected, or one simple persisted TV display
  preference.
- How TV volume control divides responsibility between Naviamp software volume and the TV/AVR
  system volume.
- Whether a stationary TV needs user-visible offline downloads or only an internal bounded playback
  cache. Downloads are excluded until a concrete disconnected-TV use case is demonstrated.
- Which reviewed PAKE and platform crypto implementation satisfies short-code pairing on every
  supported host without placing cryptographic primitives in product code.
- Whether pairing offers a manual address or QR fallback when LAN policy blocks mDNS while still
  preserving the same authenticated local-only transport.
- The exact canonical-source identity rules for aliases, reverse proxies, changed usernames,
  multi-library selection, and rotated credentials.

## Progress Log

### 2026-08-25

- Chose a complete standalone Android TV/Google TV client rather than a Cast-only receiver.
- Chose lyrics and large typography over an initial visualizer feature.
- Required optional phone/Desktop control while preserving full TV-only setup and operation.
- Dropped Roku from the active direction; its runtime cannot reuse Naviamp's Kotlin Core or BASS
  playback implementation and is not required by the intended hardware path.
- Started the feature branch and M0 architecture work against the running ARM64 TV emulator.
- Added the shared application-surface model, bounded TV destination policy, common policy tests,
  standalone TV setup surface, and initial ten-foot navigation shell.
- Added only Android's `UI_MODE_TYPE_TELEVISION` selection and Leanback launcher/device metadata at
  the host boundary.
- Built and installed the debug APK on `emulator-5556`; the shared TV setup surface rendered at
  1920x1080 with visible D-pad focus on provider selection and no runtime crash.
- Exercised setup using only D-pad events: focus entered the connection-name field and opened the
  Android TV system keyboard successfully.
- Verified the debug APK's complete BASS inventory for all four Android ABIs.
- Passed shared UI JVM tests, Core presentation JVM tests, Android host unit tests, Android debug
  compilation, and iOS Simulator ARM64 compilation for both shared UI and the Core presentation
  entry.
- Fixed the TV connection-form keyboard trap found during emulator use. Name, server URL, username,
  and password now expose an explicit Next/Done sequence; Done closes the keyboard, and the TV setup
  screen explains how to return to the form. Added a shared UI regression test for the complete
  focus sequence.
- Made Password Done focus Connect explicitly after emulator testing showed that clearing focus
  restarted traversal at the top of the form. Confirmed that the intermittent gray lower screen is
  Google's TV input-method window; emulator logs show slow keyboard frames and keyboard-view helper
  warnings rather than a Naviamp crash.
- Replaced spatial focus guessing between the closed form's Advanced and Connect actions with an
  explicit two-way D-pad path after emulator testing showed that Up from Connect preferred the
  geometrically closer Username field.
- Removed local-file actions from TV connection setup. Provider-settings import, trusted CA file,
  PKCS12 client-certificate file, and client-certificate password inputs remain available on phone
  and Desktop but are hidden on TV, where there is no supported file-import workflow.
- Removed fallback URL configuration from TV setup because a stationary playback target does not
  need the phone/Desktop roaming-endpoint workflow.
- Connected successfully to Navidrome on the 1080p emulator. The initial connected-screen capture
  exposed navigation wrapping and motivated the later dedicated three-destination Television bar
  with a compact Settings entry.
- Verified the current dedicated navigation layout on the 1080p AVD at its native
  1920x1080/320 dpi with readable focus treatment.
- Added a native 3840x2160/640 dpi `Television_4K` AVD and verified that the standalone setup
  surface renders correctly at its physical 4K resolution. The same AVD also accepts a real
  1920x1080/320 dpi `wm` override, producing a 1920x1080 framebuffer, so it is now the primary
  dual-resolution acceptance device. Restore native 4K with `wm size reset` and
  `wm density reset`.
- Connected the native 4K AVD to Navidrome and verified the populated Home surface at a true
  3840x2160/640 dpi. The dedicated Home, Library, and Search destinations plus the compact Settings
  entry remain clearly visible without clipping and support D-pad traversal. Emulator layout
  acceptance is therefore complete at both 1080p and 4K. Physical hardware remains necessary for
  HDMI/CEC, suspend/resume, and sustained playback-performance testing.
- Fixed the populated Home carousel after remote testing exposed ambiguous card focus and
  fractional scrolling that clipped the leading artwork. TV cards now use a strong accent border
  and tint without changing their measured size, while focus-driven scrolling snaps to whole-card
  boundaries. Verified traversal from the middle through the final Mixes for You item at native 4K.
- Chose a dedicated shared-Core TV presentation instead of continuing to adapt the standard
  landscape UI. Every TV Home section will use the same horizontal-carousel interaction model;
  phone/Desktop Grid and List preferences will not alter the TV layout.
- Defined Television as a provider- and platform-neutral shared product surface for Android
  TV/Google TV and a future Apple TV/tvOS host. Roku, Samsung Tizen, LG webOS, and other proprietary
  television runtimes are outside the supported platform scope.
- Added the first dedicated shared Television composition: a three-destination top bar, carousel-only
  Home, large Library and Search grids, a reduced mini player, and a full-screen Now Playing layout
  with large artwork, track metadata, lyrics, waveform/scrubber, transport, favorite, repeat,
  shuffle, and secondary actions.
- Added shared Back policy that returns stable secondary Television destinations to Home while
  preserving transient detail and Now Playing handling. Removed a duplicate focus target from the
  top navigation after native-4K D-pad testing showed it required two Down presses to enter Home.
- Verified the dedicated Home, Now Playing, and lyrics layouts at native 3840x2160. Playback,
  artwork, timed lyrics, transport, and clear focus borders all render and respond on the connected
  Navidrome emulator.
- Fixed TV Search submission so the IME Search action closes the system keyboard, moves the query
  field out of the results view, and focuses the first result. Back restores and focuses the query
  field with the existing text so the user can refine the search without leaving the destination.
- Reviewed the dedicated Television composition against this plan. Recorded the remaining
  TV-specific Settings/detail work, bounded Home rail policy, lyrics and queue behavior,
  direct Compose coverage, and launcher asset requirements above rather than treating the initial
  layouts as finished milestones.
- Added and tested the initial bounded Television Home policy. Its former five-category limit was
  later superseded by the shared all-section ordering and visibility policy documented above. TV
  still caps each rail at 30 items and preserves smaller shared item limits.
- Consolidated Library and Search onto a shared fixed-column Television grid with explicit TV focus
  targets and row-major Right navigation. Native-4K emulator testing confirmed that Right from the
  fifth card lands on the first card in the next row and scrolls that row into full-artwork view.
- Removed redundant Home, Library, and Search content headings because the persistent top bar
  already identifies the active primary destination.

### 2026-08-26

- Added dedicated shared Television artist and album detail pages with large hero artwork and
  typography, a reduced action set of Play, Start Radio, and Add to Queue, spacious track rows,
  and an artist-album carousel. Playlist-management controls and the standard dense detail layout
  are intentionally excluded from these Television pages.
- Established a low-click track interaction: Center plays immediately; Right opens a compact panel
  containing Play Next, Add to Queue, and Start Radio; Back closes the panel and restores focus to
  the originating track.
- Verified both detail layouts at native 3840x2160 on the connected Google TV emulator. Confirmed
  deterministic entry focus, hero-action traversal, track scrolling, contextual-action focus, and
  focus restoration after dismissing the action panel.
- Replaced the Television shell's fixed dark gradient with the shared app-background renderer.
  Native-4K emulator testing confirmed that Aurora follows current-artwork colors and tone, Album
  Blur uses the current cover and configured blur radius, and Single Color renders the configured
  hex color immediately. Restored Aurora/Dark after exercising all three modes.
- Made Play/Pause the deterministic initial focus target whenever Television Now Playing opens.
  Verified a D-pad-only path from a focused Home rail through the mini player into Now Playing at
  native 4K; the primary transport control displayed its focus ring immediately on entry.
- Made the Television waveform a selectable scrubber: Up from Play/Pause selects it, Left and Right
  seek backward or forward in 10-second steps, repeated presses build from the pending seek, and
  bounds clamp to the track duration. Native-4K emulator testing confirmed the full-width focus
  treatment and remote seek while focus remains on the scrubber.
- Recorded the next Television interaction direction: a consistent animated focus language,
  listening-mode Now Playing, keyboard-preserving Search, a nested right-side Settings sheet, and a
  future adaptive native-renderer visualizer whose product policy remains in Core.
- Added the first shared focus system across Home/Library/Search artwork, top navigation, detail
  actions and tracks, the mini player, and Television buttons. Focus now animates to 106 percent in
  140 milliseconds with raised z-order, a bright outline, and an accent-colored shadow while
  persistent selections retain their separate state treatment.
- Added interactive and listening states to Television Now Playing. After five seconds without
  remote input the transport and secondary actions fade away; the first ordinary D-pad press is
  consumed to restore the controls and deterministic Play/Pause focus without firing an action.
- Added shared Compose regression coverage for the inactivity transition, wake-event consumption,
  focus restoration, and wake-key policy. Shared UI tests passed alongside Android, Desktop, and
  iOS Simulator compilation.
- Installed and launched the build on the native 3840x2160 Television emulator. Captures confirmed
  the uncluttered listening presentation and the restored, clearly enlarged Play/Pause focus state.
- Removed focus scaling after native-4K carousel testing showed that enlarged first-column items
  could be clipped on their left and lower edges. The shared focus treatment retains its bright
  border, animated glow, tint, and raised ordering without changing layout geometry.
- Converted the Television mini player into a non-focusable status strip containing only current
  artwork, title, and artist. Removed its transport and open actions, and added a conditional Now
  Playing destination to the top bar whenever a current track exists.
- Changed primary-surface Back behavior to return focus to the selected top-navigation item. Album
  and artist detail Back first closes the detail and then returns focus to the bar; transient menus
  and other locally owned layers continue to close before global navigation.
- Replaced the solid blue focus line with the shared animated blue glow and subtle focused-surface
  tint. This preserves a clear ten-foot focus target without creating a hard inset around artwork.
- Defined Search Back as a layered unwind: results return to the query field, an open keyboard is
  dismissed next, and the following Back returns focus to the selected top-navigation item. The IME
  Search action keeps the keyboard and query focus in place; after dismissing the keyboard, Down
  enters the first result.
- Restored the requested focus zoom with explicit overflow space around carousel and grid content.
  Home now aligns the focused rail beneath the header, preventing enlargement from clipping at the
  left or bottom edge and preventing the prior rail from remaining partially visible.
- Refined the focus edge to a translucent blue border backed by an animated blue shadow, preserving
  a border-shaped highlight while making it read as a glow rather than a solid line.
- Restricted media-card zoom and glow to album artwork or artist imagery; card labels and bodies no
  longer enlarge or glow. Reduced artwork and label sizing to expose more items in the ten-foot view.
- Replaced edge-following Home scrolling with a stable third-slot anchor. The first two items advance
  into place normally; from the third item onward, focus remains at the third visible position while
  the rail scrolls beneath it. Vertical rail alignment now changes only when focus changes sections,
  eliminating horizontal-navigation scroll restarts.
- Simplified top-navigation focus to a white background with no glow or zoom, and moved the
  conditional Now Playing destination directly after Home.
- Tightened the shared Television card metadata stack so artwork, title, and artist read as one
  unit across every Home rail, while Library and Search continue to reuse the same base focusable
  card and label components.
- Made Right on the final card advance to the first card of the next Home rail when one exists and
  stop at the final rail instead of escaping to top navigation. Focused rails now use one stable
  vertical context inset: a portion of the preceding rail remains visible above and the following
  heading remains visible below whenever those neighboring sections exist.
- Revised the Home edge policy after remote testing: Right on the final card is now consumed and
  leaves focus in place on every rail. Rail positioning now snaps immediately when vertical focus
  changes instead of continuing an animation after the first horizontal input.
- Strengthened the shared artwork focus treatment with a crisp blue-white core edge, two broader
  translucent blue edge layers, and a substantially brighter blue halo. A focus-only shimmer gives
  the halo a slow pulse and the core edge two brief glints without animating unfocused cards.
  Artwork-derived focus colors remain a possible later refinement rather than part of this slice.
- Changed primary Television navigation to activate destinations as soon as they receive focus;
  Center remains harmlessly idempotent. The white navigation background now indicates focus only,
  so the current page does not retain a misleading highlight after focus moves into its content.
- Kept the top bar present above the full Now Playing presentation and routed Up from that surface
  directly back to its navigation item. The waveform is now display-only on Television: it cannot
  receive focus, show a focus glow, or seek through D-pad Left/Right input. Native media transport
  commands remain the appropriate future path for dedicated rewind and fast-forward buttons.
- Tightened Home rail spacing and the vertical context inset so a following section heading remains
  visible below the focused rail whenever another Home section exists.
- Made Home rail changes deterministic while preserving a cursor per rail. An unvisited rail starts
  at its first card; after a user moves within it, Up or Down returns to that rail's last-focused
  card instead of copying the current rail's column. Reduced the rail gap and focus context inset
  again so the following heading and artwork edge remain visible above the mini player.
- Made top-bar restoration destination-stable. While focus is in page content, only that page's
  navigation item is eligible as an Up target; Back requests that item's dedicated focus requester.
  Once the bar has focus, every destination becomes eligible for ordinary horizontal navigation.
- Kept a dedicated full-screen Now Playing mode behind an explicit Down action from its top-bar
  item. Merely focusing Now Playing previews the page beneath the still-visible bar, allowing
  uninterrupted Left/Right navigation; Back exits full-screen mode to the Now Playing preview,
  and a second Back returns to the underlying page.

### 2026-08-27

- Replaced the standard Playlists list and detail fallbacks with dedicated shared Television
  presentations. The list supports A-Z and recently played ordering, refresh, and grid navigation;
  playlist details expose Play, Shuffle, Add to Queue, spacious track rows, and the same compact
  track-action panel used by album and artist details.
- Added common policy coverage for single-track shuffle availability and passed shared UI tests plus
  Android, Desktop, and iOS Simulator compilation.
- Reconciled stale plan text with the implemented album, artist, playlist, mini-player, and Search
  behavior. At that point, standard composition remained only for Home collection pages on the M1
  path; that final fallback was removed on 2026-09-04.
- Added a shared right-side Television Settings sheet modeled on the compact category-first pattern
  used by established TV music clients. It preserves the underlying destination, dims it, exposes
  current values, uses nested choice pages, unwinds Back locally, and restores focus to the gear.
- Added TV-relevant Sources, Home, Playback, Lyrics, Display, Diagnostics, and About controls.
  Controllers is capability-gated until Naviamp Connect supplies real pairing state and actions;
  downloads, file pickers, touch gestures, Desktop shortcuts, update channels, and mobile-only
  controls remain excluded.
- Reserved overflow around every settings list after native-4K review found the first focused row's
  edge clipped by the viewport. Settings now use a calm static blue-white focus edge with no zoom,
  pulse, or glow, and Close/Back uses a plain filled focus treatment without an outline.
- Matched the shared app's gapless/crossfade exclusivity, restored focus to the originating row
  after nested Settings Back navigation, and routed Back from a connected source editor through
  the shared cancellation action instead of allowing the host to exit. Display settings now also
  expose the shared waveform-density choices.
- Removed the shared animated multi-layer outline from focused TV controls. Media artwork retains a
  single restrained, shape-matched blue halo plus scale/elevation; artist imagery is circular across
  shared collection artwork and dedicated TV Library, Search, and artist-detail surfaces, while
  album artwork remains rounded-square.
- Added a left-side, D-pad-selectable #/A–Z artist shortcut rail to TV Library. `#` groups numeric
  and other non-letter names. The compact rail distributes the complete shortcut set from top to
  bottom without scrolling. Left from the
  first grid column enters the matching shortcut, while Center performs the jump. Library grid
  positioning now uses deterministic row anchors so moving horizontally cannot shift the page.
- Connected the shared Library controller to the existing persistent artist index. Every host now
  paints the full cached artist list immediately, checks the provider scan signature on reconnect,
  and refreshes and replaces the cache when the server reports a completed library change.
- Eased transitions into and out of full-screen Now Playing, made Back reveal the Now Playing
  preview before the underlying destination, preserved that preview through Settings navigation,
  and increased played-versus-unplayed waveform contrast in display-only mode.
- Stabilized route-on-focus handoff when Library opens Playlists, an artist, or an artist album so a
  stale top-bar focus event cannot flash and then restore Library or corrupt the detail Back route.
  Album Back now restores artist detail, and artist Back restores the remembered Library artist and
  row instead of resetting the grid.
- Made artist-detail entry focus Play. Down from Play explicitly targets the first popular track, or
  the first album when no popular tracks exist. The album rail reserves focus overflow on every edge
  so a scaled first album is not clipped.
- Made Library entry deterministic: Down from Playlists targets the first artist, and Right from the
  shortcut rail returns to the first artist rather than the header controls.
- Applied the album-year display preference to TV Now Playing and artist-detail album captions, and
  lengthened the full-screen Now Playing easing so the transition remains perceptible at TV scale.
- Reworked the top-bar focus guard so repeated recovery callbacks cannot reopen Library over artist
  detail. System Back now restores the selected top-navigation item from primary pages, while
  first-row Up and header Down edges explicitly target the top bar or first content item instead of
  relying on spatial focus guesses.
- Removed dedicated Television Back and Close buttons from detail and Settings screens; Google TV
  remote Back now owns those unwind actions. Play remains the deterministic first detail action.
- Removed displaced focus shadows from TV controls and track rows. Transport focus uses the shared
  white-on-dark treatment, while tracks use only their focused fill and scale without a second edge.
- Kept the Now Playing preview visible until the full-screen route is published, eliminating the
  intermediate-page flash, and strengthened the scale/slide easing. Playlist detail explicitly
  resets its list to the top after initial Play focus.
- Expanded the track action panel with labeled Artist, Album, and Track context when available.
- Slowed the five-second inactivity change into listening mode to an 800 ms eased fade-and-collapse.
  The album cover grows from 270 to 310 dp and the display-only scrubber grows from 46 to 60 dp as
  the controls leave, while track typography remains unchanged. Cover-art decoding stays pinned to
  the final size during animation so resizing does not repeatedly reload or crossfade the image.
  The control-to-scrubber gap lives inside the collapsing region so its final removal cannot cause a
  one-frame layout snap after the visible animation completes.
- Made Center on a top-bar destination a deterministic content-entry action: Home selects its first
  card, Library its first artist, Playlists its first playlist, Search its query field, and Now
  Playing enters full screen. Directional focus continues to preview destinations without trapping
  users who are moving across the bar. Settings is deliberately click-only and no longer opens when
  the gear merely receives focus.
- Preserved an explicit return edge when Search is opened from Now Playing so immediate remote Back
  returns to full-screen Now Playing instead of escaping to the Android TV launcher.
- Pinned artist detail to its top viewport after initial Play focus settles. Down from the final
  popular track now explicitly selects album zero, avoiding geometry-based jumps into a later album
  when an artist has multiple releases.
- Made top-bar content-entry requests one-shot so leaving a full-screen Now Playing session cannot
  replay an earlier Center action and steal focus into Home or Library content. Connected startup
  now explicitly focuses the selected top-bar destination, including Home on a normal launch.
- Kept the Now Playing preview mounted behind the fullscreen entrance transition, preventing its
  outgoing layer from briefly repainting Home. Search launched from Now Playing now waits for the
  Search route and fullscreen close to settle, then explicitly transfers top-bar focus to Search.
- Routed Down on every primary top-bar tab through the same deterministic content-entry contract as
  Center. Home therefore always targets its first card instead of allowing geometric focus search
  to choose a later card; Library, Playlists, Search, and Now Playing use their explicit entry
  targets as well.
- Replaced the TV lyric window swap with the shared eased active-line scroll used by every host.
  Active and inactive lyric size, line height, weight, and color now transition over 420 ms while
  line advancement scrolls over 520 ms, preserving two lines of context without an abrupt jump.
- Added explicit Library focus edges: Up from the first artist row enters Playlists, Right reaches
  Refresh, Left returns to Playlists, and Up from either control returns to the Library tab.
- Changed the shared waveform progress treatment from per-bar color switching to a continuously
  clipped played-color layer over one stable waveform. Playback now advances that reveal linearly
  between progress reports on every host, while seeking still updates immediately.
- Preserved TV listening mode across track changes: changing the current song no longer recreates
  the inactivity state or reveals the control bar when it was already hidden.
- Added shared current-media visual transitions. The next two queued covers and palettes are
  preloaded, outgoing artwork remains visible until its replacement is decoded, album art
  crossfades over 280 ms, and Aurora/player colors interpolate over 360 ms instead of briefly
  resetting to fallback colors.
- Corrected the Album Blur transition after real-cover testing exposed a grey midpoint. The
  outgoing bitmap now remains fully opaque while the decoded incoming cover fades over it, and
  short between-track gaps without an artwork URL retain both the cover and its tint rather than
  animating through the placeholder palette.
- Added a TV-only Queue presentation in the same Now Playing panel used by Lyrics. The current
  track remains pinned above the scrolling upcoming list; Center plays a row, Right exposes Play
  Next, Remove, and Start Radio, and Left enters a local reorder mode that commits one shared queue
  mutation with Center or Right. Back cancels a pending move first, then closes Queue and restores
  focus to its control.
- Added arbitrary upcoming-queue movement to the shared domain and command path, preserving the
  current item, duplicate occurrences, Play Next prefix, and queue playback-profile groups.
- Matched the TV repeat control to the standard player's Off, Repeat All, and Repeat One cycle,
  including explicit centered `ALL` and `1` markers and mode-specific accessibility labels.
- Corrected Queue move-mode rendering so ordinary rows never inherit a `MOVING` label from two
  absent nullable indexes. Reorder now swaps the selected row first and transfers focus to its new
  position, allowing the list's focus-following scroll to happen with the move instead of visibly
  scrolling ahead of it.
- Replaced blue focus/selection fills on Now Playing controls with the requested dark inactive and
  white focused-or-active treatment. Repeat All and Repeat One use explicit centered mode markers,
  so focus cannot make Off and All appear identical. Secondary actions are smaller than Previous,
  Play/Pause, and Next. Favorite is the deliberate exception to the selected white fill: a hearted
  track uses a filled red heart and returns to its dark control background after focus leaves;
  while focused, it retains the same white background as every other highlighted control. The
  secondary row uses 38 dp buttons, 20 dp glyphs, and 12 dp spacing so the controls as a whole are
  visibly smaller without appearing compressed together.
- Corrected the lyrics-to-top-bar Back path: non-interactive Now Playing previews now initialize
  without transport controls, eliminating the overlapping secondary/Next buttons and the control
  row that previously had no timer capable of hiding it.
- Added the missing style-specific Display controls to shared Television Settings. Album Blur now
  exposes its persisted 8–48 dp blur radius through a D-pad slider. Single Color opens a live color
  preview with the shared hex value and remote-adjustable Hue, Saturation, and Brightness sliders.
  Left/Right adjusts a focused slider, Center advances one step, and Back restores focus to the
  originating Display row.
- Made the Settings sheet a true overlay over the last visible TV page. Opening the gear no longer
  clears a Now Playing preview or closes its route, and a route-driven Settings request restores the
  last non-Settings destination instead of forcing Home underneath. The sheet now uses a focusable
  shared popup instead of a platform dialog, eliminating Android's additional window dim. The only
  remaining backdrop tint is 3 percent, so Album Blur and Single Color changes remain visible
  across the exposed page while they are adjusted.
- Replaced Search in the TV Now Playing secondary controls with Settings. Search remains available
  from the persistent top navigation, while the new control opens the Settings sheet directly over
  full-screen Now Playing for a useful live Display preview.
- Expanded the shared Aurora tone policy from Light/Dark to Light/Balanced/Dark on every host. The
  former Dark appearance is now labeled Balanced and remains the default; its legacy serialized
  `Dark` value is deliberately retained so existing installations and synced settings do not
  change appearance. The new Dark option applies a substantially deeper artwork-derived gradient.
- Expanded TV Home from the former five-category subset to every available standard Home section,
  preserving the shared saved order and visibility while retaining TV carousel presentation and
  per-rail limits. Added a shared 17-section settings catalog, a TV Home settings category with
  open/closed eye controls and D-pad move mode, and a combined phone/Desktop editor supporting
  pointer drag plus touch-and-hold drag. Visibility changes now live alongside order controls on
  every host while existing per-section layout settings remain available.
- Deliberately excluded Mix Builders from both TV Home and TV Home settings. The normal apps retain
  the builders and their saved position; reordering from TV preserves that hidden standard-app slot.
- Recorded the missing dedicated TV Internet Radio Stations workflow as the next standalone-surface
  gap. Recent Internet Radio on Home remains available, but full station browsing, playback,
  refresh, add/edit, contextual actions, and confirmed deletion still need a remote-friendly page.
- Approved the product direction for local-only, cross-platform Naviamp Connect: Android, iPhone,
  and Desktop controllers can target Android TV or tvOS through one shared versioned protocol. The
  TV remains playback authority and can be discovered during explicit pairing mode, provisioned
  with an encrypted provider connection and portable settings, then operated independently.
- Defined the intended assisted-setup and handoff semantics. Short-code pairing establishes durable
  device trust; a target validates and securely persists transferred connection information; queue
  transfer requires a matching canonical source identity and preserves occurrences, groups,
  priority, position, repeat, shuffle, and playback-profile intent without transferring stream URLs.

### 2026-08-28

- Began Naviamp Connect in shared Core with a versioned, capability-gated protocol, canonical source
  identity, authoritative revisioned snapshots, duplicate-safe queue occurrences and groups, pairing
  states, request deduplication, conflict reconciliation, and conservative reconnect retry rules.
- Added deterministic domain and application fake-transport coverage for negotiation, serialization,
  explicit TV approval, code expiry and rate limiting, command capability checks, request replay,
  stale snapshots, target-local updates, revision conflicts, and same-source handoff enforcement.
  The new shared code passes its JVM tests and compiles for Android and iOS Simulator ARM64.
- Added the shared controller-side discovery lifecycle: it owns compatibility filtering, stable
  ordering, expiry, removal, duplicate refreshes, permission/unavailable states, and rejection of a
  fingerprint change for an existing service instance. Native DNS-SD adapters remain unwired and
  cannot enable commands.
- Added the narrow Android DNS-SD browsing adapter backed only by `android.net.nsd.NsdManager`.
  Discovery TXT decoding and policy remain shared, resolved endpoints stay outside advertised
  metadata, and no command socket is opened. Its native service translation and real DNS-SD
  start/stop path both passed focused instrumentation on the connected Pixel 10a.
- Added the matching shared target-advertising lifecycle and narrow Android DNS-SD registration
  adapter. A coordinated device test advertised from the 4K Android TV emulator and was discovered,
  resolved, and metadata-validated by the physical Pixel 10a in 3.6 seconds; both sides then stopped
  cleanly. This proves the intended first LAN topology before a command channel is enabled.
- Replaced the Controllers placeholder with a shared Connect settings presentation supporting
  target pairing-mode status/code, controller discovery results, and trusted-device rows. The page
  remains hidden until a secure runtime supplies real actions, so incomplete pairing cannot appear
  functional. Added a durable Android Keystore P-256 identity with SHA-256 fingerprinting and ECDSA
  signing; repeated-load and signature verification passed on the Pixel 10a.
- Recorded the Connect version 1 contract and security gate in `docs/naviamp-connect-protocol.md`.
  Android and Desktop now share the Kotlin adapter over Bouncy Castle J-PAKE; no Rust library is
  integrated. The NIST 3072-bit exchange uses Bouncy Castle's explicit confirmation round, followed
  by transcript- and pairing-session-bound HKDF-SHA-256 derivation. Matching-code, wrong-code,
  tamper, malformed, reordered, cross-session, and destruction tests pass on JVM. Production
  discovery-to-command and credential transfer stay disabled until authenticated transport and an
  interoperable reviewed Apple implementation exist. The first end-to-end acceptance path uses the
  connected Pixel 10a as controller and the Android TV 4K emulator as target.
- Verified the packaged J-PAKE adapter on the physical Pixel 10a. A complete NIST-3072 exchange
  performed mutual confirmation and produced identical 32-byte session roots for controller and
  target roles in 0.158 seconds. Android app/test packaging, shared JVM tests, and Android plus iOS
  Simulator compilation all pass with the new dependency.
- Added the shared authenticated-channel owner and one Kotlin/JCA Android/Desktop implementation.
  Independent directional AES-256-GCM keys, transcript-derived nonce prefixes, authenticated
  session headers, contiguous sequence enforcement, tamper/replay rejection, and secret
  destruction pass focused JVM tests. Added a narrow bounded framed-TCP socket effect shared by
  Android and Desktop; bidirectional framed I/O passed on the physical Pixel 10a in 0.061 seconds.
- Completed the Core pairing orchestrator. It negotiates protocol versions, validates ordered
  plaintext handshake frames, runs J-PAKE, switches immediately to the authenticated channel,
  verifies that advertised fingerprints match the supplied public keys, exchanges session-bound
  ECDSA proofs, and requires a final encrypted mutual confirmation before creating either trust
  record. The display code is removed from retained target state as handshaking begins. Matching
  code and post-pair encrypted traffic pass end to end; wrong codes and corrupted identity proofs
  fail before trust is created.
- Passed the first complete physical Pixel 10a controller to Android TV emulator target pairing.
  Both devices used their own Android Keystore identity, the Pixel discovered the TV through
  DNS-SD, pairing and encrypted ping/pong completed in 4.897 seconds on the controller, and the TV
  test completed in 16.781 seconds including advertising startup. Because the emulator advertises
  its private `10.0.2.15` NAT address, this acceptance used a temporary host TCP relay for only that
  unreachable hop; it was removed after the test and is not product code.
- Wired the secure pairing runtime into the production shared settings flow. Android TV now binds
  the real listener, advertises only during explicit pairing mode, shows its six-digit code, and
  requires approval of the named controller. Standard Android settings can start discovery, select
  a target, enter the code, and invoke the same Core pairing runtime. Successful identity-confirmed
  pairing persists the non-secret trust record through a Core-owned schema and Android
  SharedPreferences string effect. Production smoke checks passed on the TV emulator and physical
  Pixel 10a; emulator NAT still requires the existing test-only relay for the encrypted cross-device
  hop.
- Added the Core-owned authoritative target snapshot projection. Canonical playback state, clock,
  repeat/shuffle state, volume, duplicate-safe queue occurrences, Play Next count, and queue groups
  now map deterministically into the revisioned Connect wire model. Session transport and command
  execution contracts are suspendable so secure socket I/O and Core playback effects can complete
  without blocking or Android-only coroutine policy. Focused tests plus Android, Desktop, and iOS
  Simulator compilation pass.
- Retained the authenticated connection after pairing and continued the encrypted sequence space
  consumed by the handshake and welcome snapshot. The TV now executes capability-gated play,
  pause, toggle, previous, next, stop, seek, repeat, shuffle, queue selection, upcoming reorder, and
  upcoming removal against the same Core playback owners as local UI. Local/native playback changes
  publish debounced authoritative revisions back to the controller. Android phone settings expose
  the connected target's current track with Previous, Play/Pause, and Next controls. A physical
  Pixel 10a to Android TV emulator acceptance test completed pairing, retained the secure channel,
  sent encrypted Play, received acknowledgement plus the updated snapshot, and passed on both
  devices; the temporary emulator-NAT relay and forwarding rule were removed afterward.
- Projected the connected target into the existing shared Now Playing surface used by Android,
  Desktop, and iOS. The controller now renders the TV's authoritative track, clock, repeat/shuffle,
  favorite, and queue state and translates the shared UI actions into encrypted transport, seek,
  absolute favorite, queue selection, Play Next, reorder, and removal commands. Unsupported local
  media actions are omitted from the remote queue menus. The physical Pixel 10a and Android TV
  emulator again completed the retained encrypted Play/ack/snapshot test; both temporary relay
  resources were removed. Shared JVM tests and Android, Desktop, and iOS Simulator builds pass.
- Added canonical non-secret source identity projection from the active provider connection and
  required it for both queue handoff and catalog playback. A controller can now hand its current
  queue to the TV atomically; the target re-resolves every occurrence with its own credentials and
  restores the selected item, position, playing state, repeat, shuffle, Play Next prefix, queue
  groups, and playback profiles without transferring a stream URL.
- Routed shared controller playback intents for tracks, albums, artists, playlists, and Internet
  Radio through the retained encrypted session while leaving browsing and editing local. Album,
  artist, and playlist shuffle intent is retained, and direct Navidrome/Jellyfin track lookup avoids
  an expensive catalog scan. Source mismatches fail before target execution. Focused shared tests
  and the domain/app/presentation/provider JVM suites plus Android compilation pass.
- Extended the physical Pixel 10a-to-Android-TV-emulator encrypted acceptance from transport-only to
  three retained-session mutations: Play, a same-source queue handoff, and a same-source album start.
  Both device-side instrumentation runs passed, including acknowledgements and authoritative
  snapshots; the temporary emulator-NAT relay and ADB forward were removed afterward.
- Added shared assisted provisioning and reverse queue handoff. A controller can offer its current
  protected provider connection and portable settings over the encrypted session; the TV shows the
  named pending offer and must explicitly approve it before Core validates and saves anything.
  Local certificate paths and device-only settings are excluded, failed validation preserves the
  existing TV source, and the target request cache does not retain or replay a completed credential.
  Same-source TV-to-controller transfer pauses the TV only after validation and restores TV playback
  if local queue activation fails. The unconfigured TV screen exposes pairing mode, its short code,
  controller approval, and provisioning approval alongside manual setup, so assisted setup is
  available on actual first launch rather than requiring an existing server connection.
- Added the dedicated TV Internet Radio page under Library. It uses the shared station model and
  controller for the complete saved collection, Select-to-play, Refresh/New controls, Right-side
  edit/delete actions, the shared keyboard editor, deletion confirmation, deterministic entry focus,
  and Back-to-Library behavior without adding another permanent top-navigation tab.
- Extended the physical Pixel 10a-to-Android-TV-emulator retained-session acceptance to five
  commands: Play, queue handoff, album start, Internet Radio station start, and an encrypted
  provisioning offer. Both device runs passed and all temporary relay resources were removed.
- Audited the dependency catalog and moved the compatible stable line to AGP 8.13.2, Gradle 8.14.5,
  Compose Multiplatform 1.11.0, Ktor 3.5.2, AndroidX Media 1.8.0, and Kover 0.9.9. Compose
  Multiplatform 1.12 requires compile SDK 37 and AGP 9, so it is intentionally grouped with the
  separate AGP 9/KMP migration. Compose UI test v2 and Android Media3 migrations are also recorded
  as follow-up API migrations rather than hidden inside version bumps.
- Ran the Android TV acceptance sweep on the API 36 ARM64 emulators at native 1920x1080 and
  3840x2160. Home, Now Playing preview/full screen, Library, Playlists, Search, artist/album/playlist
  details, Settings, synchronized lyrics, artwork, waveform progress, and saved-session restoration
  rendered without a runtime crash.
- Passed the shared domain/UI, Navidrome provider, Android unit, and Android APK build suite. After
  the emulator fixes, shared UI JVM tests and Android assembly passed again, and the same shared UI
  compiled for Desktop and iOS Simulator ARM64.
- Fixed the hidden-Playlists navigation-owner mismatch found by the emulator sweep. Playlists now
  opens with focus on its first row, Up can return to the Library tab, playlist details return to
  the playlist list instead of Library, and the originating playlist focus is restored. The shared
  navigation policy now canonicalizes internal Playlists content to its visible Library owner.
- Fixed TV Search submission so the IME action dismisses the platform keyboard. The submitted query
  and results remain visible, and one Down press now focuses the first result. Verified the full
  path with a 34-result `2Pac` query; Back returns from results to the query and then to the Search
  top-navigation entry without exiting the app.
- Rechecked focus restoration through artist, album, and playlist details. Artist detail begins on
  Play, Down selects the first album after any popular-track rows, album Back returns to the artist,
  and artist Back returns to the originating Library artist rather than the top of the collection.
- Rechecked TV Playback and Display settings. Gapless and Crossfade remain mutually exclusive in
  both directions, Back retains the originating setting row, Aurora exposes Light/Balanced/Dark,
  Waveform Density is available, and Album Blur and Single Color expose their live-preview controls.
- Exercised Home section move mode by moving the first section six positions down. The settings
  sheet scrolled with the moving row; the item was then returned to its original position and the
  saved order/visibility were left unchanged.
- Verified native-4K Now Playing and Library layout/focus independently of the 1080p pass. The
  remaining emulator gaps at that point included multiple-source switching and the standard-fallback
  Home collection page; both were closed on 2026-09-04. Sustained audio-policy/provider reporting
  validation and automated recovery/direct Compose coverage remain. HDMI/CEC, audio focus,
  sleep/wake, and authoritative `MediaSession` acceptance remain physical-hardware work.
- Completed the dedicated Internet Radio acceptance on the native-4K TV emulator. The saved station
  collection, deterministic entry focus, live station playback, streamed track metadata/artwork,
  Refresh, New/Edit keyboard forms, and Back-to-Library behavior passed. The run exposed a risky
  Delete-first action-dialog focus; station actions now default to Edit and deletion confirmation
  defaults to Cancel. The rebuilt APK and shared UI JVM tests pass, and the corrected Edit focus was
  visually rechecked without mutating any saved station.
- Matched the normal phone/Desktop live-radio queue behavior on TV. While an Internet Radio station
  is playing, the Queue control remains available without a music-queue index, pins the current
  station, lists every other saved station, and uses Select to switch stations without exposing
  track-only move or row-action controls. Display now follows Home in the TV settings category list.

### 2026-08-31

- Closed the five recorded branch-review findings in shared code. Repository-backed artist refresh
  now consumes validated provider pages to completion before atomically replacing the index, so
  Jellyfin libraries larger than 200 artists are not silently marked complete.
- Made the complete target pairing socket lifecycle bounded in Core. Initial hello and approved
  PAKE/identity handshakes have separate deadlines; stopping or expiring pairing closes the accepted
  connection as well as the listener; incomplete and malformed clients cannot monopolize the sole
  accept flow; and the target resumes accepting after a rejected request. Added deterministic tests
  for silent clients, cancellation, reacceptance, and non-cooperative native reads.
- Scheduled pairing-code/advertisement expiry and stale discovery removal from the shared Connect
  owner. Both now transition state at their declared lifetime without another socket or DNS-SD
  callback. Added virtual-time tests for the target and controller paths.
- Failed replacement artwork now clears the previous media image instead of displaying stale cover
  art indefinitely. The existing short grace period for a transient null URL remains unchanged.
  Trusted-device records no longer advertise a disclosure action and are non-interactive until the
  shared rename/revoke workflow exists.
- Passed the Core app, presentation, and UI JVM suites plus Android and iOS Simulator ARM64
  compilation. The rebuilt debug app launched successfully with populated state on the 1080p TV AVD
  and physical Pixel 10a; framed TCP, J-PAKE, and Keystore identity instrumentation passed on both.
- Re-ran Android-to-Android pairing. The physical Pixel discovers the TV AVD; direct connection still
  fails on the advertised `10.0.2.15` NAT address as expected. A temporary ADB/LAN bridge reaches the
  target but the identity-bound handshake now reproducibly returns `AuthenticationRequired`, while
  the isolated TCP, J-PAKE, and identity tests pass. The already-running Pixel 8 emulator does not
  improve this path because its mDNS discovery cannot see the separate TV AVD. Recorded the
  authentication divergence as a new pre-merge issue rather than misclassifying it as networking.
- Resolved that apparent authentication divergence in the acceptance harness. `adb forward` had
  exposed the emulator only on Mac loopback, and the target's 30-second accept deadline elapsed
  during device instrumentation and DNS-SD startup, so the controller saw a generic closed-socket
  authentication result. With a LAN-bound relay and a 120-second test-only accept window, the
  physical Pixel 10a and 1080p TV AVD both pass the full identity-bound pairing plus encrypted play,
  queue handoff, connection provisioning, Internet Radio, and album command sequence. Production
  deadlines and transport behavior are unchanged. Added non-secret shared pairing failure stages
  so future device failures identify the last completed handshake phase.

### 2026-09-02

- Generalized Naviamp Connect roles in shared Core: phone and Desktop can act as controllers or
  playback targets, while TV remains target-only. A single durable trust record and resumption
  credential now survive role reversal, app updates, and ordinary reconnects.
- Made remote output part of the normal shared Now Playing experience. The controller can select
  local output or any remembered compatible target, sees a persistent **Playing back on** banner,
  and can stop controlling from the first three-dot-menu action without revoking trust or changing
  playback on the target.
- Completed the atomic first-Play authority handoff. Selecting a target leaves both existing queues
  untouched until Play or a new media selection, then transfers the controller queue, selected
  occurrence, position, ordering state, and portable playback profile before starting a target-owned
  stream. Fixed an authenticated outbound-sequence race between acknowledgements and snapshots.
- Added shared self-name and controller-local alias editing, duplicate-name disambiguation, revoke,
  legacy trust-record migration, and automatic remembered-device reconnect. Removed the old
  connection-sharing and manual queue-transfer actions from the ordinary workflow.
- Reworked the TV waveform to use the shared album-art-derived accent decision and a smooth
  large-display path. Also corrected track-change waveform refresh, Now Playing Play focus/selection,
  misleading persistent Play highlighting, and right-arrow access to the active queue row's actions.
- Verified the retained-trust, detach, reconnect, output-selection, stop-controlling, and native TV
  playback flows through the physical Pixel 10a and Android TV emulator product UI. A separate run
  transferred a real 38-item phone queue on first Play and controlled TV Pause/Resume with empty
  crash buffers. The emulator still needs a test-build route override for its private NAT address;
  direct-LAN, physical Google TV, Desktop live playback, and Apple acceptance remain open.
- Passed the shared Core app, presentation, and UI suites, Android assembly, Desktop compilation,
  and iOS Simulator ARM64 compilation after the final Now Playing output-selector changes.

### 2026-09-03

- Completed the emulator recovery pass with the physical Pixel 10a after full controller and target
  process restarts. Durable trust reauthenticated without another pairing code; a deliberately
  interrupted relay recovered through the shared bounded retry once the route returned.
- Verified target independence across failure and detachment. The TV retained its current track and
  all 51 persisted queue occurrences after controller restart, socket loss, **Stop controlling**,
  and reconnect.
- Verified newest-controller-wins with the trusted Pixel and Desktop controller. Desktop took the
  only live TV session, the Pixel was explicitly told another controller took over, and it returned
  to local output without an automatic reconnect fight.
- Fixed a shared layout regression found during recovery testing: opening Settings while no provider
  is connected no longer renders only its title. Settings keeps its own bounded scroll surface, so
  Controllers and remembered-device recovery remain usable before provider setup. Added a JVM
  regression test and passed shared UI JVM tests plus Android, Desktop, and iOS compilation.
- Replaced the TV-only **ALL** repeat treatment with one Core-owned repeat-icon state mapping shared
  by phone, Desktop, and TV. Repeat All now overlays **A** and Repeat One overlays **1** on the same
  loop glyph. Common/JVM tests and Android, Desktop, and iOS compilation passed; both active states
  were visually exercised on the 1080p TV emulator.
- Fixed missing artwork after Navidrome session renewal and Connect handoff. Connect queue
  occurrences now preserve artist, album, and artwork identity, while the shared provider cache
  uses a stable artwork key that excludes rotating Subsonic token/salt values and lazily promotes
  existing authenticated-URL cache entries to that stable identity. Verified on the TV
  emulator with its route to the Navidrome LAN host unavailable: the persisted cover rendered from
  cache and drove the TV background and smooth waveform palette without a network retry.
- Repaired physical-Pixel reconnect when Android DNS-SD resolves the TV emulator as a scoped or
  unscoped IPv6 link-local host. The shared endpoint override accepts an explicit set of host
  prefixes, while Android and Desktop debug wiring opt into `fe80:` alongside the existing emulator
  IPv4 route. Verified an actual encrypted retained-trust socket from the Pixel through the
  source-restricted relay to the TV listener; no new pairing code was required.
- Kept remote Now Playing reachable when a connected controller has no active local provider. The
  connection form may remain visible, but it no longer suppresses a target-backed mini player or
  full Now Playing surface. Added shared UI policy coverage and passed the Android debug build.
- Verified the complete first-Play path from the physical Pixel 10a with **No Division**: the TV
  accepted the queue/current occurrence, entered target-owned playback, and automatically revealed
  full-screen Now Playing. Existing sparse target track records are now enriched with the handoff's
  portable artist, album, duration, favorite, and artwork metadata instead of discarding it.
- Diagnosed the remaining blank cover as emulator infrastructure rather than target UI behavior.
  The TV AVD had a stale global HTTP proxy pointing at an unused port and could not route directly
  to the LAN Navidrome host. With a working temporary CONNECT proxy preserving the original HTTPS
  hostname and SNI, the TV fetched the cover itself with its transferred Navidrome session and
  rendered both the image and artwork-derived background. The clean Android build was reinstalled
  afterward with authenticated artwork URLs absent from logs.
- Corrected the temporary emulator CONNECT relay to preserve TLS bytes pipelined after the CONNECT
  headers and to honor full-duplex half-close behavior. A fresh queued track then remained in BASS
  `PLAYING` state past 85 seconds, the TV scrubber advanced from 0:20 to 1:25, Pause held that
  position, and Resume advanced it to 1:40. This verifies sustained target-owned audio retrieval and
  transport behavior through the emulator-only LAN bridge; physical Google TV direct-LAN playback
  remains open acceptance work.
- Prevented target-to-controller state backlog by publishing authoritative Connect snapshots only
  when playback structure changes (track/station, queue, play state, repeat, or shuffle), rather
  than serializing the full queue on every progress tick. Progress-only changes remain local to the
  playback device, as intended for remote-control mode. Common tests cover the policy. A physical
  Pixel 10a then paused and resumed TV playback, advanced both devices to **Vidmahe**, and followed a
  TV-local advance to **I Alone** within five seconds; the TV scrubber continued advancing while the
  controller deliberately retained its last structural-snapshot position.

### 2026-09-04

- Replaced the last standard Home collection fallback with a dedicated shared Television page.
  Every Home rail now ends in a D-pad-reachable **View all** card; the collection opens as a
  ten-foot grid with deterministic first-item focus, remote Back handling, generated-art support,
  and the same shared item actions as Home.
- Added direct shared Compose coverage for opening a collection, first-item focus, Up-to-Back
  navigation, Back dispatch, and item selection. The shared UI/presentation suites, Android APK,
  Desktop tests, and iOS Simulator compilation pass.
- Configured two distinct saved-source identities on the Android TV emulator and switched to the
  second source and back with the remote. The unavailable test source exposed a shared Core bug:
  offline restoration retained the old inventory preference and marked the wrong row current.
  Core now makes the selected offline source authoritative before publishing state; deterministic
  tests cover online and unavailable two-source transitions. The temporary database was backed up
  before the acceptance fixture was installed, restored afterward, and all extra credential-bearing
  copies were removed.
- Verified on the physical Android phone that opening and dismissing remote Now Playing restores
  the prior Search route instead of replacing controller-local browse/navigation state. Added a
  shared navigation regression that preserves both the selected Search route and last content
  route across the Now Playing overlay.
- Exercised source mismatch through the Android phone -> Android TV emulator product UI. A catalog
  selection with different library identities was rejected without changing TV playback or either
  queue, and the phone displayed the shared localized recovery dialog with Settings and secure
  target-setup choices. The Settings action opened the source workflow, the phone was restored to
  its original Music Library-only selection, and the TV approval gate completed secure setup. A
  later catalog retry reached the target but reported media unavailable because the emulator could
  not resolve the freshly provisioned catalog in this network fixture; it did not regress to a
  source-mismatch failure.

### 2026-09-09

- Resumed `feature/android-tv` and merged the released v2.4.0 main baseline. The current integration,
  validation, environment, and next-work status is recorded in
  [`android-tv-status-2026-09-09.md`](android-tv-status-2026-09-09.md).
- Added the shared TV Artists/Albums/Songs presentation, per-view search and viewport retention,
  stable-identity focus restoration after details, A–Z/loading/empty states, paging, and song
  playback/queue/radio actions. Selector and toolbar D-pad paths are explicit, and page appends
  preserve the current grid focus. All behavior remains in Core; new copy is localized in English
  and Spanish.
- Added common focus-state tests and direct shared Compose checks for navigation, detail return,
  track actions, query isolation, delayed/stale jumps, pagination focus, and 720p/1080p/4K layouts.
  The shared UI/presentation suites pass 646 tests; Android debug assembly, Desktop and iOS
  Simulator compilation, and the architecture check pass. Physical remote/IME and Google TV
  acceptance remain open.
- No Android device was connected for this restart; earlier emulator acceptance remains historical
  evidence, and the physical Google TV preview gates remain open.

- Fixed the September 9 remote-control audit findings in common code. Remote playback intent now
  survives reconnect/unavailable states without dispatching catalog playback or queue additions to
  the local player. Explicit local selection remains the way to return playback to this device.
- TV D-pad reorder mode is invalidated when its queue snapshot changes. Move requests carry the
  immutable queue they were rendered against, and Core rejects stale positions before mutation;
  this covers changes that arrive before the next UI frame as well as visible concurrent edits.
- Controller sessions now probe the authenticated peer every five seconds. Writes, heartbeat
  responses, and command acknowledgements have ten-second deadlines; failure closes the session
  and enters existing reconnect handling. Only idempotent requests survive automatic recovery;
  explicit output selection clears pending retries. New recovery messages are localized in both
  maintained languages. No persisted setting or platform production file changes were needed.
- Validation after the remote-control fixes: 193 Core app, 346 presentation, and 307 shared UI
  JVM tests pass (846 total; no failures, errors, or skips), including nine new regressions.
  Android debug assembly, Desktop compilation, iOS Simulator ARM64 compilation, and the Core
  architecture guard pass. Physical remote/network-loss acceptance was not repeated in this pass.

- Added contextual Connect recovery in shared Core: permission denial is distinct from unavailable
  discovery/advertising, including asynchronous native discovery failures. TV setup, TV settings,
  and standard settings show localized guidance and an explicit retry action. TV recovery buttons
  have explicit Up/Down focus links.
- Recovery retries only the failed discovery/advertising operation; it does not close an existing
  authenticated controller session or change playback destination. Failed target advertisements
  close their listener and clear the unusable pairing code before a new offer is created.
- Added an optional shared native-settings effect. Opening system settings does not imply that
  permission was granted, and a failed launch leaves actionable guidance. Android delegates to its
  application-details Settings intent and maps NSD permission error 7 / resolution SecurityException
  into the shared permission state. SDK/target remain 36; a future target-37 runtime permission or
  system-picker migration remains separate from this recovery UI.
- Platform diff accountability for this recovery change:
  - `AndroidNaviampConnectDiscoveryEffect.kt`: translates Android NSD callbacks and SecurityException,
    and releases the native discovery registration on failure.
  - `AndroidNaviampConnectPermissionSettingsEffect.kt`: invokes Android's application-details
    Settings intent with the application-context activity flag and returns native launch failure.
  - `AndroidNaviampCoreCatalog.kt`: injects that Android Context-backed Settings adapter into the
    existing shared Connect service composition.
  No Desktop/iOS production files or persisted settings changed. Both maintained translations
  include all new UI copy. Physical permission-denial/return/retry acceptance remains open because
  no Android device or emulator is connected; this does not close the full preview diagnostics gate.
- Recovery validation: 195 Core app, 349 presentation, and 309 shared UI JVM tests pass (853 total,
  no failures/errors/skips). Coverage includes asynchronous permission failures, target listener/code
  cleanup, settings-launch failure, remote-session preservation during discovery retry, and TV
  D-pad recovery actions. Android debug assembly and instrumented-test compilation, Desktop and
  iOS Simulator ARM64 compilation, and the Core architecture guard pass. The two new native
  Settings adapter instrumented tests were compiled but not run without a connected device.

- Added shared Now Playing screen protection for full-screen and navigation-preview listening.
  It dims after two idle minutes, deepens dimming after ten, and shifts artwork, text, lyrics, and
  progress within existing margins. Wake resets brightness/position without issuing a playback
  action, open queue interactions suspend protection, and track changes preserve elapsed exposure.
  All behavior and rendering live in `core:ui/commonMain`; no host production files, persisted
  settings, or user-facing strings changed. Navigation chrome and other TV pages are outside this
  Now Playing mitigation; full-device idle/screen-saver behavior still requires hardware acceptance.
- Screen-protection validation: all 318 shared UI JVM tests pass, including nine new policy,
  interaction, preview, and layout tests. Android debug assembly, Desktop compilation, iOS
  Simulator ARM64 compilation, and the Core architecture guard pass. Reviewed shifted/dimmed
  720p and 4K captures under `core/ui/build/reports/television-screen-protection/`; artwork,
  text, and progress stay inside the viewport. OLED validation uses automated tests and renders; no OLED device is available.

- OLED validation constraint: the maintainer does not own an OLED display and cannot perform
  physical OLED testing. Do not assign that task to them or make it a maintainer release gate.
  Use shared timing/wake tests and 720p/4K render checks as the available acceptance evidence;
  no physical panel burn-in validation is claimed. Other Google TV hardware checks remain separate.

- TV localization now uses the maintained English/Spanish resources for settings categories and
  choices, navigation, first-run instructions, radio and queue actions, playback accessibility
  labels, empty states, and detail counts. Counts use plural resources; controller names and
  other inserted values use indexed format arguments. Queue styling now uses an explicit current
  flag rather than comparing a translated label. Track context labels use resource identifiers.
- Localized the shared connection form and radio editor used by TV, plus shared Home headings.
  Genre/decade heading values travel as explicit UI data rather than being parsed from English.
  The initial localization pass followed the device locale. The follow-up below wires the existing
  shared language preference across hosts and adds the TV picker without a new setting or migration.
- Connect status messages carry resource identities and indexed device-name arguments from shared
  presentation into shared UI. Provider-supplied names and diagnostic details retain their original
  text. English/Spanish resource parity and rendered Spanish remote-navigation checks are automated.
- Localization validation: 195 Core app, 349 presentation, and 322 shared UI JVM tests pass
  (866 total, no failures/errors/skips). Both maintained languages contain 815 matching resource
  keys. Android debug assembly, Desktop and iOS Simulator ARM64 compilation, and the Core
  architecture guard pass. Reviewed the Spanish 720p settings-panel capture and exercised remote
  activation, translated plurals, connection fields, and formatted Connect status text. All
  production changes are shared Core code/resources; no platform production files changed.

- The existing shared language preference now applies at `NaviampCoreApp` for both standard and
  TV layouts. TV Display settings offers System Default, English, and Spanish through the same
  shared settings action. Selection updates resources in place without re-keying the application.
  Settings choice rows use stable page/option identities rather than translated labels, preserving
  remote focus during language changes. Settings titles support two lines for translated copy.
- Locale selection, duplicate-update suppression, and restoration lifetime are owned in common
  Core. The native resource adapters are limited to these boundaries:
  - `core/ui/src/androidMain/kotlin/app/naviamp/ui/AndroidNaviampLocaleEffect.kt` reads/restores Android
    `LocaleList` and applies the selected native locale list used by Compose.
  - `core/ui/src/jvmMain/kotlin/app/naviamp/ui/DesktopNaviampLocaleEffect.kt` reads/restores the JVM
    `java.util.Locale` default used by Compose Desktop.
  - `core/ui/src/iosMain/kotlin/app/naviamp/ui/IosNaviampLocaleEffect.kt` applies/restores a volatile
    Foundation `AppleLanguages` override consumed by `NSLocale.preferredLanguages`; it never writes
    a persistent native language preference.
- No setting schema or migration changed. Tests cover each language through shared export/import
  and store recreation, older exports defaulting to System, and invalid enum values being rejected
  by the existing import decoder. Native iOS tests verify both language changes and restoration of
  the original preferred-language list and volatile domain.
- TV settings now handles Back/Escape in shared code through the existing page-back action.
  The remote regression checks Spanish and System selection, stable focused choices, long-label
  truncation, return focus at each settings level, and closing back to the navigation settings icon.
- Language-preference validation: 1,764 JVM tests pass (893 domain, 195 app, 351 presentation,
  325 UI), plus 87 shared/native UI tests on iOS Simulator. No failures, errors, or skips.
  Android debug assembly, Desktop and iOS Simulator ARM64 host compilation, and the Core-first
  architecture guard all pass. No physical Android TV or OLED testing was required for these checks.

- Interruption/restart hardening (shared Core): a failed stream resolution no longer consumes a
  restored start position. The engine adapter retains the last valid playing/paused position for
  an explicit Play retry after reconnection and ignores post-error progress callbacks that could
  erase it. Stop, natural completion, and deliberate queue-occurrence selection clear that recovery
  cursor. This does not introduce automatic network retries or unattended playback restarts.
- Failed session writes no longer advance either shared save throttle. A retry can immediately
  persist the session once storage is available, and only successful writes advance the interval.
  Tests cover both save entry points and reconstruct the controller from the durable session.
- Added regressions for controller transport loss without target playback commands, interrupted
  listening with one successful provider listen submission after reconnect, and SQLite driver
  close/reopen preserving source isolation, duplicate occurrences, current index/position, Play Next
  priority, queue playback profiles, and the Now Playing overlay flag.
- Added 720p and native-4K interactive waveform/repeat captures and remote activation checks under
  `core/ui/build/reports/television-controls/`. The repeat label, visual marker, and retained focus
  agree after activation. Base-surface primary, secondary, and muted text meet 4.5:1 contrast.
  Reviewed both captures; these supplement the existing listening/dimming/shift captures. They do
  not close contrast acceptance across arbitrary artwork backdrops or physical-TV accessibility.
- Packaging audit found a missing launcher banner despite the existing Leanback launcher entry.
  Added `android:banner` and a 320x180 xhdpi banner using the existing Naviamp mark and brand name.
  The name is identical in both maintained languages. Editable source: `design/naviamp-tv-banner.svg`;
  regenerate with `rsvg-convert design/naviamp-tv-banner.svg -o apps/android/src/main/res/drawable-xhdpi/naviamp_tv_banner.png`.
  Reference: https://developer.android.com/training/tv/get-started/create and
  https://developer.android.com/docs/quality-guidelines/tv-app-quality.
  No Android/Desktop/iOS production Kotlin changed; the Android manifest/resource change is native
  launcher packaging metadata. Store-console assets/review, signed release packaging, real network
  transitions, OS process killing, audio focus, HDMI/downmix, CEC, and sustained native playback
  acceptance remain open. Physical OLED testing remains unavailable and is not assigned to the maintainer.
- Recovery validation: 198 app, 353 presentation, 328 UI, and 50 storage JVM tests pass (929 total;
  no failures/errors/skips). Android debug assembly, Desktop/iOS Simulator ARM64 compilation, and
  the Core architecture guard pass. `aapt dump badging` confirms the built v2-test APK's Leanback
  launcher, optional touchscreen/Leanback requirements, SDK 26 minimum/36 target, application
  banner, and arm64-v8a/armeabi-v7a/x86/x86_64 libraries. The packaged banner decodes to 320x180.
  This validates the test APK, not a signed production release or Play Store approval.

- Native lifecycle pass (disposable 1080p TV emulator): fixed a shared ownership bug where
  unmounting `NaviampCoreApp` closed the process-owned Connect controller. The renderer now borrows
  Core; `rememberNaviampCore`, which creates a composition-owned instance, also owns its cleanup.
  Regression tests verify borrowed-window removal/remount preserves discovery and owned-composition
  disposal closes it. Desktop already uses that shared owner; Android's process-owned instance is
  no longer closed by Activity disposal. No platform production code changed.
- Native media commands exposed a second shared bug: explicit Play/Pause/Resume actions were routed
  through toggle behavior. The shared command owner now preserves the requested meaning, including
  repeated Play while playing/loading and repeated Pause while paused. Connect and local/native
  transport requests use the same owner. Tests cover the command owner and Core dispatch path.
- Added a credential-free loopback Subsonic/audio fixture and opt-in real Android runtime setup.
  Reproduction instructions: [android-tv-lifecycle-fixture.md](android-tv-lifecycle-fixture.md).
  Actual native checks confirmed background MediaSession Play/Pause, process SIGKILL with a new PID
  and restoration at 57.743 seconds, force-stop/relaunch at 120.324 seconds, and offline relaunch
  followed by explicit Play retry at 121.275 seconds (rather than zero). The short mid-stream outage
  remained buffered; it is not evidence of buffer-exhaustion recovery or physical Wi-Fi transitions.
- Native boundary coverage passes for BASS decoding/seeking/mixing/EQ, Keystore credentials and
  identity, PAKE, framed TCP, DNS-SD translation/discovery/registration, and permission-settings
  delegation. DNS-SD registration initially exceeded the test's five-second deadline under host
  load. The test now reports callback failures, allows 30 seconds, and always unregisters in
  `finally`; the four DNS-SD checks passed on rerun.
- Shared regression validation: 199 app and 356 presentation JVM tests pass, with no failures,
  errors, or skips. Common Android/iOS Simulator ARM64 compilation and the architecture guard pass.
  Final Desktop/iOS host compilation and Android release bundle assembly pass. Repeated native
  Play/Play and Pause/Pause commands also preserve PLAYING and PAUSED respectively on the rebuilt app.
- Release AAB audit confirms the TV banner and arm64-v8a/armeabi-v7a/x86/x86_64 native libraries.
  The release signing environment is not configured and the resulting AAB is unsigned. Signed
  installation, store-console review, physical TV/remote/audio-focus/HDMI/CEC acceptance and direct-LAN
  cross-device pairing remain open. No release was published. Physical OLED acceptance is not
  assigned to the maintainer, who has no OLED hardware.


- Buffer-exhaustion and focus hardening: the small-buffer emulator reproduced a finite download
  being misreported as Finished, erasing its retry cursor. Core now distinguishes incomplete
  disconnected downloads using BASS byte positions in both native end callbacks and polling.
  The fixed native test retried near 9.082 seconds after interruption near 7.069 seconds, rather
  than zero. Complete audio with trailing tags, unavailable counters and unknown lengths have
  common regression coverage; unknown-length/live native recovery still needs separate acceptance.
- Extracted Android's focus/duck/resume and wake-lock renewal policy into Core. Repeated transient
  loss no longer loses resume eligibility, and explicit Pause while focus-paused cancels automatic
  resume. Resume eligibility is also reused by the existing shared external audio-session owner.
  Android now contains only AudioManager/PowerManager/SystemClock bindings for this behavior.
- Native finite-track gapless and three-second crossfade runs each completed three thirty-second
  tracks, applied provider ReplayGain of −6.0 dB, and submitted exactly one listen per track.
  A final crossfade repeat backgrounded the Activity during the first track; both subsequent
  transitions completed with exactly one listen submission per track.
  AudioManager interruption tests and the repeatable fixture are described in the
  [interruption audit](android-tv-interruption-audit.md), including each changed platform file's
  native-boundary justification and the remaining physical-output/unknown-stream limitations.
- Final interruption validation: 902 domain, 199 app, 356 presentation and 43 Desktop JVM tests
  pass (1,500 total; no failures/errors/skips). Android debug/test APKs and release AAB build,
  Desktop and iOS Simulator ARM64 host compilation, and the architecture guard pass. The final
  native AudioManager test includes duckable loss, transient recovery, explicit-pause cancellation
  and permanent loss. Release signing and physical-output acceptance remain open.

- Live-stream recovery: shared playback requests explicitly mark radio as live. Native EOF now
  produces a retryable error instead of Finished, including when byte counters are unavailable.
  Explicit Play reconnects the retained station without a seek cursor. Finite unknown-length
  sources retain normal EOF behavior because length-free, unframed transport cannot reliably
  distinguish truncation from completion. See the [follow-up audit](android-tv-interruption-audit.md)
  for the synthetic live/unknown-length fixtures and validation. No platform production code changed.
  Native live-disconnect/reconnect and unknown-length finite-completion tests pass. Shared JVM
  suites pass 1,460 tests; Android app/test builds, Desktop/iOS host compilation and the
  architecture guard pass. Ambiguous unframed finite EOF remains an explicit limitation.

- Extended the synthetic fixture to multiple albums and up to 240 tracks. Added an opt-in real
  Android background soak that saves six alternating album profiles, traverses 24 tracks, exhausts
  three interrupted streams, retries explicitly, checks exactly-once listens and samples process
  memory/descriptors/threads. The final native run passed in 720.543 seconds (712 seconds of
  background playback): 24 tracks/listens, three recoveries, peak three streams and zero at completion.
- Warm-window resource changes were +2,568 KiB PSS, +44 KiB native heap, zero descriptors and −2
  threads, within the documented regression ceilings. Full measurements and reproduction are in
  [android-tv-soak.md](android-tv-soak.md). This does not claim overnight leak freedom or acoustic
  acceptance. Android app/test assembly, architecture guard and fixture checks passed; no platform
  production files changed and no product fix was needed.
- Reconciled M2/M3/preview/M4 checklists with existing evidence: emulator recovery, pairing-recovery
  presentation, render checks and banner packaging are marked complete independently of physical
  device, artwork-contrast and signed/store acceptance. Removed the stale maintainer physical-OLED
  gate and kept Cast Connect as a deferred product decision.


### September 9, 2026 — Automated UI workflows and artwork contrast

- Added six shared Compose workflow tests covering Search submission/results/re-entry, radio
  loading/errors and create/edit/delete dialogs, lyric timing/offset/scroll reset, and remote lyric
  toggling with retained focus.
- Added nine contrast tests: 4,913 sRGB background samples plus eight rendered bright-white,
  yellow, checkerboard, and multicolor captures at 720p/4K. Reviewed all eight captures.
- Added a shared TV reading surface and improved inactive-lyric and played-waveform contrast.
  Minimum measured text contrast is 4.56405:1 and played waveform contrast is 3.34431:1.
- Passed all 343 UI and 356 presentation tests, Android debug assembly, Desktop and iOS simulator
  ARM64 compilation, and the Core-first architecture check. All production changes are common;
  no platform production adapters changed.
- Recorded scope, reproduction, and artifacts in [the acceptance audit](android-tv-ui-acceptance.md).
  Physical-TV accessibility remains open.
