package app.naviamp.desktop.cast

import app.naviamp.app.*
import app.naviamp.domain.provider.ProviderMediaByteResponse
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.*

class DesktopNaviampCastHttpServerTest {
    @Test
    fun nativeServerStreamsSharedAuthorizedRangesAndHeadThenRevokesAndCloses() = runBlocking {
        val server = DesktopNaviampCastHttpServerEffect()
        val source = object : NaviampCastMediaByteSource {
            override suspend fun stream(resource: NaviampCastMediaResource, range: NaviampCastRequestedRange?, headOnly: Boolean,
                onResponse: suspend (ProviderMediaByteResponse) -> Unit, writeChunk: suspend (ByteArray, Int) -> Unit): Boolean {
                val bytes = byteArrayOf(1, 2, 3, 4)
                if (range == NaviampCastRequestedRange.From(1, 2)) {
                    onResponse(ProviderMediaByteResponse(206, "audio/mpeg", 2, "bytes 1-2/4"))
                    if (!headOnly) writeChunk(byteArrayOf(2, 3), 2)
                } else {
                    onResponse(ProviderMediaByteResponse(200, "audio/mpeg", 4, null))
                    if (!headOnly) writeChunk(bytes, bytes.size)
                }
                return true
            }
        }
        val leases = NaviampCastMediaLeaseController(NaviampCastSecureTokenSource { "abcdefghijklmnopqrstuvwxyz012345" }, { 0L })
        val endpoint = NaviampCastMediaEndpointController(server, leases, source, localAddress = { "127.0.0.1" })
        endpoint.start()
        val url = endpoint.issue(NaviampCastMediaResource(NaviampCastMediaKind.Track, "test", "private-provider-id"))
        try {
            request(url) { assertEquals(200, responseCode); assertContentEquals(byteArrayOf(1, 2, 3, 4), inputStream.readBytes()) }
            request(url, range = "bytes=1-2") {
                assertEquals(206, responseCode); assertEquals("bytes 1-2/4", getHeaderField("Content-Range"))
                assertContentEquals(byteArrayOf(2, 3), inputStream.readBytes())
            }
            request(url, "HEAD") { assertEquals(200, responseCode); assertEquals("4", getHeaderField("Content-Length")); assertTrue(inputStream.readBytes().isEmpty()) }
            request(url, range = "bytes=bad") { assertEquals(416, responseCode) }
            leases.revokeAll()
            request(url) { assertEquals(404, responseCode) }
        } finally { endpoint.stop() }
        assertFails { request(url) { responseCode } }
        Unit
    }

    private suspend fun request(url: String, method: String = "GET", range: String? = null, assertions: HttpURLConnection.() -> Unit) =
        withContext(Dispatchers.IO) {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 2_000; connection.readTimeout = 2_000; connection.requestMethod = method
                range?.let { connection.setRequestProperty("Range", it) }
                connection.assertions()
            } finally { connection.disconnect() }
        }
}
