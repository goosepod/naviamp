# Android TV Follow-up

This document tracks issues and product improvements found while testing the Android TV work on a
physical Android 14 TV device. Unless explicitly marked complete, these are requirements for later
work rather than implementations on the current branch.

The [TV preview checklist](android-tv-plan.md#android-tv-preview-release-gates) remains the release
exit checklist. This file records the physical-device findings and their implementation status.

## Initial Connect setup

Status: implemented and validated on the Android TV emulator

Explicitly displaying a pairing code authorizes the code-authenticated peer to pair and configure
an empty target within that live session. Source and portable settings are validated and applied
without another TV approval. Existing-source replacement still requires approval. The revised
[protocol consent boundary](naviamp-connect-protocol.md#protected-assets-and-trust-boundary) records
expiration, cancellation, compatibility, and the separation from trusted reconnect.

The first connection should be a simple pairing flow:

1. The TV displays a pairing code.
2. The user selects the TV and enters that code on the controlling Naviamp device.
3. Trust, source credential transfer, and the initial connection complete automatically.
4. The TV is ready to receive playback without another credential or approval step.

When the controller already has a reusable source credential, Naviamp must not ask the user to
re-enter it or report that it is unavailable for secure transfer. If no reusable credential exists,
the controller may request it once as part of pairing, explain why it is needed, and retain it
securely for future connections.

Acceptance criteria:

- Pairing normally requires only choosing the TV and entering its displayed code.
- Source provisioning happens inside the authenticated Connect session.
- Reconnecting a trusted controller does not repeat first-run setup.
- Failure states provide a recovery action instead of a credential-transfer dead end.

September 10 credential-reuse implementation:

- The shared provider-session router now delegates setup export to the active provider's dedicated
  `currentProvisioningConnection` method. It no longer substitutes the general connection editor,
  fetches library metadata for an export, or falls back to a different provider's credentials.
- Jellyfin now passes the password from successful authentication into the existing shared
  credential-protected source store. Token-only reconnect preserves a previously saved password
  without submitting it for authentication again. This uses the existing password column and
  credential protector; no new migration or ordinary settings-sync credential field is introduced.
- Jellyfin's dedicated setup export reuses the active/saved password without a network request.
  Clearing, deleting, or switching the active source cannot expose the previous source's password.
  A rejected login does not replace the saved credential. Older token-only records still require
  password entry; an access token is never treated as a transferable password.
- The normal Jellyfin connection editor remains password-empty. The dedicated Connect export is
  the credential-bearing path, and existing encrypted provisioning remains its consumer.

Validation: 202 shared app, 30 Jellyfin, 157 Navidrome, and 50 storage JVM tests pass (439 total,
no failures/errors/skips). Android and iOS device/simulator ARM64 compilation and the Core-first
architecture guard pass. Production changes are limited to Core and Jellyfin `commonMain`.
The disposable 1080p API 36 Android TV emulator also passed
`AndroidConnectCredentialStorageInstrumentedTest` (one test, 0.233 seconds): the real SQLite row
contains a Keystore-protected value, reopening the database preserves the reusable password and
library selection through the shared provider router, and logout removes the active export. The
test uses a unique synthetic database, deletes it afterward, and performs no provider-network
requests. Android app and instrumentation APK assembly passed.

September 10 combined-flow implementation:

- Shared Core UI collects a missing password in a masked, non-saveable field, clears it on submit or
  cancellation, and explains validated secure retention. All maintained resource translations include
  the new copy. Existing reusable credentials bypass the prompt.
- Validation uses the current saved source and provider owner. Failure/cancellation restores the
  controller's connection state without clearing playback or provider data; successful authentication
  retains the reusable password through the existing protected provider store.
- Explicit code display creates an expiring offer; authenticated pairing converts it to a grant for
  that session only. Active/saved sources and ongoing connection attempts prevent automatic setup.
  Background advertising, replaced offers, expired codes, cancellation, and trusted resumption do
  not create or renew a grant. Successful setup consumes it.
- The controller automatically sends the selected source and portable settings after eligible code
  pairing. Correlated encrypted results complete the UI flow. Duplicate submissions are suppressed;
  writes, validation, and result waiting have deadlines, and disconnect cancels pending work.
- Failed target validation leaves source/settings untouched and supports retry within the live grant.
  A rejected automatic offer directs the user to show a new code and pair again. Later source changes
  retain explicit target approval. Trust reconnect never exports credentials or replays provisioning.

No schema migration, new settings preference, or platform production behavior was added. New Android
instrumentation exercises the real TV Core, NSD/TCP, J-PAKE, Keystore identity, and provider persistence,
with a synthetic controller restricted to the emulator's exact identity and loopback address.

Combined-flow validation: 206 shared app, 910 domain, and 364 presentation JVM tests pass (1,480
total, no failures/errors/skips). Android app/instrumentation builds, iOS device and simulator ARM64
compilation, and `verifyCoreFirstArchitecture` pass. On the disposable 1080p API 36 Android TV
emulator, `AndroidConnectInitialSetupInstrumentedTest` and
`AndroidConnectCredentialStorageInstrumentedTest` pass together (two tests, 4.257 seconds). The
setup scenario exercises actual TV Core with no target approval calls: code entry, missing/invalid
password, cancellation/retry, unreachable-source rollback, automatic source and album-grouping
transfer, preservation of device-local startup behavior, trusted reconnect without another credential
export, and rejection of a later approval-gated setup offer without changing source/settings. The
second test verifies protected SQLite/Keystore credential retention after reopening storage.
Visual checks also confirmed the populated home screen after setup and readable code-display consent
on the fresh-install TV screen. These emulator checks do not close the remaining physical-TV/playback/display gates below.

## Prevent the screen saver

Status: optional display setting implemented; physical playback wakefulness acceptance remains open

The shared **Keep screen awake** setting prevents automatic display sleep while Naviamp is visible.
It defaults to disabled and appears in TV Display settings and the shared Experience settings on
hosts with a native effect. Enabling it does not override explicit system sleep, screen locks, TV
power timers, or HDMI-CEC behavior.

The setting is persisted and included in settings export, import, sync, and portable Connect setup.
Older exports default to disabled. Core owns the setting, translated UI, visible-surface lifecycle,
lease ownership, and failure/retry behavior; hosts only apply the operating-system effect. Losing
focus retains the lease, while backgrounding/minimizing, disabling the preference, or disposing the
surface releases it. Display inhibition remains independent of audio playback and CPU wake locks.

Native effects use Android window flags, Windows power-request handles, macOS IOKit assertions,
X11 screen-saver suspension, and the iOS application idle timer. Wayland currently has no adapter;
its setting remains preserved in shared settings but the control is hidden. A failed acquisition
shows shared recovery copy and retries after toggling the setting or returning to the foreground.
All 17 maintained string-resource locales include the new copy. No database migration is needed.

Native contracts: [Android's visible-window flag](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on),
[Windows power requests](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-powersetrequest),
[macOS display-sleep assertion](https://developer.apple.com/documentation/iokit/kiopmassertiontypepreventuseridledisplaysleep),
and [iOS idle timer](https://developer.apple.com/documentation/uikit/uiapplication/isidletimerdisabled).
Windows requests include both display and system requirements as required by that native API;
Core releases the handle when the surface is no longer visible.


September 10 display-setting validation: 210 shared app, 911 domain, and 364 presentation JVM
tests pass (1,485 total, no failures/errors/skips). Android app/instrumentation APK assembly,
desktop compilation, iOS device/simulator ARM64 compilation, and the Core-first guard pass. The
guard now explicitly recognizes the three multiplatform lifecycle imports used by this shared
owner; Android-only `androidx` APIs remain forbidden.

On the disposable 1080p API 36 Android TV emulator, `AndroidScreenAwakeInstrumentedTest` passes
(two tests, 7.891 seconds). It checks actual window flags through enable/disable, pause while visible,
background/foreground, Activity recreation, disposal, preexisting-flag preservation, and repeated
release. The D-pad toggle also applied and released `KEEP_SCREEN_ON` in `dumpsys window`. A final 1080p
visual check confirmed focused On/Off feedback, preference preservation after app reinstall/restart,
and the complete explanation without truncation. Reproduction is in
[the display-setting check](android-tv-lifecycle-fixture.md#optional-display-awake-setting-check).
Desktop and iOS native adapters have compile validation only; runtime acceptance on those hosts
and the physical-TV tests below remain open.

Platform production diff accountability:

- `platforms/android/.../display/AndroidScreenAwakeEffect.kt`: Android `Window` flag and main Looper.
- `apps/android/.../MainActivity.kt`: supplies that Activity's native window effect to Core.
- `platforms/desktop/.../display/DesktopScreenAwakeEffect.kt`: JNA Win32/IOKit/X11 ABI calls and native resource lifetime.
- `apps/desktop/.../app/DesktopNaviampCoreHost.kt`: supplies the desktop window's native effect to Core.
- `apps/ios/.../IosScreenAwakeEffect.kt`: UIKit `UIApplication.idleTimerDisabled`.
- `apps/ios/.../NaviampIosApplication.kt`: supplies the native idle-timer effect when mounting the UIKit view controller.

Physical-device follow-up: during uninterrupted music playback, Android TV entered Ambient Mode,
continued playing for a while, and later powered off. Treat screen-saver suppression and playback
wakefulness as separate requirements:

- `FLAG_KEEP_SCREEN_ON` can suppress Android TV Ambient Mode while the Naviamp activity is visible,
  but Android's TV guidance discourages doing this for ordinary audio unless the app provides its
  own non-static screen-saver experience.
- Android already binds `PowerManager.PARTIAL_WAKE_LOCK` through `PlaybackWakeLockEffect`.
  `PlaybackFocusController` owns its fifteen-minute lease and five-minute progress-driven renewal.
  Verify that this existing binding remains effective through Ambient Mode and real stream stalls.
  Investigate whether streaming needs a Wi-Fi lock; no Wi-Fi lock is currently implemented.
- Android applications cannot override the device's Energy Saver policy. Also distinguish Android
  device sleep from television power timers and HDMI-CEC behavior, which Naviamp cannot reliably
  control.

Acceptance criteria:

- Music continues indefinitely through Ambient Mode during a long-duration streaming test.
- Playback-scoped wake resources are acquired and released with the shared playing state.
- The optional keep-screen-awake setting clearly describes display behavior and does not promise
  to override TV hardware, HDMI-CEC, or system Energy Saver settings.
- Diagnostics identify whether the Naviamp process, Android device, or external display stopped.

September 10 implementation: Core now reacquires a missing/expired lease on progress while still
playing, including recovery from an unsuccessful native acquisition. Explicit pause/stop clears
the playing intent immediately so late progress cannot reacquire the lock before the engine's
state callback arrives. Non-playing progress does not renew or reacquire a lease. This closes a
specific recovery gap; it does not establish the cause of the physical TV shutdown.

Remaining acceptance: capture process/service state, CPU wake-lock ownership, network availability,
device power state, and playback progress before and after Ambient Mode during a multi-hour run.
Separate app/process termination from device sleep and external display/CEC power-off. The existing
[712-second background soak](android-tv-soak.md) is useful short-run evidence, not overnight or
physical-device wakefulness acceptance. Progress-driven recovery cannot itself wake an already
suspended process, so verify timely renewal on hardware as well as recovery in common tests.

The September 10 emulator check also exposed stale external playback state after Stop: the shared
engine adapter invalidated native callbacks before they could publish Stopped. The adapter now
publishes its stopped state and unknown progress directly after the native stop effect, while
continuing to reject callbacks from the superseded playback generation.

Common regression validation: 909 domain and 358 presentation JVM tests pass, with no failures,
errors, or skips. Four new wakefulness tests failed against the previous controller, and the new
stop-publication test reproduced Playing being retained after Stop before its fix. All production
changes in this slice are in Core `commonMain`; no platform production adapters, settings schemas,
or user-facing strings changed.

Android TV emulator acceptance (September 10): a disposable `Television_1080p` API 36 ARM64
instance passed the corrected fixture setup test and the real BASS/PowerManager/MediaSession
smoke check. Playback remained active after Home, pause released `Naviamp:Playback`, resume
reacquired it, and Stop published `STOPPED` with no playback wake lock held. Paused native positions
advanced from 67.318 to 72.975 seconds across the resume interval on the same fixture track.
Android app/test APK assembly, shared JVM compilation, iOS device/simulator ARM64 compilation,
and `verifyCoreFirstArchitecture` passed. See [the reproduction procedure](android-tv-lifecycle-fixture.md#playback-wake-lock-smoke-check).
This short emulator check does not close the physical Ambient Mode or multi-hour acceptance above.

## Android TV waveform height

Status: planned

Reduce the vertical height and perceived thickness of the Now Playing waveform on Android TV. This
is independent of waveform sampling density: changing its presentation must not reduce waveform
detail or alter the existing density preference.

## Quick Jump readability

Status: planned

Fix the Android TV Quick Jump menu so its entries remain legible, especially the recently added
Artists, Albums, and Songs destinations. Verify text contrast, focus state, spacing, and truncation
at typical television viewing distances.

## Full-screen Now Playing performance

Status: planned

Make the transition into full-screen Now Playing smooth on Android TV. It is visibly choppy on new
hardware. Profile the transition before changing it, with particular attention to layout and state
recomposition, artwork/background decoding and effects, waveform work, and simultaneous animation.

Acceptance criteria:

- Entering and leaving full-screen Now Playing has no visible stalls on the physical test TV.
- Expensive artwork, background, and waveform work is not restarted on every animation frame.
- The optimization does not remove the intended motion or visual treatment.

## Complete Aurora controls

Status: partially implemented

TV Display settings already exposes Dark, Balanced, and Light when Aurora is selected. Reuse that
existing tone selector and add the missing controls for the existing shared settings:

- Color-stop count (`auroraColorSteps`)
- Gradient rotation (`auroraAngleDegrees`)

These controls should edit the same shared Aurora settings used by desktop and retain TV-friendly
focus, step, and value presentation.

## Artist release grouping and sorting

Status: planned

On Android TV Artist Details, support the same release organization available on desktop:

- Group releases into Albums, EPs & Singles, Compilations, and other applicable release types.
- Allow release grouping to be enabled or disabled.
- Allow the album sort order to be selected.

Grouping and sort order are shared preferences. They must be transferred during controller-to-TV
setup and preserved by settings export, import, and sync.

The shared `groupAlbumsByReleaseType` and `albumSortOrder` preferences already exist and are part
of the portable interface-settings snapshot. Complete TV rendering/controls and verify transfer
and round trips; do not create TV-specific copies of those settings.

## Completed during physical-device testing

- Fixed an authenticated Connect send race in which cancellation could consume an encrypted
  sequence number before its transport write, causing the next TV playback update to terminate with
  `The outbound sequence is not contiguous`.
