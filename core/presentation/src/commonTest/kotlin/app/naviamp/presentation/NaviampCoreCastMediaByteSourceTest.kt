package app.naviamp.presentation

import app.naviamp.app.NaviampCastMediaKind
import app.naviamp.app.NaviampCastMediaResource
import app.naviamp.app.NaviampCastRequestedRange
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampCoreCastMediaByteSourceTest {
    @Test
    fun replacementLeaseKeepsPreparedBytesUntilTheLastLeaseIsRevoked() = runTest {
        val provider = FakeCoreMediaProvider()
        val store = FakeCastMediaStore()
        val source = NaviampCoreCastMediaByteSource(NaviampCoreMediaProviderSource { provider }, store)
        var token = 0
        val endpoint = app.naviamp.app.NaviampCastMediaEndpointController(
            object : app.naviamp.app.NaviampCastHttpServerEffect {
                override suspend fun start(handler: suspend (app.naviamp.app.NaviampCastHttpRequest, app.naviamp.app.NaviampCastHttpResponse) -> Unit) = "http://192.0.2.1:1234"
                override suspend fun stop() = Unit
            },
            app.naviamp.app.NaviampCastMediaLeaseController(app.naviamp.app.NaviampCastSecureTokenSource { "casttoken${(++token).toString().padStart(32, '0')}" }, { 0L }), source)
        val resource = NaviampCastMediaResource(NaviampCastMediaKind.Track, provider.cacheNamespace, "flac",
            app.naviamp.domain.StreamQuality.Transcoded(app.naviamp.domain.AudioCodec.Mp3, 320))
        endpoint.start()
        val first = endpoint.issue(resource)
        val replacement = endpoint.issue(resource)
        endpoint.revoke(first)
        assertEquals(1, store.writes)
        assertEquals(0, store.deleted)
        endpoint.revoke(replacement)
        assertEquals(1, store.deleted)
        endpoint.issue(resource)
        endpoint.stop()
        assertEquals(2, store.deleted)
    }
    @Test
    fun chunkedTranscodeIsPreparedOnceAndServesStableHeadAndSeekRanges() = runTest {
        val provider = FakeCoreMediaProvider().also { it.castLengthKnown = false }
        val store = FakeCastMediaStore()
        val source = NaviampCoreCastMediaByteSource(NaviampCoreMediaProviderSource { provider }, store)
        val resource = NaviampCastMediaResource(NaviampCastMediaKind.Track, provider.cacheNamespace, "flac",
            app.naviamp.domain.StreamQuality.Transcoded(app.naviamp.domain.AudioCodec.Mp3, 320))
        source.prepare(resource)
        source.prepare(resource)
        assertEquals(1, store.writes)
        assertEquals(1, provider.streamRequests.size)
        assertEquals(null, provider.castRangeHeader)
        assertTrue(source.stream(resource, null, true,
            onResponse = { assertEquals(200, it.statusCode); assertEquals(3L, it.contentLength) },
            writeChunk = { _, _ -> error("HEAD sent bytes") }))
        val bytes = mutableListOf<Byte>()
        assertTrue(source.stream(resource, NaviampCastRequestedRange.From(1, 2), false,
            onResponse = { assertEquals(206, it.statusCode); assertEquals("bytes 1-2/3", it.contentRange) },
            writeChunk = { chunk, count -> bytes += chunk.take(count) }))
        assertEquals(listOf<Byte>(2, 3), bytes)
        assertTrue(source.stream(resource, NaviampCastRequestedRange.From(3), false,
            onResponse = { assertEquals(416, it.statusCode); assertEquals("bytes */3", it.contentRange) },
            writeChunk = { _, _ -> error("Invalid range sent bytes") }))
        assertEquals(1, provider.streamRequests.size)
        source.release(resource)
        assertEquals(1, store.deleted)
        assertFalse(source.stream(resource, null, false, {}, { _, _ -> }))
    }

    @Test
    fun failedOrOversizedTranscodeDeletesPartialBytesAndNeverBecomesSeekable() = runTest {
        val provider = FakeCoreMediaProvider().also { it.castStreamSuccess = false }
        val store = FakeCastMediaStore()
        val resource = NaviampCastMediaResource(NaviampCastMediaKind.Track, provider.cacheNamespace, "flac",
            app.naviamp.domain.StreamQuality.Transcoded(app.naviamp.domain.AudioCodec.Mp3, 320))
        val source = NaviampCoreCastMediaByteSource(NaviampCoreMediaProviderSource { provider }, store)
        kotlin.test.assertFailsWith<IllegalStateException> { source.prepare(resource) }
        assertEquals(1, store.deleted)
        assertFalse(source.stream(resource, null, false, {}, { _, _ -> }))
        provider.castStreamSuccess = true
        provider.castLengthKnown = false
        val bounded = NaviampCoreCastMediaByteSource(NaviampCoreMediaProviderSource { provider }, store, maxTrackBytes = 2)
        kotlin.test.assertFailsWith<IllegalStateException> { bounded.prepare(resource) }
        assertEquals(2, store.deleted)
        assertFalse(bounded.stream(resource, null, false, {}, { _, _ -> }))
    }
    @Test
    fun trackRangeUsesCurrentProviderAndRejectsSourceChange() = runTest {
        val provider = FakeCoreMediaProvider()
        var current: MediaProvider? = provider
        val source = NaviampCoreCastMediaByteSource(NaviampCoreMediaProviderSource { current })
        val resource = NaviampCastMediaResource(NaviampCastMediaKind.Track, provider.cacheNamespace, "song")
        val bytes = mutableListOf<Byte>()

        assertTrue(source.stream(resource, NaviampCastRequestedRange.From(10, 12), false,
            onResponse = { assertEquals("bytes 10-12/100", it.contentRange) },
            writeChunk = { chunk, count -> bytes += chunk.take(count) },
        ))
        assertEquals("bytes=10-12", provider.castRangeHeader)
        assertEquals(listOf<Byte>(1, 2, 3), bytes)

        assertFalse(source.stream(resource.copy(sourceId = "other-source"), null, false,
            onResponse = { error("No response expected") },
            writeChunk = { _, _ -> error("No bytes expected") },
        ))
        current = null
        assertFalse(source.stream(resource, null, false,
            onResponse = { error("No response expected") },
            writeChunk = { _, _ -> error("No bytes expected") },
        ))
    }

    @Test
    fun artworkSupportsHeadAndRangeWithoutSendingProviderUrl() = runTest {
        val provider = FakeCoreMediaProvider(ownedArtworkBytes = byteArrayOf(
            0xff.toByte(), 0xd8.toByte(), 1, 2, 3, 4,
        ))
        val source = NaviampCoreCastMediaByteSource(NaviampCoreMediaProviderSource { provider })
        val resource = NaviampCastMediaResource(NaviampCastMediaKind.Artwork, provider.cacheNamespace, "cover")
        val bytes = mutableListOf<Byte>()

        assertTrue(source.stream(resource, NaviampCastRequestedRange.From(2, 4), false,
            onResponse = {
                assertEquals(206, it.statusCode)
                assertEquals("image/jpeg", it.contentType)
                assertEquals("bytes 2-4/6", it.contentRange)
            },
            writeChunk = { chunk, count -> bytes += chunk.take(count) },
        ))
        assertEquals(listOf<Byte>(1, 2, 3), bytes)
        bytes.clear()
        assertTrue(source.stream(resource, null, true,
            onResponse = { assertEquals(6, it.contentLength) },
            writeChunk = { chunk, count -> bytes += chunk.take(count) },
        ))
        assertTrue(bytes.isEmpty())
    }
}
