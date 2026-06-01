package app.naviamp.desktop.playback

import app.naviamp.desktop.AudioTagReader
import app.naviamp.desktop.AudioTag
import app.naviamp.desktop.DesktopCache
import app.naviamp.domain.ReplayGain
import app.naviamp.domain.audio.lyricsFromAudioTags
import app.naviamp.domain.audio.replayGainFromAudioTags
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.playback.CrossfadeSettings
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackQueueSelection
import app.naviamp.domain.playback.PlaybackReplayGain
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.ReplayGainSource
import app.naviamp.domain.playback.SidecarTypeEmbeddedLyrics
import app.naviamp.domain.playback.SidecarTypeLrclibLyrics
import app.naviamp.domain.playback.SidecarTypeProviderLyrics
import app.naviamp.domain.playback.SidecarTypeWaveform
import app.naviamp.domain.playback.resolvePlaybackAudioSource
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
    private var currentTrackSidecarJob: Job? = null
    private var audioPrefetchJob: Job? = null
    private val queueController = PlaybackQueueController()
    private var playbackSource: PlaybackSource = PlaybackSource.Unknown
    private var audioPrefetchStats = AudioPrefetchStats()

    val queue: PlaybackQueue
        get() = queueController.queue

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
        currentTrackSidecarJob?.cancel()
        currentTrackSidecarJob = null
        audioPrefetchJob?.cancel()
        audioPrefetchJob = null
        audioPrefetchStats = AudioPrefetchStats()

        queueController.start(tracks, index)?.let { selection ->
            playQueueSelection(scope, selection)
        }
    }

    fun restore(
        provider: MediaProvider,
        tracks: List<Track>,
        index: Int,
        quality: StreamQuality,
        replayGainMode: ReplayGainMode,
        callbacks: PlaylistCallbacks,
        initialProgress: PlaybackProgress = PlaybackProgress.Unknown,
    ) {
        if (!queueController.restore(tracks, index)) return
        this.provider = provider
        this.streamQuality = quality
        this.replayGainMode = replayGainMode
        this.callbacks = callbacks
        currentTrackSidecarJob?.cancel()
        currentTrackSidecarJob = null
        audioPrefetchJob?.cancel()
        audioPrefetchJob = null
        audioPrefetchStats = AudioPrefetchStats()
        callbacks.onQueueChanged(queue)
        callbacks.onPlaybackProgressChanged(initialProgress)
        callbacks.onPlaybackStateChanged(PlaybackState.Idle)
    }

    fun clear() {
        queueController.clear()
        currentTrackSidecarJob?.cancel()
        currentTrackSidecarJob = null
        audioPrefetchJob?.cancel()
        audioPrefetchJob = null
        audioPrefetchStats = AudioPrefetchStats()
        playbackSource = PlaybackSource.Unknown
        playbackEngine.stop()
        callbacks?.onQueueChanged(queue)
    }

    fun cancelAudioPrefetch() {
        audioPrefetchJob?.cancel()
        audioPrefetchJob = null
        audioPrefetchStats = audioPrefetchStats.copy(running = false)
    }

    fun updateTrack(updatedTrack: Track) {
        queueController.updateTrack(updatedTrack)?.let { updatedQueue ->
            callbacks?.onQueueChanged(updatedQueue)
        }
    }

    fun appendTracks(
        tracks: List<Track>,
        maxHistory: Int? = null,
    ) {
        queueController.appendTracks(tracks, maxHistory)?.let { updatedQueue ->
            callbacks?.onQueueChanged(updatedQueue)
        }
    }

    fun replaceUpcomingTracks(
        currentTrack: Track,
        upcomingTracks: List<Track>,
        maxHistory: Int? = null,
    ) {
        callbacks?.onQueueChanged(
            queueController.replaceUpcomingTracks(currentTrack, upcomingTracks, maxHistory),
        )
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
        queueController.setRepeatMode(mode)
    }

    fun next(scope: CoroutineScope) {
        queueController.next()?.let { selection ->
            playQueueSelection(scope, selection)
        }
    }

    fun playCurrent(scope: CoroutineScope) {
        queueController.playCurrent()?.let { selection ->
            playQueueSelection(scope, selection)
        }
    }

    fun playCurrent(scope: CoroutineScope, startPositionSeconds: Double?) {
        queueController.playCurrent()?.let { selection ->
            playQueueSelection(scope, selection, startPositionSeconds)
        }
    }

    fun jumpTo(
        scope: CoroutineScope,
        index: Int,
        moveSelectedToCurrent: Boolean = true,
    ) {
        queueController.jumpTo(index, moveSelectedToCurrent)?.let { selection ->
            playQueueSelection(scope, selection)
        }
    }

    fun previous(scope: CoroutineScope) {
        queueController.previous()?.let { selection ->
            playQueueSelection(scope, selection)
        }
    }

    fun toggleUpcomingShuffle(shuffledSnapshot: List<Track>?): List<Track>? {
        val result = queueController.toggleUpcomingShuffle(shuffledSnapshot) ?: return shuffledSnapshot
        callbacks?.onQueueChanged(result.queue)
        return result.shuffledSnapshot
    }

    private fun playQueueSelection(
        scope: CoroutineScope,
        selection: PlaybackQueueSelection,
        startPositionSeconds: Double? = null,
    ) {
        val track = selection.track ?: return
        val activeSessionId = selection.sessionId
        val currentProvider = provider ?: return
        val currentQuality = streamQuality ?: return
        val currentCallbacks = callbacks ?: return
        currentTrackSidecarJob?.cancel()
        currentTrackSidecarJob = null
        audioPrefetchJob?.cancel()
        audioPrefetchJob = null

        currentCallbacks.onQueueChanged(queue)
        currentCallbacks.onPlaybackProgressChanged(PlaybackProgress.Unknown)

        scope.launch {
            try {
                val playbackTarget = playbackTarget(currentProvider, track, currentQuality, startPositionSeconds)
                playbackSource = playbackTarget.source
                val replayGain = replayGainForTrack(track, currentQuality)
                val coverArtUrl = track.coverArtId?.let { currentProvider.coverArtUrl(it) }
                currentCallbacks.onTrackStarted(track, coverArtUrl)
                startCurrentTrackSidecars(scope, activeSessionId, track)
                startAudioPrefetch(scope, activeSessionId)
                playbackEngine.play(
                    scope = scope,
                    request = PlaybackRequest(
                        url = playbackTarget.url,
                        mediaId = track.id.value,
                        replayGainMode = replayGainMode.forEngine(playbackEngine),
                        replayGain = replayGain,
                        startPositionSeconds = startPositionSeconds?.takeIf { it > 0.0 },
                    ),
                    onStateChanged = { state ->
                        scope.launch {
                            handlePlaybackState(scope, state, activeSessionId)
                        }
                    },
                    onProgressChanged = { progress ->
                        scope.launch {
                            if (activeSessionId == queueController.playbackSessionId) {
                                callbacks?.onPlaybackProgressChanged(progress)
                                prepareNextIfNeeded(scope, progress, activeSessionId)
                            }
                        }
                    },
                    onMetadataChanged = { metadata ->
                        scope.launch {
                            if (activeSessionId == queueController.playbackSessionId) {
                                callbacks?.onMetadataChanged(metadata)
                            }
                        }
                    },
                )
            } catch (exception: Exception) {
                if (activeSessionId == queueController.playbackSessionId) {
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
        startPositionSeconds: Double? = null,
    ): PlaybackTarget {
        val plan = resolvePlaybackAudioSource(
            sourceId = sourceIdProvider(),
            track = track,
            quality = quality,
            audioCachingEnabled = audioCachingEnabledProvider(),
            startPositionSeconds = startPositionSeconds,
            downloadedAudio = { sourceId, trackId, requestedQuality ->
                cache?.downloadedAudioFile(sourceId, trackId, requestedQuality)?.path
            },
            cachedAudio = { sourceId, trackId, requestedQuality ->
                cache?.cachedAudioFile(sourceId, trackId, requestedQuality)?.path
            },
        )
        plan.localAudio?.let { path ->
            return PlaybackTarget(
                url = path.toUri().toString(),
                source = plan.source,
            )
        }
        return PlaybackTarget(
            url = provider.streamUrl(plan.target.providerStreamRequest),
            source = plan.source,
        )
    }

    private fun startCurrentTrackSidecars(
        scope: CoroutineScope,
        activeSessionId: Int,
        track: Track,
    ) {
        if (!audioCachingEnabledProvider()) return
        val audioCache = cache ?: return
        val sourceId = sourceIdProvider() ?: return
        val currentProvider = provider ?: return
        val currentQuality = streamQuality ?: return

        currentTrackSidecarJob?.cancel()
        currentTrackSidecarJob = scope.launch {
            runCatching {
                val cachedAudio = audioCache.cacheAudioTrack(
                    sourceId = sourceId,
                    provider = currentProvider,
                    track = track,
                    quality = currentQuality,
                )
                if (activeSessionId == queueController.playbackSessionId) {
                    runWaveformSidecar(
                        audioCache = audioCache,
                        sourceId = sourceId,
                        track = track,
                        quality = currentQuality,
                    )
                    if (activeSessionId == queueController.playbackSessionId) {
                        callbacks?.onCurrentTrackSidecarsReady(track)
                    }
                    runMetadataSidecars(
                        audioCache = audioCache,
                        sourceId = sourceId,
                        provider = currentProvider,
                        track = track,
                        cachedAudio = cachedAudio,
                    )
                }
            }
        }
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
            sidecarCompleted = 0,
            sidecarFailed = 0,
            lastError = null,
            lastSidecarError = null,
        )
        audioPrefetchJob = scope.launch {
            upcoming.forEach { track ->
                if (activeSessionId != queueController.playbackSessionId) return@launch
                var sidecarResult = SidecarPrepResult()
                val result = runCatching {
                    val cachedAudio = audioCache.cacheAudioTrack(
                        sourceId = sourceId,
                        provider = currentProvider,
                        track = track,
                        quality = currentQuality,
                    )
                    sidecarResult = runPrefetchSidecars(
                        audioCache = audioCache,
                        sourceId = sourceId,
                        provider = currentProvider,
                        track = track,
                        quality = currentQuality,
                        cachedAudio = cachedAudio,
                    )
                }
                audioPrefetchStats = if (result.isSuccess) {
                    audioPrefetchStats.copy(
                        completed = audioPrefetchStats.completed + 1,
                        sidecarCompleted = audioPrefetchStats.sidecarCompleted + if (sidecarResult.failed == 0) 1 else 0,
                        sidecarFailed = audioPrefetchStats.sidecarFailed + if (sidecarResult.failed > 0) 1 else 0,
                        lastSidecarError = sidecarResult.lastError ?: audioPrefetchStats.lastSidecarError,
                    )
                } else {
                    audioPrefetchStats.copy(
                        failed = audioPrefetchStats.failed + 1,
                        lastError = result.exceptionOrNull()?.message,
                    )
                }
            }
            if (activeSessionId == queueController.playbackSessionId) {
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
    ): SidecarPrepResult {
        var failed = 0
        var lastError: String? = null

        fun errorMessage(error: Throwable): String =
            error.message ?: error::class.simpleName ?: "Sidecar prep failed."

        suspend fun runSidecar(sidecarType: String, block: suspend () -> Unit) {
            runCatching { block() }
                .onSuccess {
                    audioCache.recordSidecarStatus(
                        sourceId = sourceId,
                        trackId = track.id,
                        quality = quality,
                        sidecarType = sidecarType,
                        success = true,
                    )
                }
                .onFailure { error ->
                    val message = errorMessage(error)
                    audioCache.recordSidecarStatus(
                        sourceId = sourceId,
                        trackId = track.id,
                        quality = quality,
                        sidecarType = sidecarType,
                        success = false,
                        errorMessage = message,
                    )
                    failed += 1
                    lastError = message
                }
        }

        runSidecar(SidecarTypeWaveform) {
            audioCache.ensureAudioWaveform(
                sourceId = sourceId,
                trackId = track.id,
                quality = quality,
            )
        }
        runSidecar(SidecarTypeProviderLyrics) {
            audioCache.providerLyrics(sourceId, provider, track.id)
        }
        runSidecar(SidecarTypeEmbeddedLyrics) {
            val embeddedLyrics = AudioTagReader().read(cachedAudio.path)
                .let(::lyricsFromAudioTags)
            if (embeddedLyrics != null) {
                audioCache.cacheEmbeddedLyrics(sourceId, track.id, embeddedLyrics)
            }
        }
        runSidecar(SidecarTypeLrclibLyrics) {
            audioCache.lrclibLyrics(sourceId, track)
        }

        return SidecarPrepResult(failed = failed, lastError = lastError)
    }

    private suspend fun runWaveformSidecar(
        audioCache: DesktopCache,
        sourceId: String,
        track: Track,
        quality: StreamQuality,
    ) {
        audioCache.ensureAudioWaveform(
            sourceId = sourceId,
            trackId = track.id,
            quality = quality,
        )
        audioCache.recordSidecarStatus(
            sourceId = sourceId,
            trackId = track.id,
            quality = quality,
            sidecarType = SidecarTypeWaveform,
            success = true,
        )
    }

    private suspend fun runMetadataSidecars(
        audioCache: DesktopCache,
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        cachedAudio: app.naviamp.desktop.CachedAudioFile,
    ) {
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
        if (activeSessionId != queueController.playbackSessionId) return

        if (state == PlaybackState.Finished) {
            val selection = queueController.finishedSelection()
            if (selection != null) {
                playQueueSelection(scope, selection)
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
        val nextIndex = queueController.nextGaplessQueueIndex() ?: return
        if (!canPrepareForCrossfade && !canPrepareForGapless) return
        val position = progress.positionSeconds ?: return
        val duration = progress.durationSeconds ?: return
        val prepareWindowSeconds = if (canPrepareForCrossfade) {
            crossfadeSettings.durationSeconds.toDouble()
        } else {
            GaplessPrepareWindowSeconds
        }
        if (duration - position > prepareWindowSeconds) return

        val currentProvider = provider ?: return
        val currentQuality = streamQuality ?: return
        if (!queueController.shouldPrepareNext(nextIndex)) return
        queueController.markPreparedNext(nextIndex)
        val nextTrack = queue.tracks.getOrNull(nextIndex) ?: return

        scope.launch {
            if (activeSessionId != queueController.playbackSessionId) return@launch
            try {
                val streamUrl = playbackTarget(currentProvider, nextTrack, currentQuality).url
                val replayGain = replayGainForTrack(nextTrack, currentQuality)
                if (activeSessionId == queueController.playbackSessionId) {
                    val request = PlaybackRequest(
                        url = streamUrl,
                        mediaId = nextTrack.id.value,
                        replayGainMode = replayGainMode.forEngine(playbackEngine),
                        replayGain = replayGain,
                    )
                    withContext(Dispatchers.IO) {
                        queueAwareEngine.prepareNext(request)
                    }
                }
            } catch (_: Exception) {
                queueController.clearPreparedNext()
            }
        }
    }

    private suspend fun replayGainForTrack(
        track: Track,
        quality: StreamQuality,
    ): PlaybackReplayGain? {
        track.replayGain?.takeIf { it.hasAnyValue() }?.let { replayGain ->
            return PlaybackReplayGain(replayGain, ReplayGainSource.Provider)
        }

        val audioCache = cache ?: return null
        val sourceId = sourceIdProvider() ?: return null
        val audioPath = audioCache.downloadedAudioFile(sourceId, track.id, quality)?.path
            ?: audioCache.cachedAudioFile(sourceId, track.id, quality)?.path
            ?: return null
        val replayGain = withContext(Dispatchers.IO) {
            replayGainFromAudioTags(AudioTagReader().read(audioPath))
        } ?: return null
        return PlaybackReplayGain(replayGain, ReplayGainSource.LocalTags)
    }
}

data class CacheRuntimeStats(
    val playbackSource: PlaybackSource = PlaybackSource.Unknown,
    val prefetch: AudioPrefetchStats = AudioPrefetchStats(),
)

data class AudioPrefetchStats(
    val enabled: Boolean = false,
    val configuredDepth: Int = 0,
    val running: Boolean = false,
    val queued: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val sidecarCompleted: Int = 0,
    val sidecarFailed: Int = 0,
    val lastError: String? = null,
    val lastSidecarError: String? = null,
)

private data class SidecarPrepResult(
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
    val onCurrentTrackSidecarsReady: (Track) -> Unit = {},
)

private fun ReplayGainMode.forEngine(playbackEngine: PlaybackEngine): ReplayGainMode =
    if (playbackEngine.supportsReplayGain) this else ReplayGainMode.Off

private fun ReplayGain.hasAnyValue(): Boolean =
    trackGainDb != null || albumGainDb != null || trackPeak != null || albumPeak != null

private const val GaplessPrepareWindowSeconds = 8.0
private const val DefaultAudioPrefetchDepth = 10
