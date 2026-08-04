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
        assertEquals(ConnectionFormState(), state.form)
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
        assertTrue(fixture.store.state.value.shell.connectionSettings.connection.editingConnection)
        assertEquals("https://edited.example", fixture.store.state.value.shell.connectionSettings.connection.form.serverUrl)

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
        onSourceChanging: (String?, String) -> Unit = { _, _ -> },
        currentSourceId: String? = "source-1",
        hasSavedConnection: Boolean = true,
    ): ConnectionFixture {
        val record = savedRecord()
        val inventory = NaviampCoreConnectionInventory(
            connections = listOfNotNull(record.takeIf { hasSavedConnection }),
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
            ),
        )
    }

    private fun savedRecord() = NaviampCoreSavedConnectionRecord(
        id = "source-1",
        displayName = "Home Music",
        serverUrl = "https://music.example",
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
        inventory = inventory.copy(currentSourceId = "source-1")
        return NaviampCoreConnectedSession(
            sourceId = "source-1",
            displayName = "Home Music",
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
