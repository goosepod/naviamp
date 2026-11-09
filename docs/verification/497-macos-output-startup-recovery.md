# macOS native output startup recovery (#497)

## Reproduction and fault boundary

The November 9 Mac campaign starts from accepted main `632e89c60`. The existing real-output
preview acceptance fails before preview playback: BASS reports Playing, the decoder reaches
1.5 seconds, and both the direct native and shared audible clocks remain at zero for the
original 10-second deadline. Failed runs are retained; a passing replay is not a resolution.

Additional controls on Apple M1/macOS 27.0.1, JBR 21.0.11, BASS 2.4.18.3:

- Twenty fresh standalone JVM executions of the unchanged channel-level acceptance pass.
- Fresh Gradle worker executions without the coverage agent reproduce the same failure on
  run 16 (15 pass, one failure). Coverage instrumentation is not required to trigger it.
- A direct native Python/ctypes probe reproduces the failure without Kotlin, Core, Java,
  Gradle, network access or application state publication. It uses the same queued mono
  mixer, 16 kHz PCM source, float decoder, 1,500 ms playback buffer, 100 ms update period and
  30 ms macOS device period. Audio remains muted on a real output device.
- The first native matrix stops at its first failed deadline. A predefined subsequent
  80-case interleaved experiment retains all outcomes: 79 starts advance, one stays frozen.
  That failure occurs even with continuous output enabled. Keeping the device awake is
  therefore not an established fix. Explicitly selecting the physical default is only a
  diagnostic control, not a replacement for following system-default changes.
- A separate bounded recovery campaign stops on its first stalled client (run 20). It
  preserves the failed ten-second result before applying any recovery operations.

The failed client reports device 1 (BASS's Default wrapper), IsStarted=1, channel Playing,
and queued decoded data. A native thread sample does not unfreeze it: audible position is
still zero afterward. `BASS_Start` succeeds but produces no clock movement over two seconds;
`BASS_Pause` followed by `BASS_Start` also succeeds without clock movement over two seconds.
`BASS_Init` with the current channel's device and `BASS_DEVICE_REINIT` retains the channels
and restores actual output movement: approximately 0.141 then 0.343 seconds in the first
two observations, while decoded position advances from 1.5 to 1.89 seconds.

This localizes the observed fault to a native output client that is logically running but
does not consume queued audio. Its precise internal BASS/CoreAudio startup trigger remains
unknown; neither a particular race nor a particular hardware device has been proven causal.

## Shared contract and bounded recovery

Core owns detection, timing, cancellation and the one-attempt limit. Before observing an
audible clock advance, it detects a Playing output that has at least 0.2 seconds of decoded
or queued audio but whose audible clock has stayed fixed for 2,500 ms. Paused and input-starved
time does not count. Missing/invalid clock evidence cannot trigger a repair. Actual clock
movement disarms the startup monitor.

The native effect reopens only the affected channel's output device and retains its channels,
format and initialization flags. It does not free/recreate the source, seek, substitute decoded
progress for the audible clock, increase the acceptance deadline, or run an unbounded retry.
Main and independent preview playback use the same shared decision. Preview recovery must
retain the paused main channel and position, including on failure and cancellation.

The JNI and Apple adapters only query native buffer duration and perform the native device
reinitialization. No host owns the recovery scheduler or policy.

References: [device reinitialization](https://www.un4seen.com/doc/bass/BASS_Init.html),
[logical output state](https://www.un4seen.com/doc/bass/BASS_IsStarted.html),
[non-consuming queued-data query](https://www.un4seen.com/doc/bass/BASS_ChannelGetData.html),
[continuous output](https://www.un4seen.com/doc/bass/BASS_CONFIG_DEV_NONSTOP.html).

## Validation status

Before native wiring, all 1,123 shared domain tests pass and the common implementation
compiles for JVM, Android, iOS Simulator and iOS device. Native adapter compilation then
passes for Android and both iOS targets. The 12 targeted real-output Mac tests pass,
including channel retention, paused position, callback lifetime, the full Core contract
and independent preview/main lifetime acceptance.

The first full Core run after adding preview clock monitoring exposes a test-observer
assumption: it records every audible-position query as a main handle. The corrected
observer records only the main mixer; preview monitoring queries a separate direct stream.
All original clock/queue/session assertions and deadlines remain unchanged. The initial
failed XML is retained, followed by the corrected passing run.

Final local validation passes:

- Shared domain suite: 1,124 tests, zero failures/errors/skips (11 new shared regressions).
- Desktop platform suite: 70 tests, zero failures/errors, the same three optional skips.
- Android debug APK builds and its native-package verification passes. ELF dynamic symbol
  inspection confirms both new JNI exports in arm64-v8a, armeabi-v7a, x86 and x86_64.
- Android and both iOS native adapters compile; the architecture guard passes.
- The strengthened native test verifies that reinitialization selects the channel's real
  device even when the calling thread currently selects device 0. Its playback clock is
  still the real output clock; device 0 is not used for clock acceptance.
- Twenty sequential pairs of channel-level and complete-Core acceptance pass in 40 fresh
  Gradle worker JVMs with the normal coverage agent. No assertion or deadline changes.
  None of these 40 executions encounters a natural startup stall or invokes recovery;
  recovery evidence comes from the retained native fault/recovery experiment, forced native
  reinitialization and shared fault/cancellation regressions.
- The committed minimal native probe passes a real-output smoke execution, and its Python
  syntax and CLI help checks pass.

Fresh iOS native execution and exact-source CI remain gates before acceptance.

## Native production-file audit

- `platforms/android/src/main/kotlin/app/naviamp/android/playback/AndroidBassAudioBackend.kt`:
  translates the shared contract to Android's JNI BASS effect.
- `platforms/android/src/main/kotlin/app/naviamp/android/playback/AndroidBassJni.kt`:
  declares the JVM/JNI ABI for the two native operations.
- `platforms/desktop/src/desktopMain/kotlin/app/naviamp/desktop/playback/bass/DesktopBassAudioBackend.kt`:
  translates the shared contract to Desktop's JNI BASS effect.
- `platforms/desktop/src/desktopMain/kotlin/app/naviamp/desktop/playback/bass/DesktopBassJniBinding.kt`:
  declares the JVM/JNI ABI for the two native operations.
- `platforms/ios/src/iosMain/kotlin/app/naviamp/ios/playback/IosBassPlayback.kt`:
  calls the Apple BASS C ABI to read queued duration and reopen the channel's native device.
- `native/bass-jni/src/naviamp_bass_jni.cpp` and `include/naviamp_bass_jni.h`:
  export the native JNI ABI, preserving the channel device's native format/flags and
  dynamically resolving the existing BASS functions on Windows.

All timing, retry limits, state interpretation and cancellation remain in common Kotlin.

## Minimal native reproduction

The dependency-free probe preserves the original ten-second outcome and stops at its
first failure. Each attempt is a fresh process; existing evidence directories are refused.

```sh
python3 scripts/probe-macos-bass-output-clock.py \
  --library-dir platforms/desktop/vendor/bass/macos-arm64 \
  --output build/native-clock-probe-497 --runs 80 --recover
```

`--recover` performs the native start/pause/reinitialization comparison only after a
failed deadline; that attempt still exits nonzero. The probe deliberately bypasses Core,
so its underlying native fault can still reproduce after the application recovery is
implemented. It is diagnostic evidence, not a substitute for passing application tests.

Original private logs, XML, native observations, recovery stages and thread sample are
retained in `build/mac-acceptance-20261109/clock-investigation/`. Experimental outcomes are
diagnostic evidence, not replacement acceptance tests. No successful control erases a failure.
