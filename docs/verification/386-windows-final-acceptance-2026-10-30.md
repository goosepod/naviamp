# Current-source Windows acceptance — October 30, 2026

Tracking: [#386](https://github.com/goosepod/naviamp/issues/386),
[#408](https://github.com/goosepod/naviamp/issues/408), release acceptance #280.

**Disposition: automated verification passes; visible Windows and real-output acceptance remain
open.** No production implementation, animation cadence, test assertion or deadline is changed.
This report does not certify Windows performance or authorize release publication.

## Exact source and environment

- Main `91d49ef56c740054aa781bde958228930fd40463`, tree
  `ecc056a72d408bf9dd36d36a56e7a4a13baa750d`, v2.10.0/build 60. This is also the source tree of
  release commit `ccef381040dab003525e3b89de654af78c9ac32a` accepted through PR #426.
- Worktree `.worktrees/windows-final-2100`, branch `test/386-windows-final-acceptance`.
- Windows 11 Home, build 26300; AMD Radeon Graphics driver `31.0.21923.11000`,
  2560×1440 at 60 Hz, unchanged `My Custom Plan 1` power scheme.
- Installed Temurin JDK **21.0.13+9**, MSVC 19.44.35229, Windows SDK 10.0.26100.0, CMake 3.22.1,
  Python 3.14.7. The historical JDK 21.0.12 directory no longer contains Java. The first invocation
  fails before Gradle starts; its log is retained. The successful build uses the installed JDK.
  These are not matched-runtime comparisons with the older Windows measurements.

The normal installed app/profile is excluded. A copied fixture profile is accepted only after a
read-only SQLite check establishes exactly one source with base URL `http://127.0.0.1:18080`.
Sync is disabled and playback volume is zero in that disposable profile. No real provider account,
cloud folder or store submission is used.

## Automated build and regression results

The complete run succeeds in **14m24s**, all 149 tasks executed:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot'
$env:CMAKE_EXE = 'C:/Users/ursasmar/AppData/Local/Android/Sdk/cmake/3.22.1/bin/cmake.exe'
./gradlew.bat -Pkotlin.native.enableKlibsCrossCompilation=false `
  -Pnaviamp.bass.platform=windows-x64 -Pnaviamp.bass.test.outputDevice=0 `
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
| Core UI | 624 | 0 | 0 |
| Core presentation | 537 | 0 | 0 |
| Core testkit | 6 | 0 | 0 |
| Navidrome | 252 | 0 | 0 |
| Jellyfin | 35 | 0 | 0 |
| Desktop platform | 65 | 0 | 3 |
| Desktop host | 8 | 0 | 0 |
| Total | 2,949 | 0 | 3 |

The skips are two Linux MPRIS cases and the real-output preview case, which deliberately excludes
the no-output test device. Skips are not passes. Optional unconfigured live-provider entry points
do not establish live provider acceptance. Four real HTTP range-response tests and the Windows
process-ownership regressions also pass. Core architecture, shared Android compilation, package
inputs/native resources and distributable verification pass. Windows cannot compile the Apple
targets locally; PR #426's separate complete hosted matrix remains the cross-platform evidence.

XML reports are copied before the targeted native reruns overwrite their normal Gradle result
directory. Aggregate counts and exact skip names are retained in
`build/windows-final-acceptance/complete-suite-summary.json`.

## Packaged artifact and observer integrity

Archive: `apps/desktop/build/compose/distributions/Naviamp-v2.10.0-windows-x64-release.zip`.

SHA-256: `187A535DCA44C0996DFA8CFC9596F72CEC8E74C0887469083223120688159305`.

The canonical payload has 271 files. A disposable observer copy preserves the hashes of all 270
production files other than `app/Naviamp.cfg`; that configuration changes only its main entry point,
the extra observer JAR classpath and probe options. The main renderer remains OPENGL and future
popup renderer SOFTWARE_FAST. No renderer override, runtime replacement or cadence adjustment is
introduced. The observer is prepared but is not accepted as a functioning visible measurement.

The ordinary, unchanged release launcher is started separately with APPDATA/LOCALAPPDATA directed
to the fixture profile. The loopback server receives normal connection/catalog requests, including
ping, extensions, folders, album lists, playlists and album lookup. No stream request occurs in
this check. Network requests alone do not prove the UI paints or playback works.

## Real-output preview failure and audio environment

The separately requested native case fails with a ten-second timeout at the **initial main-stream
progress check**, before Pause or preview is invoked:

```powershell
./gradlew.bat -Pkotlin.native.enableKlibsCrossCompilation=false `
  -Pnaviamp.bass.platform=windows-x64 -Pnaviamp.bass.test.outputDevice=-1 `
  :platforms:desktop:desktopTest `
  --tests app.naviamp.desktop.playback.bass.DesktopPreviewPlaybackIntegrationTest
```

Temporary, test-only diagnostics preserve every assertion and deadline. They establish shared
state `Error(message=BASS_Init failed: driver error, reason=null)`, unknown progress and no native
stream. BASS and BASSmix load, and the codec plugins load. The backend enumerates only a generic
Default output, not an initialized physical endpoint. The current-thread error code queried later
is not interpreted as the original failing call's error.

Read-only Windows checks find AudioEndpointBuilder and Audiosrv running, and both the test shell
and Explorer in session 1. PnP's AudioEndpoint class contains only `Microphone Array (AMD Audio
Device)`, with no output endpoint. The owner is asked whether an output is connected and working.
No service, output device, driver, system volume or Windows setting is changed.

The initial failure and diagnostic repetitions are retained; none is a preview or paused-position
pass. This establishes unavailable native output in this test environment, not a confirmed regression
in preview/pause behavior. Temporary diagnostic source changes are reverted after their patch and
results are saved locally. There is no production fix or weakened test.

## Rejected visible probe and ordinary-window observations

The two-cycle component/menu/lifecycle probe retains pixel verification:
`NAVIAMP_PROBE_INTEGRATED=true`, `NAVIAMP_PROBE_VERIFY=true`, `NAVIAMP_PROBE_POPUPS=true`,
`NAVIAMP_PROBE_MENUS=true`, `NAVIAMP_PROBE_LIFECYCLE=true`, `NAVIAMP_PROBE_CYCLES=2`,
task `:core:ui:playerAnimationProbe`.

The first setup also supplies `NAVIAMP_PROBE_ACTIVATE=true`. Java Desktop rejects
APP_REQUEST_FOREGROUND on Windows before measurement; this is a setup error. Removing only that
unsupported option leaves pixel/motion/lifecycle assertions intact. The corrected invocation then
rejects its first visibility check: `marker=8d8d8d expected=775533 active=true`. Neither invocation
supplies an accepted CPU phase, visible-motion result or lifecycle matrix.

Computer Use selects the exact ordinary test executable/window. Two captures show a gray client
surface with the native title bar, rather than usable application content. UI Automation exposes
only the window shell. After an attempted maximize, the tool rejects input because the point belongs
to `PickerHost.exe`, not Naviamp. Explicit target activation, fresh observation and the permitted
single retry produce the same rejection. Windows identifies `C:/Windows/System32/PickerHost.Exe`;
it is not exposed as a targetable app window. The owner is asked to clear any open system picker.
No picker interaction, permission action or unrelated process termination is attempted.

These observations do not distinguish an actual application paint defect from an obstructed
capture/desktop environment. The older package comparison, keyboard/focus, restored paused seeks,
normal-close persistence, resize/clipping, menus and visible playback retests remain unperformed.
Gray/blank content is not accepted as a low-cost surface. Earlier valid Windows acceptance keeps
its original source/runtime scope and is not relabeled as acceptance of this artifact.

## Scoped graphics-capture diagnostic

The current Windows account already has enabled Performance Log Users membership. Intel's signed
standalone PresentMon 2.3.1 console executable is downloaded from its official GitHub release to the
ignored test tools directory. Signature verification is valid, signer Intel Corporation; SHA-256
`364E5D98D4D134BD54DD25C22ED2CA2F4883F8BC3ED6502BEE0C151E3436D30C`.
No service, driver, group membership, elevation or installed software configuration is changed.

A ten-second capture uses the verified disposable child PID, a unique session name,
`--no_track_input --no_console_stats --timed 10 --terminate_after_timed` and a local output path.
It starts and stops successfully. The tool warns that elevated privilege would be required to
identify some other-account/short-lived processes; those are not the selected target. It produces
no frame CSV for this gray-window interval. **No frame records is not zero GPU/compositor cost.**
This checks the capture path only; it is not performance acceptance or an isolated attribution of
Naviamp's cached-content transforms. Computer Use is reset before the interval, ending its helper.

Reproduction references: [PresentMon console options](https://github.com/GameTechDev/PresentMon/blob/v2.3.1/README-ConsoleApplication.md),
[observer procedure](../../scripts/animation-probe/README-visible-app.md), and
[prior UI Automation attribution](386-windows-uia-attribution-2026-10-29.md).

## Remaining work

Clear the blocking system picker and provide a working output endpoint before retrying dependent
Windows checks. Reconfirm real paint and moving pixels first, then repeat static, individual,
combined, overlay and lifecycle CPU/parent-frame/compositor measurements under recorded matching
conditions. Retain outliers and establish GPU attribution; neither prior measurements nor this
diagnostic closes the budget. Complete actual keyboard/focus/accessibility and packaged playback
reproductions. Release-wide hardware, store and immutable-tag gates remain unchanged.

Logs, XML, source/payload manifests, synthetic profile and trace outputs stay in ignored
`build/windows-final-acceptance`. No raw credentials, native traces or private profile content is
uploaded. Cleanup closes only the identity-verified disposable child application, its automatically
exiting launcher and the task-owned loopback fixture. The app is stopped as a process after blocked
native input; this is explicitly not graceful-close/persistence acceptance. The system picker and
other applications are untouched. The observation helper is released. Normal settings, SQLite
database and window-state SHA-256 hashes all match their three pre-test values. The synthetic
profile, archive and raw evidence remain local for reproduction.
