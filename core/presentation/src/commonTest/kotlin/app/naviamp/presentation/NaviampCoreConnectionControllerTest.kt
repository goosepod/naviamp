package app.naviamp.presentation

import app.naviamp.app.NaviampConnectionAttemptPlan
import app.naviamp.app.NaviampConnectionController
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.ui.NaviampSavedConnectionUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

class NaviampCoreConnectionControllerTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun providerSessionLifecycleRefreshesNowAndOnTheSharedSchedule() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()
        val lifecycle = NaviampCoreProviderSessionLifecycle(
            sessionPort = fixture.port,
            refreshIntervalMillis = 100L,
        )

        lifecycle.refreshNow()
        val scheduled = launch { lifecycle.maintainWhileMounted() }
        runCurrent()
        assertEquals(1, fixture.port.refreshCalls)

        advanceTimeBy(100L)
        runCurrent()
        assertEquals(2, fixture.port.refreshCalls)
        scheduled.cancelAndJoin()
    }

    @Test
    fun formAndEditingStateAreOwnedImmediatelyByCore() {
        val fixture = fixture()
        val form = ConnectionFormState(serverUrl = "https://music.example", username = "demo", password = "secret")

        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(form))
        fixture.controller.dispatch(NaviampCoreCommand.Connection.New)

        val state = fixture.store.state.value.shell.connectionSettings.connection
        assertTrue(state.editingConnection)
        assertFalse(state.editingSavedConnection)
        assertEquals(ConnectionFormState(), state.form)
    }

    @Test
    fun cancellingSavedConnectionEditsPreservesSessionAndReloadsSavedValues() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()
        fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        fixture.port.connectRequests.clear()
        fixture.controller.execute(NaviampCoreCommand.Connection.EditCurrent)
        val original = fixture.store.state.value.shell.connectionSettings.connection.form
        val inventory = fixture.port.inventory
        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(original.copy(username = "unsaved")))

        fixture.controller.dispatch(NaviampCoreCommand.Connection.CancelForm)

        val state = fixture.store.state.value.shell.connectionSettings.connection
        assertFalse(state.editingConnection)
        assertFalse(state.editingSavedConnection)
        assertTrue(state.connected)
        assertTrue(fixture.port.connectRequests.isEmpty())
        assertEquals(inventory, fixture.port.inventory)
        fixture.controller.execute(NaviampCoreCommand.Connection.EditCurrent)
        assertEquals(original, fixture.store.state.value.shell.connectionSettings.connection.form)
    }

    @Test
    fun successfulConnectionUsesSharedAttemptPolicyAndPublishesOneSnapshot() = kotlinx.coroutines.test.runTest {
        var connectedNotifications = 0
        val fixture = fixture(onConnected = { connectedNotifications += 1 })
        val form = ConnectionFormState(serverUrl = "https://music.example", username = "demo", password = "secret")
        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(form))

        fixture.controller.execute(NaviampCoreCommand.Connection.Connect)

        val (request, plan) = fixture.port.connectRequests.single()
        assertEquals(NaviampCoreConnectionRequest.Form(form), request)
        assertFalse(plan.restoreSavedSession)
        assertTrue(plan.clearExistingPlayback)
        val state = fixture.store.state.value.shell.connectionSettings
        assertTrue(state.connection.connected)
        assertFalse(state.connection.isConnecting)
        assertEquals("Connected to Home Music.", state.connection.status)
        assertEquals("source-1", state.currentSourceId)
        assertTrue(state.connection.savedConnections.single().current)
        assertEquals(1, connectedNotifications)
    }

    @Test
    fun failedConnectProvisioningLeavesTheExistingSourceUntouched() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(connectFailure = IllegalStateException("invalid credential"))

        val connected = fixture.controller.provisionConnect(
            ConnectionFormState(
                serverUrl = "https://other.example",
                username = "listener",
                password = "wrong",
            ),
        )

        assertFalse(connected)
        assertEquals("source-1", fixture.store.state.value.shell.connectionSettings.currentSourceId)
        assertEquals(listOf("source-1"), fixture.port.inventory.connections.map { it.id })
    }

    @Test
    fun provisioningTheAlreadyConnectedSourceIsIdempotent() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()
        fixture.controller.execute(
            NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()),
        )
        fixture.port.connectRequests.clear()

        val connected = fixture.controller.provisionConnect(
            ConnectionFormState(
                serverUrl = "https://MUSIC.example/",
                username = "demo",
                password = "newly-transferred-secret",
            ),
        )

        assertTrue(connected)
        assertTrue(fixture.port.connectRequests.isEmpty())
        assertEquals("source-1", fixture.store.state.value.shell.connectionSettings.currentSourceId)
    }

    @Test
    fun savedConnectionUsesRestorationPolicy() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()

        fixture.controller.execute(
            NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()),
        )

        val (request, plan) = fixture.port.connectRequests.single()
        assertEquals(NaviampCoreConnectionRequest.Saved("source-1"), request)
        assertTrue(plan.restoreSavedSession)
        assertFalse(plan.clearExistingPlayback)
    }

    @Test
    fun successfulServerSwitchPublishesSourceTransitionBeforeTheNewSource() = kotlinx.coroutines.test.runTest {
        val transitions = mutableListOf<String>()
        lateinit var fixture: ConnectionFixture
        fixture = fixture(
            currentSourceId = "source-old",
            onSourceChanging = { previous, next ->
                val published = fixture.store.state.value.shell.connectionSettings.currentSourceId
                transitions += "$previous->$next:$published"
            },
        )

        fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))

        assertEquals(listOf("source-old->source-1:source-old"), transitions)
        assertEquals("source-1", fixture.store.state.value.shell.connectionSettings.currentSourceId)
    }

    @Test
    fun switchingAmongMultipleSavedSourcesPublishesExactlyOneCurrentSource() = kotlinx.coroutines.test.runTest {
        val records = listOf(
            savedRecord(),
            savedRecord(
                id = "source-2",
                displayName = "Studio Music",
                serverUrl = "https://studio.example",
            ),
        )
        val transitions = mutableListOf<Pair<String?, String>>()
        val fixture = fixture(
            savedRecords = records,
            onSourceChanging = { previous, next -> transitions += previous to next },
        )

        fixture.controller.execute(
            NaviampCoreCommand.Connection.ConnectSaved(
                NaviampSavedConnectionUi(
                    id = "source-2",
                    displayName = "Studio Music",
                    serverUrl = "https://studio.example",
                    username = "demo",
                ),
            ),
        )

        assertEquals(NaviampCoreConnectionRequest.Saved("source-2"), fixture.port.connectRequests.single().first)
        assertEquals(listOf<Pair<String?, String>>("source-1" to "source-2"), transitions)
        val settings = fixture.store.state.value.shell.connectionSettings
        assertEquals("source-2", settings.currentSourceId)
        assertEquals(listOf(false, true), settings.connection.savedConnections.map { it.current })
        assertEquals("Connected to Studio Music.", settings.connection.status)
    }

    @Test
    fun startupRestoresThePreferredSavedConnectionInCore() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(currentSourceId = null)

        fixture.controller.restoreInitialConnection()

        val (request, plan) = fixture.port.connectRequests.single()
        assertEquals(NaviampCoreConnectionRequest.Saved("source-1"), request)
        assertTrue(plan.restoreSavedSession)
        assertTrue(fixture.store.state.value.shell.connectionSettings.connection.connected)
    }

    @Test
    fun startupDoesNothingWithoutASavedConnection() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(hasSavedConnection = false)

        fixture.controller.restoreInitialConnection()

        assertTrue(fixture.port.connectRequests.isEmpty())
        assertFalse(fixture.store.state.value.shell.connectionSettings.connection.connected)
    }

    @Test
    fun unreachableSavedConnectionRestoresTheLocalShellAndOfflineContent() = kotlinx.coroutines.test.runTest {
        var offlineSourceId: String? = null
        val fixture = fixture(
            connectFailure = IllegalStateException("Failed to connect to server"),
            onOfflineRestored = { offlineSourceId = it },
        )

        fixture.controller.restoreInitialConnection()

        val connection = fixture.store.state.value.shell.connectionSettings.connection
        assertTrue(connection.connected)
        assertFalse(connection.statusIsError)
        assertEquals("Offline. Downloaded music remains available.", connection.status)
        assertEquals("source-1", fixture.store.state.value.shell.connectionSettings.currentSourceId)
        assertEquals("source-1", offlineSourceId)
    }

    @Test
    fun unreachableSavedSourceSwitchMakesTheSelectedOfflineSourceAuthoritative() =
        kotlinx.coroutines.test.runTest {
            val records = listOf(
                savedRecord(),
                savedRecord("source-2", "Studio Music", "https://studio.example"),
            )
            val transitions = mutableListOf<Pair<String?, String>>()
            var offlineSourceId: String? = null
            val fixture = fixture(
                connectFailure = IllegalStateException("Failed to connect to server"),
                savedRecords = records,
                onSourceChanging = { previous, next -> transitions += previous to next },
                onOfflineRestored = { offlineSourceId = it },
            )

            fixture.controller.execute(
                NaviampCoreCommand.Connection.ConnectSaved(
                    NaviampSavedConnectionUi(
                        id = "source-2",
                        displayName = "Studio Music",
                        serverUrl = "https://studio.example",
                        username = "demo",
                    ),
                ),
            )

            val settings = fixture.store.state.value.shell.connectionSettings
            assertEquals("source-2", settings.currentSourceId)
            assertEquals(listOf(false, true), settings.connection.savedConnections.map { it.current })
            assertEquals(listOf<Pair<String?, String>>("source-1" to "source-2"), transitions)
            assertEquals("source-2", offlineSourceId)
            assertEquals("Offline. Downloaded music remains available.", settings.connection.status)
        }

    @Test
    fun authenticationFailureDoesNotEnterOfflineMode() = kotlinx.coroutines.test.runTest {
        var offlineRestorations = 0
        val fixture = fixture(
            connectFailure = IllegalStateException("HTTP 401"),
            onOfflineRestored = { offlineRestorations += 1 },
        )

        fixture.controller.restoreInitialConnection()

        val connection = fixture.store.state.value.shell.connectionSettings.connection
        assertFalse(connection.connected)
        assertTrue(connection.statusIsError)
        assertEquals(0, offlineRestorations)
    }

    @Test
    fun failuresBecomeCommonConnectionStateInsteadOfHostMessages() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(connectFailure = IllegalStateException("Server unavailable"))
        fixture.controller.dispatch(
            NaviampCoreCommand.Connection.ChangeForm(
                ConnectionFormState(serverUrl = "https://music.example", username = "demo", password = "secret"),
            ),
        )

        fixture.controller.execute(NaviampCoreCommand.Connection.Connect)

        val state = fixture.store.state.value.shell.connectionSettings.connection
        assertFalse(state.connected)
        assertFalse(state.isConnecting)
        assertTrue(state.statusIsError)
        assertEquals("Server unavailable", state.status)
    }

    @Test
    fun validatesNewConnectionsBeforeInvokingAHostAndRetainsEditIdentityForCredentialReuse() =
        kotlinx.coroutines.test.runTest {
            val fixture = fixture()

            fixture.controller.execute(NaviampCoreCommand.Connection.Connect)

            assertTrue(fixture.port.connectRequests.isEmpty())
            assertEquals(
                "Enter a server URL and username.",
                fixture.store.state.value.shell.connectionSettings.connection.status,
            )
            assertTrue(fixture.store.state.value.shell.connectionSettings.connection.statusIsError)

            val saved = savedConnectionUi()
            fixture.controller.execute(NaviampCoreCommand.Connection.Edit(saved))
            fixture.controller.execute(NaviampCoreCommand.Connection.Connect)

            assertEquals(
                NaviampCoreConnectionRequest.Form(
                    ConnectionFormState(serverUrl = "https://edited.example", username = "demo"),
                    savedConnectionId = "source-1",
                ),
                fixture.port.connectRequests.single().first,
            )
        }

    @Test
    fun editAndDeleteFlowsAreCoreTransactions() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()
        val saved = savedConnectionUi()

        fixture.controller.execute(NaviampCoreCommand.Connection.Edit(saved))
        val editing = fixture.store.state.value.shell.connectionSettings.connection
        assertTrue(editing.editingConnection)
        assertTrue(editing.editingSavedConnection)
        assertEquals("https://edited.example", editing.form.serverUrl)

        fixture.controller.execute(NaviampCoreCommand.Connection.Delete(saved))
        val connection = fixture.store.state.value.shell.connectionSettings.connection
        assertEquals(emptyList<NaviampSavedConnectionUi>(), connection.savedConnections)
        assertEquals("Deleted Home Music.", connection.status)
    }

    @Test
    fun databaseResetDropsTheLiveSessionAndPublishesAnEmptyDisconnectedInventory() =
        kotlinx.coroutines.test.runTest {
            val fixture = fixture()

            fixture.controller.resetAfterDatabaseClear()

            val settings = fixture.store.state.value.shell.connectionSettings
            assertTrue(fixture.port.activeSessionCleared)
            assertEquals(null, settings.currentSourceId)
            assertTrue(settings.connection.savedConnections.isEmpty())
            assertFalse(settings.connection.connected)
            assertEquals("Database reset.", settings.connection.status)
        }

    @Test
    fun musicFolderFailuresRemainVisibleWhileCoreKeepsTheConnectionEditable() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(musicFoldersLoadFailed = true)

        fixture.controller.execute(NaviampCoreCommand.Connection.Edit(savedConnectionUi()))

        val connection = fixture.store.state.value.shell.connectionSettings.connection
        assertTrue(connection.editingConnection)
        assertEquals(emptyList(), connection.availableMusicFolders)
        assertEquals(
            "Could not load music folders. You can still edit the connection.",
            connection.musicFoldersStatus,
        )
    }

    private fun fixture(
        connectFailure: Throwable? = null,
        musicFoldersLoadFailed: Boolean = false,
        onConnected: (String) -> Unit = {},
        onOfflineRestored: (String) -> Unit = {},
        onSourceChanging: (String?, String) -> Unit = { _, _ -> },
        currentSourceId: String? = "source-1",
        hasSavedConnection: Boolean = true,
        savedRecords: List<NaviampCoreSavedConnectionRecord> = listOf(savedRecord()),
    ): ConnectionFixture {
        val inventory = NaviampCoreConnectionInventory(
            connections = savedRecords.takeIf { hasSavedConnection }.orEmpty(),
            currentSourceId = currentSourceId?.takeIf { hasSavedConnection },
        )
        val port = FakeProviderSessionPort(inventory, connectFailure, musicFoldersLoadFailed)
        val store = NaviampCoreStateStore()
        return ConnectionFixture(
            store = store,
            port = port,
            controller = NaviampCoreConnectionController(
                connection = NaviampConnectionController(),
                stateStore = store,
                sessionPort = port,
                initialInventory = inventory,
                onSourceChanging = onSourceChanging,
                onConnected = onConnected,
                onOfflineRestored = onOfflineRestored,
            ),
        )
    }

    private fun savedRecord(
        id: String = "source-1",
        displayName: String = "Home Music",
        serverUrl: String = "https://music.example",
    ) = NaviampCoreSavedConnectionRecord(
        id = id,
        displayName = displayName,
        serverUrl = serverUrl,
        username = "demo",
    )

    private fun savedConnectionUi() = NaviampSavedConnectionUi(
        id = "source-1",
        displayName = "Home Music",
        serverUrl = "https://music.example",
        username = "demo",
    )
}

