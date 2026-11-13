package app.naviamp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toAwtImage
import app.naviamp.domain.provider.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampDiscoveryReleasePageTest {
    @Test fun forbiddenMetadataDoesNotHideReadyTracksOrSessionErrorsInOtherSections() = runDesktopComposeUiTest(1100, 850) {
        val initial = discoveryPageFixture()
        val release = initial.artistDetail!!.details!!.releases.first()
        val screen = mutableStateOf(initial.copy(releasePage = SharedDiscoveryReleaseUi(release, loading = false,
            result = ProviderDiscoveryReleaseDetails(ProviderDiscoveryStatus.Forbidden),
            tracksLoading = false, tracks = ProviderDiscoveryReleaseTracks(ProviderDiscoveryStatus.Ready,
                listOf(ProviderDiscoveryReleaseTrack(null, "Independent track list", 1, 1, null))),
            ownershipLoading = false, ownership = ProviderDiscoveryReleaseOwnership(ProviderDiscoveryStatus.SignInRequired))))
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SharedHomeRoute(NaviampColors.Dark, NaviampHomeScreenUi(providerDiscovery = screen.value),
                    discoveryPageFixtureActions(screen, mutableListOf()), NaviampMediaActions({}, {}), DiscoverySamplePlaybackUi())
            }
        }
        onNodeWithText("Your account cannot access discovery.").assertIsDisplayed()
        onNodeWithText("Your discovery session expired. Refresh or reconnect.").performScrollTo().assertIsDisplayed()
        onNodeWithText("Independent track list").performScrollTo().assertIsDisplayed()
        onNodeWithText("Request album").assertIsNotEnabled()
    }

    @Test fun releaseTracksKeepDiscMetadataMissingPreviewsAndIsolatedActionsVisible() {
        for ((width, height, television) in listOf(Triple(360, 780, false), Triple(1100, 850, false), Triple(1280, 800, true))) {
            runDesktopComposeUiTest(width, height) {
                val initial = discoveryPageFixture()
                val release = initial.artistDetail!!.details!!.releases.first()
                val screen = mutableStateOf(initial.copy(artistDetail = initial.artistDetail.copy(details = initial.artistDetail.details!!.copy(canRequestTracks = true)),
                    releasePage = SharedDiscoveryReleaseUi(release,
                    loading = false, result = ProviderDiscoveryReleaseDetails(ProviderDiscoveryStatus.Ready, release,
                        editions = listOf(ProviderDiscoveryEdition(DiscoveryReleaseMbid("99999999-9999-9999-9999-999999999999"), "Deluxe edition", "2026-02-03", "Official"))),
                    tracksLoading = false, tracks = ProviderDiscoveryReleaseTracks(ProviderDiscoveryStatus.Ready, listOf(
                        ProviderDiscoveryReleaseTrack(DiscoveryRecordingMbid("55555555-5555-5555-5555-555555555555"), "Playable preview", 1, 1, 150000, DiscoveryDeezerPreviewId("42")),
                        ProviderDiscoveryReleaseTrack(null, "No preview track", 2, 1, 70000))),
                    requestContext = ProviderDiscoveryReleaseContext(initial.artistDetail.artist, release), ownershipLoading = false,
                    ownership = ProviderDiscoveryReleaseOwnership(ProviderDiscoveryStatus.Ready, ProviderDiscoveryReleaseAvailability.Partial))))
                val actions = mutableListOf<String>()
                val callbacks = discoveryPageFixtureActions(screen, actions)
                setContent {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        val home = NaviampHomeScreenUi(providerDiscovery = screen.value)
                        if (television) TelevisionHome(home, NaviampColors.Dark, callbacks, NaviampMediaActions({}, {}), DiscoverySamplePlaybackUi())
                        else SharedHomeRoute(NaviampColors.Dark, home, callbacks, NaviampMediaActions({}, {}), DiscoverySamplePlaybackUi())
                    }
                }
                onNodeWithText("Deluxe edition", substring = true).assertDoesNotExist()
                onNodeWithText("Disc 1").performScrollTo().assertIsDisplayed()
                onNodeWithContentDescription("Play sample").performScrollTo().performClick()
                runOnIdle { assertEquals(listOf("sample"), actions) }
                onNodeWithText("2:30").assertIsDisplayed()
                onAllNodesWithContentDescription("Request track").assertCountEquals(1)
                onNodeWithContentDescription("Request track").performScrollTo().assertIsEnabled().performClick()
                onNodeWithContentDescription("Request track").assertIsNotEnabled()
                onNodeWithText("Request queued.").performScrollTo().assertIsDisplayed()
                onNodeWithText("Disc 2").performScrollTo().assertIsDisplayed()
                onNodeWithText("Preview unavailable").performScrollTo().assertIsDisplayed()
                onNodeWithText("1:10").assertIsDisplayed()
                onNodeWithText("Editions").performScrollTo()
                assertTrue(onNodeWithText("Editions").fetchSemanticsNode().boundsInRoot.top >
                    onNodeWithText("1:10").fetchSemanticsNode().boundsInRoot.top)
                onNodeWithText("Editions").performClick()
                onNodeWithText("Deluxe edition", substring = true).performScrollTo().assertIsDisplayed()
                onNodeWithText("Deluxe edition · 2026-02-03 · Official").assertDoesNotExist()
                onNodeWithText("Editions").performClick()
                onNodeWithText("Deluxe edition", substring = true).assertDoesNotExist()
                System.getenv("NAVIAMP_DISCOVERY_RELEASE_CAPTURE")?.let { directory ->
                    java.io.File(directory).mkdirs()
                    javax.imageio.ImageIO.write(onRoot().captureToImage().toAwtImage(), "png",
                        java.io.File(directory, "release-tracks-$width-$height-$television.png"))
                }
            }
        }
    }
}
