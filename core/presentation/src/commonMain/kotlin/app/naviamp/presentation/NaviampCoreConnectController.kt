package app.naviamp.presentation

import app.naviamp.app.NaviampConnectAdvertisingController
import app.naviamp.app.NaviampConnectAdvertisingEffect
import app.naviamp.app.NaviampConnectAdvertisingStatus
import app.naviamp.app.NaviampConnectAuthenticatedCipherFactory
import app.naviamp.app.NaviampConnectAuthenticatedSession
import app.naviamp.app.NaviampConnectControllerSession
import app.naviamp.app.NaviampConnectDeviceIdentityEffect
import app.naviamp.app.NaviampConnectDiscoveredTarget
import app.naviamp.app.NaviampConnectDiscoveryController
import app.naviamp.app.NaviampConnectDiscoveryEffect
import app.naviamp.app.NaviampConnectDiscoveryProblem
import app.naviamp.app.NaviampConnectDiscoveryStatus
import app.naviamp.app.NaviampConnectIdentityVerifier
import app.naviamp.app.NaviampConnectPakeFactory
import app.naviamp.app.NaviampConnectPairingRuntimeResult
import app.naviamp.app.NaviampConnectPendingTargetPairing
import app.naviamp.app.NaviampConnectTargetPairingRequestResult
import app.naviamp.app.NaviampConnectTargetPairingRuntime
import app.naviamp.app.NaviampConnectTargetSession
import app.naviamp.app.NaviampConnectSessionTransport
import app.naviamp.app.NaviampConnectTransportFactory
import app.naviamp.app.NaviampConnectTransportListener
import app.naviamp.app.NaviampConnectTrustRepository
import app.naviamp.app.NaviampConnectControllerPairingRuntime
import app.naviamp.domain.connect.NaviampConnectAdvertisement
import app.naviamp.domain.connect.NaviampConnectCapability
import app.naviamp.domain.connect.NaviampConnectHandoffQueue
import app.naviamp.domain.connect.NaviampConnectOfferConnectionProvisioning
import app.naviamp.domain.connect.NaviampConnectPause
import app.naviamp.domain.connect.NaviampConnectPlay
import app.naviamp.domain.connect.NaviampConnectDevice
import app.naviamp.domain.connect.NaviampConnectDeviceRole
import app.naviamp.domain.connect.NaviampConnectErrorCode
import app.naviamp.domain.connect.NaviampConnectProtocolRange
import app.naviamp.domain.connect.NaviampConnectTargetPairingController
import app.naviamp.domain.connect.NaviampConnectTrustRecord
import app.naviamp.domain.connect.NaviampConnectSourceIdentity
import app.naviamp.domain.connect.NaviampConnectWelcome
import app.naviamp.domain.connect.NaviampConnectNext
import app.naviamp.domain.connect.NaviampConnectPrevious
import app.naviamp.domain.connect.NaviampConnectTogglePlayPause
import app.naviamp.ui.NaviampConnectDiscoveredTargetUi
import app.naviamp.ui.NaviampConnectPairingUiPhase
import app.naviamp.ui.NaviampConnectSettingsActions
import app.naviamp.ui.NaviampConnectSettingsUi
import app.naviamp.ui.NaviampConnectTrustedDeviceUi
import app.naviamp.ui.NaviampConnectUiRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class NaviampCoreConnectRole { Controller, Target }

/** Native effects and host facts required by the shared Connect product controller. */
data class NaviampCoreConnectServices(
    val role: NaviampCoreConnectRole,
    val displayName: String,
    val identity: NaviampConnectDeviceIdentityEffect,
    val identityVerifier: NaviampConnectIdentityVerifier,
    val transport: NaviampConnectTransportFactory,
    val pake: NaviampConnectPakeFactory,
    val cipher: NaviampConnectAuthenticatedCipherFactory,
    val trust: NaviampConnectTrustRepository,
    val discovery: NaviampConnectDiscoveryEffect? = null,
    val advertising: NaviampConnectAdvertisingEffect? = null,
    val newOpaqueId: () -> String,
    val newPairingCode: () -> String,
    val nowEpochMillis: () -> Long,
    val pairingLifetimeMillis: Long = 5 * 60_000L,
    val targetCapabilities: Set<NaviampConnectCapability> = emptySet(),
)

