# Android TV Follow-up

This document tracks issues and product improvements found while testing the Android TV work on a
physical Android 14 TV device. Unless explicitly marked complete, these are requirements for later
work rather than implementations on the current branch.

## Initial Connect setup

Status: planned

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

## Prevent the screen saver

Status: planned

Add a shared setting that prevents the screen saver or display sleep while Naviamp is open. Expose
it on Android TV and on desktop platforms that provide a reliable native inhibition API, including
Windows. Keep the default disabled to preserve normal device power behavior.

The setting must be persisted and included in settings export, import, and sync. Core owns the
setting and policy; hosts only apply the narrow operating-system effect.

Physical-device follow-up: during uninterrupted music playback, Android TV entered Ambient Mode,
continued playing for a while, and later powered off. Treat screen-saver suppression and playback
wakefulness as separate requirements:

- `FLAG_KEEP_SCREEN_ON` can suppress Android TV Ambient Mode while the Naviamp activity is visible,
  but Android's TV guidance discourages doing this for ordinary audio unless the app provides its
  own non-static screen-saver experience.
- Verify that the Android playback service and native BASS engine hold the appropriate partial CPU
  wake lock, and a Wi-Fi lock when streaming requires it, only while playback is active. A custom
  audio engine may not receive the implicit wake behavior provided by Android media players.
- Android applications cannot override the device's Energy Saver policy. Also distinguish Android
  device sleep from television power timers and HDMI-CEC behavior, which Naviamp cannot reliably
  control.

Acceptance criteria:

- Music continues indefinitely through Ambient Mode during a long-duration streaming test.
- Playback-scoped wake resources are acquired and released with the shared playing state.
- The optional keep-screen-awake setting clearly describes display behavior and does not promise
  to override TV hardware, HDMI-CEC, or system Energy Saver settings.
- Diagnostics identify whether the Naviamp process, Android device, or external display stopped.

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

Status: planned

Expose the shared Aurora controls in Android TV settings:

- Color-stop count
- Gradient rotation
- Dark, Balanced/Normal, and Light tone choices

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

## Completed during physical-device testing

- Fixed an authenticated Connect send race in which cancellation could consume an encrypted
  sequence number before its transport write, causing the next TV playback update to terminate with
  `The outbound sequence is not contiguous`.
