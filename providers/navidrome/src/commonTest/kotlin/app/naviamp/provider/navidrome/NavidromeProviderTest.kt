package app.naviamp.provider.navidrome

import app.naviamp.domain.AlbumId
import app.naviamp.domain.AlbumExplicitStatus
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.AudioCodec
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.provider.CoverArtSize
import app.naviamp.domain.provider.MediaPageRequest
import app.naviamp.domain.network.NaviampClientName
import app.naviamp.domain.popular.NavidromeAgentMetadataSource
import app.naviamp.domain.smartplaylist.SmartPlaylistCondition
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistFields
import app.naviamp.domain.smartplaylist.SmartPlaylistMatch
import app.naviamp.domain.smartplaylist.SmartPlaylistOperator
import app.naviamp.domain.smartplaylist.SmartPlaylistSort
import app.naviamp.domain.smartplaylist.SmartPlaylistValue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavidromeProviderTest {
    @Test
    fun streamUrlUsesNormalizedBaseUrl() = runTest {
        val provider = NavidromeProvider(connection("https://music.example.test/"))

        val url = provider.streamUrl(StreamRequest(TrackId("abc123"), StreamQuality.Original))

        assertEquals(
            "https://music.example.test/rest/stream.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=abc123",
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
            "https://music.example.test/rest/stream.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=abc123&format=opus&maxBitRate=128",
            url,
        )
    }

    @Test
    fun transcodedStreamUrlIncludesTimeOffset() = runTest {
        val provider = NavidromeProvider(connection("https://music.example.test"))

        val url = provider.streamUrl(
            StreamRequest(
                trackId = TrackId("abc123"),
                quality = StreamQuality.Transcoded(AudioCodec.Opus, bitrateKbps = 128),
                startPositionSeconds = 95.8,
            ),
        )

        assertEquals(
            "https://music.example.test/rest/stream.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=abc123&format=opus&maxBitRate=128&timeOffset=95",
            url,
        )
    }

    @Test
    fun coverArtUrlIncludesAuthentication() {
        val provider = NavidromeProvider(connection("https://music.example.test"))

        val url = provider.coverArtUrl("cover-1")

        assertEquals(
            "https://music.example.test/rest/getCoverArt.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=cover-1&size=512",
            url,
        )
    }

    @Test
    fun heroCoverArtUrlRequestsA1024PixelImage() {
        val provider = NavidromeProvider(connection("https://music.example.test"))

        val url = provider.coverArtUrl("cover-1", CoverArtSize.Hero)

        assertEquals(
            "https://music.example.test/rest/getCoverArt.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=cover-1&size=1024",
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
            "https://music.example.test/rest/star.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=track-1",
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
            "https://music.example.test/rest/unstar.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=track-1",
            httpClient.urls.single(),
        )
    }

    @Test
    fun setArtistFavoriteCallsStarEndpointWithArtistId() = runTest {
        val httpClient = RecordingHttpClient()
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.setArtistFavorite(ArtistId("artist-1"), favorite = true)

        assertEquals(
            "https://music.example.test/rest/star.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&artistId=artist-1",
            httpClient.urls.single(),
        )
    }

    @Test
    fun setAlbumFavoriteCallsUnstarEndpointWithAlbumId() = runTest {
        val httpClient = RecordingHttpClient()
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.setAlbumFavorite(AlbumId("album-1"), favorite = false)

        assertEquals(
            "https://music.example.test/rest/unstar.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&albumId=album-1",
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
            "https://music.example.test/rest/setRating.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=track-1&rating=4",
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
            "https://music.example.test/rest/setRating.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=track-1&rating=0",
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
    fun validateConnectionEnablesSonicSimilarityWhenOpenSubsonicExtensionIsAdvertised() = runTest {
        val httpClient = SequencedHttpClient(
            listOf(
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "serverVersion": "0.62.0"
                  }
                }
                """.trimIndent(),
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "openSubsonicExtensions": [
                      { "name": "transcodeOffset", "versions": [1] },
                      { "name": "sonicSimilarity", "versions": [1] }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        assertFalse(provider.capabilities.supportsSonicSimilarity)
        provider.validateConnection()

        assertTrue(provider.capabilities.supportsSonicSimilarity)
        assertEquals(
            listOf(
                "https://music.example.test/rest/ping.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json",
                "https://music.example.test/rest/getOpenSubsonicExtensions.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json",
            ),
            httpClient.urls,
        )
    }

    @Test
    fun validateConnectionLeavesSonicSimilarityDisabledWhenExtensionIsMissing() = runTest {
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = SequencedHttpClient(
                listOf(
                    """
                    {
                      "subsonic-response": {
                        "status": "ok",
                        "version": "1.16.1",
                        "serverVersion": "0.62.0"
                      }
                    }
                    """.trimIndent(),
                    """
                    {
                      "subsonic-response": {
                        "status": "ok",
                        "openSubsonicExtensions": [
                          { "name": "transcodeOffset", "versions": [1] }
                        ]
                      }
                    }
                    """.trimIndent(),
                ),
            ),
        )

        provider.validateConnection()

        assertFalse(provider.capabilities.supportsSonicSimilarity)
    }

    @Test
    fun validateConnectionLeavesSonicSimilarityDisabledWhenExtensionsEndpointFails() = runTest {
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = object : NavidromeHttpClient {
                override suspend fun get(url: String): String =
                    if (url.contains("ping.view")) {
                        """
                        {
                          "subsonic-response": {
                            "status": "ok",
                            "version": "1.16.1",
                            "serverVersion": "0.62.0"
                          }
                        }
                        """.trimIndent()
                    } else {
                        throw NavidromeException("Extensions endpoint unavailable.")
                    }
            },
        )

        provider.validateConnection()

        assertFalse(provider.capabilities.supportsSonicSimilarity)
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
                          "created": "2026-05-08T12:00:00Z",
                          "starred": "2026-05-10T08:00:00Z"
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
        assertEquals("2026-05-10T08:00:00Z", albums.first().favoritedAtIso8601)
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
                      "starred": "2026-05-10T08:00:00Z",
                      "releaseTypes": ["Album", "Remixes"],
                      "explicitStatus": "explicit",
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
                          "userRating": 4,
                          "bpm": 132,
                          "mood": ["wistful", "bright"],
                          "playCount": 12,
                          "played": "2026-05-12T14:00:00Z"
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
        assertEquals("2026-05-10T08:00:00Z", details.album.favoritedAtIso8601)
        assertEquals(listOf("Album", "Remixes"), details.album.releaseTypes)
        assertEquals(AlbumExplicitStatus.Explicit, details.album.explicitStatus)
        assertEquals(1985, details.tracks.first().albumReleaseYear)
        assertEquals(259, details.tracks.first().durationSeconds)
        assertEquals("FLAC", details.tracks.first().audioInfo?.codec)
        assertEquals(921, details.tracks.first().audioInfo?.bitrateKbps)
        assertEquals("2026-05-09T13:45:00Z", details.tracks.first().favoritedAtIso8601)
        assertEquals(4, details.tracks.first().userRating)
        assertEquals(132, details.tracks.first().bpm)
        assertEquals(listOf("wistful", "bright"), details.tracks.first().moods)
        assertEquals(12, details.tracks.first().playCount)
        assertEquals("2026-05-12T14:00:00Z", details.tracks.first().lastPlayedAtIso8601)
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
                          "starred": "2026-05-10T09:00:00Z",
                          "album": [
                            {
                              "id": "album-1",
                              "name": "Master of Puppets",
                              "artist": "Metallica",
                              "year": 1986,
                              "coverArt": "cover-1",
                              "starred": "2026-05-11T09:00:00Z"
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
        assertEquals("2026-05-10T09:00:00Z", details.artist.favoritedAtIso8601)
        assertEquals(2, details.albums.size)
        assertEquals("Master of Puppets", details.albums.first().title)
        assertEquals(1986, details.albums.first().releaseYear)
        assertEquals("2026-05-11T09:00:00Z", details.albums.first().favoritedAtIso8601)
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
    fun searchScopesRequestsToSelectedMusicFolders() = runTest {
        val httpClient = RecordingResponseHttpClient(
            """
            {
              "subsonic-response": {
                "status": "ok",
                "searchResult3": {
                  "song": [
                    {
                      "id": "track-1",
                      "title": "Classical Search Result",
                      "artist": "Composer",
                      "musicFolderId": "2"
                    }
                  ]
                }
              }
            }
            """.trimIndent(),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test").copy(
                selectedMusicFolderIds = listOf("2"),
            ),
            httpClient = httpClient,
        )

        val results = provider.search("from duck till dawn")

        assertEquals(
            "https://music.example.test/rest/search3.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&query=from+duck+till+dawn&artistCount=20&artistOffset=0&albumCount=20&albumOffset=0&songCount=20&songOffset=0&musicFolderId=2",
            httpClient.urls.single(),
        )
        assertEquals("Classical Search Result", results.tracks.single().title)
        assertEquals("2", results.tracks.single().musicFolderId)
    }

    @Test
    fun popularTracksUsesNavidromeTopSongsAsMatchedLibraryTracks() = runTest {
        val httpClient = RecordingResponseHttpClient(
            """
            {
              "subsonic-response": {
                "status": "ok",
                "topSongs": {
                  "song": [
                    {
                      "id": "track-1",
                      "title": "Age of Consent",
                      "artistId": "artist-1",
                      "artist": "New Order",
                      "albumId": "album-1",
                      "album": "Power, Corruption & Lies",
                      "duration": 315,
                      "coverArt": "cover-1"
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

        val result = provider.popularTracks(Artist(ArtistId("artist-1"), "New Order"), limit = 12)

        assertEquals(NavidromeAgentMetadataSource, result.source)
        assertEquals("Age of Consent", result.candidates.single().title)
        assertEquals("track-1", result.candidates.single().sourceTrackId)
        assertEquals("track-1", result.matchedTracksBySourceTrackId["track-1"]?.id?.value)
        assertEquals(
            "https://music.example.test/rest/getTopSongs.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&artist=New+Order&count=12",
            httpClient.urls.single(),
        )
    }

    @Test
    fun similarArtistsUsesArtistInfoWithIncludeNotPresent() = runTest {
        val httpClient = RecordingResponseHttpClient(
            """
            {
              "subsonic-response": {
                "status": "ok",
                "artistInfo2": {
                  "similarArtist": [
                    {
                      "id": "artist-2",
                      "name": "Electronic",
                      "coverArt": "artist-2-cover",
                      "musicBrainzId": "55f1f4e6-2a97-4da6-9a7c-b451a2f22475"
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

        val artists = provider.similarArtists(Artist(ArtistId("artist-1"), "New Order"), limit = 20)

        assertEquals(NavidromeAgentMetadataSource, artists.single().source)
        assertEquals("artist-2", artists.single().sourceArtistId)
        assertEquals("Electronic", artists.single().name)
        assertEquals(
            "https://music.example.test/rest/getCoverArt.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=artist-2-cover&size=512",
            artists.single().imageUrl,
        )
        assertEquals("https://musicbrainz.org/artist/55f1f4e6-2a97-4da6-9a7c-b451a2f22475", artists.single().externalUrl)
        assertEquals(
            "https://music.example.test/rest/getArtistInfo2.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=artist-1&count=20&includeNotPresent=true",
            httpClient.urls.single(),
        )
    }

    @Test
    fun similarArtistsBuildsLastFmFallbackWhenNoExternalIdIsPresent() = runTest {
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = FakeHttpClient(
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "artistInfo2": {
                      "similarArtist": [
                        {
                          "name": "The Postal Service"
                        }
                      ]
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        val artists = provider.similarArtists(Artist(ArtistId("artist-1"), "Death Cab for Cutie"), limit = 20)

        assertEquals("https://www.last.fm/music/The+Postal+Service", artists.single().externalUrl)
    }

    @Test
    fun popularTracksHandlesEmptyTopSongsResponse() = runTest {
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = FakeHttpClient(
                """
                {
                  "subsonic-response": {
                    "status": "ok"
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = provider.popularTracks(Artist(ArtistId("artist-1"), "New Order"), limit = 10)

        assertEquals(emptyList(), result.candidates)
        assertEquals(emptyMap(), result.matchedTracksBySourceTrackId)
    }

    @Test
    fun similarArtistsHandlesUnavailableArtistInfo() = runTest {
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = FakeHttpClient(
                """
                {
                  "subsonic-response": {
                    "status": "ok"
                  }
                }
                """.trimIndent(),
            ),
        )

        val artists = provider.similarArtists(Artist(ArtistId("artist-1"), "New Order"), limit = 10)

        assertEquals(emptyList(), artists)
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
            "https://music.example.test/rest/getAlbumList2.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&type=random&size=8",
            httpClient.urls.single(),
        )
        assertEquals("Technique", albums.single().title)
    }

    @Test
    fun albumPageSendsTheBoundedLimitAndOffsetToNavidrome() = runTest {
        val httpClient = RecordingResponseHttpClient(
            albumListResponse(albumId = "album-1", title = "Technique", artist = "New Order"),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        val page = provider.albumsPage(MediaPageRequest(offset = 50, limit = 25))

        assertEquals(
            "https://music.example.test/rest/getAlbumList2.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&type=alphabeticalByName&size=25&offset=50",
            httpClient.urls.single(),
        )
        assertEquals(listOf("Technique"), page.items.map { it.title })
        assertFalse(page.hasMore)
    }

    @Test
    fun artistPageUsesEmptySearchWithServerSidePaging() = runTest {
        val httpClient = RecordingResponseHttpClient(searchArtistResponse("artist-1", "New Order"))
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        val page = provider.artistsPage(MediaPageRequest(offset = 75, limit = 25))

        assertEquals(
            "https://music.example.test/rest/search3.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&query=&artistCount=25&artistOffset=75&albumCount=0&albumOffset=0&songCount=0&songOffset=0",
            httpClient.urls.single(),
        )
        assertEquals(listOf("New Order"), page.items.map { it.name })
        assertFalse(page.hasMore)
    }

    @Test
    fun multiLibraryArtistPagesAdvanceWithoutRefetchingEarlierPages() = runTest {
        val httpClient = SequencedHttpClient(
            listOf(
                searchArtistsResponse("artist-1" to "A", "artist-2" to "B"),
                searchArtistsResponse(),
                searchArtistsResponse("artist-3" to "C"),
            ),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test").copy(
                selectedMusicFolderIds = listOf("rock", "archive"),
            ),
            httpClient = httpClient,
        )

        val first = provider.artistsPage(MediaPageRequest(limit = 2))
        val second = provider.artistsPage(requireNotNull(first.nextRequest))

        assertEquals(listOf("A", "B"), first.items.map { it.name })
        assertEquals("0:2", first.nextContinuationToken)
        assertEquals(listOf("C"), second.items.map { it.name })
        assertFalse(second.hasMore)
        assertTrue(httpClient.urls[1].contains("artistOffset=2"))
        assertTrue(httpClient.urls[1].contains("musicFolderId=rock"))
        assertTrue(httpClient.urls[2].contains("artistOffset=0"))
        assertTrue(httpClient.urls[2].contains("musicFolderId=archive"))
    }

    @Test
    fun albumListScopesRequestsToSelectedMusicFolders() = runTest {
        val httpClient = SequencedHttpClient(
            listOf(
                albumListResponse(albumId = "album-1", title = "Technique", artist = "New Order"),
                albumListResponse(albumId = "album-2", title = "Movement", artist = "New Order"),
            ),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test").copy(
                selectedMusicFolderIds = listOf("rock", "archive"),
            ),
            httpClient = httpClient,
        )

        val albums = provider.albumList(AlbumListType.Random, limit = 8)

        assertEquals(
            listOf(
                "https://music.example.test/rest/getAlbumList2.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&type=random&size=8&musicFolderId=rock",
                "https://music.example.test/rest/getAlbumList2.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&type=random&size=8&musicFolderId=archive",
            ),
            httpClient.urls,
        )
        assertEquals(listOf("Technique", "Movement"), albums.map { it.title })
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
    fun playlistsRecognizeOpenSubsonicSmartPlaylistMetadata() = runTest {
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
                          "id": "smart-1",
                          "name": "Work Ambient",
                          "songCount": 123,
                          "readonly": true,
                          "validUntil": "2026-07-14T20:00:00Z"
                        }
                      ]
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        assertTrue(provider.playlists().single().isSmart)
    }

    @Test
    fun playlistsScopesRequestsToSelectedMusicFolders() = runTest {
        val httpClient = SequencedHttpClient(
            listOf(
                playlistsResponse("playlist-1", "Classical"),
                playlistsResponse("playlist-2", "Piano"),
            ),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test").copy(
                selectedMusicFolderIds = listOf("2", "4"),
            ),
            httpClient = httpClient,
        )

        val playlists = provider.playlists(limit = 20)

        assertEquals(
            listOf(
                "https://music.example.test/rest/getPlaylists.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&musicFolderId=2",
                "https://music.example.test/rest/getPlaylists.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&musicFolderId=4",
            ),
            httpClient.urls,
        )
        assertEquals(listOf("Classical", "Piano"), playlists.map { it.name })
    }

    @Test
    fun playlistTracksScopesRequestAndDropsKnownOutOfScopeEntries() = runTest {
        val httpClient = RecordingResponseHttpClient(
            """
            {
              "subsonic-response": {
                "status": "ok",
                "playlist": {
                  "entry": [
                    {
                      "id": "track-1",
                      "title": "In Scope",
                      "artist": "Composer",
                      "musicFolderId": "2"
                    },
                    {
                      "id": "track-2",
                      "title": "Out Of Scope",
                      "artist": "Band",
                      "musicFolderId": "1"
                    },
                    {
                      "id": "track-3",
                      "title": "Unknown Scope",
                      "artist": "Unknown"
                    }
                  ]
                }
              }
            }
            """.trimIndent(),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test").copy(
                selectedMusicFolderIds = listOf("2"),
            ),
            httpClient = httpClient,
        )

        val tracks = provider.playlistTracks("playlist-1")

        assertEquals(
            "https://music.example.test/rest/getPlaylist.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=playlist-1&musicFolderId=2",
            httpClient.urls.single(),
        )
        assertEquals(listOf("In Scope", "Unknown Scope"), tracks.map { it.title })
    }

    @Test
    fun createPlaylistSendsNameAndSongIds() = runTest {
        val httpClient = RecordingHttpClient()
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.createPlaylist("Road Mix", listOf(TrackId("track-1"), TrackId("track-2")))

        assertEquals(
            "https://music.example.test/rest/createPlaylist.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&name=Road+Mix&songId=track-1&songId=track-2",
            httpClient.urls.single(),
        )
    }

    @Test
    fun createSmartPlaylistUsesNavidromeNativePlaylistApi() = runTest {
        val httpClient = RecordingNativeHttpClient(
            """
            {
              "data": {
                "id": "smart-1",
                "name": "Road Smart",
                "songCount": 12,
                "duration": 3200
              }
            }
            """.trimIndent(),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "native-token"),
            httpClient = httpClient,
        )

        val playlist = provider.createSmartPlaylist(smartPlaylistDefinition())

        assertEquals("smart-1", playlist.id)
        assertEquals("Road Smart", playlist.name)
        assertEquals("https://music.example.test/api/playlist", httpClient.postUrls.single())
        assertEquals(mapOf("x-nd-authorization" to "Bearer native-token"), httpClient.postHeaders.single())
        assertEquals(
            """{"name":"Road Smart","comment":"Fresh tracks","public":true,"rules":{"all":[{"is":{"loved":true}}],"sort":"-rating","limit":25}}""",
            httpClient.postBodies.single(),
        )
    }

    @Test
    fun createSmartPlaylistRetainsRefreshedNativeTokenFromResponseHeader() = runTest {
        val httpClient = RecordingNativeHttpClient(
            response = """{"data":{"id":"smart-1","name":"Road Smart"}}""",
            responseHeaders = mapOf("X-ND-Authorization" to "refreshed-native-token"),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "native-token"),
            httpClient = httpClient,
        )

        provider.createSmartPlaylist(smartPlaylistDefinition())

        assertEquals("refreshed-native-token", provider.connectionWithCurrentNativeToken().nativeToken)
    }

    @Test
    fun createSmartPlaylistScopesDefinitionToSelectedMusicFolder() = runTest {
        val httpClient = RecordingNativeHttpClient(
            """
            {
              "data": {
                "id": "smart-1",
                "name": "Road Smart"
              }
            }
            """.trimIndent(),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "native-token").copy(
                selectedMusicFolderIds = listOf("2"),
            ),
            httpClient = httpClient,
        )

        provider.createSmartPlaylist(smartPlaylistDefinition())

        assertEquals(
            """{"name":"Road Smart","comment":"Fresh tracks","public":true,"rules":{"all":[{"is":{"library_id":2}},{"is":{"loved":true}}],"sort":"-rating","limit":25}}""",
            httpClient.postBodies.single(),
        )
    }

    @Test
    fun createSmartPlaylistPreservesTopLevelAnyWhenScopingToSelectedMusicFolder() = runTest {
        val httpClient = RecordingNativeHttpClient(
            """
            {
              "data": {
                "id": "smart-1",
                "name": "Work Ambient"
              }
            }
            """.trimIndent(),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "native-token").copy(
                selectedMusicFolderIds = listOf("2"),
            ),
            httpClient = httpClient,
        )
        val definition = SmartPlaylistDefinition(
            name = "Work Ambient",
            match = SmartPlaylistMatch.Any,
            rules = listOf(
                SmartPlaylistCondition(
                    SmartPlaylistOperator.Is,
                    SmartPlaylistFields.Artist,
                    SmartPlaylistValue.Text("Ascendant"),
                ),
                SmartPlaylistCondition(
                    SmartPlaylistOperator.Is,
                    SmartPlaylistFields.Artist,
                    SmartPlaylistValue.Text("S1gns Of L1fe"),
                ),
            ),
        )

        provider.createSmartPlaylist(definition)

        assertEquals(
            """{"name":"Work Ambient","rules":{"all":[{"is":{"library_id":2}},{"any":[{"is":{"artist":"Ascendant"}},{"is":{"artist":"S1gns Of L1fe"}}]}]}}""",
            httpClient.postBodies.single(),
        )
    }

    @Test
    fun createSmartPlaylistUsesEditorLibrarySubsetInsteadOfWholeConnectionSelection() = runTest {
        val httpClient = RecordingNativeHttpClient(
            """{"data":{"id":"smart-1","name":"Road Smart"}}""",
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "native-token").copy(
                selectedMusicFolderIds = listOf("2", "4", "6"),
            ),
            httpClient = httpClient,
        )

        provider.createSmartPlaylist(smartPlaylistDefinition().copy(libraryIds = listOf("4", "6")))

        assertEquals(
            """{"name":"Road Smart","comment":"Fresh tracks","public":true,"rules":{"all":[{"any":[{"is":{"library_id":4}},{"is":{"library_id":6}}]},{"is":{"loved":true}}],"sort":"-rating","limit":25}}""",
            httpClient.postBodies.single(),
        )
    }

    @Test
    fun updateSmartPlaylistUsesNavidromeNativePlaylistApi() = runTest {
        val httpClient = RecordingNativeHttpClient("{}")
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "native-token"),
            httpClient = httpClient,
        )

        provider.updateSmartPlaylist("smart playlist/1", smartPlaylistDefinition())

        assertEquals("https://music.example.test/api/playlist/smart+playlist%2F1", httpClient.putUrls.single())
        assertEquals(mapOf("x-nd-authorization" to "Bearer native-token"), httpClient.putHeaders.single())
        assertEquals(
            """{"name":"Road Smart","comment":"Fresh tracks","public":true,"rules":{"all":[{"is":{"loved":true}}],"sort":"-rating","limit":25}}""",
            httpClient.putBodies.single(),
        )
    }

    @Test
    fun updateSmartPlaylistScopesDefinitionToSelectedMusicFolders() = runTest {
        val httpClient = RecordingNativeHttpClient("{}")
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "native-token").copy(
                selectedMusicFolderIds = listOf("2", "4"),
            ),
            httpClient = httpClient,
        )

        provider.updateSmartPlaylist("smart playlist/1", smartPlaylistDefinition())

        assertEquals(
            """{"name":"Road Smart","comment":"Fresh tracks","public":true,"rules":{"all":[{"any":[{"is":{"library_id":2}},{"is":{"library_id":4}}]},{"is":{"loved":true}}],"sort":"-rating","limit":25}}""",
            httpClient.putBodies.single(),
        )
    }

    @Test
    fun smartPlaylistDefinitionUsesNavidromeNativePlaylistApi() = runTest {
        val httpClient = RecordingNativeHttpClient(
            """
            {
              "data": {
                "id": "smart-1",
                "name": "Road Smart",
                "comment": "Fresh tracks",
                "public": true,
                "rules": {
                  "all": [
                    { "is": { "loved": true } }
                  ],
                  "sort": "-rating",
                  "limit": 25
                }
              }
            }
            """.trimIndent(),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "native-token"),
            httpClient = httpClient,
        )

        val definition = provider.smartPlaylistDefinition("smart playlist/1")

        assertEquals("Road Smart", definition.name)
        assertEquals("https://music.example.test/api/playlist/smart+playlist%2F1", httpClient.getUrls.single())
        assertEquals(mapOf("x-nd-authorization" to "Bearer native-token"), httpClient.getHeaders.single())
        assertEquals(25, definition.limit)
    }

    @Test
    fun smartPlaylistDefinitionHidesInjectedSelectedMusicFolderScope() = runTest {
        val httpClient = RecordingNativeHttpClient(
            """
            {
              "data": {
                "id": "smart-1",
                "name": "Road Smart",
                "rules": {
                  "all": [
                    { "is": { "library_id": 2 } },
                    { "is": { "loved": true } }
                  ],
                  "sort": "-rating",
                  "limit": 25
                }
              }
            }
            """.trimIndent(),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "native-token").copy(
                selectedMusicFolderIds = listOf("2"),
            ),
            httpClient = httpClient,
        )

        val definition = provider.smartPlaylistDefinition("smart playlist/1")
        val rule = definition.rules.single() as SmartPlaylistCondition

        assertEquals("Road Smart", definition.name)
        assertEquals(SmartPlaylistFields.Loved, rule.field)
    }

    @Test
    fun smartPlaylistDefinitionRestoresTopLevelAnyAfterRemovingInjectedScope() = runTest {
        val httpClient = RecordingNativeHttpClient(
            """
            {
              "data": {
                "id": "smart-1",
                "name": "Work Ambient",
                "rules": {
                  "all": [
                    { "is": { "library_id": 2 } },
                    {
                      "any": [
                        { "is": { "artist": "Ascendant" } },
                        { "is": { "artist": "S1gns Of L1fe" } }
                      ]
                    }
                  ]
                }
              }
            }
            """.trimIndent(),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "native-token").copy(
                selectedMusicFolderIds = listOf("2"),
            ),
            httpClient = httpClient,
        )

        val definition = provider.smartPlaylistDefinition("smart-1")

        assertEquals(SmartPlaylistMatch.Any, definition.match)
        assertEquals(listOf("2"), definition.libraryIds)
        assertEquals(2, definition.rules.size)
        assertEquals(
            listOf("Ascendant", "S1gns Of L1fe"),
            definition.rules.map { rule ->
                ((rule as SmartPlaylistCondition).value as SmartPlaylistValue.Text).value
            },
        )
    }

    @Test
    fun createSmartPlaylistRequiresNativeToken() = runTest {
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = RecordingNativeHttpClient("{}"),
        )

        val error = assertFailsWith<NavidromeException> {
            provider.createSmartPlaylist(smartPlaylistDefinition())
        }

        assertEquals("Reconnect to Navidrome with your password before saving smart playlists.", error.message)
    }

    @Test
    fun createSmartPlaylistSurfacesExpiredNativeToken() = runTest {
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "expired-token"),
            httpClient = FailingNativeHttpClient(),
        )

        val error = assertFailsWith<NavidromeException> {
            provider.createSmartPlaylist(smartPlaylistDefinition())
        }

        assertEquals("Navidrome returned HTTP 401.", error.message)
    }

    @Test
    fun updateSmartPlaylistSurfacesExpiredNativeToken() = runTest {
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "expired-token"),
            httpClient = FailingNativeHttpClient(),
        )

        val error = assertFailsWith<NavidromeException> {
            provider.updateSmartPlaylist("smart-1", smartPlaylistDefinition())
        }

        assertEquals("Navidrome returned HTTP 401.", error.message)
    }

    @Test
    fun nativeAuthStoresTokenWhenLoginSucceeds() = runTest {
        val httpClient = RecordingNativeHttpClient("""{"token":"native-token"}""")

        val authenticated = connection("https://music.example.test/")
            .withNativeTokenFromPassword("secret", httpClient)

        assertEquals("native-token", authenticated.nativeToken)
        assertEquals("https://music.example.test/auth/login", httpClient.postUrls.single())
        assertEquals("""{"username":"demo","password":"secret"}""", httpClient.postBodies.single())
    }

    @Test
    fun addTracksToPlaylistUsesRepeatedSongIds() = runTest {
        val httpClient = RecordingHttpClient()
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.addTracksToPlaylist("playlist-1", listOf(TrackId("track-1"), TrackId("track-2")))

        assertEquals(
            "https://music.example.test/rest/updatePlaylist.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&playlistId=playlist-1&songIdToAdd=track-1&songIdToAdd=track-2",
            httpClient.urls.single(),
        )
    }

    @Test
    fun replacePlaylistTracksRemovesExistingEntriesAndAddsDraftOrder() = runTest {
        val httpClient = RecordingHttpClient()
        val provider = NavidromeProvider(connection("https://music.example.test"), httpClient)

        provider.replacePlaylistTracks(
            playlistId = "playlist-1",
            currentTrackCount = 3,
            trackIds = listOf(TrackId("track-3"), TrackId("track-1")),
        )

        assertEquals(
            "https://music.example.test/rest/updatePlaylist.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&playlistId=playlist-1&songIndexToRemove=0&songIndexToRemove=1&songIndexToRemove=2&songIdToAdd=track-3&songIdToAdd=track-1",
            httpClient.urls.single(),
        )
    }

    @Test
    fun renamePlaylistUsesUpdatePlaylist() = runTest {
        val httpClient = RecordingHttpClient()
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.renamePlaylist("playlist-1", "New Name")

        assertEquals(
            "https://music.example.test/rest/updatePlaylist.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&playlistId=playlist-1&name=New+Name",
            httpClient.urls.single(),
        )
    }

    @Test
    fun deletePlaylistUsesDeletePlaylist() = runTest {
        val httpClient = RecordingHttpClient()
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.deletePlaylist("playlist-1")

        assertEquals(
            "https://music.example.test/rest/deletePlaylist.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=playlist-1",
            httpClient.urls.single(),
        )
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
            "https://music.example.test/rest/getRandomSongs.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&size=12&genre=House&fromYear=2000&toYear=2009",
            httpClient.urls.single(),
        )
        assertEquals("House Track", tracks.single().title)
    }

    @Test
    fun internetRadioStationsMapSubsonicStations() = runTest {
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = FakeHttpClient(
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "internetRadioStations": {
                      "internetRadioStation": [
                        {
                          "id": "station-1",
                          "name": "KEXP",
                          "streamUrl": "https://kexp.example/stream",
                          "homePageUrl": "https://kexp.org"
                        }
                      ]
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        val stations = provider.internetRadioStations()

        assertEquals("station-1", stations.single().id)
        assertEquals("KEXP", stations.single().name)
        assertEquals("https://kexp.example/stream", stations.single().streamUrl)
        assertEquals("https://kexp.org", stations.single().homePageUrl)
    }

    @Test
    fun createInternetRadioStationSendsStationFields() = runTest {
        val httpClient = RecordingResponseHttpClient(
            """
            {
              "subsonic-response": {
                "status": "ok",
                "internetRadioStations": {
                  "internetRadioStation": [
                    {
                      "id": "station-1",
                      "name": "KEXP",
                      "streamUrl": "https://kexp.example/stream",
                      "homePageUrl": "https://kexp.org"
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

        provider.createInternetRadioStation(
            name = "KEXP",
            streamUrl = "https://kexp.example/stream",
            homePageUrl = "https://kexp.org",
        )

        assertEquals(
            "https://music.example.test/rest/createInternetRadioStation.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&name=KEXP&streamUrl=https%3A%2F%2Fkexp.example%2Fstream&homePageUrl=https%3A%2F%2Fkexp.org",
            httpClient.urls.first(),
        )
    }

    @Test
    fun updateAndDeleteInternetRadioStationsUseSubsonicEndpoints() = runTest {
        val httpClient = RecordingHttpClient()
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.updateInternetRadioStation(
            app.naviamp.domain.InternetRadioStation(
                id = "station-1",
                name = "KEXP",
                streamUrl = "https://kexp.example/stream",
                homePageUrl = null,
            ),
        )
        provider.deleteInternetRadioStation("station-1")

        assertEquals(
            "https://music.example.test/rest/updateInternetRadioStation.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=station-1&name=KEXP&streamUrl=https%3A%2F%2Fkexp.example%2Fstream",
            httpClient.urls.first(),
        )
        assertEquals(
            "https://music.example.test/rest/deleteInternetRadioStation.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=station-1",
            httpClient.urls.last(),
        )
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
            "https://music.example.test/rest/getSimilarSongs2.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=artist-1&count=25",
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
            "https://music.example.test/rest/getSimilarSongs.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=album-1&count=30",
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
            "https://music.example.test/rest/getSimilarSongs.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=seed-track&count=20",
            httpClient.urls.single(),
        )
        assertEquals(listOf("track-3"), tracks.map { it.id.value })
    }

    @Test
    fun sonicSimilarTracksUsesOpenSubsonicSonicMatchEntriesAndFiltersSeedTrack() = runTest {
        val httpClient = RecordingResponseHttpClient(
            """
            {
              "subsonic-response": {
                "status": "ok",
                "sonicMatch": [
                  {
                    "entry": {
                      "id": "seed-track",
                      "title": "Seed",
                      "artist": "New Order"
                    },
                    "similarity": 1.0
                  },
                  {
                    "entry": {
                      "id": "track-4",
                      "title": "Your Silent Face",
                      "artistId": "artist-1",
                      "artist": "New Order",
                      "albumId": "album-1",
                      "album": "Power, Corruption & Lies",
                      "duration": 359,
                      "coverArt": "cover-1"
                    },
                    "similarity": 0.92
                  }
                ]
              }
            }
            """.trimIndent(),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        val matches = provider.sonicSimilarTrackMatches(TrackId("seed-track"), count = 12)

        assertEquals(
            "https://music.example.test/rest/getSonicSimilarTracks.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=seed-track&count=12",
            httpClient.urls.single(),
        )
        val tracks = matches.map { it.track }
        assertEquals(listOf("track-4"), tracks.map { it.id.value })
        assertEquals("Your Silent Face", tracks.single().title)
        assertEquals(0.92, matches.single().similarity)
    }

    @Test
    fun findSonicPathUsesOpenSubsonicSonicPathEntries() = runTest {
        val httpClient = RecordingResponseHttpClient(
            """
            {
              "subsonic-response": {
                "status": "ok",
                "sonicMatch": [
                  {
                    "entry": {
                      "id": "start-track",
                      "title": "Start",
                      "artist": "New Order"
                    },
                    "similarity": 1.0
                  },
                  {
                    "entry": {
                      "id": "middle-track",
                      "title": "The Perfect Kiss",
                      "artistId": "artist-1",
                      "artist": "New Order",
                      "albumId": "album-1",
                      "album": "Low-Life",
                      "duration": 288,
                      "coverArt": "cover-1"
                    },
                    "similarity": 0.76
                  },
                  {
                    "entry": {
                      "id": "end-track",
                      "title": "End",
                      "artist": "New Order"
                    },
                    "similarity": 1.0
                  }
                ]
              }
            }
            """.trimIndent(),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        val matches = provider.findSonicPath(
            startTrackId = TrackId("start-track"),
            endTrackId = TrackId("end-track"),
            count = 10,
        )

        assertEquals(
            "https://music.example.test/rest/findSonicPath.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&startSongId=start-track&endSongId=end-track&count=10",
            httpClient.urls.single(),
        )
        assertEquals(listOf("start-track", "middle-track", "end-track"), matches.map { it.track.id.value })
        assertEquals("The Perfect Kiss", matches[1].track.title)
        assertEquals(0.76, matches[1].similarity)
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
            "https://music.example.test/rest/getLyricsBySongId.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=track-lyrics",
            httpClient.urls.single(),
        )
        assertEquals(true, lyrics?.synced)
        assertEquals(listOf(12000L, 17000L), lyrics?.lines?.map { it.startMillis })
        assertEquals("New Order", lyrics?.displayArtist)
    }

    @Test
    fun enhancedLyricsUseSongLyricsV2CuesWhenAdvertised() = runTest {
        val httpClient = SequencedHttpClient(
            listOf(
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "serverVersion": "0.63.0"
                  }
                }
                """.trimIndent(),
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "openSubsonicExtensions": [
                      { "name": "songLyrics", "versions": [1, 2] }
                    ]
                  }
                }
                """.trimIndent(),
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
                          "kind": "main",
                          "offset": -750,
                          "synced": true,
                          "line": [
                            { "start": 12000, "value": "This is why events unnerve me" }
                          ],
                          "agents": [
                            { "id": "vocal-1", "name": "Lead", "role": "main" }
                          ],
                          "cueLine": [
                            {
                              "index": 0,
                              "start": 12000,
                              "end": 14200,
                              "value": "This is why events unnerve me",
                              "agentId": "vocal-1",
                              "cue": [
                                { "start": 12000, "end": 12400, "value": "This", "byteStart": 0, "byteEnd": 3 },
                                { "start": 12500, "end": 13000, "value": "is", "byteStart": 5, "byteEnd": 6 }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.validateConnection()
        val lyrics = provider.lyrics(TrackId("track-lyrics"))

        assertEquals(
            listOf(
                "https://music.example.test/rest/ping.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json",
                "https://music.example.test/rest/getOpenSubsonicExtensions.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json",
                "https://music.example.test/rest/getLyricsBySongId.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=track-lyrics&enhanced=true",
            ),
            httpClient.urls,
        )
        assertEquals("main", lyrics?.kind)
        assertEquals(750, lyrics?.offsetMillis)
        assertEquals("vocal-1", lyrics?.agents?.single()?.id)
        assertEquals("Lead", lyrics?.agents?.single()?.name)
        assertEquals(0, lyrics?.cueLines?.single()?.lineIndex)
        assertEquals("vocal-1", lyrics?.cueLines?.single()?.agentId)
        assertEquals(listOf("This", "is"), lyrics?.cueLines?.single()?.cues?.map { it.text })
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
            "https://music.example.test/rest/scrobble.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=track-1&submission=false",
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
            "https://music.example.test/rest/scrobble.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=track-1&submission=true&time=1778526000000",
            httpClient.urls.single(),
        )
    }

    private fun smartPlaylistDefinition(): SmartPlaylistDefinition =
        SmartPlaylistDefinition(
            name = "Road Smart",
            comment = "Fresh tracks",
            rules = listOf(
                SmartPlaylistCondition(
                    operator = SmartPlaylistOperator.Is,
                    field = SmartPlaylistFields.Loved,
                    value = SmartPlaylistValue.Flag(true),
                ),
            ),
            sort = listOf(SmartPlaylistSort(SmartPlaylistFields.Rating, descending = true)),
            limit = 25,
            isPublic = true,
        )

    private fun albumListResponse(albumId: String, title: String, artist: String): String =
        """
        {
          "subsonic-response": {
            "status": "ok",
            "albumList2": {
              "album": [
                {
                  "id": "$albumId",
                  "name": "$title",
                  "artist": "$artist"
                }
              ]
            }
          }
        }
        """.trimIndent()

    private fun searchArtistResponse(id: String, name: String): String =
        searchArtistsResponse(id to name)

    private fun searchArtistsResponse(vararg artists: Pair<String, String>): String =
        """
        {
          "subsonic-response": {
            "status": "ok",
            "searchResult3": {
              "artist": [
                ${artists.joinToString(",") { (id, name) -> """{"id":"$id","name":"$name"}""" }}
              ]
            }
          }
        }
        """.trimIndent()

    private fun playlistsResponse(playlistId: String, name: String): String =
        """
        {
          "subsonic-response": {
            "status": "ok",
            "playlists": {
              "playlist": [
                {
                  "id": "$playlistId",
                  "name": "$name",
                  "songCount": 10
                }
              ]
            }
          }
        }
        """.trimIndent()

    private fun connection(baseUrl: String, nativeToken: String? = null): NavidromeConnection =
        NavidromeConnection(
            baseUrl = baseUrl,
            username = "demo",
            token = "token",
            salt = "salt",
            nativeToken = nativeToken,
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
        val urls = mutableListOf<String>()
        private var index = 0

        override suspend fun get(url: String): String {
            urls += url
            return responses[index++]
        }
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

    private class RecordingNativeHttpClient(
        private val response: String,
        private val responseHeaders: Map<String, String> = emptyMap(),
    ) : NavidromeHttpClient {
        val getUrls = mutableListOf<String>()
        val getHeaders = mutableListOf<Map<String, String>>()
        val postUrls = mutableListOf<String>()
        val postBodies = mutableListOf<String>()
        val postHeaders = mutableListOf<Map<String, String>>()
        val putUrls = mutableListOf<String>()
        val putBodies = mutableListOf<String>()
        val putHeaders = mutableListOf<Map<String, String>>()

        override suspend fun get(url: String): String = response

        override suspend fun get(url: String, headers: Map<String, String>): String {
            getUrls += url
            getHeaders += headers
            return response
        }

        override suspend fun getResponse(url: String, headers: Map<String, String>): NavidromeHttpResponse =
            NavidromeHttpResponse(get(url, headers), responseHeaders)

        override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String {
            postUrls += url
            postBodies += body
            postHeaders += headers
            return response
        }

        override suspend fun postJsonResponse(
            url: String,
            body: String,
            headers: Map<String, String>,
        ): NavidromeHttpResponse = NavidromeHttpResponse(postJson(url, body, headers), responseHeaders)

        override suspend fun putJson(url: String, body: String, headers: Map<String, String>): String {
            putUrls += url
            putBodies += body
            putHeaders += headers
            return response
        }

        override suspend fun putJsonResponse(
            url: String,
            body: String,
            headers: Map<String, String>,
        ): NavidromeHttpResponse = NavidromeHttpResponse(putJson(url, body, headers), responseHeaders)
    }

    private class FailingNativeHttpClient : NavidromeHttpClient {
        override suspend fun get(url: String): String = throw NavidromeException("Navidrome returned HTTP 401.")

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
        ): String = throw NavidromeException("Navidrome returned HTTP 401.")

        override suspend fun putJson(
            url: String,
            body: String,
            headers: Map<String, String>,
        ): String = throw NavidromeException("Navidrome returned HTTP 401.")
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

private val ExpectedClientQuery = "c=${NaviampClientName.urlEncode()}"
