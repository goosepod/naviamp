-- Run with Perfetto trace_processor query -f this-file.sql trace-file.
-- Capture the real profileable app with android-player-sample.py --trace, which retains
-- process snapshots in an explicit 128 MiB buffer. Legacy CLI defaults can lose metadata
-- or the beginning of a busy interval. Do not interact with the player during capture.
-- Thread CPU, including GLES and the parent-window renderer, in seconds.
SELECT t.name AS thread, SUM(s.dur) / 1e9 AS cpu_seconds
FROM sched s JOIN thread t USING (utid) JOIN process p USING (upid)
WHERE p.name = 'app.naviamp.android.benchmark'
GROUP BY t.utid ORDER BY cpu_seconds DESC;

-- System compositor diagnostic, not app-attributed CPU or an uninstrumented budget result.
-- Repeat with android-player-sample.py --compositor to exclude gfx tracing overhead.
SELECT t.name AS compositor_thread, SUM(s.dur) / 1e9 AS cpu_seconds
FROM sched s JOIN thread t USING (utid) JOIN process p USING (upid)
WHERE p.name = '/system/bin/surfaceflinger'
GROUP BY t.utid ORDER BY cpu_seconds DESC;

-- Actual GLES draw callbacks; submission acceptance alone is insufficient.
SELECT COUNT(*) AS draws, (MAX(s.ts) - MIN(s.ts)) / 1e9 AS draw_span_seconds,
  (COUNT(*) - 1) * 1e9 / NULLIF(MAX(s.ts) - MIN(s.ts), 0) AS draw_fps
FROM slice s JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t USING (utid) JOIN process p USING (upid)
WHERE p.name = 'app.naviamp.android.benchmark' AND s.name = 'onDrawFrame';

WITH frames AS (
  SELECT s.ts, s.dur FROM slice s
  JOIN thread_track tt ON s.track_id = tt.id
  JOIN thread t USING (utid) JOIN process p USING (upid)
  WHERE p.name = 'app.naviamp.android.benchmark' AND s.name = 'onDrawFrame'
), intervals AS (
  SELECT (ts - LAG(ts) OVER (ORDER BY ts)) / 1e6 AS interval_ms FROM frames
)
SELECT ROUND(interval_ms / 5) * 5 AS interval_bucket_ms, COUNT(*) AS frames
FROM intervals WHERE interval_ms IS NOT NULL
GROUP BY interval_bucket_ms ORDER BY interval_bucket_ms;

-- GLES native draw/driver cost and unrelated parent buffer swaps.
SELECT t.name AS thread, s.name AS operation, COUNT(*) AS calls,
  AVG(s.dur) / 1e6 AS mean_ms, MAX(s.dur) / 1e6 AS max_ms
FROM slice s JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t USING (utid) JOIN process p USING (upid)
WHERE p.name = 'app.naviamp.android.benchmark'
  AND s.name IN ('onDrawFrame', 'eglSwapBuffers', 'queueBuffer')
GROUP BY t.utid, s.name ORDER BY calls DESC;

-- Progress/marquee callbacks and raster content replacement. These durations are wall time,
-- including waits; use sched above for CPU time. A low parent frame count alone is insufficient.
SELECT t.name AS thread, s.name AS operation, COUNT(*) AS calls,
  SUM(s.dur) / 1e6 AS total_wall_ms
FROM slice s JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t USING (utid) JOIN process p USING (upid)
WHERE p.name = 'app.naviamp.android.benchmark'
  AND s.name IN ('animation', 'allocateHardwareBitmap', 'uploadHardwareBitmap')
GROUP BY t.utid, s.name ORDER BY calls DESC;
