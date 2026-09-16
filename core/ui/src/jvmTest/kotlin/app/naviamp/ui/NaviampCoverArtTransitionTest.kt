package app.naviamp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class NaviampCoverArtTransitionTest {
    @Test
    fun failedInformationArtworkFallsBackToProviderArtwork() = runComposeUiTest {
        val bytes = requireNotNull(
            jvmGeneratedCoverArtBytes(
                "naviamp-radio-tile://cover?label=F&from=CC2244&to=551122",
            ),
        )
        val requestedUrls = mutableListOf<String>()
        resetNaviampCoverArtCache()
        setJvmPlatformCoverArtByteLoader { url ->
            requestedUrls += url
            if (url == "test://provider-cover") bytes else error("external artwork unavailable")
        }

        try {
            setContent {
                NaviampCoverArt(
                    url = "https://external.test/missing.webp",
                    colors = NaviampColors.Dark,
                    size = 128.dp,
                    cornerRadius = 8.dp,
                    fallbackUrl = "test://provider-cover",
                )
            }
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithContentDescription("Album art").fetchSemanticsNodes().size == 1
            }
            assertEquals(
                listOf("https://external.test/missing.webp", "test://provider-cover"),
                requestedUrls,
            )
            onNodeWithContentDescription("Album art").assertExists()
        } finally {
            resetJvmPlatformCoverArtByteLoader()
            resetNaviampCoverArtCache()
        }
    }

    @Test
    fun failedReplacementArtworkDoesNotLeaveThePreviousImageVisible() = runComposeUiTest {
        val artworkUrl = mutableStateOf<String?>("test://working")
        val bytes = requireNotNull(
            jvmGeneratedCoverArtBytes(
                "naviamp-radio-tile://cover?label=B&from=0044CC&to=001133",
            ),
        )
        resetNaviampCoverArtCache()
        setJvmPlatformCoverArtByteLoader { url ->
            if (url == "test://working") bytes else error("missing artwork")
        }

        try {
            setContent {
                NaviampCoverArt(artworkUrl.value, NaviampColors.Dark, 128.dp, 8.dp)
            }
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithContentDescription("Album art").fetchSemanticsNodes().size == 1
            }
            onNodeWithContentDescription("Album art").assertExists()

            runOnIdle { artworkUrl.value = "test://missing" }

            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithContentDescription("Album art").fetchSemanticsNodes().isEmpty()
            }
            onNodeWithContentDescription("Album art").assertDoesNotExist()
        } finally {
            resetJvmPlatformCoverArtByteLoader()
            resetNaviampCoverArtCache()
        }
    }

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
