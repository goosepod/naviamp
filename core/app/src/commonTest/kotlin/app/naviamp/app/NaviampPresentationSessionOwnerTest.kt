package app.naviamp.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampPresentationSessionOwnerTest {
    @Test fun acquisitionAndCloseAreIdempotent() = runTest {
        var created = 0; val disposed = mutableListOf<Any>()
        val owner = NaviampPresentationSessionOwner(create = { Any().also { created++ } }, dispose = disposed::add)
        val first = owner.acquire()
        assertSame(first, owner.acquire()); assertSame(first, owner.current); assertEquals(1, created)
        owner.close(); owner.close(); assertEquals(listOf(first), disposed)
        assertNull(owner.current); assertNull(owner.acquire())
    }
    @Test fun closeBeforeAcquisitionDoesNotCreateAnything() = runTest {
        var created = 0
        val owner = NaviampPresentationSessionOwner(create = { Any().also { created++ } }, dispose = {})
        owner.close(); assertNull(owner.acquire()); assertEquals(0, created)
    }
    @Test fun lateCreationIsDisposedAfterSceneCloses() = runTest {
        val gate = CompletableDeferred<Unit>(); val value = Any(); val disposed = mutableListOf<Any>()
        val owner = NaviampPresentationSessionOwner(create = { withContext(NonCancellable) { gate.await() }; value }, dispose = disposed::add)
        val acquisition = async { owner.acquire() }; runCurrent(); owner.close(); gate.complete(Unit)
        assertNull(acquisition.await()); assertEquals(listOf(value), disposed); assertNull(owner.current)
    }
    @Test fun cancelledAcquisitionDisposesReturnedPresentationAndCanRetry() = runTest {
        val gate = CompletableDeferred<Unit>(); val disposed = mutableListOf<Any>(); var slow = true
        val owner = NaviampPresentationSessionOwner(create = { if (slow) withContext(NonCancellable) { gate.await() }; Any() }, dispose = disposed::add)
        val job = launch { owner.acquire() }; runCurrent(); job.cancel(); gate.complete(Unit); job.join()
        assertEquals(1, disposed.size); assertNull(owner.current)
        slow = false; assertNotNull(owner.acquire()); owner.close(); assertEquals(2, disposed.size)
    }
    @Test fun cancelledObserverDisposesOnlyItsPresentation() = runTest {
        var disposed = 0
        val application = NaviampApplicationRuntimeOwner(create = { Any() }, dispose = { error("Scene disposed the application") })
        val runtime = application.getOrCreate()
        val scene = NaviampPresentationSessionOwner(create = { runtime to Any() }, dispose = { disposed++ })
        val observer = launch { scene.withPresentation { awaitCancellation() } }
        runCurrent(); observer.cancel(); observer.join()
        assertEquals(1, disposed); assertNull(scene.current); assertSame(runtime, application.getOrCreate())
    }

    @Test fun failedFactoryCanRetryAndDisposalFailureDoesNotReopenTheOwner() = runTest {
        var first = true; var disposed = 0
        val owner = NaviampPresentationSessionOwner(create = { if (first) { first = false; error("creation failed") }; Any() },
            dispose = { disposed++; error("disposal failed") })
        assertFailsWith<IllegalStateException> { owner.acquire() }
        assertNotNull(owner.acquire()); assertFailsWith<IllegalStateException> { owner.close() }
        owner.close(); assertNull(owner.current); assertNull(owner.acquire()); assertEquals(1, disposed)
    }
}
