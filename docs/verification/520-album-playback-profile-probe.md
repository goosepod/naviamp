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

Device execution is deferred at the owner's request. Nothing was installed or run on the phone.
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
native execution and audible acceptance. Active profile edits and restored-queue native transitions
also remain separate acceptance items.
