package app.naviamp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.input.key.Key
import app.naviamp.domain.provider.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampDiscoveryRequestsUiTest {
    @Test fun requestsAreAFullDestinationAboveTheHubAndArtistPage() = runDesktopComposeUiTest(360, 780) {
        val page = mutableStateOf(discoveryPageFixture().copy(requestsOpen = true,
            browser = DiscoveryBrowserState(page = DiscoveryBrowsePage.Hub)))
        var closed = 0
        val dispatcher = NaviampSystemBackDispatcher()
        setContent { CompositionLocalProvider(LocalNaviampSystemBackDispatcher provides dispatcher) { MaterialTheme(colorScheme = darkColorScheme()) {
            SharedHomeRoute(NaviampColors.Dark, NaviampHomeScreenUi(providerDiscovery = page.value),
                discoveryPageFixtureActions(page, mutableListOf()).copy(onDiscoveryRequestsDismiss = { closed++ }),
                NaviampMediaActions({}, {}), DiscoverySamplePlaybackUi())
        } } }
        onNode(isDialog()).assertDoesNotExist()
        onNodeWithText("Recent requests").assertIsDisplayed()
        onNodeWithText("Discovery artist").assertDoesNotExist()
        onNodeWithText("Recommendations").assertDoesNotExist()
        onNodeWithText("Back").performClick()
        assertEquals(1, closed)
        runOnIdle { assertNotNull(dispatcher.currentHandler).invoke() }
        assertEquals(2, closed)
    }

    @Test fun savedRequestsStayVisibleWhenOfflineAndCallbacksCarrySourceAndGeneration() {
        for ((width, height) in listOf(360 to 760, 1100 to 800, 1280 to 720)) runDesktopComposeUiTest(width, height) {
            val initial = discoveryPageFixture()
            val release = initial.artistDetail!!.details!!.releases.first()
            val record = DiscoveryAcquisitionRecord("request", initial.artistDetail.artist.id, "Artist", release.id, true,
                release.id, "Album request", ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.Saved),
                DiscoveryAcquisitionPhase.Complete, 42L)
            val page = mutableStateOf(initial.copy(sourceId = "account-one", sourceGeneration = 17, requestsOpen = true,
                acquisitions = listOf(record), requestsStatus = ProviderDiscoveryStatus.Unavailable))
            val selected = mutableListOf<Triple<String?, Long, String>>()
            var refreshed = 0; var external = 0; var dismissed = 0
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    NaviampDiscoveryRequestsUi(page.value, NaviampColors.Dark,
                        discoveryPageFixtureActions(page, mutableListOf()).copy(
                            onDiscoveryRequestSelected = { source, generation, id -> selected += Triple(source, generation, id) },
                            onDiscoveryRequestsRefresh = { refreshed++ },
                            onDiscoveryRequestsExternal = { source, generation -> assertEquals("account-one", source); assertEquals(17L, generation); external++ },
                            onDiscoveryRequestsDismiss = { dismissed++ }), television = width == 1280)
                }
            }
            onNodeWithText("Back").assertIsFocused()
            if (width == 1280) {
                onNodeWithText("Back").performKeyInput { pressKey(Key.DirectionRight) }
                onNodeWithContentDescription("Refresh").assertIsFocused()
                onNodeWithContentDescription("Refresh").performKeyInput { pressKey(Key.DirectionLeft) }
                onNodeWithText("Back").assertIsFocused()
            }
            onNodeWithText("Album request").assertIsDisplayed()
            onNodeWithText("Request finished; checking Library.").assertIsDisplayed()
            onNodeWithText("Status could not be refreshed. Showing saved information.").assertIsDisplayed()
            onNodeWithText("Open in library").assertDoesNotExist()
            onNodeWithText("View artist").performClick()
            assertEquals(1, selected.size)
            assertEquals("account-one", selected.single().first)
            assertEquals(17L, selected.single().second)
            assertEquals("request", selected.single().third)
            onNodeWithContentDescription("Refresh").performClick(); assertEquals(1, refreshed)
            onNodeWithText("Open in Aurral").performClick(); assertEquals(1, external)
            capture(onAllNodes(isRoot()).onLast().captureToImage(), "requests-$width-$height")
            runOnIdle { page.value = page.value.copy(requestsLoading = true) }
            onNodeWithContentDescription("Refresh").assertIsNotEnabled()
            onNodeWithText("Album request").assertIsDisplayed()
            onNodeWithText("Back").performClick(); assertEquals(1, dismissed)
        }
    }
    @Test fun emptyAndWaitingRequestsDoNotOfferAnAcquireOrPlaybackAction() = runDesktopComposeUiTest(360, 760) {
        val initial = discoveryPageFixture()
        val page = mutableStateOf(initial.copy(requestsOpen = true))
        setContent { MaterialTheme(colorScheme = darkColorScheme()) {
            NaviampDiscoveryRequestsUi(page.value, NaviampColors.Dark, discoveryPageFixtureActions(page, mutableListOf()))
        } }
        onNodeWithText("No recent requests for this account.").assertIsDisplayed()
        runOnIdle {
            val record = DiscoveryAcquisitionRecord("future", initial.artistDetail!!.artist.id, "Artist", "release", true,
                releaseTitle = "Future album", operation = ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.WaitingForRelease,
                    waitingForRelease = ProviderDiscoveryWaitingForRelease(DiscoveryLibraryManager.Aurral, "2027-03")),
                phase = DiscoveryAcquisitionPhase.WaitingForRelease, createdAtMillis = 42L)
            page.value = page.value.copy(acquisitions = listOf(record))
        }
        onNodeWithText("Waiting for release: 2027-03").assertIsDisplayed()
        onNodeWithText("Aurral manages this request.").assertIsDisplayed()
        onNodeWithText("Request album").assertDoesNotExist()
        onNodeWithText("Open in library").assertDoesNotExist()
    }
    @Test fun serverHistoryCompletionDoesNotClaimAnUnperformedLibraryCheck() = runDesktopComposeUiTest(360, 760) {
        val initial = discoveryPageFixture()
        val page = mutableStateOf(initial.copy(requestsOpen = true, requestActivity = ProviderDiscoveryActivities(
            ProviderDiscoveryStatus.Ready, listOf(ProviderDiscoveryActivity("remote", "completed", null,
                title = "Server request", kind = "album_requested")))))
        setContent { MaterialTheme(colorScheme = darkColorScheme()) {
            NaviampDiscoveryRequestsUi(page.value, NaviampColors.Dark, discoveryPageFixtureActions(page, mutableListOf()))
        } }
        onNodeWithText("Server request").assertIsDisplayed()
        onNodeWithText("Request finished.").assertIsDisplayed()
        onNodeWithText("Request finished; checking Library.").assertDoesNotExist()
        onNodeWithText("Open in library").assertDoesNotExist()
    }
    private fun capture(bitmap: androidx.compose.ui.graphics.ImageBitmap, name: String) {
        val directory = java.io.File("build/reports/tests/discovery-requests").apply { mkdirs() }
        javax.imageio.ImageIO.write(bitmap.toAwtImage(), "png", directory.resolve("$name.png"))
    }
}
