package app.naviamp.domain.lyrics

import app.naviamp.domain.LyricLine
import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.LyricsOffsetRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricsOffsetControllerTest {
    @Test
    fun withSavedOffsetAppliesRepositoryOffset() {
        val repository = FakeLyricsOffsetRepository(offsets = mutableMapOf("source:track" to 1_250))
        val controller = LyricsOffsetController(repository)

        val lyrics = controller.withSavedOffset("source", track(), lyrics())

        assertEquals(1_250, lyrics?.offsetMillis)
    }

    @Test
    fun saveOffsetPersistsAndReturnsUpdatedLyrics() {
        val repository = FakeLyricsOffsetRepository()
        val controller = LyricsOffsetController(repository)

        val updated = controller.saveOffset("source", track(), lyrics(), offsetMillis = -500)

        assertEquals(-500, updated?.offsetMillis)
        assertEquals(-500, repository.offsets["source:track"])
    }

    @Test
    fun missingContextLeavesLyricsUnchanged() {
        val controller = LyricsOffsetController(FakeLyricsOffsetRepository())
        val original = lyrics(offsetMillis = 100)

        assertEquals(original, controller.withSavedOffset(null, track(), original))
        assertEquals(original, controller.saveOffset(null, track(), original, offsetMillis = 200))
        assertNull(controller.withSavedOffset("source", track(), null))
    }

    private fun track(): Track =
        Track(
            id = TrackId("track"),
            title = "Track",
            artistName = "Artist",
            albumTitle = null,
            durationSeconds = null,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )

    private fun lyrics(offsetMillis: Int = 0): Lyrics =
        Lyrics(
            source = LyricsSource.Provider,
            synced = true,
            lines = listOf(LyricLine(startMillis = 0, text = "line")),
            offsetMillis = offsetMillis,
        )
}

private class FakeLyricsOffsetRepository(
    val offsets: MutableMap<String, Int> = mutableMapOf(),
) : LyricsOffsetRepository {
    override fun lyricsOffsetMillis(sourceId: String, trackId: TrackId): Int =
        offsets["$sourceId:${trackId.value}"] ?: 0

    override fun saveLyricsOffsetMillis(sourceId: String, trackId: TrackId, offsetMillis: Int) {
        offsets["$sourceId:${trackId.value}"] = offsetMillis
    }
}
