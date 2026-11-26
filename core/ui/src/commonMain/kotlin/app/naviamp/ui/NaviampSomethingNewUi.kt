package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.naviamp.domain.home.HomeStationSomethingNew
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Outcomes of Play something new that the user must be told about. */
enum class NaviampSomethingNewNotice(internal val message: StringResource) {
    NothingUnplayed(Res.string.something_new_nothing_unplayed),
    NothingUnplayedOffline(Res.string.something_new_nothing_unplayed_offline),
    NothingDownloaded(Res.string.something_new_no_downloads),
    PlayCountsUnavailable(Res.string.something_new_no_play_counts),
    Failed(Res.string.something_new_failed),
    PlayingFromDownloads(Res.string.something_new_from_downloads),
}

/** Bottom notices whose lifetime Core owns. */
enum class NaviampTimedNotice { SomethingNew, Radio, Status }

enum class NaviampTimedNoticeAction {
    /** Carries the presentation's accessibility-adjusted timeout. */
    Presented,
    FocusGained,
    FocusLost,
}

data class NaviampTimedNoticeRequest(
    val notice: NaviampTimedNotice,
    val action: NaviampTimedNoticeAction,
    val timeoutMillis: Long? = null,
)

/**
 * Same base window and accessibility rule as the queue Undo notice (#685); unify both when it lands.
 * Core widens it when the platform asks for more time for content with controls.
 */
const val NaviampNoticeBaseTimeoutMillis = 10_000L

/** Reports presentation and focus so Core can time [key]'s notice; the caller keeps its own layout. */
@Composable
internal fun Modifier.timedNotice(key: Any, onLifetime: (NaviampTimedNoticeAction, Long?) -> Unit): Modifier {
    val current = rememberUpdatedState(onLifetime)
    val accessibility = LocalAccessibilityManager.current
    // Not keyed: when a new notice replaces one in the same bar, focus can stay on its controls
    // without a new focus event, so the new notice must inherit the focus pause.
    val focused = remember { mutableStateOf(false) }
    LaunchedEffect(key, accessibility) {
        val timeout = accessibility?.calculateRecommendedTimeoutMillis(
            NaviampNoticeBaseTimeoutMillis,
            containsIcons = false,
            containsText = true,
            containsControls = true,
        ) ?: NaviampNoticeBaseTimeoutMillis
        current.value(NaviampTimedNoticeAction.Presented, timeout)
        if (focused.value) current.value(NaviampTimedNoticeAction.FocusGained, null)
    }
    return onFocusChanged { state ->
        if (state.hasFocus == focused.value) return@onFocusChanged
        focused.value = state.hasFocus
        current.value(if (state.hasFocus) NaviampTimedNoticeAction.FocusGained else NaviampTimedNoticeAction.FocusLost, null)
    }.semantics { liveRegion = LiveRegionMode.Polite }
}

@Composable
fun NaviampSomethingNewNoticeBar(
    colors: NaviampColors,
    notice: NaviampSomethingNewNotice,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onLifetime: (NaviampTimedNoticeAction, Long?) -> Unit = { _, _ -> },
) {
    Row(modifier.timedNotice(notice, onLifetime).background(colors.background).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(notice.message), color = colors.primaryText, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_close)) }
    }
}

private val SharedHomeCollectionItemUi.isSomethingNewStation: Boolean
    get() = action == SharedHomeCollectionItemAction.SelectStation && station?.id == HomeStationSomethingNew

@Composable
internal fun SharedHomeCollectionItemUi.displayTitle(): String =
    if (isSomethingNewStation) stringResource(Res.string.something_new_title) else title

@Composable
internal fun SharedHomeCollectionItemUi.displaySubtitle(): String =
    if (isSomethingNewStation) stringResource(Res.string.something_new_subtitle) else subtitle
