package app.naviamp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import app.naviamp.domain.playback.EqualizerSettings
import app.naviamp.domain.settings.WideNowPlayingLayout
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampPlayerWorkspaceTest {
    @Test fun playerPanesPreserveAlbumBackground() = runDesktopComposeUiTest(1000, 640) {
        val layout = mutableStateOf(WideNowPlayingLayout.Split)
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                NaviampPlayerWorkspace(true, layout.value, { layout.value = it }, player = {}, browser = {})
            }
        }
        assertEquals(Color.Magenta.toArgb(), onNodeWithTag("docked-player").captureToImage().toPixelMap()[10, 10].toArgb())
        onNodeWithText("Full player").performClick()
        assertEquals(Color.Magenta.toArgb(), onNodeWithTag("full-player").captureToImage().toPixelMap()[10, 10].toArgb())
    }

    @Test fun splitBrowserPaneLeavesSpaceAtWindowEdge() = runDesktopComposeUiTest(1000, 640) {
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                NaviampPlayerWorkspace(true, WideNowPlayingLayout.Split, {}, player = {}, browser = {
                    Box(Modifier.fillMaxSize().background(Color.Black))
                })
            }
        }
        val pixels = onRoot().captureToImage().toPixelMap()
        assertEquals(Color.Magenta.toArgb(), pixels[995, 320].toArgb())
        assertEquals(Color.Black.toArgb(), pixels[980, 320].toArgb())
    }

    @Test fun dockedPlayerHasNoCollapseButton() = checkDockedPlayer(800)
    @Test fun compactDockedPlayerHasNoCollapseButton() = checkDockedPlayer(480)

    private fun checkDockedPlayer(height: Int) = runDesktopComposeUiTest(400, height) {
        setContent {
            NaviampNowPlayingPanel(
                NowPlayingUi(id = "song", title = "Song", subtitle = "Artist", stateLabel = "Paused"),
                colors = NaviampColors(), actions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {}),
                panelLayout = NaviampPlayerPanelLayout.Standalone,
            )
        }
        onNodeWithContentDescription("Collapse player").assertDoesNotExist()
    }

    @Test fun workspaceNavigationShowsAlbumBackgroundEvenOnSettings() = runDesktopComposeUiTest(1000, 100) {
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                SharedBottomNavigationBar(NaviampColors.Dark, SharedRoute.Settings,
                    onRouteSelected = {}, onQueueSelected = {})
            }
        }
        val pixel = onRoot().captureToImage().toPixelMap()[1, 1]
        assertEquals(Color.Magenta.toArgb(), pixel.toArgb())
    }

    @Test fun narrowPagesPreserveSelectedBackground() = checkReadingSurface(300, false, false)
    @Test fun widePagesKeepDarkSurface() = checkReadingSurface(1000, false, true)
    @Test fun narrowSettingsAndWorkspaceKeepDarkSurface() = checkReadingSurface(300, true, true)

    private fun checkReadingSurface(width: Int, keepDarkSurface: Boolean, expectDark: Boolean) = runDesktopComposeUiTest(width, 640) {
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                NaviampReadableContent(NaviampColors.Dark, keepDarkSurface = keepDarkSurface) {}
            }
        }
        val pixel = onRoot().captureToImage().toPixelMap()[width / 2, 320]
        val expected = if (expectDark) readableSurfaceColor(NaviampColors.Dark).compositeOver(Color.Magenta) else Color.Magenta
        assertEquals(expected.red, pixel.red, 0.01f)
        assertEquals(expected.green, pixel.green, 0.01f)
        assertEquals(expected.blue, pixel.blue, 0.01f)
    }

    @Test fun mutedTextHasReadableContrastEvenOverWhiteArtwork() {
        val colors = NaviampColors.Dark
        val surface = readableSurfaceColor(colors).compositeOver(Color.White)
        val contrast = (colors.mutedText.luminance() + 0.05f) / (surface.luminance() + 0.05f)
        assertTrue(contrast >= 4.5f, "Muted text contrast: $contrast")
    }
    @Test fun fullPlayerRendersAt720p() = runDesktopComposeUiTest(1280, 720) {
        val lyricsVisible = mutableStateOf(false)
        setContent {
            NaviampNowPlayingPanel(
                NowPlayingUi(id = "song", title = "A song with a long title", subtitle = "An artist",
                    stateLabel = "Paused", albumLine = "An album", durationSeconds = 240.0, positionSeconds = 30.0,
                    lyricsAvailable = true, lyricsVisible = lyricsVisible.value,
                    lyricsLines = listOf(NaviampLyricLineUi(0, "A lyric on the right"), NaviampLyricLineUi(60000, "Next lyric"))),
                colors = NaviampColors(), actions = NaviampNowPlayingActions({}, { lyricsVisible.value = !lyricsVisible.value }, {}, {}, {}, {}, {}),
                panelLayout = NaviampPlayerPanelLayout.Full,
            )
        }
        onNodeWithText("A song with a long title").assertIsDisplayed()
        onNodeWithText("An artist").assertIsDisplayed()
        onNodeWithContentDescription("Collapse player").assertIsDisplayed()
        val position = onNodeWithText("0:30").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(position.top > 600f, "Progress must sit across the bottom")
        val title = onNodeWithText("A song with a long title").fetchSemanticsNode().boundsInRoot
        val artist = onNodeWithText("An artist").fetchSemanticsNode().boundsInRoot
        val album = onNodeWithText("An album").fetchSemanticsNode().boundsInRoot
        assertTrue(title.left >= 640f)
        assertEquals(title.left, artist.left, 1f)
        assertEquals(title.left, album.left, 1f)
        assertTrue(artist.top - title.bottom >= 12f)
        assertTrue(album.top - artist.bottom >= 12f)
        val artBounds = onNodeWithTag("full-album-art").fetchSemanticsNode().boundsInRoot
        val infoBounds = onNodeWithTag("full-track-info").fetchSemanticsNode().boundsInRoot
        assertEquals(artBounds.center.y, infoBounds.center.y, 1f, "Track info must center on the artwork")
        assertTrue(onNodeWithContentDescription("Collapse player").fetchSemanticsNode().boundsInRoot.right < 640f)
        val pixels = onRoot().captureToImage().toPixelMap()
        val output = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) output.setRGB(x, y, pixels[x, y].toArgb())
        val file = File("build/reports/player-workspace/full-player-1280x720.png")
        file.parentFile.mkdirs()
        ImageIO.write(output, "png", file)
        onNodeWithContentDescription("Show lyrics").performClick()
        onNodeWithText("A song with a long title").assertDoesNotExist()
        onNodeWithTag("full-art-controls").assertIsDisplayed()
        assertEquals(artBounds, onNodeWithTag("full-album-art").fetchSemanticsNode().boundsInRoot)
        assertTrue(onNodeWithTag("full-lyrics").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.left >= 640f)
        onNodeWithText("A lyric on the right").assertIsDisplayed()
        for ((line, fontSize) in listOf("A lyric on the right" to 40f, "Next lyric" to 36f)) {
            val layouts = mutableListOf<TextLayoutResult>()
            onNodeWithText(line).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(fontSize, layouts.single().layoutInput.style.fontSize.value)
            assertFalse(layouts.single().hasVisualOverflow)
        }
        onNodeWithContentDescription("Hide lyrics").performClick()
        onNodeWithText("A song with a long title").assertIsDisplayed()
    }

    @Test fun workspaceAt720p() = checkWorkspace(1280, 720)
    @Test fun workspaceAt1440p() = checkWorkspace(2560, 1440)

    private fun checkWorkspace(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        val mode = mutableStateOf(WideNowPlayingLayout.Split)
        val queue = mutableStateOf(false)
        setContent {
            NaviampPlayerWorkspace(true, mode.value, { mode.value = it },
                player = { Text("Player ${it.name}") }, browser = {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { NaviampReadableContent(NaviampColors()) { Text(if (queue.value) "Queue contents" else "Library contents") } }
                        SharedBottomNavigationBar(NaviampColors(), SharedRoute.Library, true,
                            onRouteSelected = { queue.value = false }, queueSelected = queue.value,
                            onQueueSelected = { queue.value = true })
                    }
                })
        }
        val player = onNodeWithTag("docked-player").fetchSemanticsNode().boundsInRoot
        val browser = onNodeWithTag("browser-pane").fetchSemanticsNode().boundsInRoot
        assertEquals(player.right, browser.left, 1f)
        assertEquals(width.toFloat() - 12f, browser.right, 1f)
        onNodeWithContentDescription("Queue").performClick()
        onNodeWithText("Queue contents").assertIsDisplayed()
        onNodeWithContentDescription("Library").performClick()
        onNodeWithText("Library contents").assertIsDisplayed()
        onNodeWithText("Full player").performClick()
        onNodeWithTag("browser-pane").assertDoesNotExist()
        assertEquals(width.toFloat(), onNodeWithTag("full-player").fetchSemanticsNode().boundsInRoot.width, 1f)
        onNodeWithText("Split view").performClick()
        onNodeWithText("Library contents").assertIsDisplayed()
    }

    @Test fun narrowWindowUsesStackedPlayerWithoutLosingWidePreference() = runDesktopComposeUiTest(300, 640) {
        setContent { NaviampPlayerWorkspace(false, WideNowPlayingLayout.Full, {},
            player = { Text(it.name) }, browser = { error("No narrow browser pane") }) }
        onNodeWithText("Adaptive").assertIsDisplayed()
        onNodeWithText("Split view").assertDoesNotExist()
        assertFalse(supportsPlayerWorkspace(899f, 700f))
        assertFalse(supportsPlayerWorkspace(1280f, 400f))
        assertTrue(supportsPlayerWorkspace(900f, 480f))
    }

    @Test fun equalizerLabelsFitMinimumWidth() = runDesktopComposeUiTest(258, 360) {
        setContent { EqualizerCurvePanel(NaviampColors(), EqualizerSettings(), false) { _, _ -> } }
        for (label in listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")) {
            val layouts = mutableListOf<TextLayoutResult>()
            onNodeWithText(label).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow }, "$label must fit")
        }
    }
}
