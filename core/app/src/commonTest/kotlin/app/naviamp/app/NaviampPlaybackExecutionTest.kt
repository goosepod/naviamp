package app.naviamp.app

import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackSeekPlan
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampPlaybackExecutionTest {
    @Test
    fun seekPlansChooseOnePlatformExecutionPath() {
        val execution = RecordingPlaybackExecution()
        val controller = NaviampPlaybackCommandController(execution)
        val ordinarySeekPositionSeconds = 12.5

        controller.executeSeek(
            seekPlan(positionSeconds = ordinarySeekPositionSeconds, replayCurrent = false),
        )
        controller.executeSeek(seekPlan(positionSeconds = 0.0, replayCurrent = true))

        assertEquals(listOf(ordinarySeekPositionSeconds), execution.seeks)
        assertEquals(listOf(0.0), execution.replays)
    }

    private fun seekPlan(positionSeconds: Double, replayCurrent: Boolean) = PlaybackSeekPlan(
        progress = PlaybackProgress(positionSeconds = positionSeconds, durationSeconds = 180.0),
        shouldReplayCurrent = replayCurrent,
        pendingSeekPositionSeconds = positionSeconds,
    )
}

private class RecordingPlaybackExecution : NaviampPlaybackExecution {
    val seeks = mutableListOf<Double>()
    val replays = mutableListOf<Double>()

    override fun seek(positionSeconds: Double) {
        seeks += positionSeconds
    }

    override fun replayCurrent(positionSeconds: Double) {
        replays += positionSeconds
    }
}
