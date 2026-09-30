package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampSharedMediaRowFavoriteTest {
    @Test
    fun favoriteHeartFollowsShortAlbumTitleInsteadOfYear() = runComposeUiTest {
        setContent {
            Box(Modifier.width(360.dp)) {
                SharedMediaRow(
                    item = SharedMediaItemUi(
                        id = "album",
                        title = "Anthems: 90s",
                        subtitle = "Various Artists",
                        meta = "2012",
                        favoriteActive = true,
                    ),
                    colors = NaviampColors(),
                    mediaKind = SharedMediaItemKind.Album,
                )
            }
        }

        val title = onNodeWithText("Anthems: 90s").fetchSemanticsNode().boundsInRoot
        val heart = onNodeWithContentDescription("Favorite").fetchSemanticsNode().boundsInRoot
        val year = onNodeWithText("2012").fetchSemanticsNode().boundsInRoot
        assertTrue(heart.left >= title.right)
        assertTrue(heart.top < title.bottom && heart.bottom > title.top)
        assertTrue(heart.bottom <= year.top)
    }

    @Test
    fun longAlbumTitleKeepsFavoriteHeartVisible() = runComposeUiTest {
        setContent {
            Box(Modifier.width(320.dp)) {
                SharedMediaRow(
                    item = SharedMediaItemUi(
                        id = "album",
                        title = "MTV Party to Go, Volume 5 (Columbia House club edition)",
                        subtitle = "Various Artists",
                        meta = "1994",
                        favoriteActive = true,
                    ),
                    colors = NaviampColors(),
                    mediaKind = SharedMediaItemKind.Album,
                )
            }
        }

        val title = onNodeWithText("MTV Party to Go, Volume 5 (Columbia House club edition)")
            .fetchSemanticsNode().boundsInRoot
        val heart = onNodeWithContentDescription("Favorite").fetchSemanticsNode().boundsInRoot
        assertTrue(heart.left >= title.right)
        assertTrue(heart.right <= 320f)
        assertTrue(heart.top < title.bottom && heart.bottom > title.top)
    }

    @Test
    fun unstarredAlbumHasNoFilledHeart() = runComposeUiTest {
        setContent {
            SharedMediaRow(
                item = SharedMediaItemUi("album", "Anthems: 90s", "Various Artists"),
                colors = NaviampColors(),
                mediaKind = SharedMediaItemKind.Album,
            )
        }

        assertTrue(onAllNodesWithContentDescription("Favorite").fetchSemanticsNodes().isEmpty())
    }
}
