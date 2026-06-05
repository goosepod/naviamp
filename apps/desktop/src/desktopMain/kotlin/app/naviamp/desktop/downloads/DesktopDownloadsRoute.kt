package app.naviamp.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.naviamp.ui.toDownloadedTrackUi

@Composable
fun DesktopDownloadsRoute(
    appColors: DesktopAppColors,
    connectedSourceId: String?,
    downloadRefreshToken: Int,
    downloadCount: Long,
    downloadBytes: Long,
    maxDownloadBytes: Long,
    status: String?,
    coverArtUrl: (String?) -> String?,
    downloadedTracks: (sourceId: String) -> List<DownloadedTrack>,
    onPlayDownloadedTrack: (downloads: List<DownloadedTrack>, index: Int) -> Unit,
    onRemoveDownloadedTrack: (DownloadedTrack) -> Unit,
    onAddDownloadedTrackToPlaylist: (DownloadedTrack) -> Unit,
) {
    val downloads = remember(
        connectedSourceId,
        downloadRefreshToken,
        downloadCount,
    ) {
        connectedSourceId
            ?.let(downloadedTracks)
            .orEmpty()
    }
    val downloadedTrackById = remember(downloads) {
        downloads.associateBy { it.path.toString() }
    }
    val downloadItems = remember(downloads, coverArtUrl) {
        downloads.map { download ->
            download.track.toDownloadedTrackUi(
                id = download.path.toString(),
                sizeBytes = download.sizeBytes,
                coverArtUrl = coverArtUrl,
            )
        }
    }
    DesktopDownloadsPanel(
        appColors = appColors,
        downloads = downloadItems,
        status = status,
        downloadBytes = downloadBytes,
        maxDownloadBytes = maxDownloadBytes,
        onTrackSelected = { download ->
            val index = downloadItems.indexOfFirst { it.id == download.id }
            if (index >= 0) onPlayDownloadedTrack(downloads, index)
        },
        onRemoveDownload = { download ->
            downloadedTrackById[download.id]?.let(onRemoveDownloadedTrack)
        },
        onTrackAddToPlaylist = { download ->
            downloadedTrackById[download.id]?.let(onAddDownloadedTrackToPlaylist)
        },
    )
}
