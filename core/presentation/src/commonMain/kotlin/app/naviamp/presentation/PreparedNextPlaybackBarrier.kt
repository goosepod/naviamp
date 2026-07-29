package app.naviamp.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Serializes invalidation with prepared playback work that may be inside a blocking native call. */
internal class PreparedNextPlaybackBarrier(
    private val scope: CoroutineScope,
) {
    private var preparation: Job? = null
    private var invalidation: Job? = null

    fun launchPreparation(block: suspend () -> Unit) {
        preparation = scope.launch { block() }
    }

    fun invalidate(clearPreparedNext: () -> Unit) {
        val pendingPreparation = preparation
        preparation = null
        pendingPreparation?.cancel()
        val previousInvalidation = invalidation
        invalidation = scope.launch {
            previousInvalidation?.join()
            pendingPreparation?.join()
            clearPreparedNext()
        }
    }

    fun currentInvalidation(): Job? = invalidation
}
