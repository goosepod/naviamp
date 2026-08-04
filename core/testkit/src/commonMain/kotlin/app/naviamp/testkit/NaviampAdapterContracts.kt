package app.naviamp.testkit

import app.naviamp.app.NaviampApplicationSession
import app.naviamp.app.NaviampPlaybackExecution

/** Standard observations used by every native playback adapter contract test. */
sealed interface NaviampPlaybackExecutionCall {
    data object Pause : NaviampPlaybackExecutionCall
    data object Resume : NaviampPlaybackExecutionCall
    data object StartOrRestore : NaviampPlaybackExecutionCall
    data class Seek(val positionSeconds: Double) : NaviampPlaybackExecutionCall
    data class ReplayCurrent(val positionSeconds: Double) : NaviampPlaybackExecutionCall
    data class SetVolume(val percent: Int) : NaviampPlaybackExecutionCall
    data object Stop : NaviampPlaybackExecutionCall
}

/**
 * Harness implemented by Android, Desktop, iOS, and fake adapters.
 *
 * A host test records the native effects caused by its real adapter as standardized calls. The
 * common contract then proves the complete narrow boundary without duplicating expectations.
 */
interface NaviampPlaybackExecutionContractHarness {
    val execution: NaviampPlaybackExecution
    val calls: List<NaviampPlaybackExecutionCall>
}

fun verifyNaviampPlaybackExecutionContract(
    harness: NaviampPlaybackExecutionContractHarness,
    expectedStartOrRestoreResult: Boolean = true,
) {
    harness.execution.pause()
    harness.execution.resume()
    check(harness.execution.startOrRestore() == expectedStartOrRestoreResult) {
        "Playback adapter returned the wrong start-or-restore result."
    }
    harness.execution.seek(12.5)
    harness.execution.replayCurrent(7.25)
    harness.execution.setVolume(37)
    harness.execution.stop()

    check(
        harness.calls == listOf(
            NaviampPlaybackExecutionCall.Pause,
            NaviampPlaybackExecutionCall.Resume,
            NaviampPlaybackExecutionCall.StartOrRestore,
            NaviampPlaybackExecutionCall.Seek(12.5),
            NaviampPlaybackExecutionCall.ReplayCurrent(7.25),
            NaviampPlaybackExecutionCall.SetVolume(37),
            NaviampPlaybackExecutionCall.Stop,
        ),
    ) { "Playback adapter did not preserve the complete Core execution contract: ${harness.calls}" }
}

/** Harness for lifecycle adapters and their observable native/session effects. */
enum class NaviampApplicationSessionCall {
    Restore,
    EnterForeground,
    EnterBackground,
    Shutdown,
}

interface NaviampApplicationSessionContractHarness {
    val session: NaviampApplicationSession
    val calls: List<NaviampApplicationSessionCall>
}

suspend fun verifyNaviampApplicationSessionContract(harness: NaviampApplicationSessionContractHarness) {
    harness.session.restore()
    harness.session.enterForeground()
    harness.session.enterBackground()
    harness.session.shutdown()

    check(
        harness.calls == listOf(
            NaviampApplicationSessionCall.Restore,
            NaviampApplicationSessionCall.EnterForeground,
            NaviampApplicationSessionCall.EnterBackground,
            NaviampApplicationSessionCall.Shutdown,
        ),
    ) { "Application-session adapter did not preserve lifecycle ordering: ${harness.calls}" }
}
