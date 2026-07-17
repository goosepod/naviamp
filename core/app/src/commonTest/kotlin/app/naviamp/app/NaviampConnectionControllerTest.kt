package app.naviamp.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampConnectionControllerTest {
    @Test
    fun restorationPrefersSavedProviderThenCompleteCredentials() {
        val controller = NaviampConnectionController()

        assertEquals(
            NaviampConnectionRestorationSource.SavedProviderConnection,
            controller.restorationSource(true, "", "", ""),
        )
        assertEquals(
            NaviampConnectionRestorationSource.SavedCredentials,
            controller.restorationSource(false, "https://server", "user", "password"),
        )
        assertEquals(
            NaviampConnectionRestorationSource.None,
            controller.restorationSource(false, "https://server", "user", ""),
        )
    }

    @Test
    fun freshConnectionsClearOldStateWhileRestorationPreservesIt() {
        val controller = NaviampConnectionController()

        val fresh = controller.begin(restoreSavedSession = false)!!
        assertTrue(fresh.clearExistingPlayback)
        assertTrue(fresh.clearProviderData)
        assertTrue(fresh.runFullLibraryRefresh)

        controller.connected("source", "1.0", "Connected.")
        val restored = controller.begin(restoreSavedSession = true)!!
        assertFalse(restored.clearExistingPlayback)
        assertFalse(restored.clearProviderData)
        assertFalse(restored.runFullLibraryRefresh)
        assertTrue(restored.restoreSavedSession)
    }

    @Test
    fun duplicateConnectAttemptsAreRejectedUntilTheFirstCompletes() {
        val controller = NaviampConnectionController()

        controller.begin(restoreSavedSession = false)

        assertNull(controller.begin(restoreSavedSession = false))
        controller.failed("No route")
        assertEquals(NaviampConnectionPhase.Failed, controller.state.value.phase)
        assertEquals("No route", controller.state.value.status)
    }
}
