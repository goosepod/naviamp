package app.naviamp.desktop.playback

import app.naviamp.desktop.CachedAudioFile
import app.naviamp.desktop.CachedAudioMetadata
import app.naviamp.desktop.toPlaybackLocalAudio
import app.naviamp.domain.ReplayGain
import app.naviamp.domain.audio.AudioMetadataSidecarService
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.cache.AudioCacheRepository
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.playback.CrossfadeSettings
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackQueueManager
import app.naviamp.domain.playback.PlaybackQueueSelection
import app.naviamp.domain.playback.PlaybackReplayGain
import app.naviamp.domain.playback.AudioPrefetchStats
import app.naviamp.domain.playback.CacheRuntimeStats
import app.naviamp.domain.playback.DefaultAudioPrefetchDepth
import app.naviamp.domain.playback.PlaybackSidecarPrepResult
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PreparedNextPlaybackCoordinator
import app.naviamp.domain.playback.PreparedNextPlaybackSettings
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.ReplayGainSource
import app.naviamp.domain.playback.PlaybackSidecarService
import app.naviamp.domain.playback.currentTrackSidecarWork
import app.naviamp.domain.playback.emptyPlaybackAudioAssetRepository
import app.naviamp.domain.playback.finished
import app.naviamp.domain.playback.initialAudioPrefetchStats
import app.naviamp.domain.playback.planPlaylistTrackStartWork
import app.naviamp.domain.playback.planAudioPrefetchWork
import app.naviamp.domain.playback.playbackStreamUrl
import app.naviamp.domain.playback.resolvePlaybackAudioSource
import app.naviamp.domain.playback.runAudioPrefetch
import app.naviamp.domain.playback.runCurrentTrackSidecars
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DesktopPlaylistEngine(
    private val playbackEngine: PlaybackEngine,
    private val sourceIdProvider: () -> String? = { null },
    private val audioCachingEnabledProvider: () -> Boolean = { true },
    private val audioPrefetchDepthProvider: () -> Int = { DefaultAudioPrefetchDepth },
    private val audioCacheRepository: AudioCacheRepository<CachedAudioFile, CachedAudioMetadata>? = null,
    private val sidecarService: PlaybackSidecarService? = null,
    private val audioMetadataSidecarService: AudioMetadataSidecarService? = null,
    playbackAudioAssets: PlaybackAudioAssetRepository? = null,
) {
    private val playbackAudioAssets = playbackAudioAssets ?: emptyPlaybackAudioAssetRepository()
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
    private val preparedNextPlaybackCoordinator = PreparedNextPlaybackCoordinator(
        provider = { provider },
        sourceId = sourceIdProvider,
        quality = { streamQuality },
        audioCachingEnabled = audioCachingEnabledProvider,
        audioAssets = this.playbackAudioAssets,
        replayGainMode = { replayGainMode },
        supportsReplayGain = { playbackEngine.supportsReplayGain },
        replayGainForTrack = ::replayGainForTrack,
    )

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
        audioPrefetchStats = audioPrefetchStats.finished()
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

    fun replaceQueue(
        queue: PlaybackQueue,
        incrementSession: Boolean = false,
        clearPreparedNext: Boolean = true,
    ) {
        queueController.replaceQueue(
            queue = queue,
            incrementSession = incrementSession,
            clearPreparedNext = clearPreparedNext,
        )
        callbacks?.onQueueChanged(queue)
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
        val update = PlaybackQueueManager().toggleUpcomingShuffle(queueController.queue, shuffledSnapshot)
        if (!update.changed) return shuffledSnapshot
        queueController.replaceQueue(update.queue)
        callbacks?.onQueueChanged(update.queue)
        return update.shuffledSnapshot
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
                val replayGain = replayGainForTrack(track, currentQuality)
                val coverArtUrl = track.coverArtId?.let { currentProvider.coverArtUrl(it) }
                val trackStartWork = planPlaylistTrackStartWork(
                    sessionId = activeSessionId,
                    track = track,
                    playbackSource = playbackTarget.source,
                    streamUrl = playbackTarget.url,
                    replayGainMode = replayGainMode,
                    replayGain = replayGain,
                    supportsReplayGain = playbackEngine.supportsReplayGain,
                    engineStartPositionSeconds = playbackTarget.engineStartPositionSeconds,
                    coverArtUrl = coverArtUrl,
                )
                playbackSource = trackStartWork.playbackSource
                currentCallbacks.onTrackStarted(trackStartWork.track, trackStartWork.coverArtUrl)
                if (trackStartWork.startSidecarPrep) startCurrentTrackSidecars(scope, activeSessionId, trackStartWork.track)
                if (trackStartWork.startAudioPrefetch) startAudioPrefetch(scope, activeSessionId)
                playbackEngine.play(
                    scope = scope,
                    request = trackStartWork.request,
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
            audioAssets = playbackAudioAssets,
        )
        return PlaybackTarget(
            url = plan.playbackStreamUrl(
                providerStreamUrl = { target -> provider.streamUrl(target.providerStreamRequest) },
            ),
            source = plan.source,
            engineStartPositionSeconds = plan.target.engineStartPositionSeconds,
        )
    }

    private fun startCurrentTrackSidecars(
        scope: CoroutineScope,
        activeSessionId: Int,
        track: Track,
    ) {
        if (!audioCachingEnabledProvider()) return
        val audioCache = audioCacheRepository ?: return
        val sidecars = sidecarService ?: return
        val work = currentTrackSidecarWork(
            sourceId = sourceIdProvider(),
            provider = provider,
            track = track,
            quality = streamQuality,
            audioCachingEnabled = audioCachingEnabledProvider(),
            onlineLyricsEnabled = true,
            lyricsVisible = false,
        ) ?: return
        val sourceId = work.sourceId ?: return

        currentTrackSidecarJob?.cancel()
        currentTrackSidecarJob = scope.launch {
            runCurrentTrackSidecars(
                work = work,
                isActive = { activeSessionId == queueController.playbackSessionId },
                prepareAudio = { sidecarWork ->
                    audioCache.cacheAudioTrack(
                        sourceId = sourceId,
                        provider = sidecarWork.provider,
                        track = sidecarWork.track,
                        quality = sidecarWork.quality,
                    )
                },
                prepareWaveform = { sidecarWork ->
                    sidecars.prepareWaveform(
                        sourceId = sourceId,
                        provider = sidecarWork.provider,
                        track = sidecarWork.track,
                        quality = sidecarWork.quality,
                        audioCachingEnabled = sidecarWork.audioCachingEnabled,
                    )
                },
                prepareLyrics = { sidecarWork ->
                    sidecars.prepareLyrics(
                        sourceId = sourceId,
                        provider = sidecarWork.provider,
                        track = sidecarWork.track,
                        quality = sidecarWork.quality,
                        audioCachingEnabled = sidecarWork.audioCachingEnabled,
                        onlineLyricsEnabled = sidecarWork.onlineLyricsEnabled,
                    )
                },
                onWaveformReady = {
                    callbacks?.onCurrentTrackSidecarsReady(work.track)
                },
            )
        }
    }

    private fun startAudioPrefetch(scope: CoroutineScope, activeSessionId: Int) {
        audioPrefetchStats = initialAudioPrefetchStats(
            enabled = audioCachingEnabledProvider(),
            configuredDepth = audioPrefetchDepthProvider(),
        )
        if (!audioCachingEnabledProvider()) return
        val audioCache = audioCacheRepository ?: return
        val sidecars = sidecarService ?: return
        val work = planAudioPrefetchWork(
            sourceId = sourceIdProvider(),
            provider = provider,
            quality = streamQuality,
            queue = queue,
            enabled = audioPrefetchStats.enabled,
            configuredDepth = audioPrefetchStats.configuredDepth,
            includeCurrentTrack = false,
        ) ?: return

        audioPrefetchJob?.cancel()
        audioPrefetchJob = scope.launch {
            audioPrefetchStats = runAudioPrefetch(
                stats = work.stats,
                tracks = work.tracks,
                isActive = { activeSessionId == queueController.playbackSessionId },
                cacheAudio = { track ->
                    audioCache.cacheAudioTrack(
                        sourceId = work.sourceId,
                        provider = work.provider,
                        track = track,
                        quality = work.quality,
                    )
                },
                prepareSidecars = { track, _ ->
                    runPrefetchSidecars(
                        sidecarService = sidecars,
                        sourceId = work.sourceId,
                        provider = work.provider,
                        track = track,
                        quality = work.quality,
                    )
                },
                onStatsChanged = { stats -> audioPrefetchStats = stats },
            )
        }
    }

    private suspend fun runPrefetchSidecars(
        sidecarService: PlaybackSidecarService,
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
    ): PlaybackSidecarPrepResult {
        val waveformResult = sidecarService.prepareAll(
            sourceId = sourceId,
            provider = provider,
            track = track,
            quality = quality,
            audioCachingEnabled = audioCachingEnabledProvider(),
            onlineLyricsEnabled = true,
            includeLyrics = false,
        )
        val lyricsResult = sidecarService.prepareDetailedLyrics(
            sourceId = sourceId,
            provider = provider,
            track = track,
            quality = quality,
            audioCachingEnabled = audioCachingEnabledProvider(),
        )
        return PlaybackSidecarPrepResult(
            failed = waveformResult.failed + lyricsResult.failed,
            lastError = lyricsResult.lastError ?: waveformResult.lastError,
        )
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
        val nextIndex = queueController.nextGaplessQueueIndex() ?: return
        val work = preparedNextPlaybackCoordinator.work(
            queue = queue,
            progress = progress,
            nextQueueIndex = nextIndex,
            preparedNextIndex = queueController.preparedNextIndex,
            settings = PreparedNextPlaybackSettings(
                gaplessEnabled = gaplessEnabled,
                supportsGapless = playbackEngine.supportsGapless,
                crossfadeDurationSeconds = crossfadeSettings.durationSeconds,
                supportsCrossfade = playbackEngine.supportsCrossfade,
                gaplessPrepareWindowSeconds = GaplessPrepareWindowSeconds,
            ),
        ) ?: return
        queueController.markPreparedNext(work.markPreparedNextIndex)

        scope.launch {
            if (activeSessionId != queueController.playbackSessionId) return@launch
            try {
                val prepared = preparedNextPlaybackCoordinator.request(work) ?: return@launch
                if (activeSessionId == queueController.playbackSessionId) {
                    withContext(Dispatchers.IO) {
                        queueAwareEngine.prepareNext(prepared.request)
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

        val sourceId = sourceIdProvider() ?: return null
        return audioMetadataSidecarService?.replayGainForTrack(
            sourceId = sourceId,
            track = track,
            quality = quality,
            audioCachingEnabled = audioCachingEnabledProvider(),
            replayGainMode = replayGainMode,
        )
    }
}

private data class PlaybackTarget(
    val url: String,
    val source: PlaybackSource,
    val engineStartPositionSeconds: Double?,
)

data class PlaylistCallbacks(
    val onTrackStarted: (Track, String?) -> Unit,
    val onQueueChanged: (PlaybackQueue) -> Unit,
    val onPlaybackStateChanged: (PlaybackState) -> Unit,
    val onPlaybackProgressChanged: (PlaybackProgress) -> Unit,
    val onMetadataChanged: (app.naviamp.domain.playback.PlaybackStreamMetadata) -> Unit = {},
    val onCurrentTrackSidecarsReady: (Track) -> Unit = {},
)

private fun ReplayGain.hasAnyValue(): Boolean =
    trackGainDb != null || albumGainDb != null || trackPeak != null || albumPeak != null

private const val GaplessPrepareWindowSeconds = 8.0
private const val DefaultAudioPrefetchDepth = 10
