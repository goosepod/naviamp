package app.naviamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** The operating system's reduce-motion accessibility preference, as reported by a host. */
internal interface NaviampReducedMotionSource {
    val reduced: Boolean

    /** Calls [onChange] after the preference changes; the returned function stops observing. */
    fun observe(onChange: () -> Unit): () -> Unit
}

/** Returns the host's source, or null when the platform offers no reliable reading. */
@Composable
internal expect fun rememberNaviampPlatformReducedMotionSource(): NaviampReducedMotionSource?

/** Overrides the platform preference, for previews and tests. */
internal val LocalNaviampReducedMotion = staticCompositionLocalOf<Boolean?> { null }

@Composable
internal fun naviampReducedMotion(): Boolean {
    val override = LocalNaviampReducedMotion.current
    val source = rememberNaviampPlatformReducedMotionSource()
    var reduced by remember(source) { mutableStateOf(source?.reduced == true) }
    DisposableEffect(source) {
        val stop = source?.observe { reduced = source.reduced }
        reduced = source?.reduced == true
        onDispose { stop?.invoke() }
    }
    return override ?: reduced
}
