package app.naviamp.app

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaviampLivePlaybackControllerTest {
    @Test
    fun ordinaryTrackSelectionClearsAStaleInternetRadioStation() {
        val station = InternetRadioStation("station", "Station", "https://radio.example.test")
        val controller = NaviampLivePlaybackController(
            NaviampLivePlaybackState(currentStation = station),
        )

        controller.updateCurrentTrack(track("ordinary"))

        assertNull(controller.state.value.currentStation)
    }

    @Test
    fun changingTracksClearsProgressUntilTheNewEngineStreamReportsIt() {
        val controller = NaviampLivePlaybackController(
            NaviampLivePlaybackState(
                currentTrack = track("old"),
                progress = PlaybackProgress(42.0, 180.0),
            ),
        )

        controller.updateCurrentTrack(track("new"))

        assertEquals(PlaybackProgress.Unknown, controller.state.value.progress)
        assertEquals(PlaybackProgress.Unknown, controller.progress.value)
    }

    @Test
    fun progressHasADedicatedObservableState() {
        val controller = NaviampLivePlaybackController()
        val progress = PlaybackProgress(positionSeconds = 12.0, durationSeconds = 180.0)

        controller.updateProgress(progress)

        assertEquals(progress, controller.progress.value)
        assertEquals(progress, controller.state.value.progress)
    }

    @Test
    fun fieldUpdatesPreserveTheRestOfThePlaybackSnapshot() {
        val track = track("one")
        val queue = PlaybackQueue(listOf(track), currentIndex = 0)
        val controller = NaviampLivePlaybackController(
            NaviampLivePlaybackState(currentTrack = track, queue = queue),
        )
        val station = InternetRadioStation("station", "Station", "https://radio.example.test")
        val progress = PlaybackProgress(positionSeconds = 12.0, durationSeconds = 180.0)

        controller.updateCurrentStation(station)
        controller.updateProgress(progress)
        controller.updatePlaybackState(PlaybackState.Playing)
        controller.updateRepeatMode(RepeatMode.Queue)

        assertEquals(track, controller.state.value.currentTrack)
        assertEquals(station, controller.state.value.currentStation)
        assertEquals(queue, controller.state.value.queue)
        assertEquals(progress, controller.state.value.progress)
        assertEquals(PlaybackState.Playing, controller.state.value.playbackState)
        assertEquals(RepeatMode.Queue, controller.state.value.repeatMode)
    }

    @Test
    fun replacesRestoredPlaybackStateAtomically() {
        val track = track("restored")
        val restored = NaviampLivePlaybackState(
            currentTrack = track,
            queue = PlaybackQueue(listOf(track), currentIndex = 0),
            progress = PlaybackProgress(positionSeconds = 42.0, durationSeconds = 180.0),
            playbackState = PlaybackState.Paused,
        )
        val controller = NaviampLivePlaybackController()

        controller.replace(restored)

        assertEquals(restored, controller.state.value)
        assertEquals(restored.progress, controller.progress.value)
    }

    @Test
    fun queueChangePublishesBeforePersistence() {
        val track = track("queued")
        val queue = PlaybackQueue(listOf(track), currentIndex = 0)
        val controller = NaviampLivePlaybackController()
        var persistedQueue: PlaybackQueue? = null
        var publishedQueueAtPersistence: PlaybackQueue? = null

        controller.applyQueueChange(queue, positionSeconds = 17.0) { savedQueue, _ ->
            persistedQueue = savedQueue
            publishedQueueAtPersistence = controller.state.value.queue
        }

        assertEquals(queue, persistedQueue)
        assertEquals(queue, publishedQueueAtPersistence)
    }

    @Test
    fun playbackStateChangePublishesBeforeReporting() {
        val progress = PlaybackProgress(positionSeconds = 23.0, durationSeconds = 180.0)
        val controller = NaviampLivePlaybackController(
            NaviampLivePlaybackState(progress = progress),
        )
        var reportedState: PlaybackState? = null
        var publishedStateAtReport: PlaybackState? = null

        controller.applyPlaybackStateChange(PlaybackState.Playing) { state, reportedProgress ->
            reportedState = state
            publishedStateAtReport = controller.state.value.playbackState
            assertEquals(progress, reportedProgress)
        }

        assertEquals(PlaybackState.Playing, reportedState)
        assertEquals(PlaybackState.Playing, publishedStateAtReport)
    }

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
