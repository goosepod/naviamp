package app.naviamp.presentation

import app.naviamp.app.*
import app.naviamp.domain.*
import app.naviamp.domain.provider.*
import app.naviamp.domain.playback.*
import app.naviamp.domain.queue.PlaybackQueue
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/** Explicit opt-in operator probe; ordinary CI never controls a LAN receiver. */
class NaviampAirPinLiveProbeTest {
    @Test fun originalFlacThroughSharedQueueAndRelay() {
        val location = System.getenv("NAVIAMP_AIRPIN_LOCATION") ?: run { org.junit.Assume.assumeTrue("Live receiver probe is opt-in", false); return }
        val address = checkNotNull(System.getenv("NAVIAMP_AIRPIN_LOCAL"))
        val audio = Files.readAllBytes(Path.of(checkNotNull(System.getenv("NAVIAMP_AIRPIN_FLAC"))))
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try { runBlocking(dispatcher) {
            val scope = CoroutineScope(SupervisorJob() + dispatcher)
            val engine = CIO.create(); val http = NaviampUpnpKtorTransport(engine)
            val acceptedSeeks = AtomicInteger()
            val auditedHttp = NaviampUpnpHttpTransport { request ->
                http.exchange(request).also { reply ->
                    if (request.body.contains("<u:Seek ") && reply.status == 200 && reply.body.contains("SeekResponse")) acceptedSeeks.incrementAndGet()
                }
            }
            val base = FakeCoreMediaProvider()
            val track = base.track.copy(durationSeconds = 8, audioInfo = AudioInfo("flac", null, "audio/flac", 16, 44_100))
            val requests = AtomicInteger()
            val provider = object : MediaProvider by base {
                override suspend fun streamTrackBytes(request: StreamRequest, rangeHeader: String?, headOnly: Boolean,
                    onResponse: suspend (ProviderMediaByteResponse) -> Unit, writeChunk: suspend (ByteArray, Int) -> Unit): Boolean {
                    check(request.quality == StreamQuality.Original); requests.incrementAndGet()
                    val range = rangeHeader?.removePrefix("bytes=")?.split('-', limit = 2)
                    val first = range?.firstOrNull()?.toIntOrNull() ?: 0
                    val last = range?.getOrNull(1)?.toIntOrNull()?.coerceAtMost(audio.lastIndex) ?: audio.lastIndex
                    onResponse(ProviderMediaByteResponse(if (range == null) 200 else 206, "audio/flac", (last - first + 1).toLong(),
                        range?.let { "bytes $first-$last/${audio.size}" }))
                    if (!headOnly) writeChunk(audio.copyOfRange(first, last + 1), last - first + 1)
                    return true
                }
            }
            val native = object : NaviampCastHttpServerEffect {
                var server: HttpServer? = null
                val executor = Executors.newFixedThreadPool(2)
                override suspend fun start(handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit): String {
                    val bound = HttpServer.create(InetSocketAddress(address, 0), 8)
                    bound.executor = executor
                    bound.createContext("/") { exchange -> runBlocking {
                        handler(NaviampCastHttpRequest(exchange.requestMethod, exchange.requestURI.path, exchange.requestHeaders.getFirst("Range")), object : NaviampCastHttpResponse {
                            override suspend fun begin(statusCode: Int, headers: Map<String, String>) {
                                headers.forEach { (name, value) -> exchange.responseHeaders.set(name, value) }
                                exchange.sendResponseHeaders(statusCode, if (exchange.requestMethod == "HEAD") -1 else headers["Content-Length"]?.toLong() ?: 0)
                            }
                            override suspend fun write(bytes: ByteArray, count: Int) { exchange.responseBody.write(bytes, 0, count) }
                        })
                        exchange.close()
                    } }
                    server = bound; bound.start(); return "http://$address:${bound.address.port}"
                }
                override suspend fun stop() { server?.stop(0); server = null; executor.shutdownNow() }
            }
            val outputs = NaviampPlaybackOutputSelectionController()
            val live = NaviampLivePlaybackController(NaviampLivePlaybackState(currentTrack = track,
                queue = PlaybackQueue(listOf(track), 0), playbackState = PlaybackState.Playing, progress = PlaybackProgress(0.0, 8.0)))
            val session = NaviampCoreUpnpSession(scope, outputs, 1_000)
            var token = 0
            val controller = NaviampCoreRemotePlaybackController(scope, session, outputs,
                NaviampCastMediaEndpointController(native, NaviampCastMediaLeaseController(NaviampCastSecureTokenSource {
                    "live-probe-${(++token).toString().padStart(32, '0')}" }, System::currentTimeMillis),
                    NaviampCoreCastMediaByteSource(NaviampCoreMediaProviderSource { provider })),
                NaviampCoreMediaProviderSource { provider }, live, NaviampPlaybackQueueCoordinator(live), fakeCoreServices().playback.effects, {})
            try {
                val discovery = NaviampUpnpDiscoveryController(scope, JvmNaviampUpnpDiscoveryEffect(), http, System::currentTimeMillis)
                val discovered = try {
                    discovery.start()
                    withTimeout(20_000) {
                        while (discovery.state.value.targets.none { it.renderer.name == "APTV_223" }) delay(250)
                        discovery.state.value.targets.first { it.renderer.name == "APTV_223" }
                    }
                } finally { discovery.stop() }
                check(discovered.location == location)
                val receiver = NaviampUpnpRendererClient.describe(auditedHttp, discovered.location, discovered.renderer.udn)
                controller.start(); assertTrue(session.select(receiver, address))
                withTimeout(30_000) { while (live.state.value.playbackState != PlaybackState.Playing || requests.get() == 0 || !outputs.hasRemotePlaybackAuthority()) delay(250) }
                controller.pause()
                withTimeout(10_000) { while (receiver.transportState() != NaviampUpnpTransportState.PAUSED_PLAYBACK) delay(250) }
                controller.seek(2.0)
                delay(1_000)
                val pausedPosition = receiver.position().positionMillis
                controller.resume()
                withTimeout(10_000) { while (receiver.transportState() != NaviampUpnpTransportState.PLAYING) delay(250) }
                val resumedPosition = receiver.position().positionMillis
                delay(1_000)
                controller.stop()
                withTimeout(10_000) { while (receiver.transportState() != NaviampUpnpTransportState.STOPPED) delay(250) }
                assertEquals(1, acceptedSeeks.get(), "The shared controller must issue an acknowledged seek")
                println("AIRPIN_RESULT name=${receiver.renderer.name} liveDiscovery=true originalFlac=true bytes=${audio.size} requests=${requests.get()} supportsNext=${session.supportsNext} pause=true seekAcknowledged=true pausedPositionMillis=$pausedPosition resumedPositionMillis=$resumedPosition resume=true stop=true")
                assertTrue(requests.get() > 0)
            } finally { controller.shutdown(); scope.cancel(); http.close(); engine.close(); native.stop() }
        } } finally { dispatcher.close() }
    }
}
