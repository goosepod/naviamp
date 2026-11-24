package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

enum class NaviampQueueUndoKind {
    RemovedTrack,
    ClearedQueue,
    ReplacedUpcoming,
    ReplacedQueue,
}

/** A pending queue undo; [subject] is the removed track or, for a replaced queue, the track Undo resumes. */
data class NaviampQueueUndoUi(
    val id: Long,
    val kind: NaviampQueueUndoKind,
    val subject: String? = null,
)

enum class NaviampQueueUndoAction {
    Undo,
    Dismiss,
    /** Carries the presentation's accessibility-adjusted timeout for the shared undo window. */
    Presented,
    FocusGained,
    FocusLost,
}

data class NaviampQueueUndoActionRequest(
    val id: Long,
    val action: NaviampQueueUndoAction,
    val timeoutMillis: Long? = null,
)

/** Base visible duration; Core widens it when the platform asks for more time for actionable content. */
const val NaviampQueueUndoBaseTimeoutMillis = 10_000L

/** Tallest Now Playing bottom action row (Radio, DJs, Collapse, Cast, Lyrics, More). */
val NaviampNowPlayingBottomActionsHeight = 46.dp

/** Lifts bottom notices above the Now Playing bottom actions while the player is open. */
val NaviampNowPlayingNoticeClearance = NaviampNowPlayingBottomActionsHeight + 8.dp

/** Where the open Now Playing panel's controls start, so bottom notices can sit above all of them. */
@Stable
class NaviampNowPlayingNoticeAnchor {
    var controlsTop: Float? by mutableStateOf(null)
        internal set
}

val LocalNaviampNowPlayingNoticeAnchor = staticCompositionLocalOf<NaviampNowPlayingNoticeAnchor?> { null }

/** Transient bottom notices share the content inset policy so system bars and the keyboard never hide them. */
@Composable
fun NaviampBottomNoticeStack(
    modifier: Modifier = Modifier,
    systemInsets: WindowInsets = WindowInsets.safeDrawing,
    bottomClearance: Dp = 0.dp,
    avoidAboveInRoot: Float? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var stackBottom by remember { mutableStateOf<Float?>(null) }
    val density = LocalDensity.current
    val controlsClearance = with(density) {
        val bottom = stackBottom
        if (avoidAboveInRoot == null || bottom == null || bottom <= avoidAboveInRoot) 0.dp
        else (bottom - avoidAboveInRoot).toDp() + NaviampNoticeControlsMargin
    }
    Column(
        modifier.fillMaxWidth().naviampContentInsets(systemInsets)
            // The stack grows upward from a fixed bottom edge, so lifting it never moves this measurement.
            .onGloballyPositioned { stackBottom = it.boundsInRoot().bottom }
            .padding(bottom = maxOf(bottomClearance, controlsClearance)),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

private val NaviampNoticeControlsMargin = 8.dp

/** Publishes the top of the Now Playing controls block so notices can sit above it. */
@Composable
internal fun Modifier.naviampNowPlayingNoticeAnchor(enabled: Boolean): Modifier {
    val anchor = LocalNaviampNowPlayingNoticeAnchor.current ?: return this
    if (!enabled) return this
    DisposableEffect(anchor) { onDispose { anchor.controlsTop = null } }
    return onGloballyPositioned { anchor.controlsTop = it.boundsInRoot().top }
}

@Composable
fun NaviampQueueUndoNotice(
    colors: NaviampColors,
    undo: NaviampQueueUndoUi,
    onAction: (NaviampQueueUndoActionRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnAction = rememberUpdatedState(onAction)
    val accessibility = LocalAccessibilityManager.current
    val focused = remember(undo.id) { mutableStateOf(false) }
    LaunchedEffect(undo.id, accessibility) {
        val timeout = accessibility?.calculateRecommendedTimeoutMillis(
            NaviampQueueUndoBaseTimeoutMillis,
            containsIcons = false,
            containsText = true,
            containsControls = true,
        ) ?: NaviampQueueUndoBaseTimeoutMillis
        currentOnAction.value(NaviampQueueUndoActionRequest(undo.id, NaviampQueueUndoAction.Presented, timeout))
    }
    val message = queueUndoMessage(undo)
    val undoLabel = stringResource(Res.string.queue_undo_action)
    val undoDescription = stringResource(Res.string.queue_undo_action_description, message)
    Row(
        modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(colors.background, RoundedCornerShape(8.dp))
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            .onFocusChanged { state ->
                if (state.hasFocus == focused.value) return@onFocusChanged
                focused.value = state.hasFocus
                currentOnAction.value(NaviampQueueUndoActionRequest(undo.id,
                    if (state.hasFocus) NaviampQueueUndoAction.FocusGained else NaviampQueueUndoAction.FocusLost))
            }
            .testTag("queue-undo-notice"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            color = colors.primaryText,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        TextButton(
            onClick = { currentOnAction.value(NaviampQueueUndoActionRequest(undo.id, NaviampQueueUndoAction.Undo)) },
            modifier = Modifier.semantics { contentDescription = undoDescription }.testTag("queue-undo-action"),
        ) { Text(undoLabel) }
        TextButton(
            onClick = { currentOnAction.value(NaviampQueueUndoActionRequest(undo.id, NaviampQueueUndoAction.Dismiss)) },
            modifier = Modifier.testTag("queue-undo-dismiss"),
        ) { Text(stringResource(Res.string.common_close)) }
    }
}

@Composable
private fun queueUndoMessage(undo: NaviampQueueUndoUi): String = when (undo.kind) {
    NaviampQueueUndoKind.RemovedTrack -> stringResource(Res.string.queue_undo_removed_track, undo.subject.orEmpty())
    NaviampQueueUndoKind.ClearedQueue -> stringResource(Res.string.queue_undo_cleared)
    NaviampQueueUndoKind.ReplacedUpcoming -> stringResource(Res.string.queue_undo_replaced_upcoming)
    NaviampQueueUndoKind.ReplacedQueue -> stringResource(Res.string.queue_undo_replaced_resumes, undo.subject.orEmpty())
}
