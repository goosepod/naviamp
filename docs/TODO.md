# Naviamp TODO

## GitHub Issue #1: Windows Now Playing Polish and Velvet Scrobbling

- **Issue:** [goosepod/naviamp#1](https://github.com/goosepod/naviamp/issues/1)
- **Title:** Little suggestions and scrobbles
- **Reporter:** `mauriva`
- **Reported environment:** Naviamp v1.2.0 on Windows with a Velvet OpenSonic server
- **Status:** UI polish implemented on `fix/issue-1-now-playing-polish`; play-reporting follow-up pending

### Visible Tooltips for Unfamiliar Icons

The shared icon controls already provide accessibility `contentDescription` values, including the Now Playing transport controls. Compose Desktop does not turn those descriptions into visible hover tooltips, so Windows users can still be left guessing what an icon does.

Implementation notes:

- Added a shared `NaviampTooltip` expect/actual wrapper.
- Desktop renders visible Compose tooltips for the shared transport/icon buttons, the collapse button, and queue overflow actions.
- Desktop hover tooltips can be turned on or off from Settings > Experience.
- Android keeps the previous touch behavior through a no-op actual while retaining accessibility descriptions.
- Tooltip text comes from the same content descriptions or action labels used by the controls.

Acceptance criteria:

- Hovering an enabled or disabled icon-only control on Windows shows a short description.
- Tooltips do not obstruct clicking, focus, keyboard navigation, or compact layouts.
- Android retains its current semantics without desktop-style hover UI.

### More Compact Now Playing Queue with Track Durations

`Track.durationSeconds` is already mapped through `durationLabel()`, and `NowPlayingItemList` already renders `item.meta` at the right side of each row. However, `nowPlayingSectionsUi()` deliberately passes `meta = { "" }` for both Back To and Up Next, which suppresses the available duration. Related-track rows use the same metadata slot for similarity details.

Current queue density is controlled by a 32 dp cover, 5 dp row padding, 8 dp internal spacing, and 5 dp spacing between rows in `NaviampNowPlayingUi.kt`.

Implementation notes:

- Back To and Up Next rows now populate metadata with `track.durationSeconds?.durationLabel()`.
- Missing durations remain blank.
- Related/Sonic rows keep similarity metadata.
- The wide desktop Now Playing side panel uses denser queue rows; the compact/mobile layout keeps the existing touch-sized rows.
- Mapper tests cover queue durations, unknown durations, and preserve related metadata behavior.

Acceptance criteria:

- Every queue track with a known duration shows it in `m:ss` or `h:mm:ss` form.
- Missing durations degrade cleanly without placeholder noise.
- More queue tracks fit vertically while titles and artists remain readable.
- Queue swipe actions and overflow menus continue to work on desktop and Android.

### Verify and Harden Play Reporting

Scrobbling is already implemented in Naviamp rather than entirely missing:

- `NavidromeProvider.reportNowPlaying()` calls `scrobble.view` with `submission=false`.
- `NavidromeProvider.reportPlayed()` calls `scrobble.view` with `submission=true` and a playback timestamp.
- The provider advertises `supportsPlayReporting = true`.
- Desktop reports Now Playing when a track starts and submits a play after 50 percent of the track or 240 seconds, whichever comes first.
- Provider tests already verify the generated OpenSubsonic request URLs.

The main diagnostic gap is that desktop wraps both requests in `runCatching` and discards failures. A Velvet incompatibility, rejected parameter, authentication problem, or server-side Last.fm forwarding failure is therefore invisible to the user and difficult to diagnose. Android has durable pending provider actions for failed reports; desktop does not have equivalent persistence.

Current Navidrome also has a newer `reportPlayback.view` endpoint behind the OpenSubsonic playback-reporting extension. That endpoint accepts `mediaId`, `mediaType=song`, `positionMs`, and a playback `state` such as `starting`, `playing`, `paused`, or `stopped`. Navidrome uses the final stopped report to increment play counts and dispatch Last.fm/ListenBrainz scrobbles after the same 50 percent or 240 second threshold. This may be a better compatibility path for Navidrome/OpenSubsonic servers than only sending legacy `scrobble.view` requests.

Investigation and implementation work:

- Gate any `reportPlayback.view` experiment on the server advertising the playback-reporting extension, then fall back to legacy `scrobble.view`.
- Confirm the exact advertised extension name/version from Navidrome and other OpenSubsonic-compatible servers before implementing.
- Map Naviamp playback lifecycle events to `starting`, `playing`, `paused`, and `stopped` without double-submitting a legacy scrobble and a reportPlayback stopped event.
- Confirm Navidrome receives reports from Naviamp and forwards accepted scrobbles to the configured Last.fm and ListenBrainz accounts.
- If Velvet testing becomes available from a reporter, confirm whether both the Now Playing and submitted scrobble requests reach the server.
- Capture the HTTP status and OpenSubsonic error payload without logging credentials or authentication query values.
- Add visible diagnostics or sanitized logging when play reporting fails instead of silently swallowing the error.
- Let users choose when Naviamp submits the played/scrobble event from Settings > Experience > Player. Current choices are Start of playback, 10%, 25%, and 50%.
- Consider using the shared pending-provider-action mechanism on desktop so transient failures can retry safely.
- Add compatibility tests for both legacy `scrobble.view` and capability-gated `reportPlayback.view` behavior.

Acceptance criteria:

- A track played past the reporting threshold from Naviamp on Windows appears in the server's play history and reaches the configured Last.fm and ListenBrainz accounts.
- Now Playing and final submission are sent without duplicate scrobbles under normal conditions.
- Reporting failures are observable without exposing credentials.
- Transient failures do not permanently suppress later play reports.

### Suggested Order

1. Verify this branch visually on Windows for tooltip placement and queue density.
2. Add sanitized desktop play-report diagnostics.
3. Prototype capability-gated `reportPlayback.view` against Navidrome with Last.fm and ListenBrainz enabled.
4. Use reporter-provided Velvet logs or a Velvet test environment only if that compatibility issue remains reproducible outside Navidrome.

