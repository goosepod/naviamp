# Quality of Life Improvements

This document tracks small interaction and layout improvements that should make Naviamp feel more natural during daily use.

## Pull to Refresh

Add pull-to-refresh gestures on these top-level pages:

- Playlists
- Internet Radio
- Library

### Goals

- Let users manually refresh server-backed content with a familiar touch gesture.
- Keep existing explicit refresh actions where they already exist.
- Use the same refresh operation as the current manual or automatic path for each page, so pull-to-refresh does not create a separate code path.

### Notes

- Playlists should refresh the playlist list and preserve the selected playlist when possible.
- Internet Radio should refresh the station list and keep the current route/context stable.
- Library should refresh the local library snapshot or trigger the existing library refresh path, depending on the current platform flow.
- The gesture should be platform-appropriate. Android should use native-feeling pull-to-refresh behavior. Desktop can wait unless there is an existing scroll/pointer pattern that makes the gesture feel intentional.

### Acceptance Criteria

- Pulling down at the top of each page starts a refresh.
- A visible refresh indicator appears while refresh is running.
- Refresh errors surface through the existing status/error UI for that page.
- Existing scroll position is preserved when the refreshed content still supports it.

## Synced Lyrics Current-Line Position

Adjust the synced lyrics panel so the current lyric line is visually centered in the seven-line display.

### Current Behavior

After the song gets past the first few lines, the active lyric appears as the fifth visible line. With seven visible lines, this makes the active line feel slightly too low.

### Desired Behavior

Move the active lyric up by one line so it appears as the fourth visible line when there is enough surrounding lyric context.

### Notes

- Keep the early-song behavior graceful when there are not enough previous lines to center the active line.
- Keep the end-of-song behavior graceful when there are not enough following lines.
- Preserve the current lyric highlighting and offset controls.
- Apply the behavior consistently across desktop and Android if they share the lyrics panel implementation.

### Acceptance Criteria

- With seven visible synced lyric lines and enough surrounding context, the active line is fourth from the top.
- The first few lines do not leave unnecessary blank space above the lyrics.
- The final few lines do not jump or leave unnecessary blank space below the lyrics.
- Manual lyrics offset adjustment still updates the highlighted line immediately.
