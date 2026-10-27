package app.naviamp.app

import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.app.NaviampRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampNavigationControllerTest {
    @Test fun playlistBackPreservesTheOpeningArtistAndItsNestedHistory() {
        val controller = NaviampNavigationController(NaviampNavigationState(route = NaviampRoute.Library))
        controller.recordArtistDetailOpened(artist("first"))
        controller.navigate(NaviampRoute.ArtistDetail)
        controller.recordArtistDetailOpened(artist("next"))
        controller.recordPlaylistDetailOpened()
        controller.navigate(NaviampRoute.PlaylistDetail)
        controller.recordArtistDetailOpened(artist("playlist-track-artist"))
        controller.navigate(NaviampRoute.ArtistDetail)
        assertEquals(NaviampDetailBackCommand.Navigate(NaviampRoute.PlaylistDetail), controller.closeActiveDetail(NaviampDetailKind.Artist))
        controller.navigate(NaviampRoute.PlaylistDetail)
        assertEquals(NaviampDetailBackCommand.OpenArtist(artist("next")), controller.closePlaylistDetail(NaviampRoute.Playlists))
        controller.navigate(NaviampRoute.ArtistDetail)
        controller.recordArtistDetailOpened(artist("next"), pushCurrentArtist = false)
        assertEquals(NaviampDetailBackCommand.OpenArtist(artist("first")), controller.closeActiveDetail(NaviampDetailKind.Artist))
        assertEquals(NaviampDetailBackCommand.Navigate(NaviampRoute.Library), controller.closeActiveDetail(NaviampDetailKind.Artist))
    }

    @Test fun otherPlaylistOriginsAndClearedHistoryDoNotResurrectAnArtist() {
        val controller = NaviampNavigationController()
        controller.recordArtistDetailOpened(artist("first"))
        controller.navigate(NaviampRoute.ArtistDetail)
        controller.recordPlaylistDetailOpened()
        controller.clearDetailHistory()
        assertEquals(NaviampDetailBackCommand.Navigate(NaviampRoute.Playlists), controller.closePlaylistDetail(NaviampRoute.Playlists))
        controller.recordArtistDetailOpened(artist("second"))
        controller.navigate(NaviampRoute.Home)
        controller.recordPlaylistDetailOpened()
        assertEquals(NaviampDetailBackCommand.Navigate(NaviampRoute.Home), controller.closePlaylistDetail(NaviampRoute.Home))
    }
    @Test
    fun routeAndLastContentRouteChangeIndependently() {
        val controller = NaviampNavigationController(
            NaviampNavigationState(
                route = NaviampRoute.Library,
                lastContentRoute = NaviampRoute.Search,
            ),
        )

        controller.navigate(NaviampRoute.Settings)

        assertEquals(NaviampRoute.Settings, controller.state.value.route)
        assertEquals(NaviampRoute.Search, controller.state.value.lastContentRoute)

        controller.updateLastContentRoute(NaviampRoute.Playlists)

        assertEquals(NaviampRoute.Settings, controller.state.value.route)
        assertEquals(NaviampRoute.Playlists, controller.state.value.lastContentRoute)
    }

    @Test
    fun replacesRestoredNavigationStateAsOneSnapshot() {
        val controller = NaviampNavigationController()
        val restored = NaviampNavigationState(
            route = NaviampRoute.Player,
            lastContentRoute = NaviampRoute.AlbumDetail,
        )

        controller.replace(restored)

        assertEquals(restored, controller.state.value)
    }

    @Test
    fun albumBackUsesTheOpeningRouteAndPlayerContentRoute() {
        val controller = NaviampNavigationController(
            NaviampNavigationState(
                route = NaviampRoute.ArtistDetail,
                lastContentRoute = NaviampRoute.Home,
            ),
        )

        controller.recordAlbumDetailOpened()
        assertEquals(
            NaviampDetailBackCommand.Navigate(NaviampRoute.ArtistDetail),
            controller.closeActiveDetail(NaviampDetailKind.Album),
        )

        controller.replace(controller.state.value.copy(route = NaviampRoute.Player))
        controller.recordAlbumDetailOpened()
        assertEquals(
            NaviampDetailBackCommand.Navigate(NaviampRoute.Home),
            controller.closeActiveDetail(NaviampDetailKind.Album),
        )
    }

    @Test
    fun detailBackPreservesNowPlayingAsTheVisibleOrigin() {
        val controller = NaviampNavigationController(
            NaviampNavigationState(route = NaviampRoute.Home, lastContentRoute = NaviampRoute.Home),
        )

        controller.recordArtistDetailOpened(
            artist = artist("linked"),
            openedFromNowPlaying = true,
        )

        assertEquals(
            NaviampDetailBackCommand.Navigate(NaviampRoute.Home, reopenNowPlaying = true),
            controller.closeActiveDetail(NaviampDetailKind.Artist),
        )
    }

    @Test
    fun nestedArtistHistoryIsOwnedAndPoppedByTheSharedController() {
        val current = artist("current")
        val next = artist("next")
        val controller = NaviampNavigationController(
            NaviampNavigationState(route = NaviampRoute.Library),
        )
        controller.recordArtistDetailOpened(current)
        controller.navigate(NaviampRoute.ArtistDetail)
        controller.recordArtistDetailOpened(next)

        assertEquals(
            NaviampDetailBackCommand.OpenArtist(current),
            controller.closeActiveDetail(NaviampDetailKind.Artist),
        )
        assertEquals(
            NaviampDetailBackCommand.Navigate(NaviampRoute.Library),
            controller.closeActiveDetail(NaviampDetailKind.Artist),
        )
    }

    @Test
    fun enteringArtistFromAnotherRootClearsStaleHistory() {
        val controller = NaviampNavigationController(
            NaviampNavigationState(route = NaviampRoute.Library),
        )
        controller.recordArtistDetailOpened(artist("first"))
        controller.navigate(NaviampRoute.ArtistDetail)
        controller.recordArtistDetailOpened(artist("nested"))
        controller.navigate(NaviampRoute.Search)

        controller.recordArtistDetailOpened(artist("fresh"))

        assertEquals(
            NaviampDetailBackCommand.Navigate(NaviampRoute.Search),
            controller.closeActiveDetail(NaviampDetailKind.Artist),
        )
    }

    private fun artist(id: String) = Artist(ArtistId(id), "Artist $id")
}
