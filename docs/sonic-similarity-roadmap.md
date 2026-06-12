# Sonic Similarity Roadmap

Status: Phase 3 complete; consolidating sonic similarity into Track Radio

## Why this exists

Naviamp is aiming for the same kind of music-discovery experience Plexamp gives Plex users, but backed by Navidrome and OpenSubsonic. Navidrome 0.62 added support for plugin-provided OpenSubsonic extensions, including sonic similarity. The AudioMuse-AI Navidrome plugin exposes the current `sonicSimilarity` extension methods.

The first product rule is that sonic features are server capabilities, not only user preferences. Naviamp should detect whether the connected server advertises sonic similarity, then expose sonic-powered features only when that capability is available. User settings should control whether Naviamp prefers sonic results when the server supports them.

## Source Notes

- Navidrome v0.62.0 added plugin extension support and advertised AudioMuse-AI as a source for sonic similarity.
- OpenSubsonic exposes `getOpenSubsonicExtensions`; a server that supports this feature should advertise `sonicSimilarity` version 1.
- The `sonicSimilarity` extension currently exposes:
  - `getSonicSimilarTracks`
  - `findSonicPath`
- AudioMuse-AI-NV-plugin v8+ implements the sonic similarity API for Navidrome.
- Plexamp's comparable surface includes sonically similar artists/tracks/albums, Track Radio, Album Radio, Sonic Adventure, Sonic Sage, Guest DJ, and mix builders.

## Design Principles

- Keep capability detection provider-owned.
- Keep feature policy shared between desktop and Android.
- Preserve fallbacks when sonic similarity is unavailable or returns no useful results.
- Prefer shared services over parallel Android/Desktop controller logic.
- Treat sonic data as richer than a plain track list when possible; keep similarity scores available for later ranking and UI labels.
- Avoid making AudioMuse-specific assumptions outside Navidrome/OpenSubsonic capability detection.

## Implementation Checklist

### Phase 1: Capability Detection and Settings

- [x] Add Navidrome provider support for `getOpenSubsonicExtensions`.
- [x] Parse advertised extensions and set `supportsSonicSimilarity` only when `sonicSimilarity` version 1+ is present.
- [x] Keep sonic support disabled if the extensions endpoint fails.
- [x] Add provider tests for advertised, absent, and failed sonic extension detection.
- [x] Hide or disable the sonic similarity setting when the connected provider does not support it.
- [x] Make Android and desktop settings use the same capability flag.
- [x] Add diagnostics visibility for sonic capability if it fits the existing stats model.

### Phase 2: Related Tracks 2.0

- [x] Keep current Related tab behavior but label sonic-backed results clearly.
- [x] Preserve fallback to local related tracks or provider track radio when sonic results are empty.
- [x] Introduce a shared result model that can carry similarity scores.
- [x] Add track-row actions for `Play next`, `Add to queue`, and `Start radio from this` where missing.
- [x] Add tests around fallback ordering and seed-track filtering.

### Phase 3: Play More Like This

- [x] Add a shared action request for "Play more like this" from track menus.
- [x] Build a shared service that turns a seed track into a queue using sonic matches when supported.
- [x] Add desktop and Android menu entries through shared UI action specs.
- [x] Support play now, play next, and add to queue variants.
- [x] Fall back to current track radio when sonic support is unavailable.

Outcome: superseded by Phase 3.1. Sonic similarity should behave as the preferred Track Radio engine, not as a separate parallel action surface.

### Phase 3.1: Track Radio Consolidation

- [x] Use sonic similarity as the preferred Track Radio engine when the setting is enabled and the provider supports it.
- [x] Fall back to provider Track Radio, then local same-album/same-artist fallback.
- [x] Remove separate "Play more like this" track-menu actions so Track Radio remains the single user-facing concept.
- [x] Preserve "Play track radio next" and "Add track radio to queue" as Track Radio actions backed by the same sonic-first generator.
- [x] Audit the Now Playing hamburger menu. Removed duplicate Show Lyrics, Show Visualizer, Favorite Track, and Start Track Radio entries from the current-track overflow menu.

### Phase 4: Sonic Path

- [x] Add provider support for `findSonicPath`.
- [x] Create a shared "Sonic path" request model with start track, destination track, and max count.
- [x] Show Sonic Path on Home underneath Artist Builder, Album Builder, and Genre Builder.
- [x] Gate Sonic Path so it only appears when sonic similarity is enabled and available from the server.
- [x] Add a simple UI flow with two track search fields and a count selector for the maximum number of tracks between the start and destination tracks.
- [x] Filter duplicate tracks and preserve start/destination semantics.
- [x] Keep Sonic Path output as a standard track list that feeds the existing queue handler; do not introduce a distinct queue or playlist type.
- [x] Add path actions for play and add to queue using the normal queue/playback handlers.
- [ ] Add playlist save if an existing shared playlist creation pattern emerges.
- [x] Add tests for request construction, response mapping, and empty-path fallback.

### Phase 5: Sonic Mix Builder

- [x] Add a shared sonic mix service that accepts multiple seed tracks.
- [x] Show Sonic Mix in the Home builder section beside Sonic Path.
- [x] Gate Sonic Mix so it only appears when sonic similarity is enabled and available from the server.
- [x] Blend results across seeds, dedupe tracks, and avoid over-representing one artist/album.
- [x] Add options for target length and bias toward favorites, unplayed tracks, or recent plays.
- [x] Reuse existing mix-builder UI patterns where possible.
- [x] Keep Sonic Mix output as a standard track list that feeds the existing queue handler.
- [x] Add play and add-to-queue actions using the normal queue/playback handlers.
- [ ] Add playlist save if an existing shared playlist creation pattern emerges.

### Phase 6: Home Discovery Rows

- [ ] Add home rows for "More like recent plays", "Sonic deep cuts", and "Similar to starred tracks".
- [ ] Load rows lazily after connection and library data are ready.
- [ ] Cache generated rows for the session to avoid repeated server calls.
- [ ] Fall back silently when capability is unavailable.

### Phase 7: Artist and Album Similarity

- [ ] Build artist similarity by sampling representative tracks and grouping sonic matches by artist.
- [ ] Build album similarity by sampling album tracks and grouping matches by album.
- [ ] Add scoring based on frequency, similarity, and local-library availability.
- [ ] Add artist/album detail rows once shared scoring is reliable.

### Phase 8: Sonic Autoplay

- [ ] Add a separate setting for sonic autoplay at queue end.
- [ ] Generate continuation tracks from the last few played items.
- [ ] Avoid repeating current queue/history.
- [ ] Keep this opt-in and independent from the Related tracks preference.

## Current First Slice

Start with Phase 1. This unlocks all later work because every sonic feature needs a trustworthy answer to "can the current server do this?"
