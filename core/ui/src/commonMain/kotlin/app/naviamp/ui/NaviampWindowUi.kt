package app.naviamp.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.naviamp.domain.settings.DesktopShortcutPlatform
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal val LocalNaviampWindowController = staticCompositionLocalOf<NaviampWindowController?> { null }

/** Shared keyboard policy; focused controls see Escape before the window does. */
@Composable
fun NaviampWindowEnvironment(
    controller: NaviampWindowController?,
    platform: DesktopShortcutPlatform?,
    overlayVisible: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (controller == null) {
        content()
        return
    }
    val popups = LocalNaviampPopupRegistry.current
    val focus = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    var shortcutHeld by remember { mutableStateOf(false) }
    LaunchedEffect(controller) { if (!hasFocus) focus.requestFocus() }
    CompositionLocalProvider(LocalNaviampWindowController provides controller) {
        Box(Modifier.fillMaxSize().testTag("application-window")
            .focusRequester(focus)
            .onFocusChanged { hasFocus = it.hasFocus; if (!it.hasFocus) shortcutHeld = false }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && (event.key == Key.F11 || event.key == Key.F)) {
                    shortcutHeld = false
                    false
                } else if (event.type != KeyEventType.KeyDown) false
                else if (naviampWindowToggleShortcut(
                    platform, event.key == Key.F11, event.key == Key.F,
                    event.isCtrlPressed, event.isMetaPressed, event.isAltPressed, event.isShiftPressed,
                )) {
                    if (!shortcutHeld) { shortcutHeld = true; controller.toggle() }
                    true
                } else false
            }
            .onKeyEvent { event ->
                event.type == KeyEventType.KeyDown && event.key == Key.Escape &&
                    controller.escape(overlayVisible || popups?.blocksWindowEscape == true)
            }.focusable()) { content() }
    }
}

internal fun naviampWindowToggleShortcut(
    platform: DesktopShortcutPlatform?, f11: Boolean, f: Boolean,
    control: Boolean, meta: Boolean, alt: Boolean, shift: Boolean,
): Boolean = !alt && !shift && (
    (f11 && !control && !meta) ||
        (platform == DesktopShortcutPlatform.MacOS && f && control && meta)
    )

@Composable
internal fun NaviampWindowFullscreenButton(colors: NaviampColors) {
    val controller = LocalNaviampWindowController.current ?: return
    val fullscreen = controller.state.placement == NaviampWindowPlacement.Fullscreen
    val label = stringResource(if (fullscreen) Res.string.window_exit_fullscreen else Res.string.window_enter_fullscreen)
    NaviampTooltip(label, colors) {
        IconButton(onClick = { controller.toggle() }, modifier = Modifier.size(42.dp).testTag("window-fullscreen")) {
            Icon(if (fullscreen) NaviampIcons.ExitFullscreen else NaviampIcons.Fullscreen,
                contentDescription = label, tint = colors.primaryText, modifier = Modifier.size(21.dp))
        }
    }
}
