package app.naviamp.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.naviamp.desktop.playback.DesktopPlaylistEngine
import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.domain.Track
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackQueueManager
import app.naviamp.domain.playback.applyPlaybackQueueUpdate
import app.naviamp.domain.sonichome.SonicHomeDiscoveryRows
import app.naviamp.domain.sonichome.SonicHomeDiscoveryService
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.ui.SharedHomeDiscoveryTrackActionRequest
import app.naviamp.ui.SharedTrackRowAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DesktopSonicHomeDiscoveryController(
    private val scope: CoroutineScope,
    private val storage: LocalLibraryIndexRepository,
    private val playbackEngine: PlaybackEngine,
    private val playlistEngine: DesktopPlaylistEngine,
    private val provider: () -> NavidromeProvider?,
    private val sourceId: () -> String?,
    private val recentTracks: () -> List<Track>,
    private val playbackSettings: () -> PlaybackSettings,
    private val playlistCallbacks: () -> PlaylistCallbacks,
    private val stopRadioContinuation: () -> Unit,
    private val clearShuffleSnapshot: () -> Unit,
    private val setOpenPlayerOnTrackStart: (Boolean) -> Unit,
    private val setConnectionStatus: (String?) -> Unit,
) {
    var rows by mutableStateOf(SonicHomeDiscoveryRows())
        private set

    private val loadedSourceIds = mutableSetOf<String>()

    fun loadIfNeeded(enabled: Boolean) {
        val activeProvider = provider()
        val activeSourceId = sourceId()
        if (!enabled || activeProvider == null || activeSourceId == null) {
            rows = SonicHomeDiscoveryRows()
            return
        }
        if (activeSourceId in loadedSourceIds && rows.rows.isNotEmpty()) return
        scope.launch {
            val loadedRows = withContext(Dispatchers.IO) {
                runCatching {
                    val tracks = storage.librarySnapshot(
                        activeSourceId,
                        limit = SonicHomeDiscoveryLibrarySampleLimit,
                        offset = 0,
                    ).tracks
                    SonicHomeDiscoveryService(activeProvider).loadRows(
                        libraryTracks = tracks,
                        recentTracks = recentTracks(),
                    )
                }.getOrDefault(SonicHomeDiscoveryRows())
            }
            rows = loadedRows
            if (loadedRows.rows.isNotEmpty()) {
                loadedSourceIds += activeSourceId
            }
        }
    }

    fun handleAction(request: SharedHomeDiscoveryTrackActionRequest) {
        val rowTracks = rows.rows.firstOrNull { row -> row.id.value == request.rowId }?.tracks.orEmpty()
        val track = rowTracks.firstOrNull { candidate -> candidate.id.value == request.track.id } ?: return
        when (request.action) {
            SharedTrackRowAction.Select -> playRow(rowTracks, track)
            SharedTrackRowAction.AddToQueue -> addTracksToQueue(listOf(track), "track")
            SharedTrackRowAction.StartRadio,
            SharedTrackRowAction.PlayTrackRadioNext,
            SharedTrackRowAction.AddTrackRadioToQueue,
            SharedTrackRowAction.Download,
            SharedTrackRowAction.AddToPlaylist,
            SharedTrackRowAction.CreatePlaylistAndAdd,
            -> Unit
        }
    }

    private fun playRow(rowTracks: List<Track>, track: Track) {
        val activeProvider = provider() ?: return
        val index = rowTracks.indexOfFirst { candidate -> candidate.id == track.id }.coerceAtLeast(0)
        stopRadioContinuation()
        clearShuffleSnapshot()
        setOpenPlayerOnTrackStart(true)
        setConnectionStatus(null)
        playlistEngine.playFrom(
            scope = scope,
            provider = activeProvider,
            tracks = rowTracks,
            index = index,
            quality = playbackSettings().streamQuality(playbackEngine),
            replayGainMode = playbackSettings().replayGainMode,
            callbacks = playlistCallbacks(),
        )
    }

    private fun addTracksToQueue(tracks: List<Track>, label: String) {
        val update = PlaybackQueueManager().appendTracks(
            currentQueue = playlistEngine.queue,
            tracksToAdd = tracks,
            label = label,
            existingTracks = playlistEngine.queue.tracks,
            deduplicateExisting = true,
        )
        applyPlaybackQueueUpdate(
            update = update,
            setStatus = setConnectionStatus,
            replaceQueue = playlistEngine::replaceQueue,
        )
    }
}

private const val SonicHomeDiscoveryLibrarySampleLimit = 5000L
