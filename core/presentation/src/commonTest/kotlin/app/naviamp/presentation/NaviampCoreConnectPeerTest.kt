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

internal fun TestScope.connectPeer(id: String, peerId: String, network: ConnectTestNetwork): NaviampCore {
    var storedTrust: String? = null
    val trust = NaviampConnectTrustRepository(object : NaviampConnectTrustStorageEffect {
        override fun read() = storedTrust
        override fun write(value: String) { storedTrust = value }
    })
    val peer = NaviampConnectDevice(peerId, peerId, NaviampConnectDeviceRole.Target,
        deviceCapabilities = NaviampCoreBidirectionalConnectCapabilities)
    trust.upsert(NaviampConnectTrustRecord("trusted-$peerId", peer, peerId, peerId, 1L))
    val credentials = NaviampConnectSessionCredentialRepository(object : NaviampConnectSessionCredentialStorageEffect {
        override fun read(peerDeviceId: String) = ByteArray(32) { 1 }
        override fun write(peerDeviceId: String, value: ByteArray) = Unit
        override fun remove(peerDeviceId: String) = Unit
    })
    var opaqueId = 0
    return NaviampCore.create(this, fakeCoreServices().copy(connect = NaviampCoreConnectServices(
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
    )))
}

internal class ConnectTestNetwork {
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
            advertisements[id] = service
            listener.onServiceRegistered(service.serviceName)
            discoveries.toList().forEach { it.onServiceResolved(resolved(id, service)) }
            return NaviampConnectAdvertisingStartResult.Started
        }
        override fun stop() { advertisements.remove(id) }
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
