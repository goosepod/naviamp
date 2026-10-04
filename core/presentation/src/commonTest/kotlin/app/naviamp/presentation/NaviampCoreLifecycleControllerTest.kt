package app.naviamp.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampCoreLifecycleControllerTest {
    @Test
    fun exitWaitsForCleanupAndRepeatedCloseDoesNotCancelIt() = runTest {
        val controller = NaviampCoreLifecycleController(this)
        val stopped = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        controller.attach { events += "STOP"; stopped.await(); events += "acknowledged" }
        controller.requestClose { events += "exit" }
        controller.requestClose { error("Duplicate exit") }
        runCurrent()
        assertEquals(listOf("STOP"), events)
        stopped.complete(Unit)
        runCurrent()
        assertEquals(listOf("STOP", "acknowledged", "exit"), events)
    }

    @Test
    fun detachedOwnerIsNotCalledDuringExit() = runTest {
        val controller = NaviampCoreLifecycleController(this)
        val detach = controller.attach { error("Disposed owner") }
        detach()
        var exits = 0
        controller.requestClose { exits++ }
        runCurrent()
        assertEquals(1, exits)
    }
}
