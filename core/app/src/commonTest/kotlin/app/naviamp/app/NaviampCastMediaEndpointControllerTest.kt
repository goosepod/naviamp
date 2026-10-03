package app.naviamp.app

import app.naviamp.domain.provider.ProviderMediaByteResponse
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampCastMediaEndpointControllerTest {
    @Test
    fun concurrentStartsBindOnceAndCancelledStartCleansUp() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var starts = 0
        var stops = 0
        val server = object : NaviampCastHttpServerEffect {
            override suspend fun start(handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit): String {
                starts++; entered.complete(Unit); release.await()
                return "http://192.0.2.1:12345"
            }
            override suspend fun stop() { stops++ }
        }
        fun endpoint() = NaviampCastMediaEndpointController(server,
            NaviampCastMediaLeaseController(NaviampCastSecureTokenSource { "abcdefghijklmnopqrstuvwxyz012345" }, { 0L }), FakeSource())
        val controller = endpoint()
        val first = async { controller.start() }
        entered.await()
        val second = async { controller.start() }
        release.complete(Unit)
        first.await(); second.await()
        assertEquals(1, starts)
        controller.stop()
        assertEquals(1, stops)

        val blocked = CompletableDeferred<Unit>()
        val cancelledServer = object : NaviampCastHttpServerEffect {
            override suspend fun start(handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit): String {
                blocked.complete(Unit)
                CompletableDeferred<Unit>().await()
                return "http://192.0.2.1:12345"
            }
            override suspend fun stop() { stops++ }
        }
        val cancelled = NaviampCastMediaEndpointController(cancelledServer,
            NaviampCastMediaLeaseController(NaviampCastSecureTokenSource { "abcdefghijklmnopqrstuvwxyz012345" }, { 0L }), FakeSource())
        val opening = async { cancelled.start() }
        blocked.await()
        opening.cancel(); opening.join()
        assertEquals(2, stops)
    }
    @Test
    fun servesOnlyActiveLeaseAndForwardsRangeWithoutExposingResourceId() = runTest {
        val server = FakeServer()
        val source = FakeSource()
        val leases = NaviampCastMediaLeaseController(
            NaviampCastSecureTokenSource { "abcdefghijklmnopqrstuvwxyz012345" },
            nowEpochMillis = { 0L },
        )
        val endpoint = NaviampCastMediaEndpointController(server, leases, source)
        val resource = NaviampCastMediaResource(NaviampCastMediaKind.Track, "source", "song?api_key=private")
        endpoint.start()
        val url = endpoint.issue(resource)

        assertEquals("http://192.0.2.1:54321/cast/media/abcdefghijklmnopqrstuvwxyz012345", url)
        assertFalse(url.contains("api_key"))
        val reply = server.request("GET", url.substringAfter("192.0.2.1:54321"), "bytes=10-12")
        assertEquals(206, reply.status)
        assertEquals("bytes 10-12/100", reply.headers["Content-Range"])
        assertEquals(listOf<Byte>(1, 2, 3), reply.bytes)
        assertEquals(NaviampCastRequestedRange.From(10, 12), source.range)

        endpoint.revoke(url.replace("192.0.2.1", "192.0.2.2"))
        assertEquals(200, server.request("HEAD", url.substringAfter("192.0.2.1:54321"), null).status)
        endpoint.revoke(url)
        assertEquals(404, server.request("GET", url.substringAfter("192.0.2.1:54321"), null).status)

        endpoint.stop()
        assertEquals(404, server.request("GET", url.substringAfter("192.0.2.1:54321"), null).status)
    }

    @Test
    fun bindsTheInterfaceSelectedByTheCastTransport() = runTest {
        val server = FakeServer()
        val endpoint = NaviampCastMediaEndpointController(server,
            NaviampCastMediaLeaseController(NaviampCastSecureTokenSource { "abcdefghijklmnopqrstuvwxyz012345" }, { 0L }),
            FakeSource(), localAddress = { "192.0.2.20" })
        endpoint.start()
        assertEquals("192.0.2.20", server.localAddress)
        endpoint.stop()
    }

    @Test
    fun headHasHeadersWithoutBodyAndInvalidRangeNeverFetchesSource() = runTest {
        val server = FakeServer()
        val source = FakeSource()
        val endpoint = NaviampCastMediaEndpointController(
            server,
            NaviampCastMediaLeaseController(
                NaviampCastSecureTokenSource { "abcdefghijklmnopqrstuvwxyz012345" },
                nowEpochMillis = { 0L },
            ),
            source,
        )
        endpoint.start()
        val path = endpoint.issue(NaviampCastMediaResource(NaviampCastMediaKind.Track, "source", "song"))
            .substringAfter("192.0.2.1:54321")

        val head = server.request("HEAD", path, null)
        assertEquals(200, head.status)
        assertTrue(head.bytes.isEmpty())
        assertEquals("3", head.headers["Content-Length"])
        val calls = source.calls
        assertEquals(416, server.request("GET", path, "bytes=bad").status)
        assertEquals(calls, source.calls)
    }

    private class FakeServer : NaviampCastHttpServerEffect {
        var localAddress: String? = null
        override suspend fun start(localAddress: String?, handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit): String {
            this.localAddress = localAddress
            return start(handler)
        }
        private lateinit var handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit
        override suspend fun start(handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit): String {
            this.handler = handler
            return "http://192.0.2.1:54321"
        }
        override suspend fun stop() = Unit
        suspend fun request(method: String, path: String, range: String?): FakeResponse =
            FakeResponse().also { handler(NaviampCastHttpRequest(method, path, range), it) }
    }

    private class FakeResponse : NaviampCastHttpResponse {
        var status = 0
        var headers: Map<String, String> = emptyMap()
        val bytes = mutableListOf<Byte>()
        override suspend fun begin(statusCode: Int, headers: Map<String, String>) {
            status = statusCode
            this.headers = headers
        }
        override suspend fun write(bytes: ByteArray, count: Int) {
            this.bytes += bytes.take(count)
        }
    }

    private class FakeSource : NaviampCastMediaByteSource {
        var range: NaviampCastRequestedRange? = null
        var calls = 0
        override suspend fun stream(
            resource: NaviampCastMediaResource,
            range: NaviampCastRequestedRange?,
            headOnly: Boolean,
            onResponse: suspend (ProviderMediaByteResponse) -> Unit,
            writeChunk: suspend (ByteArray, Int) -> Unit,
        ): Boolean {
            calls++
            this.range = range
            onResponse(ProviderMediaByteResponse(
                if (range == null) 200 else 206,
                "audio/mpeg",
                3,
                if (range == null) null else "bytes 10-12/100",
            ))
            if (!headOnly) writeChunk(byteArrayOf(1, 2, 3), 3)
            return true
        }
    }
}
