package app.naviamp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import app.naviamp.domain.provider.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampDiscoveryWaitingForReleaseUiTest {
    @Test fun artistCardsKeepWaitingDateAndDisabledRequestOnNarrowWideAndTelevision() {
        for ((width, height, television) in layouts) runDesktopComposeUiTest(width, height) {
            val initial = discoveryPageFixture()
            val release = initial.artistDetail!!.details!!.releases.first()
            val action = ProviderDiscoveryAction.RequestAlbum(release.id)
            val operation = waitingOperation("2027-03-05")
            val screen = mutableStateOf(initial.copy(artistDetail = initial.artistDetail.copy(
                operationStatus = operation.status, requestOperations = mapOf(action to operation), blockedActions = setOf(action))))
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    val home = NaviampHomeScreenUi(providerDiscovery = screen.value)
                    if (television) TelevisionHome(home, NaviampColors.Dark, discoveryPageFixtureActions(screen, mutableListOf()), NaviampMediaActions({}, {}), DiscoverySamplePlaybackUi())
                    else SharedHomeRoute(NaviampColors.Dark, home, discoveryPageFixtureActions(screen, mutableListOf()), NaviampMediaActions({}, {}), DiscoverySamplePlaybackUi())
                }
            }
            onNodeWithText("Aurral manages this request.").assertIsDisplayed()
            onNodeWithText("Waiting for release: " + calendarDateExpectation("2027-03-05")).performScrollTo().assertIsDisplayed()
            onNodeWithContentDescription("Waiting for release").assertIsNotEnabled()
            capture(onRoot().captureToImage(), "artist-$width-$height-$television")
        }
    }

    @Test fun releaseWaitingKeepsUnknownDatesAndRequestsTruthfulUntilOwnershipIsConfirmed() {
        for ((width, height, television) in layouts) for (date in listOf("2027-03-05", null)) runDesktopComposeUiTest(width, height) {
            val initial = discoveryPageFixture()
            val release = initial.artistDetail!!.details!!.releases.first()
            val action = ProviderDiscoveryAction.RequestAlbum(release.id)
            val operation = waitingOperation(date)
            val screen = mutableStateOf(initial.copy(artistDetail = initial.artistDetail.copy(
                details = initial.artistDetail.details!!.copy(canRequestTracks = true),
                requestOperations = mapOf(action to operation), blockedActions = setOf(action)),
                releasePage = SharedDiscoveryReleaseUi(release, loading = false,
                    result = ProviderDiscoveryReleaseDetails(ProviderDiscoveryStatus.Ready, release),
                    tracksLoading = false, tracks = ProviderDiscoveryReleaseTracks(ProviderDiscoveryStatus.Ready, listOf(
                        ProviderDiscoveryReleaseTrack(DiscoveryRecordingMbid("55555555-5555-5555-5555-555555555555"), "Future track", 1, 1, null))),
                    requestContext = ProviderDiscoveryReleaseContext(initial.artistDetail.artist, release), ownershipLoading = false,
                    ownership = ProviderDiscoveryReleaseOwnership(ProviderDiscoveryStatus.Ready, ProviderDiscoveryReleaseAvailability.Catalogued))))
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    val home = NaviampHomeScreenUi(providerDiscovery = screen.value)
                    if (television) TelevisionHome(home, NaviampColors.Dark, discoveryPageFixtureActions(screen, mutableListOf()), NaviampMediaActions({}, {}), DiscoverySamplePlaybackUi())
                    else SharedHomeRoute(NaviampColors.Dark, home, discoveryPageFixtureActions(screen, mutableListOf()), NaviampMediaActions({}, {}), DiscoverySamplePlaybackUi())
                }
            }
            val label = date?.let { "Waiting for release: " + calendarDateExpectation(it) } ?: "Waiting for release"
            onNode(hasText("Waiting for release") and hasClickAction()).performScrollTo().assertIsNotEnabled()
            onAllNodesWithText(label).onLast().performScrollTo().assertIsDisplayed()
            onNodeWithText("Aurral manages this request.").assertIsDisplayed()
            onNodeWithText("Saved in the server library; no files available.").assertDoesNotExist()
            onNodeWithText("Request queued.").assertDoesNotExist()
            onNodeWithText("Open in library").assertDoesNotExist()
            capture(onRoot().captureToImage(), "release-$width-$height-$television-${date ?: "unknown"}")
            onNodeWithContentDescription("Request track").performScrollTo().assertIsNotEnabled()

            runOnIdle { screen.value = screen.value.copy(releasePage = screen.value.releasePage!!.copy(
                ownership = ProviderDiscoveryReleaseOwnership(ProviderDiscoveryStatus.Ready, ProviderDiscoveryReleaseAvailability.Available))) }
            onNodeWithText("Available in the server library.").performScrollTo().assertIsDisplayed()
            onAllNodesWithText(label).assertCountEquals(0)
            onNodeWithText("Aurral manages this request.").assertDoesNotExist()
            // A native availability hint alone still cannot create a playable Library destination.
            onNodeWithText("Open in library").assertDoesNotExist()
            onNodeWithContentDescription("Request track").performScrollTo().assertIsNotEnabled()
        }
    }
}

private val layouts = listOf(Triple(360, 780, false), Triple(1100, 850, false), Triple(1280, 800, true))
private fun waitingOperation(date: String?) = ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.WaitingForRelease,
    ProviderDiscoveryRequestIdentity(canonicalAlbumId = DiscoveryCanonicalAlbumId("71")), 201,
    ProviderDiscoveryWaitingForRelease(DiscoveryLibraryManager.Aurral, date))

private fun capture(image: androidx.compose.ui.graphics.ImageBitmap, name: String) {
    System.getenv("NAVIAMP_WAITING_RELEASE_CAPTURE")?.let { directory ->
        val file = java.io.File(directory, "$name.png")
        file.parentFile.mkdirs()
        javax.imageio.ImageIO.write(image.toAwtImage(), "png", file)
    }
}
