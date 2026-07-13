package app.naviamp.android

import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackQueueManager
import app.naviamp.domain.playback.applyPlaybackQueueUpdate
import app.naviamp.domain.sonichome.SonicHomeDiscoveryRows
import app.naviamp.domain.sonichome.SonicHomeDiscoveryService
import app.naviamp.ui.SharedHomeDiscoveryTrackActionRequest
import app.naviamp.ui.SharedTrackRowAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AndroidSonicHomeDiscoveryController(
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    private val storage: AndroidStorageDependencies,
    private val queueController: PlaybackQueueController,
    private val playTrack: (Track, List<Track>) -> Unit,
) {
    private val loadedSourceIds = mutableSetOf<String>()

    fun loadIfNeeded(enabled: Boolean) {
        val activeProvider = state.provider
        val activeSourceId = state.activeSourceId
        if (!enabled || activeProvider == null || activeSourceId == null) {
            state.sonicHomeDiscoveryRows = SonicHomeDiscoveryRows()
            return
        }
        if (activeSourceId in loadedSourceIds && state.sonicHomeDiscoveryRows.rows.isNotEmpty()) return
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
                        recentTracks = recentTracks(activeSourceId),
                    )
                }.getOrDefault(SonicHomeDiscoveryRows())
            }
            state.sonicHomeDiscoveryRows = loadedRows
            if (loadedRows.rows.isNotEmpty()) {
                loadedSourceIds += activeSourceId
            }
        }
    }

    fun handleAction(request: SharedHomeDiscoveryTrackActionRequest) {
        val rowTracks = state.sonicHomeDiscoveryRows.rows
            .firstOrNull { row -> row.id.value == request.rowId }
            ?.tracks
            .orEmpty()
        val track = rowTracks.firstOrNull { candidate -> candidate.id.value == request.track.id } ?: return
        when (request.action) {
            SharedTrackRowAction.Select -> playTrack(track, rowTracks)
            SharedTrackRowAction.PlayNext -> playNext(track)
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

    private fun playNext(track: Track) {
        val update = PlaybackQueueManager().playNextTracks(
            currentQueue = state.playbackQueue,
            tracksToAdd = listOf(track),
            label = "track",
        )
        applyPlaybackQueueUpdate(
            update = update,
            setStatus = { status -> state.status = status },
            replaceQueue = { queue ->
                state.playbackQueue = queue
                queueController.replaceQueue(queue)
            },
        )
    }

    private fun addTracksToQueue(tracks: List<Track>, label: String) {
        val update = PlaybackQueueManager().appendTracks(
            currentQueue = state.playbackQueue,
            tracksToAdd = tracks,
            label = label,
            existingTracks = state.playbackQueue.tracks,
            deduplicateExisting = true,
        )
        applyPlaybackQueueUpdate(
            update = update,
            setStatus = { status -> state.status = status },
            replaceQueue = { queue ->
                state.playbackQueue = queue
                queueController.replaceQueue(queue)
            },
        )
    }

    private fun recentTracks(sourceId: String): List<Track> =
        (
            listOfNotNull(state.nowPlaying) +
                storage.playbackHistory(sourceId, limit = 20).map { item -> item.track } +
                state.playbackQueue.tracks
            ).distinctBy { track -> track.id }
}

private const val SonicHomeDiscoveryLibrarySampleLimit = 5000L
