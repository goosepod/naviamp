# Windows Store MSIX packaging acceptance — issue 323

Date: 2026-10-23. Tracking: [#323](https://github.com/goosepod/naviamp/issues/323).

## Input and package

- Published input: `Naviamp-v2.9.0-windows-x64-release.zip` from GitHub release `v2.9.0`.
- Release commit: `826e14bf1c46dba8d604515f1bf77bf6afcd541d`.
- Input SHA-256: `86907877c7bb6b34438bb43737aeea083dbc871dee7a6358c2b0dee13f4f4381`.
- Output: `Naviamp-v2.9.0-windows-x64-store.msix` (unsigned Store upload candidate).
- Output SHA-256: `ebb956cdeab37a63a662f510236383e5011db665e34b6dcdfc50088ab4874064`.
- Identity: `Goosepod.Naviamp`, version `2.9.57.0`, x64, publisher matching Partner Center.

The package contains all **268** released application files without changes, plus
the MSIX manifest, three existing-brand PNG derivatives, and MakeAppx's generated
block map/content-type metadata. No application rebuild or runtime patch occurred.
Repackaging can change the outer MSIX checksum; always record the exact tested output.

## Completed checks

| Check | Result |
| --- | --- |
| Published release ZIP checksum | Passed |
| Packaging unit tests on macOS / Python 3.15 | 8 passed |
| Packaging unit tests on Windows / Python 3.12.10 | 8 passed |
| Launcher, JVM and BASS x64 PE checks | Passed |
| MakeAppx schema and semantic validation, no `/nv` | Passed |
| Compare every MSIX application file with release ZIP | 268/268 matched |
| Re-run content verifier on retrieved package on macOS | 268/268 matched |
| Reject changed input, changed/extra app payload and wrong publisher | Passed |
| Reject unsafe paths and Windows filename normalization/collisions | Passed |
| PNG dimensions (50, 44 and 150 pixels square) | Passed |
| Install in logged-in Windows session; package registration/status | Passed |
| Start-menu app name and application identity | Naviamp / expected AUMID |
| Installed payload compared with release ZIP | 268/268 matched |
| Installed MSIX connects to Navidrome demo and plays music | Passed, owner manual test |
| JVM, Skiko, BASS and visualizer modules load from installed package | Passed, process module audit |
| Uninstall and remove temporary certificate/private key/test copy | Passed |
| Preserve existing standalone data during uninstall | 7/7 files retained with matching SHA-256 |
| Retest: saved server login after close/reopen | Passed, owner manual report |
| Retest: pause/resume, seeking and keyboard media keys | Passed, owner manual report |
| Retest: playback while minimized | Passed, owner manual report |
| Retest: downloaded track playback offline | Passed, owner manual report |

Windows build environment: x64, OS build `10.0.26300.0`. Used Microsoft NuGet
`Microsoft.Windows.SDK.BuildTools` **10.0.26100.7463**, x64 MakeAppx from its
`bin/10.0.26100.0/x64` directory. Its Authenticode signature was verified valid
and issued to Microsoft Corporation before execution. Python was the official
portable Python 3.12.10 distribution. Tools lived in an isolated build directory;
no SDK or Python system installation was needed. PowerShell execution policy was
overridden only for the build process; machine policy was unchanged.

The candidate, `makeappx.log`, `provenance.json`, local verification report and
`SHA256SUMS` are retained under the issue worktree's `build/windows-store/final`.
The Windows build also retains them under `naviamp-msix-323/output-final` in the
build user's profile. CI retains corresponding artifacts on subsequent builds.

## Outstanding gates

**Installed-MSIX connection and playback passed the owner's manual test. WACK
and Microsoft Store certification remain outstanding.**
Developer Mode was not enabled on the available Windows host during packaging.

### Authorized local signing and deployment attempt

After the project owner explicitly approved temporary test-certificate trust,
tested the final CI candidate from
[Windows Store package run 39975612140](https://github.com/goosepod/naviamp/actions/runs/39975612140).
Its unsigned SHA-256 is
`c71b45f8cd3fa8445bbcb38bd95ca27c287a1eed664bf53b7685c0216f5d4fd8`.
All 268 payload files also passed independent verification after download to macOS.

- Preflight: no existing `Goosepod.Naviamp` Store registration or Naviamp process.
- Created a one-day non-exportable code-signing key and public certificate.
- Signed a separate `LOCAL-TEST.msix`; the unsigned CI candidate remained unchanged.
- Trusted only that public certificate in machine `TrustedPeople`, with user approval.
- SignTool signature verification passed. Windows AppX also validated its signature.
- `Add-AppxPackage` through SSH failed with `0x80070005`; deployment log event 718
  reported "Failed to initialize PLM" and event 605 identified the
  `PackagesInUseClosed` state handler. Installation acceptance therefore did not pass.
- Failure cleanup completed: no Store registration remained; the signed test copy,
  machine trust entry, personal certificate and private signing key were removed.
  Independent queries confirmed both certificate-store entries were absent.

The owner requested retrying through Moonlight's Desktop connection to Sunshine.
The computer-control service failed before reaching any native app with
`Sky Computer Use native pipe startup failed`, including after resetting the tool
and directly selecting Moonlight. The agent could not drive the desktop UI;
the owner subsequently performed the application checks manually.

### Successful installed-app acceptance

The owner's first test opened the standalone launcher from the prepared layout;
process paths and the absence of an AppX registration distinguished that test
from an MSIX installation. It confirmed connection, controls and music playback
against the Navidrome demo using the released application payload.

Retried installation in the already logged-in Windows desktop session using a
one-time task with the existing interactive user token. No account, password or
Developer Mode change was needed. The approved test-signing lifecycle completed:

- Windows registered `Goosepod.Naviamp_2.9.57.0_x64__fc7t29wmhhnwe`, status `Ok`.
- Start registered **Naviamp**, AUMID `Goosepod.Naviamp_fc7t29wmhhnwe!Naviamp`.
- Every installed application file matched the published v2.9.0 ZIP (268/268).
- The owner closed the standalone launcher and opened the installed Start-menu
  app, then reported: "Installed Naviamp connects and plays" using the Navidrome demo.
- Independently verified both running launcher/JVM process paths were within
  `C:\Program Files\WindowsApps\Goosepod.Naviamp_2.9.57.0_x64__fc7t29wmhhnwe`.
- The JVM process loaded its bundled `jvm.dll`, Skiko, `bass.dll`, `naviamp_bass.dll`,
  BASS add-ons and `naviamp_visualizer_opengl.dll` from that installed package.
  Loading the visualizer module does not establish an animation performance result.
- The unsigned CI upload candidate retained SHA-256
  `c71b45f8cd3fa8445bbcb38bd95ca27c287a1eed664bf53b7685c0216f5d4fd8`.

After the manual check, stopped only the installed test app and ran cleanup in
the interactive session. Uninstall succeeded; independent queries confirmed the
package registration, temporary TrustedPeople entry, personal certificate/private
key and signed test copy were absent. Both one-time tasks were removed. All seven
existing standalone data files under `%APPDATA%/Naviamp` remained byte-identical
across uninstall, including the database, settings, DPAPI key, window state and
three cached audio files. No credential contents were printed. This does not
establish complete data isolation, migration or upgrade preservation.

Install, runtime audit and cleanup reports are retained on the Windows host under
`naviamp-msix-323`: `install-interactive-result.json`,
`installed-payload-provenance.json`, `runtime-audit.json` and
`cleanup-interactive-result.json`. `test-windows-store-install.ps1` provides the
reproducible explicit signing/install/cleanup lifecycle.

Follow the installed-app matrix in
[`windows-store-distribution.md`](../windows-store-distribution.md): native audio,
credentials/relaunch, server connections/offline behavior, downloads, media keys,
background playback, accessibility, update, coexistence/data paths and uninstall.
### Follow-up manual retest and rendering blocker

Rendering investigation: [#325](https://github.com/goosepod/naviamp/issues/325).

Reinstalled the same verified CI candidate using the approved test-signing lifecycle
in the logged-in Windows session. Registration again reported `Ok`, and the one-time
installation task was removed. Its active test state is
`naviamp-msix-323/install-retest-20261023/test-install-state.json`; cleanup remains
required when rendering diagnosis is finished.

The owner reported that all four requested additional tests passed: saved login
after close/reopen, pause/resume/seeking/media keys, playback while minimized, and
offline playback of a downloaded track. These are user-reported functional results,
not separate agent-observed UI or credential-encryption assertions.

The same retest reported two rendering failures over Moonlight/Sunshine:

- Opening a popup leaves the popup visible while the rest of Naviamp turns black.
- Hover labels have a solid black rectangle extending approximately ten pixels
  beyond the visible label in each direction.

Do not treat this as a passed rendering result or dismiss it as a streaming artifact.
The owner subsequently reproduced both defects on the physical Windows display
with Moonlight disconnected, and in the unchanged standalone v2.9.0 application.
Neither streaming nor MSIX packaging is required to reproduce these failures.
The installed launcher explicitly selects Skiko `OPENGL`; the host reports AMD
Radeon Graphics driver `32.0.21042.62` and NVIDIA GeForce RTX 5060 Ti driver
`32.0.16.1742`, with the NVIDIA display mode reported as 3840 x 2160. This records
the environment; it does not identify which GPU owns each surface or a root cause.

Core popup policy lives in `NaviampPopupSurface.kt`; desktop popup presentation
uses owned windows through `compose.layers.type=WINDOW`. The black regions are
consistent with lost alpha compositing, but that remains a hypothesis. No released
application JARs, renderer settings or native binaries were changed during diagnosis.

Connection, playback controls, relaunch login, offline playback, native loading and
uninstall now have the evidence above. Popup/tooltip rendering, accessibility,
upgrade/coexistence, WACK and Store submission preparation remain open gates.
Temporary test signing/trust remains authorized for follow-up acceptance, with
cleanup required after each test lifecycle.

Partner Center listing, screenshots, privacy/support links, age rating, reviewer
access and restricted-capability explanation remain part of submission preparation.
Nothing has been uploaded to Partner Center or submitted for certification by this
packaging work. Keep issue 323 and its PR open until acceptance is complete.

### Controlled comparisons — 2026-10-23

The owner reproduced both defects directly on the physical Windows display, with
Moonlight disconnected. The unchanged standalone v2.9.0 application also reproduced both
defects using its normal OpenGL renderer. Streaming and MSIX packaging are therefore not
required to reproduce this problem.

A child-process-only Direct3D diagnostic crashed before visual acceptance. Its JVM
fatal-error report identifies EXCEPTION_ACCESS_VIOLATION in
`skiko-windows-x64.dll+0x6a8cb` on AWT-EventQueue-0, with
PictureRecorderKt._nFinishRecordingAsPicture in the Java stack. Direct3D is not an
accepted workaround.

The same released standalone application launched with child-process-only options
`-Dskiko.renderApi=OPENGL -Dcompose.layers.type=COMPONENT` passed the owner's visual
comparison: “The currently open app works as expected, no black boxes.” Independently
verified both Naviamp process paths, stderr recording the selected flags, and all 268
payload files against the released archive byte-for-byte. No application JAR, launcher
configuration, or native library was patched.

This isolates a useful difference between owned transparent popup windows and popups
composed within the parent window. It does not establish the exact driver/framework root
cause or prove COMPONENT is an acceptable production fix. Shared raster behavior
currently falls back from native presentation when popups are not in owned windows;
continuous animation cost, stacking, input, accessibility and lifecycle behavior must be
checked before adopting a change. Rendering acceptance remains open; no Store submission
has been made.

Initial read-only counter sample in COMPONENT mode, after the owner confirmed the
window was visible, playback paused and popups closed: 16.326 seconds, 15 GPU samples,
application CPU 0.861% of one core, DWM CPU 3.385% of one core, application GPU engine
sum mean 0 and DWM GPU engine sum mean 5.983. Power plan reported Balanced. The
existing `scripts/animation-probe/measure-windows.ps1` sampled both launcher PIDs.
Raw evidence: `naviamp-msix-323/component-paused-no-popup-counters.json` on the host.
These counters are preliminary: no parent-frame observer was attached, motion and
window geometry were not independently verified, GPU engine sums are not total GPU
percentages, and this is not continuous-animation performance acceptance.

Matched follow-up with playback still paused and a previously affected popup held
open by the owner: 15.847 seconds, 15 GPU samples, application CPU 0% of one core at
counter resolution, DWM CPU 0.307% of one core, application GPU engine sum mean 0 and
DWM GPU engine sum mean 1.091. Raw evidence:
`naviamp-msix-323/component-paused-popup-open-counters.json` on the host. This sample
shows no measured sustained application CPU cost in this paused popup state; it
does not demonstrate animation motion, parent-surface frame counts, or performance
with playback/marquee animation running.

Playback resumed, popup closed, owner reported ready: 15.804 seconds, 15 GPU samples,
application CPU 2.175% of one core, DWM CPU 3.180% of one core, application GPU engine
sum mean 0.027 and DWM GPU engine sum mean 2.543. Raw evidence:
`naviamp-msix-323/component-playing-no-popup-counters.json` on the host. Whether a
marquee was active was not confirmed, so this is not an isolated progress-only or
combined-animation acceptance sample. Parent-surface frame counts remain unmeasured.

## Architecture review

No Android/Desktop/iOS production source file changed. The changes are build
scripts, a separate CI packaging workflow, Windows package metadata/assets,
packaging tests and documentation. Shared application behavior is unchanged.
