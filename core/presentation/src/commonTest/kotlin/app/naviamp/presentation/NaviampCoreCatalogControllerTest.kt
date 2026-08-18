package app.naviamp.presentation

import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaPage
import app.naviamp.domain.provider.MediaPageRequest
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreCatalogControllerTest {
    @Test
    fun changingTheSharedSearchFieldExecutesAProviderSearch() = runTest {
        val provider = CatalogTestProvider()
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreCatalogController(store, NaviampCoreMediaProviderSource { provider })
        val command = NaviampCoreCommand.Search.ChangeQuery("Canibus")

        assertEquals(NaviampCoreImmediateCommandResult.Deferred, controller.dispatch(command))
        controller.execute(command)

        assertEquals(listOf("Canibus"), provider.searchQueries)
        assertEquals("Canibus", store.state.value.shell.search.results.artists.single().title)
    }

    @Test
    fun searchPublishesMappedProviderResultsAndCommonStatus() = runTest {
        val provider = CatalogTestProvider()
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreCatalogController(store, NaviampCoreMediaProviderSource { provider })
        controller.dispatch(NaviampCoreCommand.Search.ChangeQuery("  ambient  "))

        controller.execute(NaviampCoreCommand.Search.Submit)

        val search = store.state.value.shell.search
        assertEquals("ambient", provider.searchQueries.single())
        assertEquals("ambient", search.results.artists.single().title)
        assertEquals("Found 1 matches.", search.status)
        assertFalse(search.searching)
    }

    @Test
    fun staleSearchCannotOverwriteANewerResult() = runTest {
        val firstSearchGate = CompletableDeferred<Unit>()
        val provider = CatalogTestProvider(firstSearchGate)
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreCatalogController(store, NaviampCoreMediaProviderSource { provider })
        controller.dispatch(NaviampCoreCommand.Search.ChangeQuery("first"))
        val first = launch { controller.execute(NaviampCoreCommand.Search.Submit) }
        runCurrent()
        controller.dispatch(NaviampCoreCommand.Search.ChangeQuery("second"))
        val second = launch { controller.execute(NaviampCoreCommand.Search.Submit) }
        second.join()
        firstSearchGate.complete(Unit)
        first.join()

        assertEquals("second", store.state.value.shell.search.results.artists.single().title)
    }

    @Test
    fun libraryRefreshAndLoadMoreOwnPagingAndDeduplication() = runTest {
        val provider = CatalogTestProvider()
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreCatalogController(
            stateStore = store,
            providerSource = NaviampCoreMediaProviderSource { provider },
            libraryPageSize = 2,
        )

        controller.execute(NaviampCoreCommand.Library.Refresh)
        controller.execute(NaviampCoreCommand.Library.LoadMore)

        assertEquals(listOf("artist-1", "artist-2", "artist-3"), store.state.value.shell.library.artists.map { it.id })
        assertFalse(store.state.value.shell.library.syncStatus.isSyncing)
        assertNull(store.state.value.shell.library.syncStatus.message)
        assertEquals(listOf(0, 2), provider.artistPageOffsets)
    }

    @Test
    fun fullLibraryRefreshAlsoRefreshesTheSourceGenreInventory() = runTest {
        var refreshes = 0
        val provider = CatalogTestProvider()
        val controller = NaviampCoreCatalogController(
            stateStore = NaviampCoreStateStore(),
            providerSource = NaviampCoreMediaProviderSource { provider },
            libraryGenreRefresh = NaviampCoreLibraryGenreRefreshPort { refreshes += 1 },
        )

        controller.execute(NaviampCoreCommand.Library.Refresh)
        controller.execute(NaviampCoreCommand.Library.LoadMore)

        assertEquals(1, refreshes)
    }

    @Test
    fun genreInventoryFailureDoesNotHideTheBrowsableLibrary() = runTest {
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreCatalogController(
            stateStore = store,
            providerSource = NaviampCoreMediaProviderSource { CatalogTestProvider() },
            libraryGenreRefresh = NaviampCoreLibraryGenreRefreshPort { error("genre endpoint unavailable") },
        )

        controller.execute(NaviampCoreCommand.Library.Refresh)

        assertEquals(listOf("artist-1", "artist-2", "artist-3"), store.state.value.shell.library.artists.map { it.id })
        assertNull(store.state.value.shell.library.syncStatus.message)
    }

    @Test
    fun libraryQueryUsesProviderPagingSearchAndJumpIntentStaysInCore() = runTest {
        val provider = CatalogTestProvider()
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreCatalogController(store, NaviampCoreMediaProviderSource { provider })

        controller.dispatch(NaviampCoreCommand.Library.ChangeQuery("three"))
        controller.execute(NaviampCoreCommand.Library.Refresh)
        controller.execute(NaviampCoreCommand.Library.JumpToLetter('t'))

        assertEquals(listOf("artist-3"), store.state.value.shell.library.artists.map { it.id })
        assertEquals('T', store.state.value.viewport.libraryJump?.letter)
        assertEquals(1L, store.state.value.viewport.libraryJump?.generation)
    }

    @Test
    fun alphabetJumpLoadsPagesUntilTheRequestedRangeIsAvailable() = runTest {
        val provider = CatalogTestProvider()
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreCatalogController(
            stateStore = store,
            providerSource = NaviampCoreMediaProviderSource { provider },
            libraryPageSize = 1,
        )
        controller.execute(NaviampCoreCommand.Library.Refresh)

        controller.execute(NaviampCoreCommand.Library.JumpToLetter('Z'))

        assertEquals(listOf("artist-1", "artist-2", "artist-3"), store.state.value.shell.library.artists.map { it.id })
        assertEquals(listOf(0, 1, 2), provider.artistPageOffsets)
        assertEquals('Z', store.state.value.viewport.libraryJump?.letter)
    }

    @Test
    fun missingProviderProducesSharedDisconnectedState() = runTest {
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreCatalogController(store, NaviampCoreMediaProviderSource { null })
        controller.dispatch(NaviampCoreCommand.Search.ChangeQuery("query"))

        controller.execute(NaviampCoreCommand.Search.Submit)
        controller.execute(NaviampCoreCommand.Library.Refresh)

        assertEquals("Connect to Navidrome to search.", store.state.value.shell.search.status)
        assertEquals("Connect to Navidrome to search.", store.state.value.shell.library.syncStatus.message)
    }
}

