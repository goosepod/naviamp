package app.naviamp.app

import app.naviamp.app.NaviampCastProto.bytes
import app.naviamp.app.NaviampCastProto.number
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCastChannelTest {
    @Test
    fun codecMatchesGoldenEnvelopeAndRejectsMalformedFrames() {
        val message = NaviampCastChannelMessage("s", "r", "n", text = "{}")
        val golden = byteArrayOf(8, 0, 18, 1, 115, 26, 1, 114, 34, 1, 110, 40, 0, 50, 2, 123, 125)
        assertContentEquals(golden, NaviampCastChannelCodec.encode(message))
        assertEquals(message, NaviampCastChannelCodec.decode(golden))
        assertEquals("日本語", NaviampCastChannelCodec.decode(NaviampCastChannelCodec.encode(message.copy(text = "日本語"))).text)
        listOf(
            byteArrayOf(), golden.copyOf(golden.size - 1), golden + byteArrayOf(8, 0),
            golden + byteArrayOf(64, 1), golden.copyOf().also { it[1] = 3 },
            golden + byteArrayOf(58, 0), byteArrayOf(0),
            ByteArray(NaviampCastChannelCodec.MaximumFrameBytes + 1),
            golden + byteArrayOf(80, -1, -1, -1, -1, -1, -1, -1, -1, -1, 2),
        ).forEach { assertFails { NaviampCastChannelCodec.decode(it) } }
        assertEquals(message, NaviampCastChannelCodec.decode(golden + byteArrayOf(80, 1)))
    }

    @Test
    fun authenticationRequiresNonceTrustedChainTlsBindingAndShortValidity() {
        val crypto = FakeCrypto()
        val auth = NaviampCastDeviceAuthentication(crypto, { 1_000 }, listOf(byteArrayOf(5)))
        val nonce = auth.nonce()
        val reply = authReply(nonce)
        assertTrue(auth.verify(reply, nonce, byteArrayOf(3)))
        assertContentEquals(nonce + byteArrayOf(3), crypto.signedData)
        assertFalse(auth.verify(reply, ByteArray(16) { 9 }, byteArrayOf(3)))
        crypto.chainTrusted = false
        assertFalse(auth.verify(reply, nonce, byteArrayOf(3)))
        crypto.chainTrusted = true
        crypto.signatureValid = false
        assertFalse(auth.verify(reply, nonce, byteArrayOf(3)))
        crypto.signatureValid = true
        crypto.dates = NaviampCastCertificateDates(0, 999)
        assertFalse(auth.verify(reply, nonce, byteArrayOf(3)))
        crypto.dates = NaviampCastCertificateDates(1_001, 10_000)
        assertFalse(auth.verify(reply, nonce, byteArrayOf(3)))
        crypto.dates = NaviampCastCertificateDates(0, 1_000 + 5 * 86_400_000L)
        assertFalse(auth.verify(reply, nonce, byteArrayOf(3)))
        assertFalse(auth.verify(byteArrayOf(26, 0), nonce, byteArrayOf(3)))
    }

    @Test
    fun senderLaunchesLoadsAndControlsOnlyAcknowledgedMedia() = runTest {
        val connection = FakeConnection()
        val listener = Listener()
        val sender = NaviampCastChannelSessionEffect(backgroundScope, NaviampCastTransportFactory { connection },
            NaviampCastDeviceAuthentication(FakeCrypto(), { 1_000 }), { testScheduler.currentTime })
        sender.start(listener)
        assertTrue(sender.connect(7, target))
        assertEquals(listOf(7L), listener.connected)
        assertEquals("192.0.2.10", sender.localAddress)
        assertTrue(sender.load(7, media))
        assertEquals(media.mediaUrl, listener.status.last().mediaUrl)
        assertEquals(2_000, listener.status.last().positionMillis)
        assertTrue(sender.command(7, NaviampCastReceiverCommand.Pause))
        assertTrue(sender.command(7, NaviampCastReceiverCommand.Seek(3_000)))
        assertTrue(sender.command(7, NaviampCastReceiverCommand.Volume(150)))
        val volume = connection.sent.mapNotNull { it.text?.let(Json::parseToJsonElement) as? JsonObject }
            .last { it["type"]?.jsonPrimitive?.content == "SET_VOLUME" }
        assertEquals(1.0, volume["volume"]!!.jsonObject["level"]!!.jsonPrimitive.double)
        assertFalse(sender.command(8, NaviampCastReceiverCommand.Play))
        connection.loadReply = "LOAD_FAILED"
        assertFalse(sender.load(7, media))
        sender.stop()
        assertTrue(connection.closed)
        assertFalse(sender.load(7, media))
    }

    @Test
    fun senderLaunchesAndControlsConfiguredReceiver() = runTest {
        val receiverId = "A1B2C3D4"
        val connection = FakeConnection().also { it.receiverApplicationId = receiverId }
        val listener = Listener()
        val sender = NaviampCastChannelSessionEffect(backgroundScope, NaviampCastTransportFactory { connection },
            NaviampCastDeviceAuthentication(FakeCrypto(), { 1_000 }), { testScheduler.currentTime },
            receiverApplicationId = receiverId)
        sender.start(listener)
        assertTrue(sender.connect(7, target))
        val launch = connection.sent.mapNotNull { it.text?.let(Json::parseToJsonElement) as? JsonObject }
            .single { it["type"]?.jsonPrimitive?.content == "LAUNCH" }
        assertEquals(receiverId, launch["appId"]?.jsonPrimitive?.content)
        assertEquals(listOf(7L), listener.connected)
        assertTrue(sender.load(7, media))
        assertTrue(sender.command(7, NaviampCastReceiverCommand.Pause))
        assertTrue(sender.command(7, NaviampCastReceiverCommand.Seek(3_000)))
        sender.disconnect()
        runCurrent()
        assertTrue(connection.closed)
    }

    @Test
    fun senderRejectsDifferentReceiverApplication() = runTest {
        val connection = FakeConnection()
        val listener = Listener()
        val sender = NaviampCastChannelSessionEffect(backgroundScope, NaviampCastTransportFactory { connection },
            NaviampCastDeviceAuthentication(FakeCrypto(), { 1_000 }), { testScheduler.currentTime },
            requestTimeoutMillis = 100, receiverApplicationId = "A1B2C3D4")
        sender.start(listener)
        assertFalse(sender.connect(7, target))
        assertTrue(listener.connected.isEmpty())
        assertTrue(connection.closed)
        assertFalse(sender.load(7, media))
        sender.stop()
    }

    @Test
    fun idleLoadEchoDoesNotAcceptPlaybackAuthority() = runTest {
        val connection = FakeConnection().also { it.playerState = "IDLE" }
        val sender = NaviampCastChannelSessionEffect(backgroundScope, NaviampCastTransportFactory { connection },
            NaviampCastDeviceAuthentication(FakeCrypto(), { 1_000 }), { testScheduler.currentTime }, requestTimeoutMillis = 100)
        sender.start(Listener())
        assertTrue(sender.connect(1, target))
        assertFalse(sender.load(1, media))
        sender.stop()
    }

    @Test
    fun failedAuthNeverLaunchesAndUnmatchedReplyCannotAcceptLoad() = runTest {
        val invalid = FakeConnection()
        val crypto = FakeCrypto().also { it.chainTrusted = false }
        val sender = NaviampCastChannelSessionEffect(backgroundScope, NaviampCastTransportFactory { invalid },
            NaviampCastDeviceAuthentication(crypto, { 1_000 }), { testScheduler.currentTime })
        sender.start(Listener())
        assertFalse(sender.connect(1, target))
        assertTrue(invalid.sent.all { it.namespace == NaviampCastChannelSessionEffect.Auth })
        assertTrue(invalid.closed)

        val connection = FakeConnection()
        val accepted = NaviampCastChannelSessionEffect(backgroundScope, NaviampCastTransportFactory { connection },
            NaviampCastDeviceAuthentication(FakeCrypto(), { 1_000 }), { testScheduler.currentTime }, requestTimeoutMillis = 100)
        accepted.start(Listener())
        assertTrue(accepted.connect(2, target))
        connection.wrongSource = true
        assertFalse(accepted.load(2, media))
        accepted.stop()
    }

    @Test
    fun disconnectStopsReceiverAndHeartbeatLossClosesConnection() = runTest {
        val connection = FakeConnection()
        val listener = Listener()
        val sender = NaviampCastChannelSessionEffect(backgroundScope, NaviampCastTransportFactory { connection },
            NaviampCastDeviceAuthentication(FakeCrypto(), { 1_000 }), { testScheduler.currentTime },
            heartbeatIntervalMillis = 10)
        sender.start(listener)
        assertTrue(sender.connect(1, target))
        sender.disconnect()
        runCurrent()
        assertTrue(connection.sent.any { it.namespace == NaviampCastChannelSessionEffect.ReceiverNamespace &&
            it.text?.contains("STOP") == true })
        assertTrue(connection.closed)
        assertTrue(listener.disconnected.isEmpty())

        val second = FakeConnection().also { it.answerHeartbeat = false }
        val lost = NaviampCastChannelSessionEffect(backgroundScope, NaviampCastTransportFactory { second },
            NaviampCastDeviceAuthentication(FakeCrypto(), { 1_000 }), { testScheduler.currentTime },
            heartbeatIntervalMillis = 10)
        lost.start(listener)
        assertTrue(lost.connect(2, target))
        testScheduler.advanceTimeBy(50)
        runCurrent()
        assertTrue(second.closed)
        assertEquals(listOf(2L), listener.disconnected)
    }

    private class Listener : NaviampCastSessionListener {
        val connected = mutableListOf<Long>()
        val disconnected = mutableListOf<Long>()
        val status = mutableListOf<NaviampCastReceiverStatus>()
        override fun onTargetSelected(target: NaviampCastTarget) = 1L
        override fun onConnecting(selectionId: Long) {}
        override fun onConnected(selectionId: Long, displayName: String) { connected += selectionId }
        override fun onStopped(selectionId: Long) {}
        override fun onDisconnected(selectionId: Long) { disconnected += selectionId }
        override fun onUnavailable(selectionId: Long) {}
        override fun onMediaStatus(selectionId: Long, status: NaviampCastReceiverStatus) { this.status += status }
    }

    private class FakeConnection : NaviampCastTransportConnection {
        override val peerCertificateDer = byteArrayOf(3)
        override val localAddress = "192.0.2.10"
        val sent = mutableListOf<NaviampCastChannelMessage>()
        val input = Channel<ByteArray>(Channel.UNLIMITED)
        var closed = false
        var loadReply = "MEDIA_STATUS"
        var wrongSource = false
        var answerHeartbeat = true
        var playerState = "PAUSED"
        var receiverApplicationId = NaviampCastReceiver.ApplicationId
        override suspend fun send(frame: ByteArray) {
            val message = NaviampCastChannelCodec.decode(frame)
            sent += message
            if (message.namespace == NaviampCastChannelSessionEffect.Auth) {
                input.send(NaviampCastChannelCodec.encode(NaviampCastChannelMessage("receiver-0", "sender-0",
                    message.namespace, binary = authReply(ByteArray(16) { 1 }))))
                return
            }
            val body = message.text?.let { Json.parseToJsonElement(it).jsonObject } ?: return
            val type = body["type"]?.jsonPrimitive?.content
            if (type == "CONNECT" || type == "PONG" || type == "PING" && !answerHeartbeat) return
            val receiver = message.namespace == NaviampCastChannelSessionEffect.ReceiverNamespace
            val payload = buildJsonObject {
                put("type", if (type == "PING") "PONG" else if (receiver) "RECEIVER_STATUS" else if (type == "LOAD") loadReply else "MEDIA_STATUS")
                body["requestId"]?.let { put("requestId", it) }
                if (receiver) put("status", buildJsonObject {
                    put("applications", buildJsonArray { add(buildJsonObject {
                        put("appId", receiverApplicationId); put("transportId", "app-1"); put("sessionId", "session-1")
                    }) })
                    put("volume", buildJsonObject { put("level", 0.5) })
                }) else put("status", buildJsonArray { add(buildJsonObject {
                    put("mediaSessionId", 12); put("playerState", playerState); put("currentTime", 2.0)
                    put("media", buildJsonObject { put("contentId", media.mediaUrl); put("duration", 60.0) })
                }) })
            }
            input.send(NaviampCastChannelCodec.encode(NaviampCastChannelMessage(
                if (wrongSource) "other-app" else message.destinationId, message.sourceId, message.namespace, text = payload.toString())))
        }
        override suspend fun receive() = input.receiveCatching().getOrNull()
        override fun close() { closed = true; input.close() }
    }

    private class FakeCrypto : NaviampCastCryptoEffect {
        var chainTrusted = true
        var signatureValid = true
        var dates = NaviampCastCertificateDates(0, 10_000)
        var signedData: ByteArray? = null
        override fun randomBytes(count: Int) = ByteArray(count) { 1 }
        override fun certificateDates(der: ByteArray) = dates
        override fun verifyCertificateChain(chain: List<ByteArray>, roots: List<ByteArray>, atEpochMillis: Long) = chainTrusted
        override fun verifySha256Rsa(certificate: ByteArray, signature: ByteArray, data: ByteArray): Boolean {
            signedData = data; return signatureValid
        }
    }

    companion object {
        private val target = NaviampCastDiscoveredTarget(NaviampCastTarget("tv", "Onn"), listOf(NaviampCastEndpoint("192.0.2.1", 8009)))
        private val media = NaviampCastReceiverMedia("http://192.0.2.10/media/opaque", "audio/mpeg", "Track", "Artist", "Album",
            "http://192.0.2.10/media/artwork", 60_000, 2_000, false)
        private fun authReply(nonce: ByteArray) = NaviampCastProto.encode(bytes(2, NaviampCastProto.encode(
            bytes(1, byteArrayOf(1)), bytes(2, byteArrayOf(2)), number(4, 1), bytes(5, nonce), number(6, 1),
        )))
    }
}
