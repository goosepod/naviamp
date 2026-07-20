package app.naviamp.desktop

import app.naviamp.domain.app.NaviampRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopShellChromeTest {
    @Test
    fun detailRoutesResolveToTheirOwningBottomNavigationDestination() {
        assertEquals(
            NaviampRoute.Library,
            desktopBottomNavigationRoute(
                selectedRoute = NaviampRoute.ArtistDetail,
                albumDetailBackRoute = NaviampRoute.Library,
                artistDetailBackRoute = NaviampRoute.Library,
            ),
        )
        assertEquals(
            NaviampRoute.Library,
            desktopBottomNavigationRoute(
                selectedRoute = NaviampRoute.AlbumDetail,
                albumDetailBackRoute = NaviampRoute.ArtistDetail,
                artistDetailBackRoute = NaviampRoute.Library,
            ),
        )
        assertEquals(
            NaviampRoute.Playlists,
            desktopBottomNavigationRoute(
                selectedRoute = NaviampRoute.PlaylistDetail,
                albumDetailBackRoute = NaviampRoute.Library,
                artistDetailBackRoute = NaviampRoute.Library,
            ),
        )
    }
}
