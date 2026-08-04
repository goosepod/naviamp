package app.naviamp.testkit

import app.naviamp.app.NaviampApplicationSession
import app.naviamp.app.NaviampPlaybackExecution
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class NaviampAdapterContractsTest {
    @Test
    fun playbackContractIsReusableAcrossHostHarnesses() {
        verifyNaviampPlaybackExecutionContract(RecordingPlaybackHarness())
    }

    @Test
    fun playbackContractPreservesAnUnavailableStartOrRestoreResult() {
        verifyNaviampPlaybackExecutionContract(
            harness = RecordingPlaybackHarness(startOrRestoreResult = false),
            expectedStartOrRestoreResult = false,
        )
    }

    @Test
    fun lifecycleContractIsReusableAcrossHostHarnesses() = runTest {
        verifyNaviampApplicationSessionContract(RecordingSessionHarness())
    }
}

private class RecordingPlaybackHarness(
    private val startOrRestoreResult: Boolean = true,
) : NaviampPlaybackExecutionContractHarness {
    private val mutableCalls = mutableListOf<NaviampPlaybackExecutionCall>()
    override val calls: List<NaviampPlaybackExecutionCall> get() = mutableCalls
    override val execution = object : NaviampPlaybackExecution {
        override fun pause() { mutableCalls += NaviampPlaybackExecutionCall.Pause }
        override fun resume() { mutableCalls += NaviampPlaybackExecutionCall.Resume }
        override fun startOrRestore(): Boolean {
            mutableCalls += NaviampPlaybackExecutionCall.StartOrRestore
            return startOrRestoreResult
        }
        override fun seek(positionSeconds: Double) {
            mutableCalls += NaviampPlaybackExecutionCall.Seek(positionSeconds)
        }
        override fun replayCurrent(positionSeconds: Double) {
            mutableCalls += NaviampPlaybackExecutionCall.ReplayCurrent(positionSeconds)
        }
        override fun setVolume(percent: Int) { mutableCalls += NaviampPlaybackExecutionCall.SetVolume(percent) }
        override fun stop() { mutableCalls += NaviampPlaybackExecutionCall.Stop }
    }
}

private class RecordingSessionHarness : NaviampApplicationSessionContractHarness {
    private val mutableCalls = mutableListOf<NaviampApplicationSessionCall>()
    override val calls: List<NaviampApplicationSessionCall> get() = mutableCalls
    override val session = object : NaviampApplicationSession {
        override suspend fun restore() { mutableCalls += NaviampApplicationSessionCall.Restore }
        override suspend fun enterForeground() { mutableCalls += NaviampApplicationSessionCall.EnterForeground }
        override suspend fun enterBackground() { mutableCalls += NaviampApplicationSessionCall.EnterBackground }
        override suspend fun shutdown() { mutableCalls += NaviampApplicationSessionCall.Shutdown }
    }
}
