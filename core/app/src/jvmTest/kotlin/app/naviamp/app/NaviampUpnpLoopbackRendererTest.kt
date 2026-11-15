package app.naviamp.app

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.provider.ProviderMediaByteResponse
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real loopback TCP HTTP/SOAP. JVM APIs here implement a test renderer, not product policy. */
class NaviampUpnpLoopbackRendererTest {
    @Test fun discoversProbesPlaysAndPromotesLosslessOccurrencesOverHttpWithoutReloading() = runTest {
        withContext(Dispatchers.IO) {
            LoopbackRenderer().use { renderer ->
                val engine = CIO.create()
                val transport = NaviampUpnpKtorTransport(engine)
                var token = 0
                val endpoint = NaviampCastMediaEndpointController(renderer.mediaServer, NaviampCastMediaLeaseController(
                    NaviampCastSecureTokenSource { "loopback-test-only-" + (++token).toString().padStart(32, '0') }, { 0L }), FlacSource())
                endpoint.start()
                try {
                    val advertisement = assertIs<NaviampUpnpAdvertisement.Available>(parseNaviampUpnpAdvertisement(renderer.ssdpResponse(), "127.0.0.1"))
                    val client = NaviampUpnpRendererClient.describe(transport, advertisement.location, advertisement.udn)
                    val caps = client.probe()
                    assertTrue(caps.supportsNext)
                    val plan = assertIs<NaviampUpnpMediaPlan.Original>(planNaviampUpnpMedia(
                        NaviampUpnpSourceFormat(NaviampUpnpAudioFormat.Flac, "audio/flac", 16, 44_100, 2), caps.sinkProtocols))
                    val resource = NaviampCastMediaResource(NaviampCastMediaKind.Track, "account-a", "repeated-song", plan.quality)
                    val a = NaviampRemoteQueuedMedia(NaviampRemoteQueueOccurrence(1, 1), endpoint.issue(resource))
                    val b = NaviampRemoteQueuedMedia(NaviampRemoteQueueOccurrence(1, 2), endpoint.issue(resource))
                    fun media(url: String) = NaviampUpnpRendererMedia(url, plan, "Title <& \"😀", "Artist & ensemble")
                    val ledger = NaviampRemotePreparedHandoff()
                    ledger.reset(1, a)
                    client.setCurrent(media(a.mediaUrl))
                    assertEquals(NaviampUpnpTransportState.STOPPED, client.transportState())
                    client.play()
                    val ticket = assertNotNull(ledger.prepareNext(1))
                    client.setNext(media(b.mediaUrl))
                    assertTrue(ledger.acceptPreparedNext(ticket, b))
                    val events = NaviampUpnpEventGate(1, "uuid:subscription")
                    val promotions = CopyOnWriteArrayList<NaviampRemoteHandoffObservation.Promoted>()
                    var observation = 0L
                    renderer.onEvent = { request ->
                        events.accept(1, request.headers, request.body.decodeToString())?.let { event ->
                            val uri = checkNotNull(event.transport.trackUri)
                            val result = ledger.observe(1, ++observation, uri, event.transport.state == NaviampUpnpTransportState.PLAYING)
                            if (result is NaviampRemoteHandoffObservation.Promoted) promotions += result
                        }
                    }
                    val subscriptions = NaviampUpnpEventSubscriptionClient(transport, client.renderer.services.getValue(NaviampUpnpServiceKind.AVTransport).eventUrl)
                    val subscription = subscriptions.subscribe("${renderer.base}/callback/token")
                    assertEquals(subscription, subscriptions.renew(subscription))
                    renderer.finishCurrent()
                    val notify = NaviampUpnpHttpRequest("NOTIFY", "${renderer.base}/callback/token", upnpFixtureHeaders(0), upnpFixtureEvent(b.mediaUrl))
                    assertEquals(200, transport.exchange(notify).status)
                    assertEquals(200, transport.exchange(notify).status) // Actual duplicated HTTP callback.
                    assertEquals(b.mediaUrl, client.position().trackUri)
                    assertEquals(1, promotions.size)
                    assertEquals(b, ledger.current)
                    assertEquals(1, renderer.loads.get())
                    assertEquals(1, renderer.plays.get())
                    assertEquals(2, renderer.received.size)
                    renderer.received.forEach { assertContentEquals(NaviampUpnpSoftwareRendererTest.originalFlac, it) }
                    val head = renderer.fetch(b.mediaUrl, "HEAD")
                    assertEquals("245", head.headers["Content-Length"])
                    assertTrue(head.body.isEmpty())
                    val first = renderer.fetch(b.mediaUrl, range = "bytes=0-99")
                    val rest = renderer.fetch(b.mediaUrl, range = "bytes=100-")
                    assertEquals(206, first.status)
                    assertContentEquals(NaviampUpnpSoftwareRendererTest.originalFlac, first.body + rest.body)
                    assertContentEquals(NaviampUpnpSoftwareRendererTest.originalFlac.takeLast(20).toByteArray(), renderer.fetch(b.mediaUrl, range = "bytes=-20").body)
                    assertEquals(listOf("Title <& \"😀", "Title <& \"😀"), renderer.titles)
                    client.pause(); assertEquals(NaviampUpnpTransportState.PAUSED_PLAYBACK, client.transportState())
                    client.seek(1_250); client.setVolume(40); client.setNext(null); client.stop()
                    subscriptions.unsubscribe(subscription)
                    endpoint.revoke(a.mediaUrl)
                    assertEquals(404, renderer.fetch(a.mediaUrl).status)
                    assertEquals(200, renderer.fetch(b.mediaUrl).status)
                    assertTrue(renderer.requests.any { it.path == "/callback/token" && it.method == "NOTIFY" })
                    renderer.assertHealthy()
                } finally {
                    endpoint.stop(); transport.close(); engine.close()
                }
            }
        }
    }

