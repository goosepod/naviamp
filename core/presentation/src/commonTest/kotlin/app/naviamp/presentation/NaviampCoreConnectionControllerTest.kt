package app.naviamp.presentation

import app.naviamp.app.NaviampConnectionAttemptPlan
import app.naviamp.app.NaviampConnectionController
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.ui.NaviampSavedConnectionUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampCoreConnectionControllerTest {
    @Test
    fun formAndEditingStateAreOwnedImmediatelyByCore() {
        val fixture = fixture()
        val form = ConnectionFormState(serverUrl = "https://music.example", username = "demo")

        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(form))
        fixture.controller.dispatch(NaviampCoreCommand.Connection.New)

        val state = fixture.store.state.value.shell.connectionSettings.connection
        assertTrue(state.editingConnection)
        assertEquals(ConnectionFormState(), state.form)
    }

    @Test
    fun successfulConnectionUsesSharedAttemptPolicyAndPublishesOneSnapshot() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()
        val form = ConnectionFormState(serverUrl = "https://music.example", username = "demo")
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
    fun failuresBecomeCommonConnectionStateInsteadOfHostMessages() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(connectFailure = IllegalStateException("Server unavailable"))

        fixture.controller.execute(NaviampCoreCommand.Connection.Connect)

        val state = fixture.store.state.value.shell.connectionSettings.connection
        assertFalse(state.connected)
        assertFalse(state.isConnecting)
        assertEquals("Server unavailable", state.status)
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

    private fun fixture(connectFailure: Throwable? = null): ConnectionFixture {
        val record = savedRecord()
        val inventory = NaviampCoreConnectionInventory(listOf(record), currentSourceId = record.id)
        val port = FakeProviderSessionPort(inventory, connectFailure)
        val store = NaviampCoreStateStore()
        return ConnectionFixture(
            store = store,
            port = port,
            controller = NaviampCoreConnectionController(
                connection = NaviampConnectionController(),
                stateStore = store,
                sessionPort = port,
                initialInventory = inventory,
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
) : NaviampCoreProviderSessionPort {
    var inventory = initialInventory
    val connectRequests = mutableListOf<Pair<NaviampCoreConnectionRequest, NaviampConnectionAttemptPlan>>()

    override suspend fun connect(
        request: NaviampCoreConnectionRequest,
        plan: NaviampConnectionAttemptPlan,
    ): NaviampCoreConnectedSession {
        connectRequests += request to plan
        connectFailure?.let { throw it }
        return NaviampCoreConnectedSession(
            sourceId = "source-1",
            displayName = "Home Music",
            serverVersion = "1.2.3",
            inventory = inventory,
        )
    }

    override suspend fun editableConnection(id: String) = NaviampCoreEditableConnection(
        form = ConnectionFormState(serverUrl = "https://edited.example", username = "demo"),
    )

    override suspend fun deleteConnection(id: String): NaviampCoreConnectionInventory {
        inventory = NaviampCoreConnectionInventory()
        return inventory
    }
}
