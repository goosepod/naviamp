# Naviamp Follow-Up Ideas

This document tracks useful ideas that come up during the v2 migration but are not part of the active cross-platform checklist. Keep these scoped as investigation notes until they are promoted into the main plan, an issue, or a release branch.

## Status Key

- `Idea`: Captured for later review.
- `Investigating`: Actively researching feasibility and scope.
- `Planned`: Accepted and moved into a concrete implementation plan.
- `Implemented`: Shipped and verified.
- `Rejected`: Deliberately declined, with rationale.

## Promotion Checklist

Before moving an idea into the active v2 plan or a release branch:

- Confirm the provider or local-data source is available.
- Confirm the feature can behave consistently across Android, Desktop, and iOS or document capability-gated differences.
- Identify the shared owner module and the host-specific work, if any.
- Add focused tests or prototype evidence before committing to implementation.
- Record the decision in the main plan or a dedicated issue.

## Ideas Not Yet Completed

### Weblate Translation Management

- **Status:** Investigating
- **Concept:** Use Weblate as Naviamp's source of truth for community translations, with repository synchronization keeping the shared localization resources and translator-facing strings aligned.
- **Hosting opportunity:** Apply for Weblate's gratis Libre plan for Naviamp as a public libre project. The advertised Libre plan has the same limits as Weblate's 160k plan and is intended specifically for public projects that benefit from Weblate support.
- **Eligibility assessment (2026-08-05):** Do not start the 14-day trial yet.
  - Naviamp's GPLv3 source and English/Spanish Compose Multiplatform XML catalogs are public in the same GitHub repository, and Weblate supports this resource format.
  - Development began on 2026-05-08, so the project reaches Weblate's minimum three-month activity threshold on 2026-08-08. Commit activity is substantial, but the current single-contributor history may still receive discretionary review under the "reasonable number of contributions" requirement.
  - The README does not yet mention Weblate. Add a translation section and Weblate link during trial setup, before requesting approval.
  - Naviamp bundles proprietary, non-commercial BASS binaries. Ask Weblate whether this separately licensed playback dependency is compatible with its Libre-project requirement; do not imply that the complete dependency chain is FLOSS.
  - Forgejo is the canonical repository and GitHub is a one-way public mirror. Define a reviewed path for Weblate translation commits to return to Forgejo without making GitHub an independent source of truth.
- **Next decision:** Reassess after 2026-08-08. The README and in-app BASS disclosures are now present; start the trial only when the repository write-back workflow and catalog validation are ready to complete within the 14-day approval window.
- **Shared-architecture requirement:** Keep translatable product strings and locale behavior in shared resources wherever possible. Android, Desktop, and iOS hosts should contribute only genuinely platform-owned text such as operating-system permission descriptions or packaging metadata, and should not develop independent translation catalogs for shared UI.
- **Questions to answer:**
  - Should Weblate write to a dedicated GitHub translation branch for manual import into Forgejo, or can a project-owned bridge safely submit changes to the canonical repository?
  - Which locales, plural rules, placeholders, markup, screenshots, glossary terms, contributor credit, review thresholds, and stale-string policies should be configured before inviting translators?
  - How will CI validate placeholder compatibility, locale completeness, encoding, fallback behavior, and compilation across Android, Desktop, and iOS?
