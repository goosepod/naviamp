# Desktop Performance Optimization Plan

## Goal

Bring Naviamp desktop idle CPU closer to Plexamp-style behavior. The target is to make idle playback and idle connected states feel quiet: no constant disk churn, no avoidable database polling, and no UI work unless something visible actually changed.

Current user-observed baseline on Windows:

- Plexamp generally stays at or below roughly 2.5% CPU.
- Naviamp sits around 6.5% CPU or higher.
- Turning lyrics or the visualizer on/off does not noticeably change CPU usage.

That last point matters: the visualizer and lyrics paths still need guardrails, but they are probably not the main idle CPU driver.

## Likely Hot Spots

### 1. Hidden stats and cache work during normal app recomposition

`Main.kt` currently builds `DesktopStatsForNerdsInfo` unconditionally. That calls `sessionCache.stats()`, which runs many SQLite aggregate queries:

- image count and size
- response count
- audio cache count and size
- download count and size
- waveform count and size
- lyrics count and size
- media source count
- library artist, album, and track counts

This is useful in Stats for nerds and Settings, but it should not be paid for while sitting on Home, Player, Search, or Artist Details. If playback progress, cover art, queue state, or any other UI state causes recomposition, these database queries can come along for the ride.

Implemented first pass:

- Move `sessionCache.stats()` behind route/window-specific demand.
- Keep a cached `CacheStats` state.
- Refresh it only when opening Stats for nerds, Settings, Downloads, or after cache-affecting actions.
- Avoid calculating full diagnostics in the top-level app tree.
- Avoid the startup stats sweep; initialize with empty stats until a visible route needs real numbers.

### 2. Playback progress updates at 10 Hz may recompose too much UI

`DesktopBassPlaybackEngine` emits progress every 100 ms while a track is active. That is appropriate for a smooth scrub bar, but the current top-level state shape means one progress update can invalidate a broad part of `NaviampApp`.

Measured on `build/local-test/Naviamp`, Now Playing open with a song playing:

- Before throttling: about 36% of one CPU core over a 15-second sample.
- After throttling visible progress state to roughly twice per second: about 13% of one CPU core over a 15-second sample.
- Disk I/O stayed at 0 MB/s.

Implemented first pass:

- Keep raw playback progress flowing to save-position and play-reporting logic.
- Throttle visible Compose playback progress state to avoid redrawing the Now Playing details and waveform scrubber 10 times per second.

Still planned:

- Split frequently changing playback progress away from slow-changing app state.
- Consider reducing progress updates when the Player screen is not visible, while still keeping media/session save logic correct.
- If playback CPU remains too high, separate the static waveform bars from the moving playhead so only the playhead redraws on progress changes.

### 3. Player title marquee animation may run continuously

`BouncingTitleText` has an infinite loop while text overflows. If a long title is displayed, this animation can keep Compose active even when nothing else is changing.

Implemented first pass:

- Do not run the marquee for placeholder/empty Now Playing states.

Still planned:

- Only animate marquee text when actual track text needs it.
- Consider replacing continuous bouncing with a slower, less frequent marquee cycle.

### 3a. Windows Skiko Direct3D renderer can burn a full idle core

On Windows, the default Skiko renderer pegged one native render thread at roughly one full CPU core while Naviamp was idle on the empty Now Playing route. The JVM thread dump showed Java/coroutine threads parked and no disk I/O, which pointed away from cache/import/playback code and toward native rendering.

Measured on `build/local-test/Naviamp`:

- Default renderer: about 100% of one CPU core while idle.
- `SKIKO_RENDER_API=SOFTWARE`: 0% over a settled 10-second idle sample.
- `SKIKO_RENDER_API=OPENGL`: 0% over a settled 10-second idle sample.

Implemented first pass:

- Force `-Dskiko.renderApi=OPENGL` for Windows packaged desktop builds.

Follow-up visualizer testing on Windows showed the forced OpenGL backend made visualizer animation feel choppy even after faster visualizer FFT sampling. A packaged build using Skiko's default Windows renderer made visualizer animation fluid, improved visual quality, and stayed around 8-9% CPU in Task Manager even with detailed visualizers running. Windows packaged builds now use the default Skiko renderer unless `-Pnaviamp.windows.skiko.renderApi=OPENGL` or another explicit renderer is passed for testing.

### 3b. macOS packaged builds should stay on Metal

macOS Compose Desktop should use Skiko Metal for the normal packaged app path. Keep this explicit so local packaged builds do not silently drift to a less efficient renderer while comparing idle CPU across platforms.

