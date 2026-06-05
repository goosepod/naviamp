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
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.playback.ReplayGainSource
import app.naviamp.domain.playback.SidecarTypeLyrics
import app.naviamp.domain.playback.SidecarTypeWaveform
import app.naviamp.domain.playback.audioPrefetchTracks
import app.naviamp.domain.playback.initialAudioPrefetchStats
import app.naviamp.domain.playback.planPrepareNextQueuePlayback
import app.naviamp.domain.playback.preparedNextPlaybackRequest
import app.naviamp.domain.playback.recordSidecarFailure
import app.naviamp.domain.playback.recordSidecarSuccess
import app.naviamp.domain.playback.resolvePlaybackAudioSource
import app.naviamp.domain.playback.runAudioPrefetch
import app.naviamp.domain.playback.sidecarPrepPlan
import app.naviamp.domain.playback.lyricsUnavailableStatus
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
    waveformRepository: AudioWaveformStorageRepository,
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
        ).localAudio?.let { return File(it.path) }

        val cacheKey = "${sourceId}:${track.id.value}:$quality"
        if (!audioCacheKeysInFlight.add(cacheKey)) {
            return playbackAudioAssets.cachedAudio(sourceId, track.id, quality)?.let { File(it.path) }
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
        val sourceId = state.activeSourceId ?: return
        val quality = currentStreamQuality()
        val tracksToPrefetch = audioPrefetchTracks(
            queue = queue,
            depth = AndroidAudioPrefetchDepth,
            includeCurrentTrack = false,
        )
        if (tracksToPrefetch.isEmpty()) return

        state.audioPrefetchJob = scope.launch {
            runAudioPrefetch(
                stats = initialAudioPrefetchStats(enabled = true, configuredDepth = AndroidAudioPrefetchDepth),
                tracks = tracksToPrefetch,
                isActive = { sessionToken == state.playbackSessionToken },
                cacheAudio = { track ->
                    cacheAudioTrackForPlayback(sourceId, activeProvider, track, quality)
                },
                prepareSidecars = { track, _ ->
                    preparePrefetchedSidecars(
                        sourceId = sourceId,
                        provider = activeProvider,
                        track = track,
                        quality = quality,
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
                preparedNextPlaybackRequest(
                    plan = plan,
                    provider = activeProvider,
                    sourceId = sourceId,
                    quality = quality,
                    audioCachingEnabled = true,
                    audioAssets = playbackAudioAssets,
                    replayGainMode = state.playbackSettings.replayGainMode,
                    supportsReplayGain = playbackEngine.supportsReplayGain,
                    replayGainForTrack = { track, _ ->
                        track.replayGain?.let { PlaybackReplayGain(it, ReplayGainSource.Provider) }
                    },
                )
            }.onSuccess { prepared ->
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
        val plan = sidecarPrepPlan(
            queue = queue,
            depth = 1,
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
                    sidecarService.prepareWaveform(
                        sourceId = sourceId,
                        provider = activeProvider,
                        track = track,
                        quality = sidecarQuality,
                        audioCachingEnabled = true,
                    )
                }.onSuccess { waveform ->
                    if (waveform != null && sessionToken == state.playbackSessionToken) {
                        state.waveformByTrackId = state.waveformByTrackId + (track.id.value to waveform)
                        android.util.Log.i(
                            "NaviampWaveform",
                            "Waveform ready title=${track.title} buckets=${waveform.amplitudes.size}",
                        )
                    } else if (sessionToken == state.playbackSessionToken) {
                        android.util.Log.w("NaviampWaveform", "Waveform unavailable title=${track.title}")
                    }
                }.onFailure { error ->
                    android.util.Log.w("NaviampWaveform", "Waveform failed title=${track.title}", error)
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
                val tagQuality = currentStreamQuality()
                runCatching {
                    audioMetadataSidecarService.audioTagsForTrack(
                        sourceId = sourceId,
                        track = track,
                        quality = tagQuality,
                        audioCachingEnabled = true,
                    )
                }.onSuccess { tags ->
                    if (sessionToken == state.playbackSessionToken) {
                        state.audioTagsByTrackId = state.audioTagsByTrackId + (track.id.value to tags)
                    }
                }.onFailure {
                    if (sessionToken == state.playbackSessionToken) {
                        state.audioTagsByTrackId = state.audioTagsByTrackId + (track.id.value to emptyList())
                    }
                }
                if (sessionToken != state.playbackSessionToken) return@launch
                if (plan.loadLyrics) {
                    val quality = currentStreamQuality()
                    runCatching {
                        sidecarService.prepareLyrics(
                            sourceId = sourceId,
                            provider = activeProvider,
                            track = track,
                            quality = quality,
                            audioCachingEnabled = true,
                            onlineLyricsEnabled = state.playbackSettings.lrclibLyricsEnabled,
                        )
                    }.onSuccess { lyrics ->
                        state.lyricsByTrackId = state.lyricsByTrackId + (track.id.value to lyrics)
                        state.lyricsStatusByTrackId = state.lyricsStatusByTrackId + (track.id.value to null)
                    }.onFailure { error ->
                        val message = lyricsUnavailableStatus(error)
                        state.lyricsByTrackId = state.lyricsByTrackId + (track.id.value to null)
                        state.lyricsStatusByTrackId = state.lyricsStatusByTrackId + (track.id.value to message)
                        if (sourceId != null) {
                            sidecarStatusRepository.recordSidecarFailure(
                                sourceId = sourceId,
                                trackId = track.id,
                                quality = quality,
                                sidecarType = SidecarTypeLyrics,
                                errorMessage = message,
                            )
                        }
                    }
                }
            }
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
