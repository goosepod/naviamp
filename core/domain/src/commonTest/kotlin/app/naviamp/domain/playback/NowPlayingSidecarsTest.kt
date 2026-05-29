package app.naviamp.domain.playback

import app.naviamp.domain.LyricLine
import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.internetRadioTrackId
import app.naviamp.domain.queue.PlaybackQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingSidecarsTest {
    @Test
    fun onlineLyricsLoadOnlyWhenEnabledAndNoSyncedLocalLyricsExist() {
        val synced = Lyrics(LyricsSource.Provider, synced = true, lines = listOf(LyricLine(1_000, "line")))
        val unsynced = Lyrics(LyricsSource.Provider, synced = false, lines = listOf(LyricLine(null, "line")))

        assertTrue(shouldLoadOnlineLyrics(true, providerLyrics = null, embeddedLyrics = null))
        assertTrue(shouldLoadOnlineLyrics(true, providerLyrics = unsynced, embeddedLyrics = null))
        assertFalse(shouldLoadOnlineLyrics(false, providerLyrics = null, embeddedLyrics = null))
        assertFalse(shouldLoadOnlineLyrics(true, providerLyrics = synced, embeddedLyrics = null))
    }

    @Test
    fun waveformStatusReflectsCacheAudioAndGenerationState() {
        assertEquals("Cached", waveformStatus(true, false, true, true))
        assertEquals("Generated", waveformStatus(false, true, true, true))
        assertEquals("Cache disabled", waveformStatus(false, false, false, false))
        assertEquals("Preparing", waveformStatus(false, false, false, true))
        assertEquals("Unavailable", waveformStatus(false, false, true, true))
    }

    @Test
    fun sidecarPrepTracksUseCurrentQueueWindowAndSkipRadioTracks() {
        val one = track("one")
        val radio = track(internetRadioTrackId("station").value)
        val two = track("two")
        val queue = PlaybackQueue(
            tracks = listOf(one, radio, two),
            currentIndex = 0,
        )

        assertEquals(listOf(one, two), sidecarPrepTracks(queue, depth = 3))
        assertEquals(listOf(one), sidecarPrepTracks(queue, depth = 1))
    }

    @Test
    fun sidecarPrepPlanIncludesLyricsDecision() {
        val one = track("one")
        val queue = PlaybackQueue(tracks = listOf(one), currentIndex = 0)

        assertEquals(
            SidecarPrepPlan(tracks = listOf(one), loadLyrics = false),
            sidecarPrepPlan(queue, depth = 1, onlineLyricsEnabled = false, lyricsVisible = false),
        )
        assertEquals(
            SidecarPrepPlan(tracks = listOf(one), loadLyrics = true),
            sidecarPrepPlan(queue, depth = 1, onlineLyricsEnabled = true, lyricsVisible = false),
        )
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
