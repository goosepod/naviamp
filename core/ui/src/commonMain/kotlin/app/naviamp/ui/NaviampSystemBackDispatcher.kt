package app.naviamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Shared UI ownership for transient navigation that is intentionally local to a composable.
 * Hosts only forward their native system-back event to the most deeply registered handler.
 */
class NaviampSystemBackDispatcher {
    private val handlers = mutableStateListOf<Entry>()

    val currentHandler: (() -> Unit)?
        get() = handlers.lastOrNull()?.handler

    internal fun register(key: Any, handler: () -> Unit) {
        handlers.removeAll { it.key === key }
        handlers += Entry(key, handler)
    }

    internal fun unregister(key: Any) {
        handlers.removeAll { it.key === key }
    }

    private data class Entry(val key: Any, val handler: () -> Unit)
}

val LocalNaviampSystemBackDispatcher = compositionLocalOf<NaviampSystemBackDispatcher?> { null }

@Composable
fun NaviampSystemBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    val dispatcher = LocalNaviampSystemBackDispatcher.current
    val currentOnBack = rememberUpdatedState(onBack)
    val key = remember { Any() }
    DisposableEffect(dispatcher, enabled, key) {
        if (dispatcher != null && enabled) {
            dispatcher.register(key) { currentOnBack.value() }
        }
        onDispose { dispatcher?.unregister(key) }
    }
}
