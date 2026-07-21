# V2 Shared Action Parity Audit

> **Historical focused audit.** Several individual omissions listed below have since been fixed. The broader [V2 Core-First Platform Audit](v2-core-first-platform-audit.md) is the authoritative ownership matrix and Milestone 4 exit gate. This document remains as evidence of why shared callback types and shared composables are insufficient without one common product composition.

This audit records whether Naviamp's shared Compose UI also has shared product behavior. A common callback type is not sufficient: Android and Desktop currently construct independent callback graphs, and most callbacks have silent no-op defaults. That allows a host to compile while an action shown by the shared UI does nothing.

The audit covers the complete `NaviampAppShellActions` graph, `NaviampNowPlayingActions`, their Android and Desktop factories, and the shared composables that invoke them. It classifies behavior as shared product policy, legitimate host execution, capability-dependent presentation, or an accidental divergence.

## Architectural Finding

Android mounts `NaviampSharedAppShell`, while Desktop mounts `NaviampProductRouteContent` inside Desktop-specific shell chrome. Sharing the product composables is useful, but the two hosts still assemble their screen state and action graphs independently:

- Android assembles `NaviampAppShellActions` in `AndroidMainShellActions` and focused Android action factories.
- Desktop assembles the same type in `DesktopAppShellActionFactory`, `DesktopSharedContentActions`, and `DesktopNowPlayingShell`.
- `core:app` owns shared application coordinators but cannot currently compose UI actions because it depends only on `core:domain`.
- `core:ui` depends only on `core:domain`, so it also cannot consume the shared application graph.

The target is therefore not to move Android lambdas verbatim into `core:ui`. Product intent and result policy belong in common commands/controllers, platform operations belong behind narrow ports, and a common presentation composition layer must bind those owners to the shared UI contract. Host chrome may remain native, but Android, Desktop, and iOS must not rebuild feature behavior independently.

The current shell names illustrate the problem: `NaviampAppShellActions` is a shared data contract, while `AndroidMainShellActions` and `DesktopAppShellActionFactory` are platform factory files with inconsistent names. They are neither obvious counterparts nor cleanly different roles. The migration should replace their product wiring with the common composition root; any narrow platform adapters that survive should then use aligned role names rather than preserving this mismatch.

## Callback Inventory

| Contract area | Audit result | Required ownership |
| --- | --- | --- |
| Shell navigation | Divergent | Common navigation commands own route selection, detail history, and Now Playing presentation intent. Hosts only adapt window/overlay presentation and Android system back. |
| Home | Divergent orchestration | Common owner coordinates refresh lifecycle, status, route selection, and stable-item actions. Hosts supply provider loading and playback effects. |
| Search | Ambiguous/dead callback | Common owner defines query, debounce/search trigger, cancellation, clear, and status. Remove or use the currently unconsumed `onSearch` callback. |
| Library | Capability divergence hidden by no-ops | Model paging and alphabet-jump support explicitly. Common policy selects enabled actions; hosts supply paged or indexed data execution. |
| Albums | Confirmed missing Desktop behavior | Common command dispatcher owns play/radio/download/queue/playlist/favorite and row intent. Hosts resolve/load data and execute effects. |
| Artists | Confirmed missing Desktop behavior | Common dispatcher owns catalog/popular/similar-artist/album intent and failure policy. Hosts resolve/load data and execute effects. |
| Playlist list and smart-playlist editor | Mostly parallel, inconsistent failure policy | Common owner resolves selection and standardizes stale-source and authentication outcomes. Hosts retain Navidrome/native I/O. |
| Playlist detail | Confirmed missing Desktop behavior | Replace competing direct and generic callback paths with one required common dispatcher. |
| Media rows | Partially shared | Keep stable-ID resolution behind a supplied source, but make action coverage and unsupported-action behavior common and testable. |
| Downloads | Wiring parity present | Shared dispatch/job policy is already in place. Filesystem, provider transfer, picker, and background lifetime remain host execution. |
| Internet Radio | Wiring parity present | Shared UI owns edit-dialog presentation; common intent should make that interception explicit. Hosts retain stream/provider execution. |
| Mix builders and Sonic Path/Mix | Wiring parity present | Existing parallel host controllers should become execution ports when the common presentation root is created; no immediate callback omission was found. |
| Connection | Divergent | Common connection intent/result policy owns edit, lookup, validation, missing-source errors, and form mutation. Hosts retain credential, TLS, HTTP, provider, and picker operations. |
| Settings values and maintenance | Partly capability-dependent | Common owner performs product setting mutations. File/folder pickers, storage application, update checks, and native stats remain capability-gated host services. |
| Settings synchronization | Intentional host adapter | Common synchronization policy already exists; document URI/path selection and native document I/O remain host-owned. |
| Now Playing | Divergent and duplicated | Common controller interprets current-track, queue-item, selection, display, queue, sleep, and DJ requests. Hosts retain BASS, platform lifecycle, dialogs, and native outputs. |

