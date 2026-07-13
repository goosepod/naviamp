package app.naviamp.domain.settings

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.queue.PlaybackQueue
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackSessionMappingTest {
    @Test
    fun restoredTrackSessionBuildsQueueAndProgressFromSavedSession() {
        val session = PlaybackSessionSettings.fromTracks(
            tracks = listOf(track("one"), track("two", durationSeconds = 240)),
            currentIndex = 1,
            positionSeconds = 62.0,
        )

        val restored = session?.restoredTrackSession()

        assertEquals(1, restored?.currentIndex)
        assertEquals(TrackId("two"), restored?.currentTrack?.id)
        assertEquals(
            PlaybackQueue(tracks = session?.toTracks().orEmpty(), currentIndex = 1),
            restored?.playbackQueue,
        )
        assertEquals(
            PlaybackProgress(positionSeconds = 62.0, durationSeconds = 240.0),
            restored?.playbackProgress,
        )
    }

    @Test
    fun restoredTrackSessionRejectsInvalidCurrentIndex() {
        val session = PlaybackSessionSettings(
            tracks = listOf(SavedTrack.fromTrack(track("one"))),
            currentIndex = 4,
            positionSeconds = 12.0,
        )

        assertEquals(null, session.restoredTrackSession())
        assertEquals(PlaybackQueue(), session.restoredPlaybackQueue())
    }

    @Test
    fun playbackSessionFromQueueRejectsInvalidQueueAndDropsNonPositivePosition() {
        assertEquals(null, playbackSessionFromQueue(PlaybackQueue()))

        val session = playbackSessionFromQueue(
            queue = PlaybackQueue(tracks = listOf(track("one")), currentIndex = 0),
            positionSeconds = 0.0,
        )

        assertEquals(null, session?.positionSeconds)
    }

    @Test
    fun playbackSessionFromCurrentTrackFallsBackToSingleTrackQueue() {
        val currentTrack = track("current")
        val session = playbackSessionFromCurrentTrack(
            currentTrack = currentTrack,
            queue = PlaybackQueue(tracks = listOf(track("other")), currentIndex = 0),
            positionSeconds = 12.0,
        )

        assertEquals(listOf(currentTrack), session?.toTracks())
        assertEquals(0, session?.currentIndex)
        assertEquals(12.0, session?.positionSeconds)
    }

    @Test
    fun adjacentTrackSessionMovesWithinBoundsAndClearsPosition() {
        val session = PlaybackSessionSettings.fromTracks(
            tracks = listOf(track("one"), track("two"), track("three")),
            currentIndex = 1,
            positionSeconds = 62.0,
        )

        val next = session?.adjacentTrackSession(1)
        val previous = session?.adjacentTrackSession(-1)
        val clamped = session?.adjacentTrackSession(99)

        assertEquals(2, next?.currentIndex)
        assertEquals(null, next?.positionSeconds)
        assertEquals(0, previous?.currentIndex)
        assertEquals(2, clamped?.currentIndex)
    }

    @Test
    fun playbackSessionSavePlanPrefersStationSession() {
        val station = InternetRadioStation(
            id = "station",
            name = "Station",
            streamUrl = "https://example.com/stream",
        )

        val plan = planPlaybackSessionSave(
            activeSourceId = "source",
            station = station,
            currentTrack = track("ignored"),
            playbackQueue = PlaybackQueue(),
            progressPositionSeconds = 10.0,
            notificationPositionSeconds = null,
            existingSession = null,
        )

        val save = plan as PlaybackSessionSavePlan.Save
        assertEquals(PlaybackSessionSavePlan.Kind.InternetRadio, save.kind)
        assertEquals(station, save.session.internetRadioStation?.toStation())
    }

    @Test
    fun playbackSessionSavePlanFallsBackToExistingTrackPosition() {
        val currentTrack = track("current")
        val existing = playbackSessionFromCurrentTrack(
            currentTrack = currentTrack,
            queue = PlaybackQueue(tracks = listOf(currentTrack), currentIndex = 0),
            positionSeconds = 42.0,
        )

        val plan = planPlaybackSessionSave(
            activeSourceId = "source",
            station = null,
            currentTrack = currentTrack,
            playbackQueue = PlaybackQueue(tracks = listOf(currentTrack), currentIndex = 0),
            progressPositionSeconds = null,
            notificationPositionSeconds = null,
            existingSession = existing,
        )

        val save = plan as PlaybackSessionSavePlan.Save
        assertEquals(PlaybackSessionSavePlan.Kind.Track, save.kind)
        assertEquals(42.0, save.session.positionSeconds)
    }

    @Test
    fun playbackSessionRestorePlanBuildsStationAndTrackPlans() {
        val station = InternetRadioStation(
            id = "station",
            name = "Station",
            streamUrl = "https://example.com/stream",
        )
        val stationPlan = planPlaybackSessionRestore(
            PlaybackSessionSettings.fromInternetRadioStation(station),
        )
        assertEquals(station, (stationPlan as PlaybackSessionRestorePlan.InternetRadio).station)
        assertEquals("Restored Station. Press play to resume.", stationPlan.status)

        val trackSession = PlaybackSessionSettings.fromTracks(
            tracks = listOf(track("one"), track("two")),
            currentIndex = 0,
            playNextCount = 1,
            positionSeconds = 12.0,
        )
        val trackPlan = planPlaybackSessionRestore(trackSession)
        val restoredTrack = trackPlan as PlaybackSessionRestorePlan.TrackSession
        assertEquals(TrackId("one"), restoredTrack.currentTrack.id)
        assertEquals(1, restoredTrack.playbackQueue.playNextCount)
        assertEquals(12.0, restoredTrack.restoredStartPositionSeconds)
        assertEquals("Restored Track one. Press play to resume.", restoredTrack.status)
    }

    @Test
    fun restoredPausedTrackPreparesSidecarsWithoutStartingPlayback() {
        assertEquals(
            PlaybackSessionRestoreEffects(startPlayback = false, prepareSidecars = true),
            planPlaybackSessionRestoreEffects(
                restored = true,
                hasCurrentTrack = true,
                startPlayingOnLaunch = false,
            ),
        )
        assertEquals(
            PlaybackSessionRestoreEffects(startPlayback = true, prepareSidecars = false),
            planPlaybackSessionRestoreEffects(
                restored = true,
                hasCurrentTrack = true,
                startPlayingOnLaunch = true,
            ),
        )
        assertEquals(
            PlaybackSessionRestoreEffects(startPlayback = false, prepareSidecars = false),
            planPlaybackSessionRestoreEffects(
                restored = false,
                hasCurrentTrack = true,
                startPlayingOnLaunch = false,
            ),
        )
    }

    @Test
    fun playbackSessionThrottleRequiresSourceTargetAndElapsedIntervalUnlessForced() {
        assertEquals(
            true,
            shouldThrottlePlaybackSessionSave(
                activeSourceId = null,
                hasPlaybackTarget = true,
                force = true,
                nowMillis = 10_000L,
                lastSavedAtMillis = 9_000L,
                saveIntervalMillis = 5_000L,
            ),
        )
        assertEquals(
            true,
            shouldThrottlePlaybackSessionSave(
                activeSourceId = "source",
                hasPlaybackTarget = false,
                force = true,
                nowMillis = 10_000L,
                lastSavedAtMillis = 9_000L,
                saveIntervalMillis = 5_000L,
            ),
        )
        assertEquals(
            true,
            shouldThrottlePlaybackSessionSave(
                activeSourceId = "source",
                hasPlaybackTarget = true,
                force = false,
                nowMillis = 10_000L,
                lastSavedAtMillis = 8_000L,
                saveIntervalMillis = 5_000L,
            ),
        )
        assertEquals(
            false,
            shouldThrottlePlaybackSessionSave(
                activeSourceId = "source",
                hasPlaybackTarget = true,
                force = true,
                nowMillis = 10_000L,
                lastSavedAtMillis = 8_000L,
                saveIntervalMillis = 5_000L,
            ),
        )
    }

    private fun track(
        id: String,
        durationSeconds: Int = 180,
    ): Track =
        Track(
            id = TrackId(id),
            title = "Track $id",
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = durationSeconds,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