/** Shared discovery, approval, authentication, trust, and Connect settings state owner. */
class NaviampCoreConnectController(
    private val scope: CoroutineScope,
    private val stateStore: NaviampCoreStateStore,
    private val services: NaviampCoreConnectServices,
    private val targetPlayback: NaviampCorePlaybackController? = null,
    private val targetNowPlaying: NaviampCoreNowPlayingMediaController? = null,
    private val targetCatalog: NaviampCoreConnectCatalogController? = null,
    private val localPlayback: NaviampCorePlaybackController? = targetPlayback,
    private val sourceIdentity: () -> NaviampConnectSourceIdentity? = { null },
    private val providerSessions: NaviampCoreProviderSessionPort? = null,
    private val targetConnection: NaviampCoreConnectionController? = null,
    private val targetSettings: NaviampCoreSettingsController? = null,
) {
    private val controllerScope = CoroutineScope(
        scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]),
    )
    private val localIdentity = services.identity.loadOrCreate()
    private val localDevice = NaviampConnectDevice(
        deviceId = localIdentity.deviceId,
        displayName = services.displayName,
        role = when (services.role) {
            NaviampCoreConnectRole.Controller -> NaviampConnectDeviceRole.Controller
            NaviampCoreConnectRole.Target -> NaviampConnectDeviceRole.Target
        },
    )
    private val discovery = services.discovery?.let {
        NaviampConnectDiscoveryController(it, nowEpochMillis = services.nowEpochMillis)
    }
    private val advertising = services.advertising?.let {
        NaviampConnectAdvertisingController(it, services.nowEpochMillis)
    }
    private val targetPairing = NaviampConnectTargetPairingController()
    private var listener: NaviampConnectTransportListener? = null
    private var listenerJob: Job? = null
    private var authenticatedSession: NaviampConnectAuthenticatedSession? = null
    private var authenticatedSessionJob: Job? = null
    private var targetSession: NaviampConnectTargetSession? = null
    private var targetSnapshotFactory: NaviampCoreConnectTargetSnapshotFactory? = null
    private var targetSnapshotPublishJob: Job? = null
    private var controllerSession: NaviampConnectControllerSession? = null
    private val targetSessionMutex = Mutex()
    private var pendingTargetPairing: NaviampConnectPendingTargetPairing? = null
    private var selectedTarget: NaviampConnectDiscoveredTarget? = null
    private var enteredCode = ""
    private var phase = NaviampConnectPairingUiPhase.Inactive
    private var status: String? = null
    private var pendingProvisioning: NaviampConnectOfferConnectionProvisioning? = null
    private var pendingProvisioningControllerName: String? = null
    private val supportedTargetCapabilities: Set<NaviampConnectCapability> =
        NaviampCoreSupportedConnectTargetCapabilities + if (
            targetConnection != null && targetSettings != null
        ) {
            setOf(NaviampConnectCapability.ConnectionProvisioning)
        } else {
            emptySet()
        }

    val actions = NaviampConnectSettingsActions(
        onStartPairingMode = ::startPairingMode,
        onStopPairingMode = ::stopPairingMode,
        onRefreshTargets = ::refreshTargets,
        onTargetSelected = ::selectTarget,
        onPairingCodeChanged = ::changePairingCode,
        onSubmitPairingCode = ::submitPairingCode,
        onApproveController = ::approveController,
        onRejectController = ::rejectController,
        onTrustedDeviceSelected = {},
        onRemotePrevious = { sendRemoteCommand(NaviampConnectPrevious) },
        onRemotePlayPause = { sendRemoteCommand(NaviampConnectTogglePlayPause) },
        onRemoteNext = { sendRemoteCommand(NaviampConnectNext) },
        onRemoteHandoffQueue = ::handoffLocalQueue,
        onReceiveRemoteQueue = ::receiveRemoteQueue,
        onProvisionTarget = ::provisionTarget,
        onApproveProvisioning = ::approveProvisioning,
        onRejectProvisioning = ::rejectProvisioning,
        remoteNowPlayingActions = createNaviampCoreConnectRemoteNowPlayingActions(
            snapshot = { controllerSession?.state?.value?.snapshot },
            send = ::sendRemoteCommand,
        ),
    )

    init {
        publish()
        discovery?.let { controller ->
            controllerScope.launch {
                controller.state.collectLatest { publish() }
            }
        }
        advertising?.let { controller ->
            controllerScope.launch {
                controller.state.collectLatest { value ->
                    if (phase == NaviampConnectPairingUiPhase.Starting ||
                        phase == NaviampConnectPairingUiPhase.Advertising
                    ) {
                        when (value) {
                            is NaviampConnectAdvertisingStatus.Advertising -> {
                                phase = NaviampConnectPairingUiPhase.Advertising
                                status = "Ready for a controller on this local network."
                            }
                            is NaviampConnectAdvertisingStatus.Failed -> {
                                phase = NaviampConnectPairingUiPhase.Failed
                                status = value.message
                                closeTargetResources()
                            }
                            NaviampConnectAdvertisingStatus.Idle -> Unit
                            is NaviampConnectAdvertisingStatus.Starting -> Unit
                        }
                        publish()
                    }
                }
            }
        }
    }

    fun close() {
        stopPairingMode()
        closeAuthenticatedSession()
        discovery?.stop()
        controllerScope.cancel()
    }

    fun onTargetPlaybackChanged() {
        if (services.role != NaviampCoreConnectRole.Target || targetSession == null) return
        targetSnapshotPublishJob?.cancel()
        targetSnapshotPublishJob = controllerScope.launch {
            delay(TargetSnapshotDebounceMillis)
            publishTargetPlaybackSnapshot()
        }
    }

    /** Redirects shared catalog playback intents to the connected TV while browsing locally. */
    internal fun routeProductCommand(command: NaviampCoreCommand): Boolean {
        if (services.role != NaviampCoreConnectRole.Controller || controllerSession == null) return false
        val selection = command.connectMediaSelectionOrNull() ?: return false
        val identity = sourceIdentity()
        if (identity == null) {
            status = "Connect this device to the same music source before playing on the TV."
            publish()
            return true
        }
        sendRemoteCommand(selection.toCommand(identity))
        return true
    }

    private fun startPairingMode() {
        if (services.role != NaviampCoreConnectRole.Target || advertising == null) return
        stopPairingMode()
        closeAuthenticatedSession()
        phase = NaviampConnectPairingUiPhase.Starting
        status = "Starting secure pairing…"
        val code = services.newPairingCode().filter(Char::isDigit).take(6)
        if (code.length != 6) {
            fail("Could not create a secure six-digit pairing code.")
            return
        }
        try {
            val boundListener = services.transport.listen(0)
            listener = boundListener
            val now = services.nowEpochMillis()
            val advertisement = NaviampConnectAdvertisement(
                instanceId = services.newOpaqueId(),
                displayName = localDevice.displayName,
                protocolRange = NaviampConnectProtocolRange(),
                capabilities = if (targetPlayback != null) {
                    supportedTargetCapabilities
                } else {
                    services.targetCapabilities
                },
                port = boundListener.port,
                identityFingerprint = localIdentity.identityFingerprint,
                expiresAtEpochMillis = now + services.pairingLifetimeMillis,
            )
            targetPairing.start(advertisement, services.newOpaqueId(), code, now)
            advertising.start(advertisement)
            listenerJob = controllerScope.launch { acceptPairingRequest(boundListener) }
            publish()
        } catch (failure: Exception) {
            fail(failure.message ?: "Could not start Naviamp Connect pairing.")
        }
    }

    private suspend fun acceptPairingRequest(boundListener: NaviampConnectTransportListener) {
        try {
            val connection = boundListener.accept()
            val runtime = NaviampConnectTargetPairingRuntime(
                localDevice,
                services.identity,
                services.identityVerifier,
                targetPairing,
                services.pake,
                services.cipher,
            )
            when (val result = runtime.receiveRequest(connection, services.nowEpochMillis())) {
                is NaviampConnectTargetPairingRequestResult.AwaitingApproval -> {
                    pendingTargetPairing?.reject()
                    pendingTargetPairing = result.request
                    phase = NaviampConnectPairingUiPhase.AwaitingApproval
                    status = "${result.request.controller.displayName} wants to pair."
                }
                is NaviampConnectTargetPairingRequestResult.Rejected -> {
                    phase = NaviampConnectPairingUiPhase.Failed
                    status = result.code.userMessage()
                }
            }
            publish()
        } catch (failure: Exception) {
            if (listener === boundListener) fail(failure.message ?: "The pairing connection closed.")
        }
    }

    private fun approveController() {
        val pending = pendingTargetPairing ?: return
        pendingTargetPairing = null
        phase = NaviampConnectPairingUiPhase.Handshaking
        status = "Authenticating ${pending.controller.displayName}…"
        publish()
        controllerScope.launch {
            finishPairing(pending.approve(services.nowEpochMillis(), services.newOpaqueId()))
        }
    }

    private fun rejectController() {
        pendingTargetPairing?.reject()
        pendingTargetPairing = null
        closeTargetResources()
        phase = NaviampConnectPairingUiPhase.Inactive
        status = "Pairing request rejected."
        publish()
    }

    private fun refreshTargets() {
        if (services.role != NaviampCoreConnectRole.Controller || discovery == null) return
        closeAuthenticatedSession()
        selectedTarget = null
        enteredCode = ""
        phase = NaviampConnectPairingUiPhase.Starting
        status = "Searching this local network…"
        discovery.stop()
        discovery.start()
        phase = NaviampConnectPairingUiPhase.Advertising
        publish()
    }

    private fun selectTarget(targetUi: NaviampConnectDiscoveredTargetUi) {
        selectedTarget = discovery?.state?.value?.targets?.firstOrNull {
            it.advertisement.instanceId == targetUi.instanceId
        } ?: return
        enteredCode = ""
        phase = NaviampConnectPairingUiPhase.AwaitingCode
        status = "Enter the six-digit code shown on ${targetUi.displayName}."
        publish()
    }

    private fun changePairingCode(value: String) {
        enteredCode = value.filter(Char::isDigit).take(6)
        publish()
    }

    private fun submitPairingCode() {
        val target = selectedTarget ?: return
        if (enteredCode.length != 6) {
            status = "Enter all six digits shown on the TV."
            publish()
            return
        }
        phase = NaviampConnectPairingUiPhase.Handshaking
        status = "Authenticating ${target.advertisement.displayName}…"
        val code = enteredCode.toCharArray()
        enteredCode = ""
        publish()
        controllerScope.launch {
            val runtime = NaviampConnectControllerPairingRuntime(
                localDevice,
                services.identity,
                services.identityVerifier,
                services.transport,
                services.pake,
                services.cipher,
            )
            var result: NaviampConnectPairingRuntimeResult =
                NaviampConnectPairingRuntimeResult.Failed(NaviampConnectErrorCode.TargetUnavailable)
            for (address in target.addresses) {
                val attempt = try {
                    runtime.pair(
                        address,
                        target.advertisement,
                        code.copyOf(),
                        services.nowEpochMillis(),
                        services.newOpaqueId(),
                    )
                } catch (_: Exception) {
                    null
                }
                if (attempt != null) result = attempt
                if (attempt is NaviampConnectPairingRuntimeResult.Paired) {
                    break
                }
            }
            code.fill('\u0000')
            finishPairing(result)
        }
    }

    private suspend fun finishPairing(result: NaviampConnectPairingRuntimeResult) {
        when (result) {
            is NaviampConnectPairingRuntimeResult.Paired -> {
                services.trust.upsert(result.trust)
                discovery?.stop()
                closeTargetResources()
                val connected = when (services.role) {
                    NaviampCoreConnectRole.Target -> startTargetSession(result.session)
                    NaviampCoreConnectRole.Controller -> startControllerSession(result.session)
                }
                if (connected) {
                    phase = NaviampConnectPairingUiPhase.Paired
                    status = "Connected to ${result.trust.displayName}."
                } else {
                    result.session.close()
                    phase = NaviampConnectPairingUiPhase.Failed
                    status = "The paired device did not start a compatible control session."
                }
            }
            is NaviampConnectPairingRuntimeResult.Failed -> {
                closeTargetResources()
                phase = NaviampConnectPairingUiPhase.Failed
                status = result.code.userMessage()
            }
        }
        publish()
    }

    private suspend fun startTargetSession(session: NaviampConnectAuthenticatedSession): Boolean {
        val playback = targetPlayback ?: return false
        val nowPlaying = targetNowPlaying ?: return false
        val catalog = targetCatalog ?: return false
        val snapshots = NaviampCoreConnectTargetSnapshotFactory(
            target = localDevice,
            capabilities = supportedTargetCapabilities,
            sourceIdentity = sourceIdentity,
        )
        val snapshot = snapshots.create(
            revision = 0,
            live = playback.connectLiveState(),
            volumePercent = stateStore.state.value.shell.playback.settings.volumePercent,
        )
        session.send(
            NaviampConnectWelcome(
                sessionId = session.sessionId,
                protocolVersion = session.protocolVersion,
                target = localDevice,
                capabilities = supportedTargetCapabilities,
                snapshot = snapshot,
            ),
        )
        val connectedTarget = NaviampConnectTargetSession(
            sessionId = session.sessionId,
            protocolVersion = session.protocolVersion,
            initialSnapshot = snapshot,
            transport = NaviampConnectSessionTransport(session::sendEnvelope),
            executor = NaviampCoreConnectTargetCommandExecutor(
                playback,
                nowPlaying,
                catalog,
                stateStore,
                snapshots,
                offerProvisioning = { offer ->
                    pendingProvisioning = offer
                    pendingProvisioningControllerName = session.trust?.peerDevice?.displayName
                    status = "Approve the connection setup request on this TV."
                    publish()
                    true
                },
            ),
            initialOutboundSequence = session.nextOutboundSequence(),
        )
        authenticatedSession = session
        targetSession = connectedTarget
        targetSnapshotFactory = snapshots
        authenticatedSessionJob = controllerScope.launch { receiveTargetSession(session, connectedTarget) }
        return true
    }

    private suspend fun startControllerSession(session: NaviampConnectAuthenticatedSession): Boolean {
        val welcomeEnvelope = runCatching { session.receive() }.getOrNull() ?: return false
        val welcome = welcomeEnvelope.message as? NaviampConnectWelcome ?: return false
        if (welcome.sessionId != session.sessionId ||
            welcome.protocolVersion != session.protocolVersion ||
            welcome.target.deviceId != session.trust?.peerDevice?.deviceId
        ) {
            return false
        }
        val connectedController = NaviampConnectControllerSession(
            transport = NaviampConnectSessionTransport(session::sendEnvelope),
            requestIds = app.naviamp.app.NaviampConnectRequestIdFactory(services.newOpaqueId),
        )
        connectedController.connect(
            sessionId = session.sessionId,
            protocolVersion = session.protocolVersion,
            target = welcome.target,
            capabilities = welcome.capabilities,
            snapshot = welcome.snapshot,
            nextOutboundSequence = session.nextOutboundSequence(),
            lastReceivedSequence = welcomeEnvelope.sequence,
        )
        authenticatedSession = session
        controllerSession = connectedController
        authenticatedSessionJob = controllerScope.launch { receiveControllerSession(session, connectedController) }
        publish()
        return true
    }

    private suspend fun receiveTargetSession(
        session: NaviampConnectAuthenticatedSession,
        connectedTarget: NaviampConnectTargetSession,
    ) {
        runCatching {
            while (authenticatedSession === session) {
                val envelope = session.receive()
                targetSessionMutex.withLock { connectedTarget.receive(envelope) }
            }
        }
        if (authenticatedSession === session) sessionEnded("The controller disconnected.")
    }

    private suspend fun receiveControllerSession(
        session: NaviampConnectAuthenticatedSession,
        connectedController: NaviampConnectControllerSession,
    ) {
        runCatching {
            while (authenticatedSession === session) {
                connectedController.receive(session.receive())
                publish()
            }
        }
        if (authenticatedSession === session) sessionEnded("The TV disconnected.")
    }

    private fun sessionEnded(message: String) {
        closeAuthenticatedSession()
        phase = NaviampConnectPairingUiPhase.Inactive
        status = message
        publish()
    }

    private fun closeAuthenticatedSession() {
        targetSnapshotPublishJob?.cancel()
        targetSnapshotPublishJob = null
        authenticatedSessionJob?.cancel()
        authenticatedSessionJob = null
        authenticatedSession?.close()
        authenticatedSession = null
        targetSession = null
        targetSnapshotFactory = null
        controllerSession?.disconnect()
        controllerSession = null
        pendingProvisioning = null
        pendingProvisioningControllerName = null
    }

    private fun sendRemoteCommand(command: app.naviamp.domain.connect.NaviampConnectCommand) {
        val connected = controllerSession ?: return
        controllerScope.launch {
            when (val result = connected.send(command)) {
                is app.naviamp.app.NaviampConnectCommandSendResult.Sent -> status = null
                is app.naviamp.app.NaviampConnectCommandSendResult.Rejected -> status = result.error.message
            }
            publish()
        }
    }

    private fun handoffLocalQueue() {
        val playback = localPlayback ?: return
        val identity = sourceIdentity() ?: return
        val live = playback.connectLiveState()
        if (live.queue.current == null) return
        val localSnapshot = NaviampCoreConnectTargetSnapshotFactory(
            target = localDevice,
            capabilities = emptySet(),
            sourceIdentity = { identity },
        ).create(
            revision = 0,
            live = live,
            volumePercent = stateStore.state.value.shell.playback.settings.volumePercent,
        )
        sendRemoteCommand(
            NaviampConnectHandoffQueue(
                sourceIdentity = identity,
                queue = localSnapshot.queue,
                positionMillis = localSnapshot.playback.positionMillis,
                repeatMode = localSnapshot.playback.repeatMode,
                shuffled = localSnapshot.playback.shuffled,
                playing = live.playbackState == app.naviamp.domain.playback.PlaybackState.Playing,
            ),
        )
    }

    private fun provisionTarget() {
        val sessions = providerSessions ?: return
        val connected = controllerSession ?: return
        controllerScope.launch {
            val editable = runCatching { sessions.currentProvisioningConnection() }.getOrNull()
            if (editable == null) {
                status = "Connect this device to the server you want to configure on the TV."
                publish()
                return@launch
            }
            when (val exported = editable.form.toConnectProvisioningExport()) {
                is NaviampCoreConnectProvisioningExport.Unsupported -> status = exported.message
                is NaviampCoreConnectProvisioningExport.Ready -> {
                    when (
                        val result = connected.send(
                            NaviampConnectOfferConnectionProvisioning(
                                profile = exported.profile,
                                portableSettings = stateStore.state.value.toConnectPortableSettings(),
                            ),
                        )
                    ) {
                        is app.naviamp.app.NaviampConnectCommandSendResult.Sent ->
                            status = "Approve setup on ${connected.state.value.target?.displayName ?: "the TV"}."
                        is app.naviamp.app.NaviampConnectCommandSendResult.Rejected -> status = result.error.message
                    }
                }
            }
            publish()
        }
    }

    private fun approveProvisioning() {
        val offer = pendingProvisioning ?: return
        val connection = targetConnection ?: return
        val settings = targetSettings ?: return
        status = "Validating ${offer.profile.displayName.ifBlank { offer.profile.serverUrl }}…"
        publish()
        controllerScope.launch {
            val connected = connection.provisionConnect(offer.profile.toConnectionFormState())
            if (connected) {
                offer.portableSettings?.let(settings::applyConnectPortableSettings)
                pendingProvisioning = null
                pendingProvisioningControllerName = null
                status = "TV setup completed securely."
                publishTargetPlaybackSnapshot()
            } else {
                status = "Could not validate that connection. Check the server and credential, then retry."
            }
            publish()
        }
    }

    private fun rejectProvisioning() {
        pendingProvisioning = null
        pendingProvisioningControllerName = null
        status = "Connection setup request rejected."
        publish()
    }

    private fun receiveRemoteQueue() {
        val connected = controllerSession ?: return
        val playback = localPlayback ?: return
        val remote = connected.state.value.snapshot ?: return
        val localIdentity = sourceIdentity() ?: return
        val remoteIdentity = remote.sourceIdentity ?: return
        if (!localIdentity.isCompatibleWith(remoteIdentity)) {
            status = "The TV queue belongs to a different music source."
            publish()
            return
        }
        controllerScope.launch {
            val wasPlaying = remote.playback.state ==
                app.naviamp.domain.connect.NaviampConnectPlaybackState.Playing
            if (wasPlaying && !sendRemoteCommandAndAwait(NaviampConnectPause)) {
                status = "The TV did not pause, so its queue was left in place."
                publish()
                return@launch
            }
            val accepted = playback.handoffConnectQueue(
                NaviampConnectHandoffQueue(
                    sourceIdentity = remoteIdentity,
                    queue = remote.queue,
                    positionMillis = remote.playback.positionMillis,
                    repeatMode = remote.playback.repeatMode,
                    shuffled = remote.playback.shuffled,
                    playing = wasPlaying,
                ),
            )
            if (!accepted) {
                if (wasPlaying) sendRemoteCommand(NaviampConnectPlay)
                status = "This device could not resolve every track, so the TV kept playback authority."
            } else {
                status = "The TV queue is now playing on this device."
            }
            publish()
        }
    }

    private suspend fun sendRemoteCommandAndAwait(
        command: app.naviamp.domain.connect.NaviampConnectCommand,
    ): Boolean {
        val connected = controllerSession ?: return false
        val sent = connected.send(command) as? app.naviamp.app.NaviampConnectCommandSendResult.Sent
            ?: return false
        val completed = withTimeoutOrNull(10_000L) {
            connected.state.first { state ->
                state.pendingRequests.none { request -> request.requestId == sent.requestId }
            }
        } ?: return false
        return completed.lastError == null
    }

    private suspend fun publishTargetPlaybackSnapshot() {
        val connectedTarget = targetSession ?: return
        val snapshots = targetSnapshotFactory ?: return
        val playback = targetPlayback ?: return
        targetSessionMutex.withLock {
            if (targetSession !== connectedTarget) return@withLock
            val current = connectedTarget.snapshot()
            val projected = snapshots.create(
                revision = current.revision,
                live = playback.connectLiveState(),
                volumePercent = stateStore.state.value.shell.playback.settings.volumePercent,
                visibleSurface = current.playback.visibleSurface,
            )
            if (projected != current) {
                connectedTarget.publishLocalSnapshot(projected.copy(revision = current.revision + 1))
            }
        }
    }

    private fun stopPairingMode() {
        pendingTargetPairing?.reject()
        pendingTargetPairing = null
        closeTargetResources()
        targetPairing.stop()
        phase = NaviampConnectPairingUiPhase.Inactive
        status = null
        publish()
    }

    private fun closeTargetResources() {
        advertising?.stop()
        listenerJob?.cancel()
        listenerJob = null
        listener?.close()
        listener = null
    }

    private fun fail(message: String) {
        closeTargetResources()
        phase = NaviampConnectPairingUiPhase.Failed
        status = message
        publish()
    }

    private fun publish() {
        val discovered = discovery?.state?.value
        val remote = controllerSession?.state?.value
        val remoteSnapshot = remote?.snapshot
        val remoteCurrent = remoteSnapshot?.let { snapshot ->
            snapshot.queue.occurrences.firstOrNull {
                it.occurrenceId == snapshot.playback.currentOccurrenceId
            }
        }
        val effectiveStatus = discovered?.problem?.userMessage() ?: status
        val trusts = services.trust.load()
        stateStore.updateShell { shell ->
            shell.copy(
                connect = NaviampConnectSettingsUi(
                    available = true,
                    role = if (services.role == NaviampCoreConnectRole.Target) {
                        NaviampConnectUiRole.Target
                    } else {
                        NaviampConnectUiRole.Controller
                    },
                    pairingPhase = phase,
                    pairingCode = targetPairing.displayCode(),
                    enteredPairingCode = enteredCode,
                    pendingControllerName = pendingTargetPairing?.controller?.displayName,
                    selectedTargetId = selectedTarget?.advertisement?.instanceId,
                    status = effectiveStatus,
                    connectedTargetName = remote?.target?.displayName,
                    remoteTrackTitle = remoteCurrent?.title,
                    remoteArtistName = remoteCurrent?.artistName,
                    remotePlaying = remoteSnapshot?.playback?.state ==
                        app.naviamp.domain.connect.NaviampConnectPlaybackState.Playing,
                    remoteHasPrevious = remoteSnapshot?.queue?.currentIndex?.let { it > 0 } == true,
                    remoteHasNext = remoteSnapshot?.queue?.let { it.currentIndex in 0 until it.occurrences.lastIndex } == true,
                    remoteNowPlaying = remote?.target?.let { target ->
                        remoteSnapshot?.toRemoteNowPlayingUi(target.displayName)
                    },
                    canHandoffLocalQueue = remote?.capabilities?.contains(
                        NaviampConnectCapability.QueueHandoff,
                    ) == true && sourceIdentity() != null &&
                        localPlayback?.connectLiveState()?.queue?.current != null,
                    canReceiveRemoteQueue = remote?.capabilities?.contains(
                        NaviampConnectCapability.QueueHandoff,
                    ) == true && remoteSnapshot?.queue?.currentIndex?.let { it >= 0 } == true &&
                        sourceIdentity()?.let { local ->
                            remoteSnapshot.sourceIdentity?.let(local::isCompatibleWith)
                        } == true,
                    canProvisionTarget = remote?.capabilities?.contains(
                        NaviampConnectCapability.ConnectionProvisioning,
                    ) == true && providerSessions?.currentSourceId() != null,
                    pendingProvisioningControllerName = pendingProvisioningControllerName,
                    pendingProvisioningConnectionName = pendingProvisioning?.profile?.displayName
                        ?.ifBlank { pendingProvisioning?.profile?.serverUrl },
                    discoveredTargets = discovered?.targets.orEmpty().map { target ->
                        NaviampConnectDiscoveredTargetUi(
                            instanceId = target.advertisement.instanceId,
                            displayName = target.advertisement.displayName,
                            detail = target.addresses.firstOrNull().orEmpty(),
                        )
                    },
                    trustedDevices = trusts.map(NaviampConnectTrustRecord::toUi),
                ),
            )
        }
    }
}

