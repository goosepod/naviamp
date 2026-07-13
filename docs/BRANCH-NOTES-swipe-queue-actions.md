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
  - Remove from queue (queue context only)
- [x] Persist swipe settings on desktop and Android and include them in settings sync.
- [x] Tighten settings page rows and multi-line description spacing to match the main Settings screen.
- [x] Add pull-to-refresh to Home on desktop and Android.
- [x] Add a three-dot Home menu with a Refresh action using the same Home refresh path.
- [x] Compile the shared UI, desktop app, and Android debug app successfully.
- [x] Run the shared UI tests successfully.

## Not completed / future candidates

- [ ] Add more optional swipe actions:
  - Favorite or unfavorite the track.
  - Go to album.
  - Go to artist.
  - Share the track.
  - Open track information or lyrics.
- [ ] Decide whether users need per-screen overrides beyond the current Library, Queue, and Related groups.
- [ ] Add automated UI tests for swipe distance, cancellation, direction, action triggering, and destructive-action availability.
- [ ] Manually verify the final Home pull-to-refresh gesture and overflow Refresh action on both Windows and Android.
- [ ] Review accessibility details for swipe-only discovery and ensure every configured action remains available through a visible menu.

## Before merging

- [ ] Resolve or move any worthwhile future candidates to the permanent quality-of-life tracker.
- [ ] Complete final desktop and Android manual testing.
- [ ] Delete this file.
