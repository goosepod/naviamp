package app.naviamp.app

/**
 * Owns one runtime across phone and external-display scene attachments.
 *
 * Confine calls to the application's lifecycle dispatcher. Scene disconnection must not close this
 * owner: playback and other scenes may still need the runtime. Only application shutdown closes it.
 * Native hosts supply resource creation/disposal and translate their lifecycle callbacks.
 */
class NaviampApplicationRuntimeOwner<T : Any>(
    private val create: () -> T,
    private val dispose: (T) -> Unit,
) {
    private var runtime: T? = null
    private var creating = false
    private var closed = false

    fun getOrCreate(): T {
        check(!closed) { "Application runtime owner is closed." }
        runtime?.let { return it }
        check(!creating) { "Application runtime creation is already in progress." }
        creating = true
        return try {
            create().also { runtime = it }
        } finally {
            creating = false
        }
    }

    fun close() {
        check(!creating) { "Cannot close an application runtime during creation." }
        if (closed) return
        closed = true
        val existing = runtime
        runtime = null
        existing?.let(dispose)
    }
}
