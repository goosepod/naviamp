package app.naviamp.presentation

import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.AlphabeticalLibraryKind
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
import app.naviamp.ui.NaviampLibraryView
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

        assertEquals(listOf("artist-1", "artist-2", "artist-3"), store.state.value.shell.library.artists.items.map { it.id })
        assertFalse(store.state.value.shell.library.artists.syncStatus.isSyncing)
        assertNull(store.state.value.shell.library.artists.syncStatus.message)
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

        assertEquals(listOf("artist-1", "artist-2", "artist-3"), store.state.value.shell.library.artists.items.map { it.id })
        assertNull(store.state.value.shell.library.artists.syncStatus.message)
    }

    @Test
    fun libraryQueryUsesProviderPagingSearchAndJumpIntentStaysInCore() = runTest {
        val provider = CatalogTestProvider()
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreCatalogController(store, NaviampCoreMediaProviderSource { provider })

        controller.dispatch(NaviampCoreCommand.Library.ChangeQuery("three"))
        controller.execute(NaviampCoreCommand.Library.Refresh)
        controller.execute(NaviampCoreCommand.Library.JumpToLetter('t'))

        assertEquals(listOf("artist-3"), store.state.value.shell.library.artists.items.map { it.id })
        assertEquals('T', store.state.value.shell.library.jumpRequest?.letter)
        assertEquals(1L, store.state.value.shell.library.jumpRequest?.generation)
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

        assertEquals(listOf("artist-1", "artist-2", "artist-3"), store.state.value.shell.library.artists.items.map { it.id })
        assertEquals(listOf(0, 0, 1, 2), provider.artistPageOffsets)
        assertEquals('Z', store.state.value.shell.library.jumpRequest?.letter)
    }

    @Test
    fun songAlphabetJumpUsesTheProvidersGlobalCatalogOffset() = runTest {
        val provider = CatalogTestProvider().apply {
            alphabeticalJumpOffset = 12_345
            alphabeticalJumpTitle = "M Song"
        }
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreCatalogController(
            stateStore = store,
            providerSource = NaviampCoreMediaProviderSource { provider },
            libraryPageSize = 25,
        )
        val songs = NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Songs)
        controller.dispatch(songs)
        controller.execute(songs)

        controller.execute(NaviampCoreCommand.Library.JumpToLetter('m'))

        assertEquals(AlphabeticalLibraryKind.Tracks, provider.alphabeticalJumpKind)
        assertEquals('M', provider.alphabeticalJumpLetter)
        assertEquals(listOf(0, 12_345), provider.trackPageOffsets)
        assertEquals(listOf("M Song"), store.state.value.shell.library.songs.tracks.map { it.title })
        assertEquals('M', store.state.value.shell.library.jumpRequest?.letter)
    }

    @Test
    fun albumAlphabetJumpUsesTheProvidersGlobalCatalogOffset() = runTest {
        val provider = CatalogTestProvider().apply {
            alphabeticalJumpOffset = 8_765
            alphabeticalJumpTitle = "M Album"
        }
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreCatalogController(
            stateStore = store,
            providerSource = NaviampCoreMediaProviderSource { provider },
            libraryPageSize = 25,
        )
        val albums = NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Albums)
        controller.dispatch(albums)
        controller.execute(albums)

        controller.execute(NaviampCoreCommand.Library.JumpToLetter('m'))

        assertEquals(AlphabeticalLibraryKind.Albums, provider.alphabeticalJumpKind)
        assertEquals('M', provider.alphabeticalJumpLetter)
        assertEquals(listOf(0, 8_765), provider.albumPageOffsets)
        assertEquals(listOf("M Album"), store.state.value.shell.library.albums.items.map { it.title })
    }

    @Test
    fun missingProviderProducesSharedDisconnectedState() = runTest {
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreCatalogController(store, NaviampCoreMediaProviderSource { null })
        controller.dispatch(NaviampCoreCommand.Search.ChangeQuery("query"))

        controller.execute(NaviampCoreCommand.Search.Submit)
        controller.execute(NaviampCoreCommand.Library.Refresh)

        assertEquals("Connect to Navidrome to search.", store.state.value.shell.search.status)
        assertEquals("Connect to Navidrome to search.", store.state.value.shell.library.artists.syncStatus.message)
    }

    @Test
    fun switchingViewsLoadsAlbumsAndSongsWithIndependentQueriesAndPaging() = runTest {
        val provider = CatalogTestProvider()
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreCatalogController(
            stateStore = store,
            providerSource = NaviampCoreMediaProviderSource { provider },
            libraryPageSize = 1,
        )

        val albums = NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Albums)
        controller.dispatch(albums)
        controller.execute(albums)
        controller.execute(NaviampCoreCommand.Library.LoadMore)
        val albumQuery = NaviampCoreCommand.Library.ChangeQuery("Second")
        controller.dispatch(albumQuery)
        controller.execute(albumQuery)

        val songs = NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Songs)
        controller.dispatch(songs)
        controller.execute(songs)

        val library = store.state.value.shell.library
        assertEquals(NaviampLibraryView.Songs, library.selectedView)
        assertEquals("Second", library.albums.query)
        assertEquals(listOf("album-2"), library.albums.items.map { it.id })
        assertEquals(listOf("track-2"), library.songs.tracks.map { it.id })
        assertEquals(listOf(0, 1, 0), provider.albumPageOffsets)
        assertEquals(listOf(0), provider.trackPageOffsets)
    }

    @Test
    fun librarySearchIsScopedToTheSelectedView() = runTest {
        val provider = CatalogTestProvider()
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreCatalogController(store, NaviampCoreMediaProviderSource { provider })

        val artistQuery = NaviampCoreCommand.Library.ChangeQuery("One")
        controller.dispatch(artistQuery)
        controller.execute(artistQuery)
        val albums = NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Albums)
        controller.dispatch(albums)
        controller.execute(albums)
        val albumQuery = NaviampCoreCommand.Library.ChangeQuery("Second")
        controller.dispatch(albumQuery)
        controller.execute(albumQuery)
        val songs = NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Songs)
        controller.dispatch(songs)
        controller.execute(songs)
        val songQuery = NaviampCoreCommand.Library.ChangeQuery("First")
        controller.dispatch(songQuery)
        controller.execute(songQuery)

        assertEquals(listOf("One"), provider.artistSearchQueries)
        assertEquals(listOf("Second"), provider.albumSearchQueries)
        assertEquals(listOf("First"), provider.trackSearchQueries)
        assertEquals("One", store.state.value.shell.library.artists.query)
        assertEquals("Second", store.state.value.shell.library.albums.query)
        assertEquals("First", store.state.value.shell.library.songs.query)
    }

    @Test
    fun changingViewRejectsThePreviousViewsLateResponse() = runTest {
        val albumGate = CompletableDeferred<Unit>()
        val provider = CatalogTestProvider(albumPageGate = albumGate)
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreCatalogController(store, NaviampCoreMediaProviderSource { provider })
        val albums = NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Albums)
        controller.dispatch(albums)
        val albumLoad = launch { controller.execute(albums) }
        runCurrent()

        val songs = NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Songs)
        controller.dispatch(songs)
        controller.execute(songs)
        albumGate.complete(Unit)
        albumLoad.join()

        assertEquals(NaviampLibraryView.Songs, store.state.value.shell.library.selectedView)
        assertEquals(emptyList(), store.state.value.shell.library.albums.items)
        assertEquals(listOf("track-1", "track-2"), store.state.value.shell.library.songs.tracks.map { it.id })
    }

    @Test
    fun songsAreAlphabetizedAsAdditionalPagesLoad() = runTest {
        val provider = CatalogTestProvider()
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreCatalogController(
            stateStore = store,
            providerSource = NaviampCoreMediaProviderSource { provider },
            libraryPageSize = 1,
        )

        val songs = NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Songs)
        controller.dispatch(songs)
        controller.execute(songs)
        assertEquals(listOf("Second Song"), store.state.value.shell.library.songs.tracks.map { it.title })

        controller.execute(NaviampCoreCommand.Library.LoadMore)
        assertEquals(listOf("First Song", "Second Song"), store.state.value.shell.library.songs.tracks.map { it.title })
    }
}

