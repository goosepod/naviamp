# Naviamp TV Plan

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
- Phone and Desktop controllers browse with their normal full UI while targeting the TV for
  playback. Closing a controller does not stop TV playback.
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

Now Playing opens from the persistent player bar, Playlists lives within Library, and Settings uses
a compact gear entry rather than another full-width destination. Radio, mixes, details, and
collection pages remain reachable from Home and Library content without becoming permanent
top-level chrome. Downloads are not an initial TV destination.

### Home

TV Home is a dedicated shared-Core presentation rather than the standard phone/Desktop Home placed
in a landscape shell. Every visible Home section is a horizontal carousel with large,
remote-friendly cards; TV does not reproduce the standard surface's Grid or List section layouts.
This is the consistent ten-foot interaction model used across the entire Home screen.

The TV Home screen uses at most five rails, in this stable category order:

- The first visible recent-playback or recent-radio rail
- The first visible recently-added or recent-albums rail
- Similar to Starred Tracks, when available; a literal Favorites rail requires a shared Home source
- The first visible mixes, NaviBeat mixes, Mix Builders, or More Like Recent Plays rail
- Recent playlists

Core owns the TV section policy, focus order, and item limits. Shared section visibility and ordering
select between equivalent rails in a category, but the standard surface's saved layout choice
remains untouched and continues to apply to phone and Desktop only. Each TV rail contains at most
30 items and honors a smaller shared per-section limit.

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
- The first ordinary D-pad press in listening mode restores the controls without also invoking a
  command. Restore the last sensible focus target when possible, with Play/Pause as the safe
  fallback. Hardware media commands remain immediate.
- Reset the inactivity timer for remote interaction and suspend it while an interaction that needs
  sustained focus is active, including scrubbing and open menus.
- Account for OLED burn-in before release.

### Focus and selection language

All Television surfaces use one shared focus treatment rather than screen-specific borders:

- Only the artwork or artist image on focused media cards scales approximately 5–7 percent over
  120–160 milliseconds. Labels and card layout remain stationary. Carousels and grids reserve
  overflow space so edge artwork can enlarge without clipping.
- A translucent blue border, soft animated blue shadow, and restrained surface tint provide
  contrast on artwork and every background style without the hard edge of a solid line.
- Focused content is raised above neighboring content so its glow remains unambiguous.
- Transient remote focus and persistent state are distinct. Focus uses the animated outline/glow;
  selected values such as Lyrics, Repeat, Shuffle, and settings choices retain a quieter persistent
  mark when focus moves away.
- Disabled actions remain visually legible but cannot receive focus.

### Search interaction

- Opening Search places focus in the query field and leaves the platform keyboard visible while the
  user enters or refines a query.
- Search results update without forcing the keyboard closed. The IME Search action submits the
  current query but does not move focus away from the text field.
- Back dismisses the keyboard while retaining the query; Down then enters the first result. Back
  from results returns to the query field so it can be edited again.
- Results use the shared Television grid and deterministic row-major D-pad navigation.

### Television settings presentation

- Settings opens as a right-side sheet over a dimmed version of the current destination, occupying
  roughly 40 percent of the screen instead of navigating to the dense standard settings page.
- The first level contains only TV-relevant groups: Connection, Appearance, Playback, Audio, and
  About. Each row has an icon, title, current value, and disclosure indicator.
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

- The dedicated Television shell is in place, but Playlists, collection/detail pages, and Settings
  still delegate to the standard shared content composition. Replace those fallbacks with
  remote-friendly Television presentations before completing M1, especially the reduced Settings
  information architecture defined above.
- The initial full-screen Now Playing layout is functional, but it does not yet satisfy the complete
  lyrics direction. Preserve title and artist context, add the default two- or three-line lyric
  presentation, use the existing word-synced cue model for karaoke highlighting, and show queue or
  artwork context when lyrics are unavailable.
- Queue presentation and editing are not yet part of the dedicated Television Now Playing surface.
- Add shared Compose coverage for Television Home, Library, Search submission and re-entry, the mini
  player, Now Playing actions, lyrics rendering, and route/detail Back behavior. Existing tests cover
  navigation policy, carousel arithmetic, and setup focus, but not the dedicated screen composition.
- Android TV launcher banner/icon assets remain an M4 distribution requirement; the current
  manifest work is sufficient for emulator launch but is not the final Google Play TV package.

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
- [ ] Provide Home, Library, Playlists, Search, details, and essential Settings.
- [ ] Verify server connection, library browsing, and source switching on the emulator.

### M2: TV playback experience

- [ ] Verify BASS playback, audio focus, background service behavior, and `MediaSession` controls.
- [ ] Complete TV Now Playing, queue context, and lyrics-first presentation in shared UI. An initial
  playback and line-synced lyrics layout is implemented and validated on the emulator.
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
| 1080p and 4K layout/rendering | Required | Required |
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
- Whether users may reorder the bounded TV Home categories directly on TV.
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
  shuffle, and Search actions.
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
  TV-specific Settings/detail work, bounded Home rail policy, complete lyrics and queue behavior,
  direct Compose coverage, and launcher asset requirements above rather than treating the initial
  layouts as finished milestones.
- Added and tested the shared Television Home policy. Home now renders at most one rail from each of
  five stable ten-foot categories, uses shared visibility and ordering to choose between equivalent
  rails, excludes unrelated standard Home sections, caps each rail at 30 items, and preserves any
  smaller shared item limit without changing phone or Desktop Home settings. Installed the change
  on the native 4K emulator and verified that the populated Home rail and D-pad focus render cleanly.
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
