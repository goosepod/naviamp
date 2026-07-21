package app.naviamp.app

import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.app.NaviampRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampNavigationControllerTest {
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
