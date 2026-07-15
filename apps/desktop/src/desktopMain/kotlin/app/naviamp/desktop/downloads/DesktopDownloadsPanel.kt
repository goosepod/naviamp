package app.naviamp.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.naviamp.ui.NaviampIcons
import app.naviamp.ui.NaviampPageTitle
import app.naviamp.ui.NaviampTransportIcons
import app.naviamp.ui.LocalTrackSwipeSettings
import app.naviamp.ui.SwipeActionContainer
import app.naviamp.ui.downloadedTrackSwipeActionVisual
import app.naviamp.ui.downloadRowActions
import app.naviamp.ui.oneDecimalLabel
import app.naviamp.ui.storageBytesLabel
import app.naviamp.domain.cache.DownloadJob
import app.naviamp.domain.cache.DownloadJobItemStatus
import app.naviamp.domain.cache.DownloadJobStatus

@Composable
fun DesktopDownloadsPanel(
    appColors: DesktopAppColors,
    downloads: List<NaviampDownloadedTrackUi>,
    status: String?,
    downloadJobs: List<DownloadJob>,
    downloadBytes: Long,
    maxDownloadBytes: Long,
    audioCacheCount: Long,
    audioCacheBytes: Long,
    maxAudioCacheBytes: Long,
    onCancelDownloadJob: (String) -> Unit,
    onRetryDownloadJob: (String) -> Unit,
    onRefreshDownloads: () -> Unit,
    keepFavoritesDownloaded: Boolean,
    onToggleKeepFavoritesDownloaded: () -> Unit,
    onDeleteAllDownloads: () -> Unit,
    onDownloadAction: (DownloadedTrackActionRequest) -> Unit,
) {
    var offlineDashboardExpanded by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val swipeSettings = LocalTrackSwipeSettings.current
    val remainingBytes = (maxDownloadBytes - downloadBytes).coerceAtLeast(0L)
    val usedPercent = if (maxDownloadBytes > 0L) {
        ((downloadBytes.toDouble() / maxDownloadBytes.toDouble()) * 100.0)
            .coerceIn(0.0, 100.0)
            .oneDecimalLabel() + "%"
    } else {
        "0.0%"
    }
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
                NaviampPageTitle("Downloads", appColors)
                Text(
                    "${downloads.size} files - ${downloadBytes.storageBytesLabel()} of " +
                        maxDownloadBytes.storageBytesLabel(),
                    color = appColors.secondaryText,
                    fontSize = 12.sp,
                )
                Text(
                    "${remainingBytes.storageBytesLabel()} remaining - $usedPercent used",
                    color = appColors.mutedText,
                    fontSize = 11.sp,
                )
            }
            DesktopRowOverflowMenu(
                appColors = appColors,
                items = listOf(
                    DesktopRowMenuItem("Refresh", NaviampIcons.Refresh, onRefreshDownloads),
                    DesktopRowMenuItem(
                        if (keepFavoritesDownloaded) "Stop keeping favorites downloaded" else "Keep favorites downloaded",
                        NaviampTransportIcons.Heart,
                        onToggleKeepFavoritesDownloaded,
                    ),
                    DesktopRowMenuItem("Delete All", NaviampIcons.Trash, { confirmDeleteAll = true }, downloads.isNotEmpty()),
                ),
            )
        }

        DesktopMediaRow(
            appColors = appColors,
            onClick = { offlineDashboardExpanded = !offlineDashboardExpanded },
            verticalPadding = 7.dp,
        ) {
            Text("Offline dashboard", color = appColors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(
                if (offlineDashboardExpanded) NaviampIcons.ChevronUp else NaviampIcons.ChevronDown,
                contentDescription = if (offlineDashboardExpanded) "Hide offline dashboard" else "Show offline dashboard",
                tint = appColors.secondaryText,
                modifier = Modifier.size(18.dp),
            )
        }
        if (offlineDashboardExpanded) {
            DesktopOfflineDashboardSummary(
                appColors = appColors,
                downloads = downloads,
                downloadBytes = downloadBytes,
                maxDownloadBytes = maxDownloadBytes,
                audioCacheCount = audioCacheCount,
                audioCacheBytes = audioCacheBytes,
                maxAudioCacheBytes = maxAudioCacheBytes,
            )
        }

        if (downloadJobs.isNotEmpty()) {
            Text("DOWNLOAD ACTIVITY", color = appColors.primaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            downloadJobs.forEach { job ->
                DesktopDownloadJobCard(
                    appColors = appColors,
                    job = job,
                    onCancel = { onCancelDownloadJob(job.id) },
                    onRetry = { onRetryDownloadJob(job.id) },
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
                SwipeActionContainer(
                    swipeRight = downloadedTrackSwipeActionVisual(swipeSettings.downloadsRight, download, onDownloadAction),
                    swipeLeft = downloadedTrackSwipeActionVisual(swipeSettings.downloadsLeft, download, onDownloadAction),
                ) { swipeModifier ->
                    DesktopMediaRow(
                        appColors = appColors,
                        modifier = swipeModifier,
                        onClick = { onDownloadAction(DownloadedTrackActionRequest(download, DownloadedTrackAction.Select)) },
                        verticalPadding = 5.dp,
                    ) {
                        DesktopCoverArtThumb(
                            appColors = appColors,
                            coverArtUrl = download.track.coverArtUrl,
                            size = 42.dp,
                            cornerRadius = 4.dp,
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                download.track.title,
                                color = appColors.primaryText,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp,
                            )
                            Text(
                                download.track.subtitle,
                                color = appColors.secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                            )
                            Text(
                                listOf(download.track.meta, download.qualityLabel, download.sizeBytes.storageBytesLabel()).filter { it.isNotBlank() }.joinToString(" · "),
                                color = appColors.mutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 10.sp,
                            )
                        }
                        DesktopRowOverflowMenu(
                            appColors = appColors,
                            items = rowActions.mapNotNull { action ->
                                when (action.action) {
                                    NaviampAction.AddToPlaylist -> DesktopRowMenuItem(action.label, action.icon, {
                                        onDownloadAction(DownloadedTrackActionRequest(download, DownloadedTrackAction.AddToPlaylist))
                                    }, action.enabled)
                                    NaviampAction.RemoveDownload -> DesktopRowMenuItem(action.label, action.icon, {
                                        onDownloadAction(DownloadedTrackActionRequest(download, DownloadedTrackAction.Remove))
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
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all downloads?") },
            text = { Text("This removes every downloaded file shown for the active source. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    onDeleteAllDownloads()
                }) { Text("Delete All") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DesktopDownloadJobCard(
    appColors: DesktopAppColors,
    job: DownloadJob,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val activeItem = job.items.firstOrNull { it.status == DownloadJobItemStatus.Downloading }
    val failedItem = job.items.firstOrNull { it.status == DownloadJobItemStatus.Failed }
    val statusLabel = when (job.status) {
        DownloadJobStatus.Queued -> "Queued"
        DownloadJobStatus.Running -> "${job.completedCount} of ${job.totalCount}"
        DownloadJobStatus.Completed -> "Completed · ${job.totalCount} tracks"
        DownloadJobStatus.Failed -> "Failed · ${job.completedCount} of ${job.totalCount} saved"
        DownloadJobStatus.Cancelled -> "Cancelled · ${job.completedCount} of ${job.totalCount} saved"
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(job.label, color = appColors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(statusLabel, color = appColors.secondaryText, fontSize = 11.sp)
            }
            if (job.canCancel) TextButton(onClick = onCancel) { Text("Cancel") }
            if (job.canRetry) TextButton(onClick = onRetry) { Text("Retry") }
        }
        LinearProgressIndicator(
            progress = { job.progress },
            modifier = Modifier.fillMaxWidth(),
            color = appColors.primaryText,
            trackColor = appColors.mutedText.copy(alpha = 0.25f),
        )
        activeItem?.let { Text("Downloading ${it.track.title}", color = appColors.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        failedItem?.let { Text("${it.track.title}: ${it.failureMessage ?: "Download failed"}", color = appColors.secondaryText, fontSize = 11.sp) }
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
