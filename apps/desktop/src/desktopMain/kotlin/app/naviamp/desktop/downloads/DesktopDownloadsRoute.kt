package app.naviamp.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.naviamp.ui.DownloadedTrackAction
import app.naviamp.ui.totalDownloadBytes
import app.naviamp.ui.toDownloadedTrackUi
import app.naviamp.domain.cache.DownloadJob
import app.naviamp.domain.cache.downloadedAudioQualityLabel

@Composable
fun DesktopDownloadsRoute(
    appColors: DesktopAppColors,
    connectedSourceId: String?,
    downloadRefreshToken: Int,
    downloadCount: Long,
    maxDownloadBytes: Long,
    audioCacheCount: Long,
    audioCacheBytes: Long,
    maxAudioCacheBytes: Long,
    status: String?,
    downloadJobs: List<DownloadJob>,
    coverArtUrl: (String?) -> String?,
    downloadedTracks: (sourceId: String) -> List<DownloadedTrack>,
    onPlayDownloadedTrack: (downloads: List<DownloadedTrack>, index: Int) -> Unit,
    onRemoveDownloadedTrack: (DownloadedTrack) -> Unit,
    onCancelDownloadJob: (String) -> Unit,
    onRetryDownloadJob: (String) -> Unit,
    onRefreshDownloads: () -> Unit,
    keepFavoritesDownloaded: Boolean,
    onToggleKeepFavoritesDownloaded: () -> Unit,
    onDeleteAllDownloads: () -> Unit,
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
                qualityLabel = downloadedAudioQualityLabel(download.qualityKey, download.track.audioInfo, download.contentType),
                coverArtUrl = coverArtUrl,
            )
        }
    }
    DesktopDownloadsPanel(
        appColors = appColors,
        downloads = downloadItems,
        status = status,
        downloadJobs = downloadJobs,
        downloadBytes = downloadItems.totalDownloadBytes(),
        maxDownloadBytes = maxDownloadBytes,
        audioCacheCount = audioCacheCount,
        audioCacheBytes = audioCacheBytes,
        maxAudioCacheBytes = maxAudioCacheBytes,
        onCancelDownloadJob = onCancelDownloadJob,
        onRetryDownloadJob = onRetryDownloadJob,
        onRefreshDownloads = onRefreshDownloads,
        keepFavoritesDownloaded = keepFavoritesDownloaded,
        onToggleKeepFavoritesDownloaded = onToggleKeepFavoritesDownloaded,
        onDeleteAllDownloads = onDeleteAllDownloads,
        onDownloadAction = { request ->
            when (request.action) {
                DownloadedTrackAction.Select -> {
                    val index = downloadItems.indexOfFirst { it.id == request.download.id }
                    if (index >= 0) onPlayDownloadedTrack(downloads, index)
                }
                DownloadedTrackAction.AddToPlaylist ->
                    downloadedTrackById[request.download.id]?.let(onAddDownloadedTrackToPlaylist)
                DownloadedTrackAction.Remove ->
                    downloadedTrackById[request.download.id]?.let(onRemoveDownloadedTrack)
                DownloadedTrackAction.CreatePlaylistAndAdd -> Unit
            }
        },
    )
}
