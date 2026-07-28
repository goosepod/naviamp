package app.naviamp.ui

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlatformCoverArtPalettePreloadTest {
    @Test
    fun coverArtPreloadCachesPaletteWithoutASecondLoad() = runTest {
        val url = "test://preloaded-cover-art-palette"
        val bytes = requireNotNull(
            jvmGeneratedCoverArtBytes(
                "naviamp-radio-tile://cover?label=P&from=7A2248&to=162A52",
            ),
        )
        var loadCount = 0
        setJvmPlatformCoverArtByteLoader { requestedUrl ->
            assertEquals(url, requestedUrl)
            loadCount += 1
            bytes
        }

        try {
            preloadJvmPlatformCoverArt(listOf(url))
            setJvmPlatformCoverArtByteLoader { error("Preloaded palette should not reload artwork.") }

            jvmPlatformCoverArtPlayerColors(url)

            assertEquals(1, loadCount)
        } finally {
            resetJvmPlatformCoverArtByteLoader()
        }
    }

    @Test
    fun coverArtFailureIsNonFatal() = runTest {
        setJvmPlatformCoverArtByteLoader { error("TLS certificate rejected") }

        try {
            preloadJvmPlatformCoverArt(listOf("https://untrusted.example/cover.jpg"))
        } finally {
            resetJvmPlatformCoverArtByteLoader()
        }
    }

    @Test
    fun decoderHonorsRequestedThumbnailSize() {
        val bytes = assertNotNull(
            jvmGeneratedCoverArtBytes(
                "naviamp-radio-tile://cover?label=S&from=7A2248&to=162A52",
            ),
        )

        val decoded = assertNotNull(decodePlatformCoverArt(bytes, targetSidePx = 64))

        assertTrue(maxOf(decoded.image.width, decoded.image.height) <= 64)
    }

    @Test
    fun concurrentCoverArtLoadsAreBounded() = runTest {
        val bytes = assertNotNull(
            jvmGeneratedCoverArtBytes(
                "naviamp-radio-tile://cover?label=C&from=7A2248&to=162A52",
            ),
        )
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        setJvmPlatformCoverArtByteLoader {
            val current = active.incrementAndGet()
            maximum.updateAndGet { previous -> maxOf(previous, current) }
            try {
                kotlinx.coroutines.delay(25)
                bytes
            } finally {
                active.decrementAndGet()
            }
        }

        try {
            coroutineScope {
                (1..12).map { index ->
                    async { jvmPlatformCoverArtPlayerColors("test://concurrent-$index") }
                }.awaitAll()
            }

            assertTrue(maximum.get() <= 4)
        } finally {
            resetJvmPlatformCoverArtByteLoader()
        }
    }
}
