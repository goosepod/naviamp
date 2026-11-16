package app.naviamp.app

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class NaviampKtorMediaHttpServerEffectTest {
    @Test fun knownAndUnknownLengthsHeadAndRangeSurviveRealTcp() = runTest { withContext(Dispatchers.Default) {
        val server = NaviampKtorMediaHttpServerEffect()
        val seen = mutableListOf<NaviampCastHttpRequest>()
        val url = server.start("127.0.0.1") { request, response ->
            seen += request
            response.begin(if (request.rangeHeader != null) 206 else 200,
                if (request.path == "/chunked") mapOf("Content-Type" to "audio/flac") else mapOf("Content-Length" to "3"))
            if (request.method != "HEAD") response.write(byteArrayOf(65, 66, 67), 3)
        }
        try {
            val full = wire(url, "GET /full HTTP/1.1\r\n\r\n")
            assertTrue(full.startsWith("HTTP/1.1 200")); assertTrue(full.endsWith("\r\n\r\nABC"))
            val head = wire(url, "HEAD /full HTTP/1.1\r\n\r\n")
            assertTrue(head.contains("Content-Length: 3\r\n")); assertTrue(head.endsWith("\r\n\r\n"))
            val range = wire(url, "GET /full HTTP/1.1\r\nRange: bytes=10-12\r\n\r\n")
            assertTrue(range.startsWith("HTTP/1.1 206")); assertEquals("bytes=10-12", seen.last().rangeHeader)
            val chunked = wire(url, "GET /chunked HTTP/1.1\r\n\r\n")
            assertTrue(chunked.contains("Transfer-Encoding: chunked\r\n")); assertTrue(chunked.endsWith("3\r\nABC\r\n0\r\n\r\n"))
        } finally { server.stop() }
    } }

    @Test fun malformedOrDuplicateHeadersAndRequestBodiesNeverReachTheProvider() = runTest { withContext(Dispatchers.Default) {
        val server = NaviampKtorMediaHttpServerEffect()
        var calls = 0
        val url = server.start("127.0.0.1") { _, response -> calls++; response.begin(200, emptyMap()) }
        try {
            for (headers in listOf("Range: bytes=0-\r\nRange: bytes=1-\r\n", "Transfer-Encoding: chunked\r\n", "Content-Length: 1\r\n", "bad-header\r\n")) {
                assertTrue(wire(url, "GET /x HTTP/1.1\r\n$headers\r\n").startsWith("HTTP/1.1 400"))
            }
            assertEquals(0, calls)
        } finally { server.stop() }
    } }

    @Test fun stopClosesIdleAndStreamingClientsAndTheSameEffectCanRestart() = runTest { withContext(Dispatchers.Default) {
        val server = NaviampKtorMediaHttpServerEffect()
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val url = server.start("127.0.0.1") { _, response ->
            response.begin(200, emptyMap()); entered.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        }
        val manager = SelectorManager(Dispatchers.Default)
        val port = url.substringAfterLast(':').toInt()
        val idle = aSocket(manager).tcp().connect("127.0.0.1", port)
        val streaming = aSocket(manager).tcp().connect("127.0.0.1", port)
        try {
            streaming.openWriteChannel(autoFlush = true).writeStringUtf8("GET /stream HTTP/1.1\r\n\r\n")
            withTimeout(5_000) { entered.await(); server.stop(); cancelled.await() }
            server.stop()
            val restarted = server.start("127.0.0.1") { _, response -> response.begin(204, emptyMap()) }
            val empty = wire(restarted, "GET /new HTTP/1.1\r\n\r\n")
            assertTrue(empty.startsWith("HTTP/1.1 204"))
            assertFalse(empty.contains("Transfer-Encoding:"))
            assertFalse(empty.contains("Content-Length:"))
            assertTrue(empty.endsWith("\r\n\r\n"))
        } finally { idle.close(); streaming.close(); manager.close(); server.stop() }
    } }

    private suspend fun wire(url: String, request: String): String = withTimeout(5_000) {
        val manager = SelectorManager(Dispatchers.Default)
        try {
            val socket = aSocket(manager).tcp().connect("127.0.0.1", url.substringAfterLast(':').toInt())
            try {
                socket.openWriteChannel(autoFlush = true).apply { writeStringUtf8(request); flush() }
                val input = socket.openReadChannel()
                val bytes = ByteArray(16_384)
                var size = 0
                while (true) { val n = input.readAvailable(bytes, size, bytes.size - size); if (n == -1) break; size += n; check(size < bytes.size) }
                bytes.decodeToString(0, size)
            } finally { socket.close() }
        } finally { manager.close() }
    }
}
