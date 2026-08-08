package app.naviamp.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NaviampCoreBusyIndicatorTest {
    @Test
    fun duringPublishesAndClearsMessage() = runTest {
        val store = NaviampCoreStateStore()
        val indicator = NaviampCoreBusyIndicator(store)

        indicator.during("Loading albums...") {
            assertEquals("Loading albums...", store.state.value.overlays.busyMessage)
        }

        assertNull(store.state.value.overlays.busyMessage)
    }

    @Test
    fun duringClearsMessageAfterFailure() = runTest {
        val store = NaviampCoreStateStore()
        val indicator = NaviampCoreBusyIndicator(store)

        assertFailsWith<IllegalStateException> {
            indicator.during("Loading...") { error("failed") }
        }

        assertNull(store.state.value.overlays.busyMessage)
    }

    @Test
    fun overlappingOperationsKeepAnActiveMessageVisible() = runTest {
        val store = NaviampCoreStateStore()
        val indicator = NaviampCoreBusyIndicator(store)
        val firstCanFinish = CompletableDeferred<Unit>()
        val first = async {
            indicator.during("First operation") { firstCanFinish.await() }
        }

        testScheduler.runCurrent()
        val second = indicator.begin("Second operation")
        assertEquals("Second operation", store.state.value.overlays.busyMessage)

        indicator.finish(second)
        assertEquals("First operation", store.state.value.overlays.busyMessage)

        firstCanFinish.complete(Unit)
        first.await()
        assertNull(store.state.value.overlays.busyMessage)
    }

    @Test
    fun cancellationClearsMessage() = runTest {
        val store = NaviampCoreStateStore()
        val indicator = NaviampCoreBusyIndicator(store)
        val operation = async {
            indicator.during("Loading...") { awaitCancellation() }
        }

        testScheduler.runCurrent()
        assertEquals("Loading...", store.state.value.overlays.busyMessage)

        operation.cancelAndJoin()
        assertNull(store.state.value.overlays.busyMessage)
    }
}
