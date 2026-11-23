package app.naviamp.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val NaviampNowPlayingSheetDistanceFraction = 0.25f
internal const val NaviampNowPlayingSheetFlingDpPerSecond = 1000f
internal const val NaviampNowPlayingSheetTestTag = "now-playing-sheet"
private const val NaviampNowPlayingSheetScrimAlpha = 0.32f
internal const val NaviampNowPlayingSheetFadeMillis = 200
private const val NaviampNowPlayingSheetPreparationMillis = 500L

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

/**
 * Shared phone presentation state for Now Playing sliding over the browse screen. With reduced
 * motion every transition is a short crossfade and drags never move the player, while distance
 * and fling still decide whether a released drag opens or closes it.
 */
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
    /** Opacity of the player; below 1 only while a reduced-motion crossfade runs. */
    var sheetAlpha by mutableFloatStateOf(1f)
        private set
    internal var open by mutableStateOf(initiallyOpen)
        private set
    internal var reducedMotion = false
    internal var heightPx = 0f
    private var dragStartedOpen = initiallyOpen
    private var dragFraction = closedFraction
    private var lastVelocity = 0f
    private var animation: Job? = null
    private var preparation: Job? = null

    /** True while a touch on the mini player may become a drag, so the player is built before it moves. */
    private var preparing by mutableStateOf(false)

    val presented by derivedStateOf { open || dragging || preparing || closedFraction < 1f }

    /** The settled player hides the browse layer completely, so that layer must not be visible. */
    val coversBase by derivedStateOf { open && !dragging && closedFraction <= 0f && sheetAlpha >= 1f }

    internal fun onOpenChanged(value: Boolean) {
        open = value
        if (!dragging) settleTo(value)
    }

    internal fun startDrag() {
        animation?.cancel()
        dragStartedOpen = open
        dragFraction = closedFraction
        dragging = true
    }

    /** Moves the sheet by [deltaPx] (positive toward closed) and returns the pixels it used. */
    internal fun dragBy(deltaPx: Float): Float {
        if (heightPx <= 0f) return 0f
        val next = (dragFraction + deltaPx / heightPx).coerceIn(0f, 1f)
        val used = (next - dragFraction) * heightPx
        dragFraction = next
        if (!reducedMotion) closedFraction = next
        return used
    }

    /** Composes the player off screen when a touch lands on the mini player. */
    internal fun prepare() {
        preparation?.cancel()
        preparing = true
    }

    /** Releases a preparation that did not become a drag, after a tap has had time to open. */
    internal fun endPreparation() {
        preparation?.cancel()
        preparation = scope.launch {
            delay(NaviampNowPlayingSheetPreparationMillis)
            if (!dragging) preparing = false
        }
    }

    internal fun release(velocityPx: Float, onOpen: () -> Unit, onClose: () -> Unit) {
        if (!dragging) return
        dragging = false
        val settleOpen = naviampNowPlayingSheetSettlesOpen(dragStartedOpen, dragFraction, velocityPx, flingThresholdPx)
        lastVelocity = if (heightPx > 0f) velocityPx / heightPx else 0f
        // The command recomposes the shell, so it is committed once the player has settled
        // instead of in the frame where the finger lifts.
        settleTo(settleOpen) {
            if (settleOpen != open) if (settleOpen) onOpen() else onClose()
        }
    }

    private fun finishSettle(onSettled: (() -> Unit)?) {
        lastVelocity = 0f
        preparing = false
        onSettled?.invoke()
    }

    private fun settleTo(open: Boolean, onSettled: (() -> Unit)? = null) {
        animation?.cancel()
        if (reducedMotion) {
            fadeTo(open, onSettled)
            return
        }
        sheetAlpha = 1f
        val target = if (open) 0f else 1f
        if (closedFraction == target) {
            finishSettle(onSettled)
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
            finishSettle(onSettled)
        }
    }

    private fun fadeTo(open: Boolean, onSettled: (() -> Unit)?) {
        lastVelocity = 0f
        if (open && closedFraction > 0f) {
            sheetAlpha = 0f
            closedFraction = 0f
        }
        val target = if (open) 1f else 0f
        if (closedFraction >= 1f || sheetAlpha == target) {
            if (!open) closedFraction = 1f
            sheetAlpha = 1f
            finishSettle(onSettled)
            return
        }
        animation = scope.launch {
            animate(
                initialValue = sheetAlpha,
                targetValue = target,
                animationSpec = tween(NaviampNowPlayingSheetFadeMillis),
            ) { value, _ -> sheetAlpha = value }
            if (!open) closedFraction = 1f
            sheetAlpha = 1f
            finishSettle(onSettled)
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
    val reducedMotion = naviampReducedMotion()
    SideEffect { state.reducedMotion = reducedMotion }
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
    val covered = state.coversBase
    val focusManager = LocalFocusManager.current
    var baseHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(covered) { if (covered && baseHasFocus) focusManager.clearFocus() }
    Box(modifier.fillMaxSize().onSizeChanged { state.heightPx = it.height.toFloat() }) {
        // The covered browse layer stays placed with its recorded drawing and only turns fully
        // transparent, so uncovering it does not re-place and redraw the whole browse tree.
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (covered) {
                        Modifier
                            .clearAndSetSemantics {}
                            .focusProperties { onEnter = { cancelFocusChange() } }
                    } else {
                        Modifier
                    },
                )
                .onFocusChanged { baseHasFocus = it.hasFocus }
                .focusGroup()
                .graphicsLayer {
                    val fraction = state.closedFraction
                    val fading = state.sheetAlpha < 1f
                    clip = !fading && fraction < 1f
                    shape = if (clip) TopFractionShape(fraction) else RectangleShape
                    alpha = when {
                        state.coversBase -> 0f
                        fading -> 1f - state.sheetAlpha
                        else -> 1f
                    }
                },
        ) { base() }
        if (state.presented) {
            // A sibling of the browse layer, so showing or hiding it never re-records that layer.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val fraction = state.closedFraction
                        val fading = state.sheetAlpha < 1f
                        clip = !fading && fraction < 1f
                        shape = if (clip) TopFractionShape(fraction) else RectangleShape
                        alpha = NaviampNowPlayingSheetScrimAlpha * (1f - fraction) *
                            (if (fading) 1f - state.sheetAlpha else 1f)
                    }
                    .background(Color.Black),
            )
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
                .graphicsLayer {
                    translationY = state.closedFraction * size.height
                    alpha = state.sheetAlpha
                }
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
        state.prepare()
        val velocity = VelocityTracker().apply { addPointerInputChange(down) }
        var overSlop = 0f
        val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
            if (over < 0f) {
                change.consume()
                overSlop = over
            }
        }
        if (drag == null) {
            state.endPreparation()
            return@awaitEachGesture
        }
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
