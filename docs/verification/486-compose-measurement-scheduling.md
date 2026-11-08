# API 30 smart-playlist test lifetime

Issue #486 tracks intermittent `performMeasureAndLayout called during measure layout` in
`NaviampSmartPlaylistSummaryUiTest.collapsingOptionsAndAdvancedPreservesTheWholeDefinition`.

## Preserved controls

Verify 42364866521, API 30 job 128381160295, fails one of 37 rendered cases at source
`99541488d`. The same test previously failed in 41308234011 and passed an unchanged isolated
job rerun. Its unchanged test blob was `8c07f8b004951a907a7ed6be6df738b382131bcd`.
These are separate from the emulator-offline teardown failure in 42362487723 and #330's
initial raster visibility failures. No customer-app crash is established by this test-only stack.

The first candidate switched this six-case class to the queued v2 Compose test host. All six
local JVM cases and the initial 37 hosted rendered cases passed, but Verify 42367564760 at
`ff5d41a89` failed the first independent six-case API 30 replay with the same exception.
The stack explicitly invokes the v2 host. Attempts 2 and 3 did not run. That candidate did
not fix the issue; its failed artifact is preserved privately under
`build/ios-gh-investigation/pr534-api30`.

Before the lifetime correction, the refreshed candidate also passed five independent six-case
runs on the attached Pixel 6a (Android 17/API 37). Those 30 passing cases do not reproduce
or disprove the API 30 race. Reports: `build/486-phone-baseline/run-*.txt` in the issue worktree.

## Test lifetime correction

The captured failing stack calls `TestMonotonicFrameClock.performFrame` through a nested
coroutine `runTest` at `ComposeUiTest.android.kt:1122` (an inline expansion). The exact
AndroidX ui-test-android 1.11.1 source has this nested run in `withTestCoroutines` cleanup:
`frameClockCoroutineScope.runTest {}`. Normal main-clock advancement marshals to the UI
thread; that final frame-scope drain does not use the main-clock wrapper. This identifies
cleanup as the path reaching the failing layout, not the exact OS interleaving that made it race.

The fixture previously left the native dialog open with a no-op dismissal handler, returned
as soon as `onSave` assigned a plain variable, and did not synchronize the resulting UI work.
The shared production save action still has saving-state updates and dismissal to perform
after that callback returns. A completed callback is not a completed dialog lifetime.

The shared rendered fixture now owns dialog visibility, honors its dismissal callback, awaits
Compose idleness, asserts that the save control disappears, and checks the complete saved
definition on the idle UI thread. It retains the v2 host. Every collapse/expand/scroll action,
the five-second save wait, and the definition equality assertion remain; dismissal adds an
assertion. No sleep, longer wait, exception suppression, test skip, animation change or
application workaround is introduced.

The application already performs the dismissal; no production change is needed. No Android,
Desktop or iOS production source, setting, resource string, schema or personal profile changes.

## Acceptance

Run all six JVM cases, build the standalone Android instrumentation APK, and run the architecture
check. Install only `app.naviamp.ui.test` on the attached phone and execute the complete six-case
class ten independent times; retain each instrumentation result. This fixture uses synthetic
in-memory definitions and neither connects to a server nor writes a user playlist.

The full required GitHub matrix remains intact. Label `verify:android-ui-repeat` additionally
requires ten independent API 30 class executions after the normal 37 rendered cases. The task
is explicitly rerun, each report/log is retained under `build/android-compose-replays`, and the
loop stops at its first failure, after preserving that failure's report. No retry-until-green loop.

Acceptance requires 37 initial rendered cases plus 60 repeated cases, all required Android
app-native contracts, API 35 controls and every other standard job. Audit XML identities and
counts, including required non-skipped native TLS contracts, before accepting. Exact-head
results and phone counts are recorded on PR #534. A finite passing stress run is evidence for
this test-lifetime correction, not proof of a general AndroidX framework fix.

The CI step uses only its disposable AVD. No owner emulator, phone, TV, profile or server data
is reset. The physical phone test package is isolated from installed Naviamp application profiles.

## Local results, 2026-11-07

The corrected fixture passes all six JVM cases in 12.458 seconds, with zero failures,
errors or skips. The instrumentation APK builds and `verifyCoreFirstArchitecture` passes.
On the attached, unlocked Pixel 6a, ten independent invocations each report `OK (6 tests)`:
60 passing cases, no failures or ignored cases. Private logs are in
`build/486-phone-candidate/run-1.txt` through `run-10.txt` in the issue worktree.
Only the standalone `app.naviamp.ui.test` package was installed; personal application
profiles and server data were not used.

The workflow condition-duplication guard passes locally. Its separate real-history scope
classifier test cannot run completely on Windows: it requires Bash and creates a filename
containing a newline, which Windows rejects. That unchanged Linux-specific test remains
required on the hosted runner; its local failure is not counted as passing validation.
