package app.naviamp.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.naviamp.domain.Album
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.DownloadReplacementRepository
import app.naviamp.app.NaviampDownloadJobController
import app.naviamp.app.NaviampDownloadCoordinator
import app.naviamp.app.NaviampDownloadExecutionRequest
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.DownloadJob
import app.naviamp.domain.cache.DownloadTracksResult
import app.naviamp.domain.cache.KeepDownloadedCollectionKind
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
    private val downloadReplacementRepository: DownloadReplacementRepository<DownloadedAudioFile>,
    private val keepDownloadedRepository: KeepDownloadedRepository,
    private val cacheMaintenanceRepository: CacheMaintenanceRepository<StorageCacheStats>,
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
    var downloadJobs by mutableStateOf<List<DownloadJob>>(emptyList())
        private set
    var keepDownloadedPolicies by mutableStateOf<List<KeepDownloadedCollectionPolicy>>(emptyList())
        private set

    private val providerResponseService = ProviderResponseService(providerResponseCacheRepository)
    private val jobController = NaviampDownloadJobController(
        jobs = { downloadJobs },
        setJobs = { jobs -> downloadJobs = jobs },
    )
    private val downloads = NaviampDownloadCoordinator(
        downloadRepository = downloadRepository,
        downloadReplacementRepository = downloadReplacementRepository,
        keepDownloadedRepository = keepDownloadedRepository,
        jobs = jobController,
        downloadedTrackId = { download: DownloadedTrack -> download.track.id.value },
        loadStats = { withContext(Dispatchers.IO) { cacheMaintenanceRepository.stats() } },
    )

    private fun incrementRefreshToken() {
        refreshToken += 1
    }

    fun downloadTracks(label: String, tracks: List<Track>) {
        launchDownloadJob(label, tracks, replaceExisting = false)
    }

    private fun launchDownloadJob(label: String, tracks: List<Track>, replaceExisting: Boolean) {
        val activeProvider = provider()
        val activeSourceId = sourceId()
        if (activeProvider == null || activeSourceId == null) {
            status = downloadConnectionRequiredStatus()
            return
        }
        val initialJob = jobController.create(label, tracks, replaceExisting) ?: run {
            status = "No tracks to download."
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
                    setStatus = { downloadStatus -> status = downloadStatus },
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
            status = downloadConnectionRequiredStatus()
            return
        }
        status = "Loading ${album.title}..."
        scope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) {
                    providerResponseService.album(activeProvider, album.id).tracks
                }
                downloadTracks(album.title, tracks)
            } catch (exception: Exception) {
                status = exception.message ?: "Could not load ${album.title}."
            }
        }
    }

    fun downloadPlaylist(playlist: Playlist) {
        val activeProvider = provider() ?: run {
            status = downloadConnectionRequiredStatus()
            return
        }
        status = "Loading ${playlist.name}..."
        scope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) {
                    providerResponseService.playlistTracks(activeProvider, playlist.id)
                }
                downloadTracks(playlist.name, tracks)
            } catch (exception: Exception) {
                status = exception.message ?: "Could not load ${playlist.name}."
            }
        }
    }

    fun reloadKeepDownloadedPolicies() {
        keepDownloadedPolicies = sourceId()?.let(keepDownloadedRepository::keepDownloadedPolicies).orEmpty()
    }

    fun toggleKeepDownloadedPlaylist(playlist: Playlist) {
        val activeSourceId = sourceId() ?: return
        val kind = if (playlist.isSmart) KeepDownloadedCollectionKind.SmartPlaylist else KeepDownloadedCollectionKind.Playlist
        val existing = keepDownloadedRepository.keepDownloadedPolicy(activeSourceId, kind, playlist.id)
        if (existing != null) {
            keepDownloadedRepository.deleteKeepDownloadedPolicy(activeSourceId, kind, playlist.id)
            reloadKeepDownloadedPolicies()
            status = "${playlist.name} will no longer be kept downloaded. Existing files were kept."
            return
        }
        val activeProvider = provider() ?: run {
            status = downloadConnectionRequiredStatus()
            return
        }
        scope.launch {
            runCatching {
                val tracks = withContext(Dispatchers.IO) { providerResponseService.playlistTracks(activeProvider, playlist.id) }
                reconcileKeepDownloadedPolicy(
                    KeepDownloadedCollectionPolicy(activeSourceId, kind, playlist.id, playlist.name),
                    tracks,
                )
            }.onFailure { error -> status = error.message ?: "Could not keep ${playlist.name} downloaded." }
        }
    }

    fun toggleKeepDownloadedFavorites() {
        val activeSourceId = sourceId() ?: return
        val kind = KeepDownloadedCollectionKind.Favorites
        val existing = keepDownloadedRepository.keepDownloadedPolicy(activeSourceId, kind, FavoritesCollectionId)
        if (existing != null) {
            keepDownloadedRepository.deleteKeepDownloadedPolicy(activeSourceId, kind, FavoritesCollectionId)
            reloadKeepDownloadedPolicies()
            status = "Favorites will no longer be kept downloaded. Existing files were kept."
            return
        }
        val activeProvider = provider() ?: run {
            status = downloadConnectionRequiredStatus()
            return
        }
        scope.launch {
            runCatching {
                val tracks = withContext(Dispatchers.IO) { activeProvider.favoriteTracks() }
                reconcileKeepDownloadedPolicy(
                    KeepDownloadedCollectionPolicy(activeSourceId, kind, FavoritesCollectionId, "Favorite tracks"),
                    tracks,
                )
            }.onFailure { error -> status = error.message ?: "Could not keep favorites downloaded." }
        }
    }

    fun reconcileKeepDownloadedCollections() {
        val activeProvider = provider() ?: return
        reloadKeepDownloadedPolicies()
        keepDownloadedPolicies.forEach { policy ->
            scope.launch {
                runCatching {
                    val tracks = withContext(Dispatchers.IO) {
                        when (policy.kind) {
                            KeepDownloadedCollectionKind.Playlist,
                            KeepDownloadedCollectionKind.SmartPlaylist,
                            -> providerResponseService.playlistTracks(activeProvider, policy.collectionId)
                            KeepDownloadedCollectionKind.Favorites -> activeProvider.favoriteTracks()
                        }
                    }
                    reconcileKeepDownloadedPolicy(policy, tracks)
                }.onFailure { error -> status = error.message ?: "Could not refresh ${policy.name}." }
            }
        }
    }

    private fun reconcileKeepDownloadedPolicy(policy: KeepDownloadedCollectionPolicy, tracks: List<Track>) {
        val plan = downloads.reconcile(policy, tracks)
        reloadKeepDownloadedPolicies()
        if (plan.tracksToDownload.isEmpty()) {
            status = "${policy.name} is up to date."
        } else {
            downloadTracks("Keeping ${policy.name} downloaded", plan.tracksToDownload)
        }
        if (plan.trackIdsToRemove.isNotEmpty()) incrementRefreshToken()
    }

    fun removeDownloadedTrack(download: DownloadedTrack) {
        val activeSourceId = sourceId() ?: return
        downloadRepository.removeDownloadedAudio(activeSourceId, download.track.id)
        incrementRefreshToken()
        status = downloadedTrackRemovedStatus(download.track.title)
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
            status = if (removed == 0) "Downloads are up to date." else "Removed $removed missing download${if (removed == 1) "" else "s"}."
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
            status = "Deleted ${downloads.size} download${if (downloads.size == 1) "" else "s"}."
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

private const val FavoritesCollectionId = "favorite-tracks"
