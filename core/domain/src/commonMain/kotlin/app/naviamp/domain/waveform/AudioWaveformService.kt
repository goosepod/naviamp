package app.naviamp.domain.waveform

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.cache.AudioWaveformStorageRepository
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.resolvePlaybackAudioSource
import app.naviamp.domain.provider.MediaProvider

data class AudioWaveformServiceResult<LocalAudio>(
    val waveform: AudioWaveform?,
    val localAudio: LocalAudio?,
    val cachedWaveformAvailable: Boolean,
    val generatedWaveformAvailable: Boolean,
    val audioAvailable: Boolean,
    val playbackSource: PlaybackSource,
) {
    val available: Boolean
        get() = waveform != null
}

class AudioWaveformService<LocalAudio>(
    private val waveformRepository: AudioWaveformStorageRepository,
    private val audioAssets: PlaybackAudioAssetRepository<LocalAudio>,
    private val analyzer: AudioWaveformAnalyzer,
    private val localAudioUrl: (LocalAudio) -> String,
    private val localAudioPath: (LocalAudio) -> String? = { null },
    private val prepareAnalysis: suspend () -> Unit = {},
    private val cacheAudioForWaveform: suspend (
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
    ) -> LocalAudio? = { _, _, _, _ -> null },
) {
    suspend fun loadOrCreateWaveform(
        sourceId: String?,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        audioCachingEnabled: Boolean,
    ): AudioWaveformServiceResult<LocalAudio> {
        if (sourceId != null) {
            waveformRepository.cachedAudioWaveform(sourceId, track.id, quality)?.let { waveform ->
                return AudioWaveformServiceResult(
                    waveform = waveform,
                    localAudio = null,
                    cachedWaveformAvailable = true,
                    generatedWaveformAvailable = false,
                    audioAvailable = true,
                    playbackSource = PlaybackSource.CachedFile,
                )
            }
        }

        val plan = resolvePlaybackAudioSource(
            sourceId = sourceId,
            track = track,
            quality = quality,
            audioCachingEnabled = audioCachingEnabled,
            audioAssets = audioAssets,
        )
        val cachedOrDownloadedAudio = plan.localAudio
        val generatedAudio = if (cachedOrDownloadedAudio == null && sourceId != null && audioCachingEnabled) {
            cacheAudioForWaveform(sourceId, provider, track, quality)
        } else {
            null
        }
        val localAudio = cachedOrDownloadedAudio ?: generatedAudio
        val playbackSource = when {
            cachedOrDownloadedAudio != null -> plan.source
            generatedAudio != null -> PlaybackSource.CachedFile
            else -> plan.source
        }

        if (sourceId != null && localAudio != null) {
            waveformRepository.cachedAudioWaveform(sourceId, track.id, quality)?.let { waveform ->
                return AudioWaveformServiceResult(
                    waveform = waveform,
                    localAudio = localAudio,
                    cachedWaveformAvailable = true,
                    generatedWaveformAvailable = false,
                    audioAvailable = true,
                    playbackSource = playbackSource,
                )
            }
        }

        prepareAnalysis()
        val streamUrl = localAudio
            ?.let(localAudioUrl)
            ?: provider.streamUrl(plan.target.providerStreamRequest)
        val waveform = analyzer.analyze(
            AudioWaveformAnalysisSource(
                cacheKey = track.id.value,
                streamUrl = streamUrl,
            ),
        )
        val storedWaveform = if (waveform != null && sourceId != null && localAudio != null) {
            waveformRepository.storeAudioWaveform(
                sourceId = sourceId,
                trackId = track.id,
                quality = quality,
                audioFilePath = localAudioPath(localAudio),
                waveform = waveform,
            )
        } else {
            waveform
        }

        return AudioWaveformServiceResult(
            waveform = storedWaveform,
            localAudio = localAudio,
            cachedWaveformAvailable = false,
            generatedWaveformAvailable = storedWaveform != null,
            audioAvailable = localAudio != null,
            playbackSource = playbackSource,
        )
    }
}
