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
