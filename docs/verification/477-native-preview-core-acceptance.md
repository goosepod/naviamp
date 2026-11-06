# Discovery preview acceptance through Core and native playback

Tracking: #477 / #489, dependent on #476 / #488. This is an acceptance increment on the
release-details branch, not a separate shipped capability.

The existing common sample-controller tests use a fake preview port to verify queue isolation.
The existing Desktop channel test uses native playback without the complete Core command graph.
This shared testkit contract combines those paths: a three-track queue, real queue selection,
a real seek, release navigation, preview play/pause/resume/end, Back and explicit main Resume
all run through `NaviampCore` and the shared streaming playback services.

The host supplies only a native engine, disposable file/HTTP audio locations and direct native
clock observation. Shared testkit owns the fixture provider, discovery identities, PCM tone,
source-bound commands, queue/session expectations and failure diagnostics. The three tracks
and current index must remain intact; preview IDs cannot enter saved sessions; completing or
leaving a preview must keep main audio paused. The real paused native clock and the shared
position must remain stable, and main audio advances only after explicit Resume.

## Clock and lifecycle details

Queue selection must actually initialize native playback; seeding a Core structural snapshot
alone does not mount a native queue. The fixture also supplies a consistent saved connection,
Home source and persistence source rather than relying on the default unconnected test catalog.

Seek completion is checked against the actual native clock, not the optimistic UI seek marker.
The mixer has separate audible and read-ahead clocks. Native observation uses its audible clock
while playing and source clock while paused, matching the existing polling contract. The paused
baseline is taken only after the shared callback agrees with that native clock. No native deadline
or position tolerance was increased.

BASS registrations and device lifetime are process-global. The new native test has a separate
class so the existing `forkEvery = 1` policy gives it its own JVM. Earlier runs passed the new
Core contract while the older channel test timed out at initial native progress, including one
run with separate classes. The unchanged older case passed alone; a later combined run with
state/error diagnostics passed both. This does not establish a cause or a stability fix. The
retained initial-clock failure is tracked separately in #497. No deadline or assertion was
relaxed, and no failed run is counted as a pass.

## Verification

Final local verification on 2026-11-03 passes on the refreshed `716bce49a` release-details
base plus this acceptance increment: both native test cases pass with zero skips, along with
Android/JVM/iOS ARM64/iOS Simulator ARM64 testkit compilation and architecture verification.
Log: `build-477-final-current-main.log`. An earlier diagnostic run also passed both cases in
separate JVMs (`build-477-native-preview-diagnostics.log`). Failure evidence remains linked to
#497; neither successful run establishes that startup-timeout cause.

The feature base now includes accepted main `90523ebe3`, the merged #490 / #494 fixture
isolation fix. This acceptance increment does not merge either draft discovery capability.
Hosted checks still gate the final feature source.
The reproduction command is:

```sh
./gradlew :core:testkit:compileDebugKotlinAndroid \
  :core:testkit:compileKotlinIosArm64 :core:testkit:compileKotlinIosSimulatorArm64 \
  :platforms:desktop:desktopTest --tests '*DesktopDiscoveryPreviewCoreIntegrationTest*' \
  --tests '*DesktopPreviewPlaybackIntegrationTest*' verifyCoreFirstArchitecture
```

Use a real output device for channel-clock acceptance. Both tests explicitly exclude the
`naviamp.bass.test.outputDevice=0` no-sound configuration. Their actual output volume is muted;
the new fixture contains a nonzero PCM tone. Native channel execution and nonempty-queue/session
preservation are not proof of audible speaker output or of live Aurral preview transport.

This Mac has no configured Aurral live-test credentials in its environment. The prior live
transport/request evidence remains in `477-aurral-release-details.md`. Audible live output,
physical touch/TV remote and screen-reader acceptance remain open; no server acquisition was
submitted by these tests. The Windows first-frame observation remains a separate investigation,
#493, and is not validated by a macOS native integration test.

## November 4: refreshed 2.13 integration

Refreshed this acceptance branch onto release head
`6fec69fa0ba46834bc3baddbd7a4f3995c53f981`, which includes the updated artist destination and
accepted main `c5e119f2f49c721c6816f6b6b6000ab3ae82b996` / #508. The native contract and
assertions are unchanged. The reproduction command above passes both real native test cases
with zero failures/errors/skips, along with Android/JVM/both iOS testkit compilation and
architecture verification. No deadline, position tolerance or assertion was relaxed.

The result verifies queue/current-index/session preservation, preview pause/resume/end/Back,
stable paused native/shared position and explicit main Resume on this integrated tree. Output
remains muted; this is not live Aurral audibility, physical-input or screen-reader acceptance.
No live mutation was submitted. This passing execution does not establish the cause of the
intermittent initial-clock issue #497. Current-head CI and the parent feature acceptance gates
remain open, so #498 and the parent feature PRs remain drafts.
