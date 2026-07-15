package app.naviamp.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.ui.DownloadedTrackAction
import app.naviamp.ui.DownloadedTrackActionRequest
import app.naviamp.ui.NaviampAction
import app.naviamp.ui.NaviampDownloadedTrackUi
import app.naviamp.ui.NaviampPageTitle
import app.naviamp.ui.downloadRowActions
import app.naviamp.ui.oneDecimalLabel
import app.naviamp.ui.storageBytesLabel

@Composable
fun DesktopDownloadsPanel(
    appColors: DesktopAppColors,
    downloads: List<NaviampDownloadedTrackUi>,
    status: String?,
    downloadBytes: Long,
    maxDownloadBytes: Long,
    audioCacheCount: Long,
    audioCacheBytes: Long,
    maxAudioCacheBytes: Long,
    onDownloadAction: (DownloadedTrackActionRequest) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NaviampPageTitle("Offline Mode", appColors)
                Text(
                    "${downloads.size} files - ${downloadBytes.storageBytesLabel()} of " +
                        maxDownloadBytes.storageBytesLabel(),
                    color = appColors.secondaryText,
                    fontSize = 12.sp,
                )
            }
        }

        DesktopOfflineDashboardSummary(
            appColors = appColors,
            downloads = downloads,
            downloadBytes = downloadBytes,
            maxDownloadBytes = maxDownloadBytes,
            audioCacheCount = audioCacheCount,
            audioCacheBytes = audioCacheBytes,
            maxAudioCacheBytes = maxAudioCacheBytes,
        )

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
                DesktopMediaRow(
                    appColors = appColors,
                    onClick = { onDownloadAction(DownloadedTrackActionRequest(download, DownloadedTrackAction.Select)) },
                    verticalPadding = 3.dp,
                ) {
                    DesktopCoverArtThumb(
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
                        onClick = { onDownloadAction(DownloadedTrackActionRequest(download, DownloadedTrackAction.Remove)) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = removeAction.icon,
                            contentDescription = removeAction.label,
                            tint = appColors.mutedText,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    DesktopRowOverflowMenu(
                        appColors = appColors,
                        items = rowActions.mapNotNull { action ->
                            when (action.action) {
                                NaviampAction.AddToPlaylist -> DesktopRowMenuItem(action.label, action.icon, {
                                    onDownloadAction(
                                        DownloadedTrackActionRequest(download, DownloadedTrackAction.AddToPlaylist),
                                    )
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

@Composable
private fun DesktopOfflineDashboardSummary(
    appColors: DesktopAppColors,
    downloads: List<NaviampDownloadedTrackUi>,
    downloadBytes: Long,
    maxDownloadBytes: Long,
    audioCacheCount: Long,
    audioCacheBytes: Long,
    maxAudioCacheBytes: Long,
) {
    val ready = downloads.isNotEmpty()
    val readyMessage = if (ready) {
        "Ready for offline playback and Android Auto Downloads browsing."
    } else {
        "Download albums, playlists, or tracks before using offline mode."
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.16f))
            .padding(10.dp),
    ) {
        Text(
            "OFFLINE DASHBOARD",
            color = appColors.primaryText,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
        Text(
            readyMessage,
            color = if (ready) appColors.primaryText else appColors.secondaryText,
            fontSize = 12.sp,
        )
        DesktopOfflineDashboardMetric(
            appColors = appColors,
            label = "Downloaded tracks",
            value = downloads.size.toString(),
            detail = "${downloadBytes.storageBytesLabel()} used - " +
                "${storagePercentLabel(downloadBytes, maxDownloadBytes)} of download budget",
        )
        DesktopOfflineDashboardMetric(
            appColors = appColors,
            label = "Playback cache",
            value = audioCacheCount.toString(),
            detail = "${audioCacheBytes.storageBytesLabel()} used - " +
                "${storagePercentLabel(audioCacheBytes, maxAudioCacheBytes)} of streaming cache",
        )
        DesktopOfflineDashboardMetric(
            appColors = appColors,
            label = "Pending actions",
            value = "0",
            detail = "Downloads are applied immediately; failed sync tracking is not stored yet.",
        )
    }
}

@Composable
private fun DesktopOfflineDashboardMetric(
    appColors: DesktopAppColors,
    label: String,
    value: String,
    detail: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(value, color = appColors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
            Text(label, color = appColors.secondaryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = appColors.mutedText, fontSize = 10.sp)
        }
    }
}

private fun storagePercentLabel(
    usedBytes: Long,
    maxBytes: Long,
): String =
    if (maxBytes > 0L) {
        ((usedBytes.toDouble() / maxBytes.toDouble()) * 100.0)
            .coerceIn(0.0, 100.0)
            .oneDecimalLabel() + "%"
    } else {
        "0.0%"
    }
