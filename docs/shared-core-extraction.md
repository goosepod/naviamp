# Shared Core Extraction Checklist

This tracks the architectural pass that should follow the desktop `Main.kt` split. The goal is to stop solving the same product behavior separately on desktop and Android by moving shared rules, state transitions, request planning, and provider orchestration into `core/domain` or `core/ui`.

## Goals

- [ ] Make desktop and Android share product behavior by default.
- [ ] Keep platform modules focused on platform state, UI composition, native playback, storage adapters, filesystem access, permissions, and OS integration.
- [ ] Move duplicate desktop/Android fixes into common code so playback, library, search, radio, playlist, and detail-loading behavior is fixed once.
- [ ] Preserve platform-specific escape hatches only where there is a real platform constraint.
- [ ] Add common tests for every moved rule before deleting platform-local copies.
- [ ] Keep desktop and Android builds green after each feature migration.

## Placement Rules

- `core/domain`: provider orchestration, queue/session/radio/library/search plans, pure state transitions, fallback policies, request models, and persistence-neutral mappings.
- `core/ui`: shared UI state models, action catalogs, display mapping, formatting, route/view mapping, and platform-neutral screen state.
- `apps/desktop`: Compose desktop screens, window/chrome, desktop playback engines, `DesktopCache`, desktop settings store wiring, file dialogs, filesystem paths, BASS/native integration, desktop-only sidecars.
- `apps/android`: Android screens, Android lifecycle/state holders, media session/service wiring, Android playback adapters, permissions, platform storage/cache adapters.

## Migration Method

- [x] Finish the desktop `Main.kt` split enough that feature boundaries are clear.
- [ ] For each feature, compare desktop and Android behavior before moving code.
- [ ] Extract pure behavior into `core/domain` first, using interfaces or lambdas for platform storage/playback dependencies.
- [ ] Move shared UI mapping into `core/ui` only after the domain behavior is stable.
- [ ] Replace desktop and Android local logic with thin adapters over the shared core API.
- [ ] Move existing platform tests down to common tests where possible.
- [ ] Leave a short note in this doc when behavior intentionally remains platform-specific.

## Feature Checklist

- [ ] Playback/session behavior
  - [ ] Move queue navigation and repeat/shuffle decisions into `core/domain`.
  - [ ] Move seek planning and provider-stream replay decisions into `core/domain`.
  - [ ] Move playback progress, pending-seek, and UI update gating into shared code where Android has matching behavior.
  - [ ] Move play-report and now-playing report thresholds/eligibility into `core/domain`.
  - [ ] Share restored session validation and playback-session mapping.

- [ ] Library and search behavior
  - [ ] Move library freshness/status decisions into `core/domain`.
  - [ ] Move paging/limit/snapshot planning into `core/domain`.
  - [ ] Move library search normalization/filtering/fallback decisions into shared code.
  - [ ] Make desktop and Android use the same search request/result mapping.

- [ ] Radio behavior
  - [ ] Promote radio request models from desktop `radio/` into `core/domain/radio`.
  - [ ] Promote recent-radio stream/action resolution into shared code.
  - [ ] Promote seed-selection rules using storage/provider interfaces instead of `DesktopCache`.
  - [ ] Keep desktop/Android radio controllers as thin adapters around shared plans.
  - [ ] Keep native playback queue mutation inside platform adapters.

- [ ] Playlist behavior
  - [ ] Move playlist mutation planning into `core/domain`.
  - [ ] Share create/add/rename/delete fallback and status decisions.
  - [ ] Share smart playlist update/load orchestration where possible.
  - [ ] Keep platform-specific cache refresh and UI state wiring in app modules.

- [ ] Downloads/cache behavior
  - [ ] Move download eligibility and request planning into `core/domain`.
  - [ ] Keep actual filesystem paths, cache eviction, and platform storage adapters in app modules.
  - [ ] Share downloaded-track display/status mapping where possible.

- [ ] Artist/album/detail behavior
  - [ ] Move artist detail loading fallback rules into `core/domain`.
  - [ ] Move album detail loading fallback rules into `core/domain`.
  - [ ] Share popular-track/similar-artist request planning and result mapping.
  - [ ] Keep visual layout and navigation state in platform UI layers.

- [ ] Home and navigation state
  - [ ] Share home content request planning and aggregation rules.
  - [ ] Share route persistence/restoration mapping.
  - [ ] Keep platform navigation containers and window/back-stack integration platform-local.

- [ ] Settings and preferences
  - [ ] Share playback settings validation and effective-settings derivation.
  - [ ] Share session/settings serialization models where they are not platform-specific.
  - [ ] Keep platform storage backends local.

- [ ] UI models
  - [ ] Move duplicated display models into `core/ui`.
  - [ ] Share action availability mapping for media rows, now playing, radio, playlists, and search.
  - [ ] Keep final Compose layouts platform-local when screen density/lifecycle differs.

## Verification

- [ ] `./gradlew :core:domain:allTests`
- [ ] `./gradlew :core:ui:allTests`
- [ ] `./gradlew :apps:desktop:compileKotlinDesktop :apps:desktop:desktopTest`
- [ ] `./gradlew :apps:android:assembleDebug`

## Notes

- This is the last major architecture pass after platform `Main`/`MainActivity` cleanup.
- The goal is not to make desktop and Android identical internally; the goal is to make duplicated product decisions impossible by default.
- A platform-local implementation should be the exception, and the reason should be documented next to the checklist item.
- Desktop split prerequisite is complete. The final pass left desktop-local Compose state ownership, lifecycle effects, native playback/cache/filesystem adapters, and feature/controller wiring in `apps/desktop`; shared product behavior migrations should start from the feature checklist above.
