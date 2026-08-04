# Naviamp Follow-Up Ideas

This document tracks useful ideas that come up during the v2 migration but are not part of the active cross-platform checklist. Keep these scoped as investigation notes until they are promoted into the main plan, an issue, or a release branch.

## Status Key

- `Idea`: Captured for later review.
- `Investigating`: Actively researching feasibility and scope.
- `Planned`: Accepted and moved into a concrete implementation plan.
- `Implemented`: Shipped and verified.
- `Rejected`: Deliberately declined, with rationale.

## Ideas

### Weblate Translation Management

- **Status:** Idea
- **Concept:** Use Weblate as Naviamp's source of truth for community translations, with repository synchronization keeping the shared localization resources and translator-facing strings aligned.
- **Hosting opportunity:** Apply for Weblate's gratis Libre plan for Naviamp as a public libre project. The advertised Libre plan has the same limits as Weblate's 160k plan and is intended specifically for public projects that benefit from Weblate support.
- **Shared-architecture requirement:** Keep translatable product strings and locale behavior in shared resources wherever possible. Android, Desktop, and iOS hosts should contribute only genuinely platform-owned text such as operating-system permission descriptions or packaging metadata, and should not develop independent translation catalogs for shared UI.
- **Questions to answer:**
  - Which Compose Multiplatform resource format and repository layout gives Weblate reliable round-trip updates without generated-file churn or platform catalog drift?
  - Should Weblate push directly to a dedicated translation branch, open reviewed pull requests, or be mirrored through a project-owned automation account?
  - Which locales, plural rules, placeholders, markup, screenshots, glossary terms, contributor credit, review thresholds, and stale-string policies should be configured before inviting translators?
  - How will CI validate placeholder compatibility, locale completeness, encoding, fallback behavior, and compilation across Android, Desktop, and iOS?
