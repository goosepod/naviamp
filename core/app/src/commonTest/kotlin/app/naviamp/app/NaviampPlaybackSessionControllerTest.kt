package app.naviamp.app

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.PlaybackSessionRestorePlan
import app.naviamp.domain.settings.PlaybackSessionSavePlan
import app.naviamp.domain.settings.PlaybackSessionSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class NaviampPlaybackSessionControllerTest {
    @Test
    fun plansPersistsAndRestoresTrackSessionWithinSourceScope() {
        val repository = RecordingPlaybackSessionRepository()
        val controller = NaviampPlaybackSessionController(repository)
        val track = track("one")
        val queue = PlaybackQueue(tracks = listOf(track), currentIndex = 0)

        val savePlan = controller.planAndSave(
            NaviampPlaybackSessionSaveRequest(
                sourceId = "source",
                station = null,
                currentTrack = track,
                playbackQueue = queue,
                progressPositionSeconds = 42.0,
            ),
        )

        assertIs<PlaybackSessionSavePlan.Save>(savePlan)
        assertEquals("source", repository.lastSavedSourceId)
        assertEquals(42.0, repository.sessions["source"]?.positionSeconds)
        val restorePlan = assertIs<PlaybackSessionRestorePlan.TrackSession>(controller.restorePlan("source"))
        assertEquals(track.id, restorePlan.currentTrack.id)
        assertEquals(queue, restorePlan.playbackQueue)
    }

    @Test
    fun missingPlaybackTargetDoesNotOverwriteExistingSession() {
        val existing = PlaybackSessionSettings.fromTracks(listOf(track("existing")), currentIndex = 0)
        val repository = RecordingPlaybackSessionRepository(
            mutableMapOf<String?, PlaybackSessionSettings?>("source" to existing),
        )
        val controller = NaviampPlaybackSessionController(repository)

        val plan = controller.planAndSave(
            NaviampPlaybackSessionSaveRequest(
                sourceId = "source",
                station = null,
                currentTrack = null,
                playbackQueue = PlaybackQueue(),
                progressPositionSeconds = null,
            ),
        )

        assertEquals(PlaybackSessionSavePlan.None, plan)
        assertEquals(existing, repository.sessions["source"])
    }

    @Test
    fun clearUsesTheRequestedSourceScope() {
        val repository = RecordingPlaybackSessionRepository(
            mutableMapOf<String?, PlaybackSessionSettings?>(
                "source" to PlaybackSessionSettings.fromTracks(listOf(track("one")), 0),
            ),
        )
        val controller = NaviampPlaybackSessionController(repository)

        controller.clear("source")

        assertNull(repository.sessions["source"])
        assertEquals("source", repository.lastSavedSourceId)
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

private class RecordingPlaybackSessionRepository(
    val sessions: MutableMap<String?, PlaybackSessionSettings?> = mutableMapOf(),
) : PlaybackSessionRepository {
    var lastSavedSourceId: String? = null

    override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? = sessions[sourceId]

    override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) {
        lastSavedSourceId = sourceId
        sessions[sourceId] = session
    }
}
