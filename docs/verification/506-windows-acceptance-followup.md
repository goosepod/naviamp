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

## Windows visible-window evidence and remaining gates

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
Keep #493 open until unoccluded repeated launch/restore evidence is recorded.

Exact-source cross-platform GitHub checks, Linux confirmation of the corrected seek presentation,
repeated cold iOS acceptance, audible live previews/nonempty queue walkthrough, physical input and
spoken accessibility retain their own gates. This report does not waive #454 or #497 based on a
single passing local native run. Native acquisition cancel/retry remains deferred by the owner.

## Platform production audit relative to the Aurral integration branch

- `providers/navidrome/src/jvmAndAndroidMain/.../NavidromeJvmTlsMaterial.kt`: Java KeyStore,
  TrustManagerFactory and SSLSocketFactory effects implement the common trust policy.
- `providers/navidrome/src/iosMain/.../NavidromeAppleTlsMaterial.kt`: Apple Security's SecTrust
  anchor APIs implement the common trust policy while preserving platform anchors and SSL policy.
- `core/ui/src/jvmMain/.../DesktopRasterPresenter.kt`: AWT visibility and EventQueue dispatch to
  Skiko's native frame queue implement the common one-shot surface-presentation decision.

This follow-up adds no platform production behavior. Its new product fix and logo resource remain
common; the Desktop native test and Windows acceptance script exercise actual platform effects.
