package app.naviamp.presentation

import app.naviamp.app.NaviampNavigationController
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.ui.NaviampAlbumDetailScreenUi
import app.naviamp.ui.NaviampArtistDetailScreenUi
import app.naviamp.ui.NaviampPlaylistDetailScreenUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampCoreNavigationControllerTest {
    @Test
    fun routeSelectionOwnsShellStateAndClearsTransientProductDetails() {
        val store = NaviampCoreStateStore(
            NaviampCoreState(
                shell = NaviampCoreState().shell.copy(
                    albumDetail = NaviampAlbumDetailScreenUi(selectedAlbum = item("album")),
                    artistDetail = NaviampArtistDetailScreenUi(selectedArtist = item("artist")),
                    playlistDetail = NaviampPlaylistDetailScreenUi(selectedPlaylist = item("playlist")),
                ),
            ),
        )
        val navigation = NaviampNavigationController()
        val controller = controller(navigation, store)

        controller.dispatch(NaviampCoreCommand.Navigation.SelectRoute(SharedRoute.Library))

        assertEquals(NaviampRoute.Library, navigation.state.value.route)
        assertEquals(NaviampRoute.Library, navigation.state.value.lastContentRoute)
        assertEquals(SharedRoute.Library, store.state.value.shell.shellChrome.selectedRoute)
        assertFalse(store.state.value.shell.shellChrome.nowPlayingOpen)
        assertNull(store.state.value.shell.albumDetail.selectedAlbum)
        assertNull(store.state.value.shell.artistDetail.selectedArtist)
        assertNull(store.state.value.shell.playlistDetail.selectedPlaylist)
    }

    @Test
    fun nowPlayingIsACoreOwnedOverlay() {
        val store = NaviampCoreStateStore()
        val persisted = mutableListOf<Boolean>()
        val controller = NaviampCoreNavigationController(
            navigation = NaviampNavigationController(),
            stateStore = store,
            artistNavigator = NaviampCoreArtistNavigator { error("Unexpected artist navigation") },
            persistNowPlayingOpen = persisted::add,
        )

        controller.dispatch(NaviampCoreCommand.Navigation.OpenNowPlaying)
        assertTrue(store.state.value.shell.shellChrome.nowPlayingOpen)

        controller.dispatch(NaviampCoreCommand.Navigation.CloseNowPlaying)
        assertFalse(store.state.value.shell.shellChrome.nowPlayingOpen)
        assertEquals(listOf(true, false), persisted)
    }

    @Test
    fun restoredNowPlayingVisibilityDoesNotWriteTheSessionAgain() {
        val store = NaviampCoreStateStore()
        val persisted = mutableListOf<Boolean>()
        val controller = NaviampCoreNavigationController(
            navigation = NaviampNavigationController(),
            stateStore = store,
            artistNavigator = NaviampCoreArtistNavigator { error("Unexpected artist navigation") },
            persistNowPlayingOpen = persisted::add,
        )

        controller.restoreNowPlayingOpen()

        assertTrue(store.state.value.shell.shellChrome.nowPlayingOpen)
        assertTrue(persisted.isEmpty())
    }

    @Test
    fun albumBackUsesSharedDetailHistoryAndPreservesArtistState() {
        val navigation = NaviampNavigationController(
            NaviampNavigationState(route = NaviampRoute.ArtistDetail),
        )
        val store = NaviampCoreStateStore(
            NaviampCoreState(
                shell = NaviampCoreState().shell.copy(
                    albumDetail = NaviampAlbumDetailScreenUi(selectedAlbum = item("album")),
                    artistDetail = NaviampArtistDetailScreenUi(selectedArtist = item("artist")),
                ),
            ),
        )
        val controller = controller(navigation, store)
        controller.recordAlbumOpened()
        navigation.navigate(NaviampRoute.AlbumDetail)

        controller.dispatch(NaviampCoreCommand.Navigation.BackFromAlbum)

        assertEquals(NaviampRoute.ArtistDetail, navigation.state.value.route)
        assertNull(store.state.value.shell.albumDetail.selectedAlbum)
        assertEquals("artist", store.state.value.shell.artistDetail.selectedArtist?.id)
    }

    @Test
    fun nestedArtistBackAsksTheSharedArtistControllerToOpenThePreviousArtist() {
        val navigation = NaviampNavigationController(
            NaviampNavigationState(route = NaviampRoute.Search),
        )
        val store = NaviampCoreStateStore()
        val opened = mutableListOf<Artist>()
        val controller = NaviampCoreNavigationController(
            navigation = navigation,
            stateStore = store,
            artistNavigator = NaviampCoreArtistNavigator(opened::add),
        )
        val first = artist("first")
        val second = artist("second")
        controller.recordArtistOpened(first)
        navigation.navigate(NaviampRoute.ArtistDetail)
        controller.recordArtistOpened(second)

        controller.dispatch(NaviampCoreCommand.Navigation.BackFromArtist)

        assertEquals(listOf(first), opened)
    }

    @Test
    fun artistOpenedFromNowPlayingReturnsToNowPlaying() {
        val navigation = NaviampNavigationController(NaviampNavigationState(route = NaviampRoute.Home))
        val store = NaviampCoreStateStore()
        val controller = controller(navigation, store)
        controller.openNowPlaying()
        controller.openArtistDetail(artist("linked"))

        controller.dispatch(NaviampCoreCommand.Navigation.BackFromArtist)

        assertEquals(SharedRoute.Home, store.state.value.shell.shellChrome.selectedRoute)
        assertTrue(store.state.value.shell.shellChrome.nowPlayingOpen)
    }

    @Test
    fun playlistBackClearsDetailAndReturnsToPlaylistList() {
        val navigation = NaviampNavigationController(
            NaviampNavigationState(route = NaviampRoute.PlaylistDetail),
        )
        val store = NaviampCoreStateStore(
            NaviampCoreState(
                shell = NaviampCoreState().shell.copy(
                    playlistDetail = NaviampPlaylistDetailScreenUi(selectedPlaylist = item("playlist")),
                ),
            ),
        )
        val controller = controller(navigation, store)

        controller.dispatch(NaviampCoreCommand.Navigation.BackFromPlaylist)

        assertEquals(NaviampRoute.Playlists, navigation.state.value.route)
        assertEquals(SharedRoute.Playlists, store.state.value.shell.shellChrome.selectedRoute)
        assertNull(store.state.value.shell.playlistDetail.selectedPlaylist)
    }

    private fun controller(
        navigation: NaviampNavigationController,
        store: NaviampCoreStateStore,
    ) = NaviampCoreNavigationController(
        navigation = navigation,
        stateStore = store,
        artistNavigator = NaviampCoreArtistNavigator { error("Unexpected artist navigation") },
    )

    private fun item(id: String) = SharedMediaItemUi(id = id, title = id, subtitle = "")

    private fun artist(id: String) = Artist(ArtistId(id), id)
}
