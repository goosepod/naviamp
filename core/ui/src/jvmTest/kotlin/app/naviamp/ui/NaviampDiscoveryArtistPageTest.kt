package app.naviamp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import app.naviamp.domain.provider.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampDiscoveryArtistPageTest {
    @Test fun televisionDpadCanReachAndActivateSampleWithoutPointerFocus() = runDesktopComposeUiTest(1280, 800) {
        val screen = mutableStateOf(discoveryPageFixture())
        val actions = mutableListOf<String>()
        lateinit var inputMode: InputModeManager
        setContent {
            inputMode = LocalInputModeManager.current
            MaterialTheme(colorScheme = darkColorScheme()) {
                TelevisionHome(NaviampHomeScreenUi(providerDiscovery = screen.value), NaviampColors.Dark,
                    discoveryPageFixtureActions(screen, actions), NaviampMediaActions({}, {}), DiscoverySamplePlaybackUi())
            }
        }
        onNodeWithText("Back").assertIsFocused()
        runOnIdle { assertTrue(inputMode.requestInputMode(InputMode.Keyboard)) }
        onNodeWithText("Back").performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithText("Sample track").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        onNodeWithText("Refresh").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithText("Sample track").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        runOnIdle { assertTrue("sample" in actions) }
    }

    @Test fun accessibilityTreeExposesHeadingsNamedActionsAndPermissionState() = runDesktopComposeUiTest(1100, 850) {
        val screen = mutableStateOf(discoveryPageFixture())
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SharedHomeRoute(NaviampColors.Dark, NaviampHomeScreenUi(providerDiscovery = screen.value),
                    discoveryPageFixtureActions(screen, mutableListOf()), NaviampMediaActions({}, {}), DiscoverySamplePlaybackUi())
            }
        }
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        onNodeWithText("Discovery artist").assert(heading)
        onNodeWithText("Samples").assert(heading)
        onNodeWithContentDescription("Play sample").assertHasClickAction().assertIsEnabled()
        onNodeWithText("Release 1").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        onNodeWithText("Release 1").assert(heading)
        onNodeWithText("Request album").assertHasClickAction().assertIsEnabled()
        runOnIdle {
            screen.value = screen.value.copy(artistDetail = screen.value.artistDetail?.copy(actionBusy = true))
        }
        onNodeWithText("Request album").assertIsNotEnabled()
        runOnIdle {
            screen.value = screen.value.copy(artistDetail = screen.value.artistDetail?.let {
                it.copy(actionBusy = false, details = it.details?.copy(canRequestAlbums = false))
            })
        }
        onNodeWithText("Request album").assertDoesNotExist()
    }

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
): NaviampHomeActions {
    val runAction: (String, ProviderDiscoveryAction) -> Unit = { _, action ->
        if (action is ProviderDiscoveryAction.RequestAlbum || action is ProviderDiscoveryAction.RequestTrack)
            screen.value = screen.value.copy(artistDetail = screen.value.artistDetail?.let { it.copy(
                operationStatus = ProviderDiscoveryOperationStatus.Queued, blockedActions = it.blockedActions + action,
                requestOperations = it.requestOperations + (action to ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.Queued))) })
        else actions += "sample"
    }
    return NaviampHomeActions({}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> },
    onDiscoveryArtistDismiss = { actions += "back"; screen.value = screen.value.copy(releasePage = null) },
    onDiscoveryArtistDetails = { actions += "related" },
    onDiscoveryAction = runAction,
    onDiscoveryReleaseAction = { _, _, _, action -> runAction(screen.value.artistDetail!!.artist.id, action) },
    onDiscoveryRelease = { id ->
        val release = screen.value.artistDetail!!.details!!.releases.first { it.id == id }
        screen.value = screen.value.copy(releasePage = SharedDiscoveryReleaseUi(release, loading = false,
            result = ProviderDiscoveryReleaseDetails(ProviderDiscoveryStatus.Ready, release, overview = "Release overview"),
            requestContext = ProviderDiscoveryReleaseContext(screen.value.artistDetail!!.artist, release)))
    },
    onDiscoveryViewport = { index, offset, section ->
        screen.value = screen.value.copy(artistDetail = screen.value.artistDetail?.copy(
            firstVisibleItem = index, firstVisibleOffset = offset, releaseSection = section))
    })
}
