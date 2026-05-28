package app.naviamp.domain.app

import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampNavigationRestorationTest {
    @Test
    fun restoredNavigationRouteRequiresConnection() {
        assertEquals(
            NaviampRoute.Settings,
            restoredNavigationRoute(
                savedRouteName = "Library",
                hasConnection = false,
                hasRestoredTrack = true,
            ),
        )
    }

    @Test
    fun restoredNavigationRouteKeepsPlayerOnlyWithRestoredTrack() {
        assertEquals(
            NaviampRoute.Player,
            restoredNavigationRoute(
                savedRouteName = "Player",
                hasConnection = true,
                hasRestoredTrack = true,
            ),
        )
        assertEquals(
            NaviampRoute.Home,
            restoredNavigationRoute(
                savedRouteName = "Player",
                hasConnection = true,
                hasRestoredTrack = false,
            ),
        )
    }

    @Test
    fun restoredNavigationRouteFallsBackFromDetailRoutes() {
        assertEquals(
            NaviampRoute.Home,
            restoredNavigationRoute(
                savedRouteName = "AlbumDetail",
                hasConnection = true,
                hasRestoredTrack = false,
            ),
        )
        assertEquals(
            NaviampRoute.Search,
            restoredNavigationRoute(
                savedRouteName = "ArtistDetail",
                hasConnection = true,
                hasRestoredTrack = false,
            ),
        )
        assertEquals(
            NaviampRoute.Playlists,
            restoredNavigationRoute(
                savedRouteName = "PlaylistDetail",
                hasConnection = true,
                hasRestoredTrack = false,
            ),
        )
    }

    @Test
    fun restoredLastContentRouteFallsBackFromTransientRoutes() {
        assertEquals(NaviampRoute.Home, restoredLastContentRoute("Player"))
        assertEquals(NaviampRoute.Home, restoredLastContentRoute("AlbumDetail"))
        assertEquals(NaviampRoute.Home, restoredLastContentRoute("ArtistDetail"))
        assertEquals(NaviampRoute.Home, restoredLastContentRoute("PlaylistDetail"))
        assertEquals(NaviampRoute.Library, restoredLastContentRoute("Library"))
    }

    @Test
    fun naviampRouteFromStoredNameHandlesUnknownAndLegacyNames() {
        assertEquals(NaviampRoute.Home, naviampRouteFromStoredName(null))
        assertEquals(NaviampRoute.Home, naviampRouteFromStoredName("Missing"))
        assertEquals(NaviampRoute.Radio, naviampRouteFromStoredName("InternetRadio"))
    }
}