- **Implementation output:** Confirm Libre-plan eligibility, create the Weblate project and component configuration, document translator and maintainer workflows, import the existing catalogs, add automated validation, and verify a complete translation round trip from Weblate through review to all three clients.
- **Source:** [Weblate hosting and Libre plan](https://weblate.org/en/hosting/)

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
- **Investigation output:** Produce a protocol and SDK comparison, Core session contract, credential and network-reachability threat model, receiver compatibility matrix, lifecycle/recovery test plan, and a recommendation for the smallest useful first implementation.

### Durable Start Radio Session History

- **Status:** Implemented on `feature/home-visibility-radio-history`.
- **Delivered:** Every generated **Start Radio** queue is now stored as a distinct shared session with its source identity, start time, artwork, seed metadata, and exact generated track list. Repeated runs of the same seed remain separate, appear in newest-first order on Home, survive app relaunches through the existing portable settings store, participate in Settings Sync, and reopen the saved queue instead of generating a different one.
- **Compatibility and retention:** The shared controller retains the newest 50 records across sources and filters source-owned sessions for the active source. Older seed-only entries remain readable and continue to regenerate from their saved seed. The existing common clear operation removes the complete history; no platform-specific history behavior or schema migration was added.
- **Behavior decisions:** The saved queue is authoritative, so a later DJ-preference change does not alter an old session. Repeated requests create separate records; removed sources' records are hidden unless that source becomes active again. Unavailable tracks enter the normal shared playback failure path. Retention is bounded globally, complete-history clearing remains available through the common controller, and an individual-delete UI is not part of this increment.
- **Verification:** Common controller, portable serialization, shared Home mapping, settings-sync, Core replay, source-filtering, retention, duplicate-session, and clear-history tests cover the durable contract. Android, Desktop, and iOS consume the same common model and behavior.

### Album Shuffle Radio

- **Status:** Idea
- **Priority:** Backburner
- **Concept:** Add a radio mode that selects albums in random order while playing every selected album in its canonical disc and track order before moving to another randomly selected album.
- **Decision:** Do not implement this yet. Revisit it after an official OpenSubsonic/Subsonic capability for album collections is finalized and supported by Navidrome; Naviamp should not create a proprietary persisted model or an interim radio implementation while the standard is unsettled.
- **Standards caveat:** A collection capability may define storage and ordering of typed items without defining client playback-expansion rules. When official support exists, Naviamp will still need a Core-owned policy that expands each album into canonical disc/track order while preserving collection order. Recheck the final specification, advertised extension version, and real server implementations before promoting this idea.
- **Core behavior:** Model each album as an ordered queue group. Core owns album selection, de-duplication, queue replenishment, group boundaries, progression, persistence, and recovery; provider code supplies eligible albums and their canonical tracks, and hosts only render and invoke the shared mode.
- **Design notes and remaining accessibility follow-up:**
  - Should selection cover the whole library or support filters such as genre, year, artist, favorites, library folder, rating, or downloaded-only content?
  - How should multi-disc albums, bonus tracks, missing/unavailable tracks, compilations, duplicate releases, and albums with only one playable track be ordered and represented?
  - Should Next Track advance within the album while a separate Skip Album action jumps to the next group, and what should Previous do at an album boundary?
  - When playback is restored, should the current album resume at its saved position before another album is chosen?
  - How large should the upcoming album window be, and how should the mode avoid recently played albums without requiring the entire library to remain in memory?
  - How should shuffle, repeat, crossfade, ReplayGain, per-album playback profiles, offline availability, scrobbling, and audio/lyrics/waveform prefetch behave across album boundaries?
- **Acceptance shape:** Verify that album selection is random across a representative library, every chosen album remains internally ordered, no track is silently duplicated or omitted at replenishment boundaries, queue replacement cancels obsolete prefetch work, and session restoration preserves the active album group.

### Additional Providers: Subsonic, Jellyfin, and Bandcamp

- **Status:** Implemented for `v2.0.0-alpha.4`; compatibility expansion and the remaining acceptance matrix stay open.
- **Concept:** Expand Naviamp beyond Navidrome through generic Subsonic/OpenSubsonic, Jellyfin, and Bandcamp compatibility.
- **Active discovery:** See [`provider-expansion-discovery.md`](provider-expansion-discovery.md) for the current protocol comparison, connection-selector proposal, architectural prerequisite, capability gaps, and implementation sequence.
- **Bandcamp opportunity:** Bandcamp announced an open-beta Subsonic implementation on July 16, 2026. Users generate credentials in Fan Settings and connect to `https://bandcamp.com/api/subsonic`; the announced beta supports streaming and downloading a user's collection plus creating and editing playlists that synchronize with Bandcamp. Start with a compatibility audit against Naviamp's existing Subsonic/Navidrome provider before creating a separate provider implementation.
- **Provider architecture:** Authentication, session renewal, protocol calls, response interpretation, domain mapping, feature capabilities, playlist semantics, and provider-specific persistence mapping belong in each provider's `commonMain` module. Core consumes the same provider-neutral contracts, and Android, Desktop, and iOS must not acquire separate Jellyfin or Bandcamp product implementations.
- **Questions to answer:**
  - Which existing Naviamp features can each provider support faithfully: browsing, search, multiple libraries, favorites, playlists, radio, lyrics, downloads, ReplayGain, scrobbling, artwork, related/sonic discovery, and server-side transcoding?
  - Can Jellyfin use stable, documented APIs and authentication flows without embedding web sessions, proprietary client secrets, or platform-specific SDK behavior?
  - Which Bandcamp Subsonic/OpenSubsonic endpoints and extensions are actually implemented in the beta, and how do its IDs, collection model, purchases, playlists, paging, formats, rate limits, and errors differ from Navidrome?
  - Should Bandcamp be a capability profile inside a reusable Subsonic provider family, a dedicated provider adapter sharing the Subsonic transport, or both?
  - How should provider-specific concepts be represented without leaking them into provider-neutral Core models or reducing features to the least common denominator?
- **Investigation output:** Build an endpoint/capability matrix from current official documentation and captured test responses, define authentication and credential-storage requirements, create contract tests and representative fixtures, and implement in the accepted order: generic Subsonic/OpenSubsonic, Jellyfin, then Bandcamp. Bandcamp beta behavior must be reverified before development because it may change.
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

- **Status:** Playback profiles and queue groups shipped in `v2.2.0`; group-aware Play next is implemented on the post-`v2.2.0` development line, while work launches remain a follow-up slice.
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
- **Implemented behavior:** Source-scoped album, playlist, and future work profile records support per-field transition and ReplayGain overrides. Album and playlist detail screens share one profile editor with per-field inheritance, crossfade duration, and an explicit return to global settings. Ordered album launches and configured playlist launches create explicit queue groups; Add to Queue also preserves a saved album profile. Group state survives session restoration, history pruning, and direct selection of another member. The shared playback engine applies a profile only within its boundary and returns to global transition settings when leaving it. An independently queued track does not inherit its album's profile, and a playlist-launched group uses the playlist profile instead of nested album profiles. Stats for Nerds exposes the active and upcoming resolution. Queue mutation preserves or splits unaffected contiguous runs conservatively. `Play next` now keeps the active group together and preserves request order after its boundary; `Play next track` explicitly interrupts the group and resumes its remaining members afterward. Both rules are Core-owned, survive session restoration through the existing group and priority state, and protect the scheduled sequence from upcoming-track shuffle. Work launches remain a follow-up slice.

### Multichannel Playback and Stereo Downmixing

- **Status:** Idea
- **Concept:** Treat channel layout as an explicit playback capability. Preserve and render multichannel audio when the selected output device and platform path support it, while also offering a deliberate, high-quality downmix to stereo for headphones, stereo speakers, and devices that cannot accept the source layout.
- **Existing foundation:** BASS can decode and render multichannel streams, and Naviamp already routes native playback through the shared `CoreBassPlaybackEngine` and narrow platform backends. The investigation should determine where Naviamp currently assumes stereo, drops channel-layout information, configures mixers or output devices as two-channel, or leaves platform-specific defaults to make an implicit downmix.
- **Product behavior to define:**
  - Add a shared output-channel preference such as **Automatic**, **Preserve multichannel**, and **Downmix to stereo**, capability-gating choices that the active route cannot support.
  - In Automatic mode, prefer the source layout only when the active output can render it correctly; otherwise use Naviamp's tested stereo downmix rather than relying on undocumented device behavior.
  - Show the detected source channel count/layout and effective output mode in Stats for Nerds so users can tell whether audio is preserved or downmixed.
  - Decide whether the preference is global, remembered per output device, or both, and define behavior when a route changes during playback.
- **Downmix requirements:** Define and test a channel matrix for common layouts, including center, surround, rear, and LFE handling; preserve dialogue and overall balance; reserve enough headroom to avoid clipping when channels are summed; and document how ReplayGain and clipping prevention interact with the matrix. Unknown or ambiguous layouts must fall back safely instead of silently assigning channels incorrectly.
- **Shared-architecture requirement:** Core owns source/effective channel models, output-mode preference, capability decisions, downmix policy and coefficients, settings persistence/sync, diagnostics, and fallback behavior. Android, Desktop, and iOS adapters may expose only the native device/route capabilities and apply the BASS/BASSmix channel configuration or unavoidable operating-system audio-session operation.
- **Interactions to verify:** Gapless transitions, crossfade, prepared-next playback, ReplayGain, equalizer processing, sample-rate matching and conversion, volume, visualizers, waveform analysis, downloads, transcoded streams, Bluetooth/HDMI/USB/AirPlay routes, and route changes must not accidentally collapse channels, swap layouts, double-apply gain, or restart playback unnecessarily.
- **Investigation and acceptance work:**
  - Audit BASS/BASSmix stream creation, mixer channel counts, output initialization, device enumeration, and platform audio-session configuration on Android, Desktop, and iOS.
  - Build representative fixtures for mono, stereo, quad, 5.1, and 7.1 content in formats Naviamp officially supports, with identifiable signals in every channel.
  - Add shared matrix/fallback tests and native backend tests that verify reported source channels, selected output channels, coefficient application, headroom, and route-change behavior.
  - Run physical-device acceptance with stereo headphones/speakers and available HDMI, USB, surround, Bluetooth, and Apple routes; simulators and fake backends are insufficient to claim multichannel output support.
  - Confirm a multichannel source reaches every speaker in the correct position on a capable route, produces a balanced and unclipped stereo result when downmixed, and degrades predictably when layout metadata or device capabilities are incomplete.

### Cross-Platform Typography and Spacing Polish

- **Status:** Investigating
- **Typography result:** The product typography architecture is implemented in Core. `NaviampTypography.kt` loads the shared Nunito Sans resource and applies it to every Material 3 typography role, while `NaviampSharedUi.kt` installs that typography for the common application rendered by Android, Desktop, and iOS. Platform hosts do not maintain separate product typography systems.
- **Remaining discrepancy:** Desktop's separate native Stats for Nerds window creates its own `MaterialTheme` without the shared typography. Its window shell is legitimately Desktop-owned, but it should consume the Core typography rather than falling back to the Compose default.
- **Remaining scope:** Keep only the visual acceptance and spacing work open: representative cross-platform screenshots, spacing and responsive-size review, accessibility minimums, text truncation, mobile safe areas, narrow Desktop layouts, and dynamic type or system font scaling. Shared composition prevents policy duplication, but it does not by itself prove that different font renderers, viewport sizes, and accessibility settings produce acceptable layouts.
- **Next step:** Route the Desktop diagnostics window through the shared typography, then either complete the visual-acceptance checklist in `v2-cross-platform-plan.md` or split that acceptance work into its own focused follow-up item.

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

## Completed Ideas

### Cross-Platform BASS Add-On Usage Audit

- **Status:** Done
- **Concept:** Audit which vendored BASS add-ons Naviamp actually loads and uses for real playback, analysis, effects, and supported library formats, then remove add-ons that provide no product value.
- **Scope:** Compare Android, Desktop, and iOS as one playback product. Record each add-on's call sites, dynamic-load result, formats or features it enables, representative test media, package-size cost, and whether the operating system already supplies an equivalent codec.
- **Important constraint:** Do not remove a library merely because a narrow acceptance library does not contain its format. A removal needs evidence from the supported-format contract and tests showing that Core capability claims, provider transcoding/original-stream behavior, offline playback, waveform analysis, crossfade/mixing, EQ, and visualizers remain correct.
- **Desired outcome:** Define one intentional cross-platform base inventory plus documented platform substitutions, remove unused binaries/load attempts/build metadata, and add package verification that prevents the inventories from drifting accidentally.
- **Completed so far:** Defined the shared decoder inventory; separated codec plugins from directly linked feature libraries; added Android plugin registration and diagnostics; enforced Android, Desktop, and iOS package inventories; removed unused effects/loudness components and a license-problematic niche decoder; added BASS/OpenSSL disclosures and the GPL linking exception; and refreshed BASSWEBM, BASS_SSL, and BASSOPUS from verified upstream archives.
- **Verified so far:** Clean Core tests, Android package/runtime loading, Desktop native inventory/plugin tests, and iOS simulator inventory/plugin tests all pass with the reduced dependency set.
- **Completion:** The add-on usage audit and resulting inventory cleanup are complete. The remaining supported-format fixtures, release-platform playback matrix, and artifact-size comparisons are ongoing release acceptance coverage rather than unfinished add-on-usage work. Further optional codec removals remain separate decisions and must follow the supported-format evidence rule above.
- **Audit record:** See [`bass-addon-usage-audit.md`](bass-addon-usage-audit.md) for the evidence, platform matrix, findings, decisions, and follow-on acceptance checklist.

### Navidrome Album Information on Album Detail

- **Status:** Implemented
- **Concept:** When Navidrome provides album information analogous to its artist information, add that metadata to the shared album detail page.
- **Independent visibility controls:** Add separate settings for showing artist information and showing album information. Each entity must be independently enabled or disabled; changing one setting must not affect the other.
- **Investigation first:** Identify the Navidrome/OpenSubsonic endpoint, response fields, capability/version requirements, attribution, and empty or partial response behavior. Confirm which data is server-owned and which may originate from external metadata services before defining the provider-neutral model.
- **Presentation questions:** Decide which available fields belong in the primary album detail layout, which should be expandable, how links and attribution should appear, where the two visibility controls belong in Settings, and how detail pages should degrade when information is disabled or unavailable.
- **Shared-architecture requirement:** Map Navidrome-specific responses in the provider's `commonMain` code, expose optional album information through shared provider-neutral contracts, and render it in the Core-owned album detail UI so Android, Desktop, and iOS receive the feature together. Store and validate the two visibility preferences independently in shared settings, including Settings Sync if applicable.
- **Acceptance shape:** Verify complete, partial, missing, malformed, and unavailable album-information responses; all four combinations of the artist and album information settings; source switching and stale-request cancellation; preference persistence and synchronization; and consistent rendering and accessibility across all three hosts.
- **Implemented:** Naviamp loads `getAlbumInfo2` alongside album details, maps and caches its optional notes, MusicBrainz ID, and artwork URLs in shared provider-neutral models, and renders notes with expandable text plus provider artwork on the shared album detail page. Failures or malformed/missing information do not fail the album itself. Independent shared interface settings control artist and album information, persist through the normal settings store, and participate in Settings Sync.

### NaviBeat Mixes Home Shelf

- **Status:** Done on `feature/navibeat-mixes`
- **Concept:** Recognize playlists generated by the [NaviBeat Mixes Navidrome plugin](https://github.com/nenadjokic/navibeat-mixes), remove recognized plugin mixes from Naviamp's normal Playlists collection, and present them in a dedicated shared Home shelf similar to NaviBeat.
- **Plugin behavior:** The server plugin creates and refreshes up to 23 ordinary Subsonic playlists, including time-of-day, Rediscover, New Music, loved, repeat, essentials, discovery, genre, artist, daily, decade, and Wrapped mixes. It requires Navidrome 0.63.1 or newer and exposes no client-specific endpoint.
- **Canonical detection:** Parse the machine-readable line in the playlist comment, keyed strictly by the `nb1:` schema prefix. Its colon-separated fields identify the schema version, mix kind, slot, generation date, mode, and track count. Never identify a mix by playlist name or configurable prefix. Malformed, unknown-version, or truncated markers must remain ordinary playlists so Naviamp never hides a user-created playlist accidentally.
- **Home behavior:** Show the shelf only when at least one valid plugin mix exists. Put the mix for the user's current part of day first, use stable generated artwork for each mix instead of the normal playlist mosaic, hide the machine line, and show a concise state such as `Still learning you`, `Updated today`, or `Updated yesterday` derived from the marker and human-readable description. Opening and playing a tile should continue to use Naviamp's standard shared playlist detail, queue, download, favorite, and offline behavior.
- **Playlist behavior:** Filter only positively identified `nb1:` playlists out of the normal Playlists screen and playlist summaries. Preserve their normal provider IDs and server ownership; Naviamp must not copy, rename, or create a second local representation. Define whether playlist selection dialogs should include these mixes even though the browsing screen does not.
- **Shared-architecture requirement:** Add provider-neutral playlist provenance/mix metadata in Core, parse the Navidrome comment in the provider's `commonMain` mapping, partition normal and recognized playlists in shared controllers, and render the Home shelf in shared UI. Hosts must not reproduce marker parsing, ordering, cover generation, or visibility rules.
- **Investigation and acceptance:** Confirm that Naviamp currently receives the full playlist comment through its OpenSubsonic mapping and cache, document every `nb1` kind/mode and forward-compatibility rule from representative plugin fixtures, and compare Naviamp against NaviBeat for time-zone/daypart ordering, stable covers, learning/freshness labels, empty or disabled mixes, plugin removal, source switching, refreshes, offline cache, localization, accessibility, and malformed markers.
- **Source:** [NaviBeat Mixes repository and client-recognition format](https://github.com/nenadjokic/navibeat-mixes)
- **Implemented:** Naviamp recognizes valid `nb1:` playlist markers in the shared Navidrome mapping, preserves their provider identity, excludes them from ordinary playlist browsing, and presents them as a dedicated shared Home section with stable generated artwork and freshness/learning labels. Malformed and unknown markers remain ordinary playlists. Mix selection continues through the standard shared playlist detail and playback behavior.

### Word-by-Word Karaoke Lyrics

- **Status:** Done for `v2.0.0-alpha.3`
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
- **Implementation progress:**
  - [x] Negotiate OpenSubsonic `songLyrics` version 2 and request enhanced lyrics from Navidrome.
  - [x] Preserve lyric kind, agents, cue-line intervals, word cues, UTF-8 byte ranges, and explicit cue end-times in the shared domain and backward-compatible sidecar cache.
  - [x] Prefer karaoke-capable provider responses over line-synced and plain alternatives.
  - [x] Carry cue timing through the shared UI model and progressively highlight the active word without replacing the provider's full line text.
  - [x] Apply the existing manual lyric offset to line and cue timing without modifying stored timestamps.
  - [x] Refresh legacy line-only cache payloads once so an upgraded client can discover enhanced cues while preserving old lyrics if the provider has no replacement.
  - [x] Add a shared timing preference for first available, plain, line-synced, or word-synced display. Richer cached lyrics are projected down for display without discarding their stored timing.
  - [x] Check persistent lyrics caches before any server request, audio-tag read, or online request; immediately reuse a cached result when it can satisfy the selected timing.
  - [x] Separate download timing from display timing so users can cache word-synced lyrics while normally displaying line-synced or plain lyrics.
  - [x] Add an inline Text/Lines/Words selector whose selected, available, and unavailable states reflect the current lyric payload and persist to Settings.
  - [x] Verify plain, line-synced, and word-synced tracks across macOS, Android, and iOS, including timing changes, manual scrolling, line-click seeking, timeline scrubbing, and track changes.
  - [x] Validate the shared timing, cache-projection, presentation, persistence, and connection-error behavior with common tests and Android, Desktop, and iOS builds.
- **Completion note:** The shared fallback model keeps malformed, partial, older cached, and less-capable provider responses usable without requiring platform-specific lyric behavior. Broader accessibility and unusual-provider fixtures remain ongoing regression coverage rather than blockers for the completed feature.

### LRCMUse Online Lyrics Source

- **Status:** Implemented for `v2.0.0-beta.1`; replaces the removed direct Musixmatch client.
- **Source:** [LRCMUse API documentation](https://lrcmux.dev/docs) and [MIT-licensed server source](https://github.com/f1nniboy/lrcmux).
- **Concept:** Use LRCMUse's public, keyless aggregation API as Naviamp's rich online-lyrics provider. The service fans out to multiple lyrics providers, chooses the best available match, supports self-hosting, and returns plain, line-synchronized, or word-synchronized results through one documented contract.
- **Implementation:** Core requests the native `/get` JSON response with artist, title, album, duration, and the highest acceptable synchronization level. It validates returned identity and duration, preserves absolute millisecond line/word timing and UTF-8 byte ranges, records the selected upstream source in cached metadata, and treats malformed, instrumental, mismatched, unavailable, or lower-timing responses as normal provider results or misses. Android, Desktop, and iOS supply only their existing shared HTTP engines.
- **Cache and fallback behavior:** LRCMUse uses a new `lrcmux` cache identity, so payloads from the removed Musixmatch implementation are not reused. The existing source-order and timing projection continue to check server, embedded, cached, LRCMUse, and LRCLIB results according to shared settings without exposing service-specific choices in the UI.
- **Release result:** Naviamp no longer obtains or stores a Musixmatch token, calls Musixmatch's unofficial desktop endpoint, impersonates a browser, or supports website/captcha-cookie scraping. LRCMUse requires no Naviamp credential and its public server advertises sane rate limits; upstream lyric rights and availability still belong to the selected source and should not be represented as Naviamp-owned content.
- **Verification:** Shared fixtures cover word timing, UTF-8 ranges, line/plain fallback, malformed data, instrumental results, mismatched identity, query construction, and provider capabilities. A metadata-only live request against API v1.7.0 confirmed native JSON shape and millisecond timing without adding live-network dependencies to the test suite.

### Configurable Home Sections and Layouts

- **Status:** Done; every current Home section uses the shared section/page foundation, persistent presentation settings, visibility, and persistent section ordering.
- **Concept:** Treat every Home section as one shared display unit with a stable ID, title, item type, action policy, supported layouts, Home layout, full-page layout, capability/empty-state policy, visibility, and order. The same unit must render both its Home summary and a dedicated page, rather than maintaining separate bespoke screens.
- **Dedicated pages:** Section titles are links. Selecting a title opens a Core-owned page bearing that title, a back action to Home, and the complete section contents. Begin with Mixes for You and Navibeat Mixes, then migrate the remaining album, playlist, radio, station, track, and discovery sections onto the same route and display contract.
- **Desktop rails:** Horizontally scrolling Home presentations need visible previous/next controls so pointer users are not required to know the Shift-plus-wheel gesture. Touch/trackpad swiping remains available.
- **Layout controls:** Dedicated section pages support List and Grid. Home sections support List, Grid, and Carousel; Carousel is the current horizontally scrolling mixes presentation. Keep the Home and dedicated-page choices separate so Carousel can never be selected for a dedicated page.
- **Experience settings:** Settings > Experience > Home Screen owns each section's presentation and order. All current sections persist independent Home (List, Grid, or Carousel) and dedicated-page (List or Grid) choices through normal settings storage and Settings Sync. The ordering editor reuses the playlist editor's live reorder interaction: neighboring rows move out of the way while a section is being dragged, and the order continuously previews before drop. Unavailable/capability-gated sections retain their positions, and unknown future section IDs are preserved.
- **Completed navigation behavior:** Dedicated pages open at the top, while returning to Home restores the prior Home scroll position.
- **Visibility:** Settings > Experience > Home Screen can hide or show each section. Hidden sections retain their saved Home layout, dedicated-page layout, item limit, and order, and visibility participates in Settings Sync.
- **Why it may fit:** Home contains several useful discovery and library summaries, but their value and preferred density vary by listener. Per-section choices would let users prioritize the content they use without requiring separate platform-specific Home screens.
- **Presentation reference:** Reuse the shared list/grid choice already available for album lists on artist detail pages, adapting it only where a Home section's content and interaction model support both forms.
- **Questions to answer:**
  - Which Home sections are required, optional, or capability-gated, and what default order preserves the current experience?
  - Hidden sections retain their layout and position; new sections use their default presentation and are appended after known saved section IDs.
  - Which sections genuinely support both list and grid layouts without losing important track, playlist, radio, or discovery actions?
  - How should reorder controls work accessibly with keyboard, touch, screen readers, and narrow screens?
  - Visibility participates in folder-based Settings Sync, and normalization preserves unknown section IDs.
- **Shared-architecture requirement:** Store ordered section IDs, visibility, and per-section layout in shared settings; validate and migrate them in Core; and render the same configured Home composition on Android, Desktop, and iOS.

### Configurable Keyboard Playback Controls

- **Status:** Done (August 8, 2026)
- **Delivered:** Desktop users can enable one global-shortcut master switch and customize Play/Pause, Previous, Next, Volume Up, Volume Down, and Bring to Front from Experience > Keyboard Shortcuts. Defaults are platform-specific, duplicate bindings are resolved when saved, unavailable/conflicting registrations are shown beside the affected command, individual bindings can be disabled, and each platform can be reset to its defaults.
- **Playback behavior:** Previous enters the existing shared Previous-button policy, volume moves in five-percent increments, and every command other than native window activation is interpreted by Core.
- **Focused-window behavior:** Space always toggles playback while the Naviamp window is focused. It is fixed and cannot be disabled; shared text-entry focus tracking prevents it from firing while the user is typing, and held-key repeats are ignored.
- **Platform boundary:** Core owns binding models, defaults, normalization, persistence/settings sync, settings UI, statuses, and command routing. Desktop contains only Windows `RegisterHotKey`, macOS Carbon hot-key registration, Linux X11 grabs, and AWT window activation/key delivery.

## Ideas Not Planned

### F-Droid Distribution

- **Status:** Rejected
- **Decision:** Do not pursue inclusion in the official F-Droid repository.
- **Rationale:** Naviamp's supported Android playback product depends on proprietary, prebuilt BASS and BASS add-on libraries. F-Droid requires a fully FLOSS dependency chain that its infrastructure can build from source; its upstream-binary and reproducible-build paths do not waive that requirement.
- **Reconsider only if:** Naviamp adopts and commits to maintaining a fully FLOSS playback backend that can provide an acceptable Android product without any BASS binaries. That would be a substantial playback-engine and product-parity project, not distribution packaging work.
- **Alternative:** Publish signed Android APKs through Naviamp's GitHub releases for installation and updates with Obtainium.

### BlurHash versus ThumbHash Artwork Placeholders and Backgrounds

- **Status:** Rejected
- **Decision:** Do not add BlurHash or ThumbHash.
- **Rationale:** Naviamp does not need another generated artwork representation or cache layer. The existing artwork loading, caching, transition, Aurora, and Album Blur behavior already covers the intended product experience, while either hash would add implementation and maintenance complexity without a demonstrated user problem.
