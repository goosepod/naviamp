package app.naviamp.presentation

import app.naviamp.domain.*
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampVehicleDownloadsTest {
    private val labels = NaviampVehicleBrowseLabels("Home", "Playlists", "Radio", "Empty", "Loading", "Disconnected", "Failed", "Next", "Previous", "Queue", "Play", "Shuffle", "Retry")
    private val libraryLabels = NaviampVehicleLibraryLabels("Library", "Artists", "Albums", "Tracks", "Favorites", "Unavailable")
    private val downloadLabels = NaviampVehicleDownloadsLabels("Downloads", "Refresh", "Play", "Shuffle", "Empty")
    private val fixture = FakeCoreMediaProvider()
    private val tracks = (0..8).map { fixture.track.copy(id = TrackId("$it"), title = "Track $it", trackNumber = it + 1) }
    private fun records(tracks: List<Track>) = tracks.map { NaviampCoreDownloadedTrack(it.id.value, it, 100) }
    private fun product() = MutableStateFlow(NaviampCoreState(shell = NaviampAppShellUiState(connectionSettings = NaviampConnectionSettingsUi(currentSourceId = "source"))))
    private class Storage(var rows: List<NaviampCoreDownloadedTrack>) : NaviampCoreDownloadStoragePort {
        var reads = 0
        var gate: CompletableDeferred<Unit>? = null
        var failure = false
        val sources = mutableListOf<String>()
        override suspend fun availableDownloads(sourceId: String): List<NaviampCoreDownloadedTrack> {
            sources += sourceId; reads++
            gate?.let { withContext(NonCancellable) { it.await() } }
            if (failure) error("local storage failure")
            return rows
        }
        override suspend fun snapshot(sourceId: String): NaviampCoreDownloadStorageSnapshot = error("Unverified snapshot used")
        override suspend fun pruneMissing(sourceId: String): Int = error("Browsing pruned files")
        override suspend fun remove(sourceId: String, track: Track) = error("Browsing deleted files")
        override suspend fun deleteAll(sourceId: String): Int = error("Browsing deleted downloads")
    }
    private fun TestScope.controller(product: MutableStateFlow<NaviampCoreState>, storage: NaviampCoreDownloadStoragePort, tabs: Int = 5, items: Int = 8,
        capable: () -> Boolean = { true }, play: (List<Track>, Int, Boolean) -> Boolean = { _, _, _ -> error("Browsing started playback") }) =
        NaviampVehicleBrowseController(backgroundScope, product, NaviampCoreExternalPlaybackBridge(product, {}), {}, labels, items,
            readinessTimeoutMillis = 100, maximumTabs = tabs,
            library = NaviampCoreVehicleLibraryAccess(product, NaviampCoreMediaProviderSource { null }, { error("Provider playback used") }, libraryLabels),
            downloads = NaviampVehicleDownloadsAccess(product, storage, capable, { tracks, index, shuffle, _ -> play(tracks, index, shuffle) }, libraryLabels, downloadLabels))
    private fun open(controller: NaviampVehicleBrowseController, title: String) {
        val state = controller.state.value
        val page = state.pages.lastOrNull()
        if (page != null) controller.selectPage(page.id, page.rows.first { it.title == title }.id, state.revision) { assertFalse(it) }
        else {
            val tab = state.tabs.first { it.destination == NaviampVehicleBrowseDestination.Downloads }
            controller.select(tab.destination, tab.rows.first { it.title == title }.id, state.revision) { assertFalse(it) }
        }
    }

    @Test fun downloadsAreReachableOfflineFromFifthTabOrHomeAndLibraryWithoutProvider() = runTest {
        for (tabs in listOf(3, 4, 5)) {
            val product = product()
            product.value = product.value.copy(shell = product.value.shell.copy(connectionSettings = product.value.shell.connectionSettings.copy(
                connection = product.value.shell.connectionSettings.connection.copy(statusIsError = true))))
            val storage = Storage(records(tracks))
            val controller = controller(product, storage, tabs)
            if (tabs == 5) {
                assertEquals("Downloads", controller.state.value.tabs.last().title)
                assertEquals("", controller.state.value.tabs.last().status)
                open(controller, "Tracks")
            } else {
                val state = controller.state.value
                val row = state.tabs[0].rows.single { it.title == "Downloads" }
                controller.select(NaviampVehicleBrowseDestination.Home, row.id, state.revision) { assertFalse(it) }
                runCurrent()
                assertEquals(listOf("Artists", "Albums", "Tracks"), controller.state.value.pages.single().rows.map { it.title })
                open(controller, "Tracks")
            }
            runCurrent()
            assertTrue(controller.state.value.pages.last().rows.any { it.title == "Track 0" })
            assertEquals(listOf("source"), storage.sources)
            if (tabs >= 4) assertTrue(controller.state.value.tabs.first { it.destination == NaviampVehicleBrowseDestination.Library }.rows.any { it.title == "Downloads" })
            controller.close()
        }
    }

    @Test fun artistAlbumNavigationShowsOnlyAvailableLocalTracksAndAlbumOrder() = runTest {
        val product = product()
        val local = listOf(tracks[5], tracks[0], tracks[2])
        val controller = controller(product, Storage(records(local)), items = 100)
        open(controller, "Artists"); runCurrent()
        open(controller, fixture.artist.name); runCurrent()
        open(controller, fixture.album.title); runCurrent()
        val page = controller.state.value.pages.last()
        assertEquals(listOf("Refresh", "Play", "Shuffle", "Track 0", "Track 2", "Track 5"), page.rows.map { it.title })
        assertEquals("Downloads", page.status)
        assertTrue(controller.state.value.pages.size <= 3)
        controller.close()
    }

    @Test fun trackPagingReachesEveryLocalTrackWithinBudgetAndRefreshRechecksFiles() = runTest {
        val product = product()
        val storage = Storage(records(tracks))
        val controller = controller(product, storage, items = 6)
        open(controller, "Tracks"); runCurrent()
        val seen = mutableListOf<String>()
        while (true) {
            val page = controller.state.value.pages.last()
            assertTrue(page.rows.size <= 6)
            seen += page.rows.filter { it.id.startsWith("download-track:") }.map { it.title }
            if (page.rows.none { it.title == "Next" }) break
            open(controller, "Next"); runCurrent()
        }
        assertEquals(tracks.map { it.title }, seen)
        storage.rows = records(listOf(tracks[7]))
        open(controller, "Refresh"); runCurrent()
        assertEquals(listOf("Track 7"), controller.state.value.pages.last().rows.filter { it.id.startsWith("download-track:") }.map { it.title })
        controller.close()
    }

    @Test fun localPlayShuffleAndTrackSelectionRevalidateThenWaitForFreshEngineReadiness() = runTest {
        for (action in listOf("Play", "Shuffle", "Track 2")) {
            val product = product()
            val selections = mutableListOf<Triple<List<Track>, Int, Boolean>>()
            val storage = Storage(records(tracks.take(4)))
            val controller = controller(product, storage, items = 100, play = { tracks, index, shuffle ->
                selections += Triple(tracks, index, shuffle)
                product.value = product.value.copy(playbackSelectionGeneration = 1)
                true
            })
            open(controller, "Tracks"); runCurrent()
            val state = controller.state.value
            val page = state.pages.single()
            val completions = mutableListOf<Boolean>()
            controller.selectPage(page.id, page.rows.first { it.title == action }.id, state.revision, completions::add)
            runCurrent()
            assertEquals(tracks.take(4), selections.single().first)
            assertEquals(if (action == "Track 2") 2 else 0, selections.single().second)
            assertEquals(action == "Shuffle", selections.single().third)
            assertEquals(2, storage.reads)
            assertTrue(completions.isEmpty())
            product.value = product.value.copy(readyPlaybackSelectionGeneration = 1, shell = product.value.shell.copy(nowPlaying = NowPlayingUi(
                id = "ready", title = "Ready", subtitle = "", stateLabel = "Playing", isPlaying = true)))
            runCurrent()
            assertEquals(listOf(true), completions)
            assertTrue(controller.state.value.nowPlaying)
            controller.close()
        }
    }

    @Test fun disappearingFileOrNewOutputCapabilityRejectsOldSelectionWithoutStartingAudio() = runTest {
        for (mode in listOf("file", "output")) {
            val product = product()
            var capable = true
            val storage = Storage(records(tracks))
            val controller = controller(product, storage, items = 100, capable = { capable })
            open(controller, "Tracks"); runCurrent()
            val state = controller.state.value
            val row = state.pages.single().rows.first { it.title == "Track 0" }
            if (mode == "file") storage.rows = records(tracks.drop(1)) else capable = false
            val completions = mutableListOf<Boolean>()
            controller.selectPage(state.pages.single().id, row.id, state.revision, completions::add)
            runCurrent()
            assertEquals(listOf(false), completions)
            controller.close()
        }
    }

    @Test fun sourceAccountBackAndDetachRejectLateStorageResults() = runTest {
        for (mode in listOf("source", "account", "back", "detach")) {
            val product = product()
            val gate = CompletableDeferred<Unit>()
            val storage = Storage(records(tracks)).also { it.gate = gate }
            val controller = controller(product, storage)
            open(controller, "Tracks"); runCurrent()
            val old = controller.state.value
            when (mode) {
                "source" -> product.value = product.value.copy(sourceGeneration = 1)
                "account" -> product.value = product.value.copy(shell = product.value.shell.copy(connectionSettings = product.value.shell.connectionSettings.copy(accountSelectionGeneration = 1)))
                "back" -> controller.dismissPages(listOf(old.pages.single().id))
                else -> controller.close()
            }
            runCurrent(); gate.complete(Unit); runCurrent()
            assertTrue(controller.state.value.pages.none { it.rows.any { row -> row.id.startsWith("download-track:") } })
            controller.selectPage(old.pages.single().id, "download-track:0", old.revision) { assertFalse(it) }
            controller.close()
        }
    }

    @Test fun emptyZeroByteDuplicateAndFailedSnapshotsRemainHonestAndRetryable() = runTest {
        val product = product()
        val storage = Storage(records(listOf(tracks[0], tracks[0])) + NaviampCoreDownloadedTrack("zero", tracks[1], 0))
        val controller = controller(product, storage, items = 100)
        open(controller, "Tracks"); runCurrent()
        assertEquals(listOf("Track 0"), controller.state.value.pages.single().rows.filter { it.id.startsWith("download-track:") }.map { it.title })
        storage.rows = emptyList()
        open(controller, "Refresh"); runCurrent()
        assertEquals("Empty", controller.state.value.pages.single().status)
        assertEquals(listOf("Refresh"), controller.state.value.pages.single().rows.map { it.title })
        storage.failure = true
        open(controller, "Refresh"); runCurrent()
        assertEquals("Failed", controller.state.value.pages.single().status)
        storage.failure = false
        storage.rows = records(listOf(tracks[3]))
        open(controller, "Retry"); runCurrent()
        assertTrue(controller.state.value.pages.single().rows.any { it.title == "Track 3" })
        controller.close()
    }

    @Test fun remoteAuthorityNoSourceAndUnsupportedLocalEngineHideDownloads() = runTest {
        for (mode in listOf("remote", "source", "capability", "small")) {
            val product = product()
            if (mode == "remote") product.value = product.value.copy(shell = product.value.shell.copy(connect = product.value.shell.connect.copy(remotePlaybackAuthorityActive = true)))
            if (mode == "source") product.value = product.value.copy(shell = product.value.shell.copy(connectionSettings = NaviampConnectionSettingsUi()))
            val controller = controller(product, Storage(records(tracks)), tabs = 4, items = if (mode == "small") 4 else 100, capable = { mode != "capability" })
            assertTrue(controller.state.value.tabs.all { it.rows.none { it.title == "Downloads" } })
            controller.close()
        }
    }

    @Test fun unverifiableDownloadRecordsAreUnavailableRatherThanPhantomLocalMedia() = runTest {
        val product = product()
        val storage = object : NaviampCoreDownloadStoragePort {
            override suspend fun snapshot(sourceId: String) = NaviampCoreDownloadStorageSnapshot(records(tracks))
            override suspend fun pruneMissing(sourceId: String): Int = error("Pruned records")
            override suspend fun remove(sourceId: String, track: Track) = error("Removed file")
            override suspend fun deleteAll(sourceId: String): Int = error("Deleted files")
        }
        val controller = controller(product, storage)
        open(controller, "Tracks"); runCurrent()
        assertEquals("Unavailable", controller.state.value.pages.single().status)
        assertTrue(controller.state.value.pages.single().rows.isEmpty())
        controller.close()
    }

    @Test fun composedCoreUsesLocalOnlyExecutionAndPreservesPhoneStateWithNoProvider() = runTest {
        val storage = Storage(records(tracks.take(3)))
        val defaults = fakeCoreServices(null)
        lateinit var observer: NaviampCorePlaybackObserver
        val selected = mutableListOf<PlaybackQueue>()
        var ordinary = 0
        var downloadedOnly = false
        val effects = object : NaviampCorePlaybackEffectPort by defaults.playback.effects {
            override val capabilities = defaults.playback.effects.capabilities.copy(supportsDownloadedOnly = true)
            override val downloadedOnlyQueue: Boolean get() = downloadedOnly
            override fun attach(value: NaviampCorePlaybackObserver) { observer = value }
            override fun playQueueSelection(queue: PlaybackQueue, index: Int) { ordinary++ }
            override fun playDownloadedQueueSelection(queue: PlaybackQueue, index: Int) {
                downloadedOnly = true
                selected += queue
                observer.onStateChanged(PlaybackState.Loading)
            }
        }
        val core = NaviampCore.create(backgroundScope, defaults.copy(playback = defaults.playback.copy(effects = effects), downloads = defaults.downloads.copy(storage = storage)))
        core.stateStore.updateShell { it.copy(connectionSettings = it.connectionSettings.copy(currentSourceId = "source")) }
        val before = core.state.value.shell
        val controller = NaviampVehicleBrowseController(backgroundScope, core.state, core.externalPlaybackBridge(), { core.execute(it) },
            labels, 100, maximumTabs = 5, downloads = core.vehicleDownloads(libraryLabels, downloadLabels))
        open(controller, "Tracks"); runCurrent()
        val state = controller.state.value
        val completions = mutableListOf<Boolean>()
        controller.selectPage(state.pages.single().id, state.pages.single().rows.first { it.title == "Track 1" }.id, state.revision, completions::add)
        runCurrent()
        assertEquals(tracks.take(3), selected.single().tracks)
        assertEquals(1, selected.single().currentIndex)
        assertEquals(0, ordinary)
        assertEquals(before.downloads, core.state.value.shell.downloads)
        assertEquals(before.library, core.state.value.shell.library)
        assertTrue(completions.isEmpty())
        observer.onStateChanged(PlaybackState.Playing)
        runCurrent()
        assertEquals(listOf(true), completions)
        controller.dismissPages(controller.state.value.pages.map { it.id })
        assertTrue(controller.state.value.currentAlbumAvailable)
        controller.openCurrentAlbum(controller.state.value.revision)
        runCurrent()
        assertEquals("Downloads", controller.state.value.pages.last().status, "Now Playing must use the local album view without a provider")
        val albumPageId = controller.state.value.pages.last().id
        controller.openCurrentAlbum(controller.state.value.revision)
        runCurrent()
        assertEquals(albumPageId, controller.state.value.pages.single().id, "Reopening the current downloaded album must reuse its page")
        val album = controller.state.value
        controller.selectPage(album.pages.last().id, album.pages.last().rows.first { it.title == "Play" }.id, album.revision) {}
        runCurrent()
        val group = selected.last().groups.single()
        assertEquals(app.naviamp.domain.playback.PlaybackProfileTargetType.Album, group.target.type)
        assertEquals(fixture.album.id.value, group.target.id)
        controller.close(); core.close()
    }
}
