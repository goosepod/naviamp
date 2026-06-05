# Shared UI Reduction

This checklist tracks cleanup after the desktop-main-reduction branch merge. The codebase sweep showed `core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampSharedUi.kt` is now the largest hand-written source file, so the next architecture goal is to split shared UI into cohesive model, route, panel, navigation, menu, and primitive files without changing platform behavior.

## Findings

- `NaviampSharedUi.kt` is roughly 3,400 lines and mixes shared UI models, route shell, route content, media rows, playlist dialogs, now-playing glue, settings glue, navigation, menus, and icons.
- Platform roots are still large, but they now mostly compose extracted controllers/services. Shared UI is the bigger cross-platform maintenance risk because both Android and desktop depend on it.
- Existing files already point to the intended shape: `MediaUiMappers.kt`, `NaviampNowPlayingUi.kt`, `NaviampSettingsUi.kt`, `NaviampActionCatalog.kt`, and platform `PlatformCoverArt` actuals.

## Checklist

- [x] Extract shared UI model/data classes and route enums out of `NaviampSharedUi.kt`.
- [ ] Move home/search/library/downloads route content into focused shared route files.
- [x] Move playlist list/detail content and playlist dialogs into focused shared playlist UI files.
- [x] Move reusable media rows/cards/sections into focused shared media component files.
- [x] Move text fields/buttons/section headers/placeholders/dropdowns/overflow menus into shared primitive files.
- [x] Move shared bottom navigation and route placeholder rendering into a shared navigation file.
- [x] Move `NaviampIcons` into a dedicated icon file if no existing icon file should own it.
- [ ] Add focused UI tests or compile checks after each slice.

## Verification

- [x] `ANDROID_HOME=/Users/jbmcmichael/Library/Android/sdk ./gradlew :core:ui:jvmTest :apps:desktop:compileKotlinDesktop :apps:android:compileDebugKotlin`
