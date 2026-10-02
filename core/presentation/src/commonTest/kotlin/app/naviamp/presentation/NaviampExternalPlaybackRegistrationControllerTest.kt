package app.naviamp.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.cancelAndJoin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NaviampExternalPlaybackRegistrationControllerTest {
    @Test
    fun retriesUnavailableServiceReacquiresAfterLossAndClosesOnCancellation() = runTest {
        val snapshots = MutableStateFlow(NaviampExternalPlaybackSnapshot())
        val lost = CompletableDeferred<Unit>()
        var attempts = 0
        var closes = 0
        val publications = mutableListOf<NaviampExternalPlaybackSnapshot>()
        val controller = NaviampExternalPlaybackRegistrationController(100L, 400L)
        val job = backgroundScope.maintainExternalPlaybackRegistration(snapshots, controller) {
            attempts++
            if (attempts == 1) error("No bus")
            object : NaviampExternalPlaybackRegistration {
                override suspend fun awaitLoss() { if (attempts == 2) lost.await() else CompletableDeferred<Unit>().await() }
                override fun publish(snapshot: NaviampExternalPlaybackSnapshot) { publications += snapshot }
                override fun close() { closes++ }
            }
        }
        runCurrent()
        assertEquals(1, attempts)
        advanceTimeBy(100L)
        runCurrent()
        assertEquals(2, attempts)
        assertEquals(listOf(snapshots.value), publications)
        lost.complete(Unit)
        runCurrent()
        assertEquals(1, closes)
        advanceTimeBy(100L)
        runCurrent()
        assertEquals(3, attempts)
        job.cancelAndJoin()
        assertEquals(2, closes)
        assertEquals(NaviampExternalPlaybackRegistrationState.Closed, controller.state)
        advanceTimeBy(1000L)
        assertEquals(3, attempts)
    }

    @Test
    fun invalidRetryConfigurationRemainsPositiveAndBounded() {
        val controller = NaviampExternalPlaybackRegistrationController(1000L, 10L)
        repeat(10) { assertEquals(10L, controller.registrationLost()) }
        assertEquals(1L, NaviampExternalPlaybackRegistrationController(-1L, -10L).registrationLost())
    }

    @Test
    fun unavailableRegistrationRetriesWithBoundedBackoff() {
        val controller = NaviampExternalPlaybackRegistrationController(100L, 400L)

        assertEquals(100L, controller.registrationLost())
        assertTrue(controller.retrying())
        assertEquals(200L, controller.registrationLost())
        assertTrue(controller.retrying())
        assertEquals(400L, controller.registrationLost())
        assertTrue(controller.retrying())
        assertEquals(400L, controller.registrationLost())
    }

    @Test
    fun successfulRegistrationResetsBackoffAndClosePreventsReacquisition() {
        val controller = NaviampExternalPlaybackRegistrationController(100L, 400L)
        controller.registrationLost()
        controller.retrying()
        controller.registrationLost()
        controller.retrying()

        controller.registered()
        assertEquals(NaviampExternalPlaybackRegistrationState.Registered, controller.state)
        assertEquals(100L, controller.registrationLost())

        controller.close()
        assertNull(controller.registrationLost())
        assertFalse(controller.retrying())
        assertEquals(NaviampExternalPlaybackRegistrationState.Closed, controller.state)
    }
}
