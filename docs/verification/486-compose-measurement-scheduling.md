# API 30 smart-playlist test scheduling

Issue #486 tracks intermittent `performMeasureAndLayout called during measure layout` in
`NaviampSmartPlaylistSummaryUiTest.collapsingOptionsAndAdvancedPreservesTheWholeDefinition`.

## Preserved failure

Verify 42364866521, API 30 job 128381160295, fails one of 37 rendered cases at source
`99541488d`. The same test previously failed in 41308234011 and passed an unchanged isolated
job rerun. The new stack reaches `AndroidComposeView.measureAndLayoutForTest` through
`TestMonotonicFrameClock.performFrame` and the coroutine test scheduler. It is separate from
the emulator-offline teardown failure in 42362487723.

The test blob is identical on accepted main and the album-profile branch:
`8c07f8b004951a907a7ed6be6df738b382131bcd`. The failed XML/logs remain in the hosted artifact
and privately under `build/ios-gh-investigation/pr532-api30`. No customer-app crash or precise
framework cause is established by that test-only stack.

## Candidate

Use `androidx.compose.ui.test.v2.runComposeUiTest` in this shared rendered test class only.
The [official migration guide](https://developer.android.com/develop/ui/compose/testing/migrate-v2)
documents its queued `StandardTestDispatcher` default in place of the older unconfined
composition dispatcher. This removes eager scheduling from the test host; it is a candidate
for the captured frame/layout reentrancy, not a production smart-playlist UI workaround.

All six existing cases, collapse/expand/scroll actions, five-second save wait and complete
definition equality assertions remain unchanged. No test is skipped, no sleep is added, and
no animation or application code changes.

Local validation passes all six JVM rendered cases in 13.357 seconds with zero failures,
errors or skips, builds the Android instrumentation APK, and passes the architecture check.
All 31 existing selected workflow-helper guards also pass.

## Hosted acceptance

The full required matrix remains intact. Label `verify:android-ui-repeat` additionally requires
the API 30 job to execute the complete six-case class three more times, each as an independent
instrumentation invocation. Gradle's task is explicitly rerun, the loop stops on the first failure,
and every report/log is retained under `build/android-compose-replays` in the device artifact.
The original full rendered report is preserved before filtered repeats overwrite task outputs.

Acceptance requires the initial 37 rendered cases, all required Android app-native contracts,
three complete six-case repeats, the API 35 controls and all other standard jobs. Inspect the
XML identities and counts before accepting the result; a passing retry alone does not prove a
general framework cause. Final exact-head results are recorded on the linked PR.

The repeat step uses only the workflow-created disposable AVD. No owner emulator, physical
phone, TV, profile or server data is reset or altered. No Android/Desktop/iOS production file,
setting, string resource or persistence schema changes.
