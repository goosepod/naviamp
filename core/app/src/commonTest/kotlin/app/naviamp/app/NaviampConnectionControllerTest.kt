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
        assertTrue(controller.state.value.isConnecting)
        assertFalse(controller.state.value.restoringConnection)

        controller.connected("source", "1.0", "Connected.")
        assertTrue(controller.state.value.connected)
        val restored = controller.begin(restoreSavedSession = true)!!
        assertFalse(restored.clearExistingPlayback)
        assertFalse(restored.clearProviderData)
        assertFalse(restored.runFullLibraryRefresh)
        assertTrue(restored.restoreSavedSession)
        assertTrue(controller.state.value.restoringConnection)
    }

    @Test
    fun offlineConnectionKeepsTheLocalApplicationUsable() {
        val controller = NaviampConnectionController()

        controller.offline("source", "Offline. Downloaded music remains available.")

        assertEquals(NaviampConnectionPhase.Offline, controller.state.value.phase)
        assertTrue(controller.state.value.connected)
        assertTrue(controller.state.value.offline)
        assertEquals("source", controller.state.value.sourceId)
    }

    @Test
    fun duplicateConnectAttemptsAreRejectedUntilTheFirstCompletes() {
        val applicationStatus = NaviampApplicationStatusController()
        val controller = NaviampConnectionController(applicationStatus = applicationStatus)

        controller.begin(restoreSavedSession = false)

        assertNull(controller.begin(restoreSavedSession = false))
        controller.failed("No route")
        assertEquals(NaviampConnectionPhase.Failed, controller.state.value.phase)
        assertEquals("No route", controller.state.value.status)
        assertEquals(NaviampApplicationStatusArea.Connection, applicationStatus.state.value?.area)
        assertEquals(NaviampApplicationStatusLevel.Error, applicationStatus.state.value?.level)
        assertEquals("No route", applicationStatus.state.value?.message)
    }
}
