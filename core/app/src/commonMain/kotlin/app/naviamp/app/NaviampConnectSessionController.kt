package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectAcknowledgement
import app.naviamp.domain.connect.NaviampConnectCapability
import app.naviamp.domain.connect.NaviampConnectCommand
import app.naviamp.domain.connect.NaviampConnectCommandRequest
import app.naviamp.domain.connect.NaviampConnectDevice
import app.naviamp.domain.connect.NaviampConnectEnvelope
import app.naviamp.domain.connect.NaviampConnectErrorCode
import app.naviamp.domain.connect.NaviampConnectErrorMessage
import app.naviamp.domain.connect.NaviampConnectHandoffQueue
import app.naviamp.domain.connect.NaviampConnectStartMedia
import app.naviamp.domain.connect.NaviampConnectPing
import app.naviamp.domain.connect.NaviampConnectPong
import app.naviamp.domain.connect.NaviampConnectRequestSnapshot
import app.naviamp.domain.connect.NaviampConnectSessionReplaced
import app.naviamp.domain.connect.NaviampConnectSnapshotMessage
import app.naviamp.domain.connect.NaviampConnectTargetSnapshot
import app.naviamp.domain.connect.requiredCapability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

fun interface NaviampConnectSessionTransport {
    suspend fun send(envelope: NaviampConnectEnvelope)
}

fun interface NaviampConnectRequestIdFactory {
    fun nextId(): String
}

enum class NaviampConnectControllerConnectionStatus {
    Disconnected,
    Connected,
}

data class NaviampConnectPendingRequest(
    val requestId: String,
    val command: NaviampConnectCommand,
    val idempotent: Boolean,
)

data class NaviampConnectControllerSessionState(
    val status: NaviampConnectControllerConnectionStatus = NaviampConnectControllerConnectionStatus.Disconnected,
    val sessionId: String? = null,
    val protocolVersion: Int? = null,
    val target: NaviampConnectDevice? = null,
    val capabilities: Set<NaviampConnectCapability> = emptySet(),
    val snapshot: NaviampConnectTargetSnapshot? = null,
    val pendingRequests: List<NaviampConnectPendingRequest> = emptyList(),
    val terminalResults: Map<String, NaviampConnectRequestTerminalResult> = emptyMap(),
    val lastError: NaviampConnectErrorMessage? = null,
)

sealed interface NaviampConnectRequestTerminalResult {
    data object Acknowledged : NaviampConnectRequestTerminalResult
    data class ProtocolRejected(val error: NaviampConnectErrorMessage) : NaviampConnectRequestTerminalResult
    data object TimedOut : NaviampConnectRequestTerminalResult
    data object Disconnected : NaviampConnectRequestTerminalResult
    data class WriteFailed(val message: String) : NaviampConnectRequestTerminalResult
}

sealed interface NaviampConnectCommandSendResult {
    data class Sent(val requestId: String) : NaviampConnectCommandSendResult
    data class Rejected(val error: NaviampConnectErrorMessage) : NaviampConnectCommandSendResult
    data class Failed(
        val requestId: String,
        val result: NaviampConnectRequestTerminalResult.WriteFailed,
    ) : NaviampConnectCommandSendResult
}

/**
 * Controller-side authenticated session owner.
 *
 * Socket reconnects and encryption are host effects. This owner enforces capabilities, message
 * ordering, pending-command policy, and authoritative snapshot reconciliation identically on every
 * controller platform.
 */
