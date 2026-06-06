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

## Success criteria

- Metadata/favorite/rating actions are implemented once in shared code and called through thin Android/Desktop adapters.
- Queue-to-playlist behavior is implemented once in shared code and reused by both platforms.
- Sleep timer behavior is implemented once in shared code and platform code only wires lifecycle/playback effects.
- Playback commands have a shared orchestration surface above the platform audio backend.
- `DesktopNaviampApp.kt` no longer needs Compose compiler bytecode workarounds to build.
- A representative shared action change should not require touching both platform controllers unless platform UI or lifecycle is genuinely different.

## Phase 1: Map and Freeze

- [ ] Inventory duplicated platform controller responsibilities.
  - `AndroidMediaActionsController`
  - `DesktopMediaActionsController`
  - `AndroidPlaylistsController`
  - `DesktopPlaylistsController`
  - `AndroidRadioController`
  - `DesktopRadioController`
  - playback controllers and app shells
- [ ] Tag each responsibility as shared domain, shared UI, platform adapter, or platform lifecycle.
- [ ] Identify all direct mutations of shared media state from platform files.
- [ ] Identify all direct provider mutation calls from platform files.
- [ ] Document the desired dependency direction for app, domain, provider, storage, UI, and platform modules.

## Phase 2: Shared Metadata Actions

- [ ] Move all favorite/rating/metadata mutation decision logic into shared domain services.
- [ ] Make `MediaMetadataMutationController` the single entry point for artist, album, and track metadata updates.
- [ ] Replace duplicated status/error handling in platform media controllers with shared result models.
- [ ] Reduce Android/Desktop media action controllers to adapter wiring.
- [ ] Add shared tests for mutation state transitions and provider capability handling.

## Phase 3: Shared Playlist Mutations

- [ ] Create a shared playlist mutation coordinator for save queue, add track, rename, and delete flows.
- [ ] Keep platform code responsible only for dialogs, text input, and user intent dispatch.
- [ ] Replace duplicated Android/Desktop queue-save paths with a single shared flow.
- [ ] Add tests for queue-to-playlist request construction and local state refresh.

## Phase 4: Shared Playback Orchestration

- [ ] Define a shared playback command surface for play, pause, resume, seek, previous, next, shuffle, repeat, and volume.
- [ ] Keep BASS interaction behind the existing playback engine boundary.
- [ ] Move sleep timer expiry and playback command decisions into shared domain/application code.
- [ ] Reduce platform playback controllers to lifecycle/audio-session/notification adapters.
- [ ] Add tests for sleep timer, repeat/shuffle, seek gating, and previous/next decisions.

## Phase 5: Desktop App Decomposition

- [ ] Split `DesktopNaviampApp.kt` into smaller state holders and route coordinators.
- [ ] Extract connection/provider setup into a desktop adapter plus shared connection coordinator.
- [ ] Extract library sync/freshness orchestration.
- [ ] Extract now-playing/session restoration orchestration.
- [ ] Extract mix-builder state wiring into shared application state where possible.
- [ ] Remove desktop Compose compiler bytecode workaround once root composables are small enough.
- [ ] Verify desktop compile fails no method-size limits without workaround.

## Phase 6: Shared UI and Route Contracts

- [ ] Audit shared UI models for platform-specific leakage.
- [ ] Move duplicated action catalog/menu item construction into shared UI/domain helpers.
- [ ] Ensure Android/Desktop route content takes shared state models instead of rebuilding equivalent models separately.
- [ ] Keep platform-specific layout only where viewport, window chrome, or lifecycle genuinely differs.

## Phase 7: Verification and Diff Discipline

- [ ] Add a lightweight architectural checklist to feature work: one shared path first, platform adapters second.
- [ ] Add focused tests around shared services before platform wiring.
- [ ] Establish expected build commands for each cleanup slice.
- [ ] Track file-count spread for representative changes.
- [ ] Before merging each slice, record what platform duplication was removed.

## Verification commands

Use these after meaningful slices:

```powershell
.\gradlew.bat :core:domain:allTests
.\gradlew.bat :apps:android:assembleDebug
.\gradlew.bat "-Pnaviamp.bass.platform=windows-x64" :apps:desktop:compileKotlinDesktop
.\gradlew.bat "-Pnaviamp.bass.platform=windows-x64" :apps:desktop:packageReleaseDistributable
```

## First concrete slice

Start with metadata actions because the recent diff made the smell visible there.

- [ ] Read both platform media action controllers side by side.
- [ ] Move duplicated status strings, provider capability checks, and state replacement decisions into `MediaMetadataMutationController`.
- [ ] Leave platform controllers with only provider references, coroutine scope, and callbacks into app state.
- [ ] Verify Android and desktop builds.
- [ ] Update this plan with exact files reduced and behavior preserved.
