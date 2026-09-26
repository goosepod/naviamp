package app.naviamp.presentation

import app.naviamp.app.NaviampConnectAdvertisingEffect
import app.naviamp.app.NaviampConnectAdvertisingListener
import app.naviamp.app.NaviampConnectAdvertisingStartResult
import app.naviamp.app.NaviampConnectAuthenticatedCipher
import app.naviamp.app.NaviampConnectAuthenticatedCipherFactory
import app.naviamp.app.NaviampConnectDeviceIdentity
import app.naviamp.app.NaviampConnectDeviceIdentityEffect
import app.naviamp.app.NaviampConnectDiscoveryEffect
import app.naviamp.app.NaviampConnectDiscoveryListener
import app.naviamp.app.NaviampConnectDiscoveryStartResult
import app.naviamp.app.NaviampConnectDiscoveredTarget
import app.naviamp.app.NaviampConnectIdentityVerifier
import app.naviamp.app.NaviampConnectPakeFactory
import app.naviamp.app.NaviampConnectPakeRole
import app.naviamp.app.NaviampConnectPakeSession
import app.naviamp.app.NaviampConnectRegistrationService
import app.naviamp.app.NaviampConnectResolvedService
import app.naviamp.app.NaviampConnectControllerConnectionStatus
import app.naviamp.app.NaviampConnectControllerSession
import app.naviamp.app.NaviampConnectRequestIdFactory
import app.naviamp.app.NaviampConnectSessionTransport
import app.naviamp.app.NaviampConnectSessionSecret
import app.naviamp.app.NaviampConnectTransportConnection
import app.naviamp.app.NaviampConnectTransportFactory
import app.naviamp.app.NaviampConnectTransportListener
import app.naviamp.app.NaviampConnectTrustRepository
import app.naviamp.app.NaviampConnectTrustStorageEffect
import app.naviamp.ui.NaviampConnectPairingUiPhase
import app.naviamp.ui.NaviampConnectUiRole
import app.naviamp.domain.connect.NaviampConnectAdvertisement
import app.naviamp.domain.connect.NaviampConnectDevice
import app.naviamp.domain.connect.NaviampConnectDeviceCapability
import app.naviamp.domain.connect.NaviampConnectDeviceRole
import app.naviamp.domain.connect.NaviampConnectDiscoveryMetadata
import app.naviamp.domain.connect.NaviampConnectProtocolRange
import app.naviamp.domain.connect.NaviampConnectPlaybackSnapshot
import app.naviamp.domain.connect.NaviampConnectPlaybackState
import app.naviamp.domain.connect.NaviampConnectTargetSnapshot
import app.naviamp.domain.connect.NaviampConnectTrustRecord
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NaviampCoreConnectControllerTest {
    @Test
    fun manualAddressCanOpenCodePairingWithoutDiscovery() = runTest {
        val store = NaviampCoreStateStore()
        var attemptedAddress: Pair<String, Int>? = null
        val controller = NaviampCoreConnectController(this, store, NaviampCoreConnectServices(
            networkDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            deviceCapabilities = setOf(NaviampConnectDeviceCapability.ControlPlayback),
            displayName = "Controller",
            identity = FakeIdentity, identityVerifier = FakeIdentityVerifier,
            transport = object : NaviampConnectTransportFactory {
                override suspend fun connect(host: String, port: Int): NaviampConnectTransportConnection {
                    attemptedAddress = host to port
                    error("unreachable")
                }
                override fun listen(port: Int): NaviampConnectTransportListener = error("unused")
            },
            pake = UnusedPakeFactory, cipher = UnusedCipherFactory,
            trust = NaviampConnectTrustRepository(NaviampConnectTrustStorageEffect {}),
            newOpaqueId = { "opaque" }, newPairingCode = { "123456" },
            nowEpochMillis = { testScheduler.currentTime },
        ))
        controller.actions.onManualEndpointSelected("missing-port")
        assertEquals(app.naviamp.ui.NaviampConnectStatusText.ManualEndpointInvalid,
            store.state.value.shell.connect.statusMessage?.text)
        controller.actions.onManualEndpointSelected("100.101.102.103:45678")
        assertTrue(store.state.value.shell.connect.manualEndpointAwaitingCode)
        assertEquals(NaviampConnectPairingUiPhase.AwaitingCode, store.state.value.shell.connect.pairingPhase)
        assertEquals(app.naviamp.ui.NaviampConnectStatusText.ManualEndpointEnterCode,
            store.state.value.shell.connect.statusMessage?.text)
        controller.actions.onManualEndpointSelected("bad-address")
        assertFalse(store.state.value.shell.connect.manualEndpointAwaitingCode)
        controller.actions.onManualEndpointSelected("100.101.102.103:45678")
        controller.actions.onPairingCodeChanged("123456")
        controller.actions.onSubmitPairingCode()
        runCurrent()
        assertEquals("100.101.102.103" to 45678, attemptedAddress)
        assertEquals(NaviampConnectPairingUiPhase.Failed, store.state.value.shell.connect.pairingPhase)
        assertEquals(app.naviamp.ui.NaviampConnectStatusText.ManualEndpointUnreachable,
            store.state.value.shell.connect.statusMessage?.text)
        controller.close()
    }

    @Test
    fun advertisingPermissionRecoveryClosesFailedListenerAndCreatesFreshPairingOffer() = runTest {
        var denied = true
        var opens = 0
        val listeners = mutableListOf<WaitingListener>()
        val store = NaviampCoreStateStore()
        var nextId = 0
        val controller = NaviampCoreConnectController(this, store, NaviampCoreConnectServices(
                networkDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            deviceCapabilities = NaviampCorePlaybackTargetConnectCapabilities, displayName = "TV",
            identity = FakeIdentity, identityVerifier = FakeIdentityVerifier,
            transport = object : NaviampConnectTransportFactory {
                override suspend fun connect(host: String, port: Int) = error("unused")
                override fun listen(port: Int) = WaitingListener(42424).also { listeners += it }
            },
            pake = UnusedPakeFactory, cipher = UnusedCipherFactory,
            trust = NaviampConnectTrustRepository(NaviampConnectTrustStorageEffect {}),
            advertising = object : NaviampConnectAdvertisingEffect {
                override fun start(service: NaviampConnectRegistrationService, listener: NaviampConnectAdvertisingListener)
                    : NaviampConnectAdvertisingStartResult {
                    if (denied) return NaviampConnectAdvertisingStartResult.PermissionDenied
                    listener.onServiceRegistered(service.serviceName)
                    return NaviampConnectAdvertisingStartResult.Started
                }
                override fun stop() = Unit
            },
            permissionSettings = app.naviamp.app.NaviampConnectPermissionSettingsEffect { opens++; true },
            newOpaqueId = { "id-${++nextId}" }, newPairingCode = { "123456" },
            nowEpochMillis = { testScheduler.currentTime },
        ))
        controller.actions.onStartPairingMode()
        runCurrent()
        assertEquals(app.naviamp.ui.NaviampConnectRecoveryProblem.LocalNetworkPermission, store.state.value.shell.connect.recovery?.problem)
        assertTrue(listeners.single().closed)
        assertNull(store.state.value.shell.connect.pairingCode)
        controller.actions.onOpenPermissionSettings()
        assertEquals(1, opens)
        assertNotNull(store.state.value.shell.connect.recovery) // Opening settings does not grant permission.
        denied = false
        controller.actions.onRetryConnection()
        runCurrent()
        assertNull(store.state.value.shell.connect.recovery)
        assertEquals(NaviampConnectPairingUiPhase.Advertising, store.state.value.shell.connect.pairingPhase)
        assertEquals(app.naviamp.ui.NaviampConnectStatusText.ReadyForAControllerOnThisLocalNetwork,
            store.state.value.shell.connect.statusMessage?.text)
        assertEquals(2, listeners.size)
        assertEquals(42424, store.state.value.shell.connect.listeningPort)
        controller.close()
    }

    @Test
    fun discoveryPermissionRetryPreservesRemoteSessionAndReportsSettingsLaunchFailure() = runTest {
        val discovery = RecordingDiscoveryEffect()
        val (controller, store, _) = remoteFixture(discovery,
            app.naviamp.app.NaviampConnectPermissionSettingsEffect { false }) { }
        discovery.listener.onPermissionDenied()
        runCurrent()
        val selected = store.state.value.shell.connect.selectedPlaybackDeviceId
        controller.actions.onOpenPermissionSettings()
        assertEquals(true, store.state.value.shell.connect.recovery?.settingsOpenFailed)
        val starts = discovery.startCount
        controller.actions.onRetryConnection()
        runCurrent()
        assertEquals(starts + 1, discovery.startCount)
        assertNull(store.state.value.shell.connect.recovery)
        assertEquals(selected, store.state.value.shell.connect.selectedPlaybackDeviceId)
        assertEquals("TV", store.state.value.shell.connect.connectedTargetName)
        controller.close()
    }

    @Test
    fun unavailableDiscoveryHasRetryWithoutPermissionSettings() = runTest {
        val discovery = RecordingDiscoveryEffect()
        val (controller, store, _) = remoteFixture(discovery) { }
        controller.actions.onRefreshTargets()
        discovery.listener.onDiscoveryFailed("native detail")
        runCurrent()
        assertEquals(app.naviamp.ui.NaviampConnectRecoveryProblem.DiscoveryUnavailable, store.state.value.shell.connect.recovery?.problem)
        assertEquals(false, store.state.value.shell.connect.recovery?.canOpenSettings)
        controller.actions.onRetryConnection()
        runCurrent()
        assertNull(store.state.value.shell.connect.recovery)
        controller.close()
    }

    @Test
    fun reconnectResumesAuthorityForActiveTargetButLeavesIdleTargetArmed() {
        val target = NaviampConnectDevice("tv", "Living Room TV", NaviampConnectDeviceRole.Target)

        assertFalse(
            NaviampConnectTargetSnapshot(
                revision = 0,
                target = target,
                capabilities = emptySet(),
                playback = NaviampConnectPlaybackSnapshot(state = NaviampConnectPlaybackState.Idle),
            ).shouldResumeNaviampConnectPlaybackAuthority(),
        )
        assertTrue(
            NaviampConnectTargetSnapshot(
                revision = 0,
                target = target,
                capabilities = emptySet(),
                playback = NaviampConnectPlaybackSnapshot(state = NaviampConnectPlaybackState.Playing),
            ).shouldResumeNaviampConnectPlaybackAuthority(),
        )
        assertTrue(
            NaviampConnectTargetSnapshot(
                revision = 0,
                target = target,
                capabilities = emptySet(),
                playback = NaviampConnectPlaybackSnapshot(state = NaviampConnectPlaybackState.Paused),
            ).shouldResumeNaviampConnectPlaybackAuthority(),
        )
    }

    @Test
    fun armedRemoteUsesTheLocalQueueUntilFirstPlayRegardlessOfTheTargetsOldQueue() {
        assertEquals(
            NaviampCoreConnectPlaybackRoute.Local,
            naviampCoreConnectPlaybackRoute(
                remoteAuthorityActive = false,
                hasLocalCurrent = true,
                action = app.naviamp.ui.NowPlayingPlaybackAction.Pause,
            ),
        )
        assertEquals(
            NaviampCoreConnectPlaybackRoute.InitialHandoff,
            naviampCoreConnectPlaybackRoute(
                remoteAuthorityActive = false,
                hasLocalCurrent = true,
                action = app.naviamp.ui.NowPlayingPlaybackAction.PlayCurrent,
            ),
        )
        assertEquals(
            NaviampCoreConnectPlaybackRoute.InitialHandoff,
            naviampCoreConnectPlaybackRoute(
                remoteAuthorityActive = false,
                hasLocalCurrent = true,
                action = app.naviamp.ui.NowPlayingPlaybackAction.TogglePlayPause,
            ),
        )
        assertEquals(
            NaviampCoreConnectPlaybackRoute.Remote,
            naviampCoreConnectPlaybackRoute(
                remoteAuthorityActive = true,
                hasLocalCurrent = true,
                action = app.naviamp.ui.NowPlayingPlaybackAction.PlayCurrent,
            ),
        )
    }

    @Test
    fun dualCapabilityDeviceListensWithoutSelectingARemotePlaybackTarget() {
        var storedTrust: String? = null
        val trust = NaviampConnectTrustRepository(object : NaviampConnectTrustStorageEffect {
            override fun read() = storedTrust
            override fun write(value: String) { storedTrust = value }
        })
        val deviceCapabilities = setOf(
            NaviampConnectDeviceCapability.ControlPlayback,
            NaviampConnectDeviceCapability.PlaybackTarget,
        )
        trust.upsert(
            NaviampConnectTrustRecord(
                trustedDeviceId = "trusted-peer",
                peerDevice = NaviampConnectDevice(
                    deviceId = "peer",
                    displayName = "Other Naviamp",
                    role = NaviampConnectDeviceRole.Target,
                    deviceCapabilities = deviceCapabilities,
                ),
                identityFingerprint = "peer-fingerprint",
                publicKeyBase64 = "peer-key",
                pairedAtEpochMillis = 1L,
            ),
        )
        val credentials = app.naviamp.app.NaviampConnectSessionCredentialRepository(
            object : app.naviamp.app.NaviampConnectSessionCredentialStorageEffect {
                private var credential: ByteArray? = byteArrayOf(1, 2, 3)
                override fun read(peerDeviceId: String) = credential?.copyOf()
                override fun write(peerDeviceId: String, value: ByteArray) { credential = value.copyOf() }
                override fun remove(peerDeviceId: String) { credential = null }
                override fun contains(peerDeviceId: String) = credential != null
            },
        )
        val advertising = RecordingAdvertisingEffect()
        val discovery = RecordingDiscoveryEffect()
        val listener = WaitingListener(42_425)
        var listenCount = 0
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreConnectController(
            scope = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            stateStore = store,
            services = NaviampCoreConnectServices(
                networkDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
                deviceCapabilities = deviceCapabilities,
                displayName = "Office Phone",
                identity = FakeIdentity,
                identityVerifier = FakeIdentityVerifier,
                transport = object : NaviampConnectTransportFactory {
                    override suspend fun connect(host: String, port: Int) = error("unused")
                    override fun listen(port: Int): NaviampConnectTransportListener {
                        listenCount += 1
                        return listener
                    }
                },
                pake = UnusedPakeFactory,
                cipher = UnusedCipherFactory,
                trust = trust,
                credentials = credentials,
                discovery = discovery,
                advertising = advertising,
                newOpaqueId = generateSequence(0) { it + 1 }
                    .map { "opaque-$it" }
                    .iterator()::next,
                newPairingCode = { "123456" },
                nowEpochMillis = { 1_000L },
            ),
        )

        assertEquals(1, listenCount)
        assertEquals(0, discovery.startCount)
        assertEquals(
            app.naviamp.ui.NaviampConnectPlaybackDestinationUiStatus.Local,
            store.state.value.shell.connect.playbackDestinationStatus,
        )
        controller.actions.onPlaybackDeviceSelected("trusted-peer")
        assertEquals(1, discovery.startCount)
        assertEquals(
            deviceCapabilities,
            advertising.service?.let { service ->
                NaviampConnectDiscoveryMetadata.decode(
                    attributes = service.textAttributes,
                    port = service.port,
                    expiresAtEpochMillis = Long.MAX_VALUE,
                )?.deviceCapabilities
            },
        )
        assertEquals(NaviampConnectUiRole.ControllerAndTarget, store.state.value.shell.connect.role)
        controller.close()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun trustedTargetAdvertisesAutomatically() = runTest {
        var storedTrust: String? = null
        val trust = NaviampConnectTrustRepository(object : NaviampConnectTrustStorageEffect {
            override fun read() = storedTrust
            override fun write(value: String) { storedTrust = value }
        })
        trust.upsert(
            NaviampConnectTrustRecord(
                "trusted-phone",
                NaviampConnectDevice("phone", "Pixel", NaviampConnectDeviceRole.Controller),
                "phone-fingerprint",
                "phone-key",
                1L,
            ),
        )
        val advertising = RecordingAdvertisingEffect()
        val listener = WaitingListener(42425)
        var listenCount = 0
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreConnectController(
            scope = this,
            stateStore = store,
            services = NaviampCoreConnectServices(
                networkDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
                deviceCapabilities = NaviampCorePlaybackTargetConnectCapabilities,
                displayName = "Living Room TV",
                identity = FakeIdentity,
                identityVerifier = FakeIdentityVerifier,
                transport = object : NaviampConnectTransportFactory {
                    override suspend fun connect(host: String, port: Int) = error("unused")
                    override fun listen(port: Int): NaviampConnectTransportListener {
                        listenCount += 1
                        return listener
                    }
                },
                pake = UnusedPakeFactory,
                cipher = UnusedCipherFactory,
                trust = trust,
                advertising = advertising,
                newOpaqueId = generateSequence(0) { it + 1 }
                    .map { index -> "opaque-$index" }
                    .iterator()::next,
                newPairingCode = { "123456" },
                nowEpochMillis = { testScheduler.currentTime + 1_000L },
                pairingLifetimeMillis = 1_000L,
            ),
        )

        runCurrent()

        assertNotNull(advertising.service)
        assertEquals(NaviampConnectPairingUiPhase.Advertising, store.state.value.shell.connect.pairingPhase)
        controller.ensureTrustedTargetListener()
        assertEquals(1, listenCount)
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(1, listenCount)
        assertTrue(!listener.closed)
        controller.close()
    }

    @Test
    fun trustedControllerStaysLocalUntilTheUserSelectsAPlaybackDevice() {
        var storedTrust: String? = null
        val trust = NaviampConnectTrustRepository(object : NaviampConnectTrustStorageEffect {
            override fun read() = storedTrust
            override fun write(value: String) { storedTrust = value }
        })
        val target = NaviampConnectDevice("tv", "Living Room TV", NaviampConnectDeviceRole.Target)
        trust.upsert(NaviampConnectTrustRecord("trusted-tv", target, "fingerprint", "public-key", 1L))
        var storedCredential: ByteArray? = null
        val credentials = app.naviamp.app.NaviampConnectSessionCredentialRepository(
            object : app.naviamp.app.NaviampConnectSessionCredentialStorageEffect {
                override fun read(peerDeviceId: String) = storedCredential?.copyOf()
                override fun write(peerDeviceId: String, value: ByteArray) { storedCredential = value.copyOf() }
                override fun remove(peerDeviceId: String) { storedCredential = null }
                override fun contains(peerDeviceId: String) = storedCredential != null
            },
        )
        credentials.write("tv", byteArrayOf(1, 2, 3))
        val discovery = RecordingDiscoveryEffect()
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreConnectController(
            scope = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            stateStore = store,
            services = NaviampCoreConnectServices(
                networkDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
                deviceCapabilities = setOf(NaviampConnectDeviceCapability.ControlPlayback),
                displayName = "Pixel",
                identity = FakeIdentity,
                identityVerifier = FakeIdentityVerifier,
                transport = UnusedTransportFactory,
                pake = UnusedPakeFactory,
                cipher = UnusedCipherFactory,
                trust = trust,
                credentials = credentials,
                discovery = discovery,
                newOpaqueId = { "unused" },
                newPairingCode = { "123456" },
                nowEpochMillis = { 1_000L },
            ),
        )

        assertEquals(0, discovery.startCount)
        assertEquals(
            app.naviamp.ui.NaviampConnectPlaybackDestinationUiStatus.Local,
            store.state.value.shell.connect.playbackDestinationStatus,
        )
        controller.actions.onPlaybackDeviceSelected("trusted-tv")
        assertEquals(1, discovery.startCount)
        assertTrue(store.state.value.shell.connect.status.orEmpty().contains("Looking for Living Room TV"))
        controller.actions.onRefreshTargets()
        assertEquals(1, discovery.stopCount)
        assertEquals(2, discovery.startCount)
        controller.close()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun automaticTrustedReconnectRetriesWhenTheTvIsDiscoveredBeforeItsListenerIsReady() = runTest {
        var storedTrust: String? = null
        val trust = NaviampConnectTrustRepository(object : NaviampConnectTrustStorageEffect {
            override fun read() = storedTrust
            override fun write(value: String) { storedTrust = value }
        })
        val target = NaviampConnectDevice("tv", "Living Room TV", NaviampConnectDeviceRole.Target)
        trust.upsert(NaviampConnectTrustRecord("trusted-tv", target, "fingerprint", "public-key", 1L))
        var storedCredential: ByteArray? = null
        val credentials = app.naviamp.app.NaviampConnectSessionCredentialRepository(
            object : app.naviamp.app.NaviampConnectSessionCredentialStorageEffect {
                override fun read(peerDeviceId: String) = storedCredential?.copyOf()
                override fun write(peerDeviceId: String, value: ByteArray) { storedCredential = value.copyOf() }
                override fun remove(peerDeviceId: String) { storedCredential = null }
                override fun contains(peerDeviceId: String) = storedCredential != null
            },
        )
        credentials.write("tv", byteArrayOf(1, 2, 3))
        val discovery = RecordingDiscoveryEffect()
        var connectCount = 0
        val controller = NaviampCoreConnectController(
            scope = this,
            stateStore = NaviampCoreStateStore(),
            services = NaviampCoreConnectServices(
                networkDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
                deviceCapabilities = setOf(NaviampConnectDeviceCapability.ControlPlayback),
                displayName = "Pixel",
                identity = FakeIdentity,
                identityVerifier = FakeIdentityVerifier,
                transport = object : NaviampConnectTransportFactory {
                    override suspend fun connect(host: String, port: Int): NaviampConnectTransportConnection {
                        connectCount += 1
                        return StalledConnection()
                    }
                    override fun listen(port: Int) = error("unused")
                },
                pake = UnusedPakeFactory,
                cipher = UnusedCipherFactory,
                trust = trust,
                credentials = credentials,
                discovery = discovery,
                newOpaqueId = { "unused" },
                newPairingCode = { "123456" },
                nowEpochMillis = { testScheduler.currentTime + 1_000L },
                pairingHandshakeTimeoutMillis = 100L,
            ),
        )
        controller.actions.onPlaybackDeviceSelected("trusted-tv")
        discovery.listener.onServiceResolved(
            NaviampConnectResolvedService(
                serviceName = "Living Room",
                addresses = listOf("192.0.2.10"),
                port = 42_425,
                textAttributes = NaviampConnectDiscoveryMetadata.encode(
                    NaviampConnectAdvertisement(
                        instanceId = "tv-instance",
                        displayName = "Living Room TV",
                        protocolRange = NaviampConnectProtocolRange(),
                        capabilities = emptySet(),
                        port = 42_425,
                        identityFingerprint = "fingerprint",
                        expiresAtEpochMillis = Long.MAX_VALUE,
                    ),
                ),
            ),
        )

        runCurrent()
        advanceTimeBy(100L)
        runCurrent()
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals(2, connectCount)
        advanceTimeBy(100L)
        runCurrent()
        controller.actions.onRefreshTargets()
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(2, connectCount)
        controller.close()
    }

    @Test
    fun stopControllingClosesOnlyTheLiveSessionAndPreservesTrust() = runTest {
        var storedTrust: String? = null
        val trust = NaviampConnectTrustRepository(object : NaviampConnectTrustStorageEffect {
            override fun read() = storedTrust
            override fun write(value: String) { storedTrust = value }
        })
        val target = NaviampConnectDevice("tv", "Living Room TV", NaviampConnectDeviceRole.Target)
        val record = NaviampConnectTrustRecord("trusted-tv", target, "fingerprint", "public-key", 1L)
        trust.upsert(record)
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreConnectController(
            scope = this,
            stateStore = store,
            services = NaviampCoreConnectServices(
                networkDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
                deviceCapabilities = setOf(NaviampConnectDeviceCapability.ControlPlayback),
                displayName = "Pixel",
                identity = FakeIdentity,
                identityVerifier = FakeIdentityVerifier,
                transport = UnusedTransportFactory,
                pake = UnusedPakeFactory,
                cipher = UnusedCipherFactory,
                trust = trust,
                newOpaqueId = { "unused" },
                newPairingCode = { "123456" },
                nowEpochMillis = { 1L },
            ),
        )
        val session = NaviampConnectControllerSession(
            transport = NaviampConnectSessionTransport {},
            requestIds = NaviampConnectRequestIdFactory { "request" },
        )
        session.connect("session", 1, target, emptySet(), NaviampConnectTargetSnapshot(0, target, emptySet()))
        controller.adoptControllerSession(session)

        controller.actions.onStopControlling()

        assertEquals(NaviampConnectControllerConnectionStatus.Disconnected, session.state.value.status)
        assertEquals(listOf(record), trust.load())
        assertNull(store.state.value.shell.connect.connectedTargetName)
        assertEquals(
            "Stopped controlling Living Room TV. The TV will keep playing.",
            store.state.value.shell.connect.status,
        )
        controller.close()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun outboundCommandFailureImmediatelyEntersTheReconnectLifecycle() = runTest {
        var storedTrust: String? = null
        val trust = NaviampConnectTrustRepository(object : NaviampConnectTrustStorageEffect {
            override fun read() = storedTrust
            override fun write(value: String) { storedTrust = value }
        })
        val target = NaviampConnectDevice("tv", "Living Room TV", NaviampConnectDeviceRole.Target)
        trust.upsert(NaviampConnectTrustRecord("trusted-tv", target, "fingerprint", "public-key", 1L))
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreConnectController(
            scope = this,
            stateStore = store,
            services = NaviampCoreConnectServices(
                networkDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
                deviceCapabilities = setOf(NaviampConnectDeviceCapability.ControlPlayback),
                displayName = "Pixel",
                identity = FakeIdentity,
                identityVerifier = FakeIdentityVerifier,
                transport = UnusedTransportFactory,
                pake = UnusedPakeFactory,
                cipher = UnusedCipherFactory,
                trust = trust,
                newOpaqueId = { "unused" },
                newPairingCode = { "123456" },
                nowEpochMillis = { 1L },
            ),
        )
        val session = NaviampConnectControllerSession(
            transport = NaviampConnectSessionTransport { error("socket write failed") },
            requestIds = NaviampConnectRequestIdFactory { "request" },
        )
        session.connect(
            "session",
            1,
            target,
            setOf(app.naviamp.domain.connect.NaviampConnectCapability.TransportControls),
            NaviampConnectTargetSnapshot(0, target, emptySet()),
        )
        controller.adoptControllerSession(session)

        controller.actions.onRemotePlayPause()
        runCurrent()

        assertEquals(NaviampConnectControllerConnectionStatus.Disconnected, session.state.value.status)
        assertEquals(
            app.naviamp.ui.NaviampConnectPlaybackDestinationUiStatus.Reconnecting,
            store.state.value.shell.connect.playbackDestinationStatus,
        )
        assertTrue(store.state.value.shell.connect.status.orEmpty().contains("Reconnecting"))
        controller.close()
    }

    @Test
    fun trustedReconnectUsesTheLastWorkingEndpointUntilDiscoveryRefreshesIt() {
        val recent = NaviampConnectDiscoveredTarget(
            serviceName = "Living Room",
            addresses = listOf("192.0.2.10"),
            advertisement = NaviampConnectAdvertisement(
                instanceId = "recent",
                displayName = "Living Room TV",
                protocolRange = NaviampConnectProtocolRange(),
                capabilities = emptySet(),
                port = 42_425,
                identityFingerprint = "fingerprint",
                expiresAtEpochMillis = 2_000L,
            ),
        )
        val refreshed = recent.copy(
            addresses = listOf("192.0.2.11"),
            advertisement = recent.advertisement.copy(instanceId = "refreshed", expiresAtEpochMillis = 3_000L),
        )

        assertEquals(
            recent,
            selectNaviampConnectReconnectTarget("fingerprint", emptyList(), recent),
        )
        assertEquals(
            refreshed,
            selectNaviampConnectReconnectTarget("fingerprint", listOf(refreshed), recent),
        )
        assertEquals(
            recent,
            selectNaviampConnectReconnectTarget("fingerprint", emptyList(), recent),
        )
    }

    @Test
    fun queueHandoffExplainsWhyAnEmptyLocalQueueCannotBeSent() {
        assertEquals(
            "Start something on this device before sending its queue.",
            naviampCoreConnectQueueHandoffProblem(
                hasPlayback = true,
                hasCurrentQueueItem = false,
                hasSourceIdentity = true,
                connected = true,
            ),
        )
        assertNull(
            naviampCoreConnectQueueHandoffProblem(
                hasPlayback = true,
                hasCurrentQueueItem = true,
                hasSourceIdentity = true,
                connected = true,
            ),
        )
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun socketOperationDeadlineClosesANonCooperativeNativeEffect() = runTest {
        var closed = false
        val result = async {
            awaitNaviampConnectSocketOperation(
                scope = this@runTest,
                timeoutMillis = 1_000L,
                close = { closed = true },
            ) {
                awaitCancellation()
            }
        }

        advanceTimeBy(1_000L)
        runCurrent()

        assertNull(result.await())
        assertTrue(closed)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun targetStartsRealListenerAdvertisesItsPortAndPublishesCode() = runTest {
        val advertising = RecordingAdvertisingEffect()
        val listener = WaitingListener(42425)
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreConnectController(
            scope = this,
            stateStore = store,
            services = NaviampCoreConnectServices(
                networkDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
                deviceCapabilities = NaviampCorePlaybackTargetConnectCapabilities,
                displayName = "Living Room TV",
                identity = FakeIdentity,
                identityVerifier = FakeIdentityVerifier,
                transport = object : NaviampConnectTransportFactory {
                    override suspend fun connect(host: String, port: Int) = error("unused")
                    override fun listen(port: Int) = listener
                },
                pake = UnusedPakeFactory,
                cipher = UnusedCipherFactory,
                trust = NaviampConnectTrustRepository(NaviampConnectTrustStorageEffect {}),
                advertising = advertising,
                newOpaqueId = sequenceOf("instance", "session").iterator()::next,
                newPairingCode = { "123456" },
                nowEpochMillis = { 1_000L },
            ),
        )

        controller.actions.onStartPairingMode()
        runCurrent()

        val service = assertNotNull(advertising.service)
        assertEquals(42425, service.port)
        assertTrue(service.serviceName.contains("Living Room TV"))
        assertEquals("123456", store.state.value.shell.connect.pairingCode)
        assertEquals(NaviampConnectPairingUiPhase.Advertising, store.state.value.shell.connect.pairingPhase)
        assertTrue(service.textAttributes.isNotEmpty())

        controller.close()
        assertTrue(listener.closed)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun incompletePairingClientTimesOutClosesAndDoesNotMonopolizeListener() = runTest {
        val listener = QueuedListener(42425)
        val stalled = StalledConnection()
        listener.connections.trySend(stalled)
        val store = NaviampCoreStateStore()
        val controller = targetController(
            listener = listener,
            store = store,
            nowEpochMillis = { testScheduler.currentTime + 1_000L },
            pairingHelloTimeoutMillis = 1_000L,
            pairingLifetimeMillis = 10_000L,
        )

        controller.actions.onStartPairingMode()
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()

        assertTrue(stalled.closed)
        assertEquals(2, listener.acceptCount)
        assertEquals(NaviampConnectPairingUiPhase.Advertising, store.state.value.shell.connect.pairingPhase)
        assertTrue(store.state.value.shell.connect.status.orEmpty().contains("timed out"))
        controller.close()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun clientThatClosesBeforeHelloDoesNotTearDownListener() = runTest {
        val listener = QueuedListener(42425)
        val closedClient = ClosedConnection()
        listener.connections.trySend(closedClient)
        val store = NaviampCoreStateStore()
        val controller = targetController(
            listener = listener,
            store = store,
            nowEpochMillis = { testScheduler.currentTime + 1_000L },
            pairingHelloTimeoutMillis = 1_000L,
            pairingLifetimeMillis = 10_000L,
        )

        controller.actions.onStartPairingMode()
        runCurrent()

        assertTrue(closedClient.closed)
        assertEquals(2, listener.acceptCount)
        assertTrue(!listener.closed)
        assertEquals(NaviampConnectPairingUiPhase.Advertising, store.state.value.shell.connect.pairingPhase)
        controller.close()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun stoppingPairingClosesAnAcceptedStalledConnection() = runTest {
        val listener = QueuedListener(42425)
        val stalled = StalledConnection()
        listener.connections.trySend(stalled)
        val controller = targetController(
            listener = listener,
            store = NaviampCoreStateStore(),
            nowEpochMillis = { testScheduler.currentTime + 1_000L },
            pairingHelloTimeoutMillis = 10_000L,
            pairingLifetimeMillis = 20_000L,
        )

        controller.actions.onStartPairingMode()
        runCurrent()
        controller.actions.onStopPairingMode()
        runCurrent()

        assertTrue(stalled.closed)
        assertTrue(listener.closed)
        controller.close()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun pairingLifetimeStopsAdvertisingAndListenerWithoutAnotherRequest() = runTest {
        val listener = WaitingListener(42425)
        val store = NaviampCoreStateStore()
        val controller = targetController(
            listener = listener,
            store = store,
            nowEpochMillis = { testScheduler.currentTime + 1_000L },
            pairingHelloTimeoutMillis = 10_000L,
            pairingLifetimeMillis = 1_000L,
        )

        controller.actions.onStartPairingMode()
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()

        assertTrue(listener.closed)
        assertEquals(NaviampConnectPairingUiPhase.Failed, store.state.value.shell.connect.pairingPhase)
        assertTrue(store.state.value.shell.connect.status.orEmpty().contains("expired"))
        controller.close()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun discoveredTargetExpiresWithoutAnotherNativeCallback() = runTest {
        val discovery = RecordingDiscoveryEffect()
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreConnectController(
            scope = this,
            stateStore = store,
            services = NaviampCoreConnectServices(
                networkDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
                deviceCapabilities = setOf(NaviampConnectDeviceCapability.ControlPlayback),
                displayName = "Pixel",
                identity = FakeIdentity,
                identityVerifier = FakeIdentityVerifier,
                transport = UnusedTransportFactory,
                pake = UnusedPakeFactory,
                cipher = UnusedCipherFactory,
                trust = NaviampConnectTrustRepository(NaviampConnectTrustStorageEffect {}),
                discovery = discovery,
                newOpaqueId = { "unused" },
                newPairingCode = { "123456" },
                nowEpochMillis = { testScheduler.currentTime + 1_000L },
            ),
        )

        controller.actions.onRefreshTargets()
        discovery.listener.onServiceResolved(
            NaviampConnectResolvedService(
                serviceName = "Living Room",
                addresses = listOf("192.0.2.10"),
                port = 42_425,
                textAttributes = NaviampConnectDiscoveryMetadata.encode(
                    NaviampConnectAdvertisement(
                        instanceId = "tv-instance",
                        displayName = "Living Room",
                        protocolRange = NaviampConnectProtocolRange(),
                        capabilities = emptySet(),
                        port = 42_425,
                        identityFingerprint = "tv-fingerprint",
                        expiresAtEpochMillis = Long.MAX_VALUE,
                    ),
                ),
            ),
        )
        runCurrent()
        assertEquals(1, store.state.value.shell.connect.discoveredTargets.size)

        advanceTimeBy(120_000L)
        runCurrent()

        assertTrue(store.state.value.shell.connect.discoveredTargets.isEmpty())
        controller.close()
    }

    @Test
    fun disconnectedRemoteSelectionBlocksPlaybackButAllowsBrowsingAndExplicitLocalOutput() = runTest {
        val (controller, store, _) = remoteFixture { error("write failed") }
        controller.actions.onRemotePlayPause()
        runCurrent()
        val select = NaviampCoreCommand.Media.TrackAction(app.naviamp.ui.SharedTrackRowActionRequest(
            app.naviamp.ui.SharedTrackRowUi("song", "Song", "Artist"), app.naviamp.ui.SharedTrackRowAction.Select))
        val queue = NaviampCoreCommand.Media.TrackAction(app.naviamp.ui.SharedTrackRowActionRequest(
            app.naviamp.ui.SharedTrackRowUi("song", "Song", "Artist"), app.naviamp.ui.SharedTrackRowAction.AddToQueue))
        val resume = NaviampCoreCommand.NowPlaying.Playback(app.naviamp.ui.NowPlayingPlaybackActionRequest(
            app.naviamp.ui.NowPlayingPlaybackAction.Resume))
        assertTrue(controller.routeProductCommand(select))
        assertTrue(controller.routeProductCommand(queue))
        assertTrue(controller.routeProductCommand(resume))
        assertFalse(controller.routeProductCommand(NaviampCoreCommand.Media.TrackAction(
            app.naviamp.ui.SharedTrackRowActionRequest(app.naviamp.ui.SharedTrackRowUi("song", "Song", "Artist"),
                app.naviamp.ui.SharedTrackRowAction.Download))))
        assertEquals(app.naviamp.ui.NaviampConnectStatusNotice.RemoteUnavailable, store.state.value.shell.connect.notice)
        controller.actions.onPlaybackDeviceSelected(null)
        assertFalse(controller.routeProductCommand(select))
        controller.close()
    }

    @Test
    fun anIdlePeerThatStopsRespondingTriggersReconnect() = runTest {
        val sent = mutableListOf<app.naviamp.domain.connect.NaviampConnectEnvelope>()
        val (controller, store, session) = remoteFixture { sent += it }
        advanceTimeBy(15_001); runCurrent()
        assertTrue(sent.any { it.message is app.naviamp.domain.connect.NaviampConnectPing })
        assertEquals(NaviampConnectControllerConnectionStatus.Disconnected, session.state.value.status)
        assertEquals(app.naviamp.ui.NaviampConnectPlaybackDestinationUiStatus.Reconnecting,
            store.state.value.shell.connect.playbackDestinationStatus)
        assertEquals(app.naviamp.ui.NaviampConnectStatusNotice.ConnectionTimedOut, store.state.value.shell.connect.notice)
        controller.close()
    }

    @Test
    fun ordinaryCommandsHaveAcknowledgementDeadlinesEvenWhenThePeerAnswersHeartbeats() = runTest {
        val sent = mutableListOf<app.naviamp.domain.connect.NaviampConnectEnvelope>()
        val (controller, store, session) = remoteFixture { sent += it }
        controller.actions.onRemotePlayPause()
        runCurrent()
        advanceTimeBy(5_001); runCurrent()
        val ping = sent.map { it.message }.filterIsInstance<app.naviamp.domain.connect.NaviampConnectPing>().single()
        session.receive(app.naviamp.domain.connect.NaviampConnectEnvelope(1, sessionId = "session", sequence = 0,
            message = app.naviamp.domain.connect.NaviampConnectPong(ping.sentAtEpochMillis)))
        advanceTimeBy(5_000); runCurrent()
        assertEquals(app.naviamp.ui.NaviampConnectStatusNotice.ConnectionTimedOut, store.state.value.shell.connect.notice)
        assertEquals(app.naviamp.app.NaviampConnectRequestTerminalResult.TimedOut,
            session.state.value.terminalResults[sent.first().requestId])
        controller.close()
    }

    @Test
    fun aBlockedWriteHasADeadlineAndCannotLeaveTheOutputConnected() = runTest {
        val (controller, store, session) = remoteFixture { awaitCancellation() }
        controller.actions.onRemotePlayPause()
        runCurrent()
        advanceTimeBy(10_001); runCurrent()
        assertEquals(NaviampConnectControllerConnectionStatus.Disconnected, session.state.value.status)
        assertEquals(app.naviamp.ui.NaviampConnectStatusNotice.ConnectionTimedOut, store.state.value.shell.connect.notice)
        controller.close()
    }

    @Test
    fun selectingLocalCancelsHeartbeatAndOldCommandDeadlines() = runTest {
        val (controller, store, _) = remoteFixture { }
        controller.actions.onRemotePlayPause()
        runCurrent()
        controller.actions.onPlaybackDeviceSelected(null)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(app.naviamp.ui.NaviampConnectPlaybackDestinationUiStatus.Local,
            store.state.value.shell.connect.playbackDestinationStatus)
        assertNull(store.state.value.shell.connect.notice)
        controller.close()
    }

    @Test
    fun missingSetupCredentialCanBeCancelledAndRetriedWithoutSendingAnOffer() = runTest {
        val port = SetupSourcePort("")
        val sent = mutableListOf<app.naviamp.domain.connect.NaviampConnectEnvelope>()
        val (controller, store, _) = remoteFixture(providerSessions = port) { sent += it }
        controller.actions.onProvisionTarget()
        runCurrent()
        assertTrue(store.state.value.shell.connect.needsProvisioningCredential)
        assertFalse(store.state.value.shell.connect.provisioningBusy)
        assertTrue(sent.isEmpty())
        controller.actions.onCancelProvisioningCredential()
        assertFalse(store.state.value.shell.connect.needsProvisioningCredential)
        controller.actions.onProvisionTarget()
        runCurrent()
        assertTrue(store.state.value.shell.connect.needsProvisioningCredential)
        controller.close()
    }

    @Test
    fun provisioningHasOneOutstandingOfferAndATimeoutAllowsRecovery() = runTest {
        val sent = mutableListOf<app.naviamp.domain.connect.NaviampConnectEnvelope>()
        lateinit var session: NaviampConnectControllerSession
        var sequence = 0L
        val fixture = remoteFixture(providerSessions = SetupSourcePort("secret")) { envelope ->
            val ping = envelope.message as? app.naviamp.domain.connect.NaviampConnectPing
            if (ping == null) sent += envelope else session.receive(app.naviamp.domain.connect.NaviampConnectEnvelope(
                1, "session", ++sequence, message = app.naviamp.domain.connect.NaviampConnectPong(ping.sentAtEpochMillis)))
        }
        val (controller, store, connectedSession) = fixture
        session = connectedSession
        controller.actions.onProvisionTarget()
        controller.actions.onProvisionTarget()
        runCurrent()
        assertTrue(store.state.value.shell.connect.provisioningBusy)
        val request = sent.single().message as app.naviamp.domain.connect.NaviampConnectCommandRequest
        val offer = request.command as app.naviamp.domain.connect.NaviampConnectOfferConnectionProvisioning
        assertFalse(offer.initialSetup, "An adopted/trusted session has no initial setup grant")
        assertNotNull(offer.setupId)
        assertEquals("secret", offer.profile.password)
        advanceTimeBy(60_001)
        runCurrent()
        assertFalse(store.state.value.shell.connect.provisioningBusy)
        assertFalse(store.state.value.shell.connect.needsProvisioningCredential)
        assertEquals(app.naviamp.ui.NaviampConnectStatusText.SetupTimedOut, store.state.value.shell.connect.statusMessage?.text)
        controller.close()
    }

    @Test
    fun disconnectCancelsCredentialLoadingWithoutPublishingIntoTheClosedSession() = runTest {
        val port = SetupSourcePort("secret", suspendExport = true)
        val (controller, store, _) = remoteFixture(providerSessions = port) { error("must not send") }
        controller.actions.onProvisionTarget()
        runCurrent()
        assertTrue(store.state.value.shell.connect.provisioningBusy)
        controller.actions.onStopControlling()
        runCurrent()
        assertTrue(port.cancelled)
        assertFalse(store.state.value.shell.connect.provisioningBusy)
        assertFalse(store.state.value.shell.connect.needsProvisioningCredential)
        assertNull(store.state.value.shell.connect.connectedTargetName)
        controller.close()
    }

    private fun kotlinx.coroutines.test.TestScope.remoteFixture(
        discovery: NaviampConnectDiscoveryEffect? = null,
        permissionSettings: app.naviamp.app.NaviampConnectPermissionSettingsEffect? = null,
        providerSessions: NaviampCoreProviderSessionPort? = null,
        send: suspend (app.naviamp.domain.connect.NaviampConnectEnvelope) -> Unit,
    ): Triple<NaviampCoreConnectController, NaviampCoreStateStore, NaviampConnectControllerSession> {
        var stored: String? = null
        val trust = NaviampConnectTrustRepository(object : NaviampConnectTrustStorageEffect {
            override fun read() = stored
            override fun write(value: String) { stored = value }
        })
        val target = NaviampConnectDevice("tv", "TV", NaviampConnectDeviceRole.Target)
        trust.upsert(NaviampConnectTrustRecord("trusted-tv", target, "fingerprint", "public-key", 1L))
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreConnectController(this, store, NaviampCoreConnectServices(
                networkDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            deviceCapabilities = setOf(NaviampConnectDeviceCapability.ControlPlayback), displayName = "Controller",
            identity = FakeIdentity, identityVerifier = FakeIdentityVerifier, transport = UnusedTransportFactory,
            pake = UnusedPakeFactory, cipher = UnusedCipherFactory, trust = trust,
            discovery = discovery, permissionSettings = permissionSettings,
            newOpaqueId = { "unused" }, newPairingCode = { "123456" }, nowEpochMillis = { testScheduler.currentTime },
        ), providerSessions = providerSessions)
        if (discovery != null) controller.actions.onRefreshTargets()
        var next = 0
        val session = NaviampConnectControllerSession(NaviampConnectSessionTransport(send),
            NaviampConnectRequestIdFactory { "request-${++next}" })
        session.connect("session", 1, target, setOf(app.naviamp.domain.connect.NaviampConnectCapability.TransportControls,
                app.naviamp.domain.connect.NaviampConnectCapability.ConnectionProvisioning),
            NaviampConnectTargetSnapshot(0, target, emptySet()))
        controller.adoptControllerSession(session)
        return Triple(controller, store, session)
    }

    private fun CoroutineScope.targetController(
        listener: NaviampConnectTransportListener,
        store: NaviampCoreStateStore,
        nowEpochMillis: () -> Long,
        pairingHelloTimeoutMillis: Long,
        pairingLifetimeMillis: Long,
    ) = NaviampCoreConnectController(
        scope = this,
        stateStore = store,
        services = NaviampCoreConnectServices(
                networkDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            deviceCapabilities = NaviampCorePlaybackTargetConnectCapabilities,
            displayName = "Living Room TV",
            identity = FakeIdentity,
            identityVerifier = FakeIdentityVerifier,
            transport = object : NaviampConnectTransportFactory {
                override suspend fun connect(host: String, port: Int) = error("unused")
                override fun listen(port: Int) = listener
            },
            pake = UnusedPakeFactory,
            cipher = UnusedCipherFactory,
            trust = NaviampConnectTrustRepository(NaviampConnectTrustStorageEffect {}),
            advertising = RecordingAdvertisingEffect(),
            newOpaqueId = sequenceOf("instance", "session").iterator()::next,
            newPairingCode = { "123456" },
            nowEpochMillis = nowEpochMillis,
            pairingLifetimeMillis = pairingLifetimeMillis,
            pairingHelloTimeoutMillis = pairingHelloTimeoutMillis,
        ),
    )
}

private class RecordingAdvertisingEffect : NaviampConnectAdvertisingEffect {
    var service: NaviampConnectRegistrationService? = null

    override fun start(
        service: NaviampConnectRegistrationService,
        listener: NaviampConnectAdvertisingListener,
    ): NaviampConnectAdvertisingStartResult {
        this.service = service
        listener.onServiceRegistered(service.serviceName)
        return NaviampConnectAdvertisingStartResult.Started
    }

    override fun stop() = Unit
}

private class WaitingListener(override val port: Int) : NaviampConnectTransportListener {
    var closed = false
    override suspend fun accept(): NaviampConnectTransportConnection = awaitCancellation()
    override fun close() { closed = true }
}

private class QueuedListener(override val port: Int) : NaviampConnectTransportListener {
    val connections = Channel<NaviampConnectTransportConnection>(Channel.UNLIMITED)
    var acceptCount = 0
    var closed = false

    override suspend fun accept(): NaviampConnectTransportConnection {
        acceptCount += 1
        return connections.receive()
    }

    override fun close() {
        closed = true
        connections.close()
    }
}

private class StalledConnection : NaviampConnectTransportConnection {
    override val remoteAddress = "stalled.test"
    var closed = false

    override suspend fun send(frame: ByteArray) = Unit
    override suspend fun receive(): ByteArray? = awaitCancellation()
    override fun close() { closed = true }
}

private class ClosedConnection : NaviampConnectTransportConnection {
    override val remoteAddress = "closed.test"
    var closed = false

    override suspend fun send(frame: ByteArray) = Unit
    override suspend fun receive(): ByteArray? = null
    override fun close() { closed = true }
}

private class RecordingDiscoveryEffect : NaviampConnectDiscoveryEffect {
    lateinit var listener: NaviampConnectDiscoveryListener
    var startCount = 0
    var stopCount = 0

    override fun start(listener: NaviampConnectDiscoveryListener): NaviampConnectDiscoveryStartResult {
        this.listener = listener
        startCount += 1
        return NaviampConnectDiscoveryStartResult.Started
    }

    override fun stop() {
        stopCount += 1
    }
}

internal object UnusedTransportFactory : NaviampConnectTransportFactory {
    override suspend fun connect(host: String, port: Int): NaviampConnectTransportConnection = error("unused")
    override fun listen(port: Int): NaviampConnectTransportListener = error("unused")
}

internal object FakeIdentity : NaviampConnectDeviceIdentityEffect {
    override fun loadOrCreate() = NaviampConnectDeviceIdentity("target-id", "fingerprint", "public-key")
    override fun sign(payload: ByteArray) = byteArrayOf(1)
}

internal object FakeIdentityVerifier : NaviampConnectIdentityVerifier {
    override fun fingerprint(publicKeyBase64: String) = "fingerprint"
    override fun verify(publicKeyBase64: String, payload: ByteArray, signature: ByteArray) = true
}

internal object UnusedPakeFactory : NaviampConnectPakeFactory {
    override fun create(
        pairingSessionId: String,
        protocolVersion: Int,
        localRole: NaviampConnectPakeRole,
        localDeviceId: String,
        remoteDeviceId: String,
        pairingCode: CharArray,
    ): NaviampConnectPakeSession = error("unused")
}

internal object UnusedCipherFactory : NaviampConnectAuthenticatedCipherFactory {
    override fun create(
        sessionSecret: NaviampConnectSessionSecret,
        role: NaviampConnectPakeRole,
        protocolVersion: Int,
        sessionId: String,
    ): NaviampConnectAuthenticatedCipher = error("unused")
}

private class SetupSourcePort(private val password: String, private val suspendExport: Boolean = false) : NaviampCoreProviderSessionPort {
    var cancelled = false
    override fun currentSourceId() = "source"
    override suspend fun currentProvisioningConnection(): NaviampCoreEditableConnection {
        if (suspendExport) try { awaitCancellation() } finally { cancelled = true }
        return editableConnection("source")
    }
    override suspend fun editableConnection(id: String) = NaviampCoreEditableConnection(
        app.naviamp.domain.settings.ConnectionFormState(serverUrl = "https://fixture.invalid", username = "fixture", password = password))
    override suspend fun connect(request: NaviampCoreConnectionRequest, plan: app.naviamp.app.NaviampConnectionAttemptPlan): NaviampCoreConnectedSession = error("unused")
    override suspend fun deleteConnection(id: String) = NaviampCoreConnectionInventory()
    override suspend fun smartPlaylistProvider(password: String?) = null
    override suspend fun refreshActiveSession() = true
    override suspend fun persistActiveSession() = Unit
    override suspend fun clearActiveSession() = Unit
}
