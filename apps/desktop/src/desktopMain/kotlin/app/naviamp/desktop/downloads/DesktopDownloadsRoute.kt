package app.naviamp.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.naviamp.ui.DownloadedTrackAction
import app.naviamp.ui.NaviampDownloadsScreenUi

@Composable
fun DesktopDownloadsRoute(
    appColors: DesktopAppColors,
    screen: NaviampDownloadsScreenUi,
    downloads: List<DownloadedTrack>,
    onPlayDownloadedTrack: (downloads: List<DownloadedTrack>, index: Int) -> Unit,
    onRemoveDownloadedTrack: (DownloadedTrack) -> Unit,
    onCancelDownloadJob: (String) -> Unit,
    onRetryDownloadJob: (String) -> Unit,
    onRefreshDownloads: () -> Unit,
    onToggleKeepFavoritesDownloaded: () -> Unit,
    onDeleteAllDownloads: () -> Unit,
    onAddDownloadedTrackToPlaylist: (DownloadedTrack) -> Unit,
) {
    val downloadedTrackById = remember(downloads) {
        downloads.associateBy { it.path.toString() }
    }
    DesktopDownloadsPanel(
        appColors = appColors,
        screen = screen,
        onCancelDownloadJob = onCancelDownloadJob,
        onRetryDownloadJob = onRetryDownloadJob,
        onRefreshDownloads = onRefreshDownloads,
        onToggleKeepFavoritesDownloaded = onToggleKeepFavoritesDownloaded,
        onDeleteAllDownloads = onDeleteAllDownloads,
        onDownloadAction = { request ->
            when (request.action) {
                DownloadedTrackAction.Select -> {
                    val index = screen.downloads.indexOfFirst { it.id == request.download.id }
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
