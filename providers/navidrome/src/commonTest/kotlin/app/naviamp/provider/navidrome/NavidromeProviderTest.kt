package app.naviamp.provider.navidrome

import app.naviamp.domain.AlbumId
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
        val provider = NavidromeProvider(connection("https://music.example.test/"))

        val url = provider.streamUrl(StreamRequest(TrackId("abc123"), StreamQuality.Original))

        assertEquals(
            "https://music.example.test/rest/stream.view?u=demo&t=token&s=salt&v=1.16.1&c=Naviamp&f=json&id=abc123",
            url,
        )
    }

    @Test
    fun transcodedStreamUrlIncludesCodecAndBitrate() = runTest {
        val provider = NavidromeProvider(connection("https://music.example.test"))

        val url = provider.streamUrl(
            StreamRequest(
                trackId = TrackId("abc123"),
                quality = StreamQuality.Transcoded(AudioCodec.Opus, bitrateKbps = 128),
            ),
        )

        assertEquals(
            "https://music.example.test/rest/stream.view?u=demo&t=token&s=salt&v=1.16.1&c=Naviamp&f=json&id=abc123&format=opus&maxBitRate=128",
            url,
        )
    }

    @Test
    fun coverArtUrlIncludesAuthentication() {
        val provider = NavidromeProvider(connection("https://music.example.test"))

        val url = provider.coverArtUrl("cover-1")

        assertEquals(
            "https://music.example.test/rest/getCoverArt.view?u=demo&t=token&s=salt&v=1.16.1&c=Naviamp&f=json&id=cover-1",
            url,
        )
    }

    @Test
    fun setTrackFavoriteCallsStarEndpoint() = runTest {
        val httpClient = RecordingHttpClient()
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.setTrackFavorite(TrackId("track-1"), favorite = true)

        assertEquals(
            "https://music.example.test/rest/star.view?u=demo&t=token&s=salt&v=1.16.1&c=Naviamp&f=json&id=track-1",
            httpClient.urls.single(),
        )
    }

    @Test
    fun setTrackFavoriteCallsUnstarEndpoint() = runTest {
        val httpClient = RecordingHttpClient()
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.setTrackFavorite(TrackId("track-1"), favorite = false)

        assertEquals(
            "https://music.example.test/rest/unstar.view?u=demo&t=token&s=salt&v=1.16.1&c=Naviamp&f=json&id=track-1",
            httpClient.urls.single(),
        )
    }

    @Test
    fun setTrackRatingCallsRatingEndpoint() = runTest {
        val httpClient = RecordingHttpClient()
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.setTrackRating(TrackId("track-1"), rating = 4)

        assertEquals(
            "https://music.example.test/rest/setRating.view?u=demo&t=token&s=salt&v=1.16.1&c=Naviamp&f=json&id=track-1&rating=4",
            httpClient.urls.single(),
        )
    }

    @Test
    fun setTrackRatingWithNullClearsRating() = runTest {
        val httpClient = RecordingHttpClient()
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.setTrackRating(TrackId("track-1"), rating = null)

        assertEquals(
            "https://music.example.test/rest/setRating.view?u=demo&t=token&s=salt&v=1.16.1&c=Naviamp&f=json&id=track-1&rating=0",
            httpClient.urls.single(),
        )
    }

    @Test
    fun validateConnectionReturnsServerDetails() = runTest {
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = FakeHttpClient(
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "serverVersion": "0.55.0"
                  }
                }
                """.trimIndent(),
            ),
        )

        val validation = provider.validateConnection()

        assertEquals("0.55.0", validation.serverVersion)
        assertEquals("1.16.1", validation.apiVersion)
    }

    @Test
    fun recentlyAddedAlbumsMapsSubsonicAlbums() = runTest {
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = FakeHttpClient(
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "albumList2": {
                      "album": [
                        {
                          "id": "album-1",
                          "name": "Low-Life",
                          "artist": "New Order",
                          "coverArt": "cover-1",
                          "created": "2026-05-08T12:00:00Z"
                        }
                      ]
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        val albums = provider.recentlyAddedAlbums()

        assertEquals(1, albums.size)
        assertEquals("album-1", albums.first().id.value)
        assertEquals("Low-Life", albums.first().title)
        assertEquals("New Order", albums.first().artistName)
    }

    @Test
    fun albumMapsTracks() = runTest {
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = FakeHttpClient(
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "album": {
                      "id": "album-1",
                      "name": "Low-Life",
                      "artist": "New Order",
                      "coverArt": "cover-1",
                      "created": "2026-05-08T12:00:00Z",
                      "song": [
                        {
                          "id": "track-1",
                          "title": "Love Vigilantes",
                          "artist": "New Order",
                          "album": "Low-Life",
                          "duration": 259,
                          "coverArt": "cover-1",
                          "suffix": "flac",
                          "bitRate": 921,
                          "contentType": "audio/flac",
                          "starred": "2026-05-09T13:45:00Z",
                          "userRating": 4
                        },
                        {
                          "id": "track-2",
                          "title": "The Perfect Kiss",
                          "artist": "New Order",
                          "album": "Low-Life",
                          "duration": 288,
                          "coverArt": "cover-1"
                        }
                      ]
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        val details = provider.album(AlbumId("album-1"))

        assertEquals("Low-Life", details.album.title)
        assertEquals(2, details.tracks.size)
        assertEquals("track-1", details.tracks.first().id.value)
        assertEquals("Love Vigilantes", details.tracks.first().title)
        assertEquals(259, details.tracks.first().durationSeconds)
        assertEquals("FLAC", details.tracks.first().audioInfo?.codec)
        assertEquals(921, details.tracks.first().audioInfo?.bitrateKbps)
        assertEquals("2026-05-09T13:45:00Z", details.tracks.first().favoritedAtIso8601)
        assertEquals(4, details.tracks.first().userRating)
    }

    @Test
    fun searchMapsArtistsAlbumsAndTracks() = runTest {
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = FakeHttpClient(
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "searchResult3": {
                      "artist": [
                        {
                          "id": "artist-1",
                          "name": "New Order"
                        }
                      ],
                      "album": [
                        {
                          "id": "album-1",
                          "name": "Low-Life",
                          "artist": "New Order",
                          "coverArt": "cover-1"
                        }
                      ],
                      "song": [
                        {
                          "id": "track-1",
                          "title": "Love Vigilantes",
                          "artist": "New Order",
                          "album": "Low-Life",
                          "duration": 259,
                          "coverArt": "cover-1",
                          "suffix": "flac",
                          "bitRate": 921,
                          "starred": "2026-05-09T13:45:00Z",
                          "userRating": 5
                        }
                      ]
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        val results = provider.search("new order")

        assertEquals("New Order", results.artists.first().name)
        assertEquals("Low-Life", results.albums.first().title)
        assertEquals("Love Vigilantes", results.tracks.first().title)
        assertEquals(921, results.tracks.first().audioInfo?.bitrateKbps)
        assertEquals("2026-05-09T13:45:00Z", results.tracks.first().favoritedAtIso8601)
        assertEquals(5, results.tracks.first().userRating)
    }

    private fun connection(baseUrl: String): NavidromeConnection =
        NavidromeConnection(
            baseUrl = baseUrl,
            username = "demo",
            token = "token",
            salt = "salt",
        )

    private class FakeHttpClient(private val response: String) : NavidromeHttpClient {
        override suspend fun get(url: String): String = response
    }

    private class RecordingHttpClient : NavidromeHttpClient {
        val urls = mutableListOf<String>()

        override suspend fun get(url: String): String {
            urls += url
            return """
                {
                  "subsonic-response": {
                    "status": "ok"
                  }
                }
            """.trimIndent()
        }
    }
}
