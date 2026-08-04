package app.naviamp.app

import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampRadioContinuationControllerTest {
    @Test
    fun startAndStopInvalidatePriorSessions() {
        val controller = NaviampRadioContinuationController()
        val session = controller.start(TrackId("seed"), refilling = true)

        assertTrue(controller.isCurrent(session))
        assertTrue(controller.state.refilling)
        assertEquals(TrackId("seed"), controller.state.lastRefillSeedId)

        controller.stop()

        assertFalse(controller.isCurrent(session))
        assertFalse(controller.state.active)
        assertFalse(controller.state.refilling)
        assertEquals(null, controller.state.lastRefillSeedId)
    }

    @Test
    fun staleRefillCannotFinishCurrentSession() {
        val controller = NaviampRadioContinuationController()
        val staleSession = controller.start(TrackId("old"), refilling = true)
        val currentSession = controller.start(TrackId("new"), refilling = true)

        controller.finishRefill(staleSession)
        assertTrue(controller.state.refilling)

        controller.finishRefill(currentSession)
        assertFalse(controller.state.refilling)
    }
}
