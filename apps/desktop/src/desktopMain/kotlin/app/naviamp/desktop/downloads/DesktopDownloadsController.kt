package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.DownloadReplacementRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.DownloadService
import app.naviamp.domain.cache.DownloadTracksResult
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.cache.downloadConnectionRequiredStatus
import app.naviamp.domain.cache.downloadedTrackRemovedStatus
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.desktop.playback.PlaylistEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DesktopDownloadsController(
    private val scope: CoroutineScope,
    private val downloadRepository: DownloadRepository<DownloadedAudioFile, DownloadedTrack>,
    private val downloadReplacementRepository: DownloadReplacementRepository<DownloadedAudioFile>,
    providerResponseCacheRepository: ProviderResponseCacheRepository,
    private val playbackEngine: PlaybackEngine,
    private val playbackSettings: () -> PlaybackSettings,
    private val cacheSettings: () -> CacheSettings,
    private val provider: () -> MediaProvider?,
    private val sourceId: () -> String?,
    private val stopRadioContinuation: () -> Unit,
    private val clearShuffleSnapshot: () -> Unit,
    private val setOpenPlayerOnTrackStart: (Boolean) -> Unit,
    private val playlistEngine: PlaylistEngine,
    private val playlistCallbacks: () -> PlaylistCallbacks,
    private val setDownloadStatus: (String?) -> Unit,
    private val incrementDownloadRefreshToken: () -> Unit,
) {
    private val providerResponseService = ProviderResponseService(providerResponseCacheRepository)

    fun downloadTracks(label: String, tracks: List<Track>) {
        val activeProvider = provider()
        val activeSourceId = sourceId()
        val downloadService = DownloadService(downloadRepository, downloadReplacementRepository)
        scope.launch {
            val quality = playbackSettings().streamQuality(playbackEngine)
            val maxDownloadBytes = cacheSettings().maxDownloadBytes
            val result = downloadService.downloadTracksWithStatus(
                label = label,
                tracks = tracks,
                sourceId = activeSourceId,
                provider = activeProvider,
                quality = quality,
                maxDownloadBytes = maxDownloadBytes,
                setStatus = setDownloadStatus,
            )
            if (result !is DownloadTracksResult.Blocked) {
                incrementDownloadRefreshToken()
            }
        }
    }

    fun downloadTrack(track: Track) {
        downloadTracks(track.title, listOf(track))
    }

    fun downloadAlbum(album: Album) {
        val activeProvider = provider() ?: run {
            setDownloadStatus(downloadConnectionRequiredStatus())
            return
        }
        setDownloadStatus("Loading ${album.title}...")
        scope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) {
                    providerResponseService.album(activeProvider, album.id).tracks
                }
                downloadTracks(album.title, tracks)
            } catch (exception: Exception) {
                setDownloadStatus(exception.message ?: "Could not load ${album.title}.")
            }
        }
    }

    fun downloadPlaylist(playlist: Playlist) {
        val activeProvider = provider() ?: run {
            setDownloadStatus(downloadConnectionRequiredStatus())
            return
        }
        setDownloadStatus("Loading ${playlist.name}...")
        scope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) {
                    providerResponseService.playlistTracks(activeProvider, playlist.id)
                }
                downloadTracks(playlist.name, tracks)
            } catch (exception: Exception) {
                setDownloadStatus(exception.message ?: "Could not load ${playlist.name}.")
            }
        }
    }

    fun removeDownloadedTrack(download: DownloadedTrack) {
        val activeSourceId = sourceId() ?: return
        downloadRepository.removeDownloadedAudio(activeSourceId, download.track.id)
        incrementDownloadRefreshToken()
        setDownloadStatus(downloadedTrackRemovedStatus(download.track.title))
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
