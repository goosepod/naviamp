package app.naviamp.android.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class AndroidAutoSelectionJobs(
    private val scope: CoroutineScope,
) {
    private var activeJob: Job? = null

    fun launch(block: suspend () -> Unit): Job {
        activeJob?.cancel()
        return scope.launch { block() }
            .also { activeJob = it }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
    }
}
