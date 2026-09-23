package app.naviamp.ui

import androidx.compose.runtime.*

/** Shared popup lifetime, including nested popups. Native presenters never decide UI stacking. */
internal class NaviampPopupRegistry {
    private val entries = mutableStateListOf<Any>()
    val visible: Boolean get() = entries.isNotEmpty()

    fun register(): () -> Unit {
        val entry = Any()
        entries.add(entry)
        return { entries.remove(entry) }
    }
}

internal val LocalNaviampPopupRegistry = staticCompositionLocalOf<NaviampPopupRegistry?> { null }

@Composable
internal fun NaviampPopupPresence() {
    val registry = LocalNaviampPopupRegistry.current
    DisposableEffect(registry) {
        val release = registry?.register()
        onDispose { release?.invoke() }
    }
}
