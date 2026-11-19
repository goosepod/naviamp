package app.naviamp.presentation

import app.naviamp.domain.*
import app.naviamp.domain.provider.*
import app.naviamp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampVehicleLibraryTest {
    private val labels = NaviampVehicleBrowseLabels("Home", "Playlists", "Radio", "Empty", "Loading", "Disconnected", "Failed", "Next", "Previous", "Queue", "Play", "Shuffle", "Retry")
    private val libraryLabels = NaviampVehicleLibraryLabels("Library", "Artists", "Albums", "Tracks", "Favorites", "Unavailable")
    private val fixture = FakeCoreMediaProvider()
    private fun product() = MutableStateFlow(NaviampCoreState(shell = NaviampAppShellUiState(
        connectionSettings = NaviampConnectionSettingsUi(currentSourceId = "source"))))
    private fun access(product: MutableStateFlow<NaviampCoreState>, provider: MediaProvider = fixture,
        play: (Track) -> Boolean = { error("Browsing played audio") }) =
        NaviampCoreVehicleLibraryAccess(product, NaviampCoreMediaProviderSource { provider }, play, libraryLabels)
    private fun TestScope.controller(product: MutableStateFlow<NaviampCoreState>, access: NaviampVehicleLibraryAccess,
        tabs: Int = 4, items: Int = 5, collections: NaviampVehicleCollectionAccess = NaviampVehicleCollectionAccess {
            NaviampVehicleCollection(listOf(fixture.track)) { _, _ -> error("Browse played album") }
        }) = NaviampVehicleBrowseController(backgroundScope, product, NaviampCoreExternalPlaybackBridge(product, {}), {},
            labels, items, readinessTimeoutMillis = 100, maximumTabs = tabs, collections = collections, library = access)
    private fun open(controller: NaviampVehicleBrowseController, title: String) {
        val state = controller.state.value
        val page = state.pages.lastOrNull()
        if (page != null) controller.selectPage(page.id, page.rows.first { it.title == title }.id, state.revision) { assertFalse(it) }
        else {
            val tab = state.tabs.first { it.destination == NaviampVehicleBrowseDestination.Library }
            controller.select(tab.destination, tab.rows.first { it.title == title }.id, state.revision) { assertFalse(it) }
        }
    }

    @Test fun libraryUsesFourthTabOrHomeEntryAndNeverLabelsRecentsAsFullLibrary() = runTest {
        for (tabs in listOf(1, 3, 4)) {
            val product = product()
            val controller = controller(product, access(product), tabs)
            val state = controller.state.value
            assertEquals(tabs, state.tabs.size)
            if (tabs == 4) {
                assertEquals("Library", state.tabs.last().title)
                assertEquals(listOf("Artists", "Albums", "Tracks"), state.tabs.last().rows.map { it.title })
                assertTrue(state.tabs.first().rows.none { it.title == "Library" })
            } else {
                val library = state.tabs.first().rows.single { it.title == "Library" }
                controller.select(NaviampVehicleBrowseDestination.Home, library.id, state.revision) { assertFalse(it) }
                runCurrent()
                assertEquals(listOf("Artists", "Albums", "Tracks"), controller.state.value.pages.single().rows.map { it.title })
            }
            controller.close()
        }
    }

    @Test fun artistsOpenPagedReleasesThenAlbumDetailsWithoutPhoneNavigationOrPlayback() = runTest {
        val product = product()
        val before = product.value
        val calls = mutableListOf<String>()
        val provider = object : MediaProvider by fixture {
            override suspend fun artistsPage(request: MediaPageRequest) = request.toMediaPage(listOf(fixture.artist))
            override suspend fun artistAlbumsPage(artistId: ArtistId, request: MediaPageRequest): MediaPage<Album> {
                calls += artistId.value
                return request.toMediaPage(listOf(fixture.album))
            }
        }
        val controller = controller(product, access(product, provider))
        open(controller, "Artists"); runCurrent()
        open(controller, fixture.artist.name); runCurrent()
        open(controller, fixture.album.title); runCurrent()
        val pages = controller.state.value.pages
        assertEquals(listOf("Artists", fixture.artist.name, fixture.album.title), pages.map { it.title })
        assertEquals(listOf("Play", "Shuffle", fixture.track.title), pages.last().rows.map { it.title })
        assertEquals(listOf(fixture.artist.id.value), calls)
        assertEquals(before, product.value)
        controller.dismissPages(listOf(pages.last().id))
        assertEquals(fixture.artist.name, controller.state.value.pages.last().title)
        controller.close()
    }

    @Test fun everyTrackRemainsReachableWithRemoteContinuationAndCachedPreviousPages() = runTest {
        val product = product()
        val requests = mutableListOf<MediaPageRequest>()
        val tracks = (0..10).map { fixture.track.copy(id = TrackId("$it"), title = "Song $it") }
        val provider = object : MediaProvider by fixture {
            override suspend fun tracksPage(request: MediaPageRequest): MediaPage<Track> {
                requests += request
                return MediaPage(tracks.drop(request.offset).take(request.limit), request.offset, request.limit,
                    request.offset + request.limit < tracks.size,
                    nextContinuationToken = "next-${request.offset + request.limit}")
            }
        }
        val controller = controller(product, access(product, provider))
        open(controller, "Tracks"); runCurrent()
        val seen = mutableListOf<String>()
        while (true) {
            val page = controller.state.value.pages.single()
            assertTrue(page.rows.size <= 5)
            seen += page.rows.filter { it.id.startsWith("track:") }.map { it.title }
            if (page.rows.none { it.title == "Next" }) break
            open(controller, "Next"); runCurrent()
        }
        assertEquals(tracks.map { it.title }, seen)
        assertEquals(listOf(0, 3, 6, 9), requests.map { it.offset })
        assertEquals(listOf(null, "next-3", "next-6", "next-9"), requests.map { it.continuationToken })
        repeat(3) { open(controller, "Previous"); runCurrent() }
        assertEquals(4, requests.size)
        assertEquals("Song 0", controller.state.value.pages.single().rows.first().title)
        controller.close()
    }

    @Test fun trackSelectionUsesLoadedIdentityAndWaitsForReadyAudio() = runTest {
        val product = product()
        val played = mutableListOf<Track>()
        val controller = controller(product, access(product, play = {
            played += it
            product.value = product.value.copy(playbackSelectionGeneration = 1)
            true
        }))
        open(controller, "Tracks"); runCurrent()
        val state = controller.state.value
        val page = state.pages.single()
        val results = mutableListOf<Boolean>()
        controller.selectPage(page.id, page.rows.single().id, state.revision, results::add)
        runCurrent()
        assertEquals(listOf(fixture.track), played)
        assertTrue(results.isEmpty())
        product.value = product.value.copy(readyPlaybackSelectionGeneration = 1, shell = product.value.shell.copy(
            nowPlaying = NowPlayingUi(id = fixture.track.id.value, title = "Playing", subtitle = "", stateLabel = "Playing", isPlaying = true)))
        runCurrent()
        assertEquals(listOf(true), results)
        assertTrue(controller.state.value.nowPlaying)
        assertTrue(controller.state.value.pages.size + 2 <= 5)
        controller.close()
    }

    @Test fun favoriteCategoriesRespectCapabilitiesAndUseCompletePagedContracts() = runTest {
        val product = product()
        val kinds = mutableListOf<String>()
        val provider = object : MediaProvider by fixture {
            override val capabilities = fixture.capabilities.copy(supportsTrackFavorites = true, supportsArtistFavorites = true, supportsAlbumFavorites = true)
            override suspend fun favoriteArtistsPage(request: MediaPageRequest): MediaPage<Artist> { kinds += "artists"; return request.toMediaPage(listOf(fixture.artist)) }
            override suspend fun favoriteAlbumsPage(request: MediaPageRequest): MediaPage<Album> { kinds += "albums"; return request.toMediaPage(listOf(fixture.album)) }
            override suspend fun favoriteTracksPage(request: MediaPageRequest): MediaPage<Track> { kinds += "tracks"; return request.toMediaPage(listOf(fixture.track)) }
            override suspend fun favoriteTracks(limit: Int): List<Track> = error("Truncated convenience API must not be used")
        }
        for (title in listOf("Artists", "Albums", "Tracks")) {
            val controller = controller(product, access(product, provider), items = 6)
            open(controller, "Favorites"); runCurrent()
            open(controller, title); runCurrent()
            assertTrue(controller.state.value.pages.last().rows.isNotEmpty())
            controller.close()
        }
        assertEquals(listOf("artists", "albums", "tracks"), kinds)
        val unsupported = access(product)
        assertTrue(unsupported.categories().none { it.title == "Favorites" })
    }

    @Test fun unsupportedEnumerationIsDistinctFromEmptyAndFailure() = runTest {
        val product = product()
        val provider = object : MediaProvider by fixture {
            override val capabilities = fixture.capabilities.copy(supportsTrackFavorites = true)
        }
        val controller = controller(product, access(product, provider), items = 6)
        open(controller, "Favorites"); runCurrent()
        open(controller, "Tracks"); runCurrent()
        assertEquals("Unavailable", controller.state.value.pages.last().status)
        assertTrue(controller.state.value.pages.last().rows.isEmpty())
        controller.close()
        val emptyProvider = object : MediaProvider by fixture {
            override suspend fun tracksPage(request: MediaPageRequest) = request.toMediaPage(emptyList<Track>())
        }
        val empty = controller(product, access(product, emptyProvider))
        open(empty, "Tracks"); runCurrent()
        assertEquals("Empty", empty.state.value.pages.single().status)
        empty.close()
    }

    @Test fun laterPageFailureAndTimeoutRetryTheSameRequestWithoutLosingPreviousPage() = runTest {
        for (timeout in listOf(false, true)) {
            val product = product()
            var fail = true
            val provider = object : MediaProvider by fixture {
                override suspend fun tracksPage(request: MediaPageRequest): MediaPage<Track> {
                    if (request.offset > 0 && fail) { if (timeout) awaitCancellation() else error("network") }
                    val tracks = (0..4).map { fixture.track.copy(id = TrackId("$it"), title = "Track $it") }
                    return MediaPage(tracks.drop(request.offset).take(request.limit), request.offset, request.limit, request.offset == 0)
                }
            }
            val controller = controller(product, access(product, provider))
            open(controller, "Tracks"); runCurrent()
            open(controller, "Next"); advanceTimeBy(101); runCurrent()
            assertEquals("Failed", controller.state.value.pages.single().status)
            assertEquals(listOf("Previous", "Retry"), controller.state.value.pages.single().rows.map { it.title })
            fail = false
            open(controller, "Retry"); runCurrent()
            assertEquals(listOf("Previous", "Track 3", "Track 4"), controller.state.value.pages.single().rows.map { it.title })
            open(controller, "Previous"); runCurrent()
            assertEquals("Track 0", controller.state.value.pages.single().rows.first().title)
            controller.close()
        }
    }

    @Test fun sourceBackDetachAndSupersessionRejectDelayedCatalogResultsAndStaleRows() = runTest {
        for (mode in listOf("source", "back", "detach", "supersede")) {
            val product = product()
            val gate = CompletableDeferred<Unit>()
            val provider = object : MediaProvider by fixture {
                override suspend fun tracksPage(request: MediaPageRequest): MediaPage<Track> {
                    withContext(NonCancellable) { gate.await() }
                    return request.toMediaPage(listOf(fixture.track))
                }
            }
            val controller = controller(product, access(product, provider))
            open(controller, "Tracks"); runCurrent()
            val old = controller.state.value
            when (mode) {
                "source" -> product.value = product.value.copy(sourceGeneration = 1)
                "back" -> controller.dismissPages(listOf(old.pages.single().id))
                "detach" -> controller.close()
                else -> {
                    val tab = old.tabs.first { it.destination == NaviampVehicleBrowseDestination.Library }
                    controller.select(tab.destination, tab.rows.first { it.title == "Albums" }.id, old.revision) { assertFalse(it) }
                }
            }
            runCurrent(); gate.complete(Unit); runCurrent()
            if (mode != "supersede") assertTrue(controller.state.value.pages.none { it.rows.any { row -> row.id.startsWith("track:") } })
            else assertEquals("Albums", controller.state.value.pages.last().title)
            controller.selectPage(old.pages.single().id, "track:${fixture.track.id.value}", old.revision) { assertFalse(it) }
            controller.close()
        }
    }

    @Test fun remoteOrDisconnectedSourceDoesNotExposeLocalLibraryActions() = runTest {
        val product = product()
        val controller = controller(product, access(product), tabs = 3)
        val old = controller.state.value
        val library = old.tabs[0].rows.single { it.title == "Library" }
        product.value = product.value.copy(shell = product.value.shell.copy(connect = product.value.shell.connect.copy(remotePlaybackAuthorityActive = true)))
        controller.select(NaviampVehicleBrowseDestination.Home, library.id, old.revision) { assertFalse(it) }
        runCurrent()
        assertTrue(controller.state.value.pages.isEmpty())
        assertTrue(controller.state.value.tabs[0].rows.none { it.title == "Library" })
        controller.close()
    }

    @Test fun malformedNonAdvancingAndRepeatedCatalogPagesFailInsteadOfLoopingOrTruncating() = runTest {
        for (mode in listOf("offset", "limit", "empty", "duplicate", "repeat", "total")) {
            val product = product()
            val provider = object : MediaProvider by fixture {
                override suspend fun tracksPage(request: MediaPageRequest) = when (mode) {
                    "offset" -> MediaPage(listOf(fixture.track), 99, request.limit, false)
                    "limit" -> MediaPage(listOf(fixture.track), request.offset, 1, false)
                    "empty" -> MediaPage(emptyList(), request.offset, request.limit, true)
                    "total" -> MediaPage(listOf(fixture.track), request.offset, request.limit, false, totalItemCount = 100)
                    "duplicate" -> MediaPage(listOf(fixture.track, fixture.track), request.offset, request.limit, false)
                    else -> MediaPage(listOf(fixture.track), request.offset, request.limit, true)
                }
            }
            val controller = controller(product, access(product, provider))
            open(controller, "Tracks"); runCurrent()
            if (mode == "repeat") { open(controller, "Next"); runCurrent() }
            assertEquals("Failed", controller.state.value.pages.single().status, mode)
            controller.close()
        }
    }

    @Test fun overflowNavigationAndAlbumAndQueueKeepSpaceForNowPlayingWithinFiveTemplates() = runTest {
        val product = product()
        product.value = product.value.copy(shell = product.value.shell.copy(nowPlaying = NowPlayingUi(
            id = "current", title = "Current", subtitle = "", stateLabel = "Playing", isPlaying = true)))
        val provider = object : MediaProvider by fixture {
            override suspend fun artistAlbumsPage(artistId: ArtistId, request: MediaPageRequest) = request.toMediaPage(listOf(fixture.album))
        }
        val controller = controller(product, access(product, provider), tabs = 3)
        val state = controller.state.value
        controller.select(NaviampVehicleBrowseDestination.Home, state.tabs[0].rows.first { it.title == "Library" }.id, state.revision) { assertFalse(it) }
        runCurrent(); open(controller, "Artists"); runCurrent(); open(controller, fixture.artist.name); runCurrent()
        open(controller, fixture.album.title); runCurrent()
        assertEquals(3, controller.state.value.pages.size)
        assertEquals("Artists", controller.state.value.pages.first().title)
        controller.openQueue()
        assertEquals(3, controller.state.value.pages.size)
        assertEquals("Queue", controller.state.value.pages.last().title)
        controller.close()
    }

    @Test fun composedLibraryPlaybackRetainsPhoneFiltersAndUsesSharedEngineReadiness() = runTest {
        val defaults = fakeCoreServices(fixture)
        lateinit var observer: NaviampCorePlaybackObserver
        val selections = mutableListOf<List<Track>>()
        val effects = object : NaviampCorePlaybackEffectPort by defaults.playback.effects {
            override fun attach(value: NaviampCorePlaybackObserver) { observer = value }
            override fun playQueueSelection(queue: app.naviamp.domain.queue.PlaybackQueue, index: Int) {
                selections += queue.tracks
                observer.onStateChanged(app.naviamp.domain.playback.PlaybackState.Loading)
            }
        }
        val core = NaviampCore.create(backgroundScope, defaults.copy(playback = defaults.playback.copy(effects = effects)))
        core.stateStore.updateShell { it.copy(connectionSettings = it.connectionSettings.copy(currentSourceId = "source")) }
        val before = core.state.value.shell
        val controller = NaviampVehicleBrowseController(backgroundScope, core.state, core.externalPlaybackBridge(),
            { core.execute(it) }, labels, 100, maximumTabs = 4, collections = core.vehicleCollections,
            library = core.vehicleLibrary(libraryLabels))
        open(controller, "Tracks"); runCurrent()
        assertTrue(selections.isEmpty())
        assertEquals(before.library, core.state.value.shell.library)
        assertEquals(before.shellChrome, core.state.value.shell.shellChrome)
        val state = controller.state.value
        val results = mutableListOf<Boolean>()
        controller.selectPage(state.pages.single().id, state.pages.single().rows.single().id, state.revision, results::add)
        runCurrent()
        assertEquals(listOf(listOf(fixture.track)), selections)
        assertTrue(results.isEmpty())
        observer.onStateChanged(app.naviamp.domain.playback.PlaybackState.Playing)
        runCurrent()
        assertEquals(listOf(true), results)
        assertEquals(before.library, core.state.value.shell.library)
        controller.close()
        core.close()
    }

    @Test fun largeAlbumsEnumerateEveryProviderPageAndRejectStalePlayableSnapshots() = runTest {
        val product = product()
        val tracks = (0..204).map { fixture.track.copy(id = TrackId("$it"), trackNumber = it + 1) }
        val requests = mutableListOf<Int>()
        val provider = object : MediaProvider by fixture {
            override suspend fun albumTracksPage(albumId: AlbumId, request: MediaPageRequest): MediaPage<Track> {
                requests += request.offset
                return MediaPage(tracks.drop(request.offset).take(request.limit), request.offset, request.limit, request.offset + request.limit < tracks.size)
            }
            override suspend fun album(albumId: AlbumId): AlbumDetails = error("Truncated album-detail API used")
        }
        // Use the composed Core's existing collection/playback owner rather than a platform controller.
        val core = NaviampCore.create(backgroundScope, fakeCoreServices(provider))
        val loaded = core.vehicleCollections.load(NaviampVehicleCollectionReference(NaviampVehicleCollectionKind.Album, fixture.album.id.value, fixture.album.title))
        assertEquals(tracks, loaded.tracks)
        assertEquals(listOf(0, 50, 100, 150, 200), requests)
        core.stateStore.update { it.copy(sourceGeneration = 1) }
        assertFalse(loaded.play())
        core.close()
    }
}
