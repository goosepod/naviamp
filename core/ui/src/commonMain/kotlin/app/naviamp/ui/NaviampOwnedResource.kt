package app.naviamp.ui

/**
 * Owns one replaceable native rendering resource.
 *
 * Core defines replacement and shutdown semantics; each platform supplies only the irreducible
 * native release effect, such as Skia `close`, Android `recycle`, or a queued GPU deletion.
 */
internal class NaviampOwnedResource<T : Any>(
    private val release: (T) -> Unit,
) {
    private var current: T? = null
    private var closed = false

    fun replace(next: T?): T? {
        if (closed) {
            next?.let(release)
            return null
        }
        if (current === next) return current
        val previous = current
        current = next
        previous?.let(release)
        return current
    }

    fun close() {
        if (closed) return
        closed = true
        val previous = current
        current = null
        previous?.let(release)
    }
}
