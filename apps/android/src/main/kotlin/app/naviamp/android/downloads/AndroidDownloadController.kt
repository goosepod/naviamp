package app.naviamp.android

import android.content.Context
import app.naviamp.domain.Track
import app.naviamp.domain.cache.DownloadService
import app.naviamp.domain.cache.DownloadTracksResult
import app.naviamp.domain.cache.downloadRemoveErrorStatus
import app.naviamp.domain.cache.downloadedTrackRemovedStatus
import app.naviamp.domain.cache.shouldRefreshDownloadsAfter
import app.naviamp.domain.settings.downloadStreamQuality
import app.naviamp.ui.NaviampDownloadedTrackUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun downloadAndroidTrack(
    context: Context,
    scope: CoroutineScope,
    state: AndroidAppState,
    storage: AndroidStorage,
    track: Track,
) {
    val activeProvider = state.provider
    val sourceId = state.activeSourceId
    val downloadService = DownloadService(storage, storage)
    scope.launch {
        with(state) {
            val quality = playbackSettings.downloadStreamQuality()
            val result = downloadService.downloadTracksWithStatus(
                label = track.title,
                tracks = listOf(track),
                sourceId = sourceId,
                provider = activeProvider,
                quality = quality,
                maxDownloadBytes = AndroidMaxDownloadBytes,
                isActiveNetworkMobileData = context.isActiveNetworkMobileData(),
                allowMobileDownloads = playbackSettings.allowMobileDownloads,
                includeCompletedCount = false,
                setStatus = { message ->
                    downloadStatus = message
                    status = message
                },
            )
            if (result is DownloadTracksResult.Completed) {
                downloadRefreshToken += 1
                storageStats = withContext(Dispatchers.IO) { storage.stats() }
            } else if (result is DownloadTracksResult.Failed && result.completed > 0) {
                downloadRefreshToken += 1
                storageStats = withContext(Dispatchers.IO) { storage.stats() }
            }
        }
    }
}

fun downloadAndroidTracks(
    context: Context,
    scope: CoroutineScope,
    state: AndroidAppState,
    storage: AndroidStorage,
    tracksToDownload: List<Track>,
    label: String = "tracks",
) {
    val activeProvider = state.provider
    val sourceId = state.activeSourceId
    val downloadService = DownloadService(storage, storage)
    scope.launch {
        with(state) {
            val quality = playbackSettings.downloadStreamQuality()
            val result = downloadService.downloadTracksWithStatus(
                label = label,
                tracks = tracksToDownload,
                sourceId = sourceId,
                provider = activeProvider,
                quality = quality,
                maxDownloadBytes = AndroidMaxDownloadBytes,
                isActiveNetworkMobileData = context.isActiveNetworkMobileData(),
                allowMobileDownloads = playbackSettings.allowMobileDownloads,
                setStatus = { message ->
                    downloadStatus = message
                    status = message
                },
            )
            if (result is DownloadTracksResult.Completed) {
                downloadRefreshToken += 1
                storageStats = withContext(Dispatchers.IO) { storage.stats() }
            } else if (result is DownloadTracksResult.Failed && result.completed > 0) {
                downloadRefreshToken += 1
                storageStats = withContext(Dispatchers.IO) { storage.stats() }
            }
        }
    }
}

fun redownloadAndroidTracks(
    context: Context,
    scope: CoroutineScope,
    state: AndroidAppState,
    storage: AndroidStorage,
    tracksToDownload: List<Track>,
    label: String = "downloads",
) {
    val activeProvider = state.provider
    val sourceId = state.activeSourceId
    val downloadService = DownloadService(storage, storage)
    scope.launch {
        with(state) {
            val quality = playbackSettings.downloadStreamQuality()
            val result = downloadService.redownloadTracksWithStatus(
                tracks = tracksToDownload,
                sourceId = sourceId,
                provider = activeProvider,
                quality = quality,
                maxDownloadBytes = AndroidMaxDownloadBytes,
                isActiveNetworkMobileData = context.isActiveNetworkMobileData(),
                allowMobileDownloads = playbackSettings.allowMobileDownloads,
                setStatus = { message ->
                    downloadStatus = message
                    status = message
                },
            )
            if (shouldRefreshDownloadsAfter(result)) {
                downloadRefreshToken += 1
                storageStats = withContext(Dispatchers.IO) { storage.stats() }
            }
        }
    }
}

fun removeAndroidDownload(
    scope: CoroutineScope,
    state: AndroidAppState,
    storage: AndroidStorage,
    download: NaviampDownloadedTrackUi,
    findKnownTrack: (String) -> Track?,
) {
    val sourceId = state.activeSourceId ?: return
    scope.launch {
        with(state) {
            val track = downloadedTracks.firstOrNull { it.track.id.value == download.track.id }?.track
                ?: findKnownTrack(download.track.id)
                ?: return@launch
            runCatching {
                withContext(Dispatchers.IO) {
                    storage.removeDownloadedAudioForTrack(sourceId, track.id)
                }
            }.onSuccess {
                downloadRefreshToken += 1
                storageStats = withContext(Dispatchers.IO) { storage.stats() }
                downloadStatus = downloadedTrackRemovedStatus(track.title)
                status = downloadStatus.orEmpty()
            }.onFailure { error ->
                downloadStatus = downloadRemoveErrorStatus(error)
                status = downloadStatus.orEmpty()
            }
        }
    }
}
