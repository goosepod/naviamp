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
    @Test fun chartsAndGenreBlocksRenderOnNarrowWideAndTvWithoutImplicitMutations() {
        for ((width, height) in listOf(360 to 780, 1100 to 850, 1280 to 800)) runDesktopComposeUiTest(width, height) {
            val screen = DiscoveryBrowserState(page = DiscoveryBrowsePage.Trending, charts = ProviderDiscoveryCharts(
                ProviderDiscoveryStatus.Ready, region = "US", songs = listOf(DiscoveryChartSong("key", "Chart song", "Chart artist", previewId = DiscoveryDeezerPreviewId("42"))),
                canSave = true, playlistId = "55"))
            val page = mutableStateOf(discoveryPageFixture().copy(artistPage = false, artistDetail = null, browser = screen))
            val commands = mutableListOf<DiscoveryBrowseAction>()
            setContent { MaterialTheme(colorScheme = darkColorScheme()) {
                NaviampDiscoveryBrowserUi(page.value, NaviampColors.Dark, discoveryPageFixtureActions(page, mutableListOf()).copy(
                    onDiscoveryBrowse = { commands += (it as? DiscoveryBrowseAction.Scoped)?.action ?: it }))
            } }
            onNodeWithText("Back").assertIsFocused()
            onNodeWithText("Save songs chart as playlist").assertIsDisplayed()
            assertTrue(commands.none { it == DiscoveryBrowseAction.SaveChart })
            onNodeWithContentDescription("Play sample").performScrollTo().performClick()
            assertTrue(DiscoveryBrowseAction.ChartPreview("key") in commands)
            captureBrowser("charts", width)
            runOnIdle { page.value = page.value.copy(browser = screen.copy(page = DiscoveryBrowsePage.BlockedGenres,
                genres = listOf("rock"), blockedGenres = ProviderDiscoveryBlockedGenres(ProviderDiscoveryStatus.Ready, listOf("jazz")))) }
            onNodeWithText("Block genre").performScrollTo().performClick()
            assertTrue(DiscoveryBrowseAction.BlockGenre("rock", true) in commands)
            onNodeWithText("Unblock genre").performScrollTo().performClick()
            assertTrue(DiscoveryBrowseAction.BlockGenre("jazz", false) in commands)
            captureBrowser("genre-blocks", width)
            runOnIdle { page.value = page.value.copy(browser = page.value.browser!!.copy(uncertainGenres = mapOf("rock" to true))) }
            onNodeWithText("Block genre").assertIsNotEnabled()
        }
    }

    @Test fun catalogSearchIsExplicitAndMissingIdentitiesRemainInformational() = runDesktopComposeUiTest(360, 780) {
        val page = mutableStateOf(discoveryPageFixture().copy(artistPage = false, artistDetail = null,
            browser = DiscoveryBrowserState(page = DiscoveryBrowsePage.Search)))
        val commands = mutableListOf<DiscoveryBrowseAction>()
        setContent { MaterialTheme(colorScheme = darkColorScheme()) {
            NaviampDiscoveryBrowserUi(page.value, NaviampColors.Dark, discoveryPageFixtureActions(page, mutableListOf()).copy(onDiscoveryBrowse = { commands += (it as? DiscoveryBrowseAction.Scoped)?.action ?: it }))
        } }
        onNodeWithText("Search").assertIsNotEnabled()
        onNodeWithText("Artists or releases outside your Library").performTextInput("Björk")
        assertEquals(DiscoveryBrowseAction.Query("Björk"), commands.last())
        assertTrue(commands.none { it == DiscoveryBrowseAction.Search })
        runOnIdle { page.value = page.value.copy(browser = page.value.browser!!.copy(query = "Björk", submittedQuery = "Björk",
            catalog = ProviderCatalogPage(ProviderDiscoveryStatus.Ready, artists = listOf(ProviderDiscoveryArtist("unresolved", "Björk")), totalCount = 1))) }
        onNodeWithText("Search").performClick()
        assertTrue(DiscoveryBrowseAction.Search in commands)
        onAllNodesWithText("Björk").assertCountEquals(2)
        onNodeWithText("Open in library").assertDoesNotExist()
    }

    @Test fun recommendationsExposePartialCountsFiltersAndAccessibleArtistNavigation() {
        for ((width, height) in listOf(360 to 780, 1100 to 850, 1280 to 800)) runDesktopComposeUiTest(width, height) {
            val artist = ProviderDiscoveryArtist("11111111-0000-0000-0000-000000000000", "A discovery artist", genres = listOf("Rock"),
                recommendation = ProviderRecommendationContext(rank = 1, sourceArtists = listOf("A seed")))
            val page = mutableStateOf(discoveryPageFixture().copy(artistPage = false, artistDetail = null,
                browser = DiscoveryBrowserState(recommendations = listOf(artist), total = 205, nextOffset = 100)))
            val commands = mutableListOf<DiscoveryBrowseAction>(); val opened = mutableListOf<String>()
            setContent { MaterialTheme(colorScheme = darkColorScheme()) {
                NaviampDiscoveryBrowserUi(page.value, NaviampColors.Dark, discoveryPageFixtureActions(page, mutableListOf()).copy(
                    onDiscoveryBrowse = { commands += (it as? DiscoveryBrowseAction.Scoped)?.action ?: it }, onDiscoveryArtistDetails = { opened += it }))
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

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.captureBrowser(name: String, width: Int) {
    System.getenv("NAVIAMP_DISCOVERY_BROWSER_CAPTURE")?.let { directory ->
        val target = java.io.File(directory, "$name-$width.png"); target.parentFile.mkdirs()
        javax.imageio.ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", target)
    }
}
