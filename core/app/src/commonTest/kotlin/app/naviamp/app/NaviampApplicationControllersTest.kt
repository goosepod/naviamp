package app.naviamp.app

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class NaviampApplicationControllersTest {
    @Test
    fun queueCoordinatorMutatesTheBundledPlaybackController() {
        val controllers = NaviampApplicationControllers()
        val track = Track(
            id = TrackId("track"),
            title = "Track",
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )

        controllers.queue.startQueue(listOf(track), index = 0)

        assertEquals(track, controllers.playback.state.value.queue.current)
    }

    @Test
    fun fromPreservesControllersWhileCreatingTheirSharedQueueOwner() {
        val navigation = NaviampNavigationController()
        val playback = NaviampLivePlaybackController()
        val connection = NaviampConnectionController()

        val controllers = NaviampApplicationControllers.from(navigation, playback, connection)

        assertSame(navigation, controllers.navigation)
        assertSame(playback, controllers.playback)
        assertSame(connection, controllers.connection)
    }

    @Test
    fun controllerGraphIncludesConnectionLifecycleState() {
        val controllers = NaviampApplicationControllers()

        assertEquals(NaviampConnectionPhase.Disconnected, controllers.connection.state.value.phase)
    }
}
