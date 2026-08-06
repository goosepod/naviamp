package app.naviamp.presentation

import app.naviamp.app.NaviampConnectionController
import app.naviamp.app.NaviampConnectionAttemptPlan
import app.naviamp.app.NaviampConnectionPhase
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.connectionFormError
import app.naviamp.domain.settings.selectedMusicFolderSummary
import app.naviamp.domain.source.connectionFailureStatus
import app.naviamp.ui.NaviampSavedConnectionUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/** Core owns provider-session renewal timing; hosts only execute and persist one renewal attempt. */
class NaviampCoreProviderSessionLifecycle(
    private val sessionPort: NaviampCoreProviderSessionPort,
    private val refreshIntervalMillis: Long = ProviderSessionRefreshIntervalMillis,
    private val waitForNextRefresh: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun refreshNow() {
        runCatching { sessionPort.refreshActiveSession() }
    }

    suspend fun maintainWhileMounted() {
        while (currentCoroutineContext().isActive) {
            waitForNextRefresh(refreshIntervalMillis)
            refreshNow()
        }
    }
}

const val ProviderSessionRefreshIntervalMillis: Long = 30L * 60L * 1_000L

class NaviampCoreConnectionController(
    private val connection: NaviampConnectionController,
    private val stateStore: NaviampCoreStateStore,
    private val sessionPort: NaviampCoreProviderSessionPort,
    initialInventory: NaviampCoreConnectionInventory = NaviampCoreConnectionInventory(),
    private val onSourceChanging: (previousSourceId: String?, newSourceId: String) -> Unit = { _, _ -> },
    private val onConnected: (String) -> Unit = {},
) : NaviampCoreCommandController {
    private var inventory = initialInventory
    private var editingConnectionId: String? = null

    init {
        publishConnection()
    }

    /** Publishes repository changes made by a shared settings import without reconnecting. */
    fun replaceSavedConnections(connections: List<NaviampCoreSavedConnectionRecord>) {
        inventory = inventory.copy(connections = connections)
        publishConnection()
    }

    suspend fun resetAfterDatabaseClear() {
        sessionPort.clearActiveSession()
        inventory = NaviampCoreConnectionInventory()
        editingConnectionId = null
        connection.disconnected("Database reset.")
        stateStore.updateShell { shell ->
            shell.copy(
                connectionSettings = shell.connectionSettings.copy(
                    connection = shell.connectionSettings.connection.copy(
                        editingConnection = false,
                        form = ConnectionFormState(),
                        availableMusicFolders = emptyList(),
                        musicFoldersStatus = null,
                    ),
                ),
            )
        }
        publishConnection()
    }

    /** Restores the most recently used saved source without requiring host startup policy. */
    suspend fun restoreInitialConnection() {
        if (connection.state.value.connected || connection.state.value.isConnecting) return
        val saved = inventory.currentSourceId
            ?.let { currentId -> inventory.connections.firstOrNull { it.id == currentId } }
            ?: inventory.connections.firstOrNull()
            ?: return
        connect(NaviampCoreConnectionRequest.Saved(saved.id))
    }

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult {
        val connectionCommand = command as? NaviampCoreCommand.Connection
            ?: return NaviampCoreImmediateCommandResult.Unhandled
        when (connectionCommand) {
            is NaviampCoreCommand.Connection.ChangeForm -> updateForm(connectionCommand.form)
            NaviampCoreCommand.Connection.New -> openNewForm()
            NaviampCoreCommand.Connection.CancelForm -> setEditing(false)
            NaviampCoreCommand.Connection.Connect,
            NaviampCoreCommand.Connection.EditCurrent,
            is NaviampCoreCommand.Connection.Edit,
            is NaviampCoreCommand.Connection.Delete,
            is NaviampCoreCommand.Connection.ConnectSaved,
            -> return NaviampCoreImmediateCommandResult.Deferred
        }
        return NaviampCoreImmediateCommandResult.Handled()
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        when (command) {
            NaviampCoreCommand.Connection.Connect -> connect(
                NaviampCoreConnectionRequest.Form(
                    stateStore.state.value.shell.connectionSettings.connection.form,
                    editingConnectionId,
                ),
            )
            is NaviampCoreCommand.Connection.ConnectSaved ->
                connect(NaviampCoreConnectionRequest.Saved(command.connection.id))
            NaviampCoreCommand.Connection.EditCurrent -> {
                val id = inventory.currentSourceId
                if (id == null) publishStatus("No active connection to edit.") else edit(id)
            }
            is NaviampCoreCommand.Connection.Edit -> edit(command.connection.id)
            is NaviampCoreCommand.Connection.Delete -> delete(command.connection)
            else -> return null
        }
        return NaviampCoreCommandResult.Completed
    }

    private suspend fun connect(request: NaviampCoreConnectionRequest) {
        if (request is NaviampCoreConnectionRequest.Form) {
            connectionFormError(
                form = request.form,
                hasSavedConnectionForLogin = request.savedConnectionId != null,
            )?.let { error ->
                connection.failed(error)
                publishConnection()
                return
            }
        }
        val plan = connection.begin(restoreSavedSession = request is NaviampCoreConnectionRequest.Saved)
            ?: return
        val previousSourceId = stateStore.state.value.shell.connectionSettings.currentSourceId
        publishConnection()
        runCatching { sessionPort.connect(request, plan) }
            .onSuccess { session ->
                if (previousSourceId != session.sourceId || plan.clearExistingPlayback) {
                    onSourceChanging(previousSourceId, session.sourceId)
                }
                inventory = session.inventory
                editingConnectionId = null
                connection.connected(
                    sourceId = session.sourceId,
                    serverVersion = session.serverVersion,
                    status = "Connected to ${session.displayName}.",
                )
                stateStore.updateShell { shell ->
                    shell.copy(
                        connectionSettings = shell.connectionSettings.copy(
                            connection = shell.connectionSettings.connection.copy(editingConnection = false),
                        ),
                    )
                }
                publishConnection()
                onConnected(session.sourceId)
            }
            .onFailure { cause ->
                connection.failed(connectionFailureStatus(cause, "Could not connect to the music server."))
                publishConnection()
            }
    }

    private suspend fun edit(id: String) {
        runCatching { sessionPort.editableConnection(id) }
            .onSuccess { editable ->
                editingConnectionId = id
                stateStore.updateShell { shell ->
                    shell.copy(
                        connectionSettings = shell.connectionSettings.copy(
                            connection = shell.connectionSettings.connection.copy(
                                editingConnection = true,
                                form = editable.form,
                                availableMusicFolders = editable.availableMusicFolders,
                                musicFoldersStatus = if (editable.musicFoldersLoadFailed) {
                                    "Could not load music folders. You can still edit the connection."
                                } else {
                                    null
                                },
                            ),
                        ),
                    )
                }
            }
            .onFailure { cause -> publishStatus(cause.message ?: "Connection not found.") }
    }

    private suspend fun delete(saved: NaviampSavedConnectionUi) {
        runCatching { sessionPort.deleteConnection(saved.id) }
            .onSuccess { updated ->
                inventory = updated
                if (connection.state.value.sourceId == saved.id && updated.currentSourceId == null) {
                    connection.disconnected("Deleted ${saved.displayName}.")
                } else {
                    publishStatus("Deleted ${saved.displayName}.")
                }
                publishConnection()
            }
            .onFailure { cause -> publishStatus(cause.message ?: "Could not delete ${saved.displayName}.") }
    }

    private fun updateForm(form: ConnectionFormState) {
        stateStore.updateShell { shell ->
            shell.copy(
                connectionSettings = shell.connectionSettings.copy(
                    connection = shell.connectionSettings.connection.copy(form = form),
                ),
            )
        }
    }

    private fun openNewForm() {
        editingConnectionId = null
        stateStore.updateShell { shell ->
            shell.copy(
                connectionSettings = shell.connectionSettings.copy(
                    connection = shell.connectionSettings.connection.copy(
                        editingConnection = true,
                        form = ConnectionFormState(),
                        availableMusicFolders = emptyList(),
                        musicFoldersStatus = null,
                    ),
                ),
            )
        }
    }

    private fun setEditing(editing: Boolean) {
        if (!editing) editingConnectionId = null
        stateStore.updateShell { shell ->
            shell.copy(
                connectionSettings = shell.connectionSettings.copy(
                    connection = shell.connectionSettings.connection.copy(editingConnection = editing),
                ),
            )
        }
    }

    private fun publishStatus(status: String) {
        stateStore.updateShell { shell ->
            shell.copy(
                connectionSettings = shell.connectionSettings.copy(
                    connection = shell.connectionSettings.connection.copy(status = status),
                ),
            )
        }
    }

    private fun publishConnection() {
        val runtime = connection.state.value
        val currentId = inventory.currentSourceId ?: runtime.sourceId
        val availableFolders = stateStore.state.value.shell.connectionSettings.connection.availableMusicFolders
        val savedConnections = inventory.connections.map { saved ->
            NaviampSavedConnectionUi(
                id = saved.id,
                displayName = saved.displayName,
                serverUrl = saved.serverUrl,
                username = saved.username,
                selectedLibrarySummary = selectedMusicFolderSummary(
                    selectedIds = saved.selectedMusicFolderIds,
                    availableFolders = availableFolders,
                ),
                current = saved.id == currentId,
            )
        }
        stateStore.updateShell { shell ->
            shell.copy(
                connectionSettings = shell.connectionSettings.copy(
                    currentSourceId = currentId,
                    connection = shell.connectionSettings.connection.copy(
                        status = runtime.status ?: shell.connectionSettings.connection.status,
                        statusIsError = runtime.phase == NaviampConnectionPhase.Failed,
                        serverVersion = runtime.serverVersion,
                        connected = runtime.connected,
                        restoringConnection = runtime.restoringConnection,
                        isConnecting = runtime.isConnecting,
                        savedConnections = savedConnections,
                        hasSavedConnection = savedConnections.isNotEmpty(),
                    ),
                ),
            )
        }
    }
}
