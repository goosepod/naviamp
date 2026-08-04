package app.naviamp.ui

import app.naviamp.domain.app.NaviampRoute
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun simpleColumnRoutesUseOuterVerticalScroll() {
        assertTrue(naviampProductRouteUsesOuterVerticalScroll(NaviampRoute.Search))
        assertTrue(naviampProductRouteUsesOuterVerticalScroll(NaviampRoute.Playlists))
        assertTrue(naviampProductRouteUsesOuterVerticalScroll(NaviampRoute.Radio))
        assertTrue(naviampProductRouteUsesOuterVerticalScroll(NaviampRoute.Settings))
    }
}
