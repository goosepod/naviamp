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
    fun manualTrustedReconnectUsesChangedAddressWithoutDiscoveryOrAutomaticRetry() = runTest {
        var storedTrust: String? = null
        val trust = NaviampConnectTrustRepository(object : NaviampConnectTrustStorageEffect {
            override fun read() = storedTrust
            override fun write(value: String) { storedTrust = value }
        })
        val target = NaviampConnectDevice("tv", "Living Room TV", NaviampConnectDeviceRole.Target)
        trust.upsert(NaviampConnectTrustRecord("trusted-tv", target, "fingerprint", "public-key", 1L))
        val credentials = app.naviamp.app.NaviampConnectSessionCredentialRepository(
            object : app.naviamp.app.NaviampConnectSessionCredentialStorageEffect {
                override fun read(peerDeviceId: String) = byteArrayOf(1, 2, 3)
                override fun write(peerDeviceId: String, value: ByteArray) = Unit
                override fun remove(peerDeviceId: String) = Unit
                override fun contains(peerDeviceId: String) = true
            },
        )
        val discovery = RecordingDiscoveryEffect()
        val attemptedAddresses = mutableListOf<Pair<String, Int>>()
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreConnectController(this, store, NaviampCoreConnectServices(
            networkDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            deviceCapabilities = setOf(NaviampConnectDeviceCapability.ControlPlayback),
            displayName = "Pixel", identity = FakeIdentity, identityVerifier = FakeIdentityVerifier,
            transport = object : NaviampConnectTransportFactory {
                override suspend fun connect(host: String, port: Int): NaviampConnectTransportConnection {
                    attemptedAddresses += host to port
                    error("unreachable")
                }
                override fun listen(port: Int): NaviampConnectTransportListener = error("unused")
            },
            pake = UnusedPakeFactory, cipher = UnusedCipherFactory,
            trust = trust, credentials = credentials, discovery = discovery,
            newOpaqueId = { "opaque" }, newPairingCode = { "123456" },
            nowEpochMillis = { testScheduler.currentTime }, pairingHandshakeTimeoutMillis = 100L,
        ))
        val device = store.state.value.shell.connect.trustedDevices.single()
        controller.actions.onManualTrustedEndpointSelected(device, "missing-port")
        assertEquals(app.naviamp.ui.NaviampConnectStatusText.ManualEndpointInvalid,
            store.state.value.shell.connect.statusMessage?.text)
        controller.actions.onManualTrustedEndpointSelected(device, "100.101.102.103:45678")
        runCurrent()
        assertEquals(listOf("100.101.102.103" to 45678), attemptedAddresses)
        assertEquals(0, discovery.startCount)
        assertEquals(app.naviamp.ui.NaviampConnectStatusText.ManualEndpointUnreachable,
            store.state.value.shell.connect.statusMessage?.text)
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(1, attemptedAddresses.size)
        controller.close()
    }

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

    private fun secureStorageFixture(
        scope: CoroutineScope,
        store: NaviampCoreStateStore,
        identity: NaviampConnectDeviceIdentityEffect,
        identityDispatcher: kotlinx.coroutines.CoroutineDispatcher,
        listeners: MutableList<WaitingListener>,
        now: () -> Long,
    ) = NaviampCoreConnectController(scope, store, NaviampCoreConnectServices(
        networkDispatcher = identityDispatcher,
        deviceCapabilities = NaviampCorePlaybackTargetConnectCapabilities, displayName = "TV",
        identity = identity, identityVerifier = FakeIdentityVerifier,
        transport = object : NaviampConnectTransportFactory {
            override suspend fun connect(host: String, port: Int) = error("unused")
            override fun listen(port: Int) = WaitingListener(42424).also { listeners += it }
        },
        pake = UnusedPakeFactory, cipher = UnusedCipherFactory,
        trust = NaviampConnectTrustRepository(NaviampConnectTrustStorageEffect {}),
        advertising = object : NaviampConnectAdvertisingEffect {
            override fun start(service: NaviampConnectRegistrationService, listener: NaviampConnectAdvertisingListener)
                : NaviampConnectAdvertisingStartResult {
                listener.onServiceRegistered(service.serviceName)
                return NaviampConnectAdvertisingStartResult.Started
            }
            override fun stop() = Unit
        },
        newOpaqueId = { "opaque" }, newPairingCode = { "123456" },
        nowEpochMillis = now,
    ))

    @Test
    fun constructionDoesNotWaitForASlowSecureStoreAndPairingStartsOnceTheIdentityLoads() = runTest {
        var loads = 0
        val identity = object : NaviampConnectDeviceIdentityEffect {
            override fun loadOrCreate(): NaviampConnectDeviceIdentity { loads++; return FakeIdentity.loadOrCreate() }
            override fun sign(payload: ByteArray) = byteArrayOf(1)
        }
        val store = NaviampCoreStateStore()
        val listeners = mutableListOf<WaitingListener>()
        // The secure store runs on its own dispatcher; nothing on the caller's thread may wait for it.
        val secureStore = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val controller = secureStorageFixture(this, store, identity, secureStore, listeners) { testScheduler.currentTime }
        assertEquals(0, loads, "constructing Connect must not touch the secure store on the caller's thread")
        controller.actions.onStartPairingMode()
        assertTrue(listeners.isEmpty(), "pairing waits for the identity instead of blocking")
        assertNull(store.state.value.shell.connect.recovery)
        runCurrent()
        assertEquals(1, loads)
        assertEquals(1, listeners.size)
        assertEquals(NaviampConnectPairingUiPhase.Advertising, store.state.value.shell.connect.pairingPhase)
        controller.close()
    }

    @Test
    fun unavailableSecureStoreIsARetryableRecoveryProblemInsteadOfACrash() = runTest {
        var locked = true
        var loads = 0
        val identity = object : NaviampConnectDeviceIdentityEffect {
            override fun loadOrCreate(): NaviampConnectDeviceIdentity {
                loads++
                if (locked) throw IllegalStateException("Secure credential service failed with exit code 1")
                return FakeIdentity.loadOrCreate()
            }
            override fun sign(payload: ByteArray) = byteArrayOf(1)
        }
        val store = NaviampCoreStateStore()
        val listeners = mutableListOf<WaitingListener>()
        val controller = secureStorageFixture(this, store, identity, kotlinx.coroutines.Dispatchers.Unconfined, listeners) {
            testScheduler.currentTime
        }
        runCurrent()
        assertEquals(app.naviamp.ui.NaviampConnectRecoveryProblem.SecureStorageUnavailable,
            store.state.value.shell.connect.recovery?.problem)
        controller.actions.onStartPairingMode()
        runCurrent()
        assertTrue(listeners.isEmpty(), "pairing must not start without a device identity")
        locked = false
        controller.actions.onRetryConnection()
        runCurrent()
        assertEquals(2, loads)
        assertNull(store.state.value.shell.connect.recovery)
        controller.actions.onStartPairingMode()
        runCurrent()
        assertEquals(1, listeners.size)
        assertEquals(NaviampConnectPairingUiPhase.Advertising, store.state.value.shell.connect.pairingPhase)
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
        controller.actions.onConnectScreenVisibilityChanged(true)
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
    fun trustedTargetListensWithoutShowingACode() = runTest {
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

        val service = assertNotNull(advertising.service)
        assertEquals("0", service.textAttributes["code"], "Trusted presence must not offer a code")
        assertNull(store.state.value.shell.connect.pairingCode)
        assertFalse(store.state.value.shell.connect.codeVisible)
        controller.ensureTrustedTargetListener()
        assertEquals(1, listenCount)
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(1, listenCount)
        assertNull(store.state.value.shell.connect.pairingCode, "No background code after the old lifetime")
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
        assertEquals(app.naviamp.ui.NaviampConnectStatusText.LookingForDeviceStartPairingModeOnTheTvIfNeeded, store.state.value.shell.connect.statusMessage?.text)
        controller.actions.onConnectScreenVisibilityChanged(true)
        assertEquals(0, discovery.stopCount, "Opening the connect panel must not restart a running discovery")
        assertEquals(1, discovery.startCount)
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
        controller.actions.onConnectScreenVisibilityChanged(true)
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(3, connectCount, "Opening the connect panel must not cancel an automatic reconnect")
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
            app.naviamp.ui.NaviampConnectStatusText.StoppedControllingDeviceTheTvWillKeepPlaying,
            store.state.value.shell.connect.statusMessage?.text,
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
        assertEquals(app.naviamp.ui.NaviampConnectStatusText.Reconnecting, store.state.value.shell.connect.statusMessage?.text)
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
            app.naviamp.ui.NaviampConnectStatusText.StartSomethingBeforeSendingQueue,
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
        assertEquals(app.naviamp.ui.NaviampConnectStatusText.IncompleteConnectionRequestTimedOut, store.state.value.shell.connect.statusMessage?.text)
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
    fun visibleCodeRegeneratesOnExpiry() = runTest {
        val listener = WaitingListener(42425)
        val store = NaviampCoreStateStore()
        val codes = sequenceOf("111111", "222222").iterator()
        val controller = targetController(
            listener = listener,
            store = store,
            nowEpochMillis = { testScheduler.currentTime + 1_000L },
            pairingHelloTimeoutMillis = 10_000L,
            pairingLifetimeMillis = 1_000L,
            newPairingCode = codes::next,
        )

        controller.actions.onStartPairingMode()
        runCurrent()
        assertEquals("111111", store.state.value.shell.connect.pairingCode)
        assertTrue(store.state.value.shell.connect.codeVisible)
        assertEquals(2_000L, store.state.value.shell.connect.pairingCodeExpiresAtEpochMillis)
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals("222222", store.state.value.shell.connect.pairingCode)
        assertFalse(listener.closed)
        controller.actions.onStopPairingMode()
        assertNull(store.state.value.shell.connect.pairingCode)
        assertFalse(store.state.value.shell.connect.codeVisible)
        assertTrue(listener.closed, "Without trust, hiding the code closes the listener")
        controller.close()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun pairingRequestWithoutVisibleCodeIsClosed() = runTest {
        var storedTrust: String? = null
        val trust = NaviampConnectTrustRepository(object : NaviampConnectTrustStorageEffect {
            override fun read() = storedTrust
            override fun write(value: String) { storedTrust = value }
        })
        trust.upsert(NaviampConnectTrustRecord("trusted-phone",
            NaviampConnectDevice("phone", "Pixel", NaviampConnectDeviceRole.Controller), "phone-fp", "phone-key", 1L))
        val listener = QueuedListener(42425)
        val stranger = ScriptedConnection(app.naviamp.domain.connect.NaviampConnectEnvelope(
            protocolVersion = 1,
            sequence = 0,
            message = app.naviamp.domain.connect.NaviampConnectHello(
                device = NaviampConnectDevice("stranger", "Stranger", NaviampConnectDeviceRole.Controller),
                identity = app.naviamp.domain.connect.NaviampConnectPublicIdentity("stranger", "stranger-fp", "stranger-key"),
                protocolRange = NaviampConnectProtocolRange(),
                capabilities = emptySet(),
            ),
        ))
        listener.connections.trySend(stranger)
        val store = NaviampCoreStateStore()
        val controller = targetController(
            listener = listener,
            store = store,
            nowEpochMillis = { testScheduler.currentTime + 1_000L },
            pairingHelloTimeoutMillis = 1_000L,
            pairingLifetimeMillis = 10_000L,
            trust = trust,
        )
        runCurrent()

        assertTrue(stranger.closed)
        assertEquals(2, listener.acceptCount)
        assertFalse(listener.closed)
        assertEquals(NaviampConnectPairingUiPhase.Inactive, store.state.value.shell.connect.pairingPhase)
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

        controller.actions.onConnectScreenVisibilityChanged(true)
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
    fun remoteAlbumTrackSelectionSendsTheChosenTrackWithinItsAlbum() = runTest {
        val sent = mutableListOf<app.naviamp.domain.connect.NaviampConnectEnvelope>()
        val registry = NaviampCoreMediaRegistry()
        val tracks = (1..4).map { n ->
            app.naviamp.domain.Track(id = app.naviamp.domain.TrackId("album-$n"), title = "Track $n", artistName = "Artist",
                albumTitle = "Album", durationSeconds = 180, coverArtId = null, audioInfo = null, replayGain = null)
        }
        registry.updateAlbum(app.naviamp.domain.AlbumDetails(app.naviamp.domain.Album(app.naviamp.domain.AlbumId("album"),
            "Album", "Artist", coverArtId = null, recentlyAddedAtIso8601 = null), tracks))
        val identity = app.naviamp.domain.connect.NaviampConnectSourceIdentity("navidrome", "https://music.example", "listener")
        val (controller, _, _) = remoteFixture(sourceIdentity = { identity },
            trackSelectionContext = registry::connectTrackSelectionContext) { sent += it }

        assertTrue(controller.routeProductCommand(NaviampCoreCommand.Detail.AlbumTrack(
            app.naviamp.ui.SharedTrackRowActionRequest(app.naviamp.ui.SharedTrackRowUi("album-3", "Track 3", "Artist"),
                app.naviamp.ui.SharedTrackRowAction.Select))))
        runCurrent()

        val start = sent.map { it.message }.filterIsInstance<app.naviamp.domain.connect.NaviampConnectCommandRequest>()
            .map { it.command }.filterIsInstance<app.naviamp.domain.connect.NaviampConnectStartMedia>().single()
        assertEquals(app.naviamp.domain.connect.NaviampConnectMediaType.Track, start.mediaType)
        assertEquals("album-3", start.mediaId)
        assertEquals(app.naviamp.domain.connect.NaviampConnectPlaybackContext(
            app.naviamp.domain.connect.NaviampConnectMediaType.Album, "album"), start.context)
        assertEquals(identity, start.sourceIdentity)
        controller.close()
    }

    @Test
    fun homeMixOnARemoteTargetIsSentAsSeedTrackRadioAndNeverAsTheAlbum() = runTest {
        val sent = mutableListOf<app.naviamp.domain.connect.NaviampConnectEnvelope>()
        val identity = app.naviamp.domain.connect.NaviampConnectSourceIdentity("navidrome", "https://music.test", "me")
        val (controller, _, _) = remoteFixture(sourceIdentity = { identity },
            extraCapabilities = setOf(app.naviamp.domain.connect.NaviampConnectCapability.CatalogPlayback)) { sent += it }
        val local = mutableListOf<NaviampCoreCommand>()
        val handler = NaviampCoreConnectCommandHandler(object : NaviampCoreCommandHandler {
            override fun dispatch(command: NaviampCoreCommand) { local += command }
            override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult {
                local += command
                return NaviampCoreCommandResult.Completed
            }
        }, controller)
        val album = app.naviamp.domain.Album(app.naviamp.domain.AlbumId("single"), "Single", "Artist", null, null)
        val seed = app.naviamp.domain.Track(
            id = app.naviamp.domain.TrackId("seed"), title = "Seed", artistName = "Artist", albumId = album.id,
            albumTitle = album.title, durationSeconds = 180, coverArtId = null, audioInfo = null, replayGain = null,
        )
        val tile = NaviampCoreCommand.Media.ItemAction(app.naviamp.ui.NaviampMediaItemActionRequest(
            app.naviamp.ui.SharedMediaItemUi("single", "Single", "Artist"), app.naviamp.ui.NaviampMediaItemCommand.PlayAlbumMix))

        // The tile is resolved locally first (seed lookup), then the resolved mix goes to the target.
        handler.execute(tile)
        handler.execute(NaviampCoreCommand.Home.StartAlbumMix(album, seed))
        runCurrent()

        assertEquals(listOf<NaviampCoreCommand>(tile), local)
        val started = sent.map { it.message }.filterIsInstance<app.naviamp.domain.connect.NaviampConnectCommandRequest>()
            .map { it.command }.filterIsInstance<app.naviamp.domain.connect.NaviampConnectStartMedia>().single()
        assertEquals(app.naviamp.domain.connect.NaviampConnectMediaType.Track, started.mediaType)
        assertEquals("seed", started.mediaId)
        assertTrue(started.startRadio)
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

    @Test fun companionTransferRequiresTargetSupportAndSendsTheIndependentAccount() = runTest {
        val companion = app.naviamp.domain.source.AurralCompanion(true, "https://aurral.example", "secondary", "companion-secret")
        for (supported in listOf(false, true)) {
            val sent = mutableListOf<app.naviamp.domain.connect.NaviampConnectEnvelope>()
            val (controller, store, _) = remoteFixture(providerSessions = SetupSourcePort("primary-secret", companion = companion),
                companionProvisioningSupported = supported) { sent += it }
            controller.actions.onProvisionTarget()
            runCurrent()
            if (supported) {
                val request = sent.single().message as app.naviamp.domain.connect.NaviampConnectCommandRequest
                val offer = request.command as app.naviamp.domain.connect.NaviampConnectOfferConnectionProvisioning
                assertEquals("companion-secret", offer.profile.aurralCompanion?.password)
                assertEquals("primary-secret", offer.profile.password)
            } else {
                assertTrue(sent.isEmpty(), "An older target must not silently receive only the primary connection")
                assertFalse(store.state.value.shell.connect.provisioningBusy)
            }
            controller.close()
        }
    }

    private fun kotlinx.coroutines.test.TestScope.remoteFixture(
        discovery: NaviampConnectDiscoveryEffect? = null,
        permissionSettings: app.naviamp.app.NaviampConnectPermissionSettingsEffect? = null,
        providerSessions: NaviampCoreProviderSessionPort? = null,
        companionProvisioningSupported: Boolean = false,
        sourceIdentity: () -> app.naviamp.domain.connect.NaviampConnectSourceIdentity? = { null },
        trackSelectionContext: (NaviampCoreCommand) -> app.naviamp.domain.connect.NaviampConnectPlaybackContext? = { null },
        extraCapabilities: Set<app.naviamp.domain.connect.NaviampConnectCapability> = emptySet(),
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
        ), providerSessions = providerSessions, sourceIdentity = sourceIdentity,
            trackSelectionContext = trackSelectionContext)
        if (discovery != null) controller.actions.onConnectScreenVisibilityChanged(true)
        var next = 0
        val session = NaviampConnectControllerSession(NaviampConnectSessionTransport(send),
            NaviampConnectRequestIdFactory { "request-${++next}" })
        session.connect("session", 1, target, setOf(app.naviamp.domain.connect.NaviampConnectCapability.TransportControls,
                app.naviamp.domain.connect.NaviampConnectCapability.ConnectionProvisioning,
                app.naviamp.domain.connect.NaviampConnectCapability.CatalogPlayback) + extraCapabilities,
            NaviampConnectTargetSnapshot(0, target, emptySet()))
        controller.adoptControllerSession(session, companionProvisioningSupported)
        return Triple(controller, store, session)
    }

    @Test
    fun openingConnectScreenStartsAndClosingStopsDiscovery() = runTest {
        val fixture = connectScreenFixture()
        fixture.controller.actions.onConnectScreenVisibilityChanged(true)
        assertEquals(1, fixture.discovery.startCount)
        assertTrue(fixture.store.state.value.shell.connect.connectScreenVisible)
        fixture.controller.actions.onConnectScreenVisibilityChanged(false)
        assertTrue(fixture.discovery.stopCount >= 1)
        assertFalse(fixture.store.state.value.shell.connect.connectScreenVisible)
        fixture.controller.close()
    }

    @Test
    fun closingConnectScreenKeepsDiscoveryForAPendingTrustedReconnect() = runTest {
        val fixture = connectScreenFixture(trustedTv = true)
        fixture.controller.actions.onTrustedDeviceSelected(fixture.store.state.value.shell.connect.trustedDevices.single())
        fixture.controller.actions.onConnectScreenVisibilityChanged(true)
        val stops = fixture.discovery.stopCount
        fixture.controller.actions.onConnectScreenVisibilityChanged(false)
        assertEquals(stops, fixture.discovery.stopCount, "A pending trusted reconnect still needs discovery")
        fixture.controller.close()
    }

    @Test
    fun sixthDigitSubmitsToTheOnlyDeviceShowingACode() = runTest {
        val fixture = connectScreenFixture()
        fixture.controller.actions.onConnectScreenVisibilityChanged(true)
        fixture.resolve("Living Room", "192.0.2.10", "tv-fp", pairable = true)
        fixture.resolve("Kitchen", "192.0.2.11", "kitchen-fp", pairable = false)
        runCurrent()
        assertEquals(listOf("Living Room"), fixture.store.state.value.shell.connect.discoveredTargets.map { it.displayName })
        fixture.controller.actions.onPairingCodeChanged("12345")
        runCurrent()
        assertTrue(fixture.attempts.isEmpty())
        fixture.controller.actions.onPairingCodeChanged("123456")
        runCurrent()
        assertEquals(listOf("192.0.2.10" to 42_425), fixture.attempts)
        fixture.controller.close()
    }

    @Test
    fun codeTypedBeforeDiscoveryWaitsForTheDevice() = runTest {
        val fixture = connectScreenFixture()
        fixture.controller.actions.onConnectScreenVisibilityChanged(true)
        fixture.controller.actions.onPairingCodeChanged("123456")
        advanceTimeBy(2_000L)
        runCurrent()
        assertTrue(fixture.attempts.isEmpty())
        fixture.resolve("Living Room", "192.0.2.10", "tv-fp", pairable = true)
        runCurrent()
        assertEquals(listOf("192.0.2.10" to 42_425), fixture.attempts)
        fixture.controller.close()
    }

    @Test
    fun codeTypedWithNoDeviceShowingACodeReportsItAfterWaiting() = runTest {
        val fixture = connectScreenFixture()
        fixture.controller.actions.onConnectScreenVisibilityChanged(true)
        fixture.controller.actions.onPairingCodeChanged("123456")
        advanceTimeBy(5_001L)
        runCurrent()
        assertTrue(fixture.attempts.isEmpty())
        assertEquals(app.naviamp.ui.NaviampConnectStatusText.NoDeviceShowingACode,
            fixture.store.state.value.shell.connect.statusMessage?.text)
        fixture.controller.close()
    }

    @Test
    fun multipleDevicesShowingCodesRequireAChoice() = runTest {
        val fixture = connectScreenFixture()
        fixture.controller.actions.onConnectScreenVisibilityChanged(true)
        fixture.resolve("Living Room", "192.0.2.10", "tv-fp", pairable = true)
        fixture.resolve("Bedroom", "192.0.2.12", "bedroom-fp", pairable = true)
        runCurrent()
        fixture.controller.actions.onPairingCodeChanged("123456")
        runCurrent()
        assertTrue(fixture.attempts.isEmpty())
        assertEquals(app.naviamp.ui.NaviampConnectStatusText.ChooseTheDeviceShowingThisCode,
            fixture.store.state.value.shell.connect.statusMessage?.text)
        assertEquals("123456", fixture.store.state.value.shell.connect.enteredPairingCode)
        fixture.controller.actions.onTargetSelected(
            fixture.store.state.value.shell.connect.discoveredTargets.single { it.displayName == "Bedroom" })
        runCurrent()
        assertEquals(listOf("192.0.2.12" to 42_425), fixture.attempts)
        fixture.controller.close()
    }

    private class ConnectScreenFixture(
        val controller: NaviampCoreConnectController,
        val store: NaviampCoreStateStore,
        val discovery: RecordingDiscoveryEffect,
        val attempts: MutableList<Pair<String, Int>>,
    ) {
        fun resolve(name: String, address: String, fingerprint: String, pairable: Boolean) {
            discovery.listener.onServiceResolved(NaviampConnectResolvedService(
                serviceName = name, addresses = listOf(address), port = 42_425,
                textAttributes = NaviampConnectDiscoveryMetadata.encode(NaviampConnectAdvertisement(
                    instanceId = "$fingerprint-instance", displayName = name,
                    protocolRange = NaviampConnectProtocolRange(), capabilities = emptySet(), port = 42_425,
                    identityFingerprint = fingerprint, expiresAtEpochMillis = Long.MAX_VALUE, pairable = pairable,
                )),
            ))
        }
    }

    private fun kotlinx.coroutines.test.TestScope.connectScreenFixture(trustedTv: Boolean = false): ConnectScreenFixture {
        var storedTrust: String? = null
        val trust = NaviampConnectTrustRepository(object : NaviampConnectTrustStorageEffect {
            override fun read() = storedTrust
            override fun write(value: String) { storedTrust = value }
        })
        if (trustedTv) trust.upsert(NaviampConnectTrustRecord("trusted-tv",
            NaviampConnectDevice("tv", "Living Room", NaviampConnectDeviceRole.Target), "tv-fp", "tv-key", 1L))
        val discovery = RecordingDiscoveryEffect()
        val attempts = mutableListOf<Pair<String, Int>>()
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreConnectController(this, store, NaviampCoreConnectServices(
            networkDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            deviceCapabilities = setOf(NaviampConnectDeviceCapability.ControlPlayback),
            displayName = "Pixel", identity = FakeIdentity, identityVerifier = FakeIdentityVerifier,
            transport = object : NaviampConnectTransportFactory {
                override suspend fun connect(host: String, port: Int): NaviampConnectTransportConnection {
                    attempts += host to port
                    error("unreachable")
                }
                override fun listen(port: Int): NaviampConnectTransportListener = error("unused")
            },
            pake = UnusedPakeFactory, cipher = UnusedCipherFactory, trust = trust,
            credentials = app.naviamp.app.NaviampConnectSessionCredentialRepository(
                object : app.naviamp.app.NaviampConnectSessionCredentialStorageEffect {
                    override fun read(peerDeviceId: String) = byteArrayOf(1, 2, 3)
                    override fun write(peerDeviceId: String, value: ByteArray) = Unit
                    override fun remove(peerDeviceId: String) = Unit
                    override fun contains(peerDeviceId: String) = true
                },
            ),
            discovery = discovery,
            newOpaqueId = { "opaque" }, newPairingCode = { "123456" },
            nowEpochMillis = { testScheduler.currentTime + 1_000L },
        ))
        return ConnectScreenFixture(controller, store, discovery, attempts)
    }

    private fun CoroutineScope.targetController(
        listener: NaviampConnectTransportListener,
        store: NaviampCoreStateStore,
        nowEpochMillis: () -> Long,
        pairingHelloTimeoutMillis: Long,
        pairingLifetimeMillis: Long,
        newPairingCode: () -> String = { "123456" },
        trust: NaviampConnectTrustRepository = NaviampConnectTrustRepository(NaviampConnectTrustStorageEffect {}),
        advertising: NaviampConnectAdvertisingEffect = RecordingAdvertisingEffect(),
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
            trust = trust,
            advertising = advertising,
            newOpaqueId = generateSequence(0) { it + 1 }.map { "opaque-$it" }.iterator()::next,
            newPairingCode = newPairingCode,
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

private class ScriptedConnection(envelope: app.naviamp.domain.connect.NaviampConnectEnvelope) : NaviampConnectTransportConnection {
    override val remoteAddress = "scripted.test"
    var closed = false
    private val frames = ArrayDeque(listOf(app.naviamp.app.NaviampConnectTransportPacketCodec.encode(
        app.naviamp.app.NaviampConnectTransportPacket.Plaintext(envelope))))

    override suspend fun send(frame: ByteArray) = Unit
    override suspend fun receive(): ByteArray? = if (closed) null else frames.removeFirstOrNull() ?: awaitCancellation()
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

private class SetupSourcePort(private val password: String, private val suspendExport: Boolean = false,
    private val companion: app.naviamp.domain.source.AurralCompanion? = null) : NaviampCoreProviderSessionPort {
    var cancelled = false
    override fun currentSourceId() = "source"
    override suspend fun currentProvisioningConnection(): NaviampCoreEditableConnection {
        if (suspendExport) try { awaitCancellation() } finally { cancelled = true }
        return editableConnection("source")
    }
    override suspend fun editableConnection(id: String) = NaviampCoreEditableConnection(
        app.naviamp.domain.settings.ConnectionFormState(serverUrl = "https://fixture.invalid", username = "fixture", password = password,
            aurralCompanion = companion))
    override suspend fun connect(request: NaviampCoreConnectionRequest, plan: app.naviamp.app.NaviampConnectionAttemptPlan): NaviampCoreConnectedSession = error("unused")
    override suspend fun deleteConnection(id: String) = NaviampCoreConnectionInventory()
    override suspend fun smartPlaylistProvider(password: String?) = null
    override suspend fun refreshActiveSession() = true
    override suspend fun persistActiveSession() = Unit
    override suspend fun clearActiveSession() = Unit
}
