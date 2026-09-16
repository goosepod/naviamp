package app.naviamp.presentation

import app.naviamp.domain.LyricLine
import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import app.naviamp.domain.TrackId
import app.naviamp.domain.waveform.AudioWaveform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaviampCoreNowPlayingPresenterTest {
    @Test
    fun trackSidecarsAreHiddenAsSoonAsCurrentTrackIdentityChanges() {
        val sidecars = NaviampCoreNowPlayingSidecars(
            trackId = TrackId("first"),
            lyrics = Lyrics(
                source = LyricsSource.Provider,
                synced = false,
                lines = listOf(LyricLine(null, "Lyrics from first track")),
            ),
            lyricsStatus = "Loading lyrics...",
        )

        assertEquals(sidecars, sidecars.lyricsForTrack(TrackId("first")))
        assertNull(sidecars.lyricsForTrack(TrackId("second")))
    }

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
