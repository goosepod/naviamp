package app.naviamp.domain.bass

import kotlin.math.min
import kotlin.math.sqrt

/**
 * Samples a decoder sequentially, combining native RMS windows into duration-weighted buckets.
 * The adapter only rewinds the native stream and reads a mono RMS window of at most one second.
 */
fun analyzeBassWaveformLevels(
    durationSeconds: Double,
    bucketCount: Int,
    rewind: () -> Result<Unit>,
    readRms: (seconds: Float) -> Result<Float>,
): Result<FloatArray> {
    if (!durationSeconds.isFinite() || durationSeconds < 0.0) {
        return Result.failure(IllegalArgumentException("Invalid waveform duration"))
    }
    val output = FloatArray(bucketCount.coerceIn(1, 4_096))
    if (durationSeconds == 0.0) return Result.success(output)
    rewind().getOrElse { return Result.failure(it) }
    val bucketSeconds = durationSeconds / output.size
    for (bucket in output.indices) {
        var remaining = bucketSeconds
        var squareSum = 0.0
        var measured = 0.0
        while (remaining > 0.0001) {
            val window = min(remaining, 1.0).toFloat()
            val nextRemaining = remaining - window
            if (nextRemaining >= remaining) {
                return Result.failure(IllegalArgumentException("Waveform duration exceeds sampling precision"))
            }
            val sample = readRms(window).getOrElse { return Result.failure(it) }
            if (!sample.isFinite()) {
                return Result.failure(IllegalStateException("Invalid waveform RMS level"))
            }
            val level = sample.coerceIn(0f, 1f)
            squareSum += level * level * window
            measured += window
            remaining = nextRemaining
        }
        output[bucket] = if (measured > 0.0) sqrt(squareSum / measured).toFloat() else 0f
    }
    return Result.success(output)
}
