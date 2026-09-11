package app.naviamp.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertEquals(
            NaviampTelevisionDestination.NowPlaying,
            naviampSelectedTelevisionDestination(
                SharedRoute.Library,
                nowPlayingOpen = false,
                nowPlayingPreview = true,
            ),
        )
        assertEquals(
            NaviampTelevisionDestination.Library,
            naviampSelectedTelevisionDestination(SharedRoute.Radio, nowPlayingOpen = false),
        )
    }

    @Test
    fun televisionNowPlayingNavigationRequiresDownToEnterFullScreen() {
        assertTrue(
            naviampTelevisionNavigationEntersContent(Key.DirectionDown, KeyEventType.KeyDown),
        )
        assertFalse(
            naviampTelevisionNavigationEntersContent(Key.DirectionRight, KeyEventType.KeyDown),
        )
        assertFalse(
            naviampTelevisionNavigationEntersContent(Key.DirectionDown, KeyEventType.KeyUp),
        )
    }

    @Test
    fun televisionNavigationRestoresTheVisibleOwnerOfTheCurrentPage() {
        val destinations = naviampTelevisionDestinations(nowPlayingAvailable = true)
        assertEquals(
            NaviampTelevisionDestination.Library,
            naviampTelevisionVisibleOwner(NaviampTelevisionDestination.Playlists),
        )
        assertEquals(
            NaviampTelevisionDestination.Library,
            naviampTelevisionNavigationFocusDestination(
                selected = NaviampTelevisionDestination.Library,
                settingsSelected = false,
                destinations = destinations,
            ),
        )
        assertEquals(
            NaviampTelevisionDestination.Library,
            naviampTelevisionNavigationFocusDestination(
                selected = NaviampTelevisionDestination.Playlists,
                settingsSelected = false,
                destinations = destinations,
            ),
        )
        assertEquals(
            NaviampTelevisionDestination.Settings,
            naviampTelevisionNavigationFocusDestination(
                selected = NaviampTelevisionDestination.Home,
                settingsSelected = true,
                destinations = destinations,
            ),
        )
    }

    @Test
    fun televisionSettingsKeepsTheLastVisibleRouteBehindItsSheet() {
        assertEquals(
            SharedRoute.Library,
            televisionSettingsBackgroundRoute(SharedRoute.Settings, SharedRoute.Library),
        )
        assertEquals(
            SharedRoute.Search,
            televisionSettingsBackgroundRoute(SharedRoute.Search, SharedRoute.Library),
        )
    }

    @Test
    fun televisionSettingsDismissalIsLimitedToControllerOrSetupTransitions() {
        assertTrue(
            televisionSettingsShouldDismissForControllerActivity(
                previousControllerDeviceId = null,
                controllerDeviceId = "phone",
                previousProvisioningController = null,
                provisioningController = null,
            ),
        )
        assertFalse(
            televisionSettingsShouldDismissForControllerActivity(
                previousControllerDeviceId = "phone",
                controllerDeviceId = "phone",
                previousProvisioningController = null,
                provisioningController = null,
            ),
        )
        assertTrue(
            televisionSettingsShouldDismissForControllerActivity(
                previousControllerDeviceId = "phone",
                controllerDeviceId = "phone",
                previousProvisioningController = "Pixel",
                provisioningController = null,
            ),
        )
        assertFalse(
            televisionSettingsShouldDismissForControllerActivity(
                previousControllerDeviceId = "phone",
                controllerDeviceId = "phone",
                previousProvisioningController = null,
                provisioningController = null,
            ),
        )
    }

    @Test
    fun televisionBackReturnsSecondaryRoutesHomeWithoutStealingTransientBack() {
        assertEquals(SharedRoute.Home, naviampTelevisionBackRoute(SharedRoute.Library, transientContentOpen = false))
        assertEquals(SharedRoute.Home, naviampTelevisionBackRoute(SharedRoute.Settings, transientContentOpen = false))
        assertNull(naviampTelevisionBackRoute(SharedRoute.Home, transientContentOpen = false))
        assertNull(naviampTelevisionBackRoute(SharedRoute.Library, transientContentOpen = true))
    }
}
