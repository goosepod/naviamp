package app.naviamp.app

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.BassFileCallbacks
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.ProviderMediaByteResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
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
        } finally { lifetime.cancel() }
    }
}
