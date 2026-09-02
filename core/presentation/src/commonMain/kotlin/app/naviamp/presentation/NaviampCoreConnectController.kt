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
import app.naviamp.app.NaviampConnectPlaybackDestination
import app.naviamp.app.NaviampConnectPlaybackDestinationController
import app.naviamp.app.NaviampConnectRemoteOutputStatus
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
import app.naviamp.domain.connect.visibleDisplayName
import app.naviamp.domain.connect.NaviampConnectSourceIdentity
import app.naviamp.domain.connect.NaviampConnectWelcome
import app.naviamp.domain.connect.NaviampConnectNext
import app.naviamp.domain.connect.NaviampConnectPrevious
import app.naviamp.domain.connect.NaviampConnectTogglePlayPause
import app.naviamp.ui.NaviampConnectDiscoveredTargetUi
import app.naviamp.ui.NaviampConnectPairingUiPhase
import app.naviamp.ui.NaviampConnectPlaybackDestinationUiStatus
import app.naviamp.ui.NaviampConnectSettingsActions
import app.naviamp.ui.NaviampConnectSettingsUi
import app.naviamp.ui.NaviampConnectTrustedDeviceUi
import app.naviamp.ui.NaviampConnectUiRole
import app.naviamp.ui.NowPlayingPlaybackAction
import app.naviamp.ui.disambiguateNaviampConnectDeviceNames
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

/** Shared product policy: standard clients are bidirectional; Television remains target-only. */
val NaviampCoreBidirectionalConnectCapabilities: Set<NaviampConnectDeviceCapability> = setOf(
    NaviampConnectDeviceCapability.ControlPlayback,
    NaviampConnectDeviceCapability.PlaybackTarget,
)

val NaviampCorePlaybackTargetConnectCapabilities: Set<NaviampConnectDeviceCapability> =
    setOf(NaviampConnectDeviceCapability.PlaybackTarget)

