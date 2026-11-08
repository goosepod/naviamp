package app.naviamp.app

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.ProviderMediaByteResponse
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampPlaybackStreamRelayTest {
    private val request = StreamRequest(TrackId("private-track"), StreamQuality.Original)

    @Test fun startsDeliveringBeforeTheUpstreamCompletesAndForwardsRangesAndHead() = runTest {
        withContext(Dispatchers.Default) {
            coroutineScope {
                val finish = CompletableDeferred<Unit>()
                var observedRange: String? = null
                var observedHead = false
                val provider = provider { range, head, respond, write ->
                    observedRange = range; observedHead = head
                    respond(ProviderMediaByteResponse(if (range == null) 200 else 206, "audio/wav", 6, range?.let { "bytes 0-5/100" }))
                    if (!head) {
                        write("abc".encodeToByteArray(), 3)
                        finish.await()
                        write("def".encodeToByteArray(), 3)
                    }
                    true
                }
                val relay = NaviampPlaybackStreamRelay(this) { it === provider }
                try {
                    val url = relay.url(NaviampPlaybackStreamRelay.Slot.Current, provider, request)
                    assertFalse(url.contains("private-track"))
                    wire(url, range = "bytes=0-5") { code, headers, body ->
                        assertEquals(206, code)
                        assertEquals("bytes 0-5/100", headers["content-range"])
                        val prefix = ByteArray(3); body.readFully(prefix)
                        assertEquals("abc", prefix.decodeToString())
                        assertFalse(finish.isCompleted)
                        finish.complete(Unit)
                        val suffix = ByteArray(3); body.readFully(suffix)
                        assertEquals("def", suffix.decodeToString())
                    }
                    assertEquals("bytes=0-5", observedRange)
                    wire(url, method = "HEAD") { code, headers, _ ->
                        assertEquals(200, code); assertEquals("6", headers["content-length"])
                    }
                    assertTrue(observedHead)
                } finally { finish.complete(Unit); relay.close() }
            }
        }
    }

    @Test fun revokesOldCurrentStreamsAndPreparedLeasesWithoutRevokingPromotedPlayback() = runTest {
        withContext(Dispatchers.Default) {
            coroutineScope {
                var calls = 0
                val cancelled = CompletableDeferred<Unit>()
                val provider = provider { _, _, respond, write ->
                    calls++
                    respond(ProviderMediaByteResponse(200, "audio/wav", 100, null))
                    write(byteArrayOf(1), 1)
                    try { awaitCancellation() } finally { cancelled.complete(Unit) }
                }
                val relay = NaviampPlaybackStreamRelay(this) { it === provider }
                try {
                    val current = relay.url(NaviampPlaybackStreamRelay.Slot.Current, provider, request)
                    val next = relay.url(NaviampPlaybackStreamRelay.Slot.PreparedNext, provider, request.copy(trackId = TrackId("next")))
                    wire(current) { code, _, body ->
                        assertEquals(200, code); body.readFully(ByteArray(1))
                        assertEquals(next, relay.url(NaviampPlaybackStreamRelay.Slot.Current, provider, request.copy(trackId = TrackId("next"))))
                        withTimeout(3_000) { cancelled.await() }
                    }
                    relay.clearPrepared()
                    wire(current) { code, _, _ -> assertEquals(404, code) }
                    // Clearing prepared-next must retain the same lease after it becomes current.
                    assertEquals(next, relay.url(NaviampPlaybackStreamRelay.Slot.Current, provider, request.copy(trackId = TrackId("next"))))
                    assertEquals(1, calls)
                } finally { relay.close() }
            }
        }
    }

    @Test fun rejectsUnauthorizedRequestsAndSourceChangesBeforeContactingTheProvider() = runTest {
        withContext(Dispatchers.Default) {
            coroutineScope {
                var calls = 0
                var current = true
                val provider = provider { _, _, _, _ -> calls++; false }
                val relay = NaviampPlaybackStreamRelay(this) { current }
                try {
                    val url = relay.url(NaviampPlaybackStreamRelay.Slot.Current, provider, request)
                    wire(url + "wrong") { code, _, _ -> assertEquals(404, code) }
                    wire(url, host = "attacker.example") { code, _, _ -> assertEquals(404, code) }
                    wire(url, method = "POST") { code, _, _ -> assertEquals(405, code) }
                    wire(url, range = "bytes=1-2,3-4") { code, _, _ -> assertEquals(416, code) }
                    current = false
                    wire(url) { code, _, _ -> assertEquals(404, code) }
                    assertEquals(0, calls)
                    current = true
                    wire(url) { code, _, _ -> assertEquals(502, code) }
                    assertEquals(1, calls)
                } finally { relay.close() }
            }
        }
    }

    @Test fun validatesRangeBoundsAndRejectsHeaderInjection() {
        for (range in listOf("bytes=0-", "bytes=0-5", "bytes=-20")) assertTrue(validPlaybackByteRange(range))
        for (range in listOf("bytes=-0", "bytes=2-1", "bytes=9223372036854775808-", "bytes=0-1,2-3", "bytes=--1", "bytes=-", "items=1-2")) {
            assertFalse(validPlaybackByteRange(range), range)
        }
        assertFalse(safePlaybackHeader("audio/wav\r\nInjected: yes"))
        assertTrue(safePlaybackHeader("audio/flac"))
    }

    private fun provider(stream: suspend (String?, Boolean, suspend (ProviderMediaByteResponse) -> Unit, suspend (ByteArray, Int) -> Unit) -> Boolean) =
        object : MediaProvider by RecordingProvider(false) {
            override suspend fun streamTrackBytes(request: StreamRequest, rangeHeader: String?, headOnly: Boolean,
                onResponse: suspend (ProviderMediaByteResponse) -> Unit, writeChunk: suspend (ByteArray, Int) -> Unit): Boolean =
                stream(rangeHeader, headOnly, onResponse, writeChunk)
        }

    private suspend fun wire(url: String, method: String = "GET", range: String? = null, host: String? = null,
        receive: suspend (Int, Map<String, String>, ByteReadChannel) -> Unit) = withTimeout(5_000) {
        val authority = url.removePrefix("http://").substringBefore('/')
        val path = url.removePrefix("http://$authority")
        val manager = SelectorManager(Dispatchers.Default)
        try {
            val socket = aSocket(manager).tcp().connect("127.0.0.1", authority.substringAfter(':').toInt())
            try {
                socket.openWriteChannel(autoFlush = true).apply {
                    writeStringUtf8("$method $path HTTP/1.1\r\nHost: ${host ?: authority}\r\n" +
                        (range?.let { "Range: $it\r\n" } ?: "") + "\r\n")
                    flush()
                }
                val input = socket.openReadChannel()
                val code = input.readUTF8Line()!!.split(' ')[1].toInt()
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = input.readUTF8Line()!!
                    if (line.isEmpty()) break
                    headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                }
                receive(code, headers, input)
            } finally { socket.close() }
        } finally { manager.close() }
    }
}
