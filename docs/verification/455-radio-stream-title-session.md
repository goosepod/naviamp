# First internet radio stream title

Tracking: issue #455, contributor PR #456, v2.11.0.

The engine publishes a live stream's first ICY title before the asynchronous sidecar load launched
by Playing resets the track's sidecars, so the title was lost until the next ICY update. The fix
caches the engine-published metadata and restores it after the sidecar load of the same track.

Review found that the cache survived across playback sessions: playing station A, switching to B,
and returning to A before B published metadata restored A's title from its previous session. The
cache is now consumed by the sidecar load it bridges — captured and cleared before
`loadForTrack`, restored after only if no fresher metadata arrived during the load. A later
playback of the same station starts from empty metadata.

The common regression `returningToAnEarlierStationDoesNotRestoreItsPreviousTitle` covers A → B → A
alongside the existing same-load restore and cross-track tests. Manually verified on an Android TV
against a live Octo/Navidrome internet radio source: returning to a station shows no title until
fresh metadata arrives, and the first title of a fresh playback survives the sidecar load.

## Follow-up review, 2026-11-06

The earlier cache-consumption change still left two gaps. A title published after the initial
sidecar load survived a rapid A → B → A transition if B's queued load never started, including
when B never reached Playing. Also, an old sidecar operation that completed despite cancellation
could restore its captured title after returning to A and receiving a fresh title.

The shared playback controller now invalidates the title and cancels the old sidecar job at the
actual track transition. After an asynchronous sidecar load returns, it checks cancellation before
restoring captured metadata. No platform production code changes are needed.

Three new common regressions reproduce these cases against the contributor's earlier code. The
first two failed together before the correction; the cancelled-load case failed in a separate
focused run. After the correction, all 96 focused JVM cases passed: 46 playback-controller,
44 engine-adapter, and 6 internet-radio-controller cases, with no skips. Android shared-presentation
compilation and `verifyCoreFirstArchitecture` also passed. Raw local logs are retained under
`build/verification/455/`. This follow-up did not repeat the earlier live TV check.
