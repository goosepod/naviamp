# Naviamp Connect Roadmap

Status: Draft

## Goal

Add a small renderer app for TV-style devices so Naviamp on a phone can discover it and remotely control playback through it.

The intended first use case is:

1. Start the TV app on an Android TV / Google TV / Fire TV device.
2. Start Naviamp on the phone.
3. The phone discovers available TV renderers on the local network.
4. The user selects the TV as the active output target.
5. The phone browses and controls playback, while the TV app streams and plays the audio directly.

This is closer to Spotify Connect or Plexamp casting than to screen mirroring.

## Product shape

Two app roles:

- `controller`: the main Naviamp app on phone/tablet/desktop.
- `renderer`: a small TV app that accepts playback commands and reports state.

The renderer should be intentionally thin:

- Now playing artwork, title, artist, album
- Queue preview
- Play/pause/next/previous
- Volume if the platform allows it
- Output and connection status

The renderer should not be a full-featured browsing app for the first version.

## First platform target

MVP target:

- Android TV / Google TV
- Fire TV

Do not start with Roku.

Reasons:

- Android TV and Fire TV let us reuse more of the current Kotlin, playback, and shared app code.
- The build and debug loop is better.
- Roku should be treated as a later platform once the protocol and renderer UX are proven.

## Architecture direction

The TV app should stream directly from the media server.

That means:

- the phone is the controller
- the TV is the playback renderer
- the TV does not receive raw audio from the phone

Benefits:

- less bandwidth between phone and TV
- playback continues if the phone screen sleeps
- easier state recovery
- cleaner fit with Navidrome/Subsonic provider access

## Major pieces

### 1. Renderer discovery

Primary plan:

- local-network discovery with mDNS / Bonjour

Fallback plan:

- manual pairing code

Discovery should expose:

- renderer id
- device name
- app version
- playback capabilities
- current availability

### 2. Pairing and trust

The controller should not send commands to arbitrary devices without an explicit user step.

MVP pairing options:

- one-tap local approval on the TV
- short pairing code shown on TV and entered/confirmed on phone

Questions to settle later:

- whether renderers are trusted per server, per user, or per device
- whether trust is local-only or synced

### 3. Remote playback protocol

Shared protocol models should live in core.

Minimum commands:

- connect
- disconnect
- replace queue
- add to queue
- play next
- play track now
- pause
- resume
- seek
- previous
- next
- set volume
- request state snapshot

Minimum state updates back from renderer:

- connected / unavailable
- current track
- artwork url
- playback state
- progress
- volume
- queue summary
- error state

### 4. Output target selection in Naviamp

The main app should gain an output selector:

- `This device`
- discovered remote renderers

Once a remote renderer is selected:

- play actions target the renderer
- queue mutations target the renderer
- now playing reflects renderer state

### 5. Renderer playback runtime

The TV app needs:

- provider connection
- local playback engine
- queue state
- remote command handler
- state reporter

The renderer should reuse shared playback/domain logic where possible, but it should not reuse the full phone app shell.

## Authentication options

This is the biggest unresolved design point.

### Option A: TV stores its own Navidrome login

Pros:

- simple runtime model
- TV can reconnect and recover independently

Cons:

- user has to sign into the TV app
- credentials live on another device

### Option B: phone delegates a session/token to the TV

Pros:

- better controller-driven flow
- less manual setup on TV

Cons:

- requires a token/session model we control carefully
- harder reconnect and expiry behavior

Current recommendation:

- MVP with TV-local login
- later investigate delegated sessions if the UX cost is too high

## MVP scope

Keep the first milestone narrow.

### In scope

- Android TV / Fire TV renderer app
- local discovery
- explicit pairing
- remote output picker in Naviamp
- remote play / pause / next / previous
- replace queue
- add to queue
- play next
- now playing state sync
- basic queue display on TV

### Out of scope

- Roku
- multi-room sync
- transferring live PCM audio from phone to TV
- full library browsing on TV
- offline TV downloads
- cross-network discovery outside the home LAN

## Failure cases to design for

- phone disappears from network
- TV sleeps and wakes
- renderer app restarts
- server token expires
- controller and renderer are connected to different servers
- multiple phones try to control the same renderer
- queue/state drift after reconnect

## Suggested implementation order

1. Shared core protocol models for renderer discovery, commands, and state snapshots.
2. Android TV renderer app shell with a minimal now-playing screen.
3. Local discovery service.
4. Pairing flow.
5. Remote playback controller in the phone app.
6. Output-target selector in shared UI.
7. Queue/state sync and reconnect handling.

## Notes

- This should be treated as a long-term feature track, not a side task during the current architecture cleanup.
- The cleanup work still helps here, because a remote renderer feature depends on shared playback, queue, and now-playing behavior instead of platform-specific controller code.
