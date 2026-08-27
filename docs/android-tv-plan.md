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
- Account for OLED burn-in before release.

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
- Search results update without forcing the keyboard closed. The IME Search action submits the
  current query but does not move focus away from the text field.
- Back dismisses the keyboard while retaining the query; Down then enters the first result. Back
  from results returns to the query field so it can be edited again.
- Results use the shared Television grid and deterministic row-major D-pad navigation.

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

- The dedicated Television shell, Home, Library, Search, Playlists, Settings, and artist, album, and
  playlist detail pages are in place. Home collection pages are the remaining standard-content
  fallback to replace before completing M1.
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
- [x] Provide Home, Library, Playlists, Search, details, and essential Settings.
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
  behavior. Standard composition now remains only for Home collection pages on the M1 path.
- Added a shared right-side Television Settings sheet modeled on the compact category-first pattern
  used by established TV music clients. It preserves the underlying destination, dims it, exposes
  current values, uses nested choice pages, unwinds Back locally, and restores focus to the gear.
- Added TV-relevant Sources, Playback, Lyrics, Display, Diagnostics, and About controls. Controllers
  is capability-gated until Naviamp Connect supplies real pairing state and actions; downloads,
  file pickers, touch gestures, Desktop shortcuts, update channels, and mobile-only controls remain
  excluded.
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
