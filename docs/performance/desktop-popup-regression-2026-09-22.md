# Desktop popup regression investigation

Issue #127 / PR #131. Performance follow-up: #136.

The animation compositor enabled Compose's `WINDOW` layer mode. In that mode,
`DesktopComposeSceneLayer.recordDrawBounds` expands a native popup's bounds from recorded drawing
operations. `WindowComposeSceneLayer.onDrawBoundsChanged` then resizes and moves its JDialog. This
couples opening transitions, shadows, and newly drawn hover effects to OS window geometry.

The Desktop-only Compose upgrade and custom menu experiment did not pass native acceptance. The
current candidate restores the original dependencies and Material menus and uses Compose's normal
same-canvas popup mode. Shared popup lifetime tracking temporarily returns raster presentation to
the shared canvas underneath menus, dialogs, and tooltips. It tracks nested lifetimes separately.
Animation continues; native presentation resumes after the last popup is dismissed.

Only `DesktopRasterPresenter.kt` changes platform production code. Its boundary is Compose Desktop
layer configuration and the existing AWT/native compositor selection. Popup lifetime and stacking
decisions remain in common code. Android and iOS have no host changes.

## Acceptance

- Shared tests: nested cleanup, restoration of native presentation, pointer-based menu toggle, and
  stable item bounds across first hover on each menu item.
- Compile common UI for JVM, Android, and iOS.
- Native macOS: opening, first hover, scrolling, checkboxes, cancel, repeated anchor click, resize,
  and popups over player content. Repeat relevant smoke checks on Windows and Linux before release.
- Measure static, marquee, waveform, combined, popup-open combined, and restored combined states.
  A popup's shared drawing path may cost more than native animation; record this explicitly.
  Do not treat compilation or unit tests as proof of native presentation or performance.

The existing visible-window probe now accepts `NAVIAMP_PROBE_POPUPS=true` to include the popup-open
and restored phases:

```sh
NAVIAMP_PROBE_INTEGRATED=true NAVIAMP_PROBE_POPUPS=true NAVIAMP_PROBE_VERIFY=true \
  ./gradlew :core:ui:playerAnimationProbe
```

## Results so far

- All 418 JVM shared UI tests passed, including the two pointer/hover regressions and native-surface
  release/restoration test. Android and iOS shared UI compilation passed.
- Packaged macOS app launched successfully. In the real window, the Home and player action menus
  closed on a repeated click of their anchor. Library checkbox and label clicks toggled the same
  checkbox at its visible location with unchanged dialog geometry; changes were cancelled.
  Pointer/scroll interaction on separate player-menu items left the menu at the same location.
- Performance probe completed. Environment is Apple M1, AC power, Metal, 1000 × 740 window
  (1000 × 708 measured parent surface), 150 library rows. Window/scale/power settings are unchanged
  during the six phases. Display refresh rate is not exposed by the sandboxed display query.

The owner accepted the native macOS popup behavior and explicitly approved shipping the known
popup-open performance limitation in v2.7.1 on 2026-09-22, with optimization tracked in #136.
Windows/Linux automated native checks remain required; manual smoke checks on those platforms
have not been repeated for this candidate.

### Measured rendering cost (10-second samples)

| State | Process CPU | Parent frames |
| --- | ---: | ---: |
| Static | 0.28% | 0 |
| Marquee | 0.26% | 0 |
| Waveform | 0.26% | 0 |
| Combined | 0.54% | 0 |
| Popup + combined | 20.75% | 738 |
| Restored combined | 0.70% | 0 |

Pixel checks confirmed visible text, moving marquee and waveform, and unchanged sibling pixels.
Twelve popup transitions retained visible text; waveform pointer input passed. No native popup
windows opened or closed during the measured phases. WindowServer CPU was also sampled, but it is
shared with the rest of the live desktop and is not an isolated GPU-cost measurement.

**Performance acceptance failed while a popup is open.** The candidate fixes observed popup
positioning. The owner approved this explicit hotfix exception; the 20.75% sustained CPU cost
remains open in #136 and is not an accepted performance fix.
The shared fallback redraws the parent surface. Further work must preserve native animation
presentation underneath same-canvas popups, with correct clipping and scrims, or use a verified
stable native popup implementation. Do not address this by freezing animation.

Local raw logs: `/private/tmp/naviamp-popup-tests.log`, `/private/tmp/naviamp-popup-build.log`,
`/private/tmp/naviamp-popup-probe.log`, `/private/tmp/naviamp-popup-windowserver.log`.
