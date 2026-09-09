# Android TV UI workflow and artwork contrast acceptance

## Shared workflow coverage

`NaviampTelevisionWorkflowTest` exercises the actual shared Television compositions:

- Search: IME submission exactly once, asynchronous results, D-pad entry/activation, Back to the
  query, composition removal/re-entry preserving query/results without resubmission, and empty results.
- Internet Radio: empty/loading/failed-refresh states, disabled refresh while pending, create-dialog
  required fields and trimming, edit identity preservation, cancellation without saving, and
  cancellation/confirmation of deletion through the station action menu.
- Lyrics: current-line selection, playback and manual-offset changes, loading/unavailable states,
  automatic scroll to a distant line, track-change scroll reset, plain-text fallback, and remote
  show/hide while retaining the Lyrics button's focus.

These tests inject shared actions and screen state; they do not substitute fake implementations for
screen composition. OS keyboard presentation and physical remote/accessibility behavior remain
hardware acceptance. Active lyric lines now expose their selected state through shared semantics.

## Contrast fixes and measurements

The TV shell previously placed text directly over the chosen background; white Single Color and
bright artwork could defeat the dark palette's contrast. It now applies the same 88%-opaque dark
reading surface already used by shared wide content. Artwork hues remain visible beneath it.
Inactive TV lyrics use opaque muted text instead of reducing secondary text to 58% opacity.
The common waveform played-color mix moves from 48% to 52% primary text so even a black artwork
accent remains distinguishable on the reading surface. The waveform adjustment applies to every
host that uses the shared scrubber.

Tests use 4.5:1 for text and 3:1 for the played waveform, following the reference ratios in
[W3C text contrast guidance](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html) and
[non-text contrast guidance](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html).
They compare nominal foreground colors after alpha compositing, not antialiased glyph-edge pixels.

| Check | Minimum measured ratio | Required ratio |
| --- | ---: | ---: |
| Primary, secondary and muted TV text over 4,913 sRGB background samples | 4.56405:1 | 4.5:1 |
| Played waveform across black/white/saturated/artwork accent samples | 3.34431:1 | 3:1 |

Eight rendered captures cover all-white, saturated yellow, black/white checkerboard and multicolor
gradient backdrops at 1280×720 and 3840×2160 (3× density). These synthetic extremes exercise the
luminance and spatial contrast that artwork can introduce without relying on particular album
covers or external image requests. The production reading-surface composable is used after the
backdrop, and rendered background pixels are checked as well as the color calculations. The
captures show active/inactive lyrics, playback times, played progress, and focused/selected controls.

Measurements cover the awake dark Television palette. They are not a claim of complete WCAG
conformance, physical display calibration, every disabled/decorative graphic, or reading contrast
during deliberate idle dimming. Physical accessibility acceptance remains open.

## Reproduction and artifacts

```sh
./gradlew :core:ui:jvmTest \
  --tests '*NaviampTelevisionWorkflowTest' \
  --tests '*NaviampTelevisionArtworkContrastTest'
```

Captures and exact ratios are generated in `core/ui/build/reports/television-artwork-contrast/`.
The image names are `<white|yellow|checker|gradient>-<1280x720|3840x2160>.png`, with numeric results
in `ratios.txt`. All eight captures have been visually reviewed; automated pixel checks use the
original capture resolution.

Validation on September 9, 2026 passed: 343 shared UI tests (including six new workflow tests and
nine contrast tests), 356 presentation tests, Android debug assembly, Desktop compilation, iOS
simulator ARM64 compilation, and `verifyCoreFirstArchitecture`. No tests failed or were skipped. All production changes live in `core:ui/commonMain`;
no Android/Desktop/iOS production adapters, settings schemas, or user-facing copy changed.
