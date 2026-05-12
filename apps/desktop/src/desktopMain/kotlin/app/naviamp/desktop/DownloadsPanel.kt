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

@Composable
fun DownloadsPanel(
    appColors: AppColors,
    downloads: List<DownloadedTrack>,
    status: String?,
    downloadBytes: Long,
    maxDownloadBytes: Long,
    coverArtUrl: (String?) -> String?,
    onTrackSelected: (Int) -> Unit,
    onRemoveDownload: (DownloadedTrack) -> Unit,
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
                    "${downloads.size} files - ${downloadBytes.downloadsBytesLabel()} of " +
                        maxDownloadBytes.downloadsBytesLabel(),
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
            downloads.forEachIndexed { index, download ->
                MediaRow(
                    appColors = appColors,
                    onClick = { onTrackSelected(index) },
                    verticalPadding = 3.dp,
                ) {
                    CoverArtThumb(
                        appColors = appColors,
                        coverArtUrl = coverArtUrl(download.track.coverArtId),
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
                            download.track.artistName,
                            color = appColors.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp,
                        )
                    }
                    TrackMetadataTrailing(
                        appColors = appColors,
                        track = download.track,
                        showDuration = true,
                    )
                    Text(
                        download.sizeBytes.downloadsBytesLabel(),
                        color = appColors.mutedText,
                        fontSize = 11.sp,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    IconButton(
                        onClick = { onRemoveDownload(download) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = NavigationIcons.Trash,
                            contentDescription = "Remove download",
                            tint = appColors.mutedText,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun Long.downloadsBytesLabel(): String {
    val mib = 1024.0 * 1024.0
    val gib = mib * 1024.0
    return when {
        this >= gib -> "%.1f GB".format(this / gib)
        this >= mib -> "%.0f MB".format(this / mib)
        else -> "$this B"
    }
}
