# Android MainActivity Split Checklist

This is the working history for reducing Android build/runtime warnings related to very large generated methods and for making `MainActivity.kt` easier to maintain.

## Done

- [x] Reduced oversized artwork decode paths:
  - [x] UI cover art decode now targets bounded pixel sizes.
  - [x] palette extraction uses smaller decoded artwork.
  - [x] visualizer album-art bitmap decode is capped.
  - [x] notification/media-session artwork decode is capped.
- [x] Checked Android builds for ART/profile/function-size warnings:
  - [x] `:apps:android:assembleDebug --warning-mode all`
  - [x] `:apps:android:assembleRelease --warning-mode all`
- [x] Added bytecode size spot checks with `javap`.
- [x] Moved support/helper code out of `MainActivity.kt` into `AndroidAppSupport.kt`:
  - [x] library sync helpers and freshness models
  - [x] shell model mapping
  - [x] now-playing UI mapping
  - [x] diagnostics mapping
  - [x] route conversion helpers
  - [x] internet radio playlist/url helpers
  - [x] Android playback constants
- [x] Split shared shell rendering:
  - [x] added `AndroidAppShellUiState`
  - [x] added `AndroidAppShellActions`
  - [x] moved `AndroidAppShellContent` to a top-level composable
- [x] Moved app state into `AndroidAppState`.
- [x] Moved initial runtime effects into `AndroidAppRuntimeEffects`.
- [x] Moved shell UI-state assembly into `rememberAndroidAppShellUiState`.
- [x] Moved persistence/library/download refresh effects into `AndroidAppPersistenceEffects`.
- [x] Started de-duplicating Android/desktop behavior into shared modules:
  - [x] moved route mapping into `core/ui` (`NaviampRouteMapping.kt`)
  - [x] moved generated radio queue construction into `core/domain`
  - [x] moved generated radio append/upcoming/refill rules into `core/domain`
  - [x] moved internet-radio track ID helpers into `core/domain`
  - [x] moved create-or-add-missing playlist mutation into `core/domain`
  - [x] moved playback session queue/restore/position mapping into `core/domain`
- [x] Split Android constants into `AndroidAppConstants.kt`.
- [x] Split `AndroidAppState` into `AndroidAppState.kt`.
- [x] Split Android runtime/persistence effects into `AndroidAppEffects.kt`.
- [x] Split shell state/actions/rendering into `AndroidAppShell.kt`.
- [x] Split library sync/freshness helpers into `AndroidLibrarySync.kt`.
- [x] Split shell action wiring into `androidAppShellActions`.
- [x] Split internet-radio playlist parsing into `AndroidRadioPlaylist.kt`.
- [x] Split diagnostics/API-call reporting into `AndroidDiagnostics.kt`.
- [x] Split connection/provider setup into `AndroidConnectionController.kt`.
- [x] Split playback session save/restore into `AndroidPlaybackSessionController.kt`.
- [x] Split seeded radio queue orchestration into `AndroidRadioController.kt`.
- [x] Split Android download controller logic into `AndroidDownloadController.kt`.
- [x] Split artist/album detail loading into `AndroidArtistController.kt`.
- [x] Verified across recent splits:
  - [x] `.\gradlew.bat :apps:android:compileReleaseKotlin`
  - [x] `.\gradlew.bat :apps:android:assembleRelease --warning-mode all`
  - [x] `.\gradlew.bat :apps:desktop:compileKotlinDesktop`
- [x] Verified after shared radio continuation/refill rule extraction:
  - [x] `.\gradlew.bat :apps:android:compileReleaseKotlin`
  - [x] `.\gradlew.bat :apps:desktop:compileKotlinDesktop`
- [x] Verified after shared playback session mapping extraction:
  - [x] `.\gradlew.bat :apps:android:compileReleaseKotlin`
  - [x] `.\gradlew.bat :apps:desktop:compileKotlinDesktop`
- [x] Final warning sweep:
  - [x] `.\gradlew.bat :apps:android:assembleRelease --warning-mode all`

## Current Measurements

- Initial `NaviampAndroidApp(...)` bytecode offset was roughly `47.9k`.
- After first shell/content extraction, the large generated shell method was roughly `32.5k`.
- After moving support helpers and shell rendering, `NaviampAndroidApp(...)` was roughly `37.5k`.
- After introducing `AndroidAppState`, `NaviampAndroidApp(...)` dropped to roughly `28.7k`.
- After moving runtime effects, it dropped to roughly `27.3k`.
- After moving shell UI-state mapping and persistence effects, it is roughly `25.4k`.
- After shared helper extraction and constants split, it remains roughly `25.4k`.
- After file-level effects/shell splits, it remains roughly `25.4k`; those moves were organizational, not controller extraction.
- After moving shell action wiring out of `NaviampAndroidApp`, it is roughly `17.4k`, below the remembered warning zone.
- After diagnostics and shared playlist mutation extraction, it remains roughly `17.4k`.
- After connection, session, radio, and download controller extraction, it remains roughly `17.5k`.
- After artist/album detail loading extraction, it remains roughly `17.5k`.
- After shared radio continuation/refill rule extraction, it remains roughly `17.5k` (`17542` max bytecode offset).
- After shared playback session mapping extraction, it remains roughly `17.5k` (`17542` max bytecode offset).

## Still Needed

- [x] Continue reducing `NaviampAndroidApp(...)` below the remembered warning zone of about `20k`.
- [x] Split playback/session controller logic out of `NaviampAndroidApp`.
- [x] Split connection/provider controller logic out of `NaviampAndroidApp`.
- [x] Split playlist/download controller logic out of `NaviampAndroidApp`.
- [x] Split remaining artist/detail loading logic out of `NaviampAndroidApp`.
- [x] Continue comparing Android and desktop before extracting platform-local controllers.
- [x] Prefer shared `core/domain` or `core/ui` helpers for platform-neutral behavior.
- [x] Audit remaining duplicate desktop/Android helpers:
  - [x] radio continuation/refill rules
  - [x] playlist mutation flows
  - [x] artist/album detail loading flows
  - [x] playback session restore/save mapping
- [x] Re-run bytecode size checks after each major split.
- [x] Re-run release assemble with warnings after the final split.
- [x] Consider breaking `AndroidAppSupport.kt` into smaller files once behavior is stable:
  - [x] `AndroidAppState.kt`
  - [x] `AndroidAppShell.kt`
  - [x] `AndroidAppEffects.kt`
  - [x] `AndroidDiagnostics.kt`
  - [x] `AndroidLibrarySync.kt`
  - [x] `AndroidRadioPlaylist.kt`
  - [x] `AndroidConnectionController.kt`
  - [x] `AndroidPlaybackSessionController.kt`
  - [x] `AndroidRadioController.kt`
  - [x] `AndroidDownloadController.kt`
  - [x] `AndroidArtistController.kt`
  - [x] `AndroidAppConstants.kt`

## Notes

- Latest full release assemble did not emit the ART/profile/function-size warning.
- The main composable is now below the remembered warning zone; further controller splits are still useful for maintainability.
