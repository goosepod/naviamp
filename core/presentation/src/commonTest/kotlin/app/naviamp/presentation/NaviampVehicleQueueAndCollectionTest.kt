package app.naviamp.presentation

import app.naviamp.domain.*
import app.naviamp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampVehicleQueueAndCollectionTest {
    private val labels = NaviampVehicleBrowseLabels("Home", "Playlists", "Radio", "Empty", "Loading", "Disconnected", "Failed",
        "Next", "Previous", "Queue", "Play", "Shuffle", "Retry")
    private val fixture = FakeCoreMediaProvider()
    private val tracks = (0..6).map { fixture.track.copy(id = TrackId("track$it"), title = "Track $it") }
    private fun product() = MutableStateFlow(NaviampCoreState(shell = NaviampAppShellUiState(
        connectionSettings = NaviampConnectionSettingsUi(currentSourceId = "source"),
        home = NaviampHomeScreenUi(SharedHomeUi(recentAlbums = listOf(SharedMediaItemUi("album", "Album", "Artist")))),
        playlists = NaviampPlaylistsScreenUi(playlists = listOf(SharedMediaItemUi("playlist", "Playlist", ""))),
        nowPlaying = NowPlayingUi(id = "current", title = "Current", subtitle = "", stateLabel = "Playing", isPlaying = true,
            canPlayPause = true,
            backTo = listOf(NaviampNowPlayingItemUi("queue:0", "Previous", "Artist")),
            upNext = listOf(NaviampNowPlayingItemUi("queue:2", "Next", "Artist"))),
    )))
    private fun openCollection(controller: NaviampVehicleBrowseController, album: Boolean = false) {
        val state = controller.state.value
        val destination = if (album) NaviampVehicleBrowseDestination.Home else NaviampVehicleBrowseDestination.Playlists
        val row = state.tabs.first { it.destination == destination }.rows.first { it.id != "naviamp:vehicle:queue" }
        controller.select(destination, row.id, state.revision) { assertFalse(it) }
    }

    @Test fun queueIsCurrentFirstAndSelectionReusesOccurrenceAwareQueueCommand() = runTest {
        val product = product()
        val commands = mutableListOf<NaviampCoreCommand>()
        val controller = NaviampVehicleBrowseController(backgroundScope, product, NaviampCoreExternalPlaybackBridge(product, {}), {
            commands += it
            product.value = product.value.copy(playbackSelectionGeneration = 1)
        }, labels, 100)
        assertTrue(controller.state.value.queueAvailable)
        controller.openQueue()
        val state = controller.state.value
        val page = state.pages.single()
        assertEquals(listOf("Current", "Next", "Previous"), page.rows.map { it.title })
        val completed = mutableListOf<Boolean>()
        controller.selectPage(page.id, page.rows[1].id, state.revision, completed::add)
        runCurrent()
        val selection = assertIs<NaviampCoreCommand.NowPlaying.Selection>(commands.single())
        assertEquals("queue:2", selection.request.item.id)
        assertTrue(completed.isEmpty())
        assertFalse(controller.state.value.nowPlaying)
        product.value = product.value.copy(readyPlaybackSelectionGeneration = 1)
        runCurrent()
        assertEquals(listOf(true), completed)
        assertTrue(controller.state.value.nowPlaying)
        assertTrue(controller.state.value.navigationRevision > state.navigationRevision)
        controller.close()
    }

    @Test fun currentPlayingItemOpensNowPlayingWithoutRestartAndPausedItemResumes() = runTest {
        for (paused in listOf(false, true)) {
            val product = product()
            product.value = product.value.copy(readyPlaybackSelectionGeneration = 0,
                shell = product.value.shell.copy(nowPlaying = product.value.shell.nowPlaying!!.copy(isPlaying = !paused, isPaused = paused)))
            val commands = mutableListOf<NaviampCoreCommand>()
            val controller = NaviampVehicleBrowseController(backgroundScope, product, NaviampCoreExternalPlaybackBridge(product, {}), {
                commands += it
                product.value = product.value.copy(shell = product.value.shell.copy(nowPlaying = product.value.shell.nowPlaying!!.copy(isPlaying = true)))
            }, labels, 100)
            controller.openQueue()
            val state = controller.state.value
            val completed = mutableListOf<Boolean>()
            controller.selectPage(state.pages[0].id, state.pages[0].rows[0].id, state.revision, completed::add)
            runCurrent()
            assertEquals(listOf(true), completed)
            assertEquals(if (paused) 1 else 0, commands.size)
            if (paused) assertEquals(NowPlayingPlaybackAction.Resume,
                assertIs<NaviampCoreCommand.NowPlaying.Playback>(commands.single()).request.action)
            controller.close()
        }
    }

    @Test fun queueChangesRejectStaleRowsButProgressDoesNotRebuildProjection() = runTest {
        val product = product()
        val controller = NaviampVehicleBrowseController(backgroundScope, product, NaviampCoreExternalPlaybackBridge(product, {}),
            { error("Stale queue executed") }, labels, 100)
        controller.openQueue()
        runCurrent()
        val old = controller.state.value
        product.value = product.value.copy(shell = product.value.shell.copy(nowPlaying = product.value.shell.nowPlaying!!.copy(positionSeconds = 30.0)))
        runCurrent()
        assertEquals(old, controller.state.value)
        product.value = product.value.copy(shell = product.value.shell.copy(nowPlaying = product.value.shell.nowPlaying!!.copy(upNext = emptyList())))
        val completed = mutableListOf<Boolean>()
        controller.selectPage(old.pages[0].id, old.pages[0].rows[1].id, old.revision, completed::add)
        assertEquals(listOf(false), completed)
        assertNotEquals(old.revision, controller.state.value.revision)
        controller.close()
    }

    @Test fun collectionBrowseLoadsWithoutPlaybackAndAllTracksAreReachableWithinNativeBudget() = runTest {
        for (album in listOf(false, true)) {
            val product = product()
            val initialShell = product.value.shell
            val references = mutableListOf<NaviampVehicleCollectionReference>()
            val starts = mutableListOf<Pair<Int, Boolean>>()
            val controller = NaviampVehicleBrowseController(backgroundScope, product, NaviampCoreExternalPlaybackBridge(product, {}),
                { error("Browsing dispatched a phone command") }, labels, 5, collections = NaviampVehicleCollectionAccess {
                    references += it
                    NaviampVehicleCollection(tracks) { index, shuffle -> starts += index to shuffle; true }
                })
            openCollection(controller, album)
            assertEquals("Loading", controller.state.value.pages.single().status)
            runCurrent()
            assertEquals(if (album) NaviampVehicleCollectionKind.Album else NaviampVehicleCollectionKind.Playlist, references.single().kind)
            val titles = mutableListOf<String>()
            while (true) {
                val state = controller.state.value
                val page = state.pages.single()
                assertTrue(page.rows.size <= 5)
                titles += page.rows.filter { it.id.startsWith("naviamp:vehicle:track:") }.map { it.title }
                val next = page.rows.firstOrNull { it.title == "Next" } ?: break
                controller.selectPage(page.id, next.id, state.revision) { assertFalse(it) }
            }
            assertEquals(tracks.map { it.title }, titles)
            assertTrue(starts.isEmpty())
            assertEquals(initialShell, product.value.shell)
            controller.close()
        }
    }

    @Test fun collectionPlayShuffleAndTrackSelectionUseLoadedOrderAndAwaitFreshReadyAudio() = runTest {
        for (action in listOf("Play", "Shuffle", "Track 3")) {
            val product = product()
            val starts = mutableListOf<Pair<Int, Boolean>>()
            val controller = NaviampVehicleBrowseController(backgroundScope, product, NaviampCoreExternalPlaybackBridge(product, {}), {},
                labels, 100, collections = NaviampVehicleCollectionAccess {
                    NaviampVehicleCollection(tracks) { index, shuffle ->
                        starts += index to shuffle
                        product.value = product.value.copy(playbackSelectionGeneration = 1)
                        true
                    }
                })
            openCollection(controller)
            runCurrent()
            val state = controller.state.value
            val page = state.pages.single()
            val completed = mutableListOf<Boolean>()
            controller.selectPage(page.id, page.rows.first { it.title == action }.id, state.revision, completed::add)
            runCurrent()
            assertEquals(listOf((if (action == "Track 3") 3 else 0) to (action == "Shuffle")), starts)
            assertTrue(completed.isEmpty())
            product.value = product.value.copy(readyPlaybackSelectionGeneration = 1)
            runCurrent()
            assertEquals(listOf(true), completed)
            controller.close()
        }
    }

    @Test fun failedAndTimedOutLoadsOfferRetryAndEmptyCollectionHasNoPlaybackActions() = runTest {
        for (mode in listOf("failure", "timeout", "empty")) {
            val product = product()
            var calls = 0
            val controller = NaviampVehicleBrowseController(backgroundScope, product, NaviampCoreExternalPlaybackBridge(product, {}), {},
                labels, 100, readinessTimeoutMillis = 100, collections = NaviampVehicleCollectionAccess {
                    calls++
                    if (calls == 1 && mode == "failure") error("Offline")
                    if (calls == 1 && mode == "timeout") delay(200)
                    NaviampVehicleCollection(emptyList()) { _, _ -> error("Empty collection played") }
                })
            openCollection(controller)
            advanceTimeBy(101)
            runCurrent()
            if (mode != "empty") {
                val state = controller.state.value
                assertEquals("Failed", state.pages[0].status)
                controller.selectPage(state.pages[0].id, state.pages[0].rows.single().id, state.revision) { assertFalse(it) }
                runCurrent()
            }
            assertEquals("Empty", controller.state.value.pages.single().status)
            assertTrue(controller.state.value.pages.single().rows.isEmpty())
            controller.close()
        }
    }

    @Test fun sourceChangeAndNativeBackCancelLateCollectionLoadsWithoutChangingRuntime() = runTest {
        for (mode in listOf("source", "back", "detach")) {
            val product = product()
            val release = CompletableDeferred<Unit>()
            val controller = NaviampVehicleBrowseController(backgroundScope, product, NaviampCoreExternalPlaybackBridge(product, {}), {},
                labels, 100, collections = NaviampVehicleCollectionAccess {
                    withContext(NonCancellable) { release.await() }
                    NaviampVehicleCollection(tracks) { _, _ -> error("Stale collection played") }
                })
            openCollection(controller)
            runCurrent()
            val page = controller.state.value.pages.single()
            when (mode) {
                "source" -> product.value = product.value.copy(sourceGeneration = 1)
                "back" -> controller.dismissPages(listOf(page.id))
                "detach" -> controller.close()
            }
            runCurrent()
            release.complete(Unit)
            runCurrent()
            if (mode != "detach") assertTrue(controller.state.value.pages.isEmpty())
            assertEquals("Current", product.value.shell.nowPlaying?.title)
            controller.close()
        }
    }

    @Test fun upNextBackKeepsNowPlayingAndDoesNotChangePausedPlayback() = runTest {
        val product = product()
        val paused = product.value.shell.nowPlaying!!.copy(isPlaying = false, isPaused = true, positionSeconds = 45.0)
        product.value = product.value.copy(shell = product.value.shell.copy(nowPlaying = paused))
        val controller = NaviampVehicleBrowseController(backgroundScope, product, NaviampCoreExternalPlaybackBridge(product, {}),
            { error("Up Next navigation dispatched playback") }, labels, 100)

        // The system Now Playing template can already be visible before Core requests it.
        controller.openUpNext()
        val upNext = controller.state.value
        assertTrue(upNext.nowPlaying)
        assertEquals(upNext.pages.single().id, upNext.nowPlayingBeforePageId)

        controller.dismissPages(listOf(upNext.pages.single().id))
        val back = controller.state.value
        assertTrue(back.nowPlaying)
        assertTrue(back.pages.isEmpty())
        assertNull(back.nowPlayingBeforePageId)
        assertEquals(upNext.navigationRevision, back.navigationRevision)
        assertEquals(paused, product.value.shell.nowPlaying)
        controller.close()
    }

    @Test fun selectingFromUpNextReturnsToNowPlayingOnlyAfterPlaybackIsReady() = runTest {
        val product = product()
        val controller = NaviampVehicleBrowseController(backgroundScope, product, NaviampCoreExternalPlaybackBridge(product, {}), {
            product.value = product.value.copy(playbackSelectionGeneration = 1)
        }, labels, 100)

        controller.openQueue()
        val ordinaryQueue = controller.state.value.pages.single().id
        controller.openUpNext()
        val open = controller.state.value
        val upNext = open.pages.last()
        assertEquals(listOf(ordinaryQueue, upNext.id), open.pages.map { it.id })
        assertEquals(upNext.id, open.nowPlayingBeforePageId)
        val completed = mutableListOf<Boolean>()

        controller.selectPage(upNext.id, upNext.rows[1].id, open.revision, completed::add)
        runCurrent()
        assertTrue(completed.isEmpty())
        assertEquals(upNext.id, controller.state.value.nowPlayingBeforePageId)
        product.value = product.value.copy(readyPlaybackSelectionGeneration = 1)
        runCurrent()

        assertEquals(listOf(true), completed)
        assertTrue(controller.state.value.nowPlaying)
        assertNull(controller.state.value.nowPlayingBeforePageId)
        assertEquals(listOf(ordinaryQueue), controller.state.value.pages.map { it.id })
        controller.close()
    }

    @Test fun dismissingQueueReturnsToLoadedCollectionAndRejectsDismissedCallbacks() = runTest {
        val product = product()
        val controller = NaviampVehicleBrowseController(backgroundScope, product, NaviampCoreExternalPlaybackBridge(product, {}), {},
            labels, 100, collections = NaviampVehicleCollectionAccess { NaviampVehicleCollection(tracks) { _, _ -> true } })
        openCollection(controller)
        runCurrent()
        val collection = controller.state.value.pages.single()
        controller.openQueue()
        val old = controller.state.value
        assertEquals(2, old.pages.size)
        controller.dismissPages(listOf(old.pages.last().id))
        assertEquals(collection, controller.state.value.pages.single())
        assertEquals(old.navigationRevision, controller.state.value.navigationRevision, "Native Back must not request another push")
        controller.selectPage(old.pages.last().id, old.pages.last().rows.first().id, old.revision) { assertFalse(it) }
        controller.close()
    }
    @Test fun realCoreCollectionPlaybackPreservesPlaylistOccurrencesAndAlbumOrderWithoutPhoneBrowseNavigation() = runTest {
        val first = tracks[0].copy(discNumber = 1, trackNumber = 1)
        val second = tracks[1].copy(discNumber = 1, trackNumber = 2)
        val third = tracks[2].copy(discNumber = 2, trackNumber = 1)
        val playlist = listOf(third, first, third, second)
        val provider = object : app.naviamp.domain.provider.MediaProvider by fixture {
            override suspend fun album(albumId: AlbumId) = AlbumDetails(fixture.album, listOf(third, second, first))
            override suspend fun playlistTracks(playlistId: String) = playlist
        }
        val defaults = fakeCoreServices(provider)
        val selections = mutableListOf<Pair<List<Track>, Int>>()
        val effects = object : NaviampCorePlaybackEffectPort by defaults.playback.effects {
            override fun playQueueSelection(queue: app.naviamp.domain.queue.PlaybackQueue, index: Int) {
                selections += queue.tracks to index
            }
        }
        val core = NaviampCore.create(backgroundScope, defaults.copy(playback = defaults.playback.copy(effects = effects)))
        val before = core.state.value.shell
        val loadedPlaylist = core.vehicleCollections.load(NaviampVehicleCollectionReference(
            NaviampVehicleCollectionKind.Playlist, fixture.playlist.id, fixture.playlist.name))
        val loadedAlbum = core.vehicleCollections.load(NaviampVehicleCollectionReference(
            NaviampVehicleCollectionKind.Album, fixture.album.id.value, fixture.album.title))
        assertEquals(before.playlistDetail, core.state.value.shell.playlistDetail)
        assertEquals(before.albumDetail, core.state.value.shell.albumDetail)
        assertEquals(before.shellChrome, core.state.value.shell.shellChrome)
        assertTrue(selections.isEmpty())
        assertTrue(loadedPlaylist.play(2))
        assertEquals(playlist to 2, selections.last())
        assertTrue(loadedAlbum.play(1))
        assertEquals(listOf(first, second, third) to 1, selections.last())
        assertFalse(loadedAlbum.play(99))
        assertTrue(loadedPlaylist.play(shuffle = true))
        assertEquals(playlist.groupingBy { it.id }.eachCount(), selections.last().first.groupingBy { it.id }.eachCount())
        assertEquals(0, selections.last().second)
        core.stateStore.update { it.copy(sourceGeneration = it.sourceGeneration + 1) }
        assertFalse(loadedPlaylist.play())
        assertEquals(3, selections.size)
        core.close()
    }

    @Test fun duplicateQueuedTracksRemainSeparateAndLiveRadioHasNoUpNextEntry() = runTest {
        val product = product()
        product.value = product.value.copy(shell = product.value.shell.copy(nowPlaying = product.value.shell.nowPlaying!!.copy(
            upNext = listOf(NaviampNowPlayingItemUi("queue:2", "Repeated", "Artist"), NaviampNowPlayingItemUi("queue:3", "Repeated", "Artist")))))
        val commands = mutableListOf<NaviampCoreCommand>()
        val controller = NaviampVehicleBrowseController(backgroundScope, product, NaviampCoreExternalPlaybackBridge(product, {}),
            { commands += it }, labels, 100)
        controller.openQueue()
        val state = controller.state.value
        val duplicates = state.pages.single().rows.filter { it.title == "Repeated" }
        assertEquals(2, duplicates.size)
        assertNotEquals(duplicates[0].id, duplicates[1].id)
        controller.selectPage(state.pages[0].id, duplicates[1].id, state.revision) { assertFalse(it) }
        runCurrent()
        assertEquals("queue:3", assertIs<NaviampCoreCommand.NowPlaying.Selection>(commands.single()).request.item.id)
        product.value = product.value.copy(shell = product.value.shell.copy(nowPlaying = product.value.shell.nowPlaying!!.copy(isLive = true)))
        runCurrent()
        assertFalse(controller.state.value.queueAvailable)
        assertTrue(controller.state.value.pages.single().rows.isEmpty())
        assertTrue(controller.state.value.tabs[0].rows.none { it.id == "naviamp:vehicle:queue" })
        controller.close()
    }

    @Test fun nativePresentationFailureDoesNotAutomaticallyRetryOrRetainFailedDetail() = runTest {
        val product = product()
        val controller = NaviampVehicleBrowseController(backgroundScope, product, NaviampCoreExternalPlaybackBridge(product, {}), {}, labels, 100)
        controller.openQueue()
        val state = controller.state.value
        controller.presentationFailed(state.pages.single().id)
        assertTrue(controller.state.value.pages.isEmpty())
        assertEquals(state.navigationRevision, controller.state.value.navigationRevision)
        assertEquals("Failed", controller.state.value.tabs.first().status)
        controller.openQueue()
        assertTrue(controller.state.value.navigationRevision > state.navigationRevision)
        controller.close()
    }

    @Test fun sourceSwitchDuringCoreProviderLoadCannotReturnPlayableOldCollection() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val provider = object : app.naviamp.domain.provider.MediaProvider by fixture {
            override suspend fun playlistTracks(playlistId: String): List<Track> {
                entered.complete(Unit)
                release.await()
                return tracks
            }
        }
        val defaults = fakeCoreServices(provider)
        val core = NaviampCore.create(backgroundScope, defaults)
        val loading = async {
            runCatching { core.vehicleCollections.load(NaviampVehicleCollectionReference(
                NaviampVehicleCollectionKind.Playlist, fixture.playlist.id, fixture.playlist.name)) }
        }
        entered.await()
        core.stateStore.update { it.copy(sourceGeneration = it.sourceGeneration + 1) }
        release.complete(Unit)
        assertTrue(loading.await().isFailure)
        core.close()
    }

    @Test fun realCoreUpNextSelectionPreservesDuplicateOccurrencesAndWaitsForEngine() = runTest {
        val repeated = listOf(tracks[0], tracks[1], tracks[0], tracks[2])
        val provider = object : app.naviamp.domain.provider.MediaProvider by fixture {
            override suspend fun playlistTracks(playlistId: String) = repeated
        }
        val defaults = fakeCoreServices(provider)
        lateinit var observer: NaviampCorePlaybackObserver
        val selections = mutableListOf<app.naviamp.domain.queue.PlaybackQueue>()
        val effects = object : NaviampCorePlaybackEffectPort by defaults.playback.effects {
            override fun attach(value: NaviampCorePlaybackObserver) { observer = value }
            override fun selectExistingQueueItem(queue: app.naviamp.domain.queue.PlaybackQueue, index: Int) {
                selections += queue
                observer.onStateChanged(app.naviamp.domain.playback.PlaybackState.Loading)
            }
            override fun playQueueSelection(queue: app.naviamp.domain.queue.PlaybackQueue, index: Int) {
                selections += queue
                observer.onStateChanged(app.naviamp.domain.playback.PlaybackState.Loading)
            }
        }
        val core = NaviampCore.create(backgroundScope, defaults.copy(playback = defaults.playback.copy(effects = effects)))
        val loaded = core.vehicleCollections.load(NaviampVehicleCollectionReference(
            NaviampVehicleCollectionKind.Playlist, fixture.playlist.id, fixture.playlist.name))
        assertTrue(loaded.play())
        observer.onStateChanged(app.naviamp.domain.playback.PlaybackState.Playing)
        runCurrent()
        val controller = NaviampVehicleBrowseController(backgroundScope, core.state, core.externalPlaybackBridge(),
            { core.execute(it) }, labels, 100, collections = core.vehicleCollections)
        controller.openQueue()
        val state = controller.state.value
        val page = state.pages.single()
        val duplicates = page.rows.filter { it.title == tracks[0].title }
        assertEquals(2, duplicates.size)
        val completed = mutableListOf<Boolean>()
        controller.selectPage(page.id, duplicates.last().id, state.revision, completed::add)
        runCurrent()
        assertTrue(completed.isEmpty())
        assertEquals(2, selections.size)
        assertEquals(tracks[0], selections.last().current)
        assertEquals(2, selections.last().tracks.count { it.id == tracks[0].id })
        observer.onStateChanged(app.naviamp.domain.playback.PlaybackState.Playing)
        runCurrent()
        assertEquals(listOf(true), completed)
        controller.close()
        core.close()
    }

    @Test fun rootPresentationFailureStopsSceneRequestsUntilReconnectionWithoutStoppingPlayback() = runTest {
        val product = product()
        val controller = NaviampVehicleBrowseController(backgroundScope, product, NaviampCoreExternalPlaybackBridge(product, {}),
            { error("Unavailable native scene dispatched playback") }, labels, 100)
        controller.openQueue()
        val old = controller.state.value
        controller.rootPresentationFailed()
        assertFalse(controller.state.value.presentationAvailable)
        assertTrue(controller.state.value.pages.isEmpty())
        controller.selectPage(old.pages[0].id, old.pages[0].rows.first().id, old.revision) { assertFalse(it) }
        controller.openQueue()
        assertTrue(controller.state.value.pages.isEmpty())
        assertTrue(product.value.shell.nowPlaying!!.isPlaying)
        controller.close()
        val reconnected = NaviampVehicleBrowseController(backgroundScope, product, NaviampCoreExternalPlaybackBridge(product, {}), {}, labels, 100)
        assertTrue(reconnected.state.value.presentationAvailable)
        reconnected.openQueue()
        assertEquals(1, reconnected.state.value.pages.size)
        reconnected.close()
    }

    @Test fun coldCoreRuntimeDoesNotOfferAPhantomQueueBeforeAnyTrackHasBeenSelected() = runTest {
        val core = NaviampCore.create(backgroundScope, fakeCoreServices(fixture))
        val controller = NaviampVehicleBrowseController(backgroundScope, core.state, core.externalPlaybackBridge(),
            { core.execute(it) }, labels, 100, collections = core.vehicleCollections)
        runCurrent()
        assertFalse(controller.state.value.queueAvailable)
        assertTrue(controller.state.value.tabs.first().rows.none { it.id == "naviamp:vehicle:queue" })
        controller.openQueue()
        assertTrue(controller.state.value.pages.isEmpty())
        controller.close()
        core.close()
    }

}
