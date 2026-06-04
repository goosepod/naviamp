package app.naviamp.android

import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.android.playback.AndroidPlaybackTls
import app.naviamp.android.playback.AndroidAudioWaveformAnalyzer
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.cache.SidecarStatusRepository
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackReplayGain
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.playback.ReplayGainSource
import app.naviamp.domain.playback.SidecarTypeWaveform
import app.naviamp.domain.playback.audioPrefetchTracks
import app.naviamp.domain.playback.planPrepareNextQueuePlayback
import app.naviamp.domain.playback.recordSidecarFailure
import app.naviamp.domain.playback.recordSidecarSuccess
import app.naviamp.domain.playback.resolvePlaybackAudioSource
import app.naviamp.domain.playback.sidecarPrepPlan
import app.naviamp.domain.playback.waveformUnavailableStatus
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.domain.waveform.AudioWaveformService
import app.naviamp.provider.navidrome.NavidromeProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AndroidPlaylistEngine(
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    private val storage: AndroidStorage,
    private val playbackAudioAssets: PlaybackAudioAssetRepository<File>,
    private val playbackEngine: AndroidPlaybackEngine,
    private val playbackQueueController: PlaybackQueueController,
    waveformAnalyzer: AndroidAudioWaveformAnalyzer,
    private val sidecarStatusRepository: SidecarStatusRepository,
    private val activeQueue: () -> List<Track>,
    private val currentStreamQuality: () -> StreamQuality,
    private val loadLyrics: (Track) -> Unit,
) {
    private val audioCacheKeysInFlight = mutableSetOf<String>()
    private val audioWaveformService = AudioWaveformService(
        waveformRepository = storage,
        audioAssets = playbackAudioAssets,
        analyzer = waveformAnalyzer,
        localAudioUrl = { file -> file.toURI().toString() },
        localAudioPath = { file -> file.absolutePath },
        prepareAnalysis = {
            waveformAnalyzer.applyTlsSettings(state.activeTlsSettings)
            AndroidPlaybackTls.applyDefaults(state.activeTlsSettings)
        },
        cacheAudioForWaveform = { sourceId, mediaProvider, track, quality ->
            cacheAudioTrackForPlayback(
                sourceId = sourceId,
                activeProvider = mediaProvider as NavidromeProvider,
                track = track,
                quality = quality,
            )
        },
    )

    suspend fun cacheAudioTrackForPlayback(
        sourceId: String,
        activeProvider: NavidromeProvider,
        track: Track,
        quality: StreamQuality,
    ): File? {
        if (track.isInternetRadioTrack()) return null
        resolvePlaybackAudioSource(
            sourceId = sourceId,
            track = track,
            quality = quality,
            audioCachingEnabled = true,
            audioAssets = playbackAudioAssets,
        ).localAudio?.let { return it }

        val cacheKey = "${sourceId}:${track.id.value}:$quality"
        if (!audioCacheKeysInFlight.add(cacheKey)) {
            return storage.cachedAudioFile(sourceId, track.id, quality)?.file
        }

        return try {
            storage.cacheAudioTrack(sourceId, activeProvider, track, quality).file
        } finally {
            audioCacheKeysInFlight.remove(cacheKey)
        }
    }

    fun startAudioPrefetch(
        sessionToken: Long,
        activeProvider: NavidromeProvider,
        queue: PlaybackQueue,
    ) {
        state.audioPrefetchJob?.cancel()
        val sourceId = state.activeSourceId ?: return
        val quality = currentStreamQuality()
        val tracksToPrefetch = audioPrefetchTracks(
            queue = queue,
            depth = AndroidAudioPrefetchDepth,
            includeCurrentTrack = true,
        )
        if (tracksToPrefetch.isEmpty()) return

        state.audioPrefetchJob = scope.launch {
            tracksToPrefetch.forEach { track ->
                if (sessionToken != state.playbackSessionToken) return@launch
                runCatching {
                    cacheAudioTrackForPlayback(sourceId, activeProvider, track, quality)
                }.onSuccess { file ->
                    if (file != null) {
                        android.util.Log.i("NaviampCache", "Prefetched audio title=${track.title} path=${file.name}")
                    }
                }.onFailure { error ->
                    android.util.Log.w("NaviampCache", "Audio prefetch failed title=${track.title}", error)
                }
            }
        }
    }

    fun prepareNextIfNeeded(sessionToken: Long, progress: PlaybackProgress) {
        val queueAwareEngine = playbackEngine as? QueueAwarePlaybackEngine ?: return
        val nextIndex = nextQueueIndex() ?: return
        val queue = playbackQueueController.queue
        val plan = planPrepareNextQueuePlayback(
            queue = queue,
            progress = progress,
            nextQueueIndex = nextIndex,
            alreadyPreparedNext = !playbackQueueController.shouldPrepareNext(nextIndex),
            gaplessEnabled = state.playbackSettings.gaplessEnabled,
            supportsGapless = playbackEngine.supportsGapless,
            crossfadeDurationSeconds = state.playbackSettings.crossfadeDurationSeconds,
            supportsCrossfade = playbackEngine.supportsCrossfade,
            gaplessPrepareWindowSeconds = AndroidGaplessPrepareWindowSeconds,
        ) ?: return
        val nextTrack = plan.track
        val activeProvider = state.provider ?: return
        val sourceId = state.activeSourceId
        playbackQueueController.markPreparedNext(plan.nextQueueIndex)
        scope.launch {
            runCatching {
                val quality = currentStreamQuality()
                val audioSourcePlan = resolvePlaybackAudioSource(
                    sourceId = sourceId,
                    track = nextTrack,
                    quality = quality,
                    audioCachingEnabled = true,
                    audioAssets = playbackAudioAssets,
                )
                audioSourcePlan.localAudio?.toURI()?.toString()
                    ?: activeProvider.streamUrl(audioSourcePlan.target.providerStreamRequest)
            }.onSuccess { streamUrl ->
                if (sessionToken != state.playbackSessionToken) return@onSuccess
                queueAwareEngine.prepareNext(
                    PlaybackRequest(
                        url = streamUrl,
                        mediaId = nextTrack.id.value,
                        replayGainMode = state.playbackSettings.replayGainMode,
                        replayGain = nextTrack.replayGain?.let { PlaybackReplayGain(it, ReplayGainSource.Provider) },
                    ),
                )
            }.onFailure {
                playbackQueueController.clearPreparedNext()
            }
        }
    }

    fun startSidecarPrep(
        sessionToken: Long,
        activeProvider: NavidromeProvider,
        queue: PlaybackQueue,
    ) {
        state.sidecarPrepJob?.cancel()
        val plan = sidecarPrepPlan(
            queue = queue,
            depth = AndroidSidecarPrepDepth,
            onlineLyricsEnabled = state.playbackSettings.lrclibLyricsEnabled,
            lyricsVisible = state.lyricsVisible,
        )
        if (plan.tracks.isEmpty()) return
        val sourceId = state.activeSourceId
        state.sidecarPrepJob = scope.launch {
            plan.tracks.forEach { track ->
                if (sessionToken != state.playbackSessionToken) return@launch
                val sidecarQuality = currentStreamQuality()
                runCatching {
                    ensureWaveform(activeProvider, sourceId, track)
                }.onSuccess { waveform ->
                    if (sourceId != null) {
                        sidecarStatusRepository.recordSidecarSuccess(
                            sourceId = sourceId,
                            trackId = track.id,
                            quality = sidecarQuality,
                            sidecarType = SidecarTypeWaveform,
                        )
                    }
                    if (waveform != null && sessionToken == state.playbackSessionToken) {
                        state.waveformByTrackId = state.waveformByTrackId + (track.id.value to waveform)
                    }
                }.onFailure { error ->
                    if (sourceId != null) {
                        sidecarStatusRepository.recordSidecarFailure(
                            sourceId = sourceId,
                            trackId = track.id,
                            quality = sidecarQuality,
                            sidecarType = SidecarTypeWaveform,
                            errorMessage = waveformUnavailableStatus(error),
                        )
                    }
                }
                if (sessionToken != state.playbackSessionToken) return@launch
                if (plan.loadLyrics) {
                    loadLyrics(track)
                }
            }
        }
    }

    private suspend fun ensureWaveform(
        activeProvider: NavidromeProvider,
        sourceId: String?,
        track: Track,
    ): AudioWaveform? {
        val quality = currentStreamQuality()
        return audioWaveformService.loadOrCreateWaveform(
            sourceId = sourceId,
            provider = activeProvider,
            track = track,
            quality = quality,
            audioCachingEnabled = true,
        ).waveform
    }

    private fun nextQueueIndex(): Int? {
        val currentTrack = state.nowPlaying ?: return null
        val queue = activeQueue()
        val currentIndex = queue.indexOfFirst { it.id == currentTrack.id }
        if (currentIndex < 0) return null
        playbackQueueController.replaceQueue(
            queue = PlaybackQueue(tracks = queue, currentIndex = currentIndex),
            clearPreparedNext = false,
        )
        playbackQueueController.setRepeatMode(state.repeatMode)
        return playbackQueueController.nextGaplessQueueIndex()
    }
}
