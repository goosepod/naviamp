package app.naviamp.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.naviamp.desktop.playback.DesktopPlaylistEngine
import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackQueueManager
import app.naviamp.domain.playback.applyPlaybackQueueUpdate
import app.naviamp.domain.sonicpath.SonicPathDefaultCount
import app.naviamp.domain.sonicpath.SonicPathRequest
import app.naviamp.domain.sonicpath.SonicPathService
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.ui.SharedSonicPathBuilderUi
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.toSharedTrackRowUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DesktopSonicPathController(
    private val scope: CoroutineScope,
    private val playbackEngine: PlaybackEngine,
    private val playlistEngine: DesktopPlaylistEngine,
    private val provider: () -> NavidromeProvider?,
    private val playbackSettings: () -> PlaybackSettings,
    private val playlistCallbacks: () -> PlaylistCallbacks,
    private val stopRadioContinuation: () -> Unit,
    private val clearShuffleSnapshot: () -> Unit,
    private val setOpenPlayerOnTrackStart: (Boolean) -> Unit,
    private val setConnectionStatus: (String?) -> Unit,
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
        val activeProvider = provider() ?: return
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return
        status = "Searching tracks..."
        loading = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    activeProvider.search(trimmedQuery, limit = 12).tracks
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
        val activeProvider = provider() ?: return
        val start = startTrack ?: return
        val end = endTrack ?: return
        status = "Finding sonic path..."
        loading = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    SonicPathService(activeProvider).findPath(SonicPathRequest(start, end, count))
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
        val activeProvider = provider() ?: return
        if (pathTracks.isEmpty()) return
        stopRadioContinuation()
        clearShuffleSnapshot()
        setOpenPlayerOnTrackStart(true)
        setConnectionStatus(null)
        playlistEngine.playFrom(
            scope = scope,
            provider = activeProvider,
            tracks = pathTracks,
            index = 0,
            quality = playbackSettings().streamQuality(playbackEngine),
            replayGainMode = playbackSettings().replayGainMode,
            replayGainPreampDb = playbackSettings().replayGainPreampDb,
            callbacks = playlistCallbacks(),
        )
    }

    fun addPathToQueue() {
        val update = PlaybackQueueManager().appendTracks(
            currentQueue = playlistEngine.queue,
            tracksToAdd = pathTracks,
            label = "sonic path",
            existingTracks = playlistEngine.queue.tracks,
            deduplicateExisting = true,
        )
        applyPlaybackQueueUpdate(
            update = update,
            setStatus = setConnectionStatus,
            replaceQueue = playlistEngine::replaceQueue,
        )
    }

    fun playlistTracks(): List<Track> = pathTracks
}
