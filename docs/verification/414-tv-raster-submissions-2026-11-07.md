# TV raster submission experiment — November 7, 2026

Tracking: #414 / PR #536. Equivalent integer geometry is resubmitted on every running
Android raster tick in accepted main `548e1f76`. The candidate moves sampled pixel geometry
and equivalence detection into common code and omits redundant native geometry operations and
empty transaction applications. First frames, changed buffers, visibility, ordering, clipping,
motion clocks, animation sampling cadence and playback update frequency are preserved.

This report records a measured partial improvement; scrolling and combined animation retain
material app/compositor cost.

## Owner acceptance scope — November 8, 2026

The owner accepts this increment for merge when its performance is unchanged or better than
the previous implementation. Reaching the eventual absolute CPU/GPU budget is no longer a
merge condition for PR #536. Complete this increment after non-regression acceptance and
required verification, then keep #414 open for a separate future optimization pass. This
decision supersedes the earlier draft/hold status recorded below; it does not claim that
scrolling or combined animation has reached its final performance target.

## Common ownership and native boundary

`NaviampRasterFrame` owns the existing source/destination rounding, clipping and visibility
calculation, and tracks whether the resulting pixel geometry changed. Five common regressions
cover initial/native-layer lifetimes, moving clips, holds/resumed motion, hidden restoration,
and complementary progress edges/endpoints across 15,001 sampled ticks.

Before Android wiring, the common-only commit `f239fb8c1` passes JVM tests (5/5), Android/common
metadata compilation, and all nine checks in Verify run `42423138241`. The iOS report independently
confirms the five new common tests, zero failures/ignored, plus 17 native playback cases with
zero failures/ignored, production app and Keychain checks. Verified tree:
`9e8693a0bb9f77af883a6bfa737703a3c3824dc8`.

The sole changed platform production file is `AndroidRasterPresenter.kt`: it translates the
common frame into Android Rect/SurfaceControl geometry and submits native buffer, visibility,
ordering and transaction effects. Each native layer owns its own frame cache; replacing a native
layer starts a fresh cache. Buffer publication remains independent of geometry equivalence.
The adapter loses portable clipping/motion arithmetic. Final wired-source CI is recorded on the PR.

## Conditions and method

Physical onn 4K Pro, Android 14/API 34, 32-bit app, physical 3840x2160 with its existing
1920x1080 override, density 320, 59.94 Hz, mains power. Display, volume, accessibility, power
policy and TV screen-protection policy are unchanged. Normal input resets the protection timer;
no dim/shift transition occurs in the accepted sample pairs.
Thermal status is 0 in the initial and final device snapshots.

Only the disposable `app.naviamp.android.tvperf20261107` profile connects to a loopback fixture
through the TV's explicitly scoped port 18084 mapping. Two silent 600-second WAV tracks exercise
real decoding/output with short and long metadata. Default cache settings are retained. The
TV layout wraps/ellipsizes metadata; it is not a marquee test. The component fixture separately
uses the real shared scrolling text and waveform components.

The ordinary non-debuggable R8-optimized app is built WITHOUT the animation-probe flag. APK hashes:

- Baseline and rollback: `1748e89fc36aaafc9d9498d9b0391c5ed2b6ac61fab89b50abf15da63df74091`.
- Candidate: `755ede7ecf235e8d6b4d84c5666b6a9728843a9eef7239356e9ae79dd6e0087d`.

Process CPU is `/proc/PID/stat` tick deltas over device uptime, 100 ticks/second, guarded by PID
start time; 100% means one core. Each interval lasts about 10.1 seconds, with no screenshots,
UI queries or tracing during it. Package-scoped gfxinfo and PNG captures bracket the interval.
CPU includes all decoding/cache/runtime work; nothing is subtracted. SurfaceFlinger is whole-TV
CPU, not attributable GPU duration or energy. The rollback comparison uses the original APK in
the same disposable profile/cache, with playback activated and warmed after reinstall.

Two setup controls are retained but excluded from acceptance: an initial restored candidate
has no active media session and ignores media keys; a subsequent transition has not reached
the requested paused state. The initial run is stopped; the guarded driver stops on the later
state mismatch. The accepted candidate and
rollback runs verify the disposable media session, native playback state and track title before
each interval. These are driver-setup controls, not passing playback measurements. No owner media
session is active during the initial no-session control; no owner profile is opened or modified.

## Optimized app results

Cells are **app CPU / SurfaceFlinger CPU / window-frame delta**. All CPU values are percentages
of one core. All samples, including startup/transition outliers, are retained.

