package app.naviamp.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackProgressTest {
    @Test
    fun ignoresStaleEngineProgressSoonAfterSeek() {
        assertEquals(
            true,
            shouldIgnoreProgressForPendingSeek(
                pendingSeekPositionSeconds = 60.0,
                pendingSeekIssuedAtMillis = 1_000,
                incomingPositionSeconds = 12.0,
                nowMillis = 1_500,
                toleranceSeconds = 1.0,
                staleWindowMillis = 2_000,
            ),
        )
    }

    @Test
    fun acceptsEngineProgressAfterSeekWindowExpires() {
        assertEquals(
            false,
            shouldIgnoreProgressForPendingSeek(
                pendingSeekPositionSeconds = 60.0,
                pendingSeekIssuedAtMillis = 1_000,
                incomingPositionSeconds = 12.0,
                nowMillis = 3_500,
                toleranceSeconds = 1.0,
                staleWindowMillis = 2_000,
            ),
        )
    }

    @Test
    fun clearsPendingSeekWhenIncomingProgressReachesTarget() {
        assertEquals(
            true,
            shouldClearPendingSeek(
                pendingSeekPositionSeconds = 60.0,
                pendingSeekIssuedAtMillis = 1_000,
                incomingPositionSeconds = 60.5,
                nowMillis = 1_500,
                toleranceSeconds = 1.0,
                staleWindowMillis = 2_000,
            ),
        )
    }

    @Test
    fun clearsPendingSeekWhenProgressIsUnknownOrWindowExpires() {
        assertEquals(
            true,
            shouldClearPendingSeek(
                pendingSeekPositionSeconds = 60.0,
                pendingSeekIssuedAtMillis = 1_000,
                incomingPositionSeconds = null,
                nowMillis = 1_500,
                toleranceSeconds = 1.0,
                staleWindowMillis = 2_000,
            ),
        )
        assertEquals(
            true,
            shouldClearPendingSeek(
                pendingSeekPositionSeconds = 60.0,
                pendingSeekIssuedAtMillis = 1_000,
                incomingPositionSeconds = 12.0,
                nowMillis = 3_000,
                toleranceSeconds = 1.0,
                staleWindowMillis = 2_000,
            ),
        )
    }

    @Test
    fun detectsWhenPendingSeekReachedTarget() {
        assertEquals(
            true,
            hasPendingSeekReachedTarget(
                pendingSeekPositionSeconds = 60.0,
                incomingPositionSeconds = 60.5,
                toleranceSeconds = 1.0,
            ),
        )
        assertEquals(
            false,
            hasPendingSeekReachedTarget(
                pendingSeekPositionSeconds = 60.0,
                incomingPositionSeconds = 58.0,
                toleranceSeconds = 1.0,
            ),
        )
    }

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
