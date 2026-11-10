# v2.11.0 stabilization campaign — November 10, 2026

Tracking: [#563](https://github.com/goosepod/naviamp/issues/563). The owner authorized work on
the remaining milestone using the connected Android phone, Windows computer, iOS simulators
and Linux VM. Successful controls below do not close the underlying investigations.

## Integration

- Repeat #558 merges at `71789f38ce58ca5f540cc766339fab75cb0d4d77` after full CI and the
  owner's real macOS acceptance. String audit #562 merges at
  `8bc75ad9f14b64750a1fa78c960908fecdf7d211` after full CI; its audit records 53 removed keys
  and 278 catalog entries.
- Cache #556 merges at `d281f2b0de3db72e0b3538a1a2eca4ac737c17db` after its current-main
  exact-head matrix. Tooltip #561's final refresh also passes the full matrix and merges at
  `bb2c3a5ebadf67fefa5d53da385edb9b185487ce`.
- Weblate #567 receives a focused German typo correction and passes translation validation before
  a regular merge at `0290c780880d0e32c1a5ad786a556a06deea6121`. The original translator commit
  remains an ancestor of main; no squash/rewrite of its ancestry is used.

The matched hosted cache benchmark
[42858817970](https://github.com/goosepod/naviamp/actions/runs/42858817970) completes in 20m32s
cold versus 12m11s warm, including archive overhead. All 2,512 common cases and 17 native cases
execute freshly in each stage. Scope, cache keys/sizes and limitations are audited in
[`512-ios-cache-population.md`](512-ios-cache-population.md); this is not a measurement of the
entire production Verify/Xcode/Keychain workload.

## Physical Android raster controls (#330)

Source is tooltip integration head `268e9bf39615b3cf26174ce42ad8a63e236984e4`; its native raster
implementation/probe are unchanged from accepted main `8bc75ad9f`. The Android phone reports API 37,
so it cannot establish an API 35 reproduction or correction.

Built `:core:ui:assembleDebugAndroidTest` with `-Pnaviamp.animationProbe=true` and a private init
script selecting temporary test ID `app.naviamp.ui.accept330.test`, absent before installation.
The committed application ID and production sources are unchanged. A fixed five-process campaign
executes `AndroidRasterPlacementTest` with `-e naviamp.raster.capture true`: **10 executions,
zero failures/errors/skips**. Both cases retain their original visibility deadlines and real
movement/replacement/placement/clipping/lifecycle assertions. Per-run native diagnostics/captures
are retained; final lifecycle and scrolling/reference images are visually inspected.

The installed APK hash matches the build before testing and before uninstall. The temporary
package is removed; every pre-existing Naviamp/test APK hash is unchanged. No owner profile or TV
is used. #330 remains open. Private evidence: `/private/tmp/naviamp-330-android-20261110`.
Reproduce with a fresh test ID, five fresh instrumentation processes stopping at the first failure,
and pull each run's `raster-diagnostics` before overwrite. Verify ownership before cleanup.

## Windows controls (#493)

Accepted main `8bc75ad9f14b64750a1fa78c960908fecdf7d211` is fetched into a fresh detached
checkout, preserving existing modified acceptance checkouts. The existing JDK 21/MSVC toolchain
builds and verifies the release distributable. An initial PowerShell launcher mishandles normal
CMake stderr; its incomplete log is retained. Redirecting the child through `cmd.exe` fixes only
the launcher. Up-to-date native outputs are not counted: `desktopTest --rerun` then freshly executes:

- `DesktopDiscoveryPreviewCoreIntegrationTest.releasePreviewCommandsPreserveTheRealCoreQueueAndSession`
- `DesktopPreviewPlaybackIntegrationTest.previewFinishesWhileMainRetainsItsPausedNativeStreamAndPosition`

Both pass, zero failures/errors/skips, with freshly timestamped XML. These use muted actual output
device clocks, not no-sound device 0; audible speakers and spoken accessibility are not established.

The unmodified `scripts/windows-initial-presentation.ps1` runs three fixed cold/warm pairs. All
**six untouched initial captures and six restores** pass native unoccluded/nonblank guards at
934×601 client pixels and 262 sampled colors. All 12 images have one SHA-256; an initial image
is inspected as readable connection UI. Initial capture precedes activation/input/resize/restore.

| Launch | Initial observation, ms | Five-second idle CPU, one core |
| --- | ---: | ---: |
| 1 cold | 8018 | 0.000% |
| 1 warm | 5358 | 0.000% |
| 2 cold | 5962 | 0.000% |
| 2 warm | 5397 | 0.000% |
| 3 cold | 6109 | 0.000% |
| 3 warm | 5347 | 0.312% |

Times include discovery and the existing three-second observation interval; they are not
first-pixel latency. CPU resolution is coarse; no parent-frame/GPU or animation-budget claim
follows. Empty disposable profiles do not recreate the original Aurral artist-page conditions.
The symptom does not reproduce, and #493 stays open. The probe closes every launched test process.

Evidence: `C:\Users\ursasmar\naviamp-stabilize-20261110`, including fresh XML and
`presentation-empty`; local capture bundle/hashes at `/private/tmp/naviamp-493-windows-20261110.zip`
and `/private/tmp/naviamp-493-windows-20261110`.

## Visible Mac increment (#342)

The existing isolated `build/Naviamp UI Review.app` has 87 application JARs and uses the private
synthetic long-track profile. Its track menu and Track details open; immediate Escape dismisses
details and returns to the full player, preserving paused 5:42. Visible title clipping and the
repeat-loop icon are inspected. Prior owner-confirmed tooltip, keyboard/VoiceOver and performance
evidence retains its original source/scope.

An initial action is refused after app state changes; the menu/details/Escape sequence is repeated
using fresh accessibility state and succeeds. A minimize attempt yields no decisive native
iconified-state observation: unchanged AX and an app screenshot do not establish a lifecycle pass.
The window is raised and the fixture remains paused. This increment uses the existing review
package, not every new mainline commit. No new all-state CPU/parent-frame/GPU, live-provider
audibility, exhaustive focus-return or spoken-accessibility pass is claimed. #342 and out-of-scope
visualizer draft #242 retain their remaining gates.

## XFCE/X11 Flatpak controls (#552)

The owner starts the VM, installs Flatpak/xdotool and unlocks its login keyring directly. No password
is sent to the agent. Environment: Ubuntu 26.04.1 LTS, XFCE session package 4.20.4-1, Flatpak 1.16.6,
1280×800 X11 desktop. This is virtual XFCE, not physical GNOME/KDE or hardware-GPU acceptance.
Skiko reports `Cannot create Linux GL context` and falls back; the app is visibly readable, with
no performance improvement claimed.

The public HTTPS `Naviamp.flatpakref` installs app 2.10.0 / Freedesktop 25.08 / commit
`d99a99cbf87e8acd2af3acda7a677f73add7bda1b72d94339fd3dad395522fcb` into the separate installation
selected by `FLATPAK_USER_DIR=~/naviamp-flatpak-552-20261110/installation`. Signature checking is
never disabled. An optional public `repository.json` fetch returns 404: that is an incorrect
probe URL, not an installer failure. The working installer supplies the signed remote/key.

Provider connection UI, Settings and Language render. Changing the disposable language preference
to English persists as `naviamp.interface.language=English` after normal close/reopen. Native
minimize reports `_NET_WM_STATE_HIDDEN`; the window is restored. Scoped MPRIS returns Stopped.
The existing `scripts/flatpak/audio-probe.c` runs inside the installed sandbox with generated FLAC
and muted real default output: `NATIVE_AUDIO_OK first=0.559 paused=0.740 resumed=1.231`.
It covers clock advance, settled pause, seek/resume and FLAC decoder loading. An isolated Secret
Service `test-only` item is written/read/cleared; no normal credential is changed.

### Failed metadata and recovery

After normal app close, all 19 profile files (initialized database/settings/caches) are hashed.
Only the isolated installation's remote is temporarily redirected to owned empty/offline and
malformed-summary directories. `remote-info` rejects both. In both cases `flatpak update` warns
that a fetch error is nonfatal for an installed app, prints `Nothing to do`, and exits **0**.
An exit code alone therefore does not prove an update or healthy remote.

Both controls preserve the exact installed commit and every closed-profile file hash. The public
HTTPS remote is restored, a normal update check succeeds, and the app reopens with English saved.
No database downgrade, private signing key or unsigned remote is involved. These failed-metadata
controls do not simulate interrupted payload transfer, a newer deployment or a signed bad candidate.

Private evidence: `~/naviamp-flatpak-552-20261110` on the VM. The isolated installation/reopened
app remain for continued tests. Physical GNOME/KDE, Wayland/XWayland, hardware GPU/device/media-key
behavior, portal download storage, and interrupted-transfer/new-version recovery remain open.

## iOS diagnosis and remaining gates

Accepted-main [Verify 42851514504](https://github.com/goosepod/naviamp/actions/runs/42851514504)
executes all 17 native cases, with one fixture-bootstrap timeout. Packet/JFR evidence captures
16.901212 seconds before the first server response, after native cancellation. TLS has not started.
The host-side response delay is established; the scheduling/paging/resource mechanism and durable
fix are not. See [`454-hosted-fixture-diagnosis.md`](454-hosted-fixture-diagnosis.md) and
[#564](https://github.com/goosepod/naviamp/pull/564). The bounded host-resource diagnostic
[42858007591](https://github.com/goosepod/naviamp/actions/runs/42858007591) passes its 17 native
cases, but the overall run is unsuccessful and is not full-matrix acceptance.

Candidate `32c0538afe36a21134ee66ee24bd0b2eb9914f1a` requires the exact owned fixture to answer
again after simulator boot, before timed native execution, within the existing setup budget.
It does not retry native assertions or extend the ten-second bootstrap or authentication deadlines.
Three fresh local suites pass the same complete 17-method binary inventory: **51 executions,
zero failures/errors/skips**, JFR disabled, all owned resources cleaned. Their post-boot readiness
checks take approximately 0.127s, 0.224s and 0.084s. This supplies local simulator evidence, not
proof of a hosted root-cause fix. Hosted exact-head
[Verify 42867932239](https://github.com/goosepod/naviamp/actions/runs/42867932239) passes the
complete matrix, including production iOS app and Keychain. Its initial native suite and two
cold replays execute the same complete 17-method inventory on three distinct simulators:
**51 executions, zero failures/errors/skips**, JFR disabled, both replay cleanup markers present.
Their binary SHA-256 is `44066855b05ebe2c04dd49bc90bb0337a5df344d75b4f0a5a6178762b6707e97`;
post-boot readiness takes approximately 0.125s, 0.659s and 2.804s. The reports are independently
audited after job completion. PR #564 merges at `8b030cce871063638580dee88188c7918e62d6aa`.
This accepts the concrete readiness lifecycle correction, not a deeper OS/JVM causal explanation.

Private local evidence: `/private/tmp/naviamp-454-candidate-cold-local-staged-20261110`.
An earlier sandbox-denied simulator lookup and missing-framework launch fail before native
inventory; the existing framework-staging task resolves the latter. Neither counts as a native
execution or passing suite.

There is no physical iOS device. Apple native evidence here is simulator evidence; physical iOS
runtime/audio/touch/performance acceptance is not inferred. The owner subsequently decides that
unresolved #330, #342, #493 and #552 must not hold v2.11.0. They remain open, with their original
criteria/evidence, as nonblocking follow-ups outside the release milestone. Native assertions and
the final full release matrix remain enforced. Tooltip/cache integration and measured cache benefit
are accepted, as is #454's post-boot readiness correction. The versioned release candidate still
requires its own complete verification and packaging gates.

No platform production source is modified by this campaign's new probes/documentation. The
tooltip branch's pre-existing JVM adapter only translates Core-supplied size/offset into AWT
owned-window bounds before show; measurement, placement and timing remain common.

## Flatpak release availability

The owner confirms that the original Ubuntu signing environment is on a different, currently
unavailable Windows machine. The tested public signed package remains v2.10.0. Its v2.11.0
update is tracked separately in [#569](https://github.com/goosepod/naviamp/issues/569), preserving
the existing trust identity and remote; no replacement key or signature bypass is introduced.
The app release/direct Linux packages continue with this limitation clearly stated in release
notes, the shared About changelog and plain announcements.
