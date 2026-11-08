package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampLibrarySearchLayoutTest {
    @Test fun queryPagingResumesAfterLookupWithoutRefreshFeedback() = runComposeUiTest {
        val catalog = mutableStateOf(NaviampLibraryCatalogUi(query = "Artist", searching = true,
            items = listOf(SharedMediaItemUi("artist", "Artist", "Artist"))))
        var pagesRequested = 0
        setContent {
            Box(Modifier.height(600.dp)) {
                NaviampLibraryContent(NaviampColors(), NaviampLibraryScreenUi(artists = catalog.value),
                    NaviampLibraryActions(onViewChanged = {}, onQueryChanged = {}, onRefresh = {},
                        onLoadMore = { pagesRequested++ }, onJumpToLetter = {}, onTrackAction = {}),
                    NaviampMediaActions(onTrackAction = {}, onMediaItemAction = {}),
                    rememberNaviampLibraryViewportState())
            }
        }
        runOnIdle {
            assertEquals(0, pagesRequested)
            catalog.value = catalog.value.copy(searching = false)
        }
        runOnIdle { assertEquals(1, pagesRequested) }
        onNodeWithText("Loading artists…").assertDoesNotExist()
    }

    @Test fun queryLookupDoesNotShowRefreshOrPrematureEmptyState() = runComposeUiTest {
        val catalog = mutableStateOf(NaviampLibraryCatalogUi(query = "unknown", searching = true))
        setContent {
            NaviampLibraryContent(NaviampColors(), NaviampLibraryScreenUi(artists = catalog.value),
                NaviampLibraryActions(onViewChanged = {}, onQueryChanged = {}, onRefresh = {},
                    onLoadMore = {}, onJumpToLetter = {}, onTrackAction = {}),
                NaviampMediaActions(onTrackAction = {}, onMediaItemAction = {}),
                rememberNaviampLibraryViewportState())
        }
        onNodeWithText("Loading artists…").assertDoesNotExist()
        onNodeWithText("No library artists match.").assertDoesNotExist()
        runOnIdle { catalog.value = catalog.value.copy(searching = false) }
        onNodeWithText("No library artists match.").assertIsDisplayed()
    }

    @Test fun artistSearchStaysStillDuringLoading() = verifySearchLayout(NaviampLibraryView.Artists)
    @Test fun albumSearchStaysStillDuringLoading() = verifySearchLayout(NaviampLibraryView.Albums)
    @Test fun songSearchStaysStillDuringLoading() = verifySearchLayout(NaviampLibraryView.Songs)

    private fun verifySearchLayout(view: NaviampLibraryView) = runComposeUiTest {
        val catalog = mutableStateOf(NaviampLibraryCatalogUi(
            items = listOf(SharedMediaItemUi("artist", "Artist", "Artist")),
            tracks = listOf(SharedTrackRowUi("song", "Artist song", "Artist")),
        ))
        setContent {
            Box(Modifier.height(600.dp)) {
                NaviampLibraryContent(
                    NaviampColors(),
                    NaviampLibraryScreenUi(selectedView = view,
                        artists = catalog.value, albums = catalog.value, songs = catalog.value),
                    NaviampLibraryActions(onViewChanged = {}, onQueryChanged = { query ->
                        catalog.value = catalog.value.copy(query = query,
                            syncStatus = NaviampLibrarySyncStatusUi(isSyncing = true))
                    }, onRefresh = {}, onLoadMore = {}, onJumpToLetter = {}, onTrackAction = {}),
                    NaviampMediaActions(onTrackAction = {}, onMediaItemAction = {}),
                    rememberNaviampLibraryViewportState(),
                )
            }
        }
        val search = onNode(hasSetTextAction())
        search.performClick().assertIsFocused()
        val searchBounds = search.fetchSemanticsNode().boundsInRoot
        val selectorBounds = onNodeWithText("Albums").fetchSemanticsNode().boundsInRoot
        val loadingText = when (view) {
            NaviampLibraryView.Artists -> "Loading artists…"
            NaviampLibraryView.Albums -> "Loading albums…"
            NaviampLibraryView.Songs -> "Loading songs…"
        }
        fun assertStable() {
            assertEquals(searchBounds, search.fetchSemanticsNode().boundsInRoot)
            assertEquals(selectorBounds, onNodeWithText("Albums").fetchSemanticsNode().boundsInRoot)
            search.assertIsFocused()
        }
        for (character in listOf("A", "r", "t")) {
            search.performTextInput(character)
            onNodeWithText(loadingText).assertIsDisplayed()
            assertStable()
            runOnIdle { catalog.value = catalog.value.copy(syncStatus = NaviampLibrarySyncStatusUi()) }
            onNodeWithText(loadingText).assertDoesNotExist()
            assertStable()
        }
        search.performTextClearance()
        onNodeWithText(loadingText).assertIsDisplayed()
        assertStable()
        runOnIdle { catalog.value = catalog.value.copy(pendingJump = 'M') }
        onNodeWithText("Loading M…").assertIsDisplayed()
        assertStable()
        runOnIdle { catalog.value = catalog.value.copy(pendingJump = null,
            syncStatus = NaviampLibrarySyncStatusUi(message = "Refresh failed")) }
        onNodeWithText("Refresh failed").assertIsDisplayed()
        assertStable()
        runOnIdle { catalog.value = catalog.value.copy(syncStatus = NaviampLibrarySyncStatusUi()) }
        onNodeWithText("Refresh failed").assertDoesNotExist()
        assertStable()
    }
}
