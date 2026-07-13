package app.naviamp.android.playback

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PrepareNextPlaybackReason
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.PlaybackSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidServicePlaybackRuntimeControllerTest {
    @Test
    fun plansCrossfadeForServiceOwnedAndroidAutoQueue() {
        val work = planAndroidServicePreparedNextPlayback(
            queue = PlaybackQueue(
                tracks = listOf(track("current"), track("next")),
                currentIndex = 0,
            ),
            repeatMode = RepeatMode.Off,
            progress = PlaybackProgress(positionSeconds = 96.0, durationSeconds = 100.0),
            preparedNextIndex = null,
            playbackSettings = PlaybackSettings(
                gaplessEnabled = false,
                crossfadeDurationSeconds = 5,
            ),
            supportsGapless = true,
            supportsCrossfade = true,
        )

        assertEquals(1, work?.markPreparedNextIndex)
        assertEquals(PrepareNextPlaybackReason.Crossfade, work?.plan?.reason)
        assertEquals("next", work?.plan?.track?.id?.value)
    }

    @Test
    fun doesNotPlanServiceCrossfadeTwiceForSameTrack() {
        val work = planAndroidServicePreparedNextPlayback(
            queue = PlaybackQueue(
                tracks = listOf(track("current"), track("next")),
                currentIndex = 0,
            ),
            repeatMode = RepeatMode.Off,
            progress = PlaybackProgress(positionSeconds = 96.0, durationSeconds = 100.0),
            preparedNextIndex = 1,
            playbackSettings = PlaybackSettings(
                gaplessEnabled = false,
                crossfadeDurationSeconds = 5,
            ),
            supportsGapless = true,
            supportsCrossfade = true,
        )

        assertNull(work)
    }
}

private fun track(id: String): Track =
    Track(
        id = TrackId(id),
        title = id,
        artistName = "Artist",
        albumTitle = "Album",
        durationSeconds = 100,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )
