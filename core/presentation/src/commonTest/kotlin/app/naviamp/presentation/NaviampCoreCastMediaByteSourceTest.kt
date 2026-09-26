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
