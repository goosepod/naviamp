# Google Cast playback plan (#147)

Status: architecture and compatibility investigation started on 2026-09-23. The first shared
output-selection model is connected to the existing Connect controller, with common tests. A
debug-only Android probe initializes the Cast SDK and opens its route picker. Cast playback is not
enabled yet.

## Existing shared owners

- `NaviampConnectPlaybackDestinationController` owns the current local-versus-Connect output intent. Cast must join a single shared output selection model so two remote outputs cannot own playback simultaneously.
- `NaviampCoreConnectRemoteNowPlaying` projects receiver-authoritative playback into the shared UI. Extract the reusable remote snapshot and command policy before adding a Cast-specific source; retain Connect's protocol mapping separately.
- The Core queue and playback controllers own handoff, position, navigation, reporting, and return-to-local policy. A Cast sender adapter may report receiver events and execute commands, but may not keep a second queue or decide when local audio stops.
- Providers construct stream URLs in `commonMain`. Both Navidrome and Jellyfin can embed credentials in their URL query. No provider URL, artwork URL, or local file path may be passed directly to a Cast receiver without an explicit media-access decision.

## Receiver access decision

The first implementation target is a receiver-reachable, short-lived media endpoint controlled by Naviamp. Core owns its lease lifetime, allowed media identity, playback quality, expiry, invalidation, and recovery. A narrow host effect binds a listening socket and serves byte-range requests. It fetches provider bytes through the existing shared provider contract, keeping provider credentials on the sender. Receiver-visible URLs contain only scoped opaque tokens; never provider tokens, API keys, or session credentials. Artwork needs the same access policy. The endpoint must keep working while the sender is backgrounded for as long as the host can retain its required native service; this is a physical-device acceptance gate, especially on iOS.

This is a candidate design, not a supported capability yet. Before implementing a socket, validate receiver reachability, byte-range behavior, codec/container support, and background lifetime on physical receivers. If a host cannot sustain the endpoint, define one common product behavior for that capability instead of silently exposing different Cast features by platform.

## Sender and receiver matrix to verify

| Sender | Native boundary | Initial verification |
| --- | --- | --- |
| Android | Cast SDK discovery, session callbacks, permissions, and foreground lifecycle | Physical Android sender and Cast audio/video receiver |
| iOS | Cast SDK discovery, session callbacks, local-network permission, and background lifecycle | Physical iPhone/iPad and Cast receiver |
| Desktop | No native desktop sender SDK is listed in Google's sender matrix; investigate a supported browser/Web Sender bridge or another documented route before promising native Desktop Cast | Windows, macOS, and Linux with physical receiver |

Google documents Android, iOS, and Web sender SDKs. Its Default Media Receiver can load a supplied media URL, while authentication or custom receiver logic calls for a Custom Web Receiver. The media-access proof will determine whether the Default Media Receiver is sufficient; do not register or ship a custom receiver until that choice is supported by evidence.

On 2026-09-23, a Pixel 10a running the debug probe initialized Cast SDK 22.3.1 and opened the native
route picker. It discovered `GoogleTV8565` and `Living Room TV`. Neither receiver was selected or
used for playback. The probe lives in the Android debug source set and uses the Default Media
Receiver application ID. This establishes phone-side discovery only; receiver reachability, media
delivery, and playback remain untested.

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
