package app.naviamp.desktop.playback

import app.naviamp.desktop.AudioTagReader
import app.naviamp.desktop.DesktopCache
import app.naviamp.desktop.lyricsFromAudioTags
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.playback.CrossfadeSettings
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistEngine(
    private val playbackEngine: PlaybackEngine,
    private val cache: DesktopCache? = null,
    private val sourceIdProvider: () -> String? = { null },
    private val audioCachingEnabledProvider: () -> Boolean = { true },
    private val audioPrefetchDepthProvider: () -> Int = { DefaultAudioPrefetchDepth },
) {
    private var provider: MediaProvider? = null
    private var streamQuality: StreamQuality? = null
    private var replayGainMode: ReplayGainMode = ReplayGainMode.Off
    private var callbacks: PlaylistCallbacks? = null
    private var crossfadeSettings = CrossfadeSettings()
    private var gaplessEnabled = true
    private var repeatMode = RepeatMode.Off
    private var preparedNextIndex: Int? = null
    private var audioPrefetchJob: Job? = null
    private var sessionId = 0
    private var playbackSource: PlaybackSource = PlaybackSource.Unknown
    private var audioPrefetchStats = AudioPrefetchStats()

    var queue: PlaybackQueue = PlaybackQueue()
        private set

    fun cacheRuntimeStats(): CacheRuntimeStats =
        CacheRuntimeStats(
            playbackSource = playbackSource,
            prefetch = audioPrefetchStats,
        )

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
        audioPrefetchStats = AudioPrefetchStats()

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
        audioPrefetchStats = AudioPrefetchStats()
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
        audioPrefetchStats = AudioPrefetchStats()
        playbackSource = PlaybackSource.Unknown
        queue = PlaybackQueue()
        playbackEngine.stop()
        callbacks?.onQueueChanged(queue)
    }

    fun cancelAudioPrefetch() {
        audioPrefetchJob?.cancel()
        audioPrefetchJob = null
        audioPrefetchStats = audioPrefetchStats.copy(running = false)
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

        queue = queue.appendTracks(tracks, maxHistory)
        callbacks?.onQueueChanged(queue)
    }

    fun replaceUpcomingTracks(
        currentTrack: Track,
        upcomingTracks: List<Track>,
        maxHistory: Int? = null,
    ) {
        queue = queue.replaceUpcomingTracks(currentTrack, upcomingTracks, maxHistory)
        callbacks?.onQueueChanged(queue)
    }

    fun setPlaybackTransitionSettings(
        gaplessEnabled: Boolean,
        crossfadeSettings: CrossfadeSettings,
    ) {
        this.gaplessEnabled = gaplessEnabled
        this.crossfadeSettings = crossfadeSettings
        (playbackEngine as? QueueAwarePlaybackEngine)?.setCrossfadeDuration(crossfadeSettings.durationSeconds)
    }

    fun setCrossfadeSettings(settings: CrossfadeSettings) {
        setPlaybackTransitionSettings(gaplessEnabled = settings.durationSeconds <= 0, crossfadeSettings = settings)
    }

    fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
    }

    fun next(scope: CoroutineScope) {
        val nextIndex = when {
            queue.hasNext() -> queue.currentIndex + 1
            repeatMode == RepeatMode.Queue && queue.tracks.isNotEmpty() -> 0
            else -> return
        }
        sessionId += 1
        playQueueIndex(scope, queue.tracks, nextIndex, sessionId)
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

    fun jumpTo(
        scope: CoroutineScope,
        index: Int,
        moveSelectedToCurrent: Boolean = true,
    ) {
        if (index !in queue.tracks.indices || index == queue.currentIndex) return
        sessionId += 1
        val nextQueue = queue.jumpTo(index, moveSelectedToCurrent)
        playQueueIndex(scope, nextQueue.tracks, nextQueue.currentIndex, sessionId)
    }

    fun previous(scope: CoroutineScope) {
        if (!queue.hasPrevious()) return
        sessionId += 1
        playQueueIndex(scope, queue.tracks, queue.currentIndex - 1, sessionId)
    }

    fun shuffleUpcoming(): List<Track>? {
        val originalUpcoming = queue.upNext()
        if (originalUpcoming.size < 2 || queue.currentIndex !in queue.tracks.indices) return null
        queue = queue.shuffleUpcoming()?.first ?: return null
        preparedNextIndex = null
        callbacks?.onQueueChanged(queue)
        return originalUpcoming
    }

    fun restoreUpcoming(tracks: List<Track>) {
        if (queue.currentIndex !in queue.tracks.indices) return
        queue = queue.restoreUpcoming(tracks)
        preparedNextIndex = null
        callbacks?.onQueueChanged(queue)
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
                val playbackTarget = playbackTarget(currentProvider, track, currentQuality)
                playbackSource = playbackTarget.source
                val coverArtUrl = track.coverArtId?.let { currentProvider.coverArtUrl(it) }
                currentCallbacks.onTrackStarted(track, coverArtUrl)
                startAudioPrefetch(scope, activeSessionId)
                playbackEngine.play(
                    scope = scope,
                    request = PlaybackRequest(
                        url = playbackTarget.url,
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
                    onMetadataChanged = { metadata ->
                        scope.launch {
                            if (activeSessionId == sessionId) {
                                callbacks?.onMetadataChanged(metadata)
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

    private suspend fun playbackTarget(
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
    ): PlaybackTarget {
        val sourceId = sourceIdProvider()
        val downloaded = if (sourceId != null) {
            cache?.downloadedAudioFile(sourceId, track.id, quality)
        } else {
            null
        }
        if (downloaded != null) {
            return PlaybackTarget(
                url = downloaded.path.toUri().toString(),
                source = PlaybackSource.DownloadedFile,
            )
        }

        val cached = if (sourceId != null && audioCachingEnabledProvider()) {
            cache?.cachedAudioFile(sourceId, track.id, quality)
        } else {
            null
        }
        if (cached != null) {
            return PlaybackTarget(
                url = cached.path.toUri().toString(),
                source = PlaybackSource.CachedFile,
            )
        }

        return PlaybackTarget(
            url = provider.streamUrl(
                StreamRequest(
                    trackId = track.id,
                    quality = quality,
                ),
            ),
            source = if (audioCachingEnabledProvider()) PlaybackSource.ProviderStream else PlaybackSource.ProviderStreamCacheDisabled,
        )
    }

    private fun startAudioPrefetch(scope: CoroutineScope, activeSessionId: Int) {
        audioPrefetchStats = AudioPrefetchStats(
            enabled = audioCachingEnabledProvider(),
            configuredDepth = audioPrefetchDepthProvider().coerceIn(0, 25),
        )
        if (!audioCachingEnabledProvider()) return
        val audioCache = cache ?: return
        val sourceId = sourceIdProvider() ?: return
        val currentProvider = provider ?: return
        val currentQuality = streamQuality ?: return
        val prefetchDepth = audioPrefetchDepthProvider().coerceIn(0, 25)
        if (prefetchDepth <= 0) return
        val upcoming = queue.upNext().take(prefetchDepth)
        if (upcoming.isEmpty()) return

        audioPrefetchJob?.cancel()
        audioPrefetchStats = audioPrefetchStats.copy(
            running = true,
            queued = upcoming.size,
            completed = 0,
            failed = 0,
            lastError = null,
        )
        audioPrefetchJob = scope.launch {
            upcoming.forEach { track ->
                if (activeSessionId != sessionId) return@launch
                val result = runCatching {
                    val cachedAudio = audioCache.cacheAudioTrack(
                        sourceId = sourceId,
                        provider = currentProvider,
                        track = track,
                        quality = currentQuality,
                    )
                    runPrefetchSidecars(
                        audioCache = audioCache,
                        sourceId = sourceId,
                        provider = currentProvider,
                        track = track,
                        quality = currentQuality,
                        cachedAudio = cachedAudio,
                    )
                }
                audioPrefetchStats = if (result.isSuccess) {
                    audioPrefetchStats.copy(completed = audioPrefetchStats.completed + 1)
                } else {
                    audioPrefetchStats.copy(
                        failed = audioPrefetchStats.failed + 1,
                        lastError = result.exceptionOrNull()?.message,
                    )
                }
            }
            if (activeSessionId == sessionId) {
                audioPrefetchStats = audioPrefetchStats.copy(running = false)
            }
        }
    }

    private suspend fun runPrefetchSidecars(
        audioCache: DesktopCache,
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        cachedAudio: app.naviamp.desktop.CachedAudioFile,
    ) {
        runCatching {
            audioCache.ensureAudioWaveform(
                sourceId = sourceId,
                trackId = track.id,
                quality = quality,
            )
        }
        runCatching {
            audioCache.providerLyrics(sourceId, provider, track.id)
        }
        runCatching {
            val embeddedLyrics = AudioTagReader().read(cachedAudio.path)
                .let(::lyricsFromAudioTags)
            if (embeddedLyrics != null) {
                audioCache.cacheEmbeddedLyrics(sourceId, track.id, embeddedLyrics)
            }
        }
        runCatching {
            audioCache.lrclibLyrics(sourceId, track)
        }
    }

    private fun handlePlaybackState(
        scope: CoroutineScope,
        state: PlaybackState,
        activeSessionId: Int,
    ) {
        if (activeSessionId != sessionId) return

        if (state == PlaybackState.Finished) {
            val nextIndex = when {
                repeatMode == RepeatMode.Track && queue.currentIndex in queue.tracks.indices -> queue.currentIndex
                queue.hasNext() -> queue.currentIndex + 1
                repeatMode == RepeatMode.Queue && queue.tracks.isNotEmpty() -> 0
                else -> null
            }
            if (nextIndex != null) {
                playQueueIndex(scope, queue.tracks, nextIndex, activeSessionId)
                return
            }
            callbacks?.onPlaybackStateChanged(state)
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
        val canPrepareForCrossfade = crossfadeSettings.isActive && playbackEngine.supportsCrossfade
        val canPrepareForGapless = gaplessEnabled && playbackEngine.supportsGapless
        val nextIndex = nextGaplessQueueIndex() ?: return
        if (!canPrepareForCrossfade && !canPrepareForGapless) return
        val position = progress.positionSeconds ?: return
        val duration = progress.durationSeconds ?: return
        val prepareWindowSeconds = if (canPrepareForCrossfade) {
            maxOf(
                crossfadeSettings.durationSeconds + 1.0,
                CrossfadePrepareWindowSeconds,
            )
        } else {
            GaplessPrepareWindowSeconds
        }
        if (duration - position > prepareWindowSeconds) return

        val currentProvider = provider ?: return
        val currentQuality = streamQuality ?: return
        if (preparedNextIndex == nextIndex) return
        preparedNextIndex = nextIndex
        val nextTrack = queue.tracks.getOrNull(nextIndex) ?: return

        scope.launch {
            if (activeSessionId != sessionId) return@launch
            try {
                val streamUrl = playbackTarget(currentProvider, nextTrack, currentQuality).url
                if (activeSessionId == sessionId) {
                    val request = PlaybackRequest(
                        url = streamUrl,
                        mediaId = nextTrack.id.value,
                        replayGainMode = replayGainMode.forEngine(playbackEngine),
                    )
                    withContext(Dispatchers.IO) {
                        queueAwareEngine.prepareNext(request)
                    }
                }
            } catch (_: Exception) {
                preparedNextIndex = null
            }
        }
    }

    private fun nextGaplessQueueIndex(): Int? =
        when {
            repeatMode == RepeatMode.Track && queue.currentIndex in queue.tracks.indices -> queue.currentIndex
            queue.hasNext() -> queue.currentIndex + 1
            repeatMode == RepeatMode.Queue && queue.currentIndex in queue.tracks.indices && queue.tracks.isNotEmpty() -> 0
            else -> null
        }
}

data class CacheRuntimeStats(
    val playbackSource: PlaybackSource = PlaybackSource.Unknown,
    val prefetch: AudioPrefetchStats = AudioPrefetchStats(),
)

enum class PlaybackSource(val label: String) {
    Unknown("Unknown"),
    DownloadedFile("Downloaded file"),
    CachedFile("Cached file"),
    ProviderStream("Provider stream"),
    ProviderStreamCacheDisabled("Provider stream (cache disabled)"),
}

data class AudioPrefetchStats(
    val enabled: Boolean = false,
    val configuredDepth: Int = 0,
    val running: Boolean = false,
    val queued: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val lastError: String? = null,
)

private data class PlaybackTarget(
    val url: String,
    val source: PlaybackSource,
)

data class PlaylistCallbacks(
    val onTrackStarted: (Track, String?) -> Unit,
    val onQueueChanged: (PlaybackQueue) -> Unit,
    val onPlaybackStateChanged: (PlaybackState) -> Unit,
    val onPlaybackProgressChanged: (PlaybackProgress) -> Unit,
    val onMetadataChanged: (app.naviamp.domain.playback.PlaybackStreamMetadata) -> Unit = {},
)

private fun ReplayGainMode.forEngine(playbackEngine: PlaybackEngine): ReplayGainMode =
    if (playbackEngine.supportsReplayGain) this else ReplayGainMode.Off

private const val CrossfadePrepareWindowSeconds = 30.0
private const val GaplessPrepareWindowSeconds = 8.0
private const val DefaultAudioPrefetchDepth = 10