private fun NaviampConnectTargetPairingController.displayCode(): String? =
    when (val current = state) {
        is app.naviamp.domain.connect.NaviampConnectTargetPairingState.Advertising -> current.displayCode
        is app.naviamp.domain.connect.NaviampConnectTargetPairingState.AwaitingApproval -> current.advertising.displayCode
        else -> null
    }

private fun NaviampConnectTrustRecord.toUi() = NaviampConnectTrustedDeviceUi(
    deviceId = trustedDeviceId,
    displayName = displayName,
    detail = "Paired Naviamp ${peerDevice.role.name.lowercase()}",
)

private fun NaviampConnectDiscoveryProblem.userMessage(): String = when (this) {
    NaviampConnectDiscoveryProblem.PermissionDenied -> "Local-network permission is required."
    is NaviampConnectDiscoveryProblem.Unavailable -> message
    is NaviampConnectDiscoveryProblem.Failed -> message
    is NaviampConnectDiscoveryProblem.TargetIdentityChanged -> "A discovered target changed identity."
}

private fun NaviampConnectErrorCode.userMessage(): String = when (this) {
    NaviampConnectErrorCode.IncompatibleProtocol -> "This device requires a different Naviamp version."
    NaviampConnectErrorCode.AuthenticationRequired -> "The pairing code or device identity could not be verified."
    NaviampConnectErrorCode.PairingExpired -> "The pairing code expired. Start pairing again."
    NaviampConnectErrorCode.RateLimited -> "Too many pairing attempts. Try again shortly."
    NaviampConnectErrorCode.TargetUnavailable -> "The target is no longer available."
    NaviampConnectErrorCode.InvalidRequest -> "The pairing request was invalid."
    else -> "Naviamp Connect could not complete the request."
}

private const val TargetSnapshotDebounceMillis = 250L
