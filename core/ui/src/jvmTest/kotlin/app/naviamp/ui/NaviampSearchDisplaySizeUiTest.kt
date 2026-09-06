package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampSearchDisplaySizeUiTest {
    @Test fun searchAtNarrowDesktopWidth() = checkSearch(300, 640)
    @Test fun searchAt720p() = checkSearch(1280, 720)
    @Test fun searchAt1080p() = checkSearch(1920, 1080)

    private fun checkSearch(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        val screen = mutableStateOf(NaviampSearchScreenUi())
        var selectedMedia: String? = null
        setContent {
            Box(Modifier.fillMaxSize()) {
                NaviampSearchContent(
                    NaviampColors(), screen.value,
                    NaviampSearchActions(
                        onQueryChanged = { screen.value = screen.value.copy(query = it, searching = true) },
                        onSearch = {},
                        onClear = { screen.value = NaviampSearchScreenUi() },
                    ),
                    NaviampMediaActions({}, { selectedMedia = it.item.id }),
                )
            }
        }
        onNodeWithText("Search music").performClick().performTextInput("Example")
        onNodeWithText("Searching...").assertIsDisplayed()
        runOnIdle {
            screen.value = screen.value.copy(searching = false, results = SharedSearchResultsUi(
                artists = listOf(SharedMediaItemUi("artist", "Example artist", "Artist")),
                albums = listOf(SharedMediaItemUi("album", "Example album", "Example artist")),
                tracks = listOf(SharedTrackRowUi("track", "Example song", "Example artist")),
            ))
        }
        for (title in listOf("Example artist", "Example album", "Example song")) {
            val node = onAllNodesWithText(title).onFirst().performScrollTo().assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= 0 && bounds.right <= width, "$title must fit at ${width}x$height")
        }
        onNodeWithText("Example album").performScrollTo().performClick()
        runOnIdle { assertEquals("album", selectedMedia) }
        onNodeWithContentDescription("Clear search").performClick()
        onNodeWithText("Search music").assertIsDisplayed().assertIsFocused()
        onNodeWithText("Example album").assertDoesNotExist()
        runOnIdle { screen.value = screen.value.copy(query = "No match") }
        onNodeWithText("No matches found.").assertIsDisplayed()
    }
}
