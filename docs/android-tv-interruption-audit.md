# Android TV interruption audit — September 9, 2026

## Fixes

A disconnected finite stream could reach BASS's end callback before all advertised audio bytes
had arrived. Core treated it as Finished, which cleared the recovery cursor. The small-buffer
emulator regression reproduced a retry at 0 seconds after interruption near 6 seconds.

Core now distinguishes a disconnected incomplete download from successful completion using native
file/download byte positions. Both the end callback and polling completion use the same decision;
a polling update cannot overwrite an observed interruption. The fixed emulator run interrupted
near 7.069 seconds and retried at 9.082 seconds. Complete audio does not require downloading tags
that follow it. Unavailable byte counts and unknown-length streams retain the existing behavior.
Duration estimates are not used to decide whether the download was complete. The native position
semantics are documented by [BASS_StreamGetFilePosition](https://www.un4seen.com/doc/bass/BASS_StreamGetFilePosition.html).

Android's pause/duck/resume decisions and wake-lock renewal policy were still in its host wrapper.
They now live in Core's `FocusedBassPlaybackEngine` and `PlaybackFocusController`. Resume eligibility
is shared with the existing external audio-session coordinator through `PlaybackInterruptionPolicy`.
Repeated transient-loss notifications preserve resume eligibility. Explicit Pause reaches the
engine even when focus has already paused it, so user intent cancels automatic resume without
turning Pause into a toggle. Terminal playback states release the native focus/lease.

## Emulator evidence

All scenarios use a disposable 1080p TV emulator and synthetic loopback media, not stored server
credentials. No physical device or the other running emulator was modified.

- A four-second initial stream buffer was exhausted after the fixture closed its connections.
  The app reported a playback failure and explicit retry preserved the cursor.
- Real AudioManager duckable loss kept playback active; transient loss/gain resumed playback; permanent loss remained paused;
  explicit Pause during transient loss suppressed the later automatic resume.
- Three thirty-second tracks completed with gapless enabled. Native diagnostics reported
  provider ReplayGain of −6.0 dB (0.5011872×), and the fixture received exactly three successful
  listen submissions, one for each track. Presence reports are counted separately.
- The equivalent three-track run with a three-second crossfade also completed and submitted
  exactly one listen per track. A final repeat backgrounded the Activity during the first track;
  both subsequent transitions completed in the background, again with exactly three submissions.

The tests exercise native engine state, queue advancement, focus callbacks and provider requests.
They do not measure acoustic gap lengths, speaker levels, HDMI downmix, CEC, physical Wi-Fi
reassociation, or overnight endurance. Unknown-length/live streams and codecs that do not expose
BASS download positions need separate acceptance. Signed distribution and physical TV acceptance
remain open; physical OLED testing is not assigned to the maintainer.

## Build and regression validation

The final shared suites pass 902 domain, 199 app and 356 presentation tests. The Desktop suite
passes 43 tests, including eight native JNI integration checks: **1,500 JVM tests**, with no
failures, errors or skips. Android debug/test APK assembly, Android release bundle assembly,
Desktop host compilation, iOS Simulator ARM64 host compilation and the Core-first architecture
guard pass. The release bundle remains unsigned because signing is not configured in this environment.

## Reproduction

See [the fixture setup](android-tv-lifecycle-fixture.md). Run one acceptance method per fresh
**disposable** app installation. The instrumentation methods deliberately reject an existing
current connection. Always specify the emulator serial and the single method selector.

For buffer exhaustion, start the fixture with:

```sh
python3 scripts/android-tv-fixture.py --burst-seconds 4
```

Then run `AndroidTvPlaybackAcceptanceInstrumentedTest#drainedTransportRetryPreservesPosition`.
The method closes and restores the fixture connections in a `finally` block.

For native focus, use the same long fixture and run
`AndroidTvPlaybackAcceptanceInstrumentedTest#transientAndPermanentAudioFocusLossRespectUserIntent`.

For gapless/crossfade, start a fresh fixture process (resetting its listen counters):

```sh
python3 scripts/android-tv-fixture.py --tracks 3 --track-seconds 30
adb -s emulator-5556 shell am instrument -w -r \
  -e tvLocalFixture true \
  -e class 'app.naviamp.android.AndroidTvPlaybackAcceptanceInstrumentedTest#sustainedTransitionsAndProviderReports' \
  app.naviamp.android.v2test.test/androidx.test.runner.AndroidJUnitRunner
```

For crossfade, repeat with a fresh disposable app and fixture process, adding `-e crossfade true`.
To repeat the background check, send `adb -s emulator-5556 shell input keyevent KEYCODE_HOME`
after instrumentation reports the first `TV crossfade playing` track and leave the test running.
The fixture reports only synthetic track IDs, submission flags, request counts and total bytes.
It never forwards traffic or logs credentials. Shut down the disposable emulator and fixture
server after testing.

## Platform-diff accountability

All playback decisions remain in Core. Each changed platform production file has this native boundary:

| File | Native boundary |
| --- | --- |
| `platforms/android/src/main/kotlin/app/naviamp/android/playback/AndroidFocusedBassPlaybackEngine.kt` | Binds Android AudioManager callbacks, PowerManager wake locks and SystemClock to the shared focus/lease contracts. |
| `platforms/android/src/main/kotlin/app/naviamp/android/playback/AndroidBassAudioBackend.kt` | Translates the shared BASS file-position request into the Android JNI binding. |
| `platforms/android/src/main/kotlin/app/naviamp/android/playback/AndroidBassJni.kt` | Declares/invokes the JNI native method and maps its unavailable sentinel to null. |
| `platforms/desktop/src/desktopMain/kotlin/app/naviamp/desktop/playback/bass/DesktopBassAudioBackend.kt` | Translates the shared BASS file-position request into the Desktop JNI binding. |
| `platforms/desktop/src/desktopMain/kotlin/app/naviamp/desktop/playback/bass/DesktopBassJniBinding.kt` | Declares/invokes the Desktop JNI method and maps its unavailable sentinel to null. |
| `platforms/ios/src/iosMain/kotlin/app/naviamp/ios/playback/IosBassPlayback.kt` | Calls BASS_StreamGetFilePosition through Kotlin/Native cinterop and translates its sentinel. |
| `native/bass-jni/src/naviamp_bass_jni.cpp`, `native/bass-jni/include/naviamp_bass_jni.h` | Exports the JNI ABI and loads/calls the native BASS symbol; contains no completion policy. |

Android instrumentation tests exercise real Activity, AudioManager, BASS and storage boundaries.
The Desktop integration test verifies the native file-size query against a generated WAV.
