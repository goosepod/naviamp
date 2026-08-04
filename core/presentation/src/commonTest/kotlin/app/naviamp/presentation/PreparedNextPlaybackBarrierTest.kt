package app.naviamp.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PreparedNextPlaybackBarrierTest {
    @Test
    fun invalidationWaitsForBlockingPreparationBeforeClearingNativeState() = runTest {
        val preparationStarted = CompletableDeferred<Unit>()
        val releasePreparation = CompletableDeferred<Unit>()
        var cleared = false
        val barrier = PreparedNextPlaybackBarrier(this)

        barrier.launchPreparation {
            preparationStarted.complete(Unit)
            withContext(NonCancellable) { releasePreparation.await() }
        }
        runCurrent()
        preparationStarted.await()

        barrier.invalidate { cleared = true }
        runCurrent()
        assertFalse(cleared)

        releasePreparation.complete(Unit)
        advanceUntilIdle()
        assertTrue(cleared)
    }
}
