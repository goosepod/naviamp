# Desktop Main Reduction Worksheet

This tracks the follow-up branch after the shared-core extraction and first desktop split. The goal is to keep reducing the desktop app composition file while continuing to move duplicated desktop/Android product behavior into shared modules when it is not platform-specific.

Branch: `codex/desktop-main-reduction`

## Goals

- [ ] Keep `apps/desktop/.../app/Main.kt` as a small desktop entry/window shell.
- [ ] Reduce `DesktopNaviampApp.kt` by extracting cohesive orchestration into feature controllers.
- [ ] Check desktop/Android duplication before extracting platform-local helpers.
- [ ] Move shared Navidrome/provider, playback, navigation, or UI rules into common/provider modules when both apps need them.
- [ ] Keep desktop and Android builds green after each meaningful slice.

## Work Done

- [x] Merged `codex/shared-core-extraction` into `main`.
- [x] Created `codex/desktop-main-reduction`.
- [x] Split the desktop entry point back into a small `Main.kt`.
  - `Main.kt` now owns app/window setup, window-size persistence, title bar configuration, shutdown, and wiring into `NaviampApp`.
  - The large Compose app body now lives in `DesktopNaviampApp.kt`.
- [x] Shared Navidrome connection preparation between desktop and Android.
  - Added `prepareNavidromeConnection` in `providers/navidrome`.
  - Desktop and Android now share saved-credential reuse, password connection creation, and native-token refresh/fallback behavior.
  - Moved the connection-preparation tests to provider common tests.

## Planned Work

- [ ] Extract desktop connection/session startup orchestration from `DesktopNaviampApp.kt`.
  - `connectToServer`
  - saved-session restore wiring
  - connection success/error state updates
  - post-connect refresh triggers
- [ ] Extract active connection reset/delete helpers.
  - `clearActiveConnectionState`
  - `resetDatabase`
  - `deleteConnection`
- [ ] Extract media-detail playback actions.
  - album/detail play helpers
  - search/related/popular track play helpers
  - metadata update, favorite, and rating helpers
- [ ] Extract app-state side effects where possible.
  - search debounce/load effect
  - library snapshot and sync effects
  - stats refresh effect
  - now-playing sidecar effects

## Shared-Code Watchlist

- Connection preparation is now shared through `providers/navidrome`.
- Remaining connection startup still differs by platform because desktop owns BASS/JVM TLS defaults, `DesktopCache`, window route state, and playlist engine restoration, while Android owns foreground service/playback runtime, `AndroidStorage`, and activity navigation state.
- Before extracting each helper from `DesktopNaviampApp.kt`, compare Android equivalents and move pure request/status/state-transition rules into `core/domain`, `core/ui`, or `providers/navidrome` instead of creating a new desktop-only duplicate.

## Verification

- [x] `.\gradlew.bat "-Pnaviamp.bass.platform=windows-x64" :apps:desktop:compileKotlinDesktop`
- [x] `.\gradlew.bat "-Pnaviamp.bass.platform=windows-x64" :providers:navidrome:allTests :apps:desktop:desktopTest :apps:desktop:compileKotlinDesktop :apps:android:assembleDebug`

## Follow-Up Ideas

- [ ] Reminder: revisit the artist-selection feature idea after this branch. Capture the exact workflow before implementation so it can be designed cleanly across desktop and Android.