    @Test fun soapFaultAndMissingOptionalActionCannotBeReportedAsSuccessfulNextPreparation() = runTest {
        withContext(Dispatchers.IO) {
            LoopbackRenderer(nextSupported = false).use { renderer ->
                val engine = CIO.create(); val transport = NaviampUpnpKtorTransport(engine)
                try {
                    val client = NaviampUpnpRendererClient.describe(transport, "${renderer.base}/desc/device.xml", "uuid:fixture")
                    assertEquals(false, client.probe().supportsNext)
                    assertFailsWith<NaviampUpnpUnsupportedAction> { client.setNext(null) }
                    assertTrue(renderer.requests.none { it.headers["soapaction"]?.contains("#SetNext") == true })
                } finally { transport.close(); engine.close() }
            }
            LoopbackRenderer(failNext = true).use { renderer ->
                val engine = CIO.create(); val transport = NaviampUpnpKtorTransport(engine)
                try {
                    val client = NaviampUpnpRendererClient.describe(transport, "${renderer.base}/desc/device.xml", "uuid:fixture")
                    client.probe()
                    assertEquals(701, assertFailsWith<NaviampUpnpActionFault> { client.setNext(null) }.code)
                    assertNull(renderer.nextUri)
                    renderer.assertHealthy()
                } finally { transport.close(); engine.close() }
            }
        }
    }

    @Test fun httpRejectsRedirectAndBothDeclaredAndStreamingOversizedBodies() = runTest {
        withContext(Dispatchers.IO) {
            LoopbackRenderer().use { renderer ->
                val engine = CIO.create(); val transport = NaviampUpnpKtorTransport(engine)
                try {
                    for (path in listOf("/redirect", "/oversized", "/chunked-oversized")) {
                        assertFailsWith<NaviampUpnpProtocolException> { transport.exchange(NaviampUpnpHttpRequest("GET", renderer.base + path)) }
                    }
                    assertTrue(renderer.requests.none { it.path == "/must-not-follow" })
                    renderer.assertHealthy()
                } finally { transport.close(); engine.close() }
            }
        }
    }

    private class FlacSource : NaviampCastMediaByteSource {
        override suspend fun stream(resource: NaviampCastMediaResource, range: NaviampCastRequestedRange?, headOnly: Boolean,
            onResponse: suspend (ProviderMediaByteResponse) -> Unit, writeChunk: suspend (ByteArray, Int) -> Unit): Boolean {
            check(resource.sourceId == "account-a" && resource.quality == StreamQuality.Original)
            val original = NaviampUpnpSoftwareRendererTest.originalFlac
            val resolved = range?.resolve(original.size.toLong())
            if (range != null && resolved == null) { onResponse(ProviderMediaByteResponse(416, null, 0, "bytes */${original.size}")); return true }
            val bytes = resolved?.let { original.copyOfRange(it.firstByte.toInt(), it.lastByteInclusive.toInt() + 1) } ?: original
            onResponse(ProviderMediaByteResponse(if (range == null) 200 else 206, "audio/flac", bytes.size.toLong(), resolved?.contentRange))
            if (!headOnly) writeChunk(bytes, bytes.size)
            return true
        }
    }

