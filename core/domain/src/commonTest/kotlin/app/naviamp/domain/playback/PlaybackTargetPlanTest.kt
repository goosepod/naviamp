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
