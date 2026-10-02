# Popup animation cost: issue #136

Issue: https://github.com/goosepod/naviamp/issues/136

## Candidate and acceptance budget

Core retains each native raster scene while the host reports separate owned popup windows.
Shared menus reserve their complete shadow bounds outside the entrance transform, so drawing a
hover state cannot change the native window geometry. Shared modal surfaces occupy the fixed
window viewport and draw their own scrim above the raster scene. Material menu items, dialog
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
counts. Keep native windows visibly unobscured and inspect actual motion separately. Observe
compositor cost alongside each sample; the agent does not measure GPU energy.

## Verification checkpoint

- Initial common behavior tests: 15 passed, including nested owned popups, native-scene reuse,
  hide cleanup, bounded/RTL placement, hover/toggle and modal checkbox input.
- Full shared UI suite: 458 tests passed, including the final shared dialog entrance refinement.
- The final pointer-click and dialog accessibility regression passed in a focused rerun.
- Common Android, JVM, iOS device and iOS simulator compilation passed before native host wiring.
- Desktop tests, architecture check and staged macOS packaging passed.
- Final shared/native rebuild, all common-target compilation, architecture check and staged
  packaging passed. Unobscured candidate measurements, real application acceptance, compositor
  samples and Windows/Linux native acceptance remain pending.

Raw local logs: `/private/tmp/naviamp-136-baseline2.log`, `/private/tmp/naviamp-136-candidate.log`,
`/private/tmp/naviamp-136-shared-tests2.log`, `/private/tmp/naviamp-136-package.log`,
`/private/tmp/naviamp-136-final-build.log`.
