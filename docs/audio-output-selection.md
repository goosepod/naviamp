# Audio Output Selection

Status: v0.16.0 implementation tracker.

## Product Behavior

- Default to `Follow OS Output`, which uses the current system default output device.
- Allow pinning a specific available output device on the current install.
- If the pinned device is unavailable, fall back to the OS default and show the unavailable pinned device in settings.
- Keep the choice device-local. Output device IDs are OS-local and must not be exported through settings sync.

## Current Foundation

- Shared playback settings now include `AudioOutputDevicePreference`.
- Settings sync resets `outputDevice` with other device-local playback settings.
- Android settings persistence stores the preference locally in SharedPreferences.
- Desktop settings persistence stores the preference locally in the desktop settings JSON through `PlaybackSettings`.
- Shared playback has an optional `AudioOutputDevicePlaybackEngine` contract for engines that can enumerate and select output devices.

## Platform Work

### Desktop BASS

The vendored BASS header already exposes the required primitives:

- `BASS_GetDeviceInfo` for enumerating output devices.
- `BASS_GetDevice` for reading the active output device.
- `BASS_SetDevice` for selecting the device used by subsequent stream creation and channel operations.
- `BASS_Init` accepts the selected device number. Device `-1` is the system default.
- Device flags include `BASS_DEVICE_ENABLED`, `BASS_DEVICE_DEFAULT`, and `BASS_DEVICE_INIT`.

The desktop JNI wrapper should expose a small stable shape to Kotlin:

- list devices as `AudioOutputDevice(id = deviceNumber.toString(), name, isDefault, isEnabled, isInitialized)`.
- set device by parsed numeric id, or `-1` for `Follow OS Output`.
- apply the selected device before creating playback/mixer streams.
- when a pinned id is missing or disabled, use `-1` and report the fallback state for settings UI.

### Android

Android builds should not show an output-device picker. Android playback should always follow the system/media route selected by Android, including phone speaker, Bluetooth, wired headset, car, Android Auto, and any active platform route. The Android settings store should leave `PlaybackSettings.outputDevice` at its default `Follow OS Output` value and should not persist pinned output devices.

## Settings UI Pass

- Split Playback settings into clearer groups:
  - Output
  - Playback behavior
  - Queue rules
  - ReplayGain
  - Equalizer
  - Streaming and downloads
  - Lyrics and related tracks
  - Diagnostics
- Put Output at the top of Playback settings on desktop builds.
- Show `Follow OS Output` as the first option.
- Show pinned devices below it with default/current/unavailable state.
- Keep unavailable pinned device visible until the user chooses another option.
- Hide the Output group entirely on Android builds.

## Verification

- Domain tests should cover preference normalization and sync exclusion.
- Desktop JNI tests should cover device-list mapping and invalid id fallback where possible.
- Manual desktop verification should cover:
  - launch with `Follow OS Output`;
  - switch to another available output;
  - disconnect pinned output and confirm fallback;
  - reconnect pinned output and confirm it can be selected again.
