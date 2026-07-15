package app.naviamp.domain.waveform

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.settings.DefaultWaveformBucketCount
import kotlin.math.ceil
import kotlin.math.sqrt

data class AudioWaveform(
    val amplitudes: List<Float>,
)

data class AudioWaveformCacheMetadata(
    val sourceId: String,
    val remoteTrackId: String,
    val qualityKey: String,
    val bucketCount: Int,
    val sizeBytes: Long,
    val createdAtEpochMillis: Long,
    val lastAccessedEpochMillis: Long,
)

fun StreamQuality.waveformCacheKey(): String =
    when (this) {
        StreamQuality.Original -> "original"
        is StreamQuality.Transcoded -> "transcoded:${codec.name.lowercase()}:$bitrateKbps"
    } + ":waveform-v10"

fun playbackFraction(positionSeconds: Double?, durationSeconds: Double?): Double {
    val position = positionSeconds ?: return 0.0
    val duration = durationSeconds ?: return 0.0
    if (duration <= 0.0) return 0.0
    return (position / duration).coerceIn(0.0, 1.0)
}

fun seekSecondsForFraction(fraction: Float, durationSeconds: Double?): Double? =
    durationSeconds?.takeIf { it > 0.0 }?.let { fraction.coerceIn(0f, 1f) * it }

fun normalizeWaveformPeaks(
    peaks: List<Float>,
    bucketCount: Int = DefaultWaveformBucketCount,
): AudioWaveform? {
    if (peaks.isEmpty() || bucketCount <= 0) return null
    val buckets = FloatArray(bucketCount)
    peaks.forEachIndexed { index, peak ->
        val bucket = ((index / peaks.lastIndex.coerceAtLeast(1).toFloat()) * (bucketCount - 1)).toInt()
        buckets[bucket] = maxOf(buckets[bucket], peak.coerceIn(0f, 1f))
    }
    return normalizeBuckets(buckets)
}

fun normalizePcm16Waveform(
    sampleCount: Int,
    bucketCount: Int = DefaultWaveformBucketCount,
    sampleAmplitude: (Int) -> Float,
): AudioWaveform? {
    if (sampleCount <= 0 || bucketCount <= 0) return null

    val buckets = FloatArray(bucketCount)
    repeat(bucketCount) { bucket ->
        val sampleStart = ((bucket / bucketCount.toFloat()) * sampleCount).toInt()
        val sampleEnd = ceil(((bucket + 1) / bucketCount.toFloat()) * sampleCount)
            .toInt()
            .coerceAtMost(sampleCount)
        if (sampleStart >= sampleEnd) return@repeat

        var sumSquares = 0.0
        var peak = 0f
        var count = 0
        var sampleIndex = sampleStart
        while (sampleIndex < sampleEnd) {
            val amplitude = sampleAmplitude(sampleIndex).coerceIn(0f, 1f)
            sumSquares += (amplitude * amplitude).toDouble()
            peak = maxOf(peak, amplitude)
            count += 1
            sampleIndex += 1
        }

        if (count > 0) {
            val rms = sqrt(sumSquares / count).toFloat()
            buckets[bucket] = rms * RmsWeight + peak * PeakWeight
        }
    }

    return normalizeBuckets(buckets)
}

fun normalizeFloatPcmWaveform(
    totalSamples: Long,
    bucketCount: Int = DefaultWaveformBucketCount,
    readSamples: (FloatArray) -> Int,
): AudioWaveform? {
    if (totalSamples <= 0 || bucketCount <= 0) return null
    val buffer = FloatArray(16_384)
    val buckets = FloatArray(bucketCount)
    val bucketCounts = IntArray(bucketCount)
    val bucketSquares = DoubleArray(bucketCount)
    val bucketPeaks = FloatArray(bucketCount)
    var sampleIndex = 0L

    while (true) {
        val read = readSamples(buffer)
        if (read <= 0) break
        var bufferIndex = 0
        while (bufferIndex < read) {
            if (sampleIndex >= totalSamples) break
            val bucket = ((sampleIndex * bucketCount) / totalSamples)
                .toInt()
                .coerceIn(0, bucketCount - 1)
            val amplitude = kotlin.math.abs(buffer[bufferIndex]).coerceIn(0f, 1f)
            bucketSquares[bucket] += (amplitude * amplitude).toDouble()
            bucketPeaks[bucket] = maxOf(bucketPeaks[bucket], amplitude)
            bucketCounts[bucket] += 1
            sampleIndex += 1
            bufferIndex += 1
        }
        if (sampleIndex >= totalSamples) break
    }

    if (sampleIndex <= 0) return null
    if (sampleIndex.toDouble() / totalSamples < MinDecodedSampleCoverage) return null

    repeat(bucketCount) { bucket ->
        val count = bucketCounts[bucket]
        if (count > 0) {
            val rms = sqrt(bucketSquares[bucket] / count).toFloat()
            buckets[bucket] = rms * RmsWeight + bucketPeaks[bucket] * PeakWeight
        }
    }

    return normalizeBuckets(buckets)
}

private fun normalizeBuckets(buckets: FloatArray): AudioWaveform {
    val max = buckets.maxOrNull() ?: 0f
    if (max <= 0f) return AudioWaveform(List(buckets.size) { 0f })
    return AudioWaveform(cleanWaveformAmplitudes(buckets.map { (it / max).coerceIn(0f, 1f) }))
}

fun cleanWaveformAmplitudes(amplitudes: List<Float>): List<Float> {
    if (amplitudes.size < MinSpikeSuppressionBuckets) return amplitudes
    var suppressedSpike = false
    val cleaned = amplitudes.mapIndexed { index, amplitude ->
        val previous = amplitudes.getOrNull(index - 1)
        val next = amplitudes.getOrNull(index + 1)
        val neighborPeak = listOfNotNull(previous, next).maxOrNull() ?: 0f
        if (amplitude >= IsolatedSpikeAmplitude &&
            neighborPeak <= IsolatedSpikeNeighborMax &&
            amplitude >= neighborPeak * IsolatedSpikeRatio
        ) {
            suppressedSpike = true
            neighborPeak
        } else {
            amplitude
        }
    }
    if (!suppressedSpike) return cleaned

    // The rejected spike may have been the value used to normalize every
    // bucket. Restore the remaining waveform's dynamic range after removing it.
    val cleanedMax = cleaned.maxOrNull() ?: 0f
    if (cleanedMax <= 0f) return cleaned
    return cleaned.map { (it / cleanedMax).coerceIn(0f, 1f) }
}

private const val RmsWeight = 0.82f
private const val PeakWeight = 0.18f
private const val MinDecodedSampleCoverage = 0.98
private const val MinSpikeSuppressionBuckets = 16
private const val IsolatedSpikeAmplitude = 0.92f
private const val IsolatedSpikeNeighborMax = 0.35f
private const val IsolatedSpikeRatio = 3.0f
