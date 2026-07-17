package app.naviamp.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaviampApplicationStatusControllerTest {
    @Test
    fun publishesTypedStatusesWithDistinctSequences() {
        val controller = NaviampApplicationStatusController()

        val connecting = controller.publish(
            NaviampApplicationStatusArea.Connection,
            NaviampApplicationStatusLevel.Information,
            "Connecting...",
        )
        val failed = controller.publish(
            NaviampApplicationStatusArea.Connection,
            NaviampApplicationStatusLevel.Error,
            "No route",
        )

        assertEquals(1L, connecting.sequence)
        assertEquals(2L, failed.sequence)
        assertEquals(failed, controller.state.value)
    }

    @Test
    fun scopedClearDoesNotDiscardAnotherAreasStatus() {
        val controller = NaviampApplicationStatusController()
        controller.publish(
            NaviampApplicationStatusArea.ProviderActions,
            NaviampApplicationStatusLevel.Warning,
            "One action remains pending.",
        )

        controller.clear(NaviampApplicationStatusArea.Connection)
        assertEquals(NaviampApplicationStatusArea.ProviderActions, controller.state.value?.area)

        controller.clear(NaviampApplicationStatusArea.ProviderActions)
        assertNull(controller.state.value)
    }
}
