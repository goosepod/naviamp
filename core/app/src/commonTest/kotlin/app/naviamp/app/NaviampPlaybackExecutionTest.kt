package app.naviamp.app

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.PlaybackSeekPlan
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampPlaybackExecutionTest {
    @Test
    fun playPauseRequestsUseSharedPlaybackState() {
        val execution = RecordingPlaybackExecution()
        val playback = NaviampLivePlaybackController(NaviampLivePlaybackState(playbackState = PlaybackState.Playing))
        val controller = NaviampPlaybackCommandController(execution, playback)

        assertEquals(true, controller.playPause(hasPlaybackTarget = true))
        playback.updatePlaybackState(PlaybackState.Paused)
        assertEquals(true, controller.playPause(hasPlaybackTarget = true))
        playback.updatePlaybackState(PlaybackState.Stopped)
        assertEquals(true, controller.playPause(hasPlaybackTarget = true))
        playback.updatePlaybackState(PlaybackState.Idle)
        assertEquals(false, controller.playPause(hasPlaybackTarget = false))
        controller.stop()

        assertEquals(1, execution.pauses)
        assertEquals(1, execution.resumes)
        assertEquals(1, execution.startOrRestores)
        assertEquals(1, execution.stops)
    }

    @Test
    fun seekPlansChooseOnePlatformExecutionPath() {
        val execution = RecordingPlaybackExecution()
        val controller = NaviampPlaybackCommandController(execution, NaviampLivePlaybackController())
        val ordinarySeekPositionSeconds = 12.5

        controller.executeSeek(
            seekPlan(positionSeconds = ordinarySeekPositionSeconds, replayCurrent = false),
        )
        controller.executeSeek(seekPlan(positionSeconds = 0.0, replayCurrent = true))

        assertEquals(listOf(ordinarySeekPositionSeconds), execution.seeks)
        assertEquals(listOf(0.0), execution.replays)
    }

    @Test
    fun seekRequestsPlanAndPublishPendingStateBeforeExecution() {
        val execution = RecordingPlaybackExecution()
        val playback = NaviampLivePlaybackController(
            NaviampLivePlaybackState(
                currentTrack = track("current"),
                progress = PlaybackProgress(positionSeconds = 4.0, durationSeconds = 180.0),
            ),
        )
        val controller = NaviampPlaybackCommandController(execution, playback)

        val plan = controller.seek(
            NaviampPlaybackSeekRequest(
                positionSeconds = 30.0,
                streamQuality = StreamQuality.Original,
                playbackSource = PlaybackSource.CachedFile,
                issuedAtMillis = 1_000L,
            ),
        )

        assertEquals(30.0, plan?.pendingSeekPositionSeconds)
        assertEquals(30.0, playback.state.value.progress.positionSeconds)
        assertEquals(30.0, playback.state.value.pendingSeekPositionSeconds)
        assertEquals(1_000L, playback.state.value.pendingSeekIssuedAtMillis)
        assertEquals(listOf(30.0), execution.seeks)
    }

    private fun seekPlan(positionSeconds: Double, replayCurrent: Boolean) = PlaybackSeekPlan(
        progress = PlaybackProgress(positionSeconds = positionSeconds, durationSeconds = 180.0),
        shouldReplayCurrent = replayCurrent,
        pendingSeekPositionSeconds = positionSeconds,
    )

    private fun track(id: String) = Track(
        id = TrackId(id),
        title = "Track $id",
        artistName = "Artist",
        albumTitle = "Album",
        durationSeconds = 180,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )
}

private class RecordingPlaybackExecution : NaviampPlaybackExecution {
    var pauses = 0
    var resumes = 0
    var startOrRestores = 0
    var stops = 0
    val seeks = mutableListOf<Double>()
    val replays = mutableListOf<Double>()

    override fun pause() {
        pauses += 1
    }

    override fun resume() {
        resumes += 1
    }

    override fun startOrRestore(): Boolean {
        startOrRestores += 1
        return true
    }

    override fun seek(positionSeconds: Double) {
        seeks += positionSeconds
    }

    override fun replayCurrent(positionSeconds: Double) {
        replays += positionSeconds
    }

    override fun stop() {
        stops += 1
    }
}
