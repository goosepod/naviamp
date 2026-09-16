package app.naviamp.ui

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlatformCoverArtFormatTest {
    @Test
    fun supportedFormatsDecodeAndSupplyTheSameRedPalette() {
        for (format in listOf("png", "jpg", "lossless.webp", "lossy.webp", "animated.webp")) {
            val bytes = fixture(format)
            val decoded = assertNotNull(decodePlatformCoverArt(bytes, 20), format)
            assertEquals(20, decoded.image.width, format)
            assertEquals(10, decoded.image.height, format)
            assertTrue(decoded.rgbSamples.isNotEmpty(), format)
            assertTrue(decoded.rgbSamples.all { it.red > 180 && it.green < 60 && it.blue < 80 }, format)
            val again = assertNotNull(decodePlatformCoverArt(bytes, 20), format)
            assertEquals(decoded.rgbSamples, again.rgbSamples, format)
        }
    }

    @Test
    fun malformedArtworkFailsWithoutCrashing() {
        assertNull(decodePlatformCoverArt(byteArrayOf(), 20))
        assertNull(decodePlatformCoverArt("not an image".encodeToByteArray(), 20))
        assertNull(decodePlatformCoverArt(fixture("lossless.webp").copyOf(16), 20))
    }

    @Test
    fun webpFeedsTheSharedPlayerPaletteAndNativeVisualizer() = runTest {
        val bytes = fixture("lossless.webp")
        setJvmPlatformCoverArtByteLoader { bytes }
        try {
            val colors = jvmPlatformCoverArtPlayerColors("test://webp-palette")
            assertNotEquals(NaviampPlayerColors.fallback(NaviampColors.Dark), colors)
            val image = jvmPlatformCoverArtShaderImage("test://webp-visualizer")
            assertEquals(80, image.width)
            assertEquals(40, image.height)
        } finally {
            resetJvmPlatformCoverArtByteLoader()
        }
    }

    private fun fixture(format: String): ByteArray {
        val file = if (format.endsWith("webp")) "cover-$format" else "cover.$format"
        return requireNotNull(javaClass.getResourceAsStream("/artwork/$file")).use { it.readBytes() }
    }
}
