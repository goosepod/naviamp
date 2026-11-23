package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

@Composable
fun NaviampSomethingNewNoticeBar(
    colors: NaviampColors,
    notice: NaviampSomethingNewNotice,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.background(colors.background).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