private class CatalogTestProvider(
    private val firstSearchGate: CompletableDeferred<Unit>? = null,
    private val albumPageGate: CompletableDeferred<Unit>? = null,
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
    val albumPageOffsets = mutableListOf<Int>()
    val trackPageOffsets = mutableListOf<Int>()
    val artistSearchQueries = mutableListOf<String>()
    val albumSearchQueries = mutableListOf<String>()
    val trackSearchQueries = mutableListOf<String>()
    var alphabeticalJumpOffset: Int? = null
    var alphabeticalJumpTitle: String? = null
    var alphabeticalJumpKind: AlphabeticalLibraryKind? = null
    var alphabeticalJumpLetter: Char? = null
    private val libraryArtists = listOf(
        artist("artist-1", "One"),
        artist("artist-2", "Two"),
        artist("artist-3", "Three"),
    )
    private val libraryAlbums = listOf(
        Album(AlbumId("album-1"), "First Album", "One", null, null),
        Album(AlbumId("album-2"), "Second Album", "Two", null, null),
    )
    private val libraryTracks = listOf(
        track("track-2", "Second Song"),
        track("track-1", "First Song"),
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
        artistSearchQueries += query
        val items = libraryArtists.filter { it.name.contains(query, ignoreCase = true) }
        return MediaPage(items, request.offset, request.limit, hasMore = false)
    }

    override suspend fun albumsPage(request: MediaPageRequest): MediaPage<Album> {
        albumPageGate?.await()
        albumPageOffsets += request.offset
        if (request.offset == alphabeticalJumpOffset) {
            val title = requireNotNull(alphabeticalJumpTitle)
            return MediaPage(
                listOf(Album(AlbumId("jump-album"), title, "Artist", null, null)),
                request.offset,
                request.limit,
                hasMore = true,
            )
        }
        val items = libraryAlbums.drop(request.offset).take(request.limit)
        return MediaPage(items, request.offset, request.limit, request.offset + items.size < libraryAlbums.size)
    }

    override suspend fun searchAlbumsPage(query: String, request: MediaPageRequest): MediaPage<Album> {
        albumSearchQueries += query
        albumPageOffsets += request.offset
        val items = libraryAlbums.filter { it.title.contains(query, ignoreCase = true) }
        return MediaPage(items, request.offset, request.limit, hasMore = false)
    }

    override suspend fun tracks(limit: Int) = libraryTracks.take(limit)
    override suspend fun tracksPage(request: MediaPageRequest): MediaPage<Track> {
        trackPageOffsets += request.offset
        if (request.offset == alphabeticalJumpOffset) {
            val title = requireNotNull(alphabeticalJumpTitle)
            return MediaPage(listOf(track("jump-track", title)), request.offset, request.limit, hasMore = true)
        }
        val items = libraryTracks.drop(request.offset).take(request.limit)
        return MediaPage(items, request.offset, request.limit, request.offset + items.size < libraryTracks.size)
    }

    override suspend fun alphabeticalLibraryOffset(kind: AlphabeticalLibraryKind, letter: Char): Int? {
        alphabeticalJumpKind = kind
        alphabeticalJumpLetter = letter
        return alphabeticalJumpOffset
    }

    override suspend fun searchTracksPage(query: String, request: MediaPageRequest): MediaPage<Track> {
        trackSearchQueries += query
        trackPageOffsets += request.offset
        val items = libraryTracks.filter { it.title.contains(query, ignoreCase = true) }
        return MediaPage(items, request.offset, request.limit, hasMore = false)
    }
    override suspend fun search(query: String, limit: Int): MediaSearchResults {
        searchQueries += query
        if (query == "first") firstSearchGate?.await()
        return MediaSearchResults(artists = listOf(artist("search-$query", query)))
    }

    override suspend fun streamUrl(request: StreamRequest) = "https://stream.example"
    override fun coverArtUrl(coverArtId: String) = "https://art.example/$coverArtId"

    private fun artist(id: String, name: String) = Artist(ArtistId(id), name)

    private fun track(id: String, title: String) = Track(
        id = TrackId(id),
        title = title,
        artistName = "Artist",
        albumTitle = "Album",
        durationSeconds = 180,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )
}
