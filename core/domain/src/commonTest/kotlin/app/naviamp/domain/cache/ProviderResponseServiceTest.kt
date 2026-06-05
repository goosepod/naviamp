package app.naviamp.domain.cache

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ProviderResponseServiceTest {
    @Test
    fun searchUsesCachedProviderResponseResourceKey() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeSearchProvider()
        val service = ProviderResponseService(cache)

        val results = service.search(provider, " New Order ", limit = 15)

        assertEquals(listOf("provider-one:search:new order:15"), cache.keys)
        assertEquals(1, provider.searchCalls)
        assertEquals(listOf(track("new-order")), results.tracks)
    }

    @Test
    fun searchReturnsCachedValueWithoutFetchingProviderAgain() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeSearchProvider()
        val service = ProviderResponseService(cache)

        service.search(provider, "new order", limit = 15)
        provider.results = MediaSearchResults(tracks = listOf(track("changed")))
        val results = service.search(provider, "new order", limit = 15)

        assertEquals(1, provider.searchCalls)
        assertEquals(listOf(track("new-order")), results.tracks)
    }

    @Test
    fun albumUsesCachedProviderResponseResourceKey() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeSearchProvider()
        val service = ProviderResponseService(cache)

        val details = service.album(provider, AlbumId("album-one"))

        assertEquals(listOf("provider-one:album:album-one"), cache.keys)
        assertEquals(1, provider.albumCalls)
        assertEquals(album("album-one"), details.album)
        assertEquals(listOf(track("album-track")), details.tracks)
    }

    @Test
    fun albumReturnsCachedValueWithoutFetchingProviderAgain() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeSearchProvider()
        val service = ProviderResponseService(cache)

        service.album(provider, AlbumId("album-one"))
        provider.albumDetails = AlbumDetails(album("changed"), tracks = listOf(track("changed")))
        val details = service.album(provider, AlbumId("album-one"))

        assertEquals(1, provider.albumCalls)
        assertEquals(album("album-one"), details.album)
        assertEquals(listOf(track("album-track")), details.tracks)
    }

    @Test
    fun artistUsesCachedProviderResponseResourceKey() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeSearchProvider()
        val service = ProviderResponseService(cache)

        val details = service.artist(provider, ArtistId("artist-one"))

        assertEquals(listOf("provider-one:artist:artist-one"), cache.keys)
        assertEquals(1, provider.artistCalls)
        assertEquals(artist("artist-one"), details.artist)
        assertEquals(listOf(album("artist-album")), details.albums)
    }

    @Test
    fun artistReturnsCachedValueWithoutFetchingProviderAgain() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeSearchProvider()
        val service = ProviderResponseService(cache)

        service.artist(provider, ArtistId("artist-one"))
        provider.artistDetails = ArtistDetails(artist("changed"), albums = listOf(album("changed")))
        val details = service.artist(provider, ArtistId("artist-one"))

        assertEquals(1, provider.artistCalls)
        assertEquals(artist("artist-one"), details.artist)
        assertEquals(listOf(album("artist-album")), details.albums)
    }

    @Test
    fun albumListUsesCachedProviderResponseResourceKey() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeSearchProvider()
        val service = ProviderResponseService(cache)

        val albums = service.albumList(provider, AlbumListType.Newest, limit = 8)

        assertEquals(listOf("provider-one:albumList:newest:8"), cache.keys)
        assertEquals(1, provider.albumListCalls)
        assertEquals(listOf(album("newest-8")), albums)
    }

    @Test
    fun albumListReturnsCachedValueWithoutFetchingProviderAgain() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeSearchProvider()
        val service = ProviderResponseService(cache)

        service.albumList(provider, AlbumListType.Random, limit = 6)
        provider.albumListResults = listOf(album("changed"))
        val albums = service.albumList(provider, AlbumListType.Random, limit = 6)

        assertEquals(1, provider.albumListCalls)
        assertEquals(listOf(album("random-6")), albums)
    }

    @Test
    fun genreAndYearAlbumResourceIdsNormalizeInputs() {
        assertEquals("jazz:6", albumsByGenreResourceId(" Jazz ", 6))
        assertEquals("1990:1999:6", albumsByYearResourceId(1990, 1999, 6))
    }

    @Test
    fun albumsByGenreAndYearUseCachedProviderResponses() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeSearchProvider()
        val service = ProviderResponseService(cache)

        val genreAlbums = service.albumsByGenre(provider, " Jazz ", limit = 6)
        val yearAlbums = service.albumsByYear(provider, 1990, 1999, limit = 6)

        assertEquals(
            listOf(
                "provider-one:albumsByGenre:jazz:6",
                "provider-one:albumsByYear:1990:1999:6",
            ),
            cache.keys,
        )
        assertEquals(listOf(album("genre- Jazz -6")), genreAlbums)
        assertEquals(listOf(album("years-1990-1999-6")), yearAlbums)
    }

    @Test
    fun artistsUsesCachedProviderResponseResourceKey() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeSearchProvider()
        val service = ProviderResponseService(cache)

        val artists = service.artists(provider, limit = 50)

        assertEquals(listOf("provider-one:artists:50"), cache.keys)
        assertEquals(1, provider.artistsCalls)
        assertEquals(listOf(artist("artist-50")), artists)
    }

    @Test
    fun artistsReturnsCachedValueWithoutFetchingProviderAgain() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeSearchProvider()
        val service = ProviderResponseService(cache)

        service.artists(provider, limit = 50)
        provider.artistsResults = listOf(artist("changed"))
        val artists = service.artists(provider, limit = 50)

        assertEquals(1, provider.artistsCalls)
        assertEquals(listOf(artist("artist-50")), artists)
    }

    @Test
    fun playlistsUsesCachedProviderResponseResourceKey() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeSearchProvider()
        val service = ProviderResponseService(cache)

        val playlists = service.playlists(provider, limit = 50)

        assertEquals(listOf("provider-one:playlists:50"), cache.keys)
        assertEquals(1, provider.playlistsCalls)
        assertEquals(listOf(playlist("playlist-50")), playlists)
    }

    @Test
    fun playlistsReturnsCachedValueWithoutFetchingProviderAgain() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeSearchProvider()
        val service = ProviderResponseService(cache)

        service.playlists(provider, limit = 50)
        provider.playlistsResults = listOf(playlist("changed"))
        val playlists = service.playlists(provider, limit = 50)

        assertEquals(1, provider.playlistsCalls)
        assertEquals(listOf(playlist("playlist-50")), playlists)
    }

    @Test
    fun playlistTracksUsesCachedProviderResponseResourceKey() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeSearchProvider()
        val service = ProviderResponseService(cache)

        val tracks = service.playlistTracks(provider, playlistId = "playlist-one")

        assertEquals(listOf("provider-one:playlistTracks:playlist-one"), cache.keys)
        assertEquals(1, provider.playlistTracksCalls)
        assertEquals(listOf(track("playlist-one-track")), tracks)
    }

    @Test
    fun playlistTracksReturnsCachedValueWithoutFetchingProviderAgain() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeSearchProvider()
        val service = ProviderResponseService(cache)

        service.playlistTracks(provider, playlistId = "playlist-one")
        provider.playlistTracksResults = listOf(track("changed"))
        val tracks = service.playlistTracks(provider, playlistId = "playlist-one")

        assertEquals(1, provider.playlistTracksCalls)
        assertEquals(listOf(track("playlist-one-track")), tracks)
    }

    @Test
    fun internetRadioStationsUseCachedProviderResponseResourceKey() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeSearchProvider()
        val service = ProviderResponseService(cache)

        val stations = service.internetRadioStations(provider)

        assertEquals(listOf("provider-one:internetRadioStations:all"), cache.keys)
        assertEquals(1, provider.internetRadioStationsCalls)
        assertEquals(listOf(internetRadioStation("station-one")), stations)
    }

    @Test
    fun internetRadioStationsReturnCachedValueWithoutFetchingProviderAgain() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeSearchProvider()
        val service = ProviderResponseService(cache)

        service.internetRadioStations(provider)
        provider.internetRadioStationsResults = listOf(internetRadioStation("changed"))
        val stations = service.internetRadioStations(provider)

        assertEquals(1, provider.internetRadioStationsCalls)
        assertEquals(listOf(internetRadioStation("station-one")), stations)
    }

    @Test
    fun internetRadioStationInvalidationClearsCachedStationList() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeSearchProvider()
        val service = ProviderResponseService(cache)

        service.internetRadioStations(provider)
        provider.internetRadioStationsResults = listOf(internetRadioStation("changed"))
        service.invalidateInternetRadioStations(provider)
        val stations = service.internetRadioStations(provider)

        assertEquals(2, provider.internetRadioStationsCalls)
        assertEquals(listOf(internetRadioStation("changed")), stations)
    }

    @Test
    fun playlistInvalidationClearsCachedPlaylistLists() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeSearchProvider()
        val service = ProviderResponseService(cache)

        service.playlists(provider, limit = 50)
        provider.playlistsResults = listOf(playlist("changed"))
        service.invalidatePlaylists(provider)
        val playlists = service.playlists(provider, limit = 50)

        assertEquals(2, provider.playlistsCalls)
        assertEquals(listOf(playlist("changed")), playlists)
    }

    @Test
    fun playlistTrackInvalidationClearsOnlyRequestedPlaylistTracks() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeSearchProvider()
        val service = ProviderResponseService(cache)

        service.playlistTracks(provider, playlistId = "playlist-one")
        service.playlistTracks(provider, playlistId = "playlist-two")
        provider.playlistTracksResults = listOf(track("changed"))
        service.invalidatePlaylistTracks(provider, playlistId = "playlist-one")
        val first = service.playlistTracks(provider, playlistId = "playlist-one")
        val second = service.playlistTracks(provider, playlistId = "playlist-two")

        assertEquals(3, provider.playlistTracksCalls)
        assertEquals(listOf(track("changed")), first)
        assertEquals(listOf(track("playlist-two-track")), second)
    }

    @Test
    fun searchResourceIdNormalizesQueryAndIncludesLimit() {
        assertEquals("new order:25", searchResourceId(" New Order ", 25))
    }

    private class RecordingProviderResponseCacheRepository : ProviderResponseCacheRepository {
        private val values = mutableMapOf<String, String>()
        val keys = mutableListOf<String>()

        override suspend fun <T> cachedProviderResponse(
            provider: MediaProvider,
            resourceType: String,
            resourceId: String,
            decode: (String) -> T,
            encode: (T) -> String,
            fetch: suspend () -> T,
        ): T {
            val key = "${provider.cacheNamespace}:$resourceType:$resourceId"
            keys += key
            values[key]?.let { return decode(it) }
            val value = fetch()
            values[key] = encode(value)
            return value
        }

        override fun invalidateProviderResponses(
            provider: MediaProvider,
            resourceType: String,
        ) {
            val prefix = "${provider.cacheNamespace}:$resourceType:"
            values.keys.filter { it.startsWith(prefix) }.forEach(values::remove)
        }

        override fun invalidateProviderResponse(
            provider: MediaProvider,
            resourceType: String,
            resourceId: String,
        ) {
            values.remove("${provider.cacheNamespace}:$resourceType:$resourceId")
        }
    }

    private class FakeSearchProvider : MediaProvider {
        override val id: ProviderId = ProviderId("provider-one")
        override val displayName: String = "Provider One"
        override val capabilities: ProviderCapabilities = ProviderCapabilities(
            supportsStreamingTranscode = true,
            supportsDownloadTranscode = true,
            supportsArtistRadio = true,
            supportsAlbumRadio = true,
            supportsTrackRadio = true,
        )
        var searchCalls: Int = 0
        var albumCalls: Int = 0
        var artistCalls: Int = 0
        var albumListCalls: Int = 0
        var artistsCalls: Int = 0
        var playlistsCalls: Int = 0
        var playlistTracksCalls: Int = 0
        var internetRadioStationsCalls: Int = 0
        var results: MediaSearchResults = MediaSearchResults(tracks = listOf(track("new-order")))
        var albumDetails: AlbumDetails = AlbumDetails(album("album-one"), tracks = listOf(track("album-track")))
        var artistDetails: ArtistDetails = ArtistDetails(artist("artist-one"), albums = listOf(album("artist-album")))
        var albumListResults: List<Album>? = null
        var artistsResults: List<Artist>? = null
        var playlistsResults: List<Playlist>? = null
        var playlistTracksResults: List<Track>? = null
        var internetRadioStationsResults: List<InternetRadioStation>? = null

        override suspend fun validateConnection(): ConnectionValidation =
            error("unused")

        override suspend fun recentlyAddedAlbums(limit: Int): List<Album> =
            error("unused")

        override suspend fun albumList(type: AlbumListType, limit: Int): List<Album> {
            albumListCalls += 1
            return albumListResults ?: listOf(album("${type.providerValue}-$limit"))
        }

        override suspend fun albumsByGenre(genre: String, limit: Int): List<Album> =
            listOf(album("genre-$genre-$limit"))

        override suspend fun albumsByYear(fromYear: Int, toYear: Int, limit: Int): List<Album> =
            listOf(album("years-$fromYear-$toYear-$limit"))

        override suspend fun album(albumId: AlbumId): AlbumDetails {
            albumCalls += 1
            return albumDetails
        }

        override suspend fun artist(artistId: ArtistId): ArtistDetails {
            artistCalls += 1
            return artistDetails
        }

        override suspend fun artists(limit: Int): List<Artist> {
            artistsCalls += 1
            return artistsResults ?: listOf(artist("artist-$limit"))
        }

        override suspend fun playlists(limit: Int): List<Playlist> {
            playlistsCalls += 1
            return playlistsResults ?: listOf(playlist("playlist-$limit"))
        }

        override suspend fun playlistTracks(playlistId: String): List<Track> {
            playlistTracksCalls += 1
            return playlistTracksResults ?: listOf(track("$playlistId-track"))
        }

        override suspend fun internetRadioStations(): List<InternetRadioStation> {
            internetRadioStationsCalls += 1
            return internetRadioStationsResults ?: listOf(internetRadioStation("station-one"))
        }

        override suspend fun tracks(limit: Int): List<Track> =
            error("unused")

        override suspend fun search(query: String, limit: Int): MediaSearchResults {
            searchCalls += 1
            return results
        }

        override suspend fun streamUrl(request: StreamRequest): String =
            error("unused")

        override fun coverArtUrl(coverArtId: String): String =
            error("unused")
    }
}

private fun album(id: String): Album =
    Album(
        id = AlbumId(id),
        title = id,
        artistName = "Artist",
        coverArtId = null,
        recentlyAddedAtIso8601 = null,
        releaseYear = null,
    )

private fun artist(id: String): Artist =
    Artist(
        id = ArtistId(id),
        name = id,
    )

private fun playlist(id: String): Playlist =
    Playlist(
        id = id,
        name = id,
        trackCount = 3,
        durationSeconds = 540,
        coverArtId = "cover-$id",
    )

private fun internetRadioStation(id: String): InternetRadioStation =
    InternetRadioStation(
        id = id,
        name = id,
        streamUrl = "https://example.com/$id",
        homePageUrl = "https://example.com",
    )

private fun track(id: String): Track =
    Track(
        id = TrackId(id),
        title = id,
        artistName = "Artist",
        albumTitle = "Album",
        durationSeconds = 180,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )
