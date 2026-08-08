package app.naviamp.presentation

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Identifies one user-visible operation managed by [NaviampCoreBusyIndicator]. */
class NaviampCoreBusyOperation internal constructor(internal val id: Long)

/**
 * Shared owner for user-visible work that may take noticeable time.
 *
 * Operations may overlap. The newest operation is shown, and finishing it restores the previous
 * message until that operation also finishes.
 */
class NaviampCoreBusyIndicator(private val stateStore: NaviampCoreStateStore) {
    private val mutex = Mutex()
    private val activeOperations = mutableListOf<Pair<NaviampCoreBusyOperation, String>>()
    private var nextId = 0L

    suspend fun begin(message: String): NaviampCoreBusyOperation = mutex.withLock {
        val operation = NaviampCoreBusyOperation(++nextId)
        activeOperations += operation to message
        publish()
        operation
    }

    suspend fun finish(operation: NaviampCoreBusyOperation) = mutex.withLock {
        activeOperations.removeAll { it.first == operation }
        publish()
    }

    suspend fun <T> during(message: String, block: suspend () -> T): T {
        val operation = begin(message)
        return try {
            block()
        } finally {
            withContext(NonCancellable) { finish(operation) }
        }
    }

    private fun publish() {
        val message = activeOperations.lastOrNull()?.second
        stateStore.update { state ->
            state.copy(overlays = state.overlays.copy(busyMessage = message))
        }
    }
}
