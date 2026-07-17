# Naviamp Follow-Up Ideas

This document tracks useful ideas that come up during the v2 migration but are not part of the active cross-platform checklist. Keep these scoped as investigation notes until they are promoted into the main plan, an issue, or a release branch.

## Status Key

- `Idea`: Captured for later review.
- `Investigating`: Actively researching feasibility and scope.
- `Planned`: Accepted and moved into a concrete implementation plan.
- `Implemented`: Shipped and verified.
- `Rejected`: Deliberately declined, with rationale.

## Ideas

### Configurable Keyboard Playback Controls

- **Status:** Idea
- **Concept:** Add keyboard shortcuts for controlling playback and provide settings that let users view and customize those bindings.
- **Why it may fit:** Keyboard control makes Desktop playback faster without requiring the Naviamp window or a specific control to have pointer focus. A shared command-to-binding model could also support physical keyboards on Android and iOS while leaving operating-system media keys and remote-control integrations with their platform hosts.
- **Controls to consider:** Play/pause, previous, next, stop, seek backward/forward, volume up/down, mute, shuffle, repeat, favorite, and opening or focusing Now Playing.
- **Questions to answer:**
  - Which shortcuts should work globally while Naviamp is running, and which should require the app to have focus?
  - What default bindings avoid common operating-system and text-entry conflicts?
  - Should media keys be fixed platform integrations, configurable alongside keyboard shortcuts, or both?
  - How should duplicate bindings, unsupported keys, modifier-only input, and restoring defaults behave?
  - Can shortcut definitions and validation live in shared code while each host supplies key-event capture and global-hotkey capabilities?
- **Settings notes:** Add a Keyboard Controls settings area that lists commands and current bindings, supports recording or clearing a binding, detects conflicts before saving, and provides a restore-defaults action. Capability-gate global shortcuts on platforms where they cannot be registered safely or consistently.

### Android and iOS Player Widgets

- **Status:** Idea
- **Concept:** Provide home-screen player widgets on Android and iOS that show the current track and artwork and offer useful playback controls without opening Naviamp.
- **Why it may fit:** A glanceable player is a natural extension of Naviamp's shared Now Playing state and makes common controls available when the full app is not visible. Widget presentation must be native to each platform, but the displayed snapshot, action meanings, fallback state, and artwork policy should come from shared application contracts where practical.
- **Layouts and actions to consider:** Compact and expanded layouts; artwork, title, artist, playback state, progress where platform refresh rules permit it, play/pause, previous, next, favorite, and an action that opens Now Playing.
- **Questions to answer:**
  - Which controls can Android App Widgets and iOS WidgetKit widgets invoke reliably while playback is owned by a background service or suspended app?
  - How should widget actions reconnect to the active shared playback session without constructing a second runtime or playback engine?
  - What state should be displayed before login, while disconnected, when playback is stopped, and after the operating system has terminated the host?
  - How should authenticated artwork be cached and shared safely with an iOS widget extension and Android widget process/lifecycle?
  - Which widget sizes, themes, backgrounds, and accessibility variants should be supported on each platform?
  - Should lock-screen widgets, Live Activities, or platform-specific equivalents be separate later enhancements rather than part of the initial home-screen widget scope?
- **Implementation notes to investigate later:** Define a small shared, serializable widget snapshot and playback-action vocabulary. Keep Android App Widget/Glance and iOS WidgetKit timelines, intents, storage sharing, refresh scheduling, deep links, and rendering in their native hosts. Prototype action delivery and stale-state recovery on physical devices before committing to feature parity claims.

### BlurHash versus ThumbHash Artwork Placeholders and Backgrounds

- **Status:** Idea
- **Sources:** [woltapp/blurhash](https://github.com/woltapp/blurhash), [evanw/thumbhash](https://github.com/evanw/thumbhash)
- **Concept:** Compare BlurHash and ThumbHash for artwork placeholders and as alternate album-art-derived app background sources, then select one algorithm or deliberately reject both.
- **Why it may fit:** Either hash can provide a compact, stable visual approximation of album art before the full image loads and may be cheaper than decoding and blurring full album art for every background. ThumbHash specifically claims better detail and color accuracy at a similar size, plus encoded aspect ratio and alpha support, while BlurHash offers configurable components and a broader established implementation ecosystem. Naviamp needs its own measurements and visual comparison instead of choosing from those claims alone.
- **Questions to answer:**
  - Does Navidrome expose either artwork hash through the Subsonic/OpenSubsonic API, or are its placeholders only internal?
  - If the server does not expose a usable hash, should Naviamp generate and cache one locally after fetching cover art?
  - Which algorithm produces better artwork placeholders and full-app backgrounds across dark, light, colorful, monochrome, square, non-square, and transparent artwork?
  - How do encoded size, encode/decode time, allocation, memory use, cache cost, aspect-ratio handling, and transition smoothness compare on Android, Desktop, and iOS?
  - Is either hash-derived background visually better than the current album blur option, or should hashes be used only as loading and transition placeholders?
  - Can the same decoded colors feed the existing highlight-color and visualizer-color pipeline?
  - Can one shared Kotlin Multiplatform implementation be used for encoding and decoding on all three platforms, or would either option require native wrappers or separate host code?
- **Implementation notes to investigate later:** Verify API availability and both licenses; evaluate maintained Kotlin Multiplatform implementations and the feasibility of a small shared implementation; build an identical artwork corpus and benchmark harness for both algorithms; prototype with existing artwork-cache inputs; and compare startup, track-change, same-album transitions, placeholder-to-art transitions, and background rendering against the current Aurora and Album Blur options. Record the results before selecting an algorithm.

## Promotion Checklist

Before moving an idea into the active v2 plan or a release branch:

- Confirm the provider or local-data source is available.
- Confirm the feature can behave consistently across Android, Desktop, and iOS or document capability-gated differences.
- Identify the shared owner module and the host-specific work, if any.
- Add focused tests or prototype evidence before committing to implementation.
- Record the decision in the main plan or a dedicated issue.
