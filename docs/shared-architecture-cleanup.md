# Shared Architecture Cleanup

Status: Draft plan

Branch: `codex/shared-architecture-cleanup`

## Why this exists

Recent feature work touched too many files for behavior that should have one shared path. Favorites, ratings, metadata edits, queue saves, sleep timer behavior, mix builders, and playback controls all exposed the same problem: platform code still owns too much business logic and app orchestration.

The most visible symptom is `DesktopNaviampApp.kt` hitting the JVM method-size limit. The deeper problem is that the app has too many duplicated Android/Desktop controller paths and too much state/render wiring in platform roots.

## Ground rules

- Pause new feature work until this checklist is substantially complete.
- Prefer shared/domain call paths whenever logic does not require platform APIs.
- Platform code should adapt OS, lifecycle, storage, audio backend, notifications, and filesystem concerns only.
- Keep behavior unchanged while refactoring unless a checklist item explicitly calls out a product correction.
- Verify each extraction before stacking more changes.
- Build in the Unix style: small focused services with one clear job, composed into larger application flows.
- Shared services should compose other shared services where possible instead of duplicating orchestration in platform code.
- Files prefixed with `Desktop` or `Android` should be thin by default. If one grows, it needs a written reason.

## Success criteria

- Platform-prefixed files are thin adapters, not business-logic owners.
- Shared behavior lives in platform-agnostic services with focused responsibilities and explicit inputs/outputs.
- Larger application behavior is built by composing smaller shared services, not by copying logic into Android and Desktop routes/controllers.
- Provider calls, storage updates, playback decisions, queue operations, media metadata mutations, radio/mix generation, connection orchestration, diagnostics mapping, and settings decisions all have shared service entry points unless they truly require platform APIs.
- Android and Desktop use the same call path for the same user intent. Divergence must be limited to OS/lifecycle/UI shell differences.
- Adding or changing a shared behavior should usually touch shared domain/app/UI code plus at most a thin platform adapter, not parallel Android/Desktop implementations.
- Root app files and route composables are small enough that compiler method-size limits are not a risk. `DesktopNaviampApp.kt` should not need Compose compiler bytecode workarounds to build.
- The codebase has clear dependency direction: platform adapters call shared services; shared services do not reach upward into platform modules.
- The service layer itself stays modular: small services can be tested independently and composed into larger coordinators.

## Phase 1: Map and Freeze

- [ ] Inventory every `Android*` and `Desktop*` file and classify its responsibilities.
- [ ] Inventory duplicated platform controller, route, shell, cache, settings, playback, radio, playlist, library, diagnostics, and media responsibilities.
- [ ] Tag each responsibility as shared domain, shared UI, platform adapter, or platform lifecycle.
- [ ] Mark every platform-prefixed file as one of:
  - thin adapter already
  - candidate for extraction
  - temporary coordinator to split
  - legitimate platform boundary
- [ ] Identify all direct mutations of shared media state from platform files.
- [ ] Identify all direct provider mutation calls from platform files.
- [ ] Identify all places Android and Desktop rebuild equivalent UI/domain models separately.
- [ ] Identify all places Android and Desktop perform equivalent async orchestration separately.
- [ ] Document the desired dependency direction for app, domain, provider, storage, UI, and platform modules.
- [ ] Define a short decision rule for future work: shared service first, platform adapter second.

## Phase 2: Shared Service Shape

- [ ] Define service categories and naming conventions.
  - intent planners: pure services that turn state plus user intent into a plan
  - mutation coordinators: services that call providers/storage and return result models
  - state reducers: pure services that apply results to app state models
  - adapters: platform code for OS, lifecycle, audio session, filesystem, notifications, and window/device concerns
- [ ] Create shared result types for loading/status/error outcomes instead of hand-rolled platform strings.
- [ ] Prefer pure functions for state transitions and small classes for side-effectful coordination.
- [ ] Ensure service constructors take small dependencies rather than whole app shells.
- [ ] Add focused unit tests for each shared service before replacing platform callers.

## Phase 3: Shared Metadata Actions

