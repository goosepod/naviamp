package app.naviamp.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BassPlaybackStartPlannerTest {
    @Test
    fun androidPolicyMutesAndRetriesWhenInitialSeekFails() {
        val start = planBassPlaybackStart(
            request = request(startPositionSeconds = 42.0),
            policy = BassPlaybackStartPolicy.AndroidService,
        )
        val prePlay = planBassPlaybackPrePlay(
            start = start,
            seekedBeforePlay = false,
        )

        assertEquals(42.0, start.startSeekSeconds)
        assertTrue(start.shouldSeekBeforePlay)
        assertTrue(prePlay.shouldMuteBeforePlay)
        assertTrue(prePlay.shouldRetrySeekAfterPlay)
    }

    @Test
    fun desktopPolicySeeksBeforePlayWithoutRetryMute() {
        val start = planBassPlaybackStart(
            request = request(startPositionSeconds = 42.0),
            policy = BassPlaybackStartPolicy.DesktopEngine,
        )
        val prePlay = planBassPlaybackPrePlay(
            start = start,
            seekedBeforePlay = false,
        )

        assertEquals(42.0, start.startSeekSeconds)
        assertTrue(start.shouldSeekBeforePlay)
        assertFalse(prePlay.shouldMuteBeforePlay)
        assertFalse(prePlay.shouldRetrySeekAfterPlay)
    }

    @Test
    fun skipsStartSeekWhenPositionIsMissingOrZero() {
        val missing = planBassPlaybackStart(
            request = request(startPositionSeconds = null),
            policy = BassPlaybackStartPolicy.AndroidService,
        )
        val zero = planBassPlaybackStart(
            request = request(startPositionSeconds = 0.0),
            policy = BassPlaybackStartPolicy.AndroidService,
        )

        assertEquals(null, missing.startSeekSeconds)
        assertEquals(null, zero.startSeekSeconds)
    }

    @Test
    fun skipsRetryWhenInitialSeekSucceeds() {
        val prePlay = planBassPlaybackPrePlay(
            start = planBassPlaybackStart(
                request = request(startPositionSeconds = 42.0),
                policy = BassPlaybackStartPolicy.AndroidService,
            ),
            seekedBeforePlay = true,
        )

        assertFalse(prePlay.shouldMuteBeforePlay)
        assertFalse(prePlay.shouldRetrySeekAfterPlay)
    }

    private fun request(startPositionSeconds: Double?): PlaybackRequest =
        PlaybackRequest(
            url = "file:///track.flac",
            startPositionSeconds = startPositionSeconds,
        )
}
