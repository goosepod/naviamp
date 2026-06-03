package app.naviamp.desktop

import app.naviamp.desktop.playback.bass.BassNative
import app.naviamp.domain.waveform.AudioWaveformAnalysisSource
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.domain.waveform.AudioWaveformAnalyzer as DomainAudioWaveformAnalyzer
import app.naviamp.domain.waveform.DefaultWaveformBucketCount
import app.naviamp.domain.waveform.normalizeFloatPcmWaveform
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists

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
    val effectiveTotalSamples = totalSamples?.takeIf { it > 0 } ?: return null
    var readError = false
    val waveform = normalizeFloatPcmWaveform(
        totalSamples = effectiveTotalSamples,
        bucketCount = bucketCount,
    ) { buffer ->
        bass.readFloatData(stream, buffer)
            .getOrElse {
                readError = true
                0
            }
    }
    return if (readError) null else waveform
}
