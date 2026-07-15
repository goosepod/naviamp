package app.naviamp.domain.provider

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApiCatalogServiceTest {
    @Test
    fun blankQueryUsesThePagedCatalogEndpoint() = runTest {
        val provider = RecordingProvider()
        val request = MediaPageRequest(offset = 50, limit = 25)

        val page = ApiCatalogService { provider }.artistsPage("  ", request)

        assertEquals(listOf(request), provider.artistPageRequests)
        assertEquals(emptyList(), provider.artistSearchRequests)
        assertEquals("Catalog Artist", page.items.single().name)
    }

    @Test
    fun nonBlankQueryUsesThePagedSearchEndpoint() = runTest {
        val provider = RecordingProvider()
        val request = MediaPageRequest(limit = 20)

        val page = ApiCatalogService { provider }.artistsPage("  new order  ", request)

        assertEquals(emptyList(), provider.artistPageRequests)
        assertEquals(listOf("new order" to request), provider.artistSearchRequests)
        assertEquals("Search Artist", page.items.single().name)
    }

    @Test
    fun missingProviderProducesAConnectionError() = runTest {
        val error = assertFailsWith<IllegalStateException> {
            ApiCatalogService { null }.albumsPage("", MediaPageRequest())
        }

        assertEquals("Connect to Navidrome before loading the catalog.", error.message)
    }

    private class RecordingProvider : MediaProvider {
        override val id = ProviderId("recording")
        override val displayName = "Recording"
        override val capabilities = ProviderCapabilities(
            supportsStreamingTranscode = false,
            supportsDownloadTranscode = false,
            supportsArtistRadio = false,
            supportsAlbumRadio = false,
            supportsTrackRadio = false,
        )

        val artistPageRequests = mutableListOf<MediaPageRequest>()
        val artistSearchRequests = mutableListOf<Pair<String, MediaPageRequest>>()

        override suspend fun artistsPage(request: MediaPageRequest): MediaPage<Artist> {
            artistPageRequests += request
            return request.toMediaPage(listOf(Artist(ArtistId("catalog"), "Catalog Artist")))
        }

        override suspend fun searchArtistsPage(query: String, request: MediaPageRequest): MediaPage<Artist> {
            artistSearchRequests += query to request
            return request.toMediaPage(listOf(Artist(ArtistId("search"), "Search Artist")))
        }

        override suspend fun validateConnection() = ConnectionValidation(null, null)
        override suspend fun recentlyAddedAlbums(limit: Int): List<Album> = emptyList()
        override suspend fun album(albumId: AlbumId): AlbumDetails = error("unused")
        override suspend fun artist(artistId: ArtistId): ArtistDetails = error("unused")
        override suspend fun artists(limit: Int): List<Artist> = emptyList()
        override suspend fun tracks(limit: Int): List<Track> = emptyList()
        override suspend fun search(query: String, limit: Int) = MediaSearchResults()
        override suspend fun streamUrl(request: StreamRequest): String = error("unused")
        override fun coverArtUrl(coverArtId: String): String = coverArtId
    }
}
