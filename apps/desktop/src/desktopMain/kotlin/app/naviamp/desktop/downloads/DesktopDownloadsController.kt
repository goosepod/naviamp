package app.naviamp.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.naviamp.domain.Album
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.DownloadReplacementRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.DownloadService
import app.naviamp.domain.cache.DownloadTracksResult
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.cache.downloadConnectionRequiredStatus
import app.naviamp.domain.cache.downloadTracksWithRefresh
import app.naviamp.domain.cache.downloadedTrackRemovedStatus
import app.naviamp.domain.cache.redownloadTracksWithRefresh
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.PlaybackSettings
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

    private val providerResponseService = ProviderResponseService(providerResponseCacheRepository)

    private fun incrementRefreshToken() {
        refreshToken += 1
    }

    fun downloadTracks(label: String, tracks: List<Track>) {
        val activeProvider = provider()
        val activeSourceId = sourceId()
        val downloadService = DownloadService(downloadRepository, downloadReplacementRepository)
        scope.launch {
            val quality = playbackSettings().streamQuality(playbackEngine)
            val maxDownloadBytes = cacheSettings().maxDownloadBytes
            val result = downloadService.downloadTracksWithRefresh(
                label = label,
                tracks = tracks,
                sourceId = activeSourceId,
                provider = activeProvider,
                quality = quality,
                maxDownloadBytes = maxDownloadBytes,
                setStatus = { downloadStatus -> status = downloadStatus },
                shouldRefreshDownloads = { it !is DownloadTracksResult.Blocked },
                loadStats = {},
            )
            if (result.refreshDownloads) {
                incrementRefreshToken()
            }
        }
    }

    fun downloadTrack(track: Track) {
        downloadTracks(track.title, listOf(track))
    }

    fun redownloadTracks(tracks: List<Track>, label: String = "downloads") {
        val activeProvider = provider()
        val activeSourceId = sourceId()
        val downloadService = DownloadService(downloadRepository, downloadReplacementRepository)
        scope.launch {
            val quality = playbackSettings().streamQuality(playbackEngine)
            val result = downloadService.redownloadTracksWithRefresh(
                tracks = tracks,
                sourceId = activeSourceId,
                provider = activeProvider,
                quality = quality,
                maxDownloadBytes = cacheSettings().maxDownloadBytes,
                setStatus = { downloadStatus -> status = downloadStatus },
                loadStats = { withContext(Dispatchers.IO) { cacheMaintenanceRepository.stats() } },
            )
            if (result.refreshDownloads) {
                incrementRefreshToken()
                result.stats?.let(setCacheStats)
            }
        }
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

    fun removeDownloadedTrack(download: DownloadedTrack) {
        val activeSourceId = sourceId() ?: return
        downloadRepository.removeDownloadedAudio(activeSourceId, download.track.id)
        incrementRefreshToken()
        status = downloadedTrackRemovedStatus(download.track.title)
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
            callbacks = playlistCallbacks(),
        )
    }
}
