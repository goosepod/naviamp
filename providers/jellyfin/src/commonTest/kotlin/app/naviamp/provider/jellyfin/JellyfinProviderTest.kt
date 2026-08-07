package app.naviamp.provider.jellyfin

import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.AudioCodec
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.TrackId
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.network.SharedHttpResponse
import app.naviamp.domain.provider.MediaPageRequest
import app.naviamp.domain.provider.PlaybackReportState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JellyfinProviderTest {
    @Test
    fun mapsMusicLibrariesAndPagedAlbumsFromJellyfinItems() = runTest {
        val fixture = fixture(
            responses = mapOf(
                "/UserViews" to """
                    {"Items":[
                      {"Id":"music","Name":"Music","CollectionType":"music"},
                      {"Id":"movies","Name":"Movies","CollectionType":"movies"}
                    ],"TotalRecordCount":2}
                """.trimIndent(),
                "includeItemTypes=MusicAlbum" to """
                    {"Items":[{
                      "Id":"album-1","Name":"Kind of Blue","AlbumArtist":"Miles Davis",
                      "ProductionYear":1959,"DateCreated":"2026-01-02T03:04:05Z",
                      "ImageTags":{"Primary":"tag"},
                      "AlbumArtists":[{"Id":"artist-1","Name":"Miles Davis"}],
                      "UserData":{"IsFavorite":true}
                    }],"TotalRecordCount":3}
                """.trimIndent(),
            ),
        )

        assertEquals(listOf(JellyfinMusicLibrary("music", "Music")), fixture.provider.musicLibraries())
        val page = fixture.provider.albumsPage(MediaPageRequest(offset = 0, limit = 1))

        assertEquals(1, page.items.size)
        assertTrue(page.hasMore)
        assertEquals("Kind of Blue", page.items.single().title)
        assertEquals(1959, page.items.single().releaseYear)
        assertEquals("album-1", page.items.single().coverArtId)
        assertEquals("artist-1", page.items.single().artistCredits.single().id?.value)
        assertEquals("favorite", page.items.single().favoritedAtIso8601)
        assertTrue(fixture.http.requestedUrls.last().contains("userId=user-id"))
        assertTrue(fixture.http.requestedUrls.last().contains("includeItemTypes=MusicAlbum"))
    }

    @Test
    fun mapsTracksAndAlbumDetailsIncludingAudioMetadata() = runTest {
        val trackResponse = """
            {"Items":[{
              "Id":"track-1","Name":"So What","AlbumId":"album-1","Album":"Kind of Blue",
              "ProductionYear":1959,"RunTimeTicks":5450000000,"AlbumPrimaryImageTag":"tag",
              "Artists":["Miles Davis"],"ArtistItems":[{"Id":"artist-1","Name":"Miles Davis"}],
              "Genres":["Jazz"],"ParentId":"disc-1",
              "MediaSources":[{"Container":"flac","Bitrate":1411000}],
              "MediaStreams":[{"Type":"Audio","Codec":"flac","BitRate":1411000,"BitDepth":24,"SampleRate":96000}],
              "UserData":{"PlayCount":7,"LastPlayedDate":"2026-01-01T00:00:00Z"}
            }],"TotalRecordCount":1}
        """.trimIndent()
        val fixture = fixture(
            responses = mapOf(
                "/Items/album-1?" to """
                    {"Id":"album-1","Name":"Kind of Blue","AlbumArtist":"Miles Davis","ImageTags":{"Primary":"tag"}}
                """.trimIndent(),
                "parentId=album-1" to trackResponse,
                "includeItemTypes=Audio" to trackResponse,
            ),
        )

        val details = fixture.provider.album(AlbumId("album-1"))
        val track = details.tracks.single()

        assertEquals("Kind of Blue", details.album.title)
        assertEquals("So What", track.title)
        assertEquals(545, track.durationSeconds)
        assertEquals("artist-1", track.artistId?.value)
        assertEquals("flac", track.audioInfo?.codec)
        assertEquals(1_411, track.audioInfo?.bitrateKbps)
        assertEquals(24, track.audioInfo?.bitDepth)
        assertEquals(96_000, track.audioInfo?.samplingRateHz)
        assertEquals(listOf("Jazz"), track.genres)
        assertEquals(7, track.playCount)
    }

    @Test
    fun directStreamUsesTokenButArtworkUsesAuthenticatedByteLoading() = runTest {
        val fixture = fixture(binaryResponse = JellyfinBinaryResponse(200, byteArrayOf(1, 2, 3)))

        val streamUrl = fixture.provider.streamUrl(
            StreamRequest(TrackId("track-1"), StreamQuality.Original),
        )
        val artworkUrl = fixture.provider.coverArtUrl("album-1")

        assertTrue(streamUrl.contains("Audio/track-1/stream"))
        assertTrue(streamUrl.contains("api_key=access-token"))
        assertFalse(artworkUrl.contains("access-token"))
        assertEquals(listOf<Byte>(1, 2, 3), fixture.provider.bytesForOwnedUrl(artworkUrl)?.toList())
        assertTrue(fixture.http.lastBinaryHeaders.getValue("Authorization").contains("Token=\"access-token\""))
        assertNull(fixture.provider.bytesForOwnedUrl("https://untrusted.example/image"))
    }

    @Test
    fun downloadsUseTheAuthenticatedJellyfinSessionInsteadOfTheGenericClient() = runTest {
        val fixture = fixture()
        val chunks = mutableListOf<Byte>()
        val url = fixture.provider.streamUrl(StreamRequest(TrackId("track-1"), StreamQuality.Original))

        val downloaded = fixture.provider.downloadStream(url, UnusedSharedHttpClient) { bytes, count ->
            chunks += bytes.take(count)
        }

        assertTrue(downloaded)
        assertEquals(listOf<Byte>(4, 5, 6), chunks)
        assertEquals(url, fixture.http.downloadedUrl)
        assertTrue(fixture.http.downloadHeaders.getValue("Authorization").contains("Token=\"access-token\""))
        assertFalse(
            fixture.provider.downloadStream("https://untrusted.example/audio", UnusedSharedHttpClient) { _, _ -> },
        )
    }

    @Test
    fun exposesAndBuildsJellyfinTranscodedStreams() = runTest {
        val fixture = fixture()

        assertTrue(fixture.provider.capabilities.supportsStreamingTranscode)
        assertTrue(fixture.provider.capabilities.supportsDownloadTranscode)

        listOf(
            AudioCodec.Mp3 to "mp3",
            AudioCodec.Aac to "aac",
            AudioCodec.Opus to "opus",
        ).forEach { (codec, jellyfinCodec) ->
            val url = fixture.provider.streamUrl(
                StreamRequest(
                    trackId = TrackId("track-1"),
                    quality = StreamQuality.Transcoded(codec, bitrateKbps = 128),
                    startPositionSeconds = 95.8,
                ),
            )

            assertTrue(url.contains("Audio/track-1/universal"))
            assertTrue(url.contains("container=$jellyfinCodec"))
            assertTrue(url.contains("transcodingContainer=$jellyfinCodec"))
            assertTrue(url.contains("transcodingProtocol=http"))
            assertTrue(url.contains("audioCodec=$jellyfinCodec"))
            assertTrue(url.contains("maxStreamingBitrate=128000"))
            assertTrue(url.contains("audioBitRate=128000"))
            assertTrue(url.contains("enableRedirection=false"))
            assertTrue(url.contains("userId=user-id"))
            assertTrue(url.contains("startTimeTicks=958000000"))
        }
    }

    @Test
    fun exposesAndUsesJellyfinFavoriteMutations() = runTest {
        val fixture = fixture()

        assertTrue(fixture.provider.capabilities.supportsTrackFavorites)
        assertTrue(fixture.provider.capabilities.supportsArtistFavorites)
        assertTrue(fixture.provider.capabilities.supportsAlbumFavorites)

        fixture.provider.setTrackFavorite(TrackId("track-1"), true)
        fixture.provider.setTrackFavorite(TrackId("track-1"), false)

        assertEquals("POST", fixture.http.mutations[0].first)
        assertTrue(fixture.http.mutations[0].second.contains("UserFavoriteItems/track-1?userId=user-id"))
        assertEquals("DELETE", fixture.http.mutations[1].first)
    }

    @Test
    fun mapsJellyfinInstantMixesAndArtistPopularTracks() = runTest {
        val tracks = """
            {"Items":[{
              "Id":"track-1","Name":"So What","AlbumId":"album-1","Album":"Kind of Blue",
              "Artists":["Miles Davis"],"ArtistItems":[{"Id":"artist-1","Name":"Miles Davis"}],
              "MediaStreams":[{"Type":"Audio","Codec":"flac","BitRate":1411000}]
            }],"TotalRecordCount":1}
        """.trimIndent()
        val fixture = fixture(
            responses = mapOf(
                "/Artists/artist-1/InstantMix" to tracks,
                "/MusicGenres/Alternative%20Rock/InstantMix" to tracks,
                "sortBy=PlayCount" to tracks,
            ),
        )

        assertTrue(fixture.provider.capabilities.supportsArtistRadio)
        assertEquals("track-1", fixture.provider.artistRadio(ArtistId("artist-1"), 25).single().id.value)
        assertTrue(fixture.http.requestedUrls.last().contains("limit=25"))

        val popular = fixture.provider.popularTracks(Artist(ArtistId("artist-1"), "Miles Davis"), 10)
        assertEquals("track-1", popular.candidates.single().sourceTrackId)
        assertEquals("track-1", popular.matchedTracksBySourceTrackId.getValue("track-1").id.value)
        assertTrue(fixture.http.requestedUrls.last().contains("artistIds=artist-1"))

        assertEquals("track-1", fixture.provider.genreRadio("Alternative Rock", 30).single().id.value)
        assertTrue(fixture.http.requestedUrls.last().contains("MusicGenres/Alternative%20Rock/InstantMix"))
    }

    @Test
    fun reportsPlaybackStartProgressPauseAndStop() = runTest {
        val fixture = fixture()

        assertTrue(fixture.provider.capabilities.supportsPlayReporting)
        fixture.provider.reportNowPlaying(TrackId("track-1"))
        fixture.provider.reportPlaybackState(TrackId("track-1"), PlaybackReportState.Playing, 12.5)
        fixture.provider.reportPlaybackState(TrackId("track-1"), PlaybackReportState.Paused, 14.0)
        fixture.provider.reportPlaybackState(TrackId("track-1"), PlaybackReportState.Stopped, 15.25)

        assertEquals(
            listOf(
                "/Sessions/Playing",
                "/Sessions/Playing/Progress",
                "/Sessions/Playing/Progress",
                "/Sessions/Playing/Stopped",
            ),
            fixture.http.jsonPosts.map { it.first.substringAfter("https://music.example.test") },
        )
        assertTrue(fixture.http.jsonPosts[1].second.contains("\"PositionTicks\":125000000"))
        assertTrue(fixture.http.jsonPosts[2].second.contains("\"IsPaused\":true"))
        assertTrue(fixture.http.jsonPosts[3].second.contains("\"PositionTicks\":152500000"))
    }

    @Test
    fun listsAndMutatesJellyfinPlaylists() = runTest {
        val playlistTracks = """
            {"Items":[{
              "Id":"track-1","PlaylistItemId":"entry-1","Name":"So What","Album":"Kind of Blue",
              "Artists":["Miles Davis"],"ArtistItems":[{"Id":"artist-1","Name":"Miles Davis"}]
            }],"TotalRecordCount":1}
        """.trimIndent()
        val fixture = fixture(
            responses = mapOf(
                "includeItemTypes=Playlist" to """
                    {"Items":[{"Id":"playlist-1","Name":"Late Night","ChildCount":1}],"TotalRecordCount":1}
                """.trimIndent(),
                "/Playlists/playlist-1/Items" to playlistTracks,
            ),
        )

        assertEquals("Late Night", fixture.provider.playlists(20).single().name)
        assertEquals("track-1", fixture.provider.playlistTracks("playlist-1").single().id.value)

        val created = fixture.provider.createPlaylist("New Mix", listOf(TrackId("track-1")))
        assertEquals("playlist-new", created.id)
        assertTrue(fixture.http.jsonPosts.last().second.contains("\"MediaType\":\"Audio\""))

        fixture.provider.addTracksToPlaylist("playlist-1", listOf(TrackId("track-2")))
        fixture.provider.replacePlaylistTracks(
            "playlist-1",
            listOf(TrackId("track-1")),
            listOf(TrackId("track-3")),
        )
        fixture.provider.renamePlaylist("playlist-1", "Renamed")
        fixture.provider.deletePlaylist("playlist-1")

        assertTrue(fixture.http.mutations.any { (method, url) -> method == "POST" && url.contains("ids=track-2") })
        assertTrue(fixture.http.mutations.any { (method, url) -> method == "DELETE" && url.contains("entryIds=entry-1") })
        assertTrue(fixture.http.jsonPosts.any { (url, body) -> url.endsWith("Playlists/playlist-1") && body.contains("Renamed") })
        assertTrue(fixture.http.mutations.any { (method, url) -> method == "DELETE" && url.endsWith("Items/playlist-1") })
    }

    @Test
    fun mapsPlainAndWordSyncedJellyfinLyrics() = runTest {
        val syncedFixture = fixture(
            responses = mapOf(
                "/Audio/track-synced/Lyrics" to """
                    {
                      "Metadata":{"Artist":"Spineshank","Title":"New Disease","Offset":5000000,"IsSynced":true},
                      "Lyrics":[{
                        "Text":"Hello world","Start":10000000,
                        "Cues":[
                          {"Position":0,"EndPosition":5,"Start":10000000,"End":15000000},
                          {"Position":6,"EndPosition":11,"Start":15000000,"End":20000000}
                        ]
                      }]
                    }
                """.trimIndent(),
            ),
        )

        val synced = assertNotNull(syncedFixture.provider.lyrics(TrackId("track-synced")))
        assertTrue(synced.synced)
        assertEquals(1_000L, synced.lines.single().startMillis)
        assertEquals(500, synced.offsetMillis)
        assertEquals(listOf("Hello", "world"), synced.cueLines.single().cues.map { it.text })
        assertEquals(6, synced.cueLines.single().cues.last().byteStart)

        val plainFixture = fixture(
            responses = mapOf(
                "/Audio/track-plain/Lyrics" to """
                    {"Metadata":{"IsSynced":false},"Lyrics":[{"Text":"First line"},{"Text":"Second line"}]}
                """.trimIndent(),
            ),
        )
        val plain = assertNotNull(plainFixture.provider.lyrics(TrackId("track-plain")))
        assertFalse(plain.synced)
        assertEquals(listOf("First line", "Second line"), plain.lines.map { it.text })
        assertTrue(plain.cueLines.isEmpty())
    }

    private fun fixture(
        responses: Map<String, String> = emptyMap(),
        binaryResponse: JellyfinBinaryResponse = JellyfinBinaryResponse(404, byteArrayOf()),
    ): Fixture {
        val http = FixtureJellyfinHttpClient(responses, binaryResponse)
        val service = JellyfinSessionService(
            httpClient = http,
            identity = JellyfinClientIdentity("device-id", "Test", clientVersion = "1.0"),
        )
        val factory = JellyfinSessionServiceFactory { service }
        return Fixture(
            provider = JellyfinProvider(
                connection = JellyfinConnection(
                    baseUrl = "https://music.example.test",
                    username = "alice",
                    accessToken = "access-token",
                    userId = "user-id",
                    deviceId = "device-id",
                    serverVersion = "10.11.11",
                ),
                sessionServices = factory,
            ),
            http = http,
        )
    }
}

