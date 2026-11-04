package app.naviamp.testkit

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampEventLoopTestRunTest {
    @Test fun pendingWorkCannotBeReportedAsPassing() = runTest {
        val gate = CompletableDeferred<Unit>()
        val run = NaviampEventLoopTestRun(coroutineContext) { gate.await() }
        try {
            runCurrent()
            assertFalse(run.isCompleted)
            assertFailsWith<IllegalStateException> { run.finish() }
            gate.complete(Unit)
            runCurrent()
            assertTrue(run.isCompleted)
            run.finish()
        } finally { run.close() }
    }

    @Test fun completionDoesNotBlockCallerOrCancelItsParent() = runTest {
        var completed = false
        val run = NaviampEventLoopTestRun(coroutineContext) { delay(50); completed = true }
        try {
            runCurrent()
            assertFalse(completed)
            advanceTimeBy(50)
            runCurrent()
            run.finish()
            assertTrue(completed)
            assertTrue(coroutineContext[Job]!!.isActive)
        } finally { run.close() }
    }

    @Test fun asynchronousFailureIsRethrownUnchanged() = runTest {
        val failure = IllegalArgumentException("Expected native test failure")
        val run = NaviampEventLoopTestRun(coroutineContext) { throw failure }
        try {
            runCurrent()
            assertTrue(run.isCompleted)
            assertSame(failure, assertFailsWith<IllegalArgumentException> { run.finish() })
            assertTrue(coroutineContext[Job]!!.isActive)
        } finally { run.close() }
    }

    @Test fun cancellationRunsCleanupAndCannotBecomeAPass() = runTest {
        var cleaned = false
        val run = NaviampEventLoopTestRun(coroutineContext) {
            try { awaitCancellation() } finally { cleaned = true }
        }
        runCurrent()
        run.close()
        runCurrent()
        assertTrue(run.isCompleted)
        assertTrue(cleaned)
        assertFailsWith<CancellationException> { run.finish() }
        assertTrue(coroutineContext[Job]!!.isActive)
    }

    @Test fun parentCancellationStopsTheOwnedTest() = runTest {
        val parent = Job(coroutineContext[Job])
        var cleaned = false
        val run = NaviampEventLoopTestRun(coroutineContext + parent) {
            try { awaitCancellation() } finally { cleaned = true }
        }
        try {
            runCurrent()
            parent.cancel()
            runCurrent()
            assertTrue(run.isCompleted)
            assertTrue(cleaned)
            assertFailsWith<CancellationException> { run.finish() }
            assertTrue(coroutineContext[Job]!!.isActive)
        } finally { run.close(); parent.cancel() }
    }
}
