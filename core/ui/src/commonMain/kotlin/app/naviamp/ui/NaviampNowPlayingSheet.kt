package app.naviamp.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal const val NaviampNowPlayingSheetDistanceFraction = 0.25f
internal const val NaviampNowPlayingSheetFlingDpPerSecond = 1000f
internal const val NaviampNowPlayingSheetTestTag = "now-playing-sheet"
private const val NaviampNowPlayingSheetScrimAlpha = 0.32f

internal fun naviampNowPlayingSheetFlingThresholdPx(density: Float): Float =
    NaviampNowPlayingSheetFlingDpPerSecond * density

/**
 * Decides where a released Now Playing drag settles. [closedFraction] is 0 when the player covers
 * the screen and 1 when only the mini player remains; [velocityPx] is positive toward closed.
 */
internal fun naviampNowPlayingSheetSettlesOpen(
    startOpen: Boolean,
    closedFraction: Float,
    velocityPx: Float,
    flingThresholdPx: Float,
): Boolean = when {
    velocityPx >= flingThresholdPx -> false
    velocityPx <= -flingThresholdPx -> true
    startOpen -> closedFraction < NaviampNowPlayingSheetDistanceFraction
    else -> closedFraction <= 1f - NaviampNowPlayingSheetDistanceFraction
}

private fun PointerInputChange.isTouch(): Boolean = type == PointerType.Touch || type == PointerType.Stylus

/** Shared phone presentation state for Now Playing sliding over the browse screen. */
@Stable
internal class NaviampNowPlayingSheetState internal constructor(
    initiallyOpen: Boolean,
    private val scope: CoroutineScope,
    private val flingThresholdPx: Float,
) {
    var closedFraction by mutableFloatStateOf(if (initiallyOpen) 0f else 1f)
        private set
    var dragging by mutableStateOf(false)
        private set
    internal var open by mutableStateOf(initiallyOpen)
        private set
    internal var heightPx = 0f
    private var dragStartedOpen = initiallyOpen
    private var lastVelocity = 0f
    private var animation: Job? = null

    val presented by derivedStateOf { open || dragging || closedFraction < 1f }

    /** The settled player hides the browse layer completely, so that layer must not draw. */
    val coversBase by derivedStateOf { open && !dragging && closedFraction <= 0f }

    internal fun onOpenChanged(value: Boolean) {
        open = value
        if (!dragging) settleTo(value)
    }

    internal fun startDrag() {
        animation?.cancel()
        dragStartedOpen = open
        dragging = true
    }

    /** Moves the sheet by [deltaPx] (positive toward closed) and returns the pixels it used. */
    internal fun dragBy(deltaPx: Float): Float {
        if (heightPx <= 0f) return 0f
        val next = (closedFraction + deltaPx / heightPx).coerceIn(0f, 1f)
        val used = (next - closedFraction) * heightPx
        closedFraction = next
        return used
    }

    internal fun release(velocityPx: Float, onOpen: () -> Unit, onClose: () -> Unit) {
        if (!dragging) return
        dragging = false
        val settleOpen = naviampNowPlayingSheetSettlesOpen(dragStartedOpen, closedFraction, velocityPx, flingThresholdPx)
        lastVelocity = if (heightPx > 0f) velocityPx / heightPx else 0f
        settleTo(settleOpen)
        if (settleOpen != open) if (settleOpen) onOpen() else onClose()
    }

    private fun settleTo(open: Boolean) {
        val target = if (open) 0f else 1f
        animation?.cancel()
        if (closedFraction == target) {
            lastVelocity = 0f
            return
        }
        animation = scope.launch {
            animate(
                initialValue = closedFraction,
                targetValue = target,
                initialVelocity = lastVelocity,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = 0.001f,
                ),
            ) { value, velocity ->
                closedFraction = value
                lastVelocity = velocity
            }
            lastVelocity = 0f
        }
    }
}

@Composable
internal fun rememberNaviampNowPlayingSheetState(open: Boolean): NaviampNowPlayingSheetState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val state = remember(scope, density) {
        NaviampNowPlayingSheetState(open, scope, naviampNowPlayingSheetFlingThresholdPx(density))
    }
    LaunchedEffect(state, open) { state.onOpenChanged(open) }
    return state
}

private class TopFractionShape(private val fraction: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(0f, 0f, size.width, size.height * fraction))
}

