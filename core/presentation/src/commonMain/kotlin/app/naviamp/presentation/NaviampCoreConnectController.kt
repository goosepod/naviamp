package app.naviamp.presentation
import app.naviamp.ui.NaviampConnectStatusMessage
import app.naviamp.ui.NaviampConnectStatusText

import app.naviamp.app.NaviampConnectAdvertisingController
import app.naviamp.app.NaviampConnectAdvertisingEffect
import app.naviamp.app.NaviampConnectAdvertisingStatus
import app.naviamp.app.NaviampConnectAuthenticatedCipherFactory
import app.naviamp.app.NaviampConnectAuthenticatedSession
import app.naviamp.app.NaviampConnectControllerSession
import app.naviamp.app.NaviampConnectDeviceIdentity
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
import app.naviamp.app.NaviampPlaybackOutputSelectionController
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
import app.naviamp.app.NaviampLivePlaybackState
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
import app.naviamp.domain.connect.NaviampConnectManualEndpoint
import app.naviamp.domain.connect.parseNaviampConnectManualEndpoint
import app.naviamp.domain.connect.NaviampConnectTargetPairingController
import app.naviamp.domain.connect.NaviampConnectTrustRecord
import app.naviamp.domain.connect.NaviampConnectTrustedEndpoint
import app.naviamp.domain.connect.visibleDisplayName
import app.naviamp.domain.connect.NaviampConnectSourceIdentity
import app.naviamp.domain.connect.NaviampConnectTargetSnapshot
import app.naviamp.domain.connect.NaviampConnectWelcome
import app.naviamp.domain.connect.NaviampConnectNext
import app.naviamp.domain.connect.NaviampConnectPrevious
import app.naviamp.domain.connect.NaviampConnectTogglePlayPause
import app.naviamp.ui.NaviampConnectDiscoveredTargetUi
import app.naviamp.ui.NaviampConnectPairingUiPhase
import app.naviamp.ui.NaviampConnectPlaybackDestinationUiStatus
import app.naviamp.ui.NaviampConnectSettingsActions
import app.naviamp.ui.NaviampConnectSettingsUi
import app.naviamp.ui.NaviampConnectSourceMismatchUi
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
import kotlinx.coroutines.withContext
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

