package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaviampApplicationSurfaceTest {
    @Test
    fun televisionPrimaryNavigationStaysSmall() {
        assertEquals(
            listOf(
                NaviampTelevisionDestination.Home,
                NaviampTelevisionDestination.Library,
                NaviampTelevisionDestination.Search,
            ),
            naviampTelevisionDestinations(),
        )
        assertEquals(
            listOf(
                NaviampTelevisionDestination.Home,
                NaviampTelevisionDestination.NowPlaying,
                NaviampTelevisionDestination.Library,
                NaviampTelevisionDestination.Search,
            ),
            naviampTelevisionDestinations(nowPlayingAvailable = true),
        )
    }

    @Test
    fun televisionSelectionUsesNowPlayingBeforeTheUnderlyingContentRoute() {
        assertEquals(
            NaviampTelevisionDestination.NowPlaying,
            naviampSelectedTelevisionDestination(SharedRoute.Library, nowPlayingOpen = true),
        )
        assertEquals(
            NaviampTelevisionDestination.Library,
            naviampSelectedTelevisionDestination(SharedRoute.Library, nowPlayingOpen = false),
        )
        assertNull(naviampSelectedTelevisionDestination(SharedRoute.Radio, nowPlayingOpen = false))
    }

    @Test
    fun televisionBackReturnsSecondaryRoutesHomeWithoutStealingTransientBack() {
        assertEquals(SharedRoute.Home, naviampTelevisionBackRoute(SharedRoute.Library, transientContentOpen = false))
        assertEquals(SharedRoute.Home, naviampTelevisionBackRoute(SharedRoute.Settings, transientContentOpen = false))
        assertNull(naviampTelevisionBackRoute(SharedRoute.Home, transientContentOpen = false))
        assertNull(naviampTelevisionBackRoute(SharedRoute.Library, transientContentOpen = true))
    }
}
