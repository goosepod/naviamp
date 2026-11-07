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
    fun originalFilePropertiesEnrichOnlyTheirOwnTrackAndPreserveBitrate() {
        val track = app.naviamp.domain.Track(TrackId("first"), "Track", artistName = "Artist",
            albumTitle = null, durationSeconds = null, coverArtId = null, replayGain = null,
            audioInfo = app.naviamp.domain.AudioInfo("FLAC", 350, "audio/flac"))
        val sidecars = NaviampCoreNowPlayingSidecars(trackId = track.id,
            fileAudioInfo = app.naviamp.domain.AudioInfo("FLAC", null, "audio/flac", 16, 44100))
        val enriched = sidecars.withFileProperties(track)!!
        assertEquals(44100, enriched.audioInfo?.samplingRateHz)
        assertEquals(16, enriched.audioInfo?.bitDepth)
        assertEquals(350, enriched.audioInfo?.bitrateKbps)
        val next = track.copy(id = TrackId("next"))
        assertEquals(next, sidecars.withFileProperties(next))
        assertNull(sidecars.withFileProperties(null))
    }

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
