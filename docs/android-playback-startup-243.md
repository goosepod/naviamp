# Android playback service startup acceptance — #243

## Failure and ownership

On 2026-10-14, physical Pixel 10a `5A131JEA306253` (Android 17/API 37) crash history confirmed
the October 8 benchmark failure: `ForegroundServiceDidNotStartInTimeException` in
`AndroidNaviampPlaybackService`. It also contained October 10 failures in the normal published
package from `ForegroundServiceStartNotAllowedException` at the lifecycle's native start call.

The timeout is a race between an accepted start and the latest snapshot: Core requests service
retention during loading/playback, but an idle or failed snapshot reaches the service before its
start callback. Snapshot-dependent publication then skips the required foreground acknowledgment.

Common presentation now owns two separate responsibilities:

- `NaviampPlaybackServicePresentationCoordinator` acknowledges every accepted start regardless of
  the latest snapshot, preserves transient-idle retention, and keeps natural progress ticks out of
  notification updates.
- `NaviampPlaybackServiceLifecycleCoordinator` records accepted/rejected native starts. A rejected
  start retries on the next transition to a visible application surface. Stable idle cancels the
  deferred start; there is no polling or automatic background retry loop. Existing shared retention
  grace-period behavior is unchanged.

Android translates its background-start exception into a deferred result. Other native failures
continue to propagate. The explicit foreground-start intent is acknowledged synchronously before
attaching/publishing a newer bridge snapshot.

Android's documented [startup deadline](https://developer.android.com/develop/background-work/services/fgs/troubleshooting)
and [background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
are separate requirements; the fix covers both.

## Physical-device evidence

All phone testing used a separate `app.naviamp.android.crash243` package and its test APK.
Existing production, benchmark, and v2test installations and their data were preserved.

The baseline APK used accepted main `95907f99` plus the unwired common coordinator. With an empty
profile and the Activity visible, a foreground start was delivered while the playback snapshot was
idle. The phone crashed at **09:11:52 PDT**:

```text
Process: app.naviamp.android.crash243, PID: 17153
RemoteServiceException$ForegroundServiceDidNotStartInTimeException:
Context.startForegroundService() did not then call Service.startForeground()
app.naviamp.android.crash243/app.naviamp.android.AndroidNaviampPlaybackService
```

The fixed APK passed `AndroidPlaybackServiceStartupInstrumentedTest`: **4 tests**, 55.795 seconds.
An idle snapshot and a missing bridge both produced a real foreground notification within two
seconds and survived another 25 seconds, beyond the observed native timeout. The other tests verify
translation of Android's rejection exception and propagation of unrelated failures. The rejection
exception is injected at the native Context boundary; it is not a claim that a fresh OS rejection
was forced during this run. Actual background rejection is established by the existing phone logs.

`AndroidPlaybackRecoveryInstrumentedTest`: **1 test**, 47.005 seconds, passed with the real BASS
engine, MediaSession, foreground service and loopback fixture. Fixture configuration: three
600-second mono 16 kHz WAV tracks, four-second initial burst followed by throttled delivery.
Observed checkpoints:

```text
Playback recovery: pause/resume completed
Playback recovery: unreachable provider and rapid selections survived the startup deadline
Playback recovery: restored transport played with moving progress and survived background/restore
OK (1 test)
```

The test drains interrupted playback to idle, waits through the release grace period, selects three
tracks against an unavailable provider, observes 25 seconds without a timeout crash, restores
transport, verifies advancing playback position, backgrounds/restores the Activity and stops playback.
No seek/buffering-engine behavior is changed or claimed by this fix.

## Reproduction

Use a disposable debug application ID. On Windows the acceptance run used an untracked Gradle init
script to avoid replacing any installed profile:

```groovy
allprojects {
    afterEvaluate {
        if (path == ':apps:android') {
            extensions.getByName('android').buildTypes.getByName('debug').applicationIdSuffix = '.crash243'
        }
    }
}
```

Build app and tests with `-I <init-script>`; keep the dotted `-P` argument quoted in PowerShell:

```text
gradlew -I <init-script> -Pkotlin.native.enableKlibsCrossCompilation=false :apps:android:assembleDebug :apps:android:assembleDebugAndroidTest
adb -s <serial> install -r apps/android/build/outputs/apk/debug/android-debug.apk
adb -s <serial> install -r apps/android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk
adb -s <serial> shell am instrument -w -e class app.naviamp.android.AndroidPlaybackServiceStartupInstrumentedTest app.naviamp.android.crash243.test/androidx.test.runner.AndroidJUnitRunner
```

For the baseline timeout, launch the empty disposable app, wait for its initial idle-retention
decision, then issue the following start and observe the crash buffer beyond the native timeout:

```text
adb -s <serial> shell am start-foreground-service -n app.naviamp.android.crash243/app.naviamp.android.AndroidNaviampPlaybackService -a app.naviamp.android.v2.action.REFRESH_PLAYBACK --ez app.naviamp.android.v2.extra.FOREGROUND_START true
```

For recovery, start the existing fixture and run against an empty disposable test profile:

```text
python scripts/android-tv-fixture.py --burst-seconds 4 --tracks 3
adb -s <serial> reverse tcp:18080 tcp:18080
adb -s <serial> shell am instrument -w -e class app.naviamp.android.AndroidPlaybackRecoveryInstrumentedTest -e playbackRecoveryFixture true app.naviamp.android.crash243.test/androidx.test.runner.AndroidJUnitRunner
```

The recovery test is opt-in and refuses an already connected profile. It restores the fixture and
stops its synthetic playback in `finally`. The startup checks skip an active playback session.

## Validation and platform diff

Before adding host wiring, 28 focused JVM tests passed (19 existing bridge tests, five presentation
tests and four lifecycle tests). Common presentation compiled for Android, JVM, iOS device and iOS
simulator. The Android app/test APKs and `verifyCoreFirstArchitecture` passed afterward.
Full required PR verification remains a release/merge gate; consult PR #279 for its final head.

Changed platform production files and their irreducible boundaries:

- `AndroidNaviampPlaybackService.kt`: Android Service start intents and synchronous foreground
  Notification/MediaSession API effects; presentation decisions delegate to Core.
- `AndroidNaviampPlaybackRuntime.kt`: Android Context service start/stop, the native background-start
  exception, and Android Lifecycle callbacks translated into the shared coordinator.
- `MainActivity.kt`: passes the Activity's Android Lifecycle to that adapter.

No Desktop or iOS production files change. No new setting, migration, or user-facing string is added.
There is no animation scheduling or playback-frequency change.

Remaining release work is planned in [issue #280](https://github.com/goosepod/naviamp/issues/280).
