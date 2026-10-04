package app.naviamp.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Shared orderly-exit policy; a host only reports the native close event and executes exit. */
class NaviampCoreLifecycleController(private val scope: CoroutineScope) {
    private var cleanup: (suspend () -> Unit)? = null
    private var closing = false

    fun attach(cleanup: suspend () -> Unit): () -> Unit {
        check(!closing)
        this.cleanup = cleanup
        return { if (this.cleanup === cleanup) this.cleanup = null }
    }

    fun requestClose(exit: () -> Unit) {
        if (closing) return
        closing = true
        val shutdown = cleanup
        scope.launch {
            try { shutdown?.invoke() } finally { exit() }
        }
    }
}
