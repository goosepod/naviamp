package app.naviamp.presentation

import app.naviamp.domain.TrackId
import app.naviamp.domain.waveform.AudioWaveform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaviampCoreNowPlayingPresenterTest {
    @Test
    fun waveformIsPublishedOnlyForTheTrackThatProducedIt() {
        val waveform = AudioWaveform(listOf(0.1f, 0.8f, 0.3f))
        val sidecars = NaviampCoreNowPlayingSidecars(
            trackId = TrackId("first"),
            waveform = waveform,
        )

        assertEquals(waveform, sidecars.waveformForTrack(TrackId("first"), enabled = true))
        assertNull(sidecars.waveformForTrack(TrackId("second"), enabled = true))
        assertNull(sidecars.waveformForTrack(TrackId("first"), enabled = false))
    }
}
