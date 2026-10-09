package app.naviamp.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalWindowInfo

/** All owned overlays follow their application window in the native stacking order. */
object NaviampOwnedWindowPolicy {
    const val alwaysOnTop: Boolean = false
    const val followsOwnerStack: Boolean = true
}

/** Native hosts publish activation of the complete owner group, including focused child windows. */
val LocalNaviampWindowGroupActive = staticCompositionLocalOf<Boolean?> { null }

internal class NaviampMenuActivation {
    private var wasActive = false
    fun shouldDismiss(active: Boolean): Boolean {
        if (active) wasActive = true
        return wasActive && !active
    }
}

/** A focusable popup may take focus from its parent; observe group activation when supplied. */
@Composable
internal fun NaviampMenuActivationEffect(onDismissRequest: () -> Unit) {
    val active = LocalNaviampWindowGroupActive.current ?: LocalWindowInfo.current.isWindowFocused
    val activation = remember { NaviampMenuActivation() }
    val dismiss by rememberUpdatedState(onDismissRequest)
    LaunchedEffect(active) {
        if (activation.shouldDismiss(active)) dismiss()
    }
}
