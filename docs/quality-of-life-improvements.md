# Quality of Life Improvements

This document tracks small interaction and layout improvements that should make Naviamp feel more natural during daily use.

## Pull to Refresh

Add pull-to-refresh gestures on these top-level pages:

- Playlists
- Internet Radio
- Library

### Goals

- Let users manually refresh server-backed content with a familiar touch gesture.
- Keep existing explicit refresh actions where they already exist.
- Use the same refresh operation as the current manual or automatic path for each page, so pull-to-refresh does not create a separate code path.

### Notes

- Playlists should refresh the playlist list and preserve the selected playlist when possible.
- Internet Radio should refresh the station list and keep the current route/context stable.
- Library should refresh the local library snapshot or trigger the existing library refresh path, depending on the current platform flow.
- The gesture should be platform-appropriate. Android should use native-feeling pull-to-refresh behavior. Desktop can wait unless there is an existing scroll/pointer pattern that makes the gesture feel intentional.

### Acceptance Criteria

- Pulling down at the top of each page starts a refresh.
- A visible refresh indicator appears while refresh is running.
- Refresh errors surface through the existing status/error UI for that page.
- Existing scroll position is preserved when the refreshed content still supports it.

## Synced Lyrics Current-Line Position

Adjust the synced lyrics panel so the current lyric line is visually centered in the seven-line display.

### Current Behavior

After the song gets past the first few lines, the active lyric appears as the fifth visible line. With seven visible lines, this makes the active line feel slightly too low.

### Desired Behavior

Move the active lyric up by one line so it appears as the fourth visible line when there is enough surrounding lyric context.

### Notes

- Keep the early-song behavior graceful when there are not enough previous lines to center the active line.
- Keep the end-of-song behavior graceful when there are not enough following lines.
- Preserve the current lyric highlighting and offset controls.
- Apply the behavior consistently across desktop and Android if they share the lyrics panel implementation.

### Acceptance Criteria

- With seven visible synced lyric lines and enough surrounding context, the active line is fourth from the top.
- The first few lines do not leave unnecessary blank space above the lyrics.
- The final few lines do not jump or leave unnecessary blank space below the lyrics.
- Manual lyrics offset adjustment still updates the highlighted line immediately.

## Desktop Installer Options

Offer two installer choices for desktop release builds:

- Standard installer: includes everything needed to run Naviamp, including the bundled Java runtime.
- Thin smart installer: checks for a compatible installed Java runtime and skips installing Naviamp's bundled runtime when possible.

### Goals

- Keep the standard installer as the reliable default for users who just want Naviamp to install and run.
- Provide a smaller installer path for users who already have a compatible Java runtime.
- Support the same desktop app payload, native playback resources, and user-facing version across both installer options.
- Keep the installer choice explicit in release artifacts so users can tell which package they are downloading.
- Let Windows users choose during install whether Naviamp is added to the Start Menu.

### Notes

- The current jpackage MSI/EXE path should remain the standard installer because it packages a known-good app image with its private runtime.
- The thin smart installer likely needs a separate installer pipeline or launcher instead of plain jpackage, because jpackage does not provide conditional runtime installation.
- Runtime detection should require Java 17 or newer.
- Java detection should account for `JAVA_HOME`, `java` on `PATH`, and common Windows/macOS/Linux install locations where practical.
- A launcher-level runtime check is preferable to install-time-only detection, because users can add, remove, or upgrade Java after installing Naviamp.
- If no compatible runtime is found, the thin smart installer should install or use Naviamp's bundled runtime and still produce a working app.
- Windows installer flows should ask before creating Start Menu shortcuts instead of assuming every install should add one.
- If the current jpackage installer cannot offer an optional Start Menu prompt, evaluate whether the custom installer path for the thin smart installer should also cover this behavior.

### Acceptance Criteria

- Desktop release artifacts include a standard installer and a clearly named thin smart installer option.
- The standard installer runs without requiring any system Java installation.
- The thin smart installer uses an existing compatible Java runtime when one is available.
- The thin smart installer falls back to Naviamp's bundled runtime when no compatible runtime is available.
- Both installer variants include the required BASS playback libraries and Naviamp JNI library.
- Both installer variants launch the same Naviamp version and report the same app version in About.
- Windows installers ask whether to add Naviamp to the Start Menu and honor the user's choice.

## Visualizer Performance Pass

Improve desktop visualizer smoothness so the active visualizers feel closer to Plexamp's buttery visualizer motion instead of visibly chugging.

### Current Behavior

Some visualizers appear to run at roughly 5-10 fps during playback, especially when the visual scene is more complex. This makes the motion feel uneven even when audio playback itself is stable.

### Desired Behavior

