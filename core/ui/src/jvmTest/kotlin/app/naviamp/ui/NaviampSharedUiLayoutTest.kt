package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampSharedUiLayoutTest {
    @Test
    fun connectionEditorKeepsSettingsOwnedScrollOnlyOnSettingsRoute() {
        assertTrue(
            sharedRouteCanUseOwnScroll(
                editingConnection = true,
                selectedRoute = SharedRoute.Settings,
            ),
        )
        assertFalse(
            sharedRouteCanUseOwnScroll(
                editingConnection = true,
                selectedRoute = SharedRoute.Home,
            ),
        )
        assertTrue(
            sharedRouteCanUseOwnScroll(
                editingConnection = false,
                selectedRoute = SharedRoute.Home,
            ),
        )
    }

    @Test
    fun disconnectedSettingsKeepsItsBoundedOwnedScroll() {
        assertTrue(
            sharedRouteUsesOwnScroll(
                connected = false,
                editingConnection = false,
                selectedRoute = SharedRoute.Settings,
            ),
        )
        assertFalse(
            sharedRouteUsesOwnScroll(
                connected = false,
                editingConnection = false,
                selectedRoute = SharedRoute.Home,
            ),
        )
    }

    @Test
    fun remoteNowPlayingRemainsAvailableWithoutALocalProviderConnection() {
        assertTrue(
            sharedCanShowNowPlaying(
                connected = false,
                remoteNowPlayingAvailable = true,
            ),
        )
        assertTrue(
            sharedCanShowNowPlaying(
                connected = true,
                remoteNowPlayingAvailable = false,
            ),
        )
        assertFalse(
            sharedCanShowNowPlaying(
                connected = false,
                remoteNowPlayingAvailable = false,
            ),
        )
    }
}
