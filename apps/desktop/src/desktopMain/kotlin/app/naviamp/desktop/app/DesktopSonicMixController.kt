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
import app.naviamp.domain.sonicmix.SonicMixBias
import app.naviamp.domain.sonicmix.SonicMixDefaultTargetLength
import app.naviamp.domain.sonicmix.SonicMixMaxSeeds
import app.naviamp.domain.sonicmix.SonicMixMaxTargetLength
import app.naviamp.domain.sonicmix.SonicMixMinTargetLength
import app.naviamp.domain.sonicmix.SonicMixRequest
import app.naviamp.domain.sonicmix.SonicMixService
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.ui.SharedSonicMixBiasUi
import app.naviamp.ui.SharedSonicMixBuilderUi
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.toSharedTrackRowUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DesktopSonicMixController(
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
    var query by mutableStateOf("")
        private set
    private var selectedTracks by mutableStateOf<List<Track>>(emptyList())
    private var suggestedTracks by mutableStateOf<List<Track>>(emptyList())
    private var mixTracks by mutableStateOf<List<Track>>(emptyList())
    private var targetLength by mutableStateOf(SonicMixDefaultTargetLength)
    private var bias by mutableStateOf(SonicMixBias.Balanced)
    private var status by mutableStateOf<String?>(null)
    private var loading by mutableStateOf(false)

    fun ui(coverArtUrl: (String?) -> String?): SharedSonicMixBuilderUi =
        SharedSonicMixBuilderUi(
            query = query,
            selectedTracks = selectedTracks.map { track -> track.toSharedTrackRowUi(coverArtUrl) },
            suggestedTracks = suggestedTracks.map { track -> track.toSharedTrackRowUi(coverArtUrl) },
            mixTracks = mixTracks.map { track -> track.toSharedTrackRowUi(coverArtUrl) },
            targetLength = targetLength,
            bias = bias.toSharedUi(),
            status = status,
            loading = loading,
        )

    fun updateQuery(value: String) {
        query = value
    }

    fun updateTargetLength(value: Int) {
        targetLength = value.coerceIn(SonicMixMinTargetLength, SonicMixMaxTargetLength)
    }

    fun updateBias(value: SharedSonicMixBiasUi) {
        bias = value.toDomain()
        mixTracks = emptyList()
    }

    fun selectTrack(item: SharedTrackRowUi) {
        val track = suggestedTracks.firstOrNull { track -> track.id.value == item.id } ?: return
        if (selectedTracks.any { selected -> selected.id == track.id }) return
        selectedTracks = (selectedTracks + track).take(SonicMixMaxSeeds)
        suggestedTracks = suggestedTracks.filterNot { suggestion -> suggestion.id == track.id }
        mixTracks = emptyList()
    }

    fun removeTrack(item: SharedTrackRowUi) {
        selectedTracks = selectedTracks.filterNot { track -> track.id.value == item.id }
        mixTracks = emptyList()
    }

    fun reset() {
        query = ""
        selectedTracks = emptyList()
        suggestedTracks = emptyList()
        mixTracks = emptyList()
        targetLength = SonicMixDefaultTargetLength
        bias = SonicMixBias.Balanced
        status = null
        loading = false
    }

    fun searchTracks() {
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
                val selectedIds = selectedTracks.map { track -> track.id }.toSet()
                suggestedTracks = tracks.filterNot { track -> track.id in selectedIds }
                status = if (suggestedTracks.isEmpty()) "No matching tracks." else null
            }.onFailure { error ->
                status = error.message ?: "Could not search tracks."
            }
            loading = false
        }
    }

    fun buildMix() {
        val activeProvider = provider() ?: return
        if (selectedTracks.size < 2) return
        status = "Building sonic mix..."
        loading = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    SonicMixService(activeProvider).buildMix(
                        SonicMixRequest(
                            seedTracks = selectedTracks,
                            targetLength = targetLength,
                            bias = bias,
                        ),
                    )
                }
            }.onSuccess { tracks ->
                mixTracks = tracks
                status = if (tracks.isEmpty()) "Sonic mix did not return any tracks." else null
            }.onFailure { error ->
                status = error.message ?: "Could not build sonic mix."
            }
            loading = false
        }
    }

    fun playMix() {
        val activeProvider = provider() ?: return
        if (mixTracks.isEmpty()) return
        stopRadioContinuation()
        clearShuffleSnapshot()
        setOpenPlayerOnTrackStart(true)
        setConnectionStatus(null)
        playlistEngine.playFrom(
            scope = scope,
            provider = activeProvider,
            tracks = mixTracks,
            index = 0,
            quality = playbackSettings().streamQuality(playbackEngine),
            replayGainMode = playbackSettings().replayGainMode,
            callbacks = playlistCallbacks(),
        )
    }

    fun addMixToQueue() {
        val update = PlaybackQueueManager().appendTracks(
            currentQueue = playlistEngine.queue,
            tracksToAdd = mixTracks,
            label = "sonic mix",
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

private fun SonicMixBias.toSharedUi(): SharedSonicMixBiasUi =
    when (this) {
        SonicMixBias.Balanced -> SharedSonicMixBiasUi.Balanced
        SonicMixBias.Favorites -> SharedSonicMixBiasUi.Favorites
        SonicMixBias.Unplayed -> SharedSonicMixBiasUi.Unplayed
        SonicMixBias.Recent -> SharedSonicMixBiasUi.Recent
    }

private fun SharedSonicMixBiasUi.toDomain(): SonicMixBias =
    when (this) {
        SharedSonicMixBiasUi.Balanced -> SonicMixBias.Balanced
        SharedSonicMixBiasUi.Favorites -> SonicMixBias.Favorites
        SharedSonicMixBiasUi.Unplayed -> SonicMixBias.Unplayed
        SharedSonicMixBiasUi.Recent -> SonicMixBias.Recent
    }
