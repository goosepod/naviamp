package app.naviamp.testkit

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/** Shared lifetime/result owner for native tests whose host must keep servicing an OS event loop. */
class NaviampEventLoopTestRun(
    context: CoroutineContext = Dispatchers.Default,
    block: suspend () -> Unit,
) {
    private val owner = SupervisorJob(context[Job])
    private val outcome = CoroutineScope(context + owner).async { block() }

    val isCompleted: Boolean get() = outcome.isCompleted

    /** A pending or cancelled test is never a passing result. Failures rethrow on the test thread. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun finish() {
        check(isCompleted) { "Native test is still running" }
        try { outcome.getCompleted() } finally { close() }
    }

    fun close() { owner.cancel() }
}
