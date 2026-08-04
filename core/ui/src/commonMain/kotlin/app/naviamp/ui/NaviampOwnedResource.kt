package app.naviamp.ui

import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
    private val retired = mutableListOf<T>()
    private var closed = false

    fun replace(next: T?): T? {
        val replacement = replaceDeferred(next)
        replacement.retired?.let(::releaseRetired)
        return replacement.current
    }

    /**
     * Publishes [next] while keeping the superseded resource alive until the renderer reaches a
     * safe frame boundary and calls [releaseRetired].
     *
     * Compose can still draw the previously published state while applying a replacement. Native
     * images therefore must not be destroyed synchronously with the state write.
     */
    fun replaceDeferred(next: T?): NaviampResourceReplacement<T> {
        if (closed) {
            next?.let(release)
            return NaviampResourceReplacement(current = null, retired = null)
        }
        if (current === next) {
            return NaviampResourceReplacement(current = current, retired = null)
        }

        val revivedIndex = retired.indexOfFirst { it === next }
        if (revivedIndex >= 0) retired.removeAt(revivedIndex)

        val previous = current
        current = next
        previous?.let(retired::add)
        return NaviampResourceReplacement(current = current, retired = previous)
    }

    fun releaseRetired(resource: T) {
        if (current === resource) return
        val retiredIndex = retired.indexOfFirst { it === resource }
        if (retiredIndex < 0) return
        release(retired.removeAt(retiredIndex))
    }

    fun close() {
        if (closed) return
        closed = true
        val previous = current
        current = null
        previous?.let(release)
        retired.forEach(release)
        retired.clear()
    }
}

internal data class NaviampResourceReplacement<T : Any>(
    val current: T?,
    val retired: T?,
)

/** Two completed frames let Compose stop drawing the superseded state before native destruction. */
internal suspend fun <T : Any> NaviampOwnedResource<T>.releaseAfterRenderHandoff(resource: T) {
    repeat(2) { withFrameMillis { } }
    releaseRetired(resource)
}

/** Core-owned publication order shared by every Compose renderer host. */
internal fun <T : Any> NaviampOwnedResource<T>.replaceForRendering(
    next: T?,
    retirementScope: CoroutineScope,
    publish: (T?) -> Unit,
) {
    val replacement = replaceDeferred(next)
    publish(replacement.current)
    replacement.retired?.let { retired ->
        retirementScope.launch { releaseAfterRenderHandoff(retired) }
    }
}
