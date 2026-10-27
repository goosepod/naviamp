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
