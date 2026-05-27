# Desktop Main Split Checklist

This tracks the work to give the Compose desktop app the same kind of maintainability treatment that Android just received, while continuing to move platform-neutral behavior into shared `core/domain` or `core/ui` code first.

## Goals

- [ ] Reduce `apps/desktop/.../Main.kt` from a large all-in-one app module into focused desktop adapters.
- [ ] Keep desktop behavior aligned with Android by reusing shared domain/UI helpers where possible.
- [ ] Avoid moving desktop-only windowing, filesystem/cache, native playback, file pickers, or OS integration into common code.
- [ ] Keep the packaged Windows app build green after each meaningful split.
- [ ] Keep desktop files physically grouped by feature so related UI, controllers, and adapters are easy to find.

## Planned Splits

- [x] Create this desktop split checklist.
- [x] Split desktop window/chrome setup out of `Main.kt`.
- [x] Split desktop constants and tiny app helpers out of `Main.kt`.
- [x] Physically group already-split desktop files into feature folders.
- [x] Remove abandoned Rust/Slint desktop experiment and keep BASS vendor files under the active Compose desktop app.
- [ ] Split desktop playback/session controller logic out of `Main.kt`.
  - [x] Move now-playing analysis model into `playback/`.
  - [x] Move playback progress pending-seek/UI update decisions into `playback/`.
  - [x] Move previous/next button availability and restart decisions into `playback/`.
  - [x] Move repeat-mode cycling and provider-stream seek routing decisions into `playback/`.
  - [x] Move playback position save threshold decision into `playback/`.
  - [x] Move play-report threshold/submission decisions into `playback/`.
  - [x] Move desktop seek planning into `playback/`.
- [ ] Split desktop radio orchestration out of `Main.kt`, keeping shared queue/refill rules in `core/domain`.
  - [x] Move recent radio stream metadata builders into `radio/`.
  - [x] Move generated radio queue update helpers into `radio/`.
  - [x] Move album/artist radio seed selection into `radio/`.
  - [x] Move recent radio stream action resolution into `radio/`.
  - [x] Move radio request builders into `radio/`.
  - [x] Move seeded entity radio request builders into `radio/`.
- [ ] Split connection/provider setup out of `Main.kt`.
- [ ] Split library sync/freshness helpers out of `Main.kt`.
  - [x] Move library freshness model/status decision into `library/LibrarySync.kt`.
  - [x] Move library snapshot paging helpers into `library/LibrarySync.kt`.
- [ ] Split playlist/download mutations out of `Main.kt`, keeping shared provider mutations in `core/domain`.
- [ ] Split artist/album detail loading out of `Main.kt`.
- [x] Split diagnostics/stats mapping out of `Main.kt`.
- [ ] Re-check whether any remaining desktop logic belongs in shared `core/domain` or `core/ui`.

## Physical Layout

Desktop feature folders now sit under `apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/`:

- `app/`: entry point, window/chrome setup, menus, app constants/support.
- `cache/`: desktop cache and local persistence adapters.
- `connection/`: connection UI and future provider/session connection controller.
- `discovery/`: external discovery clients, such as popular tracks lookup.
- `downloads/`: downloads UI and future download controller logic.
- `home/`: home surface.
- `library/`: library panel and sync/freshness helpers.
- `lyrics/`: LRCLIB, embedded tag parsing, and local lyrics helpers.
- `media/`: artist/album detail panels, shared media rows, cover-art thumb UI.
- `navigation/`: app route/navigation helpers and icons.
- `playback/`: playback engines, now-playing UI, queue engine, waveform analysis.
- `playlists/`: playlist panels and add-to-playlist dialog.
- `radio/`: internet radio UI and future radio orchestration.
- `search/`: search surface.
- `settings/`: settings UI and settings store.
- `stats/`: Stats for Nerds UI and mapping.
- `theme/`: colors, transport/action icons, and formatting helpers.

Package names are intentionally unchanged for this pass. The goal is to make the tree navigable first; package renames can happen later once the controller boundaries are stable.

## Verification

- [x] `.\gradlew.bat :apps:desktop:compileKotlinDesktop`
- [x] `.\gradlew.bat :apps:desktop:stageReleaseApp`

## Notes

- Unlike Android, this is not driven by ART/profile warnings. The desktop target is maintainability and shared behavior reuse.
- Start with low-risk extractions, then move the state-heavy controller logic once the boundaries are clearer.
- `DesktopStatsMapping.kt` now owns playback capability and stream stats conversion for Stats for Nerds.
- The abandoned Rust/Slint app was removed. BASS vendor libraries now live at `apps/desktop/vendor/bass/<platform>`.
- Library freshness status decisions now live with library sync helpers and have desktop unit coverage.
- Library snapshot paging decisions now live with library sync helpers and have desktop unit coverage.
- Now-playing sidecar analysis state now lives in the playback feature folder.
- Playback progress pending-seek and UI update decisions now live in the playback feature folder with desktop unit coverage.
- Previous/next control decisions now live in the playback feature folder with desktop unit coverage.
- Repeat-mode cycling and transcoded provider-stream seek routing decisions now live in the playback feature folder with desktop unit coverage.
- Playback position save threshold decisions now live in the playback feature folder with desktop unit coverage.
- Play-report threshold and submission-gating decisions now live in the playback feature folder with desktop unit coverage.
- Desktop seek planning now lives in the playback feature folder with desktop unit coverage.
- Recent radio stream metadata construction now lives in the radio feature folder with desktop unit coverage.
- Generated radio queue update session gates now live in the radio feature folder with desktop unit coverage.
- Album and artist radio seed selection now lives in the radio feature folder with desktop unit coverage.
- Recent radio stream action resolution now lives in the radio feature folder with desktop unit coverage.
- Library, genre, decade, track, and popular-track radio request builders now live in the radio feature folder with desktop unit coverage.
- Random-album, artist, and album seeded radio request builders now live in the radio feature folder with desktop unit coverage.
