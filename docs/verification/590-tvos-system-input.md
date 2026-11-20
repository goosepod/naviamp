# tvOS system Home and foreground transport (#590)

This extends [remote/text/recovery acceptance](590-tvos-remote-recovery.md) under issue 590 and
draft PR 606. The owner's simulator acceptance decision remains in force: physical Apple TV
testing is optional, and hardware-only behavior stays explicitly unverified.

## Shared ownership

The existing renderer translates UIKit Play/Pause into Compose `Key.MediaPlayPause`. Product
handling belongs in Core, rather than a second player action in the Swift host:

- `NaviampTelevisionPlaybackKeys.kt` bubbles after focused controls and dispatches the existing
  shared `TogglePlayPause` action only when playback is available. Repeated downs and release do
  not toggle again. Window focus loss clears a pending hold; inactive windows do not dispatch.
- `NaviampTelevisionAppShell.kt` mounts that shared handler for the Television surface.
- `NaviampTelevisionContent.kt` keeps an initial transport-focus request pending while its button
  is disabled during loading. Once enabled, it requests keyboard input mode before requesting
  focus. After successful initial focus, later enable/disable transitions do not steal focus
  from another control. This is the same common input-mode policy used by TV navigation.

Five rendered regressions cover held input across playback-state changes, unavailable/absent
playback, focused-control precedence, focus loss without release, and touch-mode loading-to-ready
focus with subsequent focus preservation. The native test exposed conditions that a desktop
window's default keyboard mode did not establish.

There are **no changed platform production files** in this checkpoint. Swift changes are native
UI tests, not product behavior. No new setting, schema, translation, native library, Compose-port
artifact or animation cadence is introduced. Android's runtime minimum remains API 26.

## Actual app sequence

The separate `NaviampTVSceneTests` scheme still creates both synthetic accounts through the real
system keyboard and remote, relaunches, and deletes only the terminated owned app's cache to
exercise encrypted main/Aurral recovery. The runner still verifies database absence before the
recovery launch, preserved encrypted preferences, rebuilt SQL and fresh authenticated content.

The extended case also:

1. Sends `XCUIRemote.home` after initial setup and after purge recovery. It requires an actual
   background/suspended process state, activates the app again, requires foreground state and
   checks the retained discovery page/account and an empty player. An app hierarchy alone is not
   treated as evidence that Home worked.
2. Relaunches once more with the recovered cache intact, using the documented launch focus as
   the anchor for remote-only main-mix selection. This additional process is inside the same
   XCTest session and the final recovery telemetry stage; it does not reinstall or seed the app.
3. Starts native BASS playback of a finite authenticated WAV, verifies forward clock movement,
   pauses and checks a stable clock, resumes, and verifies a 1.2-second held media press toggles
   only once.
4. Backgrounds the paused player with Home, returns and verifies the clock remains paused. The
   first remote input after return is Play/Pause, with no directional-focus workaround. The clock
   must advance again. Directional input reveals auto-hidden controls only after that command;
   their visibility is not used as the sole proof of playback state. The test ends paused.

The fixture serves a real 180-second mono 16-bit/16 kHz PCM/WAV. Its samples are silent for
unattended testing: this establishes decoder/transport/clock behavior, not audibility. Stream and
download routes require the main account's salted authentication and the expected song ID.
Single byte ranges, suffix ranges, exact lengths and 416 responses are tested. The companion
retains its separate password/bearer checks. Seven fixture regressions pass. Telemetry keeps
route names/authentication results only; no credentials, tokens or authentication query strings.

## Verification ledger

The final complete run is owned `build/590-system-media-1115-v5` on tvOS Simulator 27.2.
The actual app case passes in **135.473 seconds**, with one executed test and no failures/skips;
the test command takes 168.708 seconds including Xcode orchestration. The 269.967-second
build-for-testing step includes the changed shared UI/framework, with warm compiler/dependency
caches. It is not an unchanged-build or cold-machine benchmark. The existing two-worker/4 GiB
heap limits and 15,641-byte exported header remain; no new runtime-memory/performance claim is made.

| Verification | Result |
| --- | --- |
| Complete shared JVM UI suite | 789 pass, no failures/errors/skips; includes five new regressions |
| Android common UI compilation and Core-first architecture | Pass |
| Original iOS device/simulator common UI compilation | Both pass |
| TV device UI and host compilation | Pass; device execution is not required by the owner's acceptance policy |
| Authentication/audio/range/ownership fixture tests | Seven pass |
| Native keyboard, main/Aurral recovery, Home/return and foreground transport case | Pass together |
| Pre-launch SQL projection: create/relaunch/purged | Absent / present / absent |
| Pre-launch recovery record: create/relaunch/purged | Absent / present / present |
| Stage requests: create/relaunch/purged | 43 / 31 / 80; zero rejected authentications; eight authenticated stream requests in the final stage |
| Cache removal | Database/WAL/SHM only; 2,734-byte encrypted preference record preserved byte-identically |
| Final recovery probe | Rebuilt database and recovery record both present before test-manager cleanup |
| Source identities | All 19 app/changed-input hashes match; an additional 616-file shared/native runtime source audit matches in both directions |
| Owned simulator cleanup | Shutdown/delete both zero; the pre-existing iOS simulator and Mac user checkout are untouched |

