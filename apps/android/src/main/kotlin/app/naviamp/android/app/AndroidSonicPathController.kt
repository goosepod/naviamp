package app.naviamp.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackQueueManager
import app.naviamp.domain.playback.applyPlaybackQueueUpdate
import app.naviamp.domain.sonicpath.SonicPathDefaultCount
import app.naviamp.domain.sonicpath.SonicPathRequest
import app.naviamp.domain.sonicpath.SonicPathService
import app.naviamp.ui.SharedSonicPathBuilderUi
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.toSharedTrackRowUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AndroidSonicPathController(
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    private val queueController: PlaybackQueueController,
    private val playTrack: (Track, List<Track>?) -> Unit,
) {
    var startQuery by mutableStateOf("")
        private set
    var endQuery by mutableStateOf("")
        private set
    private var startTrack by mutableStateOf<Track?>(null)
    private var endTrack by mutableStateOf<Track?>(null)
    private var startSuggestions by mutableStateOf<List<Track>>(emptyList())
    private var endSuggestions by mutableStateOf<List<Track>>(emptyList())
    private var pathTracks by mutableStateOf<List<Track>>(emptyList())
    private var count by mutableStateOf(SonicPathDefaultCount)
    private var status by mutableStateOf<String?>(null)
    private var loading by mutableStateOf(false)

    fun ui(coverArtUrl: (String?) -> String?): SharedSonicPathBuilderUi =
        SharedSonicPathBuilderUi(
            startQuery = startQuery,
            endQuery = endQuery,
            startTrack = startTrack?.toSharedTrackRowUi(coverArtUrl),
            endTrack = endTrack?.toSharedTrackRowUi(coverArtUrl),
            startSuggestions = startSuggestions.map { track -> track.toSharedTrackRowUi(coverArtUrl) },
            endSuggestions = endSuggestions.map { track -> track.toSharedTrackRowUi(coverArtUrl) },
            pathTracks = pathTracks.map { track -> track.toSharedTrackRowUi(coverArtUrl) },
            count = count,
            status = status,
            loading = loading,
        )

    fun updateStartQuery(query: String) {
        startQuery = query
    }

    fun updateEndQuery(query: String) {
        endQuery = query
    }

    fun updateCount(value: Int) {
        count = value.coerceIn(2, 100)
    }

    fun selectStartTrack(item: SharedTrackRowUi) {
        startTrack = startSuggestions.firstOrNull { track -> track.id.value == item.id }
        startSuggestions = emptyList()
        pathTracks = emptyList()
    }

    fun selectEndTrack(item: SharedTrackRowUi) {
        endTrack = endSuggestions.firstOrNull { track -> track.id.value == item.id }
        endSuggestions = emptyList()
        pathTracks = emptyList()
    }

    fun clearStartTrack() {
        startTrack = null
        pathTracks = emptyList()
    }

    fun clearEndTrack() {
        endTrack = null
        pathTracks = emptyList()
    }

    fun reset() {
        startQuery = ""
        endQuery = ""
        startTrack = null
        endTrack = null
        startSuggestions = emptyList()
        endSuggestions = emptyList()
        pathTracks = emptyList()
        count = SonicPathDefaultCount
        status = null
        loading = false
    }

    fun searchStartTracks() {
        searchTracks(startQuery) { tracks -> startSuggestions = tracks }
    }

    fun searchEndTracks() {
        searchTracks(endQuery) { tracks -> endSuggestions = tracks }
    }

    private fun searchTracks(
        query: String,
        setSuggestions: (List<Track>) -> Unit,
    ) {
        val provider = state.provider ?: return
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return
        status = "Searching tracks..."
        loading = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    provider.search(trimmedQuery, limit = 12).tracks
                }
            }.onSuccess { tracks ->
                setSuggestions(tracks)
                status = if (tracks.isEmpty()) "No matching tracks." else null
            }.onFailure { error ->
                status = error.message ?: "Could not search tracks."
            }
            loading = false
        }
    }

    fun buildPath() {
        val provider = state.provider ?: return
        val start = startTrack ?: return
        val end = endTrack ?: return
        status = "Finding sonic path..."
        loading = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    SonicPathService(provider).findPath(SonicPathRequest(start, end, count))
                }
            }.onSuccess { tracks ->
                pathTracks = tracks
                status = if (tracks.isEmpty()) "Sonic path did not return any tracks." else null
            }.onFailure { error ->
                status = error.message ?: "Could not find sonic path."
            }
            loading = false
        }
    }

    fun playPath() {
        if (pathTracks.isEmpty()) return
        playTrack(pathTracks.first(), pathTracks)
    }

    fun addPathToQueue() {
        val update = PlaybackQueueManager().appendTracks(
            currentQueue = state.playbackQueue,
            tracksToAdd = pathTracks,
            label = "sonic path",
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
}
