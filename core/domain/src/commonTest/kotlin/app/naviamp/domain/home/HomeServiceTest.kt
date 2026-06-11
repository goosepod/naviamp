package app.naviamp.domain.home

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Genre
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeServiceTest {
    @Test
    fun loadUsesSharedArtistLimitAndAggregatesProviderSections() = runTest {
        val provider = FakeHomeProvider()

        val home = HomeService(
            provider = provider,
            date = HomeDate(year = 2026, dayOfYear = 1),
        ).load(
            recentRadioStreams = emptyList(),
            recentInternetRadioStations = listOf(radioStation("recent")),
            artistLimit = 123,
        )

        assertEquals(123, provider.artistLimit)
        assertEquals(listOf(album("newest")), home.recentlyAddedAlbums)
        assertEquals(listOf(album("random-8")), home.mixAlbums)
        assertEquals(listOf(album("recent")), home.recentAlbums)
        assertEquals(listOf(album("frequent")), home.frequentAlbums)
        assertEquals(listOf(album("random-6")), home.randomAlbums)
        assertEquals(listOf(Artist(ArtistId("artist-123"), "Artist 123")), home.artists)
        assertEquals(listOf(Playlist("playlist", "Playlist", trackCount = 2)), home.playlists)
        assertEquals(listOf(radioStation("station")), home.radioStations)
        assertEquals(listOf(radioStation("recent")), home.recentInternetRadioStations)
    }

    @Test
    fun loadUsesDefaultArtistLimit() = runTest {
        val provider = FakeHomeProvider()

        HomeService(
            provider = provider,
            date = HomeDate(year = 2026, dayOfYear = 1),
        ).load()

        assertEquals(HomeDefaultArtistLimit, provider.artistLimit)
    }

    @Test
    fun mixBuilderAlbumCandidatesUseHomeMixSourcesWithStableDeduping() {
        val shared = album("shared")
        val home = HomeContent(
            randomAlbums = listOf(album("random"), shared),
            mixAlbums = listOf(shared, album("mix")),
            recentAlbums = listOf(album("recent")),
            frequentAlbums = listOf(album("frequent"), album("random")),
            recentlyAddedAlbums = listOf(album("newest")),
            genreSpotlightAlbums = listOf(album("genre")),
            decadeAlbums = listOf(album("decade")),
        )

        assertEquals(
            listOf("random", "shared", "mix", "recent", "frequent"),
            home.mixBuilderAlbumCandidates().map { it.id.value },
        )
    }

    private class FakeHomeProvider : MediaProvider {
        override val id: ProviderId = ProviderId("fake-home")
        override val displayName: String = "Fake Home"
        override val capabilities: ProviderCapabilities = ProviderCapabilities(
            supportsStreamingTranscode = false,
            supportsDownloadTranscode = false,
            supportsArtistRadio = false,
            supportsAlbumRadio = false,
            supportsTrackRadio = false,
        )

        var artistLimit: Int? = null

        override suspend fun validateConnection(): ConnectionValidation =
            ConnectionValidation(serverVersion = null, apiVersion = null)

        override suspend fun recentlyAddedAlbums(limit: Int): List<Album> =
            listOf(album("recently-added-$limit"))

        override suspend fun albumList(type: AlbumListType, limit: Int): List<Album> =
            when (type) {
                AlbumListType.Newest -> listOf(album("newest"))
                AlbumListType.Recent -> listOf(album("recent"))
                AlbumListType.Frequent -> listOf(album("frequent"))
                AlbumListType.Random -> listOf(album("random-$limit"))
                AlbumListType.Starred -> emptyList()
            }

        override suspend fun artists(limit: Int): List<Artist> {
            artistLimit = limit
            return listOf(Artist(ArtistId("artist-$limit"), "Artist $limit"))
        }

        override suspend fun playlists(limit: Int): List<Playlist> =
            listOf(Playlist("playlist", "Playlist", trackCount = 2))

        override suspend fun internetRadioStations(): List<InternetRadioStation> =
            listOf(radioStation("station"))

        override suspend fun genres(limit: Int): List<Genre> =
            emptyList()

        override suspend fun album(albumId: AlbumId) =
            error("unused")

        override suspend fun artist(artistId: ArtistId) =
            error("unused")

        override suspend fun tracks(limit: Int): List<Track> =
            emptyList()

        override suspend fun search(query: String, limit: Int): MediaSearchResults =
            MediaSearchResults()

        override suspend fun streamUrl(request: StreamRequest): String =
            error("unused")

        override fun coverArtUrl(coverArtId: String): String =
            "https://example.test/cover/$coverArtId"
    }
}

private fun album(id: String): Album =
    Album(
        id = AlbumId(id),
        title = "Album $id",
        artistName = "Artist",
        coverArtId = null,
        recentlyAddedAtIso8601 = null,
    )

private fun radioStation(id: String): InternetRadioStation =
    InternetRadioStation(
        id = id,
        name = "Station $id",
        streamUrl = "https://example.test/$id",
    )
