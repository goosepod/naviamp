# Shared player workspace

Rollback checkpoint before this feature: `548acc70`, pushed to `origin/feature/library-discovery-playlists`.

## Behavior

- Opening Now Playing at a content viewport of at least 900 × 480 dp enables the wide workspace.
- Split view gives one-third of the available width to the player and two-thirds to the selected browsing screen. Queue is a destination in the right pane's bottom navigation, alongside the existing destinations.
- Selecting Home, Library, Search, Playlists, Radio, Downloads, or Settings replaces the right-pane content. Opening album/artist/playlist details preserves the docked player.
- Full player fills the app's content area with large artwork on the left, controls beneath the artwork, and the waveform across the bottom. Track details on the right are left-aligned with larger type and 16 dp spacing between lines. Toggling lyrics replaces the right-hand track details while preserving the artwork and controls. The layout button switches back to Split view. It does not change the operating system's window mode.
- The wide layout preference is saved with the existing shared Now Playing display settings. Narrow windows retain the stacked presentation and do not overwrite that preference.
- Explicitly closing Now Playing exits the workspace. Shrinking the window disables docking so subsequent navigation follows the existing narrow-window behavior.
- Split view hides the player's collapse arrow. Its right-pane navigation sits over the selected album background, with a 12 dp gap below the dark page surface. Full and narrow players retain the collapse control.
- Browsing content is centered within a maximum width of 1120 dp, both outside the workspace and inside its right pane. Dark surfaces keep text legible in wide windows (at least 900 dp) and workspace panes. Narrow pages retain the user's selected album background; Settings keeps its dark page surface at every size. Navigation outside the workspace remains dark on Settings and in wide windows; split-view navigation is transparent.

All product behavior, layout, models, navigation, and settings remain in common Kotlin. The only host-related change is Desktop packaging of the Windows Java accessibility module.

## Waveform and Aurora options

The regular player reuses the Android TV branch's shared continuous waveform and animated played-region clipping. Progress advances between playback reports, snaps when paused or scrubbing, and resets for a new track. Large drift resynchronizes within two seconds even on long tracks.

Settings → Experience → App Background exposes Aurora color steps (2–5) and gradient angle (0–180°). Zero degrees runs left to right, 90° top to bottom, and 180° right to left. The cover palette retains up to five distinct sampled colors where the artwork provides them. Light, Balanced, and Dark tones match the Android TV choices and apply to every color stop. Balanced retains the original gradient and its serialized `Dark` setting; the darker option uses `DeepDark`, preserving existing exports and saved appearance. These shared interface settings are included in settings export/import and sync; older documents default to three colors and 45°, and invalid imported values are clamped to their supported ranges.

Validation: 1,073 shared UI/domain tests passed, including settings round-trips and older-import defaults, five-color extraction, rotation geometry, and animated waveform pause/seek behavior. Shared Android/iOS compilation and the Windows distribution build passed. Live Windows verification confirmed the continuous waveform and immediate Aurora color-count/angle updates.

## Usability audit follow-up

- Wide rows: bounded shared reading surface; actions no longer span an entire ultrawide monitor.
- Equalizer: compact frequency labels with a separate Hz axis label; gain units appear once. Graph points and pointer hit mapping align with label column centers.
- Artwork contrast: shared reading/navigation surfaces use a fixed scrim. A regression assertion checks muted text against a white artwork backdrop at a minimum 4.5:1 contrast ratio.
- Windows accessibility: add `jdk.accessibility` to the packaged Windows runtime. The previous runtime's `release` module list omitted it. Compose uses Java Access Bridge rather than Windows UI Automation for screen-reader access, so an empty UI Automation pane is not sufficient evidence of missing Compose semantics. See the [official Compose desktop accessibility documentation](https://kotlinlang.org/docs/multiplatform/compose-desktop-accessibility.html). A user must have Java Access Bridge enabled and a compatible screen reader; this change does not alter system accessibility preferences or claim a complete NVDA/JAWS usability assessment.

## Validation

Shared regression coverage checks docking through route/detail navigation, explicit close, narrow-window navigation, 1:2 pane dimensions at 720p and 1440p, Queue navigation, switching layouts, narrow fallback, full-player progress placement, equalizer text overflow, and contrast.

Build and test results are recorded in `build/player-workspace-validation.log`; generated UI snapshots are under `core/ui/build/reports/player-workspace/`.

Validation passed: 856 domain tests, 259 presentation tests, and 204 UI tests (1,319 total). Shared Android and iOS arm64 compilation passed. After the final layout-button contrast adjustment, all six focused workspace tests and UI Android/iOS compilation passed again; the Windows distribution rebuilt successfully (`build/player-workspace-final.log`). Its runtime module list now includes `jdk.accessibility`.

Live Windows checks at 2560 × 1392 confirmed Home → Queue → Library → artist details with the player remaining docked, switching to the full player, and restoring the 306 × 672 narrow window to the stacked player. Playback remained paused during these checks. Screen-reader validation with NVDA/JAWS remains a separate manual check.

The final rebuilt app also passed a live equalizer check at 306 × 672: all ten frequency labels and both axis labels fit. Maximizing restored the saved full-player preference, and the layout button returned to Split view.

## Evening checkpoint — September 5, 2026

The experimental perceptual gradient interpolation and dithering were rejected during visual review and removed. Aurora keeps its original linear blending, the 2–5 color and 0–180° controls, and Light/Balanced/Dark tones. Smooth waveform/progress behavior is retained. The workspace, larger full-player lyrics, playlist membership permission fix, and shared settings export/import coverage are included in this checkpoint.
