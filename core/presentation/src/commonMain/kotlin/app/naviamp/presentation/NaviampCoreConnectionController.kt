package app.naviamp.presentation

import app.naviamp.app.NaviampConnectionController
import app.naviamp.app.NaviampConnectionAttemptPlan
import app.naviamp.app.NaviampConnectionPhase
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.connectionFormError
import app.naviamp.domain.settings.defaultSelectedMusicFolderIds
import app.naviamp.domain.settings.selectedMusicFolderSummary
import app.naviamp.domain.settings.toggleSelectedMusicFolderId
import app.naviamp.domain.source.connectionFailureStatus
import app.naviamp.domain.source.connectionFailureAllowsOfflineRestoration
import app.naviamp.ui.NaviampSavedConnectionUi
import app.naviamp.ui.NaviampLibrarySourcePickerUi
import app.naviamp.ui.NaviampLibrarySourcePickerError
import app.naviamp.ui.NaviampAccountSwitcherUi
import app.naviamp.ui.NaviampAccountSwitcherError
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive

/** Core owns provider-session renewal timing; hosts only execute and persist one renewal attempt. */
class NaviampCoreProviderSessionLifecycle(
    private val sessionPort: NaviampCoreProviderSessionPort,
    private val refreshIntervalMillis: Long = ProviderSessionRefreshIntervalMillis,
    private val waitForNextRefresh: suspend (Long) -> Unit = { delay(it) },
) {
    private var maintenanceJob: Job? = null

    /** Runtime ownership keeps renewal alive when a phone or external scene disconnects. */
    fun start(scope: CoroutineScope): Job = maintenanceJob?.takeIf { it.isActive }
        ?: scope.launch { maintainWhileMounted() }.also { maintenanceJob = it }

    fun close() {
        maintenanceJob?.cancel()
        maintenanceJob = null
    }

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

/**
 * When an offline-restored session retries its server. [networkPollMillis] bounds how soon a
 * returning network is noticed; [backoffMillis] spaces attempts while the server stays unreachable,
 * and its last entry is the cap.
 */
data class NaviampCoreReconnectSchedule(
    val networkPollMillis: Long = 3_000L,
    val backoffMillis: List<Long> = listOf(5_000L, 15_000L, 30_000L, 60_000L, 120_000L, 300_000L),
) {
    fun delayAfter(failures: Int): Long = backoffMillis[failures.coerceIn(0, backoffMillis.lastIndex)]
}

class NaviampCoreConnectionController(
    private val connection: NaviampConnectionController,
    private val stateStore: NaviampCoreStateStore,
    private val sessionPort: NaviampCoreProviderSessionPort,
    initialInventory: NaviampCoreConnectionInventory = NaviampCoreConnectionInventory(),
    private val onSourceChanging: (previousSourceId: String?, newSourceId: String) -> Unit = { _, _ -> },
    private val onConnected: (String) -> Unit = {},
    private val onUserConnected: (String) -> Unit = {},
    private val onOfflineRestored: (String) -> Unit = {},
    private val certificateFiles: NaviampCoreCertificateFilePort? = null,
    private val onCompanionChanged: (String) -> Unit = {},
    private val onSavedSourceSelected: (String, Boolean) -> Unit = { id, _ -> onConnected(id) },
    /** The host's network fact; null when the host cannot observe one, so Core never assumes "no network". */
    private val networkMetering: app.naviamp.domain.network.NetworkMeteringSource? = null,
    /** The user's explicit Offline Mode: no automatic connection attempts while it is on. */
    private val offlineModeEnabled: () -> Boolean = { false },
    /** A background reconnect from offline refreshes content but must not restart or replace playback. */
    private val onReconnected: (String) -> Unit = {},
    private val recoveryScope: CoroutineScope? = null,
    private val reconnectSchedule: NaviampCoreReconnectSchedule = NaviampCoreReconnectSchedule(),
) : NaviampCoreCommandController {
    private var inventory = initialInventory
    private var editingConnectionId: String? = null
    private var originalEditingForm: ConnectionFormState? = null
    private var librarySourceConnection: NaviampCoreEditableConnection? = null
    private var accountAttempt: Job? = null
    private var recovery: Job? = null
    private var runtimeRecoveryScope: CoroutineScope? = null
    private var certificateSelectionGeneration = 0L

    init {
        publishConnection()
    }

    /** Publishes repository changes made by a shared settings import without reconnecting. */
    fun replaceSavedConnections(connections: List<NaviampCoreSavedConnectionRecord>) {
        sessionPort.replaceSavedConnections(connections)
        inventory = inventory.copy(connections = connections)
        publishConnection()
    }

    /**
     * Opens an imported profile for its local password. Connecting reconnects that saved profile
     * rather than adding a new connection that would duplicate it.
     */
    fun editImportedConnection(id: String, form: ConnectionFormState) {
        editingConnectionId = id
        originalEditingForm = form
        stateStore.updateShell { shell ->
            shell.copy(
                connectionSettings = shell.connectionSettings.copy(
                    connection = shell.connectionSettings.connection.copy(
                        editingConnection = true,
                        editingSavedConnection = true,
                        saveWithoutReconnect = false,
                        form = form,
                    ),
                ),
            )
        }
    }

    /**
     * Reconnects a saved connection that an import matched and that already holds credentials,
     * so importing settings never asks again for a password this device already has.
     */
    suspend fun reconnectImportedConnection(id: String): Boolean =
        connect(NaviampCoreConnectionRequest.Saved(id))

    suspend fun resetAfterDatabaseClear() {
        recovery?.cancel()
        recovery = null
        certificateSelectionGeneration++
        accountAttempt?.cancel()
        accountAttempt?.join()
        accountAttempt = null
        updateAccounts { NaviampAccountSwitcherUi() }
        sessionPort.clearActiveSession()
        inventory = NaviampCoreConnectionInventory()
        editingConnectionId = null
        originalEditingForm = null
        connection.disconnected("Database reset.")
        stateStore.updateShell { shell ->
            shell.copy(
                connectionSettings = shell.connectionSettings.copy(
                    connection = shell.connectionSettings.connection.copy(
                        editingConnection = false,
                        editingSavedConnection = false,
                        saveWithoutReconnect = false,
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
        // An attempt that cannot succeed only delays offline content behind the provider timeouts (#702).
        if (offlineModeEnabled() || networkDisconnected()) {
            enterOffline(saved.id, previousSourceId = stateStore.state.value.shell.connectionSettings.currentSourceId,
                clearExistingPlayback = false)
            return
        }
        connect(NaviampCoreConnectionRequest.Saved(saved.id), userInitiated = false)
    }

    private fun networkDisconnected(): Boolean =
        networkMetering?.current() == app.naviamp.domain.network.NetworkMetering.Disconnected

    /**
     * While an offline-restored session lasts, retries the saved source: at once when the network
     * returns, otherwise on a capped backoff. Offline Mode and a known-missing network skip attempts.
     * Any user connection, switch or disconnect leaves offline and ends the loop.
     */
    private fun startRecovery(sourceId: String) {
        val scope = runtimeRecoveryScope ?: recoveryScope ?: return
        recovery?.cancel()
        recovery = scope.launch { recover(sourceId) }
    }

    /**
     * Runtime-owned like provider-session renewal: the loop only runs once a host starts runtime
     * services. Starting it while already offline begins recovering at once.
     */
    fun startRecovery(scope: CoroutineScope) {
        if (runtimeRecoveryScope != null) return
        runtimeRecoveryScope = scope
        val runtime = connection.state.value
        val sourceId = runtime.sourceId
        if (runtime.offline && sourceId != null && recovery?.isActive != true) startRecovery(sourceId)
    }

    private suspend fun recover(sourceId: String) {
        var failures = 0
        var waitedMillis = 0L
        var networkWasMissing = networkDisconnected()
        while (currentCoroutineContext().isActive) {
            val runtime = connection.state.value
            if (!runtime.offline || runtime.sourceId != sourceId) return
            val missing = networkDisconnected()
            if (offlineModeEnabled() || missing || runtime.isConnecting) {
                networkWasMissing = networkWasMissing || missing
                delay(reconnectSchedule.networkPollMillis)
                continue
            }
            val due = reconnectSchedule.delayAfter(failures)
            if (!networkWasMissing && waitedMillis < due) {
                val step = minOf(reconnectSchedule.networkPollMillis, due - waitedMillis)
                delay(step)
                waitedMillis += step
                continue
            }
            networkWasMissing = false
            waitedMillis = 0L
            val reconnected = connect(
                NaviampCoreConnectionRequest.Saved(sourceId),
                preserveExistingSession = true,
                userInitiated = false,
                // A background success must not close a connection editor the user has open.
                preserveEditor = true,
                onSessionConnected = onReconnected,
            )
            if (reconnected) return
            failures += 1
        }
    }

    /** Validates a one-time setup password through the existing source, without clearing playback/cache. */
    internal suspend fun updateProvisioningCredential(sourceId: String, password: String): Boolean {
        if (password.isBlank() || sessionPort.currentSourceId() != sourceId) return false
        val editable = sessionPort.currentProvisioningConnection() ?: return false
        if (sessionPort.currentSourceId() != sourceId) return false
        return connect(
            NaviampCoreConnectionRequest.Form(editable.form.copy(password = password), sourceId),
            preserveExistingSession = true,
        )
    }

    /** Validates and commits an encrypted Connect offer through the normal provider-session owner. */
    internal suspend fun provisionConnect(form: ConnectionFormState): Boolean {
        val active = inventory.currentSourceId
            ?.let { currentId -> inventory.connections.firstOrNull { it.id == currentId } }
        if (
            connection.state.value.connected &&
            active != null &&
            active.providerId == form.providerId &&
            active.serverUrl.trim().trimEnd('/').equals(
                form.serverUrl.trim().trimEnd('/'),
                ignoreCase = true,
            ) &&
            active.username == form.username.trim()
        ) {
            val companion = form.aurralCompanion ?: return true
            if (connectionFormError(form, hasSavedConnectionForLogin = true) != null) return false
            return try {
                sessionPort.saveAurralCompanion(active.id, companion)
                onCompanionChanged(active.id)
                true
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        }
        return connect(NaviampCoreConnectionRequest.Form(form))
    }

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult {
        if (command is NaviampCoreCommand.Connection && command !is NaviampCoreCommand.Connection.ChooseCertificate) {
            certificateSelectionGeneration++
        }
        when (command) {
            is NaviampCoreCommand.Connection.ChangeForm -> updateForm(command.form)
            NaviampCoreCommand.Connection.New -> openNewForm()
            NaviampCoreCommand.Connection.CancelForm -> {
                val adding = accountSwitcher().addingAccount
                accountAttempt?.cancel()
                setEditing(false)
                if (adding) updateAccounts { NaviampAccountSwitcherUi(visible = true) }
            }
            NaviampCoreCommand.Connection.OpenAccounts -> updateAccounts {
                it.copy(visible = true, addingAccount = false, error = null)
            }
            NaviampCoreCommand.Connection.CloseAccounts -> {
                accountAttempt?.cancel()
                updateAccounts { it.copy(visible = false, error = null) }
            }
            NaviampCoreCommand.Connection.AddAccount -> {
                if (!connection.state.value.isConnecting) {
                    updateAccounts { NaviampAccountSwitcherUi(addingAccount = true) }
                    openNewForm()
                }
            }
            NaviampCoreCommand.Connection.Connect,
            is NaviampCoreCommand.Connection.ChooseCertificate,
            NaviampCoreCommand.Connection.EditCurrent,
            is NaviampCoreCommand.Connection.Edit,
            is NaviampCoreCommand.Connection.Delete,
            is NaviampCoreCommand.Connection.ConnectSaved,
            is NaviampCoreCommand.Connection.SwitchAccount,
            is NaviampCoreCommand.Connection.SelectSavedSource,
            -> return NaviampCoreImmediateCommandResult.Deferred
            is NaviampCoreCommand.Library.ToggleSource -> toggleLibrarySource(command.id)
            NaviampCoreCommand.Library.CancelSources -> closeLibrarySources()
            NaviampCoreCommand.Library.OpenSources,
            NaviampCoreCommand.Library.RetrySources,
            NaviampCoreCommand.Library.SaveSources,
            -> return NaviampCoreImmediateCommandResult.Deferred
            else -> return NaviampCoreImmediateCommandResult.Unhandled
        }
        return NaviampCoreImmediateCommandResult.Handled()
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        if (command is NaviampCoreCommand.Connection && command !is NaviampCoreCommand.Connection.ChooseCertificate) {
            certificateSelectionGeneration++
        }
        when (command) {
            is NaviampCoreCommand.Connection.ChooseCertificate -> chooseCertificate(command.kind, command.companion)
            NaviampCoreCommand.Connection.Connect -> {
                val request = NaviampCoreConnectionRequest.Form(
                    stateStore.state.value.shell.connectionSettings.connection.form, editingConnectionId)
                val savedId = request.savedConnectionId
                val companion = request.form.aurralCompanion
                if (savedId != null && companion != null && isCompanionOnlyEdit(request.form)) {
                    val error = connectionFormError(request.form, hasSavedConnectionForLogin = true)
                    if (error != null) {
                        stateStore.updateShell { shell -> shell.copy(connectionSettings = shell.connectionSettings.copy(
                            connection = shell.connectionSettings.connection.copy(status = error, statusIsError = true))) }
                    } else {
                        try {
                            sessionPort.saveAurralCompanion(savedId, companion)
                            setEditing(false)
                            onCompanionChanged(savedId)
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            stateStore.updateShell { shell -> shell.copy(connectionSettings = shell.connectionSettings.copy(
                                connection = shell.connectionSettings.connection.copy(status = "connection_aurral_companion_save_failed", statusIsError = true))) }
                        }
                    }
                } else if (accountSwitcher().addingAccount) connectAccount(request) else connect(request)
            }
            is NaviampCoreCommand.Connection.SelectSavedSource -> {
                val runtime = connection.state.value
                if (command.id.isBlank() || runtime.isConnecting || stateStore.state.value.shell.connect.remotePlaybackAuthorityActive ||
                    inventory.connections.none { it.id == command.id } ||
                    (command.reconnect && command.id != inventory.currentSourceId))
                    return NaviampCoreCommandResult.SavedSourceSelected(false)
                if (!command.reconnect && command.id == inventory.currentSourceId && runtime.connected)
                    return NaviampCoreCommandResult.SavedSourceSelected(true)
                val previousId = inventory.currentSourceId
                val selected = kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                    connect(NaviampCoreConnectionRequest.Saved(command.id), preserveExistingSession = true,
                        userInitiated = false, preserveEditor = true,
                        onSessionConnected = { id -> onSavedSourceSelected(id, id != previousId) })
                } == true
                if (selected && previousId != command.id) stateStore.updateShell { shell -> shell.copy(
                    connectionSettings = shell.connectionSettings.copy(
                        accountSelectionGeneration = shell.connectionSettings.accountSelectionGeneration + 1L)) }
                return NaviampCoreCommandResult.SavedSourceSelected(selected)
            }
            is NaviampCoreCommand.Connection.SwitchAccount -> {
                val id = command.connection.id
                when {
                    connection.state.value.isConnecting -> Unit
                    inventory.connections.none { it.id == id } -> updateAccounts {
                        it.copy(visible = true, error = NaviampAccountSwitcherError.ConnectionFailed)
                    }
                    id == inventory.currentSourceId && connection.state.value.connected -> {
                        onUserConnected(id)
                        completeAccountSelection()
                    }
                    else -> connectAccount(NaviampCoreConnectionRequest.Saved(id))
                }
            }
            is NaviampCoreCommand.Connection.ConnectSaved ->
                connect(NaviampCoreConnectionRequest.Saved(command.connection.id))
            NaviampCoreCommand.Connection.EditCurrent -> {
                val id = inventory.currentSourceId
                if (id == null) publishStatus("No active connection to edit.") else edit(id)
            }
            is NaviampCoreCommand.Connection.Edit -> edit(command.connection.id)
            is NaviampCoreCommand.Connection.Delete -> delete(command.connection)
            NaviampCoreCommand.Library.OpenSources,
            NaviampCoreCommand.Library.RetrySources,
            -> openLibrarySources()
            NaviampCoreCommand.Library.SaveSources -> saveLibrarySources()
            else -> return null
        }
        return NaviampCoreCommandResult.Completed
    }

    private suspend fun chooseCertificate(kind: NaviampConnectionCertificateKind, companion: Boolean = false) {
        val port = certificateFiles ?: return
        val before = stateStore.state.value.shell.connectionSettings.connection
        if (before.isConnecting) return
        val generation = ++certificateSelectionGeneration
        val editing = editingConnectionId
        val path = try { port.chooseAndCopy(kind) } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (generation != certificateSelectionGeneration) return
            stateStore.updateShell { shell -> shell.copy(connectionSettings = shell.connectionSettings.copy(
                connection = shell.connectionSettings.connection.copy(status = "connection_certificate_import_failed", statusIsError = true),
            )) }
            return
        }?.takeIf { it.isNotBlank() } ?: return
        // Do not apply a late picker result to another form or overwrite edits made while it was open.
        val current = stateStore.state.value.shell.connectionSettings.connection
        if (generation != certificateSelectionGeneration || editing != editingConnectionId || current.form != before.form || current.isConnecting) return
        if (companion) {
            val attachment = before.form.aurralCompanion ?: return
            val tls = when (kind) {
                NaviampConnectionCertificateKind.ServerAuthority -> attachment.tlsSettings.copy(customCertificatePath = path)
                NaviampConnectionCertificateKind.ClientIdentity -> attachment.tlsSettings.copy(clientCertificateKeyStorePath = path)
            }
            updateForm(before.form.copy(aurralCompanion = attachment.copy(tlsSettings = tls)))
            return
        }
        updateForm(when (kind) {
            NaviampConnectionCertificateKind.ServerAuthority -> before.form.copy(customCertificatePath = path)
            NaviampConnectionCertificateKind.ClientIdentity -> before.form.copy(clientCertificatePath = path)
        })
    }

    private suspend fun connect(
        request: NaviampCoreConnectionRequest,
        preserveExistingSession: Boolean = false,
        userInitiated: Boolean = true,
        forceSourceRefresh: Boolean = false,
        onPreservedFailure: () -> Unit = {},
        preserveEditor: Boolean = false,
        onSessionConnected: (String) -> Unit = onConnected,
    ): Boolean {
        if (request is NaviampCoreConnectionRequest.Form) {
            connectionFormError(
                form = request.form,
                hasSavedConnectionForLogin = request.savedConnectionId != null,
            )?.let { error ->
                if (!preserveExistingSession) {
                    connection.failed(error)
                    publishConnection()
                } else {
                    onPreservedFailure()
                }
                return false
            }
        }
        val previousStatus = stateStore.state.value.shell.connectionSettings.connection.status
        val previousConnection = connection.state.value
        val plan = connection.begin(
            restoreSavedSession = preserveExistingSession || request is NaviampCoreConnectionRequest.Saved,
            preserveExistingConnection = preserveExistingSession,
        )
            ?: return false
        val previousSourceId = stateStore.state.value.shell.connectionSettings.currentSourceId
        publishConnection()
        var connected = false
        runCatching {
            sessionPort.connect(request, plan).also { kotlinx.coroutines.currentCoroutineContext().ensureActive() }
        }.onSuccess { session ->
                if (previousSourceId != session.sourceId || plan.clearExistingPlayback || forceSourceRefresh) {
                    onSourceChanging(previousSourceId, session.sourceId)
                }
                inventory = session.inventory
                if (!preserveEditor) {
                    editingConnectionId = null
                    originalEditingForm = null
                }
                connection.connected(
                    sourceId = session.sourceId,
                    serverVersion = session.serverVersion,
                    status = "Connected to ${session.displayName}.",
                )
                if (!preserveEditor) stateStore.updateShell { shell ->
                    shell.copy(
                        connectionSettings = shell.connectionSettings.copy(
                            connection = shell.connectionSettings.connection.copy(
                                editingConnection = false,
                                editingSavedConnection = false,
                                saveWithoutReconnect = false,
                            ),
                        ),
                    )
                }
                publishConnection()
                onSessionConnected(session.sourceId)
                if (userInitiated) onUserConnected(session.sourceId)
                connected = true
            }
            .onFailure { cause ->
                if (cause is kotlinx.coroutines.CancellationException || preserveExistingSession) {
                    connection.restore(previousConnection)
                    publishConnection()
                    stateStore.updateShell { shell -> shell.copy(connectionSettings = shell.connectionSettings.copy(
                        connection = shell.connectionSettings.connection.copy(status = previousStatus))) }
                    if (cause is kotlinx.coroutines.CancellationException) throw cause
                    onPreservedFailure()
                    return@onFailure
                }
                val savedSourceId = (request as? NaviampCoreConnectionRequest.Saved)?.id
                if (savedSourceId != null && connectionFailureAllowsOfflineRestoration(cause)) {
                    enterOffline(savedSourceId, previousSourceId, plan.clearExistingPlayback)
                } else {
                    connection.failed(connectionFailureStatus(cause, "Could not connect to the music server."))
                    publishConnection()
                }
            }
        return connected
    }

    private fun enterOffline(sourceId: String, previousSourceId: String?, clearExistingPlayback: Boolean) {
        if (previousSourceId != sourceId || clearExistingPlayback) onSourceChanging(previousSourceId, sourceId)
        inventory = inventory.copy(currentSourceId = sourceId)
        connection.offline(
            sourceId = sourceId,
            status = "Offline. Downloaded music remains available.",
        )
        publishConnection()
        onOfflineRestored(sourceId)
        startRecovery(sourceId)
    }

    private fun accountSwitcher() = stateStore.state.value.shell.connectionSettings.accountSwitcher

    private fun updateAccounts(transform: (NaviampAccountSwitcherUi) -> NaviampAccountSwitcherUi) {
        stateStore.updateShell { shell -> shell.copy(connectionSettings = shell.connectionSettings.copy(
            accountSwitcher = transform(shell.connectionSettings.accountSwitcher))) }
    }

    private fun completeAccountSelection() {
        stateStore.updateShell { shell -> shell.copy(connectionSettings = shell.connectionSettings.copy(
            accountSwitcher = NaviampAccountSwitcherUi(),
            accountSelectionGeneration = shell.connectionSettings.accountSelectionGeneration + 1L)) }
    }

    /** A saved-account handoff is validated before clearing any of the current account's state. */
    private suspend fun connectAccount(request: NaviampCoreConnectionRequest) {
        if (connection.state.value.isConnecting) return
        val job = currentCoroutineContext()[Job]
        accountAttempt = job
        val adding = accountSwitcher().addingAccount
        updateAccounts { it.copy(connecting = true,
            switchingConnectionId = (request as? NaviampCoreConnectionRequest.Saved)?.id, error = null) }
        try {
            val succeeded = connect(request, preserveExistingSession = true)
            if (succeeded) completeAccountSelection() else updateAccounts { it.copy(
                visible = !adding, connecting = false, switchingConnectionId = null,
                error = NaviampAccountSwitcherError.ConnectionFailed) }
        } finally {
            if (accountAttempt === job) {
                accountAttempt = null
                updateAccounts { it.copy(connecting = false, switchingConnectionId = null) }
            }
        }
    }

    private suspend fun openLibrarySources() {
        val sourceId = inventory.currentSourceId ?: connection.state.value.sourceId
        if (sourceId == null) {
            updateLibrarySourcePicker {
                NaviampLibrarySourcePickerUi(
                    visible = true,
                    errorKind = NaviampLibrarySourcePickerError.ConnectionRequired,
                )
            }
            return
        }
        updateLibrarySourcePicker {
            it.copy(visible = true, loading = true, saving = false, errorKind = null)
        }
        runCatching { sessionPort.editableConnection(sourceId) }
            .onSuccess { editable ->
                librarySourceConnection = editable
                val knownIds = editable.availableMusicFolders.map { it.id }.toSet()
                val retainedSelection = editable.form.selectedMusicFolderIds.filter { it in knownIds }
                val selectedIds = defaultSelectedMusicFolderIds(retainedSelection, editable.availableMusicFolders)
                updateLibrarySourcePicker {
                    NaviampLibrarySourcePickerUi(
                        visible = true,
                        libraries = editable.availableMusicFolders,
                        selectedIds = selectedIds,
                        errorKind = if (editable.musicFoldersLoadFailed) {
                            NaviampLibrarySourcePickerError.LoadFailed
                        } else {
                            null
                        },
                    )
                }
            }
            .onFailure {
                librarySourceConnection = null
                updateLibrarySourcePicker {
                    NaviampLibrarySourcePickerUi(
                        visible = true,
                        errorKind = NaviampLibrarySourcePickerError.LoadFailed,
                    )
                }
            }
    }

    private fun toggleLibrarySource(id: String) {
        updateLibrarySourcePicker { picker ->
            if (picker.loading || picker.saving || picker.libraries.none { it.id == id }) picker else picker.copy(
                selectedIds = picker.selectedIds.toggleSelectedMusicFolderId(id, requireOne = true),
                errorKind = null,
            )
        }
    }

    private suspend fun saveLibrarySources() {
        val editable = librarySourceConnection ?: return
        val picker = stateStore.state.value.shell.library.sourcePicker
        if (picker.loading || picker.saving || picker.libraries.isEmpty() || picker.selectedIds.isEmpty()) return
        val sourceId = inventory.currentSourceId ?: connection.state.value.sourceId ?: return
        updateLibrarySourcePicker { it.copy(saving = true, errorKind = null) }
        val connected = connect(
            request = NaviampCoreConnectionRequest.Form(
                form = editable.form.copy(selectedMusicFolderIds = picker.selectedIds),
                savedConnectionId = sourceId,
            ),
            preserveExistingSession = true,
            userInitiated = false,
            forceSourceRefresh = true,
            onPreservedFailure = {
                updateLibrarySourcePicker {
                    it.copy(
                        saving = false,
                        errorKind = NaviampLibrarySourcePickerError.SaveFailed,
                    )
                }
            },
        )
        if (connected) closeLibrarySources()
    }

    private fun closeLibrarySources() {
        librarySourceConnection = null
        updateLibrarySourcePicker { NaviampLibrarySourcePickerUi() }
    }

    private fun updateLibrarySourcePicker(
        transform: (NaviampLibrarySourcePickerUi) -> NaviampLibrarySourcePickerUi,
    ) {
        stateStore.updateShell { shell ->
            shell.copy(library = shell.library.copy(sourcePicker = transform(shell.library.sourcePicker)))
        }
    }

    private suspend fun edit(id: String) {
        runCatching { sessionPort.editableConnection(id) }
            .onSuccess { editable ->
                editingConnectionId = id
                originalEditingForm = editable.form
                stateStore.updateShell { shell ->
                    shell.copy(
                        connectionSettings = shell.connectionSettings.copy(
                            connection = shell.connectionSettings.connection.copy(
                                editingConnection = true,
                                editingSavedConnection = true,
                                form = editable.form,
                                saveWithoutReconnect = isCompanionOnlyEdit(editable.form),
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

    private fun isCompanionOnlyEdit(form: ConnectionFormState): Boolean =
        editingConnectionId != null && form.aurralCompanion != null &&
            form.aurralCompanion != originalEditingForm?.aurralCompanion &&
            originalEditingForm?.copy(aurralCompanion = form.aurralCompanion) == form

    private fun updateForm(form: ConnectionFormState) {
        stateStore.updateShell { shell ->
            shell.copy(
                connectionSettings = shell.connectionSettings.copy(
                    connection = shell.connectionSettings.connection.copy(form = form, saveWithoutReconnect = isCompanionOnlyEdit(form)),
                ),
            )
        }
    }

    private fun openNewForm() {
        originalEditingForm = null
        editingConnectionId = null
        stateStore.updateShell { shell ->
            shell.copy(
                connectionSettings = shell.connectionSettings.copy(
                    connection = shell.connectionSettings.connection.copy(
                        editingConnection = true,
                        editingSavedConnection = false,
                        saveWithoutReconnect = false,
                        form = ConnectionFormState(),
                        availableMusicFolders = emptyList(),
                        musicFoldersStatus = null,
                    ),
                ),
            )
        }
    }

    private fun setEditing(editing: Boolean) {
        if (!editing) { editingConnectionId = null; originalEditingForm = null }
        stateStore.updateShell { shell ->
            shell.copy(
                connectionSettings = shell.connectionSettings.copy(
                    connection = shell.connectionSettings.connection.copy(
                        editingConnection = editing,
                        saveWithoutReconnect = editing && shell.connectionSettings.connection.saveWithoutReconnect,
                        editingSavedConnection = if (editing) {
                            shell.connectionSettings.connection.editingSavedConnection
                        } else {
                            false
                        },
                    ),
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
                providerId = saved.providerId,
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
                        offline = runtime.offline,
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
