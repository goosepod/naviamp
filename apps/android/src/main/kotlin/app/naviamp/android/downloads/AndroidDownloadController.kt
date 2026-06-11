package app.naviamp.android

import app.naviamp.domain.cache.StorageCacheStats

import android.content.Context
import app.naviamp.domain.Track
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.DownloadReplacementRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.DownloadService
import app.naviamp.domain.cache.downloadRemoveErrorStatus
import app.naviamp.domain.cache.downloadedTrackRemovedStatus
import app.naviamp.domain.cache.shouldRefreshDownloadsAfter
import app.naviamp.domain.Playlist
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
    downloadRepository: DownloadRepository<AndroidDownloadedAudioFile, AndroidDownloadedTrack>,
    downloadReplacementRepository: DownloadReplacementRepository<AndroidDownloadedAudioFile>,
    cacheMaintenanceRepository: CacheMaintenanceRepository<StorageCacheStats>,
    track: Track,
) {
    val activeProvider = state.provider
    val sourceId = state.activeSourceId
    val downloadService = DownloadService(downloadRepository, downloadReplacementRepository)
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
            if (shouldRefreshDownloadsAfter(result)) {
                downloadRefreshToken += 1
                storageStats = withContext(Dispatchers.IO) { cacheMaintenanceRepository.stats() }
            }
        }
    }
}

fun downloadAndroidTracks(
    context: Context,
    scope: CoroutineScope,
    state: AndroidAppState,
    downloadRepository: DownloadRepository<AndroidDownloadedAudioFile, AndroidDownloadedTrack>,
    downloadReplacementRepository: DownloadReplacementRepository<AndroidDownloadedAudioFile>,
    cacheMaintenanceRepository: CacheMaintenanceRepository<StorageCacheStats>,
    tracksToDownload: List<Track>,
    label: String = "tracks",
) {
    val activeProvider = state.provider
    val sourceId = state.activeSourceId
    val downloadService = DownloadService(downloadRepository, downloadReplacementRepository)
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
            if (shouldRefreshDownloadsAfter(result)) {
                downloadRefreshToken += 1
                storageStats = withContext(Dispatchers.IO) { cacheMaintenanceRepository.stats() }
            }
        }
    }
}

fun redownloadAndroidTracks(
    context: Context,
    scope: CoroutineScope,
    state: AndroidAppState,
    downloadRepository: DownloadRepository<AndroidDownloadedAudioFile, AndroidDownloadedTrack>,
    downloadReplacementRepository: DownloadReplacementRepository<AndroidDownloadedAudioFile>,
    cacheMaintenanceRepository: CacheMaintenanceRepository<StorageCacheStats>,
    tracksToDownload: List<Track>,
    label: String = "downloads",
) {
    val activeProvider = state.provider
    val sourceId = state.activeSourceId
    val downloadService = DownloadService(downloadRepository, downloadReplacementRepository)
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
                storageStats = withContext(Dispatchers.IO) { cacheMaintenanceRepository.stats() }
            }
        }
    }
}

fun removeAndroidDownload(
    scope: CoroutineScope,
    state: AndroidAppState,
    downloadRepository: DownloadRepository<AndroidDownloadedAudioFile, AndroidDownloadedTrack>,
    cacheMaintenanceRepository: CacheMaintenanceRepository<StorageCacheStats>,
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
                    downloadRepository.removeDownloadedAudio(sourceId, track.id)
                }
            }.onSuccess {
                downloadRefreshToken += 1
                storageStats = withContext(Dispatchers.IO) { cacheMaintenanceRepository.stats() }
                downloadStatus = downloadedTrackRemovedStatus(track.title)
                status = downloadStatus.orEmpty()
            }.onFailure { error ->
                downloadStatus = downloadRemoveErrorStatus(error)
                status = downloadStatus.orEmpty()
            }
        }
    }
}

internal class AndroidDownloadActionController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    private val storage: AndroidStorageDependencies,
    private val findKnownTrack: (String) -> Track?,
) {
    fun downloadTrack(track: Track) {
        downloadAndroidTrack(
            context = context,
            scope = scope,
            state = state,
            downloadRepository = storage,
            downloadReplacementRepository = storage,
            cacheMaintenanceRepository = storage,
            track = track,
        )
    }

    fun downloadTracks(tracksToDownload: List<Track>, label: String = "tracks") {
        downloadAndroidTracks(
            context = context,
            scope = scope,
            state = state,
            downloadRepository = storage,
            downloadReplacementRepository = storage,
            cacheMaintenanceRepository = storage,
            tracksToDownload = tracksToDownload,
            label = label,
        )
    }

    fun redownloadTracks(tracksToDownload: List<Track>, label: String = "downloads") {
        redownloadAndroidTracks(
            context = context,
            scope = scope,
            state = state,
            downloadRepository = storage,
            downloadReplacementRepository = storage,
            cacheMaintenanceRepository = storage,
            tracksToDownload = tracksToDownload,
            label = label,
        )
    }

    fun downloadPlaylist(playlist: Playlist) {
        downloadAndroidPlaylist(scope, state, playlist, storage, ::downloadTracks)
    }

    fun removeDownload(download: NaviampDownloadedTrackUi) {
        removeAndroidDownload(
            scope = scope,
            state = state,
            downloadRepository = storage,
            cacheMaintenanceRepository = storage,
            download = download,
            findKnownTrack = findKnownTrack,
        )
    }
}
