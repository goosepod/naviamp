# Current-source macOS acceptance — October 30, 2026

Tracking: #408 / #280. Outcome: **not accepted for release**. Visible fixture playback and
popup checks make progress, but restored-session seek failures (#422), initial dialog keyboard
focus (#421), GPU attribution and other listed coverage remain open. No product fix, tag,
store submission or release is performed by this testing.

## Source and isolation

Tested source `c72dcccf6321e3ef51f7435ee5c21bc3dd3332ba`, v2.10.0/build 60. At testing time,
current main differs from that source only by the Android Alpha README instructions. The prepared
ordinary release bundle is in `/private/tmp/naviamp-408-scope/apps/desktop/build/compose/binaries/main-release/app/Naviamp.app`.
The prepared observer is `build/performance-408/Naviamp Performance.app` in that checkout.
All 87 production classpath JAR hashes match its retained manifest; its strict deep signature
verification succeeds. The observer changes only test launcher/configuration and uses the existing
`ApplicationRenderProbe`; it does not change app behavior or animation cadence.

The ordinary-launcher follow-up copy, `Naviamp Keyboard Acceptance.app`, retains the production
entry point and all 87 matching JARs. Only its test identity/display name, ad-hoc signature and
isolated `user.home` launcher option differ. Its unique bundle identifier is
`app.naviamp.acceptance408`, avoiding ambiguity with the already-running normal app. No production
source file or installed application is modified. Normal-profile hashes were not captured before
this session, so no before/after hash-preservation claim is made.

Both copies use the same disposable `mac-fixture-home`. Synthetic server:

```sh
python3 scripts/android-tv-fixture.py --mixed-metadata --tracks 2 --albums 2 --burst-seconds 600
```

The server binds only `127.0.0.1:18080`; username/password are the synthetic `fixture` values.
Audio is muted through the test player volume control. An initial `--burst-seconds 0` attempt
supplied data below playback bitrate and is rejected as test setup; it is not evidence of an app
startup/stream failure. The fixed-throughput fixture supplies finite, range-capable 600-second WAVs.
No live provider, Aurral request, server credentials or user's media is used.

## Visible functional results

- Fresh isolated Subsonic connection succeeds; Home and Library Artists/Albums/Songs navigation
  populate with fixture data. Real native playback progresses, including observed short-track
  clock movement from 0:06 to 0:23. Missing cover-art placeholders render; this does not test loaded
  bitmap semantics or real artist images.
- Long metadata visibly shifts while paused and remains clipped to the player region. Combined
  progress and scrolling also move. Screenshot observations come through Computer Use, outside
  each timed CPU interval. Smoothness is observed qualitatively, not a frame-time distribution.
- Track menu renders with correct options over the player, opens repeatedly, and dismisses on
  outside click. In the unique-identity ordinary copy, Escape also dismisses the menu immediately.
- Track details renders title/metadata, scrolls, and closes through its visible Close button.
  **Immediate Escape fails until Tab establishes focus** in the unique-identity retest. Tab then
  Escape closes it. Filed as #421; earlier key results from the shared-bundle-ID observer are
  inconclusive and not used as keyboard acceptance.
- Pointer seeking visibly reaches the midpoint. **A restored paused session starts playback on
  seek without a Play command**, reproduced in two ordinary-copy restored observations. Control:
  explicit Pause at 5:23 followed by seek stays at 5:00 with Play available. After normal native
  window close/reopen, the position restores near 5:22 rather than the settled sought 5:00;
  seeking immediately again exposes Pause. Filed as #422. The earlier observer SIGTERM/reopen is
  not a graceful-close test and is excluded from position-retention acceptance.
- Native minimize button succeeds (observer confirms `iconified=true`); restore retains the
  on-screen paused position and repaints the player. The shortcut attempt did not iconify and is
  retained as an invalid lifecycle sample. Hide and playing-minimized behavior are not established.
- Native zoom renders a populated wide layout with retained controls. Border-drag attempts do not
  change the observed size; interactive narrow resizing/restore is unverified, not passed.
- Ordinary launcher opens the source and queue without the render observer. One Computer Use
  launch call takes unusually long; its wall time is not an app-startup latency measurement.
- About visibly reports v2.10.0/build 60. The actual shared changelog renders Aurral first, includes
  storage totals, and scrolls through Upgrade notes and the final Known issues entry. It still
  describes the TV root cause conservatively; no changelog content is edited in this test.
- Native AX exposes media names, seek slider value, Play/Pause state, menu entries and dialog
  contents. This is semantics inspection, not spoken VoiceOver acceptance or complete keyboard
  traversal. No physical screen-reader pass is claimed.

## CPU and parent-frame samples

Apple M1, macOS 27.0.1 (26A434), packaged Metal renderer; two LG QHD 2560x1440 displays at 75 Hz,
AC power. Same 950x640 window, 950x612 parent client, scale 1x, for the following ten-second samples.
CPU is percent of one process core. No CPU, compilation, observer or audio overhead is subtracted.
The trimmed runtime reports compilation timing unavailable. No UI observations run inside these
CPU intervals. These are initial single samples, not repeated sustained budget certification.

| State | Process CPU | Parent frames |
| --- | ---: | ---: |
| Short metadata, paused/static | 0.466% | 0 |
| Short metadata, playing/progress | 4.653% | 0 |
| Long metadata, paused/marquee | 0.470% | 0 |
| Long metadata, playing/combined | 3.758% | 0 |
| Combined with track menu | 2.695% | 0 |
| Combined with track-details modal | 3.114% | 0 |
| Attempted shortcut minimize, not iconified (invalid) | 2.221% | 0 |
| Native minimized, paused | 0.944% | 0 |
| Restored, paused/marquee | 0.587% | 0 |

The menu/modal samples show no sustained parent redraw stream or CPU increase over the paired
combined sample. They support the scoped popup observation but do not establish all-state
performance acceptance. The minimized sample overlaps Metal-export processing on the host and
is not an otherwise quiet matched comparison. Retain the invalid shortcut sample and all results.
Stale disposed owned surfaces appear in observer output as uninitialized; do not interpret their
zero counts as visible content. Active parent and owned popup surfaces have attached observers.

The configured observer has `naviamp.probe.capture=false`, so its results correctly report
`physical_captures=false/false`. Independent Computer Use screenshots establish the scoped
visible motion above; there is no automated full-client unobscured/pixel-difference acceptance.

Reproduce using the documented observer setup in `scripts/animation-probe/README-visible-app.md`:
establish state visibly, write a unique phase name to `mac-measurements/phase`, wait for its result,
then inspect the actual UI again. Local raw results use the `oct30-` prefix.

## Separate GPU/compositor trace

A separate ten-second `Metal System Trace` attaches to the verified observer process during
combined playback; it is not concurrent with a CPU sample. Exporting `metal-gpu-intervals` succeeds.
The existing `summarize-metal.py` finds no attributable Naviamp active intervals. Whole-desktop
WindowServer channel totals are Compute 31.480 ms, Fragment 565.219 ms and Vertex 122.552 ms.
Channels overlap; totals are not additive GPU utilization or energy and include other desktop
work. Missing Naviamp intervals do **not** establish zero GPU cost. No matched per-state or
per-window GPU comparison is established, so the GPU/compositor gate remains open.

Raw trace, interval XML, production hashes, display/power metadata, disposable home and observer
results remain local under `/private/tmp/naviamp-408-scope/build/performance-408` (display/power
snapshot also at `/tmp/naviamp-mac-display-power-20261030.json`). Raw trace metadata is not uploaded.

## Remaining scope and cleanup

Resolve/retest #421 and #422; establish reliable native narrow resize, hide, input/focus restoration,
spoken accessibility and attributable GPU/compositor evidence. Live-provider/macOS Cast, certificate
chooser/import and cross-device settings-sync acceptance are not supplied by this synthetic run.
The Mac release gate stays open; earlier valid provider/device evidence retains its original scope.

Only disposable test processes and the loopback fixture are stopped. Test homes and local evidence
are retained for reproduction. No Android phone operation, installed-app replacement, platform
production change, source behavior change or performance-budget waiver is included.

## Shared fixes and rebuilt-package continuation

The owner requests fixing #421 and #422. They remain separate issue branches/PRs:
#424 (`fix/422-restored-paused-seek`, `098005f0`) and #425
(`fix/421-dialog-initial-focus`, initial `188a74dc`, attachment refinement `72f9e653`).
The local combined verification revision is `3b1fa20cc` on
`test/421-422-macos-verification`. Every production change is in Core commonMain. No Android,
Desktop or iOS production file, native adapter, setting, migration, translated UI copy or playback
update cadence changes.

The playback adapter extends its existing deferred-start state to restored queues: seeks update
the saved start position without opening audio, until explicit Play/Resume. Provider-offset replay
seeks retain explicit pause intent. The common playback controller uses accepted seek generations
to bypass the telemetry save throttle, including a seek immediately after Pause.

The shared dialog viewport gains a focus target, requesting it after the owned surface's first
frame when its window is active and no child already owns focus. Child-first Escape handling and
the configured dismissOnBackPress policy are retained. Focus acquisition is a bounded lifecycle
effect, not a continuous animation or polling loop.

### Regression and build evidence

- Before the playback change: four selected cases execute; the three new restored-seek/persistence
  regressions fail. Afterward all 537 presentation cases pass, with zero failures/errors/skips.
  An extended explicit-pause/provider-offset replay assertion passes too.
- Before the dialog change: two rendered cases execute and immediate Escape fails. The fake native
  surface deliberately omits Compose Popup's own focus behavior; the other case covers disabled
  Escape and Tab/Enter activation.
- Final combined source passes all 624 UI and 537 presentation cases with zero failures/errors/skips,
  verifyCoreFirstArchitecture, Android shared-presentation compilation, and both iOS ARM64 device
  and simulator shared-presentation compilations (including the changed common UI).
- The initial packaging invocation selects Homebrew's JDK and fails the packaging vendor check;
  this is not a source compilation failure and the check is not bypassed. Rebuilding with the
  existing `~/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home` succeeds. The final combined
  build repeats packaging, both full suites and all shared-target checks successfully.
- The ordinary optimized bundle is copied whole to ignored
  `build/fix421422/Naviamp Fixed Acceptance.app`, using test ID `app.naviamp.fix421422` and a copied
  synthetic fixture home. All 87 production JARs match; strict deep signature verification passes
  after the test copy's identity/configuration changes and ad-hoc seal. No observer replaces its
  production entry point. Initial/final payload manifests and source IDs remain local.

Logs: `/tmp/naviamp-422-red.log`, `/tmp/naviamp-421-red.log`, `/tmp/naviamp-422-green.log`,
`/tmp/naviamp-421-green.log`, `/tmp/naviamp-422-replay-check.log`,
`/tmp/naviamp-421-422-package.log` (vendor rejection), `/tmp/naviamp-421-422-jbr-package.log`,
`/tmp/naviamp-421-422-final-presentation.log` and `/tmp/naviamp-421-422-final.log`.

### Native playback acceptance and keyboard limitation

The initial combined fixed package (`5eddbde24`, with the final #422 production code) restores
paused at 5:37. Clicking the seek midpoint leaves Play available at 5:00; subsequent screenshot/AX
observations confirm it does not advance. Normal native window close exits the observed process;
reopening restores 5:00 with Play available. Explicit Play then advances from 5:00 to 5:09.
Explicit Pause at 5:13 followed immediately by a seek to 2:31 remains paused; normal close/reopen
restores 2:31 with Play available. These pass the two original playback reproductions. The final
combined copy (`3b1fa20cc`) also opens paused at 2:31, retaining the test source and queue.

Keyboard retesting cannot yet be accepted. Escape and Tab observations in the first fixed copy
are inconclusive: the menu control case also ignores Escape, and a read-only AppKit query confirms
`NSRunningApplication.isActive=false` for that process. A current screenshot/AX window alone does
not establish a valid keyboard target. The native visibility guard finds an obscured full client
rectangle, while a small region away from tool overlays is visibly test-owned. No first-candidate
keyboard failure is promoted to a confirmed product regression from those observations.

Computer Use app selection, explicit bundle-ID selection and native Raise do not activate the test
process; its optional launch_app method is unavailable. The owner is asked to bring the distinctly
named test app forward using its Dock icon. Final native immediate-Escape and focus-return acceptance
remain pending that activation. The #425 PR stays draft until this observation is established.

The final test app is intentionally left open and paused at 2:31 for that check; the credential-free
loopback fixture remains available. No phone, public release, tag, store submission or main/release
merge is performed. Earlier GPU, performance, narrow-resize and spoken-accessibility limits remain
open and are not waived by these fixes.
