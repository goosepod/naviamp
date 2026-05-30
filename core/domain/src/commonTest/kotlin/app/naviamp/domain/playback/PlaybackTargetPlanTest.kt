package app.naviamp.domain.playback

import app.naviamp.domain.AudioCodec
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackTargetPlanTest {
    @Test
    fun localAudioUsesEngineStartPosition() {
        val plan = playbackTargetPlan(
            track = track("one"),
            quality = StreamQuality.Transcoded(AudioCodec.Opus, 128),
            startPositionSeconds = 42.0,
            hasLocalAudio = true,
        )

        assertEquals(42.0, plan.engineStartPositionSeconds)
        assertNull(plan.providerStreamRequest.startPositionSeconds)
    }

    @Test
    fun remoteTranscodedAudioUsesProviderStartPosition() {
        val plan = playbackTargetPlan(
            track = track("one"),
            quality = StreamQuality.Transcoded(AudioCodec.Opus, 128),
            startPositionSeconds = 42.0,
            hasLocalAudio = false,
        )

        assertNull(plan.engineStartPositionSeconds)
        assertEquals(42.0, plan.providerStreamRequest.startPositionSeconds)
    }

    @Test
    fun originalRemoteAudioUsesEngineSeekPosition() {
        val plan = playbackTargetPlan(
            track = track("one"),
            quality = StreamQuality.Original,
            startPositionSeconds = 42.0,
            hasLocalAudio = false,
        )

        assertEquals(42.0, plan.engineStartPositionSeconds)
        assertNull(plan.providerStreamRequest.startPositionSeconds)
    }

    @Test
    fun playbackStartUsesRequestedQueueWhenItContainsTrack() {
        val target = track("two")
        val plan = planPlaybackStart(
            track = target,
            requestedQueue = listOf(track("one"), target, track("three")),
            activeQueue = listOf(target),
            quality = StreamQuality.Original,
            startPositionSeconds = null,
            hasLocalAudio = false,
        )

        assertEquals(listOf("one", "two", "three"), plan.queue.map { it.id.value })
        assertEquals(1, plan.queueIndex)
        assertNull(plan.restoredStartPositionSeconds)
        assertEquals(true, plan.shouldResetProgress)
    }

    @Test
    fun playbackStartFallsBackToActiveQueueThenSingleTrack() {
        val target = track("two")
        val activePlan = planPlaybackStart(
            track = target,
            requestedQueue = listOf(track("other")),
            activeQueue = listOf(track("one"), target),
            quality = StreamQuality.Original,
            startPositionSeconds = null,
            hasLocalAudio = false,
        )
        val singlePlan = planPlaybackStart(
            track = target,
            requestedQueue = listOf(track("other")),
            activeQueue = listOf(track("none")),
            quality = StreamQuality.Original,
            startPositionSeconds = null,
            hasLocalAudio = false,
        )

        assertEquals(listOf("one", "two"), activePlan.queue.map { it.id.value })
        assertEquals(1, activePlan.queueIndex)
        assertEquals(listOf("two"), singlePlan.queue.map { it.id.value })
        assertEquals(0, singlePlan.queueIndex)
    }

    @Test
    fun playbackStartCreatesInitialProgressForEngineStartPosition() {
        val plan = planPlaybackStart(
            track = track("one"),
            requestedQueue = null,
            activeQueue = emptyList(),
            quality = StreamQuality.Original,
            startPositionSeconds = 42.0,
            hasLocalAudio = false,
        )

        assertEquals(42.0, plan.restoredStartPositionSeconds)
        assertEquals(42.0, plan.initialProgress?.positionSeconds)
        assertEquals(180.0, plan.initialProgress?.durationSeconds)
        assertEquals(false, plan.shouldResetProgress)
    }

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
