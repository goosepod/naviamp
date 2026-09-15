package app.naviamp.presentation

import app.naviamp.domain.Genre
import app.naviamp.domain.cache.LibraryAlbumYear
import app.naviamp.domain.cache.LibraryIndexStats
import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.popular.ArtistPopularTrackCandidate
import app.naviamp.domain.popular.ArtistPopularTrackMatch

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
import app.naviamp.domain.settings.LibraryAlbumSortOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreCatalogControllerTest {
    @Test
    fun cachedArtistsAndPagedSongsCoexistAfterConnection() = runTest {
        val repository = CatalogIndexRepository()
        repository.artists += Artist(ArtistId("cached"), "Cached Artist")
        val provider = CatalogTestProvider()
        val store = NaviampCoreStateStore()
        store.updateShell { it.copy(connectionSettings = it.connectionSettings.copy(currentSourceId = "source")) }
        val controller = NaviampCoreCatalogController(store, { provider }, libraryIndex = repository)

        controller.refreshAfterConnection()
        assertEquals(listOf("cached"), store.state.value.shell.library.artists.items.map { it.id })
        assertTrue(provider.artistPageOffsets.isEmpty())
        val songs = NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Songs)
        controller.dispatch(songs)
        controller.execute(songs)
        assertTrue(store.state.value.shell.library.songs.tracks.isNotEmpty())
        assertEquals(listOf("cached"), store.state.value.shell.library.artists.items.map { it.id })
    }

    @Test
    fun staleCompleteArtistRefreshCannotReplaceTheNewSourceCache() = runTest {
        val gate = CompletableDeferred<Unit>()
        val provider = object : MediaProvider by CatalogTestProvider() {
            override suspend fun artistsPage(request: MediaPageRequest): MediaPage<Artist> {
                gate.await()
                return MediaPage(listOf(Artist(ArtistId("stale"), "Stale")), request.offset, request.limit, false)
            }
        }
        val repository = CatalogIndexRepository()
        repository.artists += Artist(ArtistId("cached"), "Cached")
        val store = NaviampCoreStateStore()
        store.updateShell { it.copy(connectionSettings = it.connectionSettings.copy(currentSourceId = "old")) }
        val controller = NaviampCoreCatalogController(store, { provider }, libraryIndex = repository)
        val refresh = launch { controller.execute(NaviampCoreCommand.Library.Refresh) }
        runCurrent()
        store.updateShell { it.copy(connectionSettings = it.connectionSettings.copy(currentSourceId = "new")) }
        controller.resetForSourceChange()
        gate.complete(Unit)
        refresh.join()
        assertEquals(listOf("cached"), repository.artists.map { it.id.value })
        assertTrue(store.state.value.shell.library.artists.items.isEmpty())
    }

    @Test
    fun completeArtistLibraryConsumesEveryProviderPage() = runTest {
        val provider = CatalogTestProvider()

        val artists = provider.loadCompleteArtistLibrary(maximumArtists = 3, pageSize = 2)

        assertEquals(listOf("artist-1", "artist-2", "artist-3"), artists.map { it.id.value })
        assertEquals(listOf(0, 2), provider.artistPageOffsets)
    }

    @Test
    fun completeArtistLibraryRejectsAFalseContinuingPage() = runTest {
        val provider = CatalogTestProvider(emptyContinuingPage = true)

        val failure = assertFailsWith<IllegalStateException> {
            provider.loadCompleteArtistLibrary(maximumArtists = 3, pageSize = 2)
        }

        assertTrue(failure.message.orEmpty().contains("empty continuing page"))
    }

    @Test
    fun offsetLookupCannotOvertakeANewerLetterOrCrossSources() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backing = CatalogTestProvider()
        val provider = object : MediaProvider by backing {
            override suspend fun alphabeticalLibraryOffset(kind: AlphabeticalLibraryKind, letter: Char): Int? {
                if (letter == 'M') gate.await()
                return if (letter == 'M') 10 else 20
            }
        }
        var active: MediaProvider = provider
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreCatalogController(store, { active })
        val songs = NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Songs)
        controller.dispatch(songs)
        controller.execute(songs)
        val old = launch { controller.execute(NaviampCoreCommand.Library.JumpToLetter('M')) }
        runCurrent()
        controller.execute(NaviampCoreCommand.Library.JumpToLetter('S'))
        gate.complete(Unit)
        old.join()
        assertEquals(listOf(0, 20), backing.trackPageOffsets)
        assertEquals('S', store.state.value.shell.library.jumpRequest?.letter)

        val secondGate = CompletableDeferred<Unit>()
        active = object : MediaProvider by backing {
            override suspend fun alphabeticalLibraryOffset(kind: AlphabeticalLibraryKind, letter: Char): Int? {
                secondGate.await()
                return 99
            }
        }
        val stale = launch { controller.execute(NaviampCoreCommand.Library.JumpToLetter('Z')) }
        runCurrent()
        active = object : MediaProvider by backing { override val cacheNamespace = "different-source" }
        secondGate.complete(Unit)
        stale.join()
        assertEquals(listOf(0, 20), backing.trackPageOffsets)
    }

    @Test
    fun failedFallbackPageStopsInsteadOfRetryingAThousandTimes() = runTest {
        var calls = 0
        val provider = object : MediaProvider by CatalogTestProvider() {
            override suspend fun tracksPage(request: MediaPageRequest): MediaPage<Track> {
                calls++
                error("offline")
            }
        }
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreCatalogController(store, { provider })
        controller.dispatch(NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Songs))
        controller.execute(NaviampCoreCommand.Library.JumpToLetter('Z'))
        assertEquals(1, calls)
        assertNull(store.state.value.shell.library.jumpRequest)
        assertNull(store.state.value.shell.library.songs.pendingJump)
    }

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
    fun albumFallbackJumpContinuesPastUnicodeSymbolsAndNumbers() = runTest {
        val titles = listOf("’90s Rock Essentials", "25", "G I R L")
        val offsets = mutableListOf<Int>()
        val provider = object : MediaProvider by CatalogTestProvider() {
            override suspend fun albumsPage(request: MediaPageRequest): MediaPage<Album> {
                offsets += request.offset
                val items = titles.drop(request.offset).take(request.limit).map {
                    Album(AlbumId(it), it, "Artist", null, null)
                }
                return MediaPage(items, request.offset, request.limit,
                    request.offset + items.size < titles.size)
            }
        }
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreCatalogController(store, NaviampCoreMediaProviderSource { provider }, libraryPageSize = 1)
        val albums = NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Albums)
        controller.dispatch(albums)
        controller.execute(albums)
        controller.execute(NaviampCoreCommand.Library.JumpToLetter('G'))
        assertEquals(listOf(0, 0, 1, 2), offsets)
        assertEquals(titles, store.state.value.shell.library.albums.items.map { it.title })
        assertEquals('G', store.state.value.shell.library.jumpRequest?.letter)
        assertNull(store.state.value.shell.library.albums.pendingJump)
    }

    @Test
    fun albumFallbackDoesNotMistakeArticleSortedTitlesForTheRequestedRange() = runTest {
        val titles = listOf("25", "Les Années 80", "The Aquabats!", "Another Album", "G I R L", "Generationwhy")
        val offsets = mutableListOf<Int>()
        val provider = object : MediaProvider by CatalogTestProvider() {
            override suspend fun albumsPage(request: MediaPageRequest): MediaPage<Album> {
                offsets += request.offset
                val items = titles.drop(request.offset).take(request.limit).map {
                    Album(AlbumId(it), it, "Artist", null, null)
                }
                return MediaPage(items, request.offset, request.limit, request.offset + items.size < titles.size)
            }
        }
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreCatalogController(store, NaviampCoreMediaProviderSource { provider }, libraryPageSize = 3)
        val albums = NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Albums)
        controller.dispatch(albums)
        controller.execute(albums)
        controller.execute(NaviampCoreCommand.Library.JumpToLetter('G'))
        assertEquals(listOf(0, 0, 3), offsets)
        val loaded = store.state.value.shell.library.albums.items.map { it.title }
        assertEquals(titles, loaded)
        assertEquals(4, app.naviamp.domain.library.libraryLetterJumpIndex(loaded, 'G'))
        assertEquals('G', store.state.value.shell.library.jumpRequest?.letter)
    }

    @Test
    fun indexedAlbumsBrowseSearchAndJumpWithoutRefetchingAndRememberAPendingJump() = runTest {
        val snapshots = mutableMapOf<app.naviamp.domain.library.AlbumCatalogScope, app.naviamp.domain.library.AlbumCatalogSnapshot>()
        val repository = object : app.naviamp.domain.library.AlbumCatalogRepository {
            override fun readAlbumCatalog(scope: app.naviamp.domain.library.AlbumCatalogScope) = snapshots[scope]
            override fun replaceAlbumCatalog(scope: app.naviamp.domain.library.AlbumCatalogScope, snapshot: app.naviamp.domain.library.AlbumCatalogSnapshot) { snapshots[scope] = snapshot }
        }
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var requests = 0
        val provider = object : MediaProvider by CatalogTestProvider() {
            override suspend fun albumsPage(request: MediaPageRequest): MediaPage<Album> {
                requests++
                gate.await()
                return MediaPage(listOf("The Aquabats!", "G I R L", "25").map {
                    Album(AlbumId(it), it, "Artist", null, null)
                }, 0, request.limit, false)
            }
        }
        val store = NaviampCoreStateStore()
        val index = app.naviamp.domain.library.AlbumLibraryIndex(repository, { "source" }, { 1000L })
        val controller = NaviampCoreCatalogController(store, NaviampCoreMediaProviderSource { provider }, albumIndex = index)
        val albums = NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Albums)
        controller.dispatch(albums)
        val loading = launch { controller.execute(albums) }
        runCurrent()
        controller.execute(NaviampCoreCommand.Library.JumpToLetter('G'))
        assertEquals('G', store.state.value.shell.library.albums.pendingJump)
        gate.complete(Unit)
        loading.join()
        assertEquals(listOf("25", "G I R L", "The Aquabats!"), store.state.value.shell.library.albums.items.map { it.title })
        assertEquals('G', store.state.value.shell.library.jumpRequest?.letter)
        val query = NaviampCoreCommand.Library.ChangeQuery("girl")
        controller.dispatch(query)
        controller.execute(query)
        controller.execute(NaviampCoreCommand.Library.LoadMore)
        controller.execute(NaviampCoreCommand.Library.JumpToLetter('T'))
        assertEquals(1, requests)
        val restartedStore = NaviampCoreStateStore()
        val restarted = NaviampCoreCatalogController(restartedStore, NaviampCoreMediaProviderSource { provider }, albumIndex = index)
        restarted.dispatch(albums)
        restarted.execute(albums)
        assertEquals(1, requests)
        assertEquals(store.state.value.shell.library.albums.items, restartedStore.state.value.shell.library.albums.items)
        restarted.execute(NaviampCoreCommand.Library.Refresh)
        assertEquals(2, requests)
    }

    @Test
    fun indexedAlbumsCanSortByDateAddedAndDisableAlphabeticalJumping() = runTest {
        val snapshots = mutableMapOf<app.naviamp.domain.library.AlbumCatalogScope, app.naviamp.domain.library.AlbumCatalogSnapshot>()
        val repository = object : app.naviamp.domain.library.AlbumCatalogRepository {
            override fun readAlbumCatalog(scope: app.naviamp.domain.library.AlbumCatalogScope) = snapshots[scope]
            override fun replaceAlbumCatalog(scope: app.naviamp.domain.library.AlbumCatalogScope, snapshot: app.naviamp.domain.library.AlbumCatalogSnapshot) {
                snapshots[scope] = snapshot
            }
        }
        val provider = object : MediaProvider by CatalogTestProvider() {
            override suspend fun albumsPage(request: MediaPageRequest) = MediaPage(
                listOf(
                    Album(AlbumId("old"), "Alpha", "Artist", null, "2026-09-01T00:00:00Z"),
                    Album(AlbumId("new"), "Zulu", "Artist", null, "2026-09-15T00:00:00Z"),
                    Album(AlbumId("missing"), "Beta", "Artist", null, null),
                ),
                0,
                request.limit,
                false,
            )
        }
        val store = NaviampCoreStateStore()
        val changes = mutableListOf<LibraryAlbumSortOrder>()
        val controller = NaviampCoreCatalogController(
            store,
            NaviampCoreMediaProviderSource { provider },
            albumIndex = app.naviamp.domain.library.AlbumLibraryIndex(repository, { "source" }, { 1_000L }),
            onLibraryAlbumSortOrderChanged = changes::add,
        )
        val selectAlbums = NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Albums)
        controller.dispatch(selectAlbums)
        controller.execute(selectAlbums)

        controller.dispatch(NaviampCoreCommand.Library.ChangeAlbumSortOrder(LibraryAlbumSortOrder.RecentlyAdded))
        controller.execute(NaviampCoreCommand.Library.JumpToLetter('Z'))

        assertEquals(listOf("Zulu", "Alpha", "Beta"), store.state.value.shell.library.albums.items.map { it.title })
        assertEquals(LibraryAlbumSortOrder.RecentlyAdded, store.state.value.shell.library.albums.albumSortOrder)
        assertEquals(listOf(LibraryAlbumSortOrder.RecentlyAdded), changes)
        assertEquals(null, store.state.value.shell.library.jumpRequest)
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
    private val emptyContinuingPage: Boolean = false,
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
        if (emptyContinuingPage) {
            return MediaPage(emptyList(), request.offset, request.limit, hasMore = true)
        }
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

    private class CatalogIndexRepository : LocalLibraryIndexRepository {
        var syncStarted = false
            private set
        var syncCompleted = false
            private set
        val artists = mutableListOf<Artist>()
        val albums = mutableListOf<Album>()
        val tracks = mutableListOf<Track>()
        var trackDetailAlbumWrites = 0
            private set
        var checkedScanSignature: String? = null
            private set
        var genres = emptyList<Genre>()
            private set

        override fun mediaSource(sourceId: String) =
            null

        override fun markLibraryScanChecked(sourceId: String, signature: String) {
            checkedScanSignature = signature
        }

        override fun markLibrarySyncStarted(sourceId: String) {
            syncStarted = true
        }

        override fun markLibrarySyncCompleted(sourceId: String) {
            syncCompleted = true
        }

        override fun upsertLibraryArtists(sourceId: String, artists: List<Artist>) {
            this.artists += artists
        }

        override fun replaceLibraryArtists(sourceId: String, artists: List<Artist>) {
            this.artists.clear()
            this.artists += artists
        }

        override fun upsertLibraryAlbums(sourceId: String, albums: List<Album>) {
            if (albums.size == 1) trackDetailAlbumWrites += 1
            this.albums += albums
        }

        override fun upsertLibraryTracks(sourceId: String, tracks: List<Track>) {
            this.tracks += tracks
        }

        override fun replaceLibraryGenreInventory(sourceId: String, genres: List<Genre>) {
            this.genres = genres
        }

        override fun librarySnapshot(sourceId: String, limit: Long, offset: Long): LibrarySnapshot =
            LibrarySnapshot(artists = artists.toList())

        override fun searchLibrary(sourceId: String, query: String, limit: Long, offset: Long): LibrarySnapshot =
            LibrarySnapshot(artists = artists.toList())

        override fun randomLibraryTrackForAlbum(sourceId: String, albumId: AlbumId): Track? =
            null

        override fun libraryTracksForAlbum(sourceId: String, albumId: AlbumId, limit: Long): List<Track> =
            emptyList()

        override fun randomLibraryTrackForArtist(sourceId: String, artistId: ArtistId): Track? =
            null

        override fun libraryTracksForArtist(sourceId: String, artistId: ArtistId, limit: Long): List<Track> =
            emptyList()

        override fun libraryTracksForArtistName(sourceId: String, artistName: String, limit: Long): List<Track> =
            emptyList()

        override fun relatedLibraryTracks(sourceId: String, track: Track, limit: Long): List<Track> =
            emptyList()

        override fun libraryIndexStats(sourceId: String): LibraryIndexStats =
            LibraryIndexStats(artistCount = artists.size.toLong(), albumCount = albums.size.toLong(), trackCount = tracks.size.toLong())

        override fun libraryAlbumYears(sourceId: String): List<LibraryAlbumYear> =
            emptyList()

        override fun clearLibraryData(sourceId: String?) = Unit

        override fun artistPopularTracks(
            sourceId: String,
            artistId: ArtistId,
            source: String,
        ): List<ArtistPopularTrackMatch> =
            emptyList()

        override fun replaceArtistPopularTracks(
            sourceId: String,
            artistId: ArtistId,
            source: String,
            candidates: List<ArtistPopularTrackCandidate>,
            matchedTracksBySourceTrackId: Map<String, Track>,
            fetchedAtEpochMillis: Long,
        ) = Unit
    }
