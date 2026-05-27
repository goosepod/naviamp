package app.naviamp.android

import app.naviamp.domain.Album
import app.naviamp.domain.Track
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.radio.RadioService
import app.naviamp.domain.radio.generatedRadioTracksToAppend
import app.naviamp.domain.radio.generatedRadioQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun appendAndroidGeneratedRadioTracks(
    state: AndroidAppState,
    seedTrack: Track,
    fetchedTracks: List<Track>,
) {
    with(state) {
        if (nowPlaying?.id != seedTrack.id) return
        val newTracks = generatedRadioTracksToAppend(seedTrack, fetchedTracks, playbackQueue.tracks)
        if (newTracks.isNotEmpty()) {
            playbackQueue = playbackQueue.copy(tracks = playbackQueue.tracks + newTracks)
        }
    }
}

fun startAndroidSeededRadio(
    scope: CoroutineScope,
    state: AndroidAppState,
    statusLabel: String,
    seedTrack: Track,
    playTrack: (Track, List<Track>) -> Unit,
    loadRest: suspend (RadioService) -> List<Track>,
) {
    val activeProvider = state.provider ?: return
    val seedQueue = listOf(seedTrack)
    playTrack(seedTrack, seedQueue)
    scope.launch {
        with(state) {
            runCatching { loadRest(RadioService(activeProvider, count = AndroidInitialSimilarRadioCount)) }
                .onSuccess { fetchedTracks ->
                    val queue = generatedRadioQueue(seedTrack, fetchedTracks)
                        .ifEmpty { seedQueue }
                    if (nowPlaying?.id == seedTrack.id) {
                        playbackQueue = PlaybackQueue(tracks = queue, currentIndex = 0)
                    }
                    status = "Building $statusLabel queue..."
                }
                .onFailure { error ->
                    if (nowPlaying?.id == seedTrack.id) {
                        status = error.message ?: "Could not build $statusLabel."
                    }
                }

            AndroidSimilarRadioExpansionCounts.forEach { count ->
                if (nowPlaying?.id != seedTrack.id) return@launch
                val fetchedTracks = runCatching {
                    loadRest(RadioService(activeProvider, count = count))
                }.getOrElse {
                    return@forEach
                }
                appendAndroidGeneratedRadioTracks(state, seedTrack, fetchedTracks)
                status = "Building $statusLabel queue (${playbackQueue.tracks.size} tracks)..."
            }
            if (nowPlaying?.id == seedTrack.id) {
                status = "Playing $statusLabel."
            }
        }
    }
}

fun startAndroidTrackRadio(
    scope: CoroutineScope,
    state: AndroidAppState,
    track: Track,
    playTrack: (Track, List<Track>) -> Unit,
) {
    startAndroidSeededRadio(
        scope = scope,
        state = state,
        statusLabel = "${track.title} radio",
        seedTrack = track,
        playTrack = playTrack,
    ) { radioService ->
        radioService.trackRadio(track.id)
    }
}

fun startAndroidAlbumRadio(
    scope: CoroutineScope,
    state: AndroidAppState,
    album: Album,
    loadedAlbumTracks: List<Track> = emptyList(),
    playTrack: (Track, List<Track>) -> Unit,
) {
    val service = state.provider?.let { RadioService(it) } ?: return
    scope.launch {
        with(state) {
            status = "Starting ${album.title} radio..."
            val seedTrack = service.albumSeed(album, loadedAlbumTracks)
            if (seedTrack == null) {
                status = "${album.title} did not return any tracks."
            } else {
                startAndroidSeededRadio(
                    scope = scope,
                    state = state,
                    statusLabel = "${album.title} radio",
                    seedTrack = seedTrack,
                    playTrack = playTrack,
                ) { radioService ->
                    radioService.albumRadio(album.id, loadedAlbumTracks)
                }
            }
        }
    }
}
