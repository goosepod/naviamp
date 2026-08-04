package app.naviamp.app

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.cache.PlaybackSessionRepositoryPerformance
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.PlaybackSessionRestorePlan
import app.naviamp.domain.settings.PlaybackSessionSavePlan
import app.naviamp.domain.settings.PlaybackSessionSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampPlaybackSessionControllerTest {
    @Test
    fun exposesSharedSessionPersistencePerformanceDiagnostics() {
        val repository = RecordingPlaybackSessionRepository(
            performance = PlaybackSessionRepositoryPerformance(
                readMillis = 1.25,
                decodeMillis = 2.5,
                encodeMillis = 3.75,
                writeMillis = 4.0,
                payloadCharacters = 12_345,
            ),
        )
        val controller = NaviampPlaybackSessionController(repository)
        controller.planAndSave(
            saveRequest(track("current"), PlaybackQueue(listOf(track("current")), 0), positionSeconds = 0.0),
        )

        val diagnostics = controller.performanceDiagnostics().toMap()

        assertEquals("1.25 ms", diagnostics["Session database read"])
        assertEquals("2.5 ms", diagnostics["Session JSON decode"])
        assertEquals("3.75 ms", diagnostics["Session JSON encode"])
        assertEquals("4.0 ms", diagnostics["Session database write"])
        assertEquals("12345 characters", diagnostics["Session payload"])
        assertTrue(diagnostics.containsKey("Session load total"))
        assertTrue(diagnostics.containsKey("Session plan total"))
        assertTrue(diagnostics.containsKey("Session save total"))
    }

    @Test
    fun arbitrarySessionSaveUsesSharedTimeThrottle() {
        val repository = RecordingPlaybackSessionRepository()
        val controller = NaviampPlaybackSessionController(repository)
        val session = PlaybackSessionSettings.fromTracks(listOf(track("current")), currentIndex = 0)

        assertTrue(
            controller.saveSessionThrottled(
                session = session,
                sourceId = "source",
                force = false,
                nowMillis = 30_000L,
                saveIntervalMillis = 30_000L,
            ),
        )
        assertFalse(
            controller.saveSessionThrottled(
                session = session,
                sourceId = "source",
                force = false,
                nowMillis = 40_000L,
                saveIntervalMillis = 30_000L,
            ),
        )
        assertTrue(
            controller.saveSessionThrottled(
                session = session,
                sourceId = "source",
                force = true,
                nowMillis = 40_000L,
                saveIntervalMillis = 30_000L,
            ),
        )
    }

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
    fun reusesLoadedSessionInsteadOfParsingTheQueueForEverySave() {
        val repository = RecordingPlaybackSessionRepository()
        val controller = NaviampPlaybackSessionController(repository)
        val queue = PlaybackQueue(listOf(track("one"), track("two")), currentIndex = 0)

        controller.planAndSave(saveRequest(queue.tracks[0], queue, positionSeconds = 1.0))
        controller.planAndSave(
            saveRequest(queue.tracks[1], queue.copy(currentIndex = 1), positionSeconds = 2.0),
        )

        assertEquals(1, repository.loadCount)
        assertEquals("Memory", controller.performanceDiagnostics().toMap()["Session load source"])
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

    @Test
    fun queueSaveMapsAndPersistsThroughTheSharedOwner() {
        val current = track("current")
        val next = track("next")
        val queue = PlaybackQueue(
            tracks = listOf(current, next),
            currentIndex = 0,
            playNextCount = 1,
        )
        val repository = RecordingPlaybackSessionRepository()
        val controller = NaviampPlaybackSessionController(repository)

        val session = controller.saveQueue(
            playbackQueue = queue,
            positionSeconds = 37.0,
            sourceId = "source",
        )

        assertEquals(session, repository.sessions["source"])
        assertEquals(37.0, session?.positionSeconds)
        assertEquals(1, session?.playNextCount)
        assertEquals(current.id, session?.currentTrack()?.id)
    }

    @Test
    fun nowPlayingVisibilitySurvivesLaterPlaybackSessionSaves() {
        val current = track("current")
        val queue = PlaybackQueue(listOf(current), currentIndex = 0)
        val repository = RecordingPlaybackSessionRepository(
            mutableMapOf(
                "source" to PlaybackSessionSettings.fromTracks(listOf(current), 0),
            ),
        )
        val controller = NaviampPlaybackSessionController(repository)

        assertTrue(controller.updateNowPlayingOpen(open = true, sourceId = "source"))
        controller.planAndSave(saveRequest(current, queue, positionSeconds = 12.0))
        controller.saveQueue(queue, positionSeconds = 18.0, sourceId = "source")

        assertTrue(repository.sessions["source"]?.nowPlayingOpen == true)
        assertEquals(18.0, repository.sessions["source"]?.positionSeconds)
    }

    @Test
    fun visibilityUpdateRequiresAnExistingPlaybackSession() {
        val repository = RecordingPlaybackSessionRepository()
        val controller = NaviampPlaybackSessionController(repository)

        assertFalse(controller.updateNowPlayingOpen(open = true, sourceId = "source"))
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun positionSaveUsesPersistedPositionAndRemembersSuccessfulUpdates() {
        val current = track("one")
        val queue = PlaybackQueue(listOf(current), currentIndex = 0)
        val repository = RecordingPlaybackSessionRepository(
            mutableMapOf(
                "source" to PlaybackSessionSettings.fromTracks(
                    tracks = listOf(current),
                    currentIndex = 0,
                    positionSeconds = 40.0,
                ),
            ),
        )
        val controller = NaviampPlaybackSessionController(repository)

        assertEquals(
            PlaybackSessionSavePlan.None,
            controller.planAndSavePositionIfNeeded(
                request = saveRequest(current, queue, positionSeconds = 43.0),
                saveThresholdSeconds = 5.0,
            ),
        )
        assertEquals(0, repository.saveCount)

        assertIs<PlaybackSessionSavePlan.Save>(
            controller.planAndSavePositionIfNeeded(
                request = saveRequest(current, queue, positionSeconds = 46.0),
                saveThresholdSeconds = 5.0,
            ),
        )
        assertEquals(1, repository.saveCount)
        assertEquals(46.0, repository.sessions["source"]?.positionSeconds)

        assertEquals(
            PlaybackSessionSavePlan.None,
            controller.planAndSavePositionIfNeeded(
                request = saveRequest(current, queue, positionSeconds = 49.0),
                saveThresholdSeconds = 5.0,
            ),
        )
        assertEquals(1, repository.saveCount)
    }

    @Test
    fun positionSaveDoesNotReuseAnotherTracksGate() {
        val previous = track("previous")
        val current = track("current")
        val queue = PlaybackQueue(listOf(current), currentIndex = 0)
        val repository = RecordingPlaybackSessionRepository(
            mutableMapOf(
                "source" to PlaybackSessionSettings.fromTracks(
                    tracks = listOf(previous),
                    currentIndex = 0,
                    positionSeconds = 40.0,
                ),
            ),
        )
        val controller = NaviampPlaybackSessionController(repository)

        assertIs<PlaybackSessionSavePlan.Save>(
            controller.planAndSavePositionIfNeeded(
                request = saveRequest(current, queue, positionSeconds = 41.0),
                saveThresholdSeconds = 5.0,
            ),
        )

        assertEquals(1, repository.saveCount)
        assertEquals(current.id, repository.sessions["source"]?.currentTrack()?.id)
    }

    @Test
    fun timedSaveGateIsSharedAndForceBypassesItsInterval() {
        val current = track("one")
        val queue = PlaybackQueue(listOf(current), currentIndex = 0)
        val repository = RecordingPlaybackSessionRepository()
        val controller = NaviampPlaybackSessionController(repository)
        val request = saveRequest(current, queue, positionSeconds = 12.0)

        assertIs<PlaybackSessionSavePlan.Save>(
            controller.planAndSaveThrottled(
                request = request,
                force = false,
                nowMillis = 10_000L,
                saveIntervalMillis = 1_000L,
            ),
        )
        assertEquals(
            PlaybackSessionSavePlan.None,
            controller.planAndSaveThrottled(
                request = request,
                force = false,
                nowMillis = 10_500L,
                saveIntervalMillis = 1_000L,
            ),
        )
        assertIs<PlaybackSessionSavePlan.Save>(
            controller.planAndSaveThrottled(
                request = request,
                force = true,
                nowMillis = 10_500L,
                saveIntervalMillis = 1_000L,
            ),
        )
        assertEquals(2, repository.saveCount)
    }

    private fun saveRequest(
        currentTrack: Track,
        queue: PlaybackQueue,
        positionSeconds: Double,
    ) = NaviampPlaybackSessionSaveRequest(
        sourceId = "source",
        station = null,
        currentTrack = currentTrack,
        playbackQueue = queue,
        progressPositionSeconds = positionSeconds,
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

private class RecordingPlaybackSessionRepository(
    val sessions: MutableMap<String?, PlaybackSessionSettings?> = mutableMapOf(),
    private val performance: PlaybackSessionRepositoryPerformance = PlaybackSessionRepositoryPerformance(),
) : PlaybackSessionRepository {
    var lastSavedSourceId: String? = null
    var saveCount: Int = 0
    var loadCount: Int = 0

    override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? {
        loadCount += 1
        return sessions[sourceId]
    }

    override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) {
        saveCount += 1
        lastSavedSourceId = sourceId
        sessions[sourceId] = session
    }

    override fun performanceSnapshot(): PlaybackSessionRepositoryPerformance = performance
}
