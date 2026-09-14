package app.naviamp.presentation

import app.naviamp.app.*
import app.naviamp.domain.connect.*
import app.naviamp.ui.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Exercises two complete Core compositions with in-memory native transport/crypto effects. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NaviampCoreConnectPeerTest {
    @Test
    fun incomingControllerReclaimsLocalOutputAndCancelsPreviousReconnect() = reversePeers(disconnectFirst = true)

    @Test
    fun incomingControllerReplacesAnActiveOutgoingSessionWithoutChangingTheLocalQueue() = reversePeers(disconnectFirst = false)

    @Test
    fun pairingStopRenewalAndExpiryPreserveTheLiveSessionAndPlaybackUpdates() = runTest {
        val network = ConnectTestNetwork()
        val phone = connectPeer("phone", "mac", network)
        val mac = connectPeer("mac", "phone", network, configure = { it.copy(pairingLifetimeMillis = 10_000) })
        try {
            runCurrent()
            phone.actions.shell.connectActions!!.onTrustedDeviceSelected(phone.state.value.shell.connect.trustedDevices.single())
            runCurrent()
            repeat(3) { pass ->
                when (pass) {
                    0 -> mac.actions.shell.connectActions!!.onStartPairingMode()
                    1 -> mac.actions.shell.connectActions!!.onStopPairingMode()
                    2 -> mac.actions.shell.connectActions!!.onStartPairingMode()
                }
                mac.actions.shell.connectActions!!.onRefreshTargets()
                phone.actions.shell.connectActions!!.onRefreshTargets()
                runCurrent()
                advanceTimeBy(21_000)
                runCurrent()
                val track = app.naviamp.domain.Track(id = app.naviamp.domain.TrackId("track-$pass"), title = "Track $pass",
                    artistName = "Artist", albumTitle = "Album", durationSeconds = 180, coverArtId = null, audioInfo = null, replayGain = null)
                mac.updateLivePlayback { it.copy(currentTrack = track, queue = app.naviamp.domain.queue.PlaybackQueue(listOf(track), 0)) }
                advanceTimeBy(1_000)
                runCurrent()
                assertEquals("mac", phone.state.value.shell.connect.connectedTargetName)
                assertEquals("phone", mac.state.value.shell.connect.connectedControllerName)
                assertEquals("Track $pass", phone.state.value.shell.connect.remoteTrackTitle)
                assertEquals(1, network.connectCount, "Pairing lifecycle must not require a reconnect")
            }
            assertTrue(network.advertisingStarts.getValue("mac") >= 5, "Expiry must exercise advertisement renewal")
        } finally { phone.close(); mac.close(); runCurrent() }
    }

    private fun reversePeers(disconnectFirst: Boolean) = runTest {
        val network = ConnectTestNetwork()
        val phone = connectPeer("phone", "mac", network)
        val mac = connectPeer("mac", "phone", network)
        try {
            val track = app.naviamp.domain.Track(id = app.naviamp.domain.TrackId("local"), title = "Local track", artistName = "Artist", albumTitle = "Album",
                durationSeconds = 180, coverArtId = null, audioInfo = null, replayGain = null)
            phone.updateLivePlayback { it.copy(currentTrack = track, queue = app.naviamp.domain.queue.PlaybackQueue(listOf(track), 0)) }
            runCurrent()
            phone.actions.shell.connectActions!!.onTrustedDeviceSelected(phone.state.value.shell.connect.trustedDevices.single())
            runCurrent()
            assertEquals("mac", phone.state.value.shell.connect.connectedTargetName)
            assertEquals(NaviampConnectPlaybackDestinationUiStatus.Connected, phone.state.value.shell.connect.playbackDestinationStatus)
            if (disconnectFirst) {
                network.disconnect()
                runCurrent()
                assertEquals(NaviampConnectPlaybackDestinationUiStatus.Reconnecting, phone.state.value.shell.connect.playbackDestinationStatus)
            }

            mac.actions.shell.connectActions!!.onTrustedDeviceSelected(mac.state.value.shell.connect.trustedDevices.single())
            runCurrent()
            assertEquals("mac", phone.state.value.shell.connect.connectedControllerName)
            assertEquals(NaviampConnectPlaybackDestinationUiStatus.Local, phone.state.value.shell.connect.playbackDestinationStatus)
            assertNull(phone.state.value.shell.connect.selectedPlaybackDeviceId)
            assertNull(phone.state.value.shell.connect.selectedPlaybackDeviceName)
            assertNull(phone.state.value.shell.connect.remoteNowPlaying)
            assertEquals("Local track", phone.state.value.shell.nowPlaying?.title)
            assertEquals("Local track", mac.state.value.shell.connect.remoteTrackTitle)
            advanceTimeBy(20_000)
            runCurrent()
            assertEquals("phone", mac.state.value.shell.connect.connectedTargetName)
            assertEquals("mac", phone.state.value.shell.connect.connectedControllerName)
            assertEquals(2, network.connectCount, "The previous outgoing destination must not reconnect over the incoming session")
            mac.actions.shell.connectActions!!.onStopControlling()
            runCurrent()
            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(NaviampConnectPlaybackDestinationUiStatus.Local, phone.state.value.shell.connect.playbackDestinationStatus)
            assertEquals(2, network.connectCount)
            assertEquals(1, phone.state.value.shell.connect.trustedDevices.size)
        } finally { phone.close(); mac.close() }
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal fun TestScope.connectPeer(
    id: String, peerId: String, network: ConnectTestNetwork,
    source: NaviampCoreProviderSessionPort? = null,
    preTrusted: Boolean = true,
    configure: (NaviampCoreConnectServices) -> NaviampCoreConnectServices = { it },
): NaviampCore {
    var storedTrust: String? = null
    val trust = NaviampConnectTrustRepository(object : NaviampConnectTrustStorageEffect {
        override fun read() = storedTrust
        override fun write(value: String) { storedTrust = value }
    })
    val peer = NaviampConnectDevice(peerId, peerId, NaviampConnectDeviceRole.Target,
        deviceCapabilities = NaviampCoreBidirectionalConnectCapabilities)
    if (preTrusted) trust.upsert(NaviampConnectTrustRecord("trusted-$peerId", peer, peerId, peerId, 1L))
    val secrets = mutableMapOf<String, ByteArray>()
    if (preTrusted) secrets[peerId] = ByteArray(32) { 1 }
    val credentials = NaviampConnectSessionCredentialRepository(object : NaviampConnectSessionCredentialStorageEffect {
        override fun read(peerDeviceId: String) = secrets[peerDeviceId]?.copyOf()
        override fun write(peerDeviceId: String, value: ByteArray) { secrets[peerDeviceId] = value.copyOf() }
        override fun remove(peerDeviceId: String) { secrets.remove(peerDeviceId)?.fill(0) }
    })
    var opaqueId = 0
    val base = fakeCoreServices()
    return NaviampCore.create(this, base.copy(connection = source ?: base.connection,
        connect = configure(NaviampCoreConnectServices(
        networkDispatcher = StandardTestDispatcher(testScheduler),
        deviceCapabilities = NaviampCoreBidirectionalConnectCapabilities, displayName = id,
        identity = object : NaviampConnectDeviceIdentityEffect {
            override fun loadOrCreate() = NaviampConnectDeviceIdentity(id, id, id)
            override fun sign(payload: ByteArray) = payload.copyOf()
        },
        identityVerifier = object : NaviampConnectIdentityVerifier {
            override fun fingerprint(publicKeyBase64: String) = publicKeyBase64
            override fun verify(publicKeyBase64: String, payload: ByteArray, signature: ByteArray) = payload.contentEquals(signature)
        },
        trust = trust, credentials = credentials,
        transport = network.transport(id), discovery = network.discovery(), advertising = network.advertising(id),
        pake = object : NaviampConnectPakeFactory {
            override fun create(pairingSessionId: String, protocolVersion: Int, localRole: NaviampConnectPakeRole,
                localDeviceId: String, remoteDeviceId: String, pairingCode: CharArray): NaviampConnectPakeSession = error("Trust is pre-established")
        },
        cipher = object : NaviampConnectAuthenticatedCipherFactory {
            override fun create(sessionSecret: NaviampConnectSessionSecret, role: NaviampConnectPakeRole,
                protocolVersion: Int, sessionId: String): NaviampConnectAuthenticatedCipher {
                sessionSecret.destroy()
                return object : NaviampConnectAuthenticatedCipher {
                    override fun seal(sequence: Long, plaintext: ByteArray, authenticatedData: ByteArray) = plaintext.copyOf()
                    override fun open(sequence: Long, ciphertext: ByteArray, authenticatedData: ByteArray) = ciphertext.copyOf()
                    override fun destroy() = Unit
                }
            }
        },
        newOpaqueId = { "$id-${++opaqueId}" }, newPairingCode = { "123456" }, nowEpochMillis = { testScheduler.currentTime },
    ))), initialState = NaviampCoreInitialState(connectionInventory = source?.initialInventory() ?: NaviampCoreConnectionInventory()))
}

internal class ConnectTestNetwork {
    val advertisingStarts = mutableMapOf<String, Int>()
    val advertisingStops = mutableMapOf<String, Int>()
    private val listeners = mutableMapOf<String, Channel<NaviampConnectTransportConnection>>()
    private val advertisements = mutableMapOf<String, NaviampConnectRegistrationService>()
    private val discoveries = mutableSetOf<NaviampConnectDiscoveryListener>()
    private val connections = mutableListOf<NaviampConnectTransportConnection>()
    var connectCount = 0
        private set

    fun disconnect() { connections.toList().forEach { it.close() }; connections.clear() }
    fun transport(id: String) = object : NaviampConnectTransportFactory {
        override suspend fun connect(host: String, port: Int): NaviampConnectTransportConnection {
            connectCount++
            val outgoing = Channel<ByteArray>(Channel.UNLIMITED)
            val incoming = Channel<ByteArray>(Channel.UNLIMITED)
            fun endpoint(remote: String, reads: Channel<ByteArray>, writes: Channel<ByteArray>) = object : NaviampConnectTransportConnection {
                override val remoteAddress = remote
                override suspend fun send(frame: ByteArray) { writes.send(frame.copyOf()) }
                override suspend fun receive() = reads.receiveCatching().getOrNull()
                override fun close() { reads.close(); writes.close() }
            }
            val local = endpoint(host, incoming, outgoing)
            connections += local
            listeners.getValue(host).send(endpoint(id, outgoing, incoming))
            return local
        }
        override fun listen(port: Int): NaviampConnectTransportListener {
            val incoming = Channel<NaviampConnectTransportConnection>(Channel.UNLIMITED)
            listeners[id] = incoming
            return object : NaviampConnectTransportListener {
                override val port = 42424
                override suspend fun accept() = incoming.receive()
                override fun close() { incoming.close() }
            }
        }
    }
    fun advertising(id: String) = object : NaviampConnectAdvertisingEffect {
        override fun start(service: NaviampConnectRegistrationService, listener: NaviampConnectAdvertisingListener): NaviampConnectAdvertisingStartResult {
            advertisingStarts[id] = (advertisingStarts[id] ?: 0) + 1
            advertisements[id] = service
            listener.onServiceRegistered(service.serviceName)
            discoveries.toList().forEach { it.onServiceResolved(resolved(id, service)) }
            return NaviampConnectAdvertisingStartResult.Started
        }
        override fun stop() {
            advertisingStops[id] = (advertisingStops[id] ?: 0) + 1
            advertisements.remove(id)
        }
    }
    fun discovery() = object : NaviampConnectDiscoveryEffect {
        private var listener: NaviampConnectDiscoveryListener? = null
        override fun start(listener: NaviampConnectDiscoveryListener): NaviampConnectDiscoveryStartResult {
            this.listener = listener
            discoveries += listener
            advertisements.forEach { (id, service) -> listener.onServiceResolved(resolved(id, service)) }
            return NaviampConnectDiscoveryStartResult.Started
        }
        override fun stop() { listener?.let { discoveries.remove(it) }; listener = null }
    }
    private fun resolved(id: String, service: NaviampConnectRegistrationService) = NaviampConnectResolvedService(
        serviceName = service.serviceName,
        addresses = listOf(id), port = service.port, textAttributes = service.textAttributes,
    )
}
