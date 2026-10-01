# Linux MPRIS verification

Tracking: [GitHub issue #119](https://github.com/goosepod/naviamp/issues/119).

Naviamp publishes `org.mpris.MediaPlayer2.naviamp` at `/org/mpris/MediaPlayer2` on the
Linux session bus. Failed MPRIS registration does not stop playback. The first process owns the
name; later processes keep playing independently and retry registration with bounded backoff
(1–30 seconds). Closing the owner releases its name, allowing another instance to acquire it.
Name loss and disconnection use D-Bus callbacks rather than a polling timer.

Core owns playback metadata, capabilities, commands, seek events, publication decisions,
registration scheduling, retries, and cancellation. The Desktop adapter translates the MPRIS
D-Bus ABI and owns its connection/export/callback lifetime. Android and iOS continue using the
same external playback bridge.

MPRIS supports transport controls, position and duration, volume, shuffle, repeat, metadata,
and artwork URLs. Unsupported seek/navigation capabilities are advertised as unavailable.
Raise, Quit, Fullscreen, URI opening, track lists, and variable playback speed are not advertised.
Normal progress changes do not emit property signals; accepted seeks emit `Seeked`, including
short forward seeks. MPRIS relative seeks past the track's end advance through Core, while the
existing rewind/fast-forward controls retain their clamping behavior.

## Automated checks

Run the real-bus adapter checks on Linux with JDK 21+, `dbus-run-session`, and `playerctl`:

```sh
./scripts/mpris-verify.sh
```

The script uses a private session bus so it does not collide with a running desktop player.
It checks discovery and commands with `playerctl`, property reads/writes, metadata,
property/seek signals, name contention, name-loss and connection-loss recovery, shutdown
cleanup, and unavailable-bus behavior. Linux CI runs these integration checks alongside its
native Desktop suite. Integration mode always executes the test task, preserving compilation
caches. Tests requiring a session bus are reported as skipped outside integration mode.

Shared regression tests cover capabilities, transport routing, seek bounds, seek-generation
preservation, publication suppression, bounded retries, recovery, and cancellation. Compile
Core for JVM, Android, and both iOS targets before accepting platform wiring; run the full
platform matrix through the linked pull request.

## Real desktop session checks

Build and start the staged app in a Linux desktop session:

```sh
./gradlew :apps:desktop:stageLocalTestApp
./build/local-test/Naviamp/bin/Naviamp
playerctl -l
playerctl -p naviamp metadata
playerctl -p naviamp status
```

Using a disposable source/queue, verify:

- Play, pause, play/pause, previous, next, and stop affect playback once per invocation.
- Position advances during playback and stays steady when paused. Metadata changes with tracks.
- `position 15`, `position 0.5+`, and backward seeks update playback and emit `Seeked`.
- Volume, shuffle, and loop writes are reflected in both the app and MPRIS properties.
- Source changes and live radio update metadata and seek/navigation capabilities.
- Desktop media controls and hardware media keys work with the app focused and unfocused.
- A second instance cannot displace the first; closing the first permits reacquisition.
- Shutdown releases the name; restarting restores discovery; session-bus loss/recovery does
  not interrupt audio and permits re-registration.

Use `gdbus monitor --session --dest org.mpris.MediaPlayer2.naviamp` to inspect signals.
Do not count a fixture-only bus check as verification of real audio or desktop media keys.

## Acceptance evidence — 2026-10-01

Verified against the staged Linux app in an XFCE/X11 desktop session using isolated temporary
profiles and a silent WAV/live-audio fixture derived from `scripts/android-tv-fixture.py`.
The fixture served complete finite tracks without network throttling during seek checks.

- All 724 Core app/presentation and Desktop tests passed, including real-bus integration;
  shared presentation compiled for Android,
  iOS device, and iOS simulator targets.
- Real D-Bus adapter tests passed, including `playerctl` discovery/commands, property reads,
  signal suppression during natural progress, short seek notifications, name contention,
  name-loss/disconnection recovery, and connection/name cleanup.
- Packaged-app `playerctl` checks passed for native progress, paused progress after its final
  buffered update, absolute and short relative seeks, volume, all repeat modes, shuffle,
  next/previous metadata and track IDs, stale-track seek rejection, play/pause, and stop/restart.
- XFCE's visible MPRIS controls and simulated `XF86AudioPlay` key events toggled playback once
  per invocation with Naviamp unfocused. Physical keyboard hardware was not exercised.
- Live radio changed metadata, omitted finite duration, and advertised no seek or track navigation.
  A final packaged-app restart confirmed synthetic radio artwork is omitted from MPRIS metadata.
- Starting a second real app preserved the first owner's name. Gracefully closing the first
  allowed the second to acquire it; closing the final instance released the name.
- Linux packaging now includes and verifies `jdk.security.auth`, which dbus-java needs for Unix
  user authentication. A full-JDK unit run alone does not catch a missing bundled runtime module.

Transcoded seeks can briefly reinitialize the stream. Wait for Core's advertised playable state
before dependent controls; restoration may publish the final position again through `Seeked`.
The application can publish its final buffered progress update after pausing, then remains steady.
An artificial ALSA null-device configuration caused a pre-registration BASS crash in this session;
acceptance used the normal desktop audio device and silent samples instead.

Removing the entire session bus from a fresh packaged-app profile prevented startup in the
existing Linux secure-credential service, before MPRIS registration. Whole-app startup without
a session bus is therefore not verified; optional MPRIS registration failure and recovery passed
the isolated shared and native tests.

Apple host/native execution and the remaining cross-platform CI matrix are pull-request checks.
