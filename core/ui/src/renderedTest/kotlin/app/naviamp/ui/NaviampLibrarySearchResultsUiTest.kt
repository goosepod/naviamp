package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class NaviampLibrarySearchResultsUiTest {
    @Test fun artistsKeepAccentedAndAliasMatchesFromTheServer() = verifyMatches(NaviampLibraryView.Artists)
    @Test fun albumsKeepAccentedAndMetadataMatchesFromTheServer() = verifyMatches(NaviampLibraryView.Albums)
    @Test fun songsKeepAccentedAndMetadataMatchesFromTheServer() = verifyMatches(NaviampLibraryView.Songs)

    private fun verifyMatches(view: NaviampLibraryView) = runComposeUiTest {
        val catalog = mutableStateOf(NaviampLibraryCatalogUi(query = "Rufus", resultsQuery = "Rufus",
            items = listOf(SharedMediaItemUi("accented", "RÜFÜS DU SOL", "Artist"),
                SharedMediaItemUi("literal", "Rufus Thomas", "Artist"),
                SharedMediaItemUi("alias", "Server alias match", "Metadata match")),
            tracks = listOf(SharedTrackRowUi("accented", "Innerbloom", "RÜFÜS DU SOL"),
                SharedTrackRowUi("literal", "Rufus song", "Artist"),
                SharedTrackRowUi("alias", "Server alias match", "Metadata match")),
        ))
        setContent {
            Box(Modifier.height(600.dp)) {
                NaviampLibraryContent(NaviampColors(), NaviampLibraryScreenUi(selectedView = view,
                    artists = catalog.value, albums = catalog.value, songs = catalog.value),
                    NaviampLibraryActions(onViewChanged = {}, onQueryChanged = {}, onRefresh = {},
                        onLoadMore = {}, onJumpToLetter = {}, onTrackAction = {}),
                    NaviampMediaActions(onTrackAction = {}, onMediaItemAction = {}),
                    rememberNaviampLibraryViewportState())
            }
        }
        onNodeWithText("RÜFÜS DU SOL").assertIsDisplayed()
        onNodeWithText("Server alias match").assertIsDisplayed()
        onNodeWithText("Loading artists…").assertDoesNotExist()
        runOnIdle { catalog.value = catalog.value.copy(query = "Different", searching = true) }
        onNodeWithText("RÜFÜS DU SOL").assertDoesNotExist()
        onNodeWithText("Server alias match").assertDoesNotExist()
        runOnIdle { catalog.value = catalog.value.copy(resultsQuery = "Different", searching = false) }
        // Matching may depend on metadata that is not present in the display row.
        onNodeWithText("RÜFÜS DU SOL").assertIsDisplayed()
        onNodeWithText("Server alias match").assertIsDisplayed()
    }
}
