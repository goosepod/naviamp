package app.naviamp.desktop

import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.queue.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopLivePlaybackPropertyTest {
    @Test
    fun typedPropertiesWriteIntoOneSharedPlaybackSnapshot() {
        val controller = NaviampLivePlaybackController()
        var playbackState by desktopPlaybackStateProperty(controller)
        var progress by desktopPlaybackProgressProperty(controller)
        var repeatMode by desktopRepeatModeProperty(controller)
        val updatedProgress = PlaybackProgress(positionSeconds = 15.0, durationSeconds = 180.0)

        playbackState = PlaybackState.Playing
        progress = updatedProgress
        repeatMode = RepeatMode.Queue

        assertEquals(PlaybackState.Playing, playbackState)
        assertEquals(updatedProgress, progress)
        assertEquals(PlaybackState.Playing, controller.state.value.playbackState)
        assertEquals(updatedProgress, controller.state.value.progress)
        assertEquals(RepeatMode.Queue, repeatMode)
        assertEquals(RepeatMode.Queue, controller.state.value.repeatMode)
    }
}
