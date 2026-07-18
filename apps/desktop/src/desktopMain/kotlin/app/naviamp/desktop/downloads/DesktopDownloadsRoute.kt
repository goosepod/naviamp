package app.naviamp.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.naviamp.ui.DownloadedTrackAction
import app.naviamp.ui.totalDownloadBytes
import app.naviamp.ui.NaviampDownloadsScreenUi
import app.naviamp.ui.NaviampOfflineDashboardUi
import app.naviamp.ui.toDownloadedTrackUi
import app.naviamp.ui.toDownloadJobUi
import app.naviamp.domain.cache.DownloadJob
import app.naviamp.domain.cache.downloadedAudioQualityLabel

data class DesktopDownloadsSourceState(
    val connectedSourceId: String? = null,
    val refreshToken: Int = 0,
    val downloadCount: Long = 0L,
    val maxDownloadBytes: Long = 0L,
    val offlineDashboard: NaviampOfflineDashboardUi = NaviampOfflineDashboardUi(),
    val status: String? = null,
    val jobs: List<DownloadJob> = emptyList(),
    val keepFavoritesDownloaded: Boolean = false,
)

@Composable
fun DesktopDownloadsRoute(
    appColors: DesktopAppColors,
    source: DesktopDownloadsSourceState,
    coverArtUrl: (String?) -> String?,
    downloadedTracks: (sourceId: String) -> List<DownloadedTrack>,
    onPlayDownloadedTrack: (downloads: List<DownloadedTrack>, index: Int) -> Unit,
    onRemoveDownloadedTrack: (DownloadedTrack) -> Unit,
    onCancelDownloadJob: (String) -> Unit,
    onRetryDownloadJob: (String) -> Unit,
    onRefreshDownloads: () -> Unit,
    onToggleKeepFavoritesDownloaded: () -> Unit,
    onDeleteAllDownloads: () -> Unit,
    onAddDownloadedTrackToPlaylist: (DownloadedTrack) -> Unit,
) {
    val downloads = remember(
        source.connectedSourceId,
        source.refreshToken,
        source.downloadCount,
    ) {
        source.connectedSourceId
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
        screen = NaviampDownloadsScreenUi(
            downloads = downloadItems,
            status = source.status,
            jobs = source.jobs.map { it.toDownloadJobUi() },
            downloadBytes = downloadItems.totalDownloadBytes(),
            maxDownloadBytes = source.maxDownloadBytes,
            offlineDashboard = source.offlineDashboard,
            keepFavoritesDownloaded = source.keepFavoritesDownloaded,
        ),
        onCancelDownloadJob = onCancelDownloadJob,
        onRetryDownloadJob = onRetryDownloadJob,
        onRefreshDownloads = onRefreshDownloads,
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
