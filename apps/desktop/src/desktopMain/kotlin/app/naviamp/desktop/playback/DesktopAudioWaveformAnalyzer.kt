package app.naviamp.desktop

import app.naviamp.desktop.playback.bass.BassNative
import app.naviamp.domain.waveform.AudioWaveformAnalysisSource
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.domain.waveform.AudioWaveformAnalyzer as DomainAudioWaveformAnalyzer
import app.naviamp.domain.waveform.DefaultWaveformBucketCount
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.math.abs
import kotlin.math.sqrt

class DesktopAudioWaveformAnalyzer(
    private val nativeResult: Result<BassNative> = BassNative.load(),
    private val bucketCount: Int = DefaultWaveformBucketCount,
) : DomainAudioWaveformAnalyzer {
    override suspend fun analyze(source: AudioWaveformAnalysisSource): AudioWaveform? =
        analyze(source.streamUrl)

    fun analyze(audioPath: Path): AudioWaveform? {
        if (!audioPath.exists()) return null
        val bass = nativeResult.getOrNull() ?: return null
        bass.loadAvailablePlugins()
        val stream = bass.createFileDecodeStream(audioPath.toFile()).getOrNull() ?: return null
        return try {
            val totalSamples = bass.lengthBytes(stream)
                ?.let { it / Float.SIZE_BYTES }
            decodeWaveform(
                bass = bass,
                stream = stream,
                totalSamples = totalSamples,
                bucketCount = bucketCount,
            )
        } finally {
            bass.freeStream(stream)
        }
    }

    private fun analyze(streamUrl: String): AudioWaveform? {
        val localPath = localPathFromUrl(streamUrl)
        if (localPath != null) return analyze(localPath)
        val bass = nativeResult.getOrNull() ?: return null
        bass.loadAvailablePlugins()
        val stream = bass.createUrlDecodeStream(streamUrl).getOrNull() ?: return null
        return try {
            val totalSamples = bass.lengthBytes(stream)
                ?.let { it / Float.SIZE_BYTES }
            decodeWaveform(
                bass = bass,
                stream = stream,
                totalSamples = totalSamples,
                bucketCount = bucketCount,
            )
        } finally {
            bass.freeStream(stream)
        }
    }
}

private fun localPathFromUrl(url: String): Path? =
    runCatching {
        val uri = URI(url)
        if (uri.scheme == "file") Path.of(uri) else null
    }.getOrNull()

private fun decodeWaveform(
    bass: BassNative,
    stream: Int,
    totalSamples: Long?,
    bucketCount: Int,
): AudioWaveform? {
    if (bucketCount <= 0) return null
    val effectiveTotalSamples = totalSamples?.takeIf { it > 0 } ?: return null
    val buffer = FloatArray(16_384)
    val buckets = FloatArray(bucketCount)
    val bucketCounts = IntArray(bucketCount)
    val bucketSquares = DoubleArray(bucketCount)
    val bucketPeaks = FloatArray(bucketCount)
    var sampleIndex = 0L

    while (true) {
        val read = bass.readFloatData(stream, buffer).getOrNull() ?: return null
        if (read <= 0) break
        var bufferIndex = 0
        while (bufferIndex < read) {
            if (sampleIndex >= effectiveTotalSamples) break
            val bucket = ((sampleIndex * bucketCount) / effectiveTotalSamples)
                .toInt()
                .coerceIn(0, bucketCount - 1)
            val amplitude = abs(buffer[bufferIndex]).coerceIn(0f, 1f)
            bucketSquares[bucket] += (amplitude * amplitude).toDouble()
            bucketPeaks[bucket] = maxOf(bucketPeaks[bucket], amplitude)
            bucketCounts[bucket] += 1
            sampleIndex += 1
            bufferIndex += 1
        }
        if (sampleIndex >= effectiveTotalSamples) break
    }

    if (sampleIndex <= 0) return null

    repeat(bucketCount) { bucket ->
        val count = bucketCounts[bucket]
        if (count > 0) {
            val rms = sqrt(bucketSquares[bucket] / count).toFloat()
            buckets[bucket] = rms * RmsWeight + bucketPeaks[bucket] * PeakWeight
        }
    }

    val max = buckets.maxOrNull() ?: 0f
    return if (max <= 0f) {
        AudioWaveform(List(bucketCount) { 0f })
    } else {
        AudioWaveform(buckets.map { (it / max).coerceIn(0f, 1f) })
    }
}

private const val RmsWeight = 0.82f
private const val PeakWeight = 0.18f
