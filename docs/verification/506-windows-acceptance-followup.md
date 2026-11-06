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
runs inside the state store's atomic update, and a common single publisher drains the latest pending
display request. This also handles playback returning to an identical previously rendered Playing
value, where StateFlow equality cannot detect that an intervening publication occurred. Reentrant
callbacks request another mapping without blocking or starting a second publisher. Ownership is
released even if mapping fails. Both initial/equal-state regressions fail before their respective
corrections and pass afterward. This demonstrates a real
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
  carries the server connection; the probe also configures an unrelated private fixture CA.
  Certificate-validation fixtures independently establish preservation of platform trust; the
  tunneled server connection is not evidence of a private-server TLS handshake.
  No acquisition/listen/feedback mutation is made.
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

After the equal-state publication correction, the Gaming PC reruns all 584 presentation and both
real native preview cases successfully, rebuilds/verifies the package, and passes another fresh
cold/warm launch pair with both restores. Those final-revision captures are inspected separately
under `presentation-final-owner`. Earlier six-launch results remain their own checkpoint; they are
not relabeled as measurements of the later common publisher. In total, eight packaged launches and
eight restores have positive evidence across the two checkpoints. No windowing production code
changes between them.

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

## Final-source native and CI follow-up

After serialized publication is added, all 584 presentation tests and Android/both iOS common
compilation pass again. The Gaming PC's final package, verifier, two real native preview cases and
fresh cold/warm app launches pass. The final source's full 17-case native iOS suite also passes on a
newly created owned simulator (27-second Gradle execution, excluding provisioning), with zero
failures/errors/skips. Creation is a separate local setup from the failed clone replay; the clone
failure is preserved and is not relabeled as a pass. The owned device and fixture are removed.

