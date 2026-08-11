package app.naviamp.ui

import app.naviamp.domain.app.NaviampRoute
import kotlin.test.Test
import kotlin.test.assertFalse

class NaviampProductRouteContentTest {
    @Test
    fun downloadsOwnTheirVerticalScroll() {
        assertFalse(naviampProductRouteUsesOuterVerticalScroll(NaviampRoute.Downloads))
    }

    @Test
    fun playlistDetailsOwnTheirVerticalScroll() {
        assertFalse(naviampProductRouteUsesOuterVerticalScroll(NaviampRoute.PlaylistDetail))
    }

    @Test
    fun pinnedHeaderRoutesOwnTheirVerticalScroll() {
        assertFalse(naviampProductRouteUsesOuterVerticalScroll(NaviampRoute.Search))
        assertFalse(naviampProductRouteUsesOuterVerticalScroll(NaviampRoute.Playlists))
        assertFalse(naviampProductRouteUsesOuterVerticalScroll(NaviampRoute.Radio))
        assertFalse(naviampProductRouteUsesOuterVerticalScroll(NaviampRoute.Settings))
    }
}
