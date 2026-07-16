package app.naviamp.android

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.SavedTrack
import app.naviamp.ui.NaviampVisualizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    @Test
    fun restoredInternetRadioClearsTrackQueueAndSynchronizesEmptyPlaybackQueue() {
        val station = InternetRadioStation(
            id = "station",
            name = "Deep Space",
            streamUrl = "https://radio.example.test/stream.mp3",
        )
        val state = appState().apply {
            nowPlaying = track("stale")
            tracks = listOf(track("stale"))
            playbackQueue = PlaybackQueue(tracks, currentIndex = 0)
            restoredStartPositionSeconds = 72.0
        }
        var synchronizedQueue: PlaybackQueue? = null
        var relatedTrack: Track? = null

        assertTrue(
            restoreAndroidPlaybackSession(
                state = state,
                playbackSessionRepository = TestPlaybackSessionRepository(
                    PlaybackSessionSettings.fromInternetRadioStation(station),
                ),
                sourceId = "source",
                loadRelatedTracks = { relatedTrack = it },
                synchronizePlaybackQueue = { synchronizedQueue = it },
            ),
        )

        assertNull(state.nowPlaying)
        assertEquals(station, state.nowPlayingStation)
        assertEquals(PlaybackQueue(), state.playbackQueue)
        assertEquals(PlaybackQueue(), synchronizedQueue)
        assertEquals(PlaybackProgress.Unknown, state.playbackProgress)
        assertNull(state.restoredStartPositionSeconds)
        assertNull(relatedTrack)
        assertEquals("Restored Deep Space. Press play to resume.", state.status)
    }

    @Test
    fun invalidSessionDoesNotReplaceLiveStateOrSynchronizeQueue() {
        val liveTrack = track("live")
        val liveQueue = PlaybackQueue(listOf(liveTrack), currentIndex = 0)
        val state = appState().apply {
            nowPlaying = liveTrack
            tracks = listOf(liveTrack)
            playbackQueue = liveQueue
            playbackProgress = PlaybackProgress(positionSeconds = 18.0, durationSeconds = 180.0)
        }
        val invalidSession = PlaybackSessionSettings(
            tracks = listOf(SavedTrack.fromTrack(track("saved"))),
            currentIndex = 4,
            positionSeconds = 42.0,
        )
        var synchronizeCalls = 0
        var relatedCalls = 0

        assertFalse(
            restoreAndroidPlaybackSession(
                state = state,
                playbackSessionRepository = TestPlaybackSessionRepository(invalidSession),
                sourceId = "source",
                loadRelatedTracks = { relatedCalls += 1 },
                synchronizePlaybackQueue = { synchronizeCalls += 1 },
            ),
        )

        assertEquals(liveTrack, state.nowPlaying)
        assertEquals(liveQueue, state.playbackQueue)
        assertEquals(PlaybackProgress(positionSeconds = 18.0, durationSeconds = 180.0), state.playbackProgress)
        assertEquals(0, synchronizeCalls)
        assertEquals(0, relatedCalls)
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
