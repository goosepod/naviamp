package app.naviamp.domain.waveform

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.cache.AudioWaveformStorageRepository
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackLocalAudio
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.playbackStreamUrl
import app.naviamp.domain.playback.resolvePlaybackAudioSource
import app.naviamp.domain.playback.waveformStatus
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.settings.DefaultWaveformBucketCount
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

data class AudioWaveformServiceResult(
    val waveform: AudioWaveform?,
    val localAudio: PlaybackLocalAudio?,
    val cachedWaveformAvailable: Boolean,
    val generatedWaveformAvailable: Boolean,
    val audioAvailable: Boolean,
    val playbackSource: PlaybackSource,
) {
    val available: Boolean
        get() = waveform != null

    fun status(audioCachingEnabled: Boolean): String =
        waveformStatus(
            cachedWaveformAvailable = cachedWaveformAvailable,
            generatedWaveformAvailable = generatedWaveformAvailable,
            audioAvailable = audioAvailable,
            audioCachingEnabled = audioCachingEnabled,
        )
}

class AudioWaveformService(
    private val waveformRepository: AudioWaveformStorageRepository,
    private val audioAssets: PlaybackAudioAssetRepository,
    private val analyzer: AudioWaveformAnalyzer,
    private val waveformsEnabled: () -> Boolean = { true },
    private val waveformBucketCount: () -> Int = { DefaultWaveformBucketCount },
    private val cacheAudioBeforeAnalysis: () -> Boolean = { true },
    private val prepareAnalysis: suspend () -> Unit = {},
    private val workContext: CoroutineContext = EmptyCoroutineContext,
    private val cacheAudioForWaveform: suspend (
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
    ) -> PlaybackLocalAudio? = { _, _, _, _ -> null },
) {
    private val inFlightMutex = Mutex()
    private val inFlightWaveforms = mutableMapOf<String, CompletableDeferred<AudioWaveformServiceResult>>()

    suspend fun loadOrCreateWaveform(
        sourceId: String?,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        audioCachingEnabled: Boolean,
    ): AudioWaveformServiceResult = withContext(workContext) {
        val bucketCount = waveformBucketCount().coerceAtLeast(1)
        if (!waveformsEnabled()) {
            return@withContext AudioWaveformServiceResult(
                waveform = null,
                localAudio = null,
                cachedWaveformAvailable = false,
                generatedWaveformAvailable = false,
                audioAvailable = true,
                playbackSource = PlaybackSource.CachedFile,
            )
        }

        if (sourceId != null) {
            waveformRepository.cachedAudioWaveform(sourceId, track.id, quality, bucketCount)?.let { waveform ->
                return@withContext AudioWaveformServiceResult(
                    waveform = waveform,
                    localAudio = null,
                    cachedWaveformAvailable = true,
                    generatedWaveformAvailable = false,
                    audioAvailable = true,
                    playbackSource = PlaybackSource.CachedFile,
                )
            }
        }

        val inFlightKey = waveformInFlightKey(sourceId, track, quality, bucketCount)
        var ownsAnalysis = false
        val inFlightResult = inFlightMutex.withLock {
            inFlightWaveforms[inFlightKey] ?: CompletableDeferred<AudioWaveformServiceResult>()
                .also { deferred ->
                    inFlightWaveforms[inFlightKey] = deferred
                    ownsAnalysis = true
                }
        }
        if (!ownsAnalysis) {
            return@withContext inFlightResult.await()
        }

        try {
            val result = loadOrCreateWaveformAfterCacheLookup(
                sourceId = sourceId,
                provider = provider,
                track = track,
                quality = quality,
                audioCachingEnabled = audioCachingEnabled,
                bucketCount = bucketCount,
            )
            inFlightResult.complete(result)
            return@withContext result
        } catch (error: Throwable) {
            inFlightResult.completeExceptionally(error)
            throw error
        } finally {
            inFlightMutex.withLock {
                if (inFlightWaveforms[inFlightKey] === inFlightResult) {
                    inFlightWaveforms.remove(inFlightKey)
                }
            }
        }
    }

    private suspend fun loadOrCreateWaveformAfterCacheLookup(
        sourceId: String?,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        audioCachingEnabled: Boolean,
        bucketCount: Int,
    ): AudioWaveformServiceResult {
        val plan = resolvePlaybackAudioSource(
            sourceId = sourceId,
            track = track,
            quality = quality,
            audioCachingEnabled = audioCachingEnabled,
            audioAssets = audioAssets,
        )
        val cachedOrDownloadedAudio = plan.localAudio
        val generatedAudio = if (
            cachedOrDownloadedAudio == null &&
            sourceId != null &&
            audioCachingEnabled &&
            cacheAudioBeforeAnalysis()
        ) {
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
            waveformRepository.cachedAudioWaveform(sourceId, track.id, quality, bucketCount)?.let { waveform ->
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
        val streamUrl = plan.copy(localAudio = localAudio).playbackStreamUrl(
            providerStreamUrl = { target -> provider.streamUrl(target.providerStreamRequest) },
        )
        val waveform = analyzer.analyze(
            AudioWaveformAnalysisSource(
                cacheKey = track.id.value,
                streamUrl = streamUrl,
                bucketCount = bucketCount,
            ),
        )
        val storedWaveform = if (waveform != null && sourceId != null) {
            waveformRepository.storeAudioWaveform(
                sourceId = sourceId,
                trackId = track.id,
                quality = quality,
                audioFilePath = localAudio?.path,
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

private fun waveformInFlightKey(
    sourceId: String?,
    track: Track,
    quality: StreamQuality,
    bucketCount: Int,
): String =
    "${sourceId.orEmpty()}:${track.id.value}:${quality.waveformCacheKey()}:$bucketCount"
