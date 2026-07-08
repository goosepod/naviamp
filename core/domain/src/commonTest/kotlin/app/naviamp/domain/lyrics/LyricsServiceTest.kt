package app.naviamp.domain.lyrics

import app.naviamp.domain.LyricLine
import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsServiceTest {
    @Test
    fun parsesSyncedLrcLines() {
        val lyrics = lyricsFromText(
            source = LyricsSource.Embedded,
            text = """
                [00:10.50]First line
                [00:12.000]Second line
            """.trimIndent(),
        )

        assertEquals(true, lyrics?.synced)
        assertEquals(listOf(10_500L, 12_000L), lyrics?.lines?.map { it.startMillis })
        assertEquals(listOf("First line", "Second line"), lyrics?.lines?.map { it.text })
    }

    @Test
    fun parsesPlainLyricsAsUnsyncedLines() {
        val lyrics = lyricsFromText(
            source = LyricsSource.Embedded,
            text = """
                First line

                Second line
            """.trimIndent(),
        )

        assertEquals(false, lyrics?.synced)
        assertEquals(listOf(null, null), lyrics?.lines?.map { it.startMillis })
        assertEquals(listOf("First line", "Second line"), lyrics?.lines?.map { it.text })
    }

    @Test
    fun lrclibQueryUsesTrackMetadata() {
        val query = LrclibLyricsQuery.fromTrack(track())!!

        assertEquals(
            listOf(
                "track_name" to "Song",
                "artist_name" to "Artist",
                "album_name" to "Album",
                "duration" to "180",
            ),
            query.parameters,
        )
    }

    @Test
    fun lrclibResponsePrefersSyncedLyrics() {
        val lyrics = StubLrclibLyricsProvider().lyricsFromBody(
            """
            {
              "trackName": "Song",
              "artistName": "Artist",
              "plainLyrics": "plain",
              "syncedLyrics": "[00:01.00]synced"
            }
            """.trimIndent(),
        )

        assertEquals(true, lyrics?.synced)
        assertEquals("Artist", lyrics?.displayArtist)
        assertEquals("Song", lyrics?.displayTitle)
        assertEquals(listOf(LyricLine(1_000L, "synced")), lyrics?.lines)
    }

    @Test
    fun preferredLyricsKeepsSyncedLocalLyricsOverLrclib() {
        val local = Lyrics(
            source = LyricsSource.Provider,
            synced = true,
            lines = listOf(LyricLine(1_000L, "provider")),
        )
        val lrclib = Lyrics(
            source = LyricsSource.Lrclib,
            synced = true,
            lines = listOf(LyricLine(1_000L, "lrclib")),
        )

        assertEquals(local, selectPreferredLyrics(local, embeddedLyrics = null, onlineLyrics = lrclib))
    }

    @Test
    fun preferredLyricsKeepsProviderLyricsOverSyncedLrclibLyrics() {
        val provider = Lyrics(
            source = LyricsSource.Provider,
            synced = false,
            lines = listOf(LyricLine(null, "provider")),
        )
        val lrclib = Lyrics(
            source = LyricsSource.Lrclib,
            synced = true,
            lines = listOf(LyricLine(1_000L, "lrclib")),
        )

        assertEquals(provider, selectPreferredLyrics(provider, embeddedLyrics = null, onlineLyrics = lrclib))
    }

    @Test
    fun preferredLyricsUsesEmbeddedLyricsBeforeLrclibLyrics() {
        val embedded = Lyrics(
            source = LyricsSource.Embedded,
            synced = false,
            lines = listOf(LyricLine(null, "embedded")),
        )
        val lrclib = Lyrics(
            source = LyricsSource.Lrclib,
            synced = true,
            lines = listOf(LyricLine(1_000L, "lrclib")),
        )

        assertEquals(embedded, selectPreferredLyrics(providerLyrics = null, embeddedLyrics = embedded, onlineLyrics = lrclib))
    }

    private class StubLrclibLyricsProvider : LrclibLyricsProvider() {
        fun lyricsFromBody(body: String): Lyrics? = parseResponse(body)

        protected override suspend fun responseBody(query: LrclibLyricsQuery): String? = null
    }

    private fun track(): Track =
        Track(
            id = TrackId("track"),
            title = "Song",
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
