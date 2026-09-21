package app.naviamp.ui

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/** A popup can briefly transfer pointer ownership while its native window is being positioned. */
internal data class NaviampTooltipHoverState(
    val hovered: Boolean = false,
    val visible: Boolean = false,
    val deadlineMillis: Long? = null,
) {
    fun hover(inside: Boolean, nowMillis: Long): NaviampTooltipHoverState {
        if (inside == hovered) return this
        return copy(hovered = inside, deadlineMillis = when {
            inside && visible -> null
            inside -> nowMillis + 450
            visible -> nowMillis + 100
            else -> null
        })
    }

    fun advance(nowMillis: Long): NaviampTooltipHoverState =
        if (deadlineMillis != null && nowMillis >= deadlineMillis) copy(visible = hovered, deadlineMillis = null)
        else this
}

@Composable
fun NaviampTooltip(text: String, colors: NaviampColors, content: @Composable () -> Unit) {
    val enabled = LocalNaviampTooltipsEnabled.current && text.isNotBlank()
    val anchor = remember { MutableInteractionSource() }
    val popup = remember { MutableInteractionSource() }
    val anchorHovered by anchor.collectIsHoveredAsState()
    val popupHovered by popup.collectIsHoveredAsState()
    val focused = LocalWindowInfo.current.isWindowFocused
    val epoch = remember { TimeSource.Monotonic.markNow() }
    var state by remember(text, enabled, focused) { mutableStateOf(NaviampTooltipHoverState()) }
    LaunchedEffect(anchorHovered, popupHovered, enabled, focused, text) {
        state = state.hover(enabled && focused && (anchorHovered || popupHovered), epoch.elapsedNow().inWholeMilliseconds)
    }
    LaunchedEffect(state.deadlineMillis) {
        state.deadlineMillis?.let { deadline ->
            delay((deadline - epoch.elapsedNow().inWholeMilliseconds).coerceAtLeast(0))
            state = state.advance(epoch.elapsedNow().inWholeMilliseconds)
        }
    }
    Box(Modifier.hoverable(anchor, enabled = enabled)) {
        content()
        if (state.visible) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, with(LocalDensity.current) { (-34).dp.roundToPx() }),
                properties = PopupProperties(focusable = false),
            ) {
                Surface(
                    modifier = Modifier.hoverable(popup),
                    color = colors.controlSurface.copy(alpha = 0.98f),
                    contentColor = colors.primaryText,
                    shape = RoundedCornerShape(5.dp),
                    shadowElevation = 5.dp,
                ) {
                    Text(text, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                }
            }
        }
    }
}
