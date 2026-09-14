# Android TV lifecycle fixture

This fixture exercises the real Android app, BASS engine, persisted playback session, and
MediaSession without saved server credentials. It generates a ten-minute WAV and a one-track
Subsonic catalog by default. `--burst-seconds`, `--track-seconds`, `--tracks`, and `--albums` configure
small-buffer outages or multi-track playback. `--unknown-length` omits HTTP length/range headers
for finite songs. The catalog also includes an endless, paced live radio fixture.
Tracks include synthetic ReplayGain metadata. The HTTP server binds only to loopback, never forwards traffic, and does not log
URLs or authentication values. An initial audio burst allows native seeking; subsequent bytes
are paced so the transport can be interrupted.

Use a **disposable TV emulator**, such as a read-only AVD overlay. Do not clear an existing device's
app data for this test. Install the debug app and Android test APK into that disposable instance.
Every ADB command must select its serial explicitly; other connected devices are outside this test.

```sh
python3 scripts/android-tv-fixture.py
```

In another terminal, replace `emulator-5556` with the disposable emulator's serial:

```sh
adb -s emulator-5556 reverse tcp:18080 tcp:18080
adb -s emulator-5556 shell am instrument -w -r \
  -e tvLocalFixture true \
  -e class app.naviamp.android.AndroidTvLifecycleFixtureInstrumentedTest \
  app.naviamp.android.v2test.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5556 shell am start \
  -n app.naviamp.android.v2test/app.naviamp.android.MainActivity
adb -s emulator-5556 shell dumpsys media_session
```

The opt-in setup requires no current connection. It connects to `127.0.0.1:18080` with synthetic
credentials, disables audio caching, enables resume on launch, plays the fixture, and waits for a
nonzero native position to be saved. It changes settings only in the disposable installation.

Check these scenarios against the `NaviampCorePlayback` session whose description is
`TV lifecycle fixture`. Wait for callbacks after each command; a loading session has position `-1`.
The published PLAYING position is a timestamped anchor, so pause before comparing saved positions.

- Send Home, then `shell cmd media_session dispatch pause` and `play`. Repeat each command to
  verify explicit requests do not toggle playback.
- Obtain the app PID with `shell pidof app.naviamp.android.v2test`, then terminate that specific
  debug process with `shell run-as app.naviamp.android.v2test kill -9 PID`. Relaunch and verify a
  new PID, the same track, and a position within the save interval of the last observed position.
- Use `shell am force-stop app.naviamp.android.v2test`, relaunch, and compare the restored position.
- `curl http://127.0.0.1:18080/_test/off` closes active fixture streams and returns HTTP 503 for
  provider requests. `/_test/on` restores availability; `/_test/status` reports only request counts
  and active stream count. Buffered playback may continue after transport loss; that alone does
  not prove an error/retry path. Also test cold relaunch while offline and explicit Play after
  restoring the fixture. Confirm that recovery does not restart the track at zero.

Always restore `/_test/on` after interruption tests. Stop the fixture and shut down the disposable
emulator when finished. Its overlay is discarded. This does not test physical HDMI/CEC, audio
focus competition, Wi-Fi reassociation, direct-LAN pairing, signed release installation, or OLED
hardware behavior.

For automated buffer-exhaustion, focus, and transition checks, see the
[interruption audit and method selectors](android-tv-interruption-audit.md).

For the longer saved-profile/background/resource acceptance run, see [the TV soak test](android-tv-soak.md).

## Playback wake-lock smoke check

After the fixture setup and Activity launch above, use the same disposable emulator serial:

```sh
adb -s emulator-5556 shell input keyevent KEYCODE_HOME
adb -s emulator-5556 shell dumpsys power
adb -s emulator-5556 shell dumpsys media_session
adb -s emulator-5556 shell cmd media_session dispatch pause
adb -s emulator-5556 shell dumpsys power
adb -s emulator-5556 shell cmd media_session dispatch play
adb -s emulator-5556 shell dumpsys power
```

Wait for MediaSession state changes before evaluating the power dump. In its active `Wake Locks`
section, `PARTIAL_WAKE_LOCK 'Naviamp:Playback'` must be present during background playback, absent
after pause, and present again after Play. Check that the native playback position advances between
observations and that pause/resume preserves the same fixture track. Stop playback at the end and
verify the lock is released. This smoke check exercises Android PowerManager, the real BASS engine,
and MediaSession delegation; it does not force lease expiration or prove physical-TV sleep behavior.

The common `PlaybackFocusControllerTest` separately simulates a native lease expiring after delayed
progress, unsuccessful acquisition, and late progress following explicit pause/stop. The physical
multi-hour **Keep screen awake** acceptance passed on September 11; the result and optional
Ambient Mode/Wi-Fi diagnostics are recorded in [the follow-up](ANDROID_TV_FOLLOW_UP.md).

The standalone fixture entry point explicitly returns `Unit`: its shared setup helper returns a
`NaviampCore`, and inferring that return type for the JUnit method makes AndroidJUnitRunner reject
the test before setup runs. This signature regression was corrected during September 10 acceptance.

## Connect credential storage check

The following independent test needs an installed app/test APK and an Android TV emulator, but
does not need the HTTP playback fixture or an empty app installation:

```sh
adb -s emulator-5556 shell am instrument -w -r \
  -e class app.naviamp.android.AndroidConnectCredentialStorageInstrumentedTest \
  app.naviamp.android.v2test.test/androidx.test.runner.AndroidJUnitRunner
```

It creates and removes its own uniquely named SQLite database with synthetic credentials. It checks
Keystore protection of the stored password, database close/reopen, Jellyfin setup export through
the shared provider router without HTTP requests, preservation of selected libraries, and clearing
the export on logout. It does not pair devices or validate the combined initial-setup UX.


## Optional display-awake setting check

On a disposable Android TV emulator with the current app and instrumentation APKs installed, clear
only the test app and run:

```sh
adb -s emulator-5556 shell pm clear app.naviamp.android.v2test
adb -s emulator-5556 shell am instrument -w -r \
  -e tvLocalFixture true \
  -e class app.naviamp.android.AndroidScreenAwakeInstrumentedTest \
  app.naviamp.android.v2test.test/androidx.test.runner.AndroidJUnitRunner
```

No HTTP fixture is needed. The tests check the real Activity window flag with the shared Core
preference and mounted lifecycle: disabled by default, enabled while visible, retained when paused
but visible, released on Stop/background, reacquired on return, preserved across Activity recreation,
and released on disable/disposal. The adapter test also checks preservation of a preexisting window
flag and idempotent release of its own flag. The preference is disabled during cleanup.

For a visual check after synthetic fixture setup, open Settings → Display and toggle **Keep screen
awake** with the D-pad. Confirm readable description and enabled/disabled feedback. This display
check is separate from CPU wake-lock and physical multi-hour Ambient Mode playback acceptance.