| State | Initial baseline | Warm baseline | Candidate | Rollback |
| --- | --- | --- | --- | --- |
| Paused short | 0.198 / 0.000 / 0 | 0.099 / 0.099 / 0 | 0.297 / 0.000 / 0 | 9.109 / 0.198 / 1 |
| Playing short A | 9.091 / 6.621 / 10 | 7.799 / 6.614 / 10 | 7.305 / 4.442 / 10 | 9.199 / 6.627 / 10 |
| Playing short B | 9.190 / 6.522 / 10 | 7.396 / 6.509 / 10 | 7.023 / 4.352 / 10 | 7.905 / 6.522 / 10 |
| Paused long | 1.283 / 0.099 / 0 | 1.186 / 0.099 / 0 | 0.693 / 0.099 / 0 | 0.791 / 0.099 / 0 |
| Playing long A | 8.012 / 6.429 / 11 | 7.298 / 6.410 / 10 | 6.924 / 4.451 / 10 | 8.391 / 6.614 / 10 |
| Playing long B | 8.309 / 6.528 / 11 | 7.594 / 6.312 / 10 | 6.818 / 4.447 / 10 | 8.111 / 6.627 / 10 |

Every playback pair changes 180 pixels in the progress crop (190,975)-(1730,995), with zero
changes in metadata (760,260)-(1870,720). Paused pairs change neither crop, including the high-CPU
rollback outlier. No cause is assigned to that outlier. Cache/startup variance limits app-CPU
attribution; it is not an animation-only reduction. The reproducible compositor rise on rollback
supports redundant submissions as one contributor, not the entire cause.

Candidate lifecycle samples, same units:

| State | App CPU | SurfaceFlinger CPU | Window frames |
| --- | ---: | ---: | ---: |
| Background playing | 4.352 | 2.176 | 0 |
| Hidden paused | 0.198 | 1.583 | 0 |
| Restored paused | 0.198 | 0.000 | 0 |
| Restored playing | 6.924 | 4.253 | 11 |

Background images show the launcher and do not prove visible app motion or app-attributable
compositor cost. Restored playback changes 180 progress pixels and no metadata pixels; restored
pause changes neither. Titles and accessible playback controls remain present after restore.
Physical display resizing and spoken screen-reader validation are not claimed; the separate
raster fixtures exercise changed bounds, clipping, image replacement and hide/restore.

## Shared component comparison

The component APK uses the existing opt-in fixture, not the ordinary app. Five seconds of warm-up
precede each ten-second phase. Baseline/candidate/rollback tests pass in 79.357/79.346/79.401 seconds, including
the visible marquee-change assertion. Every phase has zero root/sibling draws and zero window
FrameMetrics callbacks. This does not measure GPU execution.

| State | Baseline app / SF | Candidate app / SF | Rollback app / SF |
| --- | ---: | ---: | ---: |
| Static | 0.050 / 0.099 | 0.040 / 0.100 | 0.040 / 0.100 |
| Marquee | 7.999 / 26.205 | 7.999 / 25.904 | 7.898 / 25.351 |
| Progress | 6.879 / 5.706 | 5.579 / 3.465 | 6.939 / 5.783 |
| Combined | 7.988 / 26.553 | 7.758 / 26.479 | 8.039 / 25.956 |
| Restored static | 0.040 / 1.484 | 0.040 / 2.099 | 0.040 / 1.296 |

SF samples bracket tagged phases with ADB boundary latency; the final sample includes immediate
activity teardown. They are diagnostics, not exact partitions of internal process samples. The
fixture's global screenshot assertion does not by itself prove crop-specific movement in every
phase. Patched `AndroidRasterPlacementTest` separately passes 2/2 in 39.157s, including visible
pixels, 45 moving replacement captures, clipping and hide/restore, with unchanged assertions.

## Remaining gate

The experiment reduces progress-associated work but does not solve the material marquee/combined
cost or establish complete GPU acceptance. No animation is disabled/slowed, no playback frequency
is reduced, and no threshold or budget is broadened. The draft stays open. Raw reports, captures,
drivers, all controls and original APKs remain private under `build/tv-checks/414` in this worktree.

## Cleanup

Both temporary packages, the owned port 18084 reverse mapping and the explicitly named UI-dump
file are removed. Only the two pre-existing `.v2test` packages remain; the owner's app retains
its November 1 installation timestamp. Projectivy launcher is foreground. The loopback fixture
and its Windows Python launcher are stopped after checking command lines, parent/child
ancestry, creation timestamps and listener ownership. No unrelated process is stopped.
No device settings, owner credentials or owner playback data are changed.
