package app.naviamp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.provider.*
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Library row typography/density, with only explicitly supported discovery actions. */
@Composable
internal fun NaviampDiscoveryReleaseTrackList(discovery: SharedProviderDiscoveryUi,
    artist: SharedProviderDiscoveryArtistUi, release: SharedDiscoveryReleaseUi, colors: NaviampColors,
    actions: NaviampHomeActions, playback: DiscoverySamplePlaybackUi, waitingRequest: Boolean) {
    val tracks = release.tracks?.tracks.orEmpty()
    val hasPreviews = tracks.any { it.previewId != null }
    val numberWidth = trackNumberColumnWidth(tracks.maxOfOrNull { it.trackNumber ?: 0 } ?: 1)
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        var previousDisc: Int? = null
        tracks.forEachIndexed { index, track ->
            if (track.discNumber != previousDisc) {
                track.discNumber?.let { DiscoveryText(stringResource(Res.string.album_disc_number, it),
                    Modifier.padding(vertical = 8.dp), color = colors.secondaryText, fontSize = 12.sp) }
                previousDisc = track.discNumber
            }
            TrackRow(SharedTrackRowUi(id = "discovery:${release.release.id}:$index", title = track.title,
                subtitle = if (track.previewId == null) stringResource(Res.string.discovery_preview_unavailable) else "",
                durationLabel = track.durationMillis?.let { (it / 1000.0).durationLabel() }.orEmpty(), canToggleFavorite = false),
                colors, onTrackAction = null, canSelect = false, canStartRadio = false, canAddToQueue = false,
                canDownload = false, canAddToPlaylist = false, showCoverArt = false, verticalPadding = 0.dp,
                modifier = Modifier.heightIn(min = 40.dp), trackNumber = track.trackNumber,
                trackNumberWidth = numberWidth,
                leadingContent = if (!hasPreviews) null else { { Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) { track.previewId?.let { preview ->
                    val active = playback.trackId == DiscoveryPreviewTrackPrefix + preview.value
                    val playing = active && playback.playing
                    IconButton(enabled = !active || playback.canToggle, modifier = Modifier.size(36.dp), onClick = {
                        actions.onDiscoveryReleaseAction(discovery.sourceId, discovery.sourceGeneration,
                            release.release.id, ProviderDiscoveryAction.PlaySample(preview.value))
                    }) { Icon(if (playing) NaviampTransportIcons.Pause else NaviampTransportIcons.Play,
                        stringResource(if (playing) Res.string.transport_pause else Res.string.discovery_play_sample),
                        Modifier.size(17.dp), tint = colors.primaryText) }
                } } } },
                trailingContent = {
                    DiscoveryText(track.durationMillis?.let { (it / 1000.0).durationLabel() }.orEmpty(), color = colors.mutedText, fontSize = 11.sp)
                    track.recordingMbid?.let { recording ->
                        val action = ProviderDiscoveryAction.RequestTrack(recording.value)
                        if (artist.details?.canRequestTracks == true) IconButton(modifier = Modifier.size(36.dp),
                            enabled = !waitingRequest && release.requestContext != null && !artist.actionBusy && !artist.savingFeedback &&
                                action !in artist.blockedActions && recording !in release.ownership?.ownedRecordings.orEmpty(),
                            onClick = { actions.onDiscoveryReleaseAction(discovery.sourceId, discovery.sourceGeneration, release.release.id, action) }) {
                            Icon(NaviampIcons.Downloads, stringResource(Res.string.discovery_request_track), Modifier.size(17.dp), tint = colors.secondaryText)
                        }
                    }
                })
            track.recordingMbid?.let { recording ->
                if (recording in release.ownership?.ownedRecordings.orEmpty())
                    DiscoveryText(stringResource(Res.string.discovery_request_owned), color = colors.secondaryText, fontSize = 11.sp)
                artist.requestOperations[ProviderDiscoveryAction.RequestTrack(recording.value)]?.let { operation ->
                    artist.acquisitions[ProviderDiscoveryAction.RequestTrack(recording.value)]
                        ?.let { DiscoveryAcquisitionResult(it, colors) } ?: DiscoveryOperationResult(operation, colors)
                }
            }
        }
    }
}
