# Naviamp Follow-Up Ideas

This document tracks useful ideas that come up during the v2 migration but are not part of the active cross-platform checklist. Keep these scoped as investigation notes until they are promoted into the main plan, an issue, or a release branch.

## Status Key

- `Idea`: Captured for later review.
- `Investigating`: Actively researching feasibility and scope.
- `Planned`: Accepted and moved into a concrete implementation plan.
- `Implemented`: Shipped and verified.
- `Rejected`: Deliberately declined, with rationale.

## Ideas

### BlurHash Artwork Placeholders and Backgrounds

- **Status:** Idea
- **Source:** [woltapp/blurhash](https://github.com/woltapp/blurhash)
- **Concept:** Investigate BlurHash support for artwork placeholders and as an alternate album-art-derived app background source.
- **Why it may fit:** A BlurHash can provide a compact, stable visual approximation of album art before the full image loads. It may also be useful as a cheaper background source than decoding and blurring full album art every time.
- **Questions to answer:**
  - Does Navidrome expose artwork BlurHashes through the Subsonic/OpenSubsonic API, or are they only internal?
  - If the server does not expose BlurHashes, should Naviamp generate and cache them locally after fetching cover art?
  - Is a BlurHash-derived background visually better than the current album blur option, or should it be used only as a loading/transition placeholder?
  - Can the same decoded colors feed the existing highlight-color and visualizer-color pipeline?
  - What quality, performance, and memory tradeoffs exist on Android, Desktop, and iOS?
- **Implementation notes to investigate later:** Check Kotlin Multiplatform compatibility for available BlurHash libraries, including `woltapp/blurhash`; verify license compatibility; prototype with existing artwork cache inputs; compare startup, track-change, and same-album transition behavior against the current gradient and album blur backgrounds.

## Promotion Checklist

Before moving an idea into the active v2 plan or a release branch:

- Confirm the provider or local-data source is available.
- Confirm the feature can behave consistently across Android, Desktop, and iOS or document capability-gated differences.
- Identify the shared owner module and the host-specific work, if any.
- Add focused tests or prototype evidence before committing to implementation.
- Record the decision in the main plan or a dedicated issue.
