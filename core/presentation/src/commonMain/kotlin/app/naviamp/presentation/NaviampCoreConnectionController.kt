package app.naviamp.presentation

import app.naviamp.app.NaviampConnectionController
import app.naviamp.app.NaviampConnectionAttemptPlan
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.selectedMusicFolderSummary
import app.naviamp.ui.NaviampSavedConnectionUi

data class NaviampCoreSavedConnectionRecord(
    val id: String,
    val displayName: String,
    val serverUrl: String,
    val username: String,
    val selectedMusicFolderIds: List<String> = emptyList(),
)

data class NaviampCoreConnectionInventory(
    val connections: List<NaviampCoreSavedConnectionRecord> = emptyList(),
    val currentSourceId: String? = null,
)

data class NaviampCoreEditableConnection(
    val form: ConnectionFormState,
    val availableMusicFolders: List<ConnectionFormMusicFolder> = emptyList(),
)

sealed interface NaviampCoreConnectionRequest {
    data class Form(val form: ConnectionFormState) : NaviampCoreConnectionRequest
    data class Saved(val id: String) : NaviampCoreConnectionRequest
}

data class NaviampCoreConnectedSession(
    val sourceId: String,
    val displayName: String,
    val serverVersion: String? = null,
    val inventory: NaviampCoreConnectionInventory,
)

/** Credential access and provider-session construction are effects; all connection policy is Core. */
interface NaviampCoreProviderSessionPort {
    suspend fun connect(
        request: NaviampCoreConnectionRequest,
        plan: NaviampConnectionAttemptPlan,
    ): NaviampCoreConnectedSession
    suspend fun editableConnection(id: String): NaviampCoreEditableConnection
    suspend fun deleteConnection(id: String): NaviampCoreConnectionInventory
}

class NaviampCoreConnectionController(
    private val connection: NaviampConnectionController,
    private val stateStore: NaviampCoreStateStore,
    private val sessionPort: NaviampCoreProviderSessionPort,
    initialInventory: NaviampCoreConnectionInventory = NaviampCoreConnectionInventory(),
) : NaviampCoreCommandController {
    private var inventory = initialInventory

    init {
        publishConnection()
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
        val plan = connection.begin(restoreSavedSession = request is NaviampCoreConnectionRequest.Saved)
            ?: return
        publishConnection()
        runCatching { sessionPort.connect(request, plan) }
            .onSuccess { session ->
                inventory = session.inventory
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
            }
            .onFailure { cause ->
                connection.failed(cause.message ?: "Could not connect to the music server.")
                publishConnection()
            }
    }

    private suspend fun edit(id: String) {
        runCatching { sessionPort.editableConnection(id) }
            .onSuccess { editable ->
                stateStore.updateShell { shell ->
                    shell.copy(
                        connectionSettings = shell.connectionSettings.copy(
                            connection = shell.connectionSettings.connection.copy(
                                editingConnection = true,
                                form = editable.form,
                                availableMusicFolders = editable.availableMusicFolders,
                                musicFoldersStatus = null,
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
