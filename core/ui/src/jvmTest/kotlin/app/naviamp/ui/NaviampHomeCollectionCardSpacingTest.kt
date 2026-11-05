package app.naviamp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toAwtImage
import app.naviamp.domain.settings.InterfaceFontSize
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampHomeCollectionCardSpacingTest {
    @Test fun albumAndArtistLabelsStayCloseAtBothFontSizesWithoutWrapping() {
        for (font in listOf(InterfaceFontSize.Small, InterfaceFontSize.Large)) runDesktopComposeUiTest(200, 260) {
            val item = SharedHomeCollectionItemUi(SharedMediaItemUi("album", "A long recently added album title", "Album artist"),
                SharedMediaItemKind.Album, action = SharedHomeCollectionItemAction.OpenAlbum)
            setContent { MaterialTheme { NaviampFontSizeScope(font, includeStandardScale = true) {
                HomeCollectionGridCard(item, NaviampColors.Dark, 128.dp, {})
            } } }
            fun layout(label: String): TextLayoutResult {
                val result = mutableListOf<TextLayoutResult>()
                onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
                return result.single()
            }
            val title = layout(item.title); val artist = layout(item.subtitle)
            val titleBounds = onNodeWithText(item.title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val artistBounds = onNodeWithText(item.subtitle, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val baselineGap = artistBounds.top + artist.firstBaseline - titleBounds.top - title.firstBaseline
            assertTrue(baselineGap <= 18f * title.layoutInput.density.fontScale + 3f,
                "Album/artist labels must use compact line boxes: baseline gap $baselineGap")
            assertEquals(1, title.lineCount)
            assertEquals(1, artist.lineCount)
            assertTrue(artistBounds.top >= titleBounds.bottom, "Labels must not overlap")
            System.getenv("NAVIAMP_DISCOVERY_ALIGNMENT_CAPTURE")?.let { directory ->
                val target = java.io.File(directory, "home-card-$font.png"); target.parentFile.mkdirs()
                javax.imageio.ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", target)
            }
        }
    }
}