## Confirmed Divergences

These are implementation findings, not hypothetical iOS risks.

### Required-action omissions

- Desktop playlist detail leaves `onPlay`, `onAddToQueue`, `onAddToPlaylist`, `onCreatePlaylistAndAdd`, `onCopy`, `onRename`, and `onDelete` at their default no-ops. Desktop instead supplies `onMediaItemAction`, but `NaviampPlaylistDetailContent` invokes the direct callbacks for these controls and uses the generic path only for media-item actions such as download.
- Desktop album detail leaves `onCreatePlaylistAndAdd` unwired. Its add-to-playlist handler also ignores the playlist choice already made by the shared UI and opens another host dialog.
- Desktop artist detail leaves create-playlist-and-add, direct album selection, album favorite toggling, and explicit external-similar-artist selection unwired. Its artist and album add-to-playlist handlers likewise ignore the shared selection and open host dialogs.
- Desktop Now Playing explicitly discards create-playlist-and-add for both the current track and queue items. Android executes both.
- Desktop connection actions omit edit-current-connection. Android exposes it.

These omissions are made invisible by default `{}` lambdas on required product actions. Required actions must become constructor requirements or flow through one exhaustive dispatcher. Truly optional actions must be represented by explicit capabilities, not by a callable no-op.

### Different product decisions behind matching callbacks

- Android route selection clears detail state/history and closes Now Playing; Desktop changes only its route. The detail-back fix moved one part of this policy common, but route-transition policy remains independently assembled.
- Android Home refresh performs provider lookup, loading/status transitions, and browse-state application inline. Desktop delegates to a host controller with different lifecycle behavior.
- Android search has an explicit search callback; Desktop uses query observation and debounce. The shared search composable does not call `onSearch`, leaving the contract and ownership unclear.
- Android Library implements load-more but not alphabet jump; Desktop implements alphabet jump but not load-more. These may be valid data-source capabilities, but the UI contract currently represents them as silent no-ops rather than capabilities.
- Android reports missing saved connections and playlists through shared status. Several Desktop lookup paths silently return or use different exceptions/messages.
- Android and Desktop independently interpret Now Playing requests, including selection lookup and add-to-queue behavior. Desktop adds a selected item only in narrower cases and discards create-playlist-and-add, while Android resolves and executes those actions.
- Collapse/close behavior in Now Playing is host-shaped: Android closes an overlay/detail, while Desktop routes to its last content route. The common owner should issue a presentation command; each host may adapt how that command is displayed.

### Redundant or ambiguous API paths

The action model often exposes both a feature-specific callback and a generic request dispatcher. Examples include playlist-detail direct actions beside `onMediaItemAction`, album selection beside `onAlbumAction`, and track-selection callbacks beside `onTrackAction`. This duplication caused Desktop to implement a pathway the shared composable did not invoke.

