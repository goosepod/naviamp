package app.naviamp.android

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.ui.NaviampVisualizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AndroidPlaybackSessionControllerTest {
    @Test
    fun restoredTrackSessionSynchronizesOccurrenceAwareQueueController() {
        val duplicate = track("duplicate")
        val tracks = listOf(track("before"), duplicate, track("middle"), duplicate, track("after"))
        val session = assertNotNull(
            PlaybackSessionSettings.fromTracks(
                tracks = tracks,
                currentIndex = 3,
                positionSeconds = 42.0,
            ),
        )
        val state = appState()
        val queueController = PlaybackQueueController(
            PlaybackQueue(listOf(track("stale")), currentIndex = 0),
        )

        assertTrue(
            restoreAndroidPlaybackSession(
                state = state,
                playbackSessionRepository = TestPlaybackSessionRepository(session),
                sourceId = "source",
                loadRelatedTracks = {},
                synchronizePlaybackQueue = queueController::restoreOrClear,
            ),
        )

        assertEquals(3, state.playbackQueue.currentIndex)
        assertEquals(state.playbackQueue, queueController.queue)
        assertFalse(state.nowPlayingOpen)
        assertEquals("after", assertNotNull(queueController.next()).track?.id?.value)
    }

    private fun appState(): AndroidAppState =
        AndroidAppState(
            savedConnection = ConnectionFormState(),
            savedInterfaceSettings = InterfaceSettings(),
            savedPlaybackSettings = PlaybackSettings(),
            savedCacheSettings = CacheSettings(),
            canAutoConnect = false,
            savedSourceId = "source",
            initialSavedMediaSources = emptyList(),
            initialSavedConnectionForLogin = null,
            initialStorageStats = StorageCacheStats(),
            initialOpenNowPlayingRequest = 0,
            initialAutoPlayMediaIdRequest = null,
            initialAutoCommandRequest = null,
            initialSelectedVisualizer = NaviampVisualizer.SpectrumBars,
        )

    private fun track(id: String): Track =
        Track(
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

private class TestPlaybackSessionRepository(
    private val session: PlaybackSessionSettings?,
) : PlaybackSessionRepository {
    override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? = session

    override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) = Unit
}
