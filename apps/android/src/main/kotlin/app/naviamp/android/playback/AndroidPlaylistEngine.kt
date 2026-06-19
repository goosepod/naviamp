package app.naviamp.android

import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.android.playback.AndroidPlaybackTls
import app.naviamp.android.playback.AndroidAudioWaveformAnalyzer
import app.naviamp.android.toPlaybackLocalAudio
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.audio.AudioMetadataSidecarService
import app.naviamp.domain.cache.AudioWaveformStorageRepository
import app.naviamp.domain.cache.SidecarStatusRepository
import app.naviamp.domain.lyrics.LyricsSidecarService
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackReplayGain
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackLocalAudio
import app.naviamp.domain.playback.PlaybackSidecarService
import app.naviamp.domain.playback.PlaybackSidecarPrepResult
import app.naviamp.domain.playback.PreparedNextPlaybackCoordinator
import app.naviamp.domain.playback.PreparedNextPlaybackSettings
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.playback.ReplayGainSource
import app.naviamp.domain.playback.SidecarTypeLyrics
import app.naviamp.domain.playback.SidecarTypeWaveform
import app.naviamp.domain.playback.currentTrackSidecarWork
import app.naviamp.domain.playback.planAudioPrefetchWork
import app.naviamp.domain.playback.recordSidecarFailure
import app.naviamp.domain.playback.resolvePlaybackAudioSource
import app.naviamp.domain.playback.runAudioPrefetch
import app.naviamp.domain.playback.runCurrentTrackSidecars
import app.naviamp.domain.playback.waveformUnavailableStatus
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.waveform.AudioWaveformService
import app.naviamp.provider.navidrome.NavidromeProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AndroidPlaylistEngine(
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    waveformRepository: AudioWaveformStorageRepository,
    private val warmCoverArt: suspend (NavidromeProvider, Track) -> Unit,
    private val cacheAudioTrack: suspend (String, NavidromeProvider, Track, StreamQuality) -> File?,
    private val playbackAudioAssets: PlaybackAudioAssetRepository,
    private val playbackEngine: AndroidPlaybackEngine,
    private val playbackQueueController: PlaybackQueueController,
    waveformAnalyzer: AndroidAudioWaveformAnalyzer,
    private val audioMetadataSidecarService: AudioMetadataSidecarService,
    private val lyricsSidecarService: LyricsSidecarService,
    private val sidecarStatusRepository: SidecarStatusRepository,
    private val activeQueue: () -> List<Track>,
    private val currentStreamQuality: () -> StreamQuality,
) {
    private val audioCacheKeysInFlight = mutableSetOf<String>()
    private val audioWaveformService = AudioWaveformService(
        waveformRepository = waveformRepository,
        audioAssets = playbackAudioAssets,
        analyzer = waveformAnalyzer,
        waveformsEnabled = { state.cacheSettings.waveformsEnabled },
        waveformBucketCount = { state.cacheSettings.normalized().waveformBucketCount },
        prepareAnalysis = {
            waveformAnalyzer.applyTlsSettings(state.activeTlsSettings)
            AndroidPlaybackTls.applyDefaults(state.activeTlsSettings)
        },
        cacheAudioForWaveform = { sourceId, mediaProvider, track, quality ->
            cacheAudioTrack(sourceId, mediaProvider as NavidromeProvider, track, quality)
                ?.toPlaybackLocalAudio()
        },
    )
    private val sidecarService = PlaybackSidecarService(
        waveformService = audioWaveformService,
        lyricsSidecarService = lyricsSidecarService,
        sidecarStatusRepository = sidecarStatusRepository,
    )
    private val preparedNextPlaybackCoordinator = PreparedNextPlaybackCoordinator(
        provider = { state.provider },
        sourceId = { state.activeSourceId },
        quality = currentStreamQuality,
        audioCachingEnabled = { true },
        audioAssets = playbackAudioAssets,
        replayGainMode = { state.playbackSettings.replayGainMode },
        supportsReplayGain = { playbackEngine.supportsReplayGain },
        replayGainForTrack = { track, _ ->
            track.replayGain?.let { PlaybackReplayGain(it, ReplayGainSource.Provider) }
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
        ).localAudio?.let { localAudio ->
            warmCoverArt(activeProvider, track)
            return File(localAudio.path)
        }

        val cacheKey = "${sourceId}:${track.id.value}:$quality"
        if (!audioCacheKeysInFlight.add(cacheKey)) {
            return playbackAudioAssets.cachedAudio(sourceId, track.id, quality)?.let { localAudio ->
                warmCoverArt(activeProvider, track)
                File(localAudio.path)
            }
        }

        return try {
            cacheAudioTrack(sourceId, activeProvider, track, quality)
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
        val work = planAudioPrefetchWork(
            sourceId = state.activeSourceId,
            provider = activeProvider,
            quality = currentStreamQuality(),
            queue = queue,
            enabled = state.cacheSettings.audioCachingEnabled,
            configuredDepth = state.cacheSettings.audioPrefetchDepth,
            includeCurrentTrack = false,
        ) ?: return

        state.audioPrefetchJob = scope.launch {
            runAudioPrefetch(
                stats = work.stats,
                tracks = work.tracks,
                isActive = { sessionToken == state.playbackSessionToken },
                cacheAudio = { track ->
                    cacheAudioTrackForPlayback(work.sourceId, work.provider, track, work.quality)
                },
                warmCoverArt = { track ->
                    warmCoverArt(work.provider, track)
                },
                prepareSidecars = { track, _ ->
                    preparePrefetchedSidecars(
                        sourceId = work.sourceId,
                        provider = work.provider,
                        track = track,
                        quality = work.quality,
                    )
                },
                onTrackCached = { track, file ->
                    if (file != null) {
                        android.util.Log.i("NaviampCache", "Prefetched audio title=${track.title} path=${file.name}")
                    }
                },
                onTrackFailed = { track, error ->
                    android.util.Log.w("NaviampCache", "Audio prefetch failed title=${track.title}", error)
                },
            )
        }
    }

    fun prepareNextIfNeeded(sessionToken: Long, progress: PlaybackProgress) {
        val queueAwareEngine = playbackEngine as? QueueAwarePlaybackEngine ?: return
        val nextIndex = nextQueueIndex() ?: return
        val queue = playbackQueueController.queue
        val work = preparedNextPlaybackCoordinator.work(
            queue = queue,
            progress = progress,
            nextQueueIndex = nextIndex,
            preparedNextIndex = playbackQueueController.preparedNextIndex,
            settings = PreparedNextPlaybackSettings(
                gaplessEnabled = state.playbackSettings.gaplessEnabled,
                supportsGapless = playbackEngine.supportsGapless,
                crossfadeDurationSeconds = state.playbackSettings.crossfadeDurationSeconds,
                supportsCrossfade = playbackEngine.supportsCrossfade,
                gaplessPrepareWindowSeconds = AndroidGaplessPrepareWindowSeconds,
            ),
        ) ?: return
        playbackQueueController.markPreparedNext(work.markPreparedNextIndex)
        scope.launch {
            runCatching {
                preparedNextPlaybackCoordinator.request(work)
            }.onSuccess { prepared ->
                prepared ?: return@onSuccess
                if (sessionToken != state.playbackSessionToken) return@onSuccess
                queueAwareEngine.prepareNext(prepared.request)
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
        val work = currentTrackSidecarWork(
            sourceId = state.activeSourceId,
            provider = activeProvider,
            queue = queue,
            quality = currentStreamQuality(),
            audioCachingEnabled = true,
            onlineLyricsEnabled = state.playbackSettings.lrclibLyricsEnabled,
            lyricsVisible = state.lyricsVisible,
        ) ?: return
        state.sidecarPrepJob = scope.launch {
            runCurrentTrackSidecars(
                work = work,
                isActive = { sessionToken == state.playbackSessionToken },
                prepareWaveform = { sidecarWork ->
                    sidecarService.prepareWaveform(
                        sourceId = sidecarWork.sourceId,
                        provider = sidecarWork.provider,
                        track = sidecarWork.track,
                        quality = sidecarWork.quality,
                        audioCachingEnabled = sidecarWork.audioCachingEnabled,
                    )
                },
                prepareAudioTags = { sidecarWork ->
                    audioMetadataSidecarService.audioTagsForTrack(
                        sourceId = sidecarWork.sourceId,
                        track = sidecarWork.track,
                        quality = sidecarWork.quality,
                        audioCachingEnabled = sidecarWork.audioCachingEnabled,
                    )
                },
                prepareLyrics = { sidecarWork ->
                    sidecarService.prepareLyrics(
                        sourceId = sidecarWork.sourceId,
                        provider = sidecarWork.provider,
                        track = sidecarWork.track,
                        quality = sidecarWork.quality,
                        audioCachingEnabled = sidecarWork.audioCachingEnabled,
                        onlineLyricsEnabled = sidecarWork.onlineLyricsEnabled,
                    )
                },
                onWaveformReady = { waveform ->
                    if (waveform != null) {
                        state.waveformByTrackId = state.waveformByTrackId + (work.track.id.value to waveform)
                    }
                    if (waveform != null) {
                        android.util.Log.i(
                            "NaviampWaveform",
                            "Waveform ready title=${work.track.title} buckets=${waveform.amplitudes.size}",
                        )
                    } else {
                        android.util.Log.w("NaviampWaveform", "Waveform unavailable title=${work.track.title}")
                    }
                },
                onWaveformFailed = { error ->
                    android.util.Log.w("NaviampWaveform", "Waveform failed title=${work.track.title}", error)
                    val sourceId = work.sourceId
                    if (sourceId != null) {
                        sidecarStatusRepository.recordSidecarFailure(
                            sourceId = sourceId,
                            trackId = work.track.id,
                            quality = work.quality,
                            sidecarType = SidecarTypeWaveform,
                            errorMessage = waveformUnavailableStatus(error),
                        )
                    }
                },
                onAudioTagsReady = { tags ->
                    state.audioTagsByTrackId = state.audioTagsByTrackId + (work.track.id.value to tags)
                },
                onAudioTagsFailed = {
                    state.audioTagsByTrackId = state.audioTagsByTrackId + (work.track.id.value to emptyList())
                },
                onLyricsReady = { lyrics ->
                    if (lyrics != null || state.lyricsByTrackId[work.track.id.value] == null) {
                        state.lyricsByTrackId = state.lyricsByTrackId + (work.track.id.value to lyrics)
                    }
                    state.lyricsStatusByTrackId = state.lyricsStatusByTrackId + (work.track.id.value to null)
                },
                onLyricsFailed = { _, message ->
                    if (state.lyricsByTrackId[work.track.id.value] != null) {
                        state.lyricsStatusByTrackId = state.lyricsStatusByTrackId + (work.track.id.value to null)
                    } else {
                        state.lyricsByTrackId = state.lyricsByTrackId + (work.track.id.value to null)
                        state.lyricsStatusByTrackId = state.lyricsStatusByTrackId + (work.track.id.value to message)
                    }
                    val sourceId = work.sourceId
                    if (sourceId != null) {
                        sidecarStatusRepository.recordSidecarFailure(
                            sourceId = sourceId,
                            trackId = work.track.id,
                            quality = work.quality,
                            sidecarType = SidecarTypeLyrics,
                            errorMessage = message,
                        )
                    }
                },
            )
        }
    }

    private suspend fun preparePrefetchedSidecars(
        sourceId: String,
        provider: NavidromeProvider,
        track: Track,
        quality: StreamQuality,
    ): PlaybackSidecarPrepResult =
        sidecarService.prepareAll(
            sourceId = sourceId,
            provider = provider,
            track = track,
            quality = quality,
            audioCachingEnabled = true,
            onlineLyricsEnabled = state.playbackSettings.lrclibLyricsEnabled,
            includeLyrics = false,
        )

    private fun nextQueueIndex(): Int? {
        return playbackQueueController.nextGaplessQueueIndexForExternalQueue(
            tracks = activeQueue(),
            currentTrack = state.nowPlaying,
            repeatMode = state.repeatMode,
        )
    }
}
