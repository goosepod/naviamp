package app.naviamp.app

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
}
