# Windows/Aurral acceptance follow-up — November 6, 2026

Tracking: #506, #493, #497; follows integration PR #525. No release or merge is implied.

## Changes

The Windows branches at `c94cd6b11` and `429edf599` add platform-plus-custom certificate
trust, live native preview coverage, and a one-shot visible-content surface frame request.
This follow-up retains those changes and repairs the shared verification problems found in
Verify run [42131889038](https://github.com/goosepod/naviamp/actions/runs/42131889038).

The six storage failures report `table discovery_acquisition already exists`. Their synthetic
historical databases were created from the latest schema without removing the branch-only table
before rewinding `user_version`. The fixtures now remove it. Production migration 29 is unchanged;
accepted main's latest migration is 28 (schema version 29). A new regression upgrades that immediate
baseline twice, preserving saved authentication and album data. No compatibility migration or user
database repair is added.

The Linux native preview failure reports `main=Loading` while the native clock advances after seek.
A deterministic common regression demonstrates a publication race: mapping an old Loading snapshot
can complete after another callback publishes Playing and overwrite it. Now Playing mapping now
runs inside the state store's atomic update, so a failed compare-and-set recomputes from current
playback. The regression fails before the correction and passes afterward. This demonstrates a real
shared defect consistent with the failure, rather than proving every historical #497 timeout has
the same cause. Playback deadlines and update frequency are unchanged.

The live preview probe now requires native clock progress, a stationary paused clock, resumed
progress, and end-sync callback cleanup. A configured live run cannot reuse an up-to-date test report.
It remains muted; it does not establish speaker audibility or physical accessibility.

The Home card's Aurral logo now uses the original retained SVG path as a Compose vector drawable.
The viewport, lime fill, outline and proportions are unchanged. This avoids reducing the 512-pixel
raster to small icon sizes. The shared Home/hub regression captures phone (360), desktop (1100) and
TV (1280) widths; rendered captures have been visually inspected.

## Local evidence

- Full shared presentation suite: **584 executed, zero failures/errors/skips**.
- Shared storage suite: **70 executed, zero failures/errors/skips**.
- Trust policy: three common methods; real JVM certificate validation: three methods; shared
  surface presentation: three methods. All execute successfully.
- Real Desktop Core/queue preview contract and native preview lifecycle contract: two methods,
  zero failures/errors/skips, with muted output and actual native clocks.
- Strengthened live Aurral preview: one method, zero failures/errors/skips (14.865 seconds) on
  macOS. The dedicated acceptance account is read-only for this probe. A temporary SSH tunnel
  carries the server connection; an additional unrelated private fixture CA exercises preservation
  of the public preview media's platform trust. No acquisition/listen/feedback mutation is made.
- Fresh owned iOS simulator/fixture native suite: **17 methods, zero failures/errors/skips**.
  The fixture and cloned simulator are stopped/deleted afterward. This passing local run does
  not establish the cause of the intermittent hosted bootstrap stall in #454.
- Android debug, iOS ARM64 and iOS Simulator ARM64 shared compilation pass; Core architecture
  verification passes. The vector resource is compiled for all three UI targets.
- Gaming PC: isolated exact-source Windows distributable builds and its packaging verifier pass.
  JDK 21 and the installed Visual Studio CMake/native toolchain are used. The ordinary profile
  and installed application are not used.

Raw local logs are under `/private/tmp/naviamp-506-*`; generated reports are in this worktree's
module build directories. Counts describe individual suites, not an invented nonoverlapping total.

## Gaming PC native and visible-window evidence

`scripts/windows-initial-presentation.ps1` is an opt-in interactive probe for repeated cold/warm
packaged launches with separate disposable profile pairs. It observes initial client pixels before
any app activation, resize or pointer input, then checks minimize/restore. Captures must be
unobscured according to native Z-order and nonblank; manual inspection of retained captures is
still required. Idle CPU is recorded separately and is not a substitute for animation performance
acceptance. No repaint timer, startup sleep in production, or animation suppression is introduced.

Initial harness attempts are retained on the Gaming PC: the first watched the jpackage wrapper
rather than its child JVM; the next correctly rejected lack of foreground ownership; the native
occlusion guard subsequently rejected a covering fullscreen launcher. A later attempt could not
prepare the scheduled task's console as the foreground launcher. None counts as a visible startup
pass. Remote desktop input tooling also stalled during inspection; it supplies no new app evidence.

The final harness uses a small real, owned launcher form before Naviamp exists. It establishes
normal launch focus without activating/resizing the application after startup. Native Z-order
checks reject any launcher or foreign window covering the captured client. This is test-only
setup; production code remains unchanged.

On Windows 11 Pro build 26300, with NVIDIA GeForce RTX 5060 Ti driver `32.0.16.1742`, the exact
candidate's ordinary Windows package passes **three cold/warm profile pairs**, six launches total.
All six unoccluded initial captures show the connection UI without input or resize; all six restored
captures are byte-identical to their corresponding initial images. Every image has been inspected
visually, directly or by equality with its inspected initial capture. Captured client geometry is
934 × 601 pixels in every run. The graphics inventory reports a 3840 × 2160, 60 Hz output; the
Moonlight view is a separate remote observation and is not used to infer native pixel dimensions.
The smooth Aurral vector also appears in the connection provider picker.

| Launch | Initial capture after launch, ms | Five-second idle CPU, one core |
| --- | ---: | ---: |
| 1 cold | 10985 | 0.937% |
| 1 warm | 5928 | 0.000% |
| 2 cold | 6483 | 0.626% |
| 2 warm | 5791 | 0.000% |
| 3 cold | 6771 | 0.000% |
| 3 warm | 5372 | 0.313% |

These timings include the three-second observation delay and native-window discovery; they do not
measure first-pixel latency. CPU zeroes reflect the short interval's process accounting resolution,
not proof of zero cost. Concurrent build activity and the short static interval preclude animation
budget conclusions. Parent-surface frames and GPU/compositor cost are not measured in this startup
probe. The unrelated ongoing animation-performance gates remain open.

The Gaming PC also executes **584 shared presentation, 70 storage, three certificate-validation,
and two real native preview methods**, all with zero failures/errors/skips. The two native cases
use actual output-device clocks with muted output, not BASS no-sound device 0. They verify native
preview lifecycle plus real Core nonempty queue/session retention, seek and explicit main Resume.

Raw evidence is retained in the Gaming PC's owned `naviamp-506-acceptance` directory and locally in
`/private/tmp/naviamp-506-windows-evidence.zip`. No profile contents or private endpoints are
included in the captured evidence bundle. #493 still retains baseline-causality comparison and
broader input acceptance; six passing candidate launches alone do not prove its historical cause.

## Independent local native replay

The first additional fresh-fixture/fresh-simulator replay executes all 17 native cases with zero
failures/errors/skips. The second replay fails during the simulator **clone RPC**, before a native
test starts: `simctl clone` exceeds the existing 30-second provisioning bound. Its failure summary
and cleanup-complete marker are retained in `build/506-cold-native-replays/run-2`; the owned partial
clone and fixture are cleaned. No clone failure is counted as native acceptance, and no TLS or
playback deadline is relaxed. This remains separate from the historical native HTTP bootstrap stall.

## Remaining gates

Exact-source cross-platform GitHub checks, Linux confirmation of the corrected seek presentation,
repeated cold iOS acceptance, audible live previews/nonempty queue walkthrough, physical input and
spoken accessibility retain their own gates. This report does not waive #454 or #497 based on a
single passing local native run. Native acquisition cancel/retry remains deferred by the owner.

## Platform production audit relative to the Aurral integration branch

- `providers/navidrome/src/jvmAndAndroidMain/kotlin/app/naviamp/provider/navidrome/NavidromeJvmTlsMaterial.kt`: Java KeyStore,
  TrustManagerFactory and SSLSocketFactory effects implement the common trust policy.
- `providers/navidrome/src/iosMain/kotlin/app/naviamp/provider/navidrome/NavidromeAppleTlsMaterial.kt`: Apple Security's SecTrust
  anchor APIs implement the common trust policy while preserving platform anchors and SSL policy.
- `core/ui/src/jvmMain/kotlin/app/naviamp/ui/DesktopRasterPresenter.kt`: AWT visibility and EventQueue dispatch to
  Skiko's native frame queue implement the common one-shot surface-presentation decision.

This follow-up adds no platform production behavior. Its new product fix and logo resource remain
common; the Desktop native test and Windows acceptance script exercise actual platform effects.
