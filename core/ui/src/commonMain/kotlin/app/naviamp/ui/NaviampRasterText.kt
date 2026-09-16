package app.naviamp.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal data class NaviampTextLink(val start: Int, val end: Int, val label: String, val activate: () -> Unit)

@Composable
internal fun NaviampRasterText(
    text: AnnotatedString, style: TextStyle, height: Dp, enabled: Boolean,
    alignStart: Boolean, modifier: Modifier, links: List<NaviampTextLink> = emptyList(),
) {
    var focusedLink by remember(text) { mutableStateOf<Int?>(null) }
    val position = remember { NaviampRasterPosition() }
    val currentLinks by rememberUpdatedState(links)
    val renderedText = remember(text, focusedLink) {
        val range = focusedLink?.let { links.getOrNull(it) }
        if (range == null) text else AnnotatedString.Builder(text).apply {
            addStyle(SpanStyle(textDecoration = TextDecoration.Underline), range.start, range.end)
        }.toAnnotatedString()
    }
    val resolvedStyle = androidx.compose.material3.LocalTextStyle.current.merge(style)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val layout = remember(renderedText, resolvedStyle, measurer, density, direction) {
        measurer.measure(renderedText, resolvedStyle, softWrap = false, maxLines = 1)
    }
    val bitmap = remember(layout, density, direction) {
        ImageBitmap(layout.size.width.coerceAtLeast(1), layout.size.height.coerceAtLeast(1)).also {
            CanvasDrawScope().draw(density, direction, Canvas(it), Size(it.width.toFloat(), it.height.toFloat())) { drawText(layout) }
        }
    }
    val layers = remember(bitmap, viewport, enabled, alignStart, direction, focusedLink) {
        val overflow = (bitmap.width - viewport.width).coerceAtLeast(0).toFloat()
        val rtl = direction == LayoutDirection.Rtl
        val focusRange = focusedLink?.let { links.getOrNull(it) }
        val focusX = focusRange?.let {
            val first = layout.getBoundingBox(it.start)
            val last = layout.getBoundingBox((it.end - 1).coerceAtLeast(it.start))
            (viewport.width / 2f - (first.left + last.right) / 2f).coerceIn(-overflow, 0f)
        }
        val x = when {
            focusX != null -> focusX
            overflow > 0 -> if (rtl) -overflow else 0f
            !alignStart -> (viewport.width - bitmap.width) / 2f
            rtl -> (viewport.width - bitmap.width).toFloat()
            else -> 0f
        }
        listOf(NaviampRasterLayer(bitmap, Offset(x, (viewport.height - bitmap.height) / 2f),
            translation = if (enabled && focusedLink == null) marqueeLayerMotion(overflow, rtl) else null))
    }
    val input = if (links.isEmpty()) Modifier else Modifier
        .pointerInput(layout, layers) {
            detectTapGestures { point ->
                val local = point - layers.first().origin - Offset(position.translationX(), 0f)
                if (local.x >= 0f && local.x < layout.size.width) {
                    val character = layout.getOffsetForPosition(local)
                    currentLinks.firstOrNull { character >= it.start && character < it.end }?.activate?.invoke()
                }
            }
        }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                Key.Enter, Key.Spacebar -> { currentLinks.getOrNull(focusedLink ?: 0)?.activate?.invoke(); true }
                Key.Tab -> {
                    val next = (focusedLink ?: 0) + if (event.isShiftPressed) -1 else 1
                    if (next in currentLinks.indices) { focusedLink = next; true } else false
                }
                Key.DirectionLeft, Key.DirectionRight -> {
                    focusedLink = ((focusedLink ?: 0) + if (event.key == Key.DirectionLeft) -1 else 1).coerceIn(currentLinks.indices)
                    true
                }
                else -> false
            }
        }
        .onFocusChanged { focusedLink = if (it.isFocused) focusedLink ?: 0 else null }
        .focusable()
        .semantics {
            customActions = links.map { link -> CustomAccessibilityAction(link.label) { link.activate(); true } }
            onClick { currentLinks.getOrNull(focusedLink ?: 0)?.activate?.invoke(); true }
        }
    Box(modifier.then(input).height(height).clip(RoundedCornerShape(2.dp)).onSizeChanged { viewport = it }
        .semantics { this.text = text }) {
        NaviampAnimatedRaster(layers, Modifier.fillMaxSize(), with(density) { 2.dp.toPx() }, position)
    }
}
