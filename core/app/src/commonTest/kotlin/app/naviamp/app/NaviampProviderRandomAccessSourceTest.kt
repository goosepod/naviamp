package app.naviamp.app

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.BassFileCallbacks
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.ProviderMediaByteResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import app.naviamp.domain.playback.PlaybackRandomAccessSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampProviderRandomAccessSourceTest {
    private val request = StreamRequest(TrackId("track"), StreamQuality.Original)

    @Test fun closingANativeFileCursorCancelsItsBlockedReadWithoutRevokingOtherCursors() = runTest {
        withContext(Dispatchers.Default) {
            val entered = CompletableDeferred<Unit>()
            val source = object : PlaybackRandomAccessSource {
                override val lengthBytes = 100L
                override suspend fun read(offset: Long, count: Int): ByteArray {
                    entered.complete(Unit)
                    awaitCancellation()
                }
            }
            val cursor = BassFileCallbacks(source)
            val reader = async { cursor.read(1) }
            withTimeout(3_000) { entered.await() }
            cursor.close()
            assertTrue(withTimeout(3_000) { reader.await() }.isEmpty())
            assertTrue(BassFileCallbacks(source).seek(80), "Closing one native cursor must not close its shared source")
        }
    }

    @Test fun nativeFileCursorReadsAndSeeksUsingBoundedAuthenticatedRanges() = runTest {
        val bytes = ByteArray(3_000_000) { (it % 251).toByte() }
        val ranges = mutableListOf<String>()
        val provider = object : MediaProvider by RecordingProvider(false) {
            override suspend fun streamTrackBytes(request: StreamRequest, rangeHeader: String?, headOnly: Boolean,
                onResponse: suspend (ProviderMediaByteResponse) -> Unit, writeChunk: suspend (ByteArray, Int) -> Unit): Boolean {
                val range = requireNotNull(rangeHeader)
                ranges += range
                val first = range.substringAfter('=').substringBefore('-').toInt()
                val last = range.substringAfter('-').toInt()
                onResponse(ProviderMediaByteResponse(206, "audio/flac", (last - first + 1).toLong(), "bytes $first-$last/${bytes.size}"))
                if (!headOnly) writeChunk(bytes.copyOfRange(first, last + 1), last - first + 1)
                return true
            }
        }
        val lifetime = Job()
        try {
            val source = assertNotNull(NaviampProviderRandomAccessSource.open(provider, request, lifetime) { true })
            val cursor = BassFileCallbacks(source)
            assertEquals(bytes.size.toLong(), cursor.length())
            assertContentEquals(bytes.copyOfRange(0, 20), cursor.read(20))
            assertTrue(cursor.seek(2_900_000))
            assertContentEquals(bytes.copyOfRange(2_900_000, 2_900_020), cursor.read(20))
            assertEquals(3, ranges.size)
            assertTrue(cursor.seek(0))
            assertEquals(65_536, cursor.read(2_000_000).size, "Large decoder requests must use bounded transfer arrays")
            assertTrue(ranges.all { range ->
                range.substringAfter('-').toLong() - range.substringAfter('=').substringBefore('-').toLong() < 131_072
            })
            for (block in 1..17) source.read(block * 131_072L, 1)
            val before = ranges.size
            source.read(0, 1)
            assertEquals(before + 1, ranges.size, "Old blocks must be evicted from the bounded cache")
            lifetime.cancel()
            assertFailsWith<CancellationException> { source.read(0, 1) }
        } finally { lifetime.cancel() }
    }

    @Test fun serversThatIgnoreRangeRequestsKeepTheSequentialStreamingPath() = runTest {
        val provider = object : MediaProvider by RecordingProvider(false) {
            override suspend fun streamTrackBytes(request: StreamRequest, rangeHeader: String?, headOnly: Boolean,
                onResponse: suspend (ProviderMediaByteResponse) -> Unit, writeChunk: suspend (ByteArray, Int) -> Unit): Boolean {
                onResponse(ProviderMediaByteResponse(200, "audio/mpeg", 100, null))
                return true
            }
        }
        val lifetime = Job()
        try { assertNull(NaviampProviderRandomAccessSource.open(provider, request, lifetime) { true }) }
        finally { lifetime.cancel() }
    }

    @Test fun rejectsWrongRangeMetadataBeforeAcceptingNativeFileBytes() = runTest {
        val provider = object : MediaProvider by RecordingProvider(false) {
            override suspend fun streamTrackBytes(request: StreamRequest, rangeHeader: String?, headOnly: Boolean,
                onResponse: suspend (ProviderMediaByteResponse) -> Unit, writeChunk: suspend (ByteArray, Int) -> Unit): Boolean {
                onResponse(ProviderMediaByteResponse(206, "audio/flac", 1, "bytes 0-0/1000000"))
                if (!headOnly) writeChunk(byteArrayOf(1), 1)
                return true
            }
        }
        val lifetime = Job()
        try {
            val source = assertNotNull(NaviampProviderRandomAccessSource.open(provider, request, lifetime) { true })
            assertFailsWith<IllegalStateException> { source.read(0, 20) }
            assertNotNull(source.failure)
        } finally { lifetime.cancel() }
    }

    @Test fun invalidProbeLengthsCannotAdvertiseRandomAccessSupport() = runTest {
        for (length in listOf(0L, 2L, 100L)) {
            val provider = object : MediaProvider by RecordingProvider(false) {
                override suspend fun streamTrackBytes(request: StreamRequest, rangeHeader: String?, headOnly: Boolean,
                    onResponse: suspend (ProviderMediaByteResponse) -> Unit, writeChunk: suspend (ByteArray, Int) -> Unit): Boolean {
                    assertTrue(headOnly)
                    onResponse(ProviderMediaByteResponse(206, "audio/wav", length, "bytes 0-0/100"))
                    return true
                }
            }
            val lifetime = Job()
            try { assertNull(NaviampProviderRandomAccessSource.open(provider, request, lifetime) { true }, "length=$length") }
            finally { lifetime.cancel() }
        }
    }

    @Test fun truncatedAndOversizedTransfersNeverCachePartialBlocksAndAReopenedSourceRecovers() = runTest {
        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        var fault = "truncated"
        var bodyReads = 0
        val provider = object : MediaProvider by RecordingProvider(false) {
            override suspend fun streamTrackBytes(request: StreamRequest, rangeHeader: String?, headOnly: Boolean,
                onResponse: suspend (ProviderMediaByteResponse) -> Unit, writeChunk: suspend (ByteArray, Int) -> Unit): Boolean {
                val first = requireNotNull(rangeHeader).substringAfter('=').substringBefore('-').toInt()
                val last = rangeHeader.substringAfter('-').toInt()
                onResponse(ProviderMediaByteResponse(206, "audio/wav", (last-first+1).toLong(), "bytes $first-$last/${bytes.size}"))
                if (!headOnly) {
                    bodyReads++
                    val body = bytes.copyOfRange(first, last+1)
                    when (fault) {
                        "truncated" -> writeChunk(body, body.size/2)
                        "oversized" -> writeChunk(body + byteArrayOf(1), body.size+1)
                        else -> writeChunk(body, body.size)
                    }
                }
                return true
            }
        }
        val lifetime = Job()
        try {
            val failed = assertNotNull(NaviampProviderRandomAccessSource.open(provider, request, lifetime) { true })
            assertFailsWith<IllegalStateException> { failed.read(0, 20) }
            assertNotNull(failed.failure)
            fault = "oversized"
            assertFailsWith<IllegalStateException> { failed.read(0, 20) }
            assertEquals(2, bodyReads, "A failed partial block must not be cached")
            fault = "healthy"
            val recovered = assertNotNull(NaviampProviderRandomAccessSource.open(provider, request, lifetime) { true })
            assertContentEquals(bytes.copyOfRange(200_000, 200_040), recovered.read(200_000, 40))
            assertContentEquals(bytes.copyOfRange(0, 40), recovered.read(0, 40))
            assertNull(recovered.failure, "A fresh playback lease must not inherit an earlier transport failure")
        } finally { lifetime.cancel() }
    }

    @Test fun cachedBytesRemainRevocableAndCancellationIsNotATransportFailure() = runTest {
        var current = true
        val provider = object : MediaProvider by RecordingProvider(false) {
            override suspend fun streamTrackBytes(request: StreamRequest, rangeHeader: String?, headOnly: Boolean,
                onResponse: suspend (ProviderMediaByteResponse) -> Unit, writeChunk: suspend (ByteArray, Int) -> Unit): Boolean {
                val count = if (headOnly) 1 else 100
                onResponse(ProviderMediaByteResponse(206, "audio/wav", count.toLong(), "bytes 0-${count-1}/100"))
                if (!headOnly) writeChunk(ByteArray(100) { 7 }, 100)
                return true
            }
        }
        val lifetime = Job()
        try {
            val source = assertNotNull(NaviampProviderRandomAccessSource.open(provider, request, lifetime) { current })
            assertContentEquals(byteArrayOf(7), source.read(0, 1))
            lifetime.cancel()
            assertFailsWith<CancellationException> { source.read(0, 1) }
            assertNull(source.failure)
        } finally { lifetime.cancel() }
        val another = Job()
        try {
            val source = assertNotNull(NaviampProviderRandomAccessSource.open(provider, request, another) { current })
            source.read(0, 1)
            current = false
            assertFailsWith<IllegalStateException> { source.read(0, 1) }
        } finally { another.cancel() }
    }
}
