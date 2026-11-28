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
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
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
    private var recoveringSourceId: String? = null
    private var certificateSelectionGeneration = 0L
    /** Changes whenever the editor opens, closes or changes, so a slow save can tell it is stale. */
    private var editorGeneration = 0L

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
        editorGeneration++
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
        certificateSelectionGeneration++
        // Before anything suspends: a save waiting on its companion check must not commit afterwards.
        editorGeneration++
        accountAttempt?.cancel()
        accountAttempt?.join()
        accountAttempt = null
        // Join, and only after the account attempt: a cancelled switch resumes recovery as it
        // unwinds, and a cancelled retry restores its pre-attempt state; neither may land after the
        // reset and revive the deleted source.
        stopRecovery()
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
        recoveringSourceId = sourceId
        recovery = scope.launch { recover(sourceId) }
    }

    /**
     * Cancels recovery and waits for it to unwind. A cancelled retry restores its pre-attempt state
     * as it unwinds, so anything that changes the session afterwards must not run before it lands.
     */
    private suspend fun stopRecovery() {
        recovery?.cancelAndJoin()
        recovery = null
        recoveringSourceId = null
    }

    /**
     * Whether a user-visible attempt is running. A background recovery attempt never blocks what the
     * user asks for: their request supersedes it (see [connect]).
     */
    private fun foregroundConnecting(): Boolean =
        connection.state.value.let { it.isConnecting && !it.backgroundRecovery }

    private fun resumeRecoveryIfOffline() {
        val runtime = connection.state.value
        val sourceId = runtime.sourceId
        if (runtime.offline && !runtime.isConnecting && sourceId != null) startRecovery(sourceId)
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
            when (retry(sourceId)) {
                RecoveryOutcome.Connected, RecoveryOutcome.Rejected -> return
                RecoveryOutcome.Unreachable -> failures += 1
                // Offline Mode cancelled the attempt; the loop now idles until it is turned off.
                RecoveryOutcome.Interrupted -> Unit
            }
        }
    }

    private enum class RecoveryOutcome { Connected, Unreachable, Rejected, Interrupted }

    /**
     * One background attempt. Offline Mode turned on mid-attempt cancels it, and a late success is
     * still refused at commit, so the session never goes online against the user's choice. A server
     * that rejects the saved account or certificate is not retried: the session stays offline with
     * downloads available and shows why.
     */
    private suspend fun retry(sourceId: String): RecoveryOutcome = kotlinx.coroutines.coroutineScope {
        var cause: Throwable? = null
        val attempt = async {
            connect(
                NaviampCoreConnectionRequest.Saved(sourceId),
                preserveExistingSession = true,
                userInitiated = false,
                onPreservedFailure = { cause = it },
                commitIf = { !offlineModeEnabled() },
                automatic = true,
                // A background success must not close a connection editor the user has open.
                preserveEditor = true,
                onSessionConnected = onReconnected,
            )
        }
        val offlineModeWatch = launch {
            stateStore.state.first { offlineModeEnabled() }
            attempt.cancel()
        }
        val connected = try {
            attempt.await()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            currentCoroutineContext().ensureActive()
            return@coroutineScope RecoveryOutcome.Interrupted
        } finally {
            offlineModeWatch.cancel()
        }
        val failure = cause
        when {
            connected -> RecoveryOutcome.Connected
            failure != null && !connectionFailureAllowsOfflineRestoration(failure) -> {
                connection.offline(
                    sourceId = sourceId,
                    status = connectionFailureStatus(failure, "Could not connect to the music server."),
                    statusIsError = true,
                )
                publishConnection()
                RecoveryOutcome.Rejected
            }
            else -> RecoveryOutcome.Unreachable
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
            sameServerUrl(active.serverUrl, form.serverUrl) &&
            active.username == form.username.trim()
        ) {
            val companion = form.aurralCompanion ?: return true
            if (connectionFormError(form, hasSavedConnectionForLogin = true) != null) return false
            return try {
                if (companion.enabled && companionVerificationError(companion) != null) return false
                sessionPort.saveAurralCompanion(active.id, companion)
                onCompanionChanged(active.id)
                true
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        }
        val companion = form.aurralCompanion
        if (companion != null && companion.enabled && form.providerId != app.naviamp.domain.provider.ProviderIdAurral &&
            companionVerificationError(companion) != null) return false
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
                discardFormStatus()
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
                if (!foregroundConnecting()) {
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
            NaviampCoreCommand.Connection.Connect -> save()
            is NaviampCoreCommand.Connection.SelectSavedSource -> {
                val runtime = connection.state.value
                if (command.id.isBlank() || foregroundConnecting() || stateStore.state.value.shell.connect.remotePlaybackAuthorityActive ||
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
                    foregroundConnecting() -> Unit
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
        onPreservedFailure: (Throwable?) -> Unit = {},
        /** Checked just before a success commits; false discards it and restores the previous state. */
        commitIf: () -> Boolean = { true },
        preserveEditor: Boolean = false,
        onSessionConnected: (String) -> Unit = onConnected,
        /** True only for Core's own offline recovery; anything else supersedes that recovery. */
        automatic: Boolean = false,
    ): Boolean {
        // An attempt that may close the editor, or that switches to another source, supersedes any
        // save still waiting on its companion check, before anything here suspends (stopRecovery joins a running retry). Background retries of the
        // current source that keep the editor open leave it alone.
        val switchesSource = (request as? NaviampCoreConnectionRequest.Saved)?.id?.let { it != inventory.currentSourceId } == true
        if (!preserveEditor || switchesSource) editorGeneration++
        // A switch or reconnect the user asked for replaces a background retry; otherwise begin()
        // would refuse it while the retry is connecting and the request would be lost.
        if (!automatic) stopRecovery()
        if (request is NaviampCoreConnectionRequest.Form) {
            connectionFormError(
                form = request.form,
                hasSavedConnectionForLogin = request.savedConnectionId != null,
            )?.let { error ->
                if (!preserveExistingSession) {
                    connection.failed(error)
                    publishConnection()
                    countErrorOccurrence()
                } else {
                    onPreservedFailure(null)
                }
                return false
            }
        }
        val previousStatus = stateStore.state.value.shell.connectionSettings.connection.status
        val previousConnection = connection.state.value
        val plan = connection.begin(
            restoreSavedSession = preserveExistingSession || request is NaviampCoreConnectionRequest.Saved,
            preserveExistingConnection = preserveExistingSession,
            automaticRecovery = automatic,
        )
            ?: return false
        val previousSourceId = stateStore.state.value.shell.connectionSettings.currentSourceId
        publishConnection()
        var connected = false
        runCatching {
            sessionPort.connect(request, plan).also { kotlinx.coroutines.currentCoroutineContext().ensureActive() }
        }.onSuccess { session ->
                if (!commitIf()) {
                    connection.restore(previousConnection)
                    publishConnection()
                    stateStore.updateShell { shell -> shell.copy(connectionSettings = shell.connectionSettings.copy(
                        connection = shell.connectionSettings.connection.copy(status = previousStatus))) }
                    return@onSuccess
                }
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
                    // A user attempt that ends back on the offline session it replaced, whether it
                    // failed or was cancelled (Back during an account switch), resumes recovery.
                    // Recovery runs on the runtime scope, so this caller's cancellation cannot stop it.
                    if (!automatic) resumeRecoveryIfOffline()
                    if (cause is kotlinx.coroutines.CancellationException) throw cause
                    onPreservedFailure(cause)
                    return@onFailure
                }
                val savedSourceId = (request as? NaviampCoreConnectionRequest.Saved)?.id
                if (savedSourceId != null && connectionFailureAllowsOfflineRestoration(cause)) {
                    enterOffline(savedSourceId, previousSourceId, plan.clearExistingPlayback)
                } else {
                    connection.failed(connectionFailureStatus(cause, "Could not connect to the music server."))
                    publishConnection()
                    countErrorOccurrence()
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
        if (foregroundConnecting()) return
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
                editorGeneration++
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
        // A retry still waiting on the deleted source would otherwise restore it as it fails.
        val wasRecovering = recoveringSourceId == saved.id
        if (wasRecovering) stopRecovery()
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
            .onFailure { cause ->
                publishStatus(cause.message ?: "Could not delete ${saved.displayName}.")
                if (wasRecovering) resumeRecoveryIfOffline()
            }
    }

    /**
     * Saves the editor. Everything the decision depends on is captured before the companion check
     * suspends; a check that completes after Cancel, a form change or a newer save is discarded.
     */
    private suspend fun save() {
        val form = stateStore.state.value.shell.connectionSettings.connection.form
        val editId = editingConnectionId
        val original = originalEditingForm
        val generation = ++editorGeneration
        val request = NaviampCoreConnectionRequest.Form(form, editId)
        val companion = form.aurralCompanion
        val formError = connectionFormError(form, hasSavedConnectionForLogin = editId != null)
        // Local validation never touches a session that is in use; the editor reports it.
        if (formError != null && (connection.state.value.connected || editId != null)) {
            showFormError(formError)
            return
        }
        if (formError == null && companion != null && companionNeedsVerification(form, editId, original)) {
            val error = companionVerificationError(companion)
            if (generation != editorGeneration) return
            if (error != null) { showFormError(error); return }
        }
        when {
            editId != null && companion != null && isCompanionOnlyEdit(form, editId, original) -> try {
                sessionPort.saveAurralCompanion(editId, companion)
                if (generation == editorGeneration) setEditing(false)
                onCompanionChanged(editId)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { showFormError("connection_aurral_companion_save_failed") }
            accountSwitcher().addingAccount -> connectAccount(request)
            isSameAccountEditOfCurrentSource(form, editId, original) -> connect(
                request,
                // The edited account is the one already in use: keep its queue, playback and
                // Library cache, and report a failed save on the form without dropping the session.
                preserveExistingSession = true,
                onPreservedFailure = { cause ->
                    showFormError(cause?.let { connectionFailureStatus(it, ConnectionSaveFailedStatus) }
                        ?: ConnectionSaveFailedStatus)
                },
                onSessionConnected = { id -> onSavedSourceSelected(id, false) },
            )
            else -> connect(request)
        }
    }

    /** A new or changed enabled companion must prove it can sign in before anything is saved. */
    private fun companionNeedsVerification(form: ConnectionFormState, editId: String?, original: ConnectionFormState?): Boolean {
        val companion = form.aurralCompanion ?: return false
        return companion.enabled && form.providerId != app.naviamp.domain.provider.ProviderIdAurral &&
            (editId == null || companion != original?.aurralCompanion)
    }

    private suspend fun companionVerificationError(companion: app.naviamp.domain.source.AurralCompanion): String? {
        val status = try {
            sessionPort.verifyAurralCompanion(companion)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            app.naviamp.domain.provider.ProviderDiscoveryStatus.Unavailable
        }
        return aurralCompanionVerificationError(status)
    }

    /** Same saved account, server and Library scope as the session in use; only details such as the name or password differ. */
    private fun isSameAccountEditOfCurrentSource(form: ConnectionFormState, editId: String?, original: ConnectionFormState?): Boolean {
        if (editId == null || original == null || editId != inventory.currentSourceId) return false
        return form.providerId == original.providerId &&
            sameServerUrl(form.serverUrl, original.serverUrl) &&
            form.username.trim() == original.username.trim() &&
            form.selectedMusicFolderIds.toSet() == original.selectedMusicFolderIds.toSet()
    }

    private fun countErrorOccurrence() {
        stateStore.updateShell { shell -> shell.copy(connectionSettings = shell.connectionSettings.copy(
            connection = shell.connectionSettings.connection.copy(
                errorOccurrence = shell.connectionSettings.connection.errorOccurrence + 1L))) }
    }

    /** A discarded form's error belongs to the form; show the runtime connection's own status again. */
    private fun discardFormStatus() {
        val runtime = connection.state.value
        stateStore.updateShell { shell -> shell.copy(connectionSettings = shell.connectionSettings.copy(
            connection = shell.connectionSettings.connection.copy(status = runtime.status,
                statusIsError = runtime.phase == NaviampConnectionPhase.Failed || runtime.statusIsError))) }
    }

    private fun showFormError(status: String) {
        stateStore.updateShell { shell -> shell.copy(connectionSettings = shell.connectionSettings.copy(
            connection = shell.connectionSettings.connection.copy(status = status, statusIsError = true,
                errorOccurrence = shell.connectionSettings.connection.errorOccurrence + 1L))) }
    }

    private fun isCompanionOnlyEdit(form: ConnectionFormState): Boolean =
        isCompanionOnlyEdit(form, editingConnectionId, originalEditingForm)

    private fun isCompanionOnlyEdit(form: ConnectionFormState, editId: String?, original: ConnectionFormState?): Boolean =
        editId != null && form.aurralCompanion != null &&
            form.aurralCompanion != original?.aurralCompanion &&
            original?.copy(aurralCompanion = form.aurralCompanion) == form

    private fun updateForm(form: ConnectionFormState) {
        editorGeneration++
        stateStore.updateShell { shell ->
            shell.copy(
                connectionSettings = shell.connectionSettings.copy(
                    connection = shell.connectionSettings.connection.copy(form = form, saveWithoutReconnect = isCompanionOnlyEdit(form)),
                ),
            )
        }
    }

    private fun openNewForm() {
        editorGeneration++
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
        editorGeneration++
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
                        statusIsError = runtime.phase == NaviampConnectionPhase.Failed || runtime.statusIsError,
                        serverVersion = runtime.serverVersion,
                        connected = runtime.connected,
                        offline = runtime.offline,
                        restoringConnection = runtime.restoringConnection,
                        // Background recovery leaves offline browsing, Android Auto and CarPlay fully usable.
                        isConnecting = runtime.isConnecting && !runtime.backgroundRecovery,
                        savedConnections = savedConnections,
                        hasSavedConnection = savedConnections.isNotEmpty(),
                    ),
                ),
            )
        }
    }
}

/** Form status key for a companion sign-in check, or null when the companion accepted it. */
internal fun aurralCompanionVerificationError(status: app.naviamp.domain.provider.ProviderDiscoveryStatus): String? = when (status) {
    app.naviamp.domain.provider.ProviderDiscoveryStatus.Ready,
    app.naviamp.domain.provider.ProviderDiscoveryStatus.Building,
    -> null
    app.naviamp.domain.provider.ProviderDiscoveryStatus.PasswordRequired,
    app.naviamp.domain.provider.ProviderDiscoveryStatus.SignInRequired,
    -> "connection_aurral_companion_sign_in_failed"
    app.naviamp.domain.provider.ProviderDiscoveryStatus.Forbidden -> "connection_aurral_companion_forbidden"
    app.naviamp.domain.provider.ProviderDiscoveryStatus.Unsupported -> "connection_aurral_companion_unsupported"
    app.naviamp.domain.provider.ProviderDiscoveryStatus.Unavailable -> "connection_aurral_companion_unreachable"
}

/** Resource key shown when saving an edit fails without a more specific reason. */
internal const val ConnectionSaveFailedStatus = "connection_save_failed"

/**
 * True when two server URLs name the same server. Scheme and host are case-insensitive; the path
 * keeps its case, since a reverse proxy can serve different libraries at /Music and /music.
 */
internal fun sameServerUrl(first: String, second: String): Boolean {
    fun split(url: String): Pair<String, String> {
        val value = url.trim().trimEnd('/')
        val schemeEnd = value.indexOf("://").takeIf { it >= 0 }?.plus(3) ?: 0
        val pathStart = value.indexOf('/', schemeEnd).takeIf { it >= 0 } ?: value.length
        return value.substring(0, pathStart).lowercase() to value.substring(pathStart)
    }
    return split(first) == split(second)
}
