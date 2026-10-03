package app.naviamp.ui

import androidx.compose.runtime.*

/** Shared popup lifetime, including nested popups. Native presenters never decide UI stacking. */
internal class NaviampPopupRegistry {
    private val entries = mutableStateListOf<Entry>()
    val visible: Boolean get() = entries.isNotEmpty()

    val blocksWindowEscape: Boolean get() = entries.any { it.blocksWindowEscape }
    private class Entry(val blocksWindowEscape: Boolean)

    fun register(blocksWindowEscape: Boolean = true): () -> Unit {
        val entry = Entry(blocksWindowEscape)
        entries.add(entry)
        return { entries.remove(entry) }
    }
}

internal val LocalNaviampPopupRegistry = staticCompositionLocalOf<NaviampPopupRegistry?> { null }

@Composable
internal fun NaviampPopupPresence(blocksWindowEscape: Boolean = true) {
    val registry = LocalNaviampPopupRegistry.current
    DisposableEffect(registry, blocksWindowEscape) {
        val release = registry?.register(blocksWindowEscape)
        onDispose { release?.invoke() }
    }
}
