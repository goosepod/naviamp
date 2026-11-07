# Android raster visibility diagnostics

Issue #330 remains an unresolved intermittent Android API 35 initial-visibility failure.
Physical Android 14 passes and a passing isolated replay of the same failed hosted source do
not establish its cause. Preserve the original failure and every subsequent control separately.

## Failure evidence

`AndroidRasterPlacementTest` retains its existing readiness loops, pixel thresholds, moving
captures, placement/clipping assertions and hide/restore checks. After a failure, before closing
the fixture activity, it records a PNG and text snapshot under the standalone test application's
external files directory, `raster-diagnostics`. The snapshot contains the original exception,
expected bounds, window focus, decor attachment/visibility/size and fixture state. The original
exception is rethrown even if diagnostic capture fails. Capture errors are logged separately.

The hosted failure collector pulls this exact test-only directory, captures window and surface
lists, and retains the existing bounded logcat/environment/connectivity evidence. It still
selects only an online emulator; absent/offline devices receive no shell or pull commands.
Every subprocess has the existing per-command deadline. Collection after test teardown is
supplementary: the saved in-test PNG/state are the evidence from before the activity closes.

## Bounded hosted control

The `verify:android-raster-repeat` PR label enables ten independent API 35 invocations of the
two-case raster class after the ordinary suite passes. Each invocation has unchanged product
source and assertions. Initial reports, every replay's reports and output are retained under
`build/android-raster-replays`. The first failing invocation ends the step with its failing status;
later passes cannot replace it. This is a bounded diagnostic experiment, not retry-until-green.
The label does not enable replays on API 30 or change ordinary PR execution.

## Local verification, November 7

- Opt-in diagnostic APK compiles on Windows.
- Both unchanged pixel tests pass on the physical onn Android 14/API 34 TV in 40.846s.
- For this positive artifact-path control only, runner argument `naviamp.raster.capture=true`
  also captures after a successful test. Four files are pulled: two PNGs and two state snapshots.
  Both snapshots report focused, attached, shown 1920x1080 windows; the placement PNG visibly
  contains the expected clipped red/blue raster. The option is not enabled by hosted replays.
- The bounded collector test passes under Ubuntu/WSL, covering absent, offline, online,
  hung-discovery and hung-command cases (one test with five subcases, 9.518s).
- The disposable `app.naviamp.ui.test` package is removed after the control. The owner profile is
  untouched. Raw diagnostic captures remain private in the worktree's ignored `build/330`.

Hosted API 35 execution is required before accepting the diagnostic changes. Successful execution
alone does not close #330 or justify changing the product's initial-pixel behavior.
