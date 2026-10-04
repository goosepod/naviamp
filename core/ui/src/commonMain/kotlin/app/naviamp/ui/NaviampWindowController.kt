package app.naviamp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Host-neutral window state. Fullscreen is independent of the player layout and portable settings. */
enum class NaviampWindowPlacement { Floating, Maximized, Fullscreen }

data class NaviampWindowSnapshot(
    val placement: NaviampWindowPlacement = NaviampWindowPlacement.Floating,
    val width: Float = 950f,
    val height: Float = 640f,
    val x: Float? = null,
    val y: Float? = null,
)

/** Executes only native window placement/geometry operations; reports success without changing playback. */
fun interface NaviampWindowEffect {
    fun apply(snapshot: NaviampWindowSnapshot): Boolean
}

class NaviampWindowController(
    private val effect: NaviampWindowEffect,
    initial: NaviampWindowSnapshot,
) {
    var state by mutableStateOf(initial)
        private set
    var windowedSnapshot by mutableStateOf(initial.copy(placement = initial.placement.takeUnless {
        it == NaviampWindowPlacement.Fullscreen
    } ?: NaviampWindowPlacement.Floating))
        private set

    /** Native window controls and monitor changes use the same state as the shared action. */
    fun observe(snapshot: NaviampWindowSnapshot) {
        if (snapshot.placement != NaviampWindowPlacement.Fullscreen) windowedSnapshot = snapshot
        state = snapshot
    }

    fun toggle(): Boolean = request(
        if (state.placement == NaviampWindowPlacement.Fullscreen) windowedSnapshot
        else state.copy(placement = NaviampWindowPlacement.Fullscreen),
    )

    /** Escape is consumed only when there is no popup or dialog to dismiss first. */
    fun escape(overlayVisible: Boolean): Boolean =
        !overlayVisible && state.placement == NaviampWindowPlacement.Fullscreen && request(windowedSnapshot)

    private fun request(target: NaviampWindowSnapshot): Boolean {
        if (!runCatching { effect.apply(target) }.getOrDefault(false)) return false
        state = target
        return true
    }
}

/** Screens are host facts expressed in the same logical coordinate space as the window. */
data class NaviampWindowScreen(val x: Float, val y: Float, val width: Float, val height: Float)

fun NaviampWindowSnapshot.onAvailableScreen(screens: List<NaviampWindowScreen>): NaviampWindowSnapshot {
    val left = x ?: return copy(x = null, y = null)
    val top = y ?: return copy(x = null, y = null)
    return if (screens.isEmpty() || screens.any {
        left < it.x + it.width && left + width > it.x && top < it.y + it.height && top + height > it.y
    }) this else copy(x = null, y = null)
}