private data class Fixture(
    val provider: JellyfinProvider,
    val http: FixtureJellyfinHttpClient,
)

private class FixtureJellyfinHttpClient(
    private val responses: Map<String, String>,
    private val binaryResponse: JellyfinBinaryResponse,
) : JellyfinHttpClient {
    val requestedUrls = mutableListOf<String>()
    val mutations = mutableListOf<Pair<String, String>>()
    val jsonPosts = mutableListOf<Pair<String, String>>()
    var lastBinaryHeaders: Map<String, String> = emptyMap()
    var downloadedUrl: String? = null
    var downloadHeaders: Map<String, String> = emptyMap()

    override suspend fun get(url: String, headers: Map<String, String>): JellyfinHttpResponse {
        requestedUrls += url
        val body = responses.entries.firstOrNull { (key, _) -> url.contains(key) }?.value
        return if (body == null) JellyfinHttpResponse(404, "") else JellyfinHttpResponse(200, body)
    }

    override suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>,
    ): JellyfinHttpResponse {
        jsonPosts += url to body
        return if (url.endsWith("/Playlists")) {
            JellyfinHttpResponse(200, """{"Id":"playlist-new"}""")
        } else {
            JellyfinHttpResponse(204, "")
        }
    }

    override suspend fun post(url: String, headers: Map<String, String>): JellyfinHttpResponse {
        mutations += "POST" to url
        return JellyfinHttpResponse(200, "{}")
    }

    override suspend fun delete(url: String, headers: Map<String, String>): JellyfinHttpResponse {
        mutations += "DELETE" to url
        return JellyfinHttpResponse(200, "{}")
    }

    override suspend fun getBytes(url: String, headers: Map<String, String>): JellyfinBinaryResponse {
        lastBinaryHeaders = headers
        return binaryResponse
    }

    override suspend fun download(
        url: String,
        headers: Map<String, String>,
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean {
        downloadedUrl = url
        downloadHeaders = headers
        val bytes = byteArrayOf(4, 5, 6)
        writeChunk(bytes, bytes.size)
        return true
    }
}

private object UnusedSharedHttpClient : SharedHttpClient {
    override suspend fun get(url: String, headers: Map<String, String>): String? = null
    override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? = null
    override suspend fun getResponse(url: String, headers: Map<String, String>): SharedHttpResponse? = null
    override suspend fun download(
        url: String,
        headers: Map<String, String>,
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean = error("The generic HTTP client must not download Jellyfin media.")
}
