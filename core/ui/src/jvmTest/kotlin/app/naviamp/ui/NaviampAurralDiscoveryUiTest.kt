package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import app.naviamp.domain.provider.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampAurralDiscoveryUiTest {
    @Test fun discoveryArtworkLoadsAndPaintsACircularPortrait() {
        val buffer = java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val graphics = buffer.createGraphics()
        graphics.color = java.awt.Color.RED
        graphics.fillRect(0, 0, 64, 64)
        graphics.dispose()
        val bytes = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(buffer, "png", it) }.toByteArray()
        resetNaviampCoverArtCache()
        setJvmPlatformCoverArtByteLoader { bytes }
        try {
            runComposeUiTest {
                setContent {
                    Column(Modifier.width(400.dp).height(400.dp).background(NaviampColors.Dark.background)) {
                        NaviampProviderDiscoveryContent(SharedProviderDiscoveryUi(ProviderIdAurral,
                            ProviderDiscoveryFeed(ProviderDiscoveryStatus.Ready, trending = listOf(
                                ProviderDiscoveryArtist("portrait", "Portrait", imageUrl = "test://discovery-portrait")))),
                            NaviampColors.Dark, {})
                    }
                }
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithContentDescription("Album art").fetchSemanticsNodes().size == 1
                }
                val image = onNodeWithContentDescription("Album art").captureToImage().toAwtImage()
                assertEquals(java.awt.Color.RED.rgb, image.getRGB(image.width / 2, image.height / 2))
                assertNotEquals(java.awt.Color.RED.rgb, image.getRGB(0, 0), "Portrait corners must be clipped")
            }
        } finally {
            resetJvmPlatformCoverArtByteLoader()
        }
    }

    @Test fun discoveryRailsScrollIndependentlyInBothDirectionsAndKeepArtistImagesRound() = runComposeUiTest {
        val artists = (0..9).map { ProviderDiscoveryArtist("artist-$it", "Artist $it") }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(Modifier.width(400.dp).height(700.dp)) {
                    NaviampProviderDiscoveryContent(SharedProviderDiscoveryUi(ProviderIdAurral,
                        ProviderDiscoveryFeed(ProviderDiscoveryStatus.Ready, recommendations = artists, trending = artists)),
                        NaviampColors.Dark, {})
                }
            }
        }
        onAllNodesWithContentDescription("Scroll left")[0].assertIsNotEnabled()
        onAllNodesWithContentDescription("Scroll left")[1].assertIsNotEnabled()
        onAllNodesWithContentDescription("Scroll right")[0].assertIsEnabled().performClick()
        waitForIdle()
        onAllNodesWithContentDescription("Scroll left")[0].assertIsEnabled()
        onAllNodesWithContentDescription("Scroll left")[1].assertIsNotEnabled()
        repeat(5) { onAllNodesWithContentDescription("Scroll right")[0].performClick(); waitForIdle() }
        onAllNodesWithContentDescription("Scroll right")[0].assertIsNotEnabled()
        repeat(6) { onAllNodesWithContentDescription("Scroll left")[0].performClick(); waitForIdle() }
        onAllNodesWithContentDescription("Scroll left")[0].assertIsNotEnabled()
        onAllNodesWithContentDescription("Scroll right")[0].assertIsEnabled()
    }

    @Test fun nativeArtistDialogShowsLibraryLinkReleasesAndTruthfulFeedbackFailure() = runComposeUiTest {
        val artist = ProviderDiscoveryArtist("mbid", "Discovery artist", detailsAvailable = true,
            externalUrl = "https://music.test/artist/mbid")
        val selected = mutableStateOf(SharedProviderDiscoveryArtistUi(artist))
        var feedback: ProviderDiscoveryFeedbackAction? = null
        var libraryOpened = false
        var dismissed = false
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                NaviampProviderDiscoveryContent(SharedProviderDiscoveryUi(ProviderIdAurral, artistDetail = selected.value),
                    NaviampColors.Dark, {}, onArtistDismiss = { dismissed = true },
                    onFeedback = { _, action -> feedback = action }, onLibraryArtist = { libraryOpened = true })
            }
        }
        onAllNodesWithText("Loading discovery…").assertCountEquals(1)
        runOnIdle { selected.value = selected.value.copy(loading = false, details = ProviderDiscoveryArtistDetails(artist,
            biography = "An artist biography.", libraryArtist = app.naviamp.domain.Artist(app.naviamp.domain.ArtistId("owned-id"), "Owned"),
            feedbackAvailable = true, releases = listOf(ProviderDiscoveryRelease("release", "Album release", "2020", "Album")))) }
        onNodeWithText("Open in Library").performClick()
        onNodeWithText("More like this").performClick()
        onNodeWithText("Album release").assertExists()
        runOnIdle { assertTrue(libraryOpened); assertEquals(ProviderDiscoveryFeedbackAction.MoreLikeThis, feedback)
            selected.value = selected.value.copy(savingFeedback = true) }
        onNodeWithText("Saving feedback…").assertExists()
        onNodeWithText("Block artist").assertIsNotEnabled()
        runOnIdle { selected.value = selected.value.copy(savingFeedback = false, feedbackFailed = true,
            details = selected.value.details!!.copy(feedbackAvailable = false)) }
        onNodeWithText("Could not save feedback. Refresh to check its current state.").assertExists()
        onNodeWithText("More like this").assertDoesNotExist()
        System.getenv("NAVIAMP_AURRAL_CAPTURE")?.let { directory ->
            java.io.File(directory).mkdirs()
            javax.imageio.ImageIO.write(onNode(isDialog()).captureToImage().toAwtImage(), "png", java.io.File(directory, "artist-details.png"))
        }
        onNodeWithText("Close").performClick()
        runOnIdle { assertTrue(dismissed) }
    }

    @Test fun connectionScreenShowsTheOfficialIconAfterNavidromeAndPreservesSubsonicAccountOnSelection() = runComposeUiTest {
        val form = mutableStateOf(ConnectionFormState(providerId = ProviderIdSubsonic,
            serverUrl = "https://music.test", username = "listener", password = "saved",
            selectedMusicFolderIds = listOf("1"), skipTlsVerification = true))
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(Modifier.width(760.dp).height(780.dp).background(NaviampColors.Dark.background).padding(16.dp)) {
                    NaviampConnectionForm(form.value, NaviampColors.Dark, isReconnect = true,
                        onFormChanged = { form.value = it }, onConnect = {}, onCancel = {})
                }
            }
        }
        val navidromeBounds = onNodeWithText("Navidrome").fetchSemanticsNode().boundsInRoot
        val aurralBounds = onNodeWithText("Aurral").fetchSemanticsNode().boundsInRoot
        assertTrue(aurralBounds.left > navidromeBounds.left)
        onNodeWithText("Aurral").performClick()
        onNodeWithText("Connect to Aurral with your local account password.").assertExists()
        runOnIdle {
            assertEquals(ProviderIdAurral, form.value.providerId)
            assertEquals("saved", form.value.password)
            assertEquals(listOf("1"), form.value.selectedMusicFolderIds)
            assertTrue(form.value.skipTlsVerification)
        }
        System.getenv("NAVIAMP_AURRAL_CAPTURE")?.let { directory ->
            java.io.File(directory).mkdirs()
            javax.imageio.ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", java.io.File(directory, "connection.png"))
        }
    }

    @Test fun loadingBuildingTrendingAndPermissionStatesRenderWithSeparateExternalActions() = runComposeUiTest {
        val state = mutableStateOf(SharedProviderDiscoveryUi(ProviderIdAurral, loading = true))
        var selected: String? = null
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(Modifier.width(640.dp).height(620.dp).background(NaviampColors.Dark.background).padding(16.dp)) {
                    NaviampServiceIcon(ProviderIdAurral, NaviampColors.Dark.primaryText)
                    NaviampProviderDiscoveryContent(state.value, NaviampColors.Dark, { selected = it })
                }
            }
        }
        onNodeWithText("Aurral discovery").assertExists()
        onNodeWithText("Loading discovery…").assertExists()
        runOnIdle {
            state.value = SharedProviderDiscoveryUi(ProviderIdAurral, ProviderDiscoveryFeed(ProviderDiscoveryStatus.Building,
                trending = listOf(ProviderDiscoveryArtist("mbid", "Radiohead", genres = listOf("alternative rock"),
                    externalUrl = "https://music.test/artist/mbid"))))
        }
        onNodeWithText("Your personal recommendations are being built.").assertExists()
        onNodeWithText("Trending artists").assertExists()
        onNodeWithText("Radiohead").assertExists()
        onNodeWithText("Open in Aurral").performClick()
        runOnIdle { assertEquals("mbid", selected) }
        assertEquals(64.dp, mediaArtworkCornerRadius(SharedMediaItemKind.Artist, 128.dp, 7.dp))
        assertEquals(7.dp, mediaArtworkCornerRadius(SharedMediaItemKind.Album, 128.dp, 7.dp))
        assertNotNull(serviceIconResource(ProviderIdAurral))
        System.getenv("NAVIAMP_AURRAL_CAPTURE")?.let { directory ->
            java.io.File(directory).mkdirs()
            javax.imageio.ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", java.io.File(directory, "discovery.png"))
        }
        runOnIdle { state.value = SharedProviderDiscoveryUi(ProviderIdAurral, ProviderDiscoveryFeed(ProviderDiscoveryStatus.Forbidden)) }
        onNodeWithText("Your account cannot access discovery.").assertExists()
        onNodeWithText("Open in Aurral").assertDoesNotExist()
        onNodeWithText("Your personal recommendations will appear here.").assertDoesNotExist()
    }
}
