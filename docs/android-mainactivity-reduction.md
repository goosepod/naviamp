# Android MainActivity Reduction Worksheet

This tracks the maintainability pass after the original Android warning-driven split. The release/function-size warning work is complete in `docs/android-mainactivity-split.md`; this worksheet is for getting `MainActivity.kt` out of the business of owning whole feature workflows.

Branch: `codex/desktop-main-reduction`

## Baseline

- `apps/android/.../app/MainActivity.kt`: 2,348 lines.
- `apps/android/.../playback/AndroidPlaybackForegroundService.kt`: 2,234 lines.
- `apps/android/.../storage/AndroidStorage.kt`: 1,012 lines.
- `apps/android/.../playback/AndroidBassPlaybackEngine.kt`: 674 lines.
- `apps/android/.../app/AndroidAppShell.kt`: 646 lines.

`MainActivity.kt` is the first target. `AndroidPlaybackForegroundService.kt` deserves its own worksheet after the activity is slimmer because service, Android Auto, media session, and cold-start playback ownership are tangled enough to review separately.

## Progress

- [x] Extracted playback session startup and playback progress handling into `AndroidPlaybackOrchestration.kt`.
  - `MainActivity.kt`: 2,348 -> 2,267 lines.
  - Verification: `.\gradlew.bat :apps:android:assembleDebug`.
  - Remaining playback work: move `playTrack`, seek handling, adjacent navigation, and prefetch/sidecar orchestration behind a cohesive playback controller.
- [x] Moved playback progress update decisions into shared domain via `planPlaybackProgressUpdate`.
  - Android now applies a shared progress plan, then performs Android-only notification, foreground-service, play-report, and gapless/crossfade side effects.
  - Verification: `.\gradlew.bat :core:domain:allTests`, `.\gradlew.bat :apps:android:assembleDebug`.
- [x] Moved playback start queue/target/restored-progress planning into shared domain via `planPlaybackStart`.
  - Android now uses a shared plan for queue choice, selected index, provider stream request, engine start position, and initial restored progress.
  - `MainActivity.kt`: 2,267 -> 2,264 lines.
  - Verification: `.\gradlew.bat :core:domain:allTests`, `.\gradlew.bat :apps:android:assembleDebug`.
- [x] Moved adjacent-track navigation decisions into shared domain via `planPlaybackAdjacentAction`.
  - Android now applies shared actions for previous-button restart, adjacent queue selection, repeat wrapping, and no-op cases.
  - Verification: `.\gradlew.bat :core:domain:allTests`, `.\gradlew.bat :apps:android:assembleDebug`.

## Goals

- [ ] Reduce `MainActivity.kt` by extracting cohesive feature controllers and effect runners.
- [ ] Keep Android behavior aligned with desktop and shared core by default.
- [ ] Move duplicated Android/desktop product rules into `core/domain`, `core/ui`, or provider modules before adding platform-local helpers.
- [ ] Prefer shared plan/reducer APIs for product behavior; keep platform files as adapters that apply those plans to lifecycle, storage, engine, and OS side effects.
- [ ] Keep Android route/shell composition readable and mostly declarative.
- [ ] Keep every extraction verified with Android debug or release compile/build, plus common tests when shared rules move.

## Guardrails

- Do not move Android lifecycle, `ActivityResultLauncher`, permissions, intents, foreground-service binding, Android Auto handoff, notification, or platform storage APIs into common code.
- Do not add another giant controller. Feature controllers should map to existing folders: `playback/`, `radio/`, `media/`, `library/`, `downloads/`, `connection/`, `app/`.
- Keep shared behavior pure or dependency-injected. Provider calls, cache reads/writes, coroutine scope ownership, and Compose state assignment can remain platform-local.
- Platform-specific helpers should be thin application layers over common plans whenever the same behavior exists or could exist on desktop.
- Prefer small compile-green slices over broad rewrites.
- Before each extraction, compare the desktop controller for matching behavior and move common decision/status/planning rules down when both platforms need them.

## Planned Slices