private data class ConnectionFixture(
    val store: NaviampCoreStateStore,
    val port: FakeProviderSessionPort,
    val controller: NaviampCoreConnectionController,
)

private class FakeProviderSessionPort(
    initialInventory: NaviampCoreConnectionInventory,
    private val connectFailure: Throwable?,
    private val musicFoldersLoadFailed: Boolean,
) : NaviampCoreProviderSessionPort {
    var inventory = initialInventory
    var refreshCalls = 0
    var activeSessionCleared = false
    val connectRequests = mutableListOf<Pair<NaviampCoreConnectionRequest, NaviampConnectionAttemptPlan>>()

    override suspend fun connect(
        request: NaviampCoreConnectionRequest,
        plan: NaviampConnectionAttemptPlan,
    ): NaviampCoreConnectedSession {
        connectRequests += request to plan
        connectFailure?.let { throw it }
        val sourceId = (request as? NaviampCoreConnectionRequest.Saved)?.id ?: "source-1"
        val saved = inventory.connections.firstOrNull { it.id == sourceId }
        inventory = inventory.copy(currentSourceId = sourceId)
        return NaviampCoreConnectedSession(
            sourceId = sourceId,
            displayName = saved?.displayName ?: "Home Music",
            serverVersion = "1.2.3",
            inventory = inventory,
        )
    }

    override suspend fun editableConnection(id: String) = NaviampCoreEditableConnection(
        form = ConnectionFormState(serverUrl = "https://edited.example", username = "demo"),
        musicFoldersLoadFailed = musicFoldersLoadFailed,
    )

    override suspend fun deleteConnection(id: String): NaviampCoreConnectionInventory {
        inventory = NaviampCoreConnectionInventory()
        return inventory
    }

    override suspend fun refreshActiveSession(): Boolean {
        refreshCalls += 1
        return true
    }
    override suspend fun smartPlaylistProvider(password: String?) = null
    override suspend fun persistActiveSession() = Unit
    override suspend fun clearActiveSession() {
        activeSessionCleared = true
    }
}
