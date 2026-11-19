package app.naviamp.app

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Dispatcher-confined ownership of a disposable presentation, including asynchronous creation. */
class NaviampPresentationSessionOwner<T : Any>(
    private val create: suspend () -> T,
    private val dispose: (T) -> Unit,
) {
    private var value: T? = null
    private var closed = false
    private var creating = false
    val current: T? get() = value

    suspend fun acquire(): T? {
        if (closed) return null
        value?.let { return it }
        check(!creating) { "Presentation creation is already in progress." }
        creating = true
        var candidate: T? = null
        try {
            candidate = create()
            currentCoroutineContext().ensureActive()
            if (closed) return null
            value = candidate
            candidate = null
            return value
        } finally {
            creating = false
            candidate?.let(dispose)
        }
    }

    suspend fun withPresentation(consume: suspend (T) -> Unit) {
        try { acquire()?.let { consume(it) } } finally { close() }
    }

    fun close() {
        if (closed) return
        closed = true
        val existing = value
        value = null
        existing?.let(dispose)
    }
}
