# Manual skip stream lifetime

Tracking: issue #465, contributor PR #467, v2.11.0.

Manual navigation must invalidate the previous playback generation and stop its native stream
before resolving/starting the next track. Slow resolution must go quiet immediately; immediate
resolution must leave the new stream playing. Automatic advance keeps its prepared transition.
RestartCurrent and None keep their existing early-return behavior. Prior sidecar work is cancelled
as soon as queue selection changes.

Review found that calling `engine.stop()` after `startCurrent()` relies on deferred coroutine
dispatch. The added common regression uses an eager test dispatcher and an immediately available
provider. The original contributor implementation produced `clear, play:next, stop`, demonstrating
that a new stream could be stopped after starting. The corrected common owner increments its
generation, cancels the old resolution, and stops the old stream before launching the next
resolution. No host or platform production code changes are needed.

The regression expects `clear, stop, play:next`. The existing slow-resolution, rapid double-skip,
prepared automatic transition, preview, pause/resume and sidecar cancellation tests remain part of
the focused controller/engine suite. Shared Android, JVM and both iOS targets must compile, and the
complete required GitHub matrix still gates merge against current main.

## Combined contributor review

The seven owner-selected contributor PRs (#456, #461, #462, #463, #464, #467 and #468) were
combined only in an isolated test checkout at `a65d3205487a18f4e200b78aa269a29db2b7d670`. No integration branch was pushed.
All 262 focused tests passed: 32 domain (lyrics/settings), 86 presentation (playback controller
and engine), 126 Navidrome provider, and 18 UI (radio matching/TV focus). Shared presentation
compiled for Android, JVM and both iOS targets. Append-only translation conflicts preserved
every entry from both sides; all production changes in these PRs belong to common code.
This combined regression does not replace each PR's required CI against current main.