private class CatalogTestProvider(
    private val firstSearchGate: CompletableDeferred<Unit>? = null,
) : MediaProvider {
    override val id = ProviderId("test")
    override val displayName = "Test"
    override val capabilities = ProviderCapabilities(
        supportsStreamingTranscode = false,
        supportsDownloadTranscode = false,
        supportsArtistRadio = false,
        supportsAlbumRadio = false,
        supportsTrackRadio = false,
        supportsArtistFavorites = true,
    )
    val searchQueries = mutableListOf<String>()
    val artistPageOffsets = mutableListOf<Int>()
    private val libraryArtists = listOf(
        artist("artist-1", "One"),
        artist("artist-2", "Two"),
        artist("artist-3", "Three"),
    )

    override suspend fun validateConnection() = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int) = emptyList<app.naviamp.domain.Album>()
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("Not used")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("Not used")
    override suspend fun artists(limit: Int) = libraryArtists.take(limit)
    override suspend fun artistsPage(request: MediaPageRequest): MediaPage<Artist> {
        artistPageOffsets += request.offset
        val items = libraryArtists.drop(request.offset).take(request.limit)
        return MediaPage(items, request.offset, request.limit, request.offset + items.size < libraryArtists.size)
    }

    override suspend fun searchArtistsPage(query: String, request: MediaPageRequest): MediaPage<Artist> {
        val items = libraryArtists.filter { it.name.contains(query, ignoreCase = true) }
        return MediaPage(items, request.offset, request.limit, hasMore = false)
    }

    override suspend fun tracks(limit: Int) = emptyList<Track>()
    override suspend fun search(query: String, limit: Int): MediaSearchResults {
        searchQueries += query
        if (query == "first") firstSearchGate?.await()
        return MediaSearchResults(artists = listOf(artist("search-$query", query)))
    }

    override suspend fun streamUrl(request: StreamRequest) = "https://stream.example"
    override fun coverArtUrl(coverArtId: String) = "https://art.example/$coverArtId"

    private fun artist(id: String, name: String) = Artist(ArtistId(id), name)
}
