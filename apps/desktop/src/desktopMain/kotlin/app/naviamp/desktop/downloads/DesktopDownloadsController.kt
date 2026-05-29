package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.downloadBlockedStatus
import app.naviamp.domain.cache.downloadCompletedStatus
import app.naviamp.domain.cache.downloadConnectionRequiredStatus
import app.naviamp.domain.cache.downloadErrorStatus
import app.naviamp.domain.cache.downloadProgressStatus
import app.naviamp.domain.cache.downloadStartingStatus
import app.naviamp.domain.cache.downloadedTrackRemovedStatus
import app.naviamp.domain.cache.planDownloadTracks
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
    private val sessionCache: DesktopCache,
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
    fun downloadTracks(label: String, tracks: List<Track>) {
        val activeProvider = provider()
        val activeSourceId = sourceId()
        val plan = planDownloadTracks(
            tracks = tracks,
            hasProvider = activeProvider != null,
            hasSource = activeSourceId != null,
        )
        plan.blockedReason?.let { reason ->
            setDownloadStatus(downloadBlockedStatus(reason, label))
            return
        }
        val tracksToDownload = plan.tracks
        setDownloadStatus(downloadStartingStatus(label))
        scope.launch {
            var completed = 0
            val uiContext = coroutineContext
            try {
                withContext(Dispatchers.IO) {
                    tracksToDownload.forEachIndexed { index, track ->
                        withContext(uiContext) {
                            setDownloadStatus(downloadProgressStatus(label, index, tracksToDownload.size))
                        }
                        sessionCache.downloadAudioTrack(
                            sourceId = requireNotNull(activeSourceId),
                            provider = requireNotNull(activeProvider),
                            track = track,
                            quality = playbackSettings().streamQuality(playbackEngine),
                            maxDownloadBytes = cacheSettings().maxDownloadBytes,
                        )
                        completed += 1
                    }
                }
                incrementDownloadRefreshToken()
                setDownloadStatus(downloadCompletedStatus(label, completed))
            } catch (exception: Exception) {
                incrementDownloadRefreshToken()
                setDownloadStatus(downloadErrorStatus(label, exception))
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
                    sessionCache.album(activeProvider, album.id).tracks
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
                    activeProvider.playlistTracks(playlist.id)
                }
                downloadTracks(playlist.name, tracks)
            } catch (exception: Exception) {
                setDownloadStatus(exception.message ?: "Could not load ${playlist.name}.")
            }
        }
    }

    fun removeDownloadedTrack(download: DownloadedTrack) {
        val activeSourceId = sourceId() ?: return
        sessionCache.removeDownloadedAudio(activeSourceId, download.track.id, playbackSettings().streamQuality(playbackEngine))
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