Measured on `build/local-test/Naviamp.app`, idle after launch with no playback interaction:

- `top`: about 0.1-0.2% CPU over a settled sample window.
- `sample`: main thread parked in the AppKit event loop; Java, coroutine dispatcher, and Skiko threads were parked with no hot runnable app thread.

Implemented first pass:

- Force `-Dskiko.renderApi=METAL` for macOS packaged desktop builds.
- Make local staging and local zip tasks platform-aware so macOS testing uses `Naviamp.app`, matching the Windows staged app workflow.

### 4. Cover-art decoding and palette extraction can be expensive

The JVM cover art path decodes images and may extract palettes by sampling pixels. Recent changes reduced preloading, but this path still deserves measurement because album art changes and queue rows can trigger image work.

Planned exploration:

- Confirm hot-image memory hits vs SQLite image-cache hits vs network loads.
- Ensure palette extraction only happens for the now-playing background.
- Avoid duplicate image decode work for thumbnails and player colors.
- Add timing counters to Stats for nerds if needed.

### 5. Background sidecar work needs stricter visibility and cache rules

Waveform, embedded tags, provider lyrics, and LRCLIB lyrics run as sidecar tasks. User observation suggests lyrics are not the primary CPU problem, but sidecar scheduling still needs careful gating.

Planned exploration:

- Verify no sidecar work runs while idle with no track.
- Verify no sidecar work reruns for a track when cached results exist.
- Ensure now-playing-only work does not restart on unrelated route changes.

### 6. Library import and cache maintenance can look like idle load

Previous sluggishness was partly explained by library import running in the background. That should now be mostly controlled by the scan-status freshness check, but we still need clearer visibility.

Planned fix:

- Surface active background work in Stats for nerds.
- Add timestamps/counters for library sync, prefetch, sidecars, and cache trims.
- Make sure no sync or cache trim loop can run silently forever.

## Measurement Plan

- Establish three repeatable local scenarios:
  - Connected and idle, no track playing.
  - Track playing on Player screen.
  - Track playing on Home/Search screen.
- Record CPU, disk, and memory for each scenario before each fix.
- Add temporary lightweight counters where Task Manager cannot distinguish causes.
- Prefer application-level counters first, then use JVM profiling if needed.

Useful probes to add:

- Recomposition-sensitive state tick counters for playback progress and diagnostics.
- Count and time `sessionCache.stats()` calls.
- Count and time cover art loads, image decodes, and palette extraction.
- Count sidecar starts/completions: waveform, embedded tags, provider lyrics, LRCLIB.
- Count audio prefetch starts/completions/cancellations.

## Fix Checklist

- [x] Move `sessionCache.stats()` out of unconditional top-level composition.
- [x] Cache `CacheStats` and refresh it only on Stats/Settings/Downloads demand or cache mutations.
- [ ] Add temporary timing logs/counters for `sessionCache.stats()`.
- [ ] Measure CPU/disk after stats gating.
- [ ] Isolate playback progress updates so they do not rebuild unrelated screens.
- [ ] Consider lower-frequency progress updates when Player is not visible.
- [x] Gate placeholder/empty `BouncingTitleText` marquee animation.
- [x] Identify Windows default Skiko renderer idle CPU burn.
- [x] Force Windows packaged app to use Skiko OpenGL renderer.
- [x] Re-test idle no-track scenario with packaged OpenGL renderer.
- [x] Force macOS packaged app to use Skiko Metal renderer.
- [x] Make local desktop staging/zip tasks work for macOS `.app` bundles.
- [x] Re-test macOS idle no-track scenario with packaged Metal renderer.
- [x] Throttle visible playback progress updates while preserving play reporting and saved-position checks.
- [x] Re-test playing track on Player screen after progress throttling.
- [ ] Confirm cover-art palette extraction is now-playing-only.
- [ ] Add cover-art decode/cache hit counters if CPU remains high.
- [ ] Audit sidecar task restart keys for waveform/tags/lyrics.
- [ ] Add Stats for nerds background-work counters.
- [ ] Re-test idle no-track scenario after every fix.
- [ ] Re-test playing track on Player screen after every fix.
- [ ] Re-test playing track off Player screen after every fix.

## First Fix Candidate

Start with stats gating. It is low-risk, easy to verify, and directly explains both CPU and disk symptoms:

- SQLite aggregate queries can produce continuous disk activity.
- The work is not needed unless Stats, Settings, or Downloads are visible.
- It can be fixed without changing playback behavior.

After that, measure again. If CPU remains above target, move to playback-progress recomposition and marquee animation.
