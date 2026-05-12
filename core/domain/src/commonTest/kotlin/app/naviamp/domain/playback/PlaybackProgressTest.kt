package app.naviamp.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackProgressTest {
    @Test
    fun mergeWithKeepsPreviousValuesWhenCurrentReadIsUnknown() {
        val previous = PlaybackProgress(
            positionSeconds = 42.0,
            durationSeconds = 180.0,
        )

        val merged = PlaybackProgress.Unknown.mergeWith(previous)

        assertEquals(42.0, merged.positionSeconds)
        assertEquals(180.0, merged.durationSeconds)
    }

    @Test
    fun mergeWithIgnoresLargeBackwardPositionJumps() {
        val previous = PlaybackProgress(
            positionSeconds = 42.0,
            durationSeconds = 180.0,
        )

        val merged = PlaybackProgress(
            positionSeconds = 0.0,
            durationSeconds = 180.0,
        ).mergeWith(previous)

        assertEquals(42.0, merged.positionSeconds)
        assertEquals(180.0, merged.durationSeconds)
    }

    @Test
    fun mergeWithAllowsSmallBackwardPositionCorrection() {
        val previous = PlaybackProgress(
            positionSeconds = 42.0,
            durationSeconds = 180.0,
        )

        val merged = PlaybackProgress(
            positionSeconds = 41.4,
            durationSeconds = 180.0,
        ).mergeWith(previous)

        assertEquals(41.4, merged.positionSeconds)
    }
}