- [ ] Move all favorite/rating/metadata mutation decision logic into shared domain services.
- [ ] Make `MediaMetadataMutationController` the single entry point for artist, album, and track metadata updates.
- [ ] Replace duplicated status/error handling in platform media controllers with shared result models.
- [ ] Reduce Android/Desktop media action controllers to adapter wiring.
- [ ] Add shared tests for mutation state transitions and provider capability handling.

## Phase 4: Shared Playlist Mutations

- [ ] Create a shared playlist mutation coordinator for save queue, add track, rename, and delete flows.
- [ ] Keep platform code responsible only for dialogs, text input, and user intent dispatch.
- [ ] Replace duplicated Android/Desktop queue-save paths with a single shared flow.
- [ ] Add tests for queue-to-playlist request construction and local state refresh.

## Phase 5: Shared Playback Orchestration

- [ ] Define a shared playback command surface for play, pause, resume, seek, previous, next, shuffle, repeat, and volume.
- [ ] Keep BASS interaction behind the existing playback engine boundary.
- [ ] Move sleep timer expiry and playback command decisions into shared domain/application code.
- [ ] Reduce platform playback controllers to lifecycle/audio-session/notification adapters.
- [ ] Add tests for sleep timer, repeat/shuffle, seek gating, and previous/next decisions.

## Phase 6: Shared App Orchestration

- [ ] Extract shared coordinators for connection/provider lifecycle.
- [ ] Extract shared coordinators for library sync/freshness.
- [ ] Extract shared coordinators for home loading and refresh.
- [ ] Extract shared coordinators for artist, album, playlist, search, downloads, and internet radio flows.
- [ ] Extract shared coordinators for artist mix, album mix, genre mix, and future generated queues.
- [ ] Make Android/Desktop app shells compose shared coordinators rather than owning equivalent orchestration.

## Phase 7: Platform Root Decomposition

- [ ] Split `DesktopNaviampApp.kt` into smaller state holders and route coordinators.
- [ ] Split oversized Android app shell/activity responsibilities using the same boundaries.
- [ ] Extract connection/provider setup into a desktop adapter plus shared connection coordinator.
- [ ] Extract library sync/freshness orchestration.
- [ ] Extract now-playing/session restoration orchestration.
- [ ] Extract mix-builder state wiring into shared application state where possible.
- [ ] Remove desktop Compose compiler bytecode workaround once root composables are small enough.
- [ ] Verify desktop compile fails no method-size limits without workaround.

## Phase 8: Shared UI and Route Contracts

- [ ] Audit shared UI models for platform-specific leakage.
- [ ] Move duplicated action catalog/menu item construction into shared UI/domain helpers.
- [ ] Ensure Android/Desktop route content takes shared state models instead of rebuilding equivalent models separately.
- [ ] Keep platform-specific layout only where viewport, window chrome, or lifecycle genuinely differs.

## Phase 9: Verification and Diff Discipline

- [ ] Add a lightweight architectural checklist to feature work: one shared path first, platform adapters second.
- [ ] Add focused tests around shared services before platform wiring.
- [ ] Establish expected build commands for each cleanup slice.
- [ ] Track file-count spread for representative changes.
- [ ] Before merging each slice, record what platform duplication was removed.
- [ ] For each slice, report how many platform-prefixed files got thinner.
- [ ] For each slice, report which shared services were created or composed.

## Verification commands

Use these after meaningful slices:

```powershell
.\gradlew.bat :core:domain:allTests
.\gradlew.bat :apps:android:assembleDebug
.\gradlew.bat "-Pnaviamp.bass.platform=windows-x64" :apps:desktop:compileKotlinDesktop
.\gradlew.bat "-Pnaviamp.bass.platform=windows-x64" :apps:desktop:packageReleaseDistributable
```

## First concrete slice

Start with metadata actions because the recent diff made the smell visible there. This is only the first entry point, not the boundary of the cleanup. The same extraction standard applies across playback, playlists, radio, mix builders, settings, cache, diagnostics, connection, library, search, app shells, and route state.

- [ ] Read both platform media action controllers side by side.
- [ ] Move duplicated status strings, provider capability checks, and state replacement decisions into `MediaMetadataMutationController`.
- [ ] Leave platform controllers with only provider references, coroutine scope, and callbacks into app state.
- [ ] Verify Android and desktop builds.
- [ ] Update this plan with exact files reduced and behavior preserved.
