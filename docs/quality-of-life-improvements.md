# Quality of Life Improvements

This document tracks small interaction and layout improvements that should make Naviamp feel more natural during daily use.

## Pull to Refresh

Status: Done.

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
- Desktop should still expose an explicit refresh affordance for the same pages, likely through a compact three-dot page menu with a Refresh action, so desktop users have parity without forcing a touch-style gesture.

### Acceptance Criteria

- Pulling down at the top of each page starts a refresh.
- A visible refresh indicator appears while refresh is running.
- Refresh errors surface through the existing status/error UI for that page.
- Existing scroll position is preserved when the refreshed content still supports it.

## Mixes For You Starts Playback

Status: Done.

Fix the Mixes For You cards on the home page so selecting one starts the intended generated mix instead of opening an album detail view.

### Current Behavior

Selecting a Mixes For You item currently navigates into an album. These cards used to start playback for the generated mix directly.

### Desired Behavior

Selecting a Mixes For You card should start playing the generated mix it represents. It should not route to an album unless the user explicitly chooses an album-specific action elsewhere.

### Notes

- Confirm whether this is a shared route/action mapping regression or a platform-specific event handling issue.
- Preserve any existing queue-building behavior that previously powered these mixes.
- Verify that the selected mix populates the queue with the expected related tracks and starts playback immediately.
- Keep behavior consistent across Android and desktop if the home page uses shared UI/action plumbing.

### Acceptance Criteria

- Selecting each Mixes For You item starts playback for that generated mix.
- Selection does not open an unrelated album detail page.
- The resulting queue represents the selected mix type and source item.
- The behavior works on both Android and desktop where Mixes For You is available.

## Mix Builder Artwork

Status: Done.

Replace the plain grey boxes under Mix Builders on the home page with richer icon artwork that makes each builder easier to identify at a glance.

### Current Behavior

Mix Builder rows use simple grey placeholder boxes, which makes the section feel unfinished compared with the rest of the app.

### Desired Behavior

Each Mix Builder item should have a distinct icon treatment with Naviamp-appropriate color, shape, and gradient styling. The result should feel closer to Plexamp's simple, polished mix/radio icon language without copying it directly.

### Notes

- Create distinct treatments for Artist Mix Builder, Album Mix Builder, Library Radio, Deep Cuts Radio, Time Travel Radio, Random Album Radio, Genre Radio, Style Radio, and any other builder rows in the same group.
- Prefer reusable generated/vector assets or shared composables over one-off placeholder boxes.
- Use gradients and icon silhouettes sparingly so the artwork stays readable at small row sizes.
- Keep colors compatible with the app's dark presentation and album-art-derived backgrounds.
- Avoid making the icons look like tappable buttons separate from the row action.

### Acceptance Criteria

- Every Mix Builder row has a distinct non-placeholder icon.
- Icons remain legible at the current row size on desktop and Android.
- The styling feels coherent with Naviamp's existing visual language.
- The icons do not introduce layout shifts, clipping, or low-contrast states.

## Now Playing Bottom Controls Stay Visible

Status: Done.

Fix the Now Playing screen so the bottom row of icons keeps a stable size and remains visible as the desktop window is resized.

### Current Behavior

When the desktop window height changes, the bottom row of Now Playing icons shrinks and can disappear. This affects controls such as the radio/visualizer/queue/menu icons shown along the bottom of the Now Playing panel.

### Desired Behavior

The bottom icon row should have a stable touch/click target size and should always remain visible. Resizing the window should compress flexible content above the controls, not shrink or hide the controls themselves.

### Notes

- Treat the controls as fixed-size chrome within the Now Playing layout.
- Audit the vertical sizing priorities around album art, waveform, track metadata, rating, volume, transport controls, and the bottom action row.
- The screenshots from June 19, 2026 show the bottom icons shrinking and disappearing as the window gets shorter.
- Preserve usability on both compact and roomy desktop window sizes.

### Acceptance Criteria

- Bottom Now Playing icons keep the same visual size while resizing the desktop window.
- The bottom icon row remains visible at the supported minimum desktop window height.
- Other content adapts without overlapping the controls.
- Hit targets remain large enough to use reliably.

## Synced Lyrics Current-Line Position