class NaviampConnectControllerSession(
    private val transport: NaviampConnectSessionTransport,
    private val requestIds: NaviampConnectRequestIdFactory,
) {
    private val mutableState = MutableStateFlow(NaviampConnectControllerSessionState())
    private var nextSequence = 0L
    private var lastReceivedSequence = -1L
    private val outboundMutex = Mutex()

    val state: StateFlow<NaviampConnectControllerSessionState> = mutableState.asStateFlow()

    fun connect(
        sessionId: String,
        protocolVersion: Int,
        target: NaviampConnectDevice,
        capabilities: Set<NaviampConnectCapability>,
        snapshot: NaviampConnectTargetSnapshot,
        nextOutboundSequence: Long = 0L,
        lastReceivedSequence: Long = -1L,
    ) {
        require(sessionId.isNotBlank()) { "An authenticated Connect session ID is required." }
        require(protocolVersion > 0) { "The negotiated protocol version must be positive." }
        require(nextOutboundSequence >= 0L) { "The next outbound sequence must not be negative." }
        require(lastReceivedSequence >= -1L) { "The last received sequence is invalid." }
        nextSequence = nextOutboundSequence
        this.lastReceivedSequence = lastReceivedSequence
        mutableState.value = mutableState.value.copy(
            status = NaviampConnectControllerConnectionStatus.Connected,
            sessionId = sessionId,
            protocolVersion = protocolVersion,
            target = target,
            capabilities = capabilities,
            snapshot = newerSnapshot(mutableState.value.snapshot, snapshot),
            lastError = null,
        )
    }

    suspend fun send(command: NaviampConnectCommand): NaviampConnectCommandSendResult {
        val current = mutableState.value
        if (current.status != NaviampConnectControllerConnectionStatus.Connected ||
            current.sessionId == null || current.protocolVersion == null
        ) {
            return reject(NaviampConnectErrorCode.TargetUnavailable, "The target is disconnected.", retryable = true)
        }
        val required = command.requiredCapability()
        if (required != null && required !in current.capabilities) {
            return reject(
                NaviampConnectErrorCode.UnsupportedCapability,
                "The target does not support ${required.name}.",
            )
        }
        val request = NaviampConnectPendingRequest(
            requestId = requestIds.nextId(),
            command = command,
            idempotent = command.isNaviampConnectIdempotent(),
        )
        require(request.requestId.isNotBlank()) { "A Connect request ID is required." }
        mutableState.update { state ->
            state.copy(
                pendingRequests = state.pendingRequests + request,
                terminalResults = state.terminalResults - request.requestId,
                lastError = null,
            )
        }
        try {
            sendRequest(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (cause: Throwable) {
            val failure = NaviampConnectRequestTerminalResult.WriteFailed(
                cause.message ?: "The command could not be written to the playback device.",
            )
            completeRequest(request.requestId, failure, removePending = false)
            disconnectPendingRequests(failure)
            return NaviampConnectCommandSendResult.Failed(request.requestId, failure)
        }
        return NaviampConnectCommandSendResult.Sent(request.requestId)
    }

    suspend fun awaitTerminalResult(
        requestId: String,
        timeoutMillis: Long,
    ): NaviampConnectRequestTerminalResult {
        require(requestId.isNotBlank()) { "A Connect request ID is required." }
        require(timeoutMillis > 0L) { "The Connect request timeout must be positive." }
        mutableState.value.terminalResults[requestId]?.let { return it }
        val completed = withTimeoutOrNull(timeoutMillis) {
            state.first { requestId in it.terminalResults }.terminalResults.getValue(requestId)
        }
        if (completed != null) return completed
        val timeout = NaviampConnectRequestTerminalResult.TimedOut
        completeRequest(requestId, timeout)
        return mutableState.value.terminalResults[requestId] ?: timeout
    }

    suspend fun receive(envelope: NaviampConnectEnvelope) {
        val current = mutableState.value
        if (current.status != NaviampConnectControllerConnectionStatus.Connected ||
            envelope.sessionId != current.sessionId ||
            envelope.protocolVersion != current.protocolVersion
        ) {
            return
        }
        if (envelope.sequence <= lastReceivedSequence) return
        lastReceivedSequence = envelope.sequence

        when (val message = envelope.message) {
            is NaviampConnectAcknowledgement -> acknowledge(envelope.responseToRequestId)
            is NaviampConnectSnapshotMessage -> applySnapshot(message.snapshot)
            is NaviampConnectErrorMessage -> applyError(envelope.responseToRequestId, message)
            is NaviampConnectPing -> sendMessage(NaviampConnectPong(message.sentAtEpochMillis))
            else -> Unit
        }
    }

    fun disconnect() {
        disconnectPendingRequests(NaviampConnectRequestTerminalResult.Disconnected)
    }

    private fun disconnectPendingRequests(reason: NaviampConnectRequestTerminalResult) {
        mutableState.update { current ->
            val results = current.pendingRequests.fold(current.terminalResults) { accumulated, request ->
                if (request.requestId in accumulated) accumulated else accumulated + (request.requestId to reason)
            }
            current.copy(
                status = NaviampConnectControllerConnectionStatus.Disconnected,
                sessionId = null,
                protocolVersion = null,
                terminalResults = results.boundedTerminalResults(),
            )
        }
        nextSequence = 0L
        lastReceivedSequence = -1L
    }

    /**
     * Starts a fresh authenticated session and retries only absolute/idempotent requests. Commands
     * such as Toggle, Next, queue movement, and media start are discarded and reconciled from the
     * target snapshot instead of risking duplicate execution.
     */
    suspend fun reconnect(
        sessionId: String,
        protocolVersion: Int,
        target: NaviampConnectDevice,
        capabilities: Set<NaviampConnectCapability>,
        snapshot: NaviampConnectTargetSnapshot,
    ) {
        val retryable = mutableState.value.pendingRequests.filter { it.idempotent }
        connect(sessionId, protocolVersion, target, capabilities, snapshot)
        mutableState.update { current ->
            current.copy(
                pendingRequests = retryable,
                terminalResults = current.terminalResults - retryable.map(NaviampConnectPendingRequest::requestId).toSet(),
            )
        }
        for (request in retryable) {
            try {
                sendRequest(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (cause: Throwable) {
                val failure = NaviampConnectRequestTerminalResult.WriteFailed(
                    cause.message ?: "The command could not be written to the playback device.",
                )
                completeRequest(request.requestId, failure, removePending = false)
                disconnectPendingRequests(failure)
                return
            }
        }
    }

    private suspend fun sendRequest(request: NaviampConnectPendingRequest) {
        val current = mutableState.value
        sendMessage(
            message = NaviampConnectCommandRequest(
                command = request.command,
                expectedRevision = current.snapshot?.revision,
            ),
            requestId = request.requestId,
        )
    }

    private suspend fun sendMessage(
        message: app.naviamp.domain.connect.NaviampConnectMessage,
        requestId: String? = null,
    ) = outboundMutex.withLock {
        val current = mutableState.value
        val sessionId = checkNotNull(current.sessionId) {
            "The Connect session ended before the message was written."
        }
        val protocolVersion = checkNotNull(current.protocolVersion) {
            "The Connect session ended before the message was written."
        }
        transport.send(
            NaviampConnectEnvelope(
                protocolVersion = protocolVersion,
                sessionId = sessionId,
                sequence = nextSequence++,
                requestId = requestId,
                message = message,
            ),
        )
    }

    private fun acknowledge(requestId: String?) {
        if (requestId == null) return
        completeRequest(requestId, NaviampConnectRequestTerminalResult.Acknowledged)
    }

    private fun applySnapshot(snapshot: NaviampConnectTargetSnapshot) {
        val current = mutableState.value
        mutableState.value = current.copy(snapshot = newerSnapshot(current.snapshot, snapshot))
    }

    private fun applyError(
        requestId: String?,
        error: NaviampConnectErrorMessage,
    ) {
        if (requestId != null) completeRequest(requestId, NaviampConnectRequestTerminalResult.ProtocolRejected(error))
        mutableState.update { it.copy(lastError = error) }
    }

    private fun completeRequest(
        requestId: String,
        result: NaviampConnectRequestTerminalResult,
        removePending: Boolean = true,
    ) {
        mutableState.update { current ->
            if (current.pendingRequests.none { it.requestId == requestId } && requestId in current.terminalResults) {
                current
            } else {
                current.copy(
                    pendingRequests = if (removePending) {
                        current.pendingRequests.filterNot { it.requestId == requestId }
                    } else {
                        current.pendingRequests
                    },
                    terminalResults = (current.terminalResults + (requestId to result)).boundedTerminalResults(),
                )
            }
        }
    }

    private fun reject(
        code: NaviampConnectErrorCode,
        message: String,
        retryable: Boolean = false,
    ): NaviampConnectCommandSendResult.Rejected {
        val error = NaviampConnectErrorMessage(code, message, retryable)
        mutableState.value = mutableState.value.copy(lastError = error)
        return NaviampConnectCommandSendResult.Rejected(error)
    }
}

private fun Map<String, NaviampConnectRequestTerminalResult>.boundedTerminalResults(): Map<String, NaviampConnectRequestTerminalResult> =
    if (size <= MaximumRetainedTerminalResults) this
    else entries.toList().takeLast(MaximumRetainedTerminalResults).associate { it.toPair() }

private const val MaximumRetainedTerminalResults = 256

fun NaviampConnectCommand.isNaviampConnectIdempotent(): Boolean = when (this) {
    app.naviamp.domain.connect.NaviampConnectPlay,
    app.naviamp.domain.connect.NaviampConnectPause,
    app.naviamp.domain.connect.NaviampConnectStop,
    is app.naviamp.domain.connect.NaviampConnectSeek,
    is app.naviamp.domain.connect.NaviampConnectSetFavorite,
    is app.naviamp.domain.connect.NaviampConnectSetRepeat,
    is app.naviamp.domain.connect.NaviampConnectSetShuffle,
    NaviampConnectRequestSnapshot,
    is app.naviamp.domain.connect.NaviampConnectShowSurface,
    -> true
    app.naviamp.domain.connect.NaviampConnectTogglePlayPause,
    app.naviamp.domain.connect.NaviampConnectPrevious,
    app.naviamp.domain.connect.NaviampConnectNext,
    is app.naviamp.domain.connect.NaviampConnectSelectQueueOccurrence,
    is app.naviamp.domain.connect.NaviampConnectMoveQueueOccurrence,
    is app.naviamp.domain.connect.NaviampConnectRemoveQueueOccurrence,
    is app.naviamp.domain.connect.NaviampConnectStartMedia,
    is app.naviamp.domain.connect.NaviampConnectQueueMedia,
    is app.naviamp.domain.connect.NaviampConnectHandoffQueue,
    is app.naviamp.domain.connect.NaviampConnectOfferConnectionProvisioning,
    -> false
    app.naviamp.domain.connect.NaviampConnectClearUpNext -> true
}

sealed interface NaviampConnectTargetCommandResult {
    data class Success(
        val snapshot: NaviampConnectTargetSnapshot,
        val changed: Boolean,
    ) : NaviampConnectTargetCommandResult

    data class Failure(
        val code: NaviampConnectErrorCode,
        val message: String,
        val retryable: Boolean = false,
    ) : NaviampConnectTargetCommandResult
}

fun interface NaviampConnectTargetCommandExecutor {
    suspend fun execute(
        command: NaviampConnectCommand,
        currentSnapshot: NaviampConnectTargetSnapshot,
    ): NaviampConnectTargetCommandResult
}

/** Target-side authenticated command, replay, revision, and snapshot owner. */
class NaviampConnectTargetSession(
    private val sessionId: String,
    private val protocolVersion: Int,
    initialSnapshot: NaviampConnectTargetSnapshot,
    private val transport: NaviampConnectSessionTransport,
    private val executor: NaviampConnectTargetCommandExecutor,
    initialOutboundSequence: Long = 0L,
) {
    private var snapshot = initialSnapshot
    private var lastReceivedSequence = -1L
    private var nextSequence = initialOutboundSequence
    private val completedRequests = mutableMapOf<String, CompletedNaviampConnectRequest>()
    private val outboundMutex = Mutex()

    init {
        require(initialOutboundSequence >= 0L) { "The initial outbound sequence must not be negative." }
    }

    fun snapshot(): NaviampConnectTargetSnapshot = snapshot

    suspend fun receive(envelope: NaviampConnectEnvelope) {
        if (envelope.sessionId != sessionId || envelope.protocolVersion != protocolVersion) return
        val requestId = envelope.requestId
        if (requestId != null) {
            completedRequests[requestId]?.let { completed ->
                val repeated = envelope.message as? NaviampConnectCommandRequest
                if (completed.command == null) {
                    sendError(
                        requestId,
                        NaviampConnectErrorCode.InvalidRequest,
                        "A sensitive completed request cannot be replayed.",
                    )
                } else if (repeated?.command == completed.command) {
                    sendResponse(requestId, completed.acknowledgement)
                } else {
                    sendError(
                        requestId,
                        NaviampConnectErrorCode.InvalidRequest,
                        "A completed request ID was reused for different work.",
                    )
                }
                return
            }
        }
        if (envelope.sequence <= lastReceivedSequence) {
            sendError(requestId, NaviampConnectErrorCode.ReplayRejected, "The message sequence was already used.")
            return
        }
        lastReceivedSequence = envelope.sequence

        when (val message = envelope.message) {
            is NaviampConnectCommandRequest -> execute(requestId, message)
            is NaviampConnectPing -> sendResponse(requestId, NaviampConnectPong(message.sentAtEpochMillis))
            else -> sendError(requestId, NaviampConnectErrorCode.InvalidRequest, "The target expected a command.")
        }
    }

    /** Publishes a local-TV mutation through the same authoritative revision stream. */
    suspend fun publishLocalSnapshot(updated: NaviampConnectTargetSnapshot) {
        require(updated.revision > snapshot.revision) { "A local target mutation must advance the revision." }
        snapshot = updated
        send(NaviampConnectSnapshotMessage(snapshot))
    }

    /** Orders takeover notification within the target's single authenticated sequence stream. */
    suspend fun notifySessionReplaced() {
        send(NaviampConnectSessionReplaced)
    }

    private suspend fun execute(
        requestId: String?,
        request: NaviampConnectCommandRequest,
    ) {
        val command = request.command
        if (requestId.isNullOrBlank()) {
            sendError(requestId, NaviampConnectErrorCode.InvalidRequest, "A command request ID is required.")
            return
        }
        val required = command.requiredCapability()
        if (required != null && required !in snapshot.capabilities) {
            sendError(
                requestId,
                NaviampConnectErrorCode.UnsupportedCapability,
                "The target does not support ${required.name}.",
            )
            return
        }
        if (command != NaviampConnectRequestSnapshot &&
            request.expectedRevision != null &&
            request.expectedRevision != snapshot.revision
        ) {
            sendError(
                requestId,
                NaviampConnectErrorCode.RevisionConflict,
                "The target state changed before this command arrived.",
                currentRevision = snapshot.revision,
            )
            send(NaviampConnectSnapshotMessage(snapshot))
            return
        }
        val requestedSourceIdentity = when (command) {
            is NaviampConnectHandoffQueue -> command.sourceIdentity
            is NaviampConnectStartMedia -> command.sourceIdentity
            is app.naviamp.domain.connect.NaviampConnectQueueMedia -> command.sourceIdentity
            else -> null
        }
        if (requestedSourceIdentity != null &&
            snapshot.sourceIdentity?.isCompatibleWith(requestedSourceIdentity) != true
        ) {
            sendError(
                requestId,
                NaviampConnectErrorCode.SourceMismatch,
                "The controller and target do not share the same media source.",
                currentRevision = snapshot.revision,
            )
            return
        }
        if (command == NaviampConnectRequestSnapshot) {
            val acknowledgement = NaviampConnectAcknowledgement(snapshot.revision)
            rememberCompleted(requestId, command, acknowledgement)
            sendResponse(requestId, acknowledgement)
            send(NaviampConnectSnapshotMessage(snapshot))
            return
        }

        val execution = try {
            executor.execute(command, snapshot)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            sendError(
                requestId,
                NaviampConnectErrorCode.InternalFailure,
                "The target could not apply the requested command.",
                retryable = true,
                currentRevision = snapshot.revision,
            )
            return
        }
        when (val result = execution) {
            is NaviampConnectTargetCommandResult.Failure -> sendError(
                requestId,
                result.code,
                result.message,
                retryable = result.retryable,
                currentRevision = snapshot.revision,
            )
            is NaviampConnectTargetCommandResult.Success -> {
                if (result.changed && result.snapshot.revision <= snapshot.revision) {
                    sendError(
                        requestId,
                        NaviampConnectErrorCode.InternalFailure,
                        "A target mutation did not advance its revision.",
                        currentRevision = snapshot.revision,
                    )
                    return
                }
                if (result.snapshot.revision < snapshot.revision) {
                    sendError(
                        requestId,
                        NaviampConnectErrorCode.InternalFailure,
                        "A target command returned stale state.",
                        currentRevision = snapshot.revision,
                    )
                    return
                }
                snapshot = result.snapshot
                val acknowledgement = NaviampConnectAcknowledgement(snapshot.revision)
                rememberCompleted(requestId, command, acknowledgement)
                sendResponse(requestId, acknowledgement)
                if (result.changed) send(NaviampConnectSnapshotMessage(snapshot))
            }
        }
    }

    private suspend fun sendError(
        requestId: String?,
        code: NaviampConnectErrorCode,
        message: String,
        retryable: Boolean = false,
        currentRevision: Long? = null,
    ) = sendResponse(
        requestId,
        NaviampConnectErrorMessage(code, message, retryable, currentRevision),
    )

    private suspend fun sendResponse(
        requestId: String?,
        message: app.naviamp.domain.connect.NaviampConnectMessage,
    ) = send(message, responseToRequestId = requestId)

    private suspend fun send(
        message: app.naviamp.domain.connect.NaviampConnectMessage,
        responseToRequestId: String? = null,
    ) = outboundMutex.withLock {
        transport.send(
            NaviampConnectEnvelope(
                protocolVersion = protocolVersion,
                sessionId = sessionId,
                sequence = nextSequence++,
                responseToRequestId = responseToRequestId,
                message = message,
            ),
        )
    }

    private fun rememberCompleted(
        requestId: String,
        command: NaviampConnectCommand,
        acknowledgement: NaviampConnectAcknowledgement,
    ) {
        if (completedRequests.size >= MaximumRememberedConnectRequests) {
            completedRequests.remove(completedRequests.keys.first())
        }
        completedRequests[requestId] = CompletedNaviampConnectRequest(
            command = command.takeUnless {
                it is app.naviamp.domain.connect.NaviampConnectOfferConnectionProvisioning
            },
            acknowledgement = acknowledgement,
        )
    }
}

private data class CompletedNaviampConnectRequest(
    val command: NaviampConnectCommand?,
    val acknowledgement: NaviampConnectAcknowledgement,
)

private const val MaximumRememberedConnectRequests = 256

private fun newerSnapshot(
    current: NaviampConnectTargetSnapshot?,
    incoming: NaviampConnectTargetSnapshot,
): NaviampConnectTargetSnapshot =
    if (current == null || incoming.revision > current.revision) incoming else current