- **Implementation output:** Confirm Libre-plan eligibility, create the Weblate project and component configuration, document translator and maintainer workflows, import the existing catalogs, add automated validation, and verify a complete translation round trip from Weblate through review to all three clients.
- **Source:** [Weblate hosting and Libre plan](https://weblate.org/en/hosting/)

### Cross-Platform BASS Add-On Usage Audit

- **Status:** Idea
- **Concept:** Audit which vendored BASS add-ons Naviamp actually loads and uses for real playback, analysis, effects, and supported library formats, then remove add-ons that provide no product value.
- **Scope:** Compare Android, Desktop, and iOS as one playback product. Record each add-on's call sites, dynamic-load result, formats or features it enables, representative test media, package-size cost, and whether the operating system already supplies an equivalent codec.
- **Important constraint:** Do not remove a library merely because a narrow acceptance library does not contain its format. A removal needs evidence from the supported-format contract and tests showing that Core capability claims, provider transcoding/original-stream behavior, offline playback, waveform analysis, crossfade/mixing, EQ, and visualizers remain correct.
- **Desired outcome:** Define one intentional cross-platform base inventory plus documented platform substitutions, remove unused binaries/load attempts/build metadata, and add package verification that prevents the inventories from drifting accidentally.
- **Timing:** Keep the complete supported parity set during initial iOS BASS bring-up so missing codecs cannot be mistaken for backend or simulator defects. Run this audit after iOS playback parity is stable and before final release packaging/performance acceptance.

### F-Droid Distribution

- **Status:** Idea
- **Concept:** Research and complete the work required to list Naviamp in the official F-Droid repository, giving Android users a trusted non-Play-Store installation and update path.
- **Questions to answer:**
  - Do F-Droid's current inclusion rules permit Naviamp's official prebuilt BASS libraries, or would the standard flavor need a fully source-buildable playback alternative?
  - Can the Android release be built reproducibly from a clean F-Droid build environment without network-fetched binaries, local signing inputs, or untracked configuration?
  - Which proprietary Google, analytics, update, crash-reporting, billing, or other non-free dependencies and services are present, including transitive dependencies, and which must be removed or flavor-gated?
  - Should Naviamp submit through F-Droid's Requests For Packaging process, maintain metadata in `fdroiddata`, or first publish a project-owned F-Droid repository while official inclusion is reviewed?
  - How should version codes, tags, changelogs, signing-key continuity, reproducible-build verification, screenshots, descriptions, licenses, source links, and update checks integrate with the existing release process?
- **Investigation output:** Produce a current-policy compatibility report, dependency/license inventory, reproducible clean-build procedure, required Gradle/repository changes, store metadata checklist, and an explicit go/no-go recommendation for official inclusion. Treat an alternative playback engine or separate build flavor as a product and maintenance decision, not an automatic workaround.
- **Shared-architecture requirement:** Distribution-specific Android build metadata may live in the Android/release tooling, but it must consume the same Core product and must not fork application behavior or create an F-Droid-only product graph.

### Google Cast and Apple AirPlay

- **Status:** Idea
- **Concept:** Add cross-platform playback routing to Google Cast receivers and Apple's equivalent, AirPlay, so Naviamp can hand music to televisions, speakers, and whole-home audio targets.
- **Product distinction to define:** AirPlay can act as an operating-system audio route while Naviamp continues owning playback locally, whereas Google Cast normally creates a remote playback session whose receiver owns the media timeline. Investigate both route selection and true remote-session handoff explicitly rather than presenting them as identical implementations.
- **Shared-architecture requirement:** Core must own target/session state, queue handoff policy, playback commands, progress reconciliation, reconnect and recovery behavior, provider reporting, errors, and user-facing capability decisions. Android and Apple adapters may only wrap target discovery, platform session lifecycle, route selection, and Cast/AirPlay transport APIs.
- **Questions to answer:**
  - Can authenticated Navidrome streams, transcoded URLs, custom headers, expiring credentials, and artwork be delivered securely to a receiver that fetches media independently from the phone or computer?
  - Should queue changes remain synchronized bidirectionally, and which side becomes authoritative after a remote session begins?
  - How do crossfade, ReplayGain, EQ, visualizers, lyrics, waveform generation, scrobbling, favorites, downloads, and offline playback change when audio is rendered remotely?
  - Which Android, iOS, and Desktop SDKs support discovery and session control, and does macOS use system AirPlay routing, an app-level API, or both?
  - What simulator coverage is possible, and which acceptance cases require real receivers and physical Apple devices?
  - Does the Google Cast SDK conflict with official F-Droid inclusion, requiring a capability-gated build flavor or a different discovery/transport approach?
- **Investigation output:** Produce a protocol and SDK comparison, Core session contract, credential and network-reachability threat model, receiver compatibility matrix, lifecycle/recovery test plan, and a recommendation for the smallest useful first implementation.

### Album Shuffle Radio

- **Status:** Idea
- **Concept:** Add a radio mode that selects albums in random order while playing every selected album in its canonical disc and track order before moving to another randomly selected album.
- **Scope boundary:** Do not build a proprietary saved "playlist of albums" format. OpenSubsonic pull request [#237, Add collections](https://github.com/opensubsonic/open-subsonic-api/pull/237), proposes an interoperable ordered collection whose items may include albums, songs, artists, playlists, genres, radio stations, and podcasts, with CRUD and item-reordering endpoints. If that proposal is accepted and adopted by supported servers, use the standard collection capability for user-curated album sequences. Until then, keep this idea limited to an ephemeral/generated random-album radio queue rather than a competing persisted collection model.
- **Standards caveat:** The current collection proposal defines storage and ordering of typed items, but not client playback-expansion rules. Naviamp would still need a Core-owned policy that expands each album into canonical disc/track order while preserving collection order. Recheck the final specification, advertised extension version, and real server implementations before planning support; the pull request remains open as of July 29, 2026.
- **Core behavior:** Model each album as an ordered queue group. Core owns album selection, de-duplication, queue replenishment, group boundaries, progression, persistence, and recovery; provider code supplies eligible albums and their canonical tracks, and hosts only render and invoke the shared mode.
- **Questions to answer:**
  - Should selection cover the whole library or support filters such as genre, year, artist, favorites, library folder, rating, or downloaded-only content?
  - How should multi-disc albums, bonus tracks, missing/unavailable tracks, compilations, duplicate releases, and albums with only one playable track be ordered and represented?
  - Should Next Track advance within the album while a separate Skip Album action jumps to the next group, and what should Previous do at an album boundary?
  - When playback is restored, should the current album resume at its saved position before another album is chosen?
  - How large should the upcoming album window be, and how should the mode avoid recently played albums without requiring the entire library to remain in memory?
  - How should shuffle, repeat, crossfade, ReplayGain, per-album playback profiles, offline availability, scrobbling, and audio/lyrics/waveform prefetch behave across album boundaries?
- **Acceptance shape:** Verify that album selection is random across a representative library, every chosen album remains internally ordered, no track is silently duplicated or omitted at replenishment boundaries, queue replacement cancels obsolete prefetch work, and session restoration preserves the active album group.

### Additional Providers: Plex, Jellyfin, and Bandcamp

- **Status:** Idea
- **Concept:** Expand Naviamp beyond its current providers with shared integrations for Plex, Jellyfin, and Bandcamp.
- **Bandcamp opportunity:** Bandcamp announced an open-beta Subsonic implementation on July 16, 2026. Users generate credentials in Fan Settings and connect to `https://bandcamp.com/api/subsonic`; the announced beta supports streaming and downloading a user's collection plus creating and editing playlists that synchronize with Bandcamp. Start with a compatibility audit against Naviamp's existing Subsonic/Navidrome provider before creating a separate provider implementation.
- **Provider architecture:** Authentication, session renewal, protocol calls, response interpretation, domain mapping, feature capabilities, playlist semantics, and provider-specific persistence mapping belong in each provider's `commonMain` module. Core consumes the same provider-neutral contracts, and Android, Desktop, and iOS must not acquire separate Plex, Jellyfin, or Bandcamp product implementations.
- **Questions to answer:**
  - Which existing Naviamp features can each provider support faithfully: browsing, search, multiple libraries, favorites, playlists, radio, lyrics, downloads, ReplayGain, scrobbling, artwork, related/sonic discovery, and server-side transcoding?
  - Can Plex and Jellyfin use stable, documented APIs and authentication flows without embedding web sessions, proprietary client secrets, or platform-specific SDK behavior?
  - Which Bandcamp Subsonic/OpenSubsonic endpoints and extensions are actually implemented in the beta, and how do its IDs, collection model, purchases, playlists, paging, formats, rate limits, and errors differ from Navidrome?
  - Should Bandcamp be a capability profile inside a reusable Subsonic provider family, a dedicated provider adapter sharing the Subsonic transport, or both?
  - How should provider-specific concepts be represented without leaking them into provider-neutral Core models or reducing features to the least common denominator?
- **Investigation output:** Build an endpoint/capability matrix from current official documentation and captured test responses, define authentication and credential-storage requirements, create contract tests and representative fixtures, and recommend an implementation order. Bandcamp beta behavior must be reverified before development because it may change.
- **Source:** [Bandcamp: Discover Improvements and Subsonic Implementation](https://blog.bandcamp.com/2026/07/16/discover-improvements-and-subsonic-implementation/)

### Classical Work and Movement Grouping

- **Status:** Idea
- **Source:** [Classical music-friendly Navidrome client?](https://old.reddit.com/r/navidrome/comments/1v18t49/classical_musicfriendly_navidrome_client/)
- **Requested experience:** Group an album's tracks by musical work, with each work containing its ordered movements, similar to the classical-music presentation in Apple Music. The linked discussion specifically asks for this behavior in established iOS and macOS Navidrome clients.
- **Why it may fit:** Classical libraries often depend on work and movement relationships, richer credits, original composition dates, performance or recording dates, and multi-disc sequencing. Adding metadata-aware grouping to the existing album view could serve these libraries without creating a separate client or weakening the current browsing model.
- **Important constraint from the discussion:** Client rendering is only half of the problem. Real libraries frequently have missing `WORK` and `MOVEMENT` tags, or put a movement title in the work field, even when they were tagged with MusicBrainz. Naviamp must degrade cleanly to the normal album track list and must not invent misleading groupings from unreliable metadata.
- **Questions to answer:**
  - Does Navidrome expose work, movement name, movement number, and movement total through the current OpenSubsonic responses Naviamp consumes, or would server/API work be required first?
  - Which composer, conductor, ensemble, performer, work, movement, opus/catalog number, period, recording, release, and disc fields does Navidrome currently index and expose through the APIs Naviamp can consume?
  - Can Naviamp group movements under a work while preserving the server's canonical track order, queue behavior, offline downloads, scrobbling, and navigation back to the containing release?
  - How should mixed albums behave when only some tracks have usable work/movement metadata, and what validation prevents one incorrectly tagged work per movement?
  - Should composer and conductor be first-class browsable entities, structured contributor links on existing pages, configurable library views, or some combination?
  - How should search, sorting, display titles, album artist fallbacks, compilations, multiple performances of one work, and incomplete or inconsistently tagged libraries behave?
  - Can the shared domain and UI models represent the richer relationships once, with the same browsing behavior on Android, Desktop, and iOS?
- **Products and approaches to compare:** NaviBeat reportedly groups works within albums on iPhone and Mac; MusiCHI is cited as a strong classical-library experience because it supplements file tags with its own classical database. Study their behavior and tradeoffs without copying implementation code.
- **Implementation notes to investigate later:** Inventory Navidrome and OpenSubsonic classical metadata support using real responses; assemble a representative test library containing well-tagged, partially tagged, and incorrectly tagged albums; document fallback and mixed-metadata rules; then prototype shared work-grouping models and album presentation. Treat an external classical database as a separate, substantially larger product decision rather than a prerequisite for useful tag-based grouping.

### Continuous Playback Profiles and Queue Groups

- **Status:** Idea
- **Concept:** Let users assign playback preferences to an album, playlist, or classical work so a deliberately continuous sequence can override the global playback settings. A symphony, live recording, concept album, DJ mix, or suite could use gapless transitions and album ReplayGain even when the user's normal preference is crossfade with track ReplayGain.
- **Server boundary:** OpenSubsonic can expose track and album ReplayGain values, plus newer work and movement metadata, but gapless/crossfade policy and grouped queue behavior are client concerns. Store these preferences in Naviamp's source-scoped local state unless a future interoperable server representation becomes available.
- **Playback profile options to investigate:**
  - Inherit every global setting by default, with explicit per-field overrides rather than copying the current global profile.
  - Transition mode: inherit, gapless, crossfade, or a deliberate pause; include crossfade duration when applicable.
  - ReplayGain mode: inherit, album, track, or off; retain the global preamp and clipping-protection policy unless there is a demonstrated need to override them too.
  - Shuffle and repeat behavior, sample-rate handling, equalizer preset, and volume normalization may be useful later, but should not be added before their interaction with bit-perfect playback and platform capabilities is clear.
- **Explicit grouping instead of detection:** Starting playback through `Play album`, `Play work`, or a configured playlist can create a queue group with a stable identity and ordered member occurrences. This avoids guessing from adjacent album IDs. Reordering, removing, shuffling, or independently enqueueing members must have documented rules for preserving, splitting, or dissolving the group.
- **Queue behavior:** Offer a mode where the group behaves like one logical track for queue insertion. `Play next` would mean “after the current work/album/group,” while an explicitly named `Play next track` action could remain available for users who want to interrupt it. Previous/next should still move between physical tracks by default so movement navigation remains practical; skipping the entire group should be a separate action.
- **Scope and precedence questions:**
  - Should profiles be supported for saved playlists, smart playlists, albums, works, and ad-hoc queue groups, and which should be implemented first?
  - If a playlist profile contains an album with its own profile, does the outer playlist profile win, do album overrides apply only while inside that album, or does the launch action ask the user?
  - Does an album preference apply only when launched as a complete ordered album, or also when one of its tracks happens to appear in another queue?
  - Should a playlist edited on the server retain its local profile by playlist ID, and how should deletion, recreation, source changes, and offline playback affect it?
  - How should queue restoration persist group boundaries, profile overrides, current member, and deferred `Play next` items without changing audible behavior after restart?
- **Shared-architecture requirement:** Model playback profiles, resolved precedence, queue-group boundaries, persistence, and commands in common code. Platform hosts should only apply capability-gated engine settings and expose native transport integrations. Android Auto, media notifications, desktop media keys, and future iOS controls must observe the same resolved group semantics.
- **Suggested first slice:** Add optional profiles to saved playlists and explicit `Play album`/`Play work` launches, limited to transition mode and ReplayGain mode. Represent the sequence as grouped queue occurrences while continuing to stream and scrobble its individual tracks. Add “Play next after this group” only after restoration, editing, shuffle, and repeat contracts are tested.

### Cross-Platform Typography and Spacing Polish

- **Status:** Idea
- **Concept:** Perform a dedicated visual-polish pass after the shared UI migration stabilizes, covering typography, spacing, density, alignment, and responsive sizing across Desktop, Android, and iOS.
- **Why it may fit:** Moving product surfaces into shared composition has exposed small differences in perceived font weight, line height, padding, and control density. Correcting these piecemeal during architecture work would make regressions harder to isolate; a focused pass can establish intentional shared tokens and explicit platform adaptations.
- **Areas to review:** Page and section titles, row heights, metadata hierarchy, icon-to-label spacing, forms and buttons, compact versus full Now Playing, bottom navigation, narrow Desktop windows, mobile safe areas, text truncation, and dynamic type or system font scaling.
- **Implementation notes to investigate later:** Capture representative screenshots at agreed viewport sizes on all platforms, define shared typography and spacing tokens before changing individual screens, preserve accessibility minimums, and use platform-specific adjustments only where native font metrics or input conventions require them.

### Word-by-Word Karaoke Lyrics

- **Status:** Idea
- **Concept:** Add support for Navidrome's word-by-word, or karaoke, lyrics so the active word can be highlighted within the current lyric line as playback advances.
- **Why it may fit:** Naviamp already supports synchronized line lyrics, offsets, prefetch, and cached lyric sidecars. Preserving word-level timing would make the lyrics view more expressive while fitting the existing playback-position and cache pipeline.
- **Behavior and presentation questions:**
  - Which Navidrome and OpenSubsonic response versions expose word timing, and how should Naviamp distinguish word-synced, line-synced, and unsynced lyrics?
  - Should the active word use a progressive fill, a discrete highlight, or a configurable presentation, and how should it behave with wrapping, punctuation, instrumental gaps, translations, and multiple lyric voices?
  - How should manual lyric offsets apply to both line and word timestamps without accumulating rounding or synchronization errors?
  - What accessibility behavior is needed for reduced motion, contrast, font scaling, screen readers, and users who prefer the existing line-only display?
  - Should karaoke rendering be automatic when word timing exists, or controlled by a Lyrics setting with a line-synchronized fallback?
- **Caching and compatibility:** Preserve word timing in the shared lyric model and persistent sidecar cache rather than flattening it into line-only text. Cache identity, prefetch, offline playback, source priority, and invalidation must follow the same rules as existing lyrics. Older cached entries and providers without word timing must continue to render as line-synced or plain lyrics without migration failures.
- **Shared-architecture requirement:** Parse provider-specific word timing in the Navidrome provider's `commonMain` mapping, represent timing and fallback semantics in shared domain/storage models, and implement playback-position selection and rendering in shared Core/UI. Platform hosts should not interpret or animate lyric timing independently.
- **Implementation notes to investigate later:** Capture representative Navidrome responses, including malformed and partially timed lyrics; confirm API/version capability detection; define a backward-compatible serialized cache model; and test seeking, pause/resume, crossfade transitions, track replacement, offsets, prefetch cancellation, and offline reuse before enabling karaoke presentation by default.

### Musixmatch Lyrics Source

- **Status:** Idea
- **Source:** [Myzel394/navidrome-musixmatch-plugin](https://github.com/Myzel394/navidrome-musixmatch-plugin)
- **Concept:** Investigate Musixmatch as an additional source of plain and synchronized lyrics, using the Navidrome plugin as a working reference and, where possible, consuming its results through Naviamp's existing Navidrome/OpenSubsonic lyrics path rather than duplicating the integration in every client.
- **Observed integration:** The plugin implements Navidrome's lyrics provider contract. It first uses Musixmatch's unofficial desktop endpoint at `apic-desktop.musixmatch.com/ws/1.1`: it obtains a short-lived anonymous token, calls `macro.subtitles.get`, prefers rich-synchronized timing converted to LRC, then LRC subtitles, then plain lyrics. Artist, title, and optional rounded duration help select a match. A `401` invalidates the cached token. An optional website-scraping fallback searches candidates and validates identity, but requires a `musixmatchUserToken` cookie and may also require a captcha cookie.
- **Preferred first investigation:** Install the plugin on a test Navidrome server and verify that its plain and synchronized output reaches Naviamp unchanged through the server API Naviamp already consumes. Record response versions, source attribution, timing precision, multi-lyric behavior, cache identity, offsets, offline reuse, failure behavior, and whether Navidrome exposes enough provenance to distinguish server-local, plugin, and embedded lyrics. If that path works, document the plugin as an optional server capability before considering direct client integration.
- **Direct-client alternative:** If a Naviamp-owned fallback is justified, implement Musixmatch protocol, response interpretation, match scoring, token refresh, and mapping once in shared provider code behind the existing lyrics contracts. Use Naviamp's shared HTTP abstraction with fixtures and explicit timeouts; Android, Desktop, and iOS must not acquire separate scrapers or token stores.
- **Risks and questions:**
  - The desktop API is unofficial and may change or block clients without warning; the plugin itself recommends low provider priority and frequent updates. What failure isolation, rate limiting, retry/backoff, kill switch, and source-priority behavior would keep existing Navidrome, embedded, sidecar, and LRCLIB lyrics reliable?
  - Do Musixmatch's terms and licensing permit Naviamp to fetch, cache, display offline, redistribute, or transform these lyrics, and what attribution or geographic restrictions apply? The reference implementation's MIT code license does not grant rights to Musixmatch's data or private endpoints.
  - Is shipping or asking users for a website session/captcha cookie acceptable? Treat those values as sensitive credentials, never analytics payloads, and prefer omitting website scraping unless its security, privacy, maintenance, and terms implications are acceptable.
  - How accurate are artist/title/duration matches for remasters, live recordings, translations, featured artists, classical works, compilations, and tracks with identical names? Preserve provenance and never silently replace a higher-confidence server or embedded result.
  - The reference plugin includes opt-out lookup analytics. A Naviamp implementation must make its own explicit telemetry decision and must not inherit third-party reporting as part of lyrics lookup.
- **Investigation output:** Produce a server-plugin acceptance matrix, captured OpenSubsonic responses, legal/terms review, source-priority and cache-invalidation design, representative match-quality corpus, outage/rate-limit tests, and a go/no-go decision between optional Navidrome-plugin documentation and a maintained shared Naviamp provider.

### Configurable Home Sections and Layouts

- **Status:** Idea
- **Concept:** Add an Experience setting that lets each user choose which sections appear on Home, reorder those sections, and select a list or grid presentation for each section where both layouts make sense.
- **Why it may fit:** Home contains several useful discovery and library summaries, but their value and preferred density vary by listener. Per-section choices would let users prioritize the content they use without requiring separate platform-specific Home screens.
- **Presentation reference:** Reuse the shared list/grid choice already available for album lists on artist detail pages, adapting it only where a Home section's content and interaction model support both forms.
- **Questions to answer:**
  - Which Home sections are required, optional, or capability-gated, and what default order preserves the current experience?
  - Should hidden sections retain their layout and position settings, and how should new sections be inserted after an upgrade?
  - Which sections genuinely support both list and grid layouts without losing important track, playlist, radio, or discovery actions?
  - How should reorder controls work accessibly with keyboard, touch, screen readers, and narrow screens?
  - Should these preferences participate in folder-based Settings Sync, and how should older clients preserve unknown section IDs?
- **Shared-architecture requirement:** Store ordered section IDs, visibility, and per-section layout in shared settings; validate and migrate them in Core; and render the same configured Home composition on Android, Desktop, and iOS.

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

### Apple CarPlay

- **Status:** Idea
- **Timing:** Begin after the thin iOS application can connect, browse, and play reliably. CarPlay must not become a prerequisite for proving the initial iOS host.
- **Concept:** Add a CarPlay experience for safely browsing and searching the Naviamp library, starting albums, artists, playlists, radio, and downloads, viewing the active queue and Now Playing information, and controlling playback.
- **Shared-architecture requirement:** Reuse the shared catalog-selection intents, browse/search policies, queue paging and limits, playback commands, and application runtime already consumed by the normal app and Android Auto. Do not build a separate CarPlay product model or second iOS runtime. Apple-specific templates, scene/session lifecycle, entitlement and capability handling, Now Playing integration, and vehicle-safe presentation remain thin iOS host adapters.
- **Questions to answer:**
  - Which CarPlay audio-app capabilities, templates, entitlements, review requirements, and simulator or physical-head-unit testing are required when implementation begins?
  - Which Android Auto browse/search contracts can become genuinely vehicle-platform-neutral, and which remain Android-specific because of `MediaBrowserCompat` or stable media IDs?
  - How should CarPlay reconnect to playback that began in the phone app, recover after process termination, and avoid constructing another playback engine or application runtime?
  - Which library, playlist, radio, downloaded/offline, search, queue, favorite, and related-track actions are safe and permitted while driving?
  - How should authenticated artwork, connection failures, offline state, multiple servers, and source switching appear without exposing phone-oriented dialogs in the vehicle UI?
- **Implementation notes to investigate later:** First validate the shared vehicle catalog and playback contracts with the thin iOS host. Then prototype the smallest supported CarPlay browse-to-play flow, Now Playing synchronization, remote commands, lifecycle reattachment, and offline behavior before expanding the surface.

### Desktop Dock and Taskbar Player Controls

- **Status:** Idea
- **Concept:** Add playback controls and useful status actions to Naviamp's desktop application icon through the native macOS Dock, Windows taskbar, and supported Linux desktop integrations.
- **Why it may fit:** Dock or taskbar controls provide quick access to playback without bringing the full window forward. The command meanings can reuse Naviamp's shared playback controller, while registration, menus, icon badges, previews, and operating-system lifecycle handling remain Desktop host responsibilities.
- **Controls and information to consider:** Play/pause, previous, next, stop, favorite, current track and artist, open or focus Now Playing, show or hide the main window, and quit Naviamp without accidentally terminating background playback where the platform distinguishes those actions.
- **Questions to answer:**
  - Which native surfaces are appropriate on macOS, Windows, and the Linux desktop environments Naviamp supports?
  - Should controls appear in a right-click icon menu, taskbar thumbnail toolbar, jump list, badge or progress indicator, system tray menu, or some combination?
  - How should actions behave when no track is loaded, the server is disconnected, or the main window has been closed while playback continues?
  - Can every action route into the existing shared playback command controller without creating a second application runtime?
  - Which dynamic metadata can be updated reliably without excessive operating-system calls or stale menus?
  - Should users be able to choose which commands appear, disable dynamic dock/taskbar content, or keep only standard window actions?
- **Implementation notes to investigate later:** Define a shared snapshot and action vocabulary that can also support mobile widgets and keyboard controls. Implement each operating system's icon/menu/taskbar adapter in the Desktop host, capability-gate unsupported presentation features, and test packaged applications rather than relying only on development launches.

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
