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
import app.naviamp.app.NaviampConnectTransportConnection
import app.naviamp.app.NaviampConnectTransportListener
import app.naviamp.app.NaviampConnectTrustRepository
import app.naviamp.app.NaviampConnectControllerPairingRuntime
import app.naviamp.app.receivePlaintext
import app.naviamp.domain.connect.NaviampConnectAdvertisement
import app.naviamp.domain.connect.NaviampConnectCapability
import app.naviamp.domain.connect.NaviampConnectHandoffQueue
import app.naviamp.domain.connect.NaviampConnectOfferConnectionProvisioning
import app.naviamp.domain.connect.NaviampConnectPause
import app.naviamp.domain.connect.NaviampConnectPlay
import app.naviamp.domain.connect.NaviampConnectDevice
import app.naviamp.domain.connect.NaviampConnectDeviceCapability
import app.naviamp.domain.connect.NaviampConnectDeviceRole
import app.naviamp.domain.connect.NaviampConnectErrorCode
import app.naviamp.domain.connect.NaviampConnectProtocolRange
import app.naviamp.domain.connect.NaviampConnectTargetPairingController
import app.naviamp.domain.connect.NaviampConnectTrustRecord
import app.naviamp.domain.connect.NaviampConnectTrustedEndpoint
import app.naviamp.domain.connect.NaviampConnectSourceIdentity
import app.naviamp.domain.connect.NaviampConnectWelcome
import app.naviamp.domain.connect.NaviampConnectNext
import app.naviamp.domain.connect.NaviampConnectPrevious
import app.naviamp.domain.connect.NaviampConnectTogglePlayPause
import app.naviamp.domain.connect.requiredDeviceCapability
import app.naviamp.ui.NaviampConnectDiscoveredTargetUi
import app.naviamp.ui.NaviampConnectPairingUiPhase
import app.naviamp.ui.NaviampConnectSettingsActions
import app.naviamp.ui.NaviampConnectSettingsUi
import app.naviamp.ui.NaviampConnectTrustedDeviceUi
import app.naviamp.ui.NaviampConnectUiRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
    val deviceCapabilities: Set<NaviampConnectDeviceCapability> = setOf(
        when (role) {
            NaviampCoreConnectRole.Controller -> NaviampConnectDeviceRole.Controller
            NaviampCoreConnectRole.Target -> NaviampConnectDeviceRole.Target
        }.requiredDeviceCapability(),
    ),
    val displayName: String,
    val identity: NaviampConnectDeviceIdentityEffect,
    val identityVerifier: NaviampConnectIdentityVerifier,
    val transport: NaviampConnectTransportFactory,
    val pake: NaviampConnectPakeFactory,
    val cipher: NaviampConnectAuthenticatedCipherFactory,
    val trust: NaviampConnectTrustRepository,
    val credentials: app.naviamp.app.NaviampConnectSessionCredentialRepository? = null,
    val discovery: NaviampConnectDiscoveryEffect? = null,
    val advertising: NaviampConnectAdvertisingEffect? = null,
    val newOpaqueId: () -> String,
    val newPairingCode: () -> String,
    val nowEpochMillis: () -> Long,
    val pairingLifetimeMillis: Long = 5 * 60_000L,
    val pairingHelloTimeoutMillis: Long = 10_000L,
    val pairingHandshakeTimeoutMillis: Long = 30_000L,
    val pairingListenPort: Int = 0,
    val targetCapabilities: Set<NaviampConnectCapability> = emptySet(),
) {
    init {
        require(pairingLifetimeMillis > 0) { "The pairing lifetime must be positive." }
        require(pairingHelloTimeoutMillis > 0) { "The pairing hello timeout must be positive." }
        require(pairingHandshakeTimeoutMillis > 0) { "The pairing handshake timeout must be positive." }
        require(pairingListenPort in 0..65_535) { "The pairing listener port is invalid." }
        val requiredCapability = when (role) {
            NaviampCoreConnectRole.Controller -> NaviampConnectDeviceCapability.ControlPlayback
            NaviampCoreConnectRole.Target -> NaviampConnectDeviceCapability.PlaybackTarget
        }
        require(requiredCapability in deviceCapabilities) {
            "Connect services must support their active session role."
        }
    }
}

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
        deviceCapabilities = services.deviceCapabilities,
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
    private var acceptedPairingConnection: NaviampConnectTransportConnection? = null
    private var pairingExpiryJob: Job? = null
    private var activePairingHandshake: NaviampConnectPendingTargetPairing? = null
    private var pairingHandshakeJob: Job? = null
    private var authenticatedSession: NaviampConnectAuthenticatedSession? = null
    private var authenticatedSessionJob: Job? = null
    private var targetSession: NaviampConnectTargetSession? = null
    private var targetSnapshotFactory: NaviampCoreConnectTargetSnapshotFactory? = null
    private var targetSnapshotPublishJob: Job? = null
    private var controllerSession: NaviampConnectControllerSession? = null
    private val targetSessionMutex = Mutex()
    private var pendingTargetPairing: NaviampConnectPendingTargetPairing? = null
    private var selectedTarget: NaviampConnectDiscoveredTarget? = null
    private var pendingTrustedDeviceId: String? = null
    private val recentlyResolvedTargets = services.trust.load().mapNotNull { trust ->
        trust.lastKnownEndpoint?.takeIf {
            it.advertisement.identityFingerprint == trust.identityFingerprint
        }?.let { endpoint ->
            trust.identityFingerprint to NaviampConnectDiscoveredTarget(
                serviceName = trust.displayName,
                addresses = endpoint.addresses,
                advertisement = endpoint.advertisement,
            )
        }
    }.toMap().toMutableMap()
    private var reconnectJob: Job? = null
    private var automaticReconnectRetryJob: Job? = null
    private var activeReconnectConnection: NaviampConnectTransportConnection? = null
    private var automaticReconnectSuppressed = false
    private var enteredCode = ""
    private var phase = NaviampConnectPairingUiPhase.Inactive
    private var status: String? = null
    private var pendingProvisioning: NaviampConnectOfferConnectionProvisioning? = null
    private var pendingProvisioningSession: NaviampConnectAuthenticatedSession? = null
    private var pendingProvisioningControllerName: String? = null
    private var needsProvisioningCredential = false
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
        onTrustedDeviceSelected = ::reconnectTrustedDevice,
        onPairingCodeChanged = ::changePairingCode,
        onSubmitPairingCode = ::submitPairingCode,
        onApproveController = ::approveController,
        onRejectController = ::rejectController,
        onRemotePrevious = { sendRemoteCommand(NaviampConnectPrevious) },
        onRemotePlayPause = { sendRemoteCommand(NaviampConnectTogglePlayPause) },
        onRemoteNext = { sendRemoteCommand(NaviampConnectNext) },
        onStopControlling = ::stopControlling,
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
                controller.state.collectLatest { discoveryState ->
                    publish()
                    attemptPendingTrustedReconnect(discoveryState.targets)
                    val expiresAt = discoveryState.targets.minOfOrNull {
                        it.advertisement.expiresAtEpochMillis
                    } ?: return@collectLatest
                    delay((expiresAt - services.nowEpochMillis()).coerceAtLeast(0L))
                    controller.refreshExpiry()
                }
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
        when (services.role) {
            NaviampCoreConnectRole.Target -> ensureTrustedTargetListener()
            NaviampCoreConnectRole.Controller -> beginAutomaticTrustedReconnect()
        }
    }

    fun close() {
        stopPairingMode()
        automaticReconnectRetryJob?.cancel()
        automaticReconnectRetryJob = null
        closeAuthenticatedSession()
        discovery?.stop()
        controllerScope.cancel()
    }

    fun onLocalPlaybackChanged() {
        if (services.role == NaviampCoreConnectRole.Controller) {
            publish()
            return
        }
        if (targetSession == null) return
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
        val existingListener = listener
        if (existingListener == null) {
            stopPairingMode()
        } else {
            resetPairingOfferKeepingListener()
        }
        phase = NaviampConnectPairingUiPhase.Starting
        status = "Starting secure pairing…"
        val code = services.newPairingCode().filter(Char::isDigit).take(6)
        if (code.length != 6) {
            fail("Could not create a secure six-digit pairing code.")
            return
        }
        try {
            val boundListener = existingListener ?: services.transport.listen(services.pairingListenPort)
            listener = boundListener
            val now = services.nowEpochMillis()
            val advertisement = NaviampConnectAdvertisement(
                instanceId = services.newOpaqueId(),
                displayName = localDevice.displayName,
                protocolRange = NaviampConnectProtocolRange(),
                deviceCapabilities = localDevice.deviceCapabilities,
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
            pairingExpiryJob = controllerScope.launch {
                delay((advertisement.expiresAtEpochMillis - services.nowEpochMillis()).coerceAtLeast(0L))
                pairingExpiryJob = null
                if (targetPairing.expireIfNeeded(services.nowEpochMillis())) {
                    advertising.refreshExpiry()
                    if (services.trust.load().isNotEmpty()) {
                        startPairingMode()
                    } else {
                        closeTargetResources()
                        phase = NaviampConnectPairingUiPhase.Failed
                        status = NaviampConnectErrorCode.PairingExpired.userMessage()
                        publish()
                    }
                }
            }
            if (listenerJob?.isActive != true) {
                listenerJob = controllerScope.launch { acceptPairingRequests(boundListener) }
            }
            publish()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            fail(failure.message ?: "Could not start Naviamp Connect pairing.")
        }
    }

    private suspend fun acceptPairingRequests(boundListener: NaviampConnectTransportListener) {
        try {
            while (listener === boundListener) {
                val connection = boundListener.accept()
                acceptedPairingConnection = connection
                var initialFailureStatus: String? = null
                val initialEnvelope = try {
                    receiveInitialConnectEnvelopeWithDeadline(connection)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    initialFailureStatus = "An incomplete connection request closed. Still waiting for a controller."
                    null
                }
                if (initialEnvelope == null) {
                    connection.close()
                    acceptedPairingConnection = null
                    if (listener !== boundListener) return
                    phase = NaviampConnectPairingUiPhase.Advertising
                    status = initialFailureStatus
                        ?: "An incomplete connection request timed out. Still waiting for a controller."
                    publish()
                    continue
                }
                if (initialEnvelope.message is app.naviamp.domain.connect.NaviampConnectResumeHello) {
                    val resumed = resumeTrustedController(connection, initialEnvelope)
                    acceptedPairingConnection = null
                    if (listener !== boundListener) return
                    if (resumed) continue
                    phase = NaviampConnectPairingUiPhase.Advertising
                    status = "A trusted reconnect could not be authenticated. Still waiting for a controller."
                    publish()
                    continue
                }
                val runtime = NaviampConnectTargetPairingRuntime(
                    localDevice,
                    services.identity,
                    services.identityVerifier,
                    targetPairing,
                    services.pake,
                    services.cipher,
                )
                val result = receivePairingRequestWithDeadline(runtime, connection, initialEnvelope)
                if (result == null) {
                    connection.close()
                    acceptedPairingConnection = null
                    if (listener !== boundListener) return
                    phase = NaviampConnectPairingUiPhase.Advertising
                    status = "An incomplete pairing request timed out. Still waiting for a controller."
                    publish()
                    continue
                }
                when (result) {
                    is NaviampConnectTargetPairingRequestResult.AwaitingApproval -> {
                        acceptedPairingConnection = null
                        pendingTargetPairing?.reject()
                        pendingTargetPairing = result.request
                        phase = NaviampConnectPairingUiPhase.AwaitingApproval
                        status = "${result.request.controller.displayName} wants to pair."
                        publish()
                        return
                    }
                    is NaviampConnectTargetPairingRequestResult.Rejected -> {
                        acceptedPairingConnection = null
                        if (targetPairing.expireIfNeeded(services.nowEpochMillis())) return
                        phase = NaviampConnectPairingUiPhase.Advertising
                        status = "Pairing request rejected. Still waiting for a controller."
                        publish()
                    }
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            if (listener === boundListener) fail(failure.message ?: "The pairing connection closed.")
        } finally {
            acceptedPairingConnection?.close()
            acceptedPairingConnection = null
        }
    }

    private suspend fun receivePairingRequestWithDeadline(
        runtime: NaviampConnectTargetPairingRuntime,
        connection: NaviampConnectTransportConnection,
        initialEnvelope: app.naviamp.domain.connect.NaviampConnectEnvelope,
    ): NaviampConnectTargetPairingRequestResult? {
        // Keep the blocking native receive in a sibling job so a Core timeout can close the socket
        // immediately instead of waiting for a platform read to become cooperatively cancellable.
        return awaitNaviampConnectSocketOperation(
            scope = controllerScope,
            timeoutMillis = services.pairingHelloTimeoutMillis,
            close = connection::close,
        ) {
            runtime.receiveRequest(connection, services.nowEpochMillis(), initialEnvelope)
        }
    }

    private suspend fun receiveInitialConnectEnvelopeWithDeadline(
        connection: NaviampConnectTransportConnection,
    ): app.naviamp.domain.connect.NaviampConnectEnvelope? = awaitNaviampConnectSocketOperation(
        scope = controllerScope,
        timeoutMillis = services.pairingHelloTimeoutMillis,
        close = connection::close,
        operation = connection::receivePlaintext,
    )

    private suspend fun resumeTrustedController(
        connection: NaviampConnectTransportConnection,
        initialEnvelope: app.naviamp.domain.connect.NaviampConnectEnvelope,
    ): Boolean {
        val hello = initialEnvelope.message as? app.naviamp.domain.connect.NaviampConnectResumeHello
            ?: return false
        val trust = services.trust.load().firstOrNull { record ->
            record.peerDevice.deviceId == hello.device.deviceId &&
                record.identityFingerprint == hello.identity.identityFingerprint &&
                record.publicKeyBase64 == hello.identity.publicKeyBase64
        } ?: run {
            connection.close()
            return false
        }
        val credential = services.credentials?.read(trust.peerDevice.deviceId) ?: run {
            connection.close()
            return false
        }
        val advertisement = when (val current = targetPairing.state) {
            is app.naviamp.domain.connect.NaviampConnectTargetPairingState.Advertising -> current.advertisement
            is app.naviamp.domain.connect.NaviampConnectTargetPairingState.AwaitingApproval -> current.advertising.advertisement
            else -> {
                credential.fill(0)
                connection.close()
                return false
            }
        }
        val runtime = app.naviamp.app.NaviampConnectTargetResumptionRuntime(
            localDevice = localDevice,
            identityEffect = services.identity,
            identityVerifier = services.identityVerifier,
            cipherFactory = services.cipher,
        )
        val result = awaitNaviampConnectSocketOperation(
            scope = controllerScope,
            timeoutMillis = services.pairingHandshakeTimeoutMillis,
            close = connection::close,
        ) {
            runtime.reconnect(
                connection = connection,
                helloEnvelope = initialEnvelope,
                advertisement = advertisement,
                trust = trust,
                credential = credential,
                sessionId = services.newOpaqueId(),
            )
        } ?: return false
        return when (result) {
            is app.naviamp.app.NaviampConnectResumptionResult.Connected -> {
                acceptedPairingConnection = null
                val connected = replaceTargetSession(result.session)
                if (connected) {
                    phase = NaviampConnectPairingUiPhase.Paired
                    status = "Reconnected to ${trust.displayName}."
                } else {
                    result.session.close()
                    phase = NaviampConnectPairingUiPhase.Failed
                    status = "The trusted controller did not start a compatible session."
                }
                publish()
                connected
            }
            is app.naviamp.app.NaviampConnectResumptionResult.Failed -> false
        }
    }

    private fun approveController() {
        val pending = pendingTargetPairing ?: return
        pendingTargetPairing = null
        phase = NaviampConnectPairingUiPhase.Handshaking
        status = "Authenticating ${pending.controller.displayName}…"
        publish()
        activePairingHandshake = pending
        pairingHandshakeJob = controllerScope.launch {
            val result = awaitNaviampConnectSocketOperation(
                scope = controllerScope,
                timeoutMillis = services.pairingHandshakeTimeoutMillis,
                close = { pending.cancel(services.nowEpochMillis()) },
            ) {
                pending.approve(services.nowEpochMillis(), services.newOpaqueId())
            }
            activePairingHandshake = null
            pairingHandshakeJob = null
            finishPairing(
                result ?: NaviampConnectPairingRuntimeResult.Failed(
                    NaviampConnectErrorCode.AuthenticationRequired,
                ),
            )
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
        suspendAutomaticReconnectForManualPairing()
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

    private fun reconnectTrustedDevice(device: NaviampConnectTrustedDeviceUi) {
        if (services.role != NaviampCoreConnectRole.Controller) return
        val discoveryController = discovery ?: return
        val trust = services.trust.load().firstOrNull { it.trustedDeviceId == device.deviceId } ?: return
        if (services.credentials?.contains(trust.peerDevice.deviceId) != true) {
            status = "Pair with ${trust.displayName} once more to enable secure reconnect."
            publish()
            return
        }
        reconnectJob?.cancel()
        reconnectJob = null
        automaticReconnectRetryJob?.cancel()
        automaticReconnectRetryJob = null
        automaticReconnectSuppressed = false
        closeAuthenticatedSession()
        pendingTrustedDeviceId = trust.trustedDeviceId
        selectedTarget = null
        enteredCode = ""
        phase = NaviampConnectPairingUiPhase.Starting
        status = "Looking for ${trust.displayName}… Start pairing mode on the TV if needed."
        discoveryController.start()
        publish()
        attemptPendingTrustedReconnect(discoveryController.state.value.targets)
    }

    private fun attemptPendingTrustedReconnect(targets: List<NaviampConnectDiscoveredTarget>) {
        val trustedDeviceId = pendingTrustedDeviceId ?: return
        if (reconnectJob?.isActive == true) return
        val trust = services.trust.load().firstOrNull { it.trustedDeviceId == trustedDeviceId } ?: run {
            pendingTrustedDeviceId = null
            return
        }
        targets.forEach { target ->
            recentlyResolvedTargets[target.advertisement.identityFingerprint] = target
        }
        val target = selectNaviampConnectReconnectTarget(
            identityFingerprint = trust.identityFingerprint,
            discoveredTargets = targets,
            recentlyResolvedTarget = recentlyResolvedTargets[trust.identityFingerprint],
        ) ?: return
        pendingTrustedDeviceId = null
        phase = NaviampConnectPairingUiPhase.Handshaking
        status = "Securely reconnecting to ${trust.displayName}…"
        publish()
        reconnectJob = controllerScope.launch {
            val runtime = app.naviamp.app.NaviampConnectControllerResumptionRuntime(
                localDevice = localDevice,
                identityEffect = services.identity,
                identityVerifier = services.identityVerifier,
                transportFactory = services.transport,
                cipherFactory = services.cipher,
            )
            var result: app.naviamp.app.NaviampConnectResumptionResult =
                app.naviamp.app.NaviampConnectResumptionResult.Failed(NaviampConnectErrorCode.TargetUnavailable)
            for (address in target.addresses) {
                val credential = services.credentials?.read(trust.peerDevice.deviceId) ?: break
                val attempt = awaitNaviampConnectSocketOperation(
                    scope = controllerScope,
                    timeoutMillis = services.pairingHandshakeTimeoutMillis,
                    close = { activeReconnectConnection?.close() },
                ) {
                    runtime.reconnect(
                        address,
                        target.advertisement,
                        trust,
                        credential,
                        onConnectionOpened = { connection -> activeReconnectConnection = connection },
                    )
                } ?: app.naviamp.app.NaviampConnectResumptionResult.Failed(
                    NaviampConnectErrorCode.TargetUnavailable,
                )
                activeReconnectConnection = null
                result = attempt
                if (attempt is app.naviamp.app.NaviampConnectResumptionResult.Connected) break
            }
            when (result) {
                is app.naviamp.app.NaviampConnectResumptionResult.Connected -> {
                    services.trust.upsert(trust.withLastKnownEndpoint(target))
                    discovery?.stop()
                    val connected = awaitNaviampConnectSocketOperation(
                        scope = controllerScope,
                        timeoutMillis = services.pairingHandshakeTimeoutMillis,
                        close = result.session::close,
                    ) {
                        startControllerSession(result.session)
                    } == true
                    if (connected) {
                        automaticReconnectRetryJob?.cancel()
                        automaticReconnectRetryJob = null
                        phase = NaviampConnectPairingUiPhase.Paired
                        status = "Reconnected to ${trust.displayName}."
                    } else {
                        result.session.close()
                        phase = NaviampConnectPairingUiPhase.Failed
                        status = "The trusted TV did not start a compatible control session."
                    }
                }
                is app.naviamp.app.NaviampConnectResumptionResult.Failed -> {
                    phase = NaviampConnectPairingUiPhase.Failed
                    status = "Could not securely reconnect to ${trust.displayName}. Naviamp will retry when the TV is available."
                }
            }
            reconnectJob = null
            publish()
            if (result is app.naviamp.app.NaviampConnectResumptionResult.Failed) {
                scheduleAutomaticTrustedReconnect()
            }
        }
    }

    private fun selectTarget(targetUi: NaviampConnectDiscoveredTargetUi) {
        suspendAutomaticReconnectForManualPairing()
        selectedTarget = discovery?.state?.value?.targets?.firstOrNull {
            it.advertisement.instanceId == targetUi.instanceId
        }?.also { target ->
            recentlyResolvedTargets[target.advertisement.identityFingerprint] = target
        } ?: return
        enteredCode = ""
        phase = NaviampConnectPairingUiPhase.AwaitingCode
        status = "Enter the six-digit code shown on ${targetUi.displayName}."
        publish()
    }

    private fun suspendAutomaticReconnectForManualPairing() {
        automaticReconnectSuppressed = true
        pendingTrustedDeviceId = null
        automaticReconnectRetryJob?.cancel()
        automaticReconnectRetryJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        activeReconnectConnection?.close()
        activeReconnectConnection = null
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
                val persistedTrust = if (services.role == NaviampCoreConnectRole.Controller) {
                    selectedTarget?.let(result.trust::withLastKnownEndpoint) ?: result.trust
                } else {
                    result.trust
                }
                services.trust.upsert(persistedTrust)
                val credentialSaved = try {
                    services.credentials?.write(
                        result.trust.peerDevice.deviceId,
                        result.resumptionCredential,
                    ) != null
                } catch (_: Exception) {
                    false
                } finally {
                    result.resumptionCredential.fill(0)
                }
                discovery?.stop()
                if (services.role == NaviampCoreConnectRole.Controller) closeTargetResources()
                val connected = when (services.role) {
                    NaviampCoreConnectRole.Target -> replaceTargetSession(result.session)
                    NaviampCoreConnectRole.Controller -> startControllerSession(result.session)
                }
                if (connected) {
                    phase = NaviampConnectPairingUiPhase.Paired
                    status = if (credentialSaved) {
                        "Connected to ${result.trust.displayName}."
                    } else {
                        "Connected to ${result.trust.displayName}, but secure reconnect could not be saved."
                    }
                    if (services.role == NaviampCoreConnectRole.Target) startPairingMode()
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
                    pendingProvisioningSession = session
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

    private suspend fun replaceTargetSession(session: NaviampConnectAuthenticatedSession): Boolean =
        targetSessionMutex.withLock {
            authenticatedSession?.let { previous ->
                runCatching {
                    targetSession?.notifySessionReplaced()
                        ?: previous.send(app.naviamp.domain.connect.NaviampConnectSessionReplaced)
                }
                closeAuthenticatedSession()
            }
            startTargetSession(session)
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
        adoptControllerSession(connectedController)
        authenticatedSessionJob = controllerScope.launch { receiveControllerSession(session, connectedController) }
        return true
    }

    internal fun adoptControllerSession(session: NaviampConnectControllerSession) {
        controllerSession = session
        publish()
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
                val envelope = session.receive()
                val message = envelope.message
                if (message is app.naviamp.domain.connect.NaviampConnectSessionReplaced) {
                    automaticReconnectSuppressed = true
                    sessionEnded("Another controller took over this TV.", reconnectAutomatically = false)
                    return
                }
                if (message is app.naviamp.domain.connect.NaviampConnectConnectionProvisioningResult) {
                    status = message.message
                    publish()
                    continue
                }
                connectedController.receive(envelope)
                publish()
            }
        }
        if (authenticatedSession === session) sessionEnded("The TV disconnected.")
    }

    private fun sessionEnded(message: String, reconnectAutomatically: Boolean = true) {
        closeAuthenticatedSession()
        phase = NaviampConnectPairingUiPhase.Inactive
        status = message
        publish()
        if (!reconnectAutomatically) return
        controllerScope.launch {
            delay(AutomaticReconnectDelayMillis)
            when (services.role) {
                NaviampCoreConnectRole.Target -> ensureTrustedTargetListener()
                NaviampCoreConnectRole.Controller -> beginAutomaticTrustedReconnect()
            }
        }
    }

    internal fun ensureTrustedTargetListener() {
        if (services.trust.load().isNotEmpty() && listener == null) startPairingMode()
    }

    private fun beginAutomaticTrustedReconnect() {
        if (automaticReconnectSuppressed || services.role != NaviampCoreConnectRole.Controller) return
        val discoveryController = discovery ?: return
        val trust = services.trust.load()
            .sortedByDescending { it.pairedAtEpochMillis }
            .firstOrNull { services.credentials?.contains(it.peerDevice.deviceId) == true }
            ?: return
        if (controllerSession != null || reconnectJob?.isActive == true) return
        pendingTrustedDeviceId = trust.trustedDeviceId
        phase = NaviampConnectPairingUiPhase.Starting
        status = "Looking for ${trust.displayName}…"
        discoveryController.start()
        publish()
        attemptPendingTrustedReconnect(discoveryController.state.value.targets)
    }

    private fun scheduleAutomaticTrustedReconnect() {
        if (automaticReconnectSuppressed || services.role != NaviampCoreConnectRole.Controller) return
        if (automaticReconnectRetryJob?.isActive == true) return
        automaticReconnectRetryJob = controllerScope.launch {
            delay(AutomaticReconnectRetryMillis)
            automaticReconnectRetryJob = null
            beginAutomaticTrustedReconnect()
        }
    }

    private fun closeAuthenticatedSession() {
        activeReconnectConnection?.close()
        activeReconnectConnection = null
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
        pendingProvisioningSession = null
        pendingProvisioningControllerName = null
    }

    private fun stopControlling() {
        val targetName = controllerSession?.state?.value?.target?.displayName ?: return
        automaticReconnectSuppressed = true
        automaticReconnectRetryJob?.cancel()
        automaticReconnectRetryJob = null
        pendingTrustedDeviceId = null
        discovery?.stop()
        closeAuthenticatedSession()
        phase = NaviampConnectPairingUiPhase.Inactive
        status = "Stopped controlling $targetName. The TV will keep playing."
        publish()
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
        val playback = localPlayback
        val live = playback?.connectLiveState()
        val identity = sourceIdentity()
        val problem = naviampCoreConnectQueueHandoffProblem(
            hasPlayback = playback != null,
            hasCurrentQueueItem = live?.queue?.current != null,
            hasSourceIdentity = identity != null,
            connected = controllerSession != null,
        )
        if (problem != null) {
            status = problem
            publish()
            return
        }
        val connected = checkNotNull(controllerSession)
        controllerScope.launch {
            status = "Sending this queue to ${connected.state.value.target?.displayName ?: "the TV"}…"
            publish()
            when (val sent = connected.send(naviampCoreConnectQueueHandoff(checkNotNull(live), checkNotNull(identity)))) {
                is app.naviamp.app.NaviampConnectCommandSendResult.Rejected -> status = sent.error.message
                is app.naviamp.app.NaviampConnectCommandSendResult.Sent -> {
                    val completed = withTimeoutOrNull(10_000L) {
                        connected.state.first { state ->
                            state.pendingRequests.none { request -> request.requestId == sent.requestId }
                        }
                    }
                    val error = completed?.lastError
                    status = when {
                        completed == null -> "The TV did not respond to the queue transfer."
                        error != null -> error.message
                        else -> "Queue sent to ${completed.target?.displayName ?: "the TV"}."
                    }
                }
            }
            publish()
        }
    }

    private fun provisionTarget() {
        val sessions = providerSessions ?: return
        val connected = controllerSession ?: return
        status = "Preparing this connection for secure TV setup…"
        publish()
        controllerScope.launch {
            val editable = runCatching { sessions.currentProvisioningConnection() }.getOrNull()
            if (editable == null) {
                status = "Connect this device to the server you want to configure on the TV."
                publish()
                return@launch
            }
            when (val exported = editable.form.toConnectProvisioningExport()) {
                is NaviampCoreConnectProvisioningExport.Unsupported -> {
                    needsProvisioningCredential = exported.credentialUnavailable
                    status = exported.message
                }
                is NaviampCoreConnectProvisioningExport.Ready -> {
                    needsProvisioningCredential = false
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
        val requestingSession = pendingProvisioningSession
        val connection = targetConnection ?: return
        val settings = targetSettings ?: return
        status = "Validating ${offer.profile.displayName.ifBlank { offer.profile.serverUrl }}…"
        publish()
        controllerScope.launch {
            val connected = connection.provisionConnect(offer.profile.toConnectionFormState())
            if (connected) {
                offer.portableSettings?.let(settings::applyConnectPortableSettings)
                pendingProvisioning = null
                pendingProvisioningSession = null
                pendingProvisioningControllerName = null
                status = "TV setup completed securely."
                publishTargetPlaybackSnapshot()
            } else {
                status = "Could not validate that connection. Check the server and credential, then retry."
            }
            runCatching {
                requestingSession?.send(
                    app.naviamp.domain.connect.NaviampConnectConnectionProvisioningResult(
                        succeeded = connected,
                        message = status.orEmpty(),
                    ),
                )
            }
            publish()
        }
    }

    private fun rejectProvisioning() {
        val requestingSession = pendingProvisioningSession
        pendingProvisioning = null
        pendingProvisioningSession = null
        pendingProvisioningControllerName = null
        status = "Connection setup request rejected."
        publish()
        controllerScope.launch {
            runCatching {
                requestingSession?.send(
                    app.naviamp.domain.connect.NaviampConnectConnectionProvisioningResult(
                        succeeded = false,
                        message = "Connection setup request rejected by the TV.",
                    ),
                )
            }
        }
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

    private fun resetPairingOfferKeepingListener() {
        pendingTargetPairing?.reject()
        pendingTargetPairing = null
        advertising?.stop()
        pairingExpiryJob?.cancel()
        pairingExpiryJob = null
        pairingHandshakeJob?.cancel()
        pairingHandshakeJob = null
        activePairingHandshake?.cancel(services.nowEpochMillis())
        activePairingHandshake = null
        acceptedPairingConnection?.close()
        acceptedPairingConnection = null
        targetPairing.stop()
    }

    private fun closeTargetResources() {
        advertising?.stop()
        pairingExpiryJob?.cancel()
        pairingExpiryJob = null
        pairingHandshakeJob?.cancel()
        pairingHandshakeJob = null
        activePairingHandshake?.cancel(services.nowEpochMillis())
        activePairingHandshake = null
        listenerJob?.cancel()
        listenerJob = null
        acceptedPairingConnection?.close()
        acceptedPairingConnection = null
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
        val effectiveStatus = discovered?.problem?.userMessage() ?: status ?: remote?.lastError?.message
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
                    connectedControllerDeviceId = targetSession
                        ?.let { authenticatedSession?.trust?.peerDevice?.deviceId },
                    connectedControllerName = targetSession
                        ?.let { authenticatedSession?.trust?.peerDevice?.displayName },
                    remoteTrackTitle = remoteCurrent?.title,
                    remoteArtistName = remoteCurrent?.artistName,
                    remotePlaying = remoteSnapshot?.playback?.state ==
                        app.naviamp.domain.connect.NaviampConnectPlaybackState.Playing,
                    remoteHasPrevious = remoteSnapshot?.queue?.currentIndex?.let { it > 0 } == true,
                    remoteHasNext = remoteSnapshot?.queue?.let { it.currentIndex in 0 until it.occurrences.lastIndex } == true,
                    remoteNowPlaying = remote?.target?.let { target ->
                        remoteSnapshot?.toRemoteNowPlayingUiOrNull(target.displayName)
                    },
                    canHandoffLocalQueue = remote?.capabilities?.contains(
                        NaviampConnectCapability.QueueHandoff,
                    ) == true && naviampCoreConnectQueueHandoffProblem(
                        hasPlayback = localPlayback != null,
                        hasCurrentQueueItem = localPlayback?.connectLiveState()?.queue?.current != null,
                        hasSourceIdentity = sourceIdentity() != null,
                        connected = controllerSession != null,
                    ) == null,
                    canReceiveRemoteQueue = remote?.capabilities?.contains(
                        NaviampConnectCapability.QueueHandoff,
                    ) == true && remoteSnapshot?.queue?.currentIndex?.let { it >= 0 } == true &&
                        sourceIdentity()?.let { local ->
                            remoteSnapshot.sourceIdentity?.let(local::isCompatibleWith)
                        } == true,
                    canProvisionTarget = remote?.capabilities?.contains(
                        NaviampConnectCapability.ConnectionProvisioning,
                    ) == true && providerSessions?.currentSourceId() != null,
                    needsProvisioningCredential = needsProvisioningCredential,
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
                    trustedDevices = trusts.map { trust ->
                        trust.toUi(
                            reconnectAvailable = services.role == NaviampCoreConnectRole.Controller &&
                                services.credentials?.contains(trust.peerDevice.deviceId) == true,
                        )
                    },
                ),
            )
        }
    }
}

internal fun selectNaviampConnectReconnectTarget(
    identityFingerprint: String,
    discoveredTargets: List<NaviampConnectDiscoveredTarget>,
    recentlyResolvedTarget: NaviampConnectDiscoveredTarget?,
): NaviampConnectDiscoveredTarget? =
    discoveredTargets.firstOrNull {
        it.advertisement.identityFingerprint == identityFingerprint
    } ?: recentlyResolvedTarget?.takeIf {
        it.advertisement.identityFingerprint == identityFingerprint
    }

private fun NaviampConnectTrustRecord.withLastKnownEndpoint(
    target: NaviampConnectDiscoveredTarget,
): NaviampConnectTrustRecord = copy(
    lastKnownEndpoint = NaviampConnectTrustedEndpoint(
        addresses = target.addresses,
        advertisement = target.advertisement,
    ),
)

internal fun naviampCoreConnectQueueHandoffProblem(
    hasPlayback: Boolean,
    hasCurrentQueueItem: Boolean,
    hasSourceIdentity: Boolean,
    connected: Boolean,
): String? = when {
    !hasPlayback -> "Queue handoff is not available on this device."
    !hasCurrentQueueItem -> "Start something on this device before sending its queue."
    !hasSourceIdentity -> "Connect this device to the same music source before sending its queue."
    !connected -> "Connect to a TV before sending this queue."
    else -> null
}

internal suspend fun <T> awaitNaviampConnectSocketOperation(
    scope: CoroutineScope,
    timeoutMillis: Long,
    close: () -> Unit,
    operation: suspend () -> T,
): T? {
    require(timeoutMillis > 0) { "The socket operation timeout must be positive." }
    val operationJob = scope.async { operation() }
    return try {
        withTimeoutOrNull(timeoutMillis) { operationJob.await() }
    } finally {
        if (!operationJob.isCompleted) close()
        operationJob.cancel()
    }
}

private fun NaviampConnectTargetPairingController.displayCode(): String? =
    when (val current = state) {
        is app.naviamp.domain.connect.NaviampConnectTargetPairingState.Advertising -> current.displayCode
        is app.naviamp.domain.connect.NaviampConnectTargetPairingState.AwaitingApproval -> current.advertising.displayCode
        else -> null
    }

private fun NaviampConnectTrustRecord.toUi(reconnectAvailable: Boolean) = NaviampConnectTrustedDeviceUi(
    deviceId = trustedDeviceId,
    displayName = displayName,
    detail = "Paired Naviamp ${peerDevice.role.name.lowercase()}",
    reconnectAvailable = reconnectAvailable,
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
private const val AutomaticReconnectDelayMillis = 750L
private const val AutomaticReconnectRetryMillis = 2_000L
