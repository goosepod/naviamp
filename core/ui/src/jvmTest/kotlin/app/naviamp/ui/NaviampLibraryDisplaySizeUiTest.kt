package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampLibraryDisplaySizeUiTest {
    @Test fun libraryAtNarrowDesktopWidth() = checkLibrary(288, 640)
    @Test fun libraryAt720p() = checkLibrary(1280, 720)
    @Test fun libraryAt1080p() = checkLibrary(1920, 1080)
    @Test fun libraryAtNative4k() = checkLibrary(3840, 2160)
    @Test fun libraryAt4kWithDoubleDensity() = checkLibrary(3840, 2160, 2f)

    private fun checkLibrary(width: Int, height: Int, scale: Float = 1f) = runDesktopComposeUiTest(width, height) {
        lateinit var screen: MutableState<NaviampLibraryScreenUi>
        lateinit var viewport: NaviampLibraryViewportState
        var requestedLetter: Char? = null
        setContent {
            screen = remember { mutableStateOf(NaviampLibraryScreenUi(
                albums = NaviampLibraryCatalogUi(items = List(200) {
                    SharedMediaItemUi("album-$it", "Album $it", "Artist with a long descriptive name")
                }),
            )) }
            viewport = rememberNaviampLibraryViewportState()
            CompositionLocalProvider(LocalDensity provides Density(scale, fontScale = 1.08f)) {
                Box(Modifier.fillMaxSize().background(NaviampColors().controlSurface)) {
                    NaviampLibraryContent(
                        NaviampColors(), screen.value,
                        NaviampLibraryActions(
                            onViewChanged = { screen.value = screen.value.copy(selectedView = it) },
                            onQueryChanged = {}, onRefresh = {}, onLoadMore = {},
                            onJumpToLetter = { requestedLetter = it }, onTrackAction = {},
                        ),
                        NaviampMediaActions({}, {}), viewport,
                    )
                }
            }
        }
        onNodeWithText("Artists").performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithText("Albums").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        onNodeWithText("Albums").assertIsSelected().performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithText("Search library albums").assertIsFocused()
        onNodeWithText("Album 0").assertIsDisplayed()

        runOnIdle { viewport.listState(NaviampLibraryView.Albums).requestScrollToItem(25) }
        waitForIdle()
        for (label in listOf("Artists", "Albums", "Songs")) onNodeWithText(label).assertIsDisplayed()
        onNodeWithText("Search library albums").assertIsDisplayed().performClick()
        runOnIdle { assertEquals(25, viewport.listState(NaviampLibraryView.Albums).firstVisibleItemIndex) }
        runOnIdle { screen.value = screen.value.copy(albums = screen.value.albums.copy(pendingJump = 'M')) }
        onNodeWithText("Loading M…").assertIsDisplayed()
        runOnIdle { assertEquals(25, viewport.listState(NaviampLibraryView.Albums).firstVisibleItemIndex) }
        val panel = onNodeWithText("Loading M…").fetchSemanticsNode().boundsInRoot
        val row = onNodeWithText("Album 25").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(panel.bottom <= row.top, "Loading panel must not cover the first visible album")

        val pixels = onRoot().captureToImage().toPixelMap()
        assertEquals(width, pixels.width)
        assertEquals(height, pixels.height)
        val snapshot = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) for (x in 0 until width) snapshot.setRGB(x, y, pixels[x, y].toArgb())
        val output = File("build/reports/library-display-sizes/library-${width}x$height-${scale}x.png")
        output.parentFile.mkdirs()
        ImageIO.write(snapshot, "png", output)

        runOnIdle { screen.value = screen.value.copy(albums = screen.value.albums.copy(pendingJump = null)) }
        onNodeWithText("Loading M…").assertDoesNotExist()
        runOnIdle { assertEquals(25, viewport.listState(NaviampLibraryView.Albums).firstVisibleItemIndex) }
        onNodeWithText("Z").performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertEquals('Z', requestedLetter) }
        onNodeWithContentDescription("Back to search").performClick()
        onNodeWithText("Search library albums").assertIsFocused()
        runOnIdle { assertEquals(0, viewport.listState(NaviampLibraryView.Albums).firstVisibleItemIndex) }
        for (label in listOf("Artists", "Albums", "Songs")) {
            val bounds = onNodeWithText(label).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= 0 && bounds.right <= width, "$label must fit the viewport")
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty() && layouts.all { it.getLineEnd(0) == label.length && it.getLineRight(0) <= it.size.width + 1 }, "$label must not be clipped: ${layouts.map { it.size to it.multiParagraph.width }}")
        }
    }
}
