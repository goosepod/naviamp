package app.naviamp.android

import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.app.NaviampDownloadJobController
import app.naviamp.app.NaviampDownloadCoordinator
import app.naviamp.app.NaviampDownloadExecutionRequest
import app.naviamp.app.NaviampKeepDownloadedToggleResult
import app.naviamp.app.naviampKeepDownloadedFavoritesPolicy
import app.naviamp.app.naviampKeepDownloadedPlaylistPolicy
import app.naviamp.app.NaviampApplicationStatusArea
import app.naviamp.app.NaviampApplicationStatusLevel
import app.naviamp.app.downloadsDeletedStatus
import app.naviamp.app.downloadsRefreshStatus
import app.naviamp.app.keepDownloadedDisabledStatus
import app.naviamp.app.keepDownloadedErrorStatus
import app.naviamp.app.keepDownloadedRefreshErrorStatus
import app.naviamp.app.keepDownloadedReconciliationApplication
import app.naviamp.app.noTracksToDownloadStatus
import app.naviamp.app.naviampDownloadPreflightStatus

import android.content.Context
import app.naviamp.domain.Track
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.downloadRemoveErrorStatus
import app.naviamp.domain.cache.downloadedTrackRemovedStatus
import app.naviamp.domain.Playlist
import app.naviamp.domain.settings.downloadStreamQuality
import app.naviamp.ui.NaviampDownloadedTrackUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun AndroidAppState.publishDownloadStatus(message: String) {
    downloadStatus = message
    sharedControllers.status.publish(
        area = NaviampApplicationStatusArea.Downloads,
        level = NaviampApplicationStatusLevel.Information,
        message = message,
    )
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
                publishDownloadStatus(downloadedTrackRemovedStatus(track.title))
            }.onFailure { error ->
                publishDownloadStatus(downloadRemoveErrorStatus(error))
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
    private val downloadJobs: NaviampDownloadJobController,
    private val downloads: NaviampDownloadCoordinator<AndroidDownloadedAudioFile, AndroidDownloadedTrack, StorageCacheStats>,
) {
    fun downloadTrack(track: Track) {
        launchDownloadJob(
            label = track.title,
            tracksToDownload = listOf(track),
            replaceExisting = false,
            includeCompletedCount = false,
        )
    }

    fun downloadTracks(tracksToDownload: List<Track>, label: String = "tracks") {
        launchDownloadJob(label, tracksToDownload, replaceExisting = false)
    }

    fun redownloadTracks(tracksToDownload: List<Track>, label: String = "downloads") {
        launchDownloadJob(label, tracksToDownload, replaceExisting = true)
    }

    fun cancelDownloadJob(jobId: String) {
        val completedAny = downloadJobs.cancel(jobId)
        if (completedAny) {
            state.downloadRefreshToken += 1
            scope.launch {
                state.storageStats = withContext(Dispatchers.IO) { storage.stats() }
            }
        }
    }

    fun retryDownloadJob(jobId: String) {
        val retry = downloadJobs.retry(jobId) ?: return
        launchDownloadJob(
            label = retry.label,
            tracksToDownload = retry.tracks,
            replaceExisting = retry.replaceExisting,
        )
    }

    private fun launchDownloadJob(
        label: String,
        tracksToDownload: List<Track>,
        replaceExisting: Boolean,
        includeCompletedCount: Boolean = true,
    ) {
        val isMobileData = context.isActiveNetworkMobileData()
        val blockedStatus = naviampDownloadPreflightStatus(
            providerAvailable = state.provider != null,
            sourceId = state.activeSourceId,
            isActiveNetworkMobileData = isMobileData,
            allowMobileDownloads = state.playbackSettings.allowMobileDownloads,
        )
        if (blockedStatus != null) {
            state.publishDownloadStatus(blockedStatus)
            return
        }
        val initialJob = downloadJobs.create(label, tracksToDownload, replaceExisting)
        if (initialJob == null) {
            state.publishDownloadStatus(noTracksToDownloadStatus())
            return
        }
        val jobId = initialJob.id
        val job = scope.launch {
            val result = downloads.execute(
                request = NaviampDownloadExecutionRequest(
                    jobId = jobId,
                    label = label,
                    tracks = tracksToDownload,
                    sourceId = state.activeSourceId,
                    provider = state.provider,
                    quality = state.playbackSettings.downloadStreamQuality(),
                    maxDownloadBytes = state.cacheSettings.maxDownloadBytes,
                    replaceExisting = replaceExisting,
                    isActiveNetworkMobileData = isMobileData,
                    allowMobileDownloads = state.playbackSettings.allowMobileDownloads,
                    includeCompletedCount = includeCompletedCount,
                ),
                setStatus = { message ->
                    state.publishDownloadStatus(message)
                },
            )
            if (result.refreshDownloads) {
                state.downloadRefreshToken += 1
                result.stats?.let { stats -> state.storageStats = stats }
            }
        }
        downloadJobs.registerCancellation(jobId, job::cancel)
        job.invokeOnCompletion { downloadJobs.complete(jobId) }
    }

    fun downloadPlaylist(playlist: Playlist) {
        downloadAndroidPlaylist(scope, state, playlist, storage, ::downloadTracks)
    }

    fun reloadKeepDownloadedPolicies() {
        state.keepDownloadedPolicies = state.activeSourceId?.let(storage::keepDownloadedPolicies).orEmpty()
    }

    fun toggleKeepDownloadedPlaylist(playlist: Playlist) {
        val sourceId = state.activeSourceId ?: return
        val policy = naviampKeepDownloadedPlaylistPolicy(sourceId, playlist)
        if (downloads.toggleKeepDownloaded(policy) == NaviampKeepDownloadedToggleResult.Disabled) {
            reloadKeepDownloadedPolicies()
            state.publishDownloadStatus(keepDownloadedDisabledStatus(playlist.name))
            return
        }
        val provider = state.provider ?: return
        scope.launch {
            runCatching {
                val tracks = withContext(Dispatchers.IO) { provider.playlistTracks(playlist.id) }
                reconcileKeepDownloadedPolicy(policy, tracks)
            }.onFailure { error ->
                state.publishDownloadStatus(keepDownloadedErrorStatus(playlist.name, error))
            }
        }
    }

    fun toggleKeepDownloadedFavorites() {
        val sourceId = state.activeSourceId ?: return
        val policy = naviampKeepDownloadedFavoritesPolicy(sourceId)
        if (downloads.toggleKeepDownloaded(policy) == NaviampKeepDownloadedToggleResult.Disabled) {
            reloadKeepDownloadedPolicies()
            state.publishDownloadStatus(keepDownloadedDisabledStatus(FAVORITES_DISPLAY_NAME))
            return
        }
        val provider = state.provider ?: return
        scope.launch {
            runCatching {
                val tracks = withContext(Dispatchers.IO) { provider.favoriteTracks() }
                reconcileKeepDownloadedPolicy(policy, tracks)
            }.onFailure { error ->
                state.publishDownloadStatus(keepDownloadedErrorStatus("favorites", error))
            }
        }
    }

    fun reconcileKeepDownloadedCollections() {
        val provider = state.provider ?: return
        reloadKeepDownloadedPolicies()
        state.keepDownloadedPolicies.forEach { policy ->
            scope.launch {
                runCatching {
                    val tracks = withContext(Dispatchers.IO) {
                        downloads.loadKeepDownloadedTracks(
                            policy = policy,
                            loadPlaylistTracks = provider::playlistTracks,
                            loadFavoriteTracks = provider::favoriteTracks,
                        )
                    }
                    reconcileKeepDownloadedPolicy(policy, tracks)
                }.onFailure { error ->
                    state.publishDownloadStatus(keepDownloadedRefreshErrorStatus(policy.name, error))
                }
            }
        }
    }

    private fun reconcileKeepDownloadedPolicy(policy: KeepDownloadedCollectionPolicy, tracks: List<Track>) {
        val plan = downloads.reconcile(policy, tracks)
        val application = keepDownloadedReconciliationApplication(policy, plan)
        reloadKeepDownloadedPolicies()
        application.status?.let(state::publishDownloadStatus)
        application.downloadLabel?.let { label -> downloadTracks(application.tracksToDownload, label) }
        if (application.refreshDownloads) state.downloadRefreshToken += 1
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

    fun refreshDownloads() {
        val sourceId = state.activeSourceId ?: return
        scope.launch {
            val removed = withContext(Dispatchers.IO) {
                storage.downloadedTracks(sourceId)
                    .filterNot { download -> download.file.isFile }
                    .onEach { download -> storage.removeDownloadedAudio(sourceId, download.track.id) }
                    .size
            }
            state.downloadRefreshToken += 1
            state.storageStats = withContext(Dispatchers.IO) { storage.stats() }
            state.publishDownloadStatus(downloadsRefreshStatus(removed))
            reconcileKeepDownloadedCollections()
        }
    }

    fun deleteAllDownloads() {
        val sourceId = state.activeSourceId ?: return
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                storage.downloadedTracks(sourceId).also { downloads ->
                    downloads.forEach { download -> storage.removeDownloadedAudio(sourceId, download.track.id) }
                }.size
            }
            state.downloadRefreshToken += 1
            state.storageStats = withContext(Dispatchers.IO) { storage.stats() }
            state.publishDownloadStatus(downloadsDeletedStatus(count))
        }
    }
}

private const val FAVORITES_DISPLAY_NAME = "Favorites"
