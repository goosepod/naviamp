# Frozen v2.10.0 Windows stabilization

Tracking: [#386](https://github.com/goosepod/naviamp/issues/386), draft stabilization
[PR #387](https://github.com/goosepod/naviamp/pull/387), release acceptance
[#280](https://github.com/goosepod/naviamp/issues/280), queue-end investigation
[#333](https://github.com/goosepod/naviamp/issues/333).

## Scope and source

The owner froze release scope and requested fixes plus local Windows testing on October 28, 2026.
Baseline: `9783770a0a2d50837ebf1b57f63d5d36106f65a6`, v2.10.0/build 60. Work is isolated in
`fix/386-release-stabilization`, worktree `.worktrees/stabilize-2100`. No new feature, setting,
schema migration, platform product behavior or animation cadence change is introduced.

## Stabilization changes

- Release notes, CHANGELOG and the shared English About changelog describe the complete frozen
  public scope, including Aurral as one new capability, Home defaults and imported-connection repair.
  The changelog regression asserts Aurral discovery, samples, requests, monitoring, queue retention
  and compatibility notes. Existing rendered tests traverse every Desktop and TV changelog entry.
- Artist-playlist lookup has a 30-second shared overall deadline, including listing and track reads,
  in addition to the existing 15-second request deadline, four concurrent reads and 500-playlist cap.
  Completed reads are retained, including matches in an interrupted batch. An incomplete lookup reports failure with retry; it does not
  claim that there are no matches or reuse the misleading 500-playlist truncation message.
  Parent cancellation and stale-source guards remain authoritative. No persistent cache is added.
- A shared adapter/controller regression exercises an eleven-track queue, Autoplay batch ten,
  delayed similarity, late Playing/Loading/duplicate Finished callbacks, duplicate candidates,
  and both Sonic and Instant Mix. Eligible continuations must start exactly once; fully excluded
  results must retain the queue and report the empty outcome. This is shared regression coverage,
  not a claim of physical TV acceptance or proof of the earlier TV root cause.
- Ordinary original-audio playback on Navidrome and Jellyfin now uses the existing shared provider
  byte transport, including its bounded random-access source when the server supports finite ranges.
  Previously only certificate/header-dependent streams selected this transport. A paused seek beyond
  BASS's downloaded buffer failed in the visible Windows fixture. The shared regression first failed
  on the old selection policy. Transcodes, discovery samples and providers without byte transport
  retain their existing selection policy; servers without finite ranges retain buffering limits.
  The real BASS integration now covers an ordinary HTTP source whose unread tail remains blocked.
  The loopback Python fixture also correctly bounds explicit ranges, including `bytes=0-0`, and
  rejects invalid ranges. Four real HTTP fixture regressions pass on Windows.

## Local evidence

The new slow-account regression failed on the baseline before the deadline change. The first full
run also caught an intermediate failure-state handoff defect in the deadline wrapper; it was fixed
without changing that existing assertion. Logs, XML and captures are retained in ignored build output.

The normal installed app and user profile are not used for fixture acceptance.
Native libraries are rebuilt with the installed Visual Studio 2022 Build Tools and JDK 21.

### Automated Windows results

The final full run succeeded in 8m55s (`build/windows-final-stabilization.log`):

```powershell
.\gradlew.bat '-Pkotlin.native.enableKlibsCrossCompilation=false' `
  '-Pnaviamp.bass.platform=windows-x64' '-Pnaviamp.bass.test.outputDevice=0' `
  verifyCoreFirstArchitecture jvmTest desktopTest `
  :core:ui:compileDebugKotlinAndroid :core:presentation:compileDebugKotlinAndroid `
  :apps:desktop:verifyDesktopDistributable :apps:desktop:packageReleaseDistributable `
  --continue --max-workers=4 --console=plain
```

| Suite | Reported cases | Failures/errors | Skips |
| --- | ---: | ---: | ---: |
| Core domain | 1,071 | 0 | 0 |
| Core app | 287 | 0 | 0 |
| Core storage | 64 | 0 | 0 |
| Core UI | 615 | 0 | 0 |
| Core presentation | 530 | 0 | 0 |
| Core testkit | 6 | 0 | 0 |
| Navidrome | 252 | 0 | 0 |
| Jellyfin | 35 | 0 | 0 |
| Desktop platform | 65 | 0 | 3 |
| Desktop host | 8 | 0 | 0 |
| Total | 2,933 | 0 | 3 |

Two skips require a Linux MPRIS session bus. The third is the preview/native-clock case excluded
by BASS's no-output test device; it was separately passed using the real default output device
(`-Pnaviamp.bass.test.outputDevice=-1`) with zero stream volume. Main playback retained its paused
native stream and position during preview, then resumed. No real server credentials or cloud folder
were configured for optional live entry points; a configured live integration pass is not claimed.
The four Python loopback range tests also pass. POSIX CI helper tests were attempted through Git
Bash but require Unix process groups/path semantics and are not represented as Windows passes.

The ordinary HTTP BASS regression passed in 18.775 seconds: playback began before the unread tail
was released, paused seek reached 30 seconds beyond the initial 262,144-byte buffer, resume fetched
the distant range, and stop canceled the reader promptly. All five TLS/ordinary playback cases
passed, along with the shared mobile TLS contract on the JVM. These do not substitute for Apple
compilation or physical iOS/TV acceptance.

Full XML is retained in `build/windows-complete-native-xml`, `build/windows-complete-ui-xml` and
the unchanged module result directories; aggregate counts are in
`build/windows-complete-suite-summary.json`. After the full run, the English seek note was expanded
in all three release-note surfaces and the changelog heading hierarchy corrected. The final
changelog/render checks, Android UI compile, real-output preview and rebuilt ZIP are recorded in
`build/windows-final-notes-hardware.log`.
That final follow-up succeeded in 3m29s: five changelog/About checks, including both Desktop and TV
render traversal, passed; the real-output preview passed in 7.025 seconds; Android UI compilation
and release packaging passed. Both changelog captures were visually inspected.

Local candidate: `apps/desktop/build/compose/distributions/Naviamp-v2.10.0-windows-x64-release.zip`,
SHA-256 `6399576A0EB346AC52F297F456867D0450F481C9EEC667F3CC6EBE77A3548427`.
Built from `2d78dafe3dd519c5c57712e477d1cdb030b09067` (tree
`dc7d202d82d1cdb0537619c57a8cd08a5aa83d91`); later PR-link documentation and fixture-test naming changes do not alter
the packaged source. It supersedes the earlier local ZIP built before the ordinary-stream seek fix. This is a local
stabilization artifact, not an accepted release tag or published release.

No Android/Desktop/iOS production file changed. The changed Desktop test directly exercises real
BASS JNI/native output and SQLDelight/TLS effects; behavior and transport selection remain common.
Normal profile settings, database and window-state hashes match their pre-test values.

### Visible Windows acceptance and performance investigation

Disposable profile: `build/windows-acceptance/profile`; loopback fixture only, muted playback,
audio caching and prefetch disabled. The installed app, saved accounts and normal profile are
excluded. Captures through Computer Use confirmed the About version/build and complete release
changelog, short and long track playback, pause/resume, buffered paused seek, moving text and clock,
menu interaction, minimize/restore and maximize/restore with clipping. Windows UIA exposed the
window shell but not Compose controls; this does not establish spoken accessibility acceptance.
After the shared transport fix, the rebuilt release payload visibly played, paused at 1:14, sought
to 5:24 while remaining paused, and resumed with the clock advancing to 5:32. A further seek near
the end completed with the track retained and play control available. This short fixture check is
not the physical eleven-track TV continuation acceptance. The fixture range responses were corrected
before the final visible test; the blocked-tail native integration independently verifies the seek
behavior without depending on that Python server.

Rendering observer measurements use an unchanged release payload with a disposable launcher that
loads the existing `ApplicationRenderProbe`; the provenance manifest records matching production
payload hashes. Automatic probe captures were disabled; independent Computer Use observations
confirmed movement. Main canvas 934×601, OPENGL, AMD Radeon Graphics driver 31.0.21923.11000,
2560×1440 display at 60 Hz, unchanged power plan. CPU percentages represent one core over ten seconds.

| State | Process CPU | Parent frames |
| --- | ---: | ---: |
| Short metadata, paused | 2.812%, repeat 4.843% | 0 |
| Short metadata, progress moving after warm-up | 6.249% | 0 |
| Long metadata, progress and marquee moving | 5.937% | 0 |
| Long metadata, paused marquee moving | 6.249% | 0 |
| Combined motion with settled menu open | 4.843% | 0 |
| Minimized during playback | 0.937% | 0 |
| Restored combined motion | 4.687% | 0 |

No-capture startup control still measured 4.374%. An uninstrumented, byte-identical release payload
with the same paused short metadata measured 6.236%; the elevated cost is not confined to the
observer. A Java thread CPU comparison attributed about 2.8% of one core to the native AWT Windows
event loop; it does not identify the native call responsible. No animation cadence was reduced and
no speculative native change was accepted. The trimmed runtime lacks JFR and Windows Performance
Recorder/Toolkit is unavailable in this environment. Native sampling remains needed.

Five-sample GPU observations measured app engine sum means of 0.000 (paused) and 0.046 (menu and
combined motion). Whole-desktop DWM CPU/GPU includes other applications and tool activity, so those
values cannot establish an application compositor budget. A later GPU counter collection returned
an invalid sample and was excluded. Raw logs, phase files, counters and Java thread snapshots live
under `build/windows-acceptance`. Zero parent frames alone does not pass the animation budget;
the visible CPU finding remains an open acceptance gate.

## Current release gates

- Hosted verification of stabilization commit `41642a2902f8d55e4bbadc0198e6adeb164f8248`
  (tree `10de5665ed2c6a0b1ca46e75ba92bab51c18004f`) passed all nine required checks in
  [Verify 40821716320](https://github.com/goosepod/naviamp/actions/runs/40821716320): shared/Android,
  Android emulator API 30/35, iOS simulator, desktop native Windows/macOS/Linux, classification and
  immutable verified-source recording. This completes the hosted matrix for that source, in addition
  to the local Windows results above. Physical-device checks remain distinct.
- Retest physical TV queue-end continuation when the owner resumes TV use. The paused living-room
  TV is not contacted during Windows stabilization.
- Complete physical iOS/TV, spoken accessibility and real cross-device cloud-folder propagation
  acceptance for the frozen scope; retain the narrower previously recorded Android/Mac/live results.
- Complete or explicitly retain platform-specific real-window/performance gates. Synthetic tests,
  geometry probes, visible screenshots and process CPU each establish different parts of acceptance.
- Preserve the store hold until the owner clears it. No store submission is replaced.
- Reconcile release-tag identity before publication. Existing annotated tag object
  `1a465962eb146fe0824b1de2bc92aad9291d2666` resolves to
  `6e602832abb7c3827481de3430a8593a3495fbca`, not the frozen baseline or stabilization source.
  `docs/development-workflow.md` requires existing tags to remain immutable. Keeping that rule
  requires a new version/tag; retaining the requested v2.10.0 name requires an explicit owner
  exception to replace the unpublished tag after exact-source verification. Neither is performed
  implicitly. Record the accepted final commit/tree and the owner's decision before either action.

Publication remains separate: exact release name `Naviamp v2.10.0`, verified release/About notes,
required Announcements discussion and non-forcing manual mirror. No release/tag is published here.

## October 29 follow-up and stop point

The superseded first workflow retained three running jobs after cancellation for more than ten
minutes and blocked the current run through concurrency. Force-canceling only that superseded run
released the queue. The current source then passed the complete matrix linked above.

Native diagnostic sampling used the process and thread identities of the disposable app only.
Its AWT Windows event-loop thread consumed 2.497% of one core in a fifteen-second diagnostic sample,
including 1.457% in kernel time. Most instruction snapshots were in `NtUserWaitMessage`; the active
native chains also included Windows UI Automation hooks. A plain Swing/JDK control with no Naviamp,
Compose, audio or raster presenter measured 2.494% whole-process CPU before Computer Use capture;
its event-loop sample also contained UI Automation hooks. This demonstrates an environment/runtime
contribution, not that all Naviamp CPU is explained or that animation acceptance passes. Native
stack sampling is diagnostic and changes scheduling; its CPU is not an acceptance measurement.

The plain-control UI inspection timed out awaiting Computer Use app approval. No permission bypass
was attempted. Control measurements without a verified unobscured window, the lower readings after
the desktop changed overnight, and a 96.597% sample interrupted by interactive navigation are not
accepted foreground performance evidence. The installed earlier JDK's 0.312% control sample is also
insufficient to establish a runtime regression because foreground conditions were not matched.

After the owner provided an idle desktop window, a disposable observer copy of the rebuilt release
payload repeated the fixed-size 934×601 OPENGL measurement. Motion and the visible menu were checked
through Computer Use. The short track remained paused at 0:07; progress advanced through 0:25;
the long title and clock moved through 0:22, 1:03 with the menu, and 2:00–2:36 after restoration.
No unrelated app overlay was present in those controlled captures.

| Controlled October 29 state | Process CPU / one core | Parent frames / 10s |
| --- | ---: | ---: |
| Paused long metadata, marquee moving | 7.499% | 0 |
| Paused short metadata | 3.437% | 0 |
| Progress with short metadata | 6.874% | 0 |
| Combined long metadata and progress | 4.843% | 0 |
| Menu open with combined motion | 4.687% | 0 |
| Minimized playback | 2.500% | 0 |
| Restored combined motion | 10.155% | 0 |

The settled menu surface also recorded zero frames. Its disposed surface later reported
`SkiaLayer isn't initialized yet`; that is not an additional active rendered surface. The first
marquee and restoration CPU readings are retained, with JIT timing unavailable in the trimmed
runtime; they are not silently discarded as warm-up. No CPU budget has been relaxed.

A separate five-sample collection, restricted to this application's GPU engine instances, recorded
mean summed utilization 0.035513 and no invalid counters. Whole-desktop DWM CPU averaged 27.116%;
this includes other applications and cannot certify Naviamp's compositor budget. Raw phase files
and counters are in `build/windows-acceptance/measurements-oct29`. The earlier native snapshots and
plain-control evidence are retained beside them. Reproducible diagnostic source is under
`scripts/animation-probe/windows-native-sampler`; it is outside the shipped application.

The performance gate remains open under #136/#386. No speculative product change, renderer switch,
animation suppression, playback-cadence change, OS accessibility change or runtime downgrade was
made. Native CPU attribution beyond the observed Windows hooks still needs a controlled native
CPU trace. ETW tooling and administrator access were unavailable in this session.

At the owner's request, testing stopped after this run. All disposable application/control and
fixture processes were closed, and the normal settings/database/window-state hashes still match
their original values. The tested product code is unchanged from the successful hosted source;
this follow-up adds diagnostic source and verification documentation only. No merge, tag, release
or store action was performed.
