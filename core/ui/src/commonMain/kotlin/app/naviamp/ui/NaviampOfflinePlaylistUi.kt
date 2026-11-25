package app.naviamp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

suspend fun offlinePlaylistActionUnavailableMessage(): String =
    getString(Res.string.offline_playlist_action_unavailable)

suspend fun offlinePlaylistNothingPlayableMessage(): String =
    getString(Res.string.offline_playlist_nothing_playable)

suspend fun offlinePlaylistTrackNotDownloadedMessage(): String =
    getString(Res.string.offline_playlist_track_not_downloaded)

suspend fun playlistDownloadFailedMessage(name: String): String = getString(Res.string.playlist_download_failed, name)

suspend fun playlistDownloadStorageLimitMessage(name: String): String =
    getString(Res.string.playlist_download_failed_storage_limit, name)

suspend fun playlistDownloadMobileDataMessage(name: String): String =
    getString(Res.string.playlist_download_failed_mobile_data, name)

suspend fun playlistAlreadyDownloadedMessage(name: String): String =
    getString(Res.string.playlist_download_already_saved, name)

@Composable
internal fun offlineAvailabilityLabel(availability: NaviampOfflineAvailabilityUi): String =
    pluralStringResource(
        Res.plurals.offline_playlist_availability,
        availability.totalCount,
        availability.availableCount,
        availability.totalCount,
    )

@Composable
internal fun offlinePlaylistSavedCopyNote(): String = stringResource(Res.string.offline_playlist_saved_copy)

/** Every saved row stays in place; unavailable rows ask Core to explain instead of playing. */
@Composable
internal fun OfflinePlaylistTrackList(
    colors: NaviampColors,
    tracks: List<SharedTrackRowUi>,
    onTrackSelected: (SharedTrackRowUi) -> Unit,
    onTrackAction: (SharedTrackRowActionRequest) -> Unit,
    capabilities: PlaylistTrackCapabilities,
) {
    val trackNumberWidth = trackNumberColumnWidth(tracks.size)
    val unavailableLabel = stringResource(Res.string.offline_playlist_track_unavailable)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        tracks.forEachIndexed { index, track ->
            val available = !track.unavailableOffline
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = if (available) null else unavailableLabel) { onTrackSelected(track) }
                    .then(if (available) Modifier else Modifier.semantics { stateDescription = unavailableLabel })
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f).alpha(if (available) 1f else UnavailableRowAlpha),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TrackNumberColumn(number = index + 1, width = trackNumberWidth, color = colors.mutedText)
                    PlaylistTrackIdentity(track = track, colors = colors, modifier = Modifier.weight(1f))
                    Text(if (available) track.meta else unavailableLabel, color = colors.mutedText, fontSize = 11.sp)
                }
                if (available) PlaylistTrackOverflowMenu(colors, track, capabilities, onTrackAction)
            }
        }
    }
}

private const val UnavailableRowAlpha = 0.45f
