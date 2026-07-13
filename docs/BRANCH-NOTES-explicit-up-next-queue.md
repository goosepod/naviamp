# Explicit Up Next Queue Branch Notes

> This file is only a working checklist for `codex/explicit-up-next-queue`.
> Delete it before merging the branch.

## Goal

Treat tracks added with **Play next** as a visible priority block that preserves insertion order, plays before the ordinary context queue, and remains stable while the ordinary queue is shuffled.

## Intended behavior

- [x] Repeated Play next actions preserve the order in which tracks were added.
- [x] Add to queue continues to append to the ordinary queue.
- [x] Priority tracks remain sequential while the ordinary upcoming queue is shuffled.
- [x] Starting a new playback context clears the priority block.
- [x] Advancing into a priority track consumes it from the block.
- [x] Selecting another upcoming track moves the remaining priority block after the new current track.
- [x] Choosing Play next for an item already in the priority block promotes it to the front of that block.
- [x] Removing a priority track updates the block; removing the last one removes the grouping.
- [x] Save and restore the priority boundary with the playback session.
- [x] Show a clear Play Next grouping in the Up Next list on desktop and Android.
  - Use theme primary text and a high-contrast divider so section labels remain legible across backgrounds.
- [x] Keep Remove from queue available through configured queue swipes and the visible row menu.

## Validation

- [x] Shared queue/domain tests pass.
- [x] Shared UI tests pass.
- [x] Desktop tests and compilation pass.
- [x] Android debug compilation passes.
- [x] Build and launch the macOS app for manual testing.

## Before merging

- [ ] Record final cross-platform manual-test results.
- [ ] Delete this file.
