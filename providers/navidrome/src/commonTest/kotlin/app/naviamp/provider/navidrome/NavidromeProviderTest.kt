package app.naviamp.provider.navidrome

import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistId
import app.naviamp.domain.AudioCodec
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.AlbumListType
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
                          "year": 1985,
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
        assertEquals(1985, albums.first().releaseYear)
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
                      "year": 1985,
                      "created": "2026-05-08T12:00:00Z",
                      "song": [
                        {
                          "id": "track-1",
                          "title": "Love Vigilantes",
                          "artistId": "artist-1",
                          "artist": "New Order",
                          "albumId": "album-1",
                          "album": "Low-Life",
                          "year": 1985,
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
        assertEquals("artist-1", details.tracks.first().artistId?.value)
        assertEquals("album-1", details.tracks.first().albumId?.value)
        assertEquals(1985, details.album.releaseYear)
        assertEquals(1985, details.tracks.first().albumReleaseYear)
        assertEquals(259, details.tracks.first().durationSeconds)
        assertEquals("FLAC", details.tracks.first().audioInfo?.codec)
        assertEquals(921, details.tracks.first().audioInfo?.bitrateKbps)
        assertEquals("2026-05-09T13:45:00Z", details.tracks.first().favoritedAtIso8601)
        assertEquals(4, details.tracks.first().userRating)
    }

    @Test
    fun artistMapsAlbumsAndInfo() = runTest {
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = SequencedHttpClient(
                listOf(
                    """
                    {
                      "subsonic-response": {
                        "status": "ok",
                        "artist": {
                          "id": "artist-1",
                          "name": "Metallica",
                          "album": [
                            {
                              "id": "album-1",
                              "name": "Master of Puppets",
                              "artist": "Metallica",
                              "year": 1986,
                              "coverArt": "cover-1"
                            },
                            {
                              "id": "album-2",
                              "name": "Garage Days Re-Revisited",
                              "artist": "Metallica",
                              "coverArt": "cover-2"
                            }
                          ]
                        }
                      }
                    }
                    """.trimIndent(),
                    """
                    {
                      "subsonic-response": {
                        "status": "ok",
                        "artistInfo2": {
                          "biography": "Thrash metal band.",
                          "smallImageUrl": "https://images.example.test/small.jpg",
                          "mediumImageUrl": "https://images.example.test/medium.jpg",
                          "largeImageUrl": "https://images.example.test/large.jpg"
                        }
                      }
                    }
                    """.trimIndent(),
                ),
            ),
        )

        val details = provider.artist(ArtistId("artist-1"))

        assertEquals("Metallica", details.artist.name)
        assertEquals(2, details.albums.size)
        assertEquals("Master of Puppets", details.albums.first().title)
        assertEquals(1986, details.albums.first().releaseYear)
        assertEquals("Garage Days Re-Revisited", details.albums.last().title)
        assertEquals("Thrash metal band.", details.info?.biography)
        assertEquals("https://images.example.test/large.jpg", details.info?.largeImageUrl)
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
                          "year": 1985,
                          "coverArt": "cover-1"
                        }
                      ],
                      "song": [
                        {
                          "id": "track-1",
                          "title": "Love Vigilantes",
                          "artistId": "artist-1",
                          "artist": "New Order",
                          "albumId": "album-1",
                          "album": "Low-Life",
                          "year": 1985,
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
        assertEquals("artist-1", results.tracks.first().artistId?.value)
        assertEquals("album-1", results.tracks.first().albumId?.value)
        assertEquals(1985, results.albums.first().releaseYear)
        assertEquals(1985, results.tracks.first().albumReleaseYear)
        assertEquals(921, results.tracks.first().audioInfo?.bitrateKbps)
        assertEquals("2026-05-09T13:45:00Z", results.tracks.first().favoritedAtIso8601)
        assertEquals(5, results.tracks.first().userRating)
    }

    @Test
    fun albumListUsesRequestedType() = runTest {
        val httpClient = RecordingResponseHttpClient(
            """
            {
              "subsonic-response": {
                "status": "ok",
                "albumList2": {
                  "album": [
                    {
                      "id": "album-1",
                      "name": "Technique",
                      "artist": "New Order",
                      "year": 1989
                    }
                  ]
                }
              }
            }
            """.trimIndent(),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        val albums = provider.albumList(AlbumListType.Random, limit = 8)

        assertEquals(
            "https://music.example.test/rest/getAlbumList2.view?u=demo&t=token&s=salt&v=1.16.1&c=Naviamp&f=json&type=random&size=8",
            httpClient.urls.single(),
        )
        assertEquals("Technique", albums.single().title)
    }

    @Test
    fun playlistsMapSubsonicPlaylists() = runTest {
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = FakeHttpClient(
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "playlists": {
                      "playlist": [
                        {
                          "id": "playlist-1",
                          "name": "April 2026 Playlist",
                          "songCount": 34,
                          "duration": 25440,
                          "coverArt": "playlist-cover"
                        }
                      ]
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        val playlists = provider.playlists()

        assertEquals("playlist-1", playlists.single().id)
        assertEquals("April 2026 Playlist", playlists.single().name)
        assertEquals(34, playlists.single().trackCount)
        assertEquals(25440, playlists.single().durationSeconds)
        assertEquals("playlist-cover", playlists.single().coverArtId)
    }

    @Test
    fun randomSongsIncludesGenreAndYearFilters() = runTest {
        val httpClient = RecordingResponseHttpClient(
            """
            {
              "subsonic-response": {
                "status": "ok",
                "randomSongs": {
                  "song": [
                    {
                      "id": "track-1",
                      "title": "House Track",
                      "artist": "Someone"
                    }
                  ]
                }
              }
            }
            """.trimIndent(),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        val tracks = provider.randomSongs(limit = 12, genre = "House", fromYear = 2000, toYear = 2009)

        assertEquals(
            "https://music.example.test/rest/getRandomSongs.view?u=demo&t=token&s=salt&v=1.16.1&c=Naviamp&f=json&size=12&genre=House&fromYear=2000&toYear=2009",
            httpClient.urls.single(),
        )
        assertEquals("House Track", tracks.single().title)
    }

    @Test
    fun genresMapCounts() = runTest {
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = FakeHttpClient(
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "genres": {
                      "genre": [
                        {
                          "value": "House",
                          "songCount": 120,
                          "albumCount": 18
                        }
                      ]
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        val genres = provider.genres()

        assertEquals("House", genres.single().name)
        assertEquals(18, genres.single().albumCount)
        assertEquals(120, genres.single().trackCount)
    }

    @Test
    fun artistRadioUsesSimilarSongs2() = runTest {
        val httpClient = RecordingResponseHttpClient(radioResponse("similarSongs2", "track-1", "Ceremony"))
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        val tracks = provider.artistRadio(ArtistId("artist-1"), count = 25)

        assertEquals(
            "https://music.example.test/rest/getSimilarSongs2.view?u=demo&t=token&s=salt&v=1.16.1&c=Naviamp&f=json&id=artist-1&count=25",
            httpClient.urls.single(),
        )
        assertEquals("track-1", tracks.single().id.value)
        assertEquals("Ceremony", tracks.single().title)
    }

    @Test
    fun albumRadioUsesSimilarSongs() = runTest {
        val httpClient = RecordingResponseHttpClient(radioResponse("similarSongs", "track-2", "Age of Consent"))
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        val tracks = provider.albumRadio(AlbumId("album-1"), count = 30)

        assertEquals(
            "https://music.example.test/rest/getSimilarSongs.view?u=demo&t=token&s=salt&v=1.16.1&c=Naviamp&f=json&id=album-1&count=30",
            httpClient.urls.single(),
        )
        assertEquals("track-2", tracks.single().id.value)
        assertEquals("Age of Consent", tracks.single().title)
    }

    @Test
    fun trackRadioUsesSimilarSongsAndFiltersSeedTrack() = runTest {
        val httpClient = RecordingResponseHttpClient(
            """
            {
              "subsonic-response": {
                "status": "ok",
                "similarSongs": {
                  "song": [
                    {
                      "id": "seed-track",
                      "title": "Seed",
                      "artist": "New Order"
                    },
                    {
                      "id": "track-3",
                      "title": "Dreams Never End",
                      "artist": "New Order"
                    }
                  ]
                }
              }
            }
            """.trimIndent(),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        val tracks = provider.trackRadio(TrackId("seed-track"), count = 20)

        assertEquals(
            "https://music.example.test/rest/getSimilarSongs.view?u=demo&t=token&s=salt&v=1.16.1&c=Naviamp&f=json&id=seed-track&count=20",
            httpClient.urls.single(),
        )
        assertEquals(listOf("track-3"), tracks.map { it.id.value })
    }

    @Test
    fun lyricsUsesGetLyricsBySongId() = runTest {
        val httpClient = RecordingResponseHttpClient(
            """
            {
              "subsonic-response": {
                "status": "ok",
                "lyricsList": {
                  "structuredLyrics": [
                    {
                      "displayArtist": "New Order",
                      "displayTitle": "Ceremony",
                      "lang": "eng",
                      "synced": true,
                      "offset": 0,
                      "line": [
                        { "start": 12000, "value": "This is why events unnerve me" },
                        { "start": 17000, "value": "They find it all a different story" }
                      ]
                    }
                  ]
                }
              }
            }
            """.trimIndent(),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        val lyrics = provider.lyrics(TrackId("track-lyrics"))

        assertEquals(
            "https://music.example.test/rest/getLyricsBySongId.view?u=demo&t=token&s=salt&v=1.16.1&c=Naviamp&f=json&id=track-lyrics",
            httpClient.urls.single(),
        )
        assertEquals(true, lyrics?.synced)
        assertEquals(listOf(12000L, 17000L), lyrics?.lines?.map { it.startMillis })
        assertEquals("New Order", lyrics?.displayArtist)
    }

    @Test
    fun reportNowPlayingUsesScrobbleWithoutSubmission() = runTest {
        val httpClient = RecordingHttpClient()
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.reportNowPlaying(TrackId("track-1"))

        assertEquals(
            "https://music.example.test/rest/scrobble.view?u=demo&t=token&s=salt&v=1.16.1&c=Naviamp&f=json&id=track-1&submission=false",
            httpClient.urls.single(),
        )
    }

    @Test
    fun reportPlayedUsesScrobbleWithSubmissionTime() = runTest {
        val httpClient = RecordingHttpClient()
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.reportPlayed(TrackId("track-1"), playedAtEpochMillis = 1_778_526_000_000L)

        assertEquals(
            "https://music.example.test/rest/scrobble.view?u=demo&t=token&s=salt&v=1.16.1&c=Naviamp&f=json&id=track-1&submission=true&time=1778526000000",
            httpClient.urls.single(),
        )
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

    private class RecordingResponseHttpClient(private val response: String) : NavidromeHttpClient {
        val urls = mutableListOf<String>()

        override suspend fun get(url: String): String {
            urls += url
            return response
        }
    }

    private class SequencedHttpClient(private val responses: List<String>) : NavidromeHttpClient {
        private var index = 0

        override suspend fun get(url: String): String =
            responses[index++]
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

    private fun radioResponse(responseKey: String, trackId: String, title: String): String =
        """
        {
          "subsonic-response": {
            "status": "ok",
            "$responseKey": {
              "song": [
                {
                  "id": "$trackId",
                  "title": "$title",
                  "artistId": "artist-1",
                  "artist": "New Order",
                  "albumId": "album-1",
                  "album": "Substance",
                  "duration": 271,
                  "coverArt": "cover-1"
                }
              ]
            }
          }
        }
        """.trimIndent()
}
