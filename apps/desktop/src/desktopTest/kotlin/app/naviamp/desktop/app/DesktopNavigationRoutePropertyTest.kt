package app.naviamp.desktop

import app.naviamp.app.NaviampNavigationController
import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.app.NaviampRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopNavigationRoutePropertyTest {
    @Test
    fun currentAndLastContentPropertiesPreserveDesktopRouteBehavior() {
        val controller = NaviampNavigationController(
            NaviampNavigationState(
                route = NaviampRoute.Home,
                lastContentRoute = NaviampRoute.Library,
            ),
        )
        var current by DesktopNavigationRouteProperty(controller, DesktopNavigationField.CurrentRoute)
        var lastContent by DesktopNavigationRouteProperty(controller, DesktopNavigationField.LastContentRoute)

        current = DesktopAppRoute.Settings
        lastContent = DesktopAppRoute.Playlists

        assertEquals(DesktopAppRoute.Settings, current)
        assertEquals(DesktopAppRoute.Playlists, lastContent)
        assertEquals(NaviampRoute.Settings, controller.state.value.route)
        assertEquals(NaviampRoute.Playlists, controller.state.value.lastContentRoute)
    }
}
