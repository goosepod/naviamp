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

fun analyzeBassFloatPcmWaveform(
    bass: BassAudioBackend,
    stream: BassStreamHandle,
    bucketCount: Int = DefaultWaveformBucketCount,
): AudioWaveform? {
    bass.waveformLevels(stream, bucketCount)
        .getOrNull()
        ?.takeIf { it.isNotEmpty() }
        ?.takeIf { it.hasPlausibleDecodedCoverage() }
        ?.toList()
        ?.let { levels -> return normalizeWaveformPeaks(levels, bucketCount) }

    bass.seek(stream, 0.0)
    val totalSamples = bass.lengthBytes(stream)
        ?.let { it / Float.SIZE_BYTES }
        ?: return null
    var readError = false
    val waveform = normalizeFloatPcmWaveform(
        totalSamples = totalSamples,
        bucketCount = bucketCount,
    ) { buffer ->
        bass.readFloatData(stream, buffer)
            .getOrElse {
                readError = true
                0
            }
    } ?: return null
    return if (readError) null else waveform
}

private fun FloatArray.hasPlausibleDecodedCoverage(): Boolean {
    if (size < MinCoverageValidationBuckets) return true
    val lastSignalBucket = indexOfLast { kotlin.math.abs(it) > SilentBucketThreshold }
    if (lastSignalBucket < 0) return true
    val signalBucketCount = count { kotlin.math.abs(it) > SilentBucketThreshold }
    val minimumLastSignalBucket = (size * MinimumDecodedBucketCoverage).toInt()
    val minimumSignalBucketCount = (size * MinimumSignalBucketRatio).toInt()
    return lastSignalBucket >= minimumLastSignalBucket || signalBucketCount > minimumSignalBucketCount
}

private const val MinCoverageValidationBuckets = 16
private const val SilentBucketThreshold = 0.00001f
private const val MinimumDecodedBucketCoverage = 0.25f
private const val MinimumSignalBucketRatio = 0.10f