The encrypted record's purge-time SHA-256 is
`36e41d330e23180e35260a7c113ec015bbf16b59916483fabeccf9326fa1e5d4`.
The final paused-player and recovered-Aurral screenshots are visually inspected.

Local evidence is under `build/590-feasibility`: `tvos-system-input-{summary,attachments}.json`,
`tvos-media-runtime-sources.json`, `tvos-system-{home-return-paused,purged-aurral}.png`,
`tvos-media-final-common-v2.log` and `tvos-media-fixture-tests.log`. Native compilation logs are
`build/590-system-home-1115-v1/{ios,tvos}-media-final-v2-compile.log` on the Mac.
Reproduce with the unchanged invocation/manifest arguments in the
[remote app runner instructions](590-tvos-remote-recovery.md#app-level-acceptance-harness), choosing
a new owned output directory. The expanded runner requires an authenticated final-stage stream
request and retains the additional shared player source identities; it never builds Compose sources.

The preceding documentation checkpoint `49a7ea800` passes complete
[Verify 906](https://github.com/goosepod/naviamp/actions/runs/43786077461),
[public BASS](https://github.com/goosepod/naviamp/actions/runs/43786077478) and
[Flatpak](https://github.com/goosepod/naviamp/actions/runs/43786077489). It includes original iOS,
all Desktop targets, Android API 26/30/35, shared tests, coverage and verified-source recording.

Owned diagnostic outputs remain in `build/590-system-home-1115-v1` and
`build/590-system-media-1115-v*`. The initial Home-only sequence passes. Earlier playback probes
are not counted as complete acceptance:

- The first guessed post-discovery focus sequence selected the companion tile instead of the
  main mix. The final playback step uses a fresh launch, rather than depending on that guess.
- A loading player requested focus only once while disabled. A native directional-focus
  diagnostic made Play/Pause and held input work, but is not an accepted workaround.
- The initial loading-focus retry alone still failed on the simulator. The native port starts
  its input manager in touch mode; the final common request explicitly enters keyboard mode.
  The common touch-mode regression fails before that transition is added.
- The diagnostic's post-Home button lookup also failed when listening mode hid the controls,
  despite a moving playback clock. The final assertions check the clock and reveal controls
  explicitly. A hidden button does not establish a failed media command.
- An early common test incorrectly asserted a missing focus property on a disabled button;
  that fixture assertion was corrected to check the disabled state. It is not reported as a
  reproduced production failure.

## Remaining work

Foreground remote input and idle/paused Home transitions are separate from OS MediaPlayer
integration, uninterrupted background playback, audio interruptions, output changes and system
Now Playing publication. Those still need native simulator integration and app-level tests.

Reuse the existing common external-playback bridge, lifecycle coordinator and publication planner.
Extract portable artwork request/cancellation/stale-result ownership from the existing iOS media
adapter into Core before sharing native effects. Keep MediaPlayer/UIKit/AVAudioSession translations
in a small shared Apple adapter, separate from the headless BASS foundation so that its CI lane
does not acquire a Compose UI dependency. Preserve the iOS-facing API through delegation and
compile/test original targets before TV mounting. Advertise capabilities only after mounting and
testing those effects; no second TV lifecycle controller or provider implementation is permitted.
Attach/dispose media effects through the common recoverable-host lifetime so retries and recovery
failures cannot leave duplicate observers/command targets. The native adapter releases only its OS
resources; Core chooses when to attach, retry and detach, including ordering before audio/database
teardown. Test startup failure, reload, cancellation and late callbacks before host wiring.

Legacy held Menu is a separate system gesture; `.home` acceptance does not claim it. Older-runtime
compatibility, simulator accessibility, authenticated TLS streaming, broader certificate/settings/
error/reset GUI recovery, discovery/pairing, trusted port delivery/hosted scene CI and signing/
distribution remain under #590. Existing original-platform performance/restore/accessibility gates
remain. Physical remote feel, HDMI routes/audibility and hardware performance remain unverified,
nonblocking limitations. PR 606 stays draft.

The subsequent [shared Apple media checkpoint](590-apple-system-media.md) closes native Now
Playing publication, background playback/system Pause, held Menu return and Control Center
resume/pause on the accepted simulator. It records remaining interruption/route and broader
transport gates separately; the results above remain evidence for this earlier source.

## API references

- [Apple XCUIRemote](https://developer.apple.com/documentation/xcuiautomation/xcuiremote)
- [Apple application state](https://developer.apple.com/documentation/xcuiautomation/xcuiapplication/state-swift.enum)
