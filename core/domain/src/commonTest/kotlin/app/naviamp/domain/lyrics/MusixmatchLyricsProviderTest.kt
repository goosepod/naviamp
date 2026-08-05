package app.naviamp.domain.lyrics

import app.naviamp.domain.LyricsSource
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.network.SharedHttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MusixmatchLyricsProviderTest {
    @Test
    fun richSyncBodyPreservesAbsoluteWordTimingAndUtf8Offsets() {
        val lyrics = parseRichSyncBody(
            body = """
                [
                  {
                    "ts": 4.917,
                    "te": 6.078,
                    "l": [
                      {"c": "I", "o": 0.0},
                      {"c": " ", "o": 0.046},
                      {"c": "want", "o": 0.093}
                    ],
                    "x": "I want"
                  }
                ]
            """.trimIndent(),
            language = "en",
            displayArtist = "Billie Eilish",
            displayTitle = "BIRDS OF A FEATHER",
        )

        assertEquals(LyricsSource.Musixmatch, lyrics?.source)
        assertEquals(LyricsTiming.WordSynced, lyrics?.timing)
        assertEquals(4_917L, lyrics?.lines?.single()?.startMillis)
        assertEquals(listOf(4_917L, 4_963L, 5_010L), lyrics?.cueLines?.single()?.cues?.map { it.startMillis })
        assertEquals(listOf(0, 1, 2), lyrics?.cueLines?.single()?.cues?.map { it.byteStart })
        assertEquals(listOf(0, 1, 5), lyrics?.cueLines?.single()?.cues?.map { it.byteEnd })
        assertEquals(6_078L, lyrics?.cueLines?.single()?.cues?.last()?.endMillis)
    }

    @Test
    fun queryUsesIdentityAndDurationMatchingParameters() {
        val query = checkNotNull(RichSyncLyricsQuery.fromTrack(track()))

        assertEquals("Billie Eilish", query.artist)
        assertEquals("BIRDS OF A FEATHER", query.title)
        assertTrue(query.parameters.contains("f_subtitle_length" to "210"))
        assertTrue(query.parameters.contains("f_subtitle_length_max_deviation" to "3"))
    }

    @Test
    fun anonymousTokenIsReusedAcrossSuccessfulLookups() = runTest {
        val provider = StubMusixmatchLyricsProvider(
            tokenBodies = mutableListOf(tokenBody("token-one")),
            responseBodies = mutableListOf(successfulPlainMacro(), successfulPlainMacro()),
        )

        assertEquals("Plain lyric", provider.lyrics(track())?.lines?.single()?.text)
        assertEquals("Plain lyric", provider.lyrics(track())?.lines?.single()?.text)
        assertEquals(1, provider.tokenRequests)
        assertEquals(2, provider.lyricRequests)
    }

    @Test
    fun unauthorizedLookupInvalidatesTokenAndRetriesOnce() = runTest {
        val provider = StubMusixmatchLyricsProvider(
            tokenBodies = mutableListOf(tokenBody("expired"), tokenBody("fresh")),
            responseBodies = mutableListOf(unauthorizedBody(), successfulPlainMacro()),
        )

        assertEquals("Plain lyric", provider.lyrics(track())?.lines?.single()?.text)
        assertEquals(2, provider.tokenRequests)
        assertEquals(2, provider.lyricRequests)
    }

    private fun track() = Track(
        id = TrackId("track"),
        title = "BIRDS OF A FEATHER",
        artistName = "Billie Eilish",
        albumTitle = "HIT ME HARD AND SOFT",
        durationSeconds = 210,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )
}

private class StubMusixmatchLyricsProvider(
    private val tokenBodies: MutableList<String>,
    private val responseBodies: MutableList<String>,
) : MusixmatchLyricsProvider(NoopHttpClient, nowMillis = { 1L }) {
    var tokenRequests: Int = 0
        private set
    var lyricRequests: Int = 0
        private set

    override suspend fun tokenBody(): String? {
        tokenRequests += 1
        return tokenBodies.removeFirstOrNull()
    }

    override suspend fun responseBody(query: RichSyncLyricsQuery, token: String): String? {
        lyricRequests += 1
        return responseBodies.removeFirstOrNull()
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

private fun tokenBody(token: String): String =
    """{"message":{"header":{"status_code":200},"body":{"user_token":"$token"}}}"""

private fun unauthorizedBody(): String =
    """{"message":{"header":{"status_code":401},"body":{}}}"""

private fun successfulPlainMacro(): String = """
    {
      "message": {
        "header": {"status_code": 200},
        "body": {
          "macro_calls": {
            "track.lyrics.get": {
              "message": {
                "header": {"status_code": 200},
                "body": {"lyrics": {"lyrics_body": "Plain lyric", "lyrics_language": "en"}}
              }
            }
          }
        }
      }
    }
""".trimIndent()
