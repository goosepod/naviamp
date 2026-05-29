package app.naviamp.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.ui.NaviampAction
import app.naviamp.ui.NaviampDownloadedTrackUi
import app.naviamp.ui.downloadRowActions
import app.naviamp.ui.storageBytesLabel

@Composable
fun DownloadsPanel(
    appColors: AppColors,
    downloads: List<NaviampDownloadedTrackUi>,
    status: String?,
    downloadBytes: Long,
    maxDownloadBytes: Long,
    onTrackSelected: (NaviampDownloadedTrackUi) -> Unit,
    onRemoveDownload: (NaviampDownloadedTrackUi) -> Unit,
    onTrackAddToPlaylist: (NaviampDownloadedTrackUi) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Downloads",
                    color = appColors.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Text(
                    "${downloads.size} files - ${downloadBytes.storageBytesLabel()} of " +
                        maxDownloadBytes.storageBytesLabel(),
                    color = appColors.secondaryText,
                    fontSize = 12.sp,
                )
            }
        }

        status?.let {
            Text(it, color = appColors.secondaryText, fontSize = 12.sp)
        }

        if (downloads.isEmpty()) {
            Text(
                "Downloaded tracks will appear here.",
                color = appColors.secondaryText,
                fontSize = 12.sp,
            )
        } else {
            downloads.forEach { download ->
                val rowActions = downloadRowActions(canRemove = true, canAddToPlaylist = true)
                val removeAction = rowActions.first { it.action == NaviampAction.RemoveDownload }
                MediaRow(
                    appColors = appColors,
                    onClick = { onTrackSelected(download) },
                    verticalPadding = 3.dp,
                ) {
                    CoverArtThumb(
                        appColors = appColors,
                        coverArtUrl = download.track.coverArtUrl,
                        size = 34.dp,
                        cornerRadius = 4.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            download.track.title,
                            color = appColors.primaryText,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp,
                        )
                        Text(
                            download.track.subtitle,
                            color = appColors.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp,
                        )
                    }
                    Text(download.track.meta, color = appColors.mutedText, fontSize = 11.sp)
                    Text(
                        download.sizeBytes.storageBytesLabel(),
                        color = appColors.mutedText,
                        fontSize = 11.sp,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    IconButton(
                        onClick = { onRemoveDownload(download) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = removeAction.icon,
                            contentDescription = removeAction.label,
                            tint = appColors.mutedText,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    RowOverflowMenu(
                        appColors = appColors,
                        items = rowActions.mapNotNull { action ->
                            when (action.action) {
                                NaviampAction.AddToPlaylist -> RowMenuItem(action.label, action.icon, {
                                    onTrackAddToPlaylist(download)
                                }, action.enabled)
                                else -> null
                            }
                        },
                    )
                }
            }
        }
    }
}
