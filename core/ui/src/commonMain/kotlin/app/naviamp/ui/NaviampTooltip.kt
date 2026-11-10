package app.naviamp.ui

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.delay
import kotlin.time.TimeSource
import kotlin.math.roundToInt

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

internal val LocalNaviampTooltipTimeSource = staticCompositionLocalOf<TimeSource> { TimeSource.Monotonic }

@Composable
fun NaviampTooltip(text: String, colors: NaviampColors, content: @Composable () -> Unit) {
    val enabled = LocalNaviampTooltipsEnabled.current && text.isNotBlank()
    val anchor = remember { MutableInteractionSource() }
    val popup = remember { MutableInteractionSource() }
    val anchorHovered by anchor.collectIsHoveredAsState()
    val popupHovered by popup.collectIsHoveredAsState()
    val focused = LocalWindowInfo.current.isWindowFocused
    val timeSource = LocalNaviampTooltipTimeSource.current
    val epoch = remember(timeSource) { timeSource.markNow() }
    var anchorBounds by remember { mutableStateOf<Rect?>(null) }
    var state by remember(text, enabled, focused, timeSource) { mutableStateOf(NaviampTooltipHoverState()) }
    LaunchedEffect(anchorHovered, popupHovered, enabled, focused, text) {
        state = state.hover(enabled && focused && (anchorHovered || popupHovered), epoch.elapsedNow().inWholeMilliseconds)
    }
    LaunchedEffect(state.deadlineMillis) {
        state.deadlineMillis?.let { deadline ->
            delay((deadline - epoch.elapsedNow().inWholeMilliseconds).coerceAtLeast(0))
            state = state.advance(epoch.elapsedNow().inWholeMilliseconds)
        }
    }
    Box(Modifier.hoverable(anchor, enabled = enabled).onGloballyPositioned { anchorBounds = it.boundsInWindow() }) {
        content()
        val bounds = anchorBounds
        if (state.visible && bounds != null && bounds.width > 0 && bounds.height > 0) {
            NaviampPopupPresence(blocksWindowEscape = false)
            NaviampMeasuredTooltip(bounds) {
              Box(Modifier.padding(6.dp)) {
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
}

/** The measuring slot is never placed, so no tooltip pixels can appear at a provisional origin. */
@Composable
private fun NaviampMeasuredTooltip(anchor: Rect, content: @Composable () -> Unit) {
    val viewport = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val surface = LocalNaviampOwnedDialogSurface.current
    val properties = remember { PopupProperties(focusable = false, dismissOnClickOutside = false) }
    val margin = with(density) { 4.dp.roundToPx() }
    if (viewport.width <= margin * 2 || viewport.height <= margin * 2) return
    SubcomposeLayout(Modifier.requiredSize(0.dp)) { _ ->
        val measured = subcompose("measure") {
            // Measurement must not add a second tooltip to the accessibility tree.
            Box(Modifier.clearAndSetSemantics {}) { content() }
        }.single().measure(Constraints(
            maxWidth = (viewport.width - 2 * margin).coerceAtLeast(1),
            maxHeight = (viewport.height - 2 * margin).coerceAtLeast(1),
        ))
        val position = naviampTooltipPosition(
            IntRect(anchor.topLeft.round(), IntSize(anchor.width.roundToInt(), anchor.height.roundToInt())), viewport,
            IntSize(measured.width, measured.height), margin,
        )
        val size = with(density) { DpSize(measured.width.toDp(), measured.height.toDp()) }
        val offset = with(density) { DpOffset(position.x.toDp(), position.y.toDp()) }
        val visible = subcompose("visible") {
            if (surface != null) surface.RenderPositioned(size, offset, properties, {}, content)
            else NaviampPositionedTooltipPopup(size, offset, properties, {}, content)
        }.map { it.measure(Constraints.fixed(0, 0)) }
        layout(0, 0) { visible.forEach { it.place(0, 0) } }
    }
}

/** Center above the anchor; flip below at the top edge and clamp to the owner viewport. */
internal fun naviampTooltipPosition(anchor: IntRect, viewport: IntSize, popup: IntSize, margin: Int): IntOffset {
    fun clamp(value: Int, extent: Int, available: Int): Int =
        if (extent + 2 * margin <= available) value.coerceIn(margin, available - extent - margin) else 0
    val above = anchor.top - popup.height - margin
    val y = if (above >= margin) above else anchor.bottom + margin
    return IntOffset(clamp(anchor.left + (anchor.width - popup.width) / 2, popup.width, viewport.width),
        clamp(y, popup.height, viewport.height))
}

@Composable
internal fun NaviampPositionedTooltipPopup(size: DpSize, offset: DpOffset, properties: PopupProperties,
    onCloseRequest: () -> Unit, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val position = with(density) { IntOffset(offset.x.roundToPx(), offset.y.roundToPx()) }
    val provider = remember(position) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
            layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: IntSize) = position
    } }
    Popup(popupPositionProvider = provider, onDismissRequest = onCloseRequest, properties = properties) {
        Box(Modifier.requiredSize(size), propagateMinConstraints = true) { content() }
    }
}
