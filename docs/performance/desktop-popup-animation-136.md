# Popup animation cost: issue #136

Issue: https://github.com/goosepod/naviamp/issues/136

## Candidate and acceptance budget

Core retains each native raster scene while the host reports separate owned popup windows.
Shared menus and modal surfaces occupy the fixed window viewport, keeping native geometry
independent of entrance transforms and hover drawing. Menus position a bounded shared surface
inside that viewport and dismiss outside clicks; modals draw their own scrim above the raster scene. Material menu items, dialog
content slots, common input, and semantics remain shared. Hosts without separate popup windows
continue to delegate to their existing Material/Compose presentation.

The sole platform production change is `core/ui/src/jvmMain/kotlin/app/naviamp/ui/DesktopRasterPresenter.kt`.
Its concrete boundary is Compose Desktop's native-window layer configuration and AWT/AppKit
owned-window stacking. It publishes these facts to Core; it contains no menu/dialog controller.
No Android/iOS production file or Linux native raster implementation changes.

Acceptance is deliberately pending. A passing build or an unverified surface is insufficient:

- On this macOS machine, stabilized synthetic static, individual, combined, menu-open, modal-open,
  and restored ten-second samples must use at most 1% of one process CPU core and zero parent
  frames. Text and progress must visibly move, with no unrelated pixel changes.
- In the real application, opening an overlay must add no continuous parent redraw stream and no
  more than one percentage point of sustained process CPU versus its equivalent closed state.
  Legitimate provider/playback/UI updates must be recorded rather than misclassified as animation.
- Record compositor cost under the same size, scale, refresh and power conditions. WindowServer
  is a whole-desktop observation, not an isolated measurement of Naviamp GPU work. Reject samples
  interrupted by unrelated window activity.
- Check nested menus/dialogs, first hover, scrolling, repeated anchor clicks, checkbox and label
  clicks, cancellation, scrims, player input, resize/clipping, idle/paused, minimize/hide and restore,
  keyboard and accessibility. Repeat native acceptance on Windows and Linux before closing #136.

## Local baseline: October 2, 2026

Apple M1, Metal, AC power, 1000 × 740 window with 1000 × 708 parent surface, 150 library rows.
Both active displays reported 2560 × 1440 logical/pixel resolution and 75 Hz through CoreGraphics.

| State | Process CPU | Parent frames / 10 seconds |
| --- | ---: | ---: |
| Static | 0.39% | 0 |
| Marquee | 0.48% | 0 |
| Waveform | 0.22% | 0 |
| Combined | 0.25% | 0 |
| Popup + combined | 21.72% | 698 |

The first four phases passed visible-motion and unchanged-sibling assertions. Another application
covered the probe before the popup's final screen capture, so the complete run failed its visibility
gate. The popup CPU/frame result is diagnostic reproduction, not a fully accepted baseline sample.
A separate candidate attempt was also obscured during warm-up and supplies no performance evidence.
Do not count either failed run as candidate acceptance.

The screen comparison ignores per-channel differences of at most two values: the initial static
captures showed one-value display-color dithering with zero parent draws. Movement, nonblank content,
and sibling-content assertions remain enabled.

## Visible macOS candidate: October 2, 2026

The original owned-menu run checked moving parent content but did not assert visible menu pixels.
A later physical-display check found the menu absent; its CPU/frame numbers below are diagnostic
only and are withdrawn as menu acceptance. The shared-modal run passed its scrim/motion gate under
the same machine, window dimensions, scale, refresh and AC conditions as the baseline. Every row below recorded
zero parent frames per ten seconds. Cached text and waveform motion, unchanged sibling pixels,
stable popup geometry, twelve reopen samples, and waveform pointer input passed. These checks
did not prove that the original menu itself was painted.

| State | Menu run CPU | Modal run CPU |
| --- | ---: | ---: |
| Static | 0.47% | 0.30% |
| Marquee | 0.58% | 0.33% |
| Waveform | 0.25% | 0.30% |
| Combined | 0.34% | 0.35% |
| Popup + combined | 0.36% | 0.38% |
| Restored combined | 0.28% | 0.43% |

The dark scrim visibility marker allows six channel values of display color conversion; only
rapid reopen captures accept the marker's known brown tint at intermediate entrance opacity.
Steady-state movement and sibling comparisons retain the two-value tolerance. A partially entered
but correctly moving modal must not be mistaken for an obscured surface.

