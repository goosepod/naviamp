package app.naviamp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toAwtImage
import app.naviamp.domain.provider.*
import app.naviamp.domain.settings.InterfaceFontSize
import kotlin.test.*
import kotlin.math.abs

@OptIn(ExperimentalTestApi::class)
class NaviampDiscoveryTrackAlignmentTest {
    @Test fun trackTitlesCenterWithControlsAndKeepOneColumnWhenPreviewsAreMissing() {
        for (width in listOf(360, 1100)) for (font in listOf(InterfaceFontSize.Small, InterfaceFontSize.Large)) {
            runDesktopComposeUiTest(width, 400) {
                val initial = discoveryPageFixture()
                val release = initial.artistDetail!!.details!!.releases.first()
                val detail = SharedDiscoveryReleaseUi(release, loading = false, tracksLoading = false,
                    tracks = ProviderDiscoveryReleaseTracks(ProviderDiscoveryStatus.Ready, listOf(
                        ProviderDiscoveryReleaseTrack(null, "Aligned preview", 1, 1, 276000, DiscoveryDeezerPreviewId("42")),
                        ProviderDiscoveryReleaseTrack(null, "Missing preview", 1, 2, 276000),
                        ProviderDiscoveryReleaseTrack(null, "Another preview", 1, 10, 300000, DiscoveryDeezerPreviewId("43")))))
                val page = mutableStateOf(initial.copy(releasePage = detail))
                setContent { MaterialTheme { NaviampFontSizeScope(font, includeStandardScale = true) {
                    Box(Modifier.fillMaxSize()) {
                        NaviampDiscoveryReleaseTrackList(page.value, initial.artistDetail, detail, NaviampColors.Dark,
                            discoveryPageFixtureActions(page, mutableListOf()),
                            DiscoverySamplePlaybackUi(DiscoveryPreviewTrackPrefix + "42", playing = true), false)
                    }
                } } }
                val title = onNodeWithText("Aligned preview", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val number = onNodeWithText("1.", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val pause = onNodeWithContentDescription("Pause", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val missing = onNodeWithText("Missing preview", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val another = onNodeWithText("Another preview", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue(abs(title.center.y - pause.center.y) <= 2f,
                    "Single-line title and preview control must share a vertical center: ${title.center.y} vs ${pause.center.y}")
                assertTrue(abs(title.center.y - number.center.y) <= 2f, "Title and track number must share a vertical center")
                assertEquals(title.left, missing.left, .5f, "Missing previews must retain the same title column")
                assertEquals(title.left, another.left, .5f, "Track-number width must remain stable")
                onNodeWithText("Preview unavailable").assertIsDisplayed()
                System.getenv("NAVIAMP_DISCOVERY_ALIGNMENT_CAPTURE")?.let { directory ->
                    val target = java.io.File(directory, "tracks-$width-$font.png"); target.parentFile.mkdirs()
                    javax.imageio.ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", target)
                }
            }
        }
    }
}
