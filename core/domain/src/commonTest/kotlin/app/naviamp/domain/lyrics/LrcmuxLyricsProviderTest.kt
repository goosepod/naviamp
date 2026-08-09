package app.naviamp.domain.lyrics

import app.naviamp.domain.LyricsSource
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.network.SharedHttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class LrcmuxLyricsProviderTest {
    @Test
    fun wordSyncedResponsePreservesMillisecondTimingAndUtf8Offsets() {
        val lyrics = parseLrcmuxResponse(
            body = response(
                level = "word",
                lines = """
                    [
                      {
                        "start": 4917,
                        "end": 6078,
                        "text": "Café glow",
                        "words": [
                          {"start": 4917, "end": 5200, "text": "Café"},
                          {"start": 5200, "end": 5300, "text": " "},
                          {"start": 5300, "end": 6078, "text": "glow"}
                        ]
                      }
                    ]
                """.trimIndent(),
            ),
            requestedTrack = track(),
        )

        assertEquals(LyricsSource.Lrcmux, lyrics?.source)
        assertEquals(LyricsTiming.WordSynced, lyrics?.timing)
        assertEquals(4_917L, lyrics?.lines?.single()?.startMillis)
        assertEquals(listOf(4_917L, 5_200L, 5_300L), lyrics?.cueLines?.single()?.cues?.map { it.startMillis })
        assertEquals(listOf(0, 5, 6), lyrics?.cueLines?.single()?.cues?.map { it.byteStart })
        assertEquals(listOf(4, 5, 9), lyrics?.cueLines?.single()?.cues?.map { it.byteEnd })
        assertEquals(6_078L, lyrics?.cueLines?.single()?.endMillis)
        assertEquals("lrcmux:test-source", lyrics?.kind)
    }

    @Test
    fun lineAndPlainResponsesRemainUsableWithoutWordCues() {
        val lineLyrics = parseLrcmuxResponse(
            response(
                level = "line",
                lines = """[{"start":1000,"end":2000,"text":"Original line","words":null}]""",
            ),
            track(),
        )
        val plainLyrics = parseLrcmuxResponse(
            response(
                level = "none",
                lines = """[{"text":"Original line"}]""",
            ),
            track(),
        )

        assertEquals(LyricsTiming.LineSynced, lineLyrics?.timing)
        assertTrue(lineLyrics?.cueLines?.isEmpty() == true)
        assertEquals(LyricsTiming.Plain, plainLyrics?.timing)
        assertEquals(false, plainLyrics?.synced)
    }

    @Test
    fun queryRequestsHighestAvailableTimingWithIdentityAndDuration() {
        val query = checkNotNull(LrcmuxLyricsQuery.fromTrack(track()))

        assertEquals("Test Artist", query.artist)
        assertEquals("Test Title", query.title)
        assertEquals("Test Album", query.album)
        assertTrue(query.parameters.contains("duration" to "210"))
        assertTrue(query.parameters.contains("level" to "word"))
        assertTrue(query.parameters.contains("format" to "json"))
    }

    @Test
    fun providerUsesKeylessGetEndpoint() = runTest {
        val provider = StubLrcmuxLyricsProvider(
            response(
                level = "none",
                lines = """[{"text":"Original line"}]""",
            ),
        )

        assertEquals("Original line", provider.lyrics(track())?.lines?.single()?.text)
        assertEquals("Test Title", provider.query?.title)
        assertEquals("lrcmux", provider.id)
        assertEquals(
            setOf(LyricsTiming.Plain, LyricsTiming.LineSynced, LyricsTiming.WordSynced),
            provider.capabilities,
        )
    }

    @Test
    fun malformedInstrumentalAndMismatchedResponsesFailClosed() {
        assertNull(parseLrcmuxResponse("not-json", track()))
        assertNull(
            parseLrcmuxResponse(
                response(level = "none", lines = "null", instrumental = true),
                track(),
            ),
        )
        assertNull(
            parseLrcmuxResponse(
                response(
                    level = "none",
                    lines = """[{"text":"Wrong match"}]""",
                    title = "Different Title",
                ),
                track(),
            ),
        )
    }

    private fun track() = Track(
        id = TrackId("track"),
        title = "Test Title",
        artistName = "Test Artist",
        albumTitle = "Test Album",
        durationSeconds = 210,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )
}

private class StubLrcmuxLyricsProvider(
    private val body: String?,
) : LrcmuxLyricsProvider(NoopHttpClient) {
    var query: LrcmuxLyricsQuery? = null
        private set

    override suspend fun responseBody(query: LrcmuxLyricsQuery): String? {
        this.query = query
        return body
    }
}

private object NoopHttpClient : SharedHttpClient {
    override suspend fun get(url: String, headers: Map<String, String>): String? = null
    override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? = null
    override suspend fun getResponse(url: String, headers: Map<String, String>): SharedHttpResponse? = null
    override suspend fun download(
        url: String,
        headers: Map<String, String>,
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean = false
}

private fun response(
    level: String,
    lines: String,
    instrumental: Boolean = false,
    title: String = "Test Title",
): String = """
    {
      "lines": $lines,
      "meta": {
        "instrumental": $instrumental,
        "level": "$level",
        "source": {"id":"test-source","name":"Test Source"}
      },
      "track": {
        "title": "$title",
        "artist": "Test Artist",
        "album": "Test Album",
        "duration": 210
      }
    }
""".trimIndent()
