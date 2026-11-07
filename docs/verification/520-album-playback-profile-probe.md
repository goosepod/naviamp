# Album Play profile investigation

Sven confirmed #520 uses the album Play button with global Crossfade / 8 seconds and an individual
album Gapless profile. The separate album-track selection context bug does not explain that report.

The new common `NaviampCoreAlbumPlaybackProfileTest` exercises the actual Core album-select,
SavePlaybackProfile and album Play commands, with source-scoped profile lookup and the production
shared playback-engine adapter. Its observing engine records settings at play and next-track
preparation, then emits completion to exercise the next within-album track.

On main baseline `cafcee8cd`, one passing regression covers four saved modes: Gapless (zero
crossfade), explicit Crossfade (3 seconds), Pause (zero crossfade without preloading), and Inherit
(global 8 seconds). All retain the album queue group and expected settings on the next within-album
track. This does not reproduce the reported failure in the portable command/queue/resolution path.

The probe deliberately uses an in-memory profile repository and observing engine. It is not proof
of on-disk profile migration/lookup or audible native transitions. Those boundaries, actual
reporter/device details, shuffle, queue boundaries, active edits and restored queues still require
investigation. No production change or resolution claim is made at this checkpoint.

## Related album-track context loss

A second end-to-end regression starts by selecting an individual album track. Before the correction,
Gapless expects zero crossfade but observes the global 8 seconds at the engine boundary. This is
the separate path already identified in the issue, not Sven's confirmed album Play reproduction.

The shared track action controller now captures the loaded album context with its ordered tracks
and starts through the existing album playback owner at the selected index. That owner retains the
source-scoped saved profile and full album group. Generic library/search track selection keeps its
existing context. No new setting, persistence schema or platform production code is added.

Validation after correction: 24 focused common tests pass (17 Core, 5 queue-controller and 2 profile
entry-point tests), with zero failures/errors/skips. The two end-to-end tests cover the four saved
modes through both album Play and album-track selection, including next-track preparation and the
next within-album track. Architecture verification and shared Android/JVM/both iOS compilation pass.
The original album Play report remains open for storage/native/audible investigation.

## Follow-up review on accepted main

Reviewed PR #529 at `28173ffff` against accepted main `62851e1c3`. The accepted main tree
matches the candidate that passed Verify run `42306716280`; the PR's own final-base run
`42311698167` was cancelled, so inherited validation is not final PR acceptance.

59 focused JVM tests pass with zero failures/errors/skips: the two Core profile entry-point
tests, 44 production engine-adapter tests, five queue-controller tests, two profile-store tests,
four session-store tests and two provider-identity migration tests. The new profile-store probe
writes Gapless, explicit three-second Crossfade and Pause overrides to a real SQLite file,
closes and reopens the database, and resolves each against global Crossfade / eight seconds.
It also verifies source isolation and that clearing an override persists across another reopen.
This narrows the persistence investigation; it does not exercise a reporter's existing database
or reproduce the audible failure.

The owner tentatively identified Android as the affected platform. Added opt-in
`AndroidAlbumPlaybackProfileInstrumentedTest.albumPlayHonorsSavedGaplessWithGlobalEightSecondCrossfade`
to exercise the real Android application's file-backed repository and BASS engine through the
album Play command. It checks the album queue profile, native crossfade duration and active
state before the transition, and advancement to the next within-album track. An inherited
profile is the positive control and must activate eight-second native crossfade. This is native
state evidence, not an audible-continuity measurement.

Device execution was initially deferred at the owner's request. The later authorized phone run
is recorded below.
`:apps:android:compileDebugAndroidTestKotlin` passes for the new probe and its production dependencies.
Local logs are retained under ignored `build/sven-review/` in this worktree.
The test remains opt-in and requires a fresh disposable test installation plus this loopback
fixture (never clear a populated installation to satisfy its setup guard):

```shell
python scripts/android-tv-fixture.py --tracks 3 --albums 1 --track-seconds 30
adb reverse tcp:18080 tcp:18080
adb shell am instrument -w -e tvLocalFixture true -e class app.naviamp.android.AndroidAlbumPlaybackProfileInstrumentedTest app.naviamp.android.v2test.test/androidx.test.runner.AndroidJUnitRunner
```