Status: Done.

Adjust the synced lyrics panel so the current lyric line sits slightly above center in the seven-line display.

### Current Behavior

After the song gets past the first few lines, the active lyric appears as the fifth visible line. With seven visible lines, this makes the active line feel slightly too low.

### Desired Behavior

Move the active lyric up so it appears as the third visible line when there is enough surrounding lyric context.

### Notes

- Keep the early-song behavior graceful when there are not enough previous lines to center the active line.
- Keep the end-of-song behavior graceful when there are not enough following lines.
- Preserve the current lyric highlighting and offset controls.
- Apply the behavior consistently across desktop and Android if they share the lyrics panel implementation.
- On Android, keep already-loaded provider or embedded lyrics visible if an LRCLIB sync upgrade times out later.

### Acceptance Criteria

- With seven visible synced lyric lines and enough surrounding context, the active line is third from the top.
- The first few lines do not leave unnecessary blank space above the lyrics.
- The final few lines do not jump or leave unnecessary blank space below the lyrics.
- Manual lyrics offset adjustment still updates the highlighted line immediately.

## Desktop Installer Options

Status: Moved to `docs/new-features-roadmap.md`.

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
- The current jpackage MSI path now creates a Naviamp Start Menu entry by default; an optional prompt still needs a custom installer path if we want the user to choose during installation.

### Acceptance Criteria

- Desktop release artifacts include a standard installer and a clearly named thin smart installer option.
- The standard installer runs without requiring any system Java installation.
- The thin smart installer uses an existing compatible Java runtime when one is available.
- The thin smart installer falls back to Naviamp's bundled runtime when no compatible runtime is available.
- Both installer variants include the required BASS playback libraries and Naviamp JNI library.
- Both installer variants launch the same Naviamp version and report the same app version in About.
- Windows installers ask whether to add Naviamp to the Start Menu and honor the user's choice.

## Visualizer Performance Pass

Status: Moved to `docs/new-features-roadmap.md`.

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

## Lyric Mirror Tunnel Visualizer

Status: Prototype checkpoint; needs follow-up polish.

Add a lyric-forward visualizer built around album-art-derived color, mirror-like depth, and beat-reactive lyric typography.

### Current Behavior

Existing visualizers are mostly abstract audio-reactive scenes. Lyrics can be shown in the Now Playing UI, but they are not the central visualizer subject.

### Desired Behavior

Create a visualizer where the song lyrics are the star. A colored line travels around the outer edge of the visualizer area with a long fading tail. The line color should match the brightest or most prominent useful color from the track cover art. On strong beats, nested rectangular outlines appear inside the outer box, like a multi-mirror reflection receding into distance. The nested boxes should use colors pulled from the album art and become fainter and more transparent as they move inward.

The current lyric line should appear prominently in the center, wrapping when needed. The text should read as black letters with a white outline or drop shadow. On strong bass hits and near the transition to the next lyric line, the white outline/shadow should react: the outer edge of the letters should slowly burst outward, then fade quickly, while the black interior fades away more simply.

### Goals

- Make lyrics feel like the primary performance element, not an overlay.
- Use album art colors for the perimeter tracer, mirror boxes, and accent reactions.
- Keep lyric readability high even with active visuals behind it.
- Sync lyric transitions to timed lyrics when available, with graceful fallback for untimed lyrics.
- React more strongly to bass/beat energy than to general loudness.

### Notes

- 2026-07-04 checkpoint: A first native GPU-backed Lyric Mirror Tunnel implementation exists with lyric loading, lyric-offset timing, adaptive lyric mask sizing, nested frames, and initial shader particles. It is not final-quality yet; the visual design still needs a serious polish pass for fluid background motion, more convincing outline breakup/sparkle behavior, richer beat-driven depth, and overall professional feel.
- The perimeter tracer tail should remain visible long enough that the moving front catches up to a still-visible tail.
- The mirror-box effect should feel like depth or reflection, not simply stacked borders.
- The lyric explosion should affect the outline/shadow more than the filled text.
- Consider GPU/runtime-shader rendering if Compose text effects are too expensive or too limited.
- This concept depends on reliable lyric timing, album-art palette extraction, and beat/bass signal quality.

