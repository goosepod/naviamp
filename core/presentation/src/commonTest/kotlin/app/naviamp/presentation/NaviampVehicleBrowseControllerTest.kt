package app.naviamp.presentation

import app.naviamp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampVehicleBrowseControllerTest {
    private val labels = NaviampVehicleBrowseLabels("Home", "Playlists", "Radio", "Empty", "Loading", "Disconnected", "Failed", "Next", "Previous", "Queue", "Play", "Shuffle", "Retry")
    private fun product() = MutableStateFlow(NaviampCoreState(shell = NaviampAppShellUiState(
        connectionSettings = NaviampConnectionSettingsUi(currentSourceId = "source"),
        home = NaviampHomeScreenUi(SharedHomeUi(
            recentlyPlayedTracks = (1..7).map { SharedTrackRowUi("track$it", "Track $it", "Artist") },
            recentAlbums = listOf(SharedMediaItemUi("album", "Album", "Artist")),
            playlists = listOf(SharedMediaItemUi("playlist", "Playlist", "")),
        )),
        radio = NaviampInternetRadioScreenUi(stations = listOf(NaviampInternetRadioStationUi(
            SharedMediaItemUi("station", "Station", ""), "https://example.test/stream"))),
    )))

    @Test fun realCoreCatalogSelectionWaitsForEngineAcrossHomePlaylistAndRadioWithoutPhoneUi() = runTest {
        val fixture = FakeCoreMediaProvider()
        val station = app.naviamp.domain.InternetRadioStation("station", "Station", "https://example.test/stream")
        val provider = object : app.naviamp.domain.provider.MediaProvider by fixture {
            override suspend fun albumList(type: app.naviamp.domain.provider.AlbumListType, limit: Int) = listOf(fixture.album)
            override suspend fun internetRadioStations() = listOf(station)
        }
        val defaults = fakeCoreServices(provider)
        lateinit var observer: NaviampCorePlaybackObserver
        val selections = mutableListOf<String>()
        val effects = object : NaviampCorePlaybackEffectPort by defaults.playback.effects {
            override fun attach(value: NaviampCorePlaybackObserver) { observer = value }
            override fun playQueueSelection(queue: app.naviamp.domain.queue.PlaybackQueue, index: Int) {
                selections += queue.tracks[index].id.value
                observer.onStateChanged(app.naviamp.domain.playback.PlaybackState.Loading)
            }
        }
        val core = NaviampCore.create(backgroundScope, defaults.copy(
            playback = defaults.playback.copy(effects = effects),
            radio = defaults.radio.copy(playback = NaviampCoreInternetRadioPlaybackPort {
                selections += it.id
                observer.onStateChanged(app.naviamp.domain.playback.PlaybackState.Loading)
            }),
        ))
        core.execute(NaviampCoreCommand.Home.Refresh)
        core.execute(NaviampCoreCommand.Playlists.Refresh)
        core.execute(NaviampCoreCommand.Radio.Refresh)
        val controller = NaviampVehicleBrowseController(backgroundScope, core.state, core.externalPlaybackBridge(),
            { core.execute(it) }, labels, 100, collections = core.vehicleCollections)
        for (destination in controller.state.value.tabs.map { it.destination }) {
            runCurrent()
            val state = controller.state.value
            val tab = state.tabs.first { it.destination == destination }
            assertTrue(tab.rows.isNotEmpty(), destination.name)
            val completions = mutableListOf<Boolean>()
            val row = tab.rows.first { it.id != "naviamp:vehicle:queue" }
            if (core.externalPlaybackBridge().browseCollection(row.id) != null) {
                controller.select(destination, row.id, state.revision) { assertFalse(it) }
                runCurrent()
                val detail = controller.state.value.pages.last()
                controller.selectPage(detail.id, detail.rows.first().id, controller.state.value.revision, completions::add)
            } else controller.select(destination, row.id, state.revision, completions::add)
            runCurrent()
            assertTrue(completions.isEmpty(), destination.name)
            observer.onStateChanged(app.naviamp.domain.playback.PlaybackState.Playing)
            runCurrent()
            assertEquals(listOf(true), completions, destination.name)
        }
        assertEquals(listOf(fixture.track.id.value, fixture.track.id.value, station.id), selections)
        controller.close()
        core.close()
    }

    @Test fun directTrackAndRadioReuseCommandsAndWaitForFreshAudioReadiness() = runTest {
        val product = product()
        val commands = mutableListOf<NaviampCoreCommand>()
        val controller = NaviampVehicleBrowseController(backgroundScope, product,
            NaviampCoreExternalPlaybackBridge(product, {}), { command ->
                commands += command
                product.value = product.value.copy(playbackSelectionGeneration = product.value.playbackSelectionGeneration + 1)
            }, labels, 100)
        assertEquals(listOf("Home", "Playlists", "Radio"), controller.state.value.tabs.map { it.title })
        val destinations = listOf(NaviampVehicleBrowseDestination.Home, NaviampVehicleBrowseDestination.Radio)
        for (destination in destinations) {
            val completions = mutableListOf<Boolean>()
            val tab = controller.state.value.tabs.first { it.destination == destination }
            controller.select(destination, tab.rows.first { it.id != "naviamp:vehicle:queue" }.id, controller.state.value.revision, completions::add)
            runCurrent()
            assertTrue(completions.isEmpty())
            val generation = product.value.playbackSelectionGeneration
            product.value = product.value.copy(readyPlaybackSelectionGeneration = generation,
                shell = product.value.shell.copy(nowPlaying = NowPlayingUi(id = "ready", title = "Ready", subtitle = "", stateLabel = "Playing", isPlaying = true)))
            runCurrent()
            assertEquals(listOf(true), completions)
        }
        assertIs<NaviampCoreCommand.Home.RecentTrackAction>(commands[0])
        assertIs<NaviampCoreCommand.Home.SelectInternetRadio>(commands[1])
        controller.close()
    }

    @Test fun sourceSwitchIncludingReturnToSameSourceRejectsOldRows() = runTest {
        val product = product()
        val commands = mutableListOf<NaviampCoreCommand>()
        val controller = NaviampVehicleBrowseController(backgroundScope, product,
            NaviampCoreExternalPlaybackBridge(product, {}), { commands += it }, labels, 100)
        val old = controller.state.value
        product.value = product.value.copy(sourceGeneration = 2)
        val completions = mutableListOf<Boolean>()
        controller.select(NaviampVehicleBrowseDestination.Home, old.tabs[0].rows[0].id, old.revision, completions::add)
        assertEquals(listOf(false), completions)
        assertTrue(commands.isEmpty())
        controller.close()
    }

    @Test fun pagingFitsNativeBudgetAndCanReachEveryRowWithoutPlayingNavigation() = runTest {
        val product = product()
        val controller = NaviampVehicleBrowseController(backgroundScope, product,
            NaviampCoreExternalPlaybackBridge(product, {}), { error("Navigation played audio") }, labels, 5)
        val seen = mutableSetOf<String>()
        while (true) {
            val state = controller.state.value
            val rows = state.tabs[0].rows
            assertTrue(rows.size <= 5)
            seen += rows.filter { !it.id.startsWith("naviamp:vehicle:") }.map { it.id }
            val next = rows.firstOrNull { it.title == "Next" } ?: break
            controller.select(NaviampVehicleBrowseDestination.Home, next.id, state.revision) { assertFalse(it) }
        }
        assertEquals(8, seen.size)
        controller.close()
    }

    @Test fun failureTimeoutSupersessionAndDetachCompleteHandlersExactlyOnce() = runTest {
        for (mode in listOf("failure", "timeout", "detach", "supersede", "source")) {
            val product = product()
            val controller = NaviampVehicleBrowseController(backgroundScope, product,
                NaviampCoreExternalPlaybackBridge(product, {}), {
                    if (mode == "failure") error("Server unavailable")
                    product.value = product.value.copy(playbackSelectionGeneration = product.value.playbackSelectionGeneration + 1)
                }, labels, 100, readinessTimeoutMillis = 100)
            val state = controller.state.value
            val completions = mutableListOf<Boolean>()
            controller.select(NaviampVehicleBrowseDestination.Home, state.tabs[0].rows[0].id, state.revision, completions::add)
            runCurrent()
            when (mode) {
                "detach" -> controller.close()
                "supersede" -> controller.select(NaviampVehicleBrowseDestination.Home, state.tabs[0].rows[1].id, state.revision) {}
                "source" -> product.value = product.value.copy(sourceGeneration = 1)
            }
            advanceTimeBy(101)
            runCurrent()
            assertEquals(listOf(false), completions, mode)
            controller.close()
        }
    }

    @Test fun oldPlayingStateCannotTurnAnEmptyOrRejectedSelectionIntoSuccess() = runTest {
        val product = product()
        product.value = product.value.copy(readyPlaybackSelectionGeneration = 0,
            shell = product.value.shell.copy(nowPlaying = NowPlayingUi(title = "Old", subtitle = "", stateLabel = "Playing", isPlaying = true)))
        val controller = NaviampVehicleBrowseController(backgroundScope, product,
            NaviampCoreExternalPlaybackBridge(product, {}), {}, labels, 100)
        val completions = mutableListOf<Boolean>()
        val state = controller.state.value
        controller.select(NaviampVehicleBrowseDestination.Home, state.tabs[0].rows.first { it.id != "naviamp:vehicle:queue" }.id, state.revision, completions::add)
        runCurrent()
        assertEquals(listOf(false), completions)
        assertEquals("Failed", controller.state.value.tabs[0].status)
        controller.close()
    }

    @Test fun engineErrorFinishesSelectionWithoutWaitingForTimeout() = runTest {
        val product = product()
        val controller = NaviampVehicleBrowseController(backgroundScope, product,
            NaviampCoreExternalPlaybackBridge(product, {}), {
                product.value = product.value.copy(playbackSelectionGeneration = 1)
            }, labels, 100)
        val completions = mutableListOf<Boolean>()
        val state = controller.state.value
        controller.select(NaviampVehicleBrowseDestination.Home, state.tabs[0].rows[0].id, state.revision, completions::add)
        product.value = product.value.copy(failedPlaybackSelectionGeneration = 1)
        runCurrent()
        assertEquals(listOf(false), completions)
        controller.close()
    }
}