The Android change is an integration test of the Android application/storage/BASS boundary;
there are no changed platform production files. The original report remains unresolved pending
reproduction of the reported failure and audible acceptance. Active profile edits and restored-queue native transitions
also remain separate acceptance items.

## Pixel 6a native execution

The owner subsequently made a USB-connected Pixel 6a available and authorized testing. The phone
reports Android 17 / API 37. Existing Naviamp, v2 Test and Cast test installations were preserved.
An ignored Gradle init script assigned the disposable package `app.naviamp.android.sven520`, with
its own instrumentation package and fresh synthetic source. No real server credentials were used.

The production code from PR head `977c538a3` passes the native probe with three 30-second WAV
tracks from the loopback fixture, with audio caching disabled. At 25 seconds into the first track:

| Saved album mode | Native crossfade duration | Native crossfade active | Prepared next state | Next within-album track |
| --- | --- | --- | --- | --- |
| Gapless | Off | false | Ready, correct second track | Advances with duration Off and album profile retained |
| Inherit | 8s | true | Ready, correct second track | Advances with duration 8s and album profile retained |

The strengthened current-branch run reports `OK (1 test)` in 62.274 seconds; both cases run inside
that one test. It exercises the Core command used by the album Play button, Android's actual
file-backed profile repository and native BASS preparation/advance. It does not automate the
settings dialog or physically tap the Play button, and it does not measure audible continuity.
This does not reproduce Sven's reported override loss for this fresh-source WAV scenario.

The initial device attempt found a test-harness error: external playback media IDs have a queue
prefix and cannot be compared directly with raw track IDs. The test now compares raw identity
through Now Playing, while using the external bridge's state and opaque identity for transitions.
The first timeout is not evidence of a product playback failure. The final test additionally
requires the correct prepared native stream to be Ready, preventing an unprepared sequential
advance from satisfying the probe.

Logs and APKs remain under the worktree's ignored build directories. The successful strengthened
run is `build/sven-review/pixel-album-profile-instrumentation.log`; earlier attempts have separate
logs.

The identical strengthened test also passes against v2.10.0's unmodified production source at
`6e602832a`, rebuilt as the same isolated debug package. The detached baseline checkout has no
tracked differences; its only added source is this test. The test SHA-256 matches in both trees
(`3f42b4fb06743026b35b7a4f2fbca84589789b79ffa53688cd08761a64891cea`). Both cases produce the
same native values in the table above; the release-source run reports `OK (1 test)` in 63.593
seconds. Its log is copied to `build/sven-review/pixel-v2100-album-profile-instrumentation.log`.
This is a debug rebuild of the release source, not the signed public release binary.

The clean-source WAV reproduction therefore does not fail on either the release or PR production
tree. Do not infer that a newer mainline change fixed Sven's report. The remaining reproduction
needs the reporter's server/album/format and saved-state details, plus audible verification.
Active profile edits, restored queues, other codecs and upgraded existing databases are not
covered by these native runs. The original issue remains open.

### Isolated phone reproduction

Save this as an ignored `build/sven-review/isolated-android.init.gradle` before building. It changes
only the debug package ID; product code stays unchanged.

```groovy
gradle.beforeProject { project ->
    project.pluginManager.withPlugin('com.android.application') {
        project.extensions.getByName('androidComponents').finalizeDsl { android ->
            android.buildTypes.getByName('debug').applicationIdSuffix = '.sven520'
        }
    }
}
```

Build `:apps:android:assembleDebug :apps:android:assembleDebugAndroidTest` using that init script
and `-Pandroid.injected.build.abi=arm64-v8a` (quote the complete property argument in PowerShell).
Verify both APK metadata files identify `.sven520` / `.sven520.test` before installation. With this
injected ABI property the APKs are under `apps/android/build/intermediates/apk/`. Install using
`adb -s <serial> install -t <apk>` on a device without those disposable packages already present.
Do not clear an existing personal installation. The test refuses a nonempty source setup.