Visualizer motion should feel smooth and continuous during normal playback. The target should be a stable 60 fps where the display and hardware support it, with graceful degradation instead of obvious stutter on slower machines.

### Goals

- Profile the current desktop visualizer pipeline before changing rendering behavior.
- Identify whether frame drops come from FFT/waveform polling, shader work, Compose recomposition, Skia drawing, allocation churn, or main-thread contention.
- Reduce per-frame allocations and avoid recomposing UI state that can be drawn directly.
- Keep visualizer rendering independent from playback stability so visual load cannot interrupt audio.
- Preserve existing visualizer styles while making their motion smoother.

### Notes

- Compare against Plexamp's visualizer feel as the subjective quality bar.
- Add lightweight diagnostics for visualizer frame time, dropped frames, and render mode so performance can be inspected from Stats for Nerds or logs.
- Prefer GPU/runtime-shader paths where they are already available, but verify that shader uniforms and audio data updates are not causing unnecessary recomposition.
- Check whether visualizers should run on a dedicated animation loop or throttled draw path instead of piggybacking on broader UI state updates.
- Avoid lowering visual complexity as the first fix unless profiling shows a specific visualizer is too expensive even after pipeline improvements.

### Acceptance Criteria

- Visualizers sustain smooth motion during normal desktop playback on a typical Windows test machine.
- No visualizer routinely drops to visibly choppy 5-10 fps under normal playback conditions.
- Visualizer diagnostics expose enough frame timing information to compare before and after changes.
- Audio playback remains stable while visualizers are enabled.
- At least the heaviest existing visualizer is profiled and optimized or given a graceful lower-cost rendering path.

## Configurable Download Path

Let users choose where downloaded tracks are stored instead of always using Naviamp's default app/cache location.

### Goals

- Add a desktop setting for the downloaded-track storage directory.
- Let users keep offline music on a larger drive, shared media folder, or backed-up location.
- Preserve existing download, playback, cleanup, and cache-size behavior after the path changes.
- Make the current download location visible in settings.

### Notes

- Use a platform-native directory picker on desktop.
- Validate that the selected path exists or can be created before saving it.
- Decide whether changing the path should move existing downloads, leave old downloads in place, or offer both choices.
- Keep app-owned metadata in Naviamp storage even when audio files move to a user-selected directory.
- Handle missing or disconnected drives gracefully by showing an actionable error and avoiding destructive cleanup.
- Android can remain on the existing app-private/offline storage path unless a platform-appropriate external storage flow is designed separately.

### Acceptance Criteria

- Desktop users can choose and save a downloaded-track directory from settings.
- New downloads are written to the configured directory.
- Already-downloaded tracks remain playable or are clearly marked unavailable if their configured storage path is missing.
- Cache/download cleanup respects the configured directory and does not delete unrelated user files.
- Resetting the setting returns downloads to Naviamp's default storage location.

## Dark and Light Mode Readability

Make Naviamp readable and polished on both dark-mode and light-mode desktop systems.

### Current Behavior

On a Windows machine using a non-dark system theme, the app can become extremely hard to read. Text, surfaces, controls, and contrast appear tuned for dark mode and degrade badly when the surrounding OS or Compose theme resolves differently.

### Desired Behavior

Naviamp should render a coherent dark theme and a coherent light theme, with predictable colors, readable text, and controls that look intentional in either mode.

### Goals

- Audit desktop theme selection on Windows, macOS, and Linux.
- Decide whether Naviamp follows system theme by default or uses an app-level theme preference.
- Add explicit settings for System, Dark, and Light if the current theme path cannot reliably infer the user's intent.
- Ensure every major surface, text color, icon tint, divider, selected row, disabled state, dialog, menu, and input field has acceptable contrast in both themes.
- Keep album-art-derived accents from overpowering text contrast or making controls unreadable.

### Notes

- Test on a Windows machine with light mode enabled, not only by toggling previews or assumptions in code.
- The player, navigation, settings, library, playlists, search, dialogs, Stats for Nerds, and visualizer overlays all need a pass.
- Pay special attention to transparent or semi-transparent surfaces that may look fine on dark backgrounds and fail on light ones.
- Use theme tokens/shared UI colors where possible so fixes do not become one-off color overrides.
- Preserve the current dark-mode look where it already works well.

### Acceptance Criteria

- The app is readable on Windows with system light mode enabled.
- The app is readable on Windows with system dark mode enabled.
- Text and interactive controls meet reasonable contrast expectations in both modes.
- Users can choose System, Dark, or Light mode if automatic system theme detection is unreliable.
- Album-art/accent colors never make core text or controls unreadable.
- Screenshots of the main desktop routes show coherent styling in both dark and light mode.