Whole-desktop WindowServer samples during the menu run were polluted by other desktop activity
and provide no accepted isolated compositor-cost result. Real-application, idle/paused,
hide/minimize/restore, resize, keyboard and full accessibility acceptance remain pending.
Windows and Linux acceptance also remain pending; these synthetic results do not close #136.

Logs: `/private/tmp/naviamp-136-menu-candidate.log` and
`/private/tmp/naviamp-136-dialog-accepted.log`.

## Stronger menu paint gate and fixed viewport

Physical real-app captures exposed blank menus despite accessible menu nodes and moving parent
content. The synthetic probe originally missed this because it asserted parent visibility only.
The strengthened gate requires more than 5,000 menu-background pixels and 50 bright label-edge
pixels on the physical display. Both a popup-frame-clock change and layout padding alone failed
that gate; neither is accepted as a fix.

A fixed viewport with shared bounded placement passed the stronger popup-only run:
menu + combined 0.88% CPU, restored combined 0.30% CPU, zero parent frames in each ten-second
sample. It recorded 64,307 menu-background pixels and 1,272 label pixels, visibly moving text and
waveform, unchanged siblings, stable geometry, twelve reopen checks and pointer input. The menu
capture was visually inspected. The normal opacity/scale entrance remains enabled. Full-matrix
and real-app acceptance of this final candidate are recorded separately below when complete.

Local log: `/private/tmp/naviamp-136-menu-viewport.log`.

## Reproduction

Keep the probe visibly unobscured. Do not run rendered UI tests or change desktop spaces during
measurements. Logs and PNGs are local diagnostics and are not committed.

```sh
# Previous same-canvas policy, with the current common implementation's delegated fallback.
JAVA_TOOL_OPTIONS=-Dcompose.layers.type=SAME_CANVAS \
  NAVIAMP_PROBE_INTEGRATED=true NAVIAMP_PROBE_POPUPS=true NAVIAMP_PROBE_VERIFY=true \
  ./gradlew :core:ui:playerAnimationProbe

# Owned-window candidate; repeat with NAVIAMP_PROBE_DIALOGS=true instead of MENUS.
NAVIAMP_PROBE_INTEGRATED=true NAVIAMP_PROBE_POPUPS=true NAVIAMP_PROBE_VERIFY=true \
  NAVIAMP_PROBE_MENUS=true ./gradlew :core:ui:playerAnimationProbe
```

The menu/dialog modes verify the same cached text and waveform under actual shared overlays,
stable native geometry, unchanged siblings and zero parent frames after entrance transitions.
The default popup mode also checks tooltip-like surfaces.

### Real application sampler

`scripts/animation-probe/ApplicationRenderProbe.java` is an opt-in test agent. It observes existing
Skiko render delegates and process CPU; it creates no UI and does not alter playback. Compile it
with JDK 21, create a JAR with `Premain-Class: ApplicationRenderProbe`, and launch the staged
application JARs with that JDK and `-javaagent:/path/to/probe.jar=/path/to/measurements`.
Use `-Dskiko.renderApi=METAL -Dnaviamp.visualizer.macosMetal=true` on macOS and
`-Dnaviamp.developmentDataProfile=true` for the separate development data profile. This test launch
uses the staged application and native resources with the full JDK; packaged-launcher smoke checks
must be recorded separately.

Write a distinct phase name to the sampler directory's `phase` file after each state stabilizes.
Ten seconds later, the agent writes `<phase>.txt` with process CPU and parent/owned-surface frame
counts and before/after physical-display PNGs. It recognizes SkiaLayer subclasses and records
minimized state. Physical captures require an active probe window; an inactive app may be on
a different desktop space. `physical_captures=false` is not visibility acceptance. Inspect the
overlay itself as well as moving player content: semantics alone
do not prove that a native surface painted. Keep native windows visibly unobscured and inspect actual motion separately. Observe
compositor cost alongside each sample; the agent does not measure GPU energy.

## Verification checkpoint

- Initial common behavior tests: 15 passed, including nested owned popups, native-scene reuse,
  hide cleanup, bounded/RTL placement, hover/toggle and modal checkbox input.
