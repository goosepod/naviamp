package app.naviamp.android.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class AndroidAutoSelectionJobsTest {
    @Test
    fun newerSelectionCancelsPreviousWork() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val jobs = AndroidAutoSelectionJobs(scope)
        val firstStarted = CompletableDeferred<Unit>()
        val keepFirstRunning = CompletableDeferred<Unit>()
        var completedSelection = ""

        val first = jobs.launch {
            firstStarted.complete(Unit)
            keepFirstRunning.await()
            completedSelection = "first"
        }
        firstStarted.await()

        val second = jobs.launch {
            completedSelection = "second"
        }
        second.join()
        yield()

        assertTrue(first.isCancelled)
        assertEquals("second", completedSelection)
        scope.cancel()
    }
}
