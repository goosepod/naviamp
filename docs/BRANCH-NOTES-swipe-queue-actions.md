# Temporary Branch Notes: Swipe Queue Actions

> [!WARNING]
> This file is only a working checklist for `codex/swipe-queue-actions`.
> Delete it before merging the branch into `main`.

## Completed

- [x] Use shared, platform-independent swipe handling on desktop and Android.
- [x] Swipe right on library-style track rows to play the track next.
  - Covers search results, album and playlist track lists, library lists, recently played tracks, and similar shared track rows.
- [x] Swipe right on Related and Sonic recommendation rows to play the track next.
- [x] Swipe right on queue rows to move or insert the track directly after the currently playing track.
- [x] Swipe left on queue rows to remove the track from the queue.
- [x] Do not expose the destructive remove action on search, album, playlist, library, Related, or Sonic rows.
- [x] Reveal an icon and colored background while swiping.
  - Positive actions use green styling.
  - Destructive removal uses red styling.
- [x] Put swipe configuration under **Settings > Experience > Swipe Actions**.
- [x] Separate settings by list context:
  - Library: search, albums, playlists, and library tracks.
  - Queue: Back To and Up Next.
  - Related: Related and Sonic recommendations.
- [x] Allow independent left- and right-swipe choices for every context.
- [x] Provide these configurable actions where the context supports them:
  - No action
  - Play next
  - Add to queue
  - Add to playlist
  - Download
  - Start radio
  - Favorite or unfavorite
  - Go to album
  - Go to artist
  - Remove from queue (queue context only)
- [x] Only expose Go to album and Go to artist gestures on tracks with the corresponding metadata.
  - Suppress redundant Go to album gestures inside that album and Go to artist gestures inside that artist.
  - Preserve these capabilities through desktop track-row adapters, including artist Popular Tracks.
- [x] Persist swipe settings on desktop and Android and include them in settings sync.
- [x] Tighten settings page rows and multi-line description spacing to match the main Settings screen.
- [x] Refine album details for narrow desktop windows.
  - Increase spacing between album track rows on desktop and Android.
  - Keep Play, Shuffle, Download, album radio, and Add to queue visible.
  - Move Add to playlist and Favorite into an overflow menu when space is limited, while showing all actions at wider widths.
- [x] Add pull-to-refresh to Home on desktop and Android.
- [x] Add a three-dot Home menu with a Refresh action using the same Home refresh path.
- [x] Compile the shared UI, desktop app, and Android debug app successfully.
- [x] Run the shared UI tests successfully.

## Not completed / future candidates

- [x] Keep the current Library, Queue, and Related groups without additional per-screen overrides.
- [x] Add automated UI tests for swipe distance, cancellation, direction, action triggering, and destructive-action availability.
- [x] Manually verify the final Home pull-to-refresh gesture and overflow Refresh action on Windows, macOS, and Android.
- [x] Review accessibility details for swipe-only discovery and ensure every configured action remains available through a visible menu.
  - Label track overflow buttons as More actions for assistive technology.
  - Keep every configurable swipe action available in the corresponding visible track menu, with capability-aware Favorite, album, and artist actions.
  - Explain in Swipe Actions settings that gestures are shortcuts and the actions remain available through track menus.

## Before merging

- [ ] Resolve or move any worthwhile future candidates to the permanent quality-of-life tracker.
- [x] Complete final Windows, macOS, and Android manual testing.
- [ ] Delete this file.
