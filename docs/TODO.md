# Naviamp TODO

## GitHub Issue #1: Windows Now Playing Polish and Velvet Scrobbling

- **Issue:** [goosepod/naviamp#1](https://github.com/goosepod/naviamp/issues/1)
- **Title:** Little suggestions and scrobbles
- **Reporter:** `mauriva`
- **Reported environment:** Naviamp v1.2.0 on Windows with a Velvet OpenSonic server
- **Status:** Triaged; implementation and Velvet reproduction pending

### Visible Tooltips for Unfamiliar Icons

The shared icon controls already provide accessibility `contentDescription` values, including the Now Playing transport controls. Compose Desktop does not turn those descriptions into visible hover tooltips, so Windows users can still be left guessing what an icon does.

Implementation work:

- Add a reusable tooltip wrapper for shared icon buttons, starting with `NaviampTransportIconButton` in `NaviampNowPlayingUi.kt`.
- Apply the same behavior to Now Playing tab actions, queue overflow actions, and other icon-only desktop controls.
- Keep accessibility descriptions intact and avoid changing touch behavior on Android.
- Use the existing action label or content description as the tooltip text so labels cannot drift apart.

Acceptance criteria:

- Hovering an enabled or disabled icon-only control on Windows shows a short description.
- Tooltips do not obstruct clicking, focus, keyboard navigation, or compact layouts.
- Android retains its current semantics without desktop-style hover UI.

### More Compact Now Playing Queue with Track Durations

`Track.durationSeconds` is already mapped through `durationLabel()`, and `NowPlayingItemList` already renders `item.meta` at the right side of each row. However, `nowPlayingSectionsUi()` deliberately passes `meta = { "" }` for both Back To and Up Next, which suppresses the available duration. Related-track rows use the same metadata slot for similarity details.

Current queue density is controlled by a 32 dp cover, 5 dp row padding, 8 dp internal spacing, and 5 dp spacing between rows in `NaviampNowPlayingUi.kt`.

Implementation work:

- Populate Back To and Up Next metadata with `track.durationSeconds?.durationLabel()`.
- Preserve similarity metadata for Related/Sonic rows.
- Add a denser desktop/wide-player queue presentation without making Android rows too small for touch.
- Check title, artist, duration, artwork, overflow-menu alignment, Play Next headers, and selected-row highlighting on Windows at multiple scaling levels.

Acceptance criteria:

- Every queue track with a known duration shows it in `m:ss` or `h:mm:ss` form.
- Missing durations degrade cleanly without placeholder noise.
- More queue tracks fit vertically while titles and artists remain readable.
- Queue swipe actions and overflow menus continue to work on desktop and Android.

### Verify and Harden Scrobbling with Velvet

Scrobbling is already implemented in Naviamp rather than entirely missing:

- `NavidromeProvider.reportNowPlaying()` calls `scrobble.view` with `submission=false`.
- `NavidromeProvider.reportPlayed()` calls `scrobble.view` with `submission=true` and a playback timestamp.
- The provider advertises `supportsPlayReporting = true`.
- Desktop reports Now Playing when a track starts and submits a play after 50 percent of the track or 240 seconds, whichever comes first.
- Provider tests already verify the generated OpenSubsonic request URLs.

The main diagnostic gap is that desktop wraps both requests in `runCatching` and discards failures. A Velvet incompatibility, rejected parameter, authentication problem, or server-side Last.fm forwarding failure is therefore invisible to the user and difficult to diagnose. Android has durable pending provider actions for failed reports; desktop does not have equivalent persistence.

Investigation and implementation work:

- Reproduce against Velvet using a Windows build and confirm whether both the Now Playing and submitted scrobble requests reach the server.
- Capture the HTTP status and OpenSubsonic error payload without logging credentials or authentication query values.
- Confirm Velvet accepts `submission`, `time`, and Naviamp's track IDs exactly as sent.
- Confirm Velvet is responsible for forwarding accepted server scrobbles to Last.fm and distinguish an accepted OpenSubsonic report from a failed Last.fm delivery.
- Add visible diagnostics or sanitized logging when play reporting fails instead of silently swallowing the error.
- Consider using the shared pending-provider-action mechanism on desktop so transient failures can retry safely.
- Add a compatibility test using a Velvet-shaped response once the failure is understood.

Acceptance criteria:

- A track played past the reporting threshold from Naviamp on Windows appears in the server's play history and reaches the configured Last.fm account.
- Now Playing and final submission are sent at most once per playback session under normal conditions.
- Reporting failures are observable without exposing credentials.
- Transient failures do not permanently suppress later play reports.

### Suggested Order

1. Add sanitized desktop play-report diagnostics and reproduce with Velvet.
2. Restore queue duration metadata and tune desktop queue density.
3. Add reusable visible tooltips to icon-only desktop controls.

