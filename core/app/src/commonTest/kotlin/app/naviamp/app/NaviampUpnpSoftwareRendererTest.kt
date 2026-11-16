package app.naviamp.app

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.provider.ProviderMediaByteResponse
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deterministic in-process renderer, not a SOAP/SSDP implementation or hardware qualification.
 * Exercises the real common policy, lease authorization, HTTP relay and promotion ledger together.
 */
class NaviampUpnpSoftwareRendererTest {
    @Test fun repeatedLosslessTrackTransfersOriginalBytesAndPromotesWithoutAnotherLoadOrPlay() = runTest {
        val renderer = SoftwareRenderer()
        val source = OriginalSource()
        val endpoint = endpoint(renderer, source)
        val plan = assertIs<NaviampUpnpMediaPlan.Original>(planNaviampUpnpMedia(
            NaviampUpnpSourceFormat(NaviampUpnpAudioFormat.Flac, "audio/flac", 16, 44_100, 2),
            parseNaviampUpnpSinkProtocols(renderer.sink),
        ))
        val resource = NaviampCastMediaResource(NaviampCastMediaKind.Track, "account-a", "same-track", plan.quality)
        endpoint.start()
        try {
            val a = NaviampRemoteQueuedMedia(NaviampRemoteQueueOccurrence(1, 1), endpoint.issue(resource))
            val b = NaviampRemoteQueuedMedia(NaviampRemoteQueueOccurrence(1, 2), endpoint.issue(resource))
            assertFalse(a.mediaUrl == b.mediaUrl)
            val ledger = NaviampRemotePreparedHandoff()
            ledger.reset(1, a)
            renderer.load(a)
            renderer.play()
            val ticket = assertNotNull(ledger.prepareNext(1))
            assertTrue(renderer.setNext(b))
            assertTrue(ledger.acceptPreparedNext(ticket, b))
            assertEquals(a, ledger.current)
            assertEquals(b, renderer.finishCurrent())
            assertEquals(NaviampRemoteHandoffObservation.Promoted(b), ledger.observe(1, 1, b.mediaUrl, true))
            assertEquals(NaviampRemoteHandoffObservation.Current, ledger.observe(1, 2, b.mediaUrl, true))
            assertEquals(1, renderer.loads)
            assertEquals(1, renderer.plays)
            assertEquals(2, renderer.received.size)
            renderer.received.forEach { assertContentEquals(originalFlac, it) }
            assertEquals(plan.contentType, renderer.request("HEAD", b.mediaUrl).headers["Content-Type"])
            endpoint.revoke(a.mediaUrl)
            assertEquals(404, renderer.request("GET", a.mediaUrl).status)
            assertEquals(200, renderer.request("GET", b.mediaUrl).status)
        } finally {
            endpoint.stop()
        }
        assertTrue(source.cleared)
    }

    @Test fun rendererCanReadHeadAndReconstructOriginalUsingClosedOpenAndSuffixRanges() = runTest {
        val renderer = SoftwareRenderer()
        val endpoint = endpoint(renderer, OriginalSource())
        endpoint.start()
        try {
            val url = endpoint.issue(NaviampCastMediaResource(NaviampCastMediaKind.Track, "account-a", "track"))
            val head = renderer.request("HEAD", url)
            assertEquals(originalFlac.size.toString(), head.headers["Content-Length"])
            assertTrue(head.body.isEmpty())
            val first = renderer.request("GET", url, "bytes=0-99")
            val remainder = renderer.request("GET", url, "bytes=100-")
            assertEquals(206, first.status)
            assertEquals("bytes 0-99/${originalFlac.size}", first.headers["Content-Range"])
            assertContentEquals(originalFlac, (first.body + remainder.body).toByteArray())
            val suffix = renderer.request("GET", url, "bytes=-20")
            assertContentEquals(originalFlac.takeLast(20).toByteArray(), suffix.body.toByteArray())
            assertEquals(416, renderer.request("GET", url, "bytes=${originalFlac.size}-").status)
            assertEquals(416, renderer.request("GET", url, "bytes=0-1,4-5").status)
            assertEquals(405, renderer.request("POST", url).status)
            assertEquals(404, renderer.request("GET", url + "/extra").status)
        } finally {
            endpoint.stop()
        }
    }

    @Test fun failedNextActionDoesNotClaimGaplessOrAdvanceAndForeignSourceCannotLeakBytes() = runTest {
        val renderer = SoftwareRenderer(supportsNext = false)
        val endpoint = endpoint(renderer, OriginalSource())
        endpoint.start()
        try {
            val a = NaviampRemoteQueuedMedia(NaviampRemoteQueueOccurrence(1, 1), endpoint.issue(
                NaviampCastMediaResource(NaviampCastMediaKind.Track, "account-a", "track")))
            val b = NaviampRemoteQueuedMedia(NaviampRemoteQueueOccurrence(1, 2), endpoint.issue(
                NaviampCastMediaResource(NaviampCastMediaKind.Track, "account-a", "next")))
            val ledger = NaviampRemotePreparedHandoff()
            ledger.reset(1, a)
            val ticket = assertNotNull(ledger.prepareNext(1))
            if (renderer.setNext(b)) ledger.acceptPreparedNext(ticket, b)
            assertNull(ledger.next)
            assertEquals(a, ledger.current)
            val foreign = endpoint.issue(NaviampCastMediaResource(NaviampCastMediaKind.Track, "account-b", "private"))
            val denied = renderer.request("GET", foreign)
            assertEquals(404, denied.status)
            assertTrue(denied.body.isEmpty())
        } finally {
            endpoint.stop()
        }
    }

