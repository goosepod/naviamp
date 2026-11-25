package app.naviamp.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.library.AlbumCatalogRepository
import app.naviamp.domain.library.AlbumCatalogScope
import app.naviamp.domain.library.AlbumCatalogSnapshot
import app.naviamp.domain.library.AlbumLibraryIndex
import app.naviamp.domain.provider.MediaPage
import app.naviamp.domain.provider.MediaPageRequest
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.ui.NaviampColors
import app.naviamp.ui.NaviampLibraryActions
import app.naviamp.ui.NaviampLibraryView
import app.naviamp.ui.NaviampLibraryContent
import app.naviamp.ui.NaviampLibraryViewportState
import app.naviamp.ui.NaviampMediaActions
import app.naviamp.ui.rememberNaviampLibraryViewportState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampLibraryRefreshUiTest {
    @Test
    fun refreshReturnsToTopAndTheIndicatorShowsAlbumsAddedByExplicitAndAutomaticSyncs() = runComposeUiTest {
        val snapshots = mutableMapOf<AlbumCatalogScope, AlbumCatalogSnapshot>()
        val repository = object : AlbumCatalogRepository {
            override fun readAlbumCatalog(scope: AlbumCatalogScope) = snapshots[scope]
            override fun replaceAlbumCatalog(scope: AlbumCatalogScope, snapshot: AlbumCatalogSnapshot) {
                snapshots[scope] = snapshot
            }
        }
        var titles = List(100) { "Album %03d".format(it) }
        val provider = object : MediaProvider by FakeCoreMediaProvider() {
            override suspend fun albumsPage(request: MediaPageRequest) = MediaPage(
                titles.map { Album(AlbumId(it), it, "Artist", null, null) }, 0, request.limit, false)
        }
        var now = 1_000L
        val store = NaviampCoreStateStore()
        // Mirrors the app, whose provider source returns a new decorator for every lookup.
        val controller = NaviampCoreCatalogController(store, { object : MediaProvider by provider {} },
            albumIndex = AlbumLibraryIndex(repository, { "source" }, { now }))
        val albums = NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Albums)
        runBlocking {
            controller.dispatch(albums)
            controller.execute(albums)
        }
        lateinit var viewport: NaviampLibraryViewportState
        setContent {
            val state by store.state.collectAsState()
            viewport = rememberNaviampLibraryViewportState()
            Box(Modifier.height(500.dp)) {
                NaviampLibraryContent(NaviampColors(), state.shell.library, NaviampLibraryActions(
                    onViewChanged = {}, onQueryChanged = {}, onRefresh = {}, onLoadMore = {},
                    onJumpToLetter = {}, onTrackAction = {},
                ), NaviampMediaActions({}, {}), viewport)
            }
        }
        val list = { viewport.listState(NaviampLibraryView.Albums) }
        runOnIdle { list().requestScrollToItem(80) }
        waitForIdle()

        titles = titles + "Album 050 Deluxe"
        runBlocking { controller.execute(NaviampCoreCommand.Library.Refresh) }
        waitForIdle()
        runOnIdle {
            assertEquals(0, list().firstVisibleItemIndex)
            assertEquals(0, list().firstVisibleItemScrollOffset)
        }
        onNodeWithText("1 new album").assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
            .performClick()
        waitForIdle()
        onNodeWithText("Album 050 Deluxe").assertIsDisplayed()
        runOnIdle { assertEquals(51, list().firstVisibleItemIndex) }
        onNodeWithText("1 new album").assertDoesNotExist()

        runOnIdle { list().requestScrollToItem(80) }
        runBlocking { controller.execute(NaviampCoreCommand.Library.Refresh) }
        waitForIdle()
        runOnIdle { assertEquals(0, list().firstVisibleItemIndex) }
        onNodeWithText("1 new album").assertDoesNotExist()

        // Reopening Albums with a stale index syncs in the background without moving the list.
        runOnIdle { list().requestScrollToItem(80) }
        waitForIdle()
        titles = titles + "Album 020 Live"
        now += 60 * 60 * 1000L
        runBlocking {
            controller.dispatch(albums)
            controller.execute(albums)
        }
        waitForIdle()
        runOnIdle { assertEquals(81, list().firstVisibleItemIndex) }
        onNodeWithText("1 new album").assertIsDisplayed().performClick()
        waitForIdle()
        onNodeWithText("Album 020 Live").assertIsDisplayed()
        runOnIdle { assertEquals(21, list().firstVisibleItemIndex) }
    }
}
