package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A short, translatable outcome Core reports to the user in the shared bottom notice stack.
 * Only outcomes the user needs to hear about belong here; malformed commands and progress steps do not.
 */
sealed class NaviampStatusMessage(internal val text: StringResource) {
    /** Arguments are names from the user's library, shown verbatim inside the translated sentence. */
    internal open val arguments: List<String> get() = emptyList()

    data object AddedToQueue : NaviampStatusMessage(Res.string.status_added_to_queue)
    data object AddedToPlayNext : NaviampStatusMessage(Res.string.status_added_to_play_next)
    data object AlreadyInQueue : NaviampStatusMessage(Res.string.status_already_in_queue)
    data object NoTracksAvailable : NaviampStatusMessage(Res.string.status_no_tracks_available)
    data class NoTracksToPlay(val subject: String) : NaviampStatusMessage(Res.string.status_no_tracks_to_play) {
        override val arguments get() = listOf(subject)
    }
    data object RadioFailed : NaviampStatusMessage(Res.string.status_radio_failed)
    data object ItemUnavailable : NaviampStatusMessage(Res.string.status_item_unavailable)
    data object MatchedSeedsRequired : NaviampStatusMessage(Res.string.status_matched_seeds_required)
    data class AddedToPlaylist(val playlist: String) : NaviampStatusMessage(Res.string.status_added_to_playlist) {
        override val arguments get() = listOf(playlist)
    }
    data object AddedToAPlaylist : NaviampStatusMessage(Res.string.status_added_to_a_playlist)
    data class PlaylistCreated(val playlist: String) : NaviampStatusMessage(Res.string.status_playlist_created) {
        override val arguments get() = listOf(playlist)
    }
    data class QueueSaved(val playlist: String) : NaviampStatusMessage(Res.string.status_queue_saved) {
        override val arguments get() = listOf(playlist)
    }
    data object PlaylistNameBlank : NaviampStatusMessage(Res.string.status_playlist_name_blank)
    data object ChoosePlaylist : NaviampStatusMessage(Res.string.status_choose_playlist)
    data object QueueEmpty : NaviampStatusMessage(Res.string.status_queue_empty)
    data object PlaylistFailed : NaviampStatusMessage(Res.string.status_playlist_failed)
    data object FavoriteFailed : NaviampStatusMessage(Res.string.status_favorite_failed)
    data object RatingFailed : NaviampStatusMessage(Res.string.status_rating_failed)
    data object FavoritesUnsupported : NaviampStatusMessage(Res.string.status_favorites_unsupported)
    data object RatingsUnsupported : NaviampStatusMessage(Res.string.status_ratings_unsupported)
    data object AlbumUnavailable : NaviampStatusMessage(Res.string.status_album_unavailable)
    data object ArtistUnavailable : NaviampStatusMessage(Res.string.status_artist_unavailable)
    data object TrackUnavailable : NaviampStatusMessage(Res.string.status_track_unavailable)
    data object RadioDjUnavailable : NaviampStatusMessage(Res.string.status_radio_dj_unavailable)
    data object HomeSectionUnavailable : NaviampStatusMessage(Res.string.status_home_section_unavailable)
    data object ConnectionRequired : NaviampStatusMessage(Res.string.offline_playlist_action_unavailable)
    data object StreamNotSeekable : NaviampStatusMessage(Res.string.status_stream_not_seekable)
    data object ShuffleNeedsMoreTracks : NaviampStatusMessage(Res.string.status_shuffle_needs_more)
    data object NoPreviousTrack : NaviampStatusMessage(Res.string.status_no_previous_track)
    data object NoNextTrack : NaviampStatusMessage(Res.string.status_no_next_track)
    data object SleepTimerStoppedPlayback : NaviampStatusMessage(Res.string.status_sleep_timer_stopped)
    data object LibraryRefreshed : NaviampStatusMessage(Res.string.status_library_refreshed)
    data object CacheCleared : NaviampStatusMessage(Res.string.status_cache_cleared)
    data object LibraryIndexCleared : NaviampStatusMessage(Res.string.status_library_index_cleared)
    data object DatabaseReset : NaviampStatusMessage(Res.string.status_database_reset)
    data object HomeFailed : NaviampStatusMessage(Res.string.status_home_failed)
}

/** One published status; [id] distinguishes a repeat of the same message so it is shown and timed again. */
data class NaviampStatusNotice(
    val id: Long,
    val message: NaviampStatusMessage,
)

@Composable
fun naviampStatusText(message: NaviampStatusMessage): String =
    stringResource(message.text, *message.arguments.toTypedArray())

@Composable
fun NaviampStatusNoticeBar(
    colors: NaviampColors,
    notice: NaviampStatusNotice,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onLifetime: (NaviampTimedNoticeAction, Long?) -> Unit = { _, _ -> },
) {
    Row(
        modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .timedNotice(notice.id, onLifetime)
            .background(colors.background, RoundedCornerShape(8.dp))
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            .testTag("status-notice"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(naviampStatusText(notice.message), color = colors.primaryText, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss, modifier = Modifier.testTag("status-notice-dismiss")) {
            Text(stringResource(Res.string.common_close))
        }
    }
}
