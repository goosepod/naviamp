package app.naviamp.domain.waveform

import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassStreamHandle
import app.naviamp.domain.settings.DefaultWaveformBucketCount

data class AudioWaveformAnalysisSource(
    val cacheKey: String,
    val streamUrl: String,
    val bucketCount: Int = DefaultWaveformBucketCount,
    val expectedDurationSeconds: Double? = null,
)

interface AudioWaveformAnalyzer {
    suspend fun analyze(source: AudioWaveformAnalysisSource): AudioWaveform?
}

/**
 * Shared waveform analyzer for every host backed by BASS.
 *
 * Hosts provide only the native ABI adapter and, where supported, native file-URL translation.
 * Core owns network setup, decode-stream selection, analysis, and stream lifetime.
 */
class BassAudioWaveformAnalyzer(
    private val bass: BassAudioBackend,
    private val verifyNetworkCertificates: () -> Boolean = { true },
    private val localFilePath: (String) -> String? = { null },
) : AudioWaveformAnalyzer {
    override suspend fun analyze(source: AudioWaveformAnalysisSource): AudioWaveform? {
        bass.setVerifyNet(verifyNetworkCertificates()).getOrElse { return null }
        bass.configureInternetStreams().getOrElse { return null }
        val stream = localFilePath(source.streamUrl)
            ?.let(bass::createFileDecodeStream)
            ?: bass.createBoundedUrlDecodeStream(source.streamUrl)
        val handle = stream.getOrElse { return null }
        return try {
            analyzeBassFloatPcmWaveform(
                bass = bass,
                stream = handle,
                bucketCount = source.bucketCount,
                expectedDurationSeconds = source.expectedDurationSeconds,
            )
        } finally {
            bass.freeStream(handle)
        }
    }
}

suspend fun analyzeBassFloatPcmWaveform(
    bass: BassAudioBackend,
    stream: BassStreamHandle,
    bucketCount: Int = DefaultWaveformBucketCount,
    expectedDurationSeconds: Double? = null,
    chunkDelayMillis: Long = DefaultWaveformAnalysisChunkDelayMillis,
    shouldContinue: () -> Boolean = { true },
): AudioWaveform? {
    val totalSamples = bass.lengthBytes(stream)
        ?.takeIf { it > 0L }
        ?.let { it / Float.SIZE_BYTES }
        ?: expectedDurationSeconds
            ?.takeIf { it > 0.0 }
            ?.let { duration ->
                bass.channelInfo(stream).getOrNull()?.let { info ->
                    (duration * info.frequency * info.channels).toLong()
                }
            }
        ?: return null
    var readError = false
    val waveform = normalizeFloatPcmWaveform(
        totalSamples = totalSamples,
        bucketCount = bucketCount,
        chunkDelayMillis = chunkDelayMillis,
        shouldContinue = shouldContinue,
    ) { buffer ->
        bass.readFloatData(stream, buffer)
            .getOrElse {
                readError = true
                0
            }
    } ?: return null
    return if (readError) null else waveform
}

const val DefaultWaveformAnalysisChunkDelayMillis = 8L