Start the fixture with `--tracks 3 --albums 1 --track-seconds 30 --burst-seconds 4 --artwork`,
reverse port 18080 on the selected device, and run the earlier instrumentation command with the
runner package replaced by `app.naviamp.android.sven520.test` and an explicit `-s <serial>`.
The two mode checks take approximately one minute in total. The temporary packages and owned
reverse mapping were removed after recording both results, and the owned loopback fixture was
stopped. Package inventory afterward matches the original three Naviamp installations.

## Real Navidrome with FLAC

The owner then identified Navidrome and tentatively FLAC. To avoid treating the earlier Subsonic
fixture as real Navidrome coverage, started a separate loopback-only
[Navidrome 0.64.2 release](https://github.com/navidrome/navidrome/releases/tag/v0.64.2), build
`10114574`, with its own empty data directory, a disposable `fixture` account and no personal
library. The Windows AMD64 archive SHA-256 matches the official asset digest:
`72e06c9101fca6b14ff1437f9a56beb7ad6fe4bd7f25ff62cd5b70a075fee305`.

Generated three tagged 30-second stereo FLAC tracks at 44.1 kHz / 16-bit in one album. The real
server reports `type=navidrome`, `openSubsonic=true`, `contentType=audio/flac`, `suffix=flac`,
correct track order and a 90-second album duration. Audio caching remains disabled on the phone.
All 26 streaming log entries across these two runs report original FLAC, raw delivery and
`transcoding=false`; the test is not silently exercising a converted WAV or MP3 stream.

Reused the same two isolated APK builds and identical strengthened instrumentation test from the
WAV comparison. The production trees remain PR `977c538a3` and release `6e602832a`; no product
source was changed to make this pass.

| Production source | Result | Gapless before advance | Inherited control before advance |
| --- | --- | --- | --- |
| PR | OK, 1 test in 61.920 seconds | Off / inactive / correct next stream Ready | 8s / active / correct next stream Ready |
| v2.10.0 source | OK, 1 test in 63.323 seconds | Off / inactive / correct next stream Ready | 8s / active / correct next stream Ready |

Both modes advance to the correct second track and retain their album profiles. This still does
not reproduce Sven's reported failure. It establishes native override behavior with real
Navidrome and original FLAC in a fresh installation; it does not establish audible continuity,
the behavior of Sven's exact album/server version, upgraded existing profile state, cached files,
active edits or restored queues. Keep #520 open.

Evidence under ignored `build/sven-review/`: `pixel-navidrome-flac-current.log`,
`pixel-navidrome-flac-v2100.log`, and `navidrome/` containing server logs, album metadata, release
and FLAC checksums, configuration and the stream-verification summary. The ordinary phone apps
are preserved; the session-owned `.sven520` packages and reverse mapping were removed afterward,
and the owned Navidrome server was stopped.

### FLAC reproduction delta

`scripts/generate-playback-profile-flac.py` creates the album and refuses a nonempty output
directory. Its output was decoded and checked for all three durations, channel/sample format and
album/track tags. The run used numpy 2.5.4, soundfile 0.14.0 and mutagen 1.48.1, installed only in
the ignored local tool directory. For example, in PowerShell:

```powershell
python3.14 -m pip install --target build/sven-review/python-libs numpy soundfile mutagen
$env:PYTHONPATH = (Resolve-Path build/sven-review/python-libs).Path
python3.14 scripts/generate-playback-profile-flac.py build/sven-review/navidrome/music
```

Use the real server in place of `android-tv-fixture.py`, bound only to `127.0.0.1:18080` with fresh
DataFolder/MusicFolder paths, external services disabled, and a working FFmpeg path. The test
used FFmpeg 9.0.2 (no stream transcoding occurred). Create the disposable `fixture` / `fixture`
account using that server's first-admin setup. Wait until its API lists exactly the one album
with three 30-second tracks, then use the same isolated-phone instrumentation command above.
Do not point this server or the generator at an existing personal library or database.
