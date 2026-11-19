package app.naviamp.presentation

import app.naviamp.app.*
import app.naviamp.domain.*
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.playback.*
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.*
import app.naviamp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampVehicleConnectionTest {
    private val labels = NaviampVehicleConnectionLabels("Connections", "Reconnect", "Current", "Connecting", "Failed", "Set up on phone", "Unavailable", "Offline")
    private val browseLabels = NaviampVehicleBrowseLabels("Home", "Playlists", "Radio", "Empty", "Loading", "Disconnected", "Failed", "Next", "Previous", "Queue", "Play", "Shuffle", "Retry")
    private fun saved(id: String) = NaviampSavedConnectionUi(id, "Account $id", "https://private.example/$id?secret=hidden", "User $id")
    private fun product(count: Int = 3, current: String? = "0") = MutableStateFlow(NaviampCoreState(shell = NaviampAppShellUiState(
        connectionSettings = NaviampConnectionSettingsUi(currentSourceId = current, connection = NaviampShellConnectionUi(
            connected = current != null, savedConnections = (0 until count).map { saved("$it") })))))
    private fun TestScope.owner(product: MutableStateFlow<NaviampCoreState>, select: suspend (String, Boolean) -> Boolean = { _, _ -> error("Browsing connected") }) =
        NaviampVehicleConnectionController(backgroundScope, product, labels, select, timeoutMillis = 100)
    private fun TestScope.browser(product: MutableStateFlow<NaviampCoreState>, owner: NaviampVehicleConnectionController, limit: Int = 5) =
        NaviampVehicleBrowseController(backgroundScope, product, NaviampCoreExternalPlaybackBridge(product, {}), {}, browseLabels,
            limit, connections = owner)
    private fun open(browser: NaviampVehicleBrowseController) {
        val state = browser.state.value
        browser.select(NaviampVehicleBrowseDestination.Home, state.tabs.first().rows.first { it.title == "Connections" }.id, state.revision) { assertFalse(it) }
    }
    @Test fun pickerUsesSavedIdentityAndCurrentMarkerWithoutServerUrlsOrCredentialForms() = runTest {
        val state = product(); val owner = owner(state)
        assertEquals("Reconnect", owner.rows().first().title)
        assertFalse(owner.rows().first { it.title == "Account 0" }.enabled)
        assertTrue(owner.rows().first { it.title == "Account 1" }.enabled)
        assertTrue(owner.rows().first { it.title == "Account 0" }.subtitle.contains("Current"))
        assertTrue(owner.rows().none { "https:" in it.toString() || "secret" in it.toString() })
        assertFalse(owner.rows().last().enabled)
        assertEquals("Set up on phone", owner.rows().last().title)
        owner.close()
    }
    @Test fun emptyMissingAndRemoteSourcesOfferHonestSetupOrUnavailableStates() = runTest {
        val state = product(0, null); val requests = mutableListOf<String>()
        val owner = owner(state) { id, _ -> requests += id; true }
        assertEquals("Set up on phone", owner.status)
        assertEquals(1, owner.rows().size)
        owner.select("missing"); owner.select(NaviampVehicleConnectionController.Reconnect); runCurrent()
        assertTrue(requests.isEmpty())
        state.value = product().value.copy(shell = product().value.shell.let { it.copy(connect = it.connect.copy(remotePlaybackAuthorityActive = true)) })
        runCurrent(); assertEquals("Unavailable", owner.status); assertTrue(owner.rows().none { it.enabled })
        owner.select("naviamp:vehicle:source:1"); runCurrent(); assertTrue(requests.isEmpty()); owner.close()
    }
    @Test fun busyRequestsAreSingleFlightAndFailureSupportsExplicitRetry() = runTest {
        val state = product(); val gate = CompletableDeferred<Boolean>(); val requests = mutableListOf<Pair<String, Boolean>>()
        var pending = true
        val owner = owner(state) { id, reconnect -> requests += id to reconnect; if (pending) gate.await() else true }
        owner.select("naviamp:vehicle:source:1"); owner.select("naviamp:vehicle:source:2"); runCurrent()
        assertEquals(listOf("1" to false), requests); assertEquals("Connecting", owner.status)
        assertTrue(owner.rows().none { it.enabled })
        gate.complete(false); runCurrent(); assertEquals("Failed", owner.status)
        pending = false; owner.select("naviamp:vehicle:source:1"); runCurrent()
        assertEquals(2, requests.size); assertEquals("", owner.status)
        owner.select(NaviampVehicleConnectionController.Reconnect); runCurrent()
        assertEquals("0" to true, requests.last()); owner.close()
    }
    @Test fun inventoryAuthorityAndExternalSourceChangesCancelPendingSelection() = runTest {
        for (change in listOf("remove", "identity", "authority", "source")) {
            val state = product(); var cancelled = false
            val owner = owner(state) { _, _ -> try { awaitCancellation() } finally { cancelled = true } }
            owner.select("naviamp:vehicle:source:1"); runCurrent()
            state.value = state.value.copy(shell = state.value.shell.let { shell -> when (change) {
                "authority" -> shell.copy(connect = shell.connect.copy(remotePlaybackAuthorityActive = true))
                "source" -> shell.copy(connectionSettings = shell.connectionSettings.copy(currentSourceId = "2"))
                else -> shell.copy(connectionSettings = shell.connectionSettings.copy(connection = shell.connectionSettings.connection.copy(
                    savedConnections = if (change == "remove") listOf(saved("0")) else listOf(saved("0"), saved("1").copy(username = "Changed")))))
            } })
            runCurrent(); assertTrue(cancelled, change); assertFalse(owner.state.value.working); owner.close()
        }
    }
    @Test fun deadlineAndDetachRejectLateCompletionsIncludingANewerAttempt() = runTest {
        val state = product(); val first = CompletableDeferred<Unit>(); val second = CompletableDeferred<Unit>(); var calls = 0
        val owner = owner(state) { _, _ -> calls++; withContext(NonCancellable) { if (calls == 1) first.await() else second.await() }; true }
        owner.select("naviamp:vehicle:source:1"); runCurrent(); advanceTimeBy(101); runCurrent()
        assertEquals("Failed", owner.status)
        owner.select("naviamp:vehicle:source:2"); runCurrent(); assertTrue(owner.state.value.working)
        first.complete(Unit); runCurrent(); assertTrue(owner.state.value.working)
        owner.close(); second.complete(Unit); runCurrent(); assertFalse(owner.state.value.working)
    }
    @Test fun retainedOfflineSessionsAreLabelledOfflineAndOfferDirectRecovery() = runTest {
        val state = product()
        state.value = state.value.copy(shell = state.value.shell.copy(connectionSettings = state.value.shell.connectionSettings.copy(
            connection = state.value.shell.connectionSettings.connection.copy(connected = true, offline = true))))
        val requests = mutableListOf<Pair<String, Boolean>>()
        val owner = owner(state) { id, reconnect -> requests += id to reconnect; true }
        assertTrue(owner.recoveryNeeded); assertEquals("Offline", owner.status)
        val browser = browser(state, owner, 8)
        val snapshot = browser.state.value
        browser.select(NaviampVehicleBrowseDestination.Home, snapshot.tabs.first().rows.first { it.title == "Reconnect" }.id, snapshot.revision) { assertFalse(it) }
        runCurrent(); assertEquals(listOf("0" to true), requests)
        assertEquals("Offline", browser.state.value.pages.single().status); browser.close()
    }

    @Test fun homeRecoveryIsReachableOfflineAndCompletesNativeSelectionImmediately() = runTest {
        val state = product(); state.value = state.value.copy(shell = state.value.shell.copy(connectionSettings = state.value.shell.connectionSettings.copy(
            connection = state.value.shell.connectionSettings.connection.copy(connected = false, statusIsError = true))))
        val gate = CompletableDeferred<Boolean>(); var calls = 0
        val owner = owner(state) { id, reconnect -> assertEquals("0", id); assertTrue(reconnect); calls++; gate.await() }
        val browser = browser(state, owner, 8)
        val before = browser.state.value
        assertEquals("Connections", before.tabs.first().rows.first().title)
        val completed = mutableListOf<Boolean>()
        browser.select(NaviampVehicleBrowseDestination.Home, before.tabs.first().rows.first { it.title == "Reconnect" }.id, before.revision, completed::add)
        assertEquals(listOf(false), completed); runCurrent(); assertEquals(1, calls)
        assertEquals("Connections", browser.state.value.pages.single().title)
        assertEquals("Connecting", browser.state.value.pages.single().status)
        gate.complete(false); runCurrent(); assertEquals("Failed", browser.state.value.pages.single().status)
        browser.close()
    }
    @Test fun chooserSurvivesConnectionTransitionsAndRejectsOldInventorySelections() = runTest {
        val state = product(); val requests = mutableListOf<String>(); val owner = owner(state) { id, _ -> requests += id; true }
        val browser = browser(state, owner, 8); open(browser); runCurrent()
        val old = browser.state.value; val page = old.pages.single(); val row = page.rows.first { it.title == "Account 1" }
        state.value = state.value.copy(shell = state.value.shell.copy(connectionSettings = state.value.shell.connectionSettings.copy(
            connection = state.value.shell.connectionSettings.connection.copy(savedConnections = listOf(saved("0"), saved("2"))))))
        runCurrent(); browser.selectPage(page.id, row.id, old.revision) { assertFalse(it) }; runCurrent(); assertTrue(requests.isEmpty())
        state.value = state.value.copy(shell = state.value.shell.copy(connectionSettings = state.value.shell.connectionSettings.copy(
            connection = state.value.shell.connectionSettings.connection.copy(isConnecting = true))))
        runCurrent(); assertEquals(page.id, browser.state.value.pages.single().id); assertEquals("Connecting", browser.state.value.pages.single().status)
        state.value = state.value.copy(sourceGeneration = 1, shell = state.value.shell.copy(connectionSettings = state.value.shell.connectionSettings.copy(
            currentSourceId = "2", connection = state.value.shell.connectionSettings.connection.copy(isConnecting = false))))
        runCurrent(); assertEquals(page.id, browser.state.value.pages.single().id)
        assertTrue(browser.state.value.pages.single().rows.first { it.title == "Account 2" }.subtitle.contains("Current")); browser.close()
    }
    @Test fun constrainedPagesEnumerateAllSavedSourcesAndBackCancelsWork() = runTest {
        val state = product(12); var cancelled = false
        val owner = owner(state) { _, _ -> try { awaitCancellation() } finally { cancelled = true } }
        val browser = browser(state, owner, 3); open(browser); runCurrent()
        val seen = mutableSetOf<String>()
        while (true) {
            val snapshot = browser.state.value; val page = snapshot.pages.single()
            assertTrue(page.rows.size <= 3)
            seen += page.rows.filter { it.title.startsWith("Account ") }.map { it.title }
            val next = page.rows.firstOrNull { it.title == "Next" } ?: break
            browser.selectPage(page.id, next.id, snapshot.revision) { assertFalse(it) }
        }
        assertEquals(12, seen.size)
        browser.dismissPages(browser.state.value.pages.map { it.id }); open(browser); runCurrent()
        val snapshot = browser.state.value; val page = snapshot.pages.single()
        browser.selectPage(page.id, page.rows.first { it.title == "Reconnect" }.id, snapshot.revision) { assertFalse(it) }; runCurrent()
        browser.dismissPages(listOf(page.id)); runCurrent(); assertTrue(cancelled); browser.close()
    }
    @Test fun leavingThePickerForMediaCancelsItsPendingConnectionRequest() = runTest {
        val state = product()
        state.value = state.value.copy(shell = state.value.shell.copy(home = NaviampHomeScreenUi(SharedHomeUi(
            recentAlbums = listOf(SharedMediaItemUi("album", "Album", "Artist"))))))
        var cancelled = false
        val owner = owner(state) { _, _ -> try { awaitCancellation() } finally { cancelled = true } }
        val browser = browser(state, owner, 8); open(browser); runCurrent()
        val page = browser.state.value.pages.single()
        browser.selectPage(page.id, page.rows.first { it.title == "Account 1" }.id, browser.state.value.revision) { assertFalse(it) }; runCurrent()
        val home = browser.state.value
        browser.select(NaviampVehicleBrowseDestination.Home, home.tabs.first().rows.first { it.title == "Album" }.id, home.revision) { assertFalse(it) }
        runCurrent(); assertTrue(cancelled)
        assertTrue(browser.state.value.pages.none { it.id == page.id }); browser.close()
    }

    @Test fun sceneDetachCancelsPendingSelectionWithoutClosingProductState() = runTest {
        val state = product(); var cancelled = false
        val owner = owner(state) { _, _ -> try { awaitCancellation() } finally { cancelled = true } }
        val browser = browser(state, owner, 8); open(browser); runCurrent()
        val snapshot = browser.state.value; val page = snapshot.pages.single()
        browser.selectPage(page.id, page.rows.first { it.title == "Account 1" }.id, snapshot.revision) { assertFalse(it) }; runCurrent()
        val before = state.value
        browser.close(); runCurrent(); assertTrue(cancelled); assertEquals(before, state.value)
    }
    @Test fun realCoreRecoveryPreservesQueueAndSourceSelectionDoesNotAutoplayOrNavigatePhone() = runTest {
        for (radio in listOf(false, true)) {
            val provider = FakeCoreMediaProvider(); val defaults = fakeCoreServices(provider)
            var active = "0"
            val records = (0..1).map { NaviampCoreSavedConnectionRecord("$it", "Account $it", "https://example.test", "user$it") }
            val port = object : NaviampCoreProviderSessionPort by defaults.connection {
                override suspend fun connect(request: NaviampCoreConnectionRequest, plan: NaviampConnectionAttemptPlan): NaviampCoreConnectedSession {
                    assertFalse(plan.clearExistingPlayback); assertFalse(plan.clearProviderData)
                    active = (request as NaviampCoreConnectionRequest.Saved).id
                    return NaviampCoreConnectedSession(active, "Account $active", inventory = NaviampCoreConnectionInventory(records, active))
                }
                override fun currentSourceId() = active
                override fun currentProvider() = provider
            }
            var restores = 0; var starts = 0
            val effects = object : NaviampCorePlaybackEffectPort by defaults.playback.effects {
                override fun restoreQueue(queue: PlaybackQueue, startPositionSeconds: Double?) { restores++ }
                override fun restoreInternetRadio(station: InternetRadioStation) { restores++ }
                override fun startOrRestore(): Boolean { starts++; return true }
            }
            val saved = (if (radio) PlaybackSessionSettings.fromInternetRadioStation(InternetRadioStation("radio", "Radio", "https://example.test/live"))
                else assertNotNull(PlaybackSessionSettings.fromTracks(listOf(provider.track), 0, positionSeconds = 24.0))).copy(nowPlayingOpen = true)
            val sessions = NaviampPlaybackSessionController(object : PlaybackSessionRepository {
                override fun loadPlaybackSession(sourceId: String?) = saved.takeIf { sourceId == "1" }
                override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) = Unit
            })
            val core = NaviampCore.create(backgroundScope, defaults.copy(connection = port,
                playback = defaults.playback.copy(effects = effects, sessions = sessions)),
                initialState = NaviampCoreInitialState(connection = NaviampConnectionRuntimeState(phase = NaviampConnectionPhase.Offline, sourceId = "0"),
                    connectionInventory = NaviampCoreConnectionInventory(records, "0"),
                    playback = NaviampLivePlaybackState(currentTrack = provider.track, queue = PlaybackQueue(listOf(provider.track), 0),
                        progress = PlaybackProgress(18.0, 180.0), playbackState = PlaybackState.Playing)))
            core.stateStore.updateShell { it.copy(general = it.general.copy(interfaceSettings = it.general.interfaceSettings.copy(startPlayingOnLaunch = true))) }
            core.execute(NaviampCoreCommand.Navigation.SelectRoute(SharedRoute.Library)); runCurrent()
            assertTrue(core.state.value.shell.connectionSettings.connection.offline)
            val before = core.externalPlaybackBridge().snapshot()
            val progress = core.playbackProgress.value
            assertEquals(NaviampCoreCommandResult.SavedSourceSelected(true), core.execute(NaviampCoreCommand.Connection.SelectSavedSource("0", reconnect = true)))
            runCurrent(); assertFalse(core.state.value.shell.connectionSettings.connection.offline)
            assertEquals(before.queue, core.externalPlaybackBridge().snapshot().queue)
            assertEquals(progress, core.playbackProgress.value); assertEquals(0, restores); assertEquals(0, starts)
            assertEquals(NaviampCoreCommandResult.SavedSourceSelected(true), core.execute(NaviampCoreCommand.Connection.SelectSavedSource("1")))
            runCurrent(); assertEquals("1", core.state.value.shell.connectionSettings.currentSourceId)
            assertEquals(1, restores); assertEquals(0, starts)
            assertEquals(SharedRoute.Library, core.state.value.shell.shellChrome.selectedRoute)
            assertFalse(core.state.value.shell.shellChrome.nowPlayingOpen)
            assertTrue(core.state.value.shell.general.interfaceSettings.startPlayingOnLaunch)
            core.close()
        }
    }
}
