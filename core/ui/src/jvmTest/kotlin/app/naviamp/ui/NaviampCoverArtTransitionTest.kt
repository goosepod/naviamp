package app.naviamp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class NaviampCoverArtTransitionTest {
    @Test
    fun transientMissingArtworkDoesNotResetPlayerColorsToFallback() = runComposeUiTest {
        val artworkUrl = mutableStateOf<String?>("test://blue-current-media")
        val fallback = NaviampPlayerColors.fallback(NaviampColors.Dark)
        var observed = fallback
        val bytes = requireNotNull(
            jvmGeneratedCoverArtBytes(
                "naviamp-radio-tile://cover?label=B&from=0044CC&to=001133",
            ),
        )
        resetNaviampCoverArtCache()
        setJvmPlatformCoverArtByteLoader { bytes }

        try {
            setContent {
                observed = rememberNaviampCoverArtPlayerColors(artworkUrl.value, NaviampColors.Dark)
            }
            waitUntil(timeoutMillis = 5_000) { observed != fallback }
            val loadedColors = observed
            assertNotEquals(fallback, loadedColors)

            mainClock.autoAdvance = false
            runOnIdle { artworkUrl.value = null }
            mainClock.advanceTimeBy(500)

            runOnIdle { assertEquals(loadedColors, observed) }
        } finally {
            resetJvmPlatformCoverArtByteLoader()
            resetNaviampCoverArtCache()
        }
    }
}
