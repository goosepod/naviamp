package app.naviamp.desktop.playback

import app.naviamp.desktop.DesktopCache
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PlaylistEngine(
    private val playbackEngine: PlaybackEngine,
    private val cache: DesktopCache? = null,
    private val sourceIdProvider: () -> String? = { null },
) {
    private var provider: MediaProvider? = null
    private var streamQuality: StreamQuality? = null
    private var replayGainMode: ReplayGainMode = ReplayGainMode.Off
    private var callbacks: PlaylistCallbacks? = null
    private var crossfadeSettings = CrossfadeSettings()
    private var preparedNextIndex: Int? = null
    private var audioPrefetchJob: Job? = null
    private var sessionId = 0

    var queue: PlaybackQueue = PlaybackQueue()
        private set

    fun playFrom(
        scope: CoroutineScope,
        provider: MediaProvider,
        tracks: List<Track>,
        index: Int,
        quality: StreamQuality,
        replayGainMode: ReplayGainMode,
        callbacks: PlaylistCallbacks,
    ) {
        this.provider = provider
        this.streamQuality = quality
        this.replayGainMode = replayGainMode
        this.callbacks = callbacks
        sessionId += 1
        audioPrefetchJob?.cancel()
        audioPrefetchJob = null

        playQueueIndex(scope, tracks, index, sessionId)
    }

    fun restore(
        provider: MediaProvider,
        tracks: List<Track>,
        index: Int,
        quality: StreamQuality,
        replayGainMode: ReplayGainMode,
        callbacks: PlaylistCallbacks,
    ) {
        if (tracks.isEmpty() || index !in tracks.indices) return
        this.provider = provider
        this.streamQuality = quality
        this.replayGainMode = replayGainMode
        this.callbacks = callbacks
        sessionId += 1
        audioPrefetchJob?.cancel()
        audioPrefetchJob = null
        queue = PlaybackQueue(tracks = tracks, currentIndex = index)
        preparedNextIndex = null
        callbacks.onQueueChanged(queue)
        callbacks.onPlaybackProgressChanged(PlaybackProgress.Unknown)
        callbacks.onPlaybackStateChanged(PlaybackState.Idle)
    }

    fun clear() {
        sessionId += 1
        audioPrefetchJob?.cancel()
        audioPrefetchJob = null
        queue = PlaybackQueue()
        playbackEngine.stop()
        callbacks?.onQueueChanged(queue)
    }

    fun updateTrack(updatedTrack: Track) {
        val updatedTracks = queue.tracks.map { track ->
            if (track.id == updatedTrack.id) updatedTrack else track
        }
        if (updatedTracks == queue.tracks) return
        queue = queue.copy(tracks = updatedTracks)
        callbacks?.onQueueChanged(queue)
    }

    fun appendTracks(
        tracks: List<Track>,
        maxHistory: Int? = null,
    ) {
        if (tracks.isEmpty()) return

        val prunedTrackCount = maxHistory
            ?.let { (queue.currentIndex - it.coerceAtLeast(0)).coerceAtLeast(0) }
            ?: 0
        queue = PlaybackQueue(
            tracks = (queue.tracks + tracks).drop(prunedTrackCount),
            currentIndex = queue.currentIndex - prunedTrackCount,
        )
        callbacks?.onQueueChanged(queue)
    }

    fun replaceUpcomingTracks(
        currentTrack: Track,
        upcomingTracks: List<Track>,
        maxHistory: Int? = null,
    ) {
        val currentQueueIndex = queue.tracks.indexOfFirst { it.id == currentTrack.id }
            .takeIf { it >= 0 }
            ?: queue.currentIndex
        val currentQueueTrack = queue.tracks.getOrNull(currentQueueIndex) ?: currentTrack
        val prunedTrackCount = maxHistory
            ?.let { (currentQueueIndex - it.coerceAtLeast(0)).coerceAtLeast(0) }
            ?: 0
        val history = queue.tracks
            .take(currentQueueIndex)
            .drop(prunedTrackCount)
        val dedupedUpcoming = upcomingTracks.filterNot { track ->
            track.id == currentQueueTrack.id || history.any { it.id == track.id }
        }

        queue = PlaybackQueue(
            tracks = history + currentQueueTrack + dedupedUpcoming,
            currentIndex = history.size,
        )
        callbacks?.onQueueChanged(queue)
    }

    fun setCrossfadeSettings(settings: CrossfadeSettings) {
        crossfadeSettings = settings
        (playbackEngine as? QueueAwarePlaybackEngine)?.setCrossfadeDuration(settings.durationSeconds)
    }

    fun next(scope: CoroutineScope) {
        if (!queue.hasNext()) return
        sessionId += 1
        playQueueIndex(scope, queue.tracks, queue.currentIndex + 1, sessionId)
    }

    fun playCurrent(scope: CoroutineScope) {
        if (queue.currentIndex !in queue.tracks.indices) return
        sessionId += 1
        playQueueIndex(scope, queue.tracks, queue.currentIndex, sessionId)
    }

    fun playCurrent(scope: CoroutineScope, startPositionSeconds: Double?) {
        if (queue.currentIndex !in queue.tracks.indices) return
        sessionId += 1
        playQueueIndex(scope, queue.tracks, queue.currentIndex, sessionId, startPositionSeconds)
    }

    fun jumpTo(scope: CoroutineScope, index: Int) {
        if (index !in queue.tracks.indices || index == queue.currentIndex) return
        sessionId += 1
        playQueueIndex(scope, queue.tracks, index, sessionId)
    }

    fun previous(scope: CoroutineScope) {
        if (!queue.hasPrevious()) return
        sessionId += 1
        playQueueIndex(scope, queue.tracks, queue.currentIndex - 1, sessionId)
    }

    private fun playQueueIndex(
        scope: CoroutineScope,
        tracks: List<Track>,
        index: Int,
        activeSessionId: Int,
        startPositionSeconds: Double? = null,
    ) {
        val track = tracks.getOrNull(index) ?: return
        val currentProvider = provider ?: return
        val currentQuality = streamQuality ?: return
        val currentCallbacks = callbacks ?: return
        audioPrefetchJob?.cancel()
        audioPrefetchJob = null

        queue = PlaybackQueue(tracks = tracks, currentIndex = index)
        preparedNextIndex = null
        currentCallbacks.onQueueChanged(queue)
        currentCallbacks.onPlaybackProgressChanged(PlaybackProgress.Unknown)

        scope.launch {
            try {
                val playbackUrl = playbackUrl(currentProvider, track, currentQuality)
                val coverArtUrl = track.coverArtId?.let { currentProvider.coverArtUrl(it) }
                currentCallbacks.onTrackStarted(track, coverArtUrl)
                startAudioPrefetch(scope, activeSessionId)
                playbackEngine.play(
                    scope = scope,
                    request = PlaybackRequest(
                        url = playbackUrl,
                        mediaId = track.id.value,
                        replayGainMode = replayGainMode.forEngine(playbackEngine),
                        startPositionSeconds = startPositionSeconds?.takeIf { it > 0.0 },
                    ),
                    onStateChanged = { state ->
                        scope.launch {
                            handlePlaybackState(scope, state, activeSessionId)
                        }
                    },
                    onProgressChanged = { progress ->
                        scope.launch {
                            if (activeSessionId == sessionId) {
                                callbacks?.onPlaybackProgressChanged(progress)
                                prepareNextIfNeeded(scope, progress, activeSessionId)
                            }
                        }
                    },
                )
            } catch (exception: Exception) {
                if (activeSessionId == sessionId) {
                    currentCallbacks.onPlaybackStateChanged(
                        PlaybackState.Error(exception.message ?: "Playback failed."),
                    )
                }
            }
        }
    }

    private suspend fun playbackUrl(
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
    ): String {
        val sourceId = sourceIdProvider()
        val cached = if (sourceId != null) {
            cache?.cachedAudioFile(sourceId, track.id, quality)
        } else {
            null
        }
        return cached?.path?.toUri()?.toString()
            ?: provider.streamUrl(
                StreamRequest(
                    trackId = track.id,
                    quality = quality,
                ),
            )
    }

    private fun startAudioPrefetch(scope: CoroutineScope, activeSessionId: Int) {
        val audioCache = cache ?: return
        val sourceId = sourceIdProvider() ?: return
        val currentProvider = provider ?: return
        val currentQuality = streamQuality ?: return
        val upcoming = queue.upNext().take(AudioPrefetchDepth)
        if (upcoming.isEmpty()) return

        audioPrefetchJob?.cancel()
        audioPrefetchJob = scope.launch {
            upcoming.forEach { track ->
                if (activeSessionId != sessionId) return@launch
                runCatching {
                    audioCache.cacheAudioTrack(
                        sourceId = sourceId,
                        provider = currentProvider,
                        track = track,
                        quality = currentQuality,
                    )
                }
            }
        }
    }

    private fun handlePlaybackState(
        scope: CoroutineScope,
        state: PlaybackState,
        activeSessionId: Int,
    ) {
        if (activeSessionId != sessionId) return

        if (state == PlaybackState.Finished && queue.hasNext()) {
            playQueueIndex(scope, queue.tracks, queue.currentIndex + 1, activeSessionId)
        } else {
            callbacks?.onPlaybackStateChanged(state)
        }
    }

    private fun prepareNextIfNeeded(
        scope: CoroutineScope,
        progress: PlaybackProgress,
        activeSessionId: Int,
    ) {
        val queueAwareEngine = playbackEngine as? QueueAwarePlaybackEngine ?: return
        if (!crossfadeSettings.isActive || !playbackEngine.supportsCrossfade || !queue.hasNext()) return
        val position = progress.positionSeconds ?: return
        val duration = progress.durationSeconds ?: return
        val prepareWindowSeconds = maxOf(
            crossfadeSettings.durationSeconds + 1.0,
            CrossfadePrepareWindowSeconds,
        )
        if (duration - position > prepareWindowSeconds) return

        val currentProvider = provider ?: return
        val currentQuality = streamQuality ?: return
        val nextIndex = queue.currentIndex + 1
        if (preparedNextIndex == nextIndex) return
        preparedNextIndex = nextIndex
        val nextTrack = queue.tracks.getOrNull(nextIndex) ?: return

        scope.launch {
            if (activeSessionId != sessionId) return@launch
            try {
                val streamUrl = playbackUrl(currentProvider, nextTrack, currentQuality)
                if (activeSessionId == sessionId) {
                    queueAwareEngine.prepareNext(
                        PlaybackRequest(
                            url = streamUrl,
                            mediaId = nextTrack.id.value,
                            replayGainMode = replayGainMode.forEngine(playbackEngine),
                        ),
                    )
                }
            } catch (_: Exception) {
                preparedNextIndex = null
            }
        }
    }
}

data class PlaylistCallbacks(
    val onTrackStarted: (Track, String?) -> Unit,
    val onQueueChanged: (PlaybackQueue) -> Unit,
    val onPlaybackStateChanged: (PlaybackState) -> Unit,
    val onPlaybackProgressChanged: (PlaybackProgress) -> Unit,
)

data class PlaybackQueue(
    val tracks: List<Track> = emptyList(),
    val currentIndex: Int = -1,
) {
    fun upNext(): List<Track> =
        tracks.drop(currentIndex + 1)

    fun hasNext(): Boolean =
        currentIndex + 1 < tracks.size

    fun hasPrevious(): Boolean =
        currentIndex > 0
}

private fun ReplayGainMode.forEngine(playbackEngine: PlaybackEngine): ReplayGainMode =
    if (playbackEngine.supportsReplayGain) this else ReplayGainMode.Off

private const val CrossfadePrepareWindowSeconds = 30.0
private const val AudioPrefetchDepth = 10
