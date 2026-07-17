# Naviamp Follow-Up Ideas

This document tracks useful ideas that come up during the v2 migration but are not part of the active cross-platform checklist. Keep these scoped as investigation notes until they are promoted into the main plan, an issue, or a release branch.

## Status Key

- `Idea`: Captured for later review.
- `Investigating`: Actively researching feasibility and scope.
- `Planned`: Accepted and moved into a concrete implementation plan.
- `Implemented`: Shipped and verified.
- `Rejected`: Deliberately declined, with rationale.

## Ideas

### BlurHash versus ThumbHash Artwork Placeholders and Backgrounds

- **Status:** Idea
- **Sources:** [woltapp/blurhash](https://github.com/woltapp/blurhash), [evanw/thumbhash](https://github.com/evanw/thumbhash)
- **Concept:** Compare BlurHash and ThumbHash for artwork placeholders and as alternate album-art-derived app background sources, then select one algorithm or deliberately reject both.
- **Why it may fit:** Either hash can provide a compact, stable visual approximation of album art before the full image loads and may be cheaper than decoding and blurring full album art for every background. ThumbHash specifically claims better detail and color accuracy at a similar size, plus encoded aspect ratio and alpha support, while BlurHash offers configurable components and a broader established implementation ecosystem. Naviamp needs its own measurements and visual comparison instead of choosing from those claims alone.
- **Questions to answer:**
  - Does Navidrome expose either artwork hash through the Subsonic/OpenSubsonic API, or are its placeholders only internal?
  - If the server does not expose a usable hash, should Naviamp generate and cache one locally after fetching cover art?
  - Which algorithm produces better artwork placeholders and full-app backgrounds across dark, light, colorful, monochrome, square, non-square, and transparent artwork?
  - How do encoded size, encode/decode time, allocation, memory use, cache cost, aspect-ratio handling, and transition smoothness compare on Android, Desktop, and iOS?
  - Is either hash-derived background visually better than the current album blur option, or should hashes be used only as loading and transition placeholders?
  - Can the same decoded colors feed the existing highlight-color and visualizer-color pipeline?
  - Can one shared Kotlin Multiplatform implementation be used for encoding and decoding on all three platforms, or would either option require native wrappers or separate host code?
- **Implementation notes to investigate later:** Verify API availability and both licenses; evaluate maintained Kotlin Multiplatform implementations and the feasibility of a small shared implementation; build an identical artwork corpus and benchmark harness for both algorithms; prototype with existing artwork-cache inputs; and compare startup, track-change, same-album transitions, placeholder-to-art transitions, and background rendering against the current Aurora and Album Blur options. Record the results before selecting an algorithm.

## Promotion Checklist

Before moving an idea into the active v2 plan or a release branch:

- Confirm the provider or local-data source is available.
- Confirm the feature can behave consistently across Android, Desktop, and iOS or document capability-gated differences.
- Identify the shared owner module and the host-specific work, if any.
- Add focused tests or prototype evidence before committing to implementation.
- Record the decision in the main plan or a dedicated issue.
