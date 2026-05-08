package app.naviamp.provider.navidrome

import app.naviamp.domain.AudioCodec
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.TrackId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NavidromeProviderTest {
    @Test
    fun streamUrlUsesNormalizedBaseUrl() = runTest {
        val provider = NavidromeProvider(NavidromeConnection("https://music.example.test/", "demo"))

        val url = provider.streamUrl(StreamRequest(TrackId("abc123"), StreamQuality.Original))

        assertEquals("https://music.example.test/rest/stream.view?id=abc123", url)
    }

    @Test
    fun transcodedStreamUrlIncludesCodecAndBitrate() = runTest {
        val provider = NavidromeProvider(NavidromeConnection("https://music.example.test", "demo"))

        val url = provider.streamUrl(
            StreamRequest(
                trackId = TrackId("abc123"),
                quality = StreamQuality.Transcoded(AudioCodec.Opus, bitrateKbps = 128),
            ),
        )

        assertEquals("https://music.example.test/rest/stream.view?id=abc123&format=opus&maxBitRate=128", url)
    }
}

