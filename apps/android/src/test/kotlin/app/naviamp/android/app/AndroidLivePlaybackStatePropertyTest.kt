package app.naviamp.android

import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.domain.playback.PlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidLivePlaybackStatePropertyTest {
    @Test
    fun writesAndroidPlaybackSnapshotIntoSharedController() {
        val controller = NaviampLivePlaybackController()
        var state by AndroidLivePlaybackStateProperty(controller)
        val playing = NaviampLivePlaybackState(playbackState = PlaybackState.Playing)

        state = playing

        assertEquals(playing, state)
        assertEquals(playing, controller.state.value)
    }
}
