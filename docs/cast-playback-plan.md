# Google Cast playback plan (#147)

Status: architecture and compatibility investigation started on 2026-09-23. The first shared
output-selection model is connected to the existing Connect controller, with common tests. A
shared Cast session contract and controller now own selection, session status,
and stale callback rejection. The Android SDK adapter translates native session callbacks into
that common controller. A debug-only Android probe opens the native route picker and exercises the
adapter. Core owns expiring opaque media leases, HTTP request authorization, and provider byte
selection. Navidrome and Jellyfin stream authenticated track bytes with range response metadata;
Android binds a receiver-reachable socket. The shared output router now diverts queue selection
and transport commands from local audio while Cast is selected. Core creates a paused receiver load
from an opaque track URL, stops local audio only after the load is accepted, and then starts the
receiver. Receiver status drives shared progress and queue completion; returning to local restores
the receiver position. The shared Now Playing menu opens the Android SDK route picker. Physical
Android testing has confirmed playback on Roku and Onn 4K Pro receivers, and return-to-local
handoff on the Onn. The PR remains draft pending lifecycle checks and broader platform coverage.

## Existing shared owners

- `NaviampConnectPlaybackDestinationController` uses the shared local/Connect/Cast output selection model so two remote outputs cannot own playback simultaneously.
- `NaviampCoreConnectRemoteNowPlaying` projects receiver-authoritative playback into the shared UI. Extract the reusable remote snapshot and command policy before adding a Cast-specific source; retain Connect's protocol mapping separately.
- The Core queue and playback controllers own handoff, position, navigation, reporting, and return-to-local policy. A Cast sender adapter may report receiver events and execute commands, but may not keep a second queue or decide when local audio stops.
- Providers construct stream URLs in `commonMain`. Both Navidrome and Jellyfin can embed credentials in their URL query. No provider URL, artwork URL, or local file path may be passed directly to a Cast receiver without an explicit media-access decision.

## Receiver access decision

The first implementation target is a receiver-reachable, short-lived media endpoint controlled by Naviamp. Core owns its lease lifetime, allowed media identity, playback quality, expiry, invalidation, and recovery. A narrow host effect binds a listening socket and serves byte-range requests. It fetches provider bytes through the existing shared provider contract, keeping provider credentials on the sender. Receiver-visible URLs contain only scoped opaque tokens; never provider tokens, API keys, or session credentials. Artwork needs the same access policy. The endpoint must keep working while the sender is backgrounded for as long as the host can retain its required native service; this is a physical-device acceptance gate, especially on iOS.

The socket and authenticated Navidrome MP3 playback have been verified with an Onn 4K Pro over
local Wi-Fi. Background lifetime and other providers and formats still need physical receiver
verification. If a host cannot sustain the endpoint, define one common product
behavior for that capability instead of silently exposing different Cast features by platform.

The initial receiver load requests MP3 transcoding at 320 kbps when the provider supports it, or
uses an original MP3 source. Other original formats wait for a verified receiver format policy.
The Default Media Receiver receives only the scoped sender endpoint URL and artwork URL.

The current shared lease policy issues a URL-safe, sender-scoped token for a track or artwork ID,
expires it after one hour by default, and revokes it when Core requests. The shared HTTP gate accepts
only GET/HEAD on that token path, validates a single byte range, and rejects unknown or expired
leases. Android supplies 256 bits of cryptographic random data for each token. The URL contains no
provider ID or credential. The provider byte source streams track chunks through its existing
authenticated client and caps buffered artwork at 8 MiB. The Android socket binds only to a local
Wi-Fi or Ethernet address and forwards the shared response headers and chunks.

## Sender and receiver matrix to verify

| Sender | Native boundary | Initial verification |
| --- | --- | --- |
| Android | Cast SDK discovery, session callbacks, permissions, and foreground lifecycle | Physical Android sender and Cast audio/video receiver |
| iOS | Cast SDK discovery, session callbacks, local-network permission, and background lifecycle | Physical iPhone/iPad and Cast receiver |
| Desktop | No native desktop sender SDK is listed in Google's sender matrix; investigate a supported browser/Web Sender bridge or another documented route before promising native Desktop Cast | Windows, macOS, and Linux with physical receiver |

Google documents Android, iOS, and Web sender SDKs. Its Android framework owns discovery and starts
a session when a user picks a route; the Android adapter reports that choice to Core instead of
trying to connect to a route ID directly. The Default Media Receiver can load a supplied media URL,
while authentication or custom receiver logic calls for a Custom Web Receiver. The media-access
proof will determine whether the Default Media Receiver is sufficient; do not register or ship a
custom receiver until that choice is supported by evidence.