/**
 * Draws [base] (browse screen, mini player and navigation) and slides [sheet] over it. Movement is
 * a graphics-layer translation and clip so dragging never relayouts or recomposes either layer.
 */
@Composable
internal fun NaviampNowPlayingSheetLayers(
    state: NaviampNowPlayingSheetState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    base: @Composable () -> Unit,
    sheet: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize().onSizeChanged { state.heightPx = it.height.toFloat() }) {
        Box(
            Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        if (!state.coversBase) placeable.place(0, 0)
                    }
                }
                .graphicsLayer {
                    val fraction = state.closedFraction
                    clip = fraction < 1f
                    shape = if (fraction < 1f) TopFractionShape(fraction) else RectangleShape
                },
        ) {
            base()
            if (state.presented) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = NaviampNowPlayingSheetScrimAlpha * (1f - state.closedFraction) }
                        .background(Color.Black),
                )
            }
        }
        if (state.presented) {
            NaviampNowPlayingDismissSurface(state, onClose, sheet)
        }
    }
}

private class NaviampSheetGesture {
    var touch = false
    var contentScrolled = false
    val velocity = VelocityTracker()
}

@Composable
private fun NaviampNowPlayingDismissSurface(
    state: NaviampNowPlayingSheetState,
    onClose: () -> Unit,
    sheet: @Composable () -> Unit,
) {
    val currentOnClose by rememberUpdatedState(onClose)
    val gesture = remember { NaviampSheetGesture() }
    val connection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource) =
                if (state.dragging && source == NestedScrollSource.UserInput) {
                    Offset(0f, state.dragBy(available.y))
                } else {
                    Offset.Zero
                }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || !gesture.touch || !state.open) {
                    return Offset.Zero
                }
                // A gesture that scrolled the queue or lyrics stays a scroll, so the player
                // only minimizes when the drag began with the content already at the top.
                if (consumed.y != 0f) gesture.contentScrolled = true
                if (gesture.contentScrolled || available.y <= 0f) return Offset.Zero
                if (!state.dragging) state.startDrag()
                return Offset(0f, state.dragBy(available.y))
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!state.dragging) return Velocity.Zero
                state.release(gesture.velocity.calculateVelocity().y, onOpen = {}, onClose = currentOnClose)
                return available
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            // Velocity and fallback drags are measured here, outside the moving layer, so the
            // translation applied below does not feed back into the finger's measured motion.
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    gesture.touch = down.isTouch()
                    gesture.contentScrolled = false
                    gesture.velocity.resetTracking()
                    gesture.velocity.addPointerInputChange(down)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.firstOrNull { it.id == down.id }?.let { gesture.velocity.addPointerInputChange(it) }
                    } while (event.changes.any { it.pressed })
                }
            }
            .nestedScroll(connection)
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!down.isTouch() || !state.open) return@awaitEachGesture
                    var overSlop = 0f
                    val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                        if (over > 0f && !state.dragging) {
                            change.consume()
                            overSlop = over
                        }
                    } ?: return@awaitEachGesture
                    state.startDrag()
                    state.dragBy(overSlop)
                    val completed = verticalDrag(drag.id) { change ->
                        state.dragBy(change.positionChange().y)
                        change.consume()
                    }
                    val velocity = if (completed) gesture.velocity.calculateVelocity().y else 0f
                    state.release(velocity, onOpen = {}, onClose = currentOnClose)
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = state.closedFraction * size.height }
                .testTag(NaviampNowPlayingSheetTestTag),
        ) { sheet() }
    }
}

/** Lets a touch drag upward on the mini player raise Now Playing; taps keep their own handling. */
internal fun Modifier.naviampNowPlayingOpenGesture(
    state: NaviampNowPlayingSheetState,
    onOpen: () -> Unit,
): Modifier = pointerInput(state, onOpen) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!down.isTouch() || state.open) return@awaitEachGesture
        val velocity = VelocityTracker().apply { addPointerInputChange(down) }
        var overSlop = 0f
        val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
            if (over < 0f) {
                change.consume()
                overSlop = over
            }
        } ?: return@awaitEachGesture
        velocity.addPointerInputChange(drag)
        state.startDrag()
        state.dragBy(overSlop)
        val completed = verticalDrag(drag.id) { change ->
            velocity.addPointerInputChange(change)
            state.dragBy(change.positionChange().y)
            change.consume()
        }
        state.release(if (completed) velocity.calculateVelocity().y else 0f, onOpen = onOpen, onClose = {})
    }
}
