# Listen reporting

Naviamp owns listen qualification in shared Core for every platform. It counts observed playback
time while audio is playing and requires half the track or four minutes, whichever is shorter.
Tracks shorter than 30 seconds are excluded. Paused, buffering, and seeked-over time do not count.

Navidrome added `playbackReport` in version 0.62.0. Naviamp selects the mode from the server's
advertised OpenSubsonic extensions rather than its version string, so older Navidrome and other
Subsonic-compatible servers remain on the legacy path. When a server advertises `playbackReport`,
Naviamp sends timeline state with
`ignoreScrobble=true`. These requests update presence and progress only. A qualified listen is sent
once through the timestamped legacy `scrobble(submission=true)` endpoint. If the timeline endpoint
fails, presence switches to `scrobble(submission=false)` without retrying the failed endpoint.

Qualified listens enter the durable pending-action queue before submission. This preserves the
original playback timestamp across process restarts and reconnects. Presence requests are not
queued because replaying old presence would misrepresent current playback.

The Subsonic scrobble protocol has no client idempotency key. If the server accepts a request but
the connection fails before Naviamp receives the response, a later replay can submit that listen
again. The queue prevents local duplication for the same track and timestamp, but it cannot prove
whether an ambiguous network request reached the server.

Successful API submission confirms only that the connected media server accepted the listen.
Delivery to Last.fm, ListenBrainz, or another external service remains controlled by the server's
account configuration and retry behavior.
