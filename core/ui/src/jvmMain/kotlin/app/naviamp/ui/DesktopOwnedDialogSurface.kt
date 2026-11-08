package app.naviamp.ui

import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.PopupProperties
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.WindowConstants
import kotlin.math.roundToInt

/** Native boundary: a fixed AWT owned window, rather than an auto-fitting Compose scene-layer window. */
internal class DesktopOwnedDialogSurface(private val owner: Window) : NaviampOwnedDialogSurface {
    @Composable
    override fun Render(size: DpSize, properties: PopupProperties, onCloseRequest: () -> Unit,
        content: @Composable () -> Unit) {
        val close = rememberUpdatedState(onCloseRequest)
        val requestedSize = rememberUpdatedState(size)
        fun targetBounds(): Rectangle = Rectangle(owner.x + owner.insets.left, owner.y + owner.insets.top,
            requestedSize.value.width.value.roundToInt(), requestedSize.value.height.value.roundToInt())
        DialogWindow(
            create = {
                ComposeDialog(owner, java.awt.Dialog.ModalityType.MODELESS).apply {
                    isUndecorated = true
                    isResizable = false
                    isTransparent = true
                    defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
                    addWindowListener(object : WindowAdapter() {
                        override fun windowClosing(event: WindowEvent) { close.value() }
                    })
                    this.bounds = targetBounds()
                }
            },
            dispose = { it.dispose() },
            update = { dialog ->
                dialog.focusableWindowState = properties.focusable
                dialog.isAlwaysOnTop = NaviampOwnedWindowPolicy.alwaysOnTop
                val target = targetBounds()
                if (dialog.bounds != target) dialog.bounds = target
            },
        ) {
            DisposableEffect(window, owner) {
                val listener = object : ComponentAdapter() {
                    override fun componentMoved(event: ComponentEvent) {
                        val target = targetBounds()
                        if (window.bounds != target) window.bounds = target
                    }
                }
                owner.addComponentListener(listener)
                onDispose { owner.removeComponentListener(listener) }
            }
            val nestedSurface = remember(window) { DesktopOwnedDialogSurface(window) }
            CompositionLocalProvider(LocalNaviampOwnedDialogSurface provides nestedSurface, content = content)
        }
    }
}
