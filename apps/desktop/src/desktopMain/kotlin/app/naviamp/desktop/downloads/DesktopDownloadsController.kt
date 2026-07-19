package app.naviamp.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.naviamp.domain.Album
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.app.NaviampDownloadJobController
import app.naviamp.app.NaviampDownloadCoordinator
import app.naviamp.app.NaviampDownloadExecutionRequest
import app.naviamp.app.NaviampKeepDownloadedToggleResult
import app.naviamp.app.naviampKeepDownloadedFavoritesPolicy
import app.naviamp.app.naviampKeepDownloadedPlaylistPolicy
import app.naviamp.app.NaviampApplicationStatusArea
import app.naviamp.app.NaviampApplicationStatusController
import app.naviamp.app.NaviampApplicationStatusLevel
import app.naviamp.app.downloadsDeletedStatus
import app.naviamp.app.downloadsRefreshStatus
import app.naviamp.app.keepDownloadedDisabledStatus
import app.naviamp.app.keepDownloadedErrorStatus
import app.naviamp.app.keepDownloadedRefreshErrorStatus
import app.naviamp.app.keepDownloadedUpToDateStatus
import app.naviamp.app.keepingDownloadedLabel
import app.naviamp.app.noTracksToDownloadStatus
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.DownloadJob
import app.naviamp.domain.cache.DownloadTracksResult
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.KeepDownloadedRepository
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.cache.downloadConnectionRequiredStatus
import app.naviamp.domain.cache.downloadedTrackRemovedStatus
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.downloadStreamQuality
import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.desktop.playback.DesktopPlaylistEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DesktopDownloadsController(
    private val scope: CoroutineScope,
    private val downloadRepository: DownloadRepository<DownloadedAudioFile, DownloadedTrack>,
    private val keepDownloadedRepository: KeepDownloadedRepository,
    private val cacheMaintenanceRepository: CacheMaintenanceRepository<StorageCacheStats>,
    private val jobController: NaviampDownloadJobController,
    private val downloads: NaviampDownloadCoordinator<DownloadedAudioFile, DownloadedTrack, StorageCacheStats>,
    private val applicationStatus: NaviampApplicationStatusController,
    providerResponseCacheRepository: ProviderResponseCacheRepository,
    private val playbackEngine: PlaybackEngine,
    private val playbackSettings: () -> PlaybackSettings,
    private val cacheSettings: () -> CacheSettings,
    private val provider: () -> MediaProvider?,
    private val sourceId: () -> String?,
    private val stopRadioContinuation: () -> Unit,
    private val clearShuffleSnapshot: () -> Unit,
    private val setOpenPlayerOnTrackStart: (Boolean) -> Unit,
    private val playlistEngine: DesktopPlaylistEngine,
    private val playlistCallbacks: () -> PlaylistCallbacks,
    private val setCacheStats: (StorageCacheStats) -> Unit = {},
) {
    var status by mutableStateOf<String?>(null)
        private set
    var refreshToken by mutableIntStateOf(0)
        private set
    val downloadJobs: List<DownloadJob> get() = jobController.currentJobs
    var keepDownloadedPolicies by mutableStateOf<List<KeepDownloadedCollectionPolicy>>(emptyList())
        private set

    private val providerResponseService = ProviderResponseService(providerResponseCacheRepository)
    private fun incrementRefreshToken() {
        refreshToken += 1
    }

    private fun updateStatus(message: String) {
        status = message
        applicationStatus.publish(
            area = NaviampApplicationStatusArea.Downloads,
            level = NaviampApplicationStatusLevel.Information,
            message = message,
        )
    }

    fun downloadTracks(label: String, tracks: List<Track>) {
        launchDownloadJob(label, tracks, replaceExisting = false)
    }

    private fun launchDownloadJob(label: String, tracks: List<Track>, replaceExisting: Boolean) {
        val activeProvider = provider()
        val activeSourceId = sourceId()
        if (activeProvider == null || activeSourceId == null) {
            updateStatus(downloadConnectionRequiredStatus())
            return
        }
        val initialJob = jobController.create(label, tracks, replaceExisting) ?: run {
            updateStatus(noTracksToDownloadStatus())
            return
        }
        val jobId = initialJob.id
        val job = scope.launch {
            val quality = playbackSettings().downloadStreamQuality()
            val maxDownloadBytes = cacheSettings().maxDownloadBytes
            try {
                val result = downloads.execute(
                    request = NaviampDownloadExecutionRequest(
                        jobId = jobId,
                        label = label,
                        tracks = tracks,
                        sourceId = activeSourceId,
                        provider = activeProvider,
                        quality = quality,
                        maxDownloadBytes = maxDownloadBytes,
                        replaceExisting = replaceExisting,
                        refreshDownloadsAfter = { result -> result !is DownloadTracksResult.Blocked },
                    ),
                    setStatus = ::updateStatus,
                )
                if (result.refreshDownloads) {
                    incrementRefreshToken()
                    result.stats?.let(setCacheStats)
                }
            } finally {
                jobController.complete(jobId)
            }
        }
        jobController.registerCancellation(jobId, job::cancel)
    }

    fun downloadTrack(track: Track) {
        downloadTracks(track.title, listOf(track))
    }

    fun redownloadTracks(tracks: List<Track>, label: String = "downloads") {
        launchDownloadJob(label, tracks, replaceExisting = true)
    }

    fun cancelDownloadJob(jobId: String) {
        val completedAny = jobController.cancel(jobId)
        if (completedAny) {
            incrementRefreshToken()
            scope.launch {
                setCacheStats(withContext(Dispatchers.IO) { cacheMaintenanceRepository.stats() })
            }
        }
    }

    fun retryDownloadJob(jobId: String) {
        val retry = jobController.retry(jobId) ?: return
        launchDownloadJob(
            label = retry.label,
            tracks = retry.tracks,
            replaceExisting = retry.replaceExisting,
        )
    }

    fun downloadAlbum(album: Album) {
        val activeProvider = provider() ?: run {
            updateStatus(downloadConnectionRequiredStatus())
            return
        }
        updateStatus("Loading ${album.title}...")
        scope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) {
                    providerResponseService.album(activeProvider, album.id).tracks
                }
                downloadTracks(album.title, tracks)
            } catch (exception: Exception) {
                updateStatus(exception.message ?: "Could not load ${album.title}.")
            }
        }
    }

    fun downloadPlaylist(playlist: Playlist) {
        val activeProvider = provider() ?: run {
            updateStatus(downloadConnectionRequiredStatus())
            return
        }
        updateStatus("Loading ${playlist.name}...")
        scope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) {
                    providerResponseService.playlistTracks(activeProvider, playlist.id)
                }
                downloadTracks(playlist.name, tracks)
            } catch (exception: Exception) {
                updateStatus(exception.message ?: "Could not load ${playlist.name}.")
            }
        }
    }

    fun reloadKeepDownloadedPolicies() {
        keepDownloadedPolicies = sourceId()?.let(keepDownloadedRepository::keepDownloadedPolicies).orEmpty()
    }

    fun toggleKeepDownloadedPlaylist(playlist: Playlist) {
        val activeSourceId = sourceId() ?: return
        val policy = naviampKeepDownloadedPlaylistPolicy(activeSourceId, playlist)
        if (downloads.toggleKeepDownloaded(policy) == NaviampKeepDownloadedToggleResult.Disabled) {
            reloadKeepDownloadedPolicies()
            updateStatus(keepDownloadedDisabledStatus(playlist.name))
            return
        }
        val activeProvider = provider() ?: run {
            updateStatus(downloadConnectionRequiredStatus())
            return
        }
        scope.launch {
            runCatching {
                val tracks = withContext(Dispatchers.IO) { providerResponseService.playlistTracks(activeProvider, playlist.id) }
                reconcileKeepDownloadedPolicy(policy, tracks)
            }.onFailure { error -> updateStatus(keepDownloadedErrorStatus(playlist.name, error)) }
        }
    }

    fun toggleKeepDownloadedFavorites() {
        val activeSourceId = sourceId() ?: return
        val policy = naviampKeepDownloadedFavoritesPolicy(activeSourceId)
        if (downloads.toggleKeepDownloaded(policy) == NaviampKeepDownloadedToggleResult.Disabled) {
            reloadKeepDownloadedPolicies()
            updateStatus(keepDownloadedDisabledStatus("Favorites"))
            return
        }
        val activeProvider = provider() ?: run {
            updateStatus(downloadConnectionRequiredStatus())
            return
        }
        scope.launch {
            runCatching {
                val tracks = withContext(Dispatchers.IO) { activeProvider.favoriteTracks() }
                reconcileKeepDownloadedPolicy(policy, tracks)
            }.onFailure { error -> updateStatus(keepDownloadedErrorStatus("favorites", error)) }
        }
    }

    fun reconcileKeepDownloadedCollections() {
        val activeProvider = provider() ?: return
        reloadKeepDownloadedPolicies()
        keepDownloadedPolicies.forEach { policy ->
            scope.launch {
                runCatching {
                    val tracks = withContext(Dispatchers.IO) {
                        downloads.loadKeepDownloadedTracks(
                            policy = policy,
                            loadPlaylistTracks = { playlistId ->
                                providerResponseService.playlistTracks(activeProvider, playlistId)
                            },
                            loadFavoriteTracks = activeProvider::favoriteTracks,
                        )
                    }
                    reconcileKeepDownloadedPolicy(policy, tracks)
                }.onFailure { error -> updateStatus(keepDownloadedRefreshErrorStatus(policy.name, error)) }
            }
        }
    }

    private fun reconcileKeepDownloadedPolicy(policy: KeepDownloadedCollectionPolicy, tracks: List<Track>) {
        val plan = downloads.reconcile(policy, tracks)
        reloadKeepDownloadedPolicies()
        if (plan.tracksToDownload.isEmpty()) {
            updateStatus(keepDownloadedUpToDateStatus(policy.name))
        } else {
            downloadTracks(keepingDownloadedLabel(policy.name), plan.tracksToDownload)
        }
        if (plan.trackIdsToRemove.isNotEmpty()) incrementRefreshToken()
    }

    fun removeDownloadedTrack(download: DownloadedTrack) {
        val activeSourceId = sourceId() ?: return
        downloadRepository.removeDownloadedAudio(activeSourceId, download.track.id)
        incrementRefreshToken()
        updateStatus(downloadedTrackRemovedStatus(download.track.title))
    }

    fun refreshDownloads() {
        val activeSourceId = sourceId() ?: return
        scope.launch {
            val removed = withContext(Dispatchers.IO) {
                downloadRepository.downloadedTracks(activeSourceId)
                    .filterNot { download -> download.path.toFile().isFile }
                    .onEach { download ->
                        downloadRepository.removeDownloadedAudio(activeSourceId, download.track.id)
                    }
                    .size
            }
            incrementRefreshToken()
            setCacheStats(withContext(Dispatchers.IO) { cacheMaintenanceRepository.stats() })
            updateStatus(downloadsRefreshStatus(removed))
            reconcileKeepDownloadedCollections()
        }
    }

    fun deleteAllDownloads() {
        val activeSourceId = sourceId() ?: return
        scope.launch {
            val downloads = withContext(Dispatchers.IO) {
                downloadRepository.downloadedTracks(activeSourceId).also { saved ->
                    saved.forEach { download ->
                        downloadRepository.removeDownloadedAudio(activeSourceId, download.track.id)
                    }
                }
            }
            incrementRefreshToken()
            setCacheStats(withContext(Dispatchers.IO) { cacheMaintenanceRepository.stats() })
            updateStatus(downloadsDeletedStatus(downloads.size))
        }
    }

    fun playDownloadedTrack(downloads: List<DownloadedTrack>, index: Int) {
        val activeProvider = provider() ?: return
        val tracks = desktopDownloadTracksForPlayback(downloads, index) ?: return
        stopRadioContinuation()
        clearShuffleSnapshot()
        setOpenPlayerOnTrackStart(true)
        playlistEngine.playFrom(
            scope = scope,
            provider = activeProvider,
            tracks = tracks,
            index = index,
            quality = playbackSettings().streamQuality(playbackEngine),
            replayGainMode = playbackSettings().replayGainMode,
            replayGainPreampDb = playbackSettings().replayGainPreampDb,
            callbacks = playlistCallbacks(),
        )
    }
}
