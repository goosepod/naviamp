package app.naviamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.window.PopupProperties

/** A transparent, undecorated owned surface with fixed viewport geometry.
 * Core supplies all content, animation, dismissal and sizing policy. Native implementations only
 * execute the window operation and own its unavoidable native resource lifetime. */
internal interface NaviampOwnedDialogSurface {
    @Composable
    fun Render(size: DpSize, properties: PopupProperties, onCloseRequest: () -> Unit,
        content: @Composable () -> Unit)

    /** Core measures and positions a small popup before the native surface is made visible. */
    @Composable
    fun RenderPositioned(size: DpSize, offset: DpOffset, properties: PopupProperties,
        onCloseRequest: () -> Unit, content: @Composable () -> Unit) {
        NaviampPositionedTooltipPopup(size, offset, properties, onCloseRequest, content)
    }
}

internal val LocalNaviampOwnedDialogSurface = staticCompositionLocalOf<NaviampOwnedDialogSurface?> { null }
