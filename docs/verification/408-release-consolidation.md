# v2.10.0 consolidation and performance continuation

Tracking: #408. The owner requested consolidating the latest Windows, physical Android TV,
and final phone acceptance work into the release line, then resuming macOS and Android performance
testing. No publication, tag replacement, or store submission is requested.

## Integrated source

The starting candidate is `0f95e6a4f` from PR #407, which includes the Windows editor-entry and
progress timeline fixes, physical TV acceptance, common navigation/accessibility fixes, phone
acceptance, Mac/iOS evidence, TLS checkbox repair, and AudioMuse server repair record.
The final phone continuation from `9254f43c8` is added without restoring superseded store-launch
changelog copy. The owner-approved candidate keeps Aurral first. The TV handoff now points to
the completed phone/server repair rather than describing that investigation as unperformed.

The existing `release/2.10.0` ref is old. Integration proceeds through linked GitHub pull requests
and required checks, retaining existing history and tags. This document is preparation evidence,
not a claim that those pull requests have merged or that the release is accepted.

## Verification and remaining gates

The unchanged candidate `0f95e6a4f` passed the complete hosted matrix: shared/Android, native
Windows/macOS/Linux, Android API 30/API 35, iOS simulator, and verified-tree recording.
Independent Mac review passed 36 focused shared UI regressions and four real HTTP range tests.
The initial `f4da8bbc5` integration changes verification documentation only. The subsequent
#412/#413 follow-up strengthens the desktop native preview test by synchronizing its paused
baseline with the actual native clock; its full local native suite passes 65 cases with two
Linux-only skips. No production implementation changes in either follow-up. The final combined
source still requires the protected main/release hosted matrices before merging.

The release transfer from its older baseline also has a separate
[native-boundary inventory](408-release-native-boundaries.md) for the already accepted host adapters.

Current-source macOS and physical Android performance testing remains necessary. Prior Mac
performance samples predate the retained progress timeline fix and include rejected/diagnostic
visibility samples. Prior Android functional/optimized-package checks do not establish a complete
CPU/frame/GPU acceptance matrix. New builds are being prepared for isolated test data and explicit
device selection. Static, marquee, progress, combined and applicable lifecycle/input/accessibility
states require visible motion, process CPU, parent frames, and GPU/compositor evidence under
recorded conditions. No test result is implied merely by starting a build.

Windows CPU variability/compositor attribution and TV signed-package, external audio focus,
HDMI/CEC, network recovery and endurance remain explicit outstanding gates. Physical iPhone
testing is unavailable; iOS testing uses the simulator as requested.

## Accepted cohort and selected-scope follow-up

The cohort subsequently passes both complete protected matrices and lands through #409/#410.
Main `13dc942ac` and release `2a6ed29a3` have identical trees. The final
[milestone scope audit](408-release-scope-audit.md) identifies the one omitted common storage-total
fix (#357/#358), validates it on current Core and records the older work already represented.
That follow-up requires its own protected checks; initial performance measurements remain
explicitly pinned to the preceding cohort and do not close performance acceptance.
