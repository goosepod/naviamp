# Naviamp TV Plan

## Purpose

Build a complete, standalone Naviamp client for Android TV and Google TV. The TV client connects,
browses, searches, plays, restores its queue, manages essential settings, and reports playback
without requiring a phone or computer. Paired Naviamp clients add convenient remote control but are
never required to finish setup or operate the TV app.

This plan is the active record for product decisions, architecture, milestones, test evidence, and
open questions. Update it as implementation changes; do not preserve obsolete branch-development
states as if they were shipped behavior.

## Product Principles

- The TV is a complete playback owner, not a display attached to another Naviamp process.
- The TV experience is intentionally quieter than phone and Desktop, not artificially incapable.
- Connection creation, editing, deletion, source switching, playback, queue control, recovery, and
  diagnostics remain available with only the TV remote and the Android TV system keyboard.
- Phone and Desktop controllers browse with their normal full UI while targeting the TV for
  playback. Closing a controller does not stop TV playback.
- When the TV owns playback, only the TV reports its playback lifecycle to the provider.
- Lyrics are the primary living-room presentation enhancement. A visualizer is not in the initial
  scope.
- Product policy, state, navigation, setup behavior, remote-session semantics, and TV UI live in
  shared Kotlin. Android code is limited to unavoidable TV, media-session, secure-storage, network
  discovery, and lifecycle boundaries.

## Initial TV Surface

### Primary navigation

Keep the always-visible destinations small:

1. Home
2. Library
3. Playlists
4. Search
5. Now Playing, when a queue exists
6. Settings

Radio, mixes, details, and collection pages remain reachable from Home and Library content without
becoming permanent top-level chrome. Downloads are not an initial TV destination.

### Home

The TV Home screen should prioritize a bounded set of large, remote-friendly rails:

- Continue listening or recent radio sessions
- Recently added
- Favorites
- Mixes or Start Radio
- Recently used playlists when useful

Core owns the TV section policy and item limits. Existing per-user Home configuration must remain
intact for other surfaces.

### Now Playing and lyrics

- Default to large artwork, title, artist, progress, essential transport controls, and two or three
  lyric lines when lyrics exist.
- Allow a lyrics-first full-screen mode with substantially larger text, current-line emphasis, and
  word-level highlighting from the existing shared lyric model.
- When lyrics are unavailable, use the space for queue context or artwork without placeholder
  noise.
- Fade nonessential controls after remote inactivity and account for OLED burn-in before release.
- Do not add a TV visualizer in the initial implementation.

### Standalone setup

The first-run screen offers **Set up on this TV** as the primary path. It must support provider,
server URL, username, password, connection testing, provider-specific fields, library selection,
connection naming, and clear recovery from validation or network errors through the Android TV
system keyboard.

An optional later **Import from another Naviamp device** path may transfer a source after an
authenticated pairing confirmation. It must not replace local setup.

### Reduced settings

The TV settings information architecture is:

- Sources
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

### Ownership

- The selected target owns the authoritative queue, playback clock, provider session, playback
  engine, and reporting lifecycle.
- Controllers send typed commands and consume target state snapshots.
- Target snapshots reconcile every attached controller after local TV-remote actions or another
  controller's command.
- Controller loss does not stop playback. Target loss produces a visible disconnected state; it
  does not silently begin duplicate local playback.
- Handoff transfers queue occurrences, group/priority state, current occurrence, position, repeat,
  shuffle, and resolved playback-profile intent before changing authority.

### Pairing and transport investigation

- Discover targets on the local network through a narrow platform discovery contract.
- Pair with an explicit short code and ephemeral authenticated key agreement.
- Persist trusted device identity in platform secure storage.
- Encrypt commands, snapshots, and any source-transfer material; never expose provider credentials
  or authenticated stream URLs in discovery metadata or logs.
- Prefer the TV maintaining its own saved provider connection. Source transfer is optional setup
  assistance, not ongoing credential proxying.

The exact protocol and threat model require a dedicated design review before network code is added.

## Architecture Placement

### Shared Core/UI

- Application-surface model and TV surface policy
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

## Delivery Milestones

### M0: Emulator foundation

- [x] Create `feature/android-tv` from `main`.
- [x] Confirm the Android TV emulator is reachable: Android 16/API 36, ARM64, 1920x1080.
- [x] Confirm Naviamp packages the complete Android BASS inventory for `arm64-v8a` and emulator
  ABIs.
- [x] Add the shared application-surface model and tested TV navigation policy.
- [x] Add TV launcher metadata and select the shared TV surface from the Android TV configuration.
- [x] Build, install, and launch Naviamp on the emulator.

### M1: Standalone TV shell

- [ ] Provide remote-friendly top navigation and focus states.
- [ ] Complete local connection setup using the system keyboard.
- [ ] Provide Home, Library, Playlists, Search, details, and essential Settings.
- [ ] Verify server connection, library browsing, and source switching on the emulator.

### M2: TV playback experience

- [ ] Verify BASS playback, audio focus, background service behavior, and `MediaSession` controls.
- [ ] Implement TV Now Playing and lyrics-first presentation in shared UI.
- [ ] Verify queue editing, profiles, gapless/crossfade, ReplayGain, and provider reporting.
- [ ] Add remote/process/network recovery tests.

### M3: Naviamp Connect

- [ ] Approve protocol, pairing, security, discovery, and authority design.
- [ ] Implement shared target/controller state machines and fake-transport tests.
- [ ] Add narrow Android TV, Android phone, and Desktop transports.
- [ ] Add target selection, remote queue control, and controller reconciliation.
- [ ] Add local-to-TV and TV-to-local handoff.

### M4: Physical-device acceptance

- [ ] Test on representative Google TV hardware.
- [ ] Verify HDMI stereo, downmix policy, CEC remote behavior, sleep/wake, process recovery, and
  performance.
- [ ] Validate Google Play TV requirements, banner/icon assets, and release packaging.
- [ ] Decide whether Cast Connect adds enough value after Naviamp Connect is complete.

## Initial Acceptance Matrix

| Area | Emulator | Physical Google TV |
| --- | --- | --- |
| Layout, focus, D-pad, system keyboard | Required | Required |
| Provider connection and browsing | Required | Required |
| BASS decoding and ordinary stereo output | Required | Required |
| Queue, restoration, lyrics, reporting | Required | Required |
| `MediaSession` commands | Required | Required |
| HDMI/CEC, surround routes, power behavior | Not authoritative | Required |
| Cast Connect discovery and registration | Not authoritative | Required |

## Open Decisions

- Whether the TV ships as the existing Android application with a TV activity/surface or as a
  separately packaged thin host under the same product listing. Begin with maximum shared runtime
  reuse; decide packaging only after emulator evidence.
- The exact bounded TV Home section defaults and whether users may reorder them on TV.
- Whether lyrics-first mode is automatic, manually selected, or one simple persisted TV display
  preference.
- How TV volume control divides responsibility between Naviamp software volume and the TV/AVR
  system volume.
- Whether a stationary TV needs user-visible offline downloads or only an internal bounded playback
  cache. Downloads are excluded until a concrete disconnected-TV use case is demonstrated.
- The local authenticated transport and compatibility/versioning policy for Naviamp Connect.

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
