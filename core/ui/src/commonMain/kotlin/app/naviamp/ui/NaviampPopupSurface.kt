package app.naviamp.ui

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** A host fact, not a platform-specific menu/dialog implementation. */
internal val LocalNaviampOwnedPopupWindows = staticCompositionLocalOf { false }

/** Fixed native bounds separate surface geometry from hover, shadows and entrance transitions. */
private object WindowOrigin : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
}

internal fun naviampMenuPosition(anchor: IntRect, window: IntSize, content: IntSize,
    direction: LayoutDirection, offset: IntOffset, margin: Int): IntOffset {
    val horizontal = if (direction == LayoutDirection.Ltr)
        listOf(anchor.left + offset.x, anchor.right - content.width + offset.x)
    else listOf(anchor.right - content.width - offset.x, anchor.left - offset.x)
    val x = horizontal.firstOrNull { it >= 0 && it + content.width <= window.width }
        ?: horizontal.first().coerceIn(0, (window.width - content.width).coerceAtLeast(0))
    val top = margin.coerceAtMost((window.height - content.height).coerceAtLeast(0))
    val bottom = (window.height - margin - content.height).coerceAtLeast(top)
    val y = listOf(anchor.bottom + offset.y, anchor.top - content.height + offset.y)
        .firstOrNull { it in top..bottom }
        ?: (anchor.top - content.height / 2 + offset.y).coerceIn(top, bottom)
    return IntOffset(x, y)
}

@Composable
private fun NaviampViewportPopup(onDismissRequest: () -> Unit, properties: PopupProperties,
    scrim: Color = Color.Transparent, content: @Composable BoxScope.() -> Unit) {
    Popup(popupPositionProvider = WindowOrigin, onDismissRequest = onDismissRequest,
        properties = properties) {
        Box(Modifier.fillMaxSize().clipToBounds().background(scrim), content = content)
    }
}

/** Reserve the complete drawing extent before animated or hovered content is recorded. */
@Composable
internal fun NaviampWindowDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit,
    modifier: Modifier, offset: DpOffset, containerColor: Color, shape: Shape,
    shadowElevation: Dp, properties: PopupProperties,
    content: @Composable ColumnScope.() -> Unit) {
    val expandedState = remember { MutableTransitionState(false) }
    expandedState.targetState = expanded
    if (!expandedState.currentState && !expandedState.targetState) return
    val transition = updateTransition(expandedState, label = "Naviamp menu")
    val alpha by transition.animateFloat(transitionSpec = { tween(120) }, label = "opacity") { if (it) 1f else 0f }
    val scale by transition.animateFloat(transitionSpec = { tween(150) }, label = "scale") { if (it) 1f else .8f }
    val density = LocalDensity.current
    val pixelOffset = with(density) { IntOffset(offset.x.roundToPx(), offset.y.roundToPx()) }
    val margin = with(density) { 48.dp.roundToPx() }
    val position = remember(pixelOffset, margin) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
            layoutDirection: LayoutDirection, popupContentSize: IntSize) =
            naviampMenuPosition(anchorBounds, windowSize, popupContentSize, layoutDirection, pixelOffset, margin)
    } }
    val scroll = rememberScrollState()
    var moveFocus by remember { mutableStateOf<(FocusDirection) -> Boolean>({ false }) }
    val menuKeys = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) false
            else when (event.key) {
                Key.DirectionDown -> moveFocus(FocusDirection.Next)
                Key.DirectionUp -> moveFocus(FocusDirection.Previous)
                else -> false
            }
        }
    Popup(popupPositionProvider = position, onDismissRequest = onDismissRequest, properties = properties) {
        val focus = LocalFocusManager.current
        SideEffect { moveFocus = focus::moveFocus }
        NaviampPopupPresence()
        Box(menuKeys.drawBehind {
            val outset = 32.dp.toPx()
            // Src clears the transparent popup backing and is recorded even at zero alpha.
            // It is outside the entrance transform and reserves shadow/hover bounds immediately.
            drawRect(Color.Transparent, Offset(-outset, -outset),
                Size(size.width + outset * 2, size.height + outset * 2), blendMode = BlendMode.Src)
        }) {
            Surface(color = containerColor, shape = shape, shadowElevation = shadowElevation,
                modifier = Modifier.graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }) {
                Column(modifier.padding(vertical = 8.dp).width(IntrinsicSize.Max).verticalScroll(scroll), content = content)
            }
        }
    }
}

@Composable
internal fun NaviampDialog(onDismissRequest: () -> Unit, properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit) {
    if (!LocalNaviampOwnedPopupWindows.current) {
        Dialog(onDismissRequest, properties, content)
        return
    }
    val entered = remember { MutableTransitionState(false).apply { targetState = true } }
    val transition = updateTransition(entered, label = "Naviamp dialog")
    val alpha by transition.animateFloat(transitionSpec = { tween(200) }, label = "opacity") { if (it) 1f else .2f }
    val scale by transition.animateFloat(transitionSpec = { tween(200) }, label = "scale") { if (it) 1f else .95f }
    NaviampViewportPopup(onDismissRequest, PopupProperties(focusable = true,
        dismissOnBackPress = properties.dismissOnBackPress, dismissOnClickOutside = false),
        scrim = Color.Black.copy(alpha = .6f * alpha)) {
        Box(Modifier.fillMaxSize().pointerInput(onDismissRequest, properties.dismissOnClickOutside) {
            if (properties.dismissOnClickOutside) detectTapGestures { onDismissRequest() }
        }.padding(24.dp), contentAlignment = Alignment.Center) {
            Box((if (properties.usePlatformDefaultWidth) Modifier.widthIn(max = 560.dp) else Modifier)
                .graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }
                .semantics { dialog() }, propagateMinConstraints = true) { content() }
        }
    }
}

/** Preserve Material's content slots and styling; only replace the popup surface owner. */
@Composable
internal fun NaviampAlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null, title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null, shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()) {
    if (!LocalNaviampOwnedPopupWindows.current) {
        AlertDialog(onDismissRequest, confirmButton, modifier, dismissButton, icon, title, text, shape,
            containerColor, iconContentColor, titleContentColor, textContentColor, tonalElevation, properties)
        return
    }
    NaviampDialog(onDismissRequest, properties) {
        Surface(modifier.widthIn(min = 280.dp, max = 560.dp), shape = shape,
            color = containerColor, tonalElevation = tonalElevation) {
            Column(Modifier.padding(24.dp)) {
                icon?.let {
                    CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                        Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)) { it() }
                    }
                }
                title?.let {
                    CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                            Box(Modifier.align(if (icon == null) Alignment.Start else Alignment.CenterHorizontally)
                                .padding(bottom = 16.dp)) { it() }
                        }
                    }
                }
                text?.let {
                    CompositionLocalProvider(LocalContentColor provides textContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                            Box(Modifier.weight(1f, fill = false).padding(bottom = 24.dp)) { it() }
                        }
                    }
                }
                FlowRow(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}
