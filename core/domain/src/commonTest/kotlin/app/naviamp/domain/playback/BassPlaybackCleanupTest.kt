package app.naviamp.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BassPlaybackCleanupTest {
    @Test
    fun clearsActiveStreamAndPreparedPlaybackStateTogether() {
        val reset = clearBassPlaybackCleanupState()

        assertEquals(0, reset.stream.stream)
        assertEquals(0, reset.stream.currentSourceStream)
        assertEquals(false, reset.stream.crossfadeActive)
        assertEquals(1f, reset.stream.replayGainFactor)
        assertEquals(ReplayGainMode.Off, reset.stream.replayGainAdjustment.mode)
        assertNull(reset.prepared.request)
        assertNull(reset.prepared.replayGainAdjustment)
        assertEquals(1f, reset.prepared.replayGainFactor)
        assertNull(reset.prepared.error)
    }
}
