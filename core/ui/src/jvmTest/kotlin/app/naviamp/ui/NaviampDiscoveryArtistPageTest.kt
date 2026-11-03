package app.naviamp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import app.naviamp.domain.provider.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampDiscoveryArtistPageTest {
    @Test fun televisionDestinationAndSidebarReentryHandFocusToBack() = runDesktopComposeUiTest(1280, 800) {
        val screen = mutableStateOf(discoveryPageFixture())
        val generation = mutableStateOf(1)
        val handled = mutableListOf<Int>()
        val actions = discoveryPageFixtureActions(screen, mutableListOf())
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TelevisionHome(NaviampHomeScreenUi(providerDiscovery = screen.value), NaviampColors.Dark,
                    actions, NaviampMediaActions({}, {}), DiscoverySamplePlaybackUi(), generation.value,
                    onEntryFocusHandled = { handled += it })
            }
        }
        onNodeWithText("Back").assertIsFocused()
        runOnIdle { assertEquals(listOf(1), handled) }
        onNodeWithText("Release 1").performScrollTo().performClick()
        onNodeWithText("Back").assertIsFocused()
        onNodeWithText("Back").performKeyInput { pressKey(Key.Enter) }
        onNodeWithText("Discovery artist").assertIsDisplayed()
        onNodeWithText("Back").assertIsFocused()
        onNodeWithContentDescription("Play sample").performScrollTo()
            .performSemanticsAction(SemanticsActions.RequestFocus)
        runOnIdle { generation.value = 2 }
        onNodeWithText("Back").assertIsFocused()
        runOnIdle { assertEquals(listOf(1, 1, 1, 2), handled) }
    }

    @Test fun narrowWideAndTelevisionPagesKeepArtistReleaseAndKeyboardActionsReachable() {
        for ((width, height, television) in listOf(Triple(360, 780, false), Triple(1100, 850, false), Triple(1280, 800, true))) {
            runDesktopComposeUiTest(width, height) {
                val screen = mutableStateOf(discoveryPageFixture())
                val actions = mutableListOf<String>()
                val homeActions = discoveryPageFixtureActions(screen, actions)
                setContent {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        val home = NaviampHomeScreenUi(providerDiscovery = screen.value)
                        if (television) TelevisionHome(home, NaviampColors.Dark, homeActions, NaviampMediaActions({}, {}), DiscoverySamplePlaybackUi())
                        else SharedHomeRoute(NaviampColors.Dark, home, homeActions, NaviampMediaActions({}, {}), DiscoverySamplePlaybackUi())
                    }
                }
                onNodeWithText("Discovery artist").assertIsDisplayed()
                onNodeWithText("Back").assertIsDisplayed()
                onNodeWithContentDescription("Play sample").performScrollTo()
                    .performSemanticsAction(SemanticsActions.RequestFocus)
                onNodeWithContentDescription("Play sample").performKeyInput { pressKey(Key.Enter) }
                runOnIdle { assertTrue("sample" in actions) }
                System.getenv("NAVIAMP_DISCOVERY_CAPTURE")?.let { directory ->
                    java.io.File(directory).mkdirs()
                    javax.imageio.ImageIO.write(onRoot().captureToImage().toAwtImage(), "png",
                        java.io.File(directory, "artist-$width-$height-$television.png"))
                }
                onNodeWithText("Release 1").performScrollTo().performClick()
                onNodeWithText("Release 1").assertIsDisplayed()
                onNodeWithText("Release overview").assertIsDisplayed()
                onNodeWithText("2026-01-01 · Albums").assertIsDisplayed()
                onNodeWithText("Request album").performScrollTo().performClick()
                onNodeWithText("Request queued.").assertIsDisplayed()
                onNodeWithText("Back").performClick()
                onNodeWithText("Discovery artist").assertIsDisplayed()
                onNodeWithTag("discovery-artist-list").performScrollToNode(hasText("Related artist"))
                onNodeWithText("Related artist").performClick()
                runOnIdle { assertTrue("related" in actions) }
                onNodeWithText("Back").performSemanticsAction(SemanticsActions.RequestFocus)
                    .performKeyInput { pressKey(Key.Enter) }
                runOnIdle { assertTrue("back" in actions) }
            }
        }
    }
}

internal fun discoveryPageFixture(): SharedProviderDiscoveryUi {
    val artist = ProviderDiscoveryArtist("11111111-1111-1111-1111-111111111111", "Discovery artist",
        genres = listOf("Rock", "Electronic"), detailsAvailable = true)
    val releases = (1..8).map { ProviderDiscoveryRelease("44444444-4444-4444-4444-${it.toString().padStart(12, '0')}",
        "Release $it", "2026-01-01", "Album") }
    return SharedProviderDiscoveryUi("aurral", artistPage = true, sourceId = "fixture", artistDetail = SharedProviderDiscoveryArtistUi(
        artist, loading = false, details = ProviderDiscoveryArtistDetails(artist, biography = "Artist biography", releases = releases, canRequestAlbums = true),
        samplesStatus = ProviderDiscoveryStatus.Ready, samples = listOf(ProviderDiscoverySample("42", "Sample track")),
        similar = ProviderDiscoverySimilarArtists(ProviderDiscoveryStatus.Ready, listOf(ProviderDiscoveryArtist(
            "22222222-2222-2222-2222-222222222222", "Related artist", detailsAvailable = true)))))
}

internal fun discoveryPageFixtureActions(
    screen: androidx.compose.runtime.MutableState<SharedProviderDiscoveryUi>,
    actions: MutableList<String>,
): NaviampHomeActions = NaviampHomeActions({}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> },
    onDiscoveryArtistDismiss = { actions += "back"; screen.value = screen.value.copy(releasePage = null) },
    onDiscoveryArtistDetails = { actions += "related" },
    onDiscoveryAction = { _, action ->
        if (action is ProviderDiscoveryAction.RequestAlbum) screen.value = screen.value.copy(artistDetail = screen.value.artistDetail?.copy(
            operationStatus = ProviderDiscoveryOperationStatus.Queued, blockedActions = setOf(action)))
        else actions += "sample"
    },
    onDiscoveryRelease = { id ->
        val release = screen.value.artistDetail!!.details!!.releases.first { it.id == id }
        screen.value = screen.value.copy(releasePage = SharedDiscoveryReleaseUi(release, loading = false,
            result = ProviderDiscoveryReleaseDetails(ProviderDiscoveryStatus.Ready, release, overview = "Release overview")))
    },
    onDiscoveryViewport = { index, offset, section ->
        screen.value = screen.value.copy(artistDetail = screen.value.artistDetail?.copy(
            firstVisibleItem = index, firstVisibleOffset = offset, releaseSection = section))
    })
