package app.naviamp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toAwtImage
import app.naviamp.domain.provider.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampDiscoveryBrowserUiTest {
    @Test fun companionRadioContextUsesReadableTextWithoutRepeatingConnectionGuidance() = runDesktopComposeUiTest(1100, 800) {
        val context = "The Radiators - Zigzagging Through Ghostland"
        val page = mutableStateOf(discoveryPageFixture().copy(isCompanion = true, artistPage = false, artistDetail = null,
            contextQuery = context, browser = DiscoveryBrowserState(page = DiscoveryBrowsePage.Search)))
        setContent {
            MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme()) {
                NaviampDiscoveryBrowserUi(page.value, NaviampColors.Dark, discoveryPageFixtureActions(page, mutableListOf()))
            }
        }
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        onNodeWithText(context).assertIsDisplayed().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(NaviampColors.Dark.primaryText, layouts.single().layoutInput.style.color)
        onNodeWithText("Use Aurral for discovery and requests. Your music server keeps control of your library and playback.").assertDoesNotExist()
        captureBrowser("companion-context", 1100)
    }

    @Test fun televisionHubCardsSupportDirectionalActivationAndConsumeSystemBack() = runDesktopComposeUiTest(1280, 800) {
        val page = mutableStateOf(discoveryPageFixture().copy(artistPage = false, artistDetail = null,
            browser = DiscoveryBrowserState(page = DiscoveryBrowsePage.Hub)))
        val commands = mutableListOf<DiscoveryBrowseAction>()
        val dispatcher = NaviampSystemBackDispatcher()
        val generation = mutableStateOf(1)
        val handled = mutableListOf<Int>()
        lateinit var inputMode: InputModeManager
        setContent { CompositionLocalProvider(LocalNaviampSystemBackDispatcher provides dispatcher) {
            inputMode = LocalInputModeManager.current
            MaterialTheme(colorScheme = darkColorScheme()) {
                TelevisionHome(NaviampHomeScreenUi(providerDiscovery = page.value), NaviampColors.Dark,
                    discoveryPageFixtureActions(page, mutableListOf()).copy(onDiscoveryBrowse = {
                        commands += (it as? DiscoveryBrowseAction.Scoped)?.action ?: it
                    }), NaviampMediaActions({}, {}), DiscoverySamplePlaybackUi(), generation.value, onEntryFocusHandled = { handled += it })
            }
        } }
        onNodeWithText("Back").assertIsFocused()
        runOnIdle { assertTrue(inputMode.requestInputMode(InputMode.Keyboard)) }
        onNodeWithText("Back").performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithText("Recommendations").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        assertTrue(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.Recommendations) in commands)
        runOnIdle { generation.value = 2 }
        onNodeWithText("Back").assertIsFocused()
        assertEquals(listOf(1, 2), handled)
        runOnIdle { assertNotNull(dispatcher.currentHandler).invoke() }
        assertEquals(DiscoveryBrowseAction.Close, commands.last())
    }

    @Test fun aurralHomeHasOneEntryAndHubCardsOpenSeparatePages() {
        for ((width, height) in listOf(360 to 780, 1100 to 850, 1280 to 800)) runDesktopComposeUiTest(width, height) {
            val page = mutableStateOf(discoveryPageFixture().copy(artistPage = false, artistDetail = null,
                feed = ProviderDiscoveryFeed(ProviderDiscoveryStatus.Ready, chartsAvailable = true, genreBlockingAvailable = true)))
            val commands = mutableListOf<DiscoveryBrowseAction>()
            var requests = 0
            val send: (DiscoveryBrowseAction) -> Unit = { commands += (it as? DiscoveryBrowseAction.Scoped)?.action ?: it }
            setContent { MaterialTheme(colorScheme = darkColorScheme()) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier) {
                    if (page.value.browser == null) NaviampProviderDiscoveryContent(page.value, NaviampColors.Dark, {}, onBrowse = send)
                    else NaviampDiscoveryBrowserUi(page.value, NaviampColors.Dark, discoveryPageFixtureActions(page, mutableListOf()).copy(
                        onDiscoveryBrowse = send, onDiscoveryRequests = { requests++ }))
                }
            } }
            captureBrowser("home-entry", width)
            onNodeWithText("Aurral").performClick()
            assertEquals(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.Hub), commands.last())
            onNodeWithText("Recommendations").assertDoesNotExist()
            onNodeWithText("Recent requests").assertDoesNotExist()
            runOnIdle { page.value = page.value.copy(browser = DiscoveryBrowserState(page = DiscoveryBrowsePage.Hub)) }
            onNodeWithText("Back").assertIsFocused()
            captureBrowser("hub", width)
            onNodeWithText("Recommendations").performScrollTo().performClick()
            assertEquals(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.Recommendations), commands.last())
            onNodeWithText("Search").performScrollTo().performClick()
            assertEquals(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.Search), commands.last())
            onNodeWithText("Trending").performScrollTo().performClick()
            assertEquals(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.Trending), commands.last())
            onNodeWithText("Recent requests").performScrollTo().performClick()
            assertEquals(1, requests)
            onNode(hasScrollAction()).performScrollToNode(hasText("Genres"))
            onNodeWithText("Genres").performClick()
            assertEquals(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.BlockedGenres), commands.last())
        }
    }

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
        captureBrowser("search", 360)
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
