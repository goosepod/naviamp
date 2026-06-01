package app.naviamp.android

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.radio.RadioService
import app.naviamp.domain.radio.generatedRadioTracksToAppend
import app.naviamp.domain.radio.generatedRadioQueue
import app.naviamp.domain.radio.radioRefillSeedTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun appendAndroidGeneratedRadioTracks(
    state: AndroidAppState,
    queueController: PlaybackQueueController,
    seedTrack: Track,
    fetchedTracks: List<Track>,
) {
    with(state) {
        if (nowPlaying?.id != seedTrack.id) return
        val previousQueue = playbackQueue
        val newTracks = generatedRadioTracksToAppend(seedTrack, fetchedTracks, playbackQueue.tracks)
        if (newTracks.isNotEmpty()) {
            playbackQueue = playbackQueue.copy(tracks = playbackQueue.tracks + newTracks)
        }
        if (playbackQueue != previousQueue) {
            queueController.replaceQueue(playbackQueue, clearPreparedNext = false)
        }
    }
}

fun refillAndroidRadioIfNeeded(
    scope: CoroutineScope,
    state: AndroidAppState,
    queue: PlaybackQueue,
    queueController: PlaybackQueueController,
) {
    val seedTrack = radioRefillSeedTrack(
        queue = queue,
        refillThreshold = AndroidRadioRefillThreshold,
        repeatMode = state.repeatMode,
        isActive = state.radioQueueActive,
        isRefilling = state.radioRefilling,
        lastRefillSeedTrackId = state.lastRadioRefillSeedId,
    ) ?: return
    val activeProvider = state.provider ?: return

    state.radioRefilling = true
    state.lastRadioRefillSeedId = seedTrack.id
    scope.launch {
        try {
            val fetchedTracks = withContext(Dispatchers.IO) {
                RadioService(activeProvider, count = AndroidRadioRefillCount).trackRadio(seedTrack.id)
            }
            val newTracks = generatedRadioTracksToAppend(
                seedTrack = seedTrack,
                fetchedTracks = fetchedTracks,
                queuedTracks = state.playbackQueue.tracks,
            )
            if (state.radioQueueActive && newTracks.isNotEmpty()) {
                queueController.replaceQueue(state.playbackQueue, clearPreparedNext = false)
                queueController.appendTracks(
                    tracks = newTracks,
                    maxHistory = AndroidRadioQueueHistoryLimit,
                )?.let { updatedQueue ->
                    state.playbackQueue = updatedQueue
                }
                state.status = "Extending radio queue (${state.playbackQueue.tracks.size} tracks)..."
            }
        } catch (error: Exception) {
            state.status = error.message ?: "Could not extend radio."
        } finally {
            if (state.lastRadioRefillSeedId == seedTrack.id) {
                state.radioRefilling = false
            }
        }
    }
}