On 2026-09-23, a Pixel 10a running the debug probe initialized Cast SDK 22.3.1 and opened the native
route picker. It discovered `GoogleTV8565` and `Living Room TV`. Neither receiver was selected or
used for playback. The probe lives in the Android debug source set and uses the Default Media
Receiver application ID. This establishes phone-side discovery only; receiver reachability, media
delivery, and playback remain untested. A later probe on that phone exposed a scoped test endpoint
at its Wi-Fi address. From the Mac, a full GET returned 200, a ranged GET returned 206 with the
expected four bytes, and HEAD returned headers without a body. Invalid ranges returned 416 and
unknown tokens returned 404. This proves local-network HTTP behavior for test bytes, not provider
media delivery to a Cast receiver.

The normal Naviamp Now Playing menu now opens the same native route picker on the Pixel 10a. The
picker again listed both televisions, and it was dismissed without selecting either one. The
Android host uses `FragmentActivity` and an AppCompat activity theme because the MediaRouter
dialog requires both. This verifies sender discovery and the product entry point, not Cast playback.

### Physical receiver feedback (2026-09-23)

The user tested the `v2.7.1-casttest` Android build on the Pixel 10a. Their setup has a Roku TV
with an Onn 4K Pro TV box connected to it. These are separate Cast targets:

- **Roku TV:** Selecting the TV switched it to a playback view with the track and album art.
  Playback worked as expected. Stopping Cast control stopped playback immediately.
- **Onn 4K Pro:** Naviamp reported that it was controlling the Onn, but nothing played on the box.
  The Onn uses the Projectivy launcher. Whether that launcher affects receiver behavior is unknown.

This prompted a receiver-side check of scoped media URL reachability, media loading, and playback.
A connected Cast session alone does not establish successful playback.

### Physical receiver retest (2026-09-27)

On a Pixel 6a with a fresh Navidrome test account, the Onn 4K Pro launched the Default Media
Receiver and accepted LOAD and PLAY. At first it could not fetch the phone's scoped media endpoint:
TCP connections from both the receiver and another LAN host to the phone's Wi-Fi address timed out
while the phone's Tailscale exit node was active. With the exit node disconnected, both hosts could
reach the endpoint. On the stable local Wi-Fi network, the receiver fetched and played authenticated
Navidrome MP3, displayed album art, reported progress to the phone, honored seek and pause/resume,
and advanced a multi-track queue. This verifies Onn playback with the current shared media path;
the earlier silence was caused by endpoint reachability in that test network. Cast sessions are
expected to use a stable network; preserving them across network changes is out of scope.

The same run exposed a return handoff bug. After using the route picker to stop casting, the native
route disconnected but Naviamp still displayed the Onn as the playback device and local playback
did not resume at the receiver position. Core and the Android SDK callback mapping were adjusted.
On 2026-09-27, the Pixel 6a and Onn verified the fix with the updated build: stopping Cast during
receiver playback cleared the TV output and started local audio near the receiver position. The
Pixel audio player was active. Stopping Cast while the receiver was paused cleared the TV output,
kept the receiver position (2:19), and left local playback paused. In both cases the TV returned
to its home screen. With Naviamp on the Pixel home screen for 20 seconds, the Onn Cast media
session remained playing and its music audio output stayed active. Longer background runs, screen
lock, process recreation, and other lifecycle cases remain to be verified.

## First implementation sequence

1. Extend the shared playback destination model to represent one selected local, Connect, or Cast output, with explicit authority and return-to-local transitions. Test stale callbacks, receiver loss, reconnect, and no duplicate local playback.
2. Define a shared Cast effect contract for discovery, session, status, commands, and receiver media access. Put capabilities and command eligibility in Core; host adapters translate only SDK and socket operations.
3. Prove a single authenticated track and artwork transfer using a physical receiver. Record the sender/receiver and provider matrix, media format, HTTPS/LAN behavior, range requests, and background/restore behavior.
4. Add shared queue handoff, position reconciliation, previous/next, seek, volume, repeat/shuffle policy, and source-change handling. Use receiver status as authority while Cast owns playback.
5. Build the shared target picker and Now Playing presentation, add all copy in every maintained translation, and verify accessibility, compact layouts, and lifecycle recovery.
6. Run common Android/JVM/iOS tests before host wiring, then complete the physical-device and cross-platform verification in #147.

## Sources

- [Google Cast SDK overview](https://developers.google.com/cast/docs/overview)
- [Google Cast Web Receiver types](https://developers.google.com/cast/docs/web_receiver)
- [Google Cast supported media](https://developers.google.com/cast/docs/media)
- [Google Cast sender design checklist](https://developers.google.com/cast/docs/design_checklist/sender)
- [Android sender integration](https://developers.google.com/cast/docs/android_sender/integrate)
- [Android SessionManager lifecycle](https://developers.google.com/android/reference/com/google/android/gms/cast/framework/SessionManager)
