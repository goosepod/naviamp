# Android TV background playback soak

This opt-in instrumentation test exercises the real Android Activity, foreground playback service,
BASS engine, Core queue/profile/reporting owners, and OS resource counters. It requires an empty
app installation on a disposable emulator and the synthetic loopback fixture. It never uses saved
provider credentials, forwards traffic, or touches another connected device.

## Scenario

The default acceptance run contains 24 unique thirty-second tracks split into six albums. Before
playback, it saves each album's profile through the shared product commands. Profiles alternate
between gapless with Track ReplayGain (−6 dB) and three-second crossfade with Album ReplayGain
(−3 dB). Global settings disable both overrides, demonstrating that the saved profiles take effect.
Transitions between album groups inherit the global transition setting, as designed.

The Activity goes to the background during the first track and stays stopped throughout the run.
At tracks 5, 13 and 21, the fixture closes streaming connections and refuses new requests. The test
waits for buffer exhaustion/error, restores connectivity, and issues explicit Play. It asserts the
same queue occurrence and preservation of the last observed position. It does not add unattended
retries to the app.

The test checks ordered traversal of every track, profile/gain diagnostics, progress deadlines,
exactly one successful listen submission per track, three recoveries, zero active fixture streams
at completion, and a bounded peak stream count. It allows brief Finished/Loading publications
between tracks; errors and a 45-second lack of progress still fail the run.

At five seconds into every track it samples process PSS, allocated native heap, file descriptors and
threads. Equal four-sample windows after warm-up and at the end are compared using their upper
median. Regression ceilings are +64 MiB PSS, +16 MiB native heap, +16 file descriptors and +16
threads; the server permits at most eight simultaneous streams. These broad ceilings detect gross
resource growth. A single twelve-minute run cannot prove absence of slow leaks or overnight
stability. Memory values include the app, Android runtime, and instrumentation overhead.

## Reproduce

Build the app and test APKs, then install them into a fresh disposable TV emulator overlay:

```sh
./gradlew :apps:android:assembleDebug :apps:android:assembleDebugAndroidTest
python3 scripts/android-tv-fixture.py --tracks 24 --albums 6 --track-seconds 30 --burst-seconds 4
```

In another terminal, use the serial of that disposable emulator explicitly:

```sh
adb -s emulator-5556 reverse tcp:18080 tcp:18080
adb -s emulator-5556 shell am instrument -w -r \
  -e tvLocalFixture true \
  -e class 'app.naviamp.android.AndroidTvPlaybackSoakInstrumentedTest#backgroundProfilesInterruptionsAndResourceGrowth' \
  app.naviamp.android.v2test.test/androidx.test.runner.AndroidJUnitRunner
```

The test backgrounds its own Activity. Leave the instrumentation process running. Restart the
fixture for each independent run so listen counters start empty. The helper rejects an existing
current connection; use a new disposable overlay rather than clearing a real user's app data.

Larger runs can increase `--tracks` (up to 240) and use thirty- to sixty-second tracks, retaining at
least six albums and four tracks per album. Album count must divide the track count. Output lines
prefixed `TV SOAK` contain synthetic track IDs, profile observations, resources, interruption
positions and final totals. Shut down the fixture and disposable emulator after testing.

## Results

The September 9, 2026 run passed: **24 tracks, six saved profiles, 24 successful listen submissions,
three outage recoveries**, and 712 seconds (11m 52s) of background playback. Total instrumentation
time including setup was 720.543 seconds. All tracks were observed in order, all profile/gain
assertions passed, and the Activity remained stopped. Fixture streams peaked at three and returned
to zero.

| Resource | Warm baseline | Final window | Change |
| --- | ---: | ---: | ---: |
| PSS | 185,774 KiB | 188,342 KiB | +2,568 KiB |
| Allocated native heap | 22,437 KiB | 22,481 KiB | +44 KiB |
| File descriptors | 148 | 148 | 0 |
| Threads | 51 | 49 | −2 |

Explicit retries resumed track 5 at 8.957 seconds after interruption at 6.949; track 13 at 9.886
after 6.877; and track 21 at 10.657 after 6.962. These results are within the stated resource
ceilings and preserve playback position; they do not prove overnight stability or acoustic output
quality. [All resource samples and recovery measurements](android-tv-soak-results.json) are retained
for review.

Android app/test assembly, the architecture guard, Python syntax validation and a six-album catalog
consistency check passed. The first harness draft rejected a brief Finished publication between
tracks; the final observer permits that asynchronous transition while retaining error/progress
checks. No product bug was found in the completed run, and no platform production code changed.