### Acceptance Criteria

- The visualizer has a clear outer tracer with a long fading tail.
- Beat events create nested, fading album-art-colored boxes inside the frame.
- The current lyric line is readable, centered, and wraps cleanly.
- Bass hits visibly affect the lyric styling without making the words unreadable.
- Lyric line changes include the requested outline/shadow burst and quick fade.

## Android Bluetooth Speaker Playback Crash

Status: Needs investigation.

Investigate an Android playback crash that occurs after switching from wireless Android Auto use to a regular Bluetooth speaker route.

### Current Behavior

After a successful wireless Android Auto drive session, switching to a regular Bluetooth speaker caused Naviamp to crash repeatedly after roughly a minute of playback. The issue became severe enough that another music app had to be used instead.

### Desired Behavior

Naviamp should remain stable when moving between Android Auto, phone playback, and standard Bluetooth audio routes. Route changes should not leave stale Android Auto, media session, audio focus, foreground service, or playback-engine state that later crashes normal Bluetooth playback.

### Goals

- Capture crash logs from a real device after reproducing the Android Auto to Bluetooth transition.
- Determine whether the crash is in foreground service lifecycle, audio focus/noisy-route handling, media session updates, BASS playback, notification updates, or Android Auto browser/session cleanup.
- Preserve the working wireless Android Auto behavior while fixing the Bluetooth route instability.
- Add defensive handling or lifecycle tests around route/session transitions if a reproducible cause is found.

### Notes

- Repro path reported on July 4, 2026: wireless Android Auto in a truck worked normally; later, normal Bluetooth speaker playback crashed after about a minute.
- Compare behavior after force-stopping Naviamp between the Android Auto session and Bluetooth playback to separate stale-session state from a general Bluetooth playback bug.
- Check logcat for native crashes, foreground-service exceptions, media session errors, and repeated audio focus transitions.

### Acceptance Criteria

- The Android Auto to regular Bluetooth speaker transition can be reproduced or confidently ruled out.
- Crash logs identify a concrete failure point.
- Playback remains stable for at least 30 minutes on a regular Bluetooth speaker after an Android Auto session.
- Android Auto playback remains stable after the fix.

## Configurable Download Path

Status: Done.

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

## Dark Mode Rendering With OS-Matched Window Chrome

Status: Done

Make Naviamp readable and polished on both dark-mode and light-mode desktop systems by keeping the app content on Naviamp's existing dark presentation while letting only native window chrome follow the operating system.

### Current Behavior

On a Windows machine using a non-dark system theme, the app can become extremely hard to read. Text, surfaces, controls, and contrast appear tuned for dark mode and degrade badly when the surrounding OS or Compose theme resolves differently.

### Desired Behavior

Naviamp's in-app rendering should always use the current dark-mode look. The native title bar/window chrome should continue to match the operating system's light or dark appearance where the platform supports it.

### Goals

- Audit desktop theme selection on Windows, macOS, and Linux.
- Decouple in-app Compose colors from system light/dark detection.
- Keep every major app surface, text color, icon tint, divider, selected row, disabled state, dialog, menu, and input field on the existing readable dark theme.
- Keep album-art-derived accents from overpowering text contrast or making controls unreadable.

### Notes

- Test on a Windows machine with light mode enabled, not only by toggling previews or assumptions in code.
- The player, navigation, settings, library, playlists, search, dialogs, Stats for Nerds, and visualizer overlays all need a pass.
- Pay special attention to transparent or semi-transparent surfaces that may accidentally depend on a light Material theme.
- Use theme tokens/shared UI colors where possible so fixes do not become one-off color overrides.
- Preserve the current dark-mode look where it already works well.
- The OS should only influence the native title bar/window chrome, not the app content theme.

### Acceptance Criteria

- The app content is readable on Windows with system light mode enabled because it still uses Naviamp's dark in-app theme.
- The app content is readable on Windows with system dark mode enabled.
- The native title bar/window chrome follows the operating system light or dark appearance where supported.
- Text and interactive controls keep the same contrast as the current dark-mode presentation.
- Album-art/accent colors never make core text or controls unreadable.
- Screenshots of the main desktop routes show coherent dark in-app styling under both system light and dark modes.