The migration must choose one canonical path for each user intent. Prefer typed, exhaustive request dispatchers for row/item action families and focused commands for screen-level operations. Remove dead callbacks after tests prove the common composables use the canonical path.

## Intentional Host Boundaries

The following differences do not need to be forced into common code:

- Android Activity, foreground service, MediaSession, notification, Android Auto, permissions, persisted URI grants, audio focus, wake locks, and service lifetime.
- Desktop window chrome, menus, updater, native dialogs, filesystem paths, packaging, and desktop media integration.
- Platform credential protection, TLS/client-certificate construction, concrete HTTP engines, database-driver creation, and native storage locations.
- BASS/native playback execution and platform audio-session integration.
- Native directory/document pickers. Common code owns the setting or synchronization intent and consumes the selected result.

These boundaries should implement narrow ports requested by common owners. They must not decide playlist semantics, route history, validation, retry/status policy, or which product action a visible shared control performs.

## Migration Order

- [ ] Define required common action commands and narrow execution ports; remove silent no-op defaults from required actions and add explicit capability states for optional ones.
  - [x] Playlist detail uses one required sealed command, exhaustive common dispatch, required host handlers, and common stale/invalid results. Unsupported commands are unrepresentable, and its competing direct and generic callbacks were removed.
  - [x] Album detail uses one required sealed command and required track-row command handler. Its direct callbacks, redundant track-selection path, and missing Desktop create-and-add behavior were removed.
  - [x] Artist detail uses required sealed artist and discography-album commands plus a required popular-track command handler. Its overlapping callbacks and confirmed Desktop selection, favorite, playlist, and external-link omissions were removed.
  - [x] The route-shared media dispatcher is required, its partial fallback and duplicate playlist callbacks are removed, and smart-playlist authoring/authentication is one required composed contract without no-op or password-bypass defaults.
  - [x] Smart-playlist load/update source resolution and stale-source failure are common; the prior silent Desktop update omission is closed.
  - [x] Resolved media handlers declare support explicitly, Android and Desktop consume one common result-producing dispatcher, and missing, invalid, or unsupported requests can no longer disappear silently.
  - [x] Host-facing album, artist, and playlist media commands are separate sealed families consumed through exhaustive common dispatchers; the remaining legacy request exists only inside shared presentation during direct-emission migration.
  - [x] Media product capabilities are one common baseline, not host-specific declarations. The Android arbitrary-artist gap was closed with shared domain catalog loading rather than preserved as platform divergence.
  - [ ] Apply the rule to the remaining action groups.
- [ ] Converge shell navigation, Home, Search, Library, connection, and settings intent policy.
- [ ] Converge media-row and smart-playlist action routing; correct every confirmed Desktop omission as part of adopting the common dispatcher. Playlist, album, and artist detail routing are complete.
- [ ] Converge Now Playing request interpretation while keeping BASS and host presentation effects behind ports.
- [ ] Add a common presentation-composition module or dependency arrangement that can consume both `core:app` and `core:ui` without reversing their current dependency direction.
- [ ] Construct one host-neutral screen-state/action graph from the shared application composition. Android and Desktop provide only platform-service and execution adapters.
- [ ] Apply the ADR cross-platform naming convention to the remaining genuine counterparts. Shared roles use a neutral or `Naviamp` name and equivalent host implementations use aligned `Android`, `Desktop`, and `Ios` prefixes; remove superseded platform product factories instead of renaming them into false symmetry.
- [ ] Add contract-completeness and parity tests that fail when a visible action is unimplemented, plus a host-neutral navigation test covering Home, Search, Library, album, artist, playlist, Downloads, radio, and Settings.
- [ ] Mount the same composition from the initial iOS wrapper without iOS-specific product controllers.

## Exit Gate

This audit is complete, but the ownership migration it identified is not. Milestone 4 cannot close merely because Android and Desktop render shared composables. It closes when the common composition graph can navigate the product with fake platform services, required actions cannot silently disappear, Android and Desktop pass the same action contract tests, and iOS needs only platform-service/execution adapters.
