package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampApplicationSurfaceTest {
    @Test
    fun televisionNavigationStaysSmallAndAddsNowPlayingOnlyForAnActiveQueue() {
        val empty = naviampTelevisionDestinations(hasNowPlaying = false)
        val playing = naviampTelevisionDestinations(hasNowPlaying = true)

        assertEquals(
            listOf(
                NaviampTelevisionDestination.Home,
                NaviampTelevisionDestination.Library,
                NaviampTelevisionDestination.Playlists,
                NaviampTelevisionDestination.Search,
                NaviampTelevisionDestination.Settings,
            ),
            empty,
        )
        assertTrue(NaviampTelevisionDestination.NowPlaying in playing)
        assertFalse(NaviampTelevisionDestination.NowPlaying in empty)
        assertFalse(playing.any { it.route == SharedRoute.Downloads })
        assertFalse(playing.any { it.route == SharedRoute.Radio })
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
}