/** Native effects and host facts required by the shared Connect product controller. */
data class NaviampCoreConnectServices(
    val deviceCapabilities: Set<NaviampConnectDeviceCapability>,
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
        require(deviceCapabilities.isNotEmpty()) { "Connect services require at least one device capability." }
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
    private val canControl = NaviampConnectDeviceCapability.ControlPlayback in services.deviceCapabilities
    private val canPlayRemotely = NaviampConnectDeviceCapability.PlaybackTarget in services.deviceCapabilities
    private val localControllerDevice: NaviampConnectDevice
        get() = localDevice(NaviampConnectDeviceRole.Controller)
    private val localTargetDevice: NaviampConnectDevice
        get() = localDevice(NaviampConnectDeviceRole.Target)
    private val discovery = services.discovery?.let {
        NaviampConnectDiscoveryController(it, nowEpochMillis = services.nowEpochMillis)
    }
    private val advertising = services.advertising?.let {
        NaviampConnectAdvertisingController(it, services.nowEpochMillis)
    }
    private val targetPairing = NaviampConnectTargetPairingController()
    private val playbackDestination = NaviampConnectPlaybackDestinationController()
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

    private fun localDevice(role: NaviampConnectDeviceRole): NaviampConnectDevice = NaviampConnectDevice(
        deviceId = localIdentity.deviceId,
        displayName = services.trust.selfName() ?: services.displayName,
        role = role,
        deviceCapabilities = services.deviceCapabilities,
    )

    val actions = NaviampConnectSettingsActions(
        onStartPairingMode = ::startPairingMode,
        onStopPairingMode = ::stopPairingMode,
        onRefreshTargets = ::refreshTargets,
        onTargetSelected = ::selectTarget,
        onTrustedDeviceSelected = ::reconnectTrustedDevice,
        onPlaybackDeviceSelected = ::selectPlaybackDevice,
        onLocalDeviceNameChanged = ::changeLocalDeviceName,
        onTrustedDeviceAliasChanged = ::changeTrustedDeviceAlias,
        onForgetTrustedDevice = ::forgetTrustedDevice,
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
            onStopControlling = ::stopControlling,
        ),
    )

    private fun selectPlaybackDevice(trustedDeviceId: String?) {
        if (trustedDeviceId == null) {
            if (controllerSession != null) stopControlling() else {
                automaticReconnectSuppressed = true
                pendingTrustedDeviceId = null
                playbackDestination.selectLocal()
                status = "Playback will stay on ${services.trust.selfName() ?: services.displayName}."
                publish()
            }
            return
        }
        services.trust.load().firstOrNull { it.trustedDeviceId == trustedDeviceId }
            ?.toUi(reconnectAvailable = true)
            ?.let(::reconnectTrustedDevice)
    }

    private fun changeLocalDeviceName(name: String) {
        runCatching { services.trust.setSelfName(name) }
            .onSuccess {
                status = "This device is now named ${it ?: services.displayName}."
                val restartAdvertising = phase == NaviampConnectPairingUiPhase.Advertising
                if (restartAdvertising) {
                    stopPairingMode()
                    startPairingMode()
                } else {
                    publish()
                }
            }
            .onFailure {
                status = it.message
                publish()
            }
    }

    private fun changeTrustedDeviceAlias(trustedDeviceId: String, alias: String) {
        runCatching { services.trust.setAlias(trustedDeviceId, alias) }
            .onSuccess { publish() }
            .onFailure {
                status = it.message
                publish()
            }
    }

    private fun forgetTrustedDevice(trustedDeviceId: String) {
        val trust = services.trust.load().firstOrNull { it.trustedDeviceId == trustedDeviceId } ?: return
        if (playbackDestination.selectedTrustedDeviceId() == trustedDeviceId) {
            if (controllerSession != null) stopControlling() else playbackDestination.selectLocal()
        }
        services.credentials?.remove(trust.peerDevice.deviceId)
        services.trust.remove(trustedDeviceId)
        status = "Forgot ${trust.visibleDisplayName()}."
        publish()
    }

    init {
        if (canControl) {
            services.trust.load()
                .firstOrNull { trust ->
                    trust.peerDevice.canActAs(NaviampConnectDeviceRole.Target) &&
                        services.credentials?.contains(trust.peerDevice.deviceId) == true
                }
                ?.let(playbackDestination::select)
        }
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
        if (canPlayRemotely) ensureTrustedTargetListener()
        if (canControl) beginAutomaticTrustedReconnect()
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
        if (targetSession == null) {
            publish()
            return
        }
        targetSnapshotPublishJob?.cancel()
        targetSnapshotPublishJob = controllerScope.launch {
            delay(TargetSnapshotDebounceMillis)
            publishTargetPlaybackSnapshot()
        }
    }

    /** Redirects shared catalog playback intents to the connected TV while browsing locally. */
    internal fun routeProductCommand(command: NaviampCoreCommand): Boolean {
        if (!canControl || controllerSession == null || !playbackDestination.isConnectedRemote()) return false
        val remoteAuthorityActive = playbackDestination.hasRemotePlaybackAuthority()
        val playbackRequest = (command as? NaviampCoreCommand.NowPlaying.Playback)?.request
        if (playbackRequest != null) {
            when (
                naviampCoreConnectPlaybackRoute(
                    remoteAuthorityActive = remoteAuthorityActive,
                    hasLocalCurrent = localPlayback?.connectLiveState()?.queue?.current != null,
                    action = playbackRequest.action,
                )
            ) {
                NaviampCoreConnectPlaybackRoute.Local -> return false
                NaviampCoreConnectPlaybackRoute.InitialHandoff -> {
                    handoffLocalQueue(playing = true)
                    return true
                }
                NaviampCoreConnectPlaybackRoute.Remote -> Unit
            }
            val remoteSnapshot = controllerSession?.state?.value?.snapshot
            remoteSnapshot?.let(playbackRequest::toNaviampConnectPlaybackCommand)?.let(::sendRemoteCommand)
            return true
        }
        val queued = command.connectQueueSelectionOrNull()
        if (queued != null) {
            if (!remoteAuthorityActive) return false
            val identity = sourceIdentity()
            if (identity == null) {
                status = "Connect this device to the same music source before editing the TV queue."
                publish()
                return true
            }
            sendRemoteCommand(queued.toCommand(identity))
            return true
        }
        val selection = command.connectMediaSelectionOrNull() ?: return false
        val identity = sourceIdentity()
        if (identity == null) {
            status = "Connect this device to the same music source before playing on the TV."
            publish()
            return true
        }
        sendRemoteCommand(selection.toCommand(identity), activatesPlaybackAuthority = true)
        return true
    }

    private fun startPairingMode() {
        if (!canPlayRemotely || advertising == null) return
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
                displayName = localTargetDevice.displayName,
                protocolRange = NaviampConnectProtocolRange(),
                deviceCapabilities = localTargetDevice.deviceCapabilities,
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
                    localTargetDevice,
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
            localDevice = localTargetDevice,
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
                services.trust.upsert(result.trust)
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
                localRole = NaviampConnectDeviceRole.Target,
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
        if (!canControl || discovery == null) return
        suspendAutomaticReconnectForManualPairing()
        playbackDestination.reconnecting()
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
        if (!canControl) return
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
        playbackDestination.select(trust)
        playbackDestination.connecting(trust.trustedDeviceId)
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
        playbackDestination.connecting(trust.trustedDeviceId)
        status = "Securely reconnecting to ${trust.displayName}…"
        publish()
        reconnectJob = controllerScope.launch {
            val runtime = app.naviamp.app.NaviampConnectControllerResumptionRuntime(
                localDevice = localControllerDevice,
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
                    services.trust.upsert(result.trust.withLastKnownEndpoint(target))
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
                        playbackDestination.incompatible()
                        phase = NaviampConnectPairingUiPhase.Failed
                        status = "The trusted TV did not start a compatible control session."
                    }
                }
                is app.naviamp.app.NaviampConnectResumptionResult.Failed -> {
                    playbackDestination.unavailable()
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
                localControllerDevice,
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
            finishPairing(result, localRole = NaviampConnectDeviceRole.Controller)
        }
    }

    private suspend fun finishPairing(
        result: NaviampConnectPairingRuntimeResult,
        localRole: NaviampConnectDeviceRole,
    ) {
        when (result) {
            is NaviampConnectPairingRuntimeResult.Paired -> {
                val persistedTrust = if (localRole == NaviampConnectDeviceRole.Controller) {
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
                val connected = when (localRole) {
                    NaviampConnectDeviceRole.Target -> replaceTargetSession(result.session)
                    NaviampConnectDeviceRole.Controller -> {
                        playbackDestination.select(persistedTrust)
                        playbackDestination.connecting(persistedTrust.trustedDeviceId)
                        discovery?.stop()
                        closeTargetResources()
                        startControllerSession(result.session)
                    }
                }
                if (connected) {
                    phase = NaviampConnectPairingUiPhase.Paired
                    status = if (credentialSaved) {
                        "Connected to ${result.trust.displayName}."
                    } else {
                        "Connected to ${result.trust.displayName}, but secure reconnect could not be saved."
                    }
                    if (localRole == NaviampConnectDeviceRole.Target) startPairingMode()
                } else {
                    result.session.close()
                    if (localRole == NaviampConnectDeviceRole.Controller) playbackDestination.incompatible()
                    phase = NaviampConnectPairingUiPhase.Failed
                    status = "The paired device did not start a compatible control session."
                }
            }
            is NaviampConnectPairingRuntimeResult.Failed -> {
                if (localRole == NaviampConnectDeviceRole.Target) closeTargetResources()
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
            target = localTargetDevice,
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
                target = localTargetDevice,
                capabilities = supportedTargetCapabilities,
                snapshot = snapshot,
            ),
        )
        val connectedTarget = NaviampConnectTargetSession(
            sessionId = session.sessionId,
            protocolVersion = session.protocolVersion,
            initialSnapshot = snapshot,
            transport = NaviampConnectSessionTransport(session::sendOrderedEnvelope),
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
            playbackDestination.incompatible()
            return false
        }
        val connectedController = NaviampConnectControllerSession(
            transport = NaviampConnectSessionTransport(session::sendOrderedEnvelope),
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
        session.state.value.target?.let { target ->
            if (playbackDestination.selectedTrustedDeviceId() == null) {
                services.trust.load()
                    .firstOrNull { it.peerDevice.deviceId == target.deviceId }
                    ?.let(playbackDestination::select)
            }
            playbackDestination.connected(target)
        }
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
        if (reconnectAutomatically) playbackDestination.reconnecting() else playbackDestination.selectLocal()
        phase = NaviampConnectPairingUiPhase.Inactive
        status = message
        publish()
        if (!reconnectAutomatically) return
        controllerScope.launch {
            delay(AutomaticReconnectDelayMillis)
            if (canPlayRemotely) ensureTrustedTargetListener()
            if (canControl) beginAutomaticTrustedReconnect()
        }
    }

    internal fun ensureTrustedTargetListener() {
        if (!canPlayRemotely) return
        if (services.trust.load().isNotEmpty() && listener == null) startPairingMode()
    }

    private fun beginAutomaticTrustedReconnect() {
        if (automaticReconnectSuppressed || !canControl) return
        val discoveryController = discovery ?: return
        val trustedDeviceId = playbackDestination.selectedTrustedDeviceId() ?: return
        val trust = services.trust.load().firstOrNull {
            it.trustedDeviceId == trustedDeviceId &&
                it.peerDevice.canActAs(NaviampConnectDeviceRole.Target) &&
                services.credentials?.contains(it.peerDevice.deviceId) == true
        }
            ?: return
        if (controllerSession != null || reconnectJob?.isActive == true) return
        pendingTrustedDeviceId = trust.trustedDeviceId
        playbackDestination.connecting(trust.trustedDeviceId)
        phase = NaviampConnectPairingUiPhase.Starting
        status = "Looking for ${trust.displayName}…"
        discoveryController.start()
        publish()
        attemptPendingTrustedReconnect(discoveryController.state.value.targets)
    }

    private fun scheduleAutomaticTrustedReconnect() {
        if (automaticReconnectSuppressed || !canControl) return
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
        playbackDestination.selectLocal()
        phase = NaviampConnectPairingUiPhase.Inactive
        status = "Stopped controlling $targetName. The TV will keep playing."
        publish()
    }

    private fun sendRemoteCommand(
        command: app.naviamp.domain.connect.NaviampConnectCommand,
        activatesPlaybackAuthority: Boolean = false,
    ) {
        val connected = controllerSession ?: return
        val trustedDeviceId = playbackDestination.selectedTrustedDeviceId()
        controllerScope.launch {
            when (val result = connected.send(command)) {
                is app.naviamp.app.NaviampConnectCommandSendResult.Sent -> {
                    if (activatesPlaybackAuthority) {
                        val completed = awaitRemoteCommand(connected, result.requestId)
                        if (completed) {
                            trustedDeviceId?.let(playbackDestination::activatePlaybackAuthority)
                            status = null
                        } else {
                            status = connected.state.value.lastError?.message
                                ?: "The playback device did not start the selection."
                        }
                    } else {
                        status = null
                    }
                }
                is app.naviamp.app.NaviampConnectCommandSendResult.Rejected -> status = result.error.message
            }
            publish()
        }
    }

    private fun handoffLocalQueue(playing: Boolean? = null) {
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
        val trustedDeviceId = playbackDestination.selectedTrustedDeviceId()
        controllerScope.launch {
            status = "Sending this queue to ${connected.state.value.target?.displayName ?: "the TV"}…"
            publish()
            val handoff = if (playing == null) {
                naviampCoreConnectQueueHandoff(checkNotNull(live), checkNotNull(identity))
            } else {
                naviampCoreConnectQueueHandoff(checkNotNull(live), checkNotNull(identity), playing)
            }
            when (val sent = connected.send(handoff)) {
                is app.naviamp.app.NaviampConnectCommandSendResult.Rejected -> status = sent.error.message
                is app.naviamp.app.NaviampConnectCommandSendResult.Sent -> {
                    val succeeded = awaitRemoteCommand(connected, sent.requestId)
                    val error = connected.state.value.lastError
                    status = when {
                        !succeeded && error != null -> error.message
                        !succeeded -> "The playback device did not respond to the queue transfer."
                        else -> {
                            trustedDeviceId?.let(playbackDestination::activatePlaybackAuthority)
                            "Queue sent to ${connected.state.value.target?.displayName ?: "the playback device"}."
                        }
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
        return awaitRemoteCommand(connected, sent.requestId)
    }

    private suspend fun awaitRemoteCommand(
        connected: NaviampConnectControllerSession,
        requestId: String,
    ): Boolean {
        val completed = withTimeoutOrNull(10_000L) {
            connected.state.first { state ->
                state.pendingRequests.none { request -> request.requestId == requestId }
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
        val trustDisplayNames = disambiguateNaviampConnectDeviceNames(trusts.map { it.visibleDisplayName() })
        val destination = playbackDestination.state.value
        val remoteDestination = destination as? NaviampConnectPlaybackDestination.Remote
        stateStore.updateShell { shell ->
            shell.copy(
                connect = NaviampConnectSettingsUi(
                    available = true,
                    role = when {
                        canControl && canPlayRemotely -> NaviampConnectUiRole.ControllerAndTarget
                        canPlayRemotely -> NaviampConnectUiRole.Target
                        else -> NaviampConnectUiRole.Controller
                    },
                    pairingPhase = phase,
                    pairingCode = targetPairing.displayCode(),
                    enteredPairingCode = enteredCode,
                    pendingControllerName = pendingTargetPairing?.controller?.displayName,
                    selectedTargetId = selectedTarget?.advertisement?.instanceId,
                    status = effectiveStatus,
                    selectedPlaybackDeviceId = remoteDestination?.device?.trustedDeviceId,
                    selectedPlaybackDeviceName = remoteDestination?.let { selected ->
                        trusts.firstOrNull { it.trustedDeviceId == selected.device.trustedDeviceId }
                            ?.visibleDisplayName()
                            ?: selected.device.displayName
                    },
                    localDeviceName = services.trust.selfName() ?: services.displayName,
                    playbackDestinationStatus = destination.toUiStatus(),
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
                    remoteNowPlaying = if (playbackDestination.hasRemotePlaybackAuthority()) {
                        remote?.target?.let { target ->
                            remoteSnapshot?.toRemoteNowPlayingUiOrNull(target.displayName)
                        }
                    } else {
                        null
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
                    trustedDevices = trusts.mapIndexed { index, trust ->
                        trust.toUi(
                            displayName = trustDisplayNames[index],
                            reconnectAvailable = canControl &&
                                trust.peerDevice.canActAs(NaviampConnectDeviceRole.Target) &&
                                services.credentials?.contains(trust.peerDevice.deviceId) == true,
                        )
                    },
                ),
            )
        }
    }
}

internal enum class NaviampCoreConnectPlaybackRoute {
    Local,
    InitialHandoff,
    Remote,
}

internal fun naviampCoreConnectPlaybackRoute(
    remoteAuthorityActive: Boolean,
    hasLocalCurrent: Boolean,
    action: NowPlayingPlaybackAction,
): NaviampCoreConnectPlaybackRoute = when {
    remoteAuthorityActive -> NaviampCoreConnectPlaybackRoute.Remote
    hasLocalCurrent && (
        action == NowPlayingPlaybackAction.PlayCurrent || action == NowPlayingPlaybackAction.Resume
    ) -> NaviampCoreConnectPlaybackRoute.InitialHandoff
    else -> NaviampCoreConnectPlaybackRoute.Local
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

private fun NaviampConnectPlaybackDestination.toUiStatus(): NaviampConnectPlaybackDestinationUiStatus =
    when (this) {
        NaviampConnectPlaybackDestination.Local -> NaviampConnectPlaybackDestinationUiStatus.Local
        is NaviampConnectPlaybackDestination.Remote -> when (status) {
            NaviampConnectRemoteOutputStatus.Armed -> NaviampConnectPlaybackDestinationUiStatus.Armed
            NaviampConnectRemoteOutputStatus.Connecting -> NaviampConnectPlaybackDestinationUiStatus.Connecting
            NaviampConnectRemoteOutputStatus.Connected -> NaviampConnectPlaybackDestinationUiStatus.Connected
            NaviampConnectRemoteOutputStatus.Reconnecting -> NaviampConnectPlaybackDestinationUiStatus.Reconnecting
            NaviampConnectRemoteOutputStatus.Unavailable -> NaviampConnectPlaybackDestinationUiStatus.Unavailable
            NaviampConnectRemoteOutputStatus.Incompatible -> NaviampConnectPlaybackDestinationUiStatus.Incompatible
        }
    }

private fun NaviampConnectTrustRecord.toUi(
    reconnectAvailable: Boolean,
    displayName: String = visibleDisplayName(),
) = NaviampConnectTrustedDeviceUi(
    deviceId = trustedDeviceId,
    displayName = displayName,
    localAlias = localAlias,
    detail = "Paired Naviamp ${peerDevice.role.name.lowercase()}",
    reconnectAvailable = reconnectAvailable,
    playbackTarget = peerDevice.canActAs(NaviampConnectDeviceRole.Target),
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