    private fun endpoint(renderer: SoftwareRenderer, source: OriginalSource): NaviampCastMediaEndpointController {
        var token = 0
        return NaviampCastMediaEndpointController(renderer, NaviampCastMediaLeaseController(
            NaviampCastSecureTokenSource { "test-only-opaque-token-" + (++token).toString().padStart(32, '0') },
            { 0L },
        ), source)
    }

    private class Reply : NaviampCastHttpResponse {
        var status = 0
        var headers = emptyMap<String, String>()
        val body = mutableListOf<Byte>()
        override suspend fun begin(statusCode: Int, headers: Map<String, String>) {
            status = statusCode
            this.headers = headers
        }
        override suspend fun write(bytes: ByteArray, count: Int) { body += bytes.take(count) }
    }

    /** Models optional SetNext acceptance and renderer-owned promotion independently of the ledger. */
    private class SoftwareRenderer(val supportsNext: Boolean = true) : NaviampCastHttpServerEffect {
        val sink = "http-get:*:audio/mpeg:*,http-get:*:audio/flac:*"
        var loads = 0
        var plays = 0
        val received = mutableListOf<ByteArray>()
        private var current: NaviampRemoteQueuedMedia? = null
        private var next: NaviampRemoteQueuedMedia? = null
        private lateinit var handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit
        override suspend fun start(handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit): String {
            this.handler = handler
            return "http://192.0.2.1:54000"
        }
        override suspend fun stop() = Unit
        suspend fun request(method: String, url: String, range: String? = null): Reply = Reply().also {
            handler(NaviampCastHttpRequest(method, url.removePrefix("http://192.0.2.1:54000"), range), it)
        }
        fun load(media: NaviampRemoteQueuedMedia) { loads++; current = media }
        suspend fun play() { plays++; fetch(checkNotNull(current)) }
        fun setNext(media: NaviampRemoteQueuedMedia): Boolean {
            if (!supportsNext) return false
            next = media
            return true
        }
        suspend fun finishCurrent(): NaviampRemoteQueuedMedia {
            val promoted = checkNotNull(next)
            next = null
            current = promoted
            fetch(promoted)
            return promoted
        }
        private suspend fun fetch(media: NaviampRemoteQueuedMedia) {
            val reply = request("GET", media.mediaUrl)
            check(reply.status == 200)
            received += reply.body.toByteArray()
        }
    }

    private class OriginalSource : NaviampCastMediaByteSource {
        var cleared = false
        override suspend fun clear() { cleared = true }
        override suspend fun stream(
            resource: NaviampCastMediaResource,
            range: NaviampCastRequestedRange?,
            headOnly: Boolean,
            onResponse: suspend (ProviderMediaByteResponse) -> Unit,
            writeChunk: suspend (ByteArray, Int) -> Unit,
        ): Boolean {
            if (resource.sourceId != "account-a") return false
            check(resource.quality == StreamQuality.Original)
            val total = originalFlac.size.toLong()
            val resolved = range?.resolve(total)
            if (range != null && resolved == null) {
                onResponse(ProviderMediaByteResponse(416, null, 0, "bytes */$total"))
                return true
            }
            val bytes = if (resolved == null) originalFlac else
                originalFlac.copyOfRange(resolved.firstByte.toInt(), resolved.lastByteInclusive.toInt() + 1)
            onResponse(ProviderMediaByteResponse(if (range == null) 200 else 206, "audio/flac", bytes.size.toLong(), resolved?.contentRange))
            if (!headOnly) bytes.asList().chunked(31).forEach { chunk -> writeChunk(chunk.toByteArray(), chunk.size) }
            return true
        }
    }

    companion object {
        // Generated 16-bit/44.1-kHz stereo sine, 441 samples. FFmpeg 9.0.2, no external audio.
        // ffmpeg -f lavfi -i 'aevalsrc=0.1*sin(2*PI*1000*t):s=44100:d=0.01' -ac 2
        //   -sample_fmt s16 -c:a flac -compression_level 8 -fflags +bitexact -flags:a +bitexact
        //   -map_metadata -1 -metadata_header_padding 0 original.flac
        internal val originalFlac = Base64.decode(
            "ZkxhQwAAACIBuQG5AAC5AAC5CsRC8AAAAblid2NY9LqBxf9zUfX27u+yhAAADgYAAABmZm1wZWcAAAAA//h5iAABuGRGAAABSQKLA8DmookYWfIrmggAECSEIQhJJN41VR9zJIQhIQhCEJCSQzetFVbTkkkISEIQhCQkkJPSrVVtMmSEIQhIEIQhCSE57aqi9ySSQhCEIEISQJCSSbkYqtXzJCQkIQkIQhCSQnLai6t2SSQhIQhCEISEJMk9tVWj8yQhCEJCBISEhJJ9aqqr5kkhCQhCEhCEhJJPti1VvkwkJIEIEhIQkyTy1Fav8hJAAAAA2PM=",
        )
    }
}
