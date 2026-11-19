package app.naviamp.presentation

import app.naviamp.domain.*
import app.naviamp.domain.provider.*
import app.naviamp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampVehicleSearchTest {
    private val labels = NaviampVehicleBrowseLabels("Home", "Playlists", "Radio", "Empty", "Loading", "Disconnected", "Failed", "Next", "Previous", "Queue", "Play", "Shuffle", "Retry")
    private val libraryLabels = NaviampVehicleLibraryLabels("Library", "Artists", "Albums", "Tracks", "Favorites", "Unavailable")
    private val searchLabels = NaviampVehicleSearchLabels("Search", "Search music", "No matches")
    private val fixture = FakeCoreMediaProvider()
    private fun product() = MutableStateFlow(NaviampCoreState(shell = NaviampAppShellUiState(connectionSettings = NaviampConnectionSettingsUi(currentSourceId = "source"))))
    private fun provider(load: suspend (String, MediaPageRequest, String) -> MediaPage<Track> = { _, r, _ -> r.toMediaPage(listOf(fixture.track)) }) = object : MediaProvider by fixture {
        override suspend fun searchTracksPage(query: String, request: MediaPageRequest) = load(query, request, "tracks")
        override suspend fun searchArtistsPage(query: String, request: MediaPageRequest): MediaPage<Artist> {
            val page = load(query, request, "artists")
            return MediaPage(page.items.map { fixture.artist.copy(id = ArtistId(it.id.value)) }, page.offset, page.limit, page.hasMore, page.nextContinuationToken)
        }
        override suspend fun searchAlbumsPage(query: String, request: MediaPageRequest): MediaPage<Album> {
            val page = load(query, request, "albums")
            return MediaPage(page.items.map { fixture.album.copy(id = AlbumId(it.id.value)) }, page.offset, page.limit, page.hasMore, page.nextContinuationToken)
        }
        override suspend fun tracksPage(request: MediaPageRequest): MediaPage<Track> = error("Search used library browsing instead of the provider search API")
    }
    private fun TestScope.controller(product: MutableStateFlow<NaviampCoreState>, provider: MediaProvider = provider(),
        supported: Boolean = true, items: Int = 20, play: (Track) -> Boolean = { error("Search played audio") }) =
        NaviampVehicleBrowseController(backgroundScope, product, NaviampCoreExternalPlaybackBridge(product, {}), {}, labels,
            items, readinessTimeoutMillis = 1000, maximumTabs = 4,
            collections = NaviampVehicleCollectionAccess { NaviampVehicleCollection(listOf(fixture.track)) { _, _ -> true } },
            library = NaviampCoreVehicleLibraryAccess(product, NaviampCoreMediaProviderSource { provider }, play, libraryLabels),
            searchLabels = searchLabels.takeIf { supported })
    private fun open(controller: NaviampVehicleBrowseController): String {
        val state = controller.state.value
        controller.select(NaviampVehicleBrowseDestination.Home, state.tabs.first().rows.single { it.title == "Search" }.id, state.revision) { assertFalse(it) }
        return controller.state.value.pages.single().also { assertTrue(it.search) }.id
    }

    @Test fun searchEntryRequiresHostCapabilitySourceAndAdequateItemBudget() = runTest {
        for ((supported, items) in listOf(false to 20, true to 3, true to 20)) {
            val product = product()
            val controller = controller(product, supported = supported, items = items)
            assertEquals(supported && items >= 4, controller.state.value.tabs.first().rows.any { it.title == "Search" })
            if (supported && items >= 4) {
                product.value = product.value.copy(shell = product.value.shell.copy(connect = product.value.shell.connect.copy(remotePlaybackAuthorityActive = true)))
                runCurrent()
                assertTrue(controller.state.value.tabs.first().rows.none { it.title == "Search" })
            }
            controller.close()
        }
    }

    @Test fun whitespaceCompletesWithoutNetworkAndValidQueryUsesDebouncedProviderSearchForAllKinds() = runTest {
        val product = product()
        val calls = mutableListOf<Pair<String, String>>()
        val controller = controller(product, provider { query, request, kind -> calls += query to kind; request.toMediaPage(listOf(fixture.track)) })
        val before = product.value
        val pageId = open(controller)
        val results = mutableListOf<NaviampVehicleSearchResults>()
        controller.updateSearch(pageId, "   ", results::add)
        assertEquals("Search music", results.single().rows.single().title)
        assertFalse(results.single().rows.single().enabled)
        controller.updateSearch(pageId, "  unique query  ", results::add)
        advanceTimeBy(249); runCurrent()
        assertTrue(calls.isEmpty())
        advanceTimeBy(2); runCurrent()
        assertEquals(setOf("tracks", "albums", "artists"), calls.map { it.second }.toSet())
        assertTrue(calls.all { it.first == "unique query" })
        assertEquals(2, results.size)
        assertTrue(results.last().rows.any { it.title == fixture.track.title })
        assertTrue(results.last().rows.any { it.title == fixture.album.title })
        assertTrue(results.last().rows.any { it.title == fixture.artist.name })
        assertEquals(before, product.value)
        controller.close()
    }

    @Test fun replacingOrClearingQueryCompletesOldCallbackImmediatelyEvenIfProviderIgnoresCancellation() = runTest {
        for (clear in listOf(false, true)) {
            val product = product()
            val gate = CompletableDeferred<Unit>()
            val controller = controller(product, provider { query, request, _ ->
                if (query == "old") withContext(NonCancellable) { gate.await() }
                request.toMediaPage(listOf(fixture.track.copy(title = query)))
            })
            val id = open(controller)
            val first = mutableListOf<NaviampVehicleSearchResults>()
            val second = mutableListOf<NaviampVehicleSearchResults>()
            controller.updateSearch(id, "old", first::add)
            advanceTimeBy(251); runCurrent()
            assertTrue(first.isEmpty())
            controller.updateSearch(id, if (clear) "" else "new", second::add)
            assertEquals(1, first.size)
            assertTrue(first.single().rows.isEmpty())
            advanceTimeBy(251); runCurrent()
            assertEquals(1, second.size)
            gate.complete(Unit); runCurrent()
            assertEquals(1, first.size)
            assertEquals(1, second.size)
            if (!clear) assertTrue(second.single().rows.any { it.title == "new" })
            else assertEquals("Search music", controller.state.value.pages.single().rows.single().title)
            controller.close()
        }
    }

    @Test fun sourceBackRootFailureAndDetachFinishCallbacksExactlyOnceAndRejectLateResults() = runTest {
        for (mode in listOf("source", "back", "root", "detach")) {
            val product = product()
            val gate = CompletableDeferred<Unit>()
            val controller = controller(product, provider { _, request, _ ->
                withContext(NonCancellable) { gate.await() }
                request.toMediaPage(listOf(fixture.track))
            })
            val id = open(controller)
            val results = mutableListOf<NaviampVehicleSearchResults>()
            controller.updateSearch(id, "query", results::add)
            advanceTimeBy(251); runCurrent()
            when (mode) {
                "source" -> product.value = product.value.copy(sourceGeneration = 1)
                "back" -> controller.dismissPages(listOf(id))
                "root" -> controller.rootPresentationFailed()
                else -> controller.close()
            }
            runCurrent()
            assertEquals(1, results.size, mode)
            gate.complete(Unit); runCurrent()
            assertEquals(1, results.size, mode)
            assertTrue(results.single().rows.isEmpty())
            controller.close()
        }
    }

    @Test fun emptyPartialFailureAndTimeoutAreTranslatedAndStillOfferFullSearchCategories() = runTest {
        for (mode in listOf("empty", "partial", "timeout")) {
            val product = product()
            val controller = controller(product, provider { _, request, kind ->
                when {
                    mode == "empty" -> request.toMediaPage(emptyList())
                    mode == "timeout" -> awaitCancellation()
                    kind == "artists" -> error("Provider secret")
                    else -> request.toMediaPage(listOf(fixture.track))
                }
            })
            val results = mutableListOf<NaviampVehicleSearchResults>()
            controller.updateSearch(open(controller), "query", results::add)
            advanceTimeBy(1300); runCurrent()
            val rows = results.single().rows
            assertEquals(if (mode == "empty") "No matches" else "Failed", rows.first().title)
            assertFalse(rows.first().enabled)
            assertEquals(listOf("Tracks", "Albums", "Artists"), rows.filter { it.id.startsWith("search-category:") }.map { it.title })
            assertTrue(rows.none { it.title.contains("secret") })
            controller.close()
        }
    }

    @Test fun fullCategorySearchPreservesQueryAndContinuationAndEveryResultIsReachable() = runTest {
        val product = product()
        val calls = mutableListOf<Triple<String, Int, String?>>()
        val tracks = (0..9).map { fixture.track.copy(id = TrackId("$it"), title = "Track $it") }
        val controller = controller(product, provider { query, request, kind ->
            if (kind == "tracks") calls += Triple(query, request.offset, request.continuationToken)
            MediaPage(tracks.drop(request.offset).take(request.limit), request.offset, request.limit,
                request.offset + request.limit < tracks.size, "token-${request.offset + request.limit}")
        }, items = 6)
        val id = open(controller)
        controller.updateSearch(id, "query", {})
        advanceTimeBy(251); runCurrent()
        val current = controller.state.value
        controller.selectPage(id, current.pages.single().rows.first { it.title == "Tracks" }.id, current.revision) { assertFalse(it) }
        runCurrent()
        val seen = mutableListOf<String>()
        while (true) {
            val state = controller.state.value
            val page = state.pages.last()
            seen += page.rows.filter { it.id.startsWith("track:") }.map { it.title }
            assertTrue(page.rows.size <= 6)
            val next = page.rows.firstOrNull { it.title == "Next" } ?: break
            controller.selectPage(page.id, next.id, state.revision) { assertFalse(it) }
            runCurrent()
        }
        assertEquals(tracks.map { it.title }, seen)
        assertEquals(listOf(0, 0, 4, 8), calls.map { it.second })
        assertTrue(calls.all { it.first == "query" })
        assertEquals("token-4", calls[2].third)
        controller.close()
    }

    @Test fun previewsNavigateArtistsAndAlbumsAndTrackPlaybackWaitsForEngineReadiness() = runTest {
        for (kind in listOf("artist:", "album:", "track:")) {
            val product = product()
            val selections = mutableListOf<Track>()
            val controller = controller(product, play = { selections += it; product.value = product.value.copy(playbackSelectionGeneration = 1); true })
            val id = open(controller)
            controller.updateSearch(id, "query", {})
            advanceTimeBy(251); runCurrent()
            val state = controller.state.value
            val row = state.pages.single().rows.first { it.id.startsWith(kind) }
            val results = mutableListOf<Boolean>()
            controller.selectPage(id, row.id, state.revision, results::add)
            runCurrent()
            if (kind == "track:") {
                assertEquals(listOf(fixture.track), selections)
                assertTrue(results.isEmpty())
                product.value = product.value.copy(readyPlaybackSelectionGeneration = 1, shell = product.value.shell.copy(
                    nowPlaying = NowPlayingUi(id = fixture.track.id.value, title = "Playing", subtitle = "", stateLabel = "Playing", isPlaying = true)))
                runCurrent()
                assertEquals(listOf(true), results)
            } else {
                assertEquals(listOf(false), results)
                assertEquals(if (kind == "album:") fixture.album.title else fixture.artist.name, controller.state.value.pages.last().title)
            }
            controller.close()
        }
    }

    @Test fun keyboardSubmitWaitsForCurrentQueryThenCreatesBoundedResultsAndRejectsOldPreviewHandlers() = runTest {
        val product = product()
        val controller = controller(product)
        val id = open(controller)
        controller.updateSearch(id, "query", {})
        controller.submitSearch(id)
        assertEquals(1, controller.state.value.pages.size)
        advanceTimeBy(251); runCurrent()
        val state = controller.state.value
        assertEquals(2, state.pages.size)
        assertFalse(state.pages.last().search)
        assertTrue(state.pages.last().rows.any { it.id.startsWith("track:") })
        controller.dismissPages(listOf(state.pages.last().id))
        controller.updateSearch(id, "changed", {})
        controller.selectPage(id, state.pages.last().rows.first().id, state.revision) { assertFalse(it) }
        controller.close()
    }

    @Test fun deadlineCompletesCallbackEvenWhenProviderIgnoresCancellation() = runTest {
        val product = product()
        val gate = CompletableDeferred<Unit>()
        val controller = controller(product, provider { _, request, _ ->
            withContext(NonCancellable) { gate.await() }
            request.toMediaPage(listOf(fixture.track))
        })
        val results = mutableListOf<NaviampVehicleSearchResults>()
        controller.updateSearch(open(controller), "query", results::add)
        advanceTimeBy(1300); runCurrent()
        assertEquals("Failed", results.single().rows.first().title)
        gate.complete(Unit); runCurrent()
        assertEquals(1, results.size)
        assertEquals("Failed", controller.state.value.pages.single().status)
        controller.close()
    }

    @Test fun querySelectionSurvivesUnrelatedPlaybackUpdatesButRejectsOlderQueries() = runTest {
        val product = product()
        val played = mutableListOf<Track>()
        val controller = controller(product, play = { played += it; product.value = product.value.copy(playbackSelectionGeneration = 1); true })
        val id = open(controller)
        val results = mutableListOf<NaviampVehicleSearchResults>()
        controller.updateSearch(id, "query", results::add)
        advanceTimeBy(251); runCurrent()
        val original = results.single()
        val row = original.rows.first { it.id.startsWith("track:") }
        product.value = product.value.copy(shell = product.value.shell.copy(nowPlaying = NowPlayingUi(
            id = "other", title = "Other", subtitle = "", stateLabel = "Playing", isPlaying = true)))
        runCurrent()
        controller.selectSearch(id, row.id, original.revision) {}
        runCurrent()
        assertEquals(listOf(fixture.track), played)
        controller.updateSearch(id, "new query", {})
        controller.selectSearch(id, row.id, original.revision) { assertFalse(it) }
        assertEquals(1, played.size)
        controller.close()
    }

    @Test fun previewBudgetsAndDisabledStatusRowsCannotSelectOrPlay() = runTest {
        for (limit in listOf(4, 6, 20, 100)) {
            val product = product()
            val controller = controller(product, items = limit)
            val id = open(controller)
            val blank = controller.state.value
            controller.selectPage(id, blank.pages.single().rows.single().id, blank.revision) { assertFalse(it) }
            val results = mutableListOf<NaviampVehicleSearchResults>()
            controller.updateSearch(id, "query", results::add)
            advanceTimeBy(251); runCurrent()
            assertTrue(results.single().rows.size <= minOf(limit, 20))
            assertTrue(results.single().rows.count { it.id.startsWith("search-category:") } == 3)
            controller.close()
        }
    }
}
