package app.naviamp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toAwtImage
import app.naviamp.domain.provider.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampDiscoveryBrowserUiTest {
    @Test fun recommendationsExposePartialCountsFiltersAndAccessibleArtistNavigation() {
        for ((width, height) in listOf(360 to 780, 1100 to 850, 1280 to 800)) runDesktopComposeUiTest(width, height) {
            val artist = ProviderDiscoveryArtist("11111111-0000-0000-0000-000000000000", "A discovery artist", genres = listOf("Rock"),
                recommendation = ProviderRecommendationContext(rank = 1, sourceArtists = listOf("A seed")))
            val page = mutableStateOf(discoveryPageFixture().copy(artistPage = false, artistDetail = null,
                browser = DiscoveryBrowserState(recommendations = listOf(artist), total = 205, nextOffset = 100)))
            val commands = mutableListOf<DiscoveryBrowseAction>(); val opened = mutableListOf<String>()
            setContent { MaterialTheme(colorScheme = darkColorScheme()) {
                NaviampDiscoveryBrowserUi(page.value, NaviampColors.Dark, discoveryPageFixtureActions(page, mutableListOf()).copy(
                    onDiscoveryBrowse = { commands += it }, onDiscoveryArtistDetails = { opened += it }))
            } }
            onNodeWithText("1 of 205 recommendations loaded").assertIsDisplayed()
            onNodeWithText("A discovery artist").performScrollTo().performClick()
            assertEquals(listOf(artist.id), opened)
            onNodeWithText("Load more").performScrollTo().performClick()
            assertTrue(DiscoveryBrowseAction.More in commands)
            onNodeWithText("Back").performClick()
            assertTrue(DiscoveryBrowseAction.Close in commands)
            System.getenv("NAVIAMP_DISCOVERY_BROWSER_CAPTURE")?.let { directory ->
                val target = java.io.File(directory, "recommendations-$width.png"); target.parentFile.mkdirs()
                javax.imageio.ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", target)
            }
        }
    }
}
