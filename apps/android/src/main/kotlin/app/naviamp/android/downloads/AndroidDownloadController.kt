package app.naviamp.android

import app.naviamp.domain.cache.StorageCacheStats

import android.content.Context
import app.naviamp.domain.Track
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.DownloadReplacementRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.DownloadService
import app.naviamp.domain.cache.DownloadJob
import app.naviamp.domain.cache.DownloadJobUpdate
import app.naviamp.domain.cache.KeepDownloadedCollectionKind
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.createDownloadJob
import app.naviamp.domain.cache.updated
import app.naviamp.domain.cache.withDownloadJob
import app.naviamp.domain.cache.planKeepDownloadedReconciliation
import app.naviamp.domain.cache.downloadRemoveErrorStatus
import app.naviamp.domain.cache.downloadConnectionRequiredStatus
import app.naviamp.domain.cache.downloadMobileDataDisabledStatus
import app.naviamp.domain.cache.downloadTracksWithRefresh
import app.naviamp.domain.cache.downloadedTrackRemovedStatus
import app.naviamp.domain.cache.redownloadTracksWithRefresh
import app.naviamp.domain.Playlist
import app.naviamp.domain.settings.downloadStreamQuality
import app.naviamp.ui.NaviampDownloadedTrackUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job

fun downloadAndroidTrack(
    context: Context,
    scope: CoroutineScope,
    state: AndroidAppState,
    downloadRepository: DownloadRepository<AndroidDownloadedAudioFile, AndroidDownloadedTrack>,
    downloadReplacementRepository: DownloadReplacementRepository<AndroidDownloadedAudioFile>,
    cacheMaintenanceRepository: CacheMaintenanceRepository<StorageCacheStats>,
    track: Track,
    onJobUpdate: (DownloadJobUpdate) -> Unit = {},
): Job {
    val activeProvider = state.provider
    val sourceId = state.activeSourceId
    val downloadService = DownloadService(downloadRepository, downloadReplacementRepository)
    return scope.launch {
        with(state) {
            val quality = playbackSettings.downloadStreamQuality()
            val result = downloadService.downloadTracksWithRefresh(
                label = track.title,
                tracks = listOf(track),
                sourceId = sourceId,
                provider = activeProvider,
                quality = quality,
                maxDownloadBytes = cacheSettings.maxDownloadBytes,
                isActiveNetworkMobileData = context.isActiveNetworkMobileData(),
                allowMobileDownloads = playbackSettings.allowMobileDownloads,
                includeCompletedCount = false,
                setStatus = { message ->
                    downloadStatus = message
                    status = message
                },
                onJobUpdate = onJobUpdate,
                loadStats = { withContext(Dispatchers.IO) { cacheMaintenanceRepository.stats() } },
            )
            if (result.refreshDownloads) {
                downloadRefreshToken += 1
                result.stats?.let { storageStats = it }
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
    onJobUpdate: (DownloadJobUpdate) -> Unit = {},
): Job {
    val activeProvider = state.provider
    val sourceId = state.activeSourceId
    val downloadService = DownloadService(downloadRepository, downloadReplacementRepository)
    return scope.launch {
        with(state) {
            val quality = playbackSettings.downloadStreamQuality()
            val result = downloadService.downloadTracksWithRefresh(
                label = label,
                tracks = tracksToDownload,
                sourceId = sourceId,
                provider = activeProvider,
                quality = quality,
                maxDownloadBytes = cacheSettings.maxDownloadBytes,
                isActiveNetworkMobileData = context.isActiveNetworkMobileData(),
                allowMobileDownloads = playbackSettings.allowMobileDownloads,
                setStatus = { message ->
                    downloadStatus = message
                    status = message
                },
                onJobUpdate = onJobUpdate,
                loadStats = { withContext(Dispatchers.IO) { cacheMaintenanceRepository.stats() } },
            )
            if (result.refreshDownloads) {
                downloadRefreshToken += 1
                result.stats?.let { storageStats = it }
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
    onJobUpdate: (DownloadJobUpdate) -> Unit = {},
): Job {
    val activeProvider = state.provider
    val sourceId = state.activeSourceId
    val downloadService = DownloadService(downloadRepository, downloadReplacementRepository)
    return scope.launch {
        with(state) {
            val quality = playbackSettings.downloadStreamQuality()
            val result = downloadService.redownloadTracksWithRefresh(
                tracks = tracksToDownload,
                sourceId = sourceId,
                provider = activeProvider,
                quality = quality,
                maxDownloadBytes = cacheSettings.maxDownloadBytes,
                isActiveNetworkMobileData = context.isActiveNetworkMobileData(),
                allowMobileDownloads = playbackSettings.allowMobileDownloads,
                setStatus = { message ->
                    downloadStatus = message
                    status = message
                },
                onJobUpdate = onJobUpdate,
                loadStats = { withContext(Dispatchers.IO) { cacheMaintenanceRepository.stats() } },
            )
            if (result.refreshDownloads) {
                downloadRefreshToken += 1
                result.stats?.let { storageStats = it }
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
    private val activeDownloadJobs = mutableMapOf<String, Job>()
    private val replacementDownloadJobs = mutableSetOf<String>()
    private var nextDownloadJobId = 0L

    fun downloadTrack(track: Track) {
        launchDownloadJob(track.title, listOf(track), replaceExisting = false)
    }

    fun downloadTracks(tracksToDownload: List<Track>, label: String = "tracks") {
        launchDownloadJob(label, tracksToDownload, replaceExisting = false)
    }

    fun redownloadTracks(tracksToDownload: List<Track>, label: String = "downloads") {
        launchDownloadJob(label, tracksToDownload, replaceExisting = true)
    }

    fun cancelDownloadJob(jobId: String) {
        val completedAny = state.downloadJobs.firstOrNull { it.id == jobId }?.completedCount?.let { it > 0 } == true
        activeDownloadJobs.remove(jobId)?.cancel()
        updateDownloadJob(jobId, DownloadJobUpdate.Cancelled)
        if (completedAny) {
            state.downloadRefreshToken += 1
            scope.launch {
                state.storageStats = withContext(Dispatchers.IO) { storage.stats() }
            }
        }
    }

    fun retryDownloadJob(jobId: String) {
        val failedJob = state.downloadJobs.firstOrNull { it.id == jobId && it.canRetry } ?: return
        launchDownloadJob(
            label = failedJob.label,
            tracksToDownload = failedJob.retryTracks,
            replaceExisting = jobId in replacementDownloadJobs,
        )
    }

    private fun launchDownloadJob(label: String, tracksToDownload: List<Track>, replaceExisting: Boolean) {
        val jobId = newDownloadJobId()
        val initialJob = createDownloadJob(jobId, label, tracksToDownload)
        if (state.provider == null || state.activeSourceId == null) {
            state.downloadStatus = downloadConnectionRequiredStatus()
            state.status = state.downloadStatus.orEmpty()
            return
        }
        if (context.isActiveNetworkMobileData() && !state.playbackSettings.allowMobileDownloads) {
            state.downloadStatus = downloadMobileDataDisabledStatus()
            state.status = state.downloadStatus.orEmpty()
            return
        }
        if (initialJob.items.isEmpty()) {
            state.downloadStatus = "No tracks to download."
            state.status = state.downloadStatus.orEmpty()
            return
        }
        state.downloadJobs = state.downloadJobs.withDownloadJob(initialJob)
        if (replaceExisting) replacementDownloadJobs += jobId
        val job = if (replaceExisting) {
            redownloadAndroidTracks(
                context = context,
                scope = scope,
                state = state,
                downloadRepository = storage,
                downloadReplacementRepository = storage,
                cacheMaintenanceRepository = storage,
                tracksToDownload = tracksToDownload,
                label = label,
                onJobUpdate = { updateDownloadJob(jobId, it) },
            )
        } else {
            downloadAndroidTracks(
                context = context,
                scope = scope,
                state = state,
                downloadRepository = storage,
                downloadReplacementRepository = storage,
                cacheMaintenanceRepository = storage,
                tracksToDownload = tracksToDownload,
                label = label,
                onJobUpdate = { updateDownloadJob(jobId, it) },
            )
        }
        activeDownloadJobs[jobId] = job
        job.invokeOnCompletion { activeDownloadJobs.remove(jobId) }
    }

    private fun updateDownloadJob(jobId: String, update: DownloadJobUpdate) {
        val current = state.downloadJobs.firstOrNull { it.id == jobId } ?: return
        state.downloadJobs = state.downloadJobs.withDownloadJob(current.updated(update))
    }

    private fun newDownloadJobId(): String {
        nextDownloadJobId += 1
        return "download-${nextDownloadJobId.toString().padStart(12, '0')}"
    }

    fun downloadPlaylist(playlist: Playlist) {
        downloadAndroidPlaylist(scope, state, playlist, storage, ::downloadTracks)
    }

    fun reloadKeepDownloadedPolicies() {
        state.keepDownloadedPolicies = state.activeSourceId?.let(storage::keepDownloadedPolicies).orEmpty()
    }

    fun toggleKeepDownloadedPlaylist(playlist: Playlist) {
        val sourceId = state.activeSourceId ?: return
        val kind = if (playlist.isSmart) KeepDownloadedCollectionKind.SmartPlaylist else KeepDownloadedCollectionKind.Playlist
        val existing = storage.keepDownloadedPolicy(sourceId, kind, playlist.id)
        if (existing != null) {
            storage.deleteKeepDownloadedPolicy(sourceId, kind, playlist.id)
            reloadKeepDownloadedPolicies()
            state.downloadStatus = "${playlist.name} will no longer be kept downloaded. Existing files were kept."
            state.status = state.downloadStatus.orEmpty()
            return
        }
        val provider = state.provider ?: return
        scope.launch {
            runCatching {
                val tracks = withContext(Dispatchers.IO) { provider.playlistTracks(playlist.id) }
                reconcileKeepDownloadedPolicy(
                    KeepDownloadedCollectionPolicy(sourceId, kind, playlist.id, playlist.name),
                    tracks,
                )
            }.onFailure { error ->
                state.downloadStatus = error.message ?: "Could not keep ${playlist.name} downloaded."
                state.status = state.downloadStatus.orEmpty()
            }
        }
    }

    fun toggleKeepDownloadedFavorites() {
        val sourceId = state.activeSourceId ?: return
        val kind = KeepDownloadedCollectionKind.Favorites
        val existing = storage.keepDownloadedPolicy(sourceId, kind, FavoritesCollectionId)
        if (existing != null) {
            storage.deleteKeepDownloadedPolicy(sourceId, kind, FavoritesCollectionId)
            reloadKeepDownloadedPolicies()
            state.downloadStatus = "Favorites will no longer be kept downloaded. Existing files were kept."
            state.status = state.downloadStatus.orEmpty()
            return
        }
        val provider = state.provider ?: return
        scope.launch {
            runCatching {
                val tracks = withContext(Dispatchers.IO) { provider.favoriteTracks() }
                reconcileKeepDownloadedPolicy(
                    KeepDownloadedCollectionPolicy(sourceId, kind, FavoritesCollectionId, "Favorite tracks"),
                    tracks,
                )
            }.onFailure { error ->
                state.downloadStatus = error.message ?: "Could not keep favorites downloaded."
                state.status = state.downloadStatus.orEmpty()
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
                        when (policy.kind) {
                            KeepDownloadedCollectionKind.Playlist,
                            KeepDownloadedCollectionKind.SmartPlaylist,
                            -> provider.playlistTracks(policy.collectionId)
                            KeepDownloadedCollectionKind.Favorites -> provider.favoriteTracks()
                        }
                    }
                    reconcileKeepDownloadedPolicy(policy, tracks)
                }.onFailure { error ->
                    state.downloadStatus = error.message ?: "Could not refresh ${policy.name}."
                    state.status = state.downloadStatus.orEmpty()
                }
            }
        }
    }

    private fun reconcileKeepDownloadedPolicy(policy: KeepDownloadedCollectionPolicy, tracks: List<Track>) {
        val downloadedIds = storage.downloadedTracks(policy.sourceId).mapTo(mutableSetOf()) { it.track.id.value }
        val otherRequiredIds = storage.keepDownloadedPolicies(policy.sourceId)
            .filterNot { it.kind == policy.kind && it.collectionId == policy.collectionId }
            .flatMapTo(mutableSetOf()) { storage.keepDownloadedTrackIds(it.sourceId, it.kind, it.collectionId) }
        val plan = planKeepDownloadedReconciliation(
            tracks = tracks,
            previousTrackIds = storage.keepDownloadedTrackIds(policy.sourceId, policy.kind, policy.collectionId),
            downloadedTrackIds = downloadedIds,
            managedTrackIds = storage.managedKeepDownloadedTrackIds(policy.sourceId),
            trackIdsRequiredByOtherPolicies = otherRequiredIds,
            removeUnneededFiles = policy.removeUnneededFiles,
        )
        storage.replaceKeepDownloadedTrackIds(policy, plan.nextTrackIds)
        storage.markManagedKeepDownloadedTracks(policy.sourceId, plan.tracksToDownload.mapTo(mutableSetOf()) { it.id.value })
        plan.trackIdsToRemove.forEach { storage.removeDownloadedAudio(policy.sourceId, app.naviamp.domain.TrackId(it)) }
        storage.unmarkManagedKeepDownloadedTracks(policy.sourceId, plan.trackIdsToRemove)
        reloadKeepDownloadedPolicies()
        if (plan.tracksToDownload.isEmpty()) {
            state.downloadStatus = "${policy.name} is up to date."
            state.status = state.downloadStatus.orEmpty()
        } else {
            downloadTracks(plan.tracksToDownload, "Keeping ${policy.name} downloaded")
        }
        if (plan.trackIdsToRemove.isNotEmpty()) state.downloadRefreshToken += 1
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
            state.downloadStatus = if (removed == 0) "Downloads are up to date." else "Removed $removed missing download${if (removed == 1) "" else "s"}."
            state.status = state.downloadStatus.orEmpty()
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
            state.downloadStatus = "Deleted $count download${if (count == 1) "" else "s"}."
            state.status = state.downloadStatus.orEmpty()
        }
    }
}

private const val FavoritesCollectionId = "favorite-tracks"