- Full shared UI suite: 458 tests passed, including the final shared dialog entrance refinement.
- The final pointer-click and dialog accessibility regression passed in a focused rerun.
- Common Android, JVM, iOS device and iOS simulator compilation passed before native host wiring.
- Desktop tests, architecture check and staged macOS packaging passed.
- Final shared/native rebuild, all common-target compilation, architecture check and staged
  packaging passed. The modal synthetic measurement passed its original checks; menu acceptance was withdrawn
  after the stronger physical menu-paint assertion failed.
  Real application acceptance, compositor samples and Windows/Linux native acceptance remain pending.

Raw local logs: `/private/tmp/naviamp-136-baseline2.log`, `/private/tmp/naviamp-136-candidate.log`,
`/private/tmp/naviamp-136-shared-tests2.log`, `/private/tmp/naviamp-136-package.log`,
`/private/tmp/naviamp-136-final-build.log`.

### Metal activity

Use `xcrun xctrace record --template 'Metal System Trace' --attach PID --time-limit 10s
--output recording.trace` separately from CPU samples. Export `metal-gpu-intervals` with xctrace,
then run `python3 scripts/animation-probe/summarize-metal.py intervals.xml --process Naviamp
--process WindowServer`. It reports top-level active intervals by process and GPU channel.
Channels overlap, so their durations are not additive GPU utilization or energy. WindowServer
includes all desktop composition. Raw traces contain environment metadata: retain them locally;
publish only the narrow summary.

## Final local checkpoint

The final common implementation preserves Compose's actual anchor bounds inside the fixed
viewport and handles arrow keys, Escape/Back and outside clicks in shared code. All 460 shared
UI tests passed, including painted labels and keyboard focus/cancellation. Android, iOS device
and simulator compilation, architecture verification and staged macOS packaging passed.

The anchor-preserving popup-only run passed physical menu paint (64,307 background pixels,
1,272 label pixels), moving text/waveform, unchanged siblings, stable native geometry, reopen
checks and pointer input. It measured 0.95% menu CPU with zero parent frames. Its restored sample
used 3.11% CPU with zero parent frames and does not meet the documented 1% sample budget.
The earlier fixed-viewport run measured 0.88% menu and 0.30% restored CPU with zero parent frames.
These differing samples remain unresolved; no full final synthetic budget acceptance is claimed.

A staged fixed-viewport app visibly painted menu labels through the native-window capture, and a pointer
click outside dismissed it. The final unobscured full matrix and real-app CPU/GPU/lifecycle
acceptance could not be completed: Windows App covered the physical display, and the computer-use
service timed out during a desktop-activation attempt. The strict visibility gate rejected those
runs. The sampler now recognizes SkiaLayer subclasses, records minimized state and saves physical
captures only while a probe window is active. The synthetic probe saves PNGs only after validating
its known visibility marker.

Earlier real-app diagnostic samples recorded idle 0.38% CPU / zero parent frames, combined playback
4.16% / ten frames, and menu 4.34% / ten frames per ten seconds. The original menu was not physically
painted, so its result is rejected as overlay acceptance. A separately recorded ten-second Metal
trace attributed 0.132 ms Vertex and 0.594 ms Fragment activity to Naviamp; WindowServer recorded
25.828 ms Vertex, 271.769 ms Fragment and 3.359 ms Compute. Channels can overlap, WindowServer
includes the whole desktop, and the menu visibility failed. These are diagnostics, not accepted
final-candidate GPU/compositor evidence. Raw trace and narrow XML/JSON summaries remain local.

Current conditions: Apple M1; two displays at 2560 × 1440, 75 Hz; AC power. Synthetic window
1000 × 740 / parent 1000 × 708; real-app window 950 × 640 / parent 950 × 608.

Logs: `/private/tmp/naviamp-136-menu-viewport.log`, `/private/tmp/naviamp-136-menu-anchor.log`,
`/private/tmp/naviamp-136-anchor-final-build.log`,
`/private/tmp/naviamp-136-menu-final-cycles.log`,
`/private/tmp/naviamp-136-menu-final-cycles2.log`.

Re-run the full menu matrix with `NAVIAMP_PROBE_CYCLES=2` once the window stays unobscured.
`NAVIAMP_PROBE_POPUP_ONLY=true` shortens diagnostic iteration but does not replace the full matrix.
Repeat modal checks, real-app static/paused/playing overlays, minimize/hide/restore, resizing,
keyboard and accessibility, and matching GPU observations. Windows and Linux acceptance remain
required. Keep #136 and its PR open until these budgets and interactions are verified.
