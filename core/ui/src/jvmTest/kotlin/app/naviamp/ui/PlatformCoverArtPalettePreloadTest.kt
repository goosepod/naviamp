package app.naviamp.ui

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