The superseded [81dc00f83 Verify run](https://github.com/goosepod/naviamp/actions/runs/42256634333)
passes Linux/macOS Desktop and API 30/API 35 native checks. Its shared job reaches all 672 UI cases
but fails a screenshot write in `NaviampDiscoveryRequestsUiTest`: the test hardcoded a Mac-only
`/private/tmp/478-request-captures` directory. Its artifacts now go to the module's portable
`build/reports/tests/discovery-requests` directory, where CI can also collect them. Assertions,
request behavior and screenshot generation remain intact. Final revision checks remain the gate;
passing checks from a superseded tree are supporting evidence only.

## Second shared race found by final-source coverage

Run [42264936139](https://github.com/goosepod/naviamp/actions/runs/42264936139) passes the dedicated
Linux/macOS/Windows native jobs and API 35. Its coverage aggregation reruns the real Desktop Core
preview contract and times out during seek: the native/shared clock reaches 23.2 seconds while
Now Playing remains Loading. The ALSA null sink was configured; this is not an audio-device
provisioning failure. The preceding presenter correction alone is insufficient.

`NaviampLivePlaybackController` also performed non-atomic whole-snapshot read/copy/write updates.
A progress callback could overwrite a concurrent Playing callback with its stale Loading snapshot.
The new common concurrency regression loses 4,767 of 8,000 seek-generation increments on the
previous source. A deterministic reentrant observer regression also receives Playing then Loading,
rather than Loading then Playing. Both failures are retained before the fix.

Field updates now use atomic StateFlow transformations. One notification owner drains the latest
canonical state to the progress flow and observers, including reentrant updates; this prevents an
older nested notification from following a newer one. No native adapter, clock frequency,
deadline, animation policy or test assertion is relaxed. This correction and its tests are common.

The corrected full Core app suite (292 methods) and presentation suite (584 methods) pass on Mac
and the Gaming PC. Android/JVM/both iOS common compilation and architecture checks pass. The
Gaming PC also passes both real native preview contracts, packaging validation and a new
cold/warm launch-and-restore pair. Five-second one-core CPU observations are 0.313% and 0.313%;
these are quantized idle observations, not complete animation performance acceptance. Fresh iOS
and final-source hosted validation remain required.

The same hosted run's API 30 failure is the existing #486 Compose test-clock measure/layout
reentrancy in the smart-playlist collapse test (36/37 rendered cases pass). Preserve that failure
and require unchanged assertions on the final revision; do not attribute it to the Aurral icon.

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

## November 6 owner Mac feedback

The owner reports that the five requested Mac checks pass on `01c863a6d`, apart from the
specific defects below. This is owner acceptance of that Mac checkpoint, not physical-device
acceptance for other hosts or proof that the historical Windows/iOS investigations are resolved.
The follow-up remains within existing Aurral issues: #383 (Home/radio), #476 (artist portrait),
#477 (release rows), #478 (request status), and #506 (integrated acceptance). No new issue is needed.

- Home and full-collection Mix Builder grids now use the existing generated builder artwork and
  icons, matching the list presentation instead of attempting to load an absent album cover.
- A server that does not advertise Sonic Similarity uses Instant Mix for Radio/Autoplay, including
  a saved Sonic preference. Settings show Instant Mix selected and Sonic disabled. The portable
  stored preference survives switching to a capable server; export/import/sync format and defaults
  are unchanged. An advertised Sonic endpoint's empty/error/timeout results are still reported;
  they do not silently change the selected method.
- Aurral Instant Mix reuses the existing owned-library album/artist fallback when its regular
  similarity route is empty or unsupported. Native Navidrome's strict endpoint behavior and raw
  diagnostic probes retain their existing contracts. A read-only live Aurral probe confirms Sonic
  is unadvertised, both Instant Mix and capability-fallback Radio return 15 tracks, and a returned
  identity resolves through the real Library API. The probe uses the dedicated normal account
  over an owned SSH tunnel; it submits no acquisition, listen or configuration mutation.
- Releases with no previews reclaim the empty playback-control column. Mixed releases keep their
  shared title column so preview-less tracks remain aligned with playable previews.
- Release request overlays use state-specific icons and accessible labels from the durable
  acquisition ledger: queued/active/waiting, saved, finished, verified available, or uncertain/error.
  A queued request no longer retains the fresh-download arrow. The icon remains legible while its
  repeated-request action is disabled, including after refresh/restoration. Finished is distinct
  from verified Library availability. The owner's Silver Panda requests are not replayed.
- Aurral artist portraits open the existing shared full-size artwork viewer. The circular portrait
  policy remains intact, absent images offer no action, and the new accessibility label is defined
  in all 17 maintained catalogs.

Gaming PC validation passes 1,106 domain, 584 presentation, 127 provider, 24 focused UI and two real
native preview methods (1,843 total, zero failures/errors/skips), plus packaging validation. All 37
changed production files match the reviewed source by normalized SHA-256. Owned scheduled tasks
are removed. Rendered captures verify all three builder icons, compact preview-less rows and the
queued clock overlay. No platform production file changes in this feedback follow-up; all product
behavior and presentation changes are common. Final-source hosted verification remains required.

## Broader Library Radio after owner review

The owner accepts Mix Builder artwork, request icons, expanded artist portraits and preview-less
spacing on `2a97577f7`. Radio still returned only the seed artist, exposing the narrowness of the
initial fallback. The owner explicitly requests a broader mix and a relaunched Mac build; this
continues #383/#506 without creating a new ticket. The existing development profile is already
Mixed with balanced artist spread; no setting is changed to obtain variety.

Shared `LibraryRadioMix` policy now interleaves and caps artists, excludes seed/duplicate/preview
IDs, and gives genre-matched candidates priority. Aurral provider common code uses up to three seed
genres, each with a bounded 80–300-track query. Missing or sparse tags can be supplemented by one
bounded random-catalog query in the same selected Library scope. Full artist-album scans are only
an optional last fallback; they are not the primary station. The rule also broadens a same-artist
server recommendation list and is shared by track, album and artist Radio. A small library can
return a shorter mix rather than fabricate diversity. Existing DJ tuning remains authoritative.

Four or more candidate artists cap each artist at a quarter of the returned fallback pool, rounded
up; smaller catalogs adapt the cap to their available artists. This is metadata/Library mixing,
not Sonic analysis. Unsupported optional catalog routes can fall back, but permission failures,
other provider errors and cancellation propagate. Normal Navidrome and raw diagnostic endpoint
behavior remain unchanged. No setting, serialization schema, translation or platform production
file changes in this increment.

Local validation passes 1,111 domain, 584 presentation and 133 provider methods on Mac (1,828 total),
including the live read-only test. Gaming PC validation passes 1,111 domain, 584 presentation,
132 provider, 24 focused UI and two real native preview methods (1,853 total), plus packaging.
All report zero failures/errors/skips. Android/JVM/both iOS common compilation and architecture
verification pass. Live Aurral returns 20 tracks from 20 artists through both direct Instant Mix and
capability-fallback Radio; an other-artist identity and its actual HTTP media stream are validated
without playing audio or submitting a listen/acquisition/configuration mutation.

The prior `2a97577f7` hosted run 42296271297 reproduces the still-open #497 native coverage failure:
seek clocks advance to 23.2 seconds while shared Now Playing says Loading. Its failed report is
preserved; it precedes this Radio-diversity increment and is not attributed to it. Passing local
native checks do not resolve that intermittent investigation. Final-source hosted checks remain a
merge/release gate.

The final live trace uses the owner's reported Sugar Ray seed: both paths return 20 tracks from
20 artists using six genre-filtered catalog queries in total and zero unfiltered fill queries.
Aurral negotiates Subsonic form POST for these reads. The acceptance transport now traces that
method and applies the same read-only endpoint guard to form POST and both GET overloads; request
bodies/credentials are never printed. The trace-only follow-up changes no production input.