    private data class Request(val method: String, val path: String, val headers: Map<String, String>, val body: ByteArray)
    private data class Reply(val status: Int = 200, val body: ByteArray = byteArrayOf(), val headers: Map<String, String> = emptyMap(), val chunked: Boolean = false)

    private class LoopbackRenderer(val nextSupported: Boolean = true, val failNext: Boolean = false) : AutoCloseable {
        private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val base = "http://127.0.0.1:${socket.localPort}"
        val requests = CopyOnWriteArrayList<Request>()
        val received = CopyOnWriteArrayList<ByteArray>()
        val titles = CopyOnWriteArrayList<String>()
        val loads = AtomicInteger()
        val plays = AtomicInteger()
        private val errors = CopyOnWriteArrayList<Throwable>()
        @Volatile private var currentUri: String? = null
        @Volatile var nextUri: String? = null
        @Volatile private var state = "STOPPED"
        @Volatile var onEvent: ((Request) -> Unit)? = null
        @Volatile private var mediaHandler: (suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit)? = null
        private val workers = Executors.newFixedThreadPool(4) { runnable -> Thread(runnable, "607-renderer-client").apply { isDaemon = true } }
        private val listener = thread(name = "607-renderer-listener", isDaemon = true) {
            while (!socket.isClosed) try { val client = socket.accept(); workers.execute { serve(client) } }
            catch (failure: IOException) { if (!socket.isClosed) errors += failure }
        }
        val mediaServer = object : NaviampCastHttpServerEffect {
            override suspend fun start(handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit): String { mediaHandler = handler; return base }
            override suspend fun stop() { mediaHandler = null }
        }
        fun ssdpResponse() = "HTTP/1.1 200 OK\r\nST: $NAVIAMP_UPNP_RENDERER_TYPE\r\nUSN: uuid:fixture::$NAVIAMP_UPNP_RENDERER_TYPE\r\nLOCATION: $base/desc/device.xml\r\nCACHE-CONTROL: max-age=1800\r\n\r\n"
        fun finishCurrent() { currentUri = checkNotNull(nextUri); nextUri = null; received += fetch(checkNotNull(currentUri)).body }
        fun fetch(uri: String, method: String = "GET", range: String? = null): Reply {
            check(uri.startsWith("$base/"))
            val connection = URL(uri).openConnection() as HttpURLConnection
            connection.connectTimeout = 3_000; connection.readTimeout = 3_000
            connection.requestMethod = method
            range?.let { connection.setRequestProperty("Range", it) }
            return try {
                val status = connection.responseCode
                Reply(status, if (method != "HEAD" && status in setOf(200, 206)) connection.inputStream.use { it.readBytes() } else byteArrayOf(),
                    mapOf("Content-Length" to connection.contentLengthLong.toString()))
            }
            finally { connection.disconnect() }
        }
        fun assertHealthy() { check(errors.isEmpty()) { errors.joinToString { it.stackTraceToString() } } }
        private fun serve(client: Socket) = client.use {
            try {
                client.soTimeout = 5_000
                val input = client.getInputStream()
                val header = ByteArrayOutputStream()
                while (true) {
                    check(header.size() < 32_768)
                    val byte = input.read(); check(byte >= 0); header.write(byte)
                    val bytes = header.toByteArray()
                    if (bytes.size >= 4 && bytes.takeLast(4) == listOf<Byte>(13, 10, 13, 10)) break
                }
                val lines = header.toString(Charsets.UTF_8).trimEnd().split("\r\n")
                val first = lines.first().split(' ')
                val headers = lines.drop(1).associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
                val size = headers["content-length"]?.toInt() ?: 0
                check(size in 0..262_144)
                val body = input.readNBytes(size); check(body.size == size)
                val request = Request(first[0], first[1], headers, body)
                requests += request
                val reply = handle(request)
                val output = client.getOutputStream()
                val framing = if (reply.chunked) "Transfer-Encoding: chunked\r\n" else "Content-Length: ${reply.headers["Content-Length"] ?: reply.body.size}\r\n"
                output.write(("HTTP/1.1 ${reply.status} Fixture\r\nConnection: close\r\n$framing" + reply.headers.filterKeys { !it.equals("Content-Length", true) }.entries.joinToString("") { "${it.key}: ${it.value}\r\n" } + "\r\n").toByteArray())
                if (reply.chunked) output.write("${reply.body.size.toString(16)}\r\n".toByteArray())
                if (request.method != "HEAD") output.write(reply.body)
                if (reply.chunked) output.write("\r\n0\r\n\r\n".toByteArray())
                output.flush()
            } catch (_: IOException) { /* Bounded/redirect tests intentionally abort response reads. */ }
            catch (failure: Throwable) { errors += failure }
        }
        private fun handle(request: Request): Reply {
            if (request.path == "/redirect") return Reply(302, headers = mapOf("Location" to "$base/must-not-follow"))
            if (request.path == "/oversized") return Reply(body = ByteArray(262_145))
            if (request.path == "/chunked-oversized") return Reply(body = ByteArray(262_145), chunked = true)
            if (request.method == "NOTIFY") { checkNotNull(onEvent)(request); return Reply() }
            if (request.method == "SUBSCRIBE") return Reply(headers = mapOf("SID" to "uuid:subscription", "TIMEOUT" to "Second-300"))
            if (request.method == "UNSUBSCRIBE") return Reply()
            if (request.path == "/desc/device.xml") return xml(upnpFixtureDescription())
            if (request.path.startsWith("/service/")) return xml(upnpFixtureScpd(NaviampUpnpServiceKind.entries.single { request.path.endsWith("$it.xml") }, nextSupported))
            if (request.path.startsWith("/cast/media/")) return runBlocking {
                var status = 0; var headers = emptyMap<String, String>(); val body = ByteArrayOutputStream()
                val handler = mediaHandler ?: return@runBlocking Reply(404)
                handler(NaviampCastHttpRequest(request.method, request.path, request.headers["range"]), object : NaviampCastHttpResponse {
                    override suspend fun begin(statusCode: Int, responseHeaders: Map<String, String>) { status = statusCode; headers = responseHeaders }
                    override suspend fun write(bytes: ByteArray, count: Int) { body.write(bytes, 0, count) }
                })
                Reply(status, body.toByteArray(), headers)
            }
            val kind = NaviampUpnpServiceKind.entries.single { request.path == "/control/$it" }
            check(request.method == "POST" && request.headers["content-type"]?.startsWith("text/xml") == true)
            val action = request.headers.getValue("soapaction").trim('"').substringAfter('#')
            val node = parseUpnpXml(request.body.decodeToString()).required(UPNP_SOAP_NS, "Body").children.single()
            check(node.name == action && node.namespace == "urn:schemas-upnp-org:service:$kind:1")
            val args = node.children.associate { it.name to it.value() }
            if (kind != NaviampUpnpServiceKind.ConnectionManager) check(args["InstanceID"] == "0")
            when (action) {
                "SetAVTransportURI", "SetNextAVTransportURI" -> {
                    if (action == "SetNextAVTransportURI" && failNext) return Reply(500, upnpFixtureFault().toByteArray())
                    val uri = args.getValue(if (action == "SetAVTransportURI") "CurrentURI" else "NextURI")
                    val metadata = args.getValue(if (action == "SetAVTransportURI") "CurrentURIMetaData" else "NextURIMetaData")
                    if (uri.isNotEmpty()) {
                        val item = parseUpnpXml(metadata).children.single()
                        titles += item.required("http://purl.org/dc/elements/1.1/", "title").value()
                        val resource = item.required("urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/", "res")
                        check(resource.value() == uri && resource.attributes["" to "protocolInfo"] == "http-get:*:audio/flac:*")
                    }
                    if (action == "SetAVTransportURI") { loads.incrementAndGet(); currentUri = uri; state = "STOPPED" }
                    else nextUri = uri.takeIf { it.isNotEmpty() }
                }
                "Play" -> { plays.incrementAndGet(); received += fetch(checkNotNull(currentUri)).body; state = "PLAYING" }
                "Pause" -> state = "PAUSED_PLAYBACK"
                "Stop" -> state = "STOPPED"
                "Seek" -> check(args["Unit"] == "REL_TIME" && args["Target"] == "00:00:01.250")
                "SetVolume" -> check(args["Channel"] == "Master" && args["DesiredVolume"] == "40")
            }
            val fields = when (action) {
                "GetProtocolInfo" -> mapOf("Source" to "", "Sink" to "http-get:*:audio/flac:*")
                "GetTransportInfo" -> mapOf("CurrentTransportState" to state)
                "GetPositionInfo" -> mapOf("TrackURI" to checkNotNull(currentUri), "RelTime" to "00:00:00", "TrackDuration" to "00:00:01")
                else -> emptyMap()
            }
            return xml(upnpFixtureSoap(kind, action, fields))
        }
        private fun xml(body: String) = Reply(body = body.toByteArray(Charsets.UTF_8), headers = mapOf("Content-Type" to "text/xml; charset=utf-8"))
        override fun close() { socket.close(); listener.join(2_000); workers.shutdownNow() }
    }
}