private val NaviampCoreConnectMonotonicOrigin = kotlin.time.TimeSource.Monotonic.markNow()
private val NaviampCoreConnectMonotonicClock: () -> Long =
    { NaviampCoreConnectMonotonicOrigin.elapsedNow().inWholeMilliseconds }

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
    /** CSPRNG-backed, collision-resistant value used for pairing IDs, challenges, and target nonces. */
    val newOpaqueId: () -> String,
    val newPairingCode: () -> String,
    val nowEpochMillis: () -> Long,
    val pairingLifetimeMillis: Long = 5 * 60_000L,
    val pairingHelloTimeoutMillis: Long = 10_000L,
    val pairingHandshakeTimeoutMillis: Long = 30_000L,
    val pairingListenPort: Int = 0,
    val targetCapabilities: Set<NaviampConnectCapability> = emptySet(),
    val permissionSettings: app.naviamp.app.NaviampConnectPermissionSettingsEffect? = null,
    val networkDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default,
    val trace: (NaviampCoreConnectTraceEvent) -> Unit = { println(it.logLine()) },
    /** Monotonic milliseconds for playback timelines; wall-clock changes must not look like seeks. */
    val monotonicMillis: () -> Long = NaviampCoreConnectMonotonicClock,
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
    private val localConnection: NaviampCoreConnectionController? = null,
    private val targetSettings: NaviampCoreSettingsController? = null,
    private val revealTargetNowPlaying: () -> Unit = {},
    private val playbackOutputs: NaviampPlaybackOutputSelectionController = NaviampPlaybackOutputSelectionController(),
    private val trackSelectionContext: (NaviampCoreCommand) -> app.naviamp.domain.connect.NaviampConnectPlaybackContext? =
        { null },
) {
    private val controllerScope = CoroutineScope(
        scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]),
    )
    /**
     * The device identity lives behind the OS secure store, which can block on a keyring unlock
     * prompt or fail outright. It is loaded off the caller's thread so constructing Core never waits
     * on it; identity-dependent work waits for it, and a failure is a retryable recovery problem.
     */
    private sealed interface IdentityState {
        data object Loading : IdentityState
        data class Ready(val identity: NaviampConnectDeviceIdentity) : IdentityState
        data object Unavailable : IdentityState
    }
    private val identityState = kotlinx.coroutines.flow.MutableStateFlow<IdentityState>(IdentityState.Loading)
    private val localIdentity: NaviampConnectDeviceIdentity
        get() = (identityState.value as? IdentityState.Ready)?.identity
            ?: error("The Naviamp Connect device identity is not available.")
    private val canControl = NaviampConnectDeviceCapability.ControlPlayback in services.deviceCapabilities
    private val canPlayRemotely = NaviampConnectDeviceCapability.PlaybackTarget in services.deviceCapabilities
    private val localControllerDevice: NaviampConnectDevice
        get() = localDevice(NaviampConnectDeviceRole.Controller)
    private val localTargetDevice: NaviampConnectDevice
        get() = localDevice(NaviampConnectDeviceRole.Target)
    private val networkEffects = app.naviamp.app.NaviampConnectNetworkEffects(
        controllerScope, services.discovery, services.advertising, services.networkDispatcher,
    )
    private val discovery = networkEffects.discovery?.let {
        NaviampConnectDiscoveryController(it, nowEpochMillis = services.nowEpochMillis)
    }
    private val advertising = networkEffects.advertising?.let {
        NaviampConnectAdvertisingController(it, services.nowEpochMillis)
    }
    private val targetPairing = NaviampConnectTargetPairingController()
    private val playbackDestination = NaviampConnectPlaybackDestinationController(playbackOutputs)
    private var listener: NaviampConnectTransportListener? = null
    private var listenerJob: Job? = null
    /** Registered endpoint for trusted resume; it exists without any code being shown. */
    private var presenceAdvertisement: NaviampConnectAdvertisement? = null
    /** True while the user keeps the code panel open; only then does a code exist. */
    private var codeVisible = false
    /** Failed code attempts while the panel stays open; each failure rotates the code. */
    private var codeFailures = 0
    private var codeRetryJob: Job? = null
    private var connectScreenVisible = false
    private var codeTargetWaitJob: Job? = null
    /** An outgoing code handshake is running; a background trusted reconnect does not count. */
    private var codePairingInFlight = false
    private var acceptedPairingConnection: NaviampConnectTransportConnection? = null
    private var pairingExpiryJob: Job? = null
    private var activePairingHandshake: NaviampConnectPendingTargetPairing? = null
    private var pairingHandshakeJob: Job? = null
    private var authenticatedSession: NaviampConnectAuthenticatedSession? = null
    private var authenticatedSessionJob: Job? = null
    private var heartbeatJob: Job? = null
    private var advertisingFailure: NaviampConnectAdvertisingStatus.Failed? = null
    private var settingsOpenFailed = false
    private var notice: app.naviamp.ui.NaviampConnectStatusNotice? = null
    private var targetSession: NaviampConnectTargetSession? = null
    private var targetSnapshotFactory: NaviampCoreConnectTargetSnapshotFactory? = null
    private var targetSnapshotPublishJob: Job? = null
    private var lastObservedTargetPlayback: NaviampLivePlaybackState? = null
    private var targetTimeline: NaviampConnectTargetTimeline? = null
    private var targetTimelineRevision = -1L
    private var controllerSession: NaviampConnectControllerSession? = null
    private var retryableRemoteCommands = emptyList<app.naviamp.domain.connect.NaviampConnectCommand>()
    private val targetSessionMutex = Mutex()
    private var pendingTargetPairing: NaviampConnectPendingTargetPairing? = null
    private var selectedTarget: NaviampConnectDiscoveredTarget? = null
    private var selectedManualEndpoint: NaviampConnectManualEndpoint? = null
    private var activeManualPairingConnection: NaviampConnectTransportConnection? = null
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
    private var statusMessage: NaviampConnectStatusMessage? = null
    // Retain diagnostic text for callers; UI uses the typed resource message when available.
    private var status: String? = null
        set(value) {
            field = value
            statusMessage = null
        }
    private var pendingProvisioning: NaviampConnectOfferConnectionProvisioning? = null
    private var pendingProvisioningSession: NaviampConnectAuthenticatedSession? = null
    private var needsProvisioningCredential = false
    private var provisioningCredentialSourceId: String? = null
    private var controllerInitialSetupAllowed = false
    private var postConnectOpen = false
    private var postConnectSignIn = false
    private var postConnectDeviceHasAccounts = false
    private var postConnectSignInFailed = false
    private var controllerTargetHasSavedAccounts = false
    /** Set while sheet-started sign-in runs: true = then play on the device, false = then stop. */
    private var postConnectPendingPlay: Boolean? = null
    private var controllerCompanionProvisioningSupported = false
    private var controllerSetupNeedsNewCode = false
    private var controllerProvisioningBusy = false
    private var controllerProvisioningJob: Job? = null
    private var controllerProvisioningAttemptId: String? = null
    private var targetProvisioningJob: Job? = null
    private var provisioningResult: Pair<String, kotlinx.coroutines.CompletableDeferred<app.naviamp.domain.connect.NaviampConnectConnectionProvisioningResult>>? = null
    private var sourceMismatchRecoveryVisible = false
    private val supportedTargetCapabilities: Set<NaviampConnectCapability> =
        NaviampCoreSupportedConnectTargetCapabilities + if (
            targetConnection != null && targetSettings != null
        ) {
            setOf(NaviampConnectCapability.ConnectionProvisioning)
        } else {
            emptySet()
        }

    private fun targetIsEmpty(): Boolean = providerSessions != null &&
        providerSessions.currentSourceId() == null &&
        stateStore.state.value.shell.connectionSettings.connection.savedConnections.isEmpty() &&
        !stateStore.state.value.shell.connectionSettings.connection.isConnecting

    private fun trace(kind: NaviampCoreConnectTraceKind, setupId: String? = null) {
        val event = NaviampCoreConnectTraceEvent(services.nowEpochMillis(), kind,
            authenticatedSession?.sessionId, setupId)
        runCatching { services.trace(event) }
    }

    private fun localDevice(role: NaviampConnectDeviceRole): NaviampConnectDevice = NaviampConnectDevice(
        deviceId = localIdentity.deviceId,
        displayName = services.trust.selfName() ?: services.displayName,
        role = role,
        deviceCapabilities = services.deviceCapabilities,
    )

    val actions = NaviampConnectSettingsActions(
        onStartPairingMode = ::startPairingMode,
        onRetryConnection = ::retryConnection,
        onOpenPermissionSettings = ::openPermissionSettings,
        onStopPairingMode = ::stopPairingMode,
        onConnectScreenVisibilityChanged = ::changeConnectScreenVisibility,
        onPostConnectSignInChanged = { selected -> postConnectSignIn = selected && postConnectCanSignIn(); publish() },
        onPostConnectPlayOnDevice = { finishPostConnect(playOnDevice = true) },
        onPostConnectDone = { finishPostConnect(playOnDevice = false) },
        onPostConnectDismiss = ::dismissPostConnect,
        onTargetSelected = ::selectTarget,
        onManualEndpointSelected = ::selectManualEndpoint,
        onManualTrustedEndpointSelected = ::reconnectTrustedDeviceAtAddress,
        onTrustedDeviceSelected = ::reconnectTrustedDevice,
        onPlaybackDeviceSelected = ::selectPlaybackDevice,
        onLocalDeviceNameChanged = ::changeLocalDeviceName,
        onTrustedDeviceAliasChanged = ::changeTrustedDeviceAlias,
        onForgetTrustedDevice = ::forgetTrustedDevice,
        onPairingCodeChanged = ::changePairingCode,
        onSubmitPairingCode = ::submitPairingCode,
        onRemotePrevious = { sendRemoteCommand(NaviampConnectPrevious) },
        onRemotePlayPause = { sendRemoteCommand(NaviampConnectTogglePlayPause) },
        onRemoteNext = { sendRemoteCommand(NaviampConnectNext) },
        onStopControlling = ::stopControlling,
        onRemoteHandoffQueue = ::handoffLocalQueue,
        onReceiveRemoteQueue = ::receiveRemoteQueue,
        onProvisionTarget = { provisionTarget() },
        onSubmitProvisioningCredential = { password ->
            if (needsProvisioningCredential && password.isNotBlank()) provisionTarget(password)
        },
        onCancelProvisioningCredential = {
            controllerProvisioningJob?.cancel()
            controllerProvisioningAttemptId = null
            controllerProvisioningBusy = false
            needsProvisioningCredential = false
            provisioningCredentialSourceId = null
            publish()
        },
        onDismissSourceMismatchRecovery = ::dismissSourceMismatchRecovery,
        remoteNowPlayingActions = createNaviampCoreConnectRemoteNowPlayingActions(
            snapshot = { controllerSession?.state?.value?.snapshot },
            send = ::sendRemoteCommand,
            onStopControlling = ::stopControlling,
        ),
    )

    private fun selectPlaybackDevice(trustedDeviceId: String?) {
        notice = null
        retryableRemoteCommands = emptyList()
        if (trustedDeviceId == null) {
            if (controllerSession != null) stopControlling() else {
                automaticReconnectSuppressed = true
                pendingTrustedDeviceId = null
                playbackDestination.selectLocal()
                status = "Playback will stay on ${services.trust.selfName() ?: services.displayName}."
                statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.PlaybackWillStayOnDevice, listOf(services.trust.selfName() ?: services.displayName))
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
                statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ThisDeviceIsNowNamedDevice, listOf(it ?: services.displayName))
                // The advertised name is part of presence; a visible code is reissued with it.
                when {
                    codeVisible -> issueCode()
                    presenceAdvertisement != null -> {
                        ensurePresence(pairable = false)
                        publish()
                    }
                    else -> publish()
                }
            }
            .onFailure {
                report(NaviampConnectStatusText.RequestFailed)
                publish()
            }
    }

    private fun changeTrustedDeviceAlias(trustedDeviceId: String, alias: String) {
        runCatching { services.trust.setAlias(trustedDeviceId, alias) }
            .onSuccess { publish() }
            .onFailure {
                report(NaviampConnectStatusText.RequestFailed)
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
        statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ForgotDevice, listOf(trust.visibleDisplayName()))
        publish()
    }

    init {
        loadIdentity()
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
                                statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ReadyEnterThisCode)
                            }
                            is NaviampConnectAdvertisingStatus.Failed -> {
                                handleAdvertisingFailure(value)
                            }
                            NaviampConnectAdvertisingStatus.Idle -> Unit
                            is NaviampConnectAdvertisingStatus.Starting -> Unit
                        }
                        publish()
                    } else if (value is NaviampConnectAdvertisingStatus.Failed && presenceAdvertisement != null) {
                        handleAdvertisingFailure(value)
                        publish()
                    }
                }
            }
        }
        if (canPlayRemotely) ensureTrustedTargetListener()
    }

    private fun loadIdentity() {
        if (identityState.value is IdentityState.Ready) return
        identityState.value = IdentityState.Loading
        // Undispatched: a dispatcher that runs inline (tests) settles before construction returns.
        controllerScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            val loaded = try {
                withContext(services.networkDispatcher) { services.identity.loadOrCreate() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            identityState.value = loaded?.let(IdentityState::Ready) ?: IdentityState.Unavailable
            publish()
        }
    }

    /** Suspends until the identity load settles; false means secure storage is unavailable. */
    private suspend fun awaitIdentity(): Boolean {
        val settled = identityState.first { it !is IdentityState.Loading }
        if (settled is IdentityState.Ready) return true
        publish()
        return false
    }

    /** Runs [action] now when the identity is ready, or once it loads; never on failure. */
    private fun withIdentity(action: () -> Unit) {
        if (identityState.value is IdentityState.Ready) {
            action()
            return
        }
        controllerScope.launch { if (awaitIdentity()) action() }
    }

    fun close() {
        codeVisible = false
        pendingTargetPairing?.reject()
        pendingTargetPairing = null
        closeTargetResources()
        targetPairing.stop()
        automaticReconnectRetryJob?.cancel()
        automaticReconnectRetryJob = null
        closeAuthenticatedSession()
        discovery?.stop()
        networkEffects.close()
        controllerScope.cancel()
    }

    fun onLocalPlaybackChanged(live: NaviampLivePlaybackState) {
        val previous = lastObservedTargetPlayback
        lastObservedTargetPlayback = live
        val connectedTarget = targetSession
        if (connectedTarget == null) {
            targetTimeline = null
            targetTimelineRevision = -1L
            publish()
            return
        }
        val now = services.monotonicMillis()
        val published = connectedTarget.snapshot()
        if (published.revision != targetTimelineRevision) {
            // Command results and local publishes both advance the revision the controller holds.
            targetTimelineRevision = published.revision
            targetTimeline = NaviampConnectTargetTimeline(
                positionSeconds = published.playback.positionMillis / 1_000.0,
                playing = published.playback.state == app.naviamp.domain.connect.NaviampConnectPlaybackState.Playing,
                anchoredAtMillis = now,
            )
        }
        if (!shouldPublishNaviampConnectTargetSnapshot(previous, live, targetTimeline, now)) return
        // Re-anchor at once so ticks before the debounced publish do not restart it indefinitely.
        live.toConnectTargetTimeline(now)?.let { targetTimeline = it }
        targetSnapshotPublishJob?.cancel()
        targetSnapshotPublishJob = controllerScope.launch {
            delay(TargetSnapshotDebounceMillis)
            publishTargetPlaybackSnapshot()
        }
    }

    /** Redirects shared catalog playback intents to the connected TV while browsing locally. */
    internal fun routeProductCommand(command: NaviampCoreCommand): Boolean {        if (!canControl || playbackDestination.selectedTrustedDeviceId() == null) return false
        if (controllerSession == null || !playbackDestination.isConnectedRemote()) {
            val playbackIntent = command is NaviampCoreCommand.NowPlaying.Playback ||
                command.connectMediaSelectionOrNull() != null || command.connectQueueSelectionOrNull() != null
            if (playbackIntent) {
                notice = app.naviamp.ui.NaviampConnectStatusNotice.RemoteUnavailable
                publish()
            }
            return playbackIntent
        }
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
                statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ConnectThisDeviceToTheSameMusicSourceBeforeEditingTheTvQueue)
                publish()
                return true
            }
            sendRemoteCommand(queued.toCommand(identity))
            return true
        }
        val selection = command.connectMediaSelectionOrNull()
            ?.withTrackListContext(trackSelectionContext(command))
            ?: return false
        val identity = sourceIdentity()
        if (identity == null) {
            status = "Connect this device to the same music source before playing on the TV."
            statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ConnectThisDeviceToTheSameMusicSourceBeforePlayingOnTheTv)
            publish()
            return true
        }
        sendRemoteCommand(selection.toCommand(identity), activatesPlaybackAuthority = true)
        return true
    }

    /** Shows a code while the user keeps this device's "Let another device connect" panel open. */
    private fun startPairingMode() {
        if (!canPlayRemotely || advertising == null) return
        // Record the panel's state now; a still-loading identity only delays the code.
        codeVisible = true
        if (identityState.value !is IdentityState.Ready) {
            withIdentity { if (codeVisible) showFreshCode() }
            return
        }
        showFreshCode()
    }

    private fun showFreshCode() {
        codeFailures = 0
        advertisingFailure = null
        settingsOpenFailed = false
        issueCode()
    }

    /** Replaces any current offer with a fresh code on the persistent presence endpoint. */
    private fun issueCode() {
        val advertisingController = advertising ?: return
        codeRetryJob?.cancel()
        codeRetryJob = null
        resetPairingOfferKeepingListener()
        val code = services.newPairingCode().filter(Char::isDigit).take(6)
        if (code.length != 6) {
            fail(NaviampConnectStatusText.CouldNotCreateCode)
            return
        }
        val presence = ensurePresence(pairable = true) ?: return
        val now = services.nowEpochMillis()
        // The offer shares the presence identity but carries its own lifetime; rotating the code
        // never re-registers DNS-SD, so a live session is not disturbed by a DNS goodbye.
        val offer = presence.copy(expiresAtEpochMillis = now + services.pairingLifetimeMillis)
        val pairingSessionId = services.newOpaqueId()
        targetPairing.start(offer, pairingSessionId, code, now)
        if (advertisingController.state.value is NaviampConnectAdvertisingStatus.Advertising) {
            phase = NaviampConnectPairingUiPhase.Advertising
            status = "Ready for a controller on this local network."
            statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ReadyEnterThisCode)
        } else {
            phase = NaviampConnectPairingUiPhase.Starting
            status = "Starting secure pairing…"
            statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.StartingSecurePairing)
        }
        pairingExpiryJob = controllerScope.launch {
            delay((offer.expiresAtEpochMillis - services.nowEpochMillis()).coerceAtLeast(0L))
            pairingExpiryJob = null
            if (targetPairing.expireIfNeeded(services.nowEpochMillis()) && codeVisible) issueCode()
        }
        publish()
    }

    /**
     * Keeps the listener and DNS-SD registration alive for trusted resume. [pairable] only changes
     * the advertised "showing a code" flag; it never creates a code.
     */
    private fun ensurePresence(pairable: Boolean): NaviampConnectAdvertisement? {
        val advertisingController = advertising ?: return null
        if (identityState.value !is IdentityState.Ready) return null
        return try {
            val boundListener = listener ?: services.transport.listen(services.pairingListenPort).also { listener = it }
            val name = localTargetDevice.displayName
            val current = presenceAdvertisement?.takeIf { it.port == boundListener.port && it.displayName == name }
            val presence = (current ?: NaviampConnectAdvertisement(
                instanceId = services.newOpaqueId(),
                displayName = name,
                protocolRange = NaviampConnectProtocolRange(),
                deviceCapabilities = localTargetDevice.deviceCapabilities,
                capabilities = if (targetPlayback != null) supportedTargetCapabilities else services.targetCapabilities,
                port = boundListener.port,
                identityFingerprint = localIdentity.identityFingerprint,
                expiresAtEpochMillis = Long.MAX_VALUE,
            )).copy(pairable = pairable)
            presenceAdvertisement = presence
            if (registeredAdvertisement() != presence) {
                advertisingFailure = null
                advertisingController.start(presence)
            }
            (advertisingController.state.value as? NaviampConnectAdvertisingStatus.Failed)?.let {
                handleAdvertisingFailure(it)
                publish()
                return null
            }
            if (listenerJob?.isActive != true) {
                listenerJob = controllerScope.launch { acceptPairingRequests(boundListener) }
            }
            presence
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            handleAdvertisingFailure(NaviampConnectAdvertisingStatus.Failed(
                failure.message ?: "Could not start Naviamp Connect pairing.",
            ))
            publish()
            null
        }
    }

    private fun registeredAdvertisement(): NaviampConnectAdvertisement? = when (val current = advertising?.state?.value) {
        is NaviampConnectAdvertisingStatus.Starting -> current.advertisement
        is NaviampConnectAdvertisingStatus.Advertising -> current.advertisement
        else -> null
    }

    private suspend fun acceptPairingRequests(boundListener: NaviampConnectTransportListener) {
        try {
            while (listener === boundListener) {
                val connection = boundListener.accept()
                acceptedPairingConnection = connection
                var initialFailure: NaviampConnectStatusText? = null
                val initialEnvelope = try {
                    receiveInitialConnectEnvelopeWithDeadline(connection)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    initialFailure = NaviampConnectStatusText.IncompleteConnectionRequestClosed
                    null
                }
                if (initialEnvelope == null) {
                    connection.close()
                    acceptedPairingConnection = null
                    if (listener !== boundListener) return
                    if (codeVisible) phase = NaviampConnectPairingUiPhase.Advertising
                    report(initialFailure ?: NaviampConnectStatusText.IncompleteConnectionRequestTimedOut)
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
                    statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.KnownDeviceCouldNotReconnect)
                    publish()
                    continue
                }
                if (targetPairing.state !is app.naviamp.domain.connect.NaviampConnectTargetPairingState.Advertising) {
                    // New connections are accepted only while this device shows a code.
                    connection.close()
                    acceptedPairingConnection = null
                    if (listener !== boundListener) return
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
                    statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.IncompleteConnectionRequestTimedOut)
                    publish()
                    continue
                }
                when (result) {
                    is NaviampConnectTargetPairingRequestResult.AwaitingApproval -> {
                        acceptedPairingConnection = null
                        pendingTargetPairing?.reject()
                        pendingTargetPairing = result.request
                        // A request exists only while this device shows its code: that is the consent.
                        approveController()
                        return
                    }
                    is NaviampConnectTargetPairingRequestResult.Rejected -> {
                        acceptedPairingConnection = null
                        // This loop also serves trusted resume: an expired offer is replaced, never fatal.
                        // The handshake may already have marked the offer expired; either way show a new code.
                        if (targetPairing.state !is app.naviamp.domain.connect.NaviampConnectTargetPairingState.Advertising ||
                            targetPairing.expireIfNeeded(services.nowEpochMillis())
                        ) {
                            val limited = (targetPairing.state as? app.naviamp.domain.connect.NaviampConnectTargetPairingState.Failed)
                                ?.code == NaviampConnectErrorCode.RateLimited
                            // A rate-limited offer counts toward the lockout instead of rotating freely.
                            if (limited) codeAttemptFailed() else if (codeVisible && codeRetryJob == null) issueCode()
                            publish()
                            continue
                        }
                        phase = NaviampConnectPairingUiPhase.Advertising
                        status = "Pairing request rejected. Still waiting for a controller."
                        statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ConnectionAttemptDeclined)
                        publish()
                    }
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            if (listener === boundListener) {
                fail(NaviampConnectStatusText.RequestFailed)
                restorePresenceLater()
            }
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
        val advertisement = presenceAdvertisement ?: run {
            credential.fill(0)
            connection.close()
            return false
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
                targetNonce = services.newOpaqueId(),
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
                    statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ReconnectedToDevice, listOf(trust.displayName))
                } else {
                    result.session.close()
                    phase = NaviampConnectPairingUiPhase.Failed
                    status = "The trusted controller did not start a compatible session."
                    statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.TheTrustedControllerDidNotStartACompatibleSession)
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
        statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.AuthenticatingDevice, listOf(pending.controller.displayName))
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

    private fun handleAdvertisingFailure(failure: NaviampConnectAdvertisingStatus.Failed) {
        advertisingFailure = failure
        phase = NaviampConnectPairingUiPhase.Failed
        status = failure.message
        closeTargetResources()
        targetPairing.stop()
    }

    private fun recoveryProblem(): app.naviamp.ui.NaviampConnectRecoveryProblem? {
        val problem = discovery?.state?.value?.problem
        return when {
            identityState.value is IdentityState.Unavailable ->
                app.naviamp.ui.NaviampConnectRecoveryProblem.SecureStorageUnavailable
            advertisingFailure?.permissionDenied == true || problem is NaviampConnectDiscoveryProblem.PermissionDenied ->
                app.naviamp.ui.NaviampConnectRecoveryProblem.LocalNetworkPermission
            advertisingFailure != null -> app.naviamp.ui.NaviampConnectRecoveryProblem.AdvertisingUnavailable
            problem is NaviampConnectDiscoveryProblem.Failed || problem is NaviampConnectDiscoveryProblem.Unavailable ->
                app.naviamp.ui.NaviampConnectRecoveryProblem.DiscoveryUnavailable
            else -> null
        }
    }

    private fun openPermissionSettings() {
        if (recoveryProblem() != app.naviamp.ui.NaviampConnectRecoveryProblem.LocalNetworkPermission) return
        val effect = services.permissionSettings ?: return
        settingsOpenFailed = !runCatching { effect.open() }.getOrDefault(false)
        publish()
    }

    private fun retryConnection() {
        settingsOpenFailed = false
        if (identityState.value is IdentityState.Unavailable) {
            loadIdentity()
            withIdentity {
                if (codeVisible) showFreshCode() else ensureTrustedTargetListener()
                if (canControl) beginAutomaticTrustedReconnect()
            }
            publish()
            return
        }
        // Retry only failed native operations. Keep authenticated sessions and playback authority intact.
        val retryAdvertising = advertisingFailure != null
        when (discovery?.state?.value?.problem) {
            is NaviampConnectDiscoveryProblem.PermissionDenied,
            is NaviampConnectDiscoveryProblem.Unavailable,
            is NaviampConnectDiscoveryProblem.Failed -> {
                discovery.stop()
                discovery.start()
            }
            else -> Unit
        }
        if (retryAdvertising) { if (codeVisible) issueCode() else ensureTrustedTargetListener() }
        publish()
    }

    /** Discovery runs while the "Connect a device" panel is open (or a trusted reconnect needs it). */
    private fun changeConnectScreenVisibility(visible: Boolean) {
        if (!canControl || discovery == null) return
        connectScreenVisible = visible
        codeTargetWaitJob?.cancel()
        codeTargetWaitJob = null
        if (authenticatedSession == null) {
            selectedTarget = null
            selectedManualEndpoint = null
            enteredCode = ""
            if (phase == NaviampConnectPairingUiPhase.AwaitingCode) phase = NaviampConnectPairingUiPhase.Inactive
        }
        if (visible) {
            status = "Searching this local network…"
            statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.SearchingThisLocalNetwork)
            discovery.start()
        } else {
            // Panel-only messages must not outlive the panel.
            if (statusMessage?.text in ConnectPanelStatuses) report(null)
            if (pendingTrustedDeviceId == null && reconnectJob?.isActive != true) discovery.stop()
        }
        publish()
    }

    /** Devices that are showing a code right now, excluding this device. */
    private fun pairableTargets(
        targets: List<NaviampConnectDiscoveredTarget> = discovery?.state?.value?.targets.orEmpty(),
    ): List<NaviampConnectDiscoveredTarget> {
        val ownFingerprint = (identityState.value as? IdentityState.Ready)?.identity?.identityFingerprint
        return targets.filter { it.advertisement.pairable && it.advertisement.identityFingerprint != ownFingerprint }
    }

    /** A code typed before discovery found its device waits briefly for one to appear. */
    private fun awaitCodeTarget() {
        val discoveryController = discovery ?: return
        if (codeTargetWaitJob?.isActive == true) return
        discoveryController.start()
        status = "Searching this local network…"
        statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.SearchingThisLocalNetwork)
        publish()
        codeTargetWaitJob = controllerScope.launch {
            val found = withTimeoutOrNull(CodeTargetWaitMillis) {
                discoveryController.state.first { pairableTargets(it.targets).isNotEmpty() }
            }
            codeTargetWaitJob = null
            if (enteredCode.length != 6) return@launch
            if (found == null) {
                status = null
                statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.NoDeviceShowingACode)
                publish()
            } else {
                submitPairingCode()
            }
        }
    }

    private fun reconnectTrustedDevice(device: NaviampConnectTrustedDeviceUi) {
        if (!canControl) return
        retryableRemoteCommands = emptyList()
        val discoveryController = discovery ?: return
        val trust = services.trust.load().firstOrNull { it.trustedDeviceId == device.deviceId } ?: return
        if (services.credentials?.contains(trust.peerDevice.deviceId) != true) {
            report(NaviampConnectStatusText.ConnectAgainWithANewCode, trust.visibleDisplayName())
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
        statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.LookingForDeviceStartPairingModeOnTheTvIfNeeded, listOf(trust.displayName))
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
        startTrustedReconnect(trust, target)
    }

    private fun reconnectTrustedDeviceAtAddress(device: NaviampConnectTrustedDeviceUi, value: String) {
        if (!canControl) return
        val endpoint = parseNaviampConnectManualEndpoint(value)
        if (endpoint == null) {
            status = null
            statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ManualEndpointInvalid)
            publish()
            return
        }
        val trust = services.trust.load().firstOrNull { it.trustedDeviceId == device.deviceId } ?: return
        if (services.credentials?.contains(trust.peerDevice.deviceId) != true) return
        suspendAutomaticReconnectForManualPairing()
        playbackDestination.select(trust)
        closeAuthenticatedSession()
        pendingTrustedDeviceId = trust.trustedDeviceId
        val version = trust.lastKnownEndpoint?.advertisement?.protocolRange?.maximum
            ?: NaviampConnectProtocolRange().maximum
        startTrustedReconnect(trust, trust.toManualTarget(endpoint, version, services.nowEpochMillis()), manual = true)
    }

    private fun startTrustedReconnect(
        trust: NaviampConnectTrustRecord,
        target: NaviampConnectDiscoveredTarget,
        manual: Boolean = false,
    ) {
        if (identityState.value !is IdentityState.Ready) {
            withIdentity { startTrustedReconnect(trust, target, manual) }
            return
        }
        // A secret that exists but cannot be read (for example restored from a backup without its
        // Keystore key) can never resume; ask for a new code instead of retrying an "unreachable" peer.
        val storedSecret = services.credentials?.read(trust.peerDevice.deviceId)
        if (storedSecret == null) {
            pendingTrustedDeviceId = null
            playbackDestination.unavailable()
            phase = NaviampConnectPairingUiPhase.Failed
            report(NaviampConnectStatusText.ConnectAgainWithANewCode, trust.visibleDisplayName())
            publish()
            return
        }
        storedSecret.fill(0)
        pendingTrustedDeviceId = null
        phase = NaviampConnectPairingUiPhase.Handshaking
        playbackDestination.connecting(trust.trustedDeviceId)
        status = "Securely reconnecting to ${trust.displayName}…"
        statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.SecurelyReconnectingToDevice, listOf(trust.displayName))
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
            var reachedEndpoint = false
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
                        controllerNonce = services.newOpaqueId(),
                        onConnectionOpened = { connection ->
                            reachedEndpoint = true
                            activeReconnectConnection = connection
                        },
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
                        statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ReconnectedToDevice, listOf(trust.displayName))
                    } else {
                        result.session.close()
                        playbackDestination.incompatible()
                        phase = NaviampConnectPairingUiPhase.Failed
                        status = "The trusted TV did not start a compatible control session."
                        statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.TheTrustedTvDidNotStartACompatibleControlSession)
                    }
                }
                is app.naviamp.app.NaviampConnectResumptionResult.Failed -> {
                    playbackDestination.unavailable()
                    phase = NaviampConnectPairingUiPhase.Failed
                    status = if (manual) null else "Could not securely reconnect to ${trust.displayName}. Naviamp will retry when the TV is available."
                    statusMessage = if (manual) NaviampConnectStatusMessage(
                        if (reachedEndpoint) NaviampConnectStatusText.ManualEndpointRejected
                        else NaviampConnectStatusText.ManualEndpointUnreachable,
                    ) else NaviampConnectStatusMessage(NaviampConnectStatusText.CouldNotSecurelyReconnectToDeviceNaviampWillRetryWhenTheTvIsAvailable, listOf(trust.displayName))
                }
            }
            reconnectJob = null
            publish()
            if (!manual && result is app.naviamp.app.NaviampConnectResumptionResult.Failed) {
                scheduleAutomaticTrustedReconnect()
            }
        }
    }

    private fun selectTarget(targetUi: NaviampConnectDiscoveredTargetUi) {
        suspendAutomaticReconnectForManualPairing()
        selectedManualEndpoint = null
        selectedTarget = discovery?.state?.value?.targets?.firstOrNull {
            it.advertisement.instanceId == targetUi.instanceId
        }?.also { target ->
            recentlyResolvedTargets[target.advertisement.identityFingerprint] = target
        } ?: return
        // Digits typed before choosing a device are kept and submitted to the chosen one.
        if (enteredCode.length == 6) {
            submitPairingCode()
            return
        }
        phase = NaviampConnectPairingUiPhase.AwaitingCode
        status = "Enter the six-digit code shown on ${targetUi.displayName}."
        statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.EnterTheSixDigitCodeShownOnDevice, listOf(targetUi.displayName))
        publish()
    }

    private fun selectManualEndpoint(value: String) {
        if (!canControl) return
        val endpoint = parseNaviampConnectManualEndpoint(value)
        if (endpoint == null) {
            selectedManualEndpoint = null
            selectedTarget = null
            enteredCode = ""
            if (phase == NaviampConnectPairingUiPhase.AwaitingCode) phase = NaviampConnectPairingUiPhase.Failed
            status = null
            statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ManualEndpointInvalid)
            publish()
            return
        }
        suspendAutomaticReconnectForManualPairing()
        selectedTarget = null
        selectedManualEndpoint = endpoint
        enteredCode = ""
        phase = NaviampConnectPairingUiPhase.AwaitingCode
        status = null
        statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ManualEndpointEnterCode)
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
        // The sixth digit submits; there is no separate confirmation step.
        if (enteredCode.length == 6) submitPairingCode() else publish()
    }

    private fun submitPairingCode() {
        // Only an outgoing code handshake blocks entry; a background trusted reconnect does not.
        if (codePairingInFlight) return
        val manualEndpoint = selectedManualEndpoint
        if (selectedTarget == null && manualEndpoint == null && enteredCode.length == 6) {
            // Older targets cannot say whether they show a code; one device that does is the clear choice.
            val pairable = pairableTargets()
            val candidates = pairable.filter { it.advertisement.codeFlagAdvertised }.takeIf { it.size == 1 } ?: pairable
            when {
                candidates.size == 1 -> selectedTarget = candidates.single().also { target ->
                    recentlyResolvedTargets[target.advertisement.identityFingerprint] = target
                }
                candidates.size > 1 -> {
                    status = null
                    statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ChooseTheDeviceShowingThisCode)
                    publish()
                    return
                }
                else -> {
                    awaitCodeTarget()
                    return
                }
            }
        }
        val target = selectedTarget
        if (target == null && manualEndpoint == null) return
        // Entering a code chooses a new device: stop reconnecting to the previous one.
        suspendAutomaticReconnectForManualPairing()
        codeTargetWaitJob?.cancel()
        codeTargetWaitJob = null
        if (identityState.value !is IdentityState.Ready) {
            withIdentity(::submitPairingCode)
            return
        }
        if (enteredCode.length != 6) {
            status = "Enter all six digits shown on the TV."
            statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.EnterAllSixDigitsShownOnTheTv)
            publish()
            return
        }
        phase = NaviampConnectPairingUiPhase.Handshaking
        status = if (target != null) "Authenticating ${target.advertisement.displayName}…" else null
        statusMessage = if (target != null) {
            NaviampConnectStatusMessage(NaviampConnectStatusText.AuthenticatingDevice, listOf(target.advertisement.displayName))
        } else NaviampConnectStatusMessage(NaviampConnectStatusText.ManualEndpointAuthenticating)
        val code = enteredCode.toCharArray()
        enteredCode = ""
        // Mark the handshake before publishing so code entry is disabled while it runs.
        codePairingInFlight = true
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
            for (address in target?.addresses ?: listOf(checkNotNull(manualEndpoint).host)) {
                val attempt = try {
                    // Discovered and manual handshakes share one deadline that closes a stalled socket.
                    if (target != null) awaitNaviampConnectSocketOperation(
                        scope = controllerScope,
                        timeoutMillis = services.pairingHandshakeTimeoutMillis,
                        close = { activeManualPairingConnection?.close() },
                    ) {
                        runtime.pair(
                            address, target.advertisement, code.copyOf(), services.nowEpochMillis(), services.newOpaqueId(),
                            onConnectionOpened = { activeManualPairingConnection = it },
                        )
                    }.also { activeManualPairingConnection = null } else awaitNaviampConnectSocketOperation(
                        scope = controllerScope,
                        timeoutMillis = services.pairingHandshakeTimeoutMillis,
                        close = { activeManualPairingConnection?.close() },
                    ) {
                        runtime.pairManual(
                            address, checkNotNull(manualEndpoint).port, code.copyOf(),
                            services.nowEpochMillis(), services.newOpaqueId(),
                            onConnectionOpened = { activeManualPairingConnection = it },
                        )
                    }.also { activeManualPairingConnection = null }
                } catch (_: Exception) {
                    null
                }
                if (attempt != null) result = attempt
                if (attempt is NaviampConnectPairingRuntimeResult.Paired) {
                    break
                }
            }
            code.fill('\u0000')
            activeManualPairingConnection = null
            if (manualEndpoint != null && result is NaviampConnectPairingRuntimeResult.Paired) {
                selectedTarget = result.trust.toManualTarget(manualEndpoint, result.session.protocolVersion, services.nowEpochMillis())
            }
            codePairingInFlight = false
            finishPairing(result, localRole = NaviampConnectDeviceRole.Controller)
            if (manualEndpoint != null && result is NaviampConnectPairingRuntimeResult.Failed &&
                (result.code == NaviampConnectErrorCode.TargetUnavailable ||
                    result.stage == app.naviamp.app.NaviampConnectPairingFailureStage.TransportConnect)
            ) {
                statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ManualEndpointUnreachable)
                publish()
            }
        }.invokeOnCompletion {
            // However the attempt ends (including cancellation), code entry is released.
            if (codePairingInFlight) {
                codePairingInFlight = false
                publish()
            }
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
                        if (codeVisible) stopPairingMode()
                        ensureTrustedTargetListener()
                        // One session at a time: a previous session (or a late reconnect) must not leak.
                        if (authenticatedSession != null) closeAuthenticatedSession()
                        startControllerSession(result.session)
                    }
                }
                if (connected) {
                    phase = NaviampConnectPairingUiPhase.Paired
                    report(if (credentialSaved) NaviampConnectStatusText.ConnectedToDevice
                        else NaviampConnectStatusText.ConnectedWithoutSecureReconnect, result.trust.displayName)
                    when (localRole) {
                        NaviampConnectDeviceRole.Target -> {
                            // The used offer is replaced at once while the panel stays open, so the device never
                            // advertises a code it no longer has; presence (and its registration) is kept for resume.
                            ensurePresence(pairable = codeVisible)
                            replaceUsedCode()
                            phase = NaviampConnectPairingUiPhase.Paired
                            report(if (credentialSaved) NaviampConnectStatusText.ConnectedToDevice
                                else NaviampConnectStatusText.ConnectedWithoutSecureReconnect, result.trust.displayName)
                        }
                        NaviampConnectDeviceRole.Controller -> {
                            openPostConnectSheet()
                            // The attempt is finished: the next code must find its own device.
                            selectedTarget = null
                            selectedManualEndpoint = null
                            if (connectScreenVisible) discovery?.start()
                        }
                    }
                } else {
                    result.session.close()
                    if (localRole == NaviampConnectDeviceRole.Controller) playbackDestination.incompatible()
                    phase = NaviampConnectPairingUiPhase.Failed
                    status = "The paired device did not start a compatible control session."
                    statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ThePairedDeviceDidNotStartACompatibleControlSession)
                    if (localRole == NaviampConnectDeviceRole.Target) {
                        ensurePresence(pairable = codeVisible)
                        replaceUsedCode()
                    }
                }
            }
            is NaviampConnectPairingRuntimeResult.Failed -> {
                val retryingCode = localRole == NaviampConnectDeviceRole.Target && codeAttemptFailed()
                phase = if (retryingCode) NaviampConnectPairingUiPhase.Advertising else NaviampConnectPairingUiPhase.Failed
                report(result.code.statusText())
            }
        }
        publish()
    }

    /** The connecting device chooses sign-in, playback, or neither right after a code connection. */
    private fun openPostConnectSheet() {
        postConnectSignInFailed = false
        val remote = controllerSession?.state?.value ?: return
        val hasAccounts = controllerTargetHasSavedAccounts
        postConnectDeviceHasAccounts = hasAccounts
        postConnectSignIn = postConnectCanSignIn() && !hasAccounts
        postConnectOpen = true
    }

    private fun postConnectCanSignIn(): Boolean =
        controllerSession?.state?.value?.capabilities?.contains(NaviampConnectCapability.ConnectionProvisioning) == true &&
            providerSessions?.currentSourceId() != null

    private fun finishPostConnect(playOnDevice: Boolean) {
        if (!postConnectOpen || controllerProvisioningBusy) return
        // After a failed attempt the choice finishes without retrying; the password prompt retries.
        if (postConnectSignIn && postConnectCanSignIn() && !postConnectSignInFailed) {
            postConnectPendingPlay = playOnDevice
            provisionTarget()
            return
        }
        completePostConnect(playOnDevice)
    }

    /** Back, Escape or tapping outside closes the sheet without sending anything. */
    private fun dismissPostConnect() {
        if (!postConnectOpen || controllerProvisioningBusy) return
        postConnectSignIn = false
        completePostConnect(playOnDevice = false)
    }

    private fun completePostConnect(playOnDevice: Boolean) {
        postConnectOpen = false
        postConnectPendingPlay = null
        if (playOnDevice) {
            if (localPlayback?.connectLiveState()?.queue?.current != null) handoffLocalQueue() else publish()
        } else {
            stopControlling()
        }
    }

    /** Sign-in started from the sheet finishes the user's choice once the device confirms it. */
    private fun postConnectSignInFinished(succeeded: Boolean) {
        if (!postConnectOpen) return
        postConnectSignInFailed = !succeeded
        if (!succeeded) return // Keep the pending choice: a corrected password completes it.
        postConnectSignIn = false
        postConnectSignInFailed = false
        val pending = postConnectPendingPlay ?: return
        completePostConnect(pending)
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
        val live = playback.connectLiveState()
        val snapshot = snapshots.create(
            revision = 0,
            live = live,
            volumePercent = stateStore.state.value.shell.playback.settings.volumePercent,
        )
        session.send(
            NaviampConnectWelcome(
                sessionId = session.sessionId,
                protocolVersion = session.protocolVersion,
                target = localTargetDevice,
                capabilities = supportedTargetCapabilities,
                snapshot = snapshot,
                companionProvisioningSupported = NaviampConnectCapability.ConnectionProvisioning in supportedTargetCapabilities,
                // Protocol v1 controllers set up an empty target only when this is true.
                // Protocol-v1 controllers set this device up automatically only when it has no accounts.
                initialSetupAllowed = NaviampConnectCapability.ConnectionProvisioning in supportedTargetCapabilities && targetIsEmpty(),
                hasSavedAccounts = stateStore.state.value.shell.connectionSettings.connection.savedConnections.isNotEmpty(),
            ),
        )
        // An authenticated incoming controller makes this device the playback owner. Retaining an
        // old outgoing selection would misroute local actions and let its reconnect displace this session.
        suspendAutomaticReconnectForManualPairing()
        playbackDestination.selectLocal()
        retryableRemoteCommands = emptyList()
        selectedTarget = null
        notice = null
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
                    trace(NaviampCoreConnectTraceKind.SetupOfferReceived, offer.setupId)
                    // Every authenticated session came from a displayed code or a trusted device.
                    if (pendingProvisioning != null || targetProvisioningJob?.isActive == true) {
                        false
                    } else {
                        pendingProvisioning = offer
                        pendingProvisioningSession = session
                        applyProvisioning()
                        true
                    }
                },
                revealNowPlaying = revealTargetNowPlaying,
            ),
            initialOutboundSequence = session.nextOutboundSequence(),
        )
        authenticatedSession = session
        targetSession = connectedTarget
        trace(NaviampCoreConnectTraceKind.TargetSessionStarted)
        targetSnapshotFactory = snapshots
        lastObservedTargetPlayback = live
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
        controllerTargetHasSavedAccounts = welcome.hasSavedAccounts ?: (welcome.snapshot.sourceIdentity != null)
        controllerInitialSetupAllowed = welcome.initialSetupAllowed &&
            NaviampConnectCapability.ConnectionProvisioning in welcome.capabilities && welcome.snapshot.sourceIdentity == null
        adoptControllerSession(connectedController, welcome.companionProvisioningSupported)
        trace(NaviampCoreConnectTraceKind.ControllerSessionStarted)
        if (controllerInitialSetupAllowed) trace(NaviampCoreConnectTraceKind.InitialSetupAuthorized)
        authenticatedSessionJob = controllerScope.launch { receiveControllerSession(session, connectedController) }
        val retries = retryableRemoteCommands
        retryableRemoteCommands = emptyList()
        retries.forEach(::sendRemoteCommand)
        return true
    }

    internal fun adoptControllerSession(session: NaviampConnectControllerSession, companionProvisioningSupported: Boolean = false) {
        controllerCompanionProvisioningSupported = companionProvisioningSupported
        val connectedState = session.state.value
        connectedState.target?.let { target ->
            if (playbackDestination.selectedTrustedDeviceId() == null) {
                services.trust.load()
                    .firstOrNull { it.peerDevice.deviceId == target.deviceId }
                    ?.let(playbackDestination::select)
            }
            playbackDestination.connected(target)
            if (connectedState.snapshot.shouldResumeNaviampConnectPlaybackAuthority()) {
                playbackDestination.selectedTrustedDeviceId()
                    ?.let(playbackDestination::activatePlaybackAuthority)
            }
        }
        controllerSession = session
        notice = null
        startHeartbeat(session)
        publish()
    }

    private fun startHeartbeat(connected: NaviampConnectControllerSession) {
        heartbeatJob?.cancel()
        heartbeatJob = controllerScope.launch {
            while (controllerSession === connected) {
                delay(5_000L)
                val alive = try {
                    awaitNaviampConnectSocketOperation(controllerScope, 10_000L,
                        close = { if (controllerSession === connected) authenticatedSession?.close() },
                    ) { connected.heartbeat(); true } == true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) { false }
                if (!alive) {
                    expireControllerSession(connected)
                    return@launch
                }
                if (controllerProvisioningBusy) trace(NaviampCoreConnectTraceKind.SetupHeartbeatConfirmed,
                    provisioningResult?.first)
            }
        }
    }

    private fun expireControllerSession(connected: NaviampConnectControllerSession) {
        if (controllerSession !== connected) return
        trace(NaviampCoreConnectTraceKind.HeartbeatOrWriteTimedOut)
        notice = app.naviamp.ui.NaviampConnectStatusNotice.ConnectionTimedOut
        sessionEnded(null)
    }

    private suspend fun sendBounded(
        connected: NaviampConnectControllerSession,
        command: app.naviamp.domain.connect.NaviampConnectCommand,
    ): app.naviamp.app.NaviampConnectCommandSendResult? {
        val result = awaitNaviampConnectSocketOperation(controllerScope, 10_000L,
            close = { if (controllerSession === connected) authenticatedSession?.close() },
        ) { connected.send(command) }
        if (result == null) expireControllerSession(connected)
        return result
    }

    private suspend fun receiveTargetSession(
        session: NaviampConnectAuthenticatedSession,
        connectedTarget: NaviampConnectTargetSession,
    ) {
        runCatching {
            while (authenticatedSession === session) {
                val envelope = session.receive()
                if (envelope.message is app.naviamp.domain.connect.NaviampConnectPing && pendingProvisioning != null)
                    trace(NaviampCoreConnectTraceKind.SetupHeartbeatReceived, pendingProvisioning?.setupId)
                targetSessionMutex.withLock { connectedTarget.receive(envelope) }
            }
        }
        if (authenticatedSession === session) {
            trace(NaviampCoreConnectTraceKind.TargetReceiveEnded)
            sessionEnded(NaviampConnectStatusText.ControllerDisconnected)
        }
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
                    sessionEnded(NaviampConnectStatusText.AnotherDeviceTookControl, reconnectAutomatically = false)
                    return
                }
                if (message is app.naviamp.domain.connect.NaviampConnectConnectionProvisioningResult) {
                    val pending = provisioningResult
                    if (pending != null && (message.setupId == pending.first ||
                            (message.setupId == null && !controllerInitialSetupAllowed))) {
                        trace(NaviampCoreConnectTraceKind.SetupResultReceived, message.setupId)
                        pending.second.complete(message)
                    }
                    continue
                }
                if (message is app.naviamp.domain.connect.NaviampConnectErrorMessage) {
                    val offer = connectedController.state.value.pendingRequests
                        .firstOrNull { it.requestId == envelope.responseToRequestId }?.command as? NaviampConnectOfferConnectionProvisioning
                    val pending = provisioningResult
                    if (offer != null && pending != null && offer.setupId == pending.first) {
                        controllerSetupNeedsNewCode = offer.initialSetup
                        pending.second.complete(app.naviamp.domain.connect.NaviampConnectConnectionProvisioningResult(
                            false, "", pending.first, rejected = true))
                    }
                }
                connectedController.receive(envelope)
                if (connectedController.state.value.status ==
                    app.naviamp.app.NaviampConnectControllerConnectionStatus.Disconnected
                ) {
                    sessionEnded(NaviampConnectStatusText.Reconnecting)
                    return
                }
                if (connectedController.state.value.snapshot.shouldResumeNaviampConnectPlaybackAuthority()) {
                    playbackDestination.selectedTrustedDeviceId()
                        ?.let(playbackDestination::activatePlaybackAuthority)
                }
                publish()
            }
        }
        if (authenticatedSession === session) {
            trace(NaviampCoreConnectTraceKind.ControllerReceiveEnded)
            sessionEnded(NaviampConnectStatusText.DeviceDisconnected)
        }
    }

    private fun sessionEnded(message: NaviampConnectStatusText?, reconnectAutomatically: Boolean = true) {
        if (reconnectAutomatically) {
            retryableRemoteCommands += controllerSession
                ?.state
                ?.value
                ?.pendingRequests
                .orEmpty()
                .filter { it.idempotent }
                .map { it.command }
        } else {
            retryableRemoteCommands = emptyList()
        }
        closeAuthenticatedSession()
        if (reconnectAutomatically) playbackDestination.reconnecting() else playbackDestination.selectLocal()
        phase = NaviampConnectPairingUiPhase.Inactive
        report(message)
        publish()
        if (!reconnectAutomatically) return
        controllerScope.launch {
            delay(AutomaticReconnectDelayMillis)
            if (canPlayRemotely) ensureTrustedTargetListener()
            if (canControl) beginAutomaticTrustedReconnect()
        }
    }

    /** Keeps presence for trusted resume (and the open code panel's flag); never creates a code. */
    internal fun ensureTrustedTargetListener() {
        if (!canPlayRemotely || advertising == null) return
        if (!codeVisible && services.trust.load().isEmpty()) return
        if (identityState.value !is IdentityState.Ready) {
            withIdentity(::ensureTrustedTargetListener)
            return
        }
        ensurePresence(pairable = codeVisible)
        replaceUsedCode()
        publish()
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
        statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.LookingForDevice, listOf(trust.displayName))
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
        if (authenticatedSession != null) trace(NaviampCoreConnectTraceKind.SessionClosed)
        controllerProvisioningJob?.cancel()
        controllerProvisioningJob = null
        controllerProvisioningAttemptId = null
        targetProvisioningJob?.cancel()
        targetProvisioningJob = null
        provisioningResult?.second?.cancel()
        provisioningResult = null
        controllerProvisioningBusy = false
        needsProvisioningCredential = false
        provisioningCredentialSourceId = null
        controllerInitialSetupAllowed = false
        postConnectOpen = false
        postConnectSignIn = false
        postConnectPendingPlay = null
        postConnectSignInFailed = false
        controllerCompanionProvisioningSupported = false
        controllerSetupNeedsNewCode = false
        heartbeatJob?.cancel()
        heartbeatJob = null
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
        lastObservedTargetPlayback = null
        controllerSession?.disconnect()
        controllerSession = null
        pendingProvisioning = null
        pendingProvisioningSession = null
    }

    private fun stopControlling() {
        notice = null
        retryableRemoteCommands = emptyList()
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
        statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.StoppedControllingDeviceTheTvWillKeepPlaying, listOf(targetName))
        publish()
    }

    private fun sendRemoteCommand(
        command: app.naviamp.domain.connect.NaviampConnectCommand,
        activatesPlaybackAuthority: Boolean = false,
    ) {
        val connected = controllerSession ?: return
        val trustedDeviceId = playbackDestination.selectedTrustedDeviceId()
        controllerScope.launch {
            when (val result = sendBounded(connected, command)) {
                is app.naviamp.app.NaviampConnectCommandSendResult.Sent -> {
                    val completed = awaitRemoteCommand(connected, result.requestId)
                    if (controllerSession !== connected) return@launch
                    if (completed is app.naviamp.app.NaviampConnectRequestTerminalResult.Acknowledged) {
                        if (activatesPlaybackAuthority) trustedDeviceId?.let(playbackDestination::activatePlaybackAuthority)
                        status = null
                    } else if (completed is app.naviamp.app.NaviampConnectRequestTerminalResult.ProtocolRejected) {
                        applyRemoteCommandError(completed.error)
                    }
                }
                is app.naviamp.app.NaviampConnectCommandSendResult.Rejected -> applyRemoteCommandError(result.error)
                is app.naviamp.app.NaviampConnectCommandSendResult.Failed -> handleControllerWriteFailure(connected, result.result)
                null -> return@launch
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
            report(problem)
            publish()
            return
        }
        val connected = checkNotNull(controllerSession)
        val trustedDeviceId = playbackDestination.selectedTrustedDeviceId()
        controllerScope.launch {
            status = "Sending this queue to ${connected.state.value.target?.displayName ?: "the TV"}…"
            statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.SendingThisQueueToDevice, listOf(connected.state.value.target?.displayName))
            publish()
            val handoff = if (playing == null) {
                naviampCoreConnectQueueHandoff(checkNotNull(live), checkNotNull(identity))
            } else {
                naviampCoreConnectQueueHandoff(checkNotNull(live), checkNotNull(identity), playing)
            }
            when (val sent = sendBounded(connected, handoff)) {
                null -> return@launch
                is app.naviamp.app.NaviampConnectCommandSendResult.Rejected -> applyRemoteCommandError(sent.error)
                is app.naviamp.app.NaviampConnectCommandSendResult.Failed ->
                    handleControllerWriteFailure(connected, sent.result)
                is app.naviamp.app.NaviampConnectCommandSendResult.Sent -> {
                    val completed = awaitRemoteCommand(connected, sent.requestId)
                    if (controllerSession !== connected) return@launch
                    if (completed is app.naviamp.app.NaviampConnectRequestTerminalResult.Acknowledged) {
                        trustedDeviceId?.let(playbackDestination::activatePlaybackAuthority)
                        // The target now plays this queue; stop this device's own audio. A failure keeps it playing.
                        localPlayback?.executeConnectPlayback(NaviampConnectPause)
                        status = "Queue sent to ${connected.state.value.target?.displayName ?: "the playback device"}."
                        statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.QueueSentToDevice, listOf(connected.state.value.target?.displayName))
                    } else {
                        applyRemoteCommandFailure(
                            completed,
                            NaviampConnectStatusText.PlaybackDeviceDidNotRespond,
                        )
                    }
                }
            }
            publish()
        }
    }

    private fun provisionTarget(password: String? = null) {
        val sessions = providerSessions ?: return
        val connected = controllerSession ?: return
        if (controllerProvisioningBusy) return
        if (controllerSetupNeedsNewCode) {
            statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.SetupNewCodeRequired)
            publish()
            return
        }
        val sourceId = sessions.currentSourceId()
        if (sourceId == null) {
            statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ConnectThisDeviceToTheServerYouWantToConfigureOnTheTv)
            publish()
            return
        }
        if (password != null && sourceId != provisioningCredentialSourceId) return
        sourceMismatchRecoveryVisible = false
        controllerProvisioningBusy = true
        val attemptId = services.newOpaqueId()
        controllerProvisioningAttemptId = attemptId
        status = null
        statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.PreparingThisConnectionForSecureTvSetup)
        publish()
        controllerProvisioningJob = controllerScope.launch {
            var signedIn = false
            val pending = services.newOpaqueId() to kotlinx.coroutines.CompletableDeferred<app.naviamp.domain.connect.NaviampConnectConnectionProvisioningResult>()
            try {
                if (password != null && localConnection?.updateProvisioningCredential(sourceId, password) != true) {
                    provisioningFailed()
                    return@launch
                }
                val editable = sessions.currentProvisioningConnection()
                if (controllerSession !== connected || sessions.currentSourceId() != sourceId) return@launch
                if (editable == null) {
                    provisioningFailed()
                    return@launch
                }
                when (val exported = editable.form.toConnectProvisioningExport()) {
                    is NaviampCoreConnectProvisioningExport.Unsupported -> {
                        needsProvisioningCredential = exported.credentialUnavailable
                        provisioningCredentialSourceId = sourceId.takeIf { exported.credentialUnavailable }
                        status = null
                        statusMessage = NaviampConnectStatusMessage(if (exported.credentialUnavailable)
                            NaviampConnectStatusText.SetupPasswordRequired else
                            NaviampConnectStatusText.CouldNotValidateThatConnectionCheckTheServerAndCredentialThenRetry)
                    }
                    is NaviampCoreConnectProvisioningExport.Ready -> {
                        if (exported.profile.aurralCompanion != null && !controllerCompanionProvisioningSupported) {
                            provisioningFailed()
                            return@launch
                        }
                        needsProvisioningCredential = false
                        provisioningCredentialSourceId = null
                        provisioningResult = pending
                        when (val sent = sendBounded(connected, NaviampConnectOfferConnectionProvisioning(
                            profile = exported.profile,
                            portableSettings = stateStore.state.value.toConnectPortableSettings(),
                            initialSetup = controllerInitialSetupAllowed,
                            setupId = pending.first,
                        ))) {
                            is app.naviamp.app.NaviampConnectCommandSendResult.Sent -> {
                                trace(NaviampCoreConnectTraceKind.SetupOfferSent, pending.first)
                                statusMessage = NaviampConnectStatusMessage(if (controllerInitialSetupAllowed)
                                    NaviampConnectStatusText.PreparingThisConnectionForSecureTvSetup else
                                    NaviampConnectStatusText.ApproveSetupOnDevice,
                                    listOf(connected.state.value.target?.displayName))
                                publish()
                                val result = withTimeoutOrNull(60_000L) { pending.second.await() }
                                if (controllerSession !== connected) return@launch
                                if (result?.succeeded == true) {
                                    signedIn = true
                                    controllerInitialSetupAllowed = false
                                    statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.TvSetupCompletedSecurely)
                                } else if (controllerSetupNeedsNewCode) {
                                    statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.SetupNewCodeRequired)
                                } else if (result == null) {
                                    trace(NaviampCoreConnectTraceKind.SetupResultTimedOut, pending.first)
                                    statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.SetupTimedOut)
                                } else if (result.rejected) {
                                    statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ConnectionSetupRequestRejected)
                                } else {
                                    provisioningFailed()
                                    // Revalidation is available for a stale saved password as well as a missing one.
                                    needsProvisioningCredential = true
                                    provisioningCredentialSourceId = sourceId
                                }
                            }
                            null -> Unit // The bounded write already entered connection recovery.
                            is app.naviamp.app.NaviampConnectCommandSendResult.Rejected ->
                                statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ConnectionSetupRequestRejected)
                            is app.naviamp.app.NaviampConnectCommandSendResult.Failed ->
                                handleControllerWriteFailure(connected, sent.result)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (controllerSession === connected) provisioningFailed()
            } finally {
                if (provisioningResult === pending) provisioningResult = null
                pending.second.cancel()
                if (controllerSession === connected && controllerProvisioningAttemptId == attemptId) {
                    controllerProvisioningAttemptId = null
                    controllerProvisioningBusy = false
                    postConnectSignInFinished(signedIn)
                    publish()
                }
            }
        }
    }

    private fun provisioningFailed() {
        status = null
        statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.CouldNotValidateThatConnectionCheckTheServerAndCredentialThenRetry)
    }

    /** Applies a setup offer from an authenticated session; no approval is needed. */
    private fun applyProvisioning() {
        val offer = pendingProvisioning ?: return
        val requestingSession = pendingProvisioningSession ?: return
        if (requestingSession !== authenticatedSession || targetProvisioningJob?.isActive == true) return
        val connection = targetConnection ?: return
        val settings = targetSettings ?: return
        trace(NaviampCoreConnectTraceKind.SetupValidationStarted, offer.setupId)
        status = null
        statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ValidatingDevice,
            listOf(offer.profile.displayName.ifBlank { offer.profile.serverUrl }))
        publish()
        targetProvisioningJob = controllerScope.launch {
            try {
                val connected = withTimeoutOrNull(45_000L) {
                    connection.provisionConnect(offer.profile.toConnectionFormState())
                } == true
                if (authenticatedSession !== requestingSession || pendingProvisioning !== offer) return@launch
                trace(if (connected) NaviampCoreConnectTraceKind.SetupValidationSucceeded
                    else NaviampCoreConnectTraceKind.SetupValidationFailed, offer.setupId)
                if (connected) {
                    offer.portableSettings?.let(settings::applyConnectPortableSettings)
                    statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.TvSetupCompletedSecurely)
                    publishTargetPlaybackSnapshot()
                } else provisioningFailed()
                // Failed validation can be retried with a corrected offer during the live code grant.
                pendingProvisioning = null
                pendingProvisioningSession = null
                awaitNaviampConnectSocketOperation(controllerScope, 10_000L, requestingSession::close) {
                    requestingSession.send(app.naviamp.domain.connect.NaviampConnectConnectionProvisioningResult(
                        succeeded = connected, message = "", setupId = offer.setupId,
                    ))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (authenticatedSession === requestingSession) {
                    pendingProvisioning = null
                    pendingProvisioningSession = null
                    provisioningFailed()
                }
            } finally {
                if (authenticatedSession === requestingSession) publish()
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
            sourceMismatchRecoveryVisible = true
            status = null
            publish()
            return
        }
        controllerScope.launch {
            val wasPlaying = remote.playback.state ==
                app.naviamp.domain.connect.NaviampConnectPlaybackState.Playing
            if (wasPlaying && !sendRemoteCommandAndAwait(NaviampConnectPause)) {
                status = "The TV did not pause, so its queue was left in place."
                statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.TheTvDidNotPauseSoItsQueueWasLeftInPlace)
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
                statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.ThisDeviceCouldNotResolveEveryTrackSoTheTvKeptPlaybackAuthority)
            } else {
                status = "The TV queue is now playing on this device."
                statusMessage = NaviampConnectStatusMessage(NaviampConnectStatusText.TheTvQueueIsNowPlayingOnThisDevice)
            }
            publish()
        }
    }

    private suspend fun sendRemoteCommandAndAwait(
        command: app.naviamp.domain.connect.NaviampConnectCommand,
    ): Boolean {
        val connected = controllerSession ?: return false
        return when (val sent = sendBounded(connected, command)) {
            null -> false
            is app.naviamp.app.NaviampConnectCommandSendResult.Sent ->
                awaitRemoteCommand(connected, sent.requestId) is
                    app.naviamp.app.NaviampConnectRequestTerminalResult.Acknowledged
            is app.naviamp.app.NaviampConnectCommandSendResult.Rejected -> false
            is app.naviamp.app.NaviampConnectCommandSendResult.Failed -> {
                handleControllerWriteFailure(connected, sent.result)
                false
            }
        }
    }

    private suspend fun awaitRemoteCommand(
        connected: NaviampConnectControllerSession,
        requestId: String,
    ): app.naviamp.app.NaviampConnectRequestTerminalResult {
        val result = connected.awaitTerminalResult(requestId, 10_000L, retainOnTimeout = true)
        if (result == app.naviamp.app.NaviampConnectRequestTerminalResult.TimedOut) expireControllerSession(connected)
        return result
    }

    private fun applyRemoteCommandError(error: app.naviamp.domain.connect.NaviampConnectErrorMessage) {
        if (error.code == NaviampConnectErrorCode.SourceMismatch) {
            sourceMismatchRecoveryVisible = true
            status = null
        } else {
            report(error.code.statusText())
        }
    }

    private fun applyRemoteCommandFailure(
        result: app.naviamp.app.NaviampConnectRequestTerminalResult,
        fallback: NaviampConnectStatusText,
    ) {
        val rejection = result as? app.naviamp.app.NaviampConnectRequestTerminalResult.ProtocolRejected
        if (rejection?.error?.code == NaviampConnectErrorCode.SourceMismatch) {
            applyRemoteCommandError(rejection.error)
        } else {
            report(result.remoteCommandFailureText(fallback))
        }
    }

    private fun dismissSourceMismatchRecovery() {
        sourceMismatchRecoveryVisible = false
        status = null
        publish()
    }

    private fun handleControllerWriteFailure(
        connected: NaviampConnectControllerSession,
        failure: app.naviamp.app.NaviampConnectRequestTerminalResult.WriteFailed,
    ) {
        if (controllerSession !== connected) return
        sessionEnded(NaviampConnectStatusText.Reconnecting)
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

    /** Closing the code panel ends the offer only; a live session and trusted presence continue. */
    private fun stopPairingMode() {
        codeVisible = false
        codeFailures = 0
        codeRetryJob?.cancel()
        codeRetryJob = null
        advertisingFailure = null
        resetPairingOfferKeepingListener()
        if (canPlayRemotely && services.trust.load().isNotEmpty()) {
            ensurePresence(pairable = false)
        } else if (authenticatedSession == null) {
            closeTargetResources()
        }
        phase = NaviampConnectPairingUiPhase.Inactive
        status = null
        publish()
    }

    /** The listener failed: bring presence (and a visible code) back after a short pause. */
    private fun restorePresenceLater() {
        if (!canPlayRemotely) return
        controllerScope.launch {
            delay(AutomaticReconnectRetryMillis)
            if (listener != null) return@launch
            if (codeVisible) issueCode() else ensureTrustedTargetListener()
        }
    }

    /** The offer is used or gone while the panel is still open: show the next code. */
    private fun replaceUsedCode() {
        if (codeVisible && targetPairing.displayCode() == null && codeRetryJob == null) issueCode()
    }

    /**
     * A failed code attempt rotates the code while the panel stays open. After
     * [MaximumCodeFailures] failures new codes pause for [CodeFailureLockoutMillis].
     * Returns true when a new code is (or will be) shown.
     */
    private fun codeAttemptFailed(): Boolean {
        if (!codeVisible) {
            resetPairingOfferKeepingListener()
            ensureTrustedTargetListener()
            return false
        }
        codeFailures += 1
        if (codeFailures < MaximumCodeFailures) {
            issueCode()
            return true
        }
        resetPairingOfferKeepingListener()
        ensurePresence(pairable = false)
        codeRetryJob = controllerScope.launch {
            delay(CodeFailureLockoutMillis)
            codeRetryJob = null
            codeFailures = 0
            if (codeVisible) issueCode()
        }
        report(NaviampConnectStatusText.TooManyAttempts)
        return false
    }

    private fun resetPairingOfferKeepingListener() {
        pendingTargetPairing?.reject()
        pendingTargetPairing = null
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
        presenceAdvertisement = null
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

    /** Sets the shown status; the diagnostic text only names the message for traces. */
    private fun report(text: NaviampConnectStatusText?, vararg arguments: String?) {
        status = text?.name
        statusMessage = text?.let { NaviampConnectStatusMessage(it, arguments.toList()) }
    }

    private fun fail(text: NaviampConnectStatusText) {
        closeTargetResources()
        phase = NaviampConnectPairingUiPhase.Failed
        report(text)
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
        val effectiveStatusMessage = discovered?.problem?.let { NaviampConnectStatusMessage(it.statusText()) }
            ?: statusMessage
            ?: remote?.lastError?.let { NaviampConnectStatusMessage(it.code.statusText()) }
        val trusts = services.trust.load()
        val trustDisplayNames = disambiguateNaviampConnectDeviceNames(trusts.map { it.visibleDisplayName() })
        val destination = playbackDestination.snapshot()
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
                    codeVisible = codeVisible,
                    pairingCodeExpiresAtEpochMillis = targetPairing.displayCode()?.let {
                        (targetPairing.state as? app.naviamp.domain.connect.NaviampConnectTargetPairingState.Advertising)
                            ?.advertisement?.expiresAtEpochMillis
                    },
                    listeningPort = listener?.port,
                    enteredPairingCode = enteredCode,
                    connectScreenVisible = connectScreenVisible,
                    codeEntryBusy = codePairingInFlight,
                    manualEndpointAwaitingCode = selectedManualEndpoint != null &&
                        phase in setOf(NaviampConnectPairingUiPhase.AwaitingCode, NaviampConnectPairingUiPhase.Handshaking),
                    selectedTargetId = selectedTarget?.advertisement?.instanceId,
                    statusMessage = effectiveStatusMessage,
                    notice = notice,
                    recovery = recoveryProblem()?.let { problem ->
                        app.naviamp.ui.NaviampConnectRecoveryUi(
                            problem = problem,
                            canOpenSettings = services.permissionSettings != null &&
                                problem == app.naviamp.ui.NaviampConnectRecoveryProblem.LocalNetworkPermission,
                            settingsOpenFailed = settingsOpenFailed,
                        )
                    },
                    selectedPlaybackDeviceId = remoteDestination?.device?.trustedDeviceId,
                    selectedPlaybackDeviceName = remoteDestination?.let { selected ->
                        trusts.firstOrNull { it.trustedDeviceId == selected.device.trustedDeviceId }
                            ?.visibleDisplayName()
                            ?: selected.device.displayName
                    },
                    localDeviceName = services.trust.selfName() ?: services.displayName,
                    playbackDestinationStatus = destination.toUiStatus(),
                    remotePlaybackAuthorityActive = playbackDestination.hasRemotePlaybackAuthority(),
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
                            val provider = providerSessions?.currentProvider()
                            remoteSnapshot?.toRemoteNowPlayingUiOrNull(target.displayName) { artworkId ->
                                artworkId?.let { provider?.coverArtUrl(it) }
                            }
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
                    sourceMismatchRecovery = if (sourceMismatchRecoveryVisible) {
                        NaviampConnectSourceMismatchUi(
                            targetName = remote?.target?.displayName,
                            canProvisionTarget = remote?.capabilities?.contains(
                                NaviampConnectCapability.ConnectionProvisioning,
                            ) == true && providerSessions?.currentSourceId() != null,
                        )
                    } else {
                        null
                    },
                    postConnect = remote?.target?.takeIf { postConnectOpen }?.let { target ->
                        app.naviamp.ui.NaviampConnectPostConnectUi(
                            deviceName = target.displayName,
                            canSignIn = postConnectCanSignIn(),
                            signInSelected = postConnectSignIn,
                            deviceHasAccounts = postConnectDeviceHasAccounts,
                            canPlayOn = target.canActAs(NaviampConnectDeviceRole.Target),
                            busy = controllerProvisioningBusy,
                        )
                    },
                    needsProvisioningCredential = needsProvisioningCredential,
                    provisioningBusy = controllerProvisioningBusy,
                    discoveredTargets = pairableTargets(discovered?.targets.orEmpty()).map { target ->
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

/** A deliberate reconnect adopts playback already in progress, but leaves an idle target armed. */
internal fun NaviampConnectTargetSnapshot?.shouldResumeNaviampConnectPlaybackAuthority(): Boolean =
    this != null && playback.state != app.naviamp.domain.connect.NaviampConnectPlaybackState.Idle

private fun app.naviamp.app.NaviampConnectRequestTerminalResult.remoteCommandFailureText(
    fallback: NaviampConnectStatusText,
): NaviampConnectStatusText = when (this) {
    app.naviamp.app.NaviampConnectRequestTerminalResult.Acknowledged -> fallback
    is app.naviamp.app.NaviampConnectRequestTerminalResult.ProtocolRejected -> error.code.statusText()
    app.naviamp.app.NaviampConnectRequestTerminalResult.TimedOut -> fallback
    app.naviamp.app.NaviampConnectRequestTerminalResult.Disconnected ->
        NaviampConnectStatusText.PlaybackDeviceDisconnectedBeforeConfirming
    is app.naviamp.app.NaviampConnectRequestTerminalResult.WriteFailed -> NaviampConnectStatusText.Reconnecting
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
        action == NowPlayingPlaybackAction.PlayCurrent ||
            action == NowPlayingPlaybackAction.Resume ||
            action == NowPlayingPlaybackAction.TogglePlayPause
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

private fun NaviampConnectTrustRecord.toManualTarget(
    endpoint: NaviampConnectManualEndpoint,
    protocolVersion: Int,
    nowEpochMillis: Long,
): NaviampConnectDiscoveredTarget = NaviampConnectDiscoveredTarget(
    serviceName = endpoint.displayAddress,
    addresses = listOf(endpoint.host),
    advertisement = NaviampConnectAdvertisement(
        instanceId = "manual-$trustedDeviceId",
        displayName = peerDevice.displayName,
        protocolRange = NaviampConnectProtocolRange(protocolVersion, protocolVersion),
        deviceCapabilities = peerDevice.deviceCapabilities,
        capabilities = emptySet(),
        port = endpoint.port,
        identityFingerprint = identityFingerprint,
        expiresAtEpochMillis = nowEpochMillis + 120_000L,
    ),
)

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
): NaviampConnectStatusText? = when {
    !hasPlayback -> NaviampConnectStatusText.QueueHandoffUnavailable
    !hasCurrentQueueItem -> NaviampConnectStatusText.StartSomethingBeforeSendingQueue
    !hasSourceIdentity -> NaviampConnectStatusText.ConnectSameSourceBeforeSendingQueue
    !connected -> NaviampConnectStatusText.ConnectBeforeSendingQueue
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
    reconnectAvailable = reconnectAvailable,
    playbackTarget = peerDevice.canActAs(NaviampConnectDeviceRole.Target),
)

private fun NaviampConnectDiscoveryProblem.statusText(): NaviampConnectStatusText = when (this) {
    NaviampConnectDiscoveryProblem.PermissionDenied -> NaviampConnectStatusText.LocalNetworkPermissionRequired
    is NaviampConnectDiscoveryProblem.Unavailable -> NaviampConnectStatusText.DiscoveryFailed
    is NaviampConnectDiscoveryProblem.Failed -> NaviampConnectStatusText.DiscoveryFailed
    is NaviampConnectDiscoveryProblem.TargetIdentityChanged -> NaviampConnectStatusText.DeviceIdentityChanged
}

private fun NaviampConnectErrorCode.statusText(): NaviampConnectStatusText = when (this) {
    NaviampConnectErrorCode.IncompatibleProtocol -> NaviampConnectStatusText.IncompatibleVersion
    NaviampConnectErrorCode.AuthenticationRequired -> NaviampConnectStatusText.CodeNotVerified
    NaviampConnectErrorCode.PairingExpired -> NaviampConnectStatusText.CodeExpired
    NaviampConnectErrorCode.RateLimited -> NaviampConnectStatusText.TooManyAttempts
    NaviampConnectErrorCode.TargetUnavailable -> NaviampConnectStatusText.DeviceNoLongerAvailable
    NaviampConnectErrorCode.InvalidRequest -> NaviampConnectStatusText.InvalidRequest
    else -> NaviampConnectStatusText.RequestFailed
}

private const val TargetSnapshotDebounceMillis = 250L
private const val AutomaticReconnectDelayMillis = 750L
private const val AutomaticReconnectRetryMillis = 2_000L
private const val MaximumCodeFailures = 5
private const val CodeFailureLockoutMillis = 30_000L
private const val CodeTargetWaitMillis = 5_000L
private val ConnectPanelStatuses = setOf(
    NaviampConnectStatusText.SearchingThisLocalNetwork,
    NaviampConnectStatusText.NoDeviceShowingACode,
    NaviampConnectStatusText.ChooseTheDeviceShowingThisCode,
)