- [ ] **Playback orchestration controller**
  - Move `playTrack`, seek handling, adjacent-track navigation, current-track replay, playback-state callbacks, notification metadata update wiring, and session-token start logic out of `MainActivity.kt`.
  - Keep Android playback engine, foreground-service progress publishing, local file lookup, and notification wiring platform-local.
  - Shared-code checks: playback target planning, repeat/adjacent queue selection, pending-seek behavior, report gating, sidecar prep, and queue mutation already live mostly in `core/domain`; add missing shared helpers only if duplicate desktop logic appears.

- [ ] **Android radio controller expansion**
  - Continue moving radio helpers out of `MainActivity.kt`: library/genre/decade/random-album/artist/popular/recent radio dispatch, track-radio queue conversion, and shell queue-item radio.
  - Keep provider execution and queue-controller mutation in Android radio adapter.
  - Shared-code checks: generated-radio queue construction, tail refill, recent-radio actions, seed selection, and request models should remain shared.

- [ ] **Internet radio playback controller**
  - Move `playInternetRadioStation`, live stream URL resolution, stream metadata notification updates, recent-station persistence, and station state reset into an Android internet-radio controller.
  - Shared-code checks: station-to-track shaping, stream-title metadata update, recent-station ordering, and URL/playlist parsing are already shared or isolated.

- [ ] **Android media action controller**
  - Move favorite/rating updates, track metadata propagation, album/artist popular-track play/add/radio/download callbacks, and known-track lookup helpers out of `MainActivity.kt`.
  - Shared-code checks: metadata propagation, action availability, favorite/rating mutation planning, and display models should stay in shared domain/UI where possible.

- [ ] **Playlist orchestration controller**
  - Move playlist play/open/refresh/preload, selected-playlist detail state, playlist delete/rename/create/add flows, and smart-playlist callbacks out of `MainActivity.kt`.
  - Shared-code checks: playlist mutation planning, detail refresh shaping, recent-playlist cleanup, and queue append planning already belong in `core/domain`.

- [ ] **Library and search orchestration cleanup**
  - Move remaining library query/snapshot/page-jump/search result loading state wiring out of `MainActivity.kt` if it is still inline after playback/media splits.
  - Shared-code checks: search session orchestration, query normalization, debounce, paging limits, freshness polling, and sync status rules should remain common.

- [ ] **Route/back-stack/effect cleanup**
  - Move `handleAndroidBack`, route-clear helpers, auto-command effects, and startup/restoration effects into focused app-level helpers where lifecycle-safe.
  - Keep actual `BackHandler`, `LaunchedEffect`, permissions, and intent collection in activity composition when Compose lifecycle ownership matters.

- [ ] **Final measurement pass**
  - Recount `MainActivity.kt`.
  - Re-run Android build validation.
  - Update this worksheet with new line counts and any remaining high-risk blocks.

## Suggested Order

1. For every slice, identify the platform-agnostic plan/reducer first and put it in `core/domain`, `core/ui`, or a provider module before adding Android adapter code.
2. Continue playback orchestration because it is the highest-risk and largest remaining workflow: `playTrack`, seek handling, adjacent navigation, prefetch, sidecars, and state callbacks.
3. Expand `AndroidRadioController` while the shared tail-refill work is fresh.
4. Split internet-radio live playback because it is distinct from generated radio.
5. Move media and playlist actions after playback/radio boundaries are stable.
6. Clean route/effect wiring last so it can use the new controller APIs.

## Verification

- [ ] `.\gradlew.bat :core:domain:allTests`
- [ ] `.\gradlew.bat :apps:android:assembleDebug`
- [ ] `.\gradlew.bat :apps:android:compileReleaseKotlin`
- [ ] `.\gradlew.bat "-Pnaviamp.bass.platform=windows-x64" :apps:desktop:compileKotlinDesktop`

## Notes

- `Main.kt` on desktop is currently small enough; the ongoing size problem is `DesktopNaviampApp.kt`, Android `MainActivity.kt`, and Android `AndroidPlaybackForegroundService.kt`.
- `AndroidPlaybackForegroundService.kt` should be handled after `MainActivity.kt`, likely with separate service/runtime/media-session/Android Auto controllers.
- The artist-selection feature idea remains parked in `docs/desktop-main-reduction.md` until the current size-reduction pass is stable enough for feature work.