fun startAndroidSeededRadio(
    scope: CoroutineScope,
    state: AndroidAppState,
    queueController: PlaybackQueueController,
    statusLabel: String,
    seedTrack: Track,
    playTrack: (Track, List<Track>) -> Unit,
    loadRest: suspend (RadioService) -> List<Track>,
) {
    val activeProvider = state.provider ?: return
    val seedQueue = listOf(seedTrack)
    state.radioQueueActive = true
    state.radioRefilling = true
    state.lastRadioRefillSeedId = seedTrack.id
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

            radioRefilling = false
            AndroidSimilarRadioExpansionCounts.forEach { count ->
                if (nowPlaying?.id != seedTrack.id) return@launch
                val fetchedTracks = runCatching {
                    loadRest(RadioService(activeProvider, count = count))
                }.getOrElse {
                    return@forEach
                }
                appendAndroidGeneratedRadioTracks(state, queueController, seedTrack, fetchedTracks)
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
    queueController: PlaybackQueueController,
    track: Track,
    playTrack: (Track, List<Track>) -> Unit,
) {
    startAndroidSeededRadio(
        scope = scope,
        state = state,
        queueController = queueController,
        statusLabel = "${track.title} radio",
        seedTrack = track,
        playTrack = playTrack,
    ) { radioService ->
        radioService.trackRadio(track.id)
    }
}

fun startAndroidArtistRadio(
    scope: CoroutineScope,
    state: AndroidAppState,
    queueController: PlaybackQueueController,
    artistId: ArtistId,
    artistTitle: String,
    artist: Artist,
    playTrack: (Track, List<Track>) -> Unit,
) {
    val activeProvider = state.provider ?: return
    scope.launch {
        with(state) {
            status = "Starting $artistTitle radio..."
            runCatching {
                RadioService(activeProvider, count = AndroidInitialSimilarRadioCount)
                    .artistSeed(artist, artistDetail?.albums.orEmpty())
            }.onSuccess { seedTrack ->
                if (seedTrack == null) {
                    status = "$artistTitle radio did not find a seed track."
                } else {
                    startAndroidSeededRadio(
                        scope = scope,
                        state = state,
                        queueController = queueController,
                        statusLabel = "$artistTitle radio",
                        seedTrack = seedTrack,
                        playTrack = playTrack,
                    ) { radioService ->
                        radioService.artistRadio(artistId)
                    }
                }
            }.onFailure { error ->
                status = error.message ?: "Could not start artist radio."
            }
        }
    }
}

fun startAndroidPopularTracksRadio(
    scope: CoroutineScope,
    state: AndroidAppState,
    queueController: PlaybackQueueController,
    artistTitle: String,
    popularTracks: List<Track>,
    playTrack: (Track, List<Track>) -> Unit,
) {
    val seedTrack = popularTracks.shuffled().firstOrNull()
    if (seedTrack == null) {
        state.status = "No popular tracks matched your library."
        return
    }
    startAndroidSeededRadio(
        scope = scope,
        state = state,
        queueController = queueController,
        statusLabel = "$artistTitle popular tracks radio",
        seedTrack = seedTrack,
        playTrack = playTrack,
    ) { radioService ->
        coroutineScope {
            popularTracks.take(AndroidPopularRadioSeedLimit)
                .map { track -> async(Dispatchers.IO) { radioService.trackRadio(track.id) } }
                .awaitAll()
                .flatten()
        }
    }
}

fun startAndroidTrackRadioQueue(
    scope: CoroutineScope,
    state: AndroidAppState,
    queueController: PlaybackQueueController,
    track: Track,
    playSeed: Boolean,
    playTrack: (Track, List<Track>) -> Unit,
) {
    val activeProvider = state.provider ?: return
    if (state.provider?.capabilities?.supportsTrackRadio != true) return
    state.radioQueueActive = true
    state.radioRefilling = true
    state.lastRadioRefillSeedId = track.id
    scope.launch {
        with(state) {
            status = "Starting ${track.title} radio..."
            runCatching { RadioService(activeProvider, count = AndroidInitialSimilarRadioCount).trackRadio(track.id) }
                .onSuccess { radioTracks ->
                    val queue = generatedRadioQueue(track, radioTracks)
                    if (playSeed) {
                        playTrack(track, queue)
                    } else {
                        queueController.replaceQueue(PlaybackQueue(tracks = queue, currentIndex = 0))
                        playbackQueue = queueController.queue
                        relatedTracks = queue.drop(1)
                        shuffledUpNextSnapshot = null
                    }
                    status = "Building ${track.title} radio queue..."
                    radioRefilling = false
                    AndroidSimilarRadioExpansionCounts.forEach { count ->
                        if (nowPlaying?.id != track.id) return@launch
                        val fetchedTracks = runCatching {
                            RadioService(activeProvider, count = count).trackRadio(track.id)
                        }.getOrElse {
                            return@forEach
                        }
                        appendAndroidGeneratedRadioTracks(state, queueController, track, fetchedTracks)
                        status = "Building ${track.title} radio queue (${playbackQueue.tracks.size} tracks)..."
                    }
                    if (nowPlaying?.id == track.id) {
                        status = "Track radio loaded."
                    }
                }
                .onFailure { error ->
                    radioRefilling = false
                    status = error.message ?: "Could not start track radio."
                }
        }
    }
}

fun startAndroidAlbumRadio(
    scope: CoroutineScope,
    state: AndroidAppState,
    queueController: PlaybackQueueController,
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
                    queueController = queueController,
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
