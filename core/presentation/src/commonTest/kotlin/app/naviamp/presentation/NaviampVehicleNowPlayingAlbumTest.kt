package app.naviamp.presentation

import app.naviamp.domain.*
import app.naviamp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampVehicleNowPlayingAlbumTest {
    private val labels = NaviampVehicleBrowseLabels("Home", "Playlists", "Radio", "Empty", "Loading", "Disconnected", "Failed",
        "Next", "Previous", "Queue", "Play", "Shuffle", "Retry")
    private val fixture = FakeCoreMediaProvider()
    private fun product() = MutableStateFlow(NaviampCoreState(shell = NaviampAppShellUiState(
        connectionSettings = NaviampConnectionSettingsUi(currentSourceId = "source"),
        nowPlaying = NowPlayingUi(id = "current", title = "Current", subtitle = "Artist", stateLabel = "Playing",
            albumId = "not-in-recents", albumTitle = "Current Album", isPlaying = true),
    )))
    private fun TestScope.controller(product: MutableStateFlow<NaviampCoreState>,
        load: suspend (NaviampVehicleCollectionReference) -> NaviampVehicleCollection = {
            NaviampVehicleCollection(listOf(fixture.track)) { _, _ -> error("Browsing started playback") }
        }) = NaviampVehicleBrowseController(backgroundScope, product, NaviampCoreExternalPlaybackBridge(product, {}),
            { error("Album browsing dispatched a phone command") }, labels, 100, readinessTimeoutMillis = 100,
            collections = NaviampVehicleCollectionAccess(load))
    private fun MutableStateFlow<NaviampCoreState>.playing(change: (NowPlayingUi) -> NowPlayingUi) {
        value = value.copy(shell = value.shell.copy(nowPlaying = change(value.shell.nowPlaying!!)))
    }

    @Test fun opensCurrentAlbumOutsideRecentsWithoutChangingPlaybackOrPhoneNavigation() = runTest {
        val product = product()
        val before = product.value
        val requests = mutableListOf<NaviampVehicleCollectionReference>()
        val controller = controller(product) {
            requests += it
            NaviampVehicleCollection(listOf(fixture.track)) { _, _ -> error("Browse played audio") }
        }
        assertTrue(controller.state.value.currentAlbumAvailable)
        assertTrue(controller.state.value.tabs[0].rows.none { it.title == "Current Album" })
        controller.openCurrentAlbum(controller.state.value.revision)
        assertEquals("Loading", controller.state.value.pages.single().status)
        runCurrent()
        assertEquals(listOf(NaviampVehicleCollectionReference(NaviampVehicleCollectionKind.Album, "not-in-recents", "Current Album")), requests)
        val page = controller.state.value.pages.single()
        assertEquals("Current Album", page.title)
        assertEquals(listOf("Play", "Shuffle", fixture.track.title), page.rows.map { it.title })
        assertFalse(controller.state.value.nowPlaying)
        assertEquals(before, product.value)
        controller.close()
    }

    @Test fun contextCapabilityExcludesMissingMetadataLiveRemoteAndUnavailableSource() = runTest {
        for (mode in listOf("idle", "album-id", "album-title", "live", "remote", "source", "connecting", "restoring", "root-failed", "closed")) {
            val product = product()
            val controller = controller(product)
            when (mode) {
                "idle" -> product.playing { it.copy(id = "") }
                "album-id" -> product.playing { it.copy(albumId = " ") }
                "album-title" -> product.playing { it.copy(albumTitle = " ") }
                "live" -> product.playing { it.copy(isLive = true) }
                "remote" -> product.value = product.value.copy(shell = product.value.shell.copy(connect = product.value.shell.connect.copy(remotePlaybackAuthorityActive = true)))
                "source" -> product.value = product.value.copy(shell = product.value.shell.copy(connectionSettings = NaviampConnectionSettingsUi()))
                "connecting", "restoring" -> product.value = product.value.copy(shell = product.value.shell.copy(connectionSettings =
                    product.value.shell.connectionSettings.copy(connection = product.value.shell.connectionSettings.connection.copy(
                        isConnecting = mode == "connecting", restoringConnection = mode == "restoring"))))
                "root-failed" -> controller.rootPresentationFailed()
                else -> controller.close()
            }
            runCurrent()
            controller.openCurrentAlbum(controller.state.value.revision)
            runCurrent()
            assertTrue(controller.state.value.pages.isEmpty(), mode)
            if (mode != "closed") assertFalse(controller.state.value.currentAlbumAvailable, mode)
            controller.close()
        }
    }

    @Test fun staleContextIsRejectedAfterTrackAlbumAndSourceChangesButProgressDoesNotInvalidate() = runTest {
        for (mode in listOf("track", "album", "source")) {
            val product = product()
            val requests = mutableListOf<NaviampVehicleCollectionReference>()
            val controller = controller(product) { requests += it; NaviampVehicleCollection(emptyList()) { _, _ -> false } }
            runCurrent()
            val before = controller.state.value
            product.playing { it.copy(positionSeconds = 45.0) }
            runCurrent()
            assertEquals(before, controller.state.value)
            when (mode) {
                "track" -> product.playing { it.copy(id = "next", title = "Next") }
                "album" -> product.playing { it.copy(albumId = "other-album", albumTitle = "Other Album") }
                else -> product.value = product.value.copy(sourceGeneration = 1)
            }
            controller.openCurrentAlbum(before.revision)
            runCurrent()
            assertTrue(requests.isEmpty(), mode)
            assertTrue(controller.state.value.pages.isEmpty(), mode)
            controller.openCurrentAlbum(controller.state.value.revision)
            runCurrent()
            assertEquals(if (mode == "album") "other-album" else "not-in-recents", requests.single().id)
            controller.close()
        }
    }

    @Test fun repeatedContextTapsReuseLoadedAlbumAndReturnFromQueueWithoutReloadOrPlayback() = runTest {
        val product = product()
        var loads = 0
        val controller = controller(product) { loads++; NaviampVehicleCollection(listOf(fixture.track)) { _, _ -> error("Browse played audio") } }
        controller.openCurrentAlbum(controller.state.value.revision)
        runCurrent()
        val album = controller.state.value.pages.single()
        controller.openCurrentAlbum(controller.state.value.revision)
        runCurrent()
        assertEquals(listOf(album), controller.state.value.pages)
        controller.openQueue()
        assertEquals(2, controller.state.value.pages.size)
        val oldQueue = controller.state.value
        controller.openCurrentAlbum(controller.state.value.revision)
        runCurrent()
        assertEquals(listOf(album), controller.state.value.pages)
        controller.selectPage(oldQueue.pages.last().id, oldQueue.pages.last().rows.first().id, oldQueue.revision) { assertFalse(it) }
        assertEquals(1, loads)
        controller.close()
    }

    @Test fun albumFailureAndTimeoutCanBeRetriedWithoutRequiringPhoneUi() = runTest {
        for (timeout in listOf(false, true)) {
            val product = product()
            var calls = 0
            val controller = controller(product) {
                if (++calls == 1) { if (timeout) awaitCancellation() else error("offline") }
                NaviampVehicleCollection(listOf(fixture.track)) { _, _ -> error("Retry played audio") }
            }
            controller.openCurrentAlbum(controller.state.value.revision)
            advanceTimeBy(101); runCurrent()
            val failed = controller.state.value
            assertEquals("Failed", failed.pages.single().status)
            val retry = failed.pages.single().rows.single()
            assertEquals("Retry", retry.title)
            controller.selectPage(failed.pages.single().id, retry.id, failed.revision) { assertFalse(it) }
            runCurrent()
            assertEquals(2, calls)
            assertEquals("", controller.state.value.pages.single().status)
            controller.close()
        }
    }

    @Test fun sourceBackAndDetachDiscardLateAlbumLoads() = runTest {
        for (mode in listOf("source", "back", "detach")) {
            val product = product()
            val gate = CompletableDeferred<Unit>()
            var cancelled = false
            val controller = controller(product) {
                try { withContext(NonCancellable) { gate.await() } }
                finally { cancelled = !currentCoroutineContext().isActive }
                NaviampVehicleCollection(listOf(fixture.track)) { _, _ -> error("Stale load played audio") }
            }
            controller.openCurrentAlbum(controller.state.value.revision)
            runCurrent()
            val page = controller.state.value.pages.single()
            when (mode) {
                "source" -> product.value = product.value.copy(sourceGeneration = 1)
                "back" -> controller.dismissPages(listOf(page.id))
                else -> controller.close()
            }
            runCurrent()
            gate.complete(Unit)
            runCurrent()
            assertTrue(cancelled, mode)
            if (mode != "detach") assertTrue(controller.state.value.pages.isEmpty(), mode)
            else assertTrue(controller.state.value.pages.single().rows.isEmpty())
            assertTrue(product.value.shell.nowPlaying!!.isPlaying)
            controller.close()
        }
    }

    @Test fun realCoreCurrentAlbumUsesProviderOrderAndExistingReadyPlaybackPath() = runTest {
        val first = fixture.track.copy(id = TrackId("first"), trackNumber = 1)
        val second = fixture.track.copy(id = TrackId("second"), trackNumber = 2)
        val provider = object : app.naviamp.domain.provider.MediaProvider by fixture {
            override suspend fun album(albumId: AlbumId): AlbumDetails {
                assertEquals(fixture.album.id, albumId)
                return AlbumDetails(fixture.album, listOf(second, first))
            }
        }
        val defaults = fakeCoreServices(provider)
        lateinit var observer: NaviampCorePlaybackObserver
        val selected = mutableListOf<Pair<List<Track>, Int>>()
        val effects = object : NaviampCorePlaybackEffectPort by defaults.playback.effects {
            override fun attach(value: NaviampCorePlaybackObserver) { observer = value }
            override fun playQueueSelection(queue: app.naviamp.domain.queue.PlaybackQueue, index: Int) {
                selected += queue.tracks to index
                observer.onStateChanged(app.naviamp.domain.playback.PlaybackState.Loading)
            }
        }
        val core = NaviampCore.create(backgroundScope, defaults.copy(playback = defaults.playback.copy(effects = effects)))
        core.stateStore.updateShell { it.copy(connectionSettings = it.connectionSettings.copy(currentSourceId = "source")) }
        core.onTrackChanged(fixture.track)
        val before = core.state.value.shell
        val controller = NaviampVehicleBrowseController(backgroundScope, core.state, core.externalPlaybackBridge(),
            { core.execute(it) }, labels, 100, collections = core.vehicleCollections)
        controller.openCurrentAlbum(controller.state.value.revision)
        runCurrent()
        assertTrue(selected.isEmpty())
        val loaded = controller.state.value
        assertEquals(listOf("Play", "Shuffle", first.title, second.title), loaded.pages.single().rows.map { it.title })
        assertEquals(before.albumDetail, core.state.value.shell.albumDetail)
        assertEquals(before.shellChrome, core.state.value.shell.shellChrome)
        val completions = mutableListOf<Boolean>()
        controller.selectPage(loaded.pages.single().id, loaded.pages.single().rows.last().id, loaded.revision, completions::add)
        runCurrent()
        assertEquals(listOf(first, second) to 1, selected.single())
        assertTrue(completions.isEmpty())
        observer.onStateChanged(app.naviamp.domain.playback.PlaybackState.Playing)
        runCurrent()
        assertEquals(listOf(true), completions)
        assertTrue(controller.state.value.nowPlaying)
        controller.close()
        core.close()
    }
}
