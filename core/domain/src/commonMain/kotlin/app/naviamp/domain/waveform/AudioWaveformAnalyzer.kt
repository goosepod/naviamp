package app.naviamp.domain.waveform

import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassStreamHandle
import app.naviamp.domain.settings.DefaultWaveformBucketCount

data class AudioWaveformAnalysisSource(
    val cacheKey: String,
    val streamUrl: String,
    val bucketCount: Int = DefaultWaveformBucketCount,
)

interface AudioWaveformAnalyzer {
    suspend fun analyze(source: AudioWaveformAnalysisSource): AudioWaveform?
}

suspend fun analyzeBassFloatPcmWaveform(
    bass: BassAudioBackend,
    stream: BassStreamHandle,
    bucketCount: Int = DefaultWaveformBucketCount,
    chunkDelayMillis: Long = DefaultWaveformAnalysisChunkDelayMillis,
    shouldContinue: () -> Boolean = { true },
): AudioWaveform? {
    val totalSamples = bass.lengthBytes(stream)
        ?.let { it / Float.SIZE_BYTES }
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
