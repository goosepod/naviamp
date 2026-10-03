package app.naviamp.desktop.cast

import app.naviamp.app.*
import app.naviamp.domain.provider.ProviderMediaByteResponse
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertTrue

/** Opt-in real LAN probe. No provider credentials or media URLs are printed. */
class DesktopNaviampCastReceiverProbeTest {
    @Test
    fun discoverAndOptionallyAuthenticateReceiver() = runBlocking {
        val selectedName = System.getenv("NAVIAMP_CAST_PROBE") ?: return@runBlocking
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val native = DesktopNaviampCastDiscoveryEffect(scope)
        val models = mutableMapOf<String, String?>()
        val discovery = NaviampCastDiscoveryController(object : NaviampCastDiscoveryEffect {
            override fun start(serviceType: String, listener: NaviampCastDiscoveryListener) {
                native.start(serviceType, object : NaviampCastDiscoveryListener by listener {
                    override fun onServiceResolved(service: NaviampCastResolvedService) {
                        service.textAttributes["fn"]?.let { models[it] = service.textAttributes["md"] }
                        listener.onServiceResolved(service)
                    }
                })
            }
            override fun refresh() = native.refresh()
            override fun stop() = native.stop()
        }, System::currentTimeMillis)
        val nativeTransport = DesktopNaviampCastTransportFactory()
        val transport = NaviampCastTransportFactory { address ->
            val connection = nativeTransport.connect(address)
            object : NaviampCastTransportConnection by connection {
                override suspend fun send(frame: ByteArray) { trace("send", frame); connection.send(frame) }
                override suspend fun receive(): ByteArray? = connection.receive()?.also { trace("receive", it) }
            }
        }
        val sender = NaviampCastChannelSessionEffect(scope, transport,
            NaviampCastDeviceAuthentication(JvmNaviampCastCryptoEffect(), System::currentTimeMillis), System::currentTimeMillis)
        var endpoint: NaviampCastMediaEndpointController? = null
        var latestStatus: NaviampCastReceiverStatus? = null
        val firewallGate = System.getenv("NAVIAMP_CAST_FIREWALL_GATE")?.let(Path::of)
        try {
            withContext(dispatcher) { discovery.start() }
            delay(20_000)
            val targets = withContext(dispatcher) { discovery.state.value.targets }
            println("CAST_PROBE discovered=${targets.map { it.target.displayName }} models=$models problem=${discovery.state.value.problem}")
            if (selectedName != "discover") {
                val target = targets.single { it.target.displayName.equals(selectedName, ignoreCase = true) }
                withContext(dispatcher) {
                    sender.start(object : NaviampCastSessionListener {
                        override fun onTargetSelected(target: NaviampCastTarget) = 1L
                        override fun onConnecting(selectionId: Long) {}
                        override fun onConnected(selectionId: Long, displayName: String) { println("CAST_PROBE authenticated=true receiver=$displayName") }
                        override fun onStopped(selectionId: Long) {}
                        override fun onDisconnected(selectionId: Long) {}
                        override fun onUnavailable(selectionId: Long) {}
                        override fun onMediaStatus(selectionId: Long, status: NaviampCastReceiverStatus) { latestStatus = status }
                    })
                    assertTrue(sender.connect(1, target), "Receiver authentication/launch failed")
                    if (System.getenv("NAVIAMP_CAST_PLAYBACK_PROBE") == "true") {
                        val bytes = quietWave()
                        val requests = AtomicInteger()
                        val random = SecureRandom()
                        val leases = NaviampCastMediaLeaseController(NaviampCastSecureTokenSource {
                            Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
                        }, System::currentTimeMillis)
                        val source = object : NaviampCastMediaByteSource {
                            override suspend fun stream(resource: NaviampCastMediaResource, range: NaviampCastRequestedRange?,
                                headOnly: Boolean, onResponse: suspend (ProviderMediaByteResponse) -> Unit,
                                writeChunk: suspend (ByteArray, Int) -> Unit): Boolean {
                                requests.incrementAndGet()
                                println("CAST_PROBE httpRequest=true range=${range != null} head=$headOnly")
                                val resolved = range?.resolve(bytes.size.toLong())
                                if (range != null && resolved == null) {
                                    onResponse(ProviderMediaByteResponse(416, "audio/wav", 0, "bytes */${bytes.size}"))
                                    return true
                                }
                                val part = resolved?.let { bytes.copyOfRange(it.firstByte.toInt(), it.lastByteInclusive.toInt() + 1) } ?: bytes
                                onResponse(ProviderMediaByteResponse(if (resolved == null) 200 else 206,
                                    "audio/wav", part.size.toLong(), resolved?.contentRange))
                                if (!headOnly) writeChunk(part, part.size)
                                return true
                            }
                        }
                        endpoint = NaviampCastMediaEndpointController(DesktopNaviampCastHttpServerEffect(), leases, source,
                            localAddress = { sender.localAddress })
                        endpoint.start()
                        val url = endpoint.issue(NaviampCastMediaResource(NaviampCastMediaKind.Track, "probe", "tone"))
                        firewallGate?.let { gate ->
                            val uri = java.net.URI(url)
                            Files.writeString(gate, buildJsonObject {
                                put("localAddress", uri.host); put("port", uri.port)
                                put("program", ProcessHandle.current().info().command().orElseThrow())
                                put("receivers", buildJsonArray { target.endpoints.forEach { add(it.host) } })
                            }.toString())
                            withTimeout(120_000) {
                                while (!Files.exists(Path.of("$gate.ready"))) delay(250)
                            }
                        }
                        assertTrue(sender.load(1, NaviampCastReceiverMedia(url, "audio/wav", "Naviamp Cast acceptance",
                            "Quiet test tone", null, null, 30_000, 0, false)), "Receiver load rejected")
                        withTimeout(15_000) {
                            while (latestStatus?.playerState != NaviampCastReceiverPlayerState.Paused) delay(250)
                        }
                        assertTrue(sender.command(1, NaviampCastReceiverCommand.Play), "Play rejected")
                        withTimeout(15_000) {
                            while (latestStatus?.playerState != NaviampCastReceiverPlayerState.Playing ||
                                (latestStatus?.positionMillis ?: 0) < 1_000) delay(250)
                        }
                        assertTrue(requests.get() > 0, "Receiver did not fetch the scoped endpoint")
                        assertTrue(sender.command(1, NaviampCastReceiverCommand.Pause))
                        withTimeout(5_000) { while (latestStatus?.playerState != NaviampCastReceiverPlayerState.Paused) delay(100) }
                        assertTrue(sender.command(1, NaviampCastReceiverCommand.Seek(10_000)))
                        withTimeout(5_000) { while ((latestStatus?.positionMillis ?: 0) < 9_000) delay(100) }
                        assertTrue(sender.command(1, NaviampCastReceiverCommand.Play))
                        withTimeout(5_000) { while (latestStatus?.playerState != NaviampCastReceiverPlayerState.Playing) delay(100) }
                        assertTrue(sender.command(1, NaviampCastReceiverCommand.Stop))
                        println("CAST_PROBE playback=true pause=true seek=true resume=true stop=true httpRequests=${requests.get()}")
                    }
                    sender.disconnect()
                }
                delay(1_000)
            }
        } finally {
            withContext(NonCancellable + dispatcher) {
                sender.disconnect()
                delay(1_000)
                endpoint?.stop(); discovery.stop(); sender.stop()
            }
            firewallGate?.let { Files.writeString(Path.of("$it.done"), "done") }
            scope.cancel()
            dispatcher.close()
        }
    }

    private fun trace(direction: String, frame: ByteArray) {
        val message = NaviampCastChannelCodec.decode(frame)
        val payload = message.text?.let { Json.parseToJsonElement(it) as? JsonObject } ?: return
        val status = (payload["status"] as? JsonArray)?.map { entry ->
            (entry as? JsonObject)?.filterKeys { it in setOf("mediaSessionId", "playerState", "idleReason", "currentTime") }
        }
        val apps = ((payload["status"] as? JsonObject)?.get("applications") as? JsonArray)?.map {
            (it as? JsonObject)?.filterKeys { key -> key in setOf("appId", "transportId", "namespaces") }
        }
        println("CAST_PROBE $direction source=${message.sourceId} destination=${message.destinationId} ns=${message.namespace.substringAfterLast('.')} type=${payload["type"]} request=${payload["requestId"]} mediaSession=${payload["mediaSessionId"]} status=$status apps=$apps")
    }

    private fun quietWave(): ByteArray {
        val samples = 44_100 * 30
        return ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples * 2); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(44_100); putInt(88_200); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(samples * 2)
            repeat(samples) { putShort((kotlin.math.sin(it * 2.0 * Math.PI * 440 / 44_100) * 400).toInt().toShort()) }
        }.array()
    }
}
