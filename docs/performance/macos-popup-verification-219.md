# macOS popup verification, October 6, 2026

Issue [#219](https://github.com/goosepod/naviamp/issues/219), draft
[PR #229](https://github.com/goosepod/naviamp/pull/229).

## Result and owner acceptance

The visible packaged app exposed a remaining distinction between composition isolation and surface
isolation: collecting diagnostics into a model value before passing it to the native window still
caused two main-window frames per ten seconds. Common presentation now passes unread Compose
State; shared window content reads that state inside the independently mounted native surface.
The final measured candidate recorded zero main-window frames in every diagnostics sample. Its
own diagnostics surface recorded two frames per interval. The one-second sampling cadence and
playback/animation frequency were preserved.

The latest retained paired comparison is 2.761% process CPU with diagnostics and 1.604% closed:
+1.158 percentage points, or 0.158 points above the original +1-point allowance. On October 6,
the owner explicitly accepted this small overage as good enough and deferred native VoiceOver
acceptance. This is an owner-accepted checkpoint, not a claim that every sample meets the original
budget. Higher compilation/warm-up samples remain in the record; compilation is never subtracted
to manufacture a passing CPU result. Keyboard traversal and the final interaction checks remain
required before closure.

## Conditions and instrumentation

Apple M1, macOS 27.0.1, Metal, Java 21.0.12.1, AC power, two 2560x1440 displays at 75 Hz and
1x scale. Frame 1000x740, client 1000x708, same primary display throughout accepted CPU phases.
The user repositioned the frame to (415,252) to clear the automation pointer annotation. The final
interaction fixture uses the same dimensions at (800,400), also on that display. Playback is muted,
with a loopback Subsonic fixture containing two ten-minute tracks, one short identity and one long
identity. No production account, Cast receiver or living-room TV is used.

The production baseline is f7881c36577f716e0fa0d09e9de8df534b3889aa. The candidate adds the
unread-state contract in Core and shared UI, with only a State parameter in the existing Desktop
window adapter. `scripts/animation-probe/README-visible-app.md` describes the opt-in observer.

The observer records ten-second one-core process CPU, compilation time, and actual Skiko delegate
frames. Before/after physical captures are outside the CPU interval. The macOS guard uses native
onscreen placement, validates all client pixels against front-to-back window rectangles, rejects
foreign overlap, excludes actual AWT decoration insets and rejects blank captures. Early samples
failed because the computer-control service's pointer annotation overlapped the app; those samples
are rejected, not performance acceptance. Focus is not used as a proxy for physical visibility.
A post-Quit computer-use state query unexpectedly relaunched the test app and overwrote the last
`combined-3` output; that replacement process's sample is excluded. No user app was closed.

## Visible candidate samples

All rows below have successful before/after physical captures and attached observers. Raw retained
metrics, including rejected captures and earlier compilation outliers, are in
[the evidence directory](macos-popup-219-2026-10-06/after-metrics.txt).

| State | CPU, % of one core | Compilation, ms | Main frames / 10s | Diagnostics frames / 10s |
| --- | ---: | ---: | ---: | ---: |
| Static | 0.631 | 9 | 0 | — |
| Progress only | 2.348 | 85 | 0 | — |
| Paused marquee | 0.664 | 21 | 0 | — |
| Combined, first | 2.015 | 40 | 0 | — |
| Diagnostics, first | 4.241 | 203 | 0 | 2 |
| Diagnostics, second | 3.066 | 64 | 0 | 2 |
| Diagnostics, third | 3.403 | 87 | 0 | 2 |
| Diagnostics, fourth | 2.761 | 59 | 0 | 2 |
| Combined, following | 1.604 | 19 | 0 | — |

Physical pixel comparisons of shared identity/progress/artwork crops confirmed:

| State | Changed title pixels | Changed progress pixels | Changed artwork pixels |
| --- | ---: | ---: | ---: |
| Static | 0 | 0 | 0 |
| Progress only | 0 | 364 | 0 |
| Paused marquee | 7,528 | 0 | 0 |
| Combined | 7,384 | 390 | 0 |

Title crop (425,210,525,120), progress (94,657,812,26), artwork (42,93,358,357), relative to the
1000x708 physical client capture. Moving/nonblank content is confirmed; low CPU or an attached
observer alone is not the acceptance argument.

## Compositor evidence

A separate ten-second Metal System Trace of the candidate diagnostics state recorded two Naviamp
Fragment intervals totaling 0.092 ms and two Vertex intervals totaling 0.021 ms. Whole-desktop
WindowServer activity was 81.409 ms Fragment and 13.985 ms Vertex across about 1,500 intervals.
These channel times overlap and are not total GPU utilization or energy. WindowServer includes
other visible apps and cannot be attributed entirely to Naviamp. Raw traces remain local because
they contain unrelated environment metadata; the narrow process/channel summary is retained.

## Validation and remaining checks

495 shared UI tests, 443 shared presentation tests and five Desktop tests passed with zero
failures/errors/skips. Android shared compilation, both iOS Arm64 shared compilations, Core-first
architecture verification and staged Mac packaging passed. The strengthened common regression
checks changed diagnostics content without recomposing its presenter and cancellation on dismissal.

Native AX inspection exposes playback transport, Favorite and rating controls as buttons, with
labels; the progress slider exposes its value. The track menu opens with its labels and dismisses
on an outside click. An attempted automated Escape did not dismiss it, but reliable native keyboard
focus was not established, so keyboard acceptance is pending. Native VoiceOver was explicitly
deferred by the owner. Complete keyboard traversal and final native interaction/lifecycle checks
must be recorded before merging and closing #219.

Platform production diff: `apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/app/DesktopStatsForNerdsWindow.kt`
creates a Compose Desktop/AWT native window and invokes OS icon/title-bar effects; it mechanically
passes shared state to shared content. No Android or iOS production file changes.
