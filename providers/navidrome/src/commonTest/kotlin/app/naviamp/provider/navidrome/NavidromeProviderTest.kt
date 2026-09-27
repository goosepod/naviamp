package app.naviamp.provider.navidrome

import app.naviamp.domain.AlbumId
import app.naviamp.domain.AlbumExplicitStatus
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.AudioCodec
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.provider.AlphabeticalLibraryKind
import app.naviamp.domain.provider.CoverArtSize
import app.naviamp.domain.provider.MediaPageRequest
import app.naviamp.domain.provider.PlaybackReportState
import app.naviamp.domain.provider.ProviderIdSubsonic
import app.naviamp.domain.provider.ProviderIdBandcamp
import app.naviamp.domain.network.NaviampClientName
import app.naviamp.domain.popular.NavidromeAgentMetadataSource
import app.naviamp.domain.smartplaylist.SmartPlaylistCondition
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistFields
import app.naviamp.domain.smartplaylist.SmartPlaylistMatch
import app.naviamp.domain.smartplaylist.SmartPlaylistOperator
import app.naviamp.domain.smartplaylist.SmartPlaylistSort
import app.naviamp.domain.smartplaylist.SmartPlaylistValue
import app.naviamp.domain.source.MediaSourceIdentity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavidromeProviderTest {
    @Test
    fun apiKeyModeUsesOnlyKeyAndResolvesAccountThroughTokenInfo() = runTest {
        val urls = mutableListOf<String>()
        val client = object : NavidromeHttpClient {
            override suspend fun get(url: String): String {
                urls += url
                val payload = when {
                    "tokenInfo.view" in url -> "\"tokenInfo\":{\"username\":\"demo\"}"
                    "getOpenSubsonicExtensions.view" in url ->
                        "\"openSubsonicExtensions\":[{\"name\":\"apiKeyAuthentication\",\"versions\":[1]}]"
                    else -> ""
                }
                return """{"subsonic-response":{"status":"ok"${if (payload.isEmpty()) "" else ",$payload"}}}"""
            }
        }
        val provider = NavidromeProvider(
            NavidromeConnection.fromApiKey(baseUrl = "https://music.example.test", apiKey = "nds_secret"),
            client,
        )
        assertFalse(provider.capabilities.supportsSmartPlaylists)
        provider.validateConnection()
        assertEquals("demo", provider.apiKeyAccountUsername())
        val art = provider.coverArtUrl("cover")
        assertTrue(art.contains("apiKey=nds_secret"))
        assertFalse(provider.artworkCacheKey(art).contains("nds_secret"))
        assertTrue(urls.all { it.contains("apiKey=nds_secret") })
        assertTrue(urls.none { "&u=" in it || "&t=" in it || "&s=" in it || "&p=" in it })
    }

    @Test
    fun revokedApiKeyReturnsActionableStatusWithoutTheSecret() = runTest {
        val provider = NavidromeProvider(
            NavidromeConnection.fromApiKey(baseUrl = "https://music.example.test", apiKey = "nds_secret"),
            FakeHttpClient("""{"subsonic-response":{"status":"failed","error":{"code":44,"message":"nds_secret rejected"}}}"""),
        )
        val failure = assertFailsWith<NavidromeException> { provider.validateConnection() }
        assertEquals(44, failure.subsonicErrorCode)
        assertEquals("connection_api_key_invalid", failure.message)
    }

    @Test
    fun structuredError41IsPreservedForNegotiation() = runTest {
        val provider = NavidromeProvider(
            connection("https://music.example.test"),
            FakeHttpClient("""{"subsonic-response":{"status":"failed","error":{"code":41,"message":"Token authentication not supported"}}}"""),
        )
        val error = assertFailsWith<NavidromeException> { provider.validateConnection() }
        assertEquals(41, error.subsonicErrorCode)
    }

    @Test
    fun passwordModeUsesUtf8HexWithoutTokenAndRedactsArtworkCacheKey() {
        val provider = NavidromeProvider(connection("https://music.example.test").copy(
            token = "", salt = "", password = "p ä&?",
        ))
        val url = provider.coverArtUrl("cover-1")
        assertTrue(url.contains("u=demo&p=enc%3A7020c3a4263f"))
        assertFalse(url.contains("&t="))
        assertFalse(url.contains("&s="))
        assertFalse(provider.artworkCacheKey(url).contains("enc"))
    }

    @Test
    fun genericSubsonicUsesItsPersistedIdentityAndDisablesNavidromeSmartPlaylists() {
        val provider = NavidromeProvider(
            connection("https://subsonic.example.test").copy(providerId = ProviderIdSubsonic),
        )

        assertEquals(ProviderIdSubsonic, provider.id.value)
        assertEquals("Subsonic", provider.displayName)
        assertFalse(provider.capabilities.supportsSmartPlaylists)
        assertTrue(provider.cacheNamespace.startsWith("subsonic:"))
    }

    @Test
    fun bandcampUsesConservativeBetaCapabilitiesAndOriginalStreams() = runTest {
        val provider = NavidromeProvider(
            connection("https://bandcamp.com/api/subsonic").copy(providerId = ProviderIdBandcamp),
        )

        val url = provider.streamUrl(
            StreamRequest(
                trackId = TrackId("purchase-1"),
                quality = StreamQuality.Transcoded(AudioCodec.Opus, 128),
            ),
        )

        assertEquals(ProviderIdBandcamp, provider.id.value)
        assertEquals("Bandcamp", provider.displayName)
        assertFalse(provider.capabilities.supportsStreamingTranscode)
        assertFalse(provider.capabilities.supportsDownloadTranscode)
        assertFalse(provider.capabilities.supportsTrackFavorites)
        assertFalse(provider.capabilities.supportsTrackRatings)
        assertFalse(provider.capabilities.supportsTrackRadio)
        assertEquals(
            "https://bandcamp.com/api/subsonic/rest/stream.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=purchase-1&format=raw",
            url,
        )
    }

    @Test
    fun bandcampDownloadsUseItsSupportedRawStreamWithoutPlaybackOffset() = runTest {
        val provider = NavidromeProvider(
            connection("https://bandcamp.com/api/subsonic").copy(providerId = ProviderIdBandcamp),
        )
        val request = StreamRequest(TrackId("purchase-1"), StreamQuality.Original, startPositionSeconds = 20.0)
        val url = provider.downloadUrl(request)
        assertTrue(url.contains("/stream.view?"))
        assertTrue(url.contains("format=raw"))
        assertFalse(url.contains("timeOffset"))
        assertEquals(url, provider.downloadUrl(request.copy(quality = StreamQuality.Transcoded(AudioCodec.Opus, 128))))
    }

    @Test
    fun streamUrlUsesNormalizedBaseUrl() = runTest {
        val provider = NavidromeProvider(connection("https://music.example.test/"))

        val url = provider.streamUrl(StreamRequest(TrackId("abc123"), StreamQuality.Original))

        assertEquals(
            "https://music.example.test/rest/stream.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=abc123&format=raw",
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
    fun transcodedStreamUrlOmitsUnadvertisedTimeOffset() = runTest {
        val provider = NavidromeProvider(connection("https://music.example.test"))

        val url = provider.streamUrl(
            StreamRequest(
                trackId = TrackId("abc123"),
                quality = StreamQuality.Transcoded(AudioCodec.Opus, bitrateKbps = 128),
                startPositionSeconds = 95.8,
            ),
        )

        assertEquals(
            "https://music.example.test/rest/stream.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=abc123&format=opus&maxBitRate=128",
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
    fun artworkCacheKeyIgnoresRotatingSubsonicCredentials() {
        val first = NavidromeProvider(connection("https://music.example.test"))
        val second = NavidromeProvider(
            connection("https://music.example.test").copy(token = "new-token", salt = "new-salt"),
        )

        val firstUrl = first.coverArtUrl("cover-1")
        val secondUrl = second.coverArtUrl("cover-1")

        assertEquals(first.artworkCacheKey(firstUrl), second.artworkCacheKey(secondUrl))
        assertFalse(first.artworkCacheKey(firstUrl).contains("token"))
        assertFalse(first.artworkCacheKey(firstUrl).contains("salt"))
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
                      { "name": "sonicSimilarity", "versions": [1] },
                      { "name": "topSongsByArtistId", "versions": [1] }
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
            NavidromeCanonicalIdMigrationSupport.Confirmed,
            provider.canonicalIdMigrationSupport(),
        )
        assertEquals(
            listOf(
                "https://music.example.test/rest/ping.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json",
                "https://music.example.test/rest/getOpenSubsonicExtensions.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json",
                "https://music.example.test/rest/getUser.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&username=demo",
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
        assertEquals(
            NavidromeCanonicalIdMigrationSupport.Unsupported,
            provider.canonicalIdMigrationSupport(),
        )
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
        assertEquals(
            NavidromeCanonicalIdMigrationSupport.Inconclusive,
            provider.canonicalIdMigrationSupport(),
        )
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
    fun favoriteTracksMapsStarredSongs() = runTest {
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = FakeHttpClient(
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "starred2": {
                      "song": [
                        {
                          "id": "favorite-1",
                          "title": "Favorite Song",
                          "artist": "Favorite Artist",
                          "album": "Favorite Album",
                          "duration": 180,
                          "starred": "2026-07-15T12:00:00Z"
                        }
                      ]
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        val tracks = provider.favoriteTracks()

        assertEquals(listOf("favorite-1"), tracks.map { it.id.value })
        assertEquals("2026-07-15T12:00:00Z", tracks.single().favoritedAtIso8601)
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
                      "artists": [
                        {"id": "artist-1", "name": "New Order"},
                        {"id": "artist-2", "name": "Gillian Gilbert"}
                      ],
                      "coverArt": "cover-1",
                      "year": 1981,
                      "originalReleaseDate": {"year": 1981, "month": 9, "day": 1},
                      "releaseDate": {"year": 1985, "month": 5, "day": 13},
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
                          "artists": [
                            {"id": "artist-1", "name": "New Order"},
                            {"id": "artist-3", "name": "Arthur Baker"}
                          ],
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
                          "genre": "Alternative",
                          "genres": [{"name": "New Wave"}, {"name": "Alternative"}],
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
        assertEquals(listOf("artist-1", "artist-3"), details.tracks.first().artistCredits.mapNotNull { it.id?.value })
        assertEquals(listOf("New Order", "Arthur Baker"), details.tracks.first().artistCredits.map { it.name })
        assertEquals(listOf("New Order", "Gillian Gilbert"), details.album.artistCredits.map { it.name })
        assertEquals("album-1", details.tracks.first().albumId?.value)
        assertEquals(1985, details.album.releaseYear)
        assertEquals(1981, details.album.originalReleaseYear)
        assertEquals("2026-05-10T08:00:00Z", details.album.favoritedAtIso8601)
        assertEquals(listOf("Album", "Remixes"), details.album.releaseTypes)
        assertEquals(AlbumExplicitStatus.Explicit, details.album.explicitStatus)
        assertEquals(1985, details.tracks.first().albumReleaseYear)
        assertEquals(1981, details.tracks.first().originalReleaseYear)
        assertEquals(1981, details.tracks.last().originalReleaseYear)
        assertEquals(259, details.tracks.first().durationSeconds)
        assertEquals("FLAC", details.tracks.first().audioInfo?.codec)
        assertEquals(921, details.tracks.first().audioInfo?.bitrateKbps)
        assertEquals("2026-05-09T13:45:00Z", details.tracks.first().favoritedAtIso8601)
        assertEquals(4, details.tracks.first().userRating)
        assertEquals(132, details.tracks.first().bpm)
        assertEquals(listOf("wistful", "bright"), details.tracks.first().moods)
        assertEquals(listOf("Alternative", "New Wave"), details.tracks.first().genres)
        assertEquals(12, details.tracks.first().playCount)
        assertEquals("2026-05-12T14:00:00Z", details.tracks.first().lastPlayedAtIso8601)
    }

    @Test
    fun albumMapsScalarPrimaryArtistCredit() = runTest {
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = FakeHttpClient(
                """{"subsonic-response":{"status":"ok","album":{"id":"album-1","name":"Album","artist":"Artist","artistId":"artist-1"}}}""",
            ),
        )

        val album = provider.album(AlbumId("album-1")).album

        assertEquals(listOf("artist-1"), album.artistCredits.mapNotNull { it.id?.value })
        assertEquals(listOf("Artist"), album.artistCredits.map { it.name })
    }

    @Test
    fun albumUsesLegacyAndEditionYearsWhenOriginalDateIsMissing() = runTest {
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = FakeHttpClient(
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "album": {
                      "id": "compilation-1",
                      "name": "Compilation",
                      "artist": "Various Artists",
                      "year": 1979,
                      "releaseDate": {"year": 2002},
                      "song": [
                        {"id": "track-1979", "title": "Early Track", "artist": "Artist One", "album": "Compilation", "year": 1979},
                        {"id": "track-1992", "title": "Later Track", "artist": "Artist Two", "album": "Compilation", "year": 1992}
                      ]
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        val details = provider.album(AlbumId("compilation-1"))

        assertEquals(2002, details.album.releaseYear)
        assertEquals(1979, details.album.originalReleaseYear)
        assertEquals(listOf(1979, 1992), details.tracks.map { it.originalReleaseYear })
    }

    @Test
    fun albumMapsOptionalAlbumInformation() = runTest {
        val http = SequencedHttpClient(
            listOf(
                """{"subsonic-response":{"status":"ok","album":{"id":"album-1","name":"Low-Life","artist":"New Order"}}}""",
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "albumInfo": {
                      "notes": "The third studio album by New Order.",
                      "musicBrainzId": "release-group-1",
                      "smallImageUrl": "https://images.test/small.jpg",
                      "mediumImageUrl": "https://images.test/medium.jpg",
                      "largeImageUrl": "https://images.test/large.jpg"
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
        val provider = NavidromeProvider(connection("https://music.example.test"), http)

        val details = provider.album(AlbumId("album-1"))
        val info = provider.albumInfo(AlbumId("album-1"))

        assertEquals("Low-Life", details.album.title)
        assertEquals("The third studio album by New Order.", info?.notes)
        assertEquals("release-group-1", info?.musicBrainzId)
        assertEquals("https://images.test/large.jpg", info?.largeImageUrl)
        assertTrue(http.urls.last().contains("/rest/getAlbumInfo2.view"))
        assertTrue(http.urls.last().contains("id=album-1"))
    }

    @Test
    fun albumInformationDoesNotReuseId3IdsInDirectoryEndpoint() = runTest {
        val http = SequencedHttpClient(listOf(
            """{"subsonic-response":{"status":"failed","error":{"code":70,"message":"Not supported"}}}"""
        ))
        val provider = NavidromeProvider(connection("https://music.example.test"), http)
        assertFailsWith<NavidromeException> { provider.albumInfo(AlbumId("album-1")) }
        assertEquals(1, http.urls.size)
        assertTrue(http.urls.single().contains("/rest/getAlbumInfo2.view"))
    }
    @Test
    fun unavailableEmptyAndMalformedAlbumInformationDoNotFailAlbumLoading() = runTest {
        val albumResponse =
            """{"subsonic-response":{"status":"ok","album":{"id":"album-1","name":"Low-Life","artist":"New Order"}}}"""
        val informationResponses = listOf(
            """{"subsonic-response":{"status":"failed","error":{"code":70,"message":"Not supported"}}}""",
            """{"subsonic-response":{"status":"ok"}}""",
            """{"subsonic-response":{"status":"ok","albumInfo":{"notes":42,"largeImageUrl":false}}}""",
        )

        informationResponses.forEach { informationResponse ->
            val provider = NavidromeProvider(
                connection("https://music.example.test"),
                SequencedHttpClient(listOf(albumResponse, informationResponse)),
            )

            val details = provider.album(AlbumId("album-1"))
            val info = runCatching { provider.albumInfo(AlbumId("album-1")) }.getOrNull()

            assertEquals("Low-Life", details.album.title)
            assertEquals(null, info)
        }
    }

    @Test
    fun partialAlbumInformationKeepsAvailableFields() = runTest {
        val provider = NavidromeProvider(
            connection("https://music.example.test"),
            SequencedHttpClient(
                listOf(
                    """{"subsonic-response":{"status":"ok","album":{"id":"album-1","name":"Low-Life","artist":"New Order"}}}""",
                    """{"subsonic-response":{"status":"ok","albumInfo":{"notes":"Album notes only."}}}""",
                ),
            ),
        )

        provider.album(AlbumId("album-1"))
        val info = provider.albumInfo(AlbumId("album-1"))

        assertEquals("Album notes only.", info?.notes)
        assertEquals(null, info?.musicBrainzId)
        assertEquals(null, info?.largeImageUrl)
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

        val discography = provider.artistDiscography(ArtistId("artist-1"))
        val details = discography.primary

        assertEquals("Metallica", details.artist.name)
        assertEquals("2026-05-10T09:00:00Z", details.artist.favoritedAtIso8601)
        assertEquals(2, details.albums.size)
        assertEquals("Master of Puppets", details.albums.first().title)
        assertEquals(1986, details.albums.first().releaseYear)
        assertEquals("2026-05-11T09:00:00Z", details.albums.first().favoritedAtIso8601)
        assertEquals("Garage Days Re-Revisited", details.albums.last().title)
        assertEquals("Thrash metal band.", details.info?.biography)
        assertEquals("https://images.example.test/large.jpg", details.info?.largeImageUrl)
        assertTrue(discography.appearanceAlbums.isEmpty(),
            "Servers that return only the traditional primary discography remain unchanged.")
        assertTrue(discography.appearanceTracks.isEmpty())
        assertFalse(provider.capabilities.supportsArtistDiscography)
    }

    @Test
    fun artistParticipationsAreSeparatedFromPrimaryReleasesUsingStableCreditIds() = runTest {
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = SequencedHttpClient(listOf(
                """{"subsonic-response":{"status":"ok","artist":{
                    "id":"selected","name":"Selected artist","album":[
                        {"id":"own","name":"Own album","artist":"Alias","artistId":"selected"},
                        {"id":"own","name":"Duplicate own album","artist":"Alias","artistId":"selected"},
                        {"id":"guest","name":"Guest appearance","artist":"Other","artistId":"other"},
                        {"id":"collaboration","name":"Joint album","artist":"Other","artistId":"other",
                         "artists":[{"id":"other","name":"Other"},{"id":"selected","name":"Alias"}]},
                        {"id":"same-name","name":"Names are not identities","artist":"Selected artist","artistId":"different"},
                        {"id":"guest","name":"Duplicate guest appearance","artist":"Other","artistId":"other"},
                        {"id":"legacy","name":"Legacy album","artist":"Different display name"},
                        {"id":"partial","name":"Incomplete credit IDs","artist":"Other / Alias",
                         "artists":[{"id":"other","name":"Other"},{"name":"Alias"}]}
                    ]
                }}}""",
                """{"subsonic-response":{"status":"ok","artistInfo2":{}}}""",
                """{"subsonic-response":{"status":"ok","album":{
                    "id":"guest","name":"Guest appearance","artist":"Other","artistId":"other","song":[
                        {"id":"guest-track","title":"Guest verse","albumId":"guest","artist":"Other / Alias",
                         "artists":[{"id":"other","name":"Other"},{"id":"selected","name":"Alias"}]},
                        {"id":"unmapped-track","title":"Unmapped remix","albumId":"guest","artist":"Alias",
                         "artists":[{"name":"Alias"}]},
                        {"id":"unrelated-track","title":"Other song","albumId":"guest","artist":"Other","artistId":"other",
                         "artists":[{"id":"other","name":"Other"}]}
                    ]
                }}}""",
                """{"subsonic-response":{"status":"ok","album":{
                    "id":"same-name","name":"Names are not identities","artist":"Selected artist","artistId":"different","song":[
                        {"id":"same-name-track","title":"Credited by ID","albumId":"same-name","artist":"Alias","artistId":"selected"}
                    ]
                }}}""",
            )),
        )

        val discography = provider.artistDiscography(ArtistId("selected"))

        assertEquals(listOf("own", "collaboration", "legacy", "partial"),
            discography.primary.albums.map { it.id.value })
        assertEquals(listOf("other", "selected"),
            discography.primary.albums[1].artistCredits.mapNotNull { it.id?.value })
        assertEquals("Other", discography.primary.albums[1].artistName,
            "The scalar display artist may omit a valid secondary album artist supplied in structured credits.")
        assertEquals(listOf("guest", "same-name"),
            discography.appearanceAlbums.map { it.id.value })
        assertEquals(listOf("guest-track", "unmapped-track", "same-name-track"),
            discography.appearanceTracks.map { it.id.value })
        assertEquals(listOf("other", "selected"),
            discography.appearanceTracks.first().artistCredits.mapNotNull { it.id?.value })
        assertTrue(provider.capabilities.supportsArtistDiscography)
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
                          "artists": [
                            {"id": "artist-1", "name": "New Order"},
                            {"id": "artist-3", "name": "Arthur Baker"}
                          ],
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
        assertEquals(listOf("New Order", "Arthur Baker"), results.tracks.first().artistCredits.map { it.name })
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
    fun popularTracksIncludesArtistIdWhenTheServerAdvertisesSupport() = runTest {
        val httpClient = SequencedHttpClient(
            listOf(
                """{"subsonic-response":{"status":"ok","version":"1.16.1","serverVersion":"custom-build"}}""",
                """{"subsonic-response":{"status":"ok","openSubsonicExtensions":[{"name":"topSongsByArtistId","versions":[1]}]}}""",
                """{"subsonic-response":{"status":"ok","topSongs":{"song":[]}}}""",
            ),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.validateConnection()
        provider.popularTracks(Artist(ArtistId("artist-1"), "New Order"), limit = 12)

        assertEquals(
            "https://music.example.test/rest/getTopSongs.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&artist=New+Order&id=artist-1&count=12",
            httpClient.urls.last(),
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
    fun nativeAlbumPageIsGloballySortedAcrossSelectedLibraries() = runTest {
        val httpClient = RecordingNativeHttpClient(
            response = """
                {
                  "data": [
                    {"id":"album-1","name":"(First Album)","albumArtist":"One"},
                    {"id":"album-2","name":"#Second Album","albumArtist":"Two"}
                  ],
                  "total": 401
                }
            """.trimIndent(),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "native-token").copy(
                selectedMusicFolderIds = listOf("rock", "archive"),
            ),
            httpClient = httpClient,
        )

        val page = provider.albumsPage(MediaPageRequest(offset = 50, limit = 25))

        assertEquals(
            "https://music.example.test/api/album?_start=50&_end=75&_order=ASC&_sort=name&library_id=rock&library_id=archive",
            httpClient.getUrls.single(),
        )
        assertEquals(listOf("(First Album)", "#Second Album"), page.items.map { it.title })
        assertEquals(listOf("One", "Two"), page.items.map { it.artistName })
        assertEquals(401, page.totalItemCount)
        assertTrue(page.alphabeticallySortedByTitle)
        assertTrue(page.hasMore)
    }

    @Test
    fun nativeSongPageMatchesNavidromesGlobalTitleOrdering() = runTest {
        val httpClient = RecordingNativeHttpClient(
            response = """
                [
                  {"id":"track-1","title":"\"C\" Section","album":"MiClub: The Curriculum","artist":"Canibus"},
                  {"id":"track-2","title":"#34","album":"Under the Table and Dreaming","artist":"Dave Matthews Band"}
                ]
            """.trimIndent(),
            responseHeaders = mapOf("X-Total-Count" to "23380"),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "native-token").copy(
                selectedMusicFolderIds = listOf("1", "2"),
            ),
            httpClient = httpClient,
        )

        val page = provider.tracksPage(MediaPageRequest(limit = 15))

        assertEquals(
            "https://music.example.test/api/song?_start=0&_end=15&_order=ASC&_sort=title&library_id=1&library_id=2",
            httpClient.getUrls.single(),
        )
        assertEquals(listOf("\"C\" Section", "#34"), page.items.map { it.title })
        assertEquals(23_380, page.totalItemCount)
        assertTrue(page.alphabeticallySortedByTitle)
        assertTrue(page.hasMore)
    }

    @Test
    fun nativeCatalogMappingPreservesArtworkAndNativeMetadata() = runTest {
        val httpClient = RecordingNativeHttpClient(
            response = """
                [
                  {"id":"one","name":"Album","title":"Song","albumId":"album",
                   "duration":200.75,"sampleRate":44100,"libraryId":2,"rating":4,
                   "starred":true,"starredAt":"2026-09-04T12:00:00Z",
                   "playDate":"2026-09-03T12:00:00Z","createdAt":"2026-09-01T12:00:00Z"},
                  {"id":"two","name":"Other","title":"Other","duration":114,
                   "coverArt":"explicit-cover","starred":false,"starredAt":"2020-01-01T00:00:00Z"}
                ]
            """.trimIndent(),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "native-token"),
            httpClient = httpClient,
        )
        val tracks = provider.tracksPage(MediaPageRequest(limit = 15)).items
        assertEquals(listOf("mf-one", "explicit-cover"), tracks.map { it.coverArtId })
        assertEquals(listOf(200, 114), tracks.map { it.durationSeconds })
        assertEquals(44100, tracks.first().audioInfo?.samplingRateHz)
        assertEquals("2", tracks.first().musicFolderId)
        assertEquals(4, tracks.first().userRating)
        assertEquals("2026-09-04T12:00:00Z", tracks.first().favoritedAtIso8601)
        assertEquals("2026-09-03T12:00:00Z", tracks.first().lastPlayedAtIso8601)
        assertNull(tracks.last().favoritedAtIso8601)
        val albums = provider.albumsPage(MediaPageRequest(limit = 15)).items
        assertEquals(listOf("al-one", "explicit-cover"), albums.map { it.coverArtId })
        assertEquals("2026-09-01T12:00:00Z", albums.first().recentlyAddedAtIso8601)
        assertEquals("2026-09-04T12:00:00Z", albums.first().favoritedAtIso8601)
        assertNull(albums.last().favoritedAtIso8601)
    }

    @Test
    fun alphabeticalOffsetBinarySearchUsesTheNativeGlobalCatalog() = runTest {
        val httpClient = OffsetNativeHttpClient(total = 8, titles = listOf("#One", "A", "C", "F", "M", "M Two", "Y", "Z"))
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "native-token"),
            httpClient = httpClient,
        )

        val offset = provider.alphabeticalLibraryOffset(AlphabeticalLibraryKind.Tracks, 'M')

        assertEquals(4, offset)
        assertTrue(httpClient.getUrls.all { "_sort=title" in it })
    }

    @Test
    fun albumOffsetKeepsSymbolsBeforeLowercaseAlphabeticalSortKeys() = runTest {
        val httpClient = OffsetNativeHttpClient(total = 5,
            titles = listOf("25", "[non-album tracks]", "a", "g", "z"), album = true)
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "native-token"),
            httpClient = httpClient,
        )
        assertEquals(2, provider.alphabeticalLibraryOffset(AlphabeticalLibraryKind.Albums, 'A'))
        assertEquals(3, provider.alphabeticalLibraryOffset(AlphabeticalLibraryKind.Albums, 'G'))
        assertTrue(httpClient.getUrls.all { "_sort=name" in it })
    }

    @Test
    fun artistPageUsesTheIndexedCatalogWithStableAlphabeticalPaging() = runTest {
        val httpClient = RecordingResponseHttpClient(
            indexedArtistsResponse(
                "artist-z" to "Zebra",
                "artist-a" to "A Certain Ratio",
            ),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        val page = provider.artistsPage(MediaPageRequest(offset = 1, limit = 1))

        assertEquals(
            "https://music.example.test/rest/getArtists.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json",
            httpClient.urls.single(),
        )
        assertEquals(listOf("Zebra"), page.items.map { it.name })
        assertFalse(page.hasMore)
    }

    @Test
    fun multiLibraryArtistPagesAdvanceWithoutRefetchingEarlierPages() = runTest {
        val httpClient = SequencedHttpClient(
            listOf(
                indexedArtistsResponse("artist-1" to "A", "artist-3" to "C"),
                indexedArtistsResponse("artist-2" to "B"),
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
        assertEquals(2, first.nextRequest?.offset)
        assertEquals(listOf("C"), second.items.map { it.name })
        assertFalse(second.hasMore)
        assertTrue(httpClient.urls[0].contains("getArtists.view"))
        assertTrue(httpClient.urls[0].contains("musicFolderId=rock"))
        assertTrue(httpClient.urls[1].contains("getArtists.view"))
        assertTrue(httpClient.urls[1].contains("musicFolderId=archive"))
        assertEquals(2, httpClient.urls.size)
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
        assertEquals(setOf("Technique", "Movement"), albums.map { it.title }.toSet())
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
                          "coverArt": "playlist-cover",
                          "comment": "Generated playlist metadata",
                          "rules": null,
                          "validUntil": null
                        }
                      ]
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        val playlists = provider.playlists()

        assertFalse(playlists.single().isSmart)
        assertEquals("playlist-1", playlists.single().id)
        assertEquals("April 2026 Playlist", playlists.single().name)
        assertEquals(34, playlists.single().trackCount)
        assertEquals(25440, playlists.single().durationSeconds)
        assertEquals("playlist-cover", playlists.single().coverArtId)
        assertEquals("Generated playlist metadata", playlists.single().comment)
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
    fun playlistsAreFetchedOnceAcrossSelectedMusicFolders() = runTest {
        val http = SequencedHttpClient(listOf(playlistsResponse("playlist-1", "Classical")))
        val provider = NavidromeProvider(connection("https://music.example.test").copy(selectedMusicFolderIds = listOf("2", "4")), http)
        assertEquals(listOf("Classical"), provider.playlists(20).map { it.name })
        assertEquals(1, http.urls.size)
        assertFalse(http.urls.single().contains("musicFolderId"))
    }
    @Test
    fun playlistTracksPreservesAuthoritativeEntriesAcrossFolderSelection() = runTest {
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
            "https://music.example.test/rest/getPlaylist.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&id=playlist-1",
            httpClient.urls.single(),
        )
        assertEquals(listOf("In Scope", "Out Of Scope", "Unknown Scope"), tracks.map { it.title })
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
    fun bandcampCreatesPlaylistThenAddsEveryTrackSeparately() = runTest {
        val httpClient = RecordingResponseHttpClient(
            """
            {
              "subsonic-response": {
                "status": "ok",
                "playlist": { "id": "playlist-1", "name": "Road Mix", "songCount": 0 }
              }
            }
            """.trimIndent(),
        )
        val provider = NavidromeProvider(
            connection = connection("https://bandcamp.com/api/subsonic").copy(providerId = ProviderIdBandcamp),
            httpClient = httpClient,
        )

        val playlist = provider.createPlaylist(
            "Road Mix",
            listOf(TrackId("track-1"), TrackId("track-2"), TrackId("track-3")),
        )

        assertEquals(3, playlist.trackCount)
        assertEquals(4, httpClient.urls.size)
        assertTrue(httpClient.urls[0].endsWith("&name=Road+Mix"))
        assertTrue(httpClient.urls[1].endsWith("&playlistId=playlist-1&songIdToAdd=track-1"))
        assertTrue(httpClient.urls[2].endsWith("&playlistId=playlist-1&songIdToAdd=track-2"))
        assertTrue(httpClient.urls[3].endsWith("&playlistId=playlist-1&songIdToAdd=track-3"))
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
    fun refreshNativeSessionUsesBoundedReadAndRetainsRotatedToken() = runTest {
        val httpClient = RecordingNativeHttpClient(
            response = """{"data":[]}""",
            responseHeaders = mapOf("X-ND-Authorization" to "rotated-native-token"),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "native-token"),
            httpClient = httpClient,
        )

        assertTrue(provider.refreshNativeSession())

        assertEquals(
            "https://music.example.test/api/playlist?range=%5B0%2C0%5D",
            httpClient.getUrls.single(),
        )
        assertEquals(mapOf("x-nd-authorization" to "Bearer native-token"), httpClient.getHeaders.single())
        assertEquals("rotated-native-token", provider.connectionWithCurrentNativeToken().nativeToken)
    }

    @Test
    fun commonNativeSessionControllerPersistsARejectedTokenAsCleared() = runTest {
        val active = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "expired-token"),
            httpClient = FailingNativeHttpClient(),
        )
        var persisted: ProviderMediaSourceConnection? = null
        val repository = object : ProviderMediaSourceRepository {
            override fun upsertProviderMediaSource(
                connection: ProviderMediaSourceConnection,
                cacheNamespace: String,
                providerId: String,
                preferredSourceId: String?,
            ): MediaSourceIdentity {
                persisted = connection
                return MediaSourceIdentity("source", cacheNamespace, connection.displayName)
            }
        }
        val controller = NavidromeNativeSessionController(
            currentProvider = { active },
            savedConnection = { null },
            replaceProvider = {},
            repository = repository,
        )

        assertFailsWith<NavidromeException> { controller.refresh() }

        assertNull(persisted?.nativeToken)
    }

    @Test
    fun commonNativeSessionControllerRefreshesReauthenticatesAndPersistsRotatedTokens() = runTest {
        val httpClient = RecordingNativeHttpClient(
            response = """{"data":[]}""",
            responseHeaders = mapOf("X-ND-Authorization" to "rotated-native-token"),
        )
        var active = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "native-token"),
            httpClient = httpClient,
        )
        var persisted: ProviderMediaSourceConnection? = null
        val repository = object : ProviderMediaSourceRepository {
            override fun upsertProviderMediaSource(
                connection: ProviderMediaSourceConnection,
                cacheNamespace: String,
                providerId: String,
                preferredSourceId: String?,
            ): MediaSourceIdentity {
                persisted = connection
                return MediaSourceIdentity("source", cacheNamespace, connection.displayName)
            }
        }
        val controller = NavidromeNativeSessionController(
            currentProvider = { active },
            savedConnection = { active.connectionWithCurrentNativeToken() },
            replaceProvider = { active = it },
            repository = repository,
            authenticate = { saved, password ->
                assertEquals("secret", password)
                saved.copy(nativeToken = "password-token")
            },
        )

        assertTrue(controller.refresh())
        assertEquals("rotated-native-token", persisted?.nativeToken)
        controller.provider("secret")
        assertEquals("password-token", active.connectionWithCurrentNativeToken().nativeToken)
        assertEquals("password-token", persisted?.nativeToken)
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
    fun navidrome064SmartPlaylistFieldsAndRefreshDelaySurviveNativeRoundTrip() = runTest {
        val httpClient = RecordingNativeHttpClient(
            """
            {
              "data": {
                "id": "smart-1",
                "name": "Daily Albums",
                "rules": {
                  "all": [
                    { "inTheLast": { "albumdateadded": 30 } },
                    { "gt": { "albumsongcount": 4 } }
                  ],
                  "sort": "-albumdateadded,album,discnumber,tracknumber",
                  "refreshDelay": "1d12h"
                }
              }
            }
            """.trimIndent(),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test", nativeToken = "native-token"),
            httpClient = httpClient,
        )

        val definition = provider.smartPlaylistDefinition("smart-1")
        provider.updateSmartPlaylist("smart-1", definition)

        assertEquals("1d12h", definition.refreshDelay)
        assertEquals(
            """{"name":"Daily Albums","rules":{"all":[{"inTheLast":{"albumdateadded":30}},{"gt":{"albumsongcount":4}}],"sort":"-albumdateadded,album,discnumber,tracknumber","refreshDelay":"1d12h"}}""",
            httpClient.putBodies.single(),
        )
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

        assertEquals(
            "Your Navidrome smart playlist session expired. Enter your password to reconnect smart playlists.",
            error.message,
        )
        assertEquals(null, provider.connectionWithCurrentNativeToken().nativeToken)
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

        assertEquals(
            "Your Navidrome smart playlist session expired. Enter your password to reconnect smart playlists.",
            error.message,
        )
        assertEquals(null, provider.connectionWithCurrentNativeToken().nativeToken)
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
    fun membershipRemovalUsesOnlyMatchingIndicesAndNeverReaddsUnrelatedTracks() = runTest {
        val http = RecordingResponseHttpClient("""{"subsonic-response":{"status":"ok","playlist":{"entry":[
            {"id":"keep","title":"Keep"},{"id":"remove","title":"Remove"},{"id":"remove","title":"Remove"}
        ]}}}""")
        val provider = NavidromeProvider(connection("https://music.example.test").copy(selectedMusicFolderIds = listOf("selected")), http)
        provider.removeTrackFromPlaylist("playlist", TrackId("remove"))
        assertFalse("musicFolderId" in http.urls.first())
        val mutation = http.urls.last()
        assertTrue("songIndexToRemove=1" in mutation && "songIndexToRemove=2" in mutation)
        assertFalse("songIndexToRemove=0" in mutation)
        assertFalse("songIdToAdd" in mutation)
        assertEquals(2, http.urls.size)
    }

    @Test
    fun replacePlaylistTracksUsesDedicatedReplacement() = runTest {
        val httpClient = RecordingHttpClient()
        val provider = NavidromeProvider(connection("https://music.example.test"), httpClient)

        provider.replacePlaylistTracks(
            playlistId = "playlist-1",
            currentTrackIds = listOf(TrackId("track-9"), TrackId("track-8"), TrackId("track-7")),
            trackIds = listOf(TrackId("track-3"), TrackId("track-1")),
        )

        assertEquals(
            "https://music.example.test/rest/createPlaylist.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&playlistId=playlist-1&songId=track-3&songId=track-1",
            httpClient.urls.single(),
        )
    }

    @Test
    fun bandcampRejectsUnsafePlaylistReorderingBeforeSendingMutations() = runTest {
        val httpClient = RecordingHttpClient()
        val provider = NavidromeProvider(
            connection("https://bandcamp.com/api/subsonic").copy(providerId = ProviderIdBandcamp),
            httpClient,
        )

        val failure = assertFailsWith<UnsupportedOperationException> {
            provider.replacePlaylistTracks(
                playlistId = "playlist-1",
                currentTrackIds = listOf(TrackId("track-1"), TrackId("track-2"), TrackId("track-3")),
                trackIds = listOf(TrackId("track-1"), TrackId("track-3"), TrackId("track-2")),
            )
        }

        assertTrue(failure.message.orEmpty().contains("does not reliably support rearranging"))
        assertTrue(httpClient.urls.isEmpty())
    }

    @Test
    fun bandcampRemovesOnlyUnwantedPlaylistTracksFromTheEndBackward() = runTest {
        val httpClient = RecordingHttpClient()
        val provider = NavidromeProvider(
            connection("https://bandcamp.com/api/subsonic").copy(providerId = ProviderIdBandcamp),
            httpClient,
        )

        provider.replacePlaylistTracks(
            playlistId = "playlist-1",
            currentTrackIds = listOf(TrackId("track-1"), TrackId("track-2"), TrackId("track-3"), TrackId("track-4")),
            trackIds = listOf(TrackId("track-1"), TrackId("track-3")),
        )

        assertEquals(2, httpClient.urls.size)
        assertTrue(httpClient.urls[0].endsWith("&playlistId=playlist-1&songIndexToRemove=3"))
        assertTrue(httpClient.urls[1].endsWith("&playlistId=playlist-1&songIndexToRemove=1"))
    }

    @Test
    fun bandcampAppendingOneTrackUsesOnePlaylistMutation() = runTest {
        val httpClient = RecordingHttpClient()
        val provider = NavidromeProvider(
            connection("https://bandcamp.com/api/subsonic").copy(providerId = ProviderIdBandcamp),
            httpClient,
        )

        provider.replacePlaylistTracks(
            playlistId = "playlist-1",
            currentTrackIds = listOf(TrackId("track-1"), TrackId("track-2")),
            trackIds = listOf(TrackId("track-1"), TrackId("track-2"), TrackId("track-3")),
        )

        assertEquals(1, httpClient.urls.size)
        assertTrue(httpClient.urls.single().endsWith("&playlistId=playlist-1&songIdToAdd=track-3"))
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
                "https://music.example.test/rest/getUser.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&username=demo",
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
    fun reportNowPlayingUsesScrobbleWhenPlaybackReportExtensionIsMissing() = runTest {
        val httpClient = RecordingHttpClient()
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.reportNowPlaying(TrackId("track-1"))

        assertTrue(httpClient.urls.single().contains("/scrobble.view?"))
        assertTrue(httpClient.urls.single().endsWith("&submission=false"))
    }

    @Test
    fun reportNowPlayingUsesPlaybackReportWhenExtensionIsAdvertised() = runTest {
        val httpClient = SequencedHttpClient(
            listOf(
                okResponse(),
                openSubsonicExtensionsResponse("playbackReport"),
                okResponse(),
            ),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.validateConnection()
        provider.reportNowPlaying(TrackId("track-1"))

        assertEquals(
            "https://music.example.test/rest/reportPlayback.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&mediaId=track-1&mediaType=song&positionMs=0&state=starting&ignoreScrobble=true",
            httpClient.urls.last(),
        )
    }

    @Test
    fun legacyFallbackUsesScrobbleEvenWhenPlaybackReportRemainsAdvertised() = runTest {
        val httpClient = SequencedHttpClient(
            listOf(
                okResponse(),
                openSubsonicExtensionsResponse("playbackReport"),
                okResponse(),
            ),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.validateConnection()
        provider.reportLegacyNowPlaying(TrackId("track-1"))

        assertTrue(httpClient.urls.last().contains("/scrobble.view?"))
        assertTrue(httpClient.urls.last().endsWith("&submission=false"))
        assertFalse(httpClient.urls.last().contains("reportPlayback"))
    }

    @Test
    fun reportPlaybackStateUsesPlaybackReportWhenExtensionIsAdvertised() = runTest {
        val httpClient = SequencedHttpClient(
            listOf(
                okResponse(),
                openSubsonicExtensionsResponse("playbackReport"),
                okResponse(),
            ),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.validateConnection()
        provider.reportPlaybackState(
            trackId = TrackId("track-1"),
            state = PlaybackReportState.Playing,
            positionSeconds = 45.25,
        )

        assertEquals(
            "https://music.example.test/rest/reportPlayback.view?u=demo&t=token&s=salt&v=1.16.1&$ExpectedClientQuery&f=json&mediaId=track-1&mediaType=song&positionMs=45250&state=playing&ignoreScrobble=true",
            httpClient.urls.last(),
        )
    }

    @Test
    fun timelinePresenceAndExplicitListenUseSeparateNonScrobblingAndScrobblingRequests() = runTest {
        val httpClient = SequencedHttpClient(
            listOf(
                okResponse(),
                openSubsonicExtensionsResponse("playbackReport"),
                okResponse(),
                okResponse(),
            ),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.validateConnection()
        provider.reportPlaybackState(TrackId("track-1"), PlaybackReportState.Playing, 31.0)
        provider.submitListen(TrackId("track-1"), 1_234L)

        val requests = httpClient.urls.takeLast(2)
        assertTrue(requests[0].contains("/reportPlayback.view?"))
        assertTrue(requests[0].endsWith("&state=playing&ignoreScrobble=true"))
        assertTrue(requests[1].contains("/scrobble.view?"))
        assertTrue(requests[1].endsWith("&submission=true&time=1234"))
    }

    @Test
    fun reportPlaybackStateDoesNothingWhenExtensionIsMissing() = runTest {
        val httpClient = SequencedHttpClient(
            listOf(
                okResponse(),
                openSubsonicExtensionsResponse(),
            ),
        )
        val provider = NavidromeProvider(
            connection = connection("https://music.example.test"),
            httpClient = httpClient,
        )

        provider.validateConnection()
        provider.reportPlaybackState(
            trackId = TrackId("track-1"),
            state = PlaybackReportState.Paused,
            positionSeconds = 45.25,
        )

        assertEquals(3, httpClient.urls.size)
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

    private fun indexedArtistsResponse(vararg artists: Pair<String, String>): String =
        """
        {
          "subsonic-response": {
            "status": "ok",
            "artists": {
              "index": [
                {
                  "name": "A-Z",
                  "artist": [
                    ${artists.joinToString(",") { (id, name) -> """{"id":"$id","name":"$name"}""" }}
                  ]
                }
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
            if ("/getUser.view" in url) return okResponse()
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

    private class OffsetNativeHttpClient(
        private val total: Int,
        private val titles: List<String>,
        private val album: Boolean = false,
    ) : NavidromeHttpClient {
        val getUrls = mutableListOf<String>()

        override suspend fun get(url: String): String = error("Headers are required")

        override suspend fun getResponse(url: String, headers: Map<String, String>): NavidromeHttpResponse {
            getUrls += url
            val offset = url.substringAfter("_start=").substringBefore('&').toInt()
            return NavidromeHttpResponse(
                body = if (album) """[{"id":"album-$offset","name":"${titles[offset]}","orderAlbumName":"${titles[offset]}"}]"""
                    else """[{"id":"track-$offset","title":"${titles[offset]}"}]""",
                headers = mapOf("X-Total-Count" to total.toString()),
            )
        }
    }

    private class FailingNativeHttpClient : NavidromeHttpClient {
        override suspend fun get(url: String): String = throw NavidromeHttpException(401)

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
        ): String = throw NavidromeHttpException(401)

        override suspend fun putJson(
            url: String,
            body: String,
            headers: Map<String, String>,
        ): String = throw NavidromeHttpException(401)
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

private fun okResponse(): String = """
    {
      "subsonic-response": {
        "status": "ok",
        "version": "1.16.1",
        "serverVersion": "0.62.0"
      }
    }
""".trimIndent()

private fun openSubsonicExtensionsResponse(vararg names: String): String {
    val extensions = names.joinToString(",\n") { name ->
        """          { "name": "$name", "versions": [1] }"""
    }
    return """
        {
          "subsonic-response": {
            "status": "ok",
            "openSubsonicExtensions": [
$extensions
            ]
          }
        }
    """.trimIndent()
}
