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

`Main.kt` currently builds `StatsForNerdsInfo` unconditionally. That calls `sessionCache.stats()`, which runs many SQLite aggregate queries:

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

`BassPlaybackEngine` emits progress every 100 ms while a track is active. That is appropriate for a smooth scrub bar, but the current top-level state shape means one progress update can invalidate a broad part of `NaviampApp`.

Planned exploration:

- Confirm how much recomposes on each progress tick.
- Split frequently changing playback progress away from slow-changing app state.
- Derive progress labels and fractions as close to the player UI as possible.
- Consider reducing progress updates when the Player screen is not visible, while still keeping media/session save logic correct.

### 3. Player title marquee animation may run continuously

`BouncingTitleText` has an infinite loop while text overflows. If a long title is displayed, this animation can keep Compose active even when nothing else is changing.

Planned fix:

- Only animate marquee text when the Player or mini-player is visible.
- Pause or slow the animation when the window is not focused.
- Consider replacing continuous bouncing with a slower, less frequent marquee cycle.

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
- [ ] Gate or slow `BouncingTitleText` marquee animation.
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
